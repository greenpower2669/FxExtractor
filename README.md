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
