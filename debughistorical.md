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
