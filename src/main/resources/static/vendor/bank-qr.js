(() => {
'use strict';
const bank=document.getElementById('bankCode'), number=document.getElementById('bankAccountNo'), name=document.getElementById('bankAccountName');
const preview=document.getElementById('bank-qr-preview'), image=document.getElementById('bank-qr-image'), status=document.getElementById('bank-qr-status');
if(!bank || !number || !name || !preview || !image || !status) return;
let timer, generation=0;
function update(){
  clearTimeout(timer); const current=++generation;
  image.hidden=true; image.removeAttribute('src');
  const account=number.value.replace(/\s/g,'');
  const accountName=name.value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[đĐ]/g,'D').trim().replace(/\s+/g,' ').toUpperCase();
  const valid=/^\d{6,19}$/.test(account);
  number.setCustomValidity(number.value && !valid ? 'Số tài khoản gồm 6–19 chữ số.' : '');
  preview.hidden=!bank.value || !valid || !/^[A-Z ]{2,100}$/.test(accountName);
  if(preview.hidden)return;
  status.textContent='Đang tạo mã QR…';
  timer=setTimeout(()=>{
    const url=new URL('https://img.vietqr.io/image/'+bank.value+'-'+account+'-compact2.png');
    url.searchParams.set('accountName',accountName);
    const pending=new Image();
    pending.onload=()=>{if(current!==generation)return; image.src=url.href; image.hidden=false; status.textContent='QR theo thông tin đang nhập. Vui lòng kiểm tra và lưu tài khoản.';};
    pending.onerror=()=>{if(current!==generation)return; status.textContent='Không tải được QR. Kiểm tra kết nối hoặc thông tin ngân hàng rồi thử lại.';};
    pending.src=url.href;
  },500);
}
[bank,number,name].forEach(field=>field.addEventListener('input',update)); bank.addEventListener('change',update); update();
})();
