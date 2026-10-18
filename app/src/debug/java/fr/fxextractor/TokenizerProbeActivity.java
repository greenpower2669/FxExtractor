package fr.fxextractor;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public final class TokenizerProbeActivity extends Activity {
    private static final String TAG = "TOKENIZER_PROBE";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView status = new TextView(this);
        status.setTextSize(21);
        status.setText("Checking DJL tokenizer JNI...");
        setContentView(status);
        try {
            String json = "{\"version\":\"1.0\",\"truncation\":null,\"padding\":null,"+
              "\"added_tokens\":[],\"normalizer\":null,"+
              "\"pre_tokenizer\":{\"type\":\"Whitespace\"},"+
              "\"post_processor\":null,\"decoder\":null,"+
              "\"model\":{\"type\":\"WordLevel\",\"vocab\":{\"[UNK]\":0,\"hello\":1},"+
              "\"unk_token\":\"[UNK]\"}}";
            try(HuggingFaceTokenizer tokenizer=HuggingFaceTokenizer.newInstance(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                    Collections.emptyMap())) {
                long[] ids = tokenizer.encode("hello",false,false).getIds();
                if(ids.length!=1 || ids[0]!=1L)
                    throw new IllegalStateException("wrong token IDs: "+java.util.Arrays.toString(ids));
            }
            Log.i(TAG, "PASS_JNI_REAL_TOKENIZER");
            status.setText("PASS_JNI_REAL_TOKENIZER");
        } catch(Throwable error) {
            StringBuilder report=new StringBuilder(error.toString());
            for(Throwable e=error.getCause(); e!=null; e=e.getCause())report.append("\nCaused by: ").append(e);
            Log.e(TAG, "FAIL_JNI_REAL_TOKENIZER "+report,error);
            status.setText("FAIL_JNI_REAL_TOKENIZER\n"+report);
        }
    }
}
