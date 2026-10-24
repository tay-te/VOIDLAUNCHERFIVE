"""
Shared pieces for VOID's Blender scenes: block columns from height grids, flat and vertex-colour materials, text,
a camera aimed at a point, a dark VOID stage and Cycles on the GPU. Scenes import it with:

    sys.path.insert(0, os.path.dirname(__file__)); import voxkit as vk
"""
import math

import bpy
import numpy as np
from mathutils import Vector

FONT_PATH = "C:/Windows/Fonts/seguisb.ttf"
OUTFIT = None   # set by use_font() when Outfit is installed


def clear():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def lin(rgb):
    """sRGB to linear, for material colours given as hex."""
    return tuple(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb)


def columns(name, top, bottom, mask, col, cell=1.0, origin=(0.0, 0.0), z_scale=1.0, z_base=0.0):
    """Block columns on a grid: a top face per cell and walls down to the neighbour or the bottom.
    top, bottom: heights (grid units); mask: bool; col: (rows, cols, 3) 0-1 colours. Row 0 is north (+y)."""
    rows, cols_n = top.shape
    verts, faces, fcol = [], [], []

    def quad(p, c):
        b = len(verts)
        verts.extend(p)
        faces.append((b, b + 1, b + 2, b + 3))
        fcol.append(c)

    Z = lambda v: v * z_scale + z_base
    ox, oy = origin
    s = cell
    for j in range(rows):
        for i in range(cols_n):
            if not mask[j, i]:
                continue
            h, b = Z(top[j, i]), Z(bottom[j, i])
            x, y = ox + i * s, oy - j * s
            c = col[j, i]
            quad([(x, y, h), (x + s, y, h), (x + s, y - s, h), (x, y - s, h)], c)
            shade = c * 0.68
            for dj, di, e in ((0, -1, "w"), (0, 1, "e"), (-1, 0, "n"), (1, 0, "s")):
                jj, ii = j + dj, i + di
                inside = 0 <= jj < rows and 0 <= ii < cols_n and mask[jj, ii]
                lo = max(Z(top[jj, ii]), b) if inside else b
                if lo >= h:
                    continue
                if e == "w":
                    quad([(x, y - s, lo), (x, y, lo), (x, y, h), (x, y - s, h)], shade)
                elif e == "e":
                    quad([(x + s, y, lo), (x + s, y - s, lo), (x + s, y - s, h), (x + s, y, h)], shade)
                elif e == "n":
                    quad([(x, y, lo), (x + s, y, lo), (x + s, y, h), (x, y, h)], shade)
                else:
                    quad([(x + s, y - s, lo), (x, y - s, lo), (x, y - s, h), (x + s, y - s, h)], shade)
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    a = mesh.color_attributes.new("Col", "FLOAT_COLOR", "CORNER")
    data = np.repeat(np.array([(c[0], c[1], c[2], 1.0) for c in fcol], np.float32), 4, axis=0)
    a.data.foreach_set("color", data.ravel())
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def vertex_material(name, rough=0.85, emit=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    v = nt.nodes.new("ShaderNodeVertexColor")
    v.layer_name = "Col"
    nt.links.new(v.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = rough
    if emit:
        nt.links.new(v.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = emit
    return m


def flat(name, hexcol, emit=0.0, alpha=1.0, rough=0.6):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    c = (*lin(hexrgb(hexcol)), 1)
    bsdf.inputs["Base Color"].default_value = c
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Alpha"].default_value = alpha
    if alpha < 1 and hasattr(m, "surface_render_method"):
        m.surface_render_method = "BLENDED"
    if emit:
        bsdf.inputs["Emission Color"].default_value = c
        bsdf.inputs["Emission Strength"].default_value = emit
    return m


def assign(obj, mat):
    obj.data.materials.append(mat)
    return obj


def use_font():
    """Outfit (VOID's typeface) if it is installed, else Segoe UI Semibold."""
    global OUTFIT
    import os
    for p in (os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\Windows\Fonts\Outfit-SemiBold.ttf"), r"C:\Windows\Fonts\Outfit-SemiBold.ttf"):
        if os.path.isfile(p):
            OUTFIT = bpy.data.fonts.load(p)
            return OUTFIT
    OUTFIT = bpy.data.fonts.load(FONT_PATH)
    return OUTFIT


def text(body, loc, size, hexcol="#f1f3f5", emit=1.4, rot=(math.radians(90), 0, 0), align="CENTER", extrude=0.0):
    bpy.ops.object.text_add(location=loc, rotation=rot)
    t = bpy.context.active_object
    t.data.body = body
    t.data.font = OUTFIT or use_font()
    t.data.size = size
    t.data.align_x = align
    t.data.align_y = "CENTER"
    t.data.extrude = extrude
    assign(t, flat("txt " + body[:12], hexcol, emit=emit))
    return t


def camera(loc, target, lens=50, clip=8000, dof=None):
    d = bpy.data.cameras.new("cam")
    d.lens = lens
    d.clip_end = clip
    if dof:
        d.dof.use_dof = True
        d.dof.focus_distance = dof[0]
        d.dof.aperture_fstop = dof[1]
    c = bpy.data.objects.new("cam", d)
    bpy.context.scene.collection.objects.link(c)
    c.location = Vector(loc)
    c.rotation_euler = (Vector(target) - c.location).to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.camera = c
    return c


def sun(elev=48, az=-35, energy=3.2, warm=(1.0, 0.96, 0.9), angle=4):
    d = bpy.data.lights.new("sun", "SUN")
    d.energy = energy
    d.angle = math.radians(angle)
    d.color = warm
    s = bpy.data.objects.new("sun", d)
    s.rotation_euler = (math.radians(90 - elev), 0, math.radians(az))
    bpy.context.scene.collection.objects.link(s)
    return s


def stage(hexbg="#101214", strength=1.0):
    w = bpy.data.worlds.new("stage")
    w.use_nodes = True
    bg = w.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (*lin(hexrgb(hexbg)), 1)
    bg.inputs["Strength"].default_value = strength
    bpy.context.scene.world = w


def render(out, w=1920, h=1080, samples=128):
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for kind in ("OPTIX", "CUDA"):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            if any(d.type == kind for d in prefs.devices):
                for d in prefs.devices:
                    d.use = d.type == kind
                sc.cycles.device = "GPU"
                break
        except TypeError:
            continue
    sc.cycles.samples = samples
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.filepath = out
    for t in ("AgX", "Filmic"):
        try:
            sc.view_settings.view_transform = t
            break
        except TypeError:
            continue
    bpy.ops.render.render(write_still=True)
    print("[voxkit] wrote", out)
