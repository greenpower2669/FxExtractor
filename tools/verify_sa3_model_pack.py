#!/usr/bin/env python3
"""Validate an *already legally obtained* Stable Audio 3 model bundle before APK packaging.

This script does NOT download or bypass gated Hugging Face files. The person
packaging the APK must have accepted the Stability Community and Gemma terms.
It rejects incomplete, unverified or wrongly named weights; models are not
committed into Git, keeping the repository and forks lightweight.
"""
import argparse
import hashlib
import json
import pathlib
import sys

ASSETS = {
    "sa3/sa3-sm-music/dit_w8a8.tflite": (300_000_000, 850_000_000),
    "sa3/t5gemma/encoder_fp16.tflite": (350_000_000, 900_000_000),
    "sa3/same-s/dec_w8a8.tflite": (40_000_000, 220_000_000),
    "sa3/tokenizer.model": (3_000_000, 6_000_000),
}

def sha256_file(path: pathlib.Path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for part in iter(lambda: stream.read(1024 * 1024 * 8), b""):
            h.update(part)
    return h.hexdigest()

def validate(models: pathlib.Path, manifest: pathlib.Path):
    expected = json.loads(manifest.read_text(encoding="utf-8"))
    if set(expected) != set(ASSETS):
        raise ValueError("Le manifeste ne contient pas exactement les 4 ressources officielles")
    report = {}
    for key, (minimum, maximum) in ASSETS.items():
        path = models / key
        if not path.is_file() or path.is_symlink():
            raise ValueError(f"Modèle manquant ou symlink non autorisé : {key}")
        stat = path.stat()
        if not minimum <= stat.st_size <= maximum:
            raise ValueError(f"Fichier anormal {key} : {stat.st_size} octets")
        if key.endswith(".tflite"):
            with path.open("rb") as f:
                magic = f.read(8)
            if len(magic) != 8 or magic[4:8] != b"TFL3":
                raise ValueError(f"Pas un graphe TFLite : {key}")
        digest = sha256_file(path)
        spec = expected[key]
        if digest != spec.get("sha256") or stat.st_size != spec.get("bytes"):
            raise ValueError(f"SHA256/taille incohérents : {key}")
        report[key] = {"sha256": digest, "bytes": stat.st_size}
        print(f"PASS {key} {stat.st_size} bytes sha256:{digest}", flush=True)
    return report

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", required=True, type=pathlib.Path)
    parser.add_argument("--manifest", required=True, type=pathlib.Path)
    parser.add_argument("--accepted-licenses", action="store_true",
        help="Explicit attestation by packager: Stability AI Community + Gemma terms accepted")
    args = parser.parse_args()
    if not args.accepted_licenses:
        parser.error("Distribution refusée : confirmer explicitement l'acceptation des deux licences")
    print(json.dumps(validate(args.assets, args.manifest), indent=2))
