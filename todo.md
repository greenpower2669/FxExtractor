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
- [x] CI 0.2.1 validée : run `39068622333`, code SHA `439be695651a26a13f01697f11b98d026a9faf30`, tests JVM + APK/AAB verts. APK brut ID `12114461455` (32 609 octets) ; AAB unsigned ID `12114685969`.
- [ ] Tester sur Samsung les fins de sons réels et risques de regroupement de deux FX rapprochés ; ajuster au besoin.
- [ ] IA audio Android : **non intégrée** ; choisir un modèle compatible hors ligne et tester mémoire/latence/qualité, sans prétendre que Qwen PC est déjà dans l'APK.

## 0.2.2 — noms modifiables, IA possible
- [x] Saisie de noms sous les 40 premiers sons (Explosion, Moteur, Voix, etc.).
- [x] Noms WAV sûrs, libellé original dans events.json, compatibilité FX_NNN et tests de sécurité.
- [x] Publication prévue APK brut depuis CI vers branche de livraison dédiée après extraction RUNNER_TEMP.
- [x] CI 0.2.2 verte (run `39069953534`, SHA source `92043f757cc18473e1db16e956495dc7d7700390`) : tests JVM, Android APK/AAB, extraction RUNNER_TEMP, publication raw. La branche `delivery/fxextractor-apk` contient `apk/FxExtractor-0.2.2-debug.apk` (33 989 octets, blob SHA `115bf1714c982e4e8ac6b95b21a26a43e863e6d3`).
- [ ] Tester clavier et export nom personnalisé sur Samsung ; les autres effets restent FX_NNN.
- [ ] Étudier YAMNet/TFLite local et mapping FR, mesurer vitesse/qualité/licence ; ne pas présenter comme déjà embarqué.

## 0.3.0 — intégration IA audio autorisée
- [x] Moteur YAMNet TensorFlow Lite réel, 521 catégories, traitement PCM16 mono 16kHz.
- [x] Lancement automatique des propositions de noms après extraction, scores et édition manuelle préservés.
- [x] CI pour télécharger le modèle officiel à la compilation, embarquer labels et modèle dans l'APK sans Internet sur le téléphone.
- [x] Run 39072658917 SUCCESS, vrai APK 0.3.0 brut 18 888 244 octets sur delivery/fxextractor-apk ; modèle YAMNet et labels inclus, SHA256 contrôlé 4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf.
- [ ] Tests Samsung Android 16 (latence, mémoire, décodage, sons explosifs/moteurs/voix, cas incertains).
- [ ] Ajuster catégories/traductions à partir de tests réels, sans inventer de reconnaissance parfaite.

## v0.3.1 — bouton IA sans réaction (18/10/2026)
- [x] Audit : incompatibilité des dimensions YAMNet [15600] / [1,15600] et message d'erreur hors écran.
- [x] Correction input rank1/rank2 de FxSoundAI et progrès/erreurs directement sous le bouton.
- [x] Rendre le bouton explicatif même si aucun son prêt ; gérer les erreurs natives.
- [x] Ajouter smoke test de véritable inférence YAMNet via LiteRT Python avant build Android.
- [ ] CI v0.3.1 verte et APK direct vérifié avec le nouveau code.
- [ ] Essai sur Samsung : reconnaissance de plusieurs extraits réels, affichage de scores, noms modifiables.

## 0.3.2 — demande Fab curseur 0 ms
- [x] Permettre explicitement **0 ms**, valeur par défaut, jusqu'à 1 500 ms par pas de 50 ms.
- [x] Moteur RMS `DEFAULT_RELEASE_MS = 0` ; aucun padding additionnel à 0, sans dénaturer le suivi naturel des queues.
- [x] Tests : zéro par défaut égal zéro explicite, valeurs positives plus longues, séparations préservées.
- [x] CI 0.3.2 run `39082262856` SUCCESS : DSP zéro par défaut + inférence YAMNet réelle LiteRT + build APK/AAB + GitHub raw. Binaire `apk/FxExtractor-0.3.2-debug.apk` 18 888 244 octets, blob `06e1935f737fdcaa20c753dadd062e5993b40ebe`.
- [ ] Essai physique Samsung : commande 0 ms, progression IA, résultats, exports.
