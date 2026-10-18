import fr.fxextractor.*;
import java.io.*;

/** Real native MP3 encoding test; ffprobe decodes the output in CI. */
public final class TestMp3Shine {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Output MP3 required");
        File wav = File.createTempFile("shine-clip-", ".wav");
        File mp3 = new File(args[0]);
        try {
            try (FileOutputStream stream = new FileOutputStream(wav)) {
                int samples = 44100, rate = 44100;
                WavIO.header(stream, rate, 1, samples * 2L);
                for (int i = 0; i < samples; i++) {
                    int pcm = (int) (Math.sin(i * Math.PI * 2.0 * 440.0 / rate) * 13000);
                    stream.write(pcm & 255);
                    stream.write((pcm >> 8) & 255);
                }
            }
            try (RandomAccessFile input = new RandomAccessFile(wav, "r")) {
                WavIO.Info info = WavIO.readInfo(input);
                Mp3ShineEncoder.writeMp3(input, info,
                        new FxAnalyzer.Span(100, 850), mp3);
            }
            if (!mp3.isFile() || mp3.length() < 1500)
                throw new AssertionError("No encoded MP3");
            try (RandomAccessFile data = new RandomAccessFile(mp3, "r")) {
                int a = data.readUnsignedByte();
                int b = data.readUnsignedByte();
                if (a != 0xff || (b & 0xe0) != 0xe0)
                    throw new AssertionError("MP3 MPEG frame sync missing");
            }
            System.out.println("PASS Shine JNI: encoded MP3 (" + mp3.length() + " bytes)");
        } finally {
            wav.delete();
        }
    }
}
