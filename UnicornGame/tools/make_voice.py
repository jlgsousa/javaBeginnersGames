#!/usr/bin/env python3
"""
Nimmt alle Sätze des Spiels mit einer deutschen Märchenstimme auf (offline, mit Piper).

Benötigt: pip install piper-tts, ffmpeg, und ein deutsches Piper-Stimmmodell (.onnx + .onnx.json),
z. B. de_DE-kerstin-low von https://huggingface.co/rhasspy/piper-voices

Aufruf:
  python3 make_voice.py voice_lines.json de_DE-kerstin-low.onnx [--pitch 1.12]

voice_lines.json erhält man im Browser über die Konsole:  copy(JSON.stringify(collectVoiceLines()))
Ergebnis: audio/voice/*.mp3 und js/voice-clips.js
"""
import json, subprocess, sys, os, tempfile, argparse

ap = argparse.ArgumentParser()
ap.add_argument("lines"); ap.add_argument("model")
ap.add_argument("--pitch", type=float, default=1.12, help="1.0 = normal, >1 = heller (Mädchenstimme)")
ap.add_argument("--slow", type=float, default=1.12, help="Sprechtempo: >1 = langsamer")
a = ap.parse_args()

base = os.path.dirname(os.path.abspath(__file__)) + "/.."
out_dir = os.path.join(base, "audio", "voice")
os.makedirs(out_dir, exist_ok=True)
lines = json.load(open(a.lines, encoding="utf-8"))
clips = {}
with tempfile.TemporaryDirectory() as tmp:
    for i, text in enumerate(lines):
        wav = os.path.join(tmp, "x.wav")
        subprocess.run([sys.executable, "-m", "piper", "-m", a.model, "-f", wav,
                        "--length-scale", str(a.slow), "--sentence-silence", "0.45"],
                       input=text.encode("utf-8"), check=True, stderr=subprocess.DEVNULL)
        rate = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a", "-show_entries", "stream=sample_rate",
                               "-of", "csv=p=0", wav], capture_output=True, text=True).stdout.strip()
        name = f"v{i:03d}.mp3"
        # heller machen (Tonhöhe rauf), Tempo ausgleichen, kurz ausblenden, als MP3 speichern
        filt = f"asetrate={rate}*{a.pitch},aresample={rate},atempo={1 / a.pitch * 1.06:.3f},apad=pad_dur=0.1"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-af", filt, "-ac", "1",
                        "-codec:a", "libmp3lame", "-q:a", "6", os.path.join(out_dir, name)], check=True)
        clips[text] = "audio/voice/" + name
        print(f"{i + 1}/{len(lines)}  {text[:60]}")

with open(os.path.join(base, "js", "voice-clips.js"), "w", encoding="utf-8") as f:
    f.write("// Automatisch erzeugt von tools/make_voice.py – Satz → aufgenommene Märchenstimme\n")
    f.write("window.VOICE_CLIPS = " + json.dumps(clips, ensure_ascii=False, indent=1) + ";\n")
print("fertig:", len(clips), "Aufnahmen")
