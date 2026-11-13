/* Dependency-free progressive enhancements; all payment state remains server-owned. */
(() => {
 'use strict';
 document.documentElement.classList.add('js-ready');
 const courtPhotoSelector='[data-court-image],.card-photo,.court-main-photo img,.court-photo-thumbs img,.court-editor-image-card img';
 const photoError=image=>{
  if(image.matches('.discovery-hero-photo,.sport-photo-card > img,.owner-invitation-photo > img')){image.hidden=true;image.parentElement.classList.add('editorial-image-missing');return;}
  if(!image.matches(courtPhotoSelector))return;image.hidden=true;
  const parent=image.closest('.card-art,.court-main-photo,.court-photo-thumbs a,.court-editor-image-card');if(!parent||parent.classList.contains('image-failed'))return;
  parent.classList.add('image-failed');
  if(!parent.querySelector('.photo-placeholder')){const note=document.createElement('span');note.className='court-image-error';note.textContent='Ảnh sân tạm chưa tải được';parent.append(note);}
  if(parent.hasAttribute('data-gallery')){parent.removeAttribute('data-gallery');parent.removeAttribute('href');}
 };
 document.addEventListener('error',event=>{if(event.target instanceof HTMLImageElement)photoError(event.target);},true);
 document.querySelectorAll(courtPhotoSelector+',.discovery-hero-photo,.sport-photo-card > img,.owner-invitation-photo > img').forEach(image=>{if(image.complete&&!image.naturalWidth)photoError(image);});
 document.querySelectorAll('[data-search-form]').forEach(form=>form.addEventListener('submit',event=>{if(event.defaultPrevented)return;const button=form.querySelector('[type=submit]');if(button){button.textContent='Đang tìm sân…';button.disabled=true;form.setAttribute('aria-busy','true');form.dataset.searching='true';}}));
 window.addEventListener('pageshow',()=>document.querySelectorAll('form[data-searching]').forEach(form=>{const button=form.querySelector('[type=submit]');button.disabled=false;button.textContent=form.classList.contains('hero-search')?'Tìm sân':'Áp dụng';form.removeAttribute('aria-busy');delete form.dataset.searching;}));
 const showQrError = image => { if (!image.matches('.deposit-qr')) return; image.hidden = true; const message = document.querySelector('[data-qr-error]'); if (message) message.hidden = false; };
 document.addEventListener('error', event => { if (event.target instanceof HTMLImageElement) showQrError(event.target); }, true);
 document.querySelectorAll('.deposit-qr').forEach(image => { if (image.complete && !image.naturalWidth) showQrError(image); });
 const status = document.createElement('div');
 status.className = 'ui-feedback'; status.setAttribute('role','status'); status.setAttribute('aria-live','polite'); document.body.append(status);
 let feedbackTimer;
 const tell = message => { status.textContent=message; status.classList.add('is-visible'); clearTimeout(feedbackTimer); feedbackTimer=setTimeout(()=>status.classList.remove('is-visible'),5000); };
 document.addEventListener('click',async event=>{
  const button=event.target.closest('[data-copy-target]'); if(!button)return;
  const source=document.getElementById(button.dataset.copyTarget); if(!source)return;
  try {
   if(!navigator.clipboard?.writeText)throw new Error('Clipboard unavailable');
   await navigator.clipboard.writeText(source.dataset.copyValue ?? source.textContent.trim()); tell('Đã sao chép. Hãy đối chiếu lại thông tin trước khi chuyển khoản.');
  } catch {
   const selection=window.getSelection(),range=document.createRange(); range.selectNodeContents(source); selection.removeAllRanges(); selection.addRange(range);
   tell('Đã chọn nội dung. Nhấn giữ hoặc dùng Ctrl+C để sao chép.');
  }
 });
 const dialog=document.createElement('dialog'); dialog.className='photo-dialog'; dialog.setAttribute('aria-label','Xem ảnh sân phóng to');
 const close=document.createElement('button'); close.type='button'; close.className='btn btn-light'; close.textContent='Đóng ảnh';
 const image=document.createElement('img'); dialog.append(close,image); document.body.append(dialog);
 close.addEventListener('click',()=>dialog.close());
 dialog.addEventListener('click',event=>{if(event.target===dialog)dialog.close();});
 document.addEventListener('click',event=>{
  const link=event.target.closest('a[data-gallery]');
  if(!link||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey||!dialog.showModal)return;
  event.preventDefault(); image.src=link.href; image.alt=link.querySelector('img')?.alt||'Ảnh sân'; dialog.showModal();
 });
 document.querySelectorAll('[data-password-toggle]').forEach(button=>{
  const input=document.getElementById(button.getAttribute('aria-controls'));
  if(!input)return;
  button.hidden=false;
  button.addEventListener('click',()=>{
   const visible=input.type==='password';
   input.type=visible?'text':'password';
   button.setAttribute('aria-pressed',String(visible));
   const label=visible?'Ẩn mật khẩu':'Hiện mật khẩu';
   button.setAttribute('aria-label',label);button.title=label;
  });
 });
 // Bubbling runs after inline confirmation handlers accept the action.
 document.addEventListener('submit',event=>{
  const form=event.target; if(event.defaultPrevented||form.method.toLowerCase()!=='post')return;
  if(form.dataset.submitting){event.preventDefault();return;}
  form.dataset.submitting='true'; form.setAttribute('aria-busy','true');
  form.querySelectorAll('button[type="submit"]').forEach(button=>{button.dataset.originalText=button.textContent;button.disabled=true;button.textContent=button.dataset.loadingText || 'Đang gửi…';});
 });
 window.addEventListener('pageshow',()=>document.querySelectorAll('form[data-submitting]').forEach(form=>{
  delete form.dataset.submitting;form.removeAttribute('aria-busy');
  form.querySelectorAll('[data-original-text]').forEach(button=>{button.disabled=false;button.textContent=button.dataset.originalText;delete button.dataset.originalText;});
 }));
 document.addEventListener('click',event=>document.querySelectorAll('.account-menu[open]').forEach(menu=>{if(!menu.contains(event.target))menu.open=false;}));
 document.addEventListener('keydown',event=>{if(event.key==='Escape')document.querySelectorAll('.account-menu[open]').forEach(menu=>menu.open=false);});
 const desktop=matchMedia('(min-width: 992px)'),sidebar=document.querySelector('.owner-navigation');
 const expandSidebar=()=>{if(sidebar&&desktop.matches)sidebar.open=true;};
 desktop.addEventListener('change',expandSidebar);expandSidebar();
})();