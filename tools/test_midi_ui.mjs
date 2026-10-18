// FxExtractor regression test: a failed module import must never silently lock UI.
import { readFileSync } from "node:fs";
import vm from "node:vm";
import assert from "node:assert/strict";

const listeners=new Map();
const nodes=new Map();
const getElementById=id=>{
  if(!nodes.has(id))nodes.set(id,{textContent:"",disabled:false,value:"cpu",addEventListener(event,cb){listeners.set(id+":"+event,cb)}});
  return nodes.get(id);
};
const context={
  document:{getElementById},
  window:{addEventListener(){},__fxMidiReady:false},
  AbortController,Date,Promise,Math,Number,String,Array,Uint8Array,Error,Set,
  performance:{now:()=>0},
  setTimeout,
  setInterval,
  clearInterval,
  clearTimeout,
  globalThis:{FxNative:{trace(){}}},
};
const script=readFileSync("app/src/main/assets/midi/main.mjs","utf8");
vm.runInNewContext(script,context,{filename:"main.mjs"});
assert.equal(context.window.__fxMidiReady,true,"UI bootstrap");
for(const id of ["load","ping","copy","cancelLoad","generate","play","save"])
 assert.equal(typeof listeners.get(id+":click"),"function",id+" click handler missing");
listeners.get("ping:click")();
assert.match(getElementById("loadStatus").textContent,/fonctionnels/);
const attempt=listeners.get("load:click")();
assert.match(getElementById("loadStatus").textContent,/Étape 1\/4/,"status must show immediately");
await attempt; // isolated Node VM cannot perform a dynamic module import: intentional failure.
assert.match(getElementById("loadStatus").textContent,/Chargement du modèle/,"error must be visible");
assert.equal(getElementById("load").disabled,false,"retry must be possible");
assert.equal(getElementById("cancelLoad").disabled,true);
console.log("PASS MIDI UI: immediate feedback, module failure visible, retry enabled");
