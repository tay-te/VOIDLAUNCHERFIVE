"""Render the respec draught item textures.

    python draught.py              # writes textures/item/draught_*.png (+ a x16 preview in the sheets dir)

Two layers, like a vanilla potion: a grey liquid the item model tints with the skill's colour (mettle:skill_colour),
and the bottle drawn over it (cork for Forgetting, a red wax seal for Unbinding).
"""
import os
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'mettle', 'textures', 'item'))

BOTTLE = [
    '................',
    '......####......',
    '.....#cCCd#.....',
    '.....#dCCd#.....',
    '....#hHHHHk#....',
    '.....#a~~b#.....',
    '.....#a~~b#.....',
    '....#a~~~~b#....',
    '...#........#...',
    '..#*.........#..',
    '..#*.........#..',
    '..#..........#..',
    '..#..........#..',
    '...#........#...',
    '....#......#....',
    '.....######.....',
]

# grey values the tint multiplies: 'S' surface, 'L' body, 'D' shade, 'o' bubble
LIQUID = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '....SSSSSSSS....',
    '...LLLLLLLLLD...',
    '...LLLLoLLLLD...',
    '...LLLLLLLLLD...',
    '...LLoLLLLLDD...',
    '....LLLLLLDD....',
    '.....DDDDDD.....',
    '................',
]

GREY = {'S': 240, 'L': 200, 'D': 150, 'o': 255}

GLASS = {
    '#': (30, 28, 38, 255),
    'h': (230, 240, 245, 255), 'H': (205, 222, 230, 255), 'k': (160, 182, 196, 255),
    'a': (200, 218, 228, 255), 'b': (150, 172, 188, 255),
    '~': (232, 242, 246, 70),
    '*': (255, 255, 255, 190),
}
CORK = {'c': (196, 158, 114, 255), 'C': (162, 122, 79, 255), 'd': (125, 88, 53, 255)}
WAX = {'c': (212, 142, 128, 255), 'C': (184, 97, 90, 255), 'd': (147, 64, 64, 255)}


def paint(rows, colours):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                im.putpixel((x, y), colours[ch])
    return im


def main():
    liquid = paint(LIQUID, {k: (v, v, v, 255) for k, v in GREY.items()})
    forgetting = paint(BOTTLE, {**GLASS, **CORK})
    unbinding = paint(BOTTLE, {**GLASS, **WAX})
    liquid.save(os.path.join(OUT, 'draught_liquid.png'))
    forgetting.save(os.path.join(OUT, 'draught_of_forgetting.png'))
    unbinding.save(os.path.join(OUT, 'draught_of_unbinding.png'))

    # preview: the bottle over liquid tinted in a few skill colours, at x16
    sheets = os.environ.get('ICONS_OUT', os.path.join(tempfile.gettempdir(), 'mettle-icons'))
    os.makedirs(sheets, exist_ok=True)
    tints = [(0xF5B03C, forgetting), (0x8DB1D8, forgetting), (0xE2455A, forgetting), (0x43BE6C, forgetting), (0xD9D2F2, unbinding)]
    sheet = Image.new('RGBA', (16 * 16 * len(tints) + 8 * (len(tints) + 1), 16 * 16 + 16), (48, 46, 50, 255))
    for i, (tint, bottle) in enumerate(tints):
        r, g, b = tint >> 16 & 255, tint >> 8 & 255, tint & 255
        tinted = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                v, _, _, a = liquid.getpixel((x, y))
                if a:
                    tinted.putpixel((x, y), (r * v // 255, g * v // 255, b * v // 255, a))
        tinted.alpha_composite(bottle)
        sheet.alpha_composite(tinted.resize((256, 256), Image.NEAREST), (8 + i * (256 + 8), 8))
    path = os.path.join(sheets, 'draughts.png')
    sheet.save(path)
    print(path)


if __name__ == '__main__':
    main()
