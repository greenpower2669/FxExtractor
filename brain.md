# FxExtractor — contrat et portée
Application Android native hors ligne pour repérer des événements dans l'audio, les écouter et les exporter en WAV séparés. Première implémentation 0.1.0 : WAV PCM16 mono/stéréo 8–192 kHz, source ≤200 Mo, extraction par enveloppe RMS + seuil adaptatif ; export ZIP avec events.json. Ne pas présenter cette approche comme une séparation de sources superposées. Aucun réseau ni échantillons personnels committés. Qwen-Omni audio = piste ultérieure indépendante, sans gros modèle dans l'APK de base. Ne pas mélanger avec le Qwen Vision de VisionMaxRead. Ne pas merger main, publier Release/Play ou remplacer la signature sans feu vert explicite. Livrer APK debug et AAB unsigned séparés, noms compréhensibles, icône originale.

## Preuve build 0.1.0 (17/10/2026)
CI `39060083448` verte sur code `6e2f2c13760b46ec5e5e6f01c3a8f3b093b7ab33` : tests et syntaxe Qwen validés ; Gradle APK/AAB réussis. APK direct `12111057669`, 24 377 octets ; AAB unsigned `12111236375`, 18 264 octets. Pas de preuve d'essai sur Samsung, ni d'inférence Qwen réelle. La mise à jour documentaire post-CI n'altère aucun code exécutable.

## v0.2.0 — Import vidéo/audio
Première piste audio des conteneurs pris en charge par Android (MediaExtractor/MediaCodec) décodée sans Internet en WAV PCM16. L'analyse RMS et export ZIP restent inchangés. 1 Go source max ; 200 Mo/90 minutes PCM. v0.2.0 code2, aucune preuve matérielle avant essai Samsung.

## Preuve build vidéo/audio v0.2.0
GitHub Actions run `39067220630` SUCCESS sur commit source `290dbec2c7a58d9800da6ca49b4efa2f157bd158` (17/10/2026) ; tests DSP et APK/AAB verts. APK test brut `FxExtractor-0.2.0-debug.apk` (31 557 octets, artefact 12113557221) et AAB non signé (22 097 octets, artefact 12112789366). Aucun essai Android Samsung ni validation exhaustive des formats de vidéo encore réalisés.

## 0.2.1 — protection des queues sonores
La détection RMS reste la base ; entrée sur seuil haut, suivi basse énergie pour les décroissances, 220 ms de silence puis ajout de fin réglable de 100 à 1 500 ms (600 ms par défaut). La pré-écoute MediaPlayer s'arrête en vérifiant la position sonore plutôt que le temps écoulé. Ne pas qualifier ceci de séparation ou de compréhension IA. Qwen-Omni reste uniquement dans `tools/qwen_tag_fx.py`, Python PC facultatif, sans poids téléchargeables dans l'APK.

### CI 0.2.1 validée le 17/10/2026
Run GitHub Actions `39068622333` SUCCESS sur code `439be695651a26a13f01697f11b98d026a9faf30` ; tests Java incluant décroissance et édition de queue, build Android APK et AAB. APK brut direct `FxExtractor-0.2.1-debug.apk` 32 609 octets, artefact `12114461455` ; AAB non signé `12114685969`. La CI ne confirme pas la qualité d'écoute sur Samsung.

## 0.2.2 — nommage et option IA
Saisie manuelle accessible d'un libellé pour chacun des 40 premiers FX. L'export WAV utilise un nom sécurisé (ex. Explosion_001.wav), et events.json contient le label original. WavIO.zip ancien appel conserve FX_NNN. IA son sur smartphone à étudier : YAMNet/TFLite compact, détection indicatrice et toujours corrigeable. Qwen PC seulement. CI publie APK brut sur `delivery/fxextractor-apk` après extraction et vérification, sans main/Release.

## Preuve CI et binaire v0.2.2 (17/10/2026)
Run `39069953534` SUCCESS sur SHA source `92043f757cc18473e1db16e956495dc7d7700390`. APK direct brut publié sur branche indépendante `delivery/fxextractor-apk`, chemin `apk/FxExtractor-0.2.2-debug.apk` (33 989 octets ; git blob `115bf1714c982e4e8ac6b95b21a26a43e863e6d3`), tests pure JVM et build Android verts. Aucun essai Samsung matériel ni IA classifiante encore validés.

## 0.3.0 — classification IA Android réelle
Modèle YAMNet Google TensorFlow Lite 521 classes AudioSet, entrée mono 16kHz / 15 600 float32, inférence FxSoundAI.java. Le modèle et ses labels sont téléchargés/embarqués durant GitHub Actions avant Gradle : téléphone hors ligne et sans permission Internet. IA lance automatiquement des suggestions sur les 40 premiers FX, noms toujours éditables, score visible, classe anglaise conservée faute de traduction. Qwen-Omni reste PC uniquement. Ne jamais confondre IA de classification avec séparation de sources.

### Validation modèle 0.3.0
Run GitHub 39072658917 SUCCESS ; APK direct brut FxExtractor-0.3.0-debug.apk = 18 888 244 octets ; blob Git aaa1bf532e841ee665b787d91ef93038a49660da. Modèle Google réel et 521 labels extraits, SHA256 4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf ; désormais vérifier cette empreinte fixe à chaque CI.

### Livraison 0.3.0 définitive de cette mission
Run GitHub Actions 39072851527 SUCCESS sur commit code aba5f44c19cc986baf404a4010a606cac029e5b0. Modèle YAMNet Google SHA256 verrouillé, 521 classes, vérifier assets dans APK réussi, livraison brute sur delivery/fxextractor-apk/apk/FxExtractor-0.3.0-debug.apk (18 888 244 octets, git blob 51148d79a2baae4d3c1728432e3f3f8b6f75fadd). CI seule : inférence sur Samsung non encore essayée.

## v0.3.1 — problème de bouton IA (18/10/2026)
Diagnostic : vérification tensorielle erronée de YAMNet. Entrée modèle officiel [15600] au lieu de [1,15600]. Correctif FxSoundAI accepte deux rangs et utilise float[] ou float[][] selon la vraie forme. MainActivity rend états et erreurs visibles près du bouton et des sons, gère prérequis, progression et LinkageError. CI nouvelle inférence vraie sur Linux, sans changer traitement DSP. Test Samsung encore nécessaire, aucun merge ni Release.

## 0.3.2 — 0 ms de fin de son par défaut
À la demande expresse de Fab, **préservation après fin : 0 ms par défaut**, curseur **0..1500 ms** par pas de 50. Cela ne désactive pas le suivi naturel de décroissance ; cela retire uniquement le supplément après le dernier échantillon reconnu. Test JVM compare option implicite à 0 explicite et garantit extension si durée positive. Le correctif YAMNet rank1 et affichage clair du statut de 0.3.1 est inclus. Pas de merge main ni Release.

### Preuve compilation 0.3.2 (18/10/2026)
Run GitHub 39082262856 SUCCESS sur commit code d9761b9ad042a65d5e083dacee6524c0be2a4809 : tests Java y compris `DEFAULT_RELEASE_MS=0`, vrai test d'inférence YAMNet sur Linux, Gradle Android, extraction dans RUNNER_TEMP, publication binaire direct APK. `delivery/fxextractor-apk/apk/FxExtractor-0.3.2-debug.apk` 18 888 244 octets, blob `06e1935f737fdcaa20c753dadd062e5993b40ebe`. Reste test sur Samsung.

## v0.3.3 — sauvegarde individuelle (18/10/2026)
Après l'analyse, chacune des 40 lignes affichées possède « Enregistrer ce son » ; sélecteur de fichier Android pour sauvegarder directement l'extrait dans le dossier choisi, sans ZIP. Sélecteur de formats général : WAV PCM16 sans perte (rate source) ou M4A/AAC-LC compressé par MediaCodec/MediaMuxer Android (44,1 kHz, débit cible 96/160 kbit/s mono/stéréo). WAV direct partagé par WavIO.writeClip, ZIP global WAV inchangé, noms manuels ou YAMNet suivis et assainis, aucune permission réseau. Le téléchargement du logiciel APK brut GitHub reste séparé de l'export des sons sur le téléphone. Toujours livrer APK brut sur delivery/fxextractor-apk, sans merge main/Release.

### Livraison 0.3.3 confirmée
Run Actions `39123489681` SUCCESS sur SHA source `711bba56575f4624fa73653c579ba56becbea3cb` ; tests JVM incluant WAV individuel, test YAMNet réel Linux, compilation Android avec nouveau module M4A, package `.apk` extrait et poussé brut dans `delivery/fxextractor-apk/apk/FxExtractor-0.3.3-debug.apk` (18 904 624 octets, git blob `e9b922107d0ae18364404e82d07f0be099255184`). Encodage M4A non encore essayé sur Samsung.

## 0.3.4 — format MP3
Choix par extrait WAV (sans perte), M4A (AAC) ou MP3 Shine natif LGPL-2.0. Encodeur Java Mp3ShineEncoder + JNI shine_bridge.c, 44,1 kHz, 128k mono/192k stereo, 3 ABIs Android. Source Shine fixée à ab5e3526b64af1a2eaa43aa6f441a7312e013519, build et test ffprobe/ffmpeg dans tools/build_shine.sh. Notices tierces dans THIRD_PARTY_NOTICES.md. Pas de merge main ni Release.

### Livraison 0.3.4 prouvée
GitHub Actions 39126300413 SUCCESS sur SHA source 6ce5fe99fcdfa4e7e21d7e9023bbff0d4e357b5a ; encoder Shine JNI réel testé par ffprobe/FFmpeg, libs Android multi-ABI, tests JVM/YAMNet, APK brut dans delivery/fxextractor-apk/apk/FxExtractor-0.3.4-debug.apk (19 063 004 octets, blob b5f6d3ed23ad221e2125509b47ddf621e7cac432). Aucun test Samsung physique, aucun merge main/Release.


## v0.3.5 — laboratoire expérimental MIDI
Accord Fab : tester MIDI Gen AI v5 à l'intérieur de FxExtractor, sans modifier le moteur d'extraction. UI séparée WebView locale ; modules MIT et runtime WebAssembly embarqués ; poids CPU ~115 Mo / GPU ~73 Mo téléchargés seulement sur clic, inférence ensuite locale, cache selon WebView. Pré-écoute Web Audio simplifiée, export .mid par SAF. Aucun envoi d'audio utilisateur. Essai Samsung / latence non validés. Pas de merge main/Release.

CI compilation 0.3.5 validée : GitHub run 39150975283 SUCCESS, modèle runtime JS/ONNX inclus ; APK brut FxExtractor-0.3.5-MIDI-v5-debug.apk (27 399 299 octets), blob 2e42c5b4e781383dd5acbe43d08fbc5d2801e667. AAB non signé distinct. Génération réelle sur Samsung encore à tester.

 
## v0.3.6 — diagnostic MIDI
Retour Fab (18/10) : appui sur Charger sans réaction, options grisées. La 0.3.5 cachait le statut loin sous les boutons et utilisait les imports statiques sans gestion d'échec visible. En 0.3.6, interface de diagnostic avant chargement dynamique, copie native et bouton test hors ligne, progression près de charger, annulation et reprise après échec. Compilation/test Android à vérifier ; test réel d'inférence encore nécessaire. Ne pas fusionner main.

Preuve CI 0.3.6 : run GitHub 39153583595 SUCCESS, APK debug brut `delivery/fxextractor-apk/apk/FxExtractor-0.3.6-MIDI-v5-debug.apk` (27 401 723 octets, blob `5200331c92c3223612ad2570f7ebf4445b4e8254`), AAB distinct non signé, test JS de gestion d'échec passé. Aucun essai matériel réel nouveau.


## v0.3.7 — IA MIDI intégralement embarquée
Décision Fab (18/10) : moteur et poids dans APK, démarrage automatique en mémoire quand le laboratoire s'ouvre ; pas de vocabulaire ambigu « télécharger/charger » ; import facultatif de poids ultérieurs à étudier après tests. Runtime ONNX/WASM livré local avec `wasmBinary`, modèle v5 CPU int8 ~109,3 Mio, tokenizer/manifest et SHA256 validés en CI. Aucune permission INTERNET. Taille du fichier Android >100 Mo probable : utiliser artifact GitHub Actions archive:false, pas branche Git brute, sans Release ni merge. Inférence réelle à tester sur Samsung.


### Preuve CI modèle embarqué v0.3.7
Run 39157742071 SUCCESS ; APK `FxExtractor-0.3.7-MIDI-OFFLINE-debug.apk` 162 031 457 octets (GitHub Actions artifact 12148160390, archive:false). SHA256 des poids CPU et binaire WASM vérifiés dans l'APK ; pas de téléchargement au téléphone, pas de droit INTERNET. Branch `delivery/fxextractor-apk` reste inchangée pour ce paquet >100Mo, limite Git. Test d'inférence sur Samsung et lecture MIDI encore nécessaires.


## v0.3.8 — corriger la variante ONNX CPU (18/10)
Journal physique Fab : 0.3.7 atteint 109,3/109,3 Mio, WASM 27Mio présent, étape 4 échec interne JS `reading 'Xe'`. Le code combinait `ort.webgpu.bundle.min.mjs` avec `ort-wasm-simd-threaded.jsep.wasm` ; v0.3.8 utilise strictement `ort.wasm.bundle.min.mjs` + `ort-wasm-simd-threaded.wasm` de onnxruntime-web 1.30.0. Nouveau test réel de chargement de modèle ONNX via Chromium Playwright avant build Android ; ne pas qualifier l'inférence Samsung de réussie sans essai. Préserver mode hors ligne, auto-initialisation, poids 109Mio, branches isolées.


### Preuve CI 0.3.8 (18/10)
Run 39159447721 **SUCCESS**, code 05a9c03f63ffa2b63a97655ede8826aa4e4c9404 : authentique modèle CPU + JS/WASM CPU compatibles initialisés en Chromium en ~1,4s lors du test précédent et revalidés sans erreur réseau. APK brut `FxExtractor-0.3.8-MIDI-CPU-debug.apk` 147 942 598 octets, artifact GitHub Actions non zippé 12148450979. Poids hash vérifiés dans l'APK. Samsung non encore testé, conserver diagnostic et limiter affirmation à navigateur Linux. Pas de merge main.


## 18/10/2026 — Choix Stable Audio 3 Small-Music pour second laboratoire
Fab valide l'option Stable Audio 3 Small-Music **audio direct** et privilégie un gros APK intégrant entièrement les modèles, sans téléchargement au téléphone ; conserver MIDI v5. Démarrage en mémoire à l'ouverture du seul nouveau laboratoire. Étude détaillée dans `docs/stable_audio3_android_plan.md` : runtime LiteRT 2.2+ incompatible à présumer avec ancien TFLite Java 2.16.1, tokeniseur SentencePiece, T5Gemma ~0,6 Go, Small-Music w8a8 ~0,5 Go, SAME-S decoder ~0,09 Go, droits Stability/Gemma, validation vraie génération avant APK. Aucun modèle Stable Audio encore intégré ; pas de merge main.

## 0.4.0-dev — premier code de Stable Audio 3 sur Android (18/10)
Branche indépendante `feature/stable-audio3-local-v1` : ajout `StableAudioActivity.java` (UI accessible, audit d'intégrité des modèles; génération bloquée si pas de modèle), `StableAudioMath.java` (pingpong LogSNR et export WAV PCM16LE réel, sans sample préenregistré), `tools/TestStableAudioMath.java` (tests). Migration vers Google LiteRT 2.3.0 côté YAMNet `LiteRtYamnet.kt` et `FxSoundAI.java` pour libérer la voie aux graphes multi-signature Stable Audio. Ce n'est pas encore un modèle générateur portatif Android fonctionnel ; poids/non-packaging et tokenizer/inférence restent à coder/tester. Compilation CI à valider. NE PAS annoncer ce laboratoire comme prêt ni fusionner main.


### CI 0.4.0-dev socle
Run GitHub Actions 39166381192 SUCCESS : LiteRT 2.3 + YAMNet Kotlin compilent, tests Java sampler pingpong/WAV passent. APK préliminaire brut 155643725 octets artifact 12150472873 ; aucune génération Stable Audio 3 démontrée, poids absents, bouton désactivé. Ne pas présenter cette version comme finalité, éviter téléchargement inutile à Fab. Ajout tools/verify_sa3_model_pack.py pour valider SHA/tailles/modèles **après** accord Gemma & Stability, sans bypass.


### 18 octobre — extension amorce sonore et port neuronal en cours
Fab demande version Stable Audio 3 TERMINÉE et souhaite un son d'amorce. Coder 2 modes : texte→musique et audio+texte→variation. Fichiers : StableAudioEngine.kt (T5Gemma, DiT rung+gcond, sampler pingpong, SAME-S enc/dec, réduction RAM par fermeture entre étapes), StableAudioSeedInput.kt (WAV PCM16 + MediaExtractor/MediaCodec offline MP3/M4A/etc, resample 44,1 kHz), StableAudioActivity.java (écran accessible, prompt durée/variation, sélection amorce, génération asynchrone, écoute, export WAV). Ajouter dépendance tokenizer-native Android et converter Python avec comparaison rigoureuse à la SentencePiece officielle. NOTE IMPORTANTE : IA native non vérifiée sur Samsung, ni poids complets livrés avant exécution intégrale et licences. Ne pas présenter build préliminaire comme produit fini.

### 19 octobre — premier APK complet Stable Audio 3 construit
Branche isolée `feature/stable-audio3-full-apk-v1` : GitHub Actions [39174830059](https://github.com/greenpower2669/FxExtractor/actions/runs/39174830059) SUCCESS. Artefact brut `FxExtractor-0.4.2-SA3-ANDROID-UNVERIFIED-debug.apk` ID **12153719163**, taille **1 465 887 394 octets** (pas d'archive ZIP à décompresser dans le chat). Build Gradle Android terminé, empreintes SHA-256 des **4 graphes TFLite plus tokenizer.model** vérifiées DANS l'APK; le modèle a été téléchargé et emballé en mémoire temporaire GitHub, jamais commité en Git. Licences Stability AI Community et Gemma + NOTICE intégrés; dialogue d'acceptation côté appli. Code trois modes : texte→musique, amorce→variation, prolongation audio jusqu'à +60s via masquage des latents et sortie PCM original conservée (durée originale 0,5..60s). Tests de vrai moteur officiel TFLite sur Linux GitHub Actions 39174719399 succès pour création/variation/outpainting. **POINT NON VALIDÉ : inférence réelle sur téléphone Android SM-A576B**, mémoire, temps, qualité musicale/raccord. Ne pas considérer ce point validé et ne pas merger main/release. Tester d'abord une génération de 1 seconde sur Samsung puis des durées courtes.

### 19 octobre — correction du chargement natif DJL (0.4.3 debug)
Fab a testé APK 0.4.2 sur Samsung SM-A576B Android 16, échec au premier rendu : `NoClassDefFoundError: ai.djl.huggingface.tokenizers.jni.LibUtils`. Audit CI 39177366560 : classe `LibUtils` présente en DEX, `libdjl_tokenizer.so` présent arm64, mais `libc++_shared.so` ABSENT. Audit ELF original JNI 39178798876 confirme `NEEDED [libc++_shared.so]`. Patch `tools/build_shine.sh` inclut libc++_shared.so depuis NDK r27c pour arm64-v8a, armeabi-v7a, x86, x86_64. APK complet 0.4.3 test : GitHub Actions 39178284751 **SUCCESS**, artefact brut 12155580851, taille 1 472 211 951 octets, tous modèles SA3 présents. La correction de dépendance native est vérifiée statiquement, mais fonctionnement sur Samsung et compatibilité éventuelle 16KiB toujours à vérifier. Emulator JNI CI 39177744225 non concluant au moment de cette note. Ne pas dire « final » sans test 1s sur téléphone.

## 19 octobre 2026 — crash fermeture du laboratoire Stable Audio après « Générer » ; instrumenter
Test utilisateur Samsung SM-A576B (Android 16) sur version 0.4.3 corrigée tokenizer : lance génération puis « surfenêtre » musicale se ferme, sans message visible. Avant ce jalon, StableAudioActivity.java ne faisait qu'un Log.e dans catch(Throwable) et perdait la trace en cas de terminaison du processus. Branche isolée `feature/sa3-persistent-debug-v1`, version `0.4.4-sa3-debug` : `FxDebugLog.java` journal local rotatif 256KiB, checkpoints synchronisés disque et marker `sa3-run.pending`, mémoire Java/native/PSS et système, stacktrace, crash handler Application sans intercepter la gestion Android. Dernière étape conservée même après kill natif/OOM si checkpoint écrit. Journaux consultables/copier/partager via boutons lisibles dans MainActivity et StableAudioActivity. Aucune donnée audio ni texte prompt stockée, aucune permission réseau. `StableAudioEngine.kt` instrumenté avant/après tokenizer, chargement modèle T5Gemma/DiT/SAME-S, forward et export. Correctif du précédent `libc++_shared.so` conservé. Génération réelle et stabilité Samsung NON encore validées.

### Compilation diagnostics Android vérifiée 19/10
GitHub Actions run [39218051002](https://github.com/greenpower2669/FxExtractor/actions/runs/39218051002) **SUCCESS** : tests structure, Android assembleDebug avec modèles complets, vérification empreintes, décompression en dossier temporaire GitHub. APK 0.4.4 debug brut disponible : artefact **12170841441**, taille 1472211963 octets. Inclut également récupération Android 11+ `ApplicationExitInfo` : motif récent LOW_MEMORY/JAVA_CRASH/NATIVE_CRASH/ANR/SIGNALED au redémarrage. Fonctionnement réel sur Samsung (fermeture de fenêtre) non encore expliqué : demander journal via bouton dans accueil après reproduire 1 seconde, ne pas déclarer correction du crash.


## 19/10/2026 — SA3 isolé par processus (0.4.5 debug, validation matérielle en attente)
Journal Samsung `0.4.4-sa3-debug` du 19/10 11:06 : 9/9 poids présents, tokenizer 19 tokens, T5Gemma calcul termine, natif redescend de 1641 à 16 Mio après fermeture ; OOM/LOW_MEMORY Android pendant chargement `dit_w8a8.tflite`, RSS de sortie 2666 Mio. Les deux modèles étaient déjà fermés séquentiellement dans un processus unique : ne pas prétendre qu'ils étaient maintenus simultanément. À la demande explicite de Fab, branche `feature/sa3-process-isolation-v1` avec services Android à processus distincts pour encoder texte, DiT (encodeur d'amorce puis diffusion), décodeur SAME-S. Passage entre processus par fichiers binaires float32 internes synchronisés, l'UI attend la mort effective du processus avant la phase suivante ; mode diagnostique « Tester DiT seul ». Le mécanisme réduit les ressources natives résiduelles, ne résout PAS un DiT individuellement trop volumineux. Tests JVM/checkpoint, CI Android et vérification réelle Samsung en attente. `main` et Release inchangées.


## 19 octobre 2026 — SA3 0.4.6, cache XNNPACK expérimental réversible
La version 0.4.5 sur Samsung a réussi à charger DiT seul après fermeture du processus T5Gemma. À 12:50, DiT `s96` a achevé les passes 0,1,2,3,4 puis le processus a été interrompu ; PSS du worker stabilisé ~4 183 Mio et natif ~4 152 Mio, mémoire système restante entre 600 et 950 Mio. Motif final exact du worker non établi (`lowMemory=false` n'exclut pas LMK, mais ne le prouve pas). La v0.4.6 conserve la même IA, une interface à 3 modes CPU (sans cache, XNNPACK cache mémoire `:memory`, XNNPACK cache de poids sur fichier dans cache interne). **Par défaut, cache fichier**, avec bascule `:memory` si stockage libre insuffisant (<1 Gio), afin d'éviter un cache disque envahissant. Cache file par empreinte officielle SHA-256 du modèle `weights-manifest.json`, ABI et LiteRT 2.3 ; purge uniquement les anciennes variantes de cache SA3 propres à l'app. Développement dans `feature/sa3-xnnpack-memory-v046`, aucune preuve d'économie RAM tant que comparaison sur Samsung non réalisée. Les trois processus et checkpoints préservés ; GPU non encore intégré. La CI doit vérifier 5 poids authentiques/empreintes dans APK complet et rendre le .apk brut sans extraction utilisateur.


## 19/10/2026 — 0.4.7 musique jusqu'à 3 min et concaténation vraie
Fab a indiqué que la 0.4.6 XNNPACK « Ça marche », sans détails de durée ni nouveau journal ; ne pas extrapoler à 3 minutes validées. Nouveau contrat : génération totale jusqu'à 180 s à 44,1 kHz stéréo, WAV continu ; prolongation = original complet PCM16 rééchantillonné + suite IA, original non recollé à la fin et aucun contexte répété. L'UI propose 1–180 s ; si prolongation, original+ajout <=180 s. Pour respecter la RAM : un encodeur T5Gemma produit `text.bin` une fois, DiT et SAME-S se relancent dans leurs propres processus pour chaque tranche (au plus 8 s de modèle). Première tranche sans contexte : jusqu'à 8 s ; les suivantes conservent ~2 s de fin audio converties en latents et produisent jusqu'à 6 nouvelles secondes. À chaque fin de chunk, sa copie PCM est jointe en flux à `assembled.wav` via `Sa3WavChain` : écriture RIFF propre, saut exact du préfixe, conservation de l'original, fin atomique `stable_audio3_last.wav`. Les tests JVM vérifient durée exacte, original byte-identique en PCM16, absence de duplication, source corrompue rejetée. Les vrais raccords musicaux, endurance, chaleur, espace temporaire et durée de calcul Android restent à confirmer. Le curseur 180 s n'implique jamais une requête DiT de 180 s qui dépasserait le modèle et la RAM.
