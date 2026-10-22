"""Generates Fray's combat HUD sprites (flat shapes and 7x7 status icons) into the GUI atlas.

    python tools/hud_sprites.py

Writes fray/src/client/resources/assets/fray/textures/gui/sprites/hud/*.png (+ .mcmeta for nine-slices). The HUD draws
every rounded plate, track and outline as one nine-sliced sprite tinted by colour (instead of a fill per row), and each
status icon as one sprite: Figma 3a's shapes at a fraction of the draw cost. Run it after changing a shape or an icon.
"""
import json
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'fray', 'src', 'client', 'resources', 'assets', 'fray', 'textures', 'gui', 'sprites', 'hud')
SS = 4  # supersampling for anti-aliased edges


def coverage(size_w, size_h, inside):
    """An RGBA white image whose alpha is the fraction of each pixel inside(x, y) (supersampled)."""
    img = Image.new('RGBA', (size_w, size_h), (255, 255, 255, 0))
    px = img.load()
    for y in range(size_h):
        for x in range(size_w):
            n = 0
            for sy in range(SS):
                for sx in range(SS):
                    if inside(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS):
                        n += 1
            px[x, y] = (255, 255, 255, round(255 * n / (SS * SS)))
    return img


def rounded(w, h, r, t=None):
    """Inside a rounded rectangle of radius r (an outline of thickness t when given)."""
    def sd(x, y):
        qx = abs(x - w / 2) - (w / 2 - r)
        qy = abs(y - h / 2) - (h / 2 - r)
        return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r
    if t is None:
        return lambda x, y: sd(x, y) <= 0
    return lambda x, y: -t < sd(x, y) <= 0


def save(img, name, nine=None):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + '.png'))
    meta = os.path.join(OUT, name + '.png.mcmeta')
    if nine:
        w, h, border = nine
        with open(meta, 'w') as f:
            json.dump({'gui': {'scaling': {'type': 'nine_slice', 'width': w, 'height': h, 'border': border}}}, f)
    elif os.path.exists(meta):
        os.remove(meta)


ICONS = {
    'bleed': ["...#...", "..###..", ".#####.", ".##+##.", "#######", ".#####.", "..###.."],
    'burn': ["...#...", "..##...", "..###..", ".##+##.", ".#+++#.", "##+++##", ".#####."],
    'chill': ["#..#..#", ".#.#.#.", "..###..", "###+###", "..###..", ".#.#.#.", "#..#..#"],
    'shock': ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
    'poison': ["..###..", "...#...", "..###..", ".#####.", "##+####", "###+###", ".#####."],
    'hex': [".#####.", "#.....#", "#.###.#", "#.#+#.#", "#.###.#", "#.....#", ".#####."],
    'blight': [".#####.", "#######", "#.###.#", "#######", ".#####.", ".#.#.#.", "......."],
    'frozen': ["#######", "#+#+#+#", "#######", "#+#+#+#", "#######", "#+#+#+#", "#######"],
    'stagger': ["#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"],
    'daze': [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.#...#", "#.####.", "......."],
    'exposed': ["#######", "#..#..#", "#.#...#", "#..#..#", ".#..#.#", "..#.#..", "...#..."],
    'rooted': ["...#...", "...#...", ".#.#.#.", "..###..", ".#.#.#.", "#..#..#", "...#..."],
    'silenced': ["..###..", ".#...#.", "#..#..#", "#.#.#.#", "#..#..#", ".#...#.", "..###.."],
    'wet': [".#...#.", ".#...#.", "###.###", "###.###", ".#...#.", "...#...", "..###.."],
    'oiled': ["...#...", "..###..", ".#####.", "##+####", "#######", "#######", ".#####."],
    'marked': ["..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.."],
    'shield': ["#######", "#+++++#", "#+++++#", "#+++++#", ".#+++#.", "..#+#..", "...#..."],
}
COLOURS = {
    'bleed': 0xE5484D, 'burn': 0xFF8A3D, 'chill': 0x8EC5FF, 'shock': 0xF5D76E, 'poison': 0x8BD45E, 'hex': 0xB07CFF, 'blight': 0x9A8F6A,
    'frozen': 0xBFE3FF, 'stagger': 0xEDE6CF, 'daze': 0xE8C46A, 'exposed': 0xFF9E5A, 'rooted': 0x8B6B4A, 'silenced': 0xB07CFF, 'wet': 0x5FA8E8,
    'oiled': 0xC9A24B, 'marked': 0xFF6B6B, 'shield': 0xFFFFFF,
}


def icon(name, rows):
    col = COLOURS[name]
    r, g, b = (col >> 16) & 255, (col >> 8) & 255, col & 255
    lr, lg, lb = [round(c + (255 - c) * 0.55) for c in (r, g, b)]
    img = Image.new('RGBA', (7, 7), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '#':
                px[x, y] = (r, g, b, 255)
            elif ch == '+':
                px[x, y] = (lr, lg, lb, 255) if name != 'shield' else (255, 255, 255, 115)
    save(img, 'icon_' + name)


def main():
    # Nine-sliced plates: radius r, texture (2r + 2) square.
    for r in (2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 14, 18, 22):
        s = 2 * r + 2
        save(coverage(s, s, rounded(s, s, r)), 'round_%d' % r, (s, s, r))
    for r, t in ((6, 1), (8, 1), (12, 2), (10, 1)):
        s = 2 * r + 2
        save(coverage(s, s, rounded(s, s, r, t)), 'outline_%d_%d' % (r, t), (s, s, r))
    # Pill caps (a bar is a fill between two half discs: 3 draws instead of a nine-slice's 9).
    save(coverage(32, 64, lambda x, y: math.hypot(x - 32, y - 32) <= 32), 'cap_l')
    save(coverage(32, 64, lambda x, y: math.hypot(x, y - 32) <= 32), 'cap_r')
    # Fixed-size plates drawn as one stretched sprite each (design px = texels): the status chip, the Art's well.
    for name, w, h, r, t in (('chip', 30, 36, 8, None), ('chip_outline', 30, 36, 8, 1), ('well', 74, 74, 18, None), ('well_inner', 56, 56, 12, None),
                             ('well_inner_outline', 56, 56, 12, 2)):
        save(coverage(w * 2, h * 2, (lambda f: (lambda x, y: f(x / 2, y / 2)))(rounded(w, h, r, t))), name)
    # A soft radial glow (H7 banners: "colour and glow allowed, done as radial gradients").
    g = Image.new('RGBA', (256, 96), (255, 255, 255, 0))
    gp = g.load()
    for y in range(96):
        for x in range(256):
            d = math.hypot((x + 0.5 - 128) / 128, (y + 0.5 - 48) / 48)
            gp[x, y] = (255, 255, 255, round(255 * max(0.0, 1 - d) ** 1.6))
    save(g, 'glow')
    # Stretched shapes.
    save(coverage(64, 64, lambda x, y: math.hypot(x - 32, y - 32) <= 32), 'disc')
    save(coverage(68, 68, lambda x, y: 32 <= math.hypot(x - 34, y - 34) <= 34), 'ring')
    save(coverage(172, 172, lambda x, y: 85 <= math.hypot(x - 86, y - 86) <= 86 and int((math.atan2(y - 86, x - 86) + math.pi) / (2 * math.pi) * 64) % 2 == 0),
        'ring_dashed')
    save(coverage(64, 64, lambda x, y: abs(x - 32) + abs(y - 32) <= 32), 'diamond')
    save(coverage(64, 64, lambda x, y: 28 <= abs(x - 32) + abs(y - 32) <= 32), 'diamond_outline')
    save(coverage(64, 56, lambda x, y: y <= 56 and abs(x - 32) <= y * 32 / 56), 'triangle')
    # The Art's spiral.
    def spiral(x, y):
        dx, dy = x - 32, y - 32
        d = math.hypot(dx, dy)
        if d > 31 or d < 3:
            return d < 4.5
        a = (math.atan2(dy, dx) + math.pi) / (2 * math.pi)
        turns = (d - 4) / 9.0
        return abs(((turns - a) % 1.0) - 0.5) > 0.32
    save(coverage(64, 64, spiral), 'spiral')
    for name, rows in ICONS.items():
        icon(name, rows)
    print('sprites written to', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
