# FxExtractor — ordres de mission
## 2026-10-17 — construction initiale
Demande de Fab : « Ok, fait le mon repo FxExtractor », dans le contexte de l'analyse audio Qwen, des silences et de la découpe de voix/FX. Interprétation conservatrice : réaliser une première base locale, utile, reproductible, sans intégrer imprudemment Qwen-Omni lourd dans l'APK. Détection d'événements sonore par RMS et découpage non destructif, export réutilisable WAV, interface Android, CI et tests. Qwen-Omni pour comprendre/étiqueter le son reste une extension explicitement notée, pas une capacité fictive. Aucune altération de VisionMaxRead ou Djipie, aucun merge main, aucune Release/Play.

## Qwen optionnel
Un adaptateur Python Qwen2.5-Omni-3B, limité aux FX WAV issus des ZIP, produit des descriptions françaises. Aucun poids n'est installé dans l'APK ; aucune analyse sémantique Android n'est revendiquée. Inférence GPU/conditions de licence à confirmer séparément.

## Règle immuable — livraison APK
À chaque livraison autorisée, GitHub Actions extrait l’artefact dans `RUNNER_TEMP`, vérifie le `.apk` et fournit à Fab un **lien direct vers le fichier `.apk`**, installable sans dézipper. Ne jamais fournir un ZIP ou un lien d’artefact imposant une extraction manuelle. Aucune Release sans accord explicite.

## 17/10/2026 — importer les vidéos
Après retour Fab : ajouter import vidéo/audio direct, extraction locale de la première piste audio et découpes FX WAV existantes. Ne pas prétendre accepter tous les codecs Android. Garder `main` intact, et appliquer la règle APK direct extrait/vérifié dans RUNNER_TEMP. Aucune Release sans accord.

## 17/10/2026 — retour écoute et état IA
Retour Fab : impressions de sons tronqués avant fin et question sur les librairies IA. Correction limitée au suivi des queues, à un padding de fin réglable et à l'arrêt de pré-écoute asservi à la position sonore ; garder extraction vidéo et export WAV existants. **Ne pas présenter le modèle Qwen-Omni comme intégré à Android : uniquement adaptateur Python PC `tools/qwen_tag_fx.py` non validé matériellement.** Conserver règle immuable APK direct GitHub et aucun merge main/Release sans nouvel accord.

## 17/10/2026 — noms des sons et IA future
Demande Fab : « Une ia est intégrable ou un nom modifiable comme explosio ». Chaque FX doit pouvoir recevoir un nom éditable, ex. Explosion_001.wav, conservé dans events.json. IA sonore possible mais non intégrée : étudier YAMNet local ; Qwen-Omni reste outil PC. Maintenir la règle immuable : APK réellement brut accessible via lien raw sur branche de livraison séparée, décompressé/vérifié dans RUNNER_TEMP GitHub. Aucun merge main ni Release sans accord.
