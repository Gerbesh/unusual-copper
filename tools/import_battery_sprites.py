"""Build conversion only: retain authored alpha and use nearest-neighbor sampling."""
from pathlib import Path
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/batteries"
manifest = json.loads((ART / "prompts.json").read_text(encoding="utf-8"))
preview = Image.new("RGBA", (512, 512), (25, 30, 31, 255))
for index, entry in enumerate(manifest["sprites"]):
    source = Image.open(ART / entry["source"]).convert("RGBA")
    assert source.getextrema()[3][0] == 0, entry["name"] + ": missing transparency"
    icon = source.resize(tuple(entry["gameResolution"]), Image.Resampling.NEAREST)
    icon.save(ROOT / "art/sprites" / (entry["name"] + ".png"))
    texture = ROOT / "src/main/resources/assets/unusualcopper/textures/item" / (entry["name"] + ".png")
    icon.save(texture)
    tile = icon.resize((256, 256), Image.Resampling.NEAREST)
    preview.alpha_composite(tile, ((index % 2) * 256, (index // 2) * 256))
preview.save(ART / "preview.png")
print("Imported four authored 64px battery sprites; source artwork and alpha retained.")
