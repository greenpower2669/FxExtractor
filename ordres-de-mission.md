# FxExtractor — ordres de mission
## 2026-10-17 — construction initiale
Demande de Fab : « Ok, fait le mon repo FxExtractor », dans le contexte de l'analyse audio Qwen, des silences et de la découpe de voix/FX. Interprétation conservatrice : réaliser une première base locale, utile, reproductible, sans intégrer imprudemment Qwen-Omni lourd dans l'APK. Détection d'événements sonore par RMS et découpage non destructif, export réutilisable WAV, interface Android, CI et tests. Qwen-Omni pour comprendre/étiqueter le son reste une extension explicitement notée, pas une capacité fictive. Aucune altération de VisionMaxRead ou Djipie, aucun merge main, aucune Release/Play.

## Qwen optionnel
Un adaptateur Python Qwen2.5-Omni-3B, limité aux FX WAV issus des ZIP, produit des descriptions françaises. Aucun poids n'est installé dans l'APK ; aucune analyse sémantique Android n'est revendiquée. Inférence GPU/conditions de licence à confirmer séparément.
