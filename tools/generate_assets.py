"""Reproducible, original cuboid art and data for the CuE prototype.

Run with Python 3 + Pillow. Blockbench sources embed textures and contain no
user-specific paths. The supplied concept image is never used as a texture.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import base64
import copy
import json
import random
import uuid

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/unusualcopper"
DATA = ROOT / "src/main/resources/data"
ART = ROOT / "art"
FACES = ("north", "south", "east", "west", "up", "down")
PALETTE = {"copper": (182, 107, 66), "dark": (39, 46, 44), "patina": (49, 91, 80), "aqua": (55, 214, 211), "fire": (246, 156, 53)}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def stable_id(value):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "unusualcopper:" + value))


def textures():
    for name, color in PALETTE.items():
        rng = random.Random(name)
        image = Image.new("RGBA", (32, 32))
        pixels = image.load()
        for y in range(32):
            for x in range(32):
                noise = rng.choice((-8, -4, 0, 0, 3, 5))
                edge = 17 if x == 0 or y == 0 else -24 if x == 31 or y == 31 else 0
                pixels[x, y] = tuple(max(0, min(255, c + noise + edge)) for c in color) + (255,)
        d = ImageDraw.Draw(image)
        if name == "copper":
            d.line((2, 29, 29, 29), fill=(100, 60, 42))
            for x, y in ((3, 3), (27, 3), (3, 27), (27, 27)):
                d.rectangle((x, y, x + 2, y + 2), fill=(101, 65, 44)); d.point((x, y), fill=(228, 158, 104))
        if name == "dark":
            for y in range(6, 28, 5):
                d.line((6, y, 26, y), fill=(15, 24, 21)); d.line((6, y + 1, 26, y + 1), fill=(64, 80, 70))
        if name == "patina":
            for _ in range(14):
                x, y = rng.randrange(2, 29), rng.randrange(2, 29)
                d.rectangle((x, y, x + 1, y + 2), fill=(79, 114, 90))
        if name in ("aqua", "fire"):
            for _ in range(11):
                x, y = rng.randrange(2, 28), rng.randrange(2, 28)
                d.rectangle((x, y, x + 2, y + 2), fill=(170, 249, 235) if name == "aqua" else (255, 232, 153))
        save_texture(name, image)
    glass = Image.new("RGBA", (32, 128), (26, 108, 108, 40))
    d = ImageDraw.Draw(glass)
    for frame in range(4):
        offset = 32 * frame
        d.rectangle((0, offset, 31, offset + 31), outline=(104, 226, 220, 200), width=1)
        d.line((3, offset + 6, 8, offset + 1), fill=(174, 247, 239, 170), width=1)
        for x, y in ((7, 11), (19, 25), (25, 8), (13, 19)):
            y = (y - frame * 3) % 28 + 2
            d.rectangle((x, offset + y, x + 1, offset + y + 1), fill=(100, 233, 225, 190))
    save_texture("glass", glass)
    write_json(ASSETS / "textures/block/glass.png.mcmeta", {"animation": {"frametime": 5, "interpolate": False}})


def save_texture(name, image):
    for path in (ASSETS / f"textures/block/{name}.png", ART / f"textures/{name}.png"):
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)


def part(start, end, material, rotation=None):
    dx, dy, dz = [end[i] - start[i] for i in range(3)]
    dims = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    element = {"from": start, "to": end, "faces": {f: {"texture": "#" + material, "uv": [0, 0, *dims[f]]} for f in FACES}}
    if rotation:
        element["rotation"] = rotation
    return element


def frame():
    parts = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 1], [15, 3, 15], "copper"), part([1, 13, 1], [15, 15, 15], "copper")]
    for x in (1, 13):
        for z in (1, 13):
            parts.append(part([x, 3, z], [x + 2, 13, z + 2], "copper"))
    return parts


def tank_windows():
    return [part([3, 3, 1.5], [13, 13, 2], "glass"), part([3, 3, 14], [13, 13, 14.5], "glass"), part([1.5, 3, 3], [2, 13, 13], "glass"), part([14, 3, 3], [14.5, 13, 13], "glass")]


def export_model(name, body, windows):
    materials = list(PALETTE) + ["glass"]
    texture_refs = {m: f"unusualcopper:block/{m}" for m in materials}
    write_json(ASSETS / f"models/block/{name}.json", {"render_type": "minecraft:cutout", "textures": texture_refs, "elements": body})
    write_json(ASSETS / f"models/block/{name}_glass.json", {"render_type": "minecraft:translucent", "textures": texture_refs, "elements": windows})
    display = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.62, .62, .62]}, "ground": {"translation": [0, 3, 0], "scale": [.25, .25, .25]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375, .375, .375]}}
    write_json(ASSETS / f"models/item/{name}.json", {"render_type": "minecraft:translucent", "textures": texture_refs, "elements": body + windows, "display": display})
    multipart = []
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for suffix in ("", "_glass"):
            multipart.append({"when": {"facing": facing}, "apply": {"model": f"unusualcopper:block/{name}{suffix}", "y": angle}})
    write_json(ASSETS / f"blockstates/{name}.json", {"multipart": multipart})
    write_bbmodel(name, body + windows, materials)


def write_bbmodel(name, parts, materials):
    elements = []
    for i, original in enumerate(parts):
        element = copy.deepcopy(original)
        element.update({"name": f"{name}_{i:02}", "uuid": stable_id(f"{name}:{i}"), "type": "cube", "origin": [8, 8, 8]})
        for face in element["faces"].values():
            face["texture"] = materials.index(face["texture"][1:])
            face["uv"] = [v * 2 for v in face["uv"]]
        if "rotation" in element:
            rotation = element.pop("rotation")
            element["origin"] = rotation["origin"]
            element["rotation"] = [rotation["angle"] if axis == rotation["axis"] else 0 for axis in "xyz"]
        elements.append(element)
    textures_data = []
    for i, material in enumerate(materials):
        textures_data.append({"name": material + ".png", "id": str(i), "uuid": stable_id("texture:" + material), "mode": "bitmap", "relative_path": "../textures/" + material + ".png", "width": 32, "height": 128 if material == "glass" else 32, "uv_width": 32, "uv_height": 32, "source": "data:image/png;base64," + base64.b64encode((ART / f"textures/{material}.png").read_bytes()).decode()})
    write_json(ART / f"blockbench/{name}.bbmodel", {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False}, "name": name, "resolution": {"width": 32, "height": 32}, "elements": elements, "outliner": [e["uuid"] for e in elements], "textures": textures_data})


def models():
    copper_cell = frame() + [part([5, 3, 5], [11, 12, 11], "aqua"), part([4, 14, 4], [12, 16, 12], "patina")]
    export_model("copper_cell", copper_cell, tank_windows())
    splitter = frame() + [part([6, 4, 6], [10, 10, 10], "aqua"), part([4, 14, 4], [12, 16, 12], "patina"), part([5, 12, 5], [11, 14, 11], "dark"), part([3, 4, .5], [6, 8, 1.5], "patina")]
    export_model("essence_splitter", splitter, tank_windows())
    tilt = {"origin": [8, 10, 8], "axis": "x", "angle": -22.5}
    solar = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 1], [15, 7, 15], "patina"), part([0, 6, 0], [16, 8, 16], "copper"), part([6, 8, 6], [10, 11, 10], "dark"), part([1, 10, 1], [15, 11, 15], "copper", tilt), part([2, 11, 2], [14, 11.5, 14], "aqua", tilt), part([7.5, 11.5, 2], [8.5, 12, 14], "copper", tilt), part([2, 11.5, 7.5], [14, 12, 8.5], "copper", tilt), part([4, 3, .5], [12, 5, 1], "aqua")]
    export_model("solar_infuser", solar, [])
    smelter = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 2], [15, 13, 15], "patina"), part([0, 11, 1], [16, 14, 16], "copper"), part([1, 2, 0], [4, 11, 2], "copper"), part([12, 2, 0], [15, 11, 2], "copper"), part([4, 2, 0], [12, 4, 2], "dark"), part([4, 9, 0], [12, 11, 2], "copper"), part([4, 4, 1], [12, 9, 2], "fire"), part([10, 14, 10], [14, 16, 14], "dark"), part([9, 14, 9], [15, 15, 15], "copper"), part([15, 4, 5], [16, 9, 11], "dark")]
    export_model("resonance_smelter", smelter, [part([4, 4, .5], [12, 9, 1], "glass")])
    pipe_materials = list(PALETTE) + ["glass"]
    refs = {m: f"unusualcopper:block/{m}" for m in pipe_materials}
    center = [part([5, 5, 5], [11, 11, 11], "copper"), part([6, 6, 4.5], [10, 10, 5], "aqua")]
    arm = [part([5, 5, 0], [11, 11, 1], "copper"), part([5, 5, 4], [11, 11, 5], "patina"), part([7, 7, 1], [9, 9, 4], "aqua")]
    glass = [part([6, 6, 1], [10, 10, 4], "glass")]
    for name, parts, layer in (("pipe_center", center, "cutout"), ("pipe_arm", arm, "cutout"), ("pipe_arm_glass", glass, "translucent")):
        write_json(ASSETS / f"models/block/{name}.json", {"render_type": "minecraft:" + layer, "textures": refs, "elements": parts})
    multipart = [{"apply": {"model": "unusualcopper:block/pipe_center"}}]
    for side, rotations in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})):
        for suffix in ("", "_glass"):
            multipart.append({"when": {side: "true"}, "apply": {"model": f"unusualcopper:block/pipe_arm{suffix}", **rotations}})
    write_json(ASSETS / "blockstates/copper_energy_pipe.json", {"multipart": multipart})
    write_json(ASSETS / "models/item/copper_energy_pipe.json", {"parent": "unusualcopper:block/pipe_center"})
    write_bbmodel("copper_energy_pipe", center + arm + glass, pipe_materials)
    for name, color in (("diamond_solar_lens", "aqua"), ("saturated_copper_essence", "aqua"), ("copper_frame", "copper"), ("copper_core", "copper")):
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
        if name == "diamond_solar_lens":
            d.ellipse((3, 3, 28, 28), fill=(182, 107, 66), outline=(234, 159, 103), width=2); d.ellipse((7, 7, 24, 24), fill=(43, 169, 175)); d.line((10, 13, 16, 7), fill=(170, 249, 239), width=3)
        elif name == "saturated_copper_essence":
            d.polygon([(16, 2), (25, 10), (25, 22), (16, 29), (7, 22), (7, 10)], fill=(44, 199, 192), outline=(186, 249, 238)); d.line((16, 3, 12, 18, 16, 28), fill=(99, 237, 224), width=2)
        else:
            d.rectangle((4, 4, 27, 27), fill=(87, 50, 36), outline=(215, 141, 86), width=3); d.rectangle((8, 8, 23, 23), fill=(0, 0, 0, 0) if name == "copper_frame" else (47, 206, 193))
        path = ASSETS / f"textures/item/{name}.png"; path.parent.mkdir(parents=True, exist_ok=True); img.save(path)
        write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"unusualcopper:item/{name}"}})


def data():
    # Recipes, loot and mining tags are generated by the Java runData providers.
    write_json(DATA / "unusualcopper/tags/items/smeltable_ores.json", {"replace": False, "values": ["#forge:ores", "#forge:raw_materials", "minecraft:raw_copper", "minecraft:raw_iron", "minecraft:raw_gold"]})
    write_json(DATA / "unusualcopper/tags/items/smelting_blacklist.json", {"replace": False, "values": []})


def languages():
    en = {"itemGroup.unusualcopper": "Unusual Copper", "block.unusualcopper.solar_infuser": "Solar Infuser", "block.unusualcopper.essence_splitter": "Essence Splitter", "block.unusualcopper.copper_cell": "Copper Cell", "block.unusualcopper.resonance_smelter": "Resonance Smelter", "block.unusualcopper.copper_energy_pipe": "Copper Energy Pipe", "item.unusualcopper.diamond_solar_lens": "Diamond Solar Lens", "item.unusualcopper.saturated_copper_essence": "Saturated Copper Essence", "item.unusualcopper.copper_frame": "Copper Frame", "item.unusualcopper.copper_core": "Copper Core", "tooltip.unusualcopper.lens": "Remaining infusion capacity: %s copper items", "gui.unusualcopper.redstone": "Redstone", "gui.unusualcopper.cancel": "×", "gui.unusualcopper.input": "Input", "gui.unusualcopper.output": "Output", "gui.unusualcopper.redstone_mode.0": "Always on", "gui.unusualcopper.redstone_mode.1": "Signal required", "gui.unusualcopper.redstone_mode.2": "No signal"}
    ru = {"itemGroup.unusualcopper": "Необычная медь", "block.unusualcopper.solar_infuser": "Солнечный насытитель", "block.unusualcopper.essence_splitter": "Расщепитель эссенции", "block.unusualcopper.copper_cell": "Медная ячейка", "block.unusualcopper.resonance_smelter": "Резонансная плавильня", "block.unusualcopper.copper_energy_pipe": "Медный энергопровод", "item.unusualcopper.diamond_solar_lens": "Алмазная солнечная линза", "item.unusualcopper.saturated_copper_essence": "Насыщенная медная эссенция", "item.unusualcopper.copper_frame": "Медная рама", "item.unusualcopper.copper_core": "Медный сердечник", "tooltip.unusualcopper.lens": "Ресурс насыщения: %s единиц меди", "gui.unusualcopper.redstone": "Редстоун", "gui.unusualcopper.cancel": "×", "gui.unusualcopper.input": "Вход", "gui.unusualcopper.output": "Выход", "gui.unusualcopper.redstone_mode.0": "Всегда включено", "gui.unusualcopper.redstone_mode.1": "Нужен сигнал", "gui.unusualcopper.redstone_mode.2": "Без сигнала"}
    statuses = {"idle": ("Idle", "Ожидание"), "working": ("Working", "Работает"), "no_sun": ("Waiting for sunlight", "Нет дневного неба"), "no_lens": ("Insert a solar lens", "Нужна солнечная линза"), "output_full": ("Output is full", "Выход заполнен"), "no_energy": ("Waiting for CuE", "Недостаточно CuE"), "invalid_input": ("No valid recipe", "Нет подходящего рецепта"), "redstone_paused": ("Paused by redstone", "Пауза по редстоуну"), "recipe_changed": ("Recipe changed; cancel to restart", "Рецепт изменён; отмените задачу"), "buffer_full": ("Energy buffer is full", "Энергобуфер заполнен")}
    en["gui.unusualcopper.cancel_hint"] = "Cancel the operation. Input remains; spent CuE is not refunded."
    ru["gui.unusualcopper.cancel_hint"] = "Отменить операцию. Вход остаётся; потраченные CuE не возвращаются."
    for name, (english, russian) in statuses.items():
        en["status.unusualcopper." + name] = english; ru["status.unusualcopper." + name] = russian
    for mode, english, russian in (("disabled", "Disabled", "Закрыто"), ("item_input", "Item input", "Вход предметов"), ("item_output", "Item output", "Выход предметов"), ("energy_input", "CuE input", "Вход CuE"), ("energy_output", "CuE output", "Выход CuE")):
        en["side.unusualcopper." + mode] = english; ru["side.unusualcopper." + mode] = russian
    write_json(ASSETS / "lang/en_us.json", en); write_json(ASSETS / "lang/ru_ru.json", ru)


if __name__ == "__main__":
    textures(); models(); data(); languages()
    print("Generated original textures, 5 editable Blockbench projects, models, item tags and RU/EN.")
