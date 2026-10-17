# FxExtractor — contrat et portée
Application Android native hors ligne pour repérer des événements dans l'audio, les écouter et les exporter en WAV séparés. Première implémentation 0.1.0 : WAV PCM16 mono/stéréo 8–192 kHz, source ≤200 Mo, extraction par enveloppe RMS + seuil adaptatif ; export ZIP avec events.json. Ne pas présenter cette approche comme une séparation de sources superposées. Aucun réseau ni échantillons personnels committés. Qwen-Omni audio = piste ultérieure indépendante, sans gros modèle dans l'APK de base. Ne pas mélanger avec le Qwen Vision de VisionMaxRead. Ne pas merger main, publier Release/Play ou remplacer la signature sans feu vert explicite. Livrer APK debug et AAB unsigned séparés, noms compréhensibles, icône originale.

## Preuve build 0.1.0 (17/10/2026)
CI `39060083448` verte sur code `6e2f2c13760b46ec5e5e6f01c3a8f3b093b7ab33` : tests et syntaxe Qwen validés ; Gradle APK/AAB réussis. APK direct `12111057669`, 24 377 octets ; AAB unsigned `12111236375`, 18 264 octets. Pas de preuve d'essai sur Samsung, ni d'inférence Qwen réelle. La mise à jour documentaire post-CI n'altère aucun code exécutable.

## v0.2.0 — Import vidéo/audio
Première piste audio des conteneurs pris en charge par Android (MediaExtractor/MediaCodec) décodée sans Internet en WAV PCM16. L'analyse RMS et export ZIP restent inchangés. 1 Go source max ; 200 Mo/90 minutes PCM. v0.2.0 code2, aucune preuve matérielle avant essai Samsung.
