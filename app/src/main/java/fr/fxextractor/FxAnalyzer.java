package fr.fxextractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure JVM amplitude-envelope analysis; no inference and no semantic claims. */
public final class FxAnalyzer {
    private FxAnalyzer() {}

    public static final class Span {
        public final long startMs, endMs;
        public Span(long startMs, long endMs) { this.startMs = startMs; this.endMs = endMs; }
    }
    public static final class Result {
        public final List<Span> spans;
        public final double noiseFloor, threshold, peak;
        public final int hopMs;
        Result(List<Span> spans, double noiseFloor, double threshold, double peak, int hopMs) {
            this.spans = spans; this.noiseFloor = noiseFloor; this.threshold = threshold;
            this.peak = peak; this.hopMs = hopMs;
        }
    }
    /** sensitivity 0.5..2.0: smaller values catch quieter events. */
    public static Result analyze(double[] rms, int hopMs, double sensitivity) {
        if (rms == null || hopMs <= 0 || !(sensitivity >= 0.5 && sensitivity <= 2.0))
            throw new IllegalArgumentException("invalid analysis arguments");
        if (rms.length == 0) return new Result(new ArrayList<>(), 0, 0, 0, hopMs);
        double[] ordered = rms.clone();
        for (double value : ordered)
            if (!Double.isFinite(value) || value < 0 || value > 1.001)
                throw new IllegalArgumentException("invalid RMS");
        Arrays.sort(ordered);
        double noise = ordered[(int)Math.floor((ordered.length - 1) * 0.20)];
        double peak = ordered[(int)Math.floor((ordered.length - 1) * 0.98)];
        // Silence and steady ambient noise need not produce an extracted effect.
        double threshold = Math.max(0.006, Math.max(noise * 2.7, peak * 0.15)) * sensitivity;
        double close = threshold * 0.52;
        int gapLimit = Math.max(1, (int)Math.ceil(140.0 / hopMs));
        int padBefore = Math.max(1, (int)Math.ceil(80.0 / hopMs));
        int padAfter = Math.max(1, (int)Math.ceil(100.0 / hopMs));
        int minFrames = Math.max(1, (int)Math.ceil(70.0 / hopMs));
        List<Span> spans = new ArrayList<>();
        int begin = -1, last = -1;
        for (int i = 0; i <= rms.length; i++) {
            double val = i == rms.length ? 0 : rms[i];
            if (begin < 0) {
                if (i < rms.length && val >= threshold) { begin = i; last = i; }
            } else {
                if (i < rms.length && val >= close) last = i;
                if (i == rms.length || i - last > gapLimit) {
                    if (last - begin + 1 >= minFrames) {
                        long start = (long)Math.max(0, begin - padBefore) * hopMs;
                        long end = (long)Math.min(rms.length, last + padAfter + 1) * hopMs;
                        if (!spans.isEmpty() && spans.get(spans.size() - 1).endMs >= start) {
                            Span old = spans.remove(spans.size() - 1);
                            spans.add(new Span(old.startMs, Math.max(old.endMs, end)));
                        } else if (spans.size() < 500) spans.add(new Span(start, end));
                    }
                    begin = -1; last = -1;
                }
            }
        }
        return new Result(spans, noise, threshold, peak, hopMs);
    }
}
