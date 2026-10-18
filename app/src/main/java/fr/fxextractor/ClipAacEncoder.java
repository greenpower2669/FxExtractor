package fr.fxextractor;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Encode one selected WAV clip into genuine AAC in an MP4/M4A container. */
public final class ClipAacEncoder {
    private static final int TARGET_RATE = 44100;
    private ClipAacEncoder() {}

    /** Android local codec only, no network and no third-party codecs. */
    public static void writeM4a(RandomAccessFile source, WavIO.Info info,
                                FxAnalyzer.Span span, File output) throws IOException {
        long[] range = WavIO.clipRange(info, span);
        long from = range[0];
        long frames = range[1] / (info.channels * 2L);
        if (frames <= 0) throw new IOException("Extrait audio vide");
        long totalOutFrames = (frames * TARGET_RATE + info.rate - 1) / info.rate;
        if (totalOutFrames <= 0) throw new IOException("Extrait audio trop court");

        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        boolean started = false, muxerStarted = false;
        boolean inputEnded = false, outputEnded = false;
        int track = -1, samples = 0;
        long queued = 0, lastProgress = System.nanoTime();
        try {
            MediaFormat settings = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, TARGET_RATE, info.channels);
            settings.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            settings.setInteger(MediaFormat.KEY_BIT_RATE,
                    info.channels == 1 ? 96000 : 160000);
            settings.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768);
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(settings, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            started = true;
            muxer = new MediaMuxer(output.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            PcmReader reader = new PcmReader(source, info, from, from + frames);
            MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
            while (!outputEnded) {
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("Encodage audio interrompu");
                if (!inputEnded) {
                    int inIndex = encoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer input = encoder.getInputBuffer(inIndex);
                        if (input == null) throw new IOException("Tampon d'entrée AAC manquant");
                        input.clear();
                        input.order(ByteOrder.LITTLE_ENDIAN);
                        int capacity = input.remaining() / (info.channels * 2);
                        int count = (int)Math.min((long)capacity, totalOutFrames - queued);
                        if (count == 0) {
                            encoder.queueInputBuffer(inIndex, 0, 0,
                                    queued * 1000000L / TARGET_RATE,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            for (int i = 0; i < count; i++) {
                                double position = (queued + i) * (info.rate / (double) TARGET_RATE);
                                long low = from + (long)Math.floor(position);
                                float frac = (float)(position - Math.floor(position));
                                for (int channel = 0; channel < info.channels; channel++) {
                                    float one = reader.get(low, channel);
                                    float two = reader.get(low + 1, channel);
                                    int value = Math.round(one + (two - one) * frac);
                                    input.putShort((short)Math.max(-32768, Math.min(32767, value)));
                                }
                            }
                            int size = count * info.channels * 2;
                            encoder.queueInputBuffer(inIndex, 0, size,
                                    queued * 1000000L / TARGET_RATE, 0);
                            queued += count;
                        }
                        lastProgress = System.nanoTime();
                    }
                }

                int outIndex = encoder.dequeueOutputBuffer(outputInfo, 10000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw new IOException("Le format AAC a changé");
                    track = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                    lastProgress = System.nanoTime();
                } else if (outIndex >= 0) {
                    try {
                        if (outputInfo.size > 0
                                && (outputInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (!muxerStarted) throw new IOException("Piste AAC non initialisée");
                            ByteBuffer encoded = encoder.getOutputBuffer(outIndex);
                            if (encoded == null) throw new IOException("Tampon AAC manquant");
                            encoded.position(outputInfo.offset);
                            encoded.limit(outputInfo.offset + outputInfo.size);
                            muxer.writeSampleData(track, encoded, outputInfo);
                            samples++;
                        }
                        if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                            outputEnded = true;
                    } finally {
                        encoder.releaseOutputBuffer(outIndex, false);
                    }
                    lastProgress = System.nanoTime();
                }
                if (System.nanoTime() - lastProgress > 30_000_000_000L)
                    throw new IOException("Encodeur AAC bloqué depuis 30 secondes");
            }
            if (!muxerStarted || samples == 0)
                throw new IOException("Encodeur AAC n'a produit aucun son");
            muxer.stop();
            muxerStarted = false;
        } catch (Exception failure) {
            output.delete();
            if (failure instanceof IOException) throw (IOException)failure;
            throw new IOException("Encodage M4A non pris en charge : "
                    + failure.getClass().getSimpleName(), failure);
        } finally {
            if (muxer != null) {
                if (muxerStarted) try { muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
            if (encoder != null) {
                if (started) try { encoder.stop(); } catch (Exception ignored) {}
                encoder.release();
            }
        }
    }

    /** Buffered random-access PCM16 frame reader for streaming resampling. */
    private static final class PcmReader {
        private static final int BLOCK_FRAMES = 4096;
        private final RandomAccessFile source;
        private final WavIO.Info info;
        private final long from, end;
        private final byte[] block;
        private long blockFrom = -1;
        private int blockFrames = 0;
        PcmReader(RandomAccessFile source, WavIO.Info info, long from, long end) {
            this.source = source;
            this.info = info;
            this.from = from;
            this.end = end;
            this.block = new byte[BLOCK_FRAMES * info.channels * 2];
        }
        short get(long frame, int channel) throws IOException {
            if (frame < from) frame = from;
            if (frame >= end) frame = end - 1;
            if (frame < blockFrom || frame >= blockFrom + blockFrames) {
                blockFrom = frame;
                blockFrames = (int)Math.min(BLOCK_FRAMES, end - blockFrom);
                source.seek(info.offset + blockFrom * info.channels * 2L);
                source.readFully(block, 0, blockFrames * info.channels * 2);
            }
            int off = (int)(frame - blockFrom) * info.channels * 2 + channel * 2;
            return (short)((block[off] & 255) | (block[off + 1] << 8));
        }
    }
}
