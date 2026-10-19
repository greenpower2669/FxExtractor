package fr.fxextractor;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.Process;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Offline, persistent, bounded diagnostic trail. Unlike logcat, survives activity
 * recreation and system process kills. No prompts or imported media content stored.
 * A native SIGSEGV/SIGKILL cannot execute a Java exception handler: the latest
 * fsynced checkpoint and pending marker still identify the last reached stage.
 */
public final class FxDebugLog {
    private static final String TAG="FxExtractor-DEBUG";
    private static final String FILE="sa3-debug.log";
    private static final String OLD="sa3-debug.previous.log";
    private static final String PENDING="sa3-run.pending";
    private static final int MAX_BYTES=256*1024;
    private static Context app;
    private static volatile String stage="startup";
    private static volatile String runInfo="";
    private FxDebugLog(){}

    public static synchronized void initialize(Context context) {
        if(app!=null)return;
        app=context.getApplicationContext();
        write("APP_START version="+BuildInfo.version(app)+" android="+
            android.os.Build.VERSION.SDK_INT+" device="+
            android.os.Build.MANUFACTURER+"/"+android.os.Build.MODEL+
            " pid="+Process.myPid());
        logRecentExitReasons();
        if(hasPending())write("PREVIOUS_GENERATION_INCOMPLETE: "+pendingSummary());
    }
    /**
     * Android 11+ system-retained reasons survive native crashes and low-memory
     * SIGKILL; unlike Java UncaughtExceptionHandler these can identify OOM.
     */
    private static void logRecentExitReasons(){
        if(app==null || android.os.Build.VERSION.SDK_INT<30)return;
        try {
            ActivityManager manager=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<ApplicationExitInfo> history=manager.getHistoricalProcessExitReasons(
                app.getPackageName(),0,3);
            if(history==null || history.isEmpty()){
                write("ANDROID_EXIT_HISTORY empty");
                return;
            }
            for(ApplicationExitInfo entry:history){
                String reason;
                switch(entry.getReason()){
                    case ApplicationExitInfo.REASON_LOW_MEMORY: reason="LOW_MEMORY";break;
                    case ApplicationExitInfo.REASON_CRASH: reason="JAVA_CRASH";break;
                    case ApplicationExitInfo.REASON_CRASH_NATIVE: reason="NATIVE_CRASH";break;
                    case ApplicationExitInfo.REASON_ANR: reason="ANR";break;
                    case ApplicationExitInfo.REASON_SIGNALED: reason="SIGNALED";break;
                    case ApplicationExitInfo.REASON_USER_REQUESTED: reason="USER_REQUESTED";break;
                    default: reason="OTHER_"+entry.getReason();break;
                }
                String description=entry.getDescription();
                write("ANDROID_LAST_EXIT reason="+reason+" status="+entry.getStatus()+
                    " time="+entry.getTimestamp()+" pssMiB="+(entry.getPss()/1024)+
                    " rssMiB="+(entry.getRss()/1024)+" desc="+
                    (description==null?"none":description));
            }
        } catch(Throwable error){
            write("ANDROID_EXIT_HISTORY unavailable="+error.getClass().getSimpleName());
        }
    }
    private static String now(){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());
    }
    private static void rotateIfNeeded(File file){
        if(file.length()<=MAX_BYTES)return;
        File older=new File(app.getFilesDir(),OLD);
        if(older.exists())older.delete();
        if(!file.renameTo(older))file.delete();
    }
    public static synchronized void write(String message) {
        if(app==null)return;
        try {
            String clean=message==null?"(null)":message.replace('\r',' ').replace("\n"," ⏎ ");
            if(clean.length()>10000)clean=clean.substring(0,10000)+"…";
            String line=now()+" | "+Thread.currentThread().getName()+" | "+clean+"\n";
            Log.i(TAG,clean);
            File file=new File(app.getFilesDir(),FILE);
            rotateIfNeeded(file);
            try(FileOutputStream stream=new FileOutputStream(file,true)){
                stream.write(line.getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
        } catch(Throwable ignored) {
            // Logging must never crash the generator, even during OOM or IO failure.
        }
    }
    public static synchronized void checkpoint(String next){
        stage=next;
        if(hasPending())persistPending(runInfo+" lastStage="+next);
        write("STAGE "+next+" | "+memory());
    }
    public static synchronized void begin(String mode,float seconds,boolean hasAudio){
        stage="generation.begin";
        runInfo=now()+" | mode="+mode+" seconds="+seconds+" hasAudio="+hasAudio;
        persistPending(runInfo+" lastStage="+stage);
        write("GENERATION_BEGIN "+runInfo+" | "+memory());
    }
    private static void persistPending(String state) {
        if(app==null)return;
        try(FileOutputStream marker=new FileOutputStream(new File(app.getFilesDir(),PENDING))){
            marker.write(state.getBytes(StandardCharsets.UTF_8));
            marker.getFD().sync();
        }catch(Throwable ignored){}
    }
    public static synchronized void finish(String result){
        write("GENERATION_END "+result+" | "+memory());
        deletePending();
        runInfo="";
        stage="idle";
    }
    public static synchronized void error(Throwable t){
        write("GENERATION_ERROR stage="+stage+" "+stack(t));
        deletePending();
        runInfo="";
    }
    public static synchronized void unexpected(Throwable t){
        write("UNCAUGHT stage="+stage+" "+stack(t));
        // Pending marker intentionally preserved on a hard process crash.
    }
    public static synchronized void lifecycle(String name){write("LIFECYCLE "+name+" busyStage="+stage);}
    public static boolean hasPending(){
        return app!=null && new File(app.getFilesDir(),PENDING).exists();
    }
    public static String pendingSummary(){
        if(!hasPending())return "";
        try{
            return "⚠️ Dernière génération interrompue : "+read(new File(app.getFilesDir(),PENDING),2048);
        }catch(Throwable ignored){return "⚠️ Dernière génération interrompue (voir journal).";}
    }
    private static void deletePending(){
        if(app!=null)new File(app.getFilesDir(),PENDING).delete();
    }
    public static String memory(){
        try{
            Runtime rt=Runtime.getRuntime();
            long heapUsed=(rt.totalMemory()-rt.freeMemory())/1048576;
            long heapMax=rt.maxMemory()/1048576;
            long nativeMb=Debug.getNativeHeapAllocatedSize()/1048576;
            long pssMb=Debug.getPss()/1024;
            ActivityManager manager=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return "memory java="+heapUsed+"/"+heapMax+"MiB native="+nativeMb+
                "MiB pss="+pssMb+"MiB systemAvailable="+(info.availMem/1048576)+
                "MiB lowMemory="+info.lowMemory;
        }catch(Throwable t){return "memory_unavailable="+t.getClass().getSimpleName();}
    }
    public static String stack(Throwable t){
        if(t==null)return "(null)";
        try{
            StringWriter buffer=new StringWriter();
            PrintWriter printer=new PrintWriter(buffer);
            t.printStackTrace(printer);printer.flush();
            String report=buffer.toString().replace("\r","");
            return report.length()>12000?report.substring(0,12000):report;
        }catch(Throwable ignored){return t.toString();}
    }
    private static String read(File f,int maxBytes)throws Exception{
        if(!f.exists())return "";
        long length=f.length();
        int size=(int)Math.min(length,maxBytes);
        byte[] bytes=new byte[size];
        try(FileInputStream in=new FileInputStream(f)){
            long skip=length-size;
            while(skip>0){long n=in.skip(skip);if(n<=0)break;skip-=n;}
            int total=0;
            while(total<size){int n=in.read(bytes,total,size-total);if(n<0)break;total+=n;}
        }
        return new String(bytes,StandardCharsets.UTF_8);
    }
    public static synchronized String getLog(){
        if(app==null)return "Diagnostic non initialisé";
        StringBuilder b=new StringBuilder("FxExtractor SA3 — journal local (aucun envoi automatique)\n");
        if(hasPending())b.append(pendingSummary()).append("\n");
        b.append("État courant : ").append(stage).append("\n").append(memory()).append("\n\n");
        try{
            b.append("=== PRÉCÉDENT ===\n").append(read(new File(app.getFilesDir(),OLD),25000));
            b.append("\n=== ACTUEL ===\n").append(read(new File(app.getFilesDir(),FILE),65000));
        }catch(Exception e){b.append("Lecture impossible : ").append(e);}
        return b.toString();
    }
    public static void show(Activity activity){
        final String text=getLog();
        TextView output=new TextView(activity);
        output.setTextSize(17);output.setPadding(24,24,24,24);
        output.setTextIsSelectable(true);output.setText(text);
        ScrollView scroll=new ScrollView(activity);
        scroll.addView(output);
        new AlertDialog.Builder(activity)
            .setTitle("🧪 Journal de débogage FxExtractor")
            .setView(scroll)
            .setNeutralButton("Copier tout",(d,which)->{
                ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("FxExtractor debug",text));
            })
            .setPositiveButton("Partager",(d,which)->{
                Intent share=new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT,text);
                activity.startActivity(Intent.createChooser(share,"Envoyer le journal de débogage"));
            })
            .setNegativeButton("Fermer",null)
            .show();
    }
    private static final class BuildInfo {
        static String version(Context c){
            try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}
            catch(Exception e){return "unknown";}
        }
    }
}
