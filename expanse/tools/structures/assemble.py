"""Offline re-implementation of JigsawPlacement for previews and checks.

Given pools of finished Build templates it assembles a structure the way
net.minecraft...pools.JigsawPlacement does (rigid projection only): each
pool's elements are expanded by weight and shuffled, an empty element stops
the search, every rotation is tried in random order, a target jigsaw must be
named like the source jigsaw's `target` and face the opposite way, and the
child's bounding box may not intersect earlier pieces (children of a jigsaw
that points inside its own piece must stay inside that piece).
"""
import random
import re

from blocks import B, BlockState
from builder import DIRS, OPP, rot_dir, rot_pos, rot_state, rot_vec

ALWAYS = {"predicate_type": "minecraft:always_true"}


def jigsaws_of(build):
    out = []
    for pos, (st, n) in build.cells.items():
        if st.id == "minecraft:jigsaw":
            front, top = st.get("orientation").split("_")
            out.append({"pos": pos, "front": front, "top": top, "name": n["name"], "target": n["target"],
                        "pool": n["pool"], "final": n["final_state"], "joint": n["joint"]})
    return out


def parse_state(s):
    m = re.match(r"^([a-z0-9_:.-]+)(?:\[(.*)\])?$", s)
    name, props = m.group(1), m.group(2)
    if name.split(":")[-1] == "structure_void":
        return None
    kw = {}
    if props:
        for kv in props.split(","):
            k, v = kv.split("=")
            kw[k] = v
    from blocks import schema, ns
    full = {k: kw.get(k, d) for k, (vals, d) in schema(ns(name)).items()}
    return BlockState(name, **full)


class Placed:
    def __init__(self, build, rot, origin, depth, pool, processors=None):
        self.build, self.rot, self.origin, self.depth, self.pool = build, rot, origin, depth, pool
        self.processors = processors
        sx, sy, sz = build.size
        cs = [rot_pos(x, z, rot) for x in (0, sx - 1) for z in (0, sz - 1)]
        ox, oy, oz = origin
        self.box = (ox + min(c[0] for c in cs), oy, oz + min(c[1] for c in cs),
                    ox + max(c[0] for c in cs), oy + sy - 1, oz + max(c[1] for c in cs))

    def world(self, p):
        x, y, z = p
        rx, rz = rot_pos(x, z, self.rot)
        return (self.origin[0] + rx, self.origin[1] + y, self.origin[2] + rz)

    def jigsaws(self):
        out = []
        for j in jigsaws_of(self.build):
            j = dict(j)
            j["wpos"] = self.world(j["pos"])
            j["front"] = rot_dir(j["front"], self.rot)
            j["top"] = rot_dir(j["top"], self.rot)
            out.append(j)
        return out


def _inside(box, p):
    return box[0] <= p[0] <= box[3] and box[1] <= p[1] <= box[4] and box[2] <= p[2] <= box[5]


def _intersects(a, b):
    return not (a[3] < b[0] or b[3] < a[0] or a[4] < b[1] or b[4] < a[1] or a[5] < b[2] or b[5] < a[2])


def _within(inner, outer):
    return outer[0] <= inner[0] and outer[1] <= inner[1] and outer[2] <= inner[2] and \
        inner[3] <= outer[3] and inner[4] <= outer[4] and inner[5] <= outer[5]


def shuffled(pool, rng):
    lst = []
    for el, w in pool:
        lst += [el] * w
    rng.shuffle(lst)
    return lst


def pool_height(pools, pid):
    """StructureTemplatePool.getMaxSize: the tallest element's height (a feature counts as 1)."""
    return max([1 if isinstance(e, tuple) else e.size[1] for e, _ in pools.get(pid, []) if e is not None] + [0])


def assemble(pools, start_pool, depth, seed, max_dist=80, fallbacks=None, expansion_hack=False):
    """pools: {pool_id: [(element, weight)]}; element = Build, None (empty) or ('feature', id).
    fallbacks: {pool_id: fallback pool_id} (tried after the pool, and alone at the last depth, as
    vanilla does); expansion_hack: raise each piece's box to fit what attaches inside it (vanilla's
    use_expansion_hack, which villages use). Returns (pieces, features)."""
    fallbacks = fallbacks or {}
    rng = random.Random(seed)
    start = [e for e in shuffled(pools[start_pool], rng) if e is not None][0]
    s = Placed(start, rng.randrange(4), (0, 0, 0), 0, start_pool, getattr(start, "_procs", None))
    pieces = [s]
    features = []
    queue = [s]
    while queue:
        src = queue.pop(0)
        if src.depth > depth or (src.depth == depth and not fallbacks):
            continue
        js = src.jigsaws()
        rng.shuffle(js)
        for j in js:
            if j["pool"] == "minecraft:empty":
                continue
            fx, fy, fz = DIRS[j["front"]]
            tpos = (j["wpos"][0] + fx, j["wpos"][1] + fy, j["wpos"][2] + fz)
            inside = _inside(src.box, tpos)
            cands = shuffled(pools.get(j["pool"], []), rng) if src.depth < depth else []
            if j["pool"] in fallbacks:
                cands += shuffled(pools.get(fallbacks[j["pool"]], []), rng)
            for el in cands:
                if el is None:
                    break
                if isinstance(el, tuple):           # feature element: its own "bottom" jigsaw faces down
                    if j["front"] == "up":
                        features.append((tpos, el[1]))
                        break
                    continue
                done = False
                rots = [0, 1, 2, 3]
                rng.shuffle(rots)
                for r in rots:
                    cand = [t for t in jigsaws_of(el)]
                    rng.shuffle(cand)
                    for t in cand:
                        tfront = rot_dir(t["front"], r)
                        if tfront != OPP[j["front"]] or t["name"] != j["target"]:
                            continue
                        if j["joint"] == "aligned" and j["front"] in ("up", "down") and rot_dir(t["top"], r) != j["top"]:
                            continue
                        lx, lz = rot_pos(t["pos"][0], t["pos"][2], r)
                        origin = (tpos[0] - lx, tpos[1] - t["pos"][1], tpos[2] - lz)
                        p = Placed(el, r, origin, src.depth + 1, j["pool"], getattr(el, "_procs", None))
                        if expansion_hack and el.size[1] <= 16:
                            up = max([max(pool_height(pools, q["pool"]), pool_height(pools, fallbacks.get(q["pool"])))
                                      for q in p.jigsaws() if _inside(p.box, tuple(
                                          q["wpos"][i] + DIRS[q["front"]][i] for i in range(3)))] + [0])
                            if up:
                                b0 = p.box
                                p.box = b0[:4] + (max(b0[4], b0[1] + max(up + 1, b0[4] - b0[1])),) + b0[5:]
                        cx = (s.box[0] + s.box[3]) // 2
                        cz = (s.box[2] + s.box[5]) // 2
                        if max(abs(p.box[0] - cx), abs(p.box[3] - cx), abs(p.box[2] - cz), abs(p.box[5] - cz)) > max_dist:
                            continue
                        if inside:
                            if not _within(p.box, src.box) or any(
                                    _intersects(p.box, q.box) for q in pieces if q is not src and _within(q.box, src.box)):
                                continue
                        elif any(_intersects(p.box, q.box) for q in pieces):
                            continue
                        pieces.append(p)
                        queue.append(p)
                        done = True
                        break
                    if done:
                        break
                if done:
                    break
    return pieces, features


def _state_of(o):
    if isinstance(o, str):
        return parse_state(o)
    return BlockState(o["id"], **o.get("properties", {}))


def _test(pred, st, rnd):
    t = pred["predicate_type"]
    if t == "minecraft:always_true":
        return True
    if t in ("minecraft:block_match", "minecraft:random_block_match"):
        ok = st.id == pred["block"]
        return ok and (t == "minecraft:block_match" or rnd.random() < pred["probability"])
    if t in ("minecraft:blockstate_match", "minecraft:random_blockstate_match"):
        ok = st == _state_of(pred["block_state"])
        return ok and (t == "minecraft:blockstate_match" or rnd.random() < pred["probability"])
    return False


def apply_processors(cells, plist, seed):
    """Approximate StructureProcessor behaviour for previews (rule, block_rot, capped)."""
    import zlib
    if not plist:
        return cells
    for proc in plist["processors"]:
        t = proc["processor_type"]
        if t == "minecraft:rule":
            for pos in list(cells):
                st = cells[pos]
                if st is None:
                    continue
                rnd = random.Random(zlib.crc32(repr((pos, seed)).encode()))
                for r in proc["rules"]:
                    if r.get("location_predicate", ALWAYS)["predicate_type"] != "minecraft:always_true":
                        continue                   # needs the world (e.g. a path over water): not previewed
                    if _test(r["input_predicate"], st, rnd):
                        cells[pos] = _state_of(r["output_state"])
                        break
        elif t == "minecraft:block_rot":
            rot_ids = set(proc.get("rottable_blocks", []))
            for pos in list(cells):
                st = cells[pos]
                rnd = random.Random(zlib.crc32(repr((pos, seed, "rot")).encode()))
                if st is not None and (not rot_ids or st.id in rot_ids) and rnd.random() > proc["integrity"]:
                    del cells[pos]
        elif t == "minecraft:capped":
            cand = [p for p, st in cells.items() if st is not None and any(
                _test(r["input_predicate"], st, random.Random(0)) for r in proc["delegate"]["rules"])]
            random.Random(seed).shuffle(cand)
            for pos in cand[:proc["limit"]]:
                cells[pos] = _state_of(proc["delegate"]["rules"][0]["output_state"])
    return cells


def combined(pieces, processor_lists=None, seed=0):
    """Merge pieces into world cells {pos: state} and entity list, applying jigsaw final states
    and (when processor_lists is given) each piece's processors."""
    cells = {}
    ents = []
    for p in pieces:
        local = {}
        for pos, (st, n) in p.build.cells.items():
            if st.id == "minecraft:jigsaw":
                fs = parse_state(n["final_state"])
                if fs is None:
                    continue
                st = fs
            local[p.world(pos)] = st
        if processor_lists and p.processors:
            local = apply_processors(local, processor_lists.get(p.processors), seed)
        for wpos, st in local.items():
            cells[wpos] = rot_state(st, p.rot)
        for e in p.build.entities:
            x, y, z = e["pos"]
            rx, rz = rot_vec(x, z, p.rot)
            ents.append({"pos": (p.origin[0] + rx, p.origin[1] + y, p.origin[2] + rz), "nbt": e["nbt"],
                         "rot": p.rot})
    return cells, ents
