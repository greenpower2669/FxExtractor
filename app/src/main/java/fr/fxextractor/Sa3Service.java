package fr.fxextractor;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.IBinder;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Each nested class has a DISTINCT android:process in the manifest.
 * IPC only carries text/metadata; private checkpoints carry tensor values.
 * Workers terminate their entire Linux process at the end of their stage
 * to release all native LiteRT allocations, caches and thread pools.
 */
public abstract class Sa3Service extends Service {
    static final int START=1, CANCEL=2, PROGRESS=3, COMPLETE=4, ERROR=5;
    public static final class Text extends Sa3Service {}
    public static final class DiT extends Sa3Service {}
    public static final class Decode extends Sa3Service {}

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Messenger incoming=new Messenger(new Handler(Looper.getMainLooper(),message->{
        if(message.what==CANCEL) {
            FxDebugLog.write("SA3_WORKER_CANCEL pid="+Process.myPid());
            Process.killProcess(Process.myPid());
            return true;
        }
        if(message.what!=START)return false;
        Messenger reply=message.replyTo;
        Bundle args=message.getData();
        executor.execute(()->execute(args,reply));
        return true;
    }));
    @Override public IBinder onBind(Intent intent){return incoming.getBinder();}
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}

    private void send(Messenger reply,int what,String detail) {
        if(reply==null)return;
        try{
            Message m=Message.obtain(null,what);
            Bundle values=new Bundle();
            values.putString("detail",detail);
            m.setData(values);
            reply.send(m);
        }catch(Exception ignored){FxDebugLog.write("SA3_IPC_SEND_FAILED "+what);}
    }

    private void execute(Bundle args,Messenger reply){
        String name=getClass().getSimpleName();
        try{
            String session=args.getString("session","");
            if(!session.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Bad SA3 session");
            File dir=new File(getFilesDir(),"sa3-work/"+session);
            float seconds=args.getFloat("seconds");
            float originalSeconds=args.getFloat("originalSeconds");
            boolean probe=args.getBoolean("probe");
            int cacheMode=Sa3CacheSettings.normalize(args.getInt("cacheMode",Sa3CacheSettings.DEFAULT));
            FxDebugLog.checkpoint("process."+name+".start pid="+Process.myPid()+" probe="+probe+
                " cache="+(this instanceof DiT?Sa3CacheSettings.label(cacheMode):"not_used"));
            send(reply,PROGRESS,"SA3_WORKER_PID="+Process.myPid());
            StableAudioEngine engine=new StableAudioEngine(getApplicationContext(),
                this instanceof DiT?cacheMode:Sa3CacheSettings.OFF);
            kotlin.jvm.functions.Function1<String,kotlin.Unit> progress=detail->{
                FxDebugLog.checkpoint("process."+name+" "+detail);
                send(reply,PROGRESS,detail);
                return kotlin.Unit.INSTANCE;
            };
            if(this instanceof Text){
                String prompt=args.getString("prompt","");
                if(prompt.isEmpty()||prompt.length()>1200)throw new IllegalArgumentException("Description invalide");
                kotlin.Pair<float[],float[]> pair=engine.encodeText(prompt,progress);
                Sa3CheckpointIO.write(dir,"text.bin",pair.getFirst(),pair.getSecond());
                FxDebugLog.checkpoint("process.Text.checkpoint_written");
            }else if(this instanceof DiT){
                if(probe){
                    send(reply,PROGRESS,engine.probeDiT(seconds));
                }else{
                    float[][] text=Sa3CheckpointIO.read(dir,"text.bin",2);
                    kotlin.Pair<float[],float[]> audio=null;
                    if(args.getBoolean("hasAudio")){
                        float[][] s=Sa3CheckpointIO.read(dir,"seed.bin",2);
                        audio=new kotlin.Pair<>(s[0],s[1]);
                    }
                    float[] latents=engine.generateLatents(text[0],text[1],seconds,audio,
                        args.getFloat("strength"),args.getLong("randomSeed"),originalSeconds,progress);
                    Sa3CheckpointIO.write(dir,"latents.bin",latents);
                    FxDebugLog.checkpoint("process.DiT.checkpoint_written");
                }
            }else if(this instanceof Decode){
                float[][] saved=Sa3CheckpointIO.read(dir,"latents.bin",1);
                kotlin.Pair<float[],float[]> original=null;
                if(originalSeconds>0f){
                    float[][] s=Sa3CheckpointIO.read(dir,"seed.bin",2);
                    original=new kotlin.Pair<>(s[0],s[1]);
                }
                StableAudioEngine.Music music=engine.decodeLatents(saved[0],seconds,original,originalSeconds,progress);
                File pending=new File(dir,"music.wav.tmp");
                try(FileOutputStream stream=new FileOutputStream(pending)){
                    StableAudioMath.writeWav(stream,music.getLeft(),music.getRight(),music.getLeft().length);
                    stream.getFD().sync();
                }
                File target=args.getBoolean("longForm")?
                    new File(dir,"music.wav"):new File(getFilesDir(),"stable_audio3_last.wav");
                if(target.exists()&&!target.delete())throw new java.io.IOException("Old WAV could not be replaced");
                if(!pending.renameTo(target))throw new java.io.IOException("WAV commit failed");
                FxDebugLog.checkpoint("process.Decode.wav="+target.length());
            }else throw new IllegalStateException("Unknown SA3 process");
            send(reply,COMPLETE,"process "+name+" finished pid="+Process.myPid());
        }catch(Throwable e){
            FxDebugLog.write("SA3_WORKER_ERROR "+name+" "+FxDebugLog.stack(e));
            send(reply,ERROR,e.getClass().getSimpleName()+": "+e.getMessage());
        }finally{
            // Do not start the next stage until the UI observes Binder death.
            // Deliberate process death is expected and must NOT be reported as OOM.
            main.postDelayed(()->Process.killProcess(Process.myPid()),400);
        }
    }
}
