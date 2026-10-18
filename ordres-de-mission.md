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

### Preuve livraison v0.2.2
CI 39069953534 verte, fichier `apk/FxExtractor-0.2.2-debug.apk` de 33 989 octets présent dans `delivery/fxextractor-apk` ; lien raw dans README, sans ZIP côté Fab. La branche main reste intacte.

## 17/10/2026 — intégrer une IA acoustique
Autorisation de Fab : « Ok o. Intègre l ia ». YAMNet/TFLite doit être **effectivement embarqué** dans l'APK avec ses 521 labels, fonctionner hors ligne et suggérer automatiquement des noms toujours modifiables. Afficher les scores, ne jamais écraser les corrections humaines. Qwen-Omni PC reste séparé. Respect de la règle APK brut GitHub par dossier temporaire et tests, sans merge main ni Release.

### Vérification de la livraison IA 0.3.0
GitHub run 39072851527 SUCCESS ; APK brut réellement disponible dans delivery/fxextractor-apk, modèle YAMNet et labels embarqués et SHA256 verrouillé. Aucun merge main ni Release. Prochain test matériel Android à effectuer par Fab.

## 18/10/2026 — bouton IA sans réaction
Retour Fab : « Quand je click reconnaissance par ia il ne se passe rien il me semble ? » Réparer la vraie forme d'entrée YAMNet, assurer un affichage clair prérequis/progrès/erreurs au clic et près des sons, ajouter test d'inférence réelle. Préserver noms manuels, décodage vidéo, découpe FX et règle immuable de lien .apk brut extrait dans GitHub RUNNER_TEMP. Aucun merge main ni Release.

## 18/10/2026 — 0 ms de préservation par défaut
Instruction de Fab confirmée par capture écran : le curseur « Préserver les fins de sons » doit **permettre 0 ms, et 0 ms est la valeur initiale par défaut**, réglage libre jusqu'à 1500 ms. À 0, pas de rallonge supplémentaire au-delà de la fin détectée (conserver la détection de la décroissance naturelle). Préserver le correctif YAMNet et livrer l'APK 0.3.2 brut vérifié depuis GitHub RUNNER_TEMP ; pas de merge main ni Release.

### Preuve de livraison correctifs 0.3.2
GitHub Actions 39082262856 SUCCESS sur code d9761b9, APK brut direct 18 888 244 octets confirmé dans delivery/fxextractor-apk ; 0 ms est le défaut, IA modèle correct et messages visibles. Pas de merge main ni Release.

## 18/10/2026 — sauvegarde directe par son et formats
Demande de Fab : « Super proposer un téléchargement direct par ligne et des option de format de sortie ». L'application doit permettre de **sauvegarder chaque effet affiché individuellement directement dans un fichier audio choisi dans Android, sans ZIP**, avec nom IA ou manuel. Ajouter choix réel **WAV PCM16 sans perte / M4A AAC compressé** ; ne pas faire croire que MP3 est pris en charge. Garder archive ZIP WAV pour export global de tous les sons, sans rupture de l'extraction, du curseur de fin à 0 ms, ni du modèle YAMNet. Tester JVM WAV et compiler AAC Android, puis livrer **vrai APK brut** via RUNNER_TEMP et branche delivery. Aucun merge main ni Release.

### Vérification livraison v0.3.3
GitHub Actions run 39123489681 vert ; APK brut `delivery/fxextractor-apk/apk/FxExtractor-0.3.3-debug.apk` confirmé 18 904 624 octets, sans ZIP, pas de merge main ni Release. Tester M4A sur téléphone pour confirmer le codec.

## 18/10/2026 — MP3 demandé
Fab : « Mp3 aussi? ». Ajouter format MP3 réel à l'enregistrement individuel sans enlever WAV et M4A, ni modifier le ZIP WAV global. Source Shine LGPL-2.0 épinglée, notices de licence, vrai test d'encodage et de décodage en CI, puis livraison APK brut direct sur branche delivery après extraction RUNNER_TEMP. Aucun merge main ni Release.

### Livraison MP3 v0.3.4
Run 39126300413 réussi, tests MP3 encode+decode réels, APK brut 19 063 004 octets sur la branche delivery/fxextractor-apk, sans fichier ZIP à décompresser, ni merge main/Release.


## 18/10/2026 — mission « tester MIDI Gen AI v5 »
Fab : « On va la tester, rajoute la à fxextractor notre app apk ». Développer un laboratoire local distinct, permettre la génération et export MIDI, ne pas casser l'extraction FX, préserver les formats et la classification YAMNet. Première récupération des poids uniquement après action explicite ; ensuite inférence sur téléphone. APK brut livré par GitHub Actions sur branche séparée, jamais main/Release sans validation.

 
## 18/10/2026 — correction retour MIDI 0.3.5
Fab : « J'ai lancé le truc midi, j'ai voulu charger et il se passe rien [...] options du bas [...] grisées. » Autorisation implicite de diagnostic et correction ciblée sur branche laboratoire. Corriger retour d'état et rendre erreurs visibles, ne pas changer extraction FX/YAMNet. Livrer .apk brut GitHub et mettre à jour todo.md si test matériel reste en attente, aucune fusion main.
