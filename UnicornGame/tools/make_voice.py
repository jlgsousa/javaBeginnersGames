#!/usr/bin/env python3
"""
Nimmt alle Sätze des Spiels mit Märchenstimmen auf (offline, mit Piper).

Benötigt: pip install piper-tts, ffmpeg und die Piper-Stimmmodelle (.onnx + .onnx.json) von
https://huggingface.co/rhasspy/piper-voices :
  - de_DE-mls-medium   (deutsche Erzählerin, Sprecherin 104)
  - pt_PT-tugão-medium (Portugiesisch aus Portugal; für Avó/Erzählerin/Alma heller gestellt)

Aufruf:
  python3 make_voice.py voice_lines.json --de de_DE-mls-medium.onnx --pt pt_PT-tugao-medium.onnx

voice_lines.json erhält man im Browser über die Konsole:  copy(JSON.stringify(collectVoiceLines()))
Ergebnis: audio/voice/*.mp3 und js/voice-clips.js
"""
import json, subprocess, sys, os, tempfile, argparse

ap = argparse.ArgumentParser()
ap.add_argument("lines")
ap.add_argument("--de", required=True)
ap.add_argument("--pt", required=True)
a = ap.parse_args()

# Stimme → (Modell, Sprecher-Nr., Tonhöhe, Tempo-Faktor)
VOICES = {
    "de":   (a.de, 104, 1.0, 1.10),   # deutsche Erzählerin, natürlich
    "pt":   (a.pt, None, 1.28, 1.10), # portugiesische Erzählerin
    "avoF": (a.pt, None, 1.32, 1.10), # Avó (Oma)
    "avoM": (a.pt, None, 1.0, 1.08),  # Avô (Opa)
    "alma": (a.pt, None, 1.48, 1.05), # Alma (Kind)
}

base = os.path.dirname(os.path.abspath(__file__)) + "/.."
out_dir = os.path.join(base, "audio", "voice")
os.makedirs(out_dir, exist_ok=True)
for f in os.listdir(out_dir):
    if f.endswith(".mp3"): os.remove(os.path.join(out_dir, f))
lines = json.load(open(a.lines, encoding="utf-8"))
clips = {}
with tempfile.TemporaryDirectory() as tmp:
    for i, item in enumerate(lines):
        text, voice = item["text"], item.get("voice", "de")
        model, spk, pitch, slow = VOICES[voice]
        wav = os.path.join(tmp, "x.wav")
        cmd = [sys.executable, "-m", "piper", "-m", model, "-f", wav, "--length-scale", str(slow), "--sentence-silence", "0.45"]
        if spk is not None: cmd += ["-s", str(spk)]
        subprocess.run(cmd, input=text.encode("utf-8"), check=True, stderr=subprocess.DEVNULL)
        rate = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a", "-show_entries", "stream=sample_rate",
                               "-of", "csv=p=0", wav], capture_output=True, text=True).stdout.strip()
        filt = "apad=pad_dur=0.1"
        if pitch != 1.0:   # heller stellen, Tempo ausgleichen
            filt = f"asetrate={rate}*{pitch},aresample={rate},atempo={1.08 / pitch:.3f}," + filt
        name = f"v{i:03d}.mp3"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-af", filt, "-ac", "1",
                        "-codec:a", "libmp3lame", "-q:a", "6", os.path.join(out_dir, name)], check=True)
        key = text if voice == "de" else f"{voice}|{text}"
        clips[key] = "audio/voice/" + name
        print(f"{i + 1}/{len(lines)} [{voice}] {text[:60]}")

with open(os.path.join(base, "js", "voice-clips.js"), "w", encoding="utf-8") as f:
    f.write("// Automatisch erzeugt von tools/make_voice.py – Satz → aufgenommene Märchenstimme\n")
    f.write("window.VOICE_CLIPS = " + json.dumps(clips, ensure_ascii=False, indent=1) + ";\n")
print("fertig:", len(clips), "Aufnahmen")
