// Real Chromium smoke test of FxExtractor's APK-packaged ONNX Runtime Web CPU pair.
// Unlike node --check, this actually initializes the 109 MiB model with WASM.
import { chromium } from "playwright";
import path from "node:path";
import { existsSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";

const root = path.resolve("app/src/main/assets/midi");
const origin = "https://appassets.androidplatform.net/midi/";
const types = {".html":"text/html",".mjs":"text/javascript",".js":"text/javascript",
  ".wasm":"application/wasm",".onnx":"application/octet-stream",".json":"application/json"};
const log=[];
const browser=await chromium.launch({headless:true,args:["--no-sandbox","--disable-dev-shm-usage"]});
try {
  const context=await browser.newContext({serviceWorkers:"block"});
  const page=await context.newPage();
  page.on("console", msg=>{if(msg.type()==="error")log.push("console: "+msg.text());});
  page.on("pageerror",e=>log.push("pageerror: "+String(e.stack||e)));
  await page.route(origin+"**", async route=>{
    const url=new URL(route.request().url());
    const relative=decodeURIComponent(url.pathname.substring("/midi/".length));
    const file=path.resolve(root,relative);
    if(!file.startsWith(root+path.sep)||!existsSync(file)||!statSync(file).isFile()){
      log.push("Missing asset "+relative);
      return route.fulfill({status:404,body:"not found"});
    }
    const contentType=types[path.extname(file)] || "application/octet-stream";
    await route.fulfill({status:200,path:file,contentType});
  });
  await page.goto(origin+"index.html",{waitUntil:"domcontentloaded",timeout:30000});
  await page.waitForFunction(()=>{
    const state=document.getElementById("loadStatus")?.textContent||"";
    const generate=document.getElementById("generate");
    return state.includes("❌") || (generate && generate.disabled===false);
  },null,{timeout:180000});
  const result=await page.evaluate(()=>({
    status:document.getElementById("loadStatus").textContent,
    ready:!document.getElementById("generate").disabled,
    internet:performance.getEntriesByType("resource").filter(r=>!r.name.startsWith("https://appassets.androidplatform.net/midi/")).map(r=>r.name)
  }));
  console.log("MIDI CPU browser smoke:",JSON.stringify(result));
  assert.equal(result.ready,true,"Real ONNX CPU model initialization failed: "+result.status+"\n"+log.join("\n"));
  assert.deepEqual(result.internet,[],"Unexpected off-app network access");
  console.log("PASS MIDI: model, tokenizer, WASM and JS runtime all initialize in Chromium fully offline");
}finally{
  await browser.close();
}
