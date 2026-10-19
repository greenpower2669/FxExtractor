package fr.fxextractor;

import android.content.Context;
import android.os.Build;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Selective XNNPACK weight-cache experiment for the quantized SA3 DiT.
 * Explicit user-controlled baseline, in-memory dedup and file-backed mmap modes.
 * The path identifies one model SHA, CPU architecture and LiteRT release.
 * Nothing here claims XNNPACK actually reduced memory until device measurements.
 */
public final class Sa3CacheSettings {
    public static final int OFF=0;
    public static final int MEMORY=1;
    public static final int FILE_BACKED=2;
    public static final int DEFAULT=FILE_BACKED;
    private static final String MODEL="sa3/sa3-sm-music/dit_w8a8.tflite";
    private static final String PREFIX="sa3-dit-w8a8-litert23-";
    private static final long MIN_DISK_FREE=1024L*1024*1024; // do not fill a nearly full phone
    private Sa3CacheSettings(){}

    public static int normalize(int mode){
        return mode>=OFF&&mode<=FILE_BACKED?mode:DEFAULT;
    }
    public static String label(int mode){
        switch(normalize(mode)){
            case OFF:return "CPU historique, aucun cache de poids explicite";
            case MEMORY:return "CPU XNNPACK : cache en mémoire (expérimental)";
            default:return "CPU XNNPACK : cache fichier partageable (expérimental)";
        }
    }
    /** null means exact 0.4.5 baseline. ":memory" is the XNNPACK documented magic path. */
    public static String cachePath(Context context,int chosenMode){
        int mode=normalize(chosenMode);
        if(mode==OFF){
            FxDebugLog.write("SA3_XNN_CACHE mode=off");
            return null;
        }
        if(mode==MEMORY){
            FxDebugLog.write("SA3_XNN_CACHE mode=memory");
            return ":memory";
        }
        try {
            String digest;
            try(InputStream input=context.getAssets().open("sa3/weights-manifest.json")){
                JSONObject manifest=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                digest=manifest.getJSONObject("files").getJSONObject(MODEL).getString("sha256");
            }
            if(!digest.matches("[a-fA-F0-9]{64}"))
                throw new IllegalArgumentException("Invalid official model SHA-256");
            String abi=Build.SUPPORTED_ABIS.length==0?"unknown":Build.SUPPORTED_ABIS[0];
            String key=PREFIX+abi+"-"+digest.substring(0,20).toLowerCase()+".xnnwc";
            File folder=new File(context.getCacheDir(),"sa3-xnnpack");
            if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("Cannot create cache folder");
            if(folder.getUsableSpace()<MIN_DISK_FREE){
                FxDebugLog.write("SA3_XNN_CACHE disk_low fallback=memory availableMiB="+
                    folder.getUsableSpace()/1048576L);
                return ":memory";
            }
            // Only delete stale copies of OUR DiT cache, never application or user files.
            File[] old=folder.listFiles();
            if(old!=null)for(File f:old){
                if(f.isFile()&&f.getName().startsWith(PREFIX)&&!f.getName().equals(key)){
                    boolean deleted=f.delete();
                    FxDebugLog.write("SA3_XNN_CACHE obsolete_deleted="+deleted);
                }
            }
            File file=new File(folder,key);
            FxDebugLog.write("SA3_XNN_CACHE mode=file exists="+file.isFile()+
                " bytes="+(file.isFile()?file.length():0)+
                " freeMiB="+folder.getUsableSpace()/1048576L);
            return file.getAbsolutePath();
        }catch(Exception error){
            FxDebugLog.write("SA3_XNN_CACHE file_unavailable fallback=memory reason="+
                error.getClass().getSimpleName());
            return ":memory";
        }
    }
    public static void logAfterLoad(String path){
        if(path==null||path.equals(":memory"))return;
        File file=new File(path);
        FxDebugLog.write("SA3_XNN_CACHE after_load file_bytes="+
            (file.isFile()?file.length():-1L));
    }
}
