#!/usr/bin/env python3
"""Structural checks for SA3 crash forensics.
Not a substitute for a runtime Android force-stop/OOM reproduction test.
"""
from pathlib import Path

src=Path("app/src/main/java/fr/fxextractor")
logger=(src/"FxDebugLog.java").read_text(encoding="utf-8")
engine=(src/"StableAudioEngine.kt").read_text(encoding="utf-8")
activity=(src/"StableAudioActivity.java").read_text(encoding="utf-8")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
application=(src/"FxApplication.java").read_text(encoding="utf-8")
manifest=Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

for marker in (
    "FileOutputStream(file,true)",
    "getFD().sync()",
    "sa3-run.pending",
    "sa3-debug.log",
    "sa3-debug.previous.log",
    "GENERATION_BEGIN",
    "GENERATION_END",
    "GENERATION_ERROR",
    "PREVIOUS_GENERATION_INCOMPLETE",
    "UNCAUGHT",
    "getLog()",
    "Copier tout",
    "Partager",
):
    assert marker in logger, f"Logger missing: {marker}"
assert "android:name=\".FxApplication\"" in manifest
assert "Thread.setDefaultUncaughtExceptionHandler" in application
assert "APP_LOW_MEMORY" in application
for marker in ("FxDebugLog.begin(", "FxDebugLog.error(", "FxDebugLog.finish(",
               "FxDebugLog.checkpoint(", "FxDebugLog.show(this)"):
    assert marker in activity, f"Missing activity instrumentation: {marker}"
for marker in ("tokenizer.open.before","tokenizer.encode.after",
               "LiteRT.load.before","T5Gemma.run.before",
               "DiT.run.before","SAME-S.decoder.run.before"):
    assert marker in engine, f"Model stage missing: {marker}"
assert "FxDebugLog.show(this)" in main
assert "FxDebugLog.hasPending()" in main
assert "INTERNET" not in manifest
print("PASS debug source checks: durable pending marker, memory traces, crash handler, Android log UI")
print("NOTICE: Java/Dex runtime, native aborts and OOM must still be tested on device.")
