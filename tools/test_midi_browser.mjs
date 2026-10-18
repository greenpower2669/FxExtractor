// Real Chromium smoke test: a local HTTP server streams the packaged 109 MiB
// CPU MIDI ONNX weights. No proxy interception or external network is used.
import { chromium } from "playwright";
import path from "node:path";
import fs from "node:fs";
import http from "node:http";
import assert from "node:assert/strict";

const root=path.resolve("app/src/main/assets/midi");
const types={".html":"text/html; charset=utf-8",".mjs":"text/javascript; charset=utf-8",
  ".js":"text/javascript; charset=utf-8",".wasm":"application/wasm",
  ".onnx":"application/octet-stream",".json":"application/json"};
const server=http.createServer((req,res)=>{
  const url=new URL(req.url||"/","http://127.0.0.1");
  const relative=decodeURIComponent(url.pathname.replace(/^\/midi\//,""));
  const file=path.resolve(root,relative);
  if(!url.pathname.startsWith("/midi/")||!file.startsWith(root+path.sep)||
     !fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end("not found");return;
  }
  const stat=fs.statSync(file);
  res.writeHead(200,{"Content-Type":types[path.extname(file)]||"application/octet-stream",
                     "Content-Length":stat.size,"Cache-Control":"no-store",
                     "Access-Control-Allow-Origin":"*"});
  fs.createReadStream(file,{highWaterMark:1024*1024}).pipe(res);
});
await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
const origin="http://127.0.0.1:"+server.address().port+"/midi/";
const logs=[];
let browser;
try{
  browser=await chromium.launch({headless:true,channel:"chromium",
    args:["--no-sandbox","--disable-dev-shm-usage","--js-flags=--max-old-space-size=4096"]});
  const context=await browser.newContext({serviceWorkers:"block"});
  const page=await context.newPage();
  page.on("console",msg=>{if(msg.type()==="error")logs.push("console: "+msg.text());});
  page.on("pageerror",e=>logs.push("pageerror: "+String(e.stack||e)));
  page.on("crash",()=>logs.push("Chromium page crashed"));
  page.on("close",()=>logs.push("Chromium page closed"));
  await page.goto(origin+"index.html",{waitUntil:"domcontentloaded",timeout:30000});
  console.log("Launched MIDI v5 CPU from",origin,"(local only)");
  await page.waitForFunction(()=>{
    const state=document.getElementById("loadStatus")?.textContent||"";
    const generate=document.getElementById("generate");
    return state.includes("❌") || (generate && generate.disabled===false);
  },null,{timeout:180000});
  const result=await page.evaluate(()=>({
    status:document.getElementById("loadStatus").textContent,
    ready:!document.getElementById("generate").disabled,
    resources:performance.getEntriesByType("resource").filter(r=>!r.name.startsWith(location.origin+"/")).map(r=>r.name)
  }));
  console.log("MIDI CPU browser smoke:",JSON.stringify(result));
  assert.equal(result.ready,true,"CPU ONNX model initialization failed: "+result.status+"\n"+logs.join("\n"));
  assert.deepEqual(result.resources,[],"Unexpected external resource access");
  console.log("PASS: real ONNX CPU model initialized from local files in Chromium");
}catch(e){
  console.error("MIDI browser smoke failure:",String(e.stack||e));
  console.error("Browser errors:",logs.slice(-30).join("\n"));
  throw e;
}finally{
  if(browser)await browser.close().catch(()=>{});
  server.close();
}
