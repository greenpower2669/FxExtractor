package fr.fxextractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * Native Shine MP3 encoder. Encode one selected FX without network access.
 * MP3 is lossy, unlike WAV. Uses 44.1kHz mono/stereo at 128/192 kbit/s.
 *
 * Third-party: Shine, GNU Library GPL v2.0, see THIRD_PARTY_NOTICES.md.
 */
public final class Mp3ShineEncoder {
    private static final int TARGET_RATE = 44100;
    private static final int MONO_KBIT = 128;
    private static final int STEREO_KBIT = 192;

    static {
        System.loadLibrary("shinefx");
    }

    private Mp3ShineEncoder() {}

    private static native long nativeOpen(int channels, int sampleRate, int kbps);
    private static native int nativeSamplesPerPass(long handle);
    private static native byte[] nativeEncode(long handle, short[] interleaved);
    private static native byte[] nativeFlush(long handle);
    private static native void nativeClose(long handle);

    public static void writeMp3(RandomAccessFile wav, WavIO.Info info,
                                FxAnalyzer.Span span, File target) throws IOException {
        long[] region = WavIO.clipRange(info, span);
        long startFrame = region[0];
        long sourceFrames = region[1] / (info.channels * 2L);
        if (sourceFrames < 1) throw new IOException("Extrait audio vide");
        if (info.channels < 1 || info.channels > 2)
            throw new IOException("MP3 nécessite une piste mono ou stéréo");

        long handle = nativeOpen(info.channels, TARGET_RATE,
                info.channels == 1 ? MONO_KBIT : STEREO_KBIT);
        if (handle == 0) throw new IOException("Encodeur MP3 indisponible");
        try {
            int pass = nativeSamplesPerPass(handle);
            if (pass < 1 || pass > 1152)
                throw new IOException("Encodeur MP3 : taille de bloc incorrecte");
            long totalOutFrames = (sourceFrames * TARGET_RATE + info.rate - 1) / info.rate;
            long queued = 0;
            short[] frame = new short[pass * info.channels];
            PcmWindow reader = new PcmWindow(wav, info, startFrame,
                    startFrame + sourceFrames);
            int packets = 0;
            try (OutputStream out = new FileOutputStream(target)) {
                while (queued < totalOutFrames) {
                    if (Thread.currentThread().isInterrupted())
                        throw new IOException("Encodage MP3 interrompu");
                    Arrays.fill(frame, (short)0);
                    int count = (int)Math.min(pass, totalOutFrames - queued);
                    for (int i = 0; i < count; i++) {
                        double absolute = startFrame +
                                (queued + i) * info.rate / (double)TARGET_RATE;
                        long low = (long)Math.floor(absolute);
                        float fraction = (float)(absolute - low);
                        for (int channel = 0; channel < info.channels; channel++) {
                            float a = reader.get(low, channel);
                            float b = reader.get(low + 1, channel);
                            int pcm = Math.round(a + (b - a) * fraction);
                            frame[i * info.channels + channel] =
                                    (short)Math.max(Short.MIN_VALUE,
                                            Math.min(Short.MAX_VALUE, pcm));
                        }
                    }
                    byte[] encoded = nativeEncode(handle, frame);
                    if (encoded == null) throw new IOException("Bloc MP3 non encodé");
                    out.write(encoded);
                    queued += count;
                    packets++;
                }
                byte[] end = nativeFlush(handle);
                if (end == null) throw new IOException("Fin de MP3 invalide");
                out.write(end);
            }
            if (packets == 0 || !target.isFile() || target.length() < 128)
                throw new IOException("MP3 vide après encodage");
        } catch (IOException | RuntimeException failure) {
            target.delete();
            throw failure;
        } finally {
            nativeClose(handle);
        }
    }

    /** PCM16 cache; avoids one random disk I/O per sample while resampling. */
    private static final class PcmWindow {
        private static final int BLOCK_FRAMES = 4096;
        private final RandomAccessFile source;
        private final WavIO.Info info;
        private final long first, last;
        private final byte[] bytes;
        private long base = -1;
        private int count = 0;

        PcmWindow(RandomAccessFile source, WavIO.Info info, long first, long last) {
            this.source = source;
            this.info = info;
            this.first = first;
            this.last = last;
            this.bytes = new byte[BLOCK_FRAMES * info.channels * 2];
        }

        short get(long at, int channel) throws IOException {
            if (at < first) at = first;
            if (at >= last) at = last - 1;
            if (at < base || at >= base + count) {
                base = at;
                count = (int)Math.min(BLOCK_FRAMES, last - base);
                source.seek(info.offset + base * info.channels * 2L);
                source.readFully(bytes, 0, count * info.channels * 2);
            }
            int index = (int)(at - base) * info.channels * 2 + channel * 2;
            return (short)((bytes[index] & 255) | (bytes[index + 1] << 8));
        }
    }
}
