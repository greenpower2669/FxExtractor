package fr.fxextractor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Up to three minutes from small, bounded SA3 renders. Every DiT and SAME-S
 * worker dies between chunks. One T5Gemma embedding survives as a tiny file.
 * For subsequent chunks, 2s of the LAST output are context-only; only newly
 * generated PCM16 samples are appended. An external original remains untouched.
 */
public final class Sa3LongFormCoordinator {
    public interface Callback {
        void onProgress(String message);
        void onComplete(File wav);
        void onError(String message,boolean unexpectedDeath);
    }
    private static final int CONTEXT_FRAMES=Sa3WavChain.RATE*2;
    private static final int RENDER_FRAMES=Sa3WavChain.RATE*8;
    private final Context context;
    private final String session,prompt;
    private final float variation;
    private final long randomSeed;
    private final int cacheMode,targetFrames;
    private final boolean originalPresent,firstPrimed;
    private final Callback callback;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final File work,assembled;
    private Sa3Coordinator pipeline;
    private volatile boolean cancelled=false;
    private boolean firstChunk=true;
    private int producedFrames,chunkContext,chunkNew,chunkNumber,estimateTotal;

    public Sa3LongFormCoordinator(Context ctx,String session,String prompt,float totalSeconds,
                                  boolean originalPresent,boolean firstPrimed,float variation,
                                  long seed,int cacheMode,Callback cb){
        this.context=ctx.getApplicationContext();
        this.session=session;
        this.prompt=prompt;
        this.variation=variation;
        this.randomSeed=seed;
        this.cacheMode=cacheMode;
        this.targetFrames=Math.round(totalSeconds*Sa3WavChain.RATE);
        if(targetFrames<=0||targetFrames>Sa3WavChain.MAX_FRAMES)
            throw new IllegalArgumentException("Durée totale maximale : 180 secondes");
        this.originalPresent=originalPresent;
        this.firstPrimed=firstPrimed;
        this.callback=cb;
        this.work=new File(context.getFilesDir(),"sa3-work/"+session);
        this.assembled=new File(work,"assembled.wav");
    }
    public void start(){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("UI thread expected");
        callback.onProgress("Préparation des morceaux (limite 8 s par IA)...");
        io.execute(()->{
            try{
                if(cancelled)return;
                if(originalPresent){
                    Sa3WavChain.copyOriginal(new File(work,"original.wav"),assembled);
                    producedFrames=Sa3WavChain.frames(assembled);
                }else{
                    Sa3WavChain.createEmpty(assembled);producedFrames=0;
                }
                if(targetFrames-producedFrames<Sa3WavChain.RATE)
                    throw new IOException("Prolongation minimale 1 seconde, durée totale maximale 3 minutes.");
                estimateTotal=1+Math.max(0,(int)Math.ceil(
                    (targetFrames-producedFrames-RENDER_FRAMES)/6.0/Sa3WavChain.RATE));
                FxDebugLog.checkpoint("SA3_LONGFORM_START existing="+producedFrames+
                    " target="+targetFrames+" chunks="+estimateTotal);
                prepareNext();
            }catch(Throwable e){fail(e.getMessage()==null?e.toString():e.getMessage(),false);}
        });
    }
    private void prepareNext()throws IOException{
        if(cancelled)return;
        if(producedFrames>=targetFrames){finish();return;}
        int left=targetFrames-producedFrames;
        chunkContext=Math.min(CONTEXT_FRAMES,producedFrames);
        chunkNew=Math.min(left,RENDER_FRAMES-chunkContext);
        // In a partial last chunk, render >=1s new audio, then trim to exact frame count.
        int renderNew=Math.max(chunkNew,Sa3WavChain.RATE);
        float seconds=(chunkContext+renderNew)/(float)Sa3WavChain.RATE;
        float prefix=chunkContext/(float)Sa3WavChain.RATE;
        if(chunkContext>0){
            float[][] tail=Sa3WavChain.tail(assembled,chunkContext);
            Sa3CheckpointIO.write(work,"seed.bin",tail[0],tail[1]);
        }
        final boolean hasSeed=chunkContext>0 || (firstChunk && firstPrimed);
        final int clipNo=++chunkNumber;
        final boolean skipText=!firstChunk;
        FxDebugLog.checkpoint("SA3_LONGFORM_SEGMENT before="+producedFrames+
            " clip="+clipNo+"/"+estimateTotal+" prefixFrames="+chunkContext+
            " appendFrames="+chunkNew+" renderSeconds="+seconds);
        ui.post(()->{
            if(cancelled)return;
            callback.onProgress("🎹 Création du morceau "+clipNo+"/"+estimateTotal+
                " — "+(producedFrames/Sa3WavChain.RATE)+" s assemblées");
            pipeline=new Sa3Coordinator(context,session,prompt,seconds,hasSeed,variation,prefix,
                    randomSeed+0x9E3779B97F4A7C15L*clipNo,false,cacheMode,true,skipText,
                    new Sa3Coordinator.Callback(){
                        @Override public void onProgress(String detail){
                            if(!cancelled)callback.onProgress("Morceau "+clipNo+"/"+estimateTotal+" · "+detail);
                        }
                        @Override public void onComplete(File rendered){
                            if(cancelled)return;
                            io.execute(()->{
                                try{
                                    if(cancelled)return;
                                    producedFrames=Sa3WavChain.append(assembled,rendered,chunkContext,chunkNew);
                                    FxDebugLog.checkpoint("SA3_LONGFORM_JOIN clip="+clipNo+
                                        " producedFrames="+producedFrames);
                                    new File(work,"latents.bin").delete();
                                    rendered.delete();
                                    firstChunk=false;
                                    prepareNext();
                                }catch(Throwable e){fail("Assemblage : "+e.getMessage(),false);}
                            });
                        }
                        @Override public void onError(String msg,boolean unexpectedDeath){
                            fail("Morceau "+clipNo+"/"+estimateTotal+" : "+msg,unexpectedDeath);
                        }
                    });
            pipeline.start();
        });
    }
    private void finish()throws IOException{
        if(cancelled)return;
        File finalFile=Sa3WavChain.complete(assembled,
            new File(context.getFilesDir(),"stable_audio3_last.wav"),targetFrames);
        FxDebugLog.finish("LONGFORM WAV="+finalFile.length()+" bytes frames="+targetFrames+
            " chunks="+chunkNumber);
        FxDebugLog.checkpoint("SA3_LONGFORM_COMPLETE frames="+targetFrames);
        for(File f:new File[]{new File(work,"seed.bin"),new File(work,"text.bin"),
            new File(work,"original.wav"),new File(work,"latents.bin"),
            new File(work,"music.wav")}){
            f.delete();
        }
        work.delete();
        io.shutdown();
        ui.post(()->{if(!cancelled)callback.onComplete(finalFile);});
    }
    private void fail(String message,boolean unexpectedDeath){
        if(cancelled)return;
        cancelled=true;
        FxDebugLog.write("SA3_LONGFORM_FAILED segment="+chunkNumber+
            " producedFrames="+producedFrames+" error="+message);
        io.shutdown();
        ui.post(()->callback.onError(message,unexpectedDeath));
    }
    public void cancel(){
        cancelled=true;
        if(pipeline!=null)pipeline.cancel();
        io.shutdownNow();
        FxDebugLog.write("SA3_LONGFORM_CANCEL segments="+chunkNumber);
    }
}
