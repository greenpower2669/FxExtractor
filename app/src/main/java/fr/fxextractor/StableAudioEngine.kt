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
internal class StableAudioEngine(private val context: Context) {
    data class Music(val left: FloatArray, val right: FloatArray)
    private val S = StableAudioMath.SAMPLES_PER_LATENT
    private val ditRungs = intArrayOf(8,48,96,192,416,704,1056,1292)
    private val codecRungs = intArrayOf(2,4,8,12,16,32,64,128,256)
    private class Graph(val model: CompiledModel,val env: Environment): AutoCloseable {
        override fun close() { try {model.close()} finally {env.close()} }
    }
    private fun model(path:String):Graph {
        val env=Environment.create()
        return try {
            val options=CompiledModel.Options(Accelerator.CPU)
            options.cpuOptions=CompiledModel.CpuOptions(numThreads=3)
            Graph(CompiledModel.create(context.assets,path,options,env),env)
        } catch(e:Throwable) {env.close();throw e}
    }
    private fun close(vararg bs:List<TensorBuffer>) {bs.forEach{list->list.forEach{it.close()}}}
    private fun interrupt() {if(Thread.currentThread().isInterrupted)throw InterruptedException("Annulé")}
    private fun tokenize(prompt:String):Pair<IntArray,IntArray> {
        val ids=IntArray(256);val mask=IntArray(256)
        context.assets.open("sa3/tokenizer.json").use{input->
            HuggingFaceTokenizer.newInstance(input, emptyMap()).use{tok->
                val p=tok.encode(prompt,false,false).ids
                require(p.isNotEmpty()){"Texte impossible à tokeniser"}
                for(i in 0 until min(256,p.size)){
                    require(p[i] in 0..Int.MAX_VALUE.toLong())
                    ids[i]=p[i].toInt();mask[i]=1
                }
            }
        }
        return Pair(ids,mask)
    }
    private fun text(prompt:String,progress:(String)->Unit):Pair<FloatArray,FloatArray> {
        progress("1/4 · T5Gemma : encodage du texte")
        val(ids,mask)=tokenize(prompt)
        val hidden=model("sa3/t5gemma/encoder_fp16.tflite").use{g->
            val i=g.model.createInputBuffers();val o=g.model.createOutputBuffers()
            try {
                check(i.size==2 && o.isNotEmpty())
                i[0].writeInt(ids);i[1].writeInt(mask);g.model.run(i,o)
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
                    g.model.run(inputs,outputs,"s"+w.size)
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
                       progress:(String)->Unit):FloatArray {
        val L=StableAudioMath.latentLength(seconds.toDouble())
        val R=ditRungs.first{it>=L};val sig="s"+R
        val rng=Random(seed)
        val state=FloatArray(256*L){i->
            val n=rng.nextGaussian().toFloat()
            if(init==null)n else (1f-noise)*init[i]+noise*n
        }
        val schedule=StableAudioMath.pingpongSchedule(8,noise)
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
                ins.getValue("args_5").writeFloat(FloatArray(257*R))
                ins.getValue("args_6").writeFloat(FloatArray(64+R){i->if(i>=64+L)-1e9f else 0f})
                val pad=FloatArray(256*R)
                for(k in 0..7){
                    interrupt()
                    val current=schedule[k];val next=schedule[k+1]
                    // gcond: seconds and current diffusion time, official rung backend
                    gi[0].writeFloat(floatArrayOf(seconds))
                    gi[1].writeFloat(floatArrayOf(current))
                    m.run(gi,go,"gcond")
                    val gc=go[0].readFloat()
                    check(gc.size==6144){"DiT Small-Music gcond : "+gc.size}
                    ins.getValue("args_1").writeFloat(gc)
                    java.util.Arrays.fill(pad,0f)
                    for(ch in 0 until 256)System.arraycopy(state,ch*L,pad,ch*R,L)
                    ins.getValue("args_0").writeFloat(pad)
                    m.run(ins,outs,sig)
                    val pv=outs.getValue(oname).readFloat()
                    check(pv.size==256*R){"DiT velocity: "+pv.size}
                    val v=FloatArray(256*L)
                    for(ch in 0 until 256)System.arraycopy(pv,ch*R,v,ch*L,L)
                    val gaussian=if(next==0f)null else FloatArray(state.size){rng.nextGaussian().toFloat()}
                    StableAudioMath.pingpongStep(state,v,gaussian,current,next)
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
                    inp[0].writeFloat(x);g.model.run(inp,out,"s"+w.size)
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
}
