# 🎛 FxExtractor — découpeur sonore local (Android + CLI)

**Prototype v0.1.0** créé sur `feature/audio-fx-analyzer-v1`, sans fusion automatique vers `main`.

## Fonctions disponibles
- Choisir un fichier **WAV PCM 16 bits**, mono ou stéréo (8–192 kHz), jusqu'à **200 Mo**.
- Détection de sons isolés par énergie RMS sur fenêtres de ~20 ms : seuil adaptatif, silence, marge de 80–100 ms, fusion de petits blancs (<140 ms) et suppression des micro-événements (<70 ms).
- Régler la sensibilité, consulter des horodatages, écouter les 40 premiers événements.
- Exporter un **ZIP de WAV indépendants** et un rapport `events.json` avec les bornes en millisecondes.
- Traitement **entièrement sur l'appareil**, aucune permission Internet ni stockage global.
- Une CLI Java sans dépendance pour analyser et exporter un WAV sur PC.
- Tests Java synthétiques + workflow GitHub Actions générant **APK directement téléchargeable sans ZIP supplémentaire** et AAB distinct.

### Installer depuis GitHub Actions
Ouvrir [Actions](https://github.com/greenpower2669/FxExtractor/actions), workflow *FxExtractor Android and DSP tests*, dernier run **vert** sur la branche de travail, puis télécharger l'artefact `FxExtractor-0.1.0-debug.apk`. Cet APK debug est destiné au test : **ce n'est pas une publication Play**. Aucun lien d'APK n'est déclaré valide tant que la CI n'a pas réussi.

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
