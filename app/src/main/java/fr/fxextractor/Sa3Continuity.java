package fr.fxextractor;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Pure Java duration alignment and conservative per-chunk audio diagnostics. */
public final class Sa3Continuity {
    public static final int RATE=StableAudioMath.SAMPLE_RATE;
    private static final int LATENT=StableAudioMath.SAMPLES_PER_LATENT;
    private static final int BLOCK=4410; // 100ms at 44.1 kHz
    private static final double QUIET_RMS=0.0025; // -52 dBFS approx
    private Sa3Continuity(){}

    public static int contextFrames(int preferredSeconds,int availableFrames){
        if(preferredSeconds!=2 && preferredSeconds!=4 && preferredSeconds!=6)
            throw new IllegalArgumentException("Contexte attendu : 2, 4 ou 6 secondes");
        if(availableFrames<=0)return 0;
        // SAME-S expects even-size latent windows; keep real PCM only and
        // never add zero-valued padding INSIDE the supposed original.
        int evenLatents=(int)Math.round(preferredSeconds*(double)RATE/(2*LATENT))*2;
        int availableEven=(availableFrames/(2*LATENT))*2;
        int positions=Math.min(evenLatents,availableEven);
        return positions*LATENT;
    }

    public static final class Check {
        public final boolean suspect;
        public final String cause;
        public final double previousRms, newRms;
        public final int longestQuietMs;
        Check(boolean suspect,String cause,double previousRms,double newRms,int quietMs){
            this.suspect=suspect;this.cause=cause;this.previousRms=previousRms;
            this.newRms=newRms;this.longestQuietMs=quietMs;
        }
        @Override public String toString(){
            return "suspect="+suspect+" reason="+cause+" prevRms="+previousRms+
                " newRms="+newRms+" longestQuietMs="+longestQuietMs;
        }
    }
    /** WAV PCM16LE verified by Sa3WavChain, read in small windows not a full file. */
    private static double[] levels(File source,int firstFrame,int frameCount)throws IOException{
        int count=Sa3WavChain.frames(source);
        if(frameCount<=0 || firstFrame<0 || (long)firstFrame+frameCount>count)
            throw new IOException("Invalid measured WAV range");
        int blocks=(frameCount+BLOCK-1)/BLOCK;
        double[] rms=new double[blocks];
        try(RandomAccessFile file=new RandomAccessFile(source,"r")){
            file.seek(44L+firstFrame*4L);
            for(int b=0;b<blocks;b++){
                int frames=Math.min(BLOCK,frameCount-b*BLOCK);
                double sum=0;
                for(int i=0;i<frames;i++){
                    int l=(short)readU16(file),r=(short)readU16(file);
                    double a=l/32768.0,c=r/32768.0;
                    sum+=a*a+c*c;
                }
                rms[b]=Math.sqrt(sum/(2.0*frames));
            }
        }
        return rms;
    }
    private static int readU16(RandomAccessFile f)throws IOException{
        return f.readUnsignedByte()|(f.readUnsignedByte()<<8);
    }
    private static double weightedAverage(double[] levels){
        double s=0;for(double x:levels)s+=x*x;
        return Math.sqrt(s/levels.length);
    }
    /**
     * Conservatively flag a real hole. Do not classify normal soft music or
     * instrumental/drum-only audio as defective. Only newly generated PCM
     * is measured: the 2/4/6s contextual prefix is excluded.
     */
    public static Check inspect(File assembled,File rendered,int contextFrames,int newFrames)
            throws IOException {
        int output=Sa3WavChain.frames(rendered);
        if(contextFrames<0||newFrames<=0||(long)contextFrames+newFrames>output)
            throw new IOException("Audio quality range incorrect");
        // Inspect the WHOLE newly generated section (up to 8 seconds), not
        // just the seam: a later hole can also pollute the next seed.
        // The conditioning prefix is excluded from all quality calculations.
        double[] fresh=levels(rendered,contextFrames,newFrames);
        double newRms=weightedAverage(fresh);
        double prevRms=0;
        int oldFrames=Sa3WavChain.frames(assembled);
        if(oldFrames>0){
            double[] previous=levels(assembled,Math.max(0,oldFrames-RATE*7/10),
                Math.min(oldFrames,RATE*7/10));
            prevRms=weightedAverage(previous);
        }
        int run=0,longest=0;
        for(double rms:fresh){
            if(rms<QUIET_RMS){run++;longest=Math.max(longest,run);}
            else run=0;
        }
        int ms=longest*100;
        // A long near-silent sequence following clearly audible audio is
        // suspicious, but two naturally quiet sources should never be blocked.
        boolean suspect=prevRms>0.014 && (ms>=800 ||
            (fresh.length>=7 && firstQuiet(fresh,7) && newRms<0.007));
        return new Check(suspect,suspect?"near-silent-new-section":"ok",
            prevRms,newRms,ms);
    }
    private static boolean firstQuiet(double[] levels,int n){
        for(int i=0;i<Math.min(n,levels.length);i++)
            if(levels[i]>=QUIET_RMS)return false;
        return levels.length>=n;
    }
}
