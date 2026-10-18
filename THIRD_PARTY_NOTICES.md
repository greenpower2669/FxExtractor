# Third-party native MP3 encoder: Shine

FxExtractor bundles the Shine fixed-point MPEG Layer III encoder for offline
conversion of individual sound effects to actual .mp3 files.

Original project: https://github.com/toots/shine
Pinned upstream revision: ab5e3526b64af1a2eaa43aa6f441a7312e013519
Source at that revision: https://github.com/toots/shine/tree/ab5e3526b64af1a2eaa43aa6f441a7312e013519
License: GNU Library General Public License, version 2.0 (LGPL-2.0).
Full license: https://github.com/toots/shine/blob/ab5e3526b64af1a2eaa43aa6f441a7312e013519/COPYING

The original C source files are downloaded **unmodified** from that revision at
build time, and compiled into an Android shared library (`libshinefx.so`) with
FxExtractor's JNI adapter `app/src/main/cpp/shine_bridge.c`. The JNI adapter
source and full reproducible build steps are published in this GitHub repo.
The upstream source and COPYING file remain freely accessible from the URL
above. CI copies COPYING into the APK's assets to give license access without
Internet. You may replace or rebuild Shine subject to the license's conditions.

Shine does not implement a modern psychoacoustic model: MP3 quality may be
lower than a modern encoder at the same bitrate. Use WAV for lossless source
preservation.
