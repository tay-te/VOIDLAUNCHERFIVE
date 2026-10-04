"""Generates the law module's textures: the lockpick and key items and the HUD sprites (KCD's red hand, padlocks,
the sneaking eye). Pixel art drawn from character grids so it can be tweaked by hand.

    python tools/law/gen_law_assets.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/hearth"

# palette: one character per colour
PAL = {
    ".": (0, 0, 0, 0),
    "k": (24, 20, 18, 255),     # outline
    "d": (62, 64, 70, 255),     # dark iron
    "i": (128, 132, 140, 255),  # iron
    "l": (196, 200, 206, 255),  # light iron
    "w": (240, 242, 246, 255),  # glint
    "b": (92, 60, 34, 255),     # dark wood
    "B": (140, 96, 56, 255),    # wood
    "y": (120, 84, 30, 255),    # dark brass
    "Y": (196, 150, 58, 255),   # brass
    "G": (240, 206, 112, 255),  # brass glint
    "r": (120, 16, 16, 255),    # dark red
    "R": (196, 32, 28, 255),    # red
    "P": (236, 84, 70, 255),    # light red
    "e": (234, 226, 210, 255),  # eye white
    "n": (60, 40, 24, 255),     # iris
    "s": (30, 22, 18, 220),     # lash shadow
}

LOCKPICK = [
    "................",
    "...........kk...",
    "..........klk...",
    "...........kik..",
    "..........kik...",
    ".........kik....",
    "........kik.....",
    ".......kik......",
    "......kik.......",
    ".....kbBk.......",
    "....kbBBk.......",
    "...kbBbk........",
    "..kbBbk.........",
    "..kBbk..........",
    "...kk...........",
    "................",
]

KEY = [
    "................",
    "....kkkk........",
    "...kYGGYk.......",
    "..kYk..kYk......",
    "..kGk..kYk......",
    "..kYk..kyk......",
    "...kYYyyk.......",
    "....kYyk........",
    ".....kYk........",
    ".....kYk........",
    ".....kYkkk......",
    ".....kYYYk......",
    ".....kYkk.......",
    ".....kYYk.......",
    ".....kkk........",
    "................",
]

HAND = [
    "................",
    ".....kk.kk......",
    "....kPRkPRk.kk..",
    "....kRRkRRkkPRk.",
    ".kk.kRRkRRkkRRk.",
    "kPRkkRRkRRkkRRk.",
    "kRRkkRRkRRkkRrk.",
    "kRRRkRRRRRRkRrk.",
    ".kRRRRRRRRRRRrk.",
    "..kRRRRRRRRRRrk.",
    "..kRRRRRRRRRRk..",
    "...kRRRRRRRrrk..",
    "....kRRRRRrrk...",
    ".....kRRRrrk....",
    ".....krrrrrk....",
    "......kkkkk.....",
]

LOCK_SHUT = [
    "...kkk...",
    "..kdddk..",
    ".kdk.kdk.",
    ".kik.kik.",
    "kkkkkkkkk",
    "kYGGGGGYk",
    "kYYYkYYyk",
    "kYYkkkYyk",
    "kYYYkYYyk",
    "kyyyyyyyk",
    "kkkkkkkkk",
]

LOCK_OPEN = [
    "...kkk...",
    "..kdddk..",
    ".kdk.kdk.",
    ".kik.kik.",
    ".kik.....",
    "kkkkkkkkk",
    "kYGGGGGYk",
    "kYYYkYYyk",
    "kYYkkkYyk",
    "kyyyyyyyk",
    "kkkkkkkkk",
]

KEY_SMALL = [
    ".kkk.....",
    "kYGYk....",
    "kY.Ykkkkk",
    "kYYYYYYYk",
    ".kkkkYkYk",
    ".....k.k.",
    ".........",
    ".........",
    ".........",
]

EYE_SHUT = [
    "................",
    "................",
    "................",
    "................",
    "..kk........kk..",
    "...kkk....kkk...",
    ".....kkkkkk.....",
    "................",
    "................",
    "................",
]

EYE_HALF = [
    "................",
    "................",
    "................",
    "....kkkkkkkk....",
    "..kkeeennneekk..",
    ".kseeennnnneesk.",
    "..kkkkkkkkkkkk..",
    "................",
    "................",
    "................",
]

EYE_OPEN = [
    "................",
    ".....kkkkkk.....",
    "...kkeeeeeekk...",
    "..keeeennneeek..",
    ".keeeennknneeek.",
    ".keeeennnnneeek.",
    "..keeeennneeek..",
    "...kkeeeeeekk...",
    ".....kkkkkk.....",
    "................",
]

EYE_ALERT = [row.replace("e", "P").replace("n", "r") for row in EYE_OPEN]


def draw(img, grid, ox, oy):
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((ox + x, oy + y), PAL[ch])


def item(name, grid):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(img, grid, 0, 0)
    out = ROOT / "textures/item" / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


def main():
    item("lockpick", LOCKPICK)
    item("key", KEY)
    sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw(sheet, HAND, 0, 0)
    draw(sheet, LOCK_SHUT, 16, 0)
    draw(sheet, LOCK_OPEN, 25, 0)
    draw(sheet, KEY_SMALL, 34, 0)
    for i, eye in enumerate([EYE_SHUT, EYE_HALF, EYE_OPEN, EYE_ALERT]):
        draw(sheet, eye, i * 16, 16)
    out = ROOT / "textures/gui/law/sprites.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


if __name__ == "__main__":
    main()
