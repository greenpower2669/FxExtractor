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

## v0.3.3 — sauvegarde unitaire, formats (18/10/2026)
- [x] Ajouter bouton d'enregistrement direct de chaque ligne FX affichée, sans ZIP.
- [x] Sélecteur WAV sans perte ou M4A/AAC compressé ; nom personnalisé repris et contrôlé pour fichier.
- [x] Écrire clip WAV séparé WavIO.writeClip, en réutilisant la même découpe du ZIP.
- [x] Encoder AAC en M4A local via MediaCodec/MediaMuxer et rééchantillonnage linéaire vers 44,1 kHz.
- [x] Conserver ZIP global WAV pour tous les clips, y compris après le 40e.
- [x] Ajouter test JVM direct WAV lossless, durée et en-tête valide.
- [x] CI 0.3.3 Android + Java SUCCESS, run 39123489681 (code 711bba56575f4624fa73653c579ba56becbea3cb). Fichier APK brut `apk/FxExtractor-0.3.3-debug.apk` 18 904 624 octets sur branche delivery, Git blob e9b922107d0ae18364404e82d07f0be099255184. Compilation M4A valide, test matériel encore nécessaire.
- [ ] Test Samsung : sauvegarde unitaire WAV/M4A, résolution de nom, lecture M4A, pas de ZIP, qualité et durée.
- [ ] Envisager formats supplémentaires après évaluation réels codecs/licences ; ne pas annoncer MP3 absent.

## v0.3.4 — MP3 demandé
- [x] Format MP3 dans chaque ligne, extension .mp3 et MIME audio/mpeg.
- [x] Encodeur Shine JNI, source épinglée et licence LGPL.
- [x] Build multi-ABI et vrai test MP3 decode FFmpeg prévus par CI.
- [x] GitHub Actions run 39126300413 SUCCESS : vrai MP3 Shine encodé/décodé FFmpeg, test ffprobe 44,1kHz, .so arm64-v8a/armeabi-v7a/x86_64 vérifiées, YAMNet test, APK brut FxExtractor-0.3.4-debug.apk 19 063 004 octets livré en branche delivery (git blob b5f6d3ed23ad221e2125509b47ddf621e7cac432).
- [ ] Tester sur Samsung et écouter MP3, comparer WAV/M4A.


## v0.3.5 — laboratoire MIDI Gen AI v5
- [x] Ajouter une activité séparée, sans changer YAMNet ou les exportations audio.
- [x] Amorce MIDI originale, BPM, créativité, mesure du temps, pré-écoute WebAudio, export .mid.
- [x] Préparer packaging CI du moteur JS/ONNX Web v5 en versions épinglées et téléchargement optionnel des poids sur smartphone.
- [x] CI Android + Java SUCCESS, run 39150975283 sur code 44f3cfef : MIDI JS/ONNX WASM embarqués ; APK brut 27 399 299 octets, Git blob 2e42c5b4e781383dd5acbe43d08fbc5d2801e667.
- [ ] Essai Samsung Android 16 : premier téléchargement, cache, latence, stabilité RAM, WebGPU, export.
- [ ] Si navigateur WebView est trop lent/instable, étudier ORT Android natif et un vrai lecteur SoundFont.
- [ ] Valider la qualité musicale et licence avant inclusion dans BlockBoost.

 
## v0.3.6 — correction « Charger ne fait rien »
- [x] Retour Samsung identifié : boutons du bas gris + statut éloigné ; impossibilité de diagnostiquer un échec de module ou réseau.
- [x] Démarrer l'UI avant l'import des modules ONNX, afficher étapes, progression et erreurs juste sous Charger.
- [x] Test hors ligne des boutons, annulation, réessai, copie du diagnostic via Android Clipboard.
- [x] Ajouter smoke test Node du clic et de la reprise après échec de module JS.
- [x] CI Android/Node SUCCESS run 39153583595, code 670977b1 : test anti-blocage MIDI, YAMNet, MP3 et APK/AAB ; APK brut 27 401 723 octets, blob 5200331c92c3223612ad2570f7ebf4445b4e8254. Essai Samsung encore requis.
- [ ] Essai Samsung : bouton test, téléchargement CPU (~115 Mo), sortie explicite de chaque étape, RAM et vraie génération.
- [ ] Si diagnostic « Accès réseau », « WebGPU », « WASM » ou crash WebView, corriger le moteur spécifique à partir du rapport réel.

## 18/10/2026 — clarification de Fab : modèle MIDI embarqué et initialisation en mémoire
- [x] Audit de la version 0.3.6 : les modules JS ONNX et le runtime WASM sont déjà empaquetés dans l’APK par GitHub Actions ; `main.mjs` lance ensuite un vrai téléchargement externe des poids CPU (~115 Mo) ou GPU (~73 Mo) et du manifest/tokenizer depuis Hugging Face sur « Charger ». Ce n’est donc pas seulement une mise en mémoire ; l’erreur précise WASM sur Samsung n’est pas encore identifiée.
- [ ] Intégrer les poids CPU MIDI Gen AI v5 **à la compilation** (comme pour YAMNet), avec manifest/tokenizer, empreintes vérifiées, et accès local depuis l’APK. Aucun téléchargement réseau requis sur le téléphone pour le modèle de base. Réserver les variantes additionnelles à une option explicite, sans toucher aux autres fonctions FX.
- [ ] Initialiser le modèle local automatiquement à l’ouverture du laboratoire MIDI, de préférence de façon asynchrone pour préserver la réactivité et la mémoire des autres écrans. Libérer les ressources quand le laboratoire n’est plus utilisé si les tests le justifient.
- [ ] Clarifier tous les libellés : « Préparation de l’IA en mémoire… », puis « IA chargée en mémoire » / « IA prête ». Si déclenchement manuel nécessaire, appeler le bouton « Charger en mémoire » ; n’utiliser « Télécharger » que pour une récupération depuis Internet.
- [ ] Tester hors réseau sur Samsung Android 16 : existence et résolution de l’exact fichier WASM, accès local manifest/tokenizer/poids, RAM, génération réelle et export .mid ; ne pas confondre validation CI avec validation sur téléphone.
- [ ] Auditer la distribution avant build : l’APK embarquant ~115 Mo de poids risque de dépasser le seuil GitHub de **100 Mio par objet Git**, incompatible avec le push direct sur `delivery/fxextractor-apk`. Trouver un vrai lien APK direct sans ZIP et sans Release/merge main non autorisés ; ne pas annoncer de livraison avant vérification effective.


## 0.3.7 — poids MIDI embarqués et WASM réparé
- [x] Diagnostic utilisateur exact : CPU ONNX 109,3 Mio téléchargé, échec de l'initialisation du moteur WebAssembly dans WebView.
- [x] Préparer CI : embarquer modèle CPU, tokenizer, manifest, WASM, hash SHA256 ; supprimer INTERNET du manifeste.
- [x] Fixer explicitement `wasmBinary` sur les octets du moteur intégré pour éviter la résolution WASM en échec.
- [x] Auto-initialiser dans l'écran MIDI seulement ; terminologie « charger en mémoire », aucun téléchargement Android.
- [ ] Vérifier run GitHub Actions, signature, APK brut >100 Mo en artefact sans ZIP, taille réelle, modèle extrait du ZIP APK et son hash.
- [ ] Test Samsung sans connexion : application ouverte, générer quelques notes, écouter, exporter .mid ; mesurer RAM et latence.
- [ ] Étudier dans les options un import de modèles additionnels compatibles, après validation v5 CPU.

- [x] GitHub Actions run 39157742071 SUCCESS : packaging CPU MIDI 109 Mio vérifié par empreinte SHA256, WASM et tokenizer dans APK ; binaire brut 162 031 457 octets, artefact non zippé ID 12148160390. Publication dans branche Git impossible >100Mo, sans Release.


## v0.3.8 — ONNX CPU cohérent
- [x] Diagnostiquer `Xe` : moteur WebGPU/JSEP employé dans un labo CPU, mélange de variantes.
- [x] Code : import ONNX CPU standard et WASM CPU standard issus de 1.30.0, garder `wasmBinary` pour WebView.
- [x] Ajouter essai réel de session MIDI v5 en Chromium, entièrement hors ligne, en CI avant publication.
- [ ] Vérifier test Chromium (prévu 180 s), puis build Android, APK >100 Mo en artifact non zippé, SHA modèle.
- [ ] Tester physiquement le Samsung : lancement CPU hors ligne puis génération 128 tokens, temps/ mémoire, écoute.
- [ ] Si navigateur Linux réussit mais Samsung échoue, relever la stack complète et différences WebView.

- [ ] Première CI v0.3.8 run 39158913666 échouée sur **outil test Playwright**, page fermée sans diagnostic ONNX. Remplacé par serveur HTTP streaming local ; relancer test réel sur navigateur et inspecter sortie.

- [x] Test réel CPU ONNX + poids 109,3 Mio **réussi sur Chromium** dans run 39159219122 (1,4s). Le build a été arrêté à tort par le filtre anti-réseau incluant le favicon local ; ajustement effectué.
- [ ] Relancer CI après correctif du test/favicon, obtenir APK 0.3.8 brut en Actions et confirmer SHA.

- [x] Run GitHub Actions 39159447721 **SUCCESS** : vraie initialisation Chromium du modèle MIDI CPU, build Android, packaging vérifié. APK brut 147 942 598 octets artifact 12148450979. Contrat sans dézipage Fab respecté.
- [ ] Test de Fab sur Samsung hors ligne : prêt en mémoire, Générer MIDI 128 tokens, écouter et sauver .mid ; relever temps réel ou trace d'échec.


## Stable Audio 3 Small-Music — nouveau laboratoire audio, 18/10/2026
- [x] Décision : conserver MIDI v5 ; ajouter Stable Audio 3 Small-Music CPU hors ligne en APK tout compris (Fab privilégie premier choix).
- [x] Vérifier source officielle de la version quantifiée LiteRT et composants T5Gemma/DiT/SAME-S/tokenizer, ordres de grandeur (1,2 Go de poids).
- [x] Identifier la contrainte de licence Stability AI Community + Gemma et de distribution (« Powered by Stability AI », notices).
- [x] Ouvrir une branche de travail indépendante, sans merge, et consigner contrat dans `docs/stable_audio3_android_plan.md`.
- [ ] Test LiteRT 2.2+ sur Android arm64 avec graphes multi-signatures, pas de réutilisation aveugle de tensorflow-lite 2.16.1.
- [ ] Port SentencePiece et modèle d'encodeur T5Gemma en vrai test instrumental.
- [ ] Port réel du sampler DiT w8a8 et décodeur SAME-S, WAV non silencieux vérifié en CI.
- [ ] Audit mémoire Samsung, génération de 20/30 secondes et arrêt propre.
- [ ] Embarquer les poids complets et licences uniquement une fois le moteur prouvé ; APK brut GitHub sans ZIP utilisateur ; comparaison hash et installation réelle.
- [ ] Conserver extraction audio/MP3/YAMNet et l'IA MIDI 0.3.8 sans régression ; ne pas merger main ni release sans validation.

## FxExtractor 0.4.0-dev — codage Stable Audio 3
- [x] Ajouter deuxième Activity indépendante « Musique audio IA » sans casser MIDI, statut accessible et vérification modèles embarqués.
- [x] Implémenter le vrai calcul des sigmas LogSNR, sampler pingpong SA3 et écriture WAV 44,1 kHz stéréo avec test JVM.
- [x] Remplacer la dépendance TFLite 2.16.1 par LiteRT 2.3.0 et migrer le pont YAMNet vers CompiledModel CPU dans worker unique.
- [ ] Vérifier pipeline Github Actions tests Java et compilation Kotlin/Android ; corriger erreurs.
- [ ] Obtenir les poids sous licence acceptée (Stability Community + Gemma), tokenizer SentencePiece et fichiers DiT+T5+codec intégrés APK hors ligne, hash et tailles.
- [ ] Port native T5Gemma encodage int32 et SentencePiece, sampler CPU DiT rung et codec SAME-S, vraie génération WAV tests Linux/Android.
- [ ] Profilage RAM/temps Samsung et tests génération audio hors ligne, arrêt propre, export et écoute.
- [ ] Ne pas livrer 0.4.0 comme IA musicale fonctionnelle tant que génération n'est pas vérifiée. Pas de merge main.

- [x] CI premier socle 0.4.0-dev : run 39166381192 SUCCESS (tests Java sampler et WAV, compilation Kotlin LiteRT/Android). Artefact brut 12150472873 de 155 643 725 octets, **non livrable comme IA musicale fonctionnelle**.
- [x] Code d'audit d'un pack de 4 modèles : tools/verify_sa3_model_pack.py, validation tailles, magic TFL3, SHA, licence explicite du responsable de packaging, pas de contournement HF.
- [ ] Le portage réel T5Gemma/SentencePiece/DiT rung/SAME-S et la génération WAV non silencieuse manquent encore ; les tester avec vrais poids après obtention autorisée. Besoin d'accord aux licences Gemma et Stability AI.

## Stable Audio 3 complet — demande explicite Fab
- [x] Programmer architecture des deux parcours : texte→musique, amorce audio+texte→variation (sigmaMax 0.2..1, SAME-S encodeur quantifié).
- [x] Programmer encodeur texte T5Gemma, DiT rung Small-Music gcond + bruit gaussian pingpong, décodage SAME-S, contrôle RMS non silencieux, sortie WAV.
- [x] Programmer import WAV/MP3/M4A via MediaCodec/MediaExtractor + conversion 44,1 kHz stéréo, entrée via sélecteur fichiers Android, écouter et exporter.
- [x] Programmer conversion tokenizer.model -> tokenizer.json locale et test de parité référence SentencePiece obligatoire.
- [ ] Faire passer compilation Kotlin/Android complète ; adapter les noms réels des signatures TFLite d'après métadonnées du checkpoint et tester tous les calculs sur poids réels.
- [ ] Intégrer **cinq modèles** dans l'APK (T5, Small-DiT, SAME-S enc + dec et tokenizer) avec licences et hashes ; nécessité validation des conditions Stability AI Community et Gemma.
- [ ] Effectuer inférence Linux sur vrais poids avec un audio témoin non silencieux puis test Samsung Android 16 hors ligne, contrôler mémoire et stabilité.
- [ ] Publier seul l'APK complet, pas le dev 0.4.1 inachevé. Aucune fusion main/release sans Fab.

## 19/10/2026 — Extension / prolongation de sons (demande Fab)
- [ ] Ajouter mode « Prolonger un son » distinct de « Variation depuis une amorce » : importer un extrait, choisir **jusqu'à +60 secondes supplémentaires** (par ex. +5, +10, +20, +30, +45, +60 s), décrire la continuité souhaitée, écouter et exporter. La durée choisie est le temps AJOUTÉ au son original, pas la durée totale; jusqu'à 120 s par génération SA3, blocs chaînés si nécessaire.
- [ ] Tester outpainting via SAME-S encoder + `--inpaint-range` (référence upstream SA3) : étendre le signal d'entrée au total souhaité, encoder son préfixe, conserver la zone originale via `keep_mask`, générer uniquement la partie manquante avec `local_add_cond` et la réinjection des latents conservés à chaque étape.
- [ ] Préserver **exactement** l'audio utilisateur d'origine dans le fichier final en réassemblant ses échantillons PCM originaux avec la continuation, et gérer la jonction par courte transition/crossfade facultative. Éviter de confondre fidélité du préfixe final et qualité du codec SAME-S.
- [ ] Pour une extension dépassant la limite 120 s du Small-Music, générer des blocs successifs avec contexte glissant, raccords et tests d'accumulation d'erreurs ; performance/mémoire Samsung à vérifier.
- [ ] Ne pas promettre une continuation parfaite pour des impacts très brefs ni pour les sons non musicaux : tester Stable Audio Small-Music, comparer au modèle Small-SFX sans intégrer un second poids massif par défaut.
- [ ] Aucun essai/validation Android de l'outpainting n'est encore réalisé. Ne pas afficher « Prolonger » comme fonction achevée avant vraie inférence vérifiée.

## 19/10/2026 — implémentation prolongation +60s et intégration complète (jalon)
- [x] Programmer StableAudioEngine.extend() : inpainting latent SAME-S avec masque de conservation du préfixe, local_add_cond [keep; masked], remise en place du contexte après chaque passe DiT, collage PCM original non modifié et raccord 30ms sur la continuation seulement. LIMITATION explicite : origine 0,5–60s ; prolongation 1–60s ; total <=120s.
- [x] Ajouter interface case « Prolonger » +60s, choisir son, force variation désactivée dans ce mode, export WAV et lecture conservés.
- [x] Programmer contrôle préalable des licences Stability AI Community et Gemma dans l'application, accès aux textes intégrés.
- [ ] Vérifier compilation Android de la nouvelle UI/extension et adapter les défauts.
- [ ] Fabriquer APK 0.4.2 intégrant automatiquement 4 TFLite (DiT/T5Gemma/SAME-S enc+dec) plus tokenizer, licences et manifestes SHA256, dans la branche de test dédiée ; pas de poids dans Git.
- [ ] Tester première génération SA3 **dans le véritable Android** ; la réussite Linux Python en CI ne suffit pas.
- [ ] Tester une prolongation réellement générée et écouter la jonction ; mesurer RAM sur SM-A576B et corriger si nécessaire.

## Bilan APK complet du 19 octobre (NE PAS confondre compilation et validation Samsung)
- [x] Compiler et emballer 4 vrais TFLite et tokenizer dans APK autonome, sha256/taille vérifiés dans archive : run 39174830059 SUCCESS.
- [x] Livrer APK brut décompressé dans la zone temporaire GitHub : artifact 12153719163, 1 465 887 394 octets, `FxExtractor-0.4.2-SA3-ANDROID-UNVERIFIED-debug.apk`.
- [x] Tests officiels CPU Linux texte→musique, variation amorce, vraie prolongation inpainting : run 39174719399 SUCCESS.
- [x] Fonction prolonger +1..+60 secondes écrite dans Android (original 0.5..60 sec, durée totale <=120 s).
- [ ] **Bloquant livraison finale :** essai réel sur Samsung SM-A576B Android 16 en mode avion, lancer 1 seconde de génération, confirmer son non silencieux et export WAV.
- [ ] Test prolongation : WAV original 3 sec +10 sec (original inchangé et raccord doux), puis +60sec, mesurer CPU/RAM/température, corriger panne.
- [ ] Nettoyer éventuelles dettes ci-dessus et demander à Fab validation avant merge main/release.

## 19/10 — correction DLJ tokenizer version 0.4.3
- [x] Reproduire l'analyse de l'APK : `LibUtils` existe dans DEX, JNI native présente, mais bibliothèque `libc++_shared.so` absente (CI 39177366560).
- [x] Confirmer la dépendance ELF originale `NEEDED libc++_shared.so` de `libdjl_tokenizer.so` (CI 39178798876).
- [x] Ajouter automatiquement les 4 variantes JNI libc++_shared.so au packaging Android via NDK.
- [x] Recompiler et vérifier APK complet 0.4.3 (CI 39178284751, succès), brut 1472211951 octets, artifact 12155580851.
- [ ] Fab : réinstaller l'APK 0.4.3, générer 1s pour vérifier que crash LibUtils a disparu; renvoyer erreur si nouvelle panne.
- [ ] Déterminer si SM-A576B requiert des bibliothèques alignées 16KB ; DJL tokenizer-native 0.33.0 a upstream issue #3815.
- [ ] Valider création, amorce, prolongation et mémoire sur Android 16 avant toute publication, ni merge vers main.

## 19/10/2026 — debug de disparition d'Activity au lancement SA3
- [x] Auditer la gestion des exceptions Android : uniquement Log.e dans catch Throwable, aucun journal utilisateur persistant.
- [x] Ajouter journal local rotatif + marker génération synchronisé sur disque, checkpoints mémoire (Java, native, PSS, mémoire système), hook Application uncaught, trim/low memory.
- [x] Ajouter « 🧪 Voir / copier le journal de débogage » en haut de l'accueil ET du laboratoire, et partage texte, bandeau de génération interrompue.
- [x] Ajouter instrumentation fine tokenize/load T5Gemma/DiT/SAME-S avant/après et export WAV.
- [ ] Vérifier la compilation complète Android avec la 0.4.4-debug et livrer APK brut non zippé.
- [ ] Fab : installer 0.4.4, lancer génération de 1s, si fenêtre disparaît rouvrir FxExtractor > bouton journal > Copier tout puis coller dans chat.
- [ ] Selon dernier checkpoint / mémoire et causes natifs, corriger crash/chargement et retester : pas de conclusion hâtive sur la cause (OOM vs erreur Java vs bibliothèque 16KB).
- [ ] Aucun merge main/release sans validation.

- [x] 0.4.4 journal persistant + motifs d'arrêt Android (ApplicationExitInfo) empaquetés, run 39218051002 vert, APK brut artefact 12170841441 (1472211963 octets).
- [ ] Test terrain : générer 1s; si écran se ferme, relancer FxExtractor puis bouton d'accueil « Voir/copier les journaux de débogage », copier/coller log. Identifier l'étape T5Gemma / DiT / codec et éventuellement motif Android LOW_MEMORY ou NATIVE_CRASH.


## 19/10 — SA3 0.4.5 processus séparés, après journal LOW_MEMORY
- [x] Examiner le crash Android : génération 8s, tokenizer OK, encodage T5Gemma OK, OOM LOW_MEMORY pendant chargement `dit_w8a8.tflite`.
- [x] Vérifier que le code ferme déjà T5Gemma avant DiT : pas de simultanéité intentionnelle dans 0.4.4.
- [x] Créer `feature/sa3-process-isolation-v1` sans modifier main.
- [x] Introduire services de calcul T5Gemma/DiT/SAME-S à processus distincts, échanges par checkpoints privés et attente de sortie effective de chaque worker.
- [x] Ajouter test indépendant de chargement du DiT et tests de sérialisation de tensors, sans prétendre à une génération validée.
- [ ] CI GitHub verte sur source finale et téléchargement APK 0.4.5 directement sans ZIP sur téléphone.
- [ ] Fab : « Tester DiT seul (mémoire) » et transmettre log, puis génération 1 seconde, puis 8 secondes.
- [ ] Si DiT seul fait OOM : optimiser préparation des signatures, accélération ou quantification/streaming sans fausse sortie musicale.
- [ ] Retester audio en amorce, prolongation jusqu'à +60s, arrêt d'application, stockage privé, modèle réellement non silencieux, et stabilité Android.
- [ ] Aucune fusion main, publication Release ou Play avant accord.


## 19/10/2026 — SA3 0.4.6 XNNPACK : mission autorisée
- [x] Analyser journal 12:50 : T5Gemma OK ; DiT ~4152 Mio natif, cinq passes s96 sur huit terminées, dernier PID mort ; motif exact inconnu.
- [x] Vérifier API `CompiledModel.CpuOptions.xnnPackWeightCachePath` et le cache officiel du modèle multi-rungs.
- [x] Créer branche isolée `feature/sa3-xnnpack-memory-v046` depuis 0.4.5.
- [x] Ajouter modes CPU témoin / cache XNNPACK mémoire / cache fichier et mesure dans les checkpoints ; pas de régression MIDI/FX, pas de GPU présenté comme actif.
- [x] Instrumenter PID du worker DiT et demander `ApplicationExitInfo` différé si arrêt anormal ; absence de motif possible.
- [x] Préparer tests structurels et build 0.4.6 complet avec modèle officiel+licences et extraction côté GitHub.
- [ ] Confirmer CI finale verte, APK brut `FxExtractor-0.4.6-SA3-XNNPACK-DEBUG.apk` téléchargeable sans ZIP, SHA et build source.
- [ ] Fab : même prompt, d'abord cache fichier 8s, ensuite éventuellement cache mémoire 8s et CPU témoin 8s (uniquement si nécessaire), copier logs : `SA3_XNN_CACHE`, `LiteRT.load.after`, PSS native, étapes, sortie.
- [ ] Si cache réduit PSS et passe 8/8 : tester SAME-S WAV réel non silencieux, amorce et prolongation +60s ; sinon étudier découpe d'export multi-rungs, n'activer GPU qu'après mesure compatibilité et usage RAM.
- [ ] Évaluer espace disque occupé par cache et comportement en cas de cache corrompu / stockage faible.
- [ ] Ne pas fusionner main ni publier Play/Release sans validation.


## 19/10/2026 — FxExtractor 0.4.7, objectif 3 minutes et audio original+suite
- [x] Consigner confirmation « Ça marche » par Fab sur 0.4.6 et nouvelle mission durée 3 min, original puis suite concaténés.
- [x] Auditer limites 30/60/120 s de l'UI, sampler DiT, lecteur audio et WAV.
- [x] Créer branche `feature/sa3-three-minute-chained-wav-v047` depuis 0.4.6, aucune modification main/release.
- [x] Écrire `Sa3WavChain` et tests Java : WAV 44.1k stéréo, taille exacte, original conservé, préfixe retiré, finale atomique.
- [x] Générer par tranches <=8 s, contexte fin <=2 s, réutiliser encodage texte entre tranches, garder XNNPACK et services séparés.
- [x] Intégrer curseur jusqu'à 180 s et import original WAV pour mode prolongation ; garder poids internes et sans réseau.
- [ ] CI complète verte avec vraies ressources IA et APK brut v0.4.7 (pas de ZIP sur téléphone).
- [ ] Fab : générer 15 s puis 45 s, écouter transitions et mesure RAM/temps ; ensuite 3 min avec batterie et stockage suffisants.
- [ ] Fab : prolonger un WAV de 10 s de +15 s, vérifier sortie exactement 25 s et original en début, puis essai plus long.
- [ ] Vérifier sources compressées 44.1k PCM16, gestion du manque d'espace, permissions SAF, application en arrière-plan et interruption volontaire.
- [ ] Documenter correction/régression après retour Samsung ; aucune fusion main/Release sans validation.
