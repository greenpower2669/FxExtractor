import fr.fxextractor.StableAudioMath;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class TestStableAudioMath {
    private static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    private static int u32(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|((b[p+3]&255)<<24);}
    public static void main(String[] args) throws Exception {
        require(StableAudioMath.latentLength(1)==11,"1s length");
        require(StableAudioMath.latentLength(10)==108,"10s length");
        require(StableAudioMath.latentLength(120)==1292,"120s length");
        float[] sigma=StableAudioMath.pingpongSchedule(8,1f);
        require(sigma.length==9 && sigma[0]==1f && sigma[8]==0f,"endpoints");
        for(int i=1;i<sigma.length;i++)require(sigma[i]<=sigma[i-1],"not descending");
        float[] x={1f,-1f,0.25f}; float[] v={.5f,1f,-.25f};
        StableAudioMath.pingpongStep(x,v,null,1f,0f);
        require(Math.abs(x[0]-.5f)<1e-6 && x[1]==-2f && x[2]==.5f,"denoise");
        float[] left=new float[44100],right=new float[44100];
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        StableAudioMath.writeWav(result,left,right,left.length);
        byte[] wave=result.toByteArray();
        require(wave.length==44+44100*4,"WAV length");
        require(new String(wave,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF"),"RIFF");
        require(u32(wave,24)==44100 && u32(wave,40)==176400,"PCM format");

        // Regression: Samsung final chunk 17/17, source=256*34=8704,
        // decoder signature s64=256*64=16384. Former copy srcPos=8670
        // requested 64 elements and crossed the end of the array.
        final int channels=256;
        float[] last=new float[channels*34];
        for(int ch=0;ch<channels;ch++)
            for(int i=0;i<34;i++)last[ch*34+i]=ch*100f+i;
        float[] expanded=StableAudioMath.decoderWindow(last,channels,34,0,64);
        require(expanded.length==16384,"short signature width");
        for(int ch=0;ch<channels;ch++){
            for(int i=0;i<34;i++)
                require(expanded[ch*64+i]==last[ch*34+i],"unaltered short latents");
            for(int i=34;i<64;i++)
                require(expanded[ch*64+i]==last[ch*34+33],"pad must remain in channel");
        }
        require(expanded[255*64+63]==25533f,"regression: last channel overread");
        // Ordinary full-width window and shifted window must remain identical.
        float[] normal=new float[256*86];
        for(int ch=0;ch<256;ch++)for(int i=0;i<86;i++)normal[ch*86+i]=ch*100f+i;
        float[] full=StableAudioMath.decoderWindow(normal,256,86,22,64);
        require(full[255*64+63]==25585f,"regular window unchanged");
        float[] small=StableAudioMath.decoderWindow(last,channels,34,30,64);
        require(small[255*64]==25530f && small[255*64+63]==25533f,
            "last-offset window padded separately for each channel");
        boolean invalid=false;
        try{StableAudioMath.decoderWindow(last,channels,34,34,64);}
        catch(IllegalArgumentException expected){invalid=true;}
        require(invalid,"invalid decoder offset must reject");
        System.out.println("PASS Stable Audio CPU arithmetic: latent length, true pingpong sampler, stereo PCM WAV");
    }
}
