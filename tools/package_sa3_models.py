#!/usr/bin/env python3
"""Pack the exact 5 Stable Audio 3 CPU graphs with mandatory licence notices.

Offline phone, downloads occur on GitHub runner. Public models are copied
with both source licence texts, a legally required NOTICE, and on-device
end-user consent before generation. Never publish a GitHub Release automatically.
Reads public model data from Hugging Face official revision; verifies metadata
SHA256 and records exact revision/hash/size. Never commits weights to git.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil

FILES = {
    "sa3/sa3-sm-music/dit_w8a8.tflite": ("tflite/sa3-sm-music/dit_w8a8.tflite", 300_000_000, 850_000_000),
    "sa3/t5gemma/encoder_fp16.tflite": ("tflite/t5gemma/encoder_fp16.tflite", 350_000_000, 900_000_000),
    "sa3/same-s/dec_w8a8.tflite": ("tflite/same-s/dec_w8a8.tflite", 40_000_000, 220_000_000),
    "sa3/same-s/enc_w8a8.tflite": ("tflite/same-s/enc_w8a8.tflite", 30_000_000, 180_000_000),
}
MODEL_ID = "stabilityai/stable-audio-3-optimized"
NOTICE = """Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms

This application redistributes Stable Audio 3 Small-Music weights under the
Stability AI Community License, and T5Gemma encoder weights under the Gemma
Terms of Use. Redistribution and use restrictions apply. The entire applicable
LICENSE.md and LICENSE_GEMMA.md are included with this app under assets/sa3/.
See also https://stability.ai/license and https://ai.google.dev/gemma/terms.
Powered by Stability AI. Neither Stability AI nor Google sponsors this app.
"""

def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for c in iter(lambda: f.read(8 * 1024 * 1024), b""): h.update(c)
    return h.hexdigest()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--assets",type=Path,required=True)
    ap.add_argument("--include-license-notices",action="store_true",required=True)
    ap.add_argument("--cache",type=Path,default=Path("/tmp/sa3-hf-cache"))
    args=ap.parse_args()
    from huggingface_hub import HfApi, hf_hub_download
    token=os.getenv("HF_TOKEN") or None
    info=HfApi().model_info(MODEL_ID,files_metadata=True,token=token)
    revision=info.sha
    assert len(revision)==40, "Need immutable pinned source revision"
    print("Official Hugging Face revision:",revision,flush=True)
    siblings={s.rfilename:s for s in info.siblings}
    manifest={}
    args.assets.mkdir(parents=True,exist_ok=True)
    for dst,(src,lower,upper) in FILES.items():
        assert src in siblings,"Official checkpoint file missing: "+src
        member=siblings[src]
        print("Downloading "+src,flush=True)
        cached=Path(hf_hub_download(repo_id=MODEL_ID,filename=src,
                    revision=revision,cache_dir=str(args.cache),token=token))
        size=cached.stat().st_size
        if not lower<=size<=upper:raise ValueError("Unexpected checkpoint size: "+str((src,size)))
        digest=sha256(cached)
        lfs=getattr(member,"lfs",None)
        expected=getattr(lfs,"sha256",None) if lfs else None
        if expected and expected != digest:raise ValueError("HF upstream LFS checksum mismatch: "+src)
        with cached.open("rb") as inp:
            if inp.read(8)[4:8]!=b"TFL3":raise ValueError("Invalid TFLite "+src)
        dest=args.assets/dst
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(cached,dest)
        assert sha256(dest)==digest, "Copy corruption: "+dst
        manifest[dst]={"bytes":size,"sha256":digest}
        print("OK",dst,size,digest,flush=True)
    for license_name in ("LICENSE.md","LICENSE_GEMMA.md"):
        from_repo=hf_hub_download(repo_id=MODEL_ID,filename=license_name,
                     revision=revision,cache_dir=str(args.cache),token=token)
        shutil.copyfile(from_repo,args.assets/"sa3"/license_name)
    notice=hf_hub_download(repo_id=MODEL_ID,filename="NOTICE",revision=revision,
                           cache_dir=str(args.cache),token=token)
    shutil.copyfile(notice,args.assets/"sa3"/"NOTICE")
    tokenizer=args.assets/"sa3"/"tokenizer.model"
    if not tokenizer.is_file() or not 3_000_000 < tokenizer.stat().st_size < 6_000_000:
        raise ValueError("Official SA3 tokenizer.model is missing or invalid")
    manifest["sa3/tokenizer.model"]={"bytes":tokenizer.stat().st_size,"sha256":sha256(tokenizer)}
    output={"model_id":MODEL_ID,"revision":revision,"files":manifest}
    (args.assets/"sa3"/"weights-manifest.json").write_text(
        json.dumps(output,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print("SA3 authentic model pack completely staged, CPU assets from revision "+revision,flush=True)

if __name__=="__main__":main()
