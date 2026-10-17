#!/usr/bin/env python3
"""Optional, explicit GPU-only Qwen-Omni sound descriptions for FxExtractor ZIPs.
Not part of Android APK. Models only downloaded with --allow-download.
"""
import argparse
import json
import re
import shutil
import tempfile
import zipfile
from pathlib import Path

MODEL_ID = "Qwen/Qwen2.5-Omni-3B"
NAME = re.compile(r"FX_[0-9]{3}\.wav\Z")

def describe(path, model, processor):
    from qwen_omni_utils import process_mm_info
    messages = [{
        "role": "user",
        "content": [
            {"type": "audio", "audio": str(path)},
            {"type": "text", "text": (
                "Décris uniquement les sons audibles dans cet extrait en français. "
                "Mentionne les impacts, objets, voix, musique ou bruits de fond réellement "
                "perceptibles. Si la source est incertaine, signale-le. Deux phrases maximum."
            )}
        ]
    }]
    prompt = processor.apply_chat_template(messages, add_generation_prompt=True, tokenize=False)
    audios, images, videos = process_mm_info(messages, use_audio_in_video=False)
    inputs = processor(text=prompt, audio=audios, images=images, videos=videos,
                       return_tensors="pt", padding=True, use_audio_in_video=False)
    inputs = inputs.to(model.device)
    result = model.generate(**inputs, return_audio=False, thinker_do_sample=False,
                            max_new_tokens=128)
    return processor.batch_decode(result, skip_special_tokens=True,
                                  clean_up_tokenization_spaces=False)[0].strip()

def main():
    parser = argparse.ArgumentParser(description="Optional Qwen-Omni description of FX_###.wav files in an FxExtractor ZIP")
    parser.add_argument("input", type=Path, help="FxExtractor export ZIP")
    parser.add_argument("output", type=Path, help="JSON labels, independent of source archive")
    parser.add_argument("--limit", type=int, default=20, help="1..100 clips maximum (default 20)")
    parser.add_argument("--allow-download", action="store_true", help="Explicitly allow multi-GB model download")
    parser.add_argument("--allow-cpu", action="store_true", help="Permit very slow CPU-only inference")
    args = parser.parse_args()
    if not 1 <= args.limit <= 100:
        parser.error("--limit must be between 1 and 100")
    if not args.input.is_file() or args.input.resolve() == args.output.resolve():
        parser.error("Input must exist and differ from output")
    # Heavy optional imports only run on deliberate Qwen usage; never during Android build.
    import torch
    from transformers import Qwen2_5OmniForConditionalGeneration, Qwen2_5OmniProcessor
    if not torch.cuda.is_available() and not args.allow_cpu:
        parser.error("No CUDA GPU. Pass --allow-cpu to explicitly accept very slow inference.")
    model = Qwen2_5OmniForConditionalGeneration.from_pretrained(
        MODEL_ID, torch_dtype="auto", device_map="auto", enable_audio_output=False,
        local_files_only=not args.allow_download
    )
    processor = Qwen2_5OmniProcessor.from_pretrained(
        MODEL_ID, local_files_only=not args.allow_download
    )
    report = {"schema": "FxExtractor-Qwen-v1", "model": MODEL_ID, "events": []}
    with zipfile.ZipFile(args.input) as archive, tempfile.TemporaryDirectory(prefix="fx-qwen-") as tmp:
        clips = sorted(n for n in archive.namelist() if NAME.fullmatch(n))
        if not clips:
            raise ValueError("No FX_###.wav entries found in archive")
        for i, name in enumerate(clips[:args.limit], 1):
            member = archive.getinfo(name)
            if member.file_size > 50_000_000:
                raise ValueError("FX exceeds 50MB safety limit: " + name)
            wave = Path(tmp, name)
            with archive.open(member) as source, wave.open("wb") as dest:
                shutil.copyfileobj(source, dest, 65536)
            print(f"[{i}/{min(len(clips), args.limit)}] {name}", flush=True)
            report["events"].append({"file": name, "description": describe(wave, model, processor)})
            wave.unlink()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Labels saved: {args.output}")

if __name__ == "__main__":
    main()
