import * as ort from "./ort.webgpu.bundle.min.mjs";
import { detect } from "./midigen-web.mjs";
import { loadMidigen } from "./tasks.mjs";
import { newScore, newTrack, writeMidi, mspqOf, parseMidi } from "./midi.mjs";

const $ = id => document.getElementById(id);
let mg = null, currentMidi = null, audio = null, activeOsc = [], controller = null;
const status = txt => { $("status").textContent = txt; };
const native = globalThis.FxNative;
function makeSeed(bpm, mood) {
  // Original two-bar seed; no transcription or quotation of a copyrighted melody.
  const score = newScore(480);
  score.tempos = [{time:0, mspq:mspqOf(bpm)}];
  score.timeSignatures = [{time:0,numerator:4,denominator:4}];
  const lead = newTrack("Retro lead", mood === "calm" ? 10 : 80, false);
  const bass = newTrack("Bass", 38, false);
  const melodies = {
    bouncy:[64,67,72,69,67,64,62,67,69,72,74,69,67,64,62,64],
    calm:[60,64,67,64,62,65,69,65,60,64,67,72,69,67,65,64],
    arcade:[72,76,79,74,81,79,76,72,74,77,81,84,79,76,74,72]
  };
  const melody = melodies[mood] || melodies.bouncy;
  melody.forEach((pitch,i) => lead.notes.push({time:i*240,duration:180,pitch,velocity:78+(i%4===0?14:0)}));
  [0,2,4,6].forEach((beat,i)=>bass.notes.push({time:beat*480,duration:750,pitch:[36,43,41,43][i],velocity:85}));
  score.tracks.push(lead,bass);
  return writeMidi(score);
}
function bytesToB64(bytes) {
  let s = "";
  for(let i=0;i<bytes.length;i+=8192) s+=String.fromCharCode(...bytes.subarray(i,i+8192));
  return btoa(s);
}
$("temperature").addEventListener("input",()=> $("tempvalue").textContent=(+$("temperature").value/100).toFixed(2));
$("load").addEventListener("click",async()=>{
  $("load").disabled=true;
  const engine=$("engine").value;
  try {
    const device=await detect();
    if(engine==="gpu"&&!device.webgpu) throw new Error("WebGPU indisponible : sélectionne CPU.");
    status("Appareil : WebGPU "+(device.webgpu?"oui":"non")+" ; chargement "+engine+"…");
    const began=performance.now();
    mg=await loadMidigen({ort,engine, onProgress:p=>{
      if(p.phase==="download") status("Téléchargement "+engine+" : "+(p.loaded/1048576).toFixed(1)+" / "+(p.total/1048576).toFixed(1)+" Mo"+(p.cached?" (cache)":""));
      else status("Initialisation du modèle local…");
    }});
    status("✅ MIDI Gen AI v5 chargé localement en "+((performance.now()-began)/1000).toFixed(1)+" s. Tu peux générer.");
    $("generate").disabled=false;
    $("load").textContent="Modèle chargé";
  }catch(e){status("Échec de chargement : "+(e.message||String(e))+"\nVérifie connexion, mémoire disponible et moteur choisi.");$("load").disabled=false;}
});
$("generate").addEventListener("click",async()=>{
  if(!mg) return;
  $("generate").disabled=true;$("cancel").disabled=false;
  $("save").disabled=true;$("play").disabled=true;
  controller=new AbortController();
  const bpm=Math.max(70,Math.min(180,Number($("bpm").value)||128));
  const n=Number($("tokens").value);
  const began=performance.now();let count=0;
  try {
    const src=makeSeed(bpm,$("mood").value);
    status("L'IA compose… 0/"+n+" tokens");
    const result=await mg.generate(src,{tempoBpm:bpm,maxNewTokens:n,
      temperature:Number($("temperature").value)/100,
      signal:controller.signal,
      onToken:()=>{count++; if(count%8===0)status("L'IA compose… "+count+"/"+n+" tokens · "+((performance.now()-began)/1000).toFixed(1)+" s");}});
    currentMidi=result.midi;
    const elapsed=((performance.now()-began)/1000).toFixed(1);
    native?.receiveMidi(bytesToB64(currentMidi),"MIDI-AI-v5");
    status("✅ MIDI produit : "+currentMidi.length+" octets · "+count+" tokens · "+elapsed+" s. Modèle exécuté sur ce téléphone.");
    $("save").disabled=false;$("play").disabled=false;
  }catch(e){status("Génération interrompue ou impossible : "+(e.message||String(e)));}
  finally{$("generate").disabled=false;$("cancel").disabled=true;controller=null;}
});
$("cancel").addEventListener("click",()=>controller?.abort());
function stopAudio(){
  for(const o of activeOsc)try{o.stop()}catch(e){}
  activeOsc=[];
  if(audio){audio.close().catch(()=>{});audio=null;}
  $("stop").disabled=true;
}
$("stop").addEventListener("click",stopAudio);
$("play").addEventListener("click",async()=>{
  if(!currentMidi)return;
  stopAudio();
  try {
    const sc=parseMidi(currentMidi);
    audio=new AudioContext();
    await audio.resume();
    const gain=audio.createGain();gain.gain.value=.10;gain.connect(audio.destination);
    const bpm=Math.max(70,Math.min(180,Number($("bpm").value)||128));
    const start=audio.currentTime+.1;let played=0;
    for(const track of sc.tracks)for(const note of track.notes) {
      if(played>=250||note.time/sc.tpq*60/bpm>30)break;
      if(track.isDrum)continue;
      const osc=audio.createOscillator(), env=audio.createGain();
      osc.type=track.program>=80?"square":"triangle";
      osc.frequency.value=440*Math.pow(2,(note.pitch-69)/12);
      const from=start+note.time/sc.tpq*60/bpm;
      const dur=Math.max(.04,note.duration/sc.tpq*60/bpm);
      env.gain.setValueAtTime(.0001,from);
      env.gain.exponentialRampToValueAtTime(Math.max(.01,note.velocity/127*.13),from+.015);
      env.gain.exponentialRampToValueAtTime(.0001,from+dur);
      osc.connect(env);env.connect(gain);
      osc.start(from);osc.stop(from+dur+.02);
      activeOsc.push(osc);played++;
    }
    $("stop").disabled=false;
    status("▶ Lecture de "+played+" notes avec le synthétiseur de démonstration. Export MIDI disponible.");
  }catch(e){status("Lecture impossible : "+(e.message||String(e)));stopAudio();}
});
$("save").addEventListener("click",()=>native?.saveMidi());
window.addEventListener("pagehide",stopAudio);
