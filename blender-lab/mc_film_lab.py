"""
MC FILM LAB - make a Minecraft scene stop looking like "Minecraft with shaders" and start
looking filmed. Pixel textures stay; the world gets a real camera, sculpted light, physical
materials, air, and a lens.  (Think The LEGO Movie: stylized objects, physically real world.)

Run it on your OPEN scene:   Scripting tab > open this file > Run Script
or headless:                 blender -b MyWorld.blend --python mc_film_lab.py
                             blender -b MyWorld.blend --python mc_film_lab.py -- '{"hero_camera": "CAM_Long"}'

What it does, in order (every step is logged to the LAB_LOG text datablock):
  0. Safety: checks the Blender version, saves <name>_LAB.blend and keeps working ONLY in that
     copy, inspects the scene and prints a 5-line summary before touching anything.
  1. Baseline render settings (ray tracing 1:1 + denoise, Fast GI = Global Illumination,
     jittered shadows, AgX + Look), then renders //lab_renders/00_baseline.png
  2. Levers, each in its own LAB sub-collection, each followed by a test render:
       01 camera     CAM_Low (24 mm, ground level) + CAM_Long (135-200 mm), DOF on FOCUS_target
       02 light      low sun, sky fill cut, warm practical, rim light
       03 materials  Weld + angle-limited Bevel on near-camera meshes, texel roughness, leaf translucency
       04 air        pooled ground fog (height gradient x noise), volume shadows, drifting dust (GN)
       05 lens       bloom, halation, chromatic aberration, vignette, grain (compositor)
  3. Final 100 % / 128-sample render from the hero camera and the LAB_LOG write-up.

Toggling a lever: in the Outliner, click the camera icon ("Disable in Renders") of its LAB_0x
collection. That hides its objects AND flips every setting the lever changed (world strength,
original sun, modifiers, material mixes, compositor switch) through drivers that read that icon.
Lever 01 is the one exception: switch Scene > Camera back to your own camera.

Re-running with the _LAB file open rebuilds the requested stages in place (it never creates
_LAB_LAB); with the original open it makes a fresh _LAB copy (Blender keeps the old one as .blend1).

Tested on Blender 4.5 LTS and 5.2 LTS (EEVEE Next era; needs 4.2+).
"""
import json
import math
import os
import re
import shutil
import sys
import time
from datetime import datetime

import bpy
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector
from mathutils.bvhtree import BVHTree

# ============================================================================== CONFIG
CONFIG = {
    "hero_structure": None,        # object name of the tallest structure; None = auto (height-map prominence)
    "subject": None,               # foreground subject (character/prop); None = auto from names, else the structure
    "practical_source": None,      # object or material name that motivates the practical; None = nearest emitter
    "hero_camera": "CAM_Low",      # camera carried through levers 2-5: "CAM_Low" or "CAM_Long"
    "hour": "golden",              # "golden" or "blue"
    "giant_mesh_policy": "stop",   # "stop": skip bevel and report | "mask": GN edge-weight mask, still no splitting
    "bevel_radius": 32.0,          # metres around the hero camera that count as "near"
    "cam_low_lens": 24.0,          # 24-35 mm
    "cam_low_fstop": 2.0,          # f/1.4-2.8
    "cam_long_lens": 150.0,        # 135-200 mm (may be nudged to frame the subject)
    "cam_long_fstop": 2.8,
    "test": {"samples": 64, "percent": 50},
    "final": {"samples": 128, "percent": 100},
    "render_dir": "//lab_renders/",
    "stages": "all",               # or a comma list: baseline,camera,light,materials,air,lens,final
    "render": True,                # False = build every lever without rendering (fast set-up pass)
    "verdict_note": "",            # your own call on the renders, appended to the LAB_LOG verdict
    "cycles": {"samples": 256, "percent": 50, "time_limit": 0},   # for stage "cycles" (0 = no time limit)
}

STAGES = ["baseline", "camera", "light", "materials", "air", "lens", "final", "log"]
EXTRA_STAGES = ["quality", "cycles"]   # opt-in: EEVEE quality pass and a Cycles comparison frame
LEVERS = {  # key: (collection, number, colour tag)
    "camera": ("LAB_01_Camera", 1, "COLOR_05"),
    "light": ("LAB_02_Light", 2, "COLOR_02"),
    "materials": ("LAB_03_Materials", 3, "COLOR_03"),
    "air": ("LAB_04_Air", 4, "COLOR_04"),
    "lens": ("LAB_05_Lens", 5, "COLOR_06"),
    "quality": ("LAB_06_Quality", 6, "COLOR_07"),
}
IS5 = bpy.app.version >= (5, 0, 0)
LEAF_RE = re.compile(r"leaves|leaf|azalea")
CLEAR_RE = re.compile(r"water|glass|pane")
EMIT_RE = re.compile(r"torch|lantern|lamp|glowstone|campfire|fire|candle|lava|shroomlight|froglight|"
                     r"sea_lantern|jack_o|redstone_lamp|end_rod|beacon|window")
SUBJECT_RE = re.compile(r"(^|[^a-z])(subject|hero|player|steve|alex|character|char|wanderer|villager|person|"
                        r"figure|actor|rig|mob)([^a-z0-9]|$)")
STONE_RE = re.compile(r"stone|cobble|andesite|diorite|granite|deepslate|brick|tuff|basalt|blackstone|bedrock|"
                      r"calcite|dripstone|gravel|mossy|terracotta")
WOOD_RE = re.compile(r"plank|log|wood|oak|spruce|birch|jungle|acacia|mangrove|cherry|bamboo|crimson|warped|"
                     r"stripped|door|fence|barrel|chest|bookshelf|ladder|crafting")


# ============================================================================== small helpers
def say(*parts):
    print("[LAB]", *parts)


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def kelvin_rgb(k):
    """Blackbody colour (linear RGB, max component 1) - Tanner Helland fit, then sRGB->linear."""
    t = k / 100.0
    r = 255 if t <= 66 else 329.698727446 * (t - 60) ** -0.1332047592
    g = 99.4708025861 * math.log(t) - 161.1195681661 if t <= 66 else 288.1221695283 * (t - 60) ** -0.0755148492
    b = 255 if t >= 66 else (0 if t <= 19 else 138.5177312231 * math.log(t - 10) - 305.0447927307)
    c = [clamp(x, 0, 255) / 255.0 for x in (r, g, b)]
    c = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    m = max(c)
    return tuple(x / m for x in c)


def sock(sockets, name=None, identifier=None, kind=None):
    for s in sockets:
        if identifier and s.identifier != identifier:
            continue
        if name and s.name != name:
            continue
        if kind and s.type != kind:
            continue
        return s
    raise KeyError(f"socket {name or identifier} ({kind})")


def set_in(node, name, value):
    """Set an input socket if it exists (4.4+/5.x) - returns True if it did."""
    s = node.inputs.get(name)
    if s is None:
        return False
    try:
        s.default_value = value
        return True
    except (TypeError, ValueError):
        return False


def set_menu(node, socket_name, prop_name, socket_value, prop_value):
    """5.x exposes node options as menu sockets, 4.x as RNA enums."""
    if socket_name in node.inputs and node.inputs[socket_name].type == "MENU":
        node.inputs[socket_name].default_value = socket_value
    elif hasattr(node, prop_name):
        setattr(node, prop_name, prop_value)


def set_gn_input(mod, identifier, value):
    """5.x keeps Geometry Nodes modifier inputs in mod.properties.inputs, 4.x in ID properties."""
    props = getattr(mod, "properties", None)
    if props is not None and hasattr(props, "inputs"):
        props.inputs[identifier]["value"] = value
    else:
        mod[identifier] = value


def ensure_nodes(idblock):
    if not IS5 and not idblock.use_nodes:
        idblock.use_nodes = True
    return idblock.node_tree


def fmt(v):
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, int):
        return str(v)
    return repr(round(float(v), 6))


def get_path(idb, path, index=-1):
    v = idb.path_resolve(path)
    if index >= 0:
        v = v[index]
    return v.copy() if hasattr(v, "copy") else v


def set_path(idb, path, index, value):
    owner, attr = (idb.path_resolve(path.rsplit(".", 1)[0]), path.rsplit(".", 1)[1]) if "." in path else (idb, path)
    if index >= 0:
        getattr(owner, attr)[index] = value
    else:
        setattr(owner, attr, value)


# ============================================================================== LAB_LOG
class LabLog:
    """Structured log persisted on the scene so staged runs accumulate one LAB_LOG."""

    def __init__(self, scene):
        self.scene = scene
        try:
            self.d = json.loads(scene.get("LAB_log", ""))
        except (json.JSONDecodeError, TypeError):
            self.d = {}
        self.d.setdefault("sections", {})
        self.d.setdefault("renders", {})
        self.d.setdefault("warnings", [])

    def section(self, key, title):
        sec = {"title": title, "changes": [], "values": {}, "why": "", "notes": []}
        self.d["sections"][key] = sec
        return sec

    def warn(self, msg):
        say("WARNING:", msg)
        if msg not in self.d["warnings"]:
            self.d["warnings"].append(msg)

    def save(self):
        self.scene["LAB_log"] = json.dumps(self.d)
        self.write_text()

    def write_text(self):
        d = self.d
        L = ["MC FILM LAB - LAB_LOG", "=" * 72]
        meta = d.get("meta", {})
        for k in ("file", "lab_file", "folder", "blender", "date", "hero_camera"):
            if k in meta:
                L.append(f"{k:12}: {meta[k]}")
        if d.get("summary"):
            L += ["", "SCENE AS FOUND (inspected before any change)"] + ["  " + s for s in d["summary"]]
        order = ["baseline", "camera", "light", "materials", "air", "lens", "final", "quality", "cycles"]
        for key in order:
            sec = d["sections"].get(key)
            if not sec:
                continue
            L += ["", sec["title"], "-" * len(sec["title"])]
            if sec["changes"]:
                L.append("What changed:")
                L += ["  - " + c for c in sec["changes"]]
            if sec["values"]:
                L.append("Key values:")
                L += [f"  {k}: {v}" for k, v in sec["values"].items()]
            if sec["notes"]:
                L += ["Notes:"] + ["  ! " + n for n in sec["notes"]]
            if sec["why"]:
                L.append("Why it reads as film, not game:")
                L += ["  " + line for line in wrap(sec["why"], 68)]
        if d["renders"]:
            L += ["", "RENDERS  (display-referred metrics from the saved PNGs)", "-" * 56,
                  f"  {'file':26}{'time':>7}{'mean':>7}{'shadow%':>9}{'hi%':>6}{'dSTEP':>7}  eye-lands (x,y)"]
            for name in sorted(d["renders"]):
                r = d["renders"][name]
                L.append(f"  {name:26}{r['seconds']:>6.0f}s{r['mean']:>7.3f}{r['shadow']:>8.1f}%"
                         f"{r['highlight']:>5.1f}%{r.get('delta', 0):>7.3f}  ({r['eye'][0]:.2f},{r['eye'][1]:.2f})")
            L.append("  shadow% = pixels below 0.18 display luminance; dSTEP = mean |RGB| change vs the")
            L.append("  previous step; eye-lands = centroid of the brightest 2 % of pixels (0,0 = top-left).")
        if d.get("verdict"):
            L += ["", "VERDICT", "-------"] + ["  " + line for line in wrap(d["verdict"], 68)]
        if d["warnings"]:
            L += ["", "WARNINGS"] + ["  ! " + w for w in d["warnings"]]
        txt = bpy.data.texts.get("LAB_LOG") or bpy.data.texts.new("LAB_LOG")
        txt.clear()
        txt.write("\n".join(L) + "\n")
        self.text = "\n".join(L) + "\n"
        out = d.get("meta", {}).get("render_dir")
        if out and os.path.isdir(out):  # the same write-up next to the renders, readable outside Blender
            with open(os.path.join(out, "LAB_LOG.txt"), "w", encoding="utf-8") as fh:
                fh.write(self.text)


def wrap(text, width):
    out = []
    for para in text.split("\n"):
        line = ""
        for word in para.split():
            if len(line) + len(word) + 1 > width:
                out.append(line)
                line = word
            else:
                line = (line + " " + word).strip()
        out.append(line)
    return out


# ============================================================================== collections + switches
def lab_root(scene):
    root = bpy.data.collections.get("LAB")
    if root is None:
        root = bpy.data.collections.new("LAB")
    if root.name not in scene.collection.children:
        scene.collection.children.link(root)
    return root


def lever_coll(scene, key):
    name, _, tag = LEVERS[key]
    root = lab_root(scene)
    c = bpy.data.collections.get(name)
    if c is None:
        c = bpy.data.collections.new(name)
        c.color_tag = tag
    if c.name not in root.children:
        root.children.link(c)
    return c


def link(obj, coll):
    for c in list(obj.users_collection):
        c.objects.unlink(obj)
    coll.objects.link(obj)
    return obj


def store_orig(idb, path, index, value):
    key = f"{path}|{index}"
    data = json.loads(idb.get("LAB_orig", "{}"))
    if key not in data:  # first capture wins: that's the user's value
        data[key] = list(value) if hasattr(value, "__len__") and not isinstance(value, str) else value
        idb["LAB_orig"] = json.dumps(data)


def switch(idb, path, key, on, off=None, index=-1):
    """Drive idb.path: `on` while the lever collection renders, `off` (the user's value) when its
    camera icon is off. Simple expressions only, so it works with Python auto-run disabled."""
    coll = bpy.data.collections[LEVERS[key][0]]
    if off is None:
        off = get_path(idb, path, index)
    store_orig(idb, path, index, off)
    try:
        idb.driver_remove(path, index)
    except TypeError:
        pass
    fc = idb.driver_add(path, index) if index >= 0 else idb.driver_add(path)
    for m in list(fc.modifiers):
        fc.modifiers.remove(m)
    drv = fc.driver
    drv.type = "SCRIPTED"
    var = drv.variables.new()
    var.name = "off"
    var.type = "SINGLE_PROP"
    var.targets[0].id_type = "COLLECTION"
    var.targets[0].id = coll
    var.targets[0].data_path = "hide_render"
    drv.expression = f"{fmt(off)} if off else {fmt(on)}"
    try:
        set_path(idb, path, index, on)
    except (TypeError, AttributeError):
        pass
    return fc


def anim_ids():
    for coll in (bpy.data.objects, bpy.data.lights, bpy.data.cameras, bpy.data.worlds, bpy.data.scenes,
                 bpy.data.node_groups):
        yield from coll
    for m in bpy.data.materials:
        if m.node_tree:
            yield m.node_tree
    for w in bpy.data.worlds:
        if w.node_tree:
            yield w.node_tree
    for s in bpy.data.scenes:
        if getattr(s, "node_tree", None):
            yield s.node_tree


def teardown(scene, key):
    """Remove everything a lever added and put the user's values/links back."""
    name, num, _ = LEVERS[key]
    coll = bpy.data.collections.get(name)
    if coll is not None:
        for idb in list(anim_ids()):
            ad = getattr(idb, "animation_data", None)
            if not ad:
                continue
            for fc in list(ad.drivers):
                tgt = [t.id for v in fc.driver.variables for t in v.targets]
                if coll in tgt:
                    path, idx = fc.data_path, fc.array_index
                    store = json.loads(idb.get("LAB_orig", "{}"))
                    key = next((k for k in (f"{path}|{idx}", f"{path}|-1") if k in store), None)
                    idb.driver_remove(path, idx)
                    if key is not None:
                        try:
                            set_path(idb, path, int(key.rsplit("|", 1)[1]), store.pop(key))
                        except (TypeError, AttributeError, ValueError, KeyError):
                            pass
                        idb["LAB_orig"] = json.dumps(store)
        for ob in list(coll.all_objects):
            kind = {"MESH": bpy.data.meshes, "LIGHT": bpy.data.lights, "CAMERA": bpy.data.cameras,
                    "LIGHT_PROBE": bpy.data.lightprobes}.get(ob.type)
            dname = ob.data.name if ob.data is not None else None
            bpy.data.objects.remove(ob, do_unlink=True)
            data = kind.get(dname) if (kind is not None and dname) else None
            if data is not None and data.users == 0:
                kind.remove(data)
    saved = json.loads(scene.get(f"LAB{num}_settings", "{}"))
    for path, value in saved.items():   # plain render settings a lever changed (not drivable)
        try:
            set_path(scene, path, -1, value)
        except (TypeError, AttributeError, ValueError):
            pass
    if f"LAB{num}_settings" in scene:
        del scene[f"LAB{num}_settings"]
    prefix = f"LAB{num}_"
    trees = [(m, m.node_tree) for m in bpy.data.materials if m.node_tree]
    trees += [(w, w.node_tree) for w in bpy.data.worlds if w.node_tree]
    comp = comp_tree(scene, create=False)
    if comp is not None:
        trees.append((scene, comp))
    for owner, tree in trees:
        recs = json.loads(owner.get(f"{prefix}links", "[]"))
        for n in [n for n in tree.nodes if n.name.startswith(prefix)]:
            tree.nodes.remove(n)
        for r in recs:
            to_node, from_node = tree.nodes.get(r["to"]), tree.nodes.get(r["from"]) if r["from"] else None
            if to_node is None:
                continue
            to_s = sock(to_node.inputs, identifier=r["to_s"])
            if from_node is not None:
                tree.links.new(sock(from_node.outputs, identifier=r["from_s"]), to_s)
        if f"{prefix}links" in owner:
            del owner[f"{prefix}links"]
    for ng in [g for g in bpy.data.node_groups if g.name.startswith(prefix)]:
        bpy.data.node_groups.remove(ng)
    for m in [m for m in bpy.data.materials if m.name.startswith(prefix)]:
        bpy.data.materials.remove(m)
    for ob in bpy.data.objects:
        for md in [md for md in ob.modifiers if md.name.startswith(prefix)]:
            ob.modifiers.remove(md)


def record_link(owner, num, to_sock, from_sock):
    """Remember the user's original link into to_sock so teardown can restore it."""
    key = f"LAB{num}_links"
    recs = json.loads(owner.get(key, "[]"))
    recs.append({"to": to_sock.node.name, "to_s": to_sock.identifier,
                 "from": from_sock.node.name if from_sock else "", "from_s": from_sock.identifier if from_sock else ""})
    owner[key] = json.dumps(recs)


# ============================================================================== compositor access
def comp_tree(scene, create=True):
    if IS5:
        tree = scene.compositing_node_group
        if tree is None and create:
            tree = bpy.data.node_groups.new("Compositing", "CompositorNodeTree")
            tree.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
            scene.compositing_node_group = tree
        return tree
    if not scene.use_nodes:
        if not create:
            return scene.node_tree if scene.node_tree and len(scene.node_tree.nodes) else None
        scene.use_nodes = True
    return scene.node_tree


def comp_output(tree):
    if IS5:
        outs = [n for n in tree.nodes if n.bl_idname == "NodeGroupOutput"]
        out = next((n for n in outs if n.is_active_output), outs[0] if outs else None)
        if out is None:
            out = tree.nodes.new("NodeGroupOutput")
        if not tree.interface.items_tree:
            tree.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
        return out, out.inputs[0]
    out = next((n for n in tree.nodes if n.bl_idname == "CompositorNodeComposite"), None)
    if out is None:
        out = tree.nodes.new("CompositorNodeComposite")
    return out, out.inputs["Image"]


# ============================================================================== scene scan (BVH + height map)
class Scan:
    """World-space BVHs of every rendered mesh, split by what a ray should treat as a wall."""
    MAX_TRIS = 1_500_000   # beyond this, only geometry within KEEP_RADIUS of the camera is scanned

    def __init__(self, scene, exclude_colls=("LAB",), focus_xy=None, keep_radius=350.0):
        self.scene = scene
        dg = bpy.context.evaluated_depsgraph_get()
        excl = set()
        for cname in exclude_colls:
            c = bpy.data.collections.get(cname)
            if c:
                excl |= {o.name for o in c.all_objects}
        self.mat_class, self.mat_emit = {}, {}
        verts, tris, tri_cls, tri_owner, tri_mat = [], [], [], [], []
        self.owners, self.volumes = [], []
        voff = 0
        for inst in dg.object_instances:
            ob = inst.object
            if ob.type != "MESH":
                continue
            src = (inst.parent.original if inst.is_instance and inst.parent else ob.original)
            if src.name in excl or src.hide_render:
                continue
            me = ob.data
            if me is None or len(me.polygons) == 0:
                continue
            mats = [s.material for s in ob.material_slots] or [None]
            cls = [self.classify(m) for m in mats]
            if all(c == "volume" for c in cls):
                self.volumes.append(src.name)
                continue
            me.calc_loop_triangles()
            nt = len(me.loop_triangles)
            if nt == 0:
                continue
            co = np.empty(len(me.vertices) * 3, np.float32)
            me.vertices.foreach_get("co", co)
            M = np.array(inst.matrix_world)
            co = co.reshape(-1, 3) @ M[:3, :3].T + M[:3, 3]
            tv = np.empty(nt * 3, np.int32)
            me.loop_triangles.foreach_get("vertices", tv)
            tm = np.empty(nt, np.int32)
            me.loop_triangles.foreach_get("material_index", tm)
            tm = np.clip(tm, 0, len(mats) - 1)
            verts.append(co)
            tris.append(tv.reshape(-1, 3) + voff)
            voff += len(co)
            cls_arr = np.array([{"solid": 0, "leaves": 1, "clear": 2, "cutout": 3, "volume": 4}[c] for c in cls])
            tri_cls.append(cls_arr[tm])
            self.owners.append(src.name)
            tri_owner.append(np.full(nt, len(self.owners) - 1, np.int32))
            tri_mat.append(np.array([self.mat_index(m) for m in mats])[tm])
        if not tris:
            raise RuntimeError("no renderable meshes found")
        self.V = np.concatenate(verts)
        self.T = np.concatenate(tris)
        self.cls = np.concatenate(tri_cls)
        self.owner = np.concatenate(tri_owner)
        self.tmat = np.concatenate(tri_mat)
        if len(self.T) > self.MAX_TRIS and focus_xy is not None:  # huge worlds: keep what the shot can see
            cen = self.V[self.T].mean(1)[:, :2]
            keep = np.linalg.norm(cen - np.array(focus_xy), axis=1) < keep_radius
            self.T, self.cls, self.owner, self.tmat = self.T[keep], self.cls[keep], self.owner[keep], self.tmat[keep]
            say(f"  large world: scanning {keep.sum():,} of {len(keep):,} triangles within {keep_radius:.0f} m")
        self.bvh, self.map = {}, {}
        vl = self.V.tolist()
        for name, sel in (("solid", self.cls == 0), ("occ", (self.cls == 0) | (self.cls == 1)),
                          ("top", self.cls != 3), ("all", self.cls >= 0)):
            idx = np.nonzero(sel)[0]
            self.map[name] = idx
            self.bvh[name] = BVHTree.FromPolygons(vl, self.T[idx].tolist(), all_triangles=True)
        self.lo, self.hi = self.V.min(0), self.V.max(0)

    _mats = []

    def mat_index(self, m):
        key = m.name if m else ""
        if key not in self.mat_emit:
            self.mat_emit[key] = is_emissive(m)
        if key not in Scan._mats:
            Scan._mats.append(key)
        return Scan._mats.index(key)

    def classify(self, m):
        key = m.name if m else ""
        if key not in self.mat_class:
            self.mat_class[key] = classify_material(m)
        return self.mat_class[key]

    def cast(self, origin, direction, dist=1e5, kind="occ", skip=None):
        """First hit as (location, normal, distance, triangle); owners in `skip` are passed through."""
        o, d = Vector(origin), Vector(direction).normalized()
        travelled = 0.0
        for _ in range(24):
            loc, nor, idx, t = self.bvh[kind].ray_cast(o, d, dist - travelled)
            if loc is None:
                return None
            tri = self.map[kind][idx]
            if skip and self.owners[self.owner[tri]] in skip:
                travelled += t + 1e-3
                o = loc + d * 1e-3
                continue
            return loc, nor, travelled + t, tri
        return None

    def clear(self, a, b, kind="occ", slack=0.3, skip=None):
        a, b = Vector(a), Vector(b)
        v = b - a
        hit = self.cast(a, v, v.length, kind, skip)
        return hit is None or hit[2] >= v.length - slack

    def ground(self, x, y, kind="solid"):
        hit = self.cast((x, y, self.hi[2] + 5), (0, 0, -1), self.hi[2] - self.lo[2] + 10, kind)
        return hit[0].z if hit else None

    def top_class(self, x, y):
        hit = self.cast((x, y, self.hi[2] + 5), (0, 0, -1), self.hi[2] - self.lo[2] + 10, "top")
        if hit is None:
            return None, None
        return hit[0].z, ["solid", "leaves", "clear", "cutout", "volume"][self.cls[hit[3]]]


def principled_of(mat):
    if not mat or not mat.node_tree:
        return None
    out = mat.node_tree.get_output_node("EEVEE") or mat.node_tree.get_output_node("ALL")
    if out and out.inputs["Surface"].is_linked:
        n = out.inputs["Surface"].links[0].from_node
        if n.bl_idname == "ShaderNodeBsdfPrincipled":
            return n
    return next((n for n in mat.node_tree.nodes if n.bl_idname == "ShaderNodeBsdfPrincipled"), None)


def mat_text(mat):
    parts = [mat.name]
    if mat.node_tree:
        parts += [n.image.name for n in mat.node_tree.nodes if n.bl_idname == "ShaderNodeTexImage" and n.image]
    return " ".join(parts).lower()


def classify_material(mat):
    if mat is None:
        return "solid"
    if mat.node_tree:
        out = mat.node_tree.get_output_node("EEVEE") or mat.node_tree.get_output_node("ALL")
        if out and out.inputs["Volume"].is_linked and not out.inputs["Surface"].is_linked:
            return "volume"
    text = mat_text(mat)
    if LEAF_RE.search(text):
        return "leaves"
    if CLEAR_RE.search(text):
        return "clear"
    p = principled_of(mat)
    if p and (p.inputs["Alpha"].is_linked or p.inputs["Alpha"].default_value < 0.99):
        return "cutout"
    return "solid"


def is_emissive(mat):
    if mat is None or not mat.node_tree:
        return False
    for n in mat.node_tree.nodes:
        if n.bl_idname == "ShaderNodeEmission" and n.outputs[0].is_linked:
            return True
        if n.bl_idname == "ShaderNodeBsdfPrincipled":
            s = n.inputs["Emission Strength"]
            c = n.inputs["Emission Color"]
            if (s.is_linked or s.default_value > 0.01) and (c.is_linked or max(c.default_value[:3]) > 0.01):
                return True
    return False


# ------------------------------------------------------------------------------ height map + structure
def box_filter(a, r, fn):
    p = np.pad(a, r, mode="edge")
    out = a.copy()
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            out = fn(out, p[r + dx:r + dx + a.shape[0], r + dy:r + dy + a.shape[1]])
    return out


class HeightMap:
    def __init__(self, scan, x0, y0, x1, y1, max_cells=170):
        self.step = max(0.5, max(x1 - x0, y1 - y0) / max_cells)
        self.xs = np.arange(x0, x1, self.step) + self.step / 2
        self.ys = np.arange(y0, y1, self.step) + self.step / 2
        nx, ny = len(self.xs), len(self.ys)
        self.hs = np.full((nx, ny), np.nan)
        self.ht = np.full((nx, ny), np.nan)
        self.tcls = np.empty((nx, ny), dtype=object)
        for i, x in enumerate(self.xs):
            for j, y in enumerate(self.ys):
                z = scan.ground(x, y)
                if z is not None:
                    self.hs[i, j] = z
                zt, c = scan.top_class(x, y)
                if zt is not None:
                    self.ht[i, j], self.tcls[i, j] = zt, c
        fill = np.nanmin(self.hs) if np.isfinite(self.hs).any() else 0.0
        h = np.where(np.isfinite(self.hs), self.hs, fill)
        r = max(1, int(round(8.0 / self.step)))           # opening removes anything narrower than ~16 m
        self.g = box_filter(box_filter(h, r, np.minimum), r, np.maximum)
        self.p = h - self.g

    def ij(self, x, y):
        i = int((x - self.xs[0] + self.step / 2) // self.step)
        j = int((y - self.ys[0] + self.step / 2) // self.step)
        if 0 <= i < len(self.xs) and 0 <= j < len(self.ys):
            return i, j
        return None

    def structures(self, min_prom=3.0, min_area=4.0):
        mask = self.p > min_prom
        seen = np.zeros_like(mask)
        comps = []
        for i, j in zip(*np.nonzero(mask)):
            if seen[i, j]:
                continue
            stack, cells = [(i, j)], []
            seen[i, j] = True
            while stack:
                a, b = stack.pop()
                cells.append((a, b))
                for da, db in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    na, nb = a + da, b + db
                    if 0 <= na < mask.shape[0] and 0 <= nb < mask.shape[1] and mask[na, nb] and not seen[na, nb]:
                        seen[na, nb] = True
                        stack.append((na, nb))
            if len(cells) * self.step ** 2 < min_area:
                continue
            ci = np.array(cells)
            xs, ys = self.xs[ci[:, 0]], self.ys[ci[:, 1]]
            tops = self.hs[ci[:, 0], ci[:, 1]]
            base = float(np.median(self.g[ci[:, 0], ci[:, 1]]))
            c = Vector((float(xs.mean()), float(ys.mean())))
            comps.append({"center": c, "top": float(np.nanmax(tops)), "base": base,
                          "prom": float(np.nanmax(tops)) - base,
                          "radius": float(np.max(np.hypot(xs - c.x, ys - c.y))) + self.step,
                          "area": len(cells) * self.step ** 2})
        return sorted(comps, key=lambda s: -s["prom"])


# ============================================================================== camera maths
def frame_fov(cam_data, scene):
    rx = scene.render.resolution_x * scene.render.pixel_aspect_x
    ry = scene.render.resolution_y * scene.render.pixel_aspect_y
    sw = cam_data.sensor_width
    if cam_data.sensor_fit == "VERTICAL":
        sh = cam_data.sensor_height
        sw = sh * rx / ry
    else:
        sh = sw * ry / rx if rx >= ry else sw
    return 2 * math.atan(sw / (2 * cam_data.lens)), 2 * math.atan(sh / (2 * cam_data.lens)), sw, sh


def aim(obj, yaw, pitch):
    fwd = Vector((math.cos(pitch) * math.cos(yaw), math.cos(pitch) * math.sin(yaw), math.sin(pitch)))
    obj.rotation_euler = fwd.to_track_quat("-Z", "Y").to_euler()
    bpy.context.view_layer.update()


def cam_rays(cam_obj, scene, nu, nv, u=(0.0, 1.0), v=(0.0, 1.0)):
    tr, br, bl, tl = [cam_obj.matrix_world.to_3x3() @ c for c in cam_obj.data.view_frame(scene=scene)]
    o = cam_obj.matrix_world.translation.copy()
    out = []
    for a in np.linspace(u[0], u[1], nu):
        for b in np.linspace(v[0], v[1], nv):
            p = bl + (br - bl) * float(a) + (tl - bl) * float(b)
            out.append((float(a), float(b), p.normalized()))
    return o, out


def project(scene, cam_obj, p):
    c = world_to_camera_view(scene, cam_obj, Vector(p))
    return c.x, c.y, c.z


def make_camera(name, coll, lens, fstop, focus):
    data = bpy.data.cameras.get(name) or bpy.data.cameras.new(name)
    data.lens = lens
    data.sensor_fit = "AUTO"
    data.sensor_width = 36.0
    data.dof.use_dof = True
    data.dof.focus_object = focus
    data.dof.aperture_fstop = fstop
    data.dof.aperture_blades = 7          # a real iris: heptagonal bokeh instead of perfect discs
    data.dof.aperture_rotation = math.radians(9)
    obj = bpy.data.objects.get(name) or bpy.data.objects.new(name, data)
    link(obj, coll)
    return obj


# ============================================================================== the lab
class Lab:
    def __init__(self, cfg):
        self.cfg = cfg
        self.scene = bpy.context.scene
        self.log = LabLog(self.scene)
        self.prev_png = None
        self.scan = None

    # ---------------------------------------------------------------- 0. safety
    def preflight(self):
        v = bpy.app.version
        if v < (4, 2, 0):
            raise RuntimeError(f"Blender {bpy.app.version_string}: needs 4.2+ (EEVEE Next ray tracing, "
                               "jittered shadows and Fast GI options).")
        say(f"Blender {bpy.app.version_string} - using the {'5.x' if IS5 else '4.2-4.5'} Python API")
        path = bpy.data.filepath
        stem = os.path.splitext(os.path.basename(path))[0] if path else "untitled"
        resume = stem.endswith("_LAB")
        if resume:
            say(f"{os.path.basename(path)} is already a LAB copy - rebuilding stages in place")
            lab_path = path
            orig = os.path.join(os.path.dirname(path), self.log.d.get("meta", {}).get("file", "?"))
        else:
            folder = os.path.dirname(path) if path else os.path.expanduser("~")
            lab_path = os.path.join(folder, f"{stem}_LAB.blend")
            orig = path or "(unsaved scene)"
            dirty = bpy.data.is_dirty
            # every change below happens in the copy; the original file on disk is never written
            bpy.ops.wm.save_as_mainfile(filepath=lab_path, check_existing=False, copy=False)
            say(f"saved working copy {lab_path}; the original stays untouched")
            if dirty:
                self.log.warn("Blender reported unsaved changes in the open scene: they are in the _LAB copy; "
                              "the original file on disk is untouched")
            self.log.d["sections"] = {}
            self.log.d["renders"] = {}
        self.log.d["meta"] = {"file": os.path.basename(orig), "lab_file": os.path.basename(lab_path),
                              "folder": os.path.dirname(lab_path), "blender": bpy.app.version_string,
                              "date": datetime.now().strftime("%Y-%m-%d %H:%M"),
                              "hero_camera": self.cfg["hero_camera"]}
        self.render_dir = bpy.path.abspath(self.cfg["render_dir"])
        os.makedirs(self.render_dir, exist_ok=True)
        self.log.d["meta"]["render_dir"] = self.render_dir
        if not resume or "LAB_orig_scene" not in self.scene:
            self.scene["LAB_orig_scene"] = json.dumps({
                "camera": self.scene.camera.name if self.scene.camera else "",
                "filepath": self.scene.render.filepath})
        return resume

    def inspect(self):
        s = self.scene
        meshes = [o for o in s.objects if o.type == "MESH" and not self.is_lab(o)]
        faces = sum(len(o.data.polygons) for o in meshes)
        verts = sum(len(o.data.vertices) for o in meshes)
        big = max(meshes, key=lambda o: len(o.data.polygons)) if meshes else None
        cams = [o for o in s.objects if o.type == "CAMERA" and not self.is_lab(o)]
        lights = [o for o in s.objects if o.type == "LIGHT" and not self.is_lab(o)]
        mats = [m for m in bpy.data.materials if m.users and not m.name.startswith("LAB")]
        imgs = [n for m in mats if m.node_tree for n in m.node_tree.nodes
                if n.bl_idname == "ShaderNodeTexImage" and n.image]
        closest = sum(1 for n in imgs if n.interpolation == "Closest")
        emit = sorted({m.name for m in mats if is_emissive(m)})
        fog, wind = self.find_fog(), self.find_wind()
        comp = comp_tree(s, create=False)
        comp_desc = "none"
        if comp is not None and len(comp.nodes):
            comp_desc = " > ".join(n.label or n.name for n in comp.nodes if n.bl_idname not in (
                "NodeGroupOutput", "CompositorNodeComposite", "CompositorNodeViewer", "NodeReroute",
                "NodeFrame")) or "empty"
        world = s.world
        wdesc = "none"
        if world and world.node_tree:
            sky = next((n for n in world.node_tree.nodes if n.bl_idname == "ShaderNodeTexSky"), None)
            env = next((n for n in world.node_tree.nodes if n.bl_idname == "ShaderNodeTexEnvironment"), None)
            bg = next((n for n in world.node_tree.nodes if n.bl_idname == "ShaderNodeBackground"), None)
            kind = f"{sky.sky_type.lower()} sky" if sky else ("HDRI" if env else "colour")
            wdesc = f"{kind} x{bg.inputs['Strength'].default_value:.2f}" if bg else kind
        cam_desc = ", ".join(f"{c.name} ({c.data.lens:.0f} mm)" for c in cams) or "none"
        light_desc = ", ".join(f"{l.name} {l.data.type.lower()} {l.data.energy:g}" for l in lights) or "none"
        engine = s.render.engine.replace("BLENDER_", "").replace("_NEXT", " Next").title()
        summary = [
            f"Blender {bpy.app.version_string}, {engine}, {s.render.resolution_x}x{s.render.resolution_y}, "
            f"{s.eevee.taa_render_samples} samples, view '{s.view_settings.view_transform}'.",
            f"{len(meshes)} meshes / {faces:,} faces / {verts:,} verts (~{verts / max(faces, 1):.1f} per face = "
            f"{'unwelded quads' if verts > 3 * faces else 'welded'}); largest '{big.name if big else '-'}' "
            f"holds {100 * len(big.data.polygons) / max(faces, 1):.0f}%.",
            f"Cameras: {cam_desc}. Lights: {light_desc}.",
            f"World: {wdesc}. {len(mats)} materials, {len(imgs)} image nodes ({closest} on Closest), "
            f"emissive: {', '.join(emit[:5]) or 'none'}.",
            f"Existing fog: {', '.join(fog) or 'none'} | wind: {', '.join(wind) or 'none'} | "
            f"compositor: {comp_desc} - all kept, LAB layers on top.",
        ]
        self.log.d["summary"] = summary
        print("\n[LAB] ===== scene as found =====")
        for line in summary:
            print("[LAB]  " + line)
        print("[LAB] ===========================\n")
        return summary

    def is_lab(self, obj):
        root = bpy.data.collections.get("LAB")
        return bool(root and obj.name in root.all_objects)

    def find_fog(self):
        found = []
        w = self.scene.world
        if w and w.node_tree:
            out = w.node_tree.get_output_node("EEVEE") or w.node_tree.get_output_node("ALL")
            if out and out.inputs["Volume"].is_linked:
                found.append(f"world volume '{w.name}'")
        for o in self.scene.objects:
            if o.type == "MESH" and not self.is_lab(o) and o.material_slots and all(
                    classify_material(s.material) == "volume" for s in o.material_slots):
                found.append(f"volume object '{o.name}'")
            elif o.type == "VOLUME" and not self.is_lab(o):
                found.append(f"VDB '{o.name}'")
        if self.scene.world and self.scene.world.mist_settings.use_mist:
            found.append("world mist")
        return found

    def find_wind(self):
        found = []
        for o in self.scene.objects:
            if self.is_lab(o):
                continue
            if o.field and o.field.type == "WIND":
                found.append(f"wind field '{o.name}'")
            for m in o.modifiers:
                if re.search(r"wind|sway|wave", m.name, re.I) or m.type == "WAVE":
                    found.append(f"{m.type.lower()} '{m.name}' on {o.name}")
        return found

    def wind_vector(self):
        for o in self.scene.objects:
            if o.field and o.field.type == "WIND" and not self.is_lab(o):
                return (o.matrix_world.to_3x3() @ Vector((0, 0, 1))).normalized()
        return Vector((0.6, 0.3, 0.0)).normalized()

    # ---------------------------------------------------------------- renders
    def render(self, name, cam=None, final=False):
        s = self.scene
        q = self.cfg["final" if final else "test"]
        if cam is not None:
            s.camera = cam
        path = os.path.join(self.render_dir, name + ".png")
        if not self.cfg["render"]:
            say(f"(build only) skipped render {name}")
            open(path, "a").close()
            return path
        s.eevee.taa_render_samples = q["samples"]
        s.render.resolution_percentage = q["percent"]
        s.render.image_settings.file_format = "PNG"
        s.render.image_settings.color_mode = "RGB"
        s.render.image_settings.color_depth = "8"
        s.render.filepath = path
        say(f"rendering {name} ({q['percent']}%, {q['samples']} samples, camera {s.camera.name}) ...")
        t = time.time()
        bpy.ops.render.render(write_still=True)
        dt = time.time() - t
        m = self.metrics(path)
        m["seconds"] = dt
        m["camera"] = s.camera.name
        self.log.d["renders"][name] = m
        self.prev_png = path
        self.log.save()
        say(f"  -> {path}  {dt:.0f}s  mean {m['mean']:.3f}  shadow {m['shadow']:.0f}%  step change {m.get('delta', 0):.3f}")
        return path

    def metrics(self, path):
        img = bpy.data.images.load(path, check_existing=False)
        w, h = img.size
        px = np.empty(w * h * 4, np.float32)
        img.pixels.foreach_get(px)
        bpy.data.images.remove(img)
        px = px.reshape(h, w, 4)[::-1, :, :3]
        lum = px @ np.array([0.2126, 0.7152, 0.0722], np.float32)
        thr = np.quantile(lum, 0.98)
        ys, xs = np.nonzero(lum >= thr)
        m = {"mean": float(lum.mean()), "shadow": float((lum < 0.18).mean() * 100),
             "highlight": float((lum > 0.85).mean() * 100),
             "eye": [float(xs.mean() / w), float(ys.mean() / h)] if len(xs) else [0.5, 0.5]}
        if self.prev_png and os.path.exists(self.prev_png):
            prev = bpy.data.images.load(self.prev_png, check_existing=False)
            if tuple(prev.size) == (w, h):
                pp = np.empty(w * h * 4, np.float32)
                prev.pixels.foreach_get(pp)
                m["delta"] = float(np.abs(pp.reshape(h, w, 4)[::-1, :, :3] - px).mean())
            bpy.data.images.remove(prev)
        return m

    # ---------------------------------------------------------------- baseline settings
    def baseline(self):
        s, e = self.scene, self.scene.eevee
        sec = self.log.section("baseline", "BASELINE RENDER SETTINGS (kept for every render)")
        engine = "BLENDER_EEVEE" if IS5 else "BLENDER_EEVEE_NEXT"
        if s.render.engine != engine:
            sec["changes"].append(f"render engine {s.render.engine} -> {engine}")
        s.render.engine = engine
        e.use_raytracing = True
        e.ray_tracing_options.resolution_scale = "1"
        e.ray_tracing_options.use_denoise = True
        e.ray_tracing_options.denoise_spatial = True
        e.ray_tracing_options.denoise_temporal = True
        e.ray_tracing_options.denoise_bilateral = True
        e.use_fast_gi = True
        e.fast_gi_method = "GLOBAL_ILLUMINATION"
        e.use_shadows = True
        keys = []
        for o in s.objects:
            if o.type == "LIGHT" and not self.is_lab(o) and (o.data.type == "SUN" or o.data.energy >= 10):
                o.data.use_shadow_jitter = True
                keys.append(o.name)
        vs = s.view_settings
        look_before = f"{vs.view_transform} / {vs.look}"
        vs.view_transform = "AgX"
        vs.look = self.pick_look(("AgX - Medium High Contrast", "Medium High Contrast", "AgX - Base Contrast"))
        sec["changes"] += [
            "ray tracing ON, resolution 1:1, denoise (spatial + temporal + bilateral) ON",
            "Fast GI ON, method Global Illumination",
            f"jittered (soft, physically sized) shadows on key lights: {', '.join(keys) or 'none found'}",
            f"view transform {look_before} -> AgX / {vs.look}",
            "test renders: EEVEE 64 samples at 50 %; final: 128 samples at 100 %",
        ]
        sec["values"] = {"ray tracing resolution": "1:1", "fast_gi_method": e.fast_gi_method,
                         "view": f"AgX + {vs.look}", "exposure": vs.exposure}
        sec["why"] = ("A game frame is lit by screen-space tricks and clipped to flat white. Ray tracing at full "
                      "resolution gives real contact shadows and reflections, Fast GI lets light bounce off blocks "
                      "into shadows, jittered shadows turn razor shadow-map edges into penumbrae that widen with "
                      "distance like a real sun, and AgX rolls highlights off the way film does instead of "
                      "clipping - the Medium High Contrast look adds a print-like S-curve.")
        self.log.save()

    def pick_look(self, wanted):
        vs = self.scene.view_settings
        try:
            vs.look = "__probe__"
        except TypeError as err:
            names = re.findall(r"'([^']+)'", str(err).split("not found in", 1)[-1])
        for w in wanted:
            if w in names:
                return w
        return next((n for n in names if "Contrast" in n), "None")

    # ---------------------------------------------------------------- analysis shared by levers
    def analyse(self):
        say("scanning scene geometry ...")
        t = time.time()
        orig_cam = bpy.data.objects.get(json.loads(self.scene["LAB_orig_scene"])["camera"] or "")
        cam_xy = tuple(orig_cam.matrix_world.translation.xy) if orig_cam else None
        self.scan = Scan(self.scene, focus_xy=cam_xy)
        lo, hi = self.scan.lo, self.scan.hi
        roi = [lo[0], lo[1], hi[0], hi[1]]
        if orig_cam is not None:
            o, rays = cam_rays(orig_cam, self.scene, 28, 16)
            pts = [o]
            for _, _, d in rays:
                hit = self.scan.cast(o, d, 400)
                if hit:
                    pts.append(hit[0])
            if len(pts) > 20:
                P = np.array(pts)
                roi = [P[:, 0].min() - 30, P[:, 1].min() - 30, P[:, 0].max() + 30, P[:, 1].max() + 30]
        roi = [max(roi[0], lo[0]), max(roi[1], lo[1]), min(roi[2], hi[0]), min(roi[3], hi[1])]
        self.hm = HeightMap(self.scan, *roi)
        cfg = self.cfg
        if cfg["hero_structure"] and cfg["hero_structure"] in bpy.data.objects:
            ob = bpy.data.objects[cfg["hero_structure"]]
            pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
            P = np.array(pts)
            c = Vector((P[:, 0].mean(), P[:, 1].mean()))
            self.structure = {"center": c, "top": float(P[:, 2].max()), "base": float(P[:, 2].min()),
                              "radius": float(max(np.ptp(P[:, 0]), np.ptp(P[:, 1])) / 2), "name": ob.name}
        else:
            comps = self.hm.structures()
            if comps:
                self.structure = comps[0]
                self.structure["name"] = "auto (height-map prominence)"
            else:
                i, j = np.unravel_index(np.nanargmax(self.hm.hs), self.hm.hs.shape)
                self.structure = {"center": Vector((self.hm.xs[i], self.hm.ys[j])), "top": float(self.hm.hs[i, j]),
                                  "base": float(np.nanmedian(self.hm.hs)), "radius": 4.0, "name": "highest point"}
        st = self.structure
        st["prom"] = st["top"] - st["base"]
        self.subject = self.find_subject()
        say(f"  structure: {st['name']} at ({st['center'].x:.1f}, {st['center'].y:.1f}), "
            f"{st['prom']:.1f} m tall; subject: {self.subject['name']} ({time.time() - t:.1f}s)")

    def find_subject(self):
        want = self.cfg["subject"]
        cands = []
        for o in self.scene.objects:
            if self.is_lab(o) or o.type not in ("MESH", "ARMATURE", "EMPTY") or o.parent is not None:
                continue
            if (want and o.name == want) or (not want and SUBJECT_RE.search(o.name.lower())):
                cands.append(o)
        if cands:
            st = self.structure
            o = min(cands, key=lambda c: (c.matrix_world.translation.xy - st["center"]).length)
            pts = []
            for ob in [o] + list(o.children_recursive):
                if ob.type == "MESH":
                    pts += [ob.matrix_world @ Vector(c) for c in ob.bound_box]
            P = np.array(pts) if pts else np.array([o.matrix_world.translation] * 2)
            lo, hi = P.min(0), P.max(0)
            h = max(0.5, hi[2] - lo[2])
            return {"name": o.name, "obj": o, "base": Vector((lo + hi) / 2) - Vector((0, 0, h / 2)),
                    "height": h, "head": Vector(((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, hi[2] - 0.14 * h)),
                    "names": {o.name} | {c.name for c in o.children_recursive}}
        st = self.structure
        c = st["center"]
        return {"name": f"structure ({st['name']})", "obj": None, "height": st["prom"],
                "base": Vector((c.x, c.y, st["base"])), "head": Vector((c.x, c.y, st["base"] + 0.35 * st["prom"])),
                "names": set()}

    # ---------------------------------------------------------------- LEVER 1: camera / scale
    def lever_camera(self):
        s = self.scene
        coll = lever_coll(s, "camera")
        sec = self.log.section("camera", "LEVER 1 - CAMERA / SCALE")
        subj, st = self.subject, self.structure
        focus = bpy.data.objects.get("FOCUS_target") or bpy.data.objects.new("FOCUS_target", None)
        link(focus, coll)
        focus.empty_display_type = "SPHERE"
        focus.empty_display_size = 0.25
        focus.parent = None
        focus.location = subj["head"]
        if subj["obj"] is not None:  # follow the subject if it moves
            focus.parent = subj["obj"]
            focus.matrix_parent_inverse = subj["obj"].matrix_world.inverted()
        bpy.context.view_layer.update()

        low = make_camera("CAM_Low", coll, self.cfg["cam_low_lens"], self.cfg["cam_low_fstop"], focus)
        low.data.clip_start, low.data.clip_end = 0.05, 1500
        low_info = self.place_low(low)
        long_ = make_camera("CAM_Long", coll, self.cfg["cam_long_lens"], self.cfg["cam_long_fstop"], focus)
        long_.data.clip_start, long_.data.clip_end = 0.5, 4000
        long_info = self.place_long(long_)

        sec["changes"] += [
            f"FOCUS_target empty on {subj['name']} (eye height), parented so focus follows it",
            f"CAM_Low: {low.data.lens:.0f} mm, lens {low_info['lens_h']:.2f} m above ground, "
            f"{low_info['dist']:.1f} m from the structure, tilted up {math.degrees(low_info['pitch']):.0f} deg, "
            f"f/{low.data.dof.aperture_fstop:g}",
            f"CAM_Long: {long_.data.lens:.0f} mm, {long_info['dist']:.1f} m from {subj['name']}, "
            f"background terrain {long_info['bg']:.0f} m behind it, f/{long_.data.dof.aperture_fstop:g}",
            "both cameras: 36 mm sensor, 7-blade iris (heptagonal bokeh), DOF on FOCUS_target",
        ]
        sec["values"] = {"CAM_Low": f"{low.data.lens:.0f} mm f/{low.data.dof.aperture_fstop:g} @ "
                                    f"{(low.location - focus.matrix_world.translation).length:.1f} m focus",
                         "CAM_Long": f"{long_.data.lens:.0f} mm f/{long_.data.dof.aperture_fstop:g} @ "
                                     f"{(long_.location - focus.matrix_world.translation).length:.1f} m focus",
                         "structure": f"{st['prom']:.1f} m tall ({st['name']})"}
        self.log.save()
        a = self.render("01_camera_CAM_Low", low)
        b = self.render("01_camera_CAM_Long", long_)
        hero = bpy.data.objects[self.cfg["hero_camera"]]
        s.camera = hero
        shutil.copyfile(a if hero == low else b, os.path.join(self.render_dir, "01_camera.png"))
        if "01_camera_" + hero.name in self.log.d["renders"]:
            self.log.d["renders"]["01_camera"] = dict(self.log.d["renders"]["01_camera_" + hero.name])
        self.prev_png = os.path.join(self.render_dir, "01_camera.png")
        sec["values"]["hero camera"] = hero.name
        sec["why"] = ("Games shoot everything from one eye-height, wide, everything-in-focus lens. A film camera "
                      "picks a lens for a feeling. The low 24 mm makes the camera small and the world big: blocks "
                      "stop being 1 m cubes and become architecture, verticals converge, foreground blocks go soft "
                      "and huge. The long lens from far away flattens depth so distant terrain stacks up behind "
                      "the subject. Shallow depth of field says 'real glass, real aperture' and tells the eye "
                      "where to look; games almost never defocus.")
        self.log.save()

    def place_low(self, cam):
        """Ground-level lens, structure filling the frame height, subject in the midground.
        Minecraft is axis-aligned, so corner-on (3/4) views get a bonus: two faces, two light values."""
        scan, st, subj = self.scan, self.structure, self.subject
        c, h, r = st["center"], st["prom"], st["radius"]
        hfov, vfov, _, _ = frame_fov(cam.data, self.scene)
        lens_h = 0.45
        mid = Vector((c.x, c.y, st["base"] + 0.5 * h))
        targets = [Vector((c.x, c.y, st["base"] + f * h)) for f in (0.25, 0.6, 0.95)]
        has_subj = subj["obj"] is not None
        chest = subj["base"] + Vector((0, 0, 0.55 * subj["height"]))
        best = None
        for az in range(0, 360, 6):
            a = math.radians(az)
            for k in (1.0, 1.15, 1.3, 1.5, 1.7):
                d = k * h + r
                x, y = c.x + d * math.cos(a), c.y + d * math.sin(a)
                ij = self.hm.ij(x, y)
                if ij is None or self.hm.tcls[ij] in ("clear", "leaves"):
                    continue
                gz = scan.ground(x, y)
                if gz is None:
                    continue
                p = Vector((x, y, gz + lens_h))
                if scan.cast(p, (0, 0, 1), 2.5, "occ") is not None:
                    continue  # under a tree or an overhang
                inward = (Vector((c.x, c.y, 0)) - Vector((x, y, 0))).normalized()
                vis = np.mean([scan.clear(p, t - inward * (r * 0.9), slack=1.5) for t in targets])
                view_az = math.atan2(c.y - y, c.x - x)
                diag = abs(math.sin(2 * view_az))
                score = 2.0 * vis + 0.8 * diag - 0.3 * abs(k - 1.3)
                look = mid
                if has_subj:
                    to_s, to_m = (chest - p), (mid - p)
                    sep = abs(math.atan2(to_s.y, to_s.x) - math.atan2(to_m.y, to_m.x))
                    sep = min(sep, 2 * math.pi - sep)
                    fits = sep < 0.36 * hfov and to_s.length < to_m.length
                    svis = 0.5 * scan.clear(p, subj["head"], skip=subj["names"]) + \
                        0.5 * scan.clear(p, chest, skip=subj["names"])
                    near = math.exp(-((to_s.length - 12.0) / 6.0) ** 2)
                    score += (1.6 * svis + 1.0 * near) * (1.0 if fits else 0.15)
                    look = mid.lerp(chest, 0.5)
                # tall grass or flowers right against the lens hide the frame
                fwd = (look - p).normalized()
                side = fwd.cross(Vector((0, 0, 1))).normalized()
                blocked = sum(1 for sx in (-0.25, 0, 0.25) for sz in (-0.1, 0.05, 0.2)
                              if scan.cast(p, fwd + side * sx + Vector((0, 0, sz)), 1.4, "all") is not None)
                score -= 0.6 * blocked / 9
                if best is None or score > best[0]:
                    best = (score, p, d)
        if best is None:
            raise RuntimeError("could not find open ground for CAM_Low")
        _, p, d = best
        cam.location = p

        def ang(v):
            return math.atan2(v.y - p.y, v.x - p.x)
        yaw = ang(mid)
        if has_subj:  # balance structure and subject either side of centre
            a1, a2 = ang(mid), ang(chest)
            diff = (a2 - a1 + math.pi) % (2 * math.pi) - math.pi
            yaw = a1 + 0.5 * diff
        horiz = max(1.0, (Vector((c.x, c.y)) - p.xy).length - r)
        top_angle = math.atan2(st["top"] - p.z, horiz)
        pitch = clamp(top_angle - 0.78 * vfov / 2, math.radians(4), math.radians(35))
        aim(cam, yaw, pitch)
        if has_subj:  # keep the subject's feet and body in frame
            for _ in range(30):
                u, v, z = project(self.scene, cam, subj["base"] + Vector((0, 0, 0.1)))
                if v > 0.06 or pitch <= math.radians(2):
                    break
                pitch -= math.radians(1)
                aim(cam, yaw, pitch)
        return {"lens_h": lens_h, "dist": d, "pitch": pitch}

    def place_long(self, cam):
        scan, subj, st = self.scan, self.subject, self.structure
        lens = self.cfg["cam_long_lens"]
        cam.data.lens = lens
        _, _, sw, sh = frame_fov(cam.data, self.scene)
        frac = 0.42 if subj["obj"] is not None else 0.62
        D = clamp(subj["height"] / frac * lens / sh, 20.0, 600.0)
        head = subj["head"]
        aimpt = subj["base"] + Vector((0, 0, subj["height"] * (0.58 if subj["obj"] else 0.45)))
        best = None
        for az in range(0, 360, 10):
            a = math.radians(az)
            dirv = Vector((math.cos(a), math.sin(a), 0))
            x, y = head.x - dirv.x * D, head.y - dirv.y * D
            gz = scan.ground(x, y)
            base_z = max(aimpt.z - 0.1 * subj["height"], (gz if gz is not None else -1e9) + 1.7)
            for lift in (0.0, 2.0, 4.0, 7.0):
                p = Vector((x, y, base_z + lift))
                if not (scan.clear(p, head, skip=subj["names"]) and scan.clear(p, aimpt, skip=subj["names"])):
                    continue
                cam.location = p
                d = aimpt - p
                aim(cam, math.atan2(d.y, d.x), math.atan2(d.z, d.xy.length))
                o, rays = cam_rays(cam, self.scene, 9, 5)
                w = []
                for _, _, rd in rays:
                    hit = scan.cast(o, rd, 3000, "occ")
                    if hit is None:
                        w.append(0.0)
                    elif scan.owners[scan.owner[hit[3]]] in subj["names"] or abs(hit[2] - D) < 2.5:
                        continue
                    elif hit[2] < D - 2.5:
                        w.append(-0.6)
                    else:
                        w.append(clamp((hit[2] - D) / 80.0, 0.15, 1.0))
                score = (np.mean(w) if w else -1) - 0.03 * lift
                if best is None or score > best[0]:
                    bg = [clamp(x_, 0, 1) for x_ in w]
                    best = (score, p.copy(), D, float(np.mean(bg) * 80 if bg else 0))
                break
        if best is None:
            raise RuntimeError("no clear line of sight for CAM_Long")
        _, p, D, bg = best
        cam.location = p
        d = aimpt - p
        aim(cam, math.atan2(d.y, d.x), math.atan2(d.z, d.xy.length))
        return {"dist": (head - p).length, "bg": bg}

    # ---------------------------------------------------------------- LEVER 2: sculpted light
    def lever_light(self):
        s = self.scene
        coll = lever_coll(s, "light")
        sec = self.log.section("light", "LEVER 2 - SCULPTED LIGHT")
        hero = s.camera
        golden = self.cfg["hour"] == "golden"
        subj = self.subject
        pick = self.choose_sun(hero, golden)
        to_sun, side, off, elev = pick["to_sun"], pick["side"], pick["off"], pick["elev"]
        self.to_sun = to_sun

        # the new sun (and the old one switched off by the lever)
        sun_color = kelvin_rgb(3100 if golden else 9000)
        sun = bpy.data.objects.new("LAB_Sun", bpy.data.lights.new("LAB_Sun", "SUN"))
        link(sun, coll)
        sun.rotation_euler = (-self.to_sun).to_track_quat("-Z", "Y").to_euler()
        sun.data.energy = 3.2 if golden else 0.12
        sun.data.color = sun_color
        sun.data.angle = math.radians(1.2)
        sun.data.use_shadow_jitter = True
        sun.data.shadow_jitter_overblur = 10.0
        old_suns = [o for o in s.objects if o.type == "LIGHT" and o.data.type == "SUN" and not self.is_lab(o)]
        for o in old_suns:
            switch(o, "hide_render", "light", on=1)
            switch(o, "hide_viewport", "light", on=1)

        # world: negative fill. Scale lighting hard, keep the visible sky brighter.
        k_light, k_cam = (0.2, 0.4) if golden else (0.3, 0.4)
        world_note = self.dim_world(k_light, k_cam)
        sky_note = self.align_sky(elev, self.to_sun)

        # practical: nearest emitter to the subject, warm, soft falloff
        prac, prac_note = self.add_practical(coll, golden)
        rim, rim_note = self.add_rim(coll, golden, side)
        sec["changes"] += [
            f"LAB_Sun: {'golden hour' if golden else 'blue hour'}, {math.degrees(elev):.0f} deg elevation, "
            f"{sun.data.energy:g} W/m2, ~{3100 if golden else 9000} K, angle 1.2 deg, jittered shadows; "
            f"{off} deg off the lens axis to the {'left' if side > 0 else 'right'} "
            f"({'back light' if off < 70 else 'side light' if off <= 100 else 'side light, slightly frontal'}), picked "
            f"by ray tests: subject {pick['subject_lit']:.0%} lit, camera-facing structure faces "
            f"{pick['face_lit']:.0%} lit, {pick['frame_lit']:.0%} of visible surfaces in sun",
            f"original sun(s) {', '.join(o.name for o in old_suns) or '-'} switched off by the lever",
            world_note, sky_note, prac_note, rim_note]
        sec["values"] = {"sun": f"{sun.data.energy} W/m2 @ {math.degrees(elev):.0f} deg",
                         "world lighting": f"x{k_light}", "visible sky": f"x{k_cam}",
                         "practical": f"{prac.data.energy:.0f} W, {'2000' if golden else '2200'} K" if prac else "-",
                         "rim": f"{rim.data.energy:.0f} W, 7000 K" if rim else "-"}
        sec["why"] = ("Golden hour, because the other levers need a hard low source: a sun 7-13 deg up rakes across block "
                      "faces (every bevel and texel edge catches it), throws long shadows that put most of the "
                      "frame in shade, and gives the fog something to cut into shafts. Blue hour would have no "
                      "sun, so bevels, shafts and dust would have nothing to catch. Cutting the sky fill ~80 % is "
                      "negative fill: shadows go deep, so the few lit edges carry the image. The warm practical "
                      "gives the light a reason (someone is here) and the cool rim peels the silhouette off the "
                      "background - key, rim and practical instead of a game's even ambient.")
        self.log.save()
        self.render("02_light", hero)

    def choose_sun(self, hero, golden):
        """Try low suns in front of the lens (35-110 deg off-axis, both sides) and score what this camera
        would actually see lit: the subject, the structure faces facing the lens, and ~a quarter of the
        frame. Every test is a ray against the real geometry, so hills and mountains that swallow a
        low sun are caught."""
        scan, subj, st = self.scan, self.subject, self.structure
        fwd = hero.matrix_world.to_3x3() @ Vector((0, 0, -1))
        yaw = math.atan2(fwd.y, fwd.x)
        cam_p = hero.matrix_world.translation
        c, r, h = st["center"], st["radius"], st["prom"]
        up = Vector((0, 0, 1))
        o, rays = cam_rays(hero, self.scene, 16, 9, (0.03, 0.97), (0.03, 0.97))
        seen = []
        for _, _, d in rays:
            hit = scan.cast(o, d, 3000, "occ")
            if hit:
                n = hit[1] if hit[1].dot(d) < 0 else -hit[1]
                seen.append((hit[0] + n * 0.05, n))
        faces = []
        for n in (Vector((1, 0, 0)), Vector((-1, 0, 0)), Vector((0, 1, 0)), Vector((0, -1, 0))):
            if n.dot(Vector((cam_p.x - c.x, cam_p.y - c.y, 0))) <= 0:
                continue
            t = n.cross(up)
            for fz in (0.2, 0.45, 0.7, 0.92):
                for ft in (-0.5, 0.0, 0.5):
                    faces.append((Vector((c.x, c.y, st["base"] + fz * h)) + n * (r + 0.8) + t * (ft * r), n))
        body = [subj["head"], subj["base"] + Vector((0, 0, 0.55 * subj["height"]))]
        best = None
        for side in (1, -1):
            for off in (35, 50, 65, 80, 95, 110):
                for el in ((7, 10, 13) if golden else (3,)):
                    az, e = yaw + side * math.radians(off), math.radians(el)
                    ts = Vector((math.cos(az) * math.cos(e), math.sin(az) * math.cos(e), math.sin(e)))

                    def lit(pt, n=None):
                        if n is not None and n.dot(ts) <= 0.05:
                            return 0.0
                        return float(scan.clear(pt, pt + ts * 800, slack=0, skip=subj["names"]))
                    s_subj = np.mean([lit(pt) for pt in body])
                    s_face = np.mean([lit(pt, n) for pt, n in faces]) if faces else 0.0
                    frac = np.mean([lit(pt, n) for pt, n in seen]) if seen else 0.0
                    s_frame = 1.0 - min(1.0, abs(frac - 0.25) / 0.25)
                    score = 1.2 * s_subj + 1.5 * s_face + 1.0 * s_frame - 0.03 * (el - 7) - 0.004 * off
                    if best is None or score > best["score"]:
                        best = {"score": score, "to_sun": ts, "side": side, "off": off, "elev": e,
                                "subject_lit": s_subj, "face_lit": s_face, "frame_lit": frac}
        if best["subject_lit"] == 0 and best["face_lit"] == 0:
            self.log.warn("no low sun direction lights the subject or the structure (terrain blocks it); "
                          "kept the best frame coverage - consider hour='blue'")
        return best

    def dim_world(self, k_light, k_cam):
        w = self.scene.world
        if w is None:
            return "no world to dim"
        nt = ensure_nodes(w)
        out = nt.get_output_node("EEVEE") or nt.get_output_node("ALL")
        if out is None or not out.inputs["Surface"].is_linked:
            return "world has no surface shader - left alone"
        src = out.inputs["Surface"].links[0].from_socket
        record_link(w, 2, out.inputs["Surface"], src)
        black = nt.nodes.new("ShaderNodeBackground")
        black.name = "LAB2_Black"
        black.inputs["Color"].default_value = (0, 0, 0, 1)
        lp = nt.nodes.new("ShaderNodeLightPath")
        lp.name = "LAB2_LightPath"
        mix_f = nt.nodes.new("ShaderNodeMix")
        mix_f.name = "LAB2_Scale"
        mix_f.data_type = "FLOAT"
        mixs = nt.nodes.new("ShaderNodeMixShader")
        mixs.name = "LAB2_WorldDim"
        nt.links.new(lp.outputs["Is Camera Ray"], sock(mix_f.inputs, identifier="Factor_Float"))
        nt.links.new(sock(mix_f.outputs, identifier="Result_Float"), mixs.inputs[0])
        nt.links.new(black.outputs[0], mixs.inputs[1])
        nt.links.new(src, mixs.inputs[2])
        nt.links.new(mixs.outputs[0], out.inputs["Surface"])
        a_id = sock(mix_f.inputs, identifier="A_Float")
        b_id = sock(mix_f.inputs, identifier="B_Float")
        for n, x in zip((black, lp, mix_f, mixs), range(4)):
            n.location = out.location + Vector((-220 * (4 - x), -260))
        switch(nt, f'nodes["{mix_f.name}"].inputs[{list(mix_f.inputs).index(a_id)}].default_value', "light",
               on=k_light, off=1.0)
        switch(nt, f'nodes["{mix_f.name}"].inputs[{list(mix_f.inputs).index(b_id)}].default_value', "light",
               on=k_cam, off=1.0)
        return (f"world '{w.name}': lighting scaled x{k_light}, camera-visible sky x{k_cam} "
                "(Light Path mix, original nodes untouched)")

    def align_sky(self, elev, to_sun):
        w = self.scene.world
        if not (w and w.node_tree):
            return "no sky texture"
        notes = []
        for n in w.node_tree.nodes:
            if n.bl_idname != "ShaderNodeTexSky":
                continue
            path = f'nodes["{n.name}"]'
            if n.sky_type in ("PREETHAM", "HOSEK_WILKIE"):
                for i in range(3):
                    switch(w.node_tree, path + ".sun_direction", "light", on=float(to_sun[i]), index=i)
            else:
                switch(w.node_tree, path + ".sun_elevation", "light", on=max(elev, math.radians(0.5)))
                switch(w.node_tree, path + ".sun_rotation", "light", on=math.atan2(to_sun.x, to_sun.y) % (2 * math.pi))
            if getattr(n, "sun_disc", False):
                switch(w.node_tree, path + ".sun_disc", "light", on=0)
            notes.append(n.name)
        return f"sky texture {', '.join(notes)} re-aimed to the LAB sun" if notes else "no sky texture to re-aim"

    def add_practical(self, coll, golden):
        scan, subj, hero = self.scan, self.subject, self.scene.camera
        want = self.cfg["practical_source"]
        emit_mat = np.array([bool(scan.mat_emit.get(m) or EMIT_RE.search(m.lower())) for m in Scan._mats])
        tris = np.nonzero(emit_mat[scan.tmat])[0]
        if want:
            tris = np.array([i for i in tris if want in (scan.owners[scan.owner[i]], Scan._mats[scan.tmat[i]])],
                            dtype=int)
        if len(tris) == 0:
            self.log.warn("no torch/lantern/window emitter found: practical placed beside the subject instead")
            pos, src_name = subj["head"] + Vector((0.6, -0.6, 0.2)), "none (unmotivated)"
        else:
            cent = scan.V[scan.T[tris]].mean(1)
            cells = {}
            for i, cpt in zip(tris, cent):
                cells.setdefault(tuple(np.floor(cpt).astype(int)), []).append(i)

            def rank(ids):
                ctr = Vector(scan.V[scan.T[ids]].reshape(-1, 3).mean(0))
                u, v, z = project(self.scene, hero, ctr)
                return (ctr - subj["head"]).length + (0.0 if (0 < u < 1 and 0 < v < 1 and z > 0) else 6.0)

            ids = min(cells.values(), key=rank)
            pts = scan.V[scan.T[ids]].reshape(-1, 3)
            lo, hi = pts.min(0) - 0.05, pts.max(0) + 0.05
            ctr = Vector(pts.mean(0))
            src_name = f"{scan.owners[scan.owner[ids[0]]]} / {Scan._mats[scan.tmat[ids[0]]]}"
            toward = subj["head"] if (subj["head"] - ctr).length < 6 else hero.matrix_world.translation
            step = (toward - ctr).normalized() if (toward - ctr).length > 0.2 else Vector((0, 0, 1))
            pos = ctr.copy()
            for _ in range(60):  # leave the lamp's own shell, or it would shadow its own light
                if not all(lo[k] <= pos[k] <= hi[k] for k in range(3)):
                    break
                pos += step * 0.02
        dist = max(0.6, (subj["head"] - pos).length)
        e_target = 0.45 * (3.2 if golden else 0.8)
        power = clamp(4 * math.pi * e_target * dist * dist, 8.0, 800.0)
        light = bpy.data.objects.new("LAB_Practical", bpy.data.lights.new("LAB_Practical", "POINT"))
        link(light, coll)
        light.location = pos
        light.data.energy = power
        light.data.color = kelvin_rgb(2000 if golden else 2200)
        light.data.shadow_soft_size = 0.06
        light.data.use_shadow_jitter = True
        return light, (f"LAB_Practical: point light at {src_name}, {power:.0f} W, ~2000 K, 6 cm radius, "
                       f"jittered shadows ({dist:.1f} m from the subject's face)")

    def add_rim(self, coll, golden, sun_side):
        subj, hero = self.subject, self.scene.camera
        cam_p = hero.matrix_world.translation
        c = subj["base"] + Vector((0, 0, subj["height"] * 0.6))
        back = (c - cam_p)
        back.z = 0
        back.normalize()
        side = back.cross(Vector((0, 0, 1))).normalized() * sun_side  # camera-right when the sun is left
        scale = max(1.0, subj["height"] / 1.8)
        pos = c + back * 3.2 * scale + side * 1.8 * scale + Vector((0, 0, 1.4 * scale))
        light = bpy.data.objects.new("LAB_Rim", bpy.data.lights.new("LAB_Rim", "AREA"))
        link(light, coll)
        light.location = pos
        light.rotation_euler = (c - pos).to_track_quat("-Z", "Y").to_euler()
        light.data.shape = "RECTANGLE"
        light.data.size, light.data.size_y = 1.2 * scale, 2.0 * scale
        d = (c - pos).length
        light.data.energy = clamp(2.4 * math.pi * d * d, 20.0, 20000.0)
        light.data.color = kelvin_rgb(7000)
        light.data.use_shadow_jitter = True
        light.data.spread = math.radians(60)
        light.data.volume_factor = 0.0   # a rim is a surface tool: without this it paints a glowing cone in the fog
        return light, (f"LAB_Rim: {light.data.size:.1f} x {light.data.size_y:.1f} m area light behind "
                       f"{subj['name']} on the side away from the sun, {light.data.energy:.0f} W, 7000 K, spread 60 deg, "
                       "no volume scatter")

    # ---------------------------------------------------------------- LEVER 3: physical materials
    def lever_materials(self):
        s = self.scene
        lever_coll(s, "materials")
        sec = self.log.section("materials", "LEVER 3 - PHYSICAL MATERIALS")
        hero = s.camera
        cam_p = hero.matrix_world.translation
        radius = self.cfg["bevel_radius"]
        meshes = [o for o in s.objects if o.type == "MESH" and not self.is_lab(o) and not o.hide_render]
        total = sum(len(o.data.polygons) for o in meshes) or 1
        solid = [o for o in meshes if o.material_slots and any(
            classify_material(sl.material) in ("solid",) for sl in o.material_slots)]
        giant = [o for o in solid if len(o.data.polygons) / total > 0.6 and len(o.data.polygons) > 20000]
        near = []
        for o in solid:
            if o in giant:
                continue
            n = len(o.data.vertices)
            co = np.empty(n * 3, np.float32)
            o.data.vertices.foreach_get("co", co)
            co = co.reshape(-1, 3)[:: max(1, n // 4000)]
            M = np.array(o.matrix_world)
            W = co @ M[:3, :3].T + M[:3, 3]
            dist = np.linalg.norm(W - np.array(cam_p), axis=1)
            W = W[dist < radius]
            if not len(W):
                continue
            inside = False
            for pt in W[:: max(1, len(W) // 300)]:
                u, v, z = project(s, hero, Vector(pt))
                if -0.1 < u < 1.1 and -0.1 < v < 1.1 and z > 0:
                    inside = True
                    break
            if inside:
                near.append(o)
        bevelled, skipped = [], []
        if giant and self.cfg["giant_mesh_policy"] == "stop":
            g = giant[0]
            msg = (f"STOPPED before bevel: '{g.name}' holds {100 * len(g.data.polygons) / total:.0f}% of all faces "
                   f"({len(g.data.polygons):,}) - the world is one giant mesh. Nothing was split. Either split it "
                   "yourself (e.g. by chunk/material) and re-run with stages='materials', or set "
                   "giant_mesh_policy='mask' to bevel only near-camera edges through a Geometry Nodes weight mask "
                   "(still non-destructive, no splitting).")
            self.log.warn(msg)
            sec["notes"].append(msg)
        else:
            st = self.structure
            # an edge mask is stricter than "object touches the radius": always reach past the structure
            mask_r = max(radius, (cam_p.xy - st["center"]).length + st["radius"] + 4.0)
            for o in near + (giant if self.cfg["giant_mesh_policy"] == "mask" else []):
                if any(m.type == "BEVEL" for m in o.modifiers):
                    skipped.append(f"{o.name} (already has a Bevel)")
                    continue
                self.add_bevel(o, masked=o in giant, cam_p=cam_p, radius=mask_r)
                bevelled.append(o.name)
            if giant and self.cfg["giant_mesh_policy"] == "mask":
                sec["notes"].append(f"'{giant[0].name}' is one giant mesh: bevelled through a Geometry Nodes edge "
                                    f"mask (edges > 30 deg within {mask_r:.0f} m of the camera) - nothing was split")
        sec["changes"].append(
            f"Weld (merge by distance 1 mm) + Bevel on {len(bevelled)} near-camera meshes within {radius:.0f} m "
            f"and in frame: {', '.join(bevelled[:12])}{' ...' if len(bevelled) > 12 else ''}")
        if skipped:
            sec["notes"].append("kept existing bevels: " + ", ".join(skipped))
        sec["changes"].append(f"left {len(solid) - len(bevelled)} far/out-of-frame meshes untouched (cheap, and "
                              "a 2.5 cm bevel is sub-pixel there anyway)")
        # textures + shading
        closest_fixed, rough, leaves = self.edit_materials()
        sec["changes"] += [
            f"image textures on Closest: {closest_fixed} switched from smooth filtering, the rest already Closest",
            f"texel-snapped noise roughness on stone/wood: {len(rough)} materials ({', '.join(rough[:8])}"
            f"{' ...' if len(rough) > 8 else ''})",
            f"leaf translucency (Translucent BSDF mix 0.35, yellow-green, alpha-aware): {', '.join(leaves) or 'none'}"]
        sec["values"] = {"weld distance": "0.001 m", "bevel": "0.025 m, 2 segments, angle limit 30 deg, "
                                                              "clamp overlap ON, harden normals ON",
                         "roughness variation": "stone +/-0.16, wood +/-0.12 around the original, per 1/16 m texel",
                         "leaf translucency": "0.35 mix, colour = texture x (1.25, 1.35, 0.55)"}
        sec["why"] = ("Nothing real has a perfect 90 deg edge. A 2.5 cm bevel puts a thin highlight on every "
                      "block corner - the same cue that makes LEGO bricks read as physical objects rather than "
                      "CG. The textures stay nearest-neighbour (that is the style), but roughness now changes "
                      "texel by texel like weathered stone and worn wood, so highlights break up instead of "
                      "sliding across plastic. Leaves transmit light, so backlit canopies glow yellow-green "
                      "the way sunlight through foliage does.")
        self.log.save()
        self.render("03_materials", hero)

    def add_bevel(self, o, masked, cam_p, radius):
        existing_weld = any(m.type == "WELD" for m in o.modifiers)
        mods = []
        if not existing_weld:
            w = o.modifiers.new("LAB3_Weld", "WELD")
            w.merge_threshold = 0.001
            w.mode = "ALL"
            mods.append(w)
        if masked:
            gn = o.modifiers.new("LAB3_BevelMask", "NODES")
            gn.node_group = self.bevel_mask_group(cam_p, radius)
            mods.append(gn)
        b = o.modifiers.new("LAB3_Bevel", "BEVEL")
        b.width = 0.025
        b.segments = 2
        b.limit_method = "WEIGHT" if masked else "ANGLE"
        b.angle_limit = math.radians(30)
        b.use_clamp_overlap = True
        b.harden_normals = True
        b.miter_outer = "MITER_ARC"
        mods.append(b)
        for i, m in enumerate(mods):  # before any deform (wind etc.) so the bevel sees clean blocks
            o.modifiers.move(len(o.modifiers) - 1 - (len(mods) - 1 - i), i)
        for m in mods:
            switch(o, f'modifiers["{m.name}"].show_render', "materials", on=1, off=0)
            switch(o, f'modifiers["{m.name}"].show_viewport', "materials", on=1, off=0)

    def bevel_mask_group(self, cam_p, radius):
        ng = bpy.data.node_groups.get("LAB3_BevelMaskGN")
        if ng:
            return ng
        ng = bpy.data.node_groups.new("LAB3_BevelMaskGN", "GeometryNodeTree")
        ng.interface.new_socket("Geometry", in_out="INPUT", socket_type="NodeSocketGeometry")
        ng.interface.new_socket("Geometry", in_out="OUTPUT", socket_type="NodeSocketGeometry")
        N = ng.nodes
        gi, go = N.new("NodeGroupInput"), N.new("NodeGroupOutput")
        ang = N.new("GeometryNodeInputMeshEdgeAngle")
        gt = N.new("FunctionNodeCompare")
        gt.data_type, gt.operation = "FLOAT", "GREATER_THAN"
        sock(gt.inputs, "B", kind="VALUE").default_value = math.radians(30)
        pos = N.new("GeometryNodeInputPosition")
        dist = N.new("ShaderNodeVectorMath")
        dist.operation = "DISTANCE"
        dist.inputs[1].default_value = cam_p
        lt = N.new("FunctionNodeCompare")
        lt.data_type, lt.operation = "FLOAT", "LESS_THAN"
        sock(lt.inputs, "B", kind="VALUE").default_value = radius
        both = N.new("FunctionNodeBooleanMath")
        both.operation = "AND"
        store = N.new("GeometryNodeStoreNamedAttribute")
        store.data_type, store.domain = "FLOAT", "EDGE"
        store.inputs["Name"].default_value = "bevel_weight_edge"
        L = ng.links.new
        L(ang.outputs["Unsigned Angle"], sock(gt.inputs, "A", kind="VALUE"))
        L(pos.outputs[0], dist.inputs[0])
        L(dist.outputs["Value"], sock(lt.inputs, "A", kind="VALUE"))
        L(gt.outputs[0], both.inputs[0])
        L(lt.outputs[0], both.inputs[1])
        L(gi.outputs[0], store.inputs["Geometry"])
        L(both.outputs[0], sock(store.inputs, "Value", kind="VALUE"))
        L(store.outputs[0], go.inputs[0])
        return ng

    def edit_materials(self):
        closest_fixed, rough, leaves = 0, [], []
        used = {sl.material for o in self.scene.objects if not self.is_lab(o) for sl in o.material_slots if sl.material}
        for mat in sorted(used, key=lambda m: m.name):
            if not mat.node_tree or mat.name.startswith("LAB"):
                continue
            nt = mat.node_tree
            for n in nt.nodes:
                if n.bl_idname == "ShaderNodeTexImage" and n.image and max(n.image.size) <= 512 \
                        and n.interpolation != "Closest":
                    n.interpolation = "Closest"
                    closest_fixed += 1
            p = principled_of(mat)
            if p is None:
                continue
            text = mat_text(mat)
            cls = classify_material(mat)
            if cls == "leaves":
                if self.leaf_translucency(mat, p):
                    leaves.append(mat.name)
            elif cls == "solid" and (STONE_RE.search(text) or WOOD_RE.search(text)):
                amp = 0.16 if STONE_RE.search(text) else 0.12
                self.roughness_noise(mat, p, amp)
                rough.append(mat.name)
        return closest_fixed, rough, leaves

    def roughness_noise(self, mat, p, amp):
        nt = mat.node_tree
        L = nt.links.new
        rin = p.inputs["Roughness"]
        src = rin.links[0].from_socket if rin.is_linked else None
        record_link(mat, 3, rin, src)
        x0 = p.location.x - 900
        tc = nt.nodes.new("ShaderNodeTexCoord")
        snap = nt.nodes.new("ShaderNodeVectorMath")
        snap.operation = "SNAP"
        snap.inputs[1].default_value = (1 / 16, 1 / 16, 1 / 16)
        noise = nt.nodes.new("ShaderNodeTexNoise")
        noise.inputs["Scale"].default_value = 0.9
        noise.inputs["Detail"].default_value = 4.0
        noise.inputs["Roughness"].default_value = 0.62
        center = nt.nodes.new("ShaderNodeMath")
        center.operation = "SUBTRACT"
        center.inputs[1].default_value = 0.5
        gain = nt.nodes.new("ShaderNodeMath")
        gain.operation = "MULTIPLY"
        base = nt.nodes.new("ShaderNodeValue")
        add = nt.nodes.new("ShaderNodeMath")
        add.operation = "ADD"
        add.use_clamp = True
        for i, (n, nm) in enumerate(((tc, "Coords"), (snap, "TexelSnap"), (noise, "RoughNoise"),
                                     (center, "Center"), (gain, "Amount"), (base, "BaseRough"), (add, "Roughness"))):
            n.name, n.label = f"LAB3_{nm}", f"LAB {nm}"
            n.location = (x0 + 150 * i, p.location.y - 420)
        L(tc.outputs["Object"], snap.inputs[0])
        L(snap.outputs[0], noise.inputs["Vector"])
        L(noise.outputs[0], center.inputs[0])
        L(center.outputs[0], gain.inputs[0])
        L(gain.outputs[0], add.inputs[1])
        if src is not None:
            L(src, add.inputs[0])
            nt.nodes.remove(base)
        else:
            base.outputs[0].default_value = rin.default_value
            L(base.outputs[0], add.inputs[0])
        L(add.outputs[0], rin)
        switch(nt, f'nodes["{gain.name}"].inputs[1].default_value', "materials", on=2 * amp, off=0.0)

    def leaf_translucency(self, mat, p):
        nt = mat.node_tree
        out = nt.get_output_node("EEVEE") or nt.get_output_node("ALL")
        if out is None or not out.inputs["Surface"].is_linked:
            return False
        L = nt.links.new
        surf = out.inputs["Surface"].links[0].from_socket
        record_link(mat, 3, out.inputs["Surface"], surf)
        col = p.inputs["Base Color"].links[0].from_socket if p.inputs["Base Color"].is_linked else None
        alpha = p.inputs["Alpha"].links[0].from_socket if p.inputs["Alpha"].is_linked else None
        tint = nt.nodes.new("ShaderNodeMix")
        tint.data_type, tint.blend_type = "RGBA", "MULTIPLY"
        sock(tint.inputs, identifier="Factor_Float").default_value = 1.0
        sock(tint.inputs, identifier="B_Color").default_value = (1.25, 1.35, 0.55, 1)
        if col is not None:
            L(col, sock(tint.inputs, identifier="A_Color"))
        else:
            sock(tint.inputs, identifier="A_Color").default_value = p.inputs["Base Color"].default_value
        trl = nt.nodes.new("ShaderNodeBsdfTranslucent")
        L(sock(tint.outputs, identifier="Result_Color"), trl.inputs["Color"])
        cut = nt.nodes.new("ShaderNodeMixShader")
        clear = nt.nodes.new("ShaderNodeBsdfTransparent")
        if alpha is not None:
            L(alpha, cut.inputs[0])
        else:
            cut.inputs[0].default_value = 1.0
        L(clear.outputs[0], cut.inputs[1])
        L(trl.outputs[0], cut.inputs[2])
        mix = nt.nodes.new("ShaderNodeMixShader")
        L(surf, mix.inputs[1])
        L(cut.outputs[0], mix.inputs[2])
        L(mix.outputs[0], out.inputs["Surface"])
        for i, (n, nm) in enumerate(((tint, "LeafTint"), (trl, "Translucent"), (clear, "Cutout"),
                                     (cut, "AlphaCut"), (mix, "LeafMix"))):
            n.name, n.label = f"LAB3_{nm}", f"LAB {nm}"
            n.location = out.location + Vector((-900 + 170 * i, -300))
        if hasattr(mat, "thickness_mode"):
            mat.thickness_mode = "SLAB"   # thin sheets, not solid blobs
        switch(nt, f'nodes["{mix.name}"].inputs[0].default_value', "materials", on=0.35, off=0.0)
        return True

    # ---------------------------------------------------------------- LEVER 4: air
    def lever_air(self):
        s = self.scene
        coll = lever_coll(s, "air")
        sec = self.log.section("air", "LEVER 4 - AIR")
        hero = s.camera
        e = s.eevee
        focus = bpy.data.objects.get("FOCUS_target")
        fdist = (hero.matrix_world.translation - focus.matrix_world.translation).length if focus else 20.0
        # --- where is the low ground in view?
        o, rays = cam_rays(hero, s, 24, 14)
        far = clamp(max(60.0, 2.5 * fdist), 60.0, 260.0)
        pts = [o]
        for _, _, d in rays:
            hit = self.scan.cast(o, d, far)
            pts.append(hit[0] if hit else o + d * far)
        P = np.array(pts)
        grounds = [self.scan.ground(x, y) for x, y in P[:, :2][:: max(1, len(P) // 200)]]
        grounds = [g for g in grounds if g is not None]
        z0 = float(np.percentile(grounds, 8)) if grounds else float(o.z - 2)
        depth = self.cfg.get("fog_depth") or clamp(0.26 * self.structure["prom"], 3.0, 10.0)
        xmin, ymin = P[:, 0].min() - 15, P[:, 1].min() - 15
        xmax, ymax = P[:, 0].max() + 15, P[:, 1].max() + 15
        zmin, zmax = z0 - 2.0, z0 + depth * 2.2
        fog = self.make_fog((xmin, ymin, zmin), (xmax, ymax, zmax), z0, depth, coll)
        # --- EEVEE volumes: shadows on (light shafts), enough range, finer froxels
        e.use_volumetric_shadows = True   # render setting, not animatable: stays on (it only affects volumes)
        e.volumetric_shadow_samples = max(e.volumetric_shadow_samples, 24)
        old_tile, old_end = e.volumetric_tile_size, e.volumetric_end
        e.volumetric_tile_size = "4" if int(e.volumetric_tile_size) > 4 else e.volumetric_tile_size
        need_end = float(np.max(np.linalg.norm(P - np.array(o), axis=1))) + 20
        e.volumetric_end = max(e.volumetric_end, need_end)
        e.volumetric_samples = max(e.volumetric_samples, 96)
        e.volumetric_sample_distribution = 0.85
        sun = bpy.data.objects.get("LAB_Sun")
        if sun:
            switch(sun.data, "volume_factor", "air", on=1.6, off=sun.data.volume_factor)
        # --- dust in the brightest shaft
        shaft, how = self.find_shaft(hero, fdist)
        dust = self.make_dust(shaft, fdist, coll)
        wind = self.find_wind()
        sec["changes"] += [
            f"LAB_GroundFog: volume box {xmax - xmin:.0f} x {ymax - ymin:.0f} m over the view, pooled from "
            f"z={z0:.1f} m (lowest 8 % of ground in frame), exponential falloff over ~{depth:.1f} m x "
            "3D noise (slowly evolving), forward-scattering (anisotropy 0.6)",
            f"EEVEE volumes: volume shadows ON ({e.volumetric_shadow_samples} samples; a scene setting that "
            "cannot be driven, so it stays on when the lever is off - it only changes volumes), tile "
            f"{old_tile}->{e.volumetric_tile_size} px, {e.volumetric_samples} steps, end {old_end:.0f}->"
            f"{e.volumetric_end:.0f} m; LAB_Sun volume contribution x1.6 so shafts read",
            f"LAB_Dust: {dust['count']} emissive motes (Geometry Nodes points -> instanced icospheres, "
            f"3-8 mm, shrinking to nothing toward the rim so the cloud has no edge) drifting in a "
            f"{dust['size'][0]:.1f} x {dust['size'][1]:.1f} x {dust['size'][2]:.1f} m volume aligned to the sun "
            f"beam {how}",
            "existing fog/wind kept: " + (", ".join(self.find_fog() + wind) or "none found")
            + (" - dust drifts along the existing wind field" if any("field" in w for w in wind) else "")]
        sec["values"] = {"fog density at floor": f"{fog['d0']} /m", "fog depth": f"{depth:.1f} m",
                         "noise scale": "0.09 /m (11 m billows)", "dust": f"{dust['count']} motes, emission "
                                                                       f"{dust['emit']}", "volume shadows": "ON"}
        sec["why"] = ("Air is the difference between a render and a place. Fog that pools in the low ground "
                      "(dense at the bottom, torn up by noise, not a flat grey wash) layers the depth - far "
                      "things go pale, valleys fill with light - and with volume shadows the blocks cut the "
                      "low sun into visible shafts. Dust in the brightest shaft makes the light itself a "
                      "thing you can see, and its slow drift says the air is real and moving.")
        self.log.save()
        self.render("04_air", hero)

    def make_fog(self, lo, hi, z0, depth, coll):
        me = bpy.data.meshes.new("LAB_GroundFog")
        (x0, y0, zb), (x1, y1, zt) = lo, hi
        me.from_pydata([(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (zb, zt)], [],
                       [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)])
        ob = bpy.data.objects.new("LAB_GroundFog", me)
        link(ob, coll)
        ob.display_type = "BOUNDS"
        mat = bpy.data.materials.new("LAB4_FogMat")
        nt = ensure_nodes(mat)
        nt.nodes.remove(nt.nodes["Principled BSDF"])
        out = nt.nodes["Material Output"]
        N, L = nt.nodes, nt.links.new
        tc = N.new("ShaderNodeTexCoord")
        sep = N.new("ShaderNodeSeparateXYZ")
        sub = N.new("ShaderNodeMath")
        sub.operation = "SUBTRACT"
        sub.inputs[1].default_value = z0
        k = depth / 2.3
        dv = N.new("ShaderNodeMath")
        dv.operation = "MULTIPLY"
        dv.inputs[1].default_value = -1.0 / k
        ex = N.new("ShaderNodeMath")
        ex.operation = "EXPONENT"
        ex.use_clamp = True                               # below the floor it would explode; clamp to 1
        top = N.new("ShaderNodeMapRange")
        top.inputs["From Min"].default_value = depth * 1.2
        top.inputs["From Max"].default_value = depth * 2.1
        top.inputs["To Min"].default_value = 1.0
        top.inputs["To Max"].default_value = 0.0
        top.interpolation_type = "SMOOTHSTEP"
        noise = N.new("ShaderNodeTexNoise")
        noise.noise_dimensions = "4D"
        noise.inputs["Scale"].default_value = 0.09
        noise.inputs["Detail"].default_value = 3.0
        noise.inputs["Roughness"].default_value = 0.55
        nr = N.new("ShaderNodeMapRange")
        nr.inputs["From Min"].default_value = 0.36
        nr.inputs["From Max"].default_value = 0.70
        nr.inputs["To Min"].default_value = 0.0
        nr.inputs["To Max"].default_value = 1.7
        g = N.new("ShaderNodeMath")
        g.operation = "MULTIPLY"
        g2 = N.new("ShaderNodeMath")
        g2.operation = "MULTIPLY"
        d0 = 0.07
        dens = N.new("ShaderNodeMath")
        dens.operation = "MULTIPLY"
        dens.inputs[1].default_value = d0
        vol = N.new("ShaderNodeVolumePrincipled")
        vol.inputs["Color"].default_value = (0.93, 0.95, 1.0, 1)
        vol.inputs["Anisotropy"].default_value = 0.6
        L(tc.outputs["Object"], sep.inputs[0])
        L(tc.outputs["Object"], noise.inputs["Vector"])
        L(sep.outputs["Z"], sub.inputs[0])
        L(sub.outputs[0], dv.inputs[0])
        L(dv.outputs[0], ex.inputs[0])
        L(sub.outputs[0], top.inputs["Value"])
        L(noise.outputs[0], nr.inputs["Value"])
        L(ex.outputs[0], g.inputs[0])
        L(top.outputs["Result"], g.inputs[1])
        L(g.outputs[0], g2.inputs[0])
        L(nr.outputs["Result"], g2.inputs[1])
        L(g2.outputs[0], dens.inputs[0])
        L(dens.outputs[0], vol.inputs["Density"])
        L(vol.outputs[0], out.inputs["Volume"])
        for i, n in enumerate((tc, sep, sub, dv, ex, top, noise, nr, g, g2, dens, vol)):
            n.location = (-1900 + 160 * i, 0 if i % 2 else -220)
        fc = nt.driver_add(f'nodes["{noise.name}"].inputs["W"].default_value')
        fc.driver.type = "SCRIPTED"
        fc.driver.expression = "frame / 400"            # the fog breathes slowly over the shot
        ob.data.materials.append(mat)
        return {"d0": d0}

    def sun_dir(self):
        if getattr(self, "to_sun", None) is not None:
            return self.to_sun
        sun = bpy.data.objects.get("LAB_Sun")
        return (sun.matrix_world.to_3x3() @ Vector((0, 0, 1))).normalized() if sun else None

    def find_shaft(self, hero, fdist):
        s, scan = self.scene, self.scan
        to_sun = self.sun_dir()
        o, rays = cam_rays(hero, s, 18, 11, (0.08, 0.92), (0.1, 0.9))
        depths = [fdist * f for f in (0.35, 0.55, 0.75, 0.95, 1.15)]
        grid = {}
        bg_lit = {}
        if to_sun is not None and to_sun.z > 0:
            for (u, v, d) in rays:
                hit = scan.cast(o, d, fdist * 1.3)
                behind = scan.cast(o, d, 3000, "occ")      # what the mote would be seen against
                if behind is not None:
                    n = behind[1] if behind[1].dot(d) < 0 else -behind[1]
                    p_bg = behind[0] + n * 0.05
                    bg_lit[(round(u, 3), round(v, 3))] = n.dot(to_sun) > 0.05 and scan.clear(
                        p_bg, p_bg + to_sun * 800, slack=0)
                else:
                    bg_lit[(round(u, 3), round(v, 3))] = True    # bright sky behind
                for di, depth in enumerate(depths):
                    if hit and hit[2] < depth:
                        continue
                    pt = o + d * depth
                    g = scan.ground(pt.x, pt.y)
                    if g is not None and pt.z < g + 0.3:
                        continue
                    grid[(round(u, 3), round(v, 3), di)] = (pt, scan.clear(pt, pt + to_sun * 300, slack=0))
        best = None
        us = sorted({k[0] for k in grid})
        vs_ = sorted({k[1] for k in grid})
        for (u, v, di), (pt, lit) in grid.items():
            if not lit:
                continue
            iu, iv = us.index(u), vs_.index(v)
            nb = [grid.get((us[a], vs_[b], di)) for a in (iu - 1, iu, iu + 1) for b in (iv - 1, iv, iv + 1)
                  if 0 <= a < len(us) and 0 <= b < len(vs_) and (a, b) != (iu, iv)]
            nb = [n for n in nb if n]
            edge = sum(1 for n in nb if not n[1]) / max(1, len(nb))
            centr = 1 - 0.8 * math.hypot(u - 0.5, v - 0.5)
            foc = 1 / (1 + abs(depths[di] - fdist) / fdist)
            dark_bg = 0.3 if bg_lit.get((u, v), True) else 1.0   # motes only read against shadow
            score = (0.35 + edge) * centr * foc * dark_bg
            if best is None or score > best[0]:
                best = (score, pt)
        if best is not None:
            return best[1], "(sun-lit air in front of shadow, near the focus plane)"
        prac = bpy.data.objects.get("LAB_Practical")
        if prac:
            return prac.location + Vector((0, 0, 0.4)), "(no sun shaft in view: around the practical)"
        return self.subject["head"] + Vector((0, 0, 1)), "(fallback: above the subject)"

    def make_dust(self, center, fdist, coll):
        size = max(1.5, 0.12 * fdist)
        box = (size, size, size * 4.0)
        count = 240
        ng = bpy.data.node_groups.new("LAB4_DustGN", "GeometryNodeTree")
        itf = ng.interface
        itf.new_socket("Geometry", in_out="INPUT", socket_type="NodeSocketGeometry")
        s_count = itf.new_socket("Count", in_out="INPUT", socket_type="NodeSocketInt")
        s_size = itf.new_socket("Box Size", in_out="INPUT", socket_type="NodeSocketVector")
        s_seed = itf.new_socket("Seed", in_out="INPUT", socket_type="NodeSocketInt")
        s_drift = itf.new_socket("Drift (m/s)", in_out="INPUT", socket_type="NodeSocketVector")
        itf.new_socket("Geometry", in_out="OUTPUT", socket_type="NodeSocketGeometry")
        N, L = ng.nodes, ng.links.new
        gi, go = N.new("NodeGroupInput"), N.new("NodeGroupOutput")
        idx = N.new("GeometryNodeInputIndex")
        rnd = N.new("FunctionNodeRandomValue")
        rnd.data_type = "FLOAT_VECTOR"
        sock(rnd.inputs, "Min", kind="VECTOR").default_value = (-0.5, -0.5, -0.5)
        sock(rnd.inputs, "Max", kind="VECTOR").default_value = (0.5, 0.5, 0.5)
        time_ = N.new("GeometryNodeInputSceneTime")
        drift = N.new("ShaderNodeVectorMath")
        drift.operation = "SCALE"
        rel = N.new("ShaderNodeVectorMath")
        rel.operation = "DIVIDE"
        wob = N.new("ShaderNodeTexNoise")
        wob.noise_dimensions = "4D"
        wob.inputs["Scale"].default_value = 1.3
        wscale = N.new("ShaderNodeMath")
        wscale.operation = "MULTIPLY"
        wscale.inputs[1].default_value = 0.08
        wc = N.new("ShaderNodeVectorMath")
        wc.operation = "SUBTRACT"
        wc.inputs[1].default_value = (0.5, 0.5, 0.5)
        wamp = N.new("ShaderNodeVectorMath")
        wamp.operation = "SCALE"
        wamp.inputs["Scale"].default_value = 0.35
        add1 = N.new("ShaderNodeVectorMath")
        add1.operation = "ADD"
        add2 = N.new("ShaderNodeVectorMath")
        add2.operation = "ADD"
        wrap_ = N.new("ShaderNodeVectorMath")
        wrap_.operation = "WRAP"
        wrap_.inputs[1].default_value = (0.5, 0.5, 0.5)
        wrap_.inputs[2].default_value = (-0.5, -0.5, -0.5)
        mul = N.new("ShaderNodeVectorMath")
        mul.operation = "MULTIPLY"
        pts = N.new("GeometryNodePoints")
        sphere = N.new("GeometryNodeMeshIcoSphere")
        sphere.inputs["Radius"].default_value = 1.0
        sphere.inputs["Subdivisions"].default_value = 1
        rsize = N.new("FunctionNodeRandomValue")
        rsize.data_type = "FLOAT"
        sock(rsize.inputs, "Min", kind="VALUE").default_value = 0.003
        sock(rsize.inputs, "Max", kind="VALUE").default_value = 0.008
        seed2 = N.new("ShaderNodeMath")
        seed2.operation = "ADD"
        seed2.inputs[1].default_value = 7
        # soft edges: motes shrink to nothing toward the cloud's rim, so it has no visible box
        nrm = N.new("ShaderNodeVectorMath")
        nrm.operation = "SCALE"
        nrm.inputs["Scale"].default_value = 2.0
        rad = N.new("ShaderNodeVectorMath")
        rad.operation = "LENGTH"
        fall = N.new("ShaderNodeMapRange")
        fall.interpolation_type = "SMOOTHSTEP"
        fall.clamp = True
        fall.inputs["From Min"].default_value = 0.35
        fall.inputs["From Max"].default_value = 1.0
        fall.inputs["To Min"].default_value = 1.0
        fall.inputs["To Max"].default_value = 0.0
        fscale = N.new("ShaderNodeMath")
        fscale.operation = "MULTIPLY"
        inst = N.new("GeometryNodeInstanceOnPoints")
        setm = N.new("GeometryNodeSetMaterial")
        mat = bpy.data.materials.new("LAB4_DustMat")
        nt = ensure_nodes(mat)
        nt.nodes.remove(nt.nodes["Principled BSDF"])
        em = nt.nodes.new("ShaderNodeEmission")
        em.inputs["Color"].default_value = (*kelvin_rgb(4200), 1)
        emit = 10.0
        em.inputs["Strength"].default_value = emit
        nt.links.new(em.outputs[0], nt.nodes["Material Output"].inputs["Surface"])
        setm.inputs["Material"].default_value = mat
        L(idx.outputs[0], sock(rnd.inputs, "ID"))
        L(gi.outputs[s_seed.name], sock(rnd.inputs, "Seed"))
        L(time_.outputs["Seconds"], sock(drift.inputs, "Scale"))
        L(gi.outputs[s_drift.name], drift.inputs[0])
        L(drift.outputs[0], rel.inputs[0])
        L(gi.outputs[s_size.name], rel.inputs[1])
        L(sock(rnd.outputs, kind="VECTOR"), wob.inputs["Vector"])
        L(time_.outputs["Seconds"], wscale.inputs[0])
        L(wscale.outputs[0], wob.inputs["W"])
        L(wob.outputs["Color"], wc.inputs[0])
        L(wc.outputs[0], wamp.inputs[0])
        L(sock(rnd.outputs, kind="VECTOR"), add1.inputs[0])
        L(rel.outputs[0], add1.inputs[1])
        L(add1.outputs[0], add2.inputs[0])
        L(wamp.outputs[0], add2.inputs[1])
        L(add2.outputs[0], wrap_.inputs[0])
        L(wrap_.outputs[0], mul.inputs[0])
        L(gi.outputs[s_size.name], mul.inputs[1])
        L(gi.outputs[s_count.name], pts.inputs["Count"])
        L(mul.outputs[0], pts.inputs["Position"])
        L(pts.outputs[0], inst.inputs["Points"])
        L(sphere.outputs["Mesh"], inst.inputs["Instance"])
        L(idx.outputs[0], sock(rsize.inputs, "ID"))
        L(gi.outputs[s_seed.name], seed2.inputs[0])
        L(seed2.outputs[0], sock(rsize.inputs, "Seed"))
        L(wrap_.outputs[0], nrm.inputs[0])
        L(nrm.outputs[0], rad.inputs[0])
        L(rad.outputs["Value"], fall.inputs["Value"])
        L(sock(rsize.outputs, kind="VALUE"), fscale.inputs[0])
        L(fall.outputs["Result"], fscale.inputs[1])
        L(fscale.outputs[0], inst.inputs["Scale"])
        L(inst.outputs[0], setm.inputs["Geometry"])
        L(setm.outputs[0], go.inputs[0])
        for i, n in enumerate(N):
            n.location = (180 * (i % 9), -200 * (i // 9))
        me = bpy.data.meshes.new("LAB_Dust")
        ob = bpy.data.objects.new("LAB_Dust", me)
        link(ob, coll)
        ob.location = center
        beam = self.sun_dir() or Vector((0, 0, 1))
        ob.rotation_euler = beam.to_track_quat("Z", "Y").to_euler()
        bpy.context.view_layer.update()
        wind = ob.matrix_world.to_3x3().inverted() @ (self.wind_vector() * 0.045)
        wind.z -= 0.006                                   # dust settles, slowly
        values = ((s_count, count), (s_size, box), (s_seed, 11), (s_drift, tuple(wind)))
        for s_, val in values:
            s_.default_value = val                        # defaults first: new modifiers copy them
        mod = ob.modifiers.new("LAB4_Dust", "NODES")
        mod.node_group = ng
        for s_, val in values:
            set_gn_input(mod, s_.identifier, val)
        if hasattr(ob, "visible_shadow"):
            ob.visible_shadow = False
        return {"count": count, "size": box, "emit": emit}

    # ---------------------------------------------------------------- LEVER 5: camera imperfection
    def lever_lens(self):
        s = self.scene
        lever_coll(s, "lens")
        sec = self.log.section("lens", "LEVER 5 - CAMERA IMPERFECTION (compositor)")
        tree = comp_tree(s, create=True)
        s.render.use_compositing = True
        out, out_s = comp_output(tree)
        if not out_s.is_linked:
            rl = next((n for n in tree.nodes if n.bl_idname == "CompositorNodeRLayers"), None) \
                or tree.nodes.new("CompositorNodeRLayers")
            tree.links.new(rl.outputs["Image"], out_s)
        src = out_s.links[0].from_socket
        existing = [n for n in tree.nodes if not n.name.startswith("LAB")]
        has_bloom = any(n.bl_idname == "CompositorNodeGlare" and self.glare_kind(n) in ("BLOOM", "FOG_GLOW")
                        for n in existing)
        record_link(s, 5, out_s, src)
        group = self.lens_group(skip_bloom=has_bloom)
        gn = tree.nodes.new("CompositorNodeGroup")
        gn.node_tree = group
        gn.name, gn.label = "LAB5_Lens", "LAB Lens"
        gn.location = out.location + Vector((-220, 0))
        out.location += Vector((160, 0))
        tree.links.new(src, gn.inputs[0])
        tree.links.new(gn.outputs[0], out_s)
        kept = [n.label or n.name for n in existing if n.bl_idname not in (
            "NodeGroupOutput", "CompositorNodeComposite", "CompositorNodeRLayers", "CompositorNodeViewer")]
        sec["changes"] += [
            "LAB5_Lens node group inserted just before the output; your nodes stay upstream: "
            + (", ".join(kept) or "none"),
            "bloom: " + ("kept your existing glare, no second bloom added" if has_bloom else
                         "Glare (Bloom), threshold 1.0, strength 0.12, size 0.62, high quality"),
            "halation: luminance 0.9-3.5 key -> blur 0.55 % of width -> tinted (1.0, 0.24, 0.07) -> added at 0.16",
            "chromatic aberration: Lens Distortion dispersion 0.012, distortion 0",
            "vignette: radial (aspect-corrected), corners x0.80, smoothstep from 45 % of the radius",
            "grain: per-pixel white noise, multiplicative +/-4 %, new pattern every frame",
            "a Switch inside the group bypasses all of it when LAB_05_Lens is disabled in renders"]
        sec["values"] = {"bloom strength": "0.12", "halation strength": "0.16", "dispersion": "0.012",
                         "vignette": "0.80 at corners", "grain": "+/-4 % (multiplicative)"}
        sec["why"] = ("Every real camera leaves fingerprints and games leave none, so their absence reads as "
                      "synthetic. Highlights bleed into the lens (bloom); bright edges pick up a red halo from "
                      "light bouncing off the film base (halation); glass splits colours toward the frame edge "
                      "(chromatic aberration); corners fall off (vignette); the image has grain. Each is set "
                      "below the threshold of noticing - you should not see an effect, you should just believe "
                      "a camera was there.")
        self.log.save()
        self.render("05_lens", s.camera)

    def glare_kind(self, n):
        if "Type" in n.inputs and n.inputs["Type"].type == "MENU":
            return n.inputs["Type"].default_value.upper().replace(" ", "_")
        return getattr(n, "glare_type", "")

    def lens_group(self, skip_bloom):
        g = bpy.data.node_groups.new("LAB5_LensGroup", "CompositorNodeTree")
        g.interface.new_socket("Image", in_out="INPUT", socket_type="NodeSocketColor")
        g.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
        N, L = g.nodes, g.links.new
        gi, go = N.new("NodeGroupInput"), N.new("NodeGroupOutput")
        x = [0]

        def put(n, label):
            n.label = label
            n.location = (x[0], 0 if len(N) % 2 else -200)
            x[0] += 190
            return n

        def mix(blend, a, b, fac=1.0):
            m = put(N.new("ShaderNodeMix"), blend.title())
            m.data_type, m.blend_type = "RGBA", blend
            m.clamp_result = False
            sock(m.inputs, identifier="Factor_Float").default_value = fac
            for sid, val in (("A_Color", a), ("B_Color", b)):
                if isinstance(val, tuple):
                    sock(m.inputs, identifier=sid).default_value = val
                else:
                    L(val, sock(m.inputs, identifier=sid))
            return sock(m.outputs, identifier="Result_Color")

        def maprange(v, a, b, c, d, smooth=False):
            n = put(N.new("ShaderNodeMapRange"), "Map Range")
            n.clamp = True
            if smooth:
                n.interpolation_type = "SMOOTHSTEP"
            for nm, val in (("From Min", a), ("From Max", b), ("To Min", c), ("To Max", d)):
                n.inputs[nm].default_value = val
            L(v, n.inputs["Value"])
            return n.outputs["Result"]

        def rel_blur(img, frac, label):
            r2p = put(N.new("CompositorNodeRelativeToPixel"), "Relative to Pixel")
            r2p.data_type = "VECTOR"
            r2p.reference_dimension = "X"
            sock(r2p.inputs, "Value", kind="VECTOR").default_value = (frac, frac)
            L(img, r2p.inputs["Image"])
            bl = put(N.new("CompositorNodeBlur"), label)
            set_menu(bl, "Type", "filter_type", "Gaussian", "GAUSS")
            L(img, bl.inputs["Image"])
            L(sock(r2p.outputs, kind="VECTOR"), bl.inputs["Size"])
            return bl.outputs[0]

        img = gi.outputs[0]
        if not skip_bloom:
            gl = put(N.new("CompositorNodeGlare"), "Bloom")
            set_menu(gl, "Type", "glare_type", "Bloom", "BLOOM")
            set_menu(gl, "Quality", "quality", "High", "HIGH")
            set_in(gl, "Threshold", 1.0)
            set_in(gl, "Smoothness", 0.3)
            set_in(gl, "Strength", 0.12)
            set_in(gl, "Size", 0.62)
            L(img, gl.inputs["Image"])
            img = gl.outputs["Image"]
        # halation: only the hot highlights, blurred tight, tinted red-orange, added back softly
        bw = put(N.new("CompositorNodeRGBToBW"), "Luminance")
        L(img, bw.inputs[0])
        key = maprange(bw.outputs[0], 0.9, 3.5, 0.0, 1.0)
        hot = mix("MULTIPLY", img, key)
        halo = rel_blur(hot, 0.0055, "Halation Blur")
        red = mix("MULTIPLY", halo, (1.0, 0.24, 0.07, 1.0))
        img = mix("ADD", img, red, 0.16)
        # chromatic aberration
        ld = put(N.new("CompositorNodeLensdist"), "Chromatic Aberration")
        set_in(ld, "Distortion", 0.0)
        set_in(ld, "Dispersion", 0.012)
        set_in(ld, "Jitter", False)
        set_in(ld, "Fit", False)
        L(img, ld.inputs["Image"])
        img = ld.outputs[0]
        # vignette: radial falloff from the frame centre, aspect-corrected
        info = put(N.new("CompositorNodeImageCoordinates"), "Image Coordinates")
        L(img, info.inputs[0])
        cen = put(N.new("ShaderNodeVectorMath"), "Centre")
        cen.operation = "SUBTRACT"
        cen.inputs[1].default_value = (0.5, 0.5, 0.0)
        L(info.outputs["Normalized"], cen.inputs[0])
        asp = put(N.new("ShaderNodeVectorMath"), "Aspect")
        asp.operation = "MULTIPLY"
        rx, ry = self.scene.render.resolution_x, self.scene.render.resolution_y
        asp.inputs[1].default_value = (rx / ry, 1.0, 0.0)
        L(cen.outputs[0], asp.inputs[0])
        ln = put(N.new("ShaderNodeVectorMath"), "Radius")
        ln.operation = "LENGTH"
        L(asp.outputs[0], ln.inputs[0])
        rmax = math.hypot(0.5 * rx / ry, 0.5)
        fall = maprange(ln.outputs["Value"], 0.45 * rmax, 1.0 * rmax, 1.0, 0.80, smooth=True)
        img = mix("MULTIPLY", img, fall)
        # grain: multiplicative, so it lives in the mid-tones like film, not in the blacks
        pc = put(N.new("CompositorNodeImageCoordinates"), "Pixel Coordinates")
        L(img, pc.inputs[0])
        wn = put(N.new("ShaderNodeTexWhiteNoise"), "Grain")
        wn.noise_dimensions = "4D"
        L(pc.outputs["Pixel"], wn.inputs["Vector"])
        fcg = g.driver_add(f'nodes["{wn.name}"].inputs["W"].default_value')
        fcg.driver.type = "SCRIPTED"
        fcg.driver.expression = "frame"
        gm = maprange(wn.outputs["Value"], 0.0, 1.0, 0.96, 1.04)
        img = mix("MULTIPLY", img, gm)
        sw = put(N.new("CompositorNodeSwitch"), "LAB 05 on/off")
        sw.name = "LAB5_Switch"
        L(gi.outputs[0], sw.inputs["Off"])
        L(img, sw.inputs["On"])
        L(sw.outputs[0], go.inputs[0])
        go.location = (x[0] + 100, 0)
        switch(g, 'nodes["LAB5_Switch"].inputs[0].default_value', "lens", on=1, off=0)
        return g

    # ---------------------------------------------------------------- optional: EEVEE quality pass
    def set_saved(self, num, path, value):
        """Change a plain scene setting and remember the user's value for teardown."""
        key = f"LAB{num}_settings"
        saved = json.loads(self.scene.get(key, "{}"))
        if path not in saved:
            v = get_path(self.scene, path)
            saved[path] = list(v) if hasattr(v, "__len__") and not isinstance(v, str) else v
        self.scene[key] = json.dumps(saved)
        set_path(self.scene, path, -1, value)

    def quality(self):
        """EEVEE's biggest weakness here is indirect light: without probes it only knows what is on
        screen. A baked Light Probe Volume over the shot gives it world-space bounce and sky occlusion."""
        s = self.scene
        coll = lever_coll(s, "quality")
        sec = self.log.section("quality", "OPTIONAL - EEVEE QUALITY PASS (stage 'quality')")
        hero = s.camera
        st = self.structure
        o, rays = cam_rays(hero, s, 24, 14)
        reach = (hero.matrix_world.translation.xy - st["center"]).length + st["radius"] + 12
        pts = [o]
        for _, _, d in rays:
            hit = self.scan.cast(o, d, reach * 1.2)
            pts.append(hit[0] if hit else o + d * reach)
        P = np.array(pts)
        lo = P.min(0) - np.array([6, 6, 2])
        hi = P.max(0) + np.array([6, 6, 0])
        hi[2] = max(hi[2], st["top"] + 4)
        size = hi - lo
        probe = bpy.data.lightprobes.new("LAB6_GIProbe", "VOLUME")
        ob = bpy.data.objects.new("LAB_GIProbe", probe)
        link(ob, coll)
        ob.location = Vector((lo + hi) / 2)
        ob.scale = Vector(size / 2)
        res = [int(clamp(round(v / 2.5), 4, 40)) for v in size]
        probe.resolution_x, probe.resolution_y, probe.resolution_z = res
        if hasattr(probe, "bake_samples"):
            probe.bake_samples = 512
        if hasattr(probe, "capture_distance"):
            probe.capture_distance = float(max(size)) * 2
        bpy.context.view_layer.update()
        for other in bpy.context.view_layer.objects:
            other.select_set(False)
        ob.select_set(True)
        bpy.context.view_layer.objects.active = ob
        t = time.time()
        bpy.ops.object.lightprobe_cache_bake(subset="ACTIVE")
        bake_s = time.time() - t
        e = "eevee."
        for path, value in ((e + "use_overscan", True), (e + "overscan_size", 3.0),
                            (e + "ray_tracing_options.trace_max_roughness", 0.8),
                            (e + "fast_gi_resolution", "1"), (e + "fast_gi_step_count", 16),
                            (e + "fast_gi_ray_count", 4), (e + "volumetric_tile_size", "2"),
                            (e + "volumetric_samples", 128), (e + "shadow_ray_count", 2),
                            (e + "shadow_step_count", 12), (e + "use_bokeh_jittered", True)):
            self.set_saved(6, path, value)
        sec["changes"] += [
            f"LAB_GIProbe: Light Probe Volume {size[0]:.0f} x {size[1]:.0f} x {size[2]:.0f} m over what "
            f"{hero.name} sees, {res[0]}x{res[1]}x{res[2]} probes, baked in {bake_s:.0f}s "
            "(re-bake after changing the light: Object Data > Bake Light Cache)",
            "overscan 3 % (screen-space effects get data past the frame edge)",
            "ray tracing on rough surfaces up to roughness 0.8 (was 0.5)",
            "Fast GI at full resolution, 16 steps, 4 rays; shadows 2 rays x 12 steps",
            "volumes: 2 px tiles, 128 steps; jittered depth of field (true bokeh, not a post blur)"]
        sec["why"] = ("EEVEE is a rasterizer that borrows ray-traced tricks. Out of the box its bounce light "
                      "comes from what is on screen plus the world, so shadowed faces glow with sky light that "
                      "real terrain would block, and light never bounces off anything behind the camera. A "
                      "baked probe volume gives the shot world-space bounce and sky occlusion; overscan, "
                      "rougher tracing and finer volume tiles remove the tell-tale edge darkening, "
                      "plastic-looking rough reflections and blocky fog.")
        self.log.save()
        self.render("07_eevee_quality", hero)

    # ---------------------------------------------------------------- optional: Cycles comparison
    def cycles_compare(self):
        """Same scene, same camera, path traced - to see what EEVEE is approximating."""
        s = self.scene
        sec = self.log.section("cycles", "OPTIONAL - CYCLES COMPARISON (stage 'cycles')")
        hero = bpy.data.objects.get(self.cfg["hero_camera"]) or s.camera
        q = self.cfg["cycles"]
        before = {"engine": s.render.engine}
        s.render.engine = "CYCLES"
        c = s.cycles
        prefs = bpy.context.preferences.addons.get("cycles")
        gpu = prefs is not None and getattr(prefs.preferences, "compute_device_type", "NONE") != "NONE"
        c.device = "GPU" if gpu else "CPU"
        c.samples = q["samples"]
        c.use_adaptive_sampling = True
        c.adaptive_threshold = 0.02
        c.use_denoising = True
        c.time_limit = q["time_limit"]
        rim = bpy.data.objects.get("LAB_Rim")
        rim_vol = rim.visible_volume_scatter if rim else None
        if rim:
            rim.visible_volume_scatter = False   # Cycles' version of the EEVEE rim's volume_factor = 0
        test = self.cfg["test"]
        self.cfg["test"] = {"samples": q["samples"], "percent": q["percent"]}
        try:
            self.render("08_cycles", hero)
        finally:
            self.cfg["test"] = test
            s.render.engine = before["engine"]
            if rim:
                rim.visible_volume_scatter = rim_vol
        r = self.log.d["renders"].get("08_cycles", {})
        sec["changes"].append(f"Cycles on {c.device}, {q['samples']} samples (adaptive, denoised), {q['percent']} %, "
                              f"{r.get('seconds', 0) / 60:.0f} min; the saved scene stays on EEVEE")
        sec["why"] = ("Cycles path-traces: every bounce, shadow and fog scatter is computed instead of "
                      "approximated. Expect warmer, fuller bounce light in the shadows, crisper and more "
                      "physical light shafts (multiple scattering inside the fog) and correct light through "
                      "leaves - at minutes per frame instead of seconds.")
        self.log.save()

    # ---------------------------------------------------------------- final
    def final(self):
        s = self.scene
        sec = self.log.section("final", "FINAL")
        hero = bpy.data.objects.get(self.cfg["hero_camera"]) or s.camera
        path = self.render("06_final", hero, final=True)
        sec["changes"].append(f"{os.path.basename(path)}: {self.cfg['final']['percent']} %, "
                              f"{self.cfg['final']['samples']} samples, camera {hero.name}")
        self.verdict()
        self.log.save()

    def verdict(self):
        r = self.log.d["renders"]
        steps = [("02_light", "Lever 2 - light"), ("03_materials", "Lever 3 - materials"),
                 ("04_air", "Lever 4 - air"), ("05_lens", "Lever 5 - lens")]
        deltas = [(r[k].get("delta", 0), label) for k, label in steps if k in r]
        if deltas:
            big = max(deltas)
            self.log.d.setdefault("verdict", "")
            auto = (f"Largest measured change after the camera move: {big[1]} "
                    f"(mean |RGB| change {big[0]:.3f}). ") + ", ".join(f"{l.split(' - ')[1]} {d:.3f}"
                                                                      for d, l in deltas) + "."
            if "Largest measured change" not in self.log.d["verdict"]:
                self.log.d["verdict"] = (auto + " " + self.log.d["verdict"]).strip()

    def restore_output_path(self):
        orig = json.loads(self.scene.get("LAB_orig_scene", "{}"))
        if "filepath" in orig:
            self.scene.render.filepath = orig["filepath"]


def run(cfg=None):
    cfg = cfg or load_config()
    lab = Lab(cfg)
    resume = lab.preflight()
    wanted = STAGES if cfg["stages"] == "all" else [x.strip() for x in cfg["stages"].split(",")]
    unknown = [w for w in wanted if w not in STAGES + EXTRA_STAGES]
    if unknown:
        raise ValueError(f"unknown stage(s) {unknown}; use {STAGES + EXTRA_STAGES}")
    lab.inspect() if not resume or "baseline" in wanted else None
    for key in reversed([k for k in LEVERS if k in wanted] if resume else []):
        teardown(lab.scene, key)
    if resume and "quality" not in wanted and any(k in wanted for k in ("light", "materials", "air")):
        teardown(lab.scene, "quality")   # a baked probe would be stale after re-lighting
    if "baseline" in wanted:
        for key in reversed(list(LEVERS)):
            teardown(lab.scene, key)
        orig_cam = bpy.data.objects.get(json.loads(lab.scene["LAB_orig_scene"])["camera"] or "")
        lab.baseline()
        if orig_cam is not None:
            lab.render("00_baseline", orig_cam)
        else:
            lab.log.warn("the scene had no camera, so there is no 00_baseline render")
        bpy.ops.wm.save_mainfile()
    needs_scan = any(k in wanted for k in ("camera", "light", "materials", "air", "quality"))
    if needs_scan:
        lab.analyse()
    steps = [("camera", lab.lever_camera), ("light", lab.lever_light), ("materials", lab.lever_materials),
             ("air", lab.lever_air), ("lens", lab.lever_lens)]
    for key, fn in steps:
        if key not in wanted:
            continue
        if key != "camera":
            lab.scene.camera = bpy.data.objects.get(cfg["hero_camera"]) or lab.scene.camera
            if lab.prev_png is None:
                prev = sorted(f for f in os.listdir(lab.render_dir) if f[:2].isdigit() and f[:2] < LEVERS[key][0][4:6]
                              and "CAM_" not in f)
                lab.prev_png = os.path.join(lab.render_dir, prev[-1]) if prev else None
        say(f"===== lever: {key} =====")
        fn()
        bpy.ops.wm.save_mainfile()
    if "final" in wanted:
        lab.final()
    if "quality" in wanted:
        lab.scene.camera = bpy.data.objects.get(cfg["hero_camera"]) or lab.scene.camera
        say("===== optional: EEVEE quality pass =====")
        lab.quality()
        bpy.ops.wm.save_mainfile()
    if "cycles" in wanted:
        say("===== optional: Cycles comparison =====")
        lab.cycles_compare()
        bpy.ops.wm.save_mainfile()
    if cfg["verdict_note"]:
        lab.verdict()
        lab.log.d["verdict"] = lab.log.d.get("verdict", "").split("\n\n")[0] + "\n\n" + cfg["verdict_note"]
    lab.restore_output_path()
    lab.log.save()
    bpy.ops.wm.save_mainfile()
    say("done. LAB_LOG is in the Text Editor; renders in", lab.render_dir)
    return lab


def load_config():
    cfg = json.loads(json.dumps(CONFIG))
    if "--" in sys.argv:
        extra = sys.argv[sys.argv.index("--") + 1:]
        if extra and extra[0].strip().startswith("{"):
            cfg.update(json.loads(extra[0]))
    return cfg


if __name__ == "__main__":
    run()
