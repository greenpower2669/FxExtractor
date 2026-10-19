package fr.fxextractor

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import java.util.Random
import kotlin.math.min
import kotlin.math.sqrt

/**
 * True SA3 CPU inference pipeline, ported from Stability-AI/stable-audio-3
 * optimized/tflite/scripts/sa3_tflite.py + rung_{dit,encoder,decoder}.py.
 * No mock music. Models/tokenizer must be real and licensed.
 * NOTE: unverified until HF tokenizer parity, model signatures and Android inference pass.
 */
internal class StableAudioEngine @JvmOverloads constructor(
    private val context: Context,
    private val cacheMode: Int = Sa3CacheSettings.OFF
) {
    data class Music(val left: FloatArray, val right: FloatArray)
    private val S = StableAudioMath.SAMPLES_PER_LATENT
    private val ditRungs = intArrayOf(8,48,96,192,416,704,1056,1292)
    private val codecRungs = intArrayOf(2,4,8,12,16,32,64,128,256)
    private class Graph(val model: CompiledModel,val env: Environment): AutoCloseable {
        override fun close() { try {model.close()} finally {env.close()} }
    }
    private fun model(path:String):Graph {
        // Only DiT uses an experimental XNNPACK weight cache; T5Gemma/SAME-S unchanged.
        // Never pretend a cache saves RAM unless PSS measurements on the phone prove it.
        val isDiT=path=="sa3/sa3-sm-music/dit_w8a8.tflite"
        val cachePath=if(isDiT)Sa3CacheSettings.cachePath(context,cacheMode) else null
        FxDebugLog.checkpoint("LiteRT.load.before "+path+
            (if(isDiT)" cache="+Sa3CacheSettings.label(cacheMode) else ""))
        val env=Environment.create()
        return try {
            val options=CompiledModel.Options(Accelerator.CPU)
            options.cpuOptions=CompiledModel.CpuOptions(
                numThreads=3, xnnPackWeightCachePath=cachePath)
            val compiled=CompiledModel.create(context.assets,path,options,env)
            FxDebugLog.checkpoint("LiteRT.load.after "+path)
            if(isDiT)Sa3CacheSettings.logAfterLoad(cachePath)
            Graph(compiled,env)
        } catch(e:Throwable) {
            FxDebugLog.write("SA3_LITERT_OPEN_ERROR cache="+(if(isDiT)Sa3CacheSettings.label(cacheMode) else "off")+
                " type="+e.javaClass.simpleName)
            env.close();throw e
        }
    }
    private fun close(vararg bs:List<TensorBuffer>) {bs.forEach{list->list.forEach{it.close()}}}
    private fun interrupt() {if(Thread.currentThread().isInterrupted)throw InterruptedException("Annulé")}
    private fun tokenize(prompt:String):Pair<IntArray,IntArray> {
        FxDebugLog.checkpoint("tokenizer.open.before")
        val ids=IntArray(256);val mask=IntArray(256)
        context.assets.open("sa3/tokenizer.json").use{input->
            HuggingFaceTokenizer.newInstance(input, emptyMap()).use{tok->
                FxDebugLog.checkpoint("tokenizer.encode.before")
                val p=tok.encode(prompt,false,false).ids
                FxDebugLog.checkpoint("tokenizer.encode.after tokens="+p.size)
                require(p.isNotEmpty()){"Texte impossible à tokeniser"}
                for(i in 0 until min(256,p.size)){
                    require(p[i] in 0..Int.MAX_VALUE.toLong())
                    ids[i]=p[i].toInt();mask[i]=1
                }
            }
        }
        FxDebugLog.checkpoint("tokenizer.close.after")
        return Pair(ids,mask)
    }
    private fun text(prompt:String,progress:(String)->Unit):Pair<FloatArray,FloatArray> {
        progress("1/4 · T5Gemma : encodage du texte")
        val(ids,mask)=tokenize(prompt)
        val hidden=model("sa3/t5gemma/encoder_fp16.tflite").use{g->
            val i=g.model.createInputBuffers();val o=g.model.createOutputBuffers()
            try {
                check(i.size==2 && o.isNotEmpty())
                i[0].writeInt(ids);i[1].writeInt(mask)
                FxDebugLog.checkpoint("T5Gemma.run.before")
                g.model.run(i,o)
                FxDebugLog.checkpoint("T5Gemma.run.after")
                o[0].readFloat().also {check(it.size==256*768){"T5Gemma: dimension incorrecte"}}
            }finally{close(i,o)}
        }
        return Pair(hidden,FloatArray(256){mask[it].toFloat()})
    }
    private data class Win(val start:Int,val size:Int,val low:Int,val high:Int)
    private fun windows(length:Int,trim:Int=16):List<Win>{
        if(length<=64){
            val rung=codecRungs.first{it>=length}
            return listOf(Win(0,rung,0,length))
        }
        val size=64;val step=size-2*trim;val starts=mutableListOf<Int>()
        var st=0
        while(st<length-size){starts+=st;st+=step}
        if(starts.lastOrNull()!=length-size)starts+=length-size
        return starts.mapIndexed {idx,s->Win(s,size,if(idx==0)0 else trim,if(s+size>=length)size else size-trim)}
    }
    private fun encodeSeed(seed:Pair<FloatArray,FloatArray>,L:Int,progress:(String)->Unit):FloatArray {
        progress("2/4 · SAME-S : lecture et encodage de l'amorce")
        val even=L+(L%2)
        val pcm=arrayOf(FloatArray(even*S),FloatArray(even*S))
        for(ch in 0..1){val src=if(ch==0)seed.first else seed.second
            System.arraycopy(src,0,pcm[ch],0,min(src.size,pcm[ch].size))}
        val result=FloatArray(256*L)
        model("sa3/same-s/enc_w8a8.tflite").use{g->
            for(w in windows(even)){
                interrupt()
                val inputs=g.model.createInputBuffers("s"+w.size)
                val outputs=g.model.createOutputBuffers("s"+w.size)
                try {
                    val data=FloatArray(2*w.size*S);val block=w.size*S
                    for(ch in 0..1)for(i in 0 until block){
                        data[ch*block+i]=pcm[ch][min(pcm[ch].size-1,w.start*S+i)]
                    }
                    inputs[0].writeFloat(data)
                    FxDebugLog.checkpoint("SAME-S.encoder.run.before size="+w.size)
                    g.model.run(inputs,outputs,"s"+w.size)
                    FxDebugLog.checkpoint("SAME-S.encoder.run.after size="+w.size)
                    val lat=outputs[0].readFloat()
                    check(lat.size==256*w.size){"SA3 SAME-S encodeur: dimensions incorrectes"}
                    for(ch in 0 until 256)for(i in w.low until w.high){
                        val dst=w.start+i
                        if(dst<L)result[ch*L+dst]=lat[ch*w.size+i]
                    }
                }finally{close(inputs,outputs)}
            }
        }
        return result
    }
    private fun outputName(m:CompiledModel,signature:String):String {
        for(name in arrayOf("output_0","output","Identity","args_0")) {
            try {m.getOutputTensorType(name,signature);return name}catch(_:Exception){}
        }
        throw IllegalStateException("Signature "+signature+" : sortie inconnue")
    }
    private fun sample(hidden:FloatArray,mask:FloatArray,seconds:Float,
                       init:FloatArray?,noise:Float,seed:Long,
                       progress:(String)->Unit,
                       preservePrefix:Int=0):FloatArray {
        val L=StableAudioMath.latentLength(seconds.toDouble())
        val R=ditRungs.first{it>=L};val sig="s"+R
        val rng=Random(seed)
        val state=FloatArray(256*L){i->
            val n=rng.nextGaussian().toFloat()
            if(init==null)n else (1f-noise)*init[i]+noise*n
        }
        val schedule=StableAudioMath.pingpongSchedule(8,noise)
        require(preservePrefix in 0..L && (preservePrefix==0 || init!=null))
        // SA3 inpainting conditioning: 1 channel keep_mask plus 256 channels masked latents.
        // The original audio is reinserted losslessly in PCM later; masked latents keep
        // model context fixed while only the new tail is generated.
        val masked=FloatArray(257*R)
        if(preservePrefix>0) {
            for(t in 0 until preservePrefix) masked[t]=1f
            for(ch in 0 until 256) for(t in 0 until preservePrefix)
                masked[(ch+1)*R+t]=init!![ch*L+t]
        }
        model("sa3/sa3-sm-music/dit_w8a8.tflite").use{g->
            val m=g.model;val oname=outputName(m,sig)
            val ins=mutableMapOf<String,TensorBuffer>()
            val outs=mutableMapOf<String,TensorBuffer>()
            val gi=m.createInputBuffers("gcond")
            val go=m.createOutputBuffers("gcond")
            try{
                for(n in 0..6)ins["args_"+n]=m.createInputBuffer("args_"+n,sig)
                outs[oname]=m.createOutputBuffer(oname,sig)
                ins.getValue("args_2").writeFloat(hidden)
                ins.getValue("args_3").writeFloat(mask)
                ins.getValue("args_4").writeFloat(floatArrayOf(seconds))
                ins.getValue("args_5").writeFloat(masked)
                ins.getValue("args_6").writeFloat(FloatArray(64+R){i->if(i>=64+L)-1e9f else 0f})
                val pad=FloatArray(256*R)
                for(k in 0..7){
                    interrupt()
                    val current=schedule[k];val next=schedule[k+1]
                    // gcond: seconds and current diffusion time, official rung backend
                    gi[0].writeFloat(floatArrayOf(seconds))
                    gi[1].writeFloat(floatArrayOf(current))
                    FxDebugLog.checkpoint("DiT.gcond.run.before step="+k)
                    m.run(gi,go,"gcond")
                    FxDebugLog.checkpoint("DiT.gcond.run.after step="+k)
                    val gc=go[0].readFloat()
                    check(gc.size==6144){"DiT Small-Music gcond : "+gc.size}
                    ins.getValue("args_1").writeFloat(gc)
                    java.util.Arrays.fill(pad,0f)
                    for(ch in 0 until 256)System.arraycopy(state,ch*L,pad,ch*R,L)
                    ins.getValue("args_0").writeFloat(pad)
                    FxDebugLog.checkpoint("DiT.run.before step="+k+" rung="+sig)
                    m.run(ins,outs,sig)
                    FxDebugLog.checkpoint("DiT.run.after step="+k)
                    val pv=outs.getValue(oname).readFloat()
                    check(pv.size==256*R){"DiT velocity: "+pv.size}
                    val v=FloatArray(256*L)
                    for(ch in 0 until 256)System.arraycopy(pv,ch*R,v,ch*L,L)
                    val gaussian=if(next==0f)null else FloatArray(state.size){rng.nextGaussian().toFloat()}
                    StableAudioMath.pingpongStep(state,v,gaussian,current,next)
                    if(preservePrefix>0) for(ch in 0 until 256)
                        System.arraycopy(init!!,ch*L,state,ch*L,preservePrefix)
                    progress("3/4 · Synthèse IA étape "+(k+1)+"/8")
                }
            }finally{close(ins.values.toList(),outs.values.toList(),gi,go)}
        }
        return state
    }
    private fun decode(latents:FloatArray,L:Int,seconds:Float,progress:(String)->Unit):Music {
        progress("4/4 · SAME-S : décodage audio")
        val even=L+(L%2)
        val padded=FloatArray(256*even)
        for(ch in 0 until 256){
            System.arraycopy(latents,ch*L,padded,ch*even,L)
            if(even>L)padded[ch*even+L]=latents[ch*L+L-1]
        }
        val n=Math.round(seconds*StableAudioMath.SAMPLE_RATE)
        val pcm=arrayOf(FloatArray(n),FloatArray(n))
        model("sa3/same-s/dec_w8a8.tflite").use{g->
            val wins=windows(even)
            for((k,w) in wins.withIndex()){
                interrupt()
                val inp=g.model.createInputBuffers("s"+w.size)
                val out=g.model.createOutputBuffers("s"+w.size)
                try {
                    val x=FloatArray(256*w.size)
                    for(ch in 0 until 256)
                        System.arraycopy(padded,ch*even+w.start,x,ch*w.size,w.size)
                    inp[0].writeFloat(x)
                    FxDebugLog.checkpoint("SAME-S.decoder.run.before size="+w.size)
                    g.model.run(inp,out,"s"+w.size)
                    FxDebugLog.checkpoint("SAME-S.decoder.run.after size="+w.size)
                    val audio=out[0].readFloat();val block=w.size*S
                    check(audio.size==2*block){"Décodeur : dimensions invalides"}
                    for(ch in 0..1){
                        val start=(w.start+w.low)*S
                        val amount=min((w.high-w.low)*S,n-start)
                        if(amount>0)System.arraycopy(audio,ch*block+w.low*S,pcm[ch],start,amount)
                    }
                }finally{close(inp,out)}
                progress("4/4 · Décodage WAV "+(k+1)+"/"+wins.size)
            }
        }
        var power=0.0
        for(ch in 0..1)for(x in pcm[ch]){
            check(x.isFinite()){"Échantillon non fini"}
            power+=x.toDouble()*x.toDouble()
        }
        check(sqrt(power/(2.0*n))>1e-6){"Le réseau a renvoyé du silence"}
        return Music(pcm[0],pcm[1])
    }
    /**
     * Audio outpainting, not an audio crossfade trick:
     * SAME-S encodes the original prefix; DiT preserves its latent channels at
     * every diffusion step and generates the appended region only.
     * Original PCM is copied sample-perfect, then 30ms of new audio is
     * smoothed from the original endpoint without modifying the original prefix.
     *
     * Requires existing input up to 60s; supports 1..60s EXTRA (<=120s total).
     */
    fun extend(prompt:String,original:Pair<FloatArray,FloatArray>,
               originalSeconds:Float,extraSeconds:Float,seed:Long,
               progress:(String)->Unit):Music{
        require(prompt.isNotBlank() && prompt.length<=1200)
        require(originalSeconds in 0.5f..60f && extraSeconds in 1f..60f)
        val seconds=originalSeconds+extraSeconds
        val L=StableAudioMath.latentLength(seconds.toDouble())
        val keep=StableAudioMath.latentLength(originalSeconds.toDouble())
        require(keep<L)
        val(hidden,mask)=text(prompt,progress)
        interrupt()
        // Encode the full timeline with zero padding after the preserved sound,
        // exactly as upstream inpainting uses local_add_cond.
        val init=encodeSeed(original,L,progress)
        interrupt()
        val x=sample(hidden,mask,seconds,init,1f,seed,progress,keep)
        interrupt()
        val generated=decode(x,L,seconds,progress)
        val originalFrames=Math.round(originalSeconds*StableAudioMath.SAMPLE_RATE)
        val fullFrames=Math.round(seconds*StableAudioMath.SAMPLE_RATE)
        require(originalFrames>0 && originalFrames<fullFrames)
        val left=FloatArray(fullFrames);val right=FloatArray(fullFrames)
        val rawLeft=original.first;val rawRight=original.second
        require(rawLeft.size>=originalFrames && rawRight.size>=originalFrames)
        System.arraycopy(rawLeft,0,left,0,originalFrames)
        System.arraycopy(rawRight,0,right,0,originalFrames)
        System.arraycopy(generated.left,originalFrames,left,originalFrames,fullFrames-originalFrames)
        System.arraycopy(generated.right,originalFrames,right,originalFrames,fullFrames-originalFrames)
        // Keep every original sample bit-for-bit. Only adjust generated tail.
        val transition=min(fullFrames-originalFrames,1323) // 30ms
        if(transition>0){
            val endL=rawLeft[originalFrames-1];val endR=rawRight[originalFrames-1]
            for(i in 0 until transition){
                val w=(i+1).toFloat()/(transition+1)
                left[originalFrames+i]=endL*(1f-w)+left[originalFrames+i]*w
                right[originalFrames+i]=endR*(1f-w)+right[originalFrames+i]*w
            }
        }
        return Music(left,right)
    }
    fun render(prompt:String,seconds:Float,seed:Long,
               audioSeed:Pair<FloatArray,FloatArray>?,variation:Float,
               progress:(String)->Unit):Music{
        require(prompt.isNotBlank()&&prompt.length<=1200)
        require(seconds in 1f..120f && variation in .2f..1f)
        val L=StableAudioMath.latentLength(seconds.toDouble())
        val(hidden,mask)=text(prompt,progress)
        interrupt()
        val init=if(audioSeed==null)null else encodeSeed(audioSeed,L,progress)
        interrupt()
        val strength=if(init==null)1f else variation
        val lat=sample(hidden,mask,seconds,init,strength,seed,progress)
        interrupt()
        return decode(lat,L,seconds,progress)
    }
    /** Separate process entry points: no model is retained between these calls. */
    fun encodeText(prompt:String, progress:(String)->Unit):Pair<FloatArray,FloatArray> =
        text(prompt,progress)

    fun generateLatents(hidden:FloatArray,mask:FloatArray,seconds:Float,
                        seedAudio:Pair<FloatArray,FloatArray>?,strength:Float,
                        seed:Long,originalSeconds:Float,progress:(String)->Unit):FloatArray {
        require(seconds in 1f..120f)
        require(hidden.size==256*768 && mask.size==256)
        val L=StableAudioMath.latentLength(seconds.toDouble())
        val init=if(seedAudio==null)null else encodeSeed(seedAudio,L,progress)
        val keep=if(originalSeconds>0f) {
            require(init!=null && originalSeconds in .5f..60f && originalSeconds<seconds)
            StableAudioMath.latentLength(originalSeconds.toDouble())
        } else 0
        val noise=if(init==null || keep>0)1f else strength
        return sample(hidden,mask,seconds,init,noise,seed,progress,keep)
    }

    fun decodeLatents(latents:FloatArray,seconds:Float,
                      original:Pair<FloatArray,FloatArray>?,originalSeconds:Float,
                      progress:(String)->Unit):Music {
        val L=StableAudioMath.latentLength(seconds.toDouble())
        require(latents.size==256*L){"Latents incompatibles avec la durée"}
        val generated=decode(latents,L,seconds,progress)
        if(originalSeconds<=0f)return generated
        require(original!=null && originalSeconds<seconds)
        val originalFrames=Math.round(originalSeconds*StableAudioMath.SAMPLE_RATE)
        val frames=generated.left.size
        require(originalFrames in 1 until frames)
        require(original.first.size>=originalFrames && original.second.size>=originalFrames)
        val left=generated.left;val right=generated.right
        System.arraycopy(original.first,0,left,0,originalFrames)
        System.arraycopy(original.second,0,right,0,originalFrames)
        val smoothing=min(frames-originalFrames,1323)
        for(i in 0 until smoothing){
            val w=(i+1).toFloat()/(smoothing+1)
            left[originalFrames+i]=original.first[originalFrames-1]*(1-w)+left[originalFrames+i]*w
            right[originalFrames+i]=original.second[originalFrames-1]*(1-w)+right[originalFrames+i]*w
        }
        return Music(left,right)
    }

    /** Diagnostics: measure true DiT compile cost without loading Gemma or SAME-S. */
    fun probeDiT(seconds:Float):String {
        val L=StableAudioMath.latentLength(seconds.toDouble())
        val R=ditRungs.first{it>=L}
        FxDebugLog.checkpoint("DiT.only.before rung=s"+R)
        model("sa3/sa3-sm-music/dit_w8a8.tflite").use { g ->
            val output=outputName(g.model,"s"+R)
            FxDebugLog.checkpoint("DiT.only.loaded rung=s"+R+" output="+output)
        }
        FxDebugLog.checkpoint("DiT.only.closed")
        return "DiT chargé et fermé indépendamment (s"+R+")"
    }

}
