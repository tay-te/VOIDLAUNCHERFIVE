"""Generates the quest module's block and item assets: the notice board's models and paper texture, the journal's
texture, item definitions, blockstate, loot table and recipes.

    python tools/quest/gen_quest_assets.py [path/to/minecraft-client.jar]

The journal is the vanilla book recoloured as a leather-bound journal with gold tooling and a ribbon, so it sits with
vanilla's items; the board uses vanilla wood textures and only its pinned papers are drawn here.
"""
import io
import json
import os
import random
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources"))
ASSETS = os.path.join(RES, "assets", "hearth")
DATA = os.path.join(RES, "data", "hearth")
JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")


def vanilla_png(name):
    with zipfile.ZipFile(JAR) as z:
        return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + name))).convert("RGBA")


# ------------------------------------------------------------------------------------------------ textures

def journal_texture():
    book = vanilla_png("item/book.png")
    remap = {
        (49, 33, 4): (52, 26, 14),      # outline of the boards
        (82, 46, 16): (96, 48, 26),     # board edge
        (101, 75, 23): (122, 64, 34),   # leather
        (84, 62, 19): (104, 54, 29),    # leather, shaded
        (68, 37, 10): (88, 43, 22),     # creases in the leather
        (91, 91, 91): (196, 168, 120),  # page shadow
        (183, 183, 183): (232, 218, 186),
        (153, 153, 153): (205, 186, 148),
        (214, 214, 214): (243, 233, 207),
        (22, 16, 5): (28, 14, 8),
    }
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = book.getpixel((x, y))
            if a == 0:
                continue
            out.putpixel((x, y), remap.get((r, g, b), (r, g, b)) + (255,))
    # A gold-tooled diamond on the cover, a ribbon bookmark hanging out of the pages, a brass clasp on the fore-edge.
    for (x, y, c) in [(8, 3, (214, 170, 72)), (7, 4, (214, 170, 72)), (9, 4, (214, 170, 72)), (8, 5, (214, 170, 72)), (8, 4, (150, 96, 40)),
                      (7, 13, (158, 32, 36)), (7, 14, (158, 32, 36)), (8, 14, (122, 22, 26)), (7, 15, (122, 22, 26)),
                      (13, 6, (226, 190, 92)), (14, 6, (170, 128, 48)), (13, 7, (170, 128, 48))]:
        out.putpixel((x, y), c + (255,))
    return out


PAPER = (236, 226, 199)
PAPER_B = (226, 214, 184)
EDGE = (204, 188, 152)
INK = (70, 52, 36)
INK_F = (128, 104, 78)
SEAL = (158, 38, 38)
MAP = (96, 120, 72)


def paper_texture():
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def sheet(x0, y0, w, h, ink):
        for y in range(h):
            for x in range(w):
                c = PAPER if rnd.random() > 0.25 else PAPER_B
                if x == 0 or y == h - 1 or x == w - 1:
                    c = EDGE if (x + y) % 3 else PAPER_B
                img.putpixel((x0 + x, y0 + y), c + (255,))
        for (x, y, c) in ink:
            img.putpixel((x0 + x, y0 + y), c + (255,))

    # A notice with a heading, lines and a wax seal.
    sheet(0, 0, 5, 7, [(1, 1, INK), (2, 1, INK), (3, 1, INK), (1, 3, INK_F), (2, 3, INK_F), (3, 3, INK_F), (1, 4, INK_F), (2, 4, INK_F),
                        (3, 5, SEAL)])
    # A wide note of three lines.
    sheet(6, 0, 6, 5, [(1, 1, INK), (2, 1, INK), (3, 1, INK), (4, 1, INK_F), (1, 2, INK_F), (2, 2, INK_F), (3, 2, INK_F), (1, 3, INK_F),
                        (2, 3, INK_F), (4, 3, INK_F)])
    # A short note.
    sheet(0, 8, 5, 4, [(1, 1, INK), (2, 1, INK), (3, 1, INK_F), (1, 2, INK_F), (2, 2, INK_F)])
    # A sketch map with a route.
    sheet(6, 8, 4, 4, [(1, 1, MAP), (2, 1, INK_F), (2, 2, MAP), (1, 2, SEAL)])
    # Pin heads: red, brass, the red's highlight, and the pin's shadow.
    img.putpixel((12, 8), (179, 38, 42, 255))
    img.putpixel((13, 8), (196, 160, 70, 255))
    img.putpixel((12, 9), (232, 106, 106, 255))
    img.putpixel((13, 9), (40, 26, 16, 255))
    return img


# ------------------------------------------------------------------------------------------------ models

def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), uv=None, rotation=None):
    e = {"from": frm, "to": to, "faces": {}}
    for f in faces:
        face = {"texture": tex}
        if uv is not None:
            face["uv"] = uv
        e["faces"][f] = face
    if rotation:
        e["rotation"] = rotation
    return e


def post(x0):
    e = box([x0, 0, 7], [x0 + 2, 22, 9], "#post")
    e["faces"]["up"]["texture"] = "#post_top"
    e["faces"]["down"]["texture"] = "#post_top"
    for f in ("north", "south", "east", "west"):
        e["faces"][f]["uv"] = [7, 0, 9, 16]
    return e


BASE_TEXTURES = {
    "particle": "minecraft:block/spruce_planks",
    "planks": "minecraft:block/spruce_planks",
    "frame": "minecraft:block/dark_oak_planks",
    "roof": "minecraft:block/dark_oak_planks",
    "post": "minecraft:block/stripped_dark_oak_log",
    "post_top": "minecraft:block/stripped_dark_oak_log_top",
    "paper": "hearth:block/notice_board_paper",
}


def base_elements():
    return [
        post(1),
        post(13),
        # The board itself, its frame, and a little gable roof to keep the rain off the notices.
        box([1, 8.5, 7.5], [15, 20.5, 8.5], "#planks"),
        box([0, 7, 6.75], [16, 8.5, 9.25], "#frame"),
        box([0, 20.5, 6.75], [16, 22, 9.25], "#frame"),
        box([0, 8.5, 6.75], [1, 20.5, 9.25], "#frame"),
        box([15, 8.5, 6.75], [16, 20.5, 9.25], "#frame"),
        box([-1, 21.5, 5.5], [8.25, 22.75, 10.5], "#roof", rotation={"origin": [8, 22.5, 8], "axis": "z", "angle": 22.5}),
        box([7.75, 21.5, 5.5], [17, 22.75, 10.5], "#roof", rotation={"origin": [8, 22.5, 8], "axis": "z", "angle": -22.5}),
        box([7, 23.25, 5.25], [9, 24.25, 10.75], "#frame"),
    ]


# (x0, y0, x1, y1) on the board's front, its uv on the paper texture, and the pin above it.
PAPERS = [
    ((9, 13, 14, 20), [0, 0, 5, 7], (11.5, 19.25)),
    ((2, 15, 8, 20), [6, 0, 12, 5], (5, 19.25)),
    ((3, 9, 8, 13), [0, 8, 5, 12], (5.5, 12.25)),
    ((9.5, 8.75, 13.5, 12.75), [6, 8, 10, 12], (11.5, 12)),
]


def paper_elements(i):
    (x0, y0, x1, y1), uv, (px, py) = PAPERS[i]
    sheet = {"from": [x0, y0, 7.4], "to": [x1, y1, 7.4], "faces": {"north": {"texture": "#paper", "uv": uv}}}
    pin = box([px - 0.5, py - 0.5, 7.0], [px + 0.5, py + 0.5, 7.4], "#paper", faces=("north", "east", "west", "up", "down"), uv=[12, 8, 13, 9])
    return [sheet, pin]


DISPLAY = {
    "gui": {"rotation": [20, 200, 0], "translation": [0, -2.5, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 1.5, 1], "scale": [0.35, 0.35, 0.35]},
    "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 1.5, 1], "scale": [0.35, 0.35, 0.35]},
    "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
}


def main():
    os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "textures", "item"), exist_ok=True)
    paper_texture().save(os.path.join(ASSETS, "textures", "block", "notice_board_paper.png"))
    journal_texture().save(os.path.join(ASSETS, "textures", "item", "journal.png"))

    models = os.path.join(ASSETS, "models")
    write_json(os.path.join(models, "block", "notice_board.json"), {"textures": BASE_TEXTURES, "elements": base_elements()})
    for i in range(4):
        write_json(os.path.join(models, "block", "notice_board_paper_%d.json" % (i + 1)),
                   {"textures": {"particle": "hearth:block/notice_board_paper", "paper": "hearth:block/notice_board_paper"},
                    "elements": paper_elements(i)})
    inventory = base_elements()
    for i in range(4):
        inventory += paper_elements(i)
    write_json(os.path.join(models, "item", "notice_board.json"), {"textures": BASE_TEXTURES, "elements": inventory, "display": DISPLAY})
    write_json(os.path.join(models, "item", "journal.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "hearth:item/journal"}})

    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    multipart = []
    for facing, y in rot.items():
        apply = {"model": "hearth:block/notice_board"}
        if y:
            apply["y"] = y
        multipart.append({"when": {"facing": facing}, "apply": apply})
        for i in range(4):
            papers = "|".join(str(n) for n in range(i + 1, 5))
            apply = {"model": "hearth:block/notice_board_paper_%d" % (i + 1)}
            if y:
                apply["y"] = y
            multipart.append({"when": {"facing": facing, "papers": papers}, "apply": apply})
    write_json(os.path.join(ASSETS, "blockstates", "notice_board.json"), {"multipart": multipart})

    write_json(os.path.join(ASSETS, "items", "notice_board.json"), {"model": {"type": "minecraft:model", "model": "hearth:item/notice_board"}})
    write_json(os.path.join(ASSETS, "items", "journal.json"), {"model": {"type": "minecraft:model", "model": "hearth:item/journal"}})

    write_json(os.path.join(DATA, "loot_table", "blocks", "notice_board.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "hearth:notice_board"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "hearth:blocks/notice_board"})
    write_json(os.path.join(DATA, "recipe", "notice_board.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"S": "#minecraft:wooden_slabs", "P": "minecraft:paper", "B": "#minecraft:planks", "T": "minecraft:stick"},
        "pattern": ["SSS", "BPB", "T T"],
        "result": {"id": "hearth:notice_board"}})
    write_json(os.path.join(DATA, "recipe", "journal.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:book", "minecraft:leather"],
        "result": {"id": "hearth:journal"}})
    print("quest assets written under", RES)


if __name__ == "__main__":
    main()
