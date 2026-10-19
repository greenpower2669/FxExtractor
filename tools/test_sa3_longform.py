#!/usr/bin/env python3
"""Static guards for genuine segmented SA3 long-form rendering.
These do NOT prove on-device PCM quality or runtime stability."""
from pathlib import Path
p=Path("app/src/main/java/fr/fxextractor")
ui=(p/"StableAudioActivity.java").read_text(encoding="utf-8")
coordinator=(p/"Sa3LongFormCoordinator.java").read_text(encoding="utf-8")
ipc=(p/"Sa3Coordinator.java").read_text(encoding="utf-8")
service=(p/"Sa3Service.java").read_text(encoding="utf-8")
wav=(p/"Sa3WavChain.java").read_text(encoding="utf-8")
seed=(p/"StableAudioSeedInput.kt").read_text(encoding="utf-8")
math=(p/"StableAudioMath.java").read_text(encoding="utf-8")
for marker in (
    "duration.setMax(179)", "original.wav", "launchLongPipeline",
    "Sa3LongFormCoordinator", "longPipeline.cancel()", "total>180.0001f",
):
    assert marker in ui, ("UI long-form missing", marker)
for marker in (
    "RENDER_FRAMES=Sa3WavChain.RATE*8", "Sa3Continuity.contextFrames(contextSeconds,producedFrames)",
    "Sa3WavChain.append", "Sa3WavChain.tail", "Sa3WavChain.complete",
    "firstChunk=false", "firstPrimed", "text.bin", "skipText",
):
    assert marker in coordinator, ("Long-form state machine missing",marker)
assert 'startStage(probeOnly||skipText?1:0)' in ipc
assert 'args.putBoolean("longForm",longForm)' in ipc
assert 'new File(dir,"music.wav")' in service
assert "MAX_FRAMES=RATE*180" in wav and "readFully(buf,0,n)" in wav
assert "require(maxSeconds in 1f..180f)" in seed
assert "samples > SAMPLE_RATE * 180" in math
assert "Sa3WavChain.frames(part)" in ui
assert 'Sa3Continuity.inspect(' in coordinator
assert 'SA3_AUDIO_QC_RETRY' in coordinator
assert 'sa3_context_seconds' in ui
assert 'sa3_retry_silence' in ui
assert 'SA3_SEED_PCM validFrames=' in (p/"StableAudioEngine.kt").read_text()
assert "stable_audio3_partial_recovered.wav" in ui
assert "Récupérer les parties déjà générées" in ui
assert "StableAudioMath.decoderWindow(padded,256,even,w.start,w.size)" in (p/"StableAudioEngine.kt").read_text()
assert "SAME-S.decoder.pad valid=" in (p/"StableAudioEngine.kt").read_text()
assert "android.permission.INTERNET" not in Path("app/src/main/AndroidManifest.xml").read_text()
print("PASS SA3 3-min: bounded chunks, rolling 2-s context, no duplicate audio, single WAV, offline")
print("NOTE Actual acoustic continuity and successful 3-min export need Android device test.")
