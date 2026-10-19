import fr.fxextractor.Sa3Continuity;
import fr.fxextractor.Sa3WavChain;
import fr.fxextractor.StableAudioMath;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;

/** Pure JVM assertions: no Android and no fake inference. */
public final class TestSa3Continuity {
    private static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static File wav(File root,String name,int frames,float amplitude)throws Exception{
        File file=new File(root,name);
        float[] l=new float[frames],r=new float[frames];
        Arrays.fill(l,amplitude);
        Arrays.fill(r,amplitude);
        try(FileOutputStream out=new FileOutputStream(file)){
            StableAudioMath.writeWav(out,l,r,frames);
        }
        return file;
    }
    public static void main(String[] args)throws Exception{
        final int rate=StableAudioMath.SAMPLE_RATE;
        final int latent=StableAudioMath.SAMPLES_PER_LATENT;
        ok(Sa3Continuity.contextFrames(2,8*rate)==22*latent,"2s alignment");
        ok(Sa3Continuity.contextFrames(4,8*rate)==44*latent,"4s alignment");
        ok(Sa3Continuity.contextFrames(6,8*rate)==64*latent,"6s alignment");
        ok(Sa3Continuity.contextFrames(6,rate)==10*latent,"short input must not invent missing samples");
        ok(Sa3Continuity.contextFrames(4,0)==0,"free generation context");
        ok(Sa3Continuity.contextFrames(6,8*rate)<=8*rate-rate,"must generate at least one second");
        for(int length:new int[]{0,rate,8*rate,90*rate})
            for(int requested:new int[]{2,4,6}){
                int context=Sa3Continuity.contextFrames(requested,length);
                ok(context>=0 && context<=length,"never invent context");
                ok(context%(2*latent)==0,"even latent alignment");
            }
        boolean rejected=false;
        try{Sa3Continuity.contextFrames(3,8*rate);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"only approved UI durations");
        File root=Files.createTempDirectory("sa3-continuity").toFile();
        try{
            File previous=wav(root,"previous.wav",rate*2,0.20f);
            File quiet=wav(root,"quiet.wav",rate*2,0f);
            File loud=wav(root,"loud.wav",rate*2,0.20f);
            ok(Sa3Continuity.inspect(previous,quiet,0,rate*2).suspect,
                "unexpected prolonged zero following active music");
            ok(!Sa3Continuity.inspect(previous,loud,0,rate*2).suspect,
                "ordinary audible music should remain accepted");
            ok(!Sa3Continuity.inspect(quiet,quiet,0,rate*2).suspect,
                "legitimate silent intro should not be blindly rewritten");
            int context=Sa3Continuity.contextFrames(4,8*rate);
            File withPrefix=wav(root,"with-context.wav",rate*8,0.20f);
            ok(!Sa3Continuity.inspect(previous,withPrefix,context,8*rate-context).suspect,
                "prefix must be excluded from inspection");
            float[] left=new float[rate*2],right=new float[left.length];
            Arrays.fill(left,0.20f);Arrays.fill(right,0.20f);
            // 1s silence after 0.3s new music, then resumed sound.
            Arrays.fill(left,rate*3/10,rate*13/10,0f);
            Arrays.fill(right,rate*3/10,rate*13/10,0f);
            File gap=new File(root,"gap.wav");
            try(FileOutputStream out=new FileOutputStream(gap)){
                StableAudioMath.writeWav(out,left,right,left.length);
            }
            Sa3Continuity.Check c=Sa3Continuity.inspect(previous,gap,0,left.length);
            ok(c.suspect && c.longestQuietMs>=800,"internal silence hole must be found");
            System.out.println("PASS SA3 CONTINUITY: 2/4/6s aligned, no fake context, gap detection and quiet ambience");
        }finally{
            File[] files=root.listFiles();if(files!=null)for(File file:files)file.delete();
            root.delete();
        }
    }
}
