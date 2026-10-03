"""Reusable furniture and set dressing for the structure designs."""
from blocks import AIR, B
from builder import CCW, CW, DIRS, OPP, item, slab, stairs, trapdoor


def vec(d):
    return DIRS[d][0], DIRS[d][2]


def table(b, x, y, z, wood, top="plate", leg="fence"):
    """A one-block table: fence leg and a pressure plate / carpet / slab top."""
    b.set(x, y, z, B(f"{wood}_fence") if leg == "fence" else B(leg))
    if top == "plate":
        b.set(x, y + 1, z, B(f"{wood}_pressure_plate"))
    elif isinstance(top, str) and top.endswith("_carpet"):
        b.set(x, y + 1, z, B(top))
    elif top:
        b.set(x, y + 1, z, top if not isinstance(top, str) else B(top))


def long_table(b, cells, y, wood, cloth=None):
    """A row of top-slabs with fence legs at both ends; optional carpet cloth on top."""
    for i, (x, z) in enumerate(cells):
        end = i in (0, len(cells) - 1)
        b.set(x, y, z, B(f"{wood}_fence") if end else slab(wood, "top"))
        if cloth:
            b.set(x, y + 1, z, B(cloth))


def chair(b, x, y, z, wood, facing):
    """Stair seat whose backrest is on the `facing` side (the sitter looks the other way)."""
    b.set(x, y, z, stairs(wood, facing))


def chain_lamp(b, x, y_ceiling, z, length=1, soul=False):
    """Lantern hanging on `length` iron chains below the block at y_ceiling."""
    y = y_ceiling - 1
    for _ in range(length):
        b.set(x, y, z, B("iron_chain", axis="y"))
        y -= 1
    b.set(x, y, z, B("soul_lantern" if soul else "lantern", hanging=True))


def window(b, x, y, z, out, height=2, glass="glass_pane", shutters=None, box=None, box_wood="spruce"):
    """Glass in the wall cell (x, y..), optional open-trapdoor shutters beside it and a flower box below.
    `out` is the outward direction of the wall."""
    ox, oz = vec(out)
    sx, sz = vec(CW[out])
    for h in range(height):
        b.set(x, y + h, z, B(glass))
    if shutters:
        for s in (1, -1):
            for h in range(height):
                b.set(x + s * sx + ox, y + h, z + s * sz + oz,
                      trapdoor(shutters, out, "top" if h == height - 1 else "bottom", True))
    if box:
        b.set(x + ox, y - 1, z + oz, trapdoor(box_wood, out, "top", False))
        b.set(x + ox, y, z + oz, B(box))


def rug(b, x0, z0, x1, z1, y, main, border=None, accent=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            col = border if (edge and border) else main
            if accent and not edge and (x + z) % 2 == 0:
                col = accent
            b.set(x, y, z, B(f"{col}_carpet"))


def woodpile(b, x, y, z, wood, axis, length=3, height=2, rng=None):
    """Stacked logs lying along `axis` (the pile runs perpendicular to it)."""
    dx, dz = (1, 0) if axis == "z" else (0, 1)
    for i in range(length):
        h = height - (1 if rng and rng.random() < 0.35 and i in (0, length - 1) else 0)
        for k in range(h):
            b.set(x + dx * i, y + k, z + dz * i, B(f"{wood}_log", axis=axis))


def lamp_post(b, x, y, z, wood="spruce", height=2, hanging_from=None):
    """Fence post with a lantern on top, or a crook arm (`hanging_from` dir) carrying a hanging lantern."""
    for h in range(height):
        b.set(x, y + h, z, B(f"{wood}_fence"))
    if hanging_from:
        ax, az = vec(hanging_from)
        b.set(x, y + height, z, B(f"{wood}_fence"))
        b.set(x + ax, y + height, z + az, B(f"{wood}_fence"))
        b.set(x + ax, y + height - 1, z + az, B("lantern", hanging=True))
    else:
        b.set(x, y + height, z, B("lantern"))


def fallen_log(b, x, y, z, wood, axis, length, rng, mossy=True):
    dx, dz = (1, 0) if axis == "x" else (0, 1)
    for i in range(length):
        b.set(x + dx * i, y, z + dz * i, B(f"{wood}_log", axis=axis))
        if mossy and rng.random() < 0.5:
            b.set(x + dx * i, y + 1, z + dz * i, B("moss_carpet"))
        elif rng.random() < 0.2:
            b.set(x + dx * i, y + 1, z + dz * i, B(rng.choice(["red_mushroom", "brown_mushroom"])))


def scarecrow(b, x, y, z, yaw, rng=None):
    """Armor-stand scarecrow on a fence post: pumpkin head, leather tunic, straw at its feet."""
    b.set(x, y, z, B("hay_block", axis="y"))
    b.armor_stand(x, y + 1, z, yaw=yaw, equipment={"head": "carved_pumpkin", "chest": "leather_chestplate",
                                                    "legs": "leather_leggings", "mainhand": "wooden_hoe"},
                  pose={"Head": (0.0, 0.0, 6.0), "Body": (0.0, 0.0, 0.0), "LeftArm": (0.0, 0.0, -80.0),
                        "RightArm": (0.0, 0.0, 80.0)})


def well(b, x, y, z, stone="cobblestone", roof="spruce", post="spruce_fence"):
    """3x3 well centred on (x,z): stone curb, water, posts and a little roof with a bucket-chain."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(x + dx, y - 1, z + dz, B(stone))
            if dx or dz:
                b.set(x + dx, y, z + dz, B(f"{stone}_wall") if (dx and dz) else B(stone))
    b.set(x, y - 2, z, B(stone))
    b.set(x, y - 1, z, B("water", level=0))
    b.set(x, y, z, B("water", level=0))
    for dx, dz in ((-1, -1), (1, 1)):
        b.set(x + dx, y + 1, z + dz, B(post))
        b.set(x + dx, y + 2, z + dz, B(post))
    for dx in (-1, 0, 1):
        b.set(x + dx, y + 3, z - 1, stairs(roof, "south"))
        b.set(x + dx, y + 3, z + 1, stairs(roof, "north"))
        b.set(x + dx, y + 3, z, slab(roof))
    b.set(x, y + 2, z, B("iron_chain", axis="y"))


def beehive_patch(b, x, y, z, facing, rng, flowers=("poppy", "dandelion", "cornflower", "oxeye_daisy")):
    b.set(x, y, z, B("spruce_fence"))
    b.set(x, y + 1, z, B("beehive", facing=facing, honey_level=rng.randint(0, 5)))
    fx, fz = vec(facing)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0) and rng.random() < 0.6:
                b.set(x + dx + fx * 2, y, z + dz + fz * 2, B(rng.choice(flowers)))


def shelf_items(rng, kind):
    pools = {
        "kitchen": ["bowl", "bread", "honey_bottle", "glass_bottle", "apple", "cookie", "pumpkin_pie", "milk_bucket"],
        "study": ["book", "writable_book", "feather", "ink_sac", "map", "compass", "spyglass", "candle"],
        "alchemy": ["glass_bottle", "experience_bottle", "glowstone_dust", "redstone", "nether_wart", "blaze_powder",
                    "amethyst_shard", "spider_eye"],
        "hunter": ["arrow", "leather", "rabbit_hide", "bone", "feather", "string", "flint", "shears"],
        "fisher": ["cod", "salmon", "kelp", "string", "bowl", "lily_pad", "fishing_rod", "nautilus_shell"],
        "temple": ["candle", "gold_nugget", "bone", "glowstone_dust", "paper", "emerald", "clock"],
    }[kind]
    return [rng.choice(pools) if rng.random() < 0.85 else None for _ in range(3)]


def scatter(b, cells, y, choices, rng, density=0.4, need_ground=None):
    """Place random decoration at height y on cells that are empty (need_ground: block placed below)."""
    for (x, z) in cells:
        if rng.random() >= density or not b.is_air_or_void(x, y, z):
            continue
        if need_ground is not None and b.get(x, y - 1, z) is None:
            b.set(x, y - 1, z, B(need_ground) if isinstance(need_ground, str) else need_ground)
        c = rng.choice(choices)
        if isinstance(c, tuple) and c[0] == "tall":
            if b.is_air_or_void(x, y + 1, z):
                b.tall_plant(x, y, z, c[1])
        else:
            b.set(x, y, z, B(c) if isinstance(c, str) else c)


def path(b, cells, y, rng, mix=(("dirt_path", 6), ("coarse_dirt", 2), ("gravel", 1))):
    pool = [m for m, w in mix for _ in range(w)]
    for (x, z) in cells:
        if b.inb(x, y, z):
            b.set(x, y, z, B(rng.choice(pool)))


def line(p0, p1):
    """Integer cells on a 4-connected line between two (x, z) points."""
    (x0, z0), (x1, z1) = p0, p1
    out = [(x0, z0)]
    x, z = x0, z0
    while (x, z) != (x1, z1):
        if abs(x1 - x) >= abs(z1 - z) and x != x1:
            x += 1 if x1 > x else -1
        else:
            z += 1 if z1 > z else -1
        out.append((x, z))
    return out


def frame_item(it):
    return item(it)
