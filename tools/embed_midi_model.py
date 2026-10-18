#!/usr/bin/env python3
"""Embed the actual MIDI Gen AI v5 CPU checkpoint in an Android APK build.

This script runs only in CI; no checkpoint is committed to Git, and no model
download happens on the phone. The upstream manifest pins exact bytes/SHA256.
"""
import hashlib
import json
import pathlib
import re
import subprocess
from urllib.parse import quote

BASE = "https://huggingface.co/nicholasbien/midigenai/resolve/main/v5"
DEST = pathlib.Path("app/src/main/assets/midi/v5")
ONNX = DEST / "onnx"

def download(url: str, path: pathlib.Path, limit_seconds: int = 1200):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".partial")
    temp.unlink(missing_ok=True)
    subprocess.run([
        "curl", "--fail", "--location", "--retry", "4",
        "--retry-all-errors", "--connect-timeout", "30",
        "--max-time", str(limit_seconds), "--silent", "--show-error",
        "--output", str(temp), url
    ], check=True)
    if temp.stat().st_size == 0:
        raise RuntimeError("Downloaded empty resource: " + url)
    temp.replace(path)

def verify(path: pathlib.Path, expected_sha: str, expected_size: int):
    actual_size = path.stat().st_size
    if actual_size != expected_size:
        raise RuntimeError(f"{path}: size mismatch expected {expected_size}, got {actual_size}")
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(4 * 1024 * 1024), b""):
            h.update(chunk)
    got = h.hexdigest()
    if got != expected_sha.lower():
        raise RuntimeError(f"{path}: SHA256 mismatch: {got} instead of {expected_sha}")
    print(f"Verified {path}: {actual_size} bytes, SHA256 {got}", flush=True)

def main():
    ONNX.mkdir(parents=True, exist_ok=True)
    manifest_path = ONNX / "manifest.json"
    tokenizer_path = DEST / "tokenizer.json"
    download(BASE + "/onnx/manifest.json", manifest_path, 90)
    download(BASE + "/tokenizer.json", tokenizer_path, 90)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    tokenizer = json.loads(tokenizer_path.read_text(encoding="utf-8"))
    if not isinstance(tokenizer, dict) or tokenizer.get("tokenization") != "REMI":
        raise RuntimeError("This v5 tokenizer is not REMI; incompatible with shipped decoder")
    f = manifest.get("files", {}).get("cpu")
    if not isinstance(f, dict):
        raise RuntimeError("No CPU checkpoint in upstream v5 ONNX manifest")
    name, expected_sha, expected_size = f["file"], f["sha256"], int(f["bytes"])
    if not re.fullmatch(r"[A-Za-z0-9_.-]+\.onnx", name) or ".." in name:
        raise RuntimeError("Unexpected or unsafe ONNX filename: " + name)
    if not re.fullmatch(r"[a-fA-F0-9]{64}", expected_sha):
        raise RuntimeError("Invalid manifest SHA256")
    if not 50_000_000 < expected_size < 250_000_000:
        raise RuntimeError(f"Unexpected CPU model size: {expected_size}")
    target = ONNX / name
    print(f"Embedding MIDI Gen AI v5 CPU checkpoint: {name} ({expected_size} bytes)", flush=True)
    download(BASE + "/onnx/" + quote(name), target)
    verify(target, expected_sha, expected_size)
    # Ensure exactly one large ONNX model and no GPU weights in this variant.
    found = list(ONNX.glob("*.onnx"))
    if found != [target]:
        raise RuntimeError("Unexpected additional ONNX model weights in APK build")
    print("PASS: tokenizer, manifest, CPU model and hash ready for offline APK", flush=True)

if __name__ == "__main__":
    main()
