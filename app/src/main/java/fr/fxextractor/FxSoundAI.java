package fr.fxextractor;

import android.content.Context;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real, on-device YAMNet TensorFlow Lite inference.
 * Bundled model: 16kHz mono float32 [1,15600] -> 521 AudioSet scores.
 * No network API, user clips stay on the phone, and predictions are suggestions.
 */
public final class FxSoundAI implements AutoCloseable {
    private static final int SAMPLES = 15600;
    private static final int SAMPLE_RATE = 16000;
    private static final int CLASSES = 521;
    private static final float MIN_SCORE = 0.14f;
    private final Interpreter interpreter;
    private final String[] labels;
    private final float[] input = new float[SAMPLES];
    private final boolean batchedInput;
    private final float[][] output = new float[1][CLASSES];

    public static final class Prediction {
        public final String label, original;
        public final float score;
        public Prediction(String label, String original, float score) {
            this.label = label; this.original = original; this.score = score;
        }
    }

    public FxSoundAI(Context context) throws IOException {
        ByteBuffer weights;
        try (InputStream stream = context.getAssets().open("yamnet.tflite")) {
            byte[] bytes = readLimited(stream, 24_000_000);
            if (bytes.length < 1_000_000 || bytes[4] != 'T' || bytes[5] != 'F'
                    || bytes[6] != 'L' || bytes[7] != '3')
                throw new IOException("Poids YAMNet invalides ou manquants");
            weights = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            weights.put(bytes).rewind();
        }
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("yamnet_labels.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = line.trim();
                if (!entry.isEmpty()) names.add(entry);
            }
        }
        if (names.size() != CLASSES)
            throw new IOException("Étiquettes YAMNet : " + names.size() + " au lieu de " + CLASSES);
        this.labels = names.toArray(new String[0]);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        this.interpreter = new Interpreter(weights, options);
        int[] inShape = interpreter.getInputTensor(0).shape();
        int[] outShape = interpreter.getOutputTensor(0).shape();
        boolean oneDimensional = inShape.length == 1 && inShape[0] == SAMPLES;
        boolean twoDimensional = inShape.length == 2
                && inShape[0] == 1 && inShape[1] == SAMPLES;
        if (!(oneDimensional || twoDimensional)
                || outShape.length != 2 || outShape[0] != 1 || outShape[1] != CLASSES) {
            interpreter.close();
            throw new IOException("Dimensions de YAMNet inattendues : entrée "
                    + java.util.Arrays.toString(inShape) + ", sortie "
                    + java.util.Arrays.toString(outShape));
        }
        batchedInput = twoDimensional;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int n;
        while ((n = input.read(buf)) != -1) {
            if (out.size() + n > limit) throw new IOException("Modèle IA trop volumineux");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    public Prediction predict(RandomAccessFile wav, WavIO.Info info, FxAnalyzer.Span span)
            throws IOException {
        if (span.endMs <= span.startMs) return null;
        float[] peak = new float[CLASSES];
        long duration = span.endMs - span.startMs;
        // Compare 1-3 short windows: avoid one silent tail dominating inference.
        int windows = duration > 2400 ? 3 : (duration > 1200 ? 2 : 1);
        for (int i = 0; i < windows; i++) {
            long centerMs = span.startMs + duration * (i + 1) / (windows + 1);
            loadWindow(wav, info, centerMs);
            try {
                // Official Google/MediaPipe YAMNet expects rank-1 [15600].
                // Accept rank-2 [1,15600] variants without changing the WAV data.
                interpreter.run(batchedInput ? new float[][] { input } : input, output);
            } catch (RuntimeException e) {
                throw new IOException("Inférence IA impossible", e);
            }
            for (int j = 0; j < CLASSES; j++)
                if (Float.isFinite(output[0][j]) && output[0][j] > peak[j])
                    peak[j] = output[0][j];
        }
        int best = -1;
        float score = MIN_SCORE;
        for (int i = 0; i < CLASSES; i++) {
            // Some broad classes are true predictions and should not be silently
            // substituted by what a game developer might hope to hear.
            if (peak[i] >= score) { score = peak[i]; best = i; }
        }
        if (best < 0) return null;
        String english = labels[best];
        return new Prediction(toFrench(english), english, score);
    }

    private void loadWindow(RandomAccessFile wav, WavIO.Info info, long centerMs)
            throws IOException {
        double ratio = info.rate / (double) SAMPLE_RATE;
        double start = centerMs * (info.rate / 1000.0) - (SAMPLES / 2.0) * ratio;
        long low = Math.max(0, (long) Math.floor(start));
        long high = Math.min(info.frames(), (long) Math.ceil(start + SAMPLES * ratio) + 2);
        int channels = info.channels;
        int stride = channels * 2;
        long availableFrames = Math.max(0, high - low);
        if (availableFrames > 220_000) throw new IOException("Fenêtre audio IA trop grande");
        byte[] data = new byte[(int) availableFrames * stride];
        if (data.length != 0) {
            wav.seek(info.offset + low * stride);
            wav.readFully(data);
        }
        for (int i = 0; i < SAMPLES; i++) {
            double sourcePos = start + i * ratio;
            long lower = (long) Math.floor(sourcePos);
            float fraction = (float) (sourcePos - lower);
            float a = getMono(data, low, lower, info.frames(), channels);
            float b = getMono(data, low, lower + 1, info.frames(), channels);
            input[i] = a + (b - a) * fraction;
        }
    }

    private static float getMono(byte[] block, long origin, long frame,
                                 long totalFrames, int channels) {
        if (frame < 0 || frame >= totalFrames) return 0;
        long pos = (frame - origin) * channels * 2;
        if (pos < 0 || pos + channels * 2 > block.length) return 0;
        int p = (int)pos;
        float sum = 0;
        for (int i = 0; i < channels; i++) {
            int sample = (short) ((block[p] & 255) | (block[p + 1] << 8));
            sum += sample / 32768.0f;
            p += 2;
        }
        return sum / channels;
    }

    public static String toFrench(String original) {
        if (original == null) return "Son";
        String n = original.toLowerCase(Locale.ROOT);
        if (n.contains("explosion") || n.contains("boom") || n.contains("blast")) return "Explosion";
        if (n.contains("gunshot") || n.contains("firecracker") || n.contains("firework"))
            return "Détonation";
        if (n.contains("speech") || n.contains("conversation") || n.contains("narration")
                || n.contains("whisper") || n.contains("shout")) return "Voix";
        if (n.contains("music") || n.contains("musical")) return "Musique";
        if (n.contains("engine") || n.contains("motor") || n.contains("vehicle")
                || n.contains("car passing") || n.contains("accelerating")) return "Moteur";
        if (n.contains("wind") || n.contains("whoosh")) return "Souffle";
        if (n.contains("thunder")) return "Tonnerre";
        if (n.contains("crash") || n.contains("smash") || n.contains("bang")
                || n.contains("impact")) return "Impact";
        if (n.contains("mechanical") || n.contains("machine")) return "Mécanisme";
        if (n.contains("bell") || n.contains("chime")) return "Cloche";
        if (n.contains("beep") || n.contains("alarm")) return "Signal";
        if (n.contains("bird")) return "Oiseau";
        if (n.contains("dog") || n.contains("bark")) return "Chien";
        if (n.contains("cat") || n.contains("meow")) return "Chat";
        if (n.contains("water") || n.contains("splash")) return "Eau";
        if (n.contains("footsteps") || n.contains("walk")) return "Pas";
        if (n.contains("hiss") || n.contains("sizzle")) return "Sifflement";
        if (n.contains("scream")) return "Cri";
        if (n.contains("silence")) return "Silence";
        // Keep unrecognised AudioSet classes instead of making up a label.
        return original;
    }

    @Override public void close() {
        interpreter.close();
    }
}
