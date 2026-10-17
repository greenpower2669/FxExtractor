package fr.fxextractor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** RIFF/WAVE PCM16 (mono/stereo); streaming scan/export, no full audio buffer. */
public final class WavIO {
    private WavIO() {}
    public static final class Info {
        public final int rate, channels;
        public final long offset, bytes;
        public Info(int rate, int channels, long offset, long bytes) {
            this.rate = rate; this.channels = channels; this.offset = offset; this.bytes = bytes;
        }
        public long frames() { return bytes / (channels * 2L); }
        public long durationMs() { return frames() * 1000L / rate; }
    }
    private static int u16(RandomAccessFile r) throws IOException {
        return r.readUnsignedByte() | (r.readUnsignedByte() << 8);
    }
    private static long u32(RandomAccessFile r) throws IOException {
        return (long)u16(r) | ((long)u16(r) << 16);
    }
    private static void put16(OutputStream o, int n) throws IOException {
        o.write(n & 255); o.write((n >>> 8) & 255);
    }
    private static void put32(OutputStream o, long n) throws IOException {
        put16(o, (int)(n & 65535)); put16(o, (int)((n >>> 16) & 65535));
    }
    public static void header(OutputStream o, int rate, int channels, long pcmBytes) throws IOException {
        if (pcmBytes < 0 || pcmBytes > 0xFFFFFFFFL - 36) throw new IOException("WAV too large");
        o.write("RIFF".getBytes(StandardCharsets.US_ASCII)); put32(o, 36 + pcmBytes);
        o.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)); put32(o, 16);
        put16(o, 1); put16(o, channels); put32(o, rate);
        put32(o, rate * channels * 2L); put16(o, channels * 2); put16(o, 16);
        o.write("data".getBytes(StandardCharsets.US_ASCII)); put32(o, pcmBytes);
    }
    public static Info readInfo(RandomAccessFile r) throws IOException {
        if (r.length() < 44 || r.readInt() != 0x52494646) throw new IOException("Not RIFF");
        u32(r);
        if (r.readInt() != 0x57415645) throw new IOException("Not WAVE");
        int rate = -1, channels = -1, fmt = -1, bits = -1;
        long dataOffset = -1, dataBytes = -1;
        while (r.getFilePointer() + 8 <= r.length()) {
            long pos = r.getFilePointer();
            int id = r.readInt();
            long size = u32(r);
            long start = r.getFilePointer();
            long next = start + size + (size & 1);
            if (next < start || next > r.length() + 1) throw new IOException("Corrupt chunk");
            if (id == 0x666d7420) { // 'fmt '
                if (size < 16) throw new IOException("Bad fmt");
                fmt = u16(r); channels = u16(r); rate = (int)u32(r);
                u32(r); u16(r); bits = u16(r);
            } else if (id == 0x64617461) { // 'data'
                dataOffset = start; dataBytes = Math.min(size, r.length() - start);
            }
            r.seek(Math.min(r.length(), next));
            if (r.getFilePointer() <= pos) throw new IOException("Invalid WAV");
        }
        if (fmt != 1 || bits != 16 || channels < 1 || channels > 2 || rate < 8000 || rate > 192000 ||
            dataOffset < 0 || dataBytes < channels * 2L) throw new IOException("PCM WAV 16-bit mono/stereo required");
        dataBytes -= dataBytes % (channels * 2L);
        return new Info(rate, channels, dataOffset, dataBytes);
    }
    /** 20-ms analysis hop, capped to 90 minutes and 200MB of source WAV. */
    public static double[] envelope(RandomAccessFile r, Info in) throws IOException {
        if (in.bytes > 200_000_000L || in.durationMs() > 90L * 60_000)
            throw new IOException("WAV exceeds 200 MB / 90 minutes");
        int step = Math.max(1, in.rate / 50);
        long count = (in.frames() + step - 1) / step;
        if (count > Integer.MAX_VALUE - 8) throw new IOException("Too many frames");
        List<Double> levels = new ArrayList<>((int)count);
        byte[] buf = new byte[step * in.channels * 2];
        r.seek(in.offset); long remaining = in.frames();
        while (remaining > 0) {
            int frames = (int)Math.min(step, remaining);
            r.readFully(buf, 0, frames * in.channels * 2);
            double squares = 0;
            for (int i = 0; i < frames * in.channels * 2; i += 2) {
                int value = (short)((buf[i] & 255) | (buf[i + 1] << 8));
                double normalized = value / 32768.0;
                squares += normalized * normalized;
            }
            levels.add(Math.sqrt(squares / (frames * in.channels)));
            remaining -= frames;
        }
        double[] a = new double[levels.size()];
        for (int i = 0; i < a.length; i++) a[i] = levels.get(i);
        return a;
    }
    private static String jsonQuote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('\"').toString();
    }
    public static void zip(RandomAccessFile r, Info in, FxAnalyzer.Result result,
                           String name, OutputStream destination) throws IOException {
        byte[] buffer = new byte[32768];
        try (ZipOutputStream z = new ZipOutputStream(destination)) {
            z.putNextEntry(new ZipEntry("events.json"));
            StringBuilder report = new StringBuilder();
            report.append("{\"format\":\"FxExtractor-1\",\"source\":").append(jsonQuote(name))
                    .append(",\"rate\":").append(in.rate).append(",\"channels\":").append(in.channels)
                    .append(",\"durationMs\":").append(in.durationMs()).append(",\"threshold\":")
                    .append(String.format(Locale.ROOT, "%.7f", result.threshold)).append(",\"events\":[");
            for (int i = 0; i < result.spans.size(); i++) {
                FxAnalyzer.Span x = result.spans.get(i);
                if (i > 0) report.append(',');
                report.append("{\"file\":\"FX_").append(String.format(Locale.ROOT, "%03d", i + 1))
                      .append(".wav\",\"startMs\":").append(x.startMs).append(",\"endMs\":").append(x.endMs).append('}');
            }
            report.append("]}");
            z.write(report.toString().getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            for (int i = 0; i < result.spans.size(); i++) {
                FxAnalyzer.Span span = result.spans.get(i);
                long from = Math.max(0, Math.min(in.frames(), span.startMs * in.rate / 1000));
                long to = Math.max(from, Math.min(in.frames(), span.endMs * in.rate / 1000));
                long bytes = (to - from) * in.channels * 2L;
                if (bytes == 0) continue;
                String filename = String.format(Locale.ROOT, "FX_%03d.wav", i + 1);
                z.putNextEntry(new ZipEntry(filename));
                header(z, in.rate, in.channels, bytes);
                r.seek(in.offset + from * in.channels * 2L);
                long left = bytes;
                while (left > 0) {
                    int n = (int)Math.min(left, buffer.length);
                    r.readFully(buffer, 0, n); z.write(buffer, 0, n); left -= n;
                }
                z.closeEntry();
            }
        }
    }
}
