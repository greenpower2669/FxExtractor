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
        System.out.println("PASS Stable Audio CPU arithmetic: latent length, true pingpong sampler, stereo PCM WAV");
    }
}
