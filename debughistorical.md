# FxExtractor — historique de diagnostic
## D001 — initialisation 17/10/2026
La branche main ne comportait que LICENSE. Prototype créé sur feature/audio-fx-analyzer-v1. Aucun binaire ni essai matériel existants. Séparer CI et test sur Samsung.
## D002 — limites de l'analyse
Enveloppe RMS sur WAV PCM16 uniquement. Une superposition musique/bruit ne peut pas être démixée par le seul seuil ; le comportement sur faible souffle et bruit continu doit être évalué sur cas réels. L'API MediaPlayer peut varier selon les constructeurs ; valider l'écoute sur téléphone.
