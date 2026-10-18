// No static imports: if ONNX or a MIDI module fails in Android WebView, the
// buttons and diagnostics MUST still be available, even on unsupported devices.
const $ = id => document.getElementById(id);
let ort, detect, loadMidigen, newScore, newTrack, writeMidi, mspqOf, parseMidi;
let mg = null, currentMidi = null, audio = null, activeOsc = [], controller = null;
let loadController = null;
let trace = ["FxExtractor 0.3.6 MIDI · initialisation"];
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
status("✅ Interface prête. Appuie sur « Test de l'interface » puis sur « Charger ». Les boutons de génération s'activeront seulement lorsque l'IA est prête.");
$("ping").addEventListener("click", () => status("✅ Commande tactile et JavaScript fonctionnels sur ce téléphone. Le modèle n'est pas encore chargé."));
$("copy").addEventListener("click", () => {
  const report = trace.join("\n");
  if (native?.copyDiagnostic) native.copyDiagnostic(report);
  else status("Copie native indisponible. Diagnostic : " + report);
});
$("cancelLoad").addEventListener("click", () => {
  loadController?.abort();
  status("Chargement arrêté à ta demande. Tu peux réessayer.");
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
$("load").addEventListener("click",async()=>{
  if (mg) { status("✅ Modèle déjà chargé : tu peux générer."); return; }
  $("load").disabled = true;
  $("cancelLoad").disabled = false;
  loadController = new AbortController();
  const signal = loadController.signal;
  let engine = $("engine").value;
  try {
    status("⏳ Étape 1/4 · Ouverture des modules MIDI/ONNX inclus dans l'APK…");
    const modules = await withTimeout(
      Promise.all([
        import("./ort.webgpu.bundle.min.mjs"),
        import("./midigen-web.mjs"),
        import("./tasks.mjs"),
        import("./midi.mjs")
      ]),
      25000,
      "Chargement des modules"
    );
    ort=modules[0]; detect=modules[1].detect; loadMidigen=modules[2].loadMidigen;
    ({newScore,newTrack,writeMidi,mspqOf,parseMidi}=modules[3]);
    if (!ort?.InferenceSession || !loadMidigen)
      throw new Error("Bibliothèque ONNX ou MIDI manquante.");
    status("⏳ Étape 2/4 · Détection du moteur local…");
    // Android WebView may hang in GPU adapter probing: do not block CPU.
    if(engine === "gpu"){
      const device=await withTimeout(detect(),12000,"Détection WebGPU");
      if(!device.webgpu) throw new Error("WebGPU indisponible : sélectionne CPU.");
    }
    if(signal.aborted) throw new Error("Annulé");
    status("⏳ Étape 3/4 · Connexion aux fichiers du modèle (Hugging Face), puis téléchargement. La première fois, le CPU nécessite environ 115 Mo. Cela peut être long en connexion mobile.");
    const started = performance.now();
    let progressAt=Date.now();
    // Short requests (tokenizer/manifest) must fail clearly instead of hanging.
    // The large weight download is allowed to run for several minutes.
    const fetchImpl = async (url,opts={}) => {
      const address=String(url);
      const tiny = /manifest\.json|tokenizer\.json/.test(address);
      if(!tiny) return fetch(url,{...opts,signal});
      const timeout = new AbortController();
      const timer=setTimeout(()=>timeout.abort(),35000);
      const abort=()=>timeout.abort();
      signal.addEventListener("abort",abort,{once:true});
      try {
        return await fetch(url,{...opts,signal:timeout.signal});
      } catch(e) {
        throw new Error("Accès réseau à "+(address.includes("manifest")?"manifest.json":"tokenizer.json")+
          " impossible (connexion, CORS ou délai). "+(e.message||e));
      } finally {
        clearTimeout(timer);
        signal.removeEventListener("abort",abort);
      }
    };
    const watchdog = setInterval(()=>{
      if(!signal.aborted && Date.now()-progressAt > 45000){
        status("⌛ Toujours en attente du modèle. Vérifie ta connexion Internet. Si rien n'avance, appuie sur « Annuler le chargement », puis copie le diagnostic.");
        progressAt=Date.now();
      }
    },10000);
    try {
      mg=await loadMidigen({ort,engine,signal,fetchImpl,onProgress:p=>{
        progressAt=Date.now();
        if(p.phase==="download")status("⏳ Étape 3/4 · Modèle "+engine+" : "+
          (p.loaded/1048576).toFixed(1)+" / "+(p.total/1048576).toFixed(1)+
          " Mio"+(p.cached?" (cache)":" (téléchargement)"));
        else status("⏳ Étape 4/4 · Initialisation des poids et du moteur local…");
      }});
    } finally { clearInterval(watchdog); }
    if(signal.aborted) {mg=null;throw new Error("Annulé");}
    status("✅ IA v5 chargée en "+((performance.now()-started)/1000).toFixed(1)+" s. Bouton « Générer » maintenant actif.");
    $("generate").disabled=false;
    $("load").textContent="✅ Modèle chargé";
  } catch(e) {
    mg=null;
    failure("Chargement du modèle",e);
    $("load").disabled=false;
  } finally {
    $("cancelLoad").disabled=true;
    loadController=null;
  }
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
