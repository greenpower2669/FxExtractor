import fr.fxextractor.Sa3CheckpointIO;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;

public final class TestSa3CheckpointIO {
    private static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        File directory=Files.createTempDirectory("sa3-tensors").toFile();
        float[] hidden=new float[256*768], mask=new float[256];
        for(int i=0;i<hidden.length;i++)hidden[i]=(i%139-70)/139f;
        Arrays.fill(mask,1f);
        Sa3CheckpointIO.write(directory,"text.bin",hidden,mask);
        float[][] recovered=Sa3CheckpointIO.read(directory,"text.bin",2);
        check(Arrays.equals(hidden,recovered[0]),"T5Gemma intermediate mismatched");
        check(Arrays.equals(mask,recovered[1]),"Token mask mismatched");
        check(new File(directory,"text.bin").length()>780000,"Intermediate not persisted");
        check(!new File(directory,"text.bin.tmp").exists(),"Temporary file leaked");
        Sa3CheckpointIO.write(directory,"latents.bin",new float[]{1f,-2f,0.5f});
        check(Sa3CheckpointIO.read(directory,"latents.bin",1)[0][1]==-2f,"Latents mismatched");
        try(RandomAccessFile broken=new RandomAccessFile(new File(directory,"latents.bin"),"rw")){
            broken.setLength(10);
        }
        boolean rejected=false;
        try{Sa3CheckpointIO.read(directory,"latents.bin",1);}
        catch(java.io.IOException expected){rejected=true;}
        check(rejected,"Truncated model checkpoint must fail");
        for(File file:directory.listFiles())file.delete();
        directory.delete();
        System.out.println("SA3 CHECKPOINT IO: PASS");
    }
}
