/* Private customer-owner inbox. Server authenticates all reads, writes and SSE. */
(() => {
 'use strict';
 const root=document.querySelector('[data-court-messages]');if(!root)return;
 const endpoint=root.dataset.endpoint,me=Number(root.dataset.accountId),home=(root.dataset.home||'/').replace(/\/$/,'');
 const threads=root.querySelector('.messaging-threads'),history=root.querySelector('.messaging-history'),
 form=root.querySelector('form'),input=form.querySelector('textarea'),send=form.querySelector('button'),
 status=root.querySelector('.messaging-status'),connection=root.querySelector('.messaging-connection'),
 older=root.querySelector('.messaging-older'),moreThreads=root.querySelector('.messaging-more-threads');
 let selected=Number(root.dataset.selected)||0,latest=0,earliest=0,listPage=0,busy=false,catching=false,refreshPending=false,selectionVersion=0;
 let stream,displayed=new Set(),pending=new Map();
 const csrf=()=>{const token=document.querySelector('meta[name=_csrf]')?.content,header=document.querySelector('meta[name=_csrf_header]')?.content;return token&&header?{[header]:token}:{};};
 async function api(path,options={}) {
  const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),15000);
  try {
   const response=await fetch(endpoint+path,{...options,credentials:'same-origin',signal:abort.signal,headers:{'Content-Type':'application/json',...csrf(),...options.headers}});
   if(response.status===401||response.redirected)throw Error('Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.');
   if(response.status===403)throw Error('Không có quyền truy cập hoặc phiên đã hết hạn. Vui lòng tải lại trang.');
   const data=await response.json().catch(()=>({}));
   if(!response.ok)throw Error(data.message||'Không thực hiện được yêu cầu. Vui lòng thử lại.');
   return data;
  }catch(error){if(error.name==='AbortError')throw Error('Kết nối quá lâu. Vui lòng thử lại.');throw error;}
  finally{clearTimeout(timer);}
 }
 function showError(error){status.textContent=error.message==='Failed to fetch'?'Mất kết nối. Tin chưa gửi có thể thử lại.':error.message;}
 function relative(path){return home+path;}
 function unread(value){document.querySelectorAll('[data-chat-unread]').forEach(el=>{el.textContent=String(value);el.setAttribute('aria-label',value+' tin chưa đọc');});}
 async function list(append=false) {
  const page=append?listPage+1:0,data=await api('/conversations?page='+page);
  if(!append)threads.replaceChildren();listPage=page;moreThreads.hidden=!data.hasMore;unread(data.unread);
  for(const thread of data.conversations){
   if(threads.querySelector('[data-thread="'+thread.id+'"]'))continue;
   const button=document.createElement('button');button.type='button';button.className='messaging-thread';button.dataset.thread=thread.id;
   button.setAttribute('aria-pressed',String(thread.id===selected));
   const title=document.createElement('strong');title.textContent=thread.courtName;
   const peer=document.createElement('span');peer.textContent=thread.peerName+' · '+(thread.peerRole==='OWNER'?'Chủ sân':'Khách hàng');
   button.append(title,peer);
   if(thread.unread){const count=document.createElement('small');count.textContent=thread.unread+' tin chưa đọc';button.append(count);}
   button.addEventListener('click',()=>choose(thread.id));threads.append(button);
  }
  if(!threads.children.length){const empty=document.createElement('p');empty.className='messaging-empty';empty.textContent='Chưa có hội thoại. Mở trang chi tiết sân để nhắn cho chủ sân.';threads.append(empty);}
 }
 function render(m,prepend=false) {
  if(displayed.has(m.id))return null;displayed.add(m.id);
  const pendingEntry=pending.get(m.clientId);
  if(pendingEntry&&m.senderId===me){pendingEntry.element.remove();pending.delete(m.clientId);}
  const bubble=document.createElement('article');bubble.className='court-chat-message'+(m.senderId===me?' is-mine':'');bubble.dataset.messageId=m.id;
  const name=document.createElement('strong');name.textContent=m.senderName+' · '+(m.senderRole==='OWNER'?'Chủ sân':'Khách hàng');
  const text=document.createElement('p');text.textContent=m.content;
  const time=document.createElement('time');time.dateTime=m.sentAt;time.textContent=new Intl.DateTimeFormat('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(m.sentAt));
  bubble.append(name,text,time);if(prepend)history.prepend(bubble);else history.append(bubble);
  latest=Math.max(latest,m.id);earliest=earliest?Math.min(earliest,m.id):m.id;return bubble;
 }
 async function read(id,through){if(!through||document.hidden||selected!==id)return;await api('/conversations/'+id+'/read',{method:'POST',body:JSON.stringify({through})});}
 async function choose(id){
  if(busy){status.textContent='Vui lòng đợi tin hiện tại gửi xong.';return;}
  const version=++selectionVersion;selected=id;latest=0;earliest=0;displayed=new Set();history.replaceChildren();status.textContent='Đang tải tin nhắn…';input.disabled=true;send.disabled=true;older.hidden=true;
  // Unsent messages remain in memory by conversation and can be retried on return.
  try {
   const [thread,data]=await Promise.all([api('/conversations/'+id),api('/conversations/'+id+'/messages')]);
   if(version!==selectionVersion)return;
   root.querySelector('#conversation-title').textContent=thread.courtName;
   root.querySelector('.messaging-peer').textContent=thread.peerName+' · '+(thread.peerRole==='OWNER'?'Chủ sân':'Khách hàng');
   const link=root.querySelector('.messaging-court-link');link.href=relative('/courts/'+thread.courtId);link.hidden=false;
   for(const m of data.messages)render(m);older.hidden=!data.hasMore;
   if(!data.messages.length){const empty=document.createElement('p');empty.className='messaging-empty';empty.textContent='Chưa có tin nhắn. Hãy để lại lời nhắn; người nhận có thể trả lời sau.';history.append(empty);}
   for(const entry of pending.values())if(entry.conversation===id)history.append(entry.element);
   status.textContent='';input.disabled=false;send.disabled=false;history.scrollTop=history.scrollHeight;
   window.history.replaceState(null,'',relative('/messages')+'?conversation='+id);
   await read(id,latest);await list();
  }catch(error){if(version===selectionVersion)showError(error);}
 }
 async function catchup(){
  if(!selected)return;
  if(catching){refreshPending=true;return;}catching=true;const id=selected,version=selectionVersion;
  try {
   let more=true;
   while(more){const after=latest;const data=await api('/conversations/'+id+'/messages'+(after?'?after='+after:''));if(version!==selectionVersion)return;
    if(data.messages.length)history.querySelector('.messaging-empty')?.remove();
    for(const m of data.messages)render(m);more=after>0&&data.hasMore;
    if(!data.messages.length)more=false;
   }
   history.scrollTop=history.scrollHeight;await read(id,latest);await list();
  }catch(error){showError(error);}
  finally{catching=false;if(refreshPending){refreshPending=false;catchup();}}
 }
 older.addEventListener('click',async()=>{
  if(!selected||!earliest)return;const id=selected,version=selectionVersion;older.disabled=true;const height=history.scrollHeight;
  try{const data=await api('/conversations/'+id+'/messages?before='+earliest);if(version!==selectionVersion)return;
   for(const m of [...data.messages].reverse())render(m,true);older.hidden=!data.hasMore;history.scrollTop=history.scrollHeight-height;
  }catch(error){showError(error);}finally{older.disabled=false;}
 });
 async function transmit(entry){
  if(busy)return;busy=true;send.disabled=true;entry.retry.disabled=true;entry.state.textContent='Đang gửi…';
  try{const m=await api('/conversations/'+entry.conversation+'/messages',{method:'POST',body:JSON.stringify({content:entry.content,clientId:entry.clientId})});
   pending.delete(entry.clientId);entry.element.remove();if(selected===entry.conversation){render(m);history.scrollTop=history.scrollHeight;}list().catch(showError);
  }catch(error){entry.state.textContent='Gửi thất bại · '+error.message;entry.retry.hidden=false;entry.retry.disabled=false;}
  finally{busy=false;send.disabled=!selected;}
 }
 form.addEventListener('submit',event=>{
  event.preventDefault();if(busy||!selected)return;const content=input.value.trim();if(!content||content.length>1000)return;
  if(pending.size>=10){status.textContent='Bạn còn nhiều tin chưa gửi. Hãy thử lại các tin đó trước.';return;}
  const clientId=crypto.randomUUID(),element=document.createElement('article');element.className='court-chat-message is-mine is-pending';
  const text=document.createElement('p');text.textContent=content;const state=document.createElement('small'),retry=document.createElement('button');retry.type='button';retry.textContent='Thử lại';retry.hidden=true;
  const entry={clientId,conversation:selected,content,element,state,retry};retry.addEventListener('click',()=>transmit(entry));element.append(text,state,retry);
  history.querySelector('.messaging-empty')?.remove();history.append(element);history.scrollTop=history.scrollHeight;input.value='';pending.set(clientId,entry);transmit(entry);
 });
 input.addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();form.requestSubmit();}});
 moreThreads.addEventListener('click',()=>list(true).catch(showError));
 function connect(){
  stream=new EventSource(endpoint+'/events');
  stream.addEventListener('ready',()=>{connection.textContent='Kênh cập nhật tin nhắn đã kết nối.';catchup();list().catch(showError);});
  stream.addEventListener('message',event=>{try{const change=JSON.parse(event.data);list().catch(showError);if(change.conversationId===selected)catchup();}catch{}});
  stream.onerror=()=>{connection.textContent='Đang kết nối lại kênh tin nhắn…';};
 }
 const recovery=setInterval(()=>{if(!document.hidden){list().catch(showError);if(selected)catchup();}},15000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden){catchup();list().catch(showError);}});
 window.addEventListener('pagehide',()=>{stream?.close();clearInterval(recovery);});
 window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});
 list().catch(showError);if(selected)choose(selected);connect();
})();
