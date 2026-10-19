import fr.fxextractor.Sa3WavChain;
import fr.fxextractor.StableAudioMath;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;

public final class TestSa3WavChain {
    static void ok(boolean check,String message){if(!check)throw new AssertionError(message);}
    private static File wav(File dir,String name,int n,float level)throws Exception{
        File f=new File(dir,name);
        float[] l=new float[n],r=new float[n];
        Arrays.fill(l,level);Arrays.fill(r,-level);
        try(FileOutputStream out=new FileOutputStream(f)){
            StableAudioMath.writeWav(out,l,r,n);
        }
        return f;
    }
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("sa3-wav-chain").toFile();
        File base=wav(dir,"original.wav",44100,0.3f);
        File assembled=new File(dir,"assembled.wav");
        Sa3WavChain.copyOriginal(base,assembled);
        ok(Sa3WavChain.frames(assembled)==44100,"original duration changed");
        File first=wav(dir,"generated.wav",44100*8,0.6f);
        // The first two seconds are DiT conditioning ONLY: don't repeat them.
        int joined=Sa3WavChain.append(assembled,first,44100*2,44100*6);
        ok(joined==44100*7,"concat should be 1+6 seconds");
        float[][] tail=Sa3WavChain.tail(assembled,88200);
        ok(tail[0].length==88200,"context length");
        ok(Math.abs(tail[0][0]-0.6f)<0.0001f,"tail must be last part");
        byte[] originalBytes=Files.readAllBytes(base.toPath());
        byte[] joinedBytes=Files.readAllBytes(assembled.toPath());
        for(int i=44;i<originalBytes.length;i++)
            ok(originalBytes[i]==joinedBytes[i],"original samples altered "+i);
        File shorter=wav(dir,"short.wav",44100*3,0.15f);
        joined=Sa3WavChain.append(assembled,shorter,44100*2,44100);
        ok(joined==44100*8,"partial last segment must be exact");
        boolean rejected=false;
        try{Sa3WavChain.append(assembled,shorter,88200,88200);}
        catch(java.io.IOException expected){rejected=true;}
        ok(rejected,"should reject out-of-bounds segment");
        File finalFile=new File(dir,"music.wav");
        Sa3WavChain.complete(assembled,finalFile,joined);
        ok(finalFile.isFile() && !assembled.exists(),"atomic completion");
        ok(Sa3WavChain.frames(finalFile)==44100*8,"final header must be valid");
        Sa3WavChain.createEmpty(assembled);
        File old=wav(dir,"first.wav",44100,0.1f);
        ok(Sa3WavChain.append(assembled,old,0,44100)==44100,"new song first clip");
        for(File f:dir.listFiles())f.delete();
        dir.delete();
        System.out.println("SA3 CONCAT WAV: PASS");
    }
}
