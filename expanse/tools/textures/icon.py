"""128x128 mod icon: a pixel-art vista (drawn at 64x64, scaled 2x).

Night sky with a teal aurora, layered snow-capped peaks, teal glowing mist
in the valley and a wisteria tree in the foreground."""
import math

import numpy as np

from texlib import blank, put


W = H = 64

BAYER = np.array([[0, 2], [3, 1]]) / 4.0 + 0.125


def _dither_pick(cols, t, x, y):
    """t in [0, len(cols)-1]: ordered-dither between neighbouring colours."""
    t = max(0.0, min(len(cols) - 1 - 1e-6, t))
    k = int(t)
    f = t - k
    return cols[k + 1] if f > BAYER[y % 2, x % 2] else cols[k]


def _ridge(rng, x0, x1, apex_x, apex_y, slope_l, slope_r, jag=1):
    """Mountain silhouette height per column (smaller y = higher)."""
    h = {}
    for x in range(x0, x1):
        if x <= apex_x:
            y = apex_y + (apex_x - x) * slope_l
        else:
            y = apex_y + (x - apex_x) * slope_r
        y += rng.integers(-jag, jag + 1) * (0 if abs(x - apex_x) < 2 else 1)
        h[x] = int(round(y))
    return h


def generate(rng_for):
    rng = rng_for('icon')
    img = blank(W, H)

    # ---------------- sky ----------------
    sky = ['#0e0c26', '#17133a', '#221a4e', '#30215f', '#43296f', '#5a3380', '#74408f']
    for y in range(H):
        t = (y / 40.0) ** 1.15 * (len(sky) - 1)
        for x in range(W):
            put(img, x, y, _dither_pick(sky, t, x, y), wrap=False)

    # aurora ribbon with hanging curtain rays
    aur = ['#1d5f6a', '#238f8a', '#3fc7b8', '#8ff5e6', '#d8fff8']
    for x in range(W):
        cy = 15 + 4.0 * math.sin(x / 8.5 + 0.6) + 1.5 * math.sin(x / 3.7)
        for y in range(2, 34):
            d = y - cy
            if -1.5 <= d <= 1.2:
                t = 4.0 - abs(d + 0.2) * 1.6
            elif 1.2 < d < 11:
                # curtain rays fall below the ribbon, fading with distance
                ray = 0.5 + 0.5 * math.sin(x * 1.3 + math.sin(x * 0.7) * 2)
                t = (2.6 - d * 0.25) * ray
            elif -4 < d < -1.5:
                t = 1.2 + (d + 1.5) * 0.45
            else:
                continue
            if t <= 0.15:
                continue
            c = _dither_pick([None] + aur, t, x, y)
            if c is not None:
                put(img, x, y, c, wrap=False)

    # stars
    star_rng = np.random.default_rng(7)
    for _ in range(34):
        x, y = int(star_rng.integers(0, W)), int(star_rng.integers(0, 30))
        cur = tuple(int(v) for v in img[y, x, :3])
        if sum(cur) < 200:
            put(img, x, y, '#e6e9ff' if star_rng.random() < 0.6 else '#a9b4ff', wrap=False)
    for (x, y) in ((50, 5), (8, 9)):
        put(img, x, y, '#ffffff', wrap=False)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(img, x + dx, y + dy, '#8f9bff', wrap=False)

    # ---------------- far range ----------------
    far = {}
    for x in range(W):
        y = 33 + 3.0 * math.sin(x / 6.0 + 1.0) + 2.0 * math.sin(x / 2.7)
        far[x] = int(round(y))
    for x in range(W):
        for y in range(far[x], H):
            snow = y < far[x] + 2 and far[x] < 33
            put(img, x, y, '#9f9ad8' if snow else ('#4a3f86' if (x + y) % 7 else '#433a7c'), wrap=False)

    # ---------------- main peaks ----------------
    def mountain(apex_x, apex_y, sl, sr, base, lit, mid, shadow, snow_l, snow_r, snow_depth, seed,
                 shadow_mid=None):
        r = np.random.default_rng(seed)
        ridge = _ridge(r, 0, W, apex_x, apex_y, sl, sr, jag=1)
        crest = {}
        cx = float(apex_x)
        for y in range(apex_y, base):
            crest[y] = int(round(cx))
            cx += r.choice([-0.5, 0.0, 0.5, 0.5])
        # rock ribs running down and out from the crest (give the faces form)
        ribs = set()
        snowy = set()
        for k, y0 in enumerate(range(apex_y + 4, base, 5)):
            rx = crest.get(y0, apex_x)
            L = 8 + 3 * k
            for i in range(L):
                ribs.add((rx - 1 - i, y0 + i + (i // 2)))
                if i < 2 and k < 2:
                    snowy.add((rx - 1 - i, y0 + i + (i // 2)))
            for i in range(L - 2):
                ribs.add((rx + 2 + i, y0 + 2 + i))
        snow_line = {x: snow_depth + int(r.integers(-1, 2)) for x in range(W)}
        for x in range(W):
            top = ridge[x]
            for y in range(max(top, 0), base):
                ridge_x = crest.get(y, apex_x)
                left = x <= ridge_x
                if y < top + snow_line[x] - abs(x - apex_x) // 3 or (x, y) in snowy:
                    c = snow_l if left else snow_r
                    if (x, y) in ribs and not (x, y) in snowy:
                        c = snow_r
                else:
                    c = lit if left else shadow
                    if (x, y) in ribs:
                        c = mid if left else (shadow_mid or shadow)
                    elif left and x == ridge_x:
                        c = mid
                put(img, x, y, c, wrap=False)

    mountain(16, 15, 1.2, 1.1, H, '#605aa8', '#4f4996', '#2b2660',
             '#e3e5ff', '#a29fd6', 5, 11, '#373173')
    mountain(42, 5, 1.0, 1.15, H, '#7670c0', '#5d57a8', '#332d6c',
             '#f4f5ff', '#b2afe4', 6, 23, '#423b80')

    # ---------------- teal valley glow ----------------
    glow = ['#2c2a66', '#253f70', '#236f7a', '#2a9e94', '#4fd8c8']
    for y in range(36, 50):
        for x in range(W):
            t = 3.3 - abs(y - 45) * 0.55 + 0.35 * math.sin(x / 3.0) + 0.25 * math.sin(x / 1.7 + y)
            if t > 0.6:
                c = _dither_pick(glow, t, x, y)
                cur = tuple(int(v) for v in img[y, x, :3])
                if t > 1.0 or (x + y) % 2 == 0:
                    put(img, x, y, c, wrap=False)

    # glowing lumen trees in the valley: short pale stems, round teal crowns
    for (tx, ty) in ((37, 45), (42, 44), (54, 45), (59, 46), (47, 46)):
        put(img, tx, ty, '#9fb0b4', wrap=False)
        put(img, tx, ty - 1, '#c9d6d8', wrap=False)
        crown = [(-1, -2, '#2a9e94'), (0, -2, '#4fe0cf'), (1, -2, '#2a9e94'),
                 (-1, -3, '#4fe0cf'), (0, -3, '#8ff5e6'), (1, -3, '#3fc7b8'),
                 (0, -4, '#d8fff8')]
        for (dx, dy, c) in crown:
            put(img, tx + dx, ty + dy, c, wrap=False)

    # ---------------- foreground hill ----------------
    for x in range(W):
        top = int(round(50 + 3.5 * math.sin((x + 6) / 11.0) - (6 if x < 22 else 0) * math.cos((x - 10) / 14.0)))
        for y in range(top, H):
            c = '#1b1533' if y > top + 1 else '#2b2350'
            if y == top:
                c = '#3a2f66'
            put(img, x, y, c, wrap=False)
        # grass tufts
        if x % 3 == 0:
            put(img, x, top - 1, '#2b2350', wrap=False)
    # fireflies
    for (x, y) in ((40, 53), (47, 57), (55, 51), (33, 58), (60, 56)):
        put(img, x, y, '#a6fbef', wrap=False)
        put(img, x + 1, y, '#2a9e94', wrap=False)

    # ---------------- wisteria tree ----------------
    bark = ['#1f1917', '#3a302c', '#574a43', '#6f6058']
    # twisting trunk: centre line + width, from the ground up into the canopy
    path = []
    for y in range(63, 25, -1):
        t = (63 - y) / 38.0
        cx = 11 + 5.0 * t + 1.6 * math.sin(t * 7.0)
        wdt = 3.4 - 1.6 * t
        path.append((cx, y, wdt))
    for (cx, y, wdt) in path:
        x0 = int(round(cx - wdt / 2))
        x1 = int(round(cx + wdt / 2))
        for x in range(x0, x1 + 1):
            c = bark[2] if x == x0 else (bark[0] if x == x1 else bark[1])
            if x == x0 and (y % 4 == 0):
                c = bark[3]
            put(img, x, y, c, wrap=False)
    # roots
    for (x, y, c) in ((8, 63, 1), (9, 62, 2), (15, 63, 0), (14, 62, 1), (7, 63, 2)):
        put(img, x, y, bark[c], wrap=False)
    # branches reaching out under the canopy
    for br in ([(15, 33), (13, 32), (11, 31), (9, 30), (7, 30)],
               [(16, 32), (18, 31), (20, 30), (22, 29), (24, 29), (26, 28)],
               [(16, 30), (16, 29), (17, 28), (17, 27)]):
        for (x, y) in br:
            put(img, x, y, bark[1], wrap=False)
            put(img, x, y + 1, bark[0], wrap=False)

    bloom = ['#3a1f63', '#55328d', '#7448ad', '#9468cc', '#b88ee6', '#d7b9f6', '#f1e4fd']
    clusters = [  # (cx, cy, r, depth)  back to front
        (26, 20, 5, 1), (5, 20, 5, 1), (15, 14, 6, 1),
        (9, 22, 6, 0), (21, 22, 6, 0), (15, 19, 6, 0), (3, 26, 4, 0), (28, 26, 4, 0),
    ]
    canopy_bottom = {}
    for (cx, cy, r, depth) in clusters:
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - r - 2, cx + r + 3):
                if not (0 <= x < W and 0 <= y < H):
                    continue
                d = math.hypot((x - cx) / 1.2, y - cy)
                # lumpy outline
                d += 0.6 * math.sin(math.atan2(y - cy, x - cx) * 5 + cx)
                if d > r:
                    continue
                lv = 4.3 - ((x - cx) * 0.7 + (y - cy) * 1.2) / r * 2.0 - depth * 1.2
                if d > r - 1.1:
                    lv -= 1.3
                c = _dither_pick(bloom, lv, x, y)
                put(img, x, y, c, wrap=False)
                canopy_bottom[x] = max(canopy_bottom.get(x, 0), y)
    # cascading racemes, long and tapering, pale at the top and deep at the tip
    rac_rng = np.random.default_rng(5)
    for x in range(0, 34):
        if x not in canopy_bottom or x % 2 == 1:
            continue
        top = canopy_bottom[x] - 1
        L = int(rac_rng.integers(4, 11))
        for i in range(L):
            t = i / max(1, L - 1)
            put(img, x, top + i, bloom[int(round(5.2 - 4.4 * t))], wrap=False)
            if i < L - 2:
                put(img, x + 1, top + i, bloom[int(round(3.8 - 3.2 * t))], wrap=False)
    # a few leaves and petal sparkles
    for (x, y) in ((7, 15), (20, 12), (26, 17), (12, 20), (23, 24), (3, 22)):
        put(img, x, y, '#6b9a45', wrap=False)
        put(img, x + 1, y + 1, '#3f6a2e', wrap=False)
    for (x, y) in ((12, 10), (6, 17), (20, 16), (17, 9)):
        put(img, x, y, '#fbf5ff', wrap=False)
    # drifting petals
    for (x, y) in ((33, 40), (38, 50), (29, 47), (24, 55)):
        put(img, x, y, '#c9a2ef', wrap=False)

    # ---------------- foreground details ----------------
    # glowcap mushrooms on the hill
    for (mx, my) in ((48, 58), (52, 59), (44, 60)):
        put(img, mx, my, '#e6efe8', wrap=False)
        put(img, mx, my - 1, '#e6efe8', wrap=False)
        for dx in (-1, 0, 1):
            put(img, mx + dx, my - 2, '#3fc7b8' if dx else '#8ff5e6', wrap=False)
        put(img, mx, my - 3, '#8ff5e6', wrap=False)

    # ---------------- rounded corners ----------------
    for (cx, cy) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
        for dx in range(3):
            for dy in range(3):
                if dx + dy < 2:
                    x = cx + dx if cx == 0 else cx - dx
                    y = cy + dy if cy == 0 else cy - dy
                    img[y, x] = 0

    big = np.repeat(np.repeat(img, 2, axis=0), 2, axis=1)
    return [('icon.png', big, False)]
