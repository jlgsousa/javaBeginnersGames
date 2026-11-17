#!/usr/bin/env python3
"""Lädt die verwendeten CC0-Texturen und -Modelle von Poly Haven (polyhaven.com) nach assets/polyhaven.

Alle Poly-Haven-Assets stehen unter CC0 (gemeinfrei) und dürfen frei mitverteilt werden.
Aufruf:  python3 tools/fetch_polyhaven.py
"""
import json, os, sys, urllib.request

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "polyhaven")
TEXTURES = ["bark_brown_02", "brown_planks_05", "mossy_stone_wall", "thatch_roof_angled", "clay_roof_tiles",
            "clay_plaster", "forest_ground_04", "mossy_rock", "mossy_cobblestone", "moss_wood"]
TEX_MAPS = ["Diffuse", "nor_gl"]
MODELS = ["Lantern_01", "wooden_lantern_01", "fern_02", "tree_stump_01", "rock_moss_set_01", "Rockingchair_01",
          "wooden_bookshelf_worn", "tea_set_01", "vintage_oil_lamp", "wicker_basket_01", "wooden_bucket_01",
          "round_wooden_table_01", "wooden_stool_01", "lantern_chandelier_01", "painted_wooden_bench"]
RES = "1k"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Daemmerwald-asset-fetcher/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def fetch_texture(name):
    files = json.loads(get("https://api.polyhaven.com/files/" + name))
    for m in TEX_MAPS:
        e = files.get(m, {}).get(RES, {}).get("jpg")
        if not e:
            print("  fehlt:", name, m); continue
        out = os.path.join(ROOT, "textures", name, f"{name}_{'diff' if m == 'Diffuse' else 'nor'}.jpg")
        if not os.path.exists(out):
            save(out, get(e["url"]))
    print("Textur", name)


def fetch_model(name):
    files = json.loads(get("https://api.polyhaven.com/files/" + name))
    g = files["gltf"][RES]["gltf"]
    base = os.path.join(ROOT, "models", name)
    save(os.path.join(base, name + ".gltf"), get(g["url"]))
    for rel, e in g.get("include", {}).items():
        out = os.path.join(base, rel)
        if not os.path.exists(out):
            save(out, get(e["url"]))
    slim(os.path.join(base, name + ".gltf"))
    print("Modell", name)


def slim(path):
    """Entfernt Rauheits-/Metall-/AO-Texturen (das Spiel nutzt nur Farbe und Normalen)."""
    g = json.load(open(path))
    for m in g.get("materials", []):
        pbr = m.get("pbrMetallicRoughness", {})
        pbr.pop("metallicRoughnessTexture", None)
        m.pop("occlusionTexture", None)
    used = set()
    for m in g.get("materials", []):
        for t in [m.get("pbrMetallicRoughness", {}).get("baseColorTexture"), m.get("normalTexture"), m.get("emissiveTexture")]:
            if t: used.add(g["textures"][t["index"]]["source"])
    base = os.path.dirname(path)
    for i, img in enumerate(g.get("images", [])):
        if i not in used and "uri" in img:
            f = os.path.join(base, img["uri"])
            if os.path.exists(f): os.remove(f)
            img["uri"] = img["uri"]  # Verweis bleibt, wird aber von keinem Material mehr genutzt
    json.dump(g, open(path, "w"))


if __name__ == "__main__":
    for t in TEXTURES:
        fetch_texture(t)
    for m in MODELS:
        try:
            fetch_model(m)
        except Exception as ex:
            print("  Fehler bei", m, ex, file=sys.stderr)
