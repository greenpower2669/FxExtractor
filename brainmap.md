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
