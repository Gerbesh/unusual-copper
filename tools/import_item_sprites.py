"""Build-size conversion only: preserve generated artwork and alpha, sample nearest pixels.

The original image_gen PNGs and prompts are versioned in art/sprites. No background
replacement, painting, color change or procedural fallback is performed here.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/sprites"
ASSETS = ROOT / "src/main/resources/assets/unusualcopper/textures/item"

def main():
    manifest = json.loads((ART / "prompts.json").read_text(encoding="utf-8"))
    preview = Image.new("RGB", (768, 864), (25, 30, 31))
    draw = ImageDraw.Draw(preview)
    for i, item in enumerate(manifest["sprites"]):
        source = Image.open(ART / item["source"]).convert("RGBA")
        if source.getextrema()[3][0] != 0:
            raise ValueError(f"Missing transparent background: {item['name']}")
        icon = source.resize(tuple(item["gameResolution"]), Image.Resampling.NEAREST)
        icon.save(ART / f"{item['name']}.png")
        ASSETS.mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / f"{item['name']}.png")
        x, y = (i % 3) * 256, (i // 3) * 288
        preview.paste(icon.resize((256, 256), Image.Resampling.NEAREST), (x,y), icon.resize((256,256),Image.Resampling.NEAREST))
        draw.text((x+8,y+263),item["name"], fill=(219,231,224))
    preview.save(ART / "preview.png")
    print(f"Imported {len(manifest['sprites'])} transparent 64px item sprites; originals preserved.")

if __name__ == "__main__": main()
