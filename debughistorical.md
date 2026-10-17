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
