#!/usr/bin/env python3
"""Structural regression checks for XNNPACK A/B trial (not a real Android RAM benchmark)."""
from pathlib import Path
p = Path("app/src/main/java/fr/fxextractor")
engine = (p / "StableAudioEngine.kt").read_text()
cache = (p / "Sa3CacheSettings.java").read_text()
coordinator = (p / "Sa3Coordinator.java").read_text()
service = (p / "Sa3Service.java").read_text()
activity = (p / "StableAudioActivity.java").read_text()
manifest = Path("app/src/main/AndroidManifest.xml").read_text()
gradle = Path("app/build.gradle").read_text()
for marker in (
    'xnnPackWeightCachePath=cachePath',
    'Sa3CacheSettings.cachePath(context,cacheMode)',
    'path=="sa3/sa3-sm-music/dit_w8a8.tflite"',
    'Sa3CacheSettings.logAfterLoad(cachePath)',
    'FxDebugLog.checkpoint("LiteRT.load.after "+path)',
):
    assert marker in engine, ("Missing engine cache integration",marker)
for marker in ('return null;', 'return ":memory";', 'weights-manifest.json',
               'getCacheDir()', 'getUsableSpace()', 'obsolete_deleted',
               'Sa3CacheSettings.DEFAULT', 'SA3_XNN_CACHE mode='):
    assert marker in cache or marker in activity, ("Cache policy missing",marker)
assert 'Sa3CacheSettings.normalize(args.getInt("cacheMode"' in service
assert 'args.putInt("cacheMode",cacheMode)' in coordinator
assert 'cacheMode,false,false,new Sa3Coordinator.Callback()' in activity
assert 'SA3_WORKER_PID=' in service and 'SA3_WORKER_EXIT pid=' in coordinator
assert all(x in manifest for x in ('sa3_text','sa3_dit','sa3_decode'))
assert '0.4.7-sa3-longform-debug' in gradle
assert 'android.permission.INTERNET' not in manifest
print("PASS: XNNPACK cached and baseline CPU modes wired end-to-end, offline, process-isolated.")
print("NOTE: Only a Samsung test can confirm a reduction in PSS or successful 8-step audio.")
