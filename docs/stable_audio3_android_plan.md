# Mission Stable Audio 3 Small-Music — IA musicale audio 100 % locale sur Android

## Décision Fab (18 octobre 2026)
Conserver intégralement le laboratoire MIDI Gen AI v5 (v0.3.8) ; créer un second laboratoire consacré à la **musique audio directement générée** par **Stable Audio 3 Small-Music**. Choix de livraison : **un gros APK tout compris**, y compris les poids/encodeur/décodeur, sans téléchargement au premier lancement ni compte en ligne. Initialisation en mémoire uniquement à l'entrée du laboratoire ; libérer les ressources en le quittant ; ne pas pénaliser le fonctionnement ordinaire de FxExtractor. Ne pas merger vers main/release sans approbation. Ne pas publier d'APK prétendument fonctionnel sans inférence réelle testée.

## Provenance officielle vérifiée
- Code: https://github.com/Stability-AI/stable-audio-3/tree/main/optimized/tflite
- Poids optimisés: https://huggingface.co/stabilityai/stable-audio-3-optimized/tree/main/tflite
- Manifest officiel d'options: `optimized/tflite/scripts/weights.py`.
- Guide: https://github.com/Stability-AI/stable-audio-3/blob/main/optimized/tflite/README.md
- Licence Stability AI Community: https://huggingface.co/stabilityai/stable-audio-3-optimized/blob/main/LICENSE.md
- Gemma Terms: https://huggingface.co/stabilityai/stable-audio-3-optimized/blob/main/LICENSE_GEMMA.md

## Configuration ciblée en première approche
- **Générateur** DiT Small-Music int8 : `tflite/sa3-sm-music/dit_w8a8.tflite`, environ 0,5 Go.
- **Conditionneur de texte** T5Gemma fp16 : `tflite/t5gemma/encoder_fp16.tflite`, environ 0,6 Go.
- **Décodeur audio** SAME-S w8a8 : `tflite/same-s/dec_w8a8.tflite`, environ 0,09 Go.
- **Tokeniseur** SentencePiece : `tokenizer.model`, environ 4,24 Mo.
- L'encodeur SAME-S `enc_w8a8.tflite` (~0,06 Go) n'est **pas nécessaire pour texte → audio** ; il le sera pour audio → audio / inpainting dans une phase ultérieure.
- Poids totaux du mode texte-vers-WAV : ordre de grandeur 1,2 Go ; APK avec l'app et MIDI davantage. Taille finale à mesurer, ne pas présenter 1,3 Go comme garantie.
- Format : WAV stéréo 44,1 kHz ; CPU avec LiteRT 2.2+ recommandé par l'éditeur ; attention : l'app actuelle utilise `org.tensorflow:tensorflow-lite:2.16.1`, insuffisant à présumer compatible avec les nouveaux graphes rung multi-signatures.
- Premiers contrôles courts 20 / 30 secondes, prompts anglais (modèle entraîné sur descriptions anglaises) ; interface française possible avec prompts prédéfinis/traduction locale explicite.

## Verrous à auditer et démontrer avant intégration
1. **Compatibilité Android arm64 :** trouver LiteRT natif/Java réellement compatible avec les graphes multi-signatures, XNNPACK et les opérateurs nécessaires. Tester l'ouverture et `allocateTensors` avec un minuscule fixture, puis le vrai modèle, sans crash mémoire. Ne pas mélanger runtime 2.16.1 et graphes requérant >=2.2.
2. **Tokenizer SentencePiece :** implémenter/embarquer un moteur local ARM64 sous licence compatible. Vérifier sorties face aux tokens de référence du runtime `sa3_tflite.py`.
3. **Port fidèle de génération :** T5Gemma encoder → embeddings + masks ; calculs de schedule/seed et 8 étapes `pingpong` du DiT rung, signatures global conditioning, CFG=1 ; SAME-S decoder → PCM 16-bit stéréo ; aucune synthèse factice ou simple lecteur d'échantillon à présenter comme IA.
4. **Mémoire :** mesurer RSS/PSS Android, chargements paresseux, arrêter/annuler sans fuite, isoler l'inférence dans un processus si nécessaire. Le guide officiel évoque ~0,8 Go pour le seul DiT int8, sans compter T5 et décodeur.
5. **Licence + redistribution :** distribuer `LICENSE.md`, `LICENSE_GEMMA.md` et notices dans l'APK ; afficher `Powered by Stability AI`. Vérifier les termes d'usage avant publication, notamment la redistribution des poids et l'utilisation commerciale.
6. **Packaging/delivery :** les poids ne sont pas à committer dans Git (>100 Mo/fichier). Les obtenir sur le runner GitHub Actions, épingler révision et empreintes SHA256, vérifier chaque asset dans l'APK. Publier l'APK *brut, sans ZIP à décompresser chez Fab*, sous artefact GitHub Actions `archive:false`; vérifier limite de taille GitHub Actions et espace disque du runner. Aucune Release/Play/merge automatique.
7. **Validation :** (a) test CPU réel de la chaîne sur runner Linux pour produire un WAV non silencieux ; (b) test Android instrumentation sur ARM64 si disponible ; (c) essai Samsung SM-A576B sur courts clips hors ligne. L'APK n'est considéré prêt que lorsque de l'audio musical est réellement produit.

## Interface envisagée (après validation)
Deux laboratoires séparés : « MIDI IA v5 » conservé, « Musique audio IA (Stable Audio 3) » ajouté. Choix d'ambiance (électro pétillante/arcade/synthpop/ambient), champ descriptif, durée courte, lancer / arrêter, journal d'étapes, écoute immédiate et export WAV (MP3 en seconde étape avec l'encodeur Shine existant). Messages précis : « modèles installés dans l'APK », « chargement en mémoire », « génération en cours », « sauvegarder ».

## État honnête au 18/10/2026
**Étude de faisabilité et architecture seulement.** Les poids Stable Audio 3 ne sont pas encore intégrés, et aucune génération musicale sur le Samsung n'a été prouvée. Ne pas livrer un bouton muet ou des sons préfabriqués sous étiquette « IA ». Conserver 0.3.8 comme dernière version MIDI vérifiée par Chromium, en attente de test Samsung.
