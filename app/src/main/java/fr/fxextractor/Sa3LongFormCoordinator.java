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
    private static final int RENDER_FRAMES=Sa3WavChain.RATE*8;
    private final Context context;
    private final String session,prompt;
    private final float variation;
    private final long randomSeed;
    private final int cacheMode,targetFrames,contextSeconds;
    private final boolean originalPresent,firstPrimed,retrySilence;
    private final Callback callback;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final File work,assembled;
    private Sa3Coordinator pipeline;
    private volatile boolean cancelled=false;
    private boolean firstChunk=true;
    private int producedFrames,chunkContext,chunkNew,chunkNumber,estimateTotal,attempt;

    public Sa3LongFormCoordinator(Context ctx,String session,String prompt,float totalSeconds,
                                  boolean originalPresent,boolean firstPrimed,float variation,
                                  long seed,int cacheMode,int contextSeconds,
                                  boolean retrySilence,Callback cb){
        this.context=ctx.getApplicationContext();
        this.session=session;
        this.prompt=prompt;
        this.variation=variation;
        this.randomSeed=seed;
        this.cacheMode=cacheMode;
        if(contextSeconds!=2 && contextSeconds!=4 && contextSeconds!=6)
            throw new IllegalArgumentException("Contexte : 2, 4 ou 6 secondes");
        this.contextSeconds=contextSeconds;
        this.retrySilence=retrySilence;
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
                // Simulate the SAME aligned context policy to show the true
                // number of small inference jobs before starting a 3-min run.
                int simulated=producedFrames;
                while(simulated<targetFrames){
                    int preserved=Sa3Continuity.contextFrames(contextSeconds,simulated);
                    int capacity=RENDER_FRAMES-preserved;
                    if(capacity<Sa3WavChain.RATE)
                        throw new IOException("Contexte trop long pour Stable Audio 3");
                    simulated+=Math.min(capacity,targetFrames-simulated);
                    estimateTotal++;
                }
                FxDebugLog.checkpoint("SA3_LONGFORM_START existing="+producedFrames+
                    " target="+targetFrames+" chunks="+estimateTotal+
                    " contextSelected="+contextSeconds+"s retrySilence="+retrySilence);
                prepareNext();
            }catch(Throwable e){fail(e.getMessage()==null?e.toString():e.getMessage(),false);}
        });
    }
    private void prepareNext()throws IOException{
        if(cancelled)return;
        if(producedFrames>=targetFrames){finish();return;}
        int left=targetFrames-producedFrames;
        chunkContext=Sa3Continuity.contextFrames(contextSeconds,producedFrames);
        chunkNew=Math.min(left,RENDER_FRAMES-chunkContext);
        if(chunkNew<=0)throw new IOException("Impossible de générer avec ce contexte");
        // In a partial last chunk render >=1s, then trim precisely to the
        // requested frame count after SAME-S decoding.
        int renderNew=Math.max(chunkNew,Sa3WavChain.RATE);
        float seconds=(chunkContext+renderNew)/(float)Sa3WavChain.RATE;
        float prefix=chunkContext/(float)Sa3WavChain.RATE;
        if(chunkContext>0){
            float[][] tail=Sa3WavChain.tail(assembled,chunkContext);
            if(tail[0].length!=chunkContext || tail[1].length!=chunkContext)
                throw new IOException("Contexte audio réel incomplet");
            Sa3CheckpointIO.write(work,"seed.bin",tail[0],tail[1]);
        }
        attempt=0;
        int clipNo=++chunkNumber;
        final boolean hasSeed=chunkContext>0 || (firstChunk && firstPrimed);
        FxDebugLog.checkpoint("SA3_LONGFORM_SEGMENT before="+producedFrames+
            " clip="+clipNo+"/"+estimateTotal+" contextRequested="+contextSeconds+
            " contextFrames="+chunkContext+" prefixLatents="+
            (chunkContext/StableAudioMath.SAMPLES_PER_LATENT)+
            " appendFrames="+chunkNew+" renderSeconds="+seconds);
        launchChunk(clipNo,seconds,prefix,hasSeed);
    }
    private void launchChunk(int clipNo,float seconds,float prefix,boolean hasSeed){
        final int attemptId=attempt;
        // On a targeted retry reuse the T5Gemma file and existing seed.
        final boolean skipText=!firstChunk || attemptId>0;
        ui.post(()->{
            if(cancelled)return;
            callback.onProgress("🎹 Morceau "+clipNo+"/"+estimateTotal+
                " · Contexte "+String.format(java.util.Locale.FRANCE,"%.2f",prefix)+
                "s · Essai "+(attemptId+1)+(retrySilence?"/2":""));
            pipeline=new Sa3Coordinator(context,session,prompt,seconds,hasSeed,variation,prefix,
                randomSeed+0x9E3779B97F4A7C15L*clipNo+
                0x632BE59BD9B4E019L*attemptId,false,cacheMode,true,skipText,
                new Sa3Coordinator.Callback(){
                    @Override public void onProgress(String detail){
                        if(!cancelled)callback.onProgress("Morceau "+clipNo+"/"+estimateTotal+
                            " · "+detail);
                    }
                    @Override public void onComplete(File rendered){
                        if(cancelled)return;
                        io.execute(()->{
                            try{
                                if(cancelled)return;
                                Sa3Continuity.Check qc=Sa3Continuity.inspect(
                                    assembled,rendered,chunkContext,chunkNew);
                                FxDebugLog.checkpoint("SA3_AUDIO_QC clip="+clipNo+
                                    " attempt="+(attemptId+1)+" "+qc);
                                if(qc.suspect && retrySilence){
                                    if(attemptId<1){
                                        if(!rendered.delete())
                                            FxDebugLog.write("SA3_AUDIO_QC couldn't remove retry clip");
                                        attempt=attemptId+1;
                                        FxDebugLog.checkpoint("SA3_AUDIO_QC_RETRY clip="+clipNo);
                                        launchChunk(clipNo,seconds,prefix,hasSeed);
                                        return;
                                    }
                                    // Never blindly append a second suspicious
                                    // hole. Preserve assembled.wav for recovery.
                                    throw new IOException("Silence prolongé détecté au morceau "+
                                        clipNo+" malgré deux essais. WAV partiel récupérable.");
                                }
                                producedFrames=Sa3WavChain.append(assembled,rendered,
                                    chunkContext,chunkNew);
                                FxDebugLog.checkpoint("SA3_LONGFORM_JOIN clip="+clipNo+
                                    " producedFrames="+producedFrames+
                                    " contextFrames="+chunkContext);
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
