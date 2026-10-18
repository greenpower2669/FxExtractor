package fr.fxextractor;

import java.io.*;
import java.util.Locale;

/** No dependencies: java -cp out fr.fxextractor.FxCli input.wav output.zip [sensitivity] */
public final class FxCli {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: FxCli input.wav output.zip [sensitivity=1]");
            System.exit(2);
        }
        double sensitivity = args.length == 3 ? Double.parseDouble(args[2]) : 1.0;
        try (RandomAccessFile wav = new RandomAccessFile(args[0], "r")) {
            WavIO.Info info = WavIO.readInfo(wav);
            FxAnalyzer.Result result = FxAnalyzer.analyze(WavIO.envelope(wav, info), 20, sensitivity);
            try (OutputStream dest = new FileOutputStream(args[1])) {
                WavIO.zip(wav, info, result, new File(args[0]).getName(), dest);
            }
            System.out.printf(Locale.ROOT, "%d events detected, threshold %.5f%n",
                result.spans.size(), result.threshold);
        }
    }
}
