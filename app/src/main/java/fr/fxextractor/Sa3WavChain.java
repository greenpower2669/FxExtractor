package fr.fxextractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Exact RIFF/PCM16 stereo joining, with no full-song float arrays in the UI.
 * A segment can include a context prefix used by DiT: it is NEVER appended
 * again. Existing music remains sample-identical after joining.
 * Pure Java, so the chunk boundary contract is testable in GitHub CI.
 */
public final class Sa3WavChain {
    public static final int RATE=StableAudioMath.SAMPLE_RATE;
    private static final int BPF=4;
    private static final int HEADER=44;
    public static final int MAX_FRAMES=RATE*180;
    private Sa3WavChain(){}

    private static void u16(RandomAccessFile f,int n)throws IOException{
        f.write(n&255);f.write((n>>8)&255);
    }
    private static void u32(RandomAccessFile f,long n)throws IOException{
        u16(f,(int)n);u16(f,(int)(n>>16));
    }
    private static int read16(RandomAccessFile f)throws IOException{
        int a=f.readUnsignedByte();return a|(f.readUnsignedByte()<<8);
    }
    private static long read32(RandomAccessFile f)throws IOException{
        return ((long)read16(f))|(((long)read16(f))<<16);
    }
    private static void header(RandomAccessFile file,int frames)throws IOException{
        file.seek(0);
        file.writeBytes("RIFF");u32(file,(long)frames*BPF+36);
        file.writeBytes("WAVEfmt ");u32(file,16);u16(file,1);u16(file,2);
        u32(file,RATE);u32(file,RATE*BPF);u16(file,BPF);u16(file,16);
        file.writeBytes("data");u32(file,(long)frames*BPF);
    }
    private static int verifiedFrames(RandomAccessFile file)throws IOException{
        if(file.length()<HEADER)throw new IOException("WAV too short");
        file.seek(0);
        byte[] riff=new byte[4];file.readFully(riff);
        if(!"RIFF".equals(new String(riff,java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("Invalid RIFF header");
        long riffBytes=read32(file);
        byte[] sig=new byte[8];file.readFully(sig);
        if(!"WAVEfmt ".equals(new String(sig,java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("Unsupported WAV format");
        if(read32(file)!=16 || read16(file)!=1 || read16(file)!=2 ||
           read32(file)!=RATE || read32(file)!=RATE*BPF ||
           read16(file)!=BPF || read16(file)!=16)
            throw new IOException("Expected stereo PCM16 at 44.1kHz");
        byte[] data=new byte[4];file.readFully(data);
        if(!"data".equals(new String(data,java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("Unsupported WAV chunk layout");
        long bytes=read32(file);
        if(bytes % BPF!=0 || riffBytes!=36+bytes || file.length()!=HEADER+bytes ||
           bytes> (long)MAX_FRAMES*BPF)throw new IOException("Inconsistent WAV length");
        return (int)(bytes/BPF);
    }
    public static int frames(File file)throws IOException{
        try(RandomAccessFile src=new RandomAccessFile(file,"r")){return verifiedFrames(src);}
    }
    public static void createEmpty(File output)throws IOException{
        try(RandomAccessFile f=new RandomAccessFile(output,"rw")){
            f.setLength(HEADER);
            header(f,0);f.getFD().sync();
        }
    }
    public static void copyOriginal(File original,File assembled)throws IOException{
        int n=frames(original);
        if(n<=0||n>MAX_FRAMES)throw new IOException("Original audio duration outside range");
        Files.copy(original.toPath(),assembled.toPath(),StandardCopyOption.REPLACE_EXISTING);
        if(frames(assembled)!=n)throw new IOException("Original copy validation failed");
    }
    /** Appends exactly wantedFrames, skipping contextual audio already in assembled. */
    public static int append(File assembled,File clip,int contextFrames,int wantedFrames)
            throws IOException {
        try(RandomAccessFile target=new RandomAccessFile(assembled,"rw");
            RandomAccessFile source=new RandomAccessFile(clip,"r")){
            int old=verifiedFrames(target);
            int total=verifiedFrames(source);
            if(contextFrames<0||wantedFrames<=0||contextFrames+wantedFrames>total ||
               (long)old+wantedFrames>MAX_FRAMES)
                throw new IOException("Invalid WAV context/append range");
            long initialLength=target.length();
            target.seek(initialLength);
            source.seek(HEADER+(long)contextFrames*BPF);
            long remaining=(long)wantedFrames*BPF;
            byte[] buf=new byte[64*1024];
            try{
                while(remaining>0){
                    int n=(int)Math.min(buf.length,remaining);
                    source.readFully(buf,0,n);
                    target.write(buf,0,n);
                    remaining-=n;
                }
                header(target,old+wantedFrames);
                target.getFD().sync();
            }catch(IOException|RuntimeException e){
                target.setLength(initialLength);
                header(target,old);
                throw e;
            }
            return old+wantedFrames;
        }
    }
    /** Reads only a tiny context window, so long songs never enter Java heap. */
    public static float[][] tail(File source,int maxFrames)throws IOException{
        if(maxFrames<=0||maxFrames>RATE*7)throw new IOException("Context max 7s");
        try(RandomAccessFile file=new RandomAccessFile(source,"r")){
            int count=verifiedFrames(file);
            int n=Math.min(count,maxFrames);
            if(n==0)throw new IOException("Empty context source");
            float[][] out={new float[n],new float[n]};
            file.seek(HEADER+(long)(count-n)*BPF);
            for(int i=0;i<n;i++){
                out[0][i]=(short)read16(file)/32768f;
                out[1][i]=(short)read16(file)/32768f;
            }
            return out;
        }
    }
    public static File complete(File assembled,File destination,int expectedFrames)
            throws IOException{
        if(frames(assembled)!=expectedFrames)
            throw new IOException("Incomplete output; expected "+expectedFrames+" frames");
        // Same private-files filesystem: atomic rename where possible.
        try{
            Files.move(assembled.toPath(),destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(java.nio.file.AtomicMoveNotSupportedException ignored){
            Files.move(assembled.toPath(),destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        }
        return destination;
    }
}
