#!/usr/bin/env python3
"""Smoke-test the exact YAMNet model embedded in Android, including real inference.

Runs on the GitHub runner *before* Gradle; never downloads anything to the app.
Requires: pip install ai-edge-litert numpy
"""
import pathlib
import zipfile
import numpy as np
from ai_edge_litert.interpreter import Interpreter

model = pathlib.Path("app/src/main/assets/yamnet.tflite")
labels = pathlib.Path("app/src/main/assets/yamnet_labels.txt").read_text(encoding="utf-8").splitlines()
assert len(labels) == 521, len(labels)
with zipfile.ZipFile(model) as f:
    assert "yamnet_label_list.txt" in f.namelist()

engine = Interpreter(model_path=str(model), num_threads=2)
input_details = engine.get_input_details()
output_details = engine.get_output_details()
assert len(input_details) == 1, input_details
assert len(output_details) >= 1, output_details
shape = tuple(int(i) for i in input_details[0]["shape"])
assert shape in ((15600,), (1, 15600)), f"Unexpected model input: {shape}"
out_shape = tuple(int(i) for i in output_details[0]["shape"])
assert out_shape == (1, 521), f"Unexpected model output: {out_shape}"
assert input_details[0]["dtype"] == np.float32, input_details
engine.allocate_tensors()
wave = np.zeros(shape, dtype=np.float32)
engine.set_tensor(input_details[0]["index"], wave)
engine.invoke()
scores = engine.get_tensor(output_details[0]["index"])
assert scores.shape == (1, 521) and np.isfinite(scores).all()
assert (scores >= 0).all(), "Invalid inference scores"
print("PASS YAMNet: waveform input", shape, "scores", scores.shape,
      "top class on silence:", labels[int(scores.argmax())],
      "score:", float(scores.max()))
