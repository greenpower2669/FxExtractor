package fr.fxextractor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.CheckBox;
import android.media.MediaMetadataRetriever;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Two modes: genuine text-to-music, or user audio priming via SAME-S encoder.
 * No Internet permission. A real model must render before export/play.
 * The UI is accessible and does not pretend assets are loaded when absent.
 */
public final class StableAudioActivity extends Activity {
    private static final int PICK_SEED = 21;
    private static final int SAVE_WAV = 22;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView state, seedName, durationName, variationName;
    private Button generate, play, export, pick, clear;
    private EditText prompt;
    private SeekBar duration, variation;
    private CheckBox extensionMode;
    private Uri seedUri;
    private File audioFile;
    private MediaPlayer player;
    private Sa3Coordinator pipeline;
    private volatile boolean busy=false;
    private volatile boolean packPresent=false;

    private TextView label(LinearLayout parent, String content, int size) {
        TextView t = new TextView(this);
        t.setText(content);t.setTextColor(Color.rgb(25,38,59));t.setTextSize(size);
        t.setPadding(12,16,12,16);parent.addView(t);return t;
    }
    private Button button(LinearLayout parent,String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setTextSize(18);
        b.setAllCaps(false);b.setMinHeight(65);parent.addView(b);
        b.setOnClickListener(v->action.run());return b;
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FxDebugLog.lifecycle("StableAudioActivity.onCreate");
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,20,20,45);
        scroll.addView(root);
        label(root,"🎼 Musique audio IA — Stable Audio 3",26);
        if(FxDebugLog.hasPending())label(root,FxDebugLog.pendingSummary()+" — touche « Voir journal » pour copier le diagnostic.",19);
        button(root,"🧪 Voir / copier le journal de débogage",()->FxDebugLog.show(this));
        label(root,"Création de musique directement à partir d’un texte, ou en prenant un extrait sonore comme amorce. Toutes les opérations se feront sur ce téléphone, sans envoyer ton son sur Internet.",17);
        label(root,"Décris les instruments, l’ambiance et le rythme (le modèle comprend surtout l’anglais) :",18);
        prompt=new EditText(this);prompt.setTextSize(18);prompt.setMinLines(3);
        prompt.setSingleLine(false);prompt.setText("Upbeat retro arcade synthpop, bouncy bass, crisp drums, joyful electronic melody, instrumental.");
        root.addView(prompt);
        extensionMode=new CheckBox(this);
        extensionMode.setText("🔁 Prolonger le son original jusqu'à +60 secondes");
        extensionMode.setTextSize(19);
        root.addView(extensionMode);
        label(root,"Durée de génération :",18);
        durationName=label(root,"8 secondes",17);
        duration=new SeekBar(this);duration.setMax(29);duration.setProgress(7);
        root.addView(duration);
        duration.setOnSeekBarChangeListener(new SimpleSeek(){@Override public void onProgressChanged(SeekBar s,int p,boolean user){
            durationName.setText(extensionMode.isChecked()?"Ajouter +"+(p+1)+" secondes":(p+1)+" secondes");
        }});
        seedName=label(root,"Amorce : aucune. Création libre à partir du texte.",18);
        pick=button(root,"🎧 Choisir un morceau de son en amorce",this::selectSeed);
        clear=button(root,"✖ Retirer l’amorce (création libre)",()->{seedUri=null;seedName.setText("Amorce : aucune. Création libre à partir du texte.");});
        variationName=label(root,"Force de variation : 65 % (avec amorce)",17);
        variation=new SeekBar(this);variation.setMax(80);variation.setProgress(45);
        root.addView(variation);
        variation.setOnSeekBarChangeListener(new SimpleSeek(){@Override public void onProgressChanged(SeekBar s,int p,boolean user){
            if(!extensionMode.isChecked())variationName.setText("Force de variation : "+(p+20)+" % (20 % fidèle, 100 % libre)");
        }});
        label(root,"🧠 Mémoire DiT — méthode CPU (comparaison expérimentale)",19);
        label(root,"Cache fichier : tentative d'économie de RAM. CPU historique : témoin 0.4.5. "+
            "Les trois modes utilisent la même IA sans changer ses poids.",16);
        Spinner cacheChoice=new Spinner(this);
        String[] modes={
            "CPU historique — sans cache supplémentaire",
            "CPU XNNPACK — cache mémoire",
            "CPU XNNPACK — cache fichier (à essayer en premier)"
        };
        ArrayAdapter<String> choices=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,modes);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cacheChoice.setAdapter(choices);
        cacheChoice.setMinimumHeight(80);
        cacheChoice.setSelection(Sa3CacheSettings.normalize(getPreferences(MODE_PRIVATE)
            .getInt("sa3_dit_cache_mode",Sa3CacheSettings.DEFAULT)));
        cacheChoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                getPreferences(MODE_PRIVATE).edit().putInt("sa3_dit_cache_mode",
                    Sa3CacheSettings.normalize(position)).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        root.addView(cacheChoice);
        state=label(root,"⏳ Vérification de la présence des modèles IA dans l’APK…",18);
        state.setContentDescription("Journal de progression de la génération musicale");
        generate=button(root,"🎵 Générer une musique WAV",this::generate);
        generate.setEnabled(false);
        button(root,"🔬 Tester le DiT seul (mémoire)",this::probeDiT);
        play=button(root,"▶ Écouter la musique",this::play);
        play.setEnabled(false);
        export=button(root,"💾 Enregistrer la musique au format WAV",this::save);
        export.setEnabled(false);
        extensionMode.setOnCheckedChangeListener((button,checked)->{
            duration.setMax(checked?59:29);
            duration.setProgress(checked?9:7);
            variation.setEnabled(!checked);
            variationName.setText(checked?
                "Prolongation : original conservé, nouvelle suite créée par IA":
                "Force de variation : "+(variation.getProgress()+20)+" %");
            durationName.setText(checked?"Ajouter +10 secondes":"8 secondes");
        });
        button(root,"📜 Licences Stable Audio / Gemma",this::readLicenses);
        button(root,"🔍 Vérifier les modèles intégrés",this::checkAssets);
        button(root,"↩ Retour à FxExtractor",this::finish);
        setContentView(scroll);
        checkAssets();
    }
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener{
        @Override public void onStartTrackingTouch(SeekBar s){}
        @Override public void onStopTrackingTouch(SeekBar s){}
    }
    private void checkAssets() {
        state.setText("⏳ Contrôle du pack Stable Audio 3…");
        FxDebugLog.write("ASSET_CHECK_BEGIN");
        generate.setEnabled(false);
        worker.execute(()->{
            String[] required={
                "sa3/sa3-sm-music/dit_w8a8.tflite",
                "sa3/t5gemma/encoder_fp16.tflite",
                "sa3/same-s/dec_w8a8.tflite",
                "sa3/same-s/enc_w8a8.tflite",
                "sa3/tokenizer.model",
                "sa3/tokenizer.json",
                "sa3/LICENSE.md",
                "sa3/LICENSE_GEMMA.md",
                "sa3/NOTICE"
            };
            StringBuilder report=new StringBuilder();
            int count=0;long total=0;
            for(String asset:required){
                try(InputStream input=getAssets().open(asset)) {
                    byte[] head=new byte[8];
                    if(input.read(head)!=8)throw new IOException("En-tête incomplet");
                    if(asset.endsWith(".tflite")&&(head[4]!='T'||head[5]!='F'||head[6]!='L'||head[7]!='3'))
                        throw new IOException("Poids LiteRT invalides");
                    long size;
                    try(android.content.res.AssetFileDescriptor fd=getAssets().openFd(asset)) {
                        size=fd.getLength();
                    } catch(IOException compressed) {
                        if(asset.endsWith(".tflite"))
                            throw new IOException("Modèle compressé dans l’APK");
                        size=1; // text notices & tokenizer can be compressed
                    }
                    if(asset.endsWith(".tflite") && size<40_000_000L)
                        throw new IOException("Modèle incomplet");
                    if(asset.endsWith(".model")&&size<1_000_000L)
                        throw new IOException("Tokeniseur incomplet");
                    count++;total+=size;
                }catch(Exception error) {
                    report.append("❌ ").append(asset).append(" : ")
                          .append(error.getMessage()).append("\n");
                }
            }
            final boolean complete=count==required.length;
            FxDebugLog.write("ASSET_CHECK_END found="+count+"/"+required.length+" errors="+report.toString().replace("\n",";"));
            final long bytes=total;
            final String errors=report.toString();
            ui.post(()->{
                if(isFinishing()||isDestroyed())return;
                packPresent=complete;
                if(complete){
                    state.setText("✅ Neuf ressources locales vérifiées ("+
                        String.format(Locale.FRANCE,"%.0f Mio",bytes/1048576.0)+
                        "). Modèles installés ; prêt pour l’essai de génération musicale. "+
                        "Le premier calcul peut être long sur téléphone.");
                }else{
                    state.setText("⚠️ Stable Audio 3 n’est pas encore intégralement embarqué dans cet APK. "+
                        "Génération désactivée pour éviter de te donner un résultat factice.\n"+errors);
                }
                generate.setEnabled(complete && !busy);
            });
        });
    }
    private void selectSeed() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,PICK_SEED);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==PICK_SEED){
            seedUri=uri;
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(Exception ignored){}
            String name="extrait sélectionné";
            try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){
                if(c!=null&&c.moveToFirst()){
                    int col=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(col>=0)name=c.getString(col);
                }
            }catch(Exception ignored){}
            seedName.setText("🎧 Amorce : "+name+
                ". FxExtractor utilisera ce son pour guider la variation, pas seulement pour le coller au début.");
        }else if(request==SAVE_WAV && audioFile!=null) {
            final File selected=audioFile;
            worker.execute(()->{
                try(InputStream source=new FileInputStream(selected);
                    java.io.OutputStream output=getContentResolver().openOutputStream(uri)){
                    if(output==null)throw new IOException("Destination inaccessible");
                    byte[] buffer=new byte[32768];int n;
                    while((n=source.read(buffer))!=-1)output.write(buffer,0,n);
                    ui.post(()->state.setText("✅ Musique WAV enregistrée."));
                }catch(Exception e){ui.post(()->state.setText("❌ Export : "+e.getMessage()));}
            });
        }
    }
    private void setBusy(boolean value,String status){
        busy=value;state.setText(status);generate.setEnabled(!busy && packPresent);
        pick.setEnabled(!busy);clear.setEnabled(!busy);
        extensionMode.setEnabled(!busy);
        play.setEnabled(!busy&&audioFile!=null);export.setEnabled(!busy&&audioFile!=null);
    }
    private void generate(){
        if(busy||!packPresent)return;
        if(!getPreferences(MODE_PRIVATE).getBoolean("sa3_licence_accept",false)){
            new android.app.AlertDialog.Builder(this)
                .setTitle("Licences des modèles Stable Audio et Gemma")
                .setMessage("Cette IA embarque Stable Audio 3 (licence Stability AI Community) et T5Gemma (conditions Google Gemma). "+
                    "La redistribution et certains usages sont encadrés par ces licences. "+
                    "Lis les textes complets grâce au bouton « Licences Stable Audio / Gemma ». "+
                    "En continuant, tu acceptes ces conditions, notamment les restrictions d’utilisation Gemma. "+
                    "Powered by Stability AI.")
                .setPositiveButton("J'accepte", (dialog,which)->{
                    getPreferences(MODE_PRIVATE).edit().putBoolean("sa3_licence_accept",true).apply();
                    generate();
                })
                .setNegativeButton("Annuler",(dialog,which)->state.setText("Conditions non acceptées. Génération non lancée."))
                .show();
            return;
        }
        final String description=prompt.getText().toString().trim();
        if(description.isEmpty()){state.setText("Écris une ambiance musicale.");return;}
        final float added=duration.getProgress()+1;
        final boolean extend=extensionMode.isChecked();
        if(extend && seedUri==null){
            state.setText("Pour prolonger un son, choisis d'abord un extrait.");
            return;
        }
        final float strength=(variation.getProgress()+20)/100f;
        final Uri selected=seedUri;
        final long randomSeed=new java.security.SecureRandom().nextLong();
        final String session=Sa3Coordinator.newSession();
        audioFile=null;
        FxDebugLog.begin(extend?"outpaint":selected!=null?"audio-prime":"text",added,selected!=null);
        setBusy(true,"⏳ Préparation des données, puis lancement des trois processus indépendants…");
        worker.execute(()->{
            try{
                float originalSeconds=0f;
                if(selected!=null){
                    if(extend){
                        MediaMetadataRetriever metadata=new MediaMetadataRetriever();
                        try{
                            metadata.setDataSource(getApplicationContext(),selected);
                            String millis=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            if(millis==null)throw new IOException("Impossible de lire la durée de l'original");
                            originalSeconds=Float.parseFloat(millis)/1000f;
                        }finally{metadata.release();}
                        if(originalSeconds<0.5f || originalSeconds>60f)
                            throw new IOException("L'original doit durer entre 0,5 et 60 secondes.");
                    }
                    float decodeSeconds=extend?Math.max(1f,originalSeconds):added;
                    FxDebugLog.checkpoint("seed.decode.before");
                    kotlin.Pair<float[],float[]> audio=
                        StableAudioSeedInput.INSTANCE.load(getApplicationContext(),selected,decodeSeconds);
                    Sa3CheckpointIO.write(new File(getFilesDir(),"sa3-work/"+session),
                        "seed.bin",audio.getFirst(),audio.getSecond());
                    FxDebugLog.checkpoint("seed.checkpoint.after");
                }
                final float prefix=originalSeconds;
                final float total=extend?prefix+added:added;
                ui.post(()->{
                    if(isFinishing()||isDestroyed())return;
                    launchPipeline(session,description,total,selected!=null,strength,prefix,randomSeed,false);
                });
            }catch(Throwable error){
                FxDebugLog.error(error);
                ui.post(()->{
                    if(!isFinishing()&&!isDestroyed())
                        setBusy(false,"❌ Préparation audio : "+error.getMessage());
                });
            }
        });
    }
    private void probeDiT(){
        if(busy||!packPresent)return;
        if(!getPreferences(MODE_PRIVATE).getBoolean("sa3_licence_accept",false)){
            state.setText("Accepte d'abord les licences en lançant une génération normale.");
            return;
        }
        FxDebugLog.begin("DiT-only",8f,false);
        setBusy(true,"🔬 Chargement du DiT isolé, sans T5Gemma…");
        launchPipeline(Sa3Coordinator.newSession(),"",8f,false,1f,0f,0L,true);
    }
    private void launchPipeline(String session,String description,float total,boolean hasAudio,
                                float strength,float prefix,long randomSeed,boolean probe){
        if(pipeline!=null)pipeline.cancel();
        int cacheMode=Sa3CacheSettings.normalize(getPreferences(MODE_PRIVATE)
            .getInt("sa3_dit_cache_mode",Sa3CacheSettings.DEFAULT));
        FxDebugLog.write("SA3_DIT_CACHE_SELECTED "+Sa3CacheSettings.label(cacheMode));
        pipeline=new Sa3Coordinator(this,session,description,total,hasAudio,strength,prefix,randomSeed,
            probe,cacheMode,new Sa3Coordinator.Callback(){
                @Override public void onProgress(String detail){
                    if(!isFinishing()&&!isDestroyed())state.setText("🎹 "+detail);
                }
                @Override public void onComplete(File file){
                    if(isFinishing()||isDestroyed())return;
                    if(file==null){
                        setBusy(false,"✅ DiT seul : chargement et libération réussis. Voir journal mémoire.");
                    }else{
                        audioFile=file;
                        setBusy(false,"✅ Musique générée en processus séparés : "+
                            String.format(Locale.FRANCE,"%.1f",total)+" secondes. WAV prêt.");
                    }
                }
                @Override public void onError(String message,boolean unexpectedDeath){
                    if(!isFinishing()&&!isDestroyed())
                        setBusy(false,"❌ "+message+(unexpectedDeath?
                            " — Android a interrompu le processus. Copie le journal.":""));
                }
            });
        pipeline.start();
    }
    private void play(){
        if(audioFile==null)return;
        try{
            if(player!=null){player.release();player=null;}
            player=new MediaPlayer();
            player.setDataSource(audioFile.getAbsolutePath());player.prepare();
            player.setOnCompletionListener(p->{p.release();if(player==p)player=null;});
            player.start();state.setText("▶ Lecture du WAV généré.");
        }catch(Exception e){state.setText("❌ Lecture : "+e.getMessage());}
    }
    private void save(){
        if(audioFile==null||busy)return;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("audio/wav");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"FxExtractor-StableAudio3.wav");
        startActivityForResult(intent,SAVE_WAV);
    }
    private void readLicenses(){
        StringBuilder text=new StringBuilder("Powered by Stability AI.\n");
        for(String f:new String[]{"LICENSE.md","LICENSE_GEMMA.md","NOTICE"}){
            text.append("\n—— ").append(f).append(" ——\n");
            try(InputStream input=getAssets().open("sa3/"+f)){
                java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[4096];int n;
                while((n=input.read(buffer))>=0){
                    if(output.size()+n>150000)throw new IOException("Licence trop longue");
                    output.write(buffer,0,n);
                }
                text.append(output.toString("UTF-8"));
            }catch(Exception e){text.append("Texte non intégré : ").append(e.getMessage());}
        }
        TextView legal=new TextView(this);
        legal.setTextSize(19);legal.setTextColor(Color.BLACK);
        legal.setText(text.toString());legal.setTextIsSelectable(true);
        legal.setPadding(24,20,24,22);
        ScrollView viewport=new ScrollView(this);viewport.addView(legal);
        new android.app.AlertDialog.Builder(this).setTitle("Licences des modèles IA")
            .setView(viewport).setPositiveButton("Fermer",(dialog,which)->{}).show();
    }
    @Override protected void onStart(){ super.onStart(); FxDebugLog.lifecycle("StableAudioActivity.onStart"); }
    @Override protected void onStop(){ FxDebugLog.lifecycle("StableAudioActivity.onStop busy="+busy); super.onStop(); }
    @Override protected void onDestroy(){
        FxDebugLog.lifecycle("StableAudioActivity.onDestroy busy="+busy);
        if(pipeline!=null)pipeline.cancel();
        worker.shutdownNow();
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}
