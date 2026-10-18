#!/usr/bin/env python3
"""Build AND VALIDATE an entirely local HF Fast tokenizer for Stable Audio 3 T5Gemma.

The official source uses a 4.24MB SentencePiece file (models/tokenizer.model)
without BOS/EOS. Convert the SAME fixed file to the Rust tokenizers offline JSON
used by ai.djl.android:tokenizer-native, then compare outputs against the
reference SentencePiece runtime for common prompts. Reject ANY mismatch.
Distribution carries the original tokenizer and license notices.
"""
import argparse
import hashlib
from pathlib import Path
import sentencepiece as spm

ORIGINAL_SHA = None  # pin to logged official GitHub SHA after first verified build
REFERENCE_URL = ("https://raw.githubusercontent.com/Stability-AI/"
                 "stable-audio-3/main/optimized/tflite/models/tokenizer.model")

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--source",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    import transformers
    from tokenizers import Tokenizer
    raw=args.source.read_bytes()
    digest=hashlib.sha256(raw).hexdigest()
    assert 4_000_000<len(raw)<4_500_000, "Bad tokenizer size"
    if ORIGINAL_SHA is not None:
        assert digest==ORIGINAL_SHA,"Official SentencePiece checksum changed!"
    print("Original SentencePiece SHA256:",digest,flush=True)
    reference=spm.SentencePieceProcessor(model_file=str(args.source))
    tok=transformers.T5TokenizerFast(vocab_file=str(args.source),extra_ids=0)
    tok._tokenizer.save(str(args.out))
    reconstructed=Tokenizer.from_file(str(args.out))
    examples=[
        "Upbeat retro video game chiptune with playful synths, no vocals.",
        "Gentle piano melody with soft strings, dreamy and cinematic.",
        "Impending tribal, epic orchestral buildup",
        "Energetic disco funk with bass guitar and a lively drum groove",
        "Ambient electronic arpeggios evolving into a warm optimistic melody",
        "French accordéon, upbeat fast accordion, acoustic guitar",
        "Rock drums, distorted guitar, explosion effects"
    ]
    for s in examples:
        expected=reference.encode(s)
        obtained=reconstructed.encode(s,add_special_tokens=False).ids
        if expected!=obtained:
            raise RuntimeError("Tokenizer mismatch for "+repr(s)+
              "\nExpected "+repr(expected)+"\nObtained "+repr(obtained))
        print("PASS tokenizer parity:",s[:45],len(expected),"tokens",flush=True)
    print("SA3 tokenizers exported:",args.out,args.out.stat().st_size,"bytes")
if __name__=="__main__":main()
