# FxExtractor — historique de diagnostic
## D001 — initialisation 17/10/2026
La branche main ne comportait que LICENSE. Prototype créé sur feature/audio-fx-analyzer-v1. Aucun binaire ni essai matériel existants. Séparer CI et test sur Samsung.
## D002 — limites de l'analyse
Enveloppe RMS sur WAV PCM16 uniquement. Une superposition musique/bruit ne peut pas être démixée par le seul seuil ; le comportement sur faible souffle et bruit continu doit être évalué sur cas réels. L'API MediaPlayer peut varier selon les constructeurs ; valider l'écoute sur téléphone.

## D003 — CI Android (17/10/2026)
Run `39059711095` : SDK `android-actions/setup-android@v3` échoue en demandant le paquet retiré `tools`, les tests Java sont verts. Run `39059798918` : tentative sdkmanager direct sans setup échoue car non dans PATH. Correctif `android-actions/setup-android@v4` avec `packages: ''` suivi de `sdkmanager platforms;android-35 build-tools;35.0.0`. Run `39059864373` : Android + tests verts. Run final `39060083448` : syntaxe adaptateur Qwen + tests Java + APK/AAB tous verts. Android/Samsung réel et Qwen GPU non testés.

## D004 — import vidéo refusé
Cause 0.1.0 : filtre SAF audio/WAV et lecteur PCM16 seul. Version 0.2.0 : SAF audio/vidéo + MediaExtractor/MediaCodec vers WAV local, sans changer FxAnalyzer. Compatibilité Android selon conteneur/codec ; tester sur Samsung. DRM/AVI et séparation de sources restent hors garantie.

## D005 — preuve CI vidéo/audio
GitHub Actions `39067220630` SUCCESS : étape de packaging temporaire GitHub (zip de transit, extraction RUNNER_TEMP, comparaison CMP, vérification ZIP interne de l'APK), tests Java et build APK+AAB. La réussite CI ne garantit pas que le MediaCodec du Samsung décodera tous les conteneurs.

## D006 — queue FX coupée (17/10/2026)
Cause de code confirmée : FxAnalyzer.java conservait seulement 100 ms après la dernière fenêtre RMS >=52% du seuil d'entrée, et le preview MediaPlayer cessait selon un timer en temps réel plutôt que sa position de lecture. Réduction prématurée possible des queues de réverbération et de la pré-écoute. Correctif 0.2.1 : seuil de sustain abaissé, silence mesuré avant fin, extension 100..1500 ms ajustable et polling position pour pré-écoute. Les fichiers WAV source sont inchangés. Tests audio synthétiques ajoutés ; écoute Samsung encore nécessaire.

### D006 — preuve CI
Run `39068622333` SUCCESS : tests JVM (silence, sons séparés, réverbération décroissante, fin réglable, ZIP), Gradle APK et AAB, extraction en RUNNER_TEMP et publication `.apk` brut. Aucun test matériel Samsung ni inférence Qwen réelle revendiqués.

## D007 — noms et livraison APK
La 0.2.1 n'avait ni saisie des noms, ni IA embarquée. La 0.2.2 rend les libellés éditables avant export et normalise les noms WAV ; tests anti-traversée ZIP. Le lien d'artefact Actions peut rester une archive même si nommé .apk : la CI ajoute une publication de fichier brut sur la branche `delivery/fxextractor-apk`. Contrôler avant remise du lien.

### D007 — validation CI
Run `39069953534` success : extraction temporaire GitHub, contrôle contenu APK, push de `apk/FxExtractor-0.2.2-debug.apk` sur branche delivery. Git tree direct contient blob binaire 33 989 octets. Anciennes pages d'artefacts Actions restent sujettes au ZIP ; seul lien raw de branche est livré comme fichier brut. Pas de tests sur téléphone.

## D008 — ajout modèle YAMNet (17/10/2026)
Précédemment, noms manuels et découpe RMS, aucune IA on-device. Ajout d'un vrai interpréteur TensorFlow Lite Android, source de poids Google et extraction des 521 labels embarqués. Modèle distinct du script Qwen PC. Le modèle est téléchargé à la compilation, pas au lancement. Risques à auditer : compatibilité ABI/poids, index de labels, latence et fausses reconnaissances de SFX synthétiques. Aucun test matériel présumé.

### D008 — preuve du modèle et de l'APK
Run CI 39072658917 vert. SHA256 du modèle YAMNet provenant de Google : 4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf. Les métadonnées du modèle contiennent exactement yamnet_label_list.txt (521 classes). Les étapes de vérification du contenu de l'APK et de publication du fichier brut ont réussi. Un contrôle fixe du checksum est maintenant ajouté à la CI pour éviter les modifications silencieuses des poids.

### D008 — validation checksum après reprise CI
Après correction du texte d'interface et verrouillage SHA256 du modèle, run GitHub 39072851527 vert (tests et build Android). Présence du binaire APK brut v0.3.0 de 18 888 244 octets confirmée dans la branche delivery. Ne pas confondre build validé avec test d'inférence sur appareil.

## D009 — bouton reconnaissance semble inactif (18/10/2026)
Retour Fab : touche reconnaissance IA apparemment sans effet. Cause source identifiable : FxSoundAI exigeait input [1,15600], mais le YAMNet officiel est rank1 [15600], sortie [1,521] ; il refusait de créer le classifieur. L'exception était signalée dans un texte au-dessus des extraits, pouvant échapper à la vue après défilement, et le bouton était désactivé pendant le traitement. Correctifs : supporter les deux dimensions, statut près du bouton et des extraits, erreurs de linkage explicites. Ajout test CI avec vraie inférence TFLite, non seulement modèle présent dans APK. Test matériel toujours requis.

## D010 — curseur ne permet pas 0 ms (18/10/2026)
Capturé par Fab avec capture écran : valeur de fin minimale 100 ms, défaut 600 ms alors que 0 ms demandé comme défaut. Le slider était codé `max=28`, `releaseMs=100+50*progress`, et `DEFAULT_RELEASE_MS=600`. Correctif 0.3.2 : `max=30`, `releaseMs=50*progress`, `DEFAULT_RELEASE_MS=0` ; nouveau test garantissant absence de queue ajoutée à zéro. Ne pas confondre avec la fin naturelle du son déterminée par seuil RMS.

### D010 — CI 0.3.2 verte
Run 39082262856 SUCCESS : test 0 ms et réverbération, test IA LiteRT réel, compilation APK/AAB, contrôle fichier brut GitHub et livraison direct APK 18 888 244 octets. L'écoute réelle et le bouton IA sur Samsung restent à confirmer.

## D011 — extraction par ligne demandée (18/10/2026)
Retour utilisateur : souhaite télécharger/enregistrer individuellement chaque son et choisir le format. Jusqu'à 0.3.2 seule l'archive ZIP WAV était exportable. v0.3.3 : bouton par FX (40 lignes affichées), ACTION_CREATE_DOCUMENT direct, WAV sans perte via WavIO.writeClip et M4A AAC-LC comprimé via MediaCodec/MediaMuxer avec temporary file (qui est supprimé). L'export ZIP historique reste inchangé. Les encodeurs AAC étant dépendants d'Android/Samsung, compilation CI ne suffit pas à prouver le résultat matériel ; montrer les erreurs clairement et tester.

### D011 — preuve CI 0.3.3
GitHub Actions 39123489681 SUCCESS, code 711bba5. Tests WAV individuel, IA YAMNet et compilation AAC Android, APK brut 18 904 624 octets présent dans delivery; aucune Release/main modifiée. Un build compilant MediaCodec ne prouve pas le support réel de l'encodeur sur Samsung ; tester un fichier M4A puis l'écouter.

## D012 — MP3
Android ne garantit pas d'encodeur MP3, seulement son décodeur. Choix de Shine LGPL2, JNI hors ligne et build natif épinglé, pas de fichier M4A renommé. FFprobe et FFmpeg pour vérifier vrai MP3 en CI ; Android ARM64 à tester physiquement.

### D012 — résultat CI
Run 39126300413 entièrement vert : Shine génère réellement MPEG Layer III, ffprobe détecte codec_name=mp3 à 44 100 Hz, FFmpeg décode sans erreur, build NDK trois ABI, gradle compile, APK de 19 063 004 octets contient libshinefx.so et copie LGPL. Sur téléphone réel non encore validé.


## 18/10/2026 — expérimentation MIDI Gen AI v5
Risques connus : CORS/Hugging Face dans Android WebView, espace de stockage des poids (~73/115 Mo), mémoire WASM, WebGPU indisponible, latence CPU, différences entre GM MIDI et synthèse WebAudio de démonstration. Les tests CI ne prouvent pas l'inférence sur appareil. Aucun effet audio personnel transmis ; le droit INTERNET sert au téléchargement des poids sur consentement.

 
## 18/10/2026 — échec silencieux du chargement MIDI 0.3.5
Retour Fab : aucun retour visuel après « Charger », commandes grisées. Défaut vérifiable dans le code : le diagnostic en bas de page est sous les commandes, non visible immédiatement ; import JavaScript statique pouvait bloquer tout l'enregistrement des actions. Cause profonde éventuelle (réseau/model/WebView) encore inconnue sans journal. 0.3.6 met le diagnostic en haut, charge les modules dynamiquement, prend en charge le timeout des petits fichiers, annulation, copie journal native et test régression. Ne pas présumer que l'inférence v5 marche avant essai physique.

### État correctif UI 0.3.6
CI 39153583595 SUCCESS : tests Node d'affichage immédiat et de reprise après échec de module, compilation Android/MP3/YAMNet, extraction et livraison .apk brute. Le diagnostic utilisateur réel (éventuel blocage réseau / WASM) reste à recueillir ; ne pas conclure que l'inférence est réparée sans test sur téléphone.


## 18/10/2026 — explication de l'erreur WASM puis v0.3.7
Retour réel Samsung : 109,3 / 109,3 Mio téléchargés correctement, puis ONNX `no available backend found; both async and sync fetching of the wasm failed`, HTTP wasm streaming non-OK. Le moteur WASM était embarqué dans APK 0.3.6 mais ONNX n'y accédait pas ; le modèle CPU restait externe. Correction 0.3.7 : lecture explicite du WASM local via `wasmBinary` et poids CPU intégrés à la compilation, aucun chargement depuis le réseau sur Android. CI reste à valider et inférence matériel encore non prouvée. APK complet >100Mo donc Git blob delivery impossible, passer par artefact Actions non-zippé.


### Build v0.3.7
CI GitHub 39157742071 SUCCESS : tests, MP3, YAMNet, téléchargement/contrôle de poids MIDI sur runner, packaging APK, validation SHA256 du modèle à l'intérieur et moteur WASM inclus. APK debug brut 162 031 457 octets via GitHub Actions artifact 12148160390 (archive:false), pas de raw branch (limite 100Mo). Aucune preuve encore que génération fonctionne sur le Samsung ; retour test à collecter.


## 18/10 — échec 0.3.7 au démarrage ONNX après lecture complète
Logs exacts : WASM 27.0 Mio local, modèle CPU 109.3/109.3 Mio chargé en RAM, erreur async `Cannot read properties of undefined (reading 'Xe')` à l'étape 4. La précédente panne de fetch WASM est réglée. Nouvelle hypothèse forte : incompatibilité d'artefacts (JS ort.webgpu.bundle + JSEP WASM) pour le CPU ; selon le contrat ONNX Runtime Web, utiliser le bundle `ort.wasm.bundle.min.mjs` + `ort-wasm-simd-threaded.wasm` de même version. Introduire vraie initialisation automatisée Chromium Playwright en CI au lieu d'affirmer qu'un test de syntaxe garantit l'inférence.


### Échec du premier test Chromium v0.3.8
Run 39158913666 : les tests Java étaient verts, mais Chromium Playwright s'est fermé abruptement pendant `page.waitForFunction` lors du test de modèle, sans message ONNX. Le banc d'essai utilisait `page.route().fulfill({path})` sur un fichier de 109Mio : risque mémoire/IPC et mort du navigateur. Nouveau test via serveur HTTP local en streaming et base URI dérivée, affichage logs/crash ; ne pas conclure à un défaut de modèle avec ce premier résultat.


### Test 0.3.8 Chromium : la cause ONNX réparée
Run 39159219122 : Chromium affiche `IA MIDI prête en mémoire (1.4 s)`, `ready: true`, modèle de 109,3 Mio et WASM CPU initialisés localement. Échec du job uniquement sur un filtre réseau trop strict qui classait la requête locale `/favicon.ico` comme externe. Corriger l'assertion, relancer build. Pas encore de test Samsung.


### CI 0.3.8 finale
Run 39159447721 SUCCESS : ORT CPU + WASM CPU même version 1.30, vraie session modèle démarrée dans Chromium; pipeline Android compilé, contrôle du modèle + WASM intégrés à APK, MP3/YAMNet préservés. Artifact APK brut 12148450979 147 942 598 octets. Pas encore de résultat physique Samsung.


## 18/10/2026 — pivot de l'IA MIDI vers audio généré
Après validation du moteur MIDI CPU dans Chromium, Fab trouve le résultat musical décevant et choisit une seconde IA directe « comme Suno » : Stable Audio 3 Small-Music. Le modèle officiel CPU nécessite de porter LiteRT, T5Gemma, SentencePiece, DiT et SAME-S à Android (les scripts publiés ciblent Python/desktop). Ne pas confondre disponibilité de fichiers TFLite et application Android fonctionnelle. Branche expérimentation séparée ; aucune preuve d'inférence sur Samsung encore.

## 18/10/2026 — démarrage implémentation Stable Audio Android
La version d'extraction FxExtractor utilisait tensorflow-lite 2.16.1 pour YAMNet, tandis que Stable Audio 3 quantifié nécessite des graphes LiteRT 2.2+ avec signatures rung et XNNPACK. Migration expérimentale vers com.google.ai.edge.litert:litert:2.3.0 et Kotlin 2.3.21, adaptation YAMNet via LiteRtYamnet. Écran d'audit modèle CPU intégré et pure math SA3. Le code ne prétend pas encore charger les poids Stable Audio ni générer de WAV à partir de l'IA ; aucun bouton générer activé avant pipeline complet. Tester CI avant APK.


### Jalon expérimental compilé
CI 39166381192 SUCCESS, Kotlin LiteRT 2.3 et extraction YAMNet compilent, tests sampler/WAV en JVM passent ; aucune preuve de non-régression **sur Samsung**, YAMNet et Stable Audio 3 à tester sur matériel. APK 0.4.0-dev 155643725 octets sans poids Stable Audio, à ne pas appeler générateur musical. Modèles Stability/Gemma à obtenir sous licences, portage neuronal restant.

### 18/10 — réel pipeline SA3 local en migration
Deux modes implémentés : texte + amorce via SAME-S encoder. Les signatures DiT supposées args_0..6/output_0 sont à confronter à la SignatureDef officielle (sans le fichier ~0.5 Go, pas encore vérifiable). L'encodeur T5Gemma utilise Rust HuggingFace tokenizer, exporté depuis SentencePiece par transformers, validé par tests de parité stricts. Le runtime poids réel, Android mémoire, génération non-silence et le packaging APK >1Go sont encore NON VALIDÉS. Ne pas confondre compilation CI et fonctionnement du réseau.

## 19 octobre — intégration SA3 complète, test matériel en attente
Les builds intermédiaires échouaient occasionnellement pendant le téléchargement du NDK Android (archive ZIP transitoirement corrompue). Correctif : 3 tentatives + nettoyage des téléchargements partiels dans `tools/build_shine.sh`, branche complète, commit 87e5bc2. Compilation initiale 39174597244 échec en téléchargement NDK ; recompilation 39174830059 SUCCESS. Modèles 4 TFLite (DiT 539350360 octets, T5Gemma 563818608, SAME-S dec 89329616, SAME-S enc 64024104) + tokenizer, licences embarqués. Taille APK 1465887394 octets ; artifact brut GitHub 12153719163. Extension réellement implémentée : masque de conservation et réinjection du préfixe latent, encodeur audio, décodage, original réassemblé avec 30ms transition sur nouvelle zone. Test upstream réel outpainting Linux réussi 39174719399. Android n'a été que COMPILÉ, jamais inféré sur appareil ; crash possible, test terrain prioritaire. Aucun merge main.

## 19 octobre — crash tokenizer capturé sur Samsung dans version 0.4.2
Capture utilisateur : `NoClassDefFoundError: ai.djl.huggingface.tokenizers.jni.LibUtils`. Diagnostic : présence classe Java et .so native démontrée par run 39177366560; absence `libc++_shared.so` dans APK; bibliothèque JNI officielle `ai.djl.android:tokenizer-native:0.33.0` requiert explicitement `libc++_shared.so` (readelf DT_NEEDED, run 39178798876). Correction : copie NDK compatible multi-ABI dans `tools/build_shine.sh`, commit 689ae670e96f4f4e14e0e0f0fe2a6434dccbc9d8. Nouveau build complet GitHub Actions 39178284751 vert, APK brut `FxExtractor-0.4.3-SA3-TOKENIZER-FIX-TEST-debug.apk`, artifact 12155580851, 1472211951 octets. Le bug exact est identifié; correction empaquetée, mais succès générateur non encore constaté sur Android réel. Note upstream DJL 0.33.0 possible incompatibilité page size 16KB sur certains Android 16 (issue #3815). Test matériel requis avant merge. 

## 19/10/2026 — disparition de surfenêtre SA3 après lancement, absence de GET/LOG debug local
Symptôme utilisateur : la fenêtre musicale se ferme au lancement de « Générer », pas d'erreur affichée. Il peut s'agir d'un kill Android sous pression mémoire, d'un crash native ou d'une exception sur UI : cause pas encore déterminée. Aucun journal durable dans APK 0.4.3 (un seul Log.e sur catch Throwable). Correction instrumentation 0.4.4 sur branche isolée, sans fusion main : `FxApplication` sauvegarde Throwable global et onTrimMemory/onLowMemory ; `FxDebugLog` logfile interne rotatif et marker synchronisé pour retrouver dernier checkpoint après process death ; télémétrie mémoire accessible via boutons « Voir/copier journal » dans accueil et laboratoire ; logs des étapes avant/après chargement des grands modèles et codec. Attention : un vrai SIGKILL ne permet pas d'écrire une stacktrace : dernière étape disque + mémoire sera le diagnostic disponible. Faire reproduire 1s sur SM-A576B ; après fermeture, réouvrir FxExtractor et copier le journal depuis l'accueil. Compilation GitHub obligatoire et nouveau APK brut demandé.

### 19/10 — Première livraison du vrai logger 0.4.4
Commit base 26728659 (journal local durable, 3 stages, bouttons copier/partager) puis 8b21b741 (historique des causes d'arrêt système Android). CI 39218051002 COMPLETE SUCCESS. APK `FxExtractor-0.4.4-SA3-PERSISTENT-DEBUG-TEST.apk`, GitHub artifact 12170841441, 1472211963 octets. Le logger capture dernier checkpoint fsync avant allocation modèle, dernier run.pending en cas de SIGKILL, trim/lowMemory, sortie système ApplicationExitInfo et traces Java ; ne capture pas de stack native pour SIGKILL direct. Les données audio et prompts ne sont pas journalisés. Test utilisateur à mener.


## D — 19/10/2026 : LOW_MEMORY DiT après T5Gemma, expérience isolation de processus
Log Samsung SM-A576B, 0.4.4, 11:06 : tokenizer réussi (19 tokens), T5Gemma FP16 chargé (PSS ~1123 Mio), inférence faite (PSS ~1877 Mio), après fermeture du modèle PSS ~218 Mio. Dernier point avant mort : `LiteRT.load.before sa3/sa3-sm-music/dit_w8a8.tflite`. Nouveau PID après ~8 secondes, `ApplicationExitInfo.REASON_LOW_MEMORY` et RSS signalé ~2666 Mio ; aucune sortie musicale. Conclusion forte : pression mémoire pendant initialisation/compilation du modèle DiT ; ne pas affirmer fuite T5Gemma ou chargement parallèle (fermeture explicite déjà présente). Mesure attendue : essai DiT seul, puis 3 processus isolés à terminaisons forcées volontaires après checkpoints privés. Attention : les terminaisons normales des workers apparaîtront possiblement comme SIGNALED dans Android ExitHistory et sont distinctes du LOW_MEMORY. Les processus ne peuvent pas sauvegarder après un SIGKILL natif ; les marqueurs disque précèdent les allocations critiques. v0.4.5 sur branche de test, CI et test matériel à valider.


## D — 19/10/2026 12:50, DiT s96 atteint 5/8 avant arrêt avec ~4,15 Gio natifs
Journal Samsung 0.4.5 : T5Gemma tokenisation + inférence OK, sortie en checkpoint, processus libéré ; DiT passe `LiteRT.load.after sa3/sa3-sm-music/dit_w8a8.tflite` avec `native=4138MiB pss=4180MiB`, puis `DiT.run.after step=0,1,2,3,4` ; dernière ligne `process.DiT 3/4 Synthèse IA étape 5/8`, puis `SA3_PIPELINE_UNEXPECTED_DEATH`. PSS stable à ~4 183 Mio, mémoire disponible Android ~600..950 Mio. Hypothèse forte : pression mémoire native due à la création de graphes multi-rungs sans cache de poids explicite. Ne pas confondre avec fuite d'accumulation étape par étape, ni affirmer `LOW_MEMORY` pour le PID DiT (motif exact non encore enregistré). Les logs `SIGNALED status=9` pour l'ancien PID correspondent à l'arrêt volontaire du process T5Gemma, pas à la mort du DiT. Source code officiel Stability-AI `optimized/tflite/scripts/rung_dit.py` indique cache poids XNNPACK partagé. LiteRT 2.3 expose `CpuOptions.xnnPackWeightCachePath` : expérience réversible off/mémoire/fichier en 0.4.6. Mesurer même prompt/durée/conditions, répétabilité et fichiers créés, surveiller la taille du cache et la place disponible ; aucune baisse RAM revendiquée à ce stade.


## 19/10/2026 — diagnostic d'architecture durée/assemblage, pas encore un bug terrain 0.4.7
Avant la v0.4.7 : sélecteur durée limité à 30 s libre / +60 s en prolongation ; le moteur `generateLatents` exige une durée <=120 s, les signatures DiT ont des paliers jusqu'à 120 s, et `StableAudioMath.writeWav` limitait la sortie à 120 s. Demander directement 180 s au DiT est risqué pour RAM et non compatible avec ce code. L'utilisateur souhaite trois minutes **au total** et original suivi de la musique générée. Correctif proposé : tranches <=8 s avec outpainting 2 s, rechargements interprocessus, réutilisation des embeddings T5, concaténation RIFF PCM16 en flux et validation des tailles. Pour les sources compressées, l'original se retrouve dans la sortie en PCM16 44,1kHz (rééchantillonnage éventuellement nécessaire, pas identité octet à octet du MP3/AAC original). Jonction lissée par le moteur sur le seul morceau nouvellement créé ; la qualité acoustique n'est pas prouvée par des tests de fichiers. En cas de crash, observer segment/étape dans logs, espace disque, code raison Android. État au commit : tests CI et validation Samsung à confirmer.


## 19/10 16h20 — SA3 longform échoue au dernier segment 17/17 : copie 34→64 latents
Reproduction issue capture Samsung : `ArrayIndexOutOfBoundsException src.length=8704 srcPos=8670 dst.length=16384 dstPos=16320 length=64`. `src.length=256*34` (34 positions par canal, 256 canaux), `srcPos=255*34=8670`, `dst.length=256*64`, `dstPos=255*64=16320`. Le segment final court utilise une signature SAME-S `s64`, mais `decode()` copiatait `w.size` (=64) par canal sans vérifier si le canal avait 64 positions réelles. Cause confirmée par inspection ligne `System.arraycopy(padded,ch*even+w.start,x,ch*w.size,w.size)`. Ce n'est pas, ici, une panne RAM prouvée. Fix : copie `min(64,34)` suivie d'un prolongement de la dernière position du canal jusqu'à la taille `s64`. Le rembourrage ne doit jamais lire les canaux suivants. Le code garde le masque de restitution (seules 34 positions décodées servent à l'audio final), aucune duplication du préfixe de concaténation. Test JVM couvre source dernier canal `srcPos=8670`, contrôle dimensions cible 16384 et scénarios ordinaires. APK 0.4.8 à tester sur appareil ; le rendu complet 17/17 n'est pas encore validé au moment de cette observation.

## 19/10/2026 — blancs entre segments : diagnostic et hypothèse testable
Fab écoute des blancs suivis de musique/percussions en rupture de cohérence. Le code préservait 22 latents (90112 frames) pour 2 secondes réelles (88200 frames), créant 1912 frames manquantes dans le contexte préservé (~43ms). Cela peut nuire au raccord mais ne prouve pas la cause de silences plus longs ; l'audio future hors contexte doit rester masqué. v0.4.9 aligne l'amorce à un compte PAIR de latents complets, refuse préfixe tronqué, log `SA3_SEED_PCM`, `SA3_SEED_KEEP`, `SA3_AUDIO_QC`, `SA3_AUDIO_QC_RETRY`. Contrôle RMS 100ms sur la section générée uniquement ; suspicion si >=0.8s sous 0.0025 après RMS antérieur >0.014. Une seule nouvelle tentative du chunk si option activée, sinon signaler échec et récupération WAV partiel. Qualité musicale réelle, faux positifs, silence voulu et raccords à mesurer sur Samsung.
