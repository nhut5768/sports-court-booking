/* Separate Gemini assistance and private customer-owner conversations. */
(() => {
 'use strict';
 const root=document.querySelector('[data-assistant]');if(!root)return;
 const launch=root.querySelector('.sn-chat-launch'),panel=root.querySelector('.sn-chat-panel'),form=root.querySelector('form'),input=form.querySelector('textarea'),send=form.querySelector('button'),log=root.querySelector('[role=log]'),status=root.querySelector('.sn-chat-status'),peer=root.querySelector('.sn-chat-peer'),reset=root.querySelector('.sn-chat-reset'),older=root.querySelector('.sn-chat-older'),suggestions=root.querySelector('.sn-chat-suggestions');
 const aiEndpoint=root.dataset.endpoint,chatEndpoint=root.dataset.chatEndpoint,role=root.dataset.role||'',me=Number(root.dataset.accountId)||0,home=(root.dataset.home||'/').replace(/\/$/,'');
 let selected=0,mode='ai',busy=false,available=true,latest=0,earliest=0,epoch=0,stream,syncing=false,syncAgain=false,askAiNext=false;
 const nodes=new Map(),outbox=new Map();
 const csrf=()=>{const token=document.querySelector('meta[name=_csrf]')?.content,header=document.querySelector('meta[name=_csrf_header]')?.content;return token&&header?{[header]:token}:{};};
 async function api(base,path,options={}){
  const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),35000);
  try{const response=await fetch(base+path,{...options,credentials:'same-origin',signal:abort.signal,headers:{'Content-Type':'application/json',...csrf(),...options.headers}});
   if(response.status===401||response.redirected)throw Error('Vui lòng đăng nhập để nhắn chủ sân.');
   if(response.status===403)throw Error('Phiên đã hết hạn hoặc bạn không có quyền. Hãy tải lại trang.');
   const data=await response.json().catch(()=>({}));if(!response.ok)throw Error(data.message||'Không kết nối được. Vui lòng thử lại.');return data;
  }catch(error){if(error.name==='AbortError')throw Error('Phản hồi hơi lâu. Vui lòng thử lại.');if(error.message==='Failed to fetch')throw Error('Mất kết nối. Bạn có thể thử gửi lại.');throw error;}
  finally{clearTimeout(timer);}
 }
 function controls(){root.querySelector('[data-chat-ai]').setAttribute('aria-pressed',String(mode==='ai'));root.querySelector('[data-chat-owners]').setAttribute('aria-pressed',String(mode==='picker'&&role==='CUSTOMER'));root.querySelector('[data-chat-inbox]').setAttribute('aria-pressed',String(mode==='conversation'));input.disabled=busy||mode==='picker';send.disabled=input.disabled;reset.disabled=busy;root.querySelectorAll('.sn-chat-tools button,.sn-chat-suggestions button').forEach(button=>button.disabled=busy);}
 function label(text){status.textContent=text;}
 function plain(text,kind='bot',links=[]){
  const node=document.createElement('div');node.className='sn-chat-bubble sn-chat-'+kind;const p=document.createElement('p');p.textContent=text;node.append(p);appendLinks(node,links);log.append(node);log.scrollTop=log.scrollHeight;return node;
 }
 function appendLinks(node,links){for(const link of (Array.isArray(links)?links:[]).slice(0,5)){
  if(typeof link.url!=='string'||!/^\/(?:courts\/\d+\?date=\d{4}-\d{2}-\d{2}|bookings|)$/.test(link.url))continue;
  const a=document.createElement('a');a.textContent=link.label;a.href=home+link.url;node.append(a);
 }}
 function render(message,prepend=false){
  latest=Math.max(latest,message.id);earliest=earliest?Math.min(earliest,message.id):message.id;
  const previous=nodes.get(message.id);
  if(message.aiStatus==='CANCELLED'){previous?.remove();nodes.delete(message.id);return;}
  const queued=outbox.get(message.clientId);if(queued&&message.senderId===me){queued.node.remove();outbox.delete(message.clientId);}
  const bubble=document.createElement('article');bubble.className='sn-chat-bubble '+(message.senderRole==='AI'?'sn-chat-bot':message.senderId===me?'sn-chat-user':'sn-chat-owner');bubble.dataset.unifiedMessage=message.id;bubble.dataset.aiStatus=message.aiStatus||'NONE';
  const name=document.createElement('strong');name.className='sn-chat-author';name.textContent=message.senderRole==='AI'?'Trợ lý AI':message.senderName+' · '+(message.senderRole==='OWNER'?'Chủ sân':'Khách hàng');
  const text=document.createElement('p');text.textContent=message.content;const time=document.createElement('time');time.dateTime=message.sentAt;time.textContent=new Intl.DateTimeFormat('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',hour:'2-digit',minute:'2-digit',day:'2-digit',month:'2-digit'}).format(new Date(message.sentAt));bubble.append(name,text,time);appendLinks(bubble,message.links);
  if(previous)previous.replaceWith(bubble);else if(prepend)log.prepend(bubble);else log.append(bubble);nodes.set(message.id,bubble);
 }
 function clear(){log.replaceChildren();nodes.clear();latest=0;earliest=0;older.hidden=true;}
 function setGeneral(){if(busy)return;++epoch;selected=0;mode='ai';clear();input.placeholder='Bạn muốn tìm sân ở đâu?';peer.textContent='Hỏi trợ lý AI hoặc chọn “Nhắn chủ sân” ngay tại đây.';suggestions.hidden=false;reset.textContent='Xóa hội thoại AI';plain('Chào bạn! Tôi có thể giúp tìm sân, kiểm tra lịch trống hoặc hướng dẫn đặt/hủy sân.');label('');controls();check();}
 function loginPrompt(){clear();plain('Bạn cần đăng nhập tài khoản khách hàng để chọn chủ sân và gửi tin.');const a=document.createElement('a');a.href=home+'/login';a.textContent='Đăng nhập';a.className='sn-chat-login';log.append(a);}
 async function check(){try{const data=await api(aiEndpoint,'/status');available=data.available===true;if(mode==='ai')label(available?'Gemini đã được cấu hình. Gửi câu hỏi để nhận phản hồi.':'AI tạm chưa khả dụng; bạn vẫn có thể nhắn chủ sân.');}catch(error){if(mode==='ai')label(error.message);}}
 function open(){panel.hidden=false;launch.setAttribute('aria-expanded','true');check();connect();if(selected)sync();else if(role==='OWNER')inbox();input.focus();}
 function hide(){panel.hidden=true;launch.setAttribute('aria-expanded','false');(document.querySelector('[data-open-chat],[data-open-ai]')||launch).focus();}
 async function choose(id){if(busy)return;const version=++epoch;selected=id;mode='conversation';clear();label('Đang tải hội thoại…');reset.textContent='Chọn hội thoại';suggestions.hidden=true;input.placeholder=role==='OWNER'?'Trả lời khách…':'Nhắn cho chủ sân…';controls();
  try{const [thread,data]=await Promise.all([api(chatEndpoint,'/conversations/'+id),api(chatEndpoint,'/conversations/'+id+'/messages')]);if(version!==epoch)return;
   describe(thread);for(const message of data.messages)render(message);older.hidden=!data.hasMore;
   if(!data.messages.length)plain(role==='OWNER'?'Khách hàng chưa gửi tin. Bạn có thể để lại lời nhắn tại đây.':'Bạn có thể để lại tin nhắn tại đây. Tin nhắn gửi trực tiếp đến chủ sân; chủ sân sẽ trả lời tại đây.');
   for(const entry of outbox.values())if(entry.conversation===id)log.append(entry.node);
   log.scrollTop=log.scrollHeight;label('');await markRead(id);counts();
  }catch(error){if(version===epoch)label(error.message);}controls();
 }
 function describe(thread){peer.textContent=thread.peerName+' · '+(thread.peerRole==='OWNER'?'Chủ sân':'Khách hàng')+' · Tin nhắn trực tiếp';}
 async function markRead(id){if(latest&&!panel.hidden&&!document.hidden&&selected===id)await api(chatEndpoint,'/conversations/'+id+'/read',{method:'POST',body:JSON.stringify({through:latest})});}
 async function sync(){if(!selected)return;if(syncing){syncAgain=true;return;}syncing=true;const id=selected,version=epoch;
  try{let more=latest>0;while(more){const data=await api(chatEndpoint,'/conversations/'+id+'/messages?after='+latest);if(version!==epoch)return;for(const message of data.messages)render(message);more=data.hasMore&&data.messages.length>0;}
   const [thread,data]=await Promise.all([api(chatEndpoint,'/conversations/'+id),api(chatEndpoint,'/conversations/'+id+'/messages')]);if(version!==epoch)return;
   describe(thread);for(const message of data.messages)render(message);log.scrollTop=log.scrollHeight;await markRead(id);counts();
  }catch(error){if(version===epoch)label(error.message);}finally{syncing=false;if(syncAgain){syncAgain=false;sync();}}
 }
 async function counts(){if(!me)return;try{const data=await api(chatEndpoint,'/conversations');document.querySelectorAll('[data-unified-unread],[data-chat-unread]').forEach(node=>{node.textContent=data.unread?String(data.unread):'';});}catch{}}
 async function directory(page=0,append=false){if(busy)return;if(!me){mode='picker';loginPrompt();controls();return;}if(role!=='CUSTOMER'){inbox();return;}
  const version=++epoch;mode='picker';selected=0;reset.textContent='Hỏi AI';suggestions.hidden=false;peer.textContent='Chọn chủ sân để bắt đầu — không cần mở trang sân.';if(!append)clear();label('Đang tải chủ sân…');controls();
  try{const data=await api(chatEndpoint,'/owners?page='+page);if(version!==epoch)return;log.querySelector('.sn-chat-picker-more')?.remove();
   for(const owner of data.owners){const button=document.createElement('button');button.type='button';button.className='sn-chat-choice';const name=document.createElement('strong');name.textContent=owner.name;const courts=document.createElement('span');courts.textContent=owner.courts.join(' · ');button.append(name,courts);button.addEventListener('click',async()=>{if(busy)return;busy=true;controls();try{const thread=await api(chatEndpoint,'/owners/'+owner.id+'/conversation',{method:'POST'});busy=false;await choose(thread.id);}catch(error){label(error.message);}finally{busy=false;controls();}});log.append(button);}
   if(!data.owners.length&&!append)plain('Chưa có chủ sân đang nhận tư vấn. Bạn có thể hỏi AI bằng các gợi ý phía trên.');
   if(data.hasMore){const more=document.createElement('button');more.type='button';more.className='sn-chat-picker-more';more.textContent='Xem thêm chủ sân';more.onclick=()=>directory(page+1,true);log.append(more);}label('');
  }catch(error){label(error.message);}
 }
 async function inbox(page=0,append=false){if(busy)return;if(!me){mode='picker';loginPrompt();controls();return;}const version=++epoch;mode='picker';selected=0;if(!append)clear();peer.textContent='Hội thoại riêng giữa khách hàng và chủ sân.';reset.textContent='Hỏi AI';suggestions.hidden=true;controls();
  try{const data=await api(chatEndpoint,'/conversations?page='+page);if(version!==epoch)return;log.querySelector('.sn-chat-picker-more')?.remove();
   for(const thread of data.conversations){const button=document.createElement('button');button.type='button';button.className='sn-chat-choice';const name=document.createElement('strong');name.textContent=thread.peerName;const about=document.createElement('span');about.textContent=thread.courtName+(thread.unread?' · '+thread.unread+' tin mới':'');button.append(name,about);button.onclick=()=>choose(thread.id);log.append(button);}
   if(!data.conversations.length)plain('Chưa có hội thoại. Khách hàng có thể chọn “Nhắn chủ sân” để bắt đầu.');
   if(data.hasMore){const more=document.createElement('button');more.type='button';more.className='sn-chat-picker-more';more.textContent='Xem thêm hội thoại';more.onclick=()=>inbox(page+1,true);log.append(more);}label('');counts();
  }catch(error){label(error.message);}
 }
 async function transmit(entry){if(busy)return;busy=true;controls();entry.retry.disabled=true;entry.state.textContent='Đang gửi…';
  try{const data=await api(chatEndpoint,'/conversations/'+entry.conversation+'/messages',{method:'POST',body:JSON.stringify({clientId:entry.id,content:entry.content,askAi:false})});outbox.delete(entry.id);entry.node.remove();if(selected===entry.conversation){render(data);sync();}counts();}
  catch(error){entry.state.textContent='Gửi thất bại: '+error.message;entry.retry.hidden=false;entry.retry.disabled=false;}
  finally{busy=false;controls();}
 }
 form.addEventListener('submit',async event=>{event.preventDefault();if(busy||mode==='picker')return;const text=input.value.trim(),explicit=askAiNext;askAiNext=false;if(!text||text.length>1000)return;
  if(selected){if(outbox.size>=10){label('Hãy gửi lại các tin đang chờ trước.');return;}const entry={id:crypto.randomUUID(),conversation:selected,content:text,askAi:explicit,node:document.createElement('div'),state:document.createElement('small'),retry:document.createElement('button')};entry.node.className='sn-chat-bubble sn-chat-user';const content=document.createElement('p');content.textContent=text;entry.retry.type='button';entry.retry.textContent='Thử lại';entry.retry.hidden=true;entry.retry.onclick=()=>transmit(entry);entry.node.append(content,entry.state,entry.retry);log.append(entry.node);outbox.set(entry.id,entry);input.value='';log.scrollTop=log.scrollHeight;transmit(entry);return;}
  const version=epoch;plain(text,'user');input.value='';busy=true;controls();label('Trợ lý đang trả lời…');
  try{const data=await api(aiEndpoint,'/messages',{method:'POST',body:JSON.stringify({message:text})});if(version===epoch){const node=plain(data.message,'bot',data.links),author=document.createElement('strong');author.className='sn-chat-author';author.textContent='Trợ lý AI';node.prepend(author);}}
  catch(error){if(version===epoch){plain(error.message,'error');input.value=text;}}finally{busy=false;controls();label('');}
 });
 suggestions.querySelectorAll('button').forEach(button=>button.onclick=()=>{if(busy)return;if(mode!=='ai')setGeneral();input.value=button.textContent;askAiNext=true;form.requestSubmit();});
 input.addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();form.requestSubmit();}});
 older.onclick=async()=>{if(!selected||!earliest)return;const id=selected,version=epoch;older.disabled=true;const height=log.scrollHeight;try{const data=await api(chatEndpoint,'/conversations/'+id+'/messages?before='+earliest);if(version!==epoch)return;for(const message of [...data.messages].reverse())render(message,true);older.hidden=!data.hasMore;log.scrollTop=log.scrollHeight-height;}catch(error){label(error.message);}finally{older.disabled=false;}};
 root.querySelector('[data-chat-ai]').onclick=setGeneral;root.querySelector('[data-chat-owners]').onclick=()=>directory();root.querySelector('[data-chat-inbox]').onclick=()=>inbox();
 if(role==='OWNER')root.querySelector('[data-chat-owners]').hidden=true;
 reset.onclick=async()=>{if(mode!=='ai'){if(role==='OWNER')inbox();else directory();return;}if(busy)return;try{await api(aiEndpoint,'/history',{method:'DELETE'});setGeneral();}catch(error){label(error.message);}};
 function connect(){if(!me||stream)return;stream=new EventSource(chatEndpoint+'/events');stream.addEventListener('ready',()=>{if(selected)sync();counts();});stream.addEventListener('message',event=>{try{const change=JSON.parse(event.data);if(change.conversationId===selected)sync();else if(mode==='picker'&&role==='OWNER'&&!panel.hidden)inbox();counts();}catch{}});stream.onerror=()=>{if(selected&&!panel.hidden)label('Đang kết nối lại; tin đã lưu sẽ được tải bù.');};}
 launch.onclick=()=>panel.hidden?open():hide();root.querySelector('.sn-chat-close').onclick=hide;panel.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();hide();}});
 document.querySelectorAll('[data-open-chat],[data-open-ai]').forEach(button=>button.addEventListener('click',event=>{event.preventDefault();open();if(button.hasAttribute('data-open-ai'))setGeneral();else if(button.hasAttribute('data-open-chat')){if(role==='OWNER')inbox();else directory();}}));
 const recovery=setInterval(()=>{if(!panel.hidden&&!document.hidden){if(selected)sync();counts();}},10000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&!panel.hidden&&selected)sync();});window.addEventListener('pagehide',()=>{stream?.close();clearInterval(recovery);});
 if(document.querySelector('[data-auto-chat]')){open();const id=Number(new URL(location.href).searchParams.get('conversation'));if(id)choose(id);else if(role==='OWNER')inbox();else directory();}
})();
