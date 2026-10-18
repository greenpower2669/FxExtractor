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
