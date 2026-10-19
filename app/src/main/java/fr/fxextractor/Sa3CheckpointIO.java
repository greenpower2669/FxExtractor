package fr.fxextractor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Durable, app-private intermediate tensors; no Binder-sized arrays or network. */
public final class Sa3CheckpointIO {
    private static final int MAGIC = 0x53413331; // SA31
    private static final int MAX_FLOATS = 6_000_000;
    private Sa3CheckpointIO(){}

    public static void write(File directory, String name, float[]... tensors) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create SA3 directory");
        if (!name.matches("[a-z]+\\.bin")) throw new IOException("Invalid checkpoint name");
        File target = new File(directory,name);
        File tmp = new File(directory,name+".tmp");
        boolean success = false;
        try (FileOutputStream stream = new FileOutputStream(tmp);
             DataOutputStream data = new DataOutputStream(stream)) {
            if (tensors.length < 1 || tensors.length > 3) throw new IOException("Invalid tensor count");
            data.writeInt(MAGIC);
            data.writeInt(tensors.length);
            for (float[] tensor : tensors) {
                if (tensor.length < 1 || tensor.length > MAX_FLOATS) throw new IOException("Invalid tensor length");
                data.writeInt(tensor.length);
                for (float number : tensor) data.writeFloat(number);
            }
            data.flush();
            stream.getFD().sync();
        }
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace old checkpoint");
        if (!tmp.renameTo(target)) throw new IOException("Cannot commit checkpoint");
        success = true;
        if (!success && tmp.exists()) tmp.delete();
    }

    public static float[][] read(File directory, String name, int expectedCount) throws IOException {
        if (!name.matches("[a-z]+\\.bin")) throw new IOException("Invalid checkpoint name");
        File file = new File(directory,name);
        try (DataInputStream data = new DataInputStream(new FileInputStream(file))) {
            if (data.readInt() != MAGIC) throw new IOException("Invalid SA3 checkpoint header");
            int count = data.readInt();
            if (count != expectedCount || count < 1 || count > 3) throw new IOException("Invalid tensor count");
            float[][] result = new float[count][];
            long floats = 0;
            for (int i=0; i<count; i++) {
                int n = data.readInt();
                if (n < 1 || n > MAX_FLOATS) throw new IOException("Invalid tensor length");
                floats += n;
                if (floats > MAX_FLOATS*2L) throw new IOException("Checkpoint too large");
                result[i] = new float[n];
                for (int j=0; j<n; j++) result[i][j] = data.readFloat();
            }
            if (data.read()!=-1) throw new IOException("Checkpoint has unexpected trailing data");
            return result;
        }
    }
}
