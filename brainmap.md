# FxExtractor — carte légère
- `app/src/main/java/fr/fxextractor/MainActivity.java` : import SAF, UI accessible, écoute et export.
- `app/src/main/java/fr/fxextractor/FxAnalyzer.java` : détection pure JVM des événements RMS.
- `app/src/main/java/fr/fxextractor/WavIO.java` : lecture PCM16 et export WAV/ZIP JSON.
- `app/src/main/java/fr/fxextractor/FxCli.java` : outils PC hors ligne.
- `tools/TestFxAnalyzer.java` : tests synthétiques reproductibles.
- `.github/workflows/android.yml` : tests et génération APK/AAB sans Release.
- `todo.md` : chantiers et validations non terminés.
- `debughistorical.md` : observations de défauts.
- `ordres-de-mission.md` : décisions et autorisations.

- `app/src/main/java/fr/fxextractor/MediaAudioDecoder.java` : première piste audio d'une vidéo via MediaExtractor/MediaCodec, conversion PCM16 WAV locale.

- `tools/qwen_tag_fx.py` : adaptateur descriptif Qwen2.5-Omni-3B facultatif sur PC ; aucune IA audio dans le binaire Android.

- `app/src/main/java/fr/fxextractor/WavIO.java` : noms WAV personnalisés sûrs et libellés originaux dans `events.json`.
- `delivery/fxextractor-apk` : branche APK installables bruts, lien raw, sans extraction ZIP chez Fab.

- app/src/main/java/fr/fxextractor/FxSoundAI.java : reconnaissance acoustique Android YAMNet, prétraitement 16kHz, prédictions et traduction limitée.
- .github/workflows/android.yml : emballage du modèle officiel et des étiquettes en assets, compilation, validation et publication du vrai APK.

- tools/test_yamnet_model.py : test de véritable inférence du modèle Google sur le runner GitHub.

- `app/src/main/java/fr/fxextractor/FxAnalyzer.java` : marge finale réglable, défaut 0 ms (à partir de 0.3.2).

- `app/src/main/java/fr/fxextractor/ClipAacEncoder.java` : conversion WAV PCM16 segmenté en M4A AAC-LC Android, resample 44,1 kHz, sans réseau.
- `app/src/main/java/fr/fxextractor/MainActivity.java` : format sélectionné, bouton Enregistrer ce son sous chaque FX, SAF ACTION_CREATE_DOCUMENT direct.

- Mp3ShineEncoder.java et shine_bridge.c : sortie MP3 native hors ligne.
- tools/build_shine.sh et tools/TestMp3Shine.java : build Shine et test MP3 réel décodeur ffmpeg.
- THIRD_PARTY_NOTICES.md : licence LGPL et provenance.

- `app/src/main/java/fr/fxextractor/MidiAiActivity.java` : écran expérimental IA MIDI, WebView sécurisé, export SAF.
- `app/src/main/assets/midi/index.html`, `main.mjs` : génération IA locale, interface, écoute WebAudio. Les modules amont/ONNX WASM sont fournis au build GitHub (pas dans Git).

- `tools/test_midi_ui.mjs` : test anti-interface silencieuse en cas d'échec d'import ONNX MIDI.

- `tools/embed_midi_model.py` : téléchargement modèle MIDI CPU v5 uniquement en CI et validation hash/poids avant intégration dans APK, jamais au lancement Android.

- `tools/test_midi_browser.mjs` : test Chromium d'initialisation réelle ONNX CPU avec le modèle MIDI embarqué (pas seulement syntaxe et SHA), assets virtuellement servis depuis l'APK.

- `docs/stable_audio3_android_plan.md` : mission d'un second laboratoire musical direct Stable Audio 3 Small-Music pour Android, plan des quatre modèles/tokenizer et des vrais tests de portage hors ligne. **Étude en cours, pas encore d'APK SA3 fonctionnel.**

- `app/src/main/java/fr/fxextractor/StableAudioActivity.java` : écran du nouveau laboratoire, audit sans réseau du vrai pack intégré, génération bloquée tant qu'absence de backend.
- `app/src/main/java/fr/fxextractor/StableAudioMath.java` et `tools/TestStableAudioMath.java` : sampler SA3 pingpong et export WAV stéréo testés en pur Java.
- `app/src/main/java/fr/fxextractor/LiteRtYamnet.kt` : pont Kotlin LiteRT 2.3 pour maintenir l'extraction/nommage YAMNet.


- `feature/sa3-process-isolation-v1` : labo SA3 0.4.5 mémoire/processus, validation Samsung nécessaire.
- `Sa3Coordinator.java` / `Sa3Service.java` : orchestration T5Gemma → DiT → SAME-S via services Android à processus distincts, mort du processus entre étapes, gestion mémoire/arrêts.
- `Sa3CheckpointIO.java`, `tools/TestSa3CheckpointIO.java` : fichiers intermédiaires float32 privés, I/O atomique et test de troncation.
- `StableAudioEngine.kt` : entrées indépendantes encoder/DiT/décoder et sonde chargement DiT seul.


- `feature/sa3-xnnpack-memory-v046` / v0.4.6 : expérimentation CPU XNNPACK sans dénaturer le pipeline T5Gemma → DiT → SAME-S.
- `Sa3CacheSettings.java` : sélection des modes 0=CPU témoin, 1=cache XNNPACK mémoire `:memory`, 2=cache fichier, protection espace libre, identité ABI/empreinte `weights-manifest.json`.
- `StableAudioEngine.kt::model()` : poids DiT uniquement configurés par `CompiledModel.CpuOptions(xnnPackWeightCachePath)`, T5/SAME-S non modifiés. Checkpoints mémoire, taille cache et timing.
- `StableAudioActivity.java` / `Sa3Coordinator.java` / `Sa3Service.java` : commande lisible, paramètre transmis jusqu'au service `:sa3_dit`, PID worker et historique Android en cas d'arrêt.
- `tools/test_sa3_xnnpack.py` : test structurel du raccordement des modes ; ne remplace pas les essais matériels avec PSS/RSS.


- `feature/sa3-three-minute-chained-wav-v047` / v0.4.7 : création musique jusqu'à 180 s et prolongation avec original + nouvelle musique dans un WAV.
- `Sa3LongFormCoordinator.java` : machine séquentielle des tranches de modèle <=8 s, première <=8 s de nouvelle musique et suivantes <=6 s de nouvelle musique avec contexte final <=2 s, encodage T5 une fois conservé sur disque, suivi de chaque process DiT et Decode ; checkpoint, progression et nettoyage.
- `Sa3WavChain.java` : validation WAV PCM16 44,1kHz, copie entière de l'original et copie à flux des seules frames nouvellement générées ; lecture de la fin audio pour outpainting, limite 180 s, mise à jour du header et finalisation atomique.
- `Sa3Coordinator.java`/`Sa3Service.java` : options `longForm` et `skipText` pour réutiliser `text.bin` et écrire `music.wav` dans la session ; services Android distincts toujours conservés.
- `StableAudioActivity.java` : curseur 1–180 s, import audio et création `original.wav` en cas de prolongation, édition des paramètres XNNPACK conservée ; récupération du WAV final.
- `tools/TestSa3WavChain.java`, `tools/test_sa3_longform.py` : tests structuraux et concaténation d'origine, durée, contexte.


- `fix/sa3-small-final-codec-window-v048`, v0.4.8 : correctif dernier segment `17/17` avec source latente 34 et signature SAME-S s64.
- `StableAudioMath.java::decoderWindow` : copier chaque canal latent en restant dans ses bornes, compléter la fin du canal avec la dernière valeur.
- `StableAudioEngine.kt::decode` : fenêtre décodeur sûre et log `SAME-S.decoder.pad` lors d'une trame de longueur inférieure à la signature.
- `tools/TestStableAudioMath.java` : test de non-régression Java exact 8704→16384 et tests d'offset régulier / court.
- `StableAudioActivity.java::recoverPartial` : trouver/copy le WAV `assembled.wav` le plus récent dans les dossiers de sessions privées, afficher durée et activer l'export si fichiers encore présents.
