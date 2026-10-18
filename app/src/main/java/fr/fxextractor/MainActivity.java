package fr.fxextractor;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.*;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local audio/video FX slicer; no storage permission, no network, no recording. */
public final class MainActivity extends Activity {
    private static final int OPEN_MEDIA=11, SAVE_ZIP=12, SAVE_CLIP=13;
    private static final long SOURCE_FILE_LIMIT=1_000_000_000L;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Button openButton, analyzeButton, exportButton, aiButton;
    private Spinner outputFormat;
    private PendingClip pendingClip;
    private TextView state, summary, help, aiStatus;
    private LinearLayout events;
    private final List<EditText> nameInputs = new ArrayList<>();
    private final List<TextView> aiDetails = new ArrayList<>();
    private File current;
    private String name = "audio.wav";
    private volatile FxAnalyzer.Result result;
    private volatile WavIO.Info info;
    private int sensitivityProgress = 33;
    private int releaseMs = FxAnalyzer.DEFAULT_RELEASE_MS;
    private boolean busy;
    private MediaPlayer player;
    private Runnable stopPreview;
    private static final class PendingClip {
        final File source;
        final WavIO.Info info;
        final FxAnalyzer.Result result;
        final FxAnalyzer.Span span;
        final String filename;
        final int format; // 0: WAV, 1: M4A/AAC, 2: MP3/Shine

        PendingClip(File source, WavIO.Info info, FxAnalyzer.Result result,
                    FxAnalyzer.Span span, String filename, int format) {
            this.source = source;
            this.info = info;
            this.result = result;
            this.span = span;
            this.filename = filename;
            this.format = format;
        }
    }


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(20),dp(18),dp(30));
        scroll.addView(root);
        TextView title = new TextView(this);
        title.setText("🎛 FxExtractor"); title.setTextSize(28); title.setTextColor(0xFF10253D);
        root.addView(title);
        help = text(root, "Choisis une vidéo (MP4, MOV, MKV, WebM, 3GP…) ou un audio (MP3, M4A, WAV, OGG…). Le son sera extrait sur ton téléphone, puis découpé en effets.", 18);
        openButton = button(root, "1 · Choisir une vidéo ou un audio", () -> open());
        TextView sensitivityLabel = text(root, "Sensibilité de détection (plus à gauche = sons plus faibles)", 17);
        SeekBar bar = new SeekBar(this); bar.setMax(100); bar.setProgress(sensitivityProgress);
        root.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                sensitivityProgress = p;
                sensitivityLabel.setText(String.format(Locale.FRANCE,"Sensibilité : %.2f (gauche = plus sensible)", sensitivity()));
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) { if(current != null) result = null; refresh(); }
        });
        TextView releaseLabel = text(root, "Préserver les fins de sons : " + releaseMs + " ms", 17);
        SeekBar releaseBar = new SeekBar(this);
        releaseBar.setMax(30); // 0..1500ms, steps of 50ms
        releaseBar.setProgress(releaseMs / 50);
        root.addView(releaseBar);
        releaseBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                if (!fromUser) return;
                releaseMs = 50 * progress;
                releaseLabel.setText("Préserver les fins de sons : " + releaseMs + " ms");
                result = null; refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        analyzeButton = button(root, "2 · Analyser les sons", () -> analyze());
        text(root, "Format de sortie pour chaque son enregistré individuellement :", 18);
        outputFormat = new Spinner(this);
        ArrayAdapter<String> formats = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[] { "WAV · sans perte", "M4A (AAC) · compressé", "MP3 · compressé" });
        formats.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        outputFormat.setAdapter(formats);
        outputFormat.setSelection(0);
        outputFormat.setContentDescription("Choisir le format de sortie individuel : WAV, M4A ou MP3");
        root.addView(outputFormat);
        text(root, "Sous chaque extrait, « Enregistrer ce son » crée directement un fichier WAV, M4A ou MP3 dans le dossier choisi. Le ZIP reste disponible pour tous les extraits (en WAV).", 15);
        exportButton = button(root, "3 · Exporter tous les FX en ZIP (WAV)", () -> save());
        aiButton = button(root, "🤖 Proposer les noms avec IA locale", () -> suggestNames());
        aiStatus = text(root, "IA : importe une vidéo puis lance l'analyse. Les résultats apparaissent sous chaque extrait.", 17);
        aiStatus.setContentDescription("État de la reconnaissance audio par intelligence artificielle");
        text(root, "🎹 Nouveau : IA MIDI et poids intégrés à l'APK. Elle se charge en mémoire automatiquement quand tu ouvres le laboratoire.", 15);
        button(root, "🎹 Laboratoire IA MIDI v5", () -> startActivity(new Intent(this, MidiAiActivity.class)));
        state = text(root, "Choisis une vidéo ou un son pour commencer.", 18);
        summary = text(root, "", 17);
        events = new LinearLayout(this); events.setOrientation(LinearLayout.VERTICAL);
        root.addView(events);
        text(root, "IA YAMNet locale : suggestions de noms après analyse, toujours modifiables. Les sons superposés ne sont pas séparés ; Qwen n’est pas embarqué.", 15);
        setContentView(scroll);
        refresh();
    }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n+.5f); }
    private double sensitivity() { return 0.5 + sensitivityProgress*0.015; }
    private TextView text(LinearLayout parent, String value, int sp) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(0xFF1A2833);
        t.setTextSize(sp); t.setPadding(0,dp(10),0,dp(10)); parent.addView(t); return t;
    }
    private Button button(LinearLayout parent, String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setTextSize(17);
        b.setAllCaps(false); parent.addView(b); b.setOnClickListener(v -> action.run()); return b;
    }
    private void refresh() {
        if(openButton == null) return;
        openButton.setEnabled(!busy);
        analyzeButton.setEnabled(!busy && current != null);
        exportButton.setEnabled(!busy && result != null && !result.spans.isEmpty());
        // Keep the AI button clickable so it explains prerequisites and ongoing
        // background work instead of appearing inactive or unresponsive.
        if (aiButton != null) aiButton.setEnabled(true);
    }
    private void setBusy(boolean value, String message) {
        busy=value; if(state!=null)state.setText(message); refresh();
    }
    private void open() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("*/*"); pick.addCategory(Intent.CATEGORY_OPENABLE);
        // No restrictive MIME filter: video and audio providers use varied MIME types.
        startActivityForResult(pick, OPEN_MEDIA);
    }
    private void saveClip(int index) {
        if (busy) {
            state.setText("Un traitement est en cours. Patiente jusqu'à sa fin avant d'enregistrer.");
            return;
        }
        FxAnalyzer.Result snapshot = result;
        if (snapshot == null || info == null || current == null
                || index < 0 || index >= snapshot.spans.size() || index >= nameInputs.size()) {
            state.setText("Analyse d'abord les sons pour activer l'enregistrement individuel.");
            return;
        }
        int format = outputFormat.getSelectedItemPosition();
        String label = nameInputs.get(index).getText().toString();
        String filename = WavIO.exportFileName(label, index + 1);
        if (format == 1) filename = filename.substring(0, filename.length() - 4) + ".m4a";
        else if (format == 2) filename = filename.substring(0, filename.length() - 4) + ".mp3";
        pendingClip = new PendingClip(current, info, snapshot, snapshot.spans.get(index),
                filename, format);
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(format == 2 ? "audio/mpeg" : format == 1 ? "audio/mp4" : "audio/wav");
        create.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(create, SAVE_CLIP);
    }

    private void save() {
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE); create.setType("application/zip");
        create.putExtra(Intent.EXTRA_TITLE, "FxExtractor_segments.zip");
        startActivityForResult(create, SAVE_ZIP);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode != RESULT_OK || data==null || data.getData()==null) return;
        Uri uri = data.getData();
        if (requestCode == SAVE_CLIP) {
            PendingClip clip = pendingClip;
            pendingClip = null;
            if (clip == null) {
                state.setText("Enregistrement annulé : choix de fichier expiré.");
                return;
            }
            if (clip.result != result || clip.source != current) {
                state.setText("La source a changé : relance l'enregistrement du son.");
                return;
            }
            setBusy(true, "Enregistrement direct de " + clip.filename + "...");
            worker.execute(() -> {
                File temp = null;
                try (RandomAccessFile wav = new RandomAccessFile(clip.source, "r")) {
                    WavIO.Info check = WavIO.readInfo(wav);
                    if (check.rate != clip.info.rate || check.channels != clip.info.channels
                            || check.bytes != clip.info.bytes)
                        throw new IOException("Le son d'origine a changé");
                    if (clip.format == 1) {
                        temp = File.createTempFile("fx-single-", ".m4a", getCacheDir());
                        ClipAacEncoder.writeM4a(wav, check, clip.span, temp);
                    } else if (clip.format == 2) {
                        temp = File.createTempFile("fx-single-", ".mp3", getCacheDir());
                        Mp3ShineEncoder.writeMp3(wav, check, clip.span, temp);
                    }
                    OutputStream output = getContentResolver().openOutputStream(uri, "wt");
                    if (output == null) throw new IOException("Destination inaccessible");
                    try (OutputStream destination = output) {
                        if (clip.format != 0) {
                            try (InputStream encoded = new FileInputStream(temp)) {
                                byte[] dataBuffer = new byte[32768]; int n;
                                while ((n = encoded.read(dataBuffer)) != -1)
                                    destination.write(dataBuffer, 0, n);
                            }
                        } else {
                            WavIO.writeClip(wav, check, clip.span, destination);
                        }
                    }
                    ui.post(() -> setBusy(false, "Fichier enregistré directement : "
                            + clip.filename + " (sans ZIP)."));
                } catch (Exception | LinkageError e) {
                    String detail = e.getMessage() != null ? e.getMessage()
                            : e.getClass().getSimpleName();
                    ui.post(() -> setBusy(false, "Enregistrement impossible : " + detail
                            + " (vérifie le fichier partiel dans le dossier choisi)."));
                } finally {
                    if (temp != null) temp.delete();
                }
            });
        } else if(requestCode==OPEN_MEDIA) {
            stopPlayback();
            setBusy(true,"Import vidéo/audio en cours...");
            worker.execute(() -> {
                String selected = displayName(uri);
                String extension = ".media";
                int dot = selected.lastIndexOf('.');
                if (dot >= 0 && dot >= selected.length() - 9) {
                    String ext = selected.substring(dot).toLowerCase(Locale.ROOT);
                    if (ext.matches("\\.[a-z0-9]{1,8}")) extension = ext;
                }
                File source = new File(getCacheDir(), "fx_source_" + System.nanoTime() + extension);
                File decoded = new File(getCacheDir(), "fx_decoded_" + System.nanoTime() + ".wav");
                File active = null;
                try {
                    try (InputStream input = getContentResolver().openInputStream(uri);
                         OutputStream out = new FileOutputStream(source)) {
                        if (input == null) throw new IOException("Fichier inaccessible");
                        long count = 0; int n; byte[] buf = new byte[65536];
                        while ((n = input.read(buf)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new IOException("Import interrompu");
                            count += n;
                            if (count > SOURCE_FILE_LIMIT)
                                throw new IOException("Vidéo/audio de plus de 1 Go : sélectionne un extrait plus court");
                            out.write(buf, 0, n);
                        }
                    }
                    WavIO.Info detectedWav = null;
                    try (RandomAccessFile wav = new RandomAccessFile(source, "r")) {
                        detectedWav = WavIO.readInfo(wav);
                    } catch (IOException ignored) {
                        // Not an uncompressed PCM16 WAV: try Android's media decoders.
                    }
                    if (detectedWav != null
                            && (detectedWav.bytes > 200_000_000L
                            || detectedWav.durationMs() > 90L * 60_000L))
                        throw new IOException("Audio PCM trop long (200 Mo / 90 min)");
                    boolean pcmWav = detectedWav != null;
                    if (pcmWav) {
                        active = source;
                    } else {
                        ui.post(() -> state.setText("Décodage de la piste audio de " + selected + "…"));
                        MediaAudioDecoder.toWav(source, decoded);
                        active = decoded;
                    }
                    final File ready = active;
                    final boolean directWav = pcmWav;
                    ui.post(() -> {
                        File previous = current;
                        current = ready;
                        name = selected; result = null; info = null; events.removeAllViews(); nameInputs.clear(); aiDetails.clear();
                        aiStatus.setText("IA : import terminé. Appuie sur « Analyser les sons ».");
                        summary.setText("");
                        if (!directWav) source.delete();
                        if (previous != null && !previous.equals(ready)) previous.delete();
                        setBusy(false, "Prêt : " + name + "\nAudio extrait. Lance l'analyse des FX.");
                    });
                } catch (Exception e) {
                    source.delete(); decoded.delete();
                    ui.post(() -> setBusy(false, "Import impossible : " + safe(e)
                            + "\nEssaie MP4/AAC, M4A, MP3, MKV, WebM ou WAV pris en charge par Android."));
                }
            });
        } else if(requestCode==SAVE_ZIP && result!=null && info!=null && current!=null) {
            FxAnalyzer.Result snapshot = result;
            WavIO.Info format = info; File inputFile=current; String sourceName=name;
            // Read editable UI labels on the main thread before starting I/O.
            List<String> labelSnapshot = new ArrayList<>();
            for (EditText input : nameInputs) labelSnapshot.add(input.getText().toString());
            setBusy(true,"Export WAV en cours...");
            worker.execute(() -> {
                try(RandomAccessFile audio = new RandomAccessFile(inputFile,"r")) {
                    // Validate file did not change between analysis and export.
                    WavIO.Info check = WavIO.readInfo(audio);
                    if(check.rate!=format.rate || check.channels!=format.channels || check.bytes!=format.bytes)
                        throw new IOException("Source modifiée");
                    OutputStream dest=getContentResolver().openOutputStream(uri,"wt");
                    if(dest==null) throw new IOException("Destination inaccessible");
                    try(OutputStream output=dest) { WavIO.zip(audio,format,snapshot,sourceName,labelSnapshot,output); }
                    ui.post(() -> setBusy(false,"Export terminé : "+snapshot.spans.size()+" extraits WAV + events.json"));
                } catch(Exception e) {
                    ui.post(() -> setBusy(false,"Export interrompu : "+safe(e)+" (supprime le ZIP partiel si nécessaire)"));
                }
            });
        }
    }
    private String displayName(Uri uri) {
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst() && c.getString(0)!=null) return c.getString(0);
        }catch(Exception ignored){}
        return "audio.wav";
    }
    private static String safe(Exception e) {
        return e.getMessage()!=null?e.getMessage():e.getClass().getSimpleName();
    }
    private void analyze() {
        if(current==null) return;
        stopPlayback();
        setBusy(true,"Mesure de l'énergie et des silences...");
        File file=current; String sourceName=name; double sens=sensitivity(); int release=releaseMs;
        worker.execute(() -> {
            try(RandomAccessFile wav = new RandomAccessFile(file,"r")) {
                WavIO.Info analyzed = WavIO.readInfo(wav);
                FxAnalyzer.Result found = FxAnalyzer.analyze(WavIO.envelope(wav,analyzed),20,sens,release);
                ui.post(() -> {
                    info=analyzed; result=found;
                    summary.setText(String.format(Locale.FRANCE,
                        "%s · %.1f s · %d Hz · %d canaux\\n%d événement(s) · seuil RMS %.4f · fin +%d ms",
                        sourceName,analyzed.durationMs()/1000.0,analyzed.rate,analyzed.channels,
                        found.spans.size(),found.threshold,found.releaseMs));
                    events.removeAllViews();
                    nameInputs.clear();
                    aiDetails.clear();
                    if (!found.spans.isEmpty())
                        text(events, "Écoute chaque effet, puis donne-lui un nom : Explosion, Moteur, Voix… Le nom sera utilisé dans les fichiers WAV.", 17);
                    if(found.spans.isEmpty()) text(events,"Aucun événement isolé trouvé. Essaie une sensibilité plus élevée (vers la gauche).",17);
                    for(int i=0;i<Math.min(40,found.spans.size());i++) {
                        FxAnalyzer.Span span=found.spans.get(i);
                        int index=i+1;
                        button(events,String.format(Locale.FRANCE,"▶ FX %03d · %.2f à %.2f s",index,
                            span.startMs/1000.0,span.endMs/1000.0),()->preview(span));
                        EditText customName = new EditText(this);
                        customName.setSingleLine(true);
                        customName.setTextSize(18);
                        customName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                        customName.setHint("Nom, par ex. Explosion");
                        customName.setContentDescription("Nom du son numéro " + index + ", modifiable");
                        events.addView(customName);
                        nameInputs.add(customName);
                        button(events, "⬇ Enregistrer ce son (sans ZIP)", () -> saveClip(index - 1));
                        TextView aiScore = text(events, "IA : reconnaissance en attente", 15);
                        aiDetails.add(aiScore);
                    }
                    if(found.spans.size()>40)
                        text(events,"40 premiers extraits disponibles individuellement ; les suivants sont toujours inclus dans le ZIP WAV.",16);
                    setBusy(false, "Découpe terminée : " + found.spans.size() + " extraits.");
                    if (!found.spans.isEmpty()) suggestNames();
                    else aiStatus.setText("IA : aucun son isolé à reconnaître. Modifie la sensibilité puis relance l'analyse.");
                });
            } catch(Exception e) { ui.post(()->setBusy(false,"Analyse impossible : "+safe(e))); }
        });
    }
    /** AI runs on a background worker; never upload or transmit personal sounds. */
    private void suggestNames() {
        FxAnalyzer.Result analyzed = result;
        WavIO.Info format = info;
        File source = current;
        if (busy) {
            if (aiStatus != null)
                aiStatus.setText("IA : une opération est déjà en cours. Regarde l'état affiché ici.");
            return;
        }
        if (analyzed == null || format == null || source == null) {
            aiStatus.setText("IA : choisis une vidéo puis appuie sur « 2 · Analyser les sons » avant de lancer la reconnaissance.");
            return;
        }
        int count = Math.min(40, analyzed.spans.size());
        if (count == 0) {
            aiStatus.setText("IA : aucun événement sonore détecté. Modifie la sensibilité.");
            return;
        }
        String loading = "🤖 YAMNet : reconnaissance hors ligne, 0/" + count + " extrait(s).";
        aiStatus.setText(loading);
        for (TextView detail : aiDetails) detail.setText("IA : analyse en cours…");
        setBusy(true, loading);
        worker.execute(() -> {
            try (FxSoundAI classifier = new FxSoundAI(getApplicationContext());
                 RandomAccessFile wav = new RandomAccessFile(source, "r")) {
                WavIO.Info live = WavIO.readInfo(wav);
                if (live.rate != format.rate || live.channels != format.channels
                        || live.bytes != format.bytes)
                    throw new IOException("Source audio modifiée depuis l'analyse");
                for (int i = 0; i < count; i++) {
                    if (Thread.currentThread().isInterrupted())
                        throw new IOException("Reconnaissance interrompue");
                    FxSoundAI.Prediction prediction =
                            classifier.predict(wav, live, analyzed.spans.get(i));
                    final int position = i;
                    ui.post(() -> {
                        if (result != analyzed || position >= nameInputs.size()) return;
                        EditText nameInput = nameInputs.get(position);
                        TextView detail = aiDetails.get(position);
                        if (prediction == null) {
                            detail.setText("IA : reconnaissance incertaine. Donne un nom manuellement.");
                        } else {
                            // The AI is advisory: never replace Fab's corrections.
                            if (nameInput.getText().toString().trim().isEmpty())
                                nameInput.setText(prediction.label);
                            detail.setText(String.format(Locale.FRANCE,
                                    "IA : %s · score %.0f %% · nom toujours modifiable",
                                    prediction.original, prediction.score * 100));
                        }
                        String progress = "🤖 IA : " + (position + 1) + "/" + count
                                + " extraits reconnus.";
                        aiStatus.setText(progress);
                        state.setText(progress);
                    });
                }
                ui.post(() -> {
                    if (result == analyzed) {
                        aiStatus.setText("IA : reconnaissance terminée pour " + count
                                + " extrait(s). Vérifie les noms, puis exporte.");
                        setBusy(false, "Reconnaissance IA terminée.");
                    }
                });
            } catch (Exception | LinkageError failure) {
                ui.post(() -> {
                    if (result == analyzed) {
                        String msg = "IA indisponible : " +
                                (failure.getMessage() != null
                                    ? failure.getMessage()
                                    : failure.getClass().getSimpleName());
                        aiStatus.setText(msg + ". Les noms manuels restent disponibles.");
                        for (TextView detail : aiDetails) {
                            if (detail.getText().toString().contains("en cours"))
                                detail.setText("IA : échec. Saisie manuelle disponible.");
                        }
                        setBusy(false, msg);
                    }
                });
            }
        });
    }

    private void preview(FxAnalyzer.Span span) {
        if (current == null || busy) return;
        stopPlayback();
        try {
            MediaPlayer m = new MediaPlayer(); player = m;
            m.setDataSource(current.getAbsolutePath());
            m.prepare();
            m.setOnCompletionListener(mp -> { if (player == m) stopPlayback(); });
            m.seekTo((int)Math.min(Integer.MAX_VALUE, span.startMs));
            m.start();
            // Do not stop based on elapsed wall time: seeking and startup
            // previously consumed part of the scheduled preview duration.
            Runnable watcher = new Runnable() {
                @Override public void run() {
                    if (player != m) return;
                    try {
                        if (m.getCurrentPosition() >= span.endMs - 20) stopPlayback();
                        else ui.postDelayed(this, 65);
                    } catch (Exception e) { stopPlayback(); }
                }
            };
            stopPreview = watcher;
            ui.postDelayed(watcher, 65);
        } catch (Exception e) {
            stopPlayback();
            state.setText("Écoute indisponible : " + safe(e));
        }
    }
    private void stopPlayback() {
        if(stopPreview!=null){ui.removeCallbacks(stopPreview);stopPreview=null;}
        if(player!=null){try{player.stop();}catch(Exception ignored){} player.release();player=null;}
    }
    @Override protected void onDestroy() {
        pendingClip = null;
        stopPlayback(); worker.shutdownNow(); super.onDestroy();
    }
}
