# 🎛 FxExtractor — découpeur sonore local (Android + CLI)

**Prototype v0.2.0** créé sur `feature/audio-fx-analyzer-v1`, sans fusion automatique vers `main`.

## Fonctions disponibles
- Importer directement une vidéo ou un son (MP4, MOV, MKV, WebM, 3GP, MP3, M4A, OGG, WAV) **si le codec est pris en charge par Android** ; extraire sa piste audio localement vers WAV PCM16, puis détecter les FX. Source max 1 Go, son décodé max 200 Mo ou 90 min.
- Détection de sons isolés par énergie RMS sur fenêtres de ~20 ms : seuil adaptatif, silence, marge de 80–100 ms, fusion de petits blancs (<140 ms) et suppression des micro-événements (<70 ms).
- Régler la sensibilité, consulter des horodatages, écouter les 40 premiers événements.
- Exporter un **ZIP de WAV indépendants** et un rapport `events.json` avec les bornes en millisecondes.
- Traitement **entièrement sur l'appareil**, aucune permission Internet ni stockage global.
- Une CLI Java sans dépendance pour analyser et exporter un WAV sur PC.
- Tests Java synthétiques + workflow GitHub Actions générant **APK directement téléchargeable sans ZIP supplémentaire** et AAB distinct.

### Installer depuis GitHub Actions
Ouvrir [Actions](https://github.com/greenpower2669/FxExtractor/actions), workflow *FxExtractor Android and DSP tests*, dernier run **vert** sur la branche de travail, puis télécharger l'artefact `FxExtractor-0.2.0-debug.apk`. Cet APK debug est destiné au test : **ce n'est pas une publication Play**. Aucun lien d'APK n'est déclaré valide tant que la CI n'a pas réussi.

### Version PC
```sh
mkdir -p out
javac -d out app/src/main/java/fr/fxextractor/{FxAnalyzer,WavIO,FxCli}.java
java -cp out fr.fxextractor.FxCli mon-son.wav extraits.zip
# option : un seuil 0.7 détecte plus de sons faibles que 1.0
java -cp out fr.fxextractor.FxCli mon-son.wav extraits.zip 0.7
```

### Limites importantes
Ce moteur **détecte des périodes sonores**, il ne sépare pas magiquement le bruit et la musique qui jouent en même temps. Un silence prolongé produit zéro FX. Un son continu peut produire un seul extrait ou aucun si le niveau est assimilé au bruit de fond. Ce n'est **ni un modèle de séparation de sources, ni une transcription, ni une classification sémantique**.

Le Qwen **Qwen3.5-0.8B-VL** de VisionMaxRead n'analyse **pas** l'audio. Pour étiqueter les sons ("cloche", "moteur", "explosion", etc.), il faudra tester un modèle **Qwen-Omni audio** séparé, avec évaluation mémoire, licence et coût d'inférence. On n'inclut **aucun poids Qwen dans l'APK** sans essai réel et accord de Fab.

### Sécurité et vie privée
L'application ne demande ni INTERNET ni READ_EXTERNAL_STORAGE, ne contacte aucun serveur, ne collecte aucune télémétrie. L'utilisateur choisit une entrée WAV et une destination ZIP via le sélecteur Android. Le WAV temporaire est dans le cache privé et peut être purgé par Android. Attention : les sons personnels demeurent dans le ZIP explicitement exporté.

### Vérifications
- Test `tools/TestFxAnalyzer.java` : deux événements, absence d'événements en silence, bruit constant, lecture WAV, export ZIP.
- La CI prouve la compilation et les tests **mais ne remplace pas un test physique Android**, notamment l'écoute MediaPlayer et les accès SAF.

## Analyse sémantique Qwen (option GPU expérimentale)
L'application Android **ne charge pas Qwen-Omni**. Pour comprendre/nommer des sons déjà exportés, l'adaptateur `tools/qwen_tag_fx.py` utilise **Qwen2.5-Omni-3B** sur un ordinateur disposant de mémoire/GPU. Il écrit un JSON de descriptions distinct du ZIP initial (les sources ne sont pas modifiées). Il n'envoie aucun extrait à une API externe ; le téléchargement des poids depuis Hugging Face requiert `--allow-download`. Les poids **ne sont jamais téléchargés dans les builds Android**.

```sh
python -m pip install 'transformers>=4.52.3' accelerate torch qwen-omni-utils soundfile
python tools/qwen_tag_fx.py FxExtractor_segments.zip etiquettes-qwen.json --allow-download
```

L'inférence Qwen n'a pas été validée dans notre CI sans GPU. Le modèle est distribué sous licence **qwen-research** : vérifier cette licence avant utilisation commerciale. Ne pas confondre avec le Qwen3.5 Vision du projet VisionMaxRead.

### Livraison CI validée (17 octobre 2026)
Code fonctionnel SHA `6e2f2c13760b46ec5e5e6f01c3a8f3b093b7ab33`, [run GitHub Actions vert](https://github.com/greenpower2669/FxExtractor/actions/runs/39060083448). [**APK Android direct**](https://github.com/greenpower2669/FxExtractor/actions/runs/39060083448/artifacts/12111057669) (24 377 octets, debug test) ; [**AAB distinct non signé**](https://github.com/greenpower2669/FxExtractor/actions/runs/39060083448/artifacts/12111236375) (non installable, pas pour publication sans signature). APK non testé physiquement sur Samsung. Les éventuels commits documentaires suivants ne modifient pas le code dont la CI a établi la compilation.

## v0.2.0 — Import vidéo/audio
Choisir une vidéo ou un audio via le sélecteur Android. MediaExtractor et MediaCodec lisent la première piste audio décodable, ignorent l'image et produisent un WAV PCM16 analysé comme auparavant ; sons multicanaux rabattus en stéréo approximativement. Les DRM, certains AVI, codecs exotiques ou fichiers sans piste sonore peuvent échouer. Aucun accès Internet ni modèle IA ajouté au téléphone. Source maximale 1 Go, WAV décodé 200 Mo / 90 min. Tests matériels requis sur Samsung ; la compilation seule ne garantit pas les codecs. La CI met l'APK dans une archive de transit, l'extrait dans RUNNER_TEMP, vérifie ses octets et publie l'APK brut via upload-artifact sans zip supplémentaire.

### APK v0.2.0 validé par GitHub Actions
[**Télécharger FxExtractor-0.2.0-debug.apk (fichier direct, sans ZIP)**](https://github.com/greenpower2669/FxExtractor/actions/runs/39067220630/artifacts/12113557221). Code et CI verts : commit `290dbec2`, run `39067220630`, APK 31 557 octets. AAB test non signé [séparément](https://github.com/greenpower2669/FxExtractor/actions/runs/39067220630/artifacts/12112789366). Essai physique Samsung à faire.

## 0.2.1 — préserver la fin des effets sonores
- Le seuil d'entrée reste conservateur ; un seuil de suivi plus bas prolonge l'événement tant qu'une réverbération ou un souffle décroissant restent détectables.
- Attente d'environ 220 ms de vrai silence avant de déclarer la fin ; queue ajoutée **réglable de 100 à 1 500 ms**, par défaut 600 ms (anciennement seulement 100 ms).
- Quand deux zones protégées se chevauchent, elles sont réunies ; si les sons sont distincts mais trop rapprochés, baisser le réglage. Aucun échantillon source n'est modifié.
- Pré-écoute : l'arrêt est déclenché à la position de lecture réelle du média, plus par un délai mural qui pouvait couper la fin avant que le démarrage/seek soit achevé.
- Tests Java couvrant une longue décroissance, l'extension réglable, la séparation de deux événements et le silence. Aucune IA embarquée à ce stade : analyse RMS, pas de classification.

### Où se trouve Qwen ?
L'unique adaptateur Qwen est **`tools/qwen_tag_fx.py`**, outil PC Python hors de l'APK ; il dépend de `torch`, `transformers`, `qwen-omni-utils` et du checkpoint `Qwen/Qwen2.5-Omni-3B` (~12 Go de poids). Le modèle **n'est ni committé au dépôt, ni inclus dans l'APK, ni exécuté par Android**. Le script ne télécharge les poids que sur instruction explicite `--allow-download` et son inférence réelle reste à vérifier. Licence de ces poids : `qwen-research` (voir fiche officielle).

### ✅ APK 0.2.1 vérifié en CI
[**Télécharger l'APK 0.2.1 directement (sans ZIP)**](https://github.com/greenpower2669/FxExtractor/actions/runs/39068622333/artifacts/12114461455) (32 609 octets, debug). [AAB séparé non signé](https://github.com/greenpower2669/FxExtractor/actions/runs/39068622333/artifacts/12114685969). Code SHA `439be695` ; run 39068622333 SUCCESS. `main` n'est pas fusionnée. Test matériel Samsung et intégration future de l'IA audio encore nécessaires.

## 0.2.2 — noms de sons modifiables (17 octobre 2026)
Après analyse, une zone de texte apparaît sous chacun des **40 premiers effets**. Fab peut écouter puis saisir « Explosion », « Moteur », « Voix », « Laser », etc., ou laisser vide pour conserver « FX ». L'export utilise `Explosion_001.wav` / `Moteur_002.wav`, assainit les caractères de fichiers invalides et préserve le libellé d'origine dans `events.json` (propriété `label`). Au-delà de 40, les noms restent `FX_NNN.wav`.

**IA audio embarquée ?** Oui, envisageable avec un classifieur léger type YAMNet/TFLite (~521 catégories). **Non encore intégré** ; ne jamais remplacer le nom éditable par un nom imposé. Tester traduction française, confiance, licence, mémoire et latence Samsung avant implémentation. Qwen2.5-Omni-3B reste un outil PC facultatif.

**Vrai APK direct sans ZIP :** la CI 0.2.2 extrait dans `RUNNER_TEMP`, vérifie l'APK, puis commite le fichier brut sur la branche séparée `delivery/fxextractor-apk`. Aucun merge main ni Release. Lien à utiliser **après CI verte** : `https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.2.2-debug.apk`. Un artefact GitHub Actions peut être fourni dans une archive ; le lien raw est réellement le fichier .apk.

### APK 0.2.2 disponible et contrôlé (CI verte)
[**Télécharger le fichier APK directement sans ZIP**](https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.2.2-debug.apk). GitHub Actions [run 39069953534](https://github.com/greenpower2669/FxExtractor/actions/runs/39069953534), tests et Android verts ; publication raw confirmée dans l'arbre Git sur la branche de livraison, APK 33 989 octets. Ceci n'est pas une Release Play et l'essai physique Samsung reste nécessaire.

## v0.3.0 — véritable IA audio embarquée (17/10/2026)
YAMNet, modèle de classification acoustique de Google, analyse **réellement** les extraits du WAV sur Android : mono 16 kHz, fenêtres de 15 600 échantillons, scores de 521 catégories AudioSet via TensorFlow Lite 2.16.1. Les propositions (« Explosion », « Moteur », « Voix » ou classe originale si aucune traduction) apparaissent automatiquement après la découpe. Le score brut est indicatif et non une certitude ; l'utilisateur peut modifier tous les noms, une correction manuelle n'est jamais écrasée. Les effets synthétiques peuvent être mal reconnus.

Modèle et liste officielle de 521 classes **incorporés au fichier APK pendant le build GitHub**. Aucune permission INTERNET, aucun téléchargement de modèle ni transmission du son à l'exécution. La piste Qwen-Omni lourde reste uniquement sur PC. L'IA ne sépare pas des sons qui se chevauchent, elle les étiquette. Le modèle officiel et ses licences applicables doivent être préservés ; source : https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite

La CI doit valider : poids de modèle >1Mo, en-tête TFL3, 521 classes exactes, compilation et APK contenant les deux assets, plus lien raw direct. Les essais sur Samsung (latence et qualité des libellés) restent distincts des tests de compilation.

### État prouvé du modèle et empreinte
GitHub Actions run 39072658917 : SUCCESS, compilation native Android et tests Java ; APK brut v0.3.0 livré sur la branche delivery/fxextractor-apk (18 888 244 octets), et vérification de la présence des fichiers assets/yamnet.tflite et assets/yamnet_labels.txt dans l'APK. Poids téléchargés chez Google, SHA256 4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf. **Cette empreinte est maintenant verrouillée dans la CI**, de sorte que le build refuse un modèle distant différent. L'inférence sur Samsung reste à tester physiquement, car un build réussi ne prouve pas la latence ni la précision.

### Fichier APK v0.3.0 prêt (contrôle final)
[**FxExtractor-0.3.0-debug.apk — téléchargement direct, aucun ZIP**](https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.3.0-debug.apk) — 18 888 244 octets. Code aba5f44c, [workflow 39072851527](https://github.com/greenpower2669/FxExtractor/actions/runs/39072851527) SUCCESS. Le binaire contient yamnet.tflite et yamnet_labels.txt, aucune permission réseau. Un essai réel sur Samsung est encore indispensable pour valider qualité IA, temps de traitement et mémoire.

## Correctif v0.3.1 : reconnaissance IA sans réaction (18 octobre 2026)
L'initialisation du modèle YAMNet échouait : l'APK attendait strictement une entrée [1,15600], alors que le modèle Google officiel attend [15600] (sortie [1,521]). Le code accepte désormais les modèles de rang 1 ou 2 et fournit le tableau float attendu.

Le bouton IA reste cliquable pour expliquer les prérequis et les opérations déjà en cours. Un texte d'état à côté du bouton indique chargement, avancement 1/N, réussite ou erreur. Les résultats de chaque extrait sont visibles près de son champ de nom. Les erreurs de chargement natif TensorFlow Lite sont désormais signalées, sans masquer les champs de renommage.

Test CI supplémentaire : charger le modèle YAMNet avec le runtime LiteRT Python sur le runner GitHub, vérifier les dimensions et exécuter une véritable inférence sur un son silencieux, AVANT d'accepter la compilation APK. Cela ne remplace pas un test sur le Samsung Android 16 de Fab.

## 0.3.2 — 0 ms par défaut et correctif IA (18/10/2026)
**Demande Fab :** la préservation de fin de son doit accepter **0 ms**, valeur **par défaut**. Curseur désormais **0 à 1 500 ms**, par pas de 50 ms. À zéro, **aucune marge de fin supplémentaire** n'est ajoutée après la fin détectée ; la segmentation RMS et la protection de décroissance sont maintenues. L'ancien curseur était limité à 100..1500 ms et commençait à 600 ms.

Les correctifs IA 0.3.1 sont inclus : vrai tenseur d'entrée YAMNet `[15600]` (confirmé sur GitHub par inférence réelle sur un silence, classé Silence), bouton reconnaissance explicatif toujours cliquable, progression et erreurs visibles. Le modèle et ses 521 étiquettes sont dans l'APK. Le test réel sur téléphone Samsung reste nécessaire, un succès Linux n'établissant pas le comportement runtime Android.

### APK 0.3.2 prêt
[**Télécharger FxExtractor 0.3.2.apk directement (sans ZIP)**](https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.3.2-debug.apk). Run [39082262856](https://github.com/greenpower2669/FxExtractor/actions/runs/39082262856) SUCCESS, 18 888 244 octets, pas de Release/Play. Règle confirmée : défaut 0 ms, curseur 0–1500 ms ; correction YAMNet entrée rank1 et message de progression inclus.

## v0.3.3 — téléchargement direct individuel et formats de sortie (18/10/2026)
Chaque ligne de son disponible (jusqu'à 40 affichés) possède un bouton **« Enregistrer ce son (sans ZIP) »**. Après écoute et édition éventuelle du nom (y compris suggestion YAMNet modifiable), choisir le format de sortie tout en haut de la liste puis appuyer sous le son. Le sélecteur Android permet de choisir le dossier et de confirmer le fichier à enregistrer, par exemple `Explosion_001.wav` ou `Explosion_001.m4a`. **Aucune archive ZIP n'est créée** pour ce flux. Les boutons sont explicites et le statut dit si l'enregistrement a abouti ou échoué. Les sons 41 et suivants continuent d'être disponibles dans l'export global ZIP.

**Formats effectivement implémentés :**
- **WAV PCM16** mono/stéréo, **sans perte**, fréquence d'origine conservée : écrit directement depuis la zone de la source sélectionnée via WavIO.writeClip, sans conversion ni compression nouvelle.
- **M4A / AAC-LC** mono/stéréo **compressé**, 44,1 kHz avec rééchantillonnage linéaire et débit demandé 96 kbit/s mono ou 160 kbit/s stéréo. Encodage 100% sur l'appareil par `MediaCodec` et emballage MP4 via `MediaMuxer`. Le codec disponible sur chaque appareil détermine le résultat réel. Ce n'est pas du MP3 : Android ne fournit pas d'encodeur MP3 standard.

L'export global ZIP existe toujours ; il conserve les **WAV uniquement** et le fichier `events.json`. Les noms personnalisés sont assainis pour rester des noms de fichiers sûrs. Aucune permission réseau ni accès général au stockage n'est nécessaire : l'utilisateur choisit la destination avec `ACTION_CREATE_DOCUMENT`. Les fichiers sources sont inchangés ; le M4A est temporairement encodé en cache privé avant copie vers la destination. Si l'encodeur AAC n'est pas pris en charge ou manque de ressources sur le téléphone, un message d'erreur apparaît, et le WAV reste disponible.

La CI valide la création WAV direct avec les tests Java ; la compilation Android vérifie le module M4A, mais **un essai physique Samsung est nécessaire pour valider l'encodeur AAC**, le choix du dossier et l'écoute.

### APK 0.3.3 direct confirmé
[**Télécharger FxExtractor-0.3.3-debug.apk sans ZIP**](https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.3.3-debug.apk) (18 904 624 octets). Code `711bba5`, run CI [39123489681](https://github.com/greenpower2669/FxExtractor/actions/runs/39123489681) SUCCESS ; fichier brut vérifié sur la branche de livraison. Pour le moment, il faut tester le format M4A/AAC sur le Samsung ; WAV et IA sont soumis aux tests automatisés disponibles mais non vérifiés physiquement.

## v0.3.4 — MP3 individuel réel (18/10/2026)
Format ajouté à « Enregistrer ce son » : WAV / M4A AAC / **MP3**. Le MP3 est un véritable MPEG Layer III, pas un M4A renommé. L'encodeur natif hors ligne Shine, version épinglée ab5e3526b64af1a2eaa43aa6f441a7312e013519, produit 44,1 kHz, 128 kbit/s mono ou 192 kbit/s stéréo ; noms d'origine conservés sous forme safe, extension .mp3. Aucun téléchargement de modèle/codec pendant l'usage Android. Le fichier source WAV reste inchangé.

**Licence tierce Shine LGPL-2.0** et sources dans [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), original sur [toots/shine](https://github.com/toots/shine). Le pont JNI est sous app/src/main/cpp/shine_bridge.c, copies .so ARM64, ARM32 et x86_64 fabriquées par GitHub Actions à partir des sources, notice COPYING intégrée à l'APK.

Test MP3 Linux : encoder via JNI la forme d'onde WAV, vérifier au ffprobe qu'il s'agit bien du codec MP3, puis décoder via ffmpeg. La CI compile aussi l'APK et vérifie la bibliothèque .so dans le fichier brut. Pas de changement du ZIP global WAV. Tests physiques Samsung nécessaires pour confirmer qualité et latence ; Shine possède une analyse psychoacoustique moins avancée que LAME.

### APK v0.3.4 confirmé en CI (18 octobre 2026)
[**Télécharger FxExtractor-0.3.4-debug.apk directement sans ZIP**](https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.3.4-debug.apk) — 19 063 004 octets. GitHub Actions [39126300413](https://github.com/greenpower2669/FxExtractor/actions/runs/39126300413) SUCCESS, source 6ce5fe99, MP3 généré et décodé par FFmpeg sur runner, bibliothèques natives Android ARM64/ARM32/x86_64 effectivement présentes dans l'APK. L'enregistrement et la lecture sur Samsung n'ont pas encore fait l'objet d'un essai physique.


## 🎹 v0.3.5 — Laboratoire MIDI Gen AI v5 (branche d'essai, 18/10/2026)

- Nouvel écran « Laboratoire IA MIDI v5 » indépendant de l'extraction de FX, des exports WAV/M4A/MP3 et du modèle YAMNet.
- Vrai modèle MIDI Gen AI v5, Transformer 113M paramètres, via JS et ONNX Runtime Web, inférence locale sur Android après chargement. Dépendances amont épinglées au commit `17c7fb775789089dc848eb13b5fd477657f024ab` (MIT).
- **Poids volontairement non inclus dans l'APK** : ~115 Mo (CPU, recommandé) ou ~73 Mo (GPU WebGPU expérimental), téléchargés au premier clic depuis Hugging Face, puis mis en cache localement si WebView l'autorise. Le droit INTERNET ajouté sert à cette récupération optionnelle ; les vidéos, extraits et fichiers MIDI personnels ne sont pas envoyés à des serveurs.
- Amorce MIDI originale de deux mesures, ambiance rétro au choix, BPM, température et taille de génération ; suivi des tokens et du temps. Pré-écoute via oscillateurs WebAudio simples (pas un SoundFont) et export MIDI direct par Android SAF.
- Compilation GitHub Actions : modules source en .mjs + binaire WASM embarqués, modèle ONNX distant seulement sur clic. L'exécution réelle et la vitesse sur le Samsung **ne sont pas encore validées**.
- Après CI réussie, APK brut attendu dans `delivery/fxextractor-apk/apk/FxExtractor-0.3.5-MIDI-v5-debug.apk`, sans fusion main/Release.
- Sources : https://github.com/nicholasbien/midigenai et https://github.com/microsoft/onnxruntime.

**Livraison test v0.3.5 vérifiée (GitHub Actions) :** [APK brut direct .apk](https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.3.5-MIDI-v5-debug.apk) — 27 399 299 octets, [run CI 39150975283 SUCCESS](https://github.com/greenpower2669/FxExtractor/actions/runs/39150975283) ; [AAB non signé séparé](https://github.com/greenpower2669/FxExtractor/actions/runs/39150975283). L'inférence MIDI sur appareil n'est pas encore attestée.

 
## v0.3.6 — correction lancement IA MIDI, diagnostic visible (18/10/2026)
Retour Samsung : « J'ai voulu charger et il ne se passe rien ; options du bas grisées ». Défaut certain : les messages de chargement de la 0.3.5 étaient très bas hors de l'écran ; les importations JS statiques pouvaient aussi échouer avant d'enregistrer le clic. La 0.3.6 initialise immédiatement les boutons et le diagnostic puis charge dynamiquement ONNX, affiche 4 étapes, progression et erreurs près de Charger, permet annulation, réessai et copie du diagnostic natif. Le bouton « Tester les boutons et JavaScript » fonctionne sans réseau. Test CI Node `tools/test_midi_ui.mjs` : absence de silence lors d'un module indisponible. La génération réelle sur Samsung reste à tester : ne pas annoncer le correctif comme matériellement validé.
Livraison après GitHub Actions SUCCESS : `https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.3.6-MIDI-v5-debug.apk`.

**v0.3.6 livrée :** [Télécharger l'APK brut direct](https://raw.githubusercontent.com/greenpower2669/FxExtractor/delivery/fxextractor-apk/apk/FxExtractor-0.3.6-MIDI-v5-debug.apk), 27 401 723 octets, [CI run 39153583595 réussi](https://github.com/greenpower2669/FxExtractor/actions/runs/39153583595). Les commandes d'extraction existantes passent toujours les tests ; modèle MIDI non encore attesté sur Samsung.


## v0.3.7 — moteur et modèle MIDI intégrés dans l'APK (18 octobre 2026)

À la demande de Fab : terminologie précise et **aucun téléchargement sur le téléphone**. Les ressources `ort.webgpu.bundle.min.mjs`, `ort-wasm-simd-threaded.jsep.wasm`, les modules MIDI, `v5/tokenizer.json`, `v5/onnx/manifest.json` et le checkpoint ONNX **CPU int8** (~109,3 Mio) sont ajoutés à l'APK au moment du build CI. `tools/embed_midi_model.py` vérifie intégrité SHA-256 et taille du vrai checkpoint avant packaging. Android n'a plus la permission INTERNET.

Le moteur WebAssembly est lu explicitement depuis l'APK et fourni à ONNX Runtime par `ort.env.wasm.wasmBinary` pour contourner l'erreur Android WebView (« both async and sync fetching of the wasm failed »). Lorsque Fab ouvre l'écran MIDI, le modèle s'initialise automatiquement **en mémoire**, sans bouton « télécharger ». En cas d'échec, un bouton « Réessayer le chargement en mémoire » reste disponible. Les boutons Générer/Écouter/Enregistrer sont liés aux états réels.

⚠️ Conséquence : l'APK contenant les poids dépasse probablement 100 Mo, limite stricte d'un blob GitHub ; impossible de le publier en lien `raw.githubusercontent.com` dans `delivery/fxextractor-apk`. La CI doit utiliser l'artefact GitHub Actions **fichier APK brut** (`archive: false`), sans ZIP à décompresser manuellement. Ne donner un lien qu'après vérification d'un run vert et du fichier de sortie. Test physique de génération sur Samsung encore nécessaire.

La possibilité d'installer d'autres modèles/poids dans les options sera examinée séparément, après validation de ce modèle CPU par défaut. Aucun modèle additionnel n'est prétendu supporté aujourd'hui. Pas de merge main/Release.


### ✅ Livraison v0.3.7 hors ligne vérifiée (18 octobre 2026)
GitHub Actions run [39157742071](https://github.com/greenpower2669/FxExtractor/actions/runs/39157742071) **SUCCESS**, jobs tests+Android verts. L'APK brut `FxExtractor-0.3.7-MIDI-OFFLINE-debug.apk` a été publié comme **artefact GitHub Actions sans archive** (`archive: false`), ID `12148160390`, **162 031 457 octets**. Le modèle CPU ONNX v5 et le WASM ont été vérifiés dans le paquet APK durant la CI (SHA256 contre manifest). L'APK est trop gros pour la branche Git (limite 100 Mo) : pas de lien `raw.githubusercontent.com` en v0.3.7. Télécharger depuis [l'artefact du run GitHub](https://github.com/greenpower2669/FxExtractor/actions/runs/39157742071/artifacts/12148160390). AAB distinct non signé, non installable. Sur Samsung, l'inférence n'est PAS encore testée après cette correction ; vérifier les performances réelles hors ligne.
