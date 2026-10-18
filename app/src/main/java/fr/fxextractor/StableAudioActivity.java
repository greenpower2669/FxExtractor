package fr.fxextractor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent experiment, not a fake audio generator. The MIDI and FX features remain
 * accessible regardless of whether the separate Stable Audio model pack is complete.
 */
public final class StableAudioActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView state;
    private Button generate;

    private TextView label(LinearLayout parent, String content, int size) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextColor(Color.rgb(25, 38, 59));
        t.setTextSize(size);
        t.setPadding(12, 16, 12, 16);
        parent.addView(t);
        return t;
    }

    private Button button(LinearLayout parent, String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title); b.setTextSize(18); b.setAllCaps(false);
        b.setMinHeight(65); parent.addView(b);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(20, 20, 20, 30);
        scroll.addView(content);
        label(content, "🎼 Musique audio IA — Stable Audio 3 Small-Music", 26);
        label(content,
            "Second laboratoire entièrement indépendant du MIDI v5. " +
            "Il devra générer de véritables fichiers WAV 44,1 kHz stéréo à partir d’une " +
            "description, directement sur le processeur du téléphone, sans réseau.", 18);
        label(content,
            "Le modèle, l’encodeur de texte et le décodeur doivent être livrés dans l’APK. " +
            "Le programme vérifie leur présence avant toute tentative de génération.", 17);
        this.state = label(content, "⏳ Vérification du moteur et des poids installés…", 19);
        this.state.setContentDescription("Diagnostic de disponibilité de l'intelligence artificielle musicale");
        generate = button(content, "🎵 Générer une musique WAV", () -> { });
        generate.setEnabled(false);
        button(content, "🔍 Vérifier les modèles intégrés", this::checkAssets);
        button(content, "↩ Retour à FxExtractor", this::finish);
        setContentView(scroll);
        checkAssets();
    }

    private void checkAssets() {
        state.setText("⏳ Vérification du pack local Stable Audio 3…");
        generate.setEnabled(false);
        worker.execute(() -> {
            String[] models = {
                "sa3/sa3-sm-music/dit_w8a8.tflite",
                "sa3/t5gemma/encoder_fp16.tflite",
                "sa3/same-s/dec_w8a8.tflite",
                "sa3/tokenizer.model"
            };
            int found = 0;
            long total = 0;
            StringBuilder details = new StringBuilder();
            for (String model : models) {
                try (InputStream input = getAssets().open(model)) {
                    // Only read the first few bytes, not the 1.2 GB contents into RAM.
                    byte[] header = new byte[8];
                    if (input.read(header) != header.length)
                        throw new IOException("En-tête incomplet");
                    if (model.endsWith(".tflite") &&
                        (header[4] != 'T' || header[5] != 'F' || header[6] != 'L' || header[7] != '3'))
                        throw new IOException("Format LiteRT invalide");
                    long bytes;
                    try {
                        try (android.content.res.AssetFileDescriptor descriptor = getAssets().openFd(model)) {
                            bytes = descriptor.getLength();
                        }
                    } catch (IOException noDescriptor) {
                        // Compressed assets have no descriptor and cannot support zero-copy
                        // model mapping, so reject them instead of consuming a huge heap.
                        throw new IOException("Modèle compressé dans l’APK (mapping impossible)");
                    }
                    if (bytes < (model.endsWith(".model") ? 1_000_000 : 10_000_000))
                        throw new IOException("Fichier incomplet : " + bytes + " octets");
                    found++; total += bytes;
                    details.append("✅ ").append(model).append(" : ")
                           .append(String.format(Locale.FRANCE, "%.1f Mio", bytes/1048576.0))
                           .append("\n");
                } catch (IOException missing) {
                    details.append("❌ ").append(model).append(" : ")
                           .append(missing.getMessage()).append("\n");
                }
            }
            final int count = found;
            final long installed = total;
            final String report = details.toString();
            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                // Even four present assets do NOT prove the sampler/tokenizer works.
                // Enable only after the real native pipeline passes verification.
                state.setText(report + "\n" + (count == models.length
                    ? "✅ Quatre fichiers présents (" + String.format(Locale.FRANCE, "%.0f Mio", installed/1048576.0) +
                       "). Portage et test de génération réelle encore nécessaires avant d'activer le bouton."
                    : "⚠️ Le pack Stable Audio 3 complet n'est pas encore livré. La génération reste désactivée pour éviter un faux résultat."));
            });
        });
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
