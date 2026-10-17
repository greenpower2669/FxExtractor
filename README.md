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
