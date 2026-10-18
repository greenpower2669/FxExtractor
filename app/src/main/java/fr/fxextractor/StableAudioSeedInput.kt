package fr.fxextractor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Decode a user-picked sound locally into 44.1kHz stereo planar PCM for SAME-S.
 * Android MediaCodec handles AAC/MP3/OGG/etc; basic PCM16 RIFF WAV is supported
 * without a codec. No network or cloud upload of private sound. */
internal object StableAudioSeedInput {
    fun load(context: Context, uri: Uri, maxSeconds: Float): Pair<FloatArray,FloatArray> {
        require(maxSeconds in 1f..120f)
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Fichier inaccessible")
        source.use { ins ->
            val signature=ByteArray(12)
            if(ins.read(signature)==12 && String(signature,0,4)== "RIFF" &&
                String(signature,8,4)=="WAVE")return readRiff(context,uri,maxSeconds)
        }
        return decodeMediaCodec(context,uri,maxSeconds)
    }

    private fun le16(a:ByteArray,pos:Int):Int=(a[pos].toInt() and 255) or ((a[pos+1].toInt() and 255) shl 8)
    private fun le32(a:ByteArray,pos:Int):Int=le16(a,pos) or (le16(a,pos+2) shl 16)

    private fun readRiff(context:Context,uri:Uri,maxSeconds:Float):Pair<FloatArray,FloatArray> {
        context.contentResolver.openInputStream(uri)!!.buffered().use { input ->
            val head=ByteArray(12);readFully(input,head)
            var rate=0;var channels=0;var depth=0;var format=0
            var result:Pair<FloatArray,FloatArray>?=null
            while(true){
                val header=ByteArray(8)
                if(input.read(header)!=8)break
                val tag=String(header,0,4,Charsets.US_ASCII)
                val count=le32(header,4).toLong() and 0xffffffffL
                require(count<=100_000_000L){"Fragment WAV trop grand"}
                if(tag=="fmt "){
                    require(count>=16&&count<=128){"En-tête WAV invalide"}
                    val fmt=ByteArray(count.toInt());readFully(input,fmt)
                    format=le16(fmt,0);channels=le16(fmt,2);rate=le32(fmt,4);depth=le16(fmt,14)
                }else if(tag=="data"){
                    require(format==1 && depth==16 && channels in 1..2 && rate in 8000..192000){
                        "Amorce WAV attendue : PCM16, mono/stéréo ; sinon MP3/M4A/OGG"
                    }
                    val limit=min(count.toInt()/(channels*2),
                                  max(1,(rate*maxSeconds).toInt()))
                    val pcm=arrayOf(FloatArray(limit),FloatArray(limit))
                    val buffer=ByteArray(4)
                    for(i in 0 until limit){
                        val n=channels*2
                        var off=0
                        while(off<n){
                            val got=input.read(buffer,off,n-off)
                            if(got<0)throw IllegalArgumentException("WAV tronqué")
                            off+=got
                        }
                        val l=le16(buffer,0).toShort()/32768f
                        val r=if(channels==1)l else le16(buffer,2).toShort()/32768f
                        pcm[0][i]=l;pcm[1][i]=r
                    }
                    result=resample(pcm[0],pcm[1],limit,rate,maxSeconds)
                    break
                }else skipExactly(input,count)
                if((count and 1L)==1L)skipExactly(input,1)
            }
            return result ?: throw IllegalArgumentException("Aucune piste PCM dans le WAV")
        }
    }

    private fun readFully(stream:java.io.InputStream,bytes:ByteArray){
        var p=0
        while(p<bytes.size){val n=stream.read(bytes,p,bytes.size-p)
            if(n<0)throw IllegalArgumentException("Fichier WAV tronqué")
            p+=n}
    }
    private fun skipExactly(stream:java.io.InputStream,n:Long){
        var remaining=n
        while(remaining>0){
            val k=stream.skip(remaining)
            if(k==0L){if(stream.read()==-1)throw IllegalArgumentException("Fin du fichier");remaining--}
            else remaining-=k
        }
    }

    private fun decodeMediaCodec(context:Context,uri:Uri,maxSeconds:Float):Pair<FloatArray,FloatArray> {
        val extractor=MediaExtractor()
        val descriptor=context.contentResolver.openFileDescriptor(uri,"r")
            ?:throw IllegalArgumentException("Accès au fichier refusé")
        var codec:MediaCodec?=null
        try {
            extractor.setDataSource(descriptor.fileDescriptor)
            var track=-1
            for(i in 0 until extractor.trackCount){
                if(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true){
                    track=i;break}
            }
            require(track>=0){"Aucune piste audio dans le fichier"}
            extractor.selectTrack(track)
            val fmt=extractor.getTrackFormat(track)
            val mime=fmt.getString(MediaFormat.KEY_MIME)
                ?:throw IllegalArgumentException("Format inconnu")
            require(mime!="audio/raw"){"Utilise le WAV PCM16 pour ce fichier"}
            codec=MediaCodec.createDecoderByType(mime)
            codec.configure(fmt,null,null,0);codec.start()
            var rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding=AudioFormat.ENCODING_PCM_16BIT
            require(channels in 1..2 && rate in 8000..192000)
            var l=FloatArray(min((rate*maxSeconds).toInt()+8192,rate*121))
            var r=FloatArray(l.size)
            var frames=0;var endInput=false;var endOutput=false;var rounds=0
            val info=MediaCodec.BufferInfo()
            while(!endOutput&&frames < (rate*maxSeconds).toInt() && rounds++<100000){
                if(!endInput){
                    val id=codec.dequeueInputBuffer(10000)
                    if(id>=0){
                        val buf=codec.getInputBuffer(id) ?:throw IllegalStateException("Entrée audio absente")
                        val bytes=extractor.readSampleData(buf,0)
                        if(bytes<0){
                            codec.queueInputBuffer(id,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            endInput=true
                        }else{
                            codec.queueInputBuffer(id,0,bytes,extractor.sampleTime,0)
                            extractor.advance()
                        }
                    }
                }
                val idx=codec.dequeueOutputBuffer(info,10000)
                when(idx){
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{
                        val out=codec.outputFormat
                        rate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding=if(out.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        require(channels in 1..2 && rate in 8000..192000)
                        require(pcmEncoding==AudioFormat.ENCODING_PCM_16BIT ||
                                pcmEncoding==AudioFormat.ENCODING_PCM_FLOAT){
                            "PCM décodé non pris en charge"
                        }
                    }
                    in 0..Int.MAX_VALUE -> {
                        val data=codec.getOutputBuffer(idx)
                            ?:throw IllegalStateException("Sortie audio absente")
                        data.order(ByteOrder.LITTLE_ENDIAN)
                        data.position(info.offset);data.limit(info.offset+info.size)
                        val bytes=if(pcmEncoding==AudioFormat.ENCODING_PCM_FLOAT)4 else 2
                        val found=info.size/(bytes*channels)
                        if(frames+found > l.size){
                            val need=min((rate*maxSeconds).toInt()+8192,frames+found+8192)
                            l=l.copyOf(need);r=r.copyOf(need)
                        }
                        for(i in 0 until min(found,l.size-frames)){
                            val a=if(bytes==4)data.float else data.short/32768f
                            val b=if(channels==1)a else
                                if(bytes==4)data.float else data.short/32768f
                            l[frames+i]=a.coerceIn(-1f,1f)
                            r[frames+i]=b.coerceIn(-1f,1f)
                        }
                        frames+=min(found,l.size-frames)
                        endOutput=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                        codec.releaseOutputBuffer(idx,false)
                    }
                }
            }
            require(frames>0){"Décodage audio vide"}
            return resample(l,r,frames,rate,maxSeconds)
        } finally {
            try {codec?.stop()}catch(_:Exception){}
            try {codec?.release()}catch(_:Exception){}
            extractor.release();descriptor.close()
        }
    }
    private fun resample(left:FloatArray,right:FloatArray,count:Int,rate:Int,
                         seconds:Float):Pair<FloatArray,FloatArray> {
        val frames=(seconds*StableAudioMath.SAMPLE_RATE).toInt()
        val a=FloatArray(frames);val b=FloatArray(frames)
        for(i in 0 until frames){
            val pos=i.toDouble()*rate/StableAudioMath.SAMPLE_RATE
            if(pos>=count)break // zero-pad short priming clip
            val p=floor(pos).toInt()
            val p2=min(count-1,p+1)
            val f=(pos-p).toFloat()
            a[i]=left[p]*(1f-f)+left[p2]*f
            b[i]=right[p]*(1f-f)+right[p2]*f
        }
        return Pair(a,b)
    }
}
