# FxExtractor — historique de diagnostic
## D001 — initialisation 17/10/2026
La branche main ne comportait que LICENSE. Prototype créé sur feature/audio-fx-analyzer-v1. Aucun binaire ni essai matériel existants. Séparer CI et test sur Samsung.
## D002 — limites de l'analyse
Enveloppe RMS sur WAV PCM16 uniquement. Une superposition musique/bruit ne peut pas être démixée par le seul seuil ; le comportement sur faible souffle et bruit continu doit être évalué sur cas réels. L'API MediaPlayer peut varier selon les constructeurs ; valider l'écoute sur téléphone.

## D003 — CI Android (17/10/2026)
Run `39059711095` : SDK `android-actions/setup-android@v3` échoue en demandant le paquet retiré `tools`, les tests Java sont verts. Run `39059798918` : tentative sdkmanager direct sans setup échoue car non dans PATH. Correctif `android-actions/setup-android@v4` avec `packages: ''` suivi de `sdkmanager platforms;android-35 build-tools;35.0.0`. Run `39059864373` : Android + tests verts. Run final `39060083448` : syntaxe adaptateur Qwen + tests Java + APK/AAB tous verts. Android/Samsung réel et Qwen GPU non testés.
