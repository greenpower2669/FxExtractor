import fr.fxextractor.*;
import java.io.*;
import java.util.zip.*;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

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
        // Reverb / motor decays: weak audio below the old close threshold
        // must not be chopped off while clearly above the actual noise floor.
        double[] decay = new double[220];
        java.util.Arrays.fill(decay, 0.001);
        for (int i = 25; i < 38; i++) decay[i] = 0.50;
        for (int i = 38; i < 115; i++) {
            decay[i] = 0.030 * Math.exp(-(i - 38) / 65.0);
        }
        check(FxAnalyzer.DEFAULT_RELEASE_MS == 0, "Default must have no artificial tail");
        FxAnalyzer.Result zeroTail = FxAnalyzer.analyze(decay, 20, 1.0, 0);
        FxAnalyzer.Result implicitTail = FxAnalyzer.analyze(decay, 20, 1.0);
        check(zeroTail.spans.size() == 1 && implicitTail.spans.size() == 1,
                "0ms release must preserve exactly the detected effect");
        check(zeroTail.spans.get(0).endMs == implicitTail.spans.get(0).endMs,
                "Implicit default must equal explicit 0ms");
        FxAnalyzer.Result shortTail = FxAnalyzer.analyze(decay, 20, 1.0, 100);
        FxAnalyzer.Result protectedTail = FxAnalyzer.analyze(decay, 20, 1.0, 1100);
        check(shortTail.spans.get(0).endMs - zeroTail.spans.get(0).endMs >= 100,
                "A positive setting should extend 0ms by its precise duration");
        check(shortTail.spans.size() == 1 && protectedTail.spans.size() == 1,
                "A long fading reverb is one FX");
        check(protectedTail.spans.get(0).endMs > 2400,
                "Protect a quiet reverb after RMS threshold falls");
        check(protectedTail.spans.get(0).endMs - shortTail.spans.get(0).endMs >= 900,
                "Release slider should extend end without cutting tail");
        check(protectedTail.spans.get(0).startMs < 25 * 20,
                "Preserve the beginning of effect");

        double[] separated = new double[300];
        java.util.Arrays.fill(separated, 0.001);
        for(int i=35; i<47; i++) separated[i] = 0.30;
        for(int i=175; i<187; i++) separated[i] = 0.30;
        check(FxAnalyzer.analyze(separated, 20, 1.0, 600).spans.size() == 2,
                "Distant FX should remain separate with padded release");
        check(FxAnalyzer.analyze(new double[10], 20, 1.0, 1500).spans.isEmpty(),
                "Tail padding cannot create audio where there is silence");


        check(WavIO.exportFileName("Explosion spatiale", 1).equals("Explosion_spatiale_001.wav"),
                "Manual names become predictable safe filenames");
        check(WavIO.exportFileName("Éclat / ../ explosion", 2).equals("Eclat_explosion_002.wav"),
                "Accents and path separators must be sanitized");
        check(WavIO.exportFileName("../", 3).equals("FX_003.wav"),
                "Unsafe-only names must fall back to FX");
        check(WavIO.exportFileName(null, 4).equals("FX_004.wav"),
                "Blank labels stay backward compatible");

        File wav = File.createTempFile("fxextractor-", ".wav");
        File zip = File.createTempFile("fxextractor-", ".zip");
        File namedZip = File.createTempFile("fxextractor-named-", ".zip");
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
            try (RandomAccessFile input = new RandomAccessFile(wav, "r");
                 OutputStream output = new FileOutputStream(namedZip)) {
                WavIO.Info info = WavIO.readInfo(input);
                FxAnalyzer.Result scanned = FxAnalyzer.analyze(WavIO.envelope(input, info), 20, 1);
                WavIO.zip(input, info, scanned, "test.wav",
                        Arrays.asList("Explosion spatiale"), output);
            }
            try (ZipFile z = new ZipFile(namedZip)) {
                check(z.getEntry("Explosion_spatiale_001.wav") != null,
                        "Named sound must be included in ZIP");
                try (InputStream in = z.getInputStream(z.getEntry("events.json"))) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    check(json.contains("\"label\":\"Explosion spatiale\""),
                            "Editable label preserved in JSON");
                    check(json.contains("\"file\":\"Explosion_spatiale_001.wav\""),
                            "Report filename must match ZIP");
                }
            }
            try (ZipFile z = new ZipFile(zip)) {
                check(z.getEntry("events.json") != null, "manifest");
                check(z.getEntry("FX_001.wav") != null, "actual audio");
                try (InputStream in = z.getInputStream(z.getEntry("FX_001.wav"))) {
                    check(in.read() == 'R', "RIFF header in export");
                }
            }
        } finally { wav.delete(); zip.delete(); namedZip.delete(); }
        System.out.println("PASS FxAnalyzer: segmentation/silence/WAV/ZIP");
    }
}
