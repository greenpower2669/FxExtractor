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
- [ ] CI du code Android v0.2.0 à vérifier et corriger si rouge.
- [ ] Test Samsung Android 16 : MP4/AAC, MOV, WebM/Opus, MKV, MP3/M4A, silence, absence piste, ZIP et lecture.
- [ ] Autres formats / codecs non pris en charge par Android à étudier séparément (AVI/DRM).
