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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local WAV FX slicer; no storage permission, no network, no recording. */
public final class MainActivity extends Activity {
    private static final int OPEN_WAV=11, SAVE_ZIP=12;
    private static final int FILE_LIMIT=200_000_100;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Button openButton, analyzeButton, exportButton;
    private TextView state, summary, help;
    private LinearLayout events;
    private File current;
    private String name = "audio.wav";
    private volatile FxAnalyzer.Result result;
    private volatile WavIO.Info info;
    private int sensitivityProgress = 33;
    private boolean busy;
    private MediaPlayer player;
    private Runnable stopPreview;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(20),dp(18),dp(30));
        scroll.addView(root);
        TextView title = new TextView(this);
        title.setText("🎛 FxExtractor"); title.setTextSize(28); title.setTextColor(0xFF10253D);
        root.addView(title);
        help = text(root, "Détecter, écouter et découper les événements sonores d'un fichier WAV PCM 16 bits. Tout reste sur ton téléphone.", 18);
        openButton = button(root, "1 · Choisir un son WAV", () -> open());
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
        analyzeButton = button(root, "2 · Analyser les sons", () -> analyze());
        exportButton = button(root, "3 · Exporter les FX en ZIP", () -> save());
        state = text(root, "Choisis un WAV pour commencer.", 18);
        summary = text(root, "", 17);
        events = new LinearLayout(this); events.setOrientation(LinearLayout.VERTICAL);
        root.addView(events);
        text(root, "Limites : les sons superposés à de la musique ne peuvent pas être séparés par une simple détection d'énergie. L'IA Qwen n'est pas incluse dans cette version locale.", 15);
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
    }
    private void setBusy(boolean value, String message) {
        busy=value; if(state!=null)state.setText(message); refresh();
    }
    private void open() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("audio/*"); pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/wav","audio/x-wav","audio/wave","audio/vnd.wave","application/octet-stream"});
        startActivityForResult(pick, OPEN_WAV);
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
        if(requestCode==OPEN_WAV) {
            stopPlayback();
            setBusy(true,"Import local du son...");
            worker.execute(() -> {
                File tmp = new File(getCacheDir(), "fx_input.wav");
                String selected = displayName(uri);
                try (InputStream input = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(tmp)) {
                    if (input==null) throw new IOException("Fichier inaccessible");
                    int count=0, n; byte[] buf = new byte[65536];
                    while((n=input.read(buf))!=-1) {
                        count+=n;
                        if(count>FILE_LIMIT) throw new IOException("Fichier trop gros (>200 Mo)");
                        out.write(buf,0,n);
                    }
                    try(RandomAccessFile wav=new RandomAccessFile(tmp,"r")) {
                        WavIO.readInfo(wav); // fail fast on incompatible/corrupt file
                    }
                    ui.post(() -> {
                        current=tmp; name=selected; result=null; info=null; events.removeAllViews();
                        summary.setText(""); setBusy(false,"Prêt : "+name+"\nLance l'analyse.");
                    });
                } catch(Exception e) {
                    tmp.delete();
                    ui.post(() -> { current=null; result=null; info=null; events.removeAllViews(); summary.setText("");
                        setBusy(false,"Import refusé : "+safe(e)); });
                }
            });
        } else if(requestCode==SAVE_ZIP && result!=null && info!=null && current!=null) {
            FxAnalyzer.Result snapshot = result;
            WavIO.Info format = info; File inputFile=current; String sourceName=name;
            setBusy(true,"Export WAV en cours...");
            worker.execute(() -> {
                try(RandomAccessFile audio = new RandomAccessFile(inputFile,"r")) {
                    // Validate file did not change between analysis and export.
                    WavIO.Info check = WavIO.readInfo(audio);
                    if(check.rate!=format.rate || check.channels!=format.channels || check.bytes!=format.bytes)
                        throw new IOException("Source modifiée");
                    OutputStream dest=getContentResolver().openOutputStream(uri,"wt");
                    if(dest==null) throw new IOException("Destination inaccessible");
                    try(OutputStream output=dest) { WavIO.zip(audio,format,snapshot,sourceName,output); }
                    ui.post(() -> setBusy(false,"Export terminé : "+snapshot.spans.size()+" extraits WAV + events.json"));
                } catch(Exception e) {
                    ui.post(() -> setBusy(false,"Export interrompu : "+safe(e)+" (supprime le ZIP partiel si nécessaire)"));
                }
            });
        }
    }
    private String displayName(Uri uri) {
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst()) return c.getString(0);
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
        File file=current; String sourceName=name; double sens=sensitivity();
        worker.execute(() -> {
            try(RandomAccessFile wav = new RandomAccessFile(file,"r")) {
                WavIO.Info analyzed = WavIO.readInfo(wav);
                FxAnalyzer.Result found = FxAnalyzer.analyze(WavIO.envelope(wav,analyzed),20,sens);
                ui.post(() -> {
                    info=analyzed; result=found;
                    summary.setText(String.format(Locale.FRANCE,
                        "%s · %.1f s · %d Hz · %d canaux\n%d événement(s) · seuil RMS %.4f",
                        sourceName,analyzed.durationMs()/1000.0,analyzed.rate,analyzed.channels,
                        found.spans.size(),found.threshold));
                    events.removeAllViews();
                    if(found.spans.isEmpty()) text(events,"Aucun événement isolé trouvé. Essaie une sensibilité plus élevée (vers la gauche).",17);
                    for(int i=0;i<Math.min(40,found.spans.size());i++) {
                        FxAnalyzer.Span span=found.spans.get(i);
                        int index=i+1;
                        button(events,String.format(Locale.FRANCE,"▶ FX %03d · %.2f à %.2f s",index,
                            span.startMs/1000.0,span.endMs/1000.0),()->preview(span));
                    }
                    if(found.spans.size()>40) text(events,"40 premiers extraits écoutables ; tous seront exportés.",16);
                    setBusy(false,"Analyse terminée. Appuie sur ▶ pour écouter un extrait.");
                });
            } catch(Exception e) { ui.post(()->setBusy(false,"Analyse impossible : "+safe(e))); }
        });
    }
    private void preview(FxAnalyzer.Span span) {
        if(current==null || busy) return;
        stopPlayback();
        try {
            MediaPlayer m=new MediaPlayer(); player=m;
            m.setDataSource(current.getAbsolutePath()); m.prepare();
            m.seekTo((int)Math.min(Integer.MAX_VALUE,span.startMs));
            m.start();
            stopPreview=()->{ if(player==m)stopPlayback(); };
            ui.postDelayed(stopPreview,Math.max(50,span.endMs-span.startMs));
        }catch(Exception e) { stopPlayback(); state.setText("Écoute indisponible : "+safe(e)); }
    }
    private void stopPlayback() {
        if(stopPreview!=null){ui.removeCallbacks(stopPreview);stopPreview=null;}
        if(player!=null){try{player.stop();}catch(Exception ignored){} player.release();player=null;}
    }
    @Override protected void onDestroy() {
        stopPlayback(); worker.shutdownNow(); super.onDestroy();
    }
}
