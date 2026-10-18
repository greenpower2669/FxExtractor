// No static imports: if ONNX or a MIDI module fails in Android WebView, the
// buttons and diagnostics MUST still be available, even on unsupported devices.
const $ = id => document.getElementById(id);
let ort, detect, loadMidigen, newScore, newTrack, writeMidi, mspqOf, parseMidi;
let mg = null, currentMidi = null, audio = null, activeOsc = [], controller = null;
let loadController = null;
let trace = ["FxExtractor 0.3.7 MIDI hors ligne · initialisation"];
const ASSET_ROOT = "https://appassets.androidplatform.net/midi/";
let startedLoading = false;
const native = globalThis.FxNative;
const status = txt => {
  const message = String(txt);
  $("status").textContent = message;
  $("loadStatus").textContent = message;
  trace.push(new Date().toISOString() + " " + message);
  if (trace.length > 50) trace.shift();
  try { native?.trace?.(message); } catch (_) {}
};
const failure = (phase,e) => status("❌ " + phase + " : " + (e?.message || String(e)) +
  "\nTu peux réessayer, ou copier le diagnostic ci-dessous.");
window.addEventListener("error", e => failure("Erreur JavaScript", e.error || e.message));
window.addEventListener("unhandledrejection", e => failure("Promesse JavaScript", e.reason));
window.__fxMidiReady = true;
status("✅ IA MIDI intégrée dans cette application. Initialisation en mémoire au démarrage du laboratoire…");
$("ping").addEventListener("click", () => status("✅ Commande tactile et JavaScript fonctionnels sur ce téléphone. Le modèle intégré n’est pas encore initialisé en mémoire."));
$("copy").addEventListener("click", () => {
  const report = trace.join("\n");
  if (native?.copyDiagnostic) native.copyDiagnostic(report);
  else status("Copie native indisponible. Diagnostic : " + report);
});
$("cancelLoad").addEventListener("click", () => {
  loadController?.abort();
  status("Initialisation en mémoire interrompue. Tu peux réessayer.");
  startedLoading = false;
  $("load").disabled = false;
  $("cancelLoad").disabled = true;
});
const withTimeout = (promise, millis, description) => Promise.race([
  promise,
  new Promise((_,reject)=>setTimeout(()=>reject(new Error(description+" : délai dépassé ("+Math.round(millis/1000)+" s)")),millis))
]);
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
async function initializeInMemory(){
  if (mg) { status("✅ L'IA est déjà en mémoire. Tu peux générer."); return; }
  if (startedLoading) return;
  startedLoading = true;
  $("load").disabled = true;
  $("cancelLoad").disabled = false;
  loadController = new AbortController();
  const signal = loadController.signal;
  try {
    status("⏳ Étape 1/4 · Chargement en mémoire des modules MIDI/ONNX déjà présents dans l'APK…");
    const modules = await withTimeout(Promise.all([
      import("./ort.webgpu.bundle.min.mjs"),
      import("./midigen-web.mjs"),
      import("./tasks.mjs"),
      import("./midi.mjs")
    ]), 25000, "Ouverture des modules intégrés");
    ort = modules[0]; detect = modules[1].detect; loadMidigen = modules[2].loadMidigen;
    ({newScore,newTrack,writeMidi,mspqOf,parseMidi} = modules[3]);
    if (!ort?.InferenceSession || !loadMidigen) throw new Error("Module ONNX ou MIDI manquant dans l'APK.");
    if (signal.aborted) throw new Error("Annulé");

    status("⏳ Étape 2/4 · Vérification du moteur WASM intégré dans l'APK…");
    const wasmUrl = ASSET_ROOT + "ort-wasm-simd-threaded.jsep.wasm";
    const wasmResponse = await withTimeout(fetch(wasmUrl, {signal, cache:"no-store"}), 25000, "Lecture WASM");
    if (!wasmResponse.ok) throw new Error("WASM absent de l'APK (HTTP "+wasmResponse.status+").");
    const wasmBytes = new Uint8Array(await wasmResponse.arrayBuffer());
    if (wasmBytes.length < 1_000_000 || wasmBytes[0] !== 0 ||
        wasmBytes[1] !== 97 || wasmBytes[2] !== 115 || wasmBytes[3] !== 109)
      throw new Error("Le WASM intégré est incomplet ou invalide ("+wasmBytes.length+" octets).");
    // The exact runtime binary and JS are packaged together; avoid Emscripten's
    // broken default URL resolution inside the Android asset-backed WebView.
    ort.env.wasm.numThreads = 1;
    ort.env.wasm.wasmPaths = {wasm: wasmUrl};
    ort.env.wasm.wasmBinary = wasmBytes;
    status("✅ Moteur WASM présent : "+(wasmBytes.length/1048576).toFixed(1)+" Mio. Aucun accès Internet nécessaire.");

    status("⏳ Étape 3/4 · Lecture du modèle MIDI CPU présent dans l'APK (environ 109 Mio). Pas de téléchargement.");
    const started = performance.now();
    let lastMessageAt=0;
    const modelHub = ASSET_ROOT.replace(/\/$/, "");
    const localFetch = async (url, options={})=>{
      const requested = String(url);
      if(!requested.startsWith(ASSET_ROOT)) throw new Error("Modèle externe refusé : "+requested);
      const res = await fetch(requested, {...options,signal});
      if(!res.ok) throw new Error("Fichier manquant dans l'APK : "+new URL(requested).pathname+" (HTTP "+res.status+")");
      return res;
    };
    mg = await loadMidigen({
      ort, engine:"cpu", hub:modelHub, version:"v5",
      signal, fetchImpl:localFetch, cache:false,
      onProgress:p=>{
        const now=Date.now();
        if(p.phase==="download"){
          if(now-lastMessageAt>600 || p.loaded===p.total){
            status("⏳ Étape 3/4 · Lecture depuis l'APK : "+(p.loaded/1048576).toFixed(1)+
              " / "+(p.total/1048576).toFixed(1)+" Mio (sans Internet)");
            lastMessageAt=now;
          }
        } else status("⏳ Étape 4/4 · Initialisation du modèle en mémoire, sur le processeur…");
      }
    });
    if(signal.aborted) {mg=null;throw new Error("Annulé");}
    status("✅ IA MIDI prête en mémoire ("+((performance.now()-started)/1000).toFixed(1)+
      " s). Tu peux composer. Aucun fichier téléchargé depuis Internet.");
    $("generate").disabled = false;
    $("load").textContent = "✅ Modèle chargé en mémoire";
  } catch(e) {
    mg=null;
    failure("Initialisation en mémoire",e);
    $("load").disabled=false;
    $("load").textContent="🔄 Réessayer le chargement en mémoire";
  } finally {
    startedLoading=false;
    $("cancelLoad").disabled=true;
    loadController=null;
  }
}
$("load").addEventListener("click",initializeInMemory);
// Lazy at laboratory opening: do not allocate 100+ MiB on FxExtractor's
// main screen. Once the user opens the MIDI screen, initialize automatically.
setTimeout(initializeInMemory, 150);
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
