#!/usr/bin/env bash
# Reproducible Shine MP3 native build for Android + actual Linux JNI smoke test.
set -euo pipefail
: "${RUNNER_TEMP:?This build must run in GitHub Actions}"
: "${ANDROID_HOME:?Android SDK required}"
: "${JAVA_HOME:?Java 17 required}"
SHA="ab5e3526b64af1a2eaa43aa6f441a7312e013519"
UPSTREAM="$RUNNER_TEMP/fx-shine-upstream"
mkdir -p "$UPSTREAM"
git -C "$UPSTREAM" init -q
git -C "$UPSTREAM" fetch --quiet --depth=1 https://github.com/toots/shine.git "$SHA"
git -C "$UPSTREAM" checkout --detach --quiet FETCH_HEAD
test "$(git -C "$UPSTREAM" rev-parse HEAD)" = "$SHA"
SRC="$UPSTREAM/src/lib"
test -f "$SRC/layer3.h"
cp "$UPSTREAM/COPYING" app/src/main/assets/shine_lgpl_v2.txt
SOURCES=(
  "$SRC/bitstream.c"
  "$SRC/huffman.c"
  "$SRC/l3bitstream.c"
  "$SRC/l3loop.c"
  "$SRC/l3mdct.c"
  "$SRC/l3subband.c"
  "$SRC/layer3.c"
  "$SRC/reservoir.c"
  "$SRC/tables.c"
  app/src/main/cpp/shine_bridge.c
)
printf 'Pinned Shine source SHA: %s\n' "$SHA"
HOST_LIB="$RUNNER_TEMP/libshinefx.so"
gcc -shared -fPIC -O2 -std=gnu99 \
  -I"$SRC" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "${SOURCES[@]}" -lm -o "$HOST_LIB"
mkdir -p build/mp3-native-test
javac -d build/mp3-native-test \
  app/src/main/java/fr/fxextractor/WavIO.java \
  app/src/main/java/fr/fxextractor/FxAnalyzer.java \
  app/src/main/java/fr/fxextractor/Mp3ShineEncoder.java \
  tools/TestMp3Shine.java
java -Djava.library.path="$RUNNER_TEMP" -cp build/mp3-native-test \
  TestMp3Shine "$RUNNER_TEMP/fx-test.mp3"
if ! command -v ffprobe >/dev/null || ! command -v ffmpeg >/dev/null; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq ffmpeg
fi
ffprobe -v error -select_streams a:0 \
  -show_entries stream=codec_name,sample_rate,channels \
  -of default=noprint_wrappers=1 "$RUNNER_TEMP/fx-test.mp3" > "$RUNNER_TEMP/fx-probe.txt"
cat "$RUNNER_TEMP/fx-probe.txt"
grep -Fx "codec_name=mp3" "$RUNNER_TEMP/fx-probe.txt"
grep -Fx "sample_rate=44100" "$RUNNER_TEMP/fx-probe.txt"
grep -Fx "channels=1" "$RUNNER_TEMP/fx-probe.txt"
ffmpeg -v error -xerror -i "$RUNNER_TEMP/fx-test.mp3" -f null -
echo 'PASS MP3: native Shine -> valid MPEG audio -> FFmpeg decode'

NDK_VERSION="27.2.12479018"
NDK="$ANDROID_HOME/ndk/$NDK_VERSION"
if [[ ! -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang" ]]; then
  sdkmanager "ndk;$NDK_VERSION"
fi
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$BIN/aarch64-linux-android26-clang"
for spec in "arm64-v8a:aarch64-linux-android26-clang" \
            "armeabi-v7a:armv7a-linux-androideabi26-clang" \
            "x86_64:x86_64-linux-android26-clang"; do
  ABI="${spec%%:*}"
  COMPILER="${spec#*:}"
  mkdir -p "app/src/main/jniLibs/$ABI"
  "$BIN/$COMPILER" -shared -fPIC -O2 -std=gnu99 \
    -I"$SRC" "${SOURCES[@]}" -lm \
    -Wl,-soname,libshinefx.so \
    -o "app/src/main/jniLibs/$ABI/libshinefx.so"
  test -s "app/src/main/jniLibs/$ABI/libshinefx.so"
  file "app/src/main/jniLibs/$ABI/libshinefx.so"
done
echo 'PASS Android MP3 JNI: arm64-v8a, armeabi-v7a, x86_64'
