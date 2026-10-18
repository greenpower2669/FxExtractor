package fr.fxextractor;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decode the first supported audio track of a video or audio file locally,
 * using Android MediaExtractor + MediaCodec. Write a standard PCM16 WAV
 * consumed by the unchanged RMS analyzer; never load the media into RAM.
 */
public final class MediaAudioDecoder {
    private static final long MAX_PCM_BYTES = 200_000_000L;
    private static final long MAX_DURATION_US = 90L * 60 * 1_000_000L;
    private MediaAudioDecoder() {}

    public static WavIO.Info toWav(File source, File target) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        boolean started = false;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = -1;
            MediaFormat input = null;
            String mime = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String type = candidate.getString(MediaFormat.KEY_MIME);
                if (type != null && type.startsWith("audio/")) {
                    track = i; input = candidate; mime = type; break;
                }
            }
            if (track < 0) throw new IOException("Aucune piste audio dans ce fichier.");
            if (input.containsKey(MediaFormat.KEY_DURATION)
                    && input.getLong(MediaFormat.KEY_DURATION) > MAX_DURATION_US)
                throw new IOException("Audio de plus de 90 minutes : découpe d'abord la vidéo.");
            extractor.selectTrack(track);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(input, null, null, 0);
            codec.start();
            started = true;

            int rate = input.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? input.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 0;
            int channels = input.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? input.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 0;
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            validate(rate, channels);
            boolean formatLocked = false;
            long pcmBytes = 0;
            boolean inputEnded = false, outputEnded = false;
            long lastProgress = System.nanoTime();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            byte[] chunk = new byte[16384];
            int chunkSize = 0;

            try (RandomAccessFile wav = new RandomAccessFile(target, "rw")) {
                wav.setLength(0);
                wav.write(new byte[44]); // canonical PCM header filled once decoded
                while (!outputEnded) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Import interrompu");
                    if (!inputEnded) {
                        int idx = codec.dequeueInputBuffer(10000);
                        if (idx >= 0) {
                            ByteBuffer in = codec.getInputBuffer(idx);
                            if (in == null) throw new IOException("Décodeur sans tampon d'entrée");
                            in.clear();
                            int count = extractor.readSampleData(in, 0);
                            if (count < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputEnded = true;
                            } else {
                                long pts = Math.max(0, extractor.getSampleTime());
                                codec.queueInputBuffer(idx, 0, count, pts, 0);
                                extractor.advance();
                            }
                            lastProgress = System.nanoTime();
                        }
                    }

                    int index = codec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat out = codec.getOutputFormat();
                        int newRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int newChannels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int newEncoding = out.containsKey(MediaFormat.KEY_PCM_ENCODING)
                                ? out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                : AudioFormat.ENCODING_PCM_16BIT;
                        validate(newRate, newChannels);
                        if (newEncoding != AudioFormat.ENCODING_PCM_16BIT
                                && newEncoding != AudioFormat.ENCODING_PCM_FLOAT)
                            throw new IOException("Format PCM non pris en charge : " + newEncoding);
                        if (formatLocked && (newRate != rate || newChannels != channels
                                || newEncoding != encoding))
                            throw new IOException("Changement de format audio pendant le décodage");
                        rate = newRate; channels = newChannels; encoding = newEncoding;
                        lastProgress = System.nanoTime();
                    } else if (index >= 0) {
                        try {
                            if (bufferInfo.size > 0
                                    && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                ByteBuffer out = codec.getOutputBuffer(index);
                                if (out == null || bufferInfo.offset < 0 || bufferInfo.size < 0
                                        || bufferInfo.offset + (long) bufferInfo.size > out.capacity())
                                    throw new IOException("Tampon audio décodé incorrect");
                                out.position(bufferInfo.offset);
                                out.limit(bufferInfo.offset + bufferInfo.size);
                                out.order(ByteOrder.nativeOrder());

                                int bps = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
                                int frameBytes = channels * bps;
                                if (bufferInfo.size % frameBytes != 0)
                                    throw new IOException("Tampon PCM non aligné");
                                int frames = bufferInfo.size / frameBytes;
                                int destChannels = Math.min(2, channels);
                                long added = (long) frames * destChannels * 2L;
                                if (pcmBytes + added > MAX_PCM_BYTES
                                        || (pcmBytes + added) / (destChannels * 2L) * 1_000_000L / rate > MAX_DURATION_US)
                                    throw new IOException("Son décodé trop long : 200 Mo / 90 minutes maximum");
                                formatLocked = true;
                                for (int i = 0; i < frames; i++) {
                                    float left = 0, right = 0;
                                    for (int c = 0; c < channels; c++) {
                                        float value = encoding == AudioFormat.ENCODING_PCM_FLOAT
                                                ? out.getFloat() : out.getShort() / 32768.0f;
                                        if (!Float.isFinite(value)) value = 0;
                                        // Multichannel soundtracks: deterministic stereo fold-down.
                                        if (channels <= 2) {
                                            if (c == 0) left = value;
                                            else right = value;
                                        } else {
                                            if ((c & 1) == 0) left += value;
                                            else right += value;
                                        }
                                    }
                                    if (channels > 2) {
                                        left /= (channels + 1) / 2;
                                        right /= channels / 2;
                                    }
                                    short l = pcm16(left);
                                    chunk[chunkSize++] = (byte) l;
                                    chunk[chunkSize++] = (byte) (l >>> 8);
                                    if (destChannels == 2) {
                                        short r = pcm16(right);
                                        chunk[chunkSize++] = (byte) r;
                                        chunk[chunkSize++] = (byte) (r >>> 8);
                                    }
                                    if (chunkSize >= chunk.length - 4) {
                                        wav.write(chunk, 0, chunkSize);
                                        chunkSize = 0;
                                    }
                                }
                                pcmBytes += added;
                            }
                        } finally {
                            codec.releaseOutputBuffer(index, false);
                        }
                        outputEnded = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        lastProgress = System.nanoTime();
                    }
                    if (System.nanoTime() - lastProgress > 30_000_000_000L)
                        throw new IOException("Le décodeur audio ne répond plus");
                }
                if (chunkSize > 0) wav.write(chunk, 0, chunkSize);
                if (pcmBytes == 0) throw new IOException("Piste audio vide ou non décodable");
                ByteArrayOutputStream header = new ByteArrayOutputStream(44);
                WavIO.header(header, rate, Math.min(2, channels), pcmBytes);
                wav.seek(0);
                wav.write(header.toByteArray());
            }
            try (RandomAccessFile check = new RandomAccessFile(target, "r")) {
                return WavIO.readInfo(check);
            }
        } catch (Exception failure) {
            target.delete();
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Format vidéo/audio non décodable sur ce téléphone : "
                    + failure.getClass().getSimpleName(), failure);
        } finally {
            if (codec != null) {
                if (started) try { codec.stop(); } catch (Exception ignored) { }
                try { codec.release(); } catch (Exception ignored) { }
            }
            extractor.release();
        }
    }

    private static void validate(int rate, int channels) throws IOException {
        if (rate < 8000 || rate > 192000 || channels < 1 || channels > 8)
            throw new IOException("Piste audio non prise en charge (" + rate + " Hz, " + channels + " canaux)");
    }

    private static short pcm16(float value) {
        if (value > 1.0f) value = 1.0f;
        if (value < -1.0f) value = -1.0f;
        return (short) Math.round(value * 32767.0f);
    }
}
