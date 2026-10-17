# FxExtractor — suivi
## Version 0.1.0
- [x] Créer branche isolée depuis dépôt vierge.
- [x] Implémenter RMS/silence/hystérésis/marges d'extraction en Java sans réseau.
- [x] Ajouter lecture WAV PCM16 et ZIP de fichiers WAV + events.json.
- [x] Fournir UI Android tactile lisible, sélection SAF, aperçu/écoute et export.
- [x] Ajouter tests de synthèse, CLI et CI APK/AAB directs non compressés.
- [x] CI finale du code SHA `6e2f2c13760b46ec5e5e6f01c3a8f3b093b7ab33` : run `39060083448` **SUCCESS**, tests Java + syntaxe Qwen, build APK/AAB ; artefacts directs APK `12111057669` / AAB `12111236375` (17/10/2026).
- [ ] Tester sur Samsung Android 16 : choix WAV, silence, bruit, écoute, ZIP, mémoire.
- [ ] Tests matériels d'accessibilité (taille de police, TalkBack) et rotation.
- [ ] Support MP3, OGG, M4A par décodage MediaCodec avant analyse, sans perte d'exactitude temporelle.
- [ ] Détection multi-pistes, réduction de bruit, segmentation d'impacts difficiles.
- [x] Fournir l'adaptateur facultatif Qwen-Omni PC/GPU, sans téléchargement implicite (inférence GPU à valider).
- [ ] Comparer Qwen-Omni pour **l'étiquetage sémantique** des extraits ; ne pas faire dépendre l'application du téléchargement d'un modèle massif ni d'un serveur.
- [ ] Valider un modèle on-device si mémoire/latence/qualité mesurées ; sinon garder l'analyse DSP autonome.
- [ ] Envisager les imports/exports de la bibliothèque Djipie sur consentement explicite.
- [ ] Demander l'accord avant merge main, tag Release et signature Play.

## v0.2.0 — Vidéo/audio
- [x] Import SAF vidéo/audio et décodage piste audio vers WAV PCM16 avec MediaExtractor et MediaCodec sans réseau.
- [x] Limites 1 Go source, 200 Mo PCM, 90 min, bascule stéréo simplifiée pour le multicanal.
- [x] Livraison via extraction GitHub RUNNER_TEMP, comparaison et vérification APK, artefact APK direct.
- [x] CI Android + tests Java **SUCCESS** sur SHA `290dbec2c7a58d9800da6ca49b4efa2f157bd158`, run `39067220630`. APK direct ID `12113557221`, AAB séparé ID `12112789366`.
- [ ] Test Samsung Android 16 : MP4/AAC, MOV, WebM/Opus, MKV, MP3/M4A, silence, absence piste, ZIP et lecture.
- [ ] Autres formats / codecs non pris en charge par Android à étudier séparément (AVI/DRM).

## 0.2.1 — retour Fab, fins d'effets
- [x] Auditer le seuil RMS 0.52 et l'ancien padding arrière de 100 ms : source de coupes prématurées possibles.
- [x] Suivre les queues faibles avec un seuil plus bas et silence 220 ms ; fin réglable de 100 à 1500 ms (600 ms par défaut).
- [x] Pré-écoute basée sur la position réelle MediaPlayer (plutôt qu'un délai fixe après un seek).
- [x] Ajouter tests Java de reverb, réglage fin, événements séparés et silence.
- [ ] Confirmer CI Android de la version 0.2.1, SHA artefact et fournir le fichier APK direct non zippé via RUNNER_TEMP.
- [ ] Tester sur Samsung les fins de sons réels et risques de regroupement de deux FX rapprochés ; ajuster au besoin.
- [ ] IA audio Android : **non intégrée** ; choisir un modèle compatible hors ligne et tester mémoire/latence/qualité, sans prétendre que Qwen PC est déjà dans l'APK.
