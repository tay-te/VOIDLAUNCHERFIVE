"""
MC SHADERPACK - the floor. Everything a Minecraft shader pack does automatically, rebuilt once as
Blender nodes you own, so every scene starts at shader-pack level instead of zero:

  PBR        LabPBR _n/_s maps -> normal (DirectX -> OpenGL), AO, roughness, F0/IOR, metals, SSS,
             emission. Solid blocks without maps get per-texel relief: every pixel becomes a tiny
             bevelled tile, brighter pixels stand taller, fading out with distance.
  Animation  vertical texture strips (water, lava, fire, ...) play at Minecraft's 20 ticks/s with
             the frametime from their .mcmeta file.
  Lights     every torch, lantern, glowstone, lava ... cell gets a real light (meshswap-lite), so a
             lantern lights the scene instead of only glowing. Cells that already have a light
             nearby (yours, MCprep's) are skipped.
  Wind       Geometry Nodes sway for leaves and plants: leaves flutter, grass tips move, bases stay.
  Sky        a cloud layer in the world shader, lit from the sun's direction, over whatever sky
             you have (or an HDRI).

Normally used through mc_film_lab.py as the opt-in stage "pack" (collection LAB_00_Pack; its camera
icon toggles the whole pack like any lever):
    blender -b World.blend --python mc_film_lab.py -- '{"stages": "baseline,pack"}'
Standalone (applies the pack to a _LAB copy, no renders):
    blender -b World.blend --python mc_shaderpack.py
Asset library (node groups only, add the file under Preferences > File Paths > Asset Libraries):
    blender -b --factory-startup --python mc_shaderpack.py -- --export mc_shaderpack_library.blend
"""
import json
import os
import re
import sys
import zlib

import bpy
import numpy as np
from mathutils import Vector

IS5 = bpy.app.version >= (5, 0, 0)
PACK = {
    "labpbr_dir": None,      # resource pack folder searched for <name>_n.png / <name>_s.png (None = next to each texture)
    "relief": 0.35,          # per-texel relief strength on solid blocks without a normal map (0 = off)
    "normal_strength": 1.0,
    "emission_boost": 4.0,   # LabPBR emission (specular alpha) multiplier
    "lights": True,
    "max_lights": 48,        # nearest to the camera win
    "wind": {"leaves": 0.045, "plants": 0.13, "speed": 1.0, "direction": [1.0, 0.35, 0.0]},
    "clouds": {"coverage": 0.5, "softness": 0.2, "altitude": 1400.0, "scale": 1.0, "speed": 6.0,
               "opacity": 0.92},
    "hdri": None,            # path to an .hdr/.exr: replaces the sky colour under the clouds
}
ANIMATED = re.compile(r"water_(still|flow)|lava_(still|flow)|^fire_|soul_fire|campfire_fire|sea_lantern|prismarine$|"
                      r"magma|nether_portal|kelp|seagrass|lantern|soul_lantern|fire$")
# regex, colour (kelvin or rgb), watts, radius, full block (no shadow, capped reach)
EMITTERS = [
    (re.compile(r"soul_(torch|lantern|fire|campfire)"), (0.35, 0.85, 1.0), 25.0, 0.05, False),
    (re.compile(r"torch"), 2100, 30.0, 0.04, False),
    (re.compile(r"lantern|candle|end_rod"), 2300, 35.0, 0.06, False),
    (re.compile(r"sea_lantern|beacon"), 7500, 70.0, 0.4, True),
    (re.compile(r"lava|magma|campfire|fire"), 1800, 120.0, 0.5, True),
    (re.compile(r"glowstone|shroomlight|froglight|redstone_lamp|jack_o|glow"), 2700, 90.0, 0.5, True),
]


# ============================================================================== node-building helper
class G:
    """Tiny builder: G(tree).math("ADD", a, b) takes sockets or numbers and returns an output socket."""

    def __init__(self, tree):
        self.t, self.N, self.L = tree, tree.nodes, tree.links
        self.col = 0

    def node(self, kind, name=None, label=None, **props):
        n = self.N.new(kind)
        if name:
            n.name = name
        if label:
            n.label = label
        for k, v in props.items():
            setattr(n, k, v)
        n.location = (self.col * 170, -(len(self.N) % 6) * 150)
        self.col += 1
        return n

    def put(self, sock, v):
        if isinstance(v, (int, float)):
            sock.default_value = v
        elif isinstance(v, (tuple, list)):
            sock.default_value = v
        elif v is not None:
            self.L.new(v, sock)

    def math(self, op, a, b=None, c=None, clamp=False):
        n = self.node("ShaderNodeMath", operation=op, use_clamp=clamp)
        for i, v in enumerate((a, b, c)):
            if v is not None:
                self.put(n.inputs[i], v)
        return n.outputs[0]

    def vmath(self, op, a, b=None, c=None, scale=None):
        n = self.node("ShaderNodeVectorMath", operation=op)
        for i, v in enumerate((a, b, c)):
            if v is not None:
                self.put(n.inputs[i], v)
        if scale is not None:
            self.put(n.inputs["Scale"], scale)
        return n.outputs["Value" if op in ("DOT_PRODUCT", "LENGTH", "DISTANCE") else "Vector"]

    def maprange(self, v, a, b, c, d, smooth=False):
        n = self.node("ShaderNodeMapRange", clamp=True)
        if smooth:
            n.interpolation_type = "SMOOTHSTEP"
        for key, val in (("Value", v), ("From Min", a), ("From Max", b), ("To Min", c), ("To Max", d)):
            self.put(n.inputs[key], val)
        return n.outputs["Result"]

    def mix(self, kind, fac, a, b, blend="MIX"):
        n = self.node("ShaderNodeMix", data_type=kind, blend_type=blend)
        suffix = {"FLOAT": "Float", "VECTOR": "Vector", "RGBA": "Color"}[kind]
        self.put(next(s for s in n.inputs if s.identifier == "Factor_Float"), fac)
        self.put(next(s for s in n.inputs if s.identifier == "A_" + suffix), a)
        self.put(next(s for s in n.inputs if s.identifier == "B_" + suffix), b)
        return next(s for s in n.outputs if s.identifier == "Result_" + suffix)

    def sep(self, v):
        n = self.node("ShaderNodeSeparateXYZ")
        self.put(n.inputs[0], v)
        return n.outputs["X"], n.outputs["Y"], n.outputs["Z"]

    def comb(self, x, y, z):
        n = self.node("ShaderNodeCombineXYZ")
        for i, v in enumerate((x, y, z)):
            self.put(n.inputs[i], v)
        return n.outputs[0]


def new_group(name, kind, inputs, outputs):
    """(name, socket type, default) lists -> node group with Group Input/Output nodes."""
    old = bpy.data.node_groups.get(name)
    if old:
        bpy.data.node_groups.remove(old)
    ng = bpy.data.node_groups.new(name, kind)
    for nm, st, dv in inputs:
        s = ng.interface.new_socket(nm, in_out="INPUT", socket_type=st)
        if dv is not None:
            s.default_value = dv
    for nm, st in outputs:
        ng.interface.new_socket(nm, in_out="OUTPUT", socket_type=st)
    gi, go = ng.nodes.new("NodeGroupInput"), ng.nodes.new("NodeGroupOutput")
    return ng, gi, go


# ============================================================================== 1. PBR
def group_pbr():
    """LabPBR decode + per-texel relief. Outputs feed a Principled BSDF; Pack=0 passes originals through."""
    ng, gi, go = new_group("LAB0_PBR", "ShaderNodeTree", [
        ("Pack", "NodeSocketFloat", 1.0), ("Albedo", "NodeSocketColor", (0.8, 0.8, 0.8, 1.0)),
        ("UV", "NodeSocketVector", None), ("Texture Size", "NodeSocketVector", (16.0, 16.0, 0.0)),
        ("Normal Map", "NodeSocketColor", (0.5, 0.5, 1.0, 1.0)), ("Has Normal", "NodeSocketFloat", 0.0),
        ("Specular Map", "NodeSocketColor", (0.2, 0.04, 0.0, 1.0)), ("Specular Alpha", "NodeSocketFloat", 1.0),
        ("Has Specular", "NodeSocketFloat", 0.0),
        ("Roughness In", "NodeSocketFloat", 0.5), ("Metallic In", "NodeSocketFloat", 0.0),
        ("IOR In", "NodeSocketFloat", 1.5), ("Subsurface In", "NodeSocketFloat", 0.0),
        ("Emission In", "NodeSocketFloat", 0.0),
        ("Normal Strength", "NodeSocketFloat", 1.0), ("AO Strength", "NodeSocketFloat", 0.6),
        ("Emission Boost", "NodeSocketFloat", 4.0), ("Relief", "NodeSocketFloat", 0.35)],
        [("Base Color", "NodeSocketColor"), ("Roughness", "NodeSocketFloat"), ("Metallic", "NodeSocketFloat"),
         ("IOR", "NodeSocketFloat"), ("Normal", "NodeSocketVector"), ("Subsurface", "NodeSocketFloat"),
         ("Emission Strength", "NodeSocketFloat")])
    g, I = G(ng), gi.outputs
    # -- normal map: DirectX (green down) -> OpenGL, Z rebuilt from XY, blue channel is AO
    sn = g.node("ShaderNodeSeparateColor")
    g.put(sn.inputs[0], I["Normal Map"])
    nx = g.math("MULTIPLY_ADD", sn.outputs[0], 2.0, -1.0)
    ny = g.math("MULTIPLY_ADD", sn.outputs[1], -2.0, 1.0)
    nz = g.math("SQRT", g.math("MAXIMUM", g.math("SUBTRACT", g.math("SUBTRACT", 1.0, g.math("MULTIPLY", nx, nx)),
                                                       g.math("MULTIPLY", ny, ny)), 0.0))
    nmc = g.vmath("MULTIPLY_ADD", g.comb(nx, ny, nz), (0.5, 0.5, 0.5), (0.5, 0.5, 0.5))
    nmap = g.node("ShaderNodeNormalMap")
    g.put(nmap.inputs["Color"], nmc)
    on = g.math("MULTIPLY", I["Pack"], I["Has Normal"])
    g.put(nmap.inputs["Strength"], g.math("MULTIPLY", on, I["Normal Strength"]))
    # -- per-texel relief where there is no normal map: bevelled pixel tiles, taller when brighter
    uvn = g.vmath("FRACTION", g.vmath("MULTIPLY", I["UV"], I["Texture Size"]))
    fx, fy, _ = g.sep(uvn)
    edge = g.math("MINIMUM", g.math("MINIMUM", fx, g.math("SUBTRACT", 1.0, fx)),
                  g.math("MINIMUM", fy, g.math("SUBTRACT", 1.0, fy)))
    bevel = g.maprange(edge, 0.0, 0.22, 0.0, 1.0, smooth=True)
    lum = g.node("ShaderNodeRGBToBW")
    g.put(lum.inputs[0], I["Albedo"])
    height = g.math("MULTIPLY", bevel, g.math("MULTIPLY_ADD", lum.outputs[0], 0.65, 0.35))
    cam = g.node("ShaderNodeCameraData")
    fade = g.maprange(cam.outputs["View Distance"], 6.0, 30.0, 1.0, 0.0, smooth=True)
    rstr = g.math("MULTIPLY", g.math("MULTIPLY", I["Relief"], fade),
                  g.math("MULTIPLY", I["Pack"], g.math("SUBTRACT", 1.0, I["Has Normal"])))
    bump = g.node("ShaderNodeBump")
    g.put(bump.inputs["Height"], height)
    g.put(bump.inputs["Distance"], 0.012)
    g.put(bump.inputs["Strength"], rstr)
    g.put(bump.inputs["Normal"], nmap.outputs[0])
    # -- ambient occlusion from the normal map's blue channel
    ao = g.math("SUBTRACT", 1.0, g.math("MULTIPLY", g.math("MULTIPLY", on, I["AO Strength"]),
                                       g.math("SUBTRACT", 1.0, sn.outputs[2])))
    base = g.mix("RGBA", 1.0, I["Albedo"], g.comb(ao, ao, ao), blend="MULTIPLY")
    # -- specular map: smoothness, F0 / metals, porosity / SSS, emission
    ss = g.node("ShaderNodeSeparateColor")
    g.put(ss.inputs[0], I["Specular Map"])
    son = g.math("MULTIPLY", I["Pack"], I["Has Specular"])
    rough_s = g.math("POWER", g.math("SUBTRACT", 1.0, ss.outputs[0]), 2.0)
    metal_s = g.math("GREATER_THAN", ss.outputs[1], 229.5 / 255.0)
    sq = g.math("SQRT", g.math("MAXIMUM", g.math("MINIMUM", ss.outputs[1], 229.0 / 255.0), 0.0004))
    ior_s = g.math("MINIMUM", g.math("DIVIDE", g.math("ADD", 1.0, sq), g.math("SUBTRACT", 1.0, sq)), 3.0)
    sss_s = g.math("MULTIPLY", g.maprange(ss.outputs[2], 65 / 255.0, 1.0, 0.0, 1.0),
                   g.math("GREATER_THAN", ss.outputs[2], 64.5 / 255.0))
    emit_s = g.math("MULTIPLY", g.math("MULTIPLY", I["Specular Alpha"], g.math("LESS_THAN", I["Specular Alpha"], 0.998)),
                    I["Emission Boost"])
    L = ng.links.new
    L(g.mix("RGBA", I["Pack"], I["Albedo"], base), go.inputs["Base Color"])
    L(g.mix("FLOAT", son, I["Roughness In"], rough_s), go.inputs["Roughness"])
    L(g.mix("FLOAT", son, I["Metallic In"], metal_s), go.inputs["Metallic"])
    L(g.mix("FLOAT", g.math("MULTIPLY", son, g.math("SUBTRACT", 1.0, metal_s)), I["IOR In"], ior_s), go.inputs["IOR"])
    geo = g.node("ShaderNodeNewGeometry")
    L(g.mix("VECTOR", I["Pack"], geo.outputs["Normal"], bump.outputs[0]), go.inputs["Normal"])
    L(g.mix("FLOAT", son, I["Subsurface In"], g.math("MULTIPLY", sss_s, 0.6)), go.inputs["Subsurface"])
    L(g.math("MAXIMUM", I["Emission In"], g.math("MULTIPLY", emit_s, son)), go.inputs["Emission Strength"])
    go.location = (g.col * 170 + 200, 0)
    return ng


# ============================================================================== 2. animated strips
def group_anim():
    ng, gi, go = new_group("LAB0_AnimStrip", "ShaderNodeTree", [
        ("Pack", "NodeSocketFloat", 1.0), ("UV", "NodeSocketVector", None), ("Frames", "NodeSocketFloat", 1.0),
        ("Frame Time", "NodeSocketFloat", 1.0), ("Seconds", "NodeSocketFloat", 0.0),
        ("First Frame UVs", "NodeSocketFloat", 0.0)], [("UV", "NodeSocketVector")])
    g, I = G(ng), gi.outputs
    ticks = g.math("MULTIPLY", I["Seconds"], 20.0)                      # Minecraft runs at 20 ticks/s
    idx = g.math("FLOORED_MODULO", g.math("FLOOR", g.math("DIVIDE", ticks, g.math("MAXIMUM", I["Frame Time"], 1.0))),
                 I["Frames"])
    u, v, w = g.sep(I["UV"])
    # frame 0 is the top of the strip; face UVs either span the whole strip or only frame 0
    v_full = g.math("DIVIDE", g.math("ADD", v, g.math("SUBTRACT", g.math("SUBTRACT", I["Frames"], 1.0), idx)), I["Frames"])
    v_first = g.math("SUBTRACT", v, g.math("DIVIDE", idx, I["Frames"]))
    v_new = g.mix("FLOAT", I["First Frame UVs"], v_full, v_first)
    ng.links.new(g.mix("VECTOR", I["Pack"], I["UV"], g.comb(u, v_new, w)), go.inputs["UV"])
    go.location = (g.col * 170 + 200, 0)
    return ng


# ============================================================================== 4. wind
def group_wind_core():
    ng, gi, go = new_group("LAB0_WindCore", "GeometryNodeTree", [
        ("Geometry", "NodeSocketGeometry", None), ("Leaves", "NodeSocketBool", False),
        ("Plants", "NodeSocketBool", False), ("Leaf Amount", "NodeSocketFloat", 0.045),
        ("Plant Amount", "NodeSocketFloat", 0.13), ("Speed", "NodeSocketFloat", 1.0),
        ("Direction", "NodeSocketVector", (1.0, 0.35, 0.0))], [("Geometry", "NodeSocketGeometry")])
    g, I = G(ng), gi.outputs
    pos = g.node("GeometryNodeInputPosition").outputs[0]
    t = g.math("MULTIPLY", g.node("GeometryNodeInputSceneTime").outputs["Seconds"], I["Speed"])
    # gusts: big slow noise travelling downwind
    drift = g.vmath("SCALE", I["Direction"], scale=g.math("MULTIPLY", t, 0.8))
    gn = g.node("ShaderNodeTexNoise", noise_dimensions="4D")
    g.put(gn.inputs["Vector"], g.vmath("ADD", g.vmath("SCALE", pos, scale=0.12), g.vmath("SCALE", drift, scale=0.12)))
    g.put(gn.inputs["W"], g.math("MULTIPLY", t, 0.25))
    g.put(gn.inputs["Scale"], 1.0)
    gust = g.vmath("MULTIPLY", g.vmath("SUBTRACT", gn.outputs["Color"], (0.5, 0.5, 0.5)), (2.0, 2.0, 0.4))
    lean = g.vmath("ADD", g.vmath("SCALE", g.vmath("NORMALIZE", I["Direction"]), scale=0.6), gust)
    # flutter: small fast noise for leaves
    fl = g.node("ShaderNodeTexNoise", noise_dimensions="4D")
    g.put(fl.inputs["Vector"], pos)
    g.put(fl.inputs["W"], g.math("MULTIPLY", t, 1.7))
    g.put(fl.inputs["Scale"], 1.6)
    flutter = g.vmath("MULTIPLY", g.vmath("SUBTRACT", fl.outputs["Color"], (0.5, 0.5, 0.5)), (2.0, 2.0, 2.0))
    leaf = g.vmath("SCALE", g.vmath("ADD", g.vmath("SCALE", gust, scale=0.6), g.vmath("SCALE", flutter, scale=0.5)),
                   scale=g.math("MULTIPLY", I["Leaf Amount"], I["Leaves"]))
    # plants: only the top moves (height inside the block cell)
    _, _, z = g.sep(pos)
    tip = g.maprange(g.math("FRACT", z), 0.05, 0.9, 0.0, 1.0)
    plant = g.vmath("SCALE", lean, scale=g.math("MULTIPLY", g.math("MULTIPLY", I["Plant Amount"], tip), I["Plants"]))
    setp = g.node("GeometryNodeSetPosition")
    ng.links.new(gi.outputs["Geometry"], setp.inputs["Geometry"])
    g.put(setp.inputs["Offset"], g.vmath("ADD", leaf, plant))
    ng.links.new(setp.outputs[0], go.inputs[0])
    go.location = (g.col * 170 + 200, 0)
    return ng


def group_wind_for(leaf_mats, plant_mats, core):
    sig = "|".join(sorted(m.name for m in leaf_mats)) + "#" + "|".join(sorted(m.name for m in plant_mats))
    key = f"LAB0_Wind_{zlib.crc32(sig.encode()):08x}"
    if key in bpy.data.node_groups:
        return bpy.data.node_groups[key]
    ng, gi, go = new_group(key, "GeometryNodeTree", [("Geometry", "NodeSocketGeometry", None)],
                           [("Geometry", "NodeSocketGeometry")])
    g = G(ng)

    def any_of(mats):
        out = None
        for m in mats:
            sel = g.node("GeometryNodeMaterialSelection")
            sel.inputs["Material"].default_value = m
            if out is None:
                out = sel.outputs[0]
            else:
                b = g.node("FunctionNodeBooleanMath", operation="OR")
                ng.links.new(out, b.inputs[0])
                ng.links.new(sel.outputs[0], b.inputs[1])
                out = b.outputs[0]
        return out
    c = g.node("GeometryNodeGroup")
    c.node_tree = core
    ng.links.new(gi.outputs[0], c.inputs["Geometry"])
    for nm, mats in (("Leaves", leaf_mats), ("Plants", plant_mats)):
        s = any_of(mats)
        if s is not None:
            ng.links.new(s, c.inputs[nm])
    ng.links.new(c.outputs[0], go.inputs[0])
    return ng


# ============================================================================== 5. clouds
def group_clouds():
    ng, gi, go = new_group("LAB0_SkyClouds", "ShaderNodeTree", [
        ("Pack", "NodeSocketFloat", 1.0), ("Sky", "NodeSocketColor", (0.4, 0.6, 1.0, 1.0)),
        ("Sun Direction", "NodeSocketVector", (0.0, -0.5, 0.8)), ("Seconds", "NodeSocketFloat", 0.0),
        ("Coverage", "NodeSocketFloat", 0.5), ("Softness", "NodeSocketFloat", 0.2),
        ("Altitude", "NodeSocketFloat", 1400.0), ("Scale", "NodeSocketFloat", 1.0),
        ("Wind", "NodeSocketFloat", 6.0), ("Opacity", "NodeSocketFloat", 0.92)], [("Color", "NodeSocketColor")])
    g, I = G(ng), gi.outputs
    d = g.node("ShaderNodeTexCoord").outputs["Generated"]            # view direction in a world shader
    dx, dy, dz = g.sep(d)
    dzc = g.math("MAXIMUM", dz, 0.015)
    # where the view ray meets a cloud deck at Altitude, drifting with the wind
    p = g.vmath("ADD", g.vmath("SCALE", g.comb(g.math("DIVIDE", dx, dzc), g.math("DIVIDE", dy, dzc), 0.0),
                               scale=I["Altitude"]),
                g.comb(g.math("MULTIPLY", I["Seconds"], I["Wind"]),
                       g.math("MULTIPLY", g.math("MULTIPLY", I["Seconds"], I["Wind"]), 0.35), 0.0))
    nscale = g.math("MULTIPLY", I["Scale"], 0.0009)

    def fbm(vec):
        n = g.node("ShaderNodeTexNoise", noise_dimensions="3D")
        g.put(n.inputs["Vector"], vec)
        g.put(n.inputs["Scale"], nscale)
        g.put(n.inputs["Detail"], 7.0)
        g.put(n.inputs["Roughness"], 0.56)
        return n.outputs[0]
    n1 = fbm(p)
    sx, sy, sz = g.sep(I["Sun Direction"])
    toward = g.vmath("SCALE", g.vmath("NORMALIZE", g.comb(sx, sy, 0.0)), scale=160.0)
    n2 = fbm(g.vmath("ADD", p, toward))                                   # denser toward the sun = self-shadowed
    horizon = g.maprange(dz, 0.0, 0.16, 0.0, 1.0, smooth=True)
    dens = g.math("MULTIPLY", g.maprange(n1, I["Coverage"], g.math("ADD", I["Coverage"], I["Softness"]), 0.0, 1.0,
                                         smooth=True), horizon)
    shade = g.math("ADD", g.math("MULTIPLY", g.math("SUBTRACT", n1, n2), 5.0), 0.55, clamp=True)
    lum = g.node("ShaderNodeRGBToBW")
    g.put(lum.inputs[0], I["Sky"])
    sun_up = g.maprange(sz, -0.05, 0.25, 0.25, 1.0)
    lit_level = g.math("MULTIPLY", g.math("MULTIPLY", g.math("MAXIMUM", lum.outputs[0], 0.02), 2.3), sun_up)
    warm = g.maprange(sz, 0.0, 0.35, 0.0, 1.0)                            # low sun -> warmer cloud tops
    tint = g.mix("RGBA", warm, (1.0, 0.62, 0.38, 1.0), (1.0, 0.96, 0.9, 1.0))
    lit = g.mix("RGBA", 1.0, tint, g.comb(lit_level, lit_level, lit_level), blend="MULTIPLY")
    dark = g.mix("RGBA", 1.0, I["Sky"], (0.72, 0.74, 0.8, 1.0), blend="MULTIPLY")
    cloud = g.mix("RGBA", shade, dark, lit)
    silver = g.math("MULTIPLY", g.math("POWER", g.math("MAXIMUM", g.vmath("DOT_PRODUCT", d, I["Sun Direction"]), 0.0),
                                        10.0), g.math("MULTIPLY", g.math("SUBTRACT", 1.0, dens), 1.6))
    cloud = g.mix("RGBA", silver, cloud, lit, blend="ADD")
    amount = g.math("MULTIPLY", g.math("MULTIPLY", dens, I["Opacity"]), I["Pack"])
    ng.links.new(g.mix("RGBA", amount, I["Sky"], cloud), go.inputs["Color"])
    go.location = (g.col * 170 + 200, 0)
    return ng


def all_groups():
    return [group_pbr(), group_anim(), group_wind_core(), group_clouds()]


# ============================================================================== applying the pack
def find_image_node(sock, depth=4):
    """Walk upstream from a socket to the image texture that feeds it."""
    if not sock.is_linked or depth == 0:
        return None
    n = sock.links[0].from_node
    if n.bl_idname == "ShaderNodeTexImage" and n.image:
        return n
    for s in n.inputs:
        found = find_image_node(s, depth - 1)
        if found:
            return found
    return None


def uv_source(tree, img_node, g):
    if img_node.inputs["Vector"].is_linked:
        return img_node.inputs["Vector"].links[0].from_socket
    tc = tree.nodes.get("LAB0_TexCoord") or g.node("ShaderNodeTexCoord", name="LAB0_TexCoord")
    return tc.outputs["UV"]


def mcmeta(img):
    path = bpy.path.abspath(img.filepath) + ".mcmeta"
    if os.path.exists(path):
        try:
            with open(path, encoding="utf-8") as fh:
                anim = json.load(fh).get("animation", {})
            return max(1, int(anim.get("frametime", 1)))
        except (OSError, ValueError):
            return 1
    return None


class Pack:
    def __init__(self, lab, L, cfg):
        self.lab, self.L, self.cfg = lab, L, {**PACK, **(cfg or {})}
        self.scene = lab.scene
        self.sec = lab.log.section("pack", "LEVER 0 - SHADER PACK (the floor)")
        self.coll = L.lever_coll(self.scene, "pack")
        self.groups = {"pbr": group_pbr(), "anim": group_anim(), "wind": group_wind_core(),
                       "clouds": group_clouds()}
        self.map_index = self.index_maps()

    def index_maps(self):
        idx = {}
        root = self.cfg["labpbr_dir"]
        if root and os.path.isdir(bpy.path.abspath(root)):
            for dp, _, files in os.walk(bpy.path.abspath(root)):
                for f in files:
                    if f.lower().endswith((".png", ".tga")):
                        idx.setdefault(f.lower(), os.path.join(dp, f))
        return idx

    def find_map(self, img, suffix):
        src = bpy.path.abspath(img.filepath)
        stem, ext = os.path.splitext(os.path.basename(src))
        for cand in (os.path.join(os.path.dirname(src), f"{stem}{suffix}{ext}"),
                     self.map_index.get(f"{stem}{suffix}{ext}".lower())):
            if cand and os.path.exists(cand):
                im = bpy.data.images.load(cand, check_existing=True)
                if hasattr(im, "colorspace_settings"):
                    im.colorspace_settings.name = "Non-Color"
                return im
        return None

    # ------------------------------------------------------------------ materials
    def materials(self):
        L = self.L
        used = sorted({sl.material for o in self.scene.objects if not self.lab.is_lab(o)
                       for sl in o.material_slots if sl.material and not sl.material.name.startswith("LAB")},
                      key=lambda m: m.name)
        n_pbr = n_relief = n_anim = 0
        anim_names, pbr_names = [], []
        for mat in used:
            if not mat.node_tree:
                continue
            p = L.principled_of(mat)
            if p is None:
                continue
            tree = mat.node_tree
            img = find_image_node(p.inputs["Base Color"])
            if img is None:
                continue
            g = G(tree)
            g.col = -12
            # animated strip first, so every map below shares the animated UVs
            w, h = img.image.size[:2]
            frames = h // w if w and h % w == 0 else 1
            ft = mcmeta(img.image)
            if frames > 1 and (ft is not None or ANIMATED.search(img.image.name.lower())):
                self.animate(mat, tree, img, frames, ft or 1, g)
                n_anim += 1
                anim_names.append(mat.name)
            nmap, smap = self.find_map(img.image, "_n"), self.find_map(img.image, "_s")
            cls = L.classify_material(mat)
            relief = self.cfg["relief"] if (cls == "solid" and nmap is None and not L.is_emissive(mat)) else 0.0
            if nmap is None and smap is None and relief == 0.0:
                continue
            self.wire_pbr(mat, tree, p, img, nmap, smap, relief, g)
            if nmap or smap:
                n_pbr += 1
                pbr_names.append(mat.name)
            elif relief:
                n_relief += 1
        self.sec["changes"] += [
            f"LabPBR maps wired on {n_pbr} materials ({', '.join(pbr_names[:8])}{' ...' if len(pbr_names) > 8 else ''}): "
            "normal DirectX->OpenGL + AO, roughness=(1-smoothness)^2, F0->IOR, metals 230-255, SSS, emission",
            f"per-texel relief on {n_relief} solid materials without a normal map (strength {self.cfg['relief']}, "
            "fades out 6-30 m from the camera)",
            f"animated texture strips on {n_anim} materials ({', '.join(anim_names) or 'none found'}), "
            "20 ticks/s, frametime from .mcmeta"]

    def animate(self, mat, tree, img, frames, frametime, g):
        L = self.L
        src = uv_source(tree, img, g)
        dst = img.inputs["Vector"]
        if dst.is_linked:
            L.record_link(mat, 0, dst, dst.links[0].from_socket)
        else:
            L.record_link(mat, 0, dst, None)
        node = g.node("ShaderNodeGroup", name="LAB0_Anim", label="LAB Animated Strip")
        node.node_tree = self.groups["anim"]
        tree.links.new(src, node.inputs["UV"])
        node.inputs["Frames"].default_value = frames
        node.inputs["Frame Time"].default_value = frametime
        node.inputs["First Frame UVs"].default_value = self.first_frame_uvs(mat, frames)
        clock = g.node("ShaderNodeValue", name="LAB0_Clock", label="LAB seconds")
        fps = self.scene.render.fps / self.scene.render.fps_base
        fc = tree.driver_add(f'nodes["{clock.name}"].outputs[0].default_value')
        fc.driver.type = "SCRIPTED"
        fc.driver.expression = f"frame / {fps:.4f}"
        tree.links.new(clock.outputs[0], node.inputs["Seconds"])
        tree.links.new(node.outputs["UV"], dst)
        L.switch(tree, f'nodes["{node.name}"].inputs["Pack"].default_value', "pack", on=1.0, off=0.0)

    def first_frame_uvs(self, mat, frames):
        """1 if faces already map only the first frame (v span ~ 1/frames), 0 if they span the whole strip."""
        for o in self.scene.objects:
            if o.type != "MESH" or mat.name not in [s.material.name for s in o.material_slots if s.material]:
                continue
            me = o.data
            if not me.uv_layers.active:
                continue
            mi = [i for i, s in enumerate(o.material_slots) if s.material == mat]
            polys = [p for p in me.polygons if p.material_index in mi][:200]
            uv = me.uv_layers.active.data
            spans = [max(uv[i].uv.y for i in p.loop_indices) - min(uv[i].uv.y for i in p.loop_indices) for p in polys]
            if spans:
                return 1.0 if np.median(spans) < 1.5 / frames else 0.0
        return 0.0

    def wire_pbr(self, mat, tree, p, img, nmap, smap, relief, g):
        L = self.L
        grp = g.node("ShaderNodeGroup", name="LAB0_PBR", label="LAB PBR")
        grp.node_tree = self.groups["pbr"]
        uv = uv_source(tree, img, g) if not img.inputs["Vector"].is_linked else img.inputs["Vector"].links[0].from_socket
        tree.links.new(uv, grp.inputs["UV"])
        w, h = img.image.size[:2]
        grp.inputs["Texture Size"].default_value = (float(w), float(h), 0.0)
        tree.links.new(img.outputs["Color"], grp.inputs["Albedo"])
        for tag, im, color_in, has_in in (("n", nmap, "Normal Map", "Has Normal"), ("s", smap, "Specular Map", "Has Specular")):
            if im is None:
                continue
            tn = g.node("ShaderNodeTexImage", name=f"LAB0_map_{tag}", label=f"LAB {im.name}")
            tn.image = im
            tn.interpolation = "Closest"
            if img.inputs["Vector"].is_linked:
                tree.links.new(img.inputs["Vector"].links[0].from_socket, tn.inputs["Vector"])
            tree.links.new(tn.outputs["Color"], grp.inputs[color_in])
            grp.inputs[has_in].default_value = 1.0
            if tag == "s":
                tree.links.new(tn.outputs["Alpha"], grp.inputs["Specular Alpha"])
        grp.inputs["Relief"].default_value = relief
        grp.inputs["Normal Strength"].default_value = self.cfg["normal_strength"]
        grp.inputs["Emission Boost"].default_value = self.cfg["emission_boost"]
        # feed the Principled; remember what it had so the lever can be torn down
        pairs = [("Base Color", "Base Color", None), ("Roughness", "Roughness", "Roughness In"),
                 ("Metallic", "Metallic", "Metallic In"), ("IOR", "IOR", "IOR In"), ("Normal", "Normal", None),
                 ("Subsurface", "Subsurface Weight", "Subsurface In"),
                 ("Emission Strength", "Emission Strength", "Emission In")]
        for out_name, p_in, passthru in pairs:
            dst = p.inputs.get(p_in)
            if dst is None:
                continue
            src = dst.links[0].from_socket if dst.is_linked else None
            L.record_link(mat, 0, dst, src)
            if passthru:
                if src is not None:
                    tree.links.new(src, grp.inputs[passthru])
                else:
                    grp.inputs[passthru].default_value = dst.default_value
            tree.links.new(grp.outputs[out_name], dst)
        ec = p.inputs.get("Emission Color")
        if smap is not None and ec is not None and not ec.is_linked:
            L.record_link(mat, 0, ec, None)
            tree.links.new(img.outputs["Color"], ec)
        L.switch(tree, f'nodes["{grp.name}"].inputs["Pack"].default_value', "pack", on=1.0, off=0.0)

    # ------------------------------------------------------------------ lights (meshswap-lite)
    def lights(self):
        L, scan = self.L, self.lab.scan
        emit_mat = np.array([bool(scan.mat_emit.get(m) or L.EMIT_RE.search(m.lower())) for m in L.Scan._mats])
        tris = np.nonzero(emit_mat[scan.tmat])[0]
        if not len(tris):
            self.sec["changes"].append("no emissive blocks found - no lights added")
            return
        tv = scan.V[scan.T[tris]]
        cent = tv.mean(1)
        nrm = np.cross(tv[:, 1] - tv[:, 0], tv[:, 2] - tv[:, 0])
        nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
        inside = np.floor(cent - nrm * 0.05).astype(int)     # a face on a block boundary belongs to its own block
        cells = {}
        for i, c in zip(tris, inside):
            name = L.Scan._mats[scan.tmat[i]].lower()
            kind = next((k for k, e in enumerate(EMITTERS) if e[0].search(name)), None)
            if kind is None:
                continue
            cells.setdefault((scan.owner[i], tuple(c), kind), []).append(i)
        groups = []                                           # merge a lamp split across cells into one
        for (owner, cell, kind), ids in sorted(cells.items()):
            ctr = scan.V[scan.T[ids]].reshape(-1, 3).mean(0)
            if not EMITTERS[kind][4]:
                g = next((g for g in groups if g[0] == owner and g[2] == kind and
                          np.linalg.norm(g[3] - ctr) < 0.9), None)
                if g is not None:
                    g[1].extend(ids)
                    g[3] = scan.V[scan.T[g[1]]].reshape(-1, 3).mean(0)
                    continue
            groups.append([owner, list(ids), kind, ctr, cell])
        existing = [o.matrix_world.translation for o in self.scene.objects
                    if o.type == "LIGHT" and not self.lab.is_lab(o)]
        cam = self.scene.camera
        cam_p = cam.matrix_world.translation if cam else Vector(scan.V.mean(0))
        cands = []
        for owner, ids, kind, ctr, cell in groups:
            pts = scan.V[scan.T[ids]].reshape(-1, 3)
            ctr = Vector(ctr) if not EMITTERS[kind][4] else Vector(cell) + Vector((0.5, 0.5, 0.5))
            if any((ctr - e).length < 0.75 for e in existing):
                continue                       # a light is already there (yours or MCprep's): keep it
            cands.append(((ctr - cam_p).length, owner, kind, pts, ctr, ids))
        cands.sort(key=lambda c: c[0])
        made, datas, hidden = 0, {}, set()

        def light_data(kind, area):
            key = (kind, area)
            if key not in datas:
                rx, color, watts, radius, block = EMITTERS[kind]
                tag = rx.pattern.split("|")[0].strip("()^$")
                d = bpy.data.lights.new(f"LAB0_light_{tag}{'_face' if area else ''}", "AREA" if area else "POINT")
                d.color = L.kelvin_rgb(color) if isinstance(color, (int, float)) else color
                d.use_shadow_jitter = True
                if area:                          # one glowing block face: a 0.9 m emitter just in front of it
                    d.shape, d.size = "SQUARE", 0.9
                    d.energy = watts / 3.0
                else:
                    d.energy, d.shadow_soft_size = watts, radius
                datas[key] = d
            return datas[key]

        for _, owner, kind, pts, ctr, ids in cands[: self.cfg["max_lights"]]:
            block = EMITTERS[kind][4]
            if block:
                # a glowing block emits from its exposed faces. A light inside it would be shadowed by the
                # block itself, and an unshadowed one leaks through walls - so light each face from outside.
                tv = scan.V[scan.T[ids]]
                nrm = np.cross(tv[:, 1] - tv[:, 0], tv[:, 2] - tv[:, 0])
                nrm = np.round(nrm / np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9), 1)
                for n in {tuple(v) for v in nrm}:
                    sel = np.all(nrm == np.array(n), axis=1)
                    face_c = Vector(tv[sel].reshape(-1, 3).mean(0))
                    nv = Vector(n).normalized()
                    ob = bpy.data.objects.new(f"LAB0_Light_{made:03d}", light_data(kind, True))
                    L.link(ob, self.coll)
                    ob.location = face_c + nv * 0.03
                    ob.rotation_euler = nv.to_track_quat("-Z", "Y").to_euler()
                    made += 1
                continue
            owner_name = scan.owners[owner]
            ob_owner = bpy.data.objects.get(owner_name)
            prop = ob_owner is not None and ob_owner.material_slots and all(
                s.material and L.EMIT_RE.search(s.material.name.lower()) for s in ob_owner.material_slots)
            if prop and owner_name not in hidden:
                # a separate lamp prop: let light pass its own shell instead of moving the light out of it
                L.switch(ob_owner, "visible_shadow", "pack", on=0, off=1)
                hidden.add(owner_name)
            pos = ctr if prop else Vector((ctr.x, ctr.y, float(pts[:, 2].max()) + 0.04))
            ob = bpy.data.objects.new(f"LAB0_Light_{made:03d}", light_data(kind, False))
            L.link(ob, self.coll)
            ob.location = pos
            made += 1
        self.sec["changes"].append(
            f"{made} real lights on emissive blocks (nearest {self.cfg['max_lights']} emitters to the camera): point "
            "lights in torches/lanterns, an area light on each exposed face of glowing blocks (no leaks through "
            "walls); one shared light per type so one edit retunes them all; props with their own shell stop "
            "casting shadows: "
            f"{', '.join(sorted(hidden)) or 'none'}")

    # ------------------------------------------------------------------ wind
    def wind(self):
        L = self.L
        done, kept = [], []
        for o in self.scene.objects:
            if o.type != "MESH" or self.lab.is_lab(o) or not o.material_slots:
                continue
            leaf = [s.material for s in o.material_slots if s.material and L.classify_material(s.material) == "leaves"]
            plant = [s.material for s in o.material_slots if s.material and L.classify_material(s.material) == "cutout"]
            if not leaf and not plant:
                continue
            if any(re.search(r"wind|sway", m.name, re.I) or m.type == "WAVE" for m in o.modifiers):
                kept.append(o.name)            # it already moves: keep yours, don't stack a second wind
                continue
            md = o.modifiers.new("LAB0_Wind", "NODES")
            md.node_group = group_wind_for(leaf, plant, self.groups["wind"])
            core = next(n for n in md.node_group.nodes if n.bl_idname == "GeometryNodeGroup")
            wcfg = self.cfg["wind"]
            core.inputs["Leaf Amount"].default_value = wcfg["leaves"]
            core.inputs["Plant Amount"].default_value = wcfg["plants"]
            core.inputs["Speed"].default_value = wcfg["speed"]
            core.inputs["Direction"].default_value = wcfg["direction"]
            for path in ("show_render", "show_viewport"):
                L.switch(o, f'modifiers["{md.name}"].{path}', "pack", on=1, off=0)
            done.append(o.name)
        self.sec["changes"].append(
            f"Geometry Nodes wind on {len(done)} objects with leaves/plants (leaves {self.cfg['wind']['leaves']} m "
            f"flutter, grass tips {self.cfg['wind']['plants']} m, bases pinned); existing wind kept on: "
            f"{', '.join(kept) or 'none'}")

    # ------------------------------------------------------------------ sky
    def sky(self):
        L, w = self.L, self.scene.world
        if w is None:
            self.sec["changes"].append("no world - no clouds")
            return
        nt = L.ensure_nodes(w)
        out = nt.get_output_node("EEVEE") or nt.get_output_node("ALL")
        bg = None
        if out and out.inputs["Surface"].is_linked:
            n = out.inputs["Surface"].links[0].from_node
            bg = n if n.bl_idname == "ShaderNodeBackground" else next(
                (x for x in nt.nodes if x.bl_idname == "ShaderNodeBackground"), None)
        if bg is None:
            self.sec["changes"].append("world has no Background node - clouds skipped")
            return
        g = G(nt)
        g.col = -6
        col_in = bg.inputs["Color"]
        src = col_in.links[0].from_socket if col_in.is_linked else None
        L.record_link(w, 0, col_in, src)
        if self.cfg["hdri"]:
            env = g.node("ShaderNodeTexEnvironment", name="LAB0_HDRI", label="LAB HDRI")
            env.image = bpy.data.images.load(bpy.path.abspath(self.cfg["hdri"]), check_existing=True)
            src = env.outputs["Color"]
        clouds = g.node("ShaderNodeGroup", name="LAB0_Clouds", label="LAB Clouds")
        clouds.node_tree = self.groups["clouds"]
        if src is not None:
            nt.links.new(src, clouds.inputs["Sky"])
        else:
            clouds.inputs["Sky"].default_value = col_in.default_value
        for k, v in self.cfg["clouds"].items():
            name = {"coverage": "Coverage", "softness": "Softness", "altitude": "Altitude", "scale": "Scale",
                    "speed": "Wind", "opacity": "Opacity"}[k]
            clouds.inputs[name].default_value = v
        nt.links.new(clouds.outputs[0], col_in)
        # sun direction follows the sky texture (which the light lever re-aims) or the key sun lamp
        sun_vec = g.node("ShaderNodeCombineXYZ", name="LAB0_SunDir", label="LAB sun direction")
        sky = next((n for n in nt.nodes if n.bl_idname == "ShaderNodeTexSky"
                    and n.sky_type not in ("PREETHAM", "HOSEK_WILKIE")), None)
        sun_obj = next((o for o in self.scene.objects if o.type == "LIGHT" and o.data.type == "SUN"
                        and not self.lab.is_lab(o)), None)
        if sky is not None:
            exprs = ("sin(r) * cos(e)", "cos(r) * cos(e)", "sin(e)")
            varsrc = [("e", "WORLD", w, f'node_tree.nodes["{sky.name}"].sun_elevation'),
                      ("r", "WORLD", w, f'node_tree.nodes["{sky.name}"].sun_rotation')]
            follow = f"sky texture '{sky.name}'"
        elif sun_obj is not None:
            exprs = ("cos(a) * sin(b) * cos(c) + sin(a) * sin(c)", "cos(a) * sin(b) * sin(c) - sin(a) * cos(c)",
                     "cos(a) * cos(b)")
            varsrc = [(v, "OBJECT", sun_obj, f"rotation_euler[{i}]") for i, v in enumerate("abc")]
            follow = f"sun lamp '{sun_obj.name}'"
        else:
            exprs, varsrc, follow = None, None, "a fixed high sun"
        if exprs:
            for i, ex in enumerate(exprs):
                fc = nt.driver_add(f'nodes["{sun_vec.name}"].inputs[{i}].default_value')
                fc.driver.type = "SCRIPTED"
                for vname, idt, idb, path in varsrc:
                    var = fc.driver.variables.new()
                    var.name, var.type = vname, "SINGLE_PROP"
                    var.targets[0].id_type = idt
                    var.targets[0].id = idb
                    var.targets[0].data_path = path
                fc.driver.expression = ex
        else:
            sun_vec.inputs[2].default_value = 1.0
        nt.links.new(sun_vec.outputs[0], clouds.inputs["Sun Direction"])
        clock = g.node("ShaderNodeValue", name="LAB0_Clock", label="LAB seconds")
        fps = self.scene.render.fps / self.scene.render.fps_base
        fc = nt.driver_add(f'nodes["{clock.name}"].outputs[0].default_value')
        fc.driver.type = "SCRIPTED"
        fc.driver.expression = f"frame / {fps:.4f}"
        nt.links.new(clock.outputs[0], clouds.inputs["Seconds"])
        L.switch(nt, f'nodes["{clouds.name}"].inputs["Pack"].default_value', "pack", on=1.0, off=0.0)
        c = self.cfg["clouds"]
        self.sec["changes"].append(
            f"cloud deck in the world shader over {'an HDRI' if self.cfg['hdri'] else 'your sky'}: coverage "
            f"{c['coverage']}, altitude {c['altitude']:.0f} m, drifting {c['speed']} m/s, self-shadowed toward the "
            f"sun, silver lining near it, warm at low sun; sun direction follows {follow}")

    def run(self):
        self.materials()
        if self.cfg["lights"]:
            self.lights()
        self.wind()
        self.sky()
        self.sec["values"] = {"relief": self.cfg["relief"], "normal strength": self.cfg["normal_strength"],
                              "emission boost": self.cfg["emission_boost"], "max lights": self.cfg["max_lights"],
                              "wind (leaves/plants m)": f"{self.cfg['wind']['leaves']} / {self.cfg['wind']['plants']}",
                              "cloud coverage": self.cfg["clouds"]["coverage"]}
        self.sec["why_title"] = "Why it raises the floor:"
        self.sec["why"] = ("Blender's problem with Minecraft scenes is the starting point, not the ceiling: an import "
                           "arrives flat - no normals, uniform gloss, frozen water, lamps that light nothing, trees "
                           "that never move, an empty sky. A shader pack fixes that floor automatically; this does "
                           "the same once, as node groups you own. Normal and specular maps (or per-texel relief) "
                           "let block faces catch light like stone and wood, real lights make every torch a "
                           "practical, wind makes the world breathe, and clouds give the sky structure and the "
                           "light a source.")


def apply(lab, L, cfg=None):
    Pack(lab, L, cfg).run()


def export_library(path):
    groups = all_groups()
    for ng in groups:
        ng.asset_mark()
        ng.asset_data.description = "MC SHADERPACK - " + ng.name[5:]
    bpy.data.libraries.write(os.path.abspath(path), set(groups), fake_user=True)
    print(f"[PACK] wrote {len(groups)} node groups to {os.path.abspath(path)}")


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if args and args[0] == "--export":
        export_library(args[1] if len(args) > 1 else "mc_shaderpack_library.blend")
    else:
        here = os.path.dirname(os.path.abspath(__file__))
        sys.path.insert(0, here)
        import mc_film_lab
        mc_film_lab.run({**mc_film_lab.load_config(), "stages": "pack", "render": False})
