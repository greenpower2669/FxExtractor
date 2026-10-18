package fr.fxextractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure JVM amplitude-envelope analysis. RMS can identify sound regions, but
 * cannot identify the source, understand sounds or separate overlapping tracks.
 * All boundaries refer to the original unmodified WAV.
 */
public final class FxAnalyzer {
    private FxAnalyzer() {}
    public static final int DEFAULT_RELEASE_MS = 0;

    public static final class Span {
        public final long startMs, endMs;
        public Span(long startMs, long endMs) {
            this.startMs = startMs; this.endMs = endMs;
        }
    }
    public static final class Result {
        public final List<Span> spans;
        public final double noiseFloor, threshold, peak;
        public final int hopMs, releaseMs;
        Result(List<Span> spans, double noiseFloor, double threshold,
               double peak, int hopMs, int releaseMs) {
            this.spans = spans; this.noiseFloor = noiseFloor;
            this.threshold = threshold; this.peak = peak;
            this.hopMs = hopMs; this.releaseMs = releaseMs;
        }
    }

    /** sensitivity 0.5..2.0, lower is more sensitive. */
    public static Result analyze(double[] rms, int hopMs, double sensitivity) {
        return analyze(rms, hopMs, sensitivity, DEFAULT_RELEASE_MS);
    }

    /**
     * Separate high-threshold onset from low-threshold sustain. Wait for actual
     * silence after the quiet decay, then preserve releaseMs more of the WAV.
     * Adjacent protected releases are merged rather than exporting duplicates.
     */
    public static Result analyze(double[] rms, int hopMs,
                                 double sensitivity, int releaseMs) {
        if (rms == null || hopMs <= 0 || !Double.isFinite(sensitivity)
                || sensitivity < 0.5 || sensitivity > 2.0
                || releaseMs < 0 || releaseMs > 2000)
            throw new IllegalArgumentException("invalid analysis arguments");
        if (rms.length == 0)
            return new Result(new ArrayList<>(), 0, 0, 0, hopMs, releaseMs);
        double[] ordered = rms.clone();
        for (double value : ordered)
            if (!Double.isFinite(value) || value < 0 || value > 1.001)
                throw new IllegalArgumentException("invalid RMS");
        Arrays.sort(ordered);
        double noise = ordered[(int)Math.floor((ordered.length - 1) * 0.20)];
        double peak = ordered[(int)Math.floor((ordered.length - 1) * 0.98)];
        // The onset stays conservative; a quiet tail is allowed to live longer.
        double threshold = Math.max(0.006, Math.max(noise * 2.7, peak * 0.15))
                * sensitivity;
        double tailThreshold = Math.max(0.0018,
                Math.max(noise * 1.5, threshold * 0.12));
        int silenceFrames = Math.max(1, (int)Math.ceil(220.0 / hopMs));
        int padBefore = Math.max(1, (int)Math.ceil(120.0 / hopMs));
        int padAfter = (int)Math.ceil((double)releaseMs / hopMs);
        int minFrames = Math.max(1, (int)Math.ceil(70.0 / hopMs));
        List<Span> spans = new ArrayList<>();
        int begin = -1, lastQuietTail = -1, highFrames = 0;

        for (int i = 0; i <= rms.length; i++) {
            double value = i < rms.length ? rms[i] : 0;
            if (begin == -1) {
                if (i < rms.length && value >= threshold) {
                    begin = i; lastQuietTail = i; highFrames = 1;
                }
            } else {
                if (i < rms.length && value >= threshold) highFrames++;
                if (i < rms.length && value >= tailThreshold) lastQuietTail = i;
                if (i == rms.length || i - lastQuietTail > silenceFrames) {
                    // Do not treat a lone high-energy frame as a separate FX.
                    if (highFrames >= minFrames) {
                        long start = (long)Math.max(0, begin - padBefore) * hopMs;
                        long end = (long)Math.min(rms.length,
                                lastQuietTail + padAfter + 1) * hopMs;
                        if (!spans.isEmpty()
                                && spans.get(spans.size() - 1).endMs >= start) {
                            Span old = spans.remove(spans.size() - 1);
                            spans.add(new Span(old.startMs, Math.max(old.endMs, end)));
                        } else if (spans.size() < 500) {
                            spans.add(new Span(start, end));
                        }
                    }
                    begin = -1; lastQuietTail = -1; highFrames = 0;
                }
            }
        }
        return new Result(spans, noise, threshold, peak, hopMs, releaseMs);
    }
}
