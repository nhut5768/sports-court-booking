/** UI smoke checks against Thymeleaf HTML rendered by CourtIntegrationTest.
 * Run: .\mvnw.cmd test -DsanNha.uiSnapshots=true
 * Then: node scripts/ui-smoke.mjs
 * Uses a local Chrome/Edge installation, Node 22+, and no npm dependencies.
 * Test data and browser profiles remain under ignored target/. No app database is used.
 */
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
const root=process.cwd(),out=path.join(root,'target/ui-preview');
const chrome=[process.env.CHROME_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p=>p&&fs.existsSync(p));
if(!chrome)throw Error('Set CHROME_PATH to a local Chrome or Edge executable.');
if(!fs.existsSync(out))throw Error('First run Maven tests with -DsanNha.uiSnapshots=true.');
const pages=fs.readdirSync(out).filter(p=>p.endsWith('.html'));
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.png':'image/png','.jpg':'image/jpeg','.webp':'image/webp','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
 const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
 // This fixture image belongs only to UI tests, never to production templates.
 const file=pathname.startsWith('/media/courts/')?path.join(root,'src/main/resources/static/images/hero-court.png'):
  pathname.endsWith('.html')?path.join(out,path.basename(pathname)):path.join(root,'src/main/resources/static',pathname);
 const allowed=[out,path.join(root,'src/main/resources/static')];
 if(!allowed.some(base=>path.resolve(file).startsWith(path.resolve(base)+path.sep))||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));
});
server.listen(0,'127.0.0.1');await once(server,'listening');
const base='http://127.0.0.1:'+server.address().port;
const profile=path.join(out,'chrome-profile-'+Date.now());fs.mkdirSync(profile,{recursive:true});
const browser=spawn(chrome,['--headless=new','--remote-debugging-port=0','--remote-allow-origins=*','--no-first-run','--disable-background-networking','--disable-component-update','--disable-sync','--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:'ignore'});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
let socket;
try {
 const portFile=path.join(profile,'DevToolsActivePort');
 for(let i=0;i<100&&!fs.existsSync(portFile);i++)await delay(100);
 const port=fs.readFileSync(portFile,'utf8').split('\n')[0];
 const targets=await(await fetch('http://127.0.0.1:'+port+'/json/list')).json();
 socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);await once(socket,'open');
 let seq=0;const pending=new Map(),listeners=new Map();
 socket.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}else{listeners.get(m.method)?.forEach(fn=>fn(m.params));}});
 const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.text);return r.result.value;};
 await call('Page.enable');await call('Runtime.enable');await call('Network.enable');
 await call('Network.setBlockedURLs',{urls:['https://*','http://img.vietqr.io/*']});
 const results=[],issues=[],jsErrors=[];
 listeners.set('Runtime.exceptionThrown',[p=>jsErrors.push(p.exceptionDetails.text)]);
 const goto=async page=>{
  const loaded=new Promise(resolve=>{const fn=()=>{listeners.set('Page.loadEventFired',[]);resolve();};listeners.set('Page.loadEventFired',[fn]);});
  await call('Page.navigate',{url:base+'/'+page});await Promise.race([loaded,delay(5000)]);await delay(80);
 };
 for(const width of [1440,1024,768,390,320]){
  await call('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:width<768});
  for(const page of pages){
   await goto(page);
   const state=await evaluate(`(() => {
    const d=document.documentElement;
    const unlabeled=[...document.querySelectorAll('input:not([type="hidden"]),select,textarea')].filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>e.name||e.id);
    const duplicateIds=[...document.querySelectorAll('[id]')].map(e=>e.id).filter((id,i,a)=>a.indexOf(id)!==i);
    const brokenAnchors=[...document.querySelectorAll('a[href^="#"]')].filter(e=>e.hash.length>1&&!document.getElementById(decodeURIComponent(e.hash.slice(1)))).map(e=>e.hash);
    const overflow=[...document.querySelectorAll('body *')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.right>d.clientWidth+2&&!e.closest('.table-responsive')&&!e.closest('dialog')&&getComputedStyle(e).position!=='fixed';}).slice(0,8).map(e=>e.tagName+'.'+e.className);
    const strayNavs=[...document.querySelectorAll('body > nav')].length; return {width:d.clientWidth,scrollWidth:d.scrollWidth,unlabeled,duplicateIds,brokenAnchors,overflow,strayNavs};
   })()`);
   results.push({page,width,...state});
   if(state.scrollWidth>state.width+1||state.unlabeled.length||state.duplicateIds.length||state.brokenAnchors.length||state.strayNavs)issues.push({page,width,...state});
   if([1440,390].includes(width)&&(['home.html','owner.html','transfer.html','detail.html','bookings.html','login.html','register.html','register-owner.html'].includes(page)||page.startsWith('admin-'))){
    // Only test fixtures: load lazy images before taking a full-page screenshot.
    await evaluate(`(async()=>{for(const img of document.images)img.loading='eager';await Promise.race([Promise.all([...document.images].map(img=>img.decode().catch(()=>{}))),new Promise(resolve=>setTimeout(resolve,2000))]);})()`);
    const metrics=await call('Page.getLayoutMetrics');const capture=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width,height:Math.min(metrics.cssContentSize.height,9000),scale:1}});
    fs.writeFileSync(path.join(out,page.replace('.html','')+'-'+width+'.png'),Buffer.from(capture.data,'base64'));
   }
  }
 }
 await call('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await goto('home.html');
 const filter=await evaluate(`(() => {const d=document.querySelector('.search-disclosure');d.querySelector('summary').click();const collapsed=!d.open;d.querySelector('summary').click();document.querySelector('.nav-toggle-label').click();return {collapsed,expanded:d.open,menuVisible:getComputedStyle(document.querySelector('.nav-links')).display!=='none'};})()`);
 if(!filter.collapsed||!filter.expanded||!filter.menuVisible)issues.push({interaction:'mobile-filter-and-menu',filter});
 await goto('owner.html');
 const sidebar=await evaluate(`(() => {const d=document.querySelector('.owner-navigation');d.querySelector('summary').click();return !d.open;})()`);
 if(!sidebar)issues.push({interaction:'mobile-sidebar'});
 await goto('detail.html');
 const gallery=await evaluate(`(() => {document.querySelector('[data-gallery]').click();const d=document.querySelector('dialog');const open=d.open;d.querySelector('button').click();return open&&!d.open;})()`);
 if(!gallery)issues.push({interaction:'gallery'});
 await goto('transfer.html');
 const copied=await evaluate(`(async () => {let text='';Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async value=>{text=value;}}});document.querySelector('[data-copy-target="transfer-note"]').click();await new Promise(r=>setTimeout(r,50));return text===document.getElementById('transfer-note').textContent.trim();})()`);
 if(!copied)issues.push({interaction:'copy-transfer-note'});
 // Dispatch synthetic events so no booking or payment request is sent.
 const submit=await evaluate(`(() => {
  const form=document.createElement('form');form.method='post';const button=document.createElement('button');button.type='submit';button.textContent='Lưu';form.append(button);document.body.append(form);
  const first=form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
  const busy=form.getAttribute('aria-busy')==='true'&&button.disabled&&button.textContent==='Đang gửi…';
  const duplicateBlocked=!form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
  window.dispatchEvent(new Event('pageshow'));const restored=!button.disabled&&button.textContent==='Lưu';
  form.onsubmit=()=>false;form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));const cancelPreserved=!button.disabled&&!form.dataset.submitting;form.remove();
  return {first,busy,duplicateBlocked,restored,cancelPreserved};
 })()`);
 if(Object.values(submit).some(v=>!v))issues.push({interaction:'submit-and-cancel',submit});
 const motion=await evaluate(`(async()=>{const button=document.querySelector('.btn');const label=button.textContent;button.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,button:0,clientX:10,clientY:10}));const ripple=!!button.querySelector('.sn-button-ripple');await new Promise(resolve=>setTimeout(resolve,650));return {ripple,cleaned:!button.querySelector('.sn-button-ripple'),labelPreserved:label===button.textContent};})()`);
 if(Object.values(motion).some(value=>!value))issues.push({interaction:'button-motion',motion});
 await call('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
 const reduced=await evaluate(`getComputedStyle(document.documentElement).scrollBehavior==='auto'`);
 if(!reduced)issues.push({interaction:'reduced-motion'});
 const reducedMotion=await evaluate(`(()=>{const button=document.querySelector('.btn');button.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,button:0}));return !button.querySelector('.sn-button-ripple');})()`);
 if(!reducedMotion)issues.push({interaction:'reduced-button-motion'});
 fs.writeFileSync(path.join(out,'browser-report.json'),JSON.stringify({pages:pages.length,viewportChecks:results.length,issues,jsErrors,interactions:{filter,sidebar,gallery,copied,submit,motion,reduced,reducedMotion},results},null,2));
 console.log(JSON.stringify({pages:pages.length,viewportChecks:results.length,issues,jsErrors,interactions:{filter,sidebar,gallery,copied,submit,motion,reduced,reducedMotion}},null,2));
 if(issues.length||jsErrors.length)process.exitCode=1;
 await call('Browser.close');
} finally {socket?.close();browser.kill();server.close();}