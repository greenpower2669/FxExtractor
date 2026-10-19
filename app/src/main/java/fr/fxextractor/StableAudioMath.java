package fr.fxextractor;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Pure Java host-side arithmetic for Stable Audio 3's genuine rectified-flow pingpong
 * sampler (reference: Stability-AI/stable-audio-3/optimized/tflite/models/defs/tflite_pipeline.py).
 * No placeholder synth or prerecorded sounds. The DiT still has to provide real velocities.
 *
 * This class is deliberately Android-free to allow byte-for-byte JVM tests in CI.
 */
public final class StableAudioMath {
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNELS = 2;
    public static final int LATENT_CHANNELS = 256;
    public static final int SAMPLES_PER_LATENT = 4096;
    private StableAudioMath() {}

    public static int latentLength(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 120)
            throw new IllegalArgumentException("Durée hors plage : 0 < secondes <= 120");
        return Math.max(1, (int) Math.ceil(seconds * SAMPLE_RATE / SAMPLES_PER_LATENT));
    }

    /** Returns monotonically decreasing sigmas; values at endpoints are exact 1.0 and 0.0. */
    public static float[] pingpongSchedule(int steps, float sigmaMax) {
        if (steps <= 0 || steps > 100) throw new IllegalArgumentException("Étapes invalides");
        if (!(sigmaMax > 0 && sigmaMax <= 1.0f))
            throw new IllegalArgumentException("sigmaMax hors plage");
        float[] out = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = (steps - i) / (float) steps;
            float logsnr = 2.0f - t * (2.0f - (-6.2f));
            float sigmoid = (float) (1.0 / (1.0 + Math.exp(logsnr)));
            out[i] = (i == 0) ? sigmaMax : ((i == steps) ? 0.0f : sigmoid * sigmaMax);
            if (i > 0 && out[i] > out[i - 1])
                throw new IllegalStateException("Ordre sigma invalide");
        }
        return out;
    }

    /**
     * Apply one TRUE SA3 denoising step in latent space, in place:
     * denoised = x - sigmaCurrent * velocity;
     * x_next = (1-sigmaNext)*denoised + sigmaNext*independentGaussian;
     * final sigmaNext==0 gives the denoised latent.
     * The caller must supply the actual neural-network velocity, never a fabricated value.
     */
    public static void pingpongStep(float[] x, float[] neuralVelocity,
                                    float[] nextGaussian, float currentSigma, float nextSigma) {
        if (x == null || neuralVelocity == null || x.length != neuralVelocity.length
                || x.length == 0 || (nextSigma > 0 && (nextGaussian == null || nextGaussian.length != x.length)))
            throw new IllegalArgumentException("Dimensions latentes incompatibles");
        if (!Float.isFinite(currentSigma) || !Float.isFinite(nextSigma)
                || currentSigma < nextSigma || nextSigma < 0 || currentSigma > 1)
            throw new IllegalArgumentException("Horodatage du sampler invalide");
        for (int i = 0; i < x.length; i++) {
            float denoised = x[i] - currentSigma * neuralVelocity[i];
            x[i] = (nextSigma == 0) ? denoised :
                    (1.0f - nextSigma) * denoised + nextSigma * nextGaussian[i];
        }
    }

    /** Emit real RIFF/WAVE stereo PCM16LE, interleaved LR, at 44100 Hz. */
    public static void writeWav(OutputStream out, float[] left, float[] right, int samples)
            throws IOException {
        if (out == null || left == null || right == null || samples <= 0
                || left.length < samples || right.length < samples
                || samples > SAMPLE_RATE * 180)
            throw new IllegalArgumentException("Dimensions audio incorrectes");
        long pcm = (long)samples * CHANNELS * 2;
        if (pcm > Integer.MAX_VALUE - 36) throw new IllegalArgumentException("WAV trop grand");
        out.write(new byte[]{'R','I','F','F'});
        le32(out,(int)pcm+36);
        out.write(new byte[]{'W','A','V','E','f','m','t',' '});
        le32(out,16); le16(out,1); le16(out,CHANNELS);
        le32(out,SAMPLE_RATE); le32(out,SAMPLE_RATE*CHANNELS*2);
        le16(out,CHANNELS*2); le16(out,16);
        out.write(new byte[]{'d','a','t','a'}); le32(out,(int)pcm);
        byte[] chunk = new byte[8192];
        int pos=0;
        for (int i=0; i<samples; i++) {
            int l = pcm16(left[i]), r = pcm16(right[i]);
            chunk[pos++] = (byte)l;
            chunk[pos++] = (byte)(l>>8);
            chunk[pos++] = (byte)r;
            chunk[pos++] = (byte)(r>>8);
            if (pos == chunk.length) {out.write(chunk,0,pos);pos=0;}
        }
        if (pos != 0) out.write(chunk,0,pos);
    }

    private static int pcm16(float f) {
        float clipped = Float.isFinite(f) ? Math.max(-1f,Math.min(1f,f)) : 0f;
        int pcm = Math.round(clipped*32767f);
        return Math.max(-32768,Math.min(32767,pcm));
    }
    private static void le16(OutputStream o,int v)throws IOException{
        o.write(v&255);o.write((v>>>8)&255);
    }
    private static void le32(OutputStream o,int v)throws IOException{
        le16(o,v);le16(o,v>>>16);
    }
}
