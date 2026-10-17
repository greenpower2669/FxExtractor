import fr.fxextractor.*;
import java.io.*;
import java.util.zip.*;

public class TestFxAnalyzer {
    private static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        double[] env = new double[250];
        for (int i = 0; i < env.length; i++) env[i] = 0.001;
        for (int i = 45; i < 70; i++) env[i] = 0.35;
        for (int i = 140; i < 157; i++) env[i] = 0.25;
        FxAnalyzer.Result result = FxAnalyzer.analyze(env, 20, 1.0);
        check(result.spans.size() == 2, "Two isolated FX required");
        check(result.spans.get(0).startMs <= 900 && result.spans.get(0).endMs >= 1400, "FX1 extent");
        check(FxAnalyzer.analyze(new double[100], 20, 1.0).spans.isEmpty(), "Silence should stay silence");
        double[] noise = new double[90]; java.util.Arrays.fill(noise, 0.03);
        check(FxAnalyzer.analyze(noise, 20, 1.0).spans.isEmpty(), "Continuous noise should not trigger");

        File wav = File.createTempFile("fxextractor-", ".wav");
        File zip = File.createTempFile("fxextractor-", ".zip");
        try {
            try (FileOutputStream out = new FileOutputStream(wav)) {
                int rate = 8000, n = 8000;
                WavIO.header(out, rate, 1, n * 2L);
                for (int i = 0; i < n; i++) {
                    int value = (i > 1500 && i < 3000) ? (i % 2 == 0 ? 22000 : -22000) : 0;
                    out.write(value & 255); out.write((value >>> 8) & 255);
                }
            }
            try (RandomAccessFile input = new RandomAccessFile(wav, "r");
                 OutputStream output = new FileOutputStream(zip)) {
                WavIO.Info info = WavIO.readInfo(input);
                check(info.durationMs() == 1000, "Expected 1s");
                FxAnalyzer.Result scanned = FxAnalyzer.analyze(WavIO.envelope(input, info), 20, 1);
                check(scanned.spans.size() == 1, "1 wav event");
                WavIO.zip(input, info, scanned, "test.wav", output);
            }
            try (ZipFile z = new ZipFile(zip)) {
                check(z.getEntry("events.json") != null, "manifest");
                check(z.getEntry("FX_001.wav") != null, "actual audio");
                try (InputStream in = z.getInputStream(z.getEntry("FX_001.wav"))) {
                    check(in.read() == 'R', "RIFF header in export");
                }
            }
        } finally { wav.delete(); zip.delete(); }
        System.out.println("PASS FxAnalyzer: segmentation/silence/WAV/ZIP");
    }
}
