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
    for tier, name in ((2, "copper_cell_ii"), (3, "copper_cell_iii")):
        body = frame() + [part([4, 3, 4], [12, 13, 12], "aqua"), part([3, 14, 3], [13, 16, 13], "patina")]
        for ring in range(tier):
            y = 3 + ring * 4
            body += [part([0, y, 0], [16, y + 1, 3], "copper"), part([0, y, 13], [16, y + 1, 16], "copper"),
                     part([0, y, 3], [3, y + 1, 13], "patina"), part([13, y, 3], [16, y + 1, 13], "patina")]
        export_model(name, body, tank_windows())
    splitter = frame() + [part([6, 4, 6], [10, 10, 10], "aqua"), part([4, 14, 4], [12, 16, 12], "patina"), part([5, 12, 5], [11, 14, 11], "dark"), part([3, 4, .5], [6, 8, 1.5], "patina")]
    export_model("essence_splitter", splitter, tank_windows())
    distributor=frame()+[part([3,3,3],[13,13,13],"patina"),part([4,5,0],[12,11,3],"copper"),part([6,7,0],[10,9,.5],"aqua"),
                        part([4,5,13],[12,11,16],"copper"),part([6,7,15.5],[10,9,16],"aqua"),part([0,5,4],[3,11,12],"copper"),
                        part([0,7,6],[.5,9,10],"aqua"),part([13,5,4],[16,11,12],"copper"),part([15.5,7,6],[16,9,10],"aqua"),
                        part([4,13,4],[12,16,12],"dark"),part([6,15.5,6],[10,16,10],"aqua")]
    export_model("energy_distributor",distributor,[])
    monitor=frame()+[part([2,3,2],[14,13,14],"patina"),part([2,4,.5],[14,12,2],"copper"),part([3,5,.2],[13,11,.5],"dark"),
                    part([4,6,0],[5,8,.2],"aqua"),part([7,6,0],[8,10,.2],"aqua"),part([10,6,0],[11,9,.2],"aqua")]
    export_model("energy_monitor",monitor,[])
    tilt = {"origin": [8, 10, 8], "axis": "x", "angle": -22.5}
    solar = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 1], [15, 7, 15], "patina"), part([0, 6, 0], [16, 8, 16], "copper"), part([6, 8, 6], [10, 11, 10], "dark"), part([1, 10, 1], [15, 11, 15], "copper", tilt), part([2, 11, 2], [14, 11.5, 14], "aqua", tilt), part([7.5, 11.5, 2], [8.5, 12, 14], "copper", tilt), part([2, 11.5, 7.5], [14, 12, 8.5], "copper", tilt), part([4, 3, .5], [12, 5, 1], "aqua")]
    export_model("solar_infuser", solar, [])
    smelter = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 2], [15, 13, 15], "patina"), part([0, 11, 1], [16, 14, 16], "copper"), part([1, 2, 0], [4, 11, 2], "copper"), part([12, 2, 0], [15, 11, 2], "copper"), part([4, 2, 0], [12, 4, 2], "dark"), part([4, 9, 0], [12, 11, 2], "copper"), part([4, 4, 1], [12, 9, 2], "fire"), part([10, 14, 10], [14, 16, 14], "dark"), part([9, 14, 9], [15, 15, 15], "copper"), part([15, 4, 5], [16, 9, 11], "dark")]
    export_model("resonance_smelter", smelter, [part([4, 4, .5], [12, 9, 1], "glass")])
    cultivator = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 1], [15, 7, 15], "patina"), part([0, 6, 0], [16, 8, 16], "copper"),
                  part([3, 8, 3], [13, 10, 13], "dark"), part([2, 9, 2], [14, 10, 4], "copper"), part([2, 9, 12], [14, 10, 14], "copper"),
                  part([2, 9, 4], [4, 10, 12], "copper"), part([12, 9, 4], [14, 10, 12], "copper"),
                  part([6, 10, 6], [10, 15, 10], "aqua", {"origin": [8, 12, 8], "axis": "y", "angle": 45}), part([4, 3, .5], [12, 5, 1], "aqua")]
    export_model("aura_cultivator", cultivator, [])
    collector = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 1], [15, 9, 15], "patina"), part([0, 8, 0], [16, 10, 16], "copper"),
                 part([4, 10, 4], [12, 11, 12], "dark"), part([3, 10, 5], [6, 16, 11], "copper"), part([10, 10, 5], [13, 16, 11], "copper"),
                 part([3, 14, 5], [6, 16, 11], "aqua"), part([10, 14, 5], [13, 16, 11], "aqua"), part([4, 3, .5], [12, 5, 1], "aqua")]
    export_model("magnetic_collector", collector, [])
    shepherd = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 1], [15, 7, 15], "patina"), part([0, 6, 0], [16, 8, 16], "copper"),
                part([7, 8, 7], [9, 13, 9], "copper"), part([3, 13, 3], [13, 14, 4], "copper"), part([3, 13, 12], [13, 14, 13], "copper"),
                part([3, 13, 4], [4, 14, 12], "copper"), part([12, 13, 4], [13, 14, 12], "copper"),
                part([6, 14, 6], [10, 16, 10], "aqua"), part([4, 3, .5], [12, 5, 1], "aqua")]
    export_model("growth_shepherd", shepherd, [])
    reaper = [part([0, 0, 0], [16, 2, 16], "dark"), part([1, 2, 1], [15, 13, 15], "patina"), part([0, 12, 0], [16, 14, 16], "copper"),
              part([1, 2, 0], [4, 12, 2], "copper"), part([12, 2, 0], [15, 12, 2], "copper"), part([4, 3, .5], [12, 12, 1], "dark"),
              part([7, 4, .2], [9, 11, .5], "aqua"), part([5, 7, .2], [11, 9, .5], "aqua"), part([6, 14, 6], [10, 16, 10], "aqua")]
    export_model("mob_reaper", reaper, [])
    arborist = [part([0,0,0],[16,2,16],"dark"),part([1,2,1],[15,13,15],"patina"),part([0,12,0],[16,14,16],"copper"),
                part([1,2,0],[4,12,2],"copper"),part([12,2,0],[15,12,2],"copper"),part([5,3,.3],[11,10,1],"dark"),
                part([7,4,0],[9,11,.4],"aqua"),part([5,8,0],[11,10,.4],"aqua"),part([6,14,6],[10,16,10],"copper"),
                part([2,14,6],[6,16,10],"dark"),part([10,14,6],[14,16,10],"dark")]
    export_model("arboreal_reconstructor",arborist,[])
    sentinel = [part([0,0,0],[16,2,16],"dark"),part([1,2,1],[15,4,15],"copper"),part([3,4,3],[13,7,13],"patina"),
                part([5,7,5],[11,9,11],"copper"),part([6,9,6],[10,10,10],"dark"),part([1,4,1],[3,9,3],"copper"),
                part([13,4,1],[15,9,3],"copper"),part([1,4,13],[3,9,15],"copper"),part([13,4,13],[15,9,15],"copper"),
                part([7,3,0],[9,6,.4],"aqua"),part([4,2,4],[12,3,12],"aqua")]
    export_model("copper_sentinel",sentinel,[])
    brewer = [part([0,0,0],[16,2,16],"dark"),part([1,2,1],[15,4,15],"copper"),part([2,4,2],[4,14,4],"copper"),
              part([12,4,2],[14,14,4],"copper"),part([2,4,12],[4,14,14],"copper"),part([12,4,12],[14,14,14],"copper"),
              part([1,13,1],[15,15,15],"patina"),part([5,15,5],[11,16,11],"copper"),part([6,3,.2],[10,6,.6],"aqua"),
              part([5,5,5],[7,9,7],"aqua"),part([9,5,5],[11,9,7],"aqua"),part([7,5,9],[9,9,11],"aqua"),
              part([5.5,9,5.5],[6.5,11,6.5],"copper"),part([9.5,9,5.5],[10.5,11,6.5],"copper"),part([7.5,9,9.5],[8.5,11,10.5],"copper")]
    export_model("aetheric_brewer",brewer,[part([3,4,3],[13,13,13],"glass")])
    enchanter = [part([0,0,0],[16,2,16],"dark"),part([1,2,1],[15,4,15],"copper"),part([3,4,3],[13,8,13],"patina"),
                 part([2,8,2],[14,10,14],"copper"),part([4,10,4],[12,11,12],"dark"),part([5,11,5],[8,12,11],"patina"),
                 part([8,11,5],[11,12,11],"aqua"),part([7.5,10,5],[8.5,13,11],"copper"),part([1,4,1],[3,13,3],"copper"),
                 part([13,4,1],[15,13,3],"copper"),part([1,4,13],[3,13,15],"copper"),part([13,4,13],[15,13,15],"copper"),
                 part([1,13,1],[3,16,3],"aqua"),part([13,13,1],[15,16,3],"aqua"),part([1,13,13],[3,16,15],"aqua"),
                 part([13,13,13],[15,16,15],"aqua"),part([6,4,.3],[10,7,.7],"aqua")]
    export_model("enchantment_resonator",enchanter,[])
    bridge = frame() + [part([3,3,3],[13,12,13],"patina"),part([3,4,1],[13,12,3],"copper"),
                        part([4,5,.5],[12,11,1],"dark"),part([6,7,0],[10,9,.4],"aqua"),
                        part([5,7,13],[11,9,16],"dark"),part([6,7.5,15],[10,8.5,16],"aqua")]
    export_model("ssn_bridge",bridge,[])
    bridge_states = []
    for facing, rotation in (("north",{}),("east",{"y":90}),("south",{"y":180}),("west",{"y":270}),("up",{"x":270}),("down",{"x":90})):
        bridge_states.append({"when":{"facing":facing},"apply":{"model":"unusualcopper:block/ssn_bridge",**rotation}})
    write_json(ASSETS / "blockstates/ssn_bridge.json",{"multipart":bridge_states})
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
    for name, bands in (("reinforced_energy_pipe", 1), ("crystalline_energy_pipe", 2)):
        tier_center = copy.deepcopy(center) + [part([4, 4, 4], [12, 5, 12], "patina"), part([4, 11, 4], [12, 12, 12], "patina")]
        tier_arm = copy.deepcopy(arm)
        for band in range(bands):
            z = 1 + band * 2
            tier_arm += [part([5, 5, z], [11, 6, z + .5], "copper"), part([5, 10, z], [11, 11, z + .5], "copper")]
        if bands == 2:
            tier_center += [part([6, 6, 4], [10, 10, 4.5], "aqua"), part([6, 12, 6], [10, 13, 10], "aqua")]
        for suffix, parts, layer in (("center", tier_center, "cutout"), ("arm", tier_arm, "cutout"), ("arm_glass", glass, "translucent")):
            write_json(ASSETS / f"models/block/{name}_{suffix}.json", {"render_type": "minecraft:" + layer, "textures": refs, "elements": parts})
        tier_states = json.loads(json.dumps(multipart).replace("unusualcopper:block/pipe_", f"unusualcopper:block/{name}_"))
        write_json(ASSETS / f"blockstates/{name}.json", {"multipart": tier_states})
        write_json(ASSETS / f"models/item/{name}.json", {"parent": f"unusualcopper:block/{name}_center"})
        write_bbmodel(name, tier_center + tier_arm + glass, pipe_materials)
    item_center = json.loads(json.dumps(center).replace('"#aqua"', '"#fire"'))
    item_arm = json.loads(json.dumps(arm).replace('"#aqua"', '"#fire"'))
    for name, parts in (("item_pipe_center", item_center), ("item_pipe_arm", item_arm)):
        write_json(ASSETS / f"models/block/{name}.json", {"render_type": "minecraft:cutout", "textures": refs, "elements": parts})
    item_parts = [{"apply": {"model": "unusualcopper:block/item_pipe_center"}}]
    for side, rotations in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})):
        item_parts.append({"when": {side: "true"}, "apply": {"model": "unusualcopper:block/item_pipe_arm", **rotations}})
    write_json(ASSETS / "blockstates/copper_item_pipe.json", {"multipart": item_parts})
    write_json(ASSETS / "models/item/copper_item_pipe.json", {"parent": "unusualcopper:block/item_pipe_center"})
    write_bbmodel("copper_item_pipe", item_center + item_arm, pipe_materials)
    for name, color in (("resonant_enchantment_core", "aqua"), ("sentinel_range_focus", "aqua"), ("diamond_solar_lens", "aqua"), ("saturated_copper_essence", "aqua"), ("copper_frame", "copper"), ("copper_core", "copper"), ("face_selector", "copper"), ("cargo_bundle", "dark"), ("area_wand", "aqua")):
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
        if name == "diamond_solar_lens":
            d.ellipse((3, 3, 28, 28), fill=(182, 107, 66), outline=(234, 159, 103), width=2); d.ellipse((7, 7, 24, 24), fill=(43, 169, 175)); d.line((10, 13, 16, 7), fill=(170, 249, 239), width=3)
        elif name == "saturated_copper_essence":
            d.polygon([(16, 2), (25, 10), (25, 22), (16, 29), (7, 22), (7, 10)], fill=(44, 199, 192), outline=(186, 249, 238)); d.line((16, 3, 12, 18, 16, 28), fill=(99, 237, 224), width=2)
        elif name == "area_wand":
            d.line((6, 28, 22, 9), fill=(111, 64, 42), width=5); d.line((6, 28, 22, 9), fill=(222, 148, 91), width=2)
            d.polygon([(23, 1), (30, 8), (23, 15), (16, 8)], fill=(55, 214, 211), outline=(190, 251, 237)); d.line((23, 3, 20, 8, 23, 13), fill=(220, 255, 249), width=2)
        elif name == "face_selector":
            d.line((8, 25, 22, 10), fill=(115, 65, 42), width=7); d.line((8, 25, 22, 10), fill=(215, 141, 86), width=3)
            d.rectangle((16, 3, 28, 14), fill=(182, 107, 66), outline=(234, 159, 103), width=2); d.rectangle((20, 6, 26, 10), fill=(55, 214, 211))
        elif name == "cargo_bundle":
            d.polygon([(8, 7), (24, 7), (28, 26), (4, 26)], fill=(92, 69, 48), outline=(192, 126, 76)); d.rectangle((13, 6, 18, 28), fill=(40, 48, 44)); d.rectangle((11, 13, 21, 20), fill=(192, 126, 76)); d.rectangle((14, 15, 18, 18), fill=(55, 214, 211))
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
    additions = {
        "block.unusualcopper.mob_reaper": ("Mob Reaper", "Жнец сущностей"),
        "block.unusualcopper.arboreal_reconstructor": ("Arborist", "Арборист"),
        "block.unusualcopper.copper_sentinel": ("Copper Sentinel", "Медный часовой"),
        "block.unusualcopper.aetheric_brewer": ("Aetheric Brewer", "Эфирный автоклав"),
        "block.unusualcopper.enchantment_resonator": ("Enchantment Resonator", "Резонатор зачарований"),
        "block.unusualcopper.ssn_bridge": ("SSN Bridge", "Мост SSN"),
        "bridge.unusualcopper.connected": ("Connected to storage network", "Подключён к сети хранения"),
        "bridge.unusualcopper.disconnected": ("Point the marked port at a loaded SSN cable or master", "Направьте отмеченный порт на загруженный кабель или контроллер SSN"),
        "bridge.unusualcopper.unsupported": ("Requires supported SSN 1.13.2; Progressive 1.2.4 if installed", "Нужна поддерживаемая SSN 1.13.2; Progressive 1.2.4, если установлен"),
        "item.unusualcopper.resonant_enchantment_core": ("Resonant Enchantment Core", "Ядро резонатора зачарований"),
        "enchanter.unusualcopper.search": ("Find an applicable enchantment", "Найти подходящее зачарование"),
        "enchanter.unusualcopper.item": ("Item", "Предмет"),
        "enchanter.unusualcopper.lapis": ("Lapis", "Лазурит"),
        "enchanter.unusualcopper.preview": ("Preview", "Результат"),
        "enchanter.unusualcopper.choose": ("Choose an enchantment to request its exact price", "Выберите чары для расчёта точной цены"),
        "enchanter.unusualcopper.no_offers": ("No compatible enchantments for this item", "Нет подходящих чар для этого предмета"),
        "enchanter.unusualcopper.price": ("Price: %s XP points + %s CuE + 64 lapis", "Цена: %s XP + %s CuE + 64 лазурита"),
        "enchanter.unusualcopper.wallet": ("Available: %s XP points | %s CuE | Lapis: %s / 64", "Доступно: %s XP | %s CuE | Лазурит: %s / 64"),
        "enchanter.unusualcopper.confirm": ("Confirm spending", "Подтвердить трату"),
        "enchanter.unusualcopper.confirm_hint": ("Apply the displayed result and spend its price. Quote expires in %s seconds.", "Применить показанный результат и списать его цену. Цена действительна ещё %s с."),
        "enchanter.unusualcopper.redstone": ("Cycle redstone mode", "Переключить режим редстоуна"),
        "enchanter.unusualcopper.result.none": ("Select, then confirm", "Выберите и подтвердите"),
        "enchanter.unusualcopper.result.quoted": ("Price is ready", "Цена рассчитана"),
        "enchanter.unusualcopper.result.applied": ("Enchantment applied", "Чары применены"),
        "enchanter.unusualcopper.result.stale": ("Refresh item/price", "Обновите предмет/цену"),
        "enchanter.unusualcopper.result.invalid": ("Incompatible option", "Неподходящие чары"),
        "enchanter.unusualcopper.result.no_xp": ("Not enough XP points", "Не хватает XP points"),
        "enchanter.unusualcopper.result.no_lapis": ("Need 64 lapis", "Нужно 64 лазурита"),
        "enchanter.unusualcopper.result.no_energy": ("Not enough CuE", "Не хватает CuE"),
        "enchanter.unusualcopper.result.denied": ("Access denied", "Нет доступа"),
        "enchanter.unusualcopper.result.paused": ("Redstone pause", "Пауза по редстоуну"),
        "enchanter.unusualcopper.result.xp_cancelled": ("XP change cancelled", "Трата XP отменена"),
        "enchanter.unusualcopper.result.xp_changed": ("XP price changed", "Изменена операция XP"),
        "enchanter.unusualcopper.result.provider_error": ("Enchantment hook error", "Ошибка обработчика чар"),
        "brewer.unusualcopper.settings": ("Choose a potion, bottle type, quantity and stock target", "Выбрать зелье, вид флакона, количество и запас"),
        "brewer.unusualcopper.search": ("Find by potion name or ID", "Поиск по названию или ID зелья"),
        "brewer.unusualcopper.count": ("Amount", "Кол-во"),
        "brewer.unusualcopper.priority": ("Priority", "Приоритет"),
        "brewer.unusualcopper.mode.0": ("One order", "Разовый заказ"),
        "brewer.unusualcopper.mode.1": ("Keep stock", "Держать запас"),
        "brewer.unusualcopper.apply": ("Apply order", "Применить"),
        "brewer.unusualcopper.back": ("Inventory", "Инвентарь"),
        "brewer.unusualcopper.cancel": ("Cancel order", "Отменить заказ"),
        "brewer.unusualcopper.price": ("%s CuE / bottle | Stored: %s CuE", "%s CuE / зелье | В буфере: %s CuE"),
        "brewer.unusualcopper.chain": ("Selected recipe (scroll)", "Цепочка (прокрутка)"),
        "brewer.unusualcopper.reagents": ("Have / need", "Есть / нужно"),
        "brewer.unusualcopper.progress": ("Order: %s | Stage: %s/%s | Batch: %s", "Заказ: %s | Стадия: %s/%s | Партия: %s"),
        "brewer.unusualcopper.result.1": ("Applied", "Принято"),
        "brewer.unusualcopper.result.2": ("Invalid order", "Неверный заказ"),
        "brewer.unusualcopper.result.3": ("Wait briefly", "Подождите"),
        "brewer.unusualcopper.result.4": ("Batch active", "Партия начата"),
        "status.unusualcopper.need_bottles": ("Need empty bottles", "Нужны пустые бутылки"),
        "status.unusualcopper.need_reagent": ("Need next reagent", "Нужен следующий реагент"),
        "status.unusualcopper.brew_fault": ("Brewing data error; recover order", "Ошибка данных варки"),
        "status.unusualcopper.no_storage": ("Bind storage for stock mode", "Привяжите хранилище"),
        "status.unusualcopper.stock_unknown": ("Stock count incomplete", "Запас ещё не подсчитан"),
        "status.unusualcopper.stock_reached": ("Stock target reached", "Нужный запас достигнут"),
        "item.unusualcopper.sentinel_range_focus": ("Sentinel Range Focus", "Фокус дальности часового"),
        "sentinel.unusualcopper.stats": ("Radius: %s | Hits: %s", "Радиус: %s | Попаданий: %s"),
        "sentinel.unusualcopper.price": ("%s CuE / shot + %s / tick", "%s CuE / выстрел + %s / тик"),
        "sentinel.unusualcopper.focus_hint": ("Use the range focus on this block to upgrade the horizontal radius.", "Примените фокус к блоку для увеличения радиуса."),
        "status.unusualcopper.sentinel_limit": ("Sentinel chunk limit", "Лимит часовых в чанке"),
        "status.unusualcopper.shot_denied": ("Shot cancelled", "Выстрел отменён"),
        "death.attack.unusualcopper.sentinel_beam": ("%1$s was struck by a Copper Sentinel", "%1$s поражён Медным часовым"),
        "wand.unusualcopper.plantation": ("Select saplings or a trunk base; click again to remove a point", "Выберите саженец или основание ствола; повторный клик снимает точку"),
        "wand.unusualcopper.plantation_added": ("Plantation point added", "Точка посадки добавлена"),
        "wand.unusualcopper.plantation_removed": ("Plantation point removed", "Точка посадки снята"),
        "arborist.unusualcopper.point": ("%s: %s, %s, %s", "%s: %s, %s, %s"),
        "arborist.unusualcopper.hint": ("Preview next plantation. Mark up to 16 points with the wand. Top input: saplings; lower input: vanilla axe. Reserve the entire tree's saplings and energy before cutting.", "Показать следующую точку. Жезлом отметьте до 16 посадок. Верхний вход: саженцы; нижний: ванильный топор. До рубки нужны саженцы и энергия на весь цикл."),
        "arborist.unusualcopper.stats": ("Points: %s · logs: %s", "Точек: %s · брёвен: %s"),
        "arborist.unusualcopper.cancel": ("Cancel/retry; reserved items return through outputs", "Отмена/повтор; резерв предметов возвращается через выходы"),
        "status.unusualcopper.no_plantations": ("Mark plantation points", "Отметьте точки посадки"),
        "status.unusualcopper.tree_scanning": ("Inspecting the tree", "Проверка дерева"),
        "status.unusualcopper.tree_too_large": ("Tree exceeds scan limits", "Дерево превышает лимиты"),
        "status.unusualcopper.building_nearby": ("Construction near the tree", "Рядом признаки постройки"),
        "status.unusualcopper.tree_ambiguous": ("Tree shape is ambiguous", "Неоднозначная форма дерева"),
        "status.unusualcopper.need_saplings": ("Reserve matching saplings", "Нужны саженцы того же вида"),
        "status.unusualcopper.no_tool": ("Need an axe for the entire trunk", "Нужен топор на весь ствол"),
        "status.unusualcopper.replanting": ("Replanting", "Повторная посадка"),
        "status.unusualcopper.tree_changed": ("Tree changed; cancel/retry", "Дерево изменено; отмена/повтор"),
        "status.unusualcopper.tree_waiting": ("Waiting for a grown tree", "Ожидание взрослого дерева"),
        "status.unusualcopper.tree_fault": ("Forestry job needs attention", "Проверьте задание Арбориста"),
        "status.unusualcopper.break_denied": ("Block break denied", "Разрушение запрещено"),
        "status.unusualcopper.plant_denied": ("Planting blocked", "Посадка заблокирована"),
        "status.unusualcopper.tree_too_costly": ("Tree operation exceeds buffer", "Цена дерева превышает буфер"),
        "status.unusualcopper.scanning": ("Scanning population", "Подсчёт популяции"), "status.unusualcopper.population_limit": ("Population threshold", "Порог популяции"), "status.unusualcopper.kill_cancelled": ("Kill cancelled", "Убийство отменено"),
        "status.unusualcopper.charging": ("Compressing the rune", "Сжатие руны"),
        "block.unusualcopper.growth_shepherd": ("Growth Shepherd", "Пастырь роста"),
        "gui.unusualcopper.own_pets.0": ("Own pets: excluded", "Свои питомцы: выкл."), "gui.unusualcopper.own_pets.1": ("Own pets: allowed", "Свои питомцы: вкл."),
        "gui.unusualcopper.shepherd_stats": ("Grown: %s · next: %s s", "Рост: %s · волна: %s с"),
        "reaper.unusualcopper.settings": ("Target rules", "Правила целей"),
        "reaper.unusualcopper.trigger": ("Start above N", "Начать, если > N"),
        "reaper.unusualcopper.keep": ("Keep M (0 ≤ M ≤ N)", "Оставить M (0 ≤ M ≤ N)"),
        "reaper.unusualcopper.passive.0": ("Passive: excluded", "Мирные: исключены"),
        "reaper.unusualcopper.passive.1": ("Passive: allowed", "Мирные: разрешены"),
        "reaper.unusualcopper.adults.0": ("Any age", "Любой возраст"),
        "reaper.unusualcopper.adults.1": ("Adults only", "Только взрослые"),
        "reaper.unusualcopper.types": ("Entity IDs (up to 8, comma separated)", "Виды (до 8 ID через запятую)"),
        "reaper.unusualcopper.tags": ("Entity tags (up to 8, without #)", "Теги сущностей (до 8, без #)"),
        "reaper.unusualcopper.categories": ("Categories (comma separated)", "Категории (через запятую)"),
        "reaper.unusualcopper.types_hint": ("Example: minecraft:cow,minecraft:sheep. IDs, tags and categories match with OR. Empty filters select nothing.", "Пример: minecraft:cow,minecraft:sheep. Виды, теги и категории объединяются по ИЛИ. Пустые фильтры никого не выбирают."),
        "reaper.unusualcopper.tags_hint": ("Example: forge:animals. Uses entity type tags from loaded datapacks. Unknown or empty tags select nothing.", "Пример: forge:animals. Теги типов сущностей из датапаков. Неизвестные и пустые теги никого не выбирают."),
        "reaper.unusualcopper.categories_hint": ("monster,creature,ambient,axolotls,underground_water_creature,water_creature,water_ambient,misc", "monster,creature,ambient,axolotls,underground_water_creature,water_creature,water_ambient,misc"),
        "reaper.unusualcopper.protected": ("Pets, NPCs and named mobs stay protected.", "Питомцы, NPC и именные мобы защищены."),
        "reaper.unusualcopper.apply": ("Apply", "Применить"),
        "reaper.unusualcopper.back": ("Back to machine", "К машине"),
        "reaper.unusualcopper.loading": ("Reading server settings…", "Загрузка настроек…"),
        "reaper.unusualcopper.pending": ("Waiting for server…", "Ожидание сервера…"),
        "reaper.unusualcopper.throttled": ("Too fast; try again", "Слишком часто; повторите"),
        "reaper.unusualcopper.ready": ("Settings received", "Настройки получены"),
        "reaper.unusualcopper.applied": ("Saved by server", "Сохранено сервером"),
        "reaper.unusualcopper.invalid": ("Invalid N/M or entity filters", "Ошибка N/M или фильтра сущностей"),
        "reaper.unusualcopper.stats": ("Targets: %s · loot: %s", "Целей: %s · буфер: %s"),
        "reaper.unusualcopper.cost": ("%s CuE per real kill; no XP or Looting", "%s CuE за убийство; без XP и Looting"),
        "gui.unusualcopper.shepherd_hint": ("%s CuE per animal; advance age by up to %s ticks. Pet permission applies only to the machine owner's animals.", "%s CuE за особь; взросление до %s тиков. Разрешение действует только на животных владельца машины."),
        "status.unusualcopper.no_babies": ("No eligible babies", "Нет подходящих детёнышей"), "status.unusualcopper.unsupported_animal": ("Species needs an adapter", "Виду нужен адаптер"),
        "block.unusualcopper.magnetic_collector": ("Magnetic Collector", "Магнитный коллектор"),
        "gui.unusualcopper.collection_checked": ("Checked: %s", "Проверено: %s"), "gui.unusualcopper.collection_taken": ("Stacks: %s", "Подобрано: %s"),
        "block.unusualcopper.aura_cultivator": ("Aura Cultivator", "Культиватор ауры"), "item.unusualcopper.area_wand": ("Copper Configuration Wand", "Медный жезл настройки"),
        "gui.unusualcopper.preview": ("Area", "Область"), "gui.unusualcopper.unbind": ("Remove inventory binding", "Снять привязку хранилища"), "gui.unusualcopper.recover": ("Recover held cargo", "Вернуть удерживаемый груз"),
        "gui.unusualcopper.area_size": ("Area: %s blocks", "Зона: %s блоков"), "gui.unusualcopper.growth_stats": ("Checked: %s; grown: %s", "Проверено: %s; рост: %s"),
        "gui.unusualcopper.growth_mode.0": ("Even growth", "Равномерно"), "gui.unusualcopper.growth_mode.1": ("Finish maturation", "До зрелости"),
        "wand.unusualcopper.mode.0": ("Mode: area selection", "Режим: выбор области"), "wand.unusualcopper.mode.1": ("Mode: inventory binding", "Режим: привязка хранилища"), "wand.unusualcopper.mode.2": ("Mode: machine rules", "Режим: правила машины"),
        "wand.unusualcopper.tooltip": ("Shift + use in air: change mode. Use on a machine to begin.", "Shift + ПКМ в воздух: смена режима. ПКМ по машине — начать."),
        "wand.unusualcopper.first": ("Machine selected. Choose the first corner.", "Машина выбрана. Укажите первую точку."), "wand.unusualcopper.second": ("First corner selected. Choose the second corner.", "Первая точка выбрана. Укажите вторую."),
        "wand.unusualcopper.storage": ("Machine selected. Choose an inventory face.", "Машина выбрана. Укажите грань хранилища."), "wand.unusualcopper.bound": ("Inventory bound.", "Хранилище привязано."), "wand.unusualcopper.selected": ("Work area saved.", "Рабочая область сохранена."),
        "wand.unusualcopper.denied": ("No access, unavailable target, or held cargo.", "Нет доступа, цель недоступна или есть удерживаемый груз."), "wand.unusualcopper.select_machine": ("Select a compatible machine in this dimension first.", "Сначала выберите подходящую машину в этом измерении."),
        "wand.unusualcopper.stale": ("Machine changed or unloaded. Select it again.", "Машина изменилась или выгружена. Выберите её снова."), "wand.unusualcopper.too_far": ("Selection must be within 32 blocks of the machine.", "Выбор должен быть в пределах 32 блоков от машины."), "wand.unusualcopper.too_large": ("Maximum work area is 16 x 16 x 16.", "Максимальная область: 16 × 16 × 16."),
        "status.unusualcopper.no_area": ("Select a work area", "Выберите рабочую область"), "status.unusualcopper.no_targets": ("No targets in this pass", "Нет целей в этом проходе"), "status.unusualcopper.protected": ("Access denied by protection", "Запрещено защитой"),
        "status.unusualcopper.unloaded": ("Area chunk is unloaded", "Чанк области выгружен"), "status.unusualcopper.unsupported": ("Plant needs an adapter", "Растению нужен адаптер"), "status.unusualcopper.no_growth": ("Mature or cannot grow", "Зрелое или нет условий роста"),
        "block.unusualcopper.copper_item_pipe": ("Copper Item Pipe", "Предметная труба"),
        "item.unusualcopper.face_selector": ("Copper Face Selector", "Селектор граней"),
        "item.unusualcopper.cargo_bundle": ("Recovered Cargo", "Сохранённый груз"),
        "pipe.unusualcopper.mode.auto": ("Auto", "Авто"), "pipe.unusualcopper.mode.extract": ("Extract", "Извлекать"), "pipe.unusualcopper.mode.insert": ("Insert", "Вставлять"), "pipe.unusualcopper.mode.disabled": ("Disabled", "Выключено"),
        "pipe.unusualcopper.whitelist": ("Whitelist", "Разрешённые"), "pipe.unusualcopper.blacklist": ("Blacklist", "Запрещённые"),
        "pipe.unusualcopper.exact_on": ("All item data", "Все данные"), "pipe.unusualcopper.exact_off": ("Item type", "Тип предмета"),
        "pipe.unusualcopper.exact_hint": ("Include durability, names, enchantments and other item data in the filter.", "Учитывать прочность, имя, чары и остальные данные предмета."),
        "pipe.unusualcopper.priority": ("Priority", "Приоритет"), "pipe.unusualcopper.minimum": ("Min.", "Ниже"), "pipe.unusualcopper.maximum": ("Max.", "До"), "pipe.unusualcopper.reserve": ("Keep", "Резерв"),
        "pipe.unusualcopper.minimum_hint": ("Start replenishing below this stock; 0 keeps replenishment enabled.", "Начинать пополнение ниже этого запаса. 0 — пополнять постоянно."),
        "pipe.unusualcopper.maximum_hint": ("Stop at this stock. * means unlimited. Each item variant is counted separately.", "Остановиться на этом запасе. * — без предела. Варианты предмета считаются отдельно."),
        "pipe.unusualcopper.reserve_hint": ("Leave this many matching items in the extraction inventory.", "Оставлять столько соответствующих предметов в источнике."),
        "pipe.unusualcopper.apply": ("Set", "OK"), "pipe.unusualcopper.tag_add": ("Add", "+ Тег"), "pipe.unusualcopper.tag_remove": ("Remove", "− Тег"),
        "pipe.unusualcopper.tags_hint": ("Tags on this face: %s", "Теги этой грани: %s"), "pipe.unusualcopper.invalid_settings": ("Invalid settings", "Неверные значения"),
    }
    for name, values in additions.items(): en[name], ru[name] = values
    for name, english, russian in (("down", "Down", "Низ"), ("up", "Up", "Верх"), ("north", "North", "Север"), ("south", "South", "Юг"), ("west", "West", "Запад"), ("east", "East", "Восток")):
        en["direction.unusualcopper." + name] = english; ru["direction.unusualcopper." + name] = russian
    for name, english, russian in (("idle", "Idle", "Ожидание"), ("transferring", "Moving", "Перенос"), ("buffered", "Buffered", "В буфере"), ("no_route", "No route", "Нет маршрута"), ("paused", "Paused", "Пауза"), ("denied", "Access denied", "Нет доступа"), ("fault", "Handler error", "Ошибка порта")):
        en["pipe.unusualcopper.status." + name] = english; ru["pipe.unusualcopper.status." + name] = russian
    for name, (english, russian) in statuses.items():
        en["status.unusualcopper." + name] = english; ru["status.unusualcopper." + name] = russian
    for mode, english, russian in (("disabled", "Disabled", "Закрыто"), ("item_input", "Item input", "Вход предметов"), ("item_output", "Item output", "Выход предметов"), ("energy_input", "CuE input", "Вход CuE"), ("energy_output", "CuE output", "Выход CuE")):
        en["side.unusualcopper." + mode] = english; ru["side.unusualcopper." + mode] = russian
    for key, english, russian in (
        ("block.unusualcopper.copper_cell", "Copper Cell I", "Медная ячейка I"),
        ("block.unusualcopper.copper_cell_ii", "Copper Cell II", "Медная ячейка II"),
        ("block.unusualcopper.copper_cell_iii", "Copper Cell III", "Медная ячейка III"),
        ("block.unusualcopper.reinforced_energy_pipe", "Reinforced Energy Pipe", "Укреплённый энергопровод"),
        ("block.unusualcopper.crystalline_energy_pipe", "Crystalline Energy Pipe", "Кристаллический энергопровод"),
        ("block.unusualcopper.energy_distributor", "Energy Distributor", "Энергетический разветвитель"),
        ("block.unusualcopper.energy_monitor", "Energy Monitor", "Энергетический монитор"),
        ("energy.unusualcopper.settings", "Energy priority and network", "Приоритет энергии и сеть"),
        ("energy.unusualcopper.priority_hint", "Priority when this device requests power.", "Приоритет заполнения буфера этого устройства."),
        ("energy.unusualcopper.branch_priority_hint", "Priority of this output branch. Configure the face as Output to use it.", "Приоритет этой выходной ветви. Для работы выберите режим «выход»."),
        ("energy.unusualcopper.priority.0", "Low", "Низкий"),
        ("energy.unusualcopper.priority.1", "Normal", "Обычный"),
        ("energy.unusualcopper.priority.2", "High", "Высокий"),
        ("energy.unusualcopper.mode.disabled", "Off", "выкл."),
        ("energy.unusualcopper.mode.energy_input", "Input", "вход"),
        ("energy.unusualcopper.mode.energy_output", "Output", "выход"),
        ("energy.unusualcopper.limit_hint", "Shared face limit in CuE/t. Click to cycle; 0 closes the branch.", "Общий лимит грани в CuE/тик. Нажмите для смены; 0 закрывает ветвь."),
        ("energy.unusualcopper.buffer", "Stored: %s / %s CuE", "Буфер: %s / %s CuE"),
        ("energy.unusualcopper.nodes", "%s wires, %s devices", "Проводов: %s; устройств: %s"),
        ("energy.unusualcopper.produced", "Generated: %s", "Производство: %s"),
        ("energy.unusualcopper.consumed", "Consumed: %s", "Расход: %s"),
        ("energy.unusualcopper.transferred", "Moved: %s", "Перенос: %s"),
        ("energy.unusualcopper.deficit", "Deficit: %s", "Дефицит: %s"),
        ("energy.unusualcopper.units", "CuE/t; updated every 10 ticks", "CuE/тик; обновление раз в 10 тиков"),
        ("energy.unusualcopper.deficit_hint", "Deficit is unmet receiver buffer demand within its port limits. It is not a prediction of future recipe costs. Moving CuE is separate from consuming it.", "Дефицит — непокрытый запрос заполнения буферов в пределах лимитов портов. Это не прогноз цены будущих рецептов. Перенос CuE показан отдельно от расхода."),
        ("energy.unusualcopper.open_hint", "Shift-right-click a machine with the Face Selector to open its energy controls. Equal priorities rotate; high priority is served first.", "Shift + ПКМ селектором граней по машине открывает управление энергией. Равные приоритеты чередуются; высокий обслуживается первым."),
        ("jei.unusualcopper.solar_infusion", "Solar Infusion", "Солнечное насыщение"),
        ("jei.unusualcopper.essence_info", "1 essence provides 500 CuE by default (configurable on the server). The Essence Splitter needs buffer space.", "1 эссенция даёт 500 CuE по умолчанию (меняется в настройках сервера). Требуется место в расщепителе."),
        ("jei.unusualcopper.lens_info", "Reusable solar lens: 16,384 input copper units of durability. Infusion needs open daytime sky.", "Многоразовая солнечная линза: ресурс 16 384 единицы входной меди. Насыщение требует открытого дневного неба."),
    ):
        en[key], ru[key] = english, russian
    write_json(ASSETS / "lang/en_us.json", en); write_json(ASSETS / "lang/ru_ru.json", ru)


if __name__ == "__main__":
    textures(); models(); data(); languages()
    print("Generated original textures, 14 editable Blockbench projects, models, item tags and RU/EN.")
