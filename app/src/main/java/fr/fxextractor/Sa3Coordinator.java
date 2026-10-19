package fr.fxextractor;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import java.io.File;
import java.util.UUID;

/** Sequential stage orchestration. No two SA3 model processes run together. */
public final class Sa3Coordinator {
    public interface Callback {
        void onProgress(String text);
        void onComplete(File audioFile);
        void onError(String text, boolean unexpectedDeath);
    }
    private final Context context;
    private final Callback callback;
    private final String session;
    private final String prompt;
    private final float seconds,strength,originalSeconds;
    private final long seed;
    private final boolean hasAudio,probeOnly;
    private final int cacheMode;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private int stage=-1;
    private boolean cancelled=false,completed=false,bound=false;
    private String workerError=null;
    private int workerPid=-1;
    private final Messenger inbox;
    private static final Class<?>[] SERVICES = {
        Sa3Service.Text.class,Sa3Service.DiT.class,Sa3Service.Decode.class
    };
    private ServiceConnection connection;
    private Messenger remote;

    public Sa3Coordinator(Context context,String session,String prompt,float seconds,
                          boolean hasAudio,float strength,float originalSeconds,long seed,
                          boolean probeOnly,int cacheMode,Callback callback) {
        this.context=context.getApplicationContext();
        if(!session.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Session invalide");
        this.session=session;
        this.prompt=prompt;
        this.seconds=seconds;
        this.hasAudio=hasAudio;
        this.strength=strength;
        this.originalSeconds=originalSeconds;
        this.seed=seed;
        this.probeOnly=probeOnly;
        this.cacheMode=Sa3CacheSettings.normalize(cacheMode);
        this.callback=callback;
        this.inbox=new Messenger(new Handler(Looper.getMainLooper(),message->{
        if(cancelled)return true;
        String detail=message.getData().getString("detail","");
        if(message.what==Sa3Service.PROGRESS){
            if(detail.startsWith("SA3_WORKER_PID=")){
                try{workerPid=Integer.parseInt(detail.substring(15));}
                catch(NumberFormatException ignored){workerPid=-1;}
                FxDebugLog.write("SA3_WORKER_CONNECTED stage="+stage+" pid="+workerPid+
                    " cache="+Sa3CacheSettings.label(cacheMode));
            }else callback.onProgress(detail);
        }else if(message.what==Sa3Service.COMPLETE){
            completed=true;
            FxDebugLog.checkpoint("pipeline.stage."+stage+".done waiting-worker-exit");
            callback.onProgress("✅ Étape "+(stage+1)+"/"+(probeOnly?1:3)+" terminée. Libération complète de la mémoire…");
        }else if(message.what==Sa3Service.ERROR){
            workerError=detail;
            FxDebugLog.write("PIPELINE_WORKER_REPORTED_ERROR "+detail);
        }
        return true;
    }));
    }
    public static String newSession(){return UUID.randomUUID().toString();}
    public File workspace(){return new File(context.getFilesDir(),"sa3-work/"+session);}
    public void start(){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("UI thread required");
        startStage(probeOnly?1:0);
    }
    private void startStage(int next){
        if(cancelled)return;
        stage=next;completed=false;workerError=null;remote=null;workerPid=-1;
        FxDebugLog.checkpoint("pipeline.stage."+stage+".binding");
        callback.onProgress(probeOnly?"🔬 Essai DiT seul (sans T5Gemma)…":
            stage==0?"🧠 T5Gemma : calcul du texte…":
            stage==1?"🎹 Stable Audio DiT : génération…":
                     "🎧 SAME-S : décodage et WAV…");
        final Class<?> clazz=SERVICES[stage];
        final ServiceConnection active=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(cancelled)return;
                remote=new Messenger(binder);
                Message run=Message.obtain(null,Sa3Service.START);
                Bundle args=new Bundle();
                args.putString("session",session);
                args.putString("prompt",prompt);
                args.putFloat("seconds",seconds);
                args.putFloat("originalSeconds",originalSeconds);
                args.putFloat("strength",strength);
                args.putLong("randomSeed",seed);
                args.putBoolean("hasAudio",hasAudio);
                args.putBoolean("probe",probeOnly);
                args.putInt("cacheMode",cacheMode);
                run.setData(args);run.replyTo=inbox;
                FxDebugLog.checkpoint("pipeline.stage."+stage+".connected");
                try{remote.send(run);}catch(RemoteException e){fail("Connexion IA impossible : "+e.getMessage(),true);}
            }
            @Override public void onServiceDisconnected(ComponentName name){
                if(cancelled || connection!=this)return;
                boolean passed=completed;
                String error=workerError;
                detach();
                if(error!=null){fail(error,false);return;}
                if(!passed){
                    fail("Processus IA interrompu à l'étape "+(stage+1)+
                        " (mémoire Android possiblement insuffisante). Voir le journal.",true);
                    return;
                }
                FxDebugLog.checkpoint("pipeline.stage."+stage+".process_exited");
                if(probeOnly || stage==2){
                    if(probeOnly) {
                        FxDebugLog.finish("Standalone DiT successfully loaded and unloaded");
                        callback.onComplete(null);
                    }else{
                        File output=new File(context.getFilesDir(),"stable_audio3_last.wav");
                        if(!output.isFile() || output.length()<44){
                            fail("Le fichier WAV final est absent.",false);return;
                        }
                        FxDebugLog.finish("wav="+output.length()+" bytes");
                        deleteWorkDir(workspace());
                        callback.onComplete(output);
                    }
                } else startStage(stage+1);
            }
            @Override public void onNullBinding(ComponentName name){fail("Service SA3 indisponible",false);}
            @Override public void onBindingDied(ComponentName name){fail("Service SA3 arrêté prématurément",true);}
        };
        connection=active;
        try{
            bound=context.bindService(new Intent(context,clazz),active,Context.BIND_AUTO_CREATE);
            if(!bound)fail("Impossible de démarrer le processus "+clazz.getSimpleName(),false);
        }catch(Exception e){fail("Service IA : "+e.getMessage(),false);}
    }
    private void detach(){
        if(bound && connection!=null){
            try{context.unbindService(connection);}catch(Exception ignored){}
        }
        connection=null;bound=false;remote=null;
    }
    private void fail(String text,boolean unexpectedDeath){
        if(cancelled)return;
        cancelled=true;
        detach();
        if(unexpectedDeath){
            FxDebugLog.write("SA3_PIPELINE_UNEXPECTED_DEATH "+text+
                " pid="+workerPid+" cache="+Sa3CacheSettings.label(cacheMode));
            final int failedPid=workerPid;
            if(failedPid>0) ui.postDelayed(()->logWorkerExitReason(failedPid),1500);
        }
        else FxDebugLog.error(new IllegalStateException(text));
        callback.onError(text,unexpectedDeath);
    }
    /** Retained Android 11+ exit reason. A voluntary worker SIGKILL is NOT a memory error. */
    private void logWorkerExitReason(int pid){
        try{
            ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<ApplicationExitInfo> exits=am.getHistoricalProcessExitReasons(
                context.getPackageName(),pid,3);
            if(exits==null||exits.isEmpty()){
                FxDebugLog.write("SA3_WORKER_EXIT pid="+pid+" reason=not_recorded_yet");
                return;
            }
            for(ApplicationExitInfo e:exits){
                if(e.getPid()!=pid)continue;
                FxDebugLog.write("SA3_WORKER_EXIT pid="+pid+" reasonCode="+e.getReason()+
                    " status="+e.getStatus()+" pssMiB="+e.getPss()/1048576L+
                    " rssMiB="+e.getRss()/1048576L);
                return;
            }
        }catch(Throwable t){FxDebugLog.write("SA3_WORKER_EXIT reason_unavailable="+t.getClass().getSimpleName());}
    }
    public void cancel(){
        if(cancelled)return;
        cancelled=true;
        if(remote!=null){
            try{remote.send(Message.obtain(null,Sa3Service.CANCEL));}
            catch(RemoteException ignored){}
        }
        detach();
    }
    private static void deleteWorkDir(File dir){
        File[] children=dir.listFiles();
        if(children!=null)for(File f:children)if(f.isFile())f.delete();
        dir.delete();
    }
}
