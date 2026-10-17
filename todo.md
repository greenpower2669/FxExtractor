# FxExtractor — suivi
## Version 0.1.0
- [x] Créer branche isolée depuis dépôt vierge.
- [x] Implémenter RMS/silence/hystérésis/marges d'extraction en Java sans réseau.
- [x] Ajouter lecture WAV PCM16 et ZIP de fichiers WAV + events.json.
- [x] Fournir UI Android tactile lisible, sélection SAF, aperçu/écoute et export.
- [x] Ajouter tests de synthèse, CLI et CI APK/AAB directs non compressés.
- [ ] Vérifier run GitHub Actions du HEAD ; corriger tout échec avant annonce APK valide.
- [ ] Tester sur Samsung Android 16 : choix WAV, silence, bruit, écoute, ZIP, mémoire.
- [ ] Tests matériels d'accessibilité (taille de police, TalkBack) et rotation.
- [ ] Support MP3, OGG, M4A par décodage MediaCodec avant analyse, sans perte d'exactitude temporelle.
- [ ] Détection multi-pistes, réduction de bruit, segmentation d'impacts difficiles.
- [ ] Comparer Qwen-Omni pour **l'étiquetage sémantique** des extraits ; ne pas faire dépendre l'application du téléchargement d'un modèle massif ni d'un serveur.
- [ ] Valider un modèle on-device si mémoire/latence/qualité mesurées ; sinon garder l'analyse DSP autonome.
- [ ] Envisager les imports/exports de la bibliothèque Djipie sur consentement explicite.
- [ ] Demander l'accord avant merge main, tag Release et signature Play.
