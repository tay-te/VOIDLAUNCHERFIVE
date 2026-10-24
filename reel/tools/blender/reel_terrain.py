"""
Builds the land from `reel.sh terrain` grids (renders/_terrain/NAME) as a Blender mesh in the same space as
reel_camera.py's camera. As a holdout it hides overlays wherever a hill, a house or the water stands in front of them;
as a plain grey surface it is how a shot's camera and land are checked against the filmed frames.

    blender -b scene.blend -P tools/blender/reel_terrain.py -- renders/_terrain/NAME [--layer surface|ground]
        [--holdout] [--origin x,y,z] [--save FILE.blend]

Or from Blender's Python: exec this file, then import_terrain(folder, origin=None, layer="surface", holdout=True).
The origin defaults to the one the scene's Reel camera was imported with.
"""
import json
import os
import sys

import bpy
import numpy as np


def import_terrain(folder, origin=None, layer="surface", holdout=True, name=None):
    with open(os.path.join(folder, "meta.json"), encoding="utf-8") as f:
        meta = json.load(f)
    nx, nz, step = meta["nx"], meta["nz"], meta["step"]
    heights = np.fromfile(os.path.join(folder, layer + ".f32"), dtype="<f4").reshape(nz, nx)
    scene = bpy.context.scene
    if origin is None:
        origin = tuple(scene.get("reel_origin", (0, 0, 0)))
    ox, oy, oz = origin

    xs = meta["x0"] + np.arange(nx) * step
    zs = meta["z0"] + np.arange(nz) * step
    gx, gz = np.meshgrid(xs, zs)
    # Minecraft (x, y, z) -> Blender (x, -z, y), less the origin (see reel_camera.py)
    verts = np.stack([gx - ox, -(gz - oz), heights - oy], axis=-1).reshape(-1, 3).astype(np.float32)

    i = np.arange(nx - 1)
    j = np.arange(nz - 1)
    ii, jj = np.meshgrid(i, j)
    a = (jj * nx + ii).ravel()
    quads = np.stack([a, a + nx, a + nx + 1, a + 1], axis=-1).astype(np.int32)

    name = name or "Reel land " + meta["name"]
    mesh = bpy.data.meshes.get(name) or bpy.data.meshes.new(name)
    mesh.clear_geometry()
    mesh.vertices.add(len(verts))
    mesh.vertices.foreach_set("co", verts.ravel())
    mesh.loops.add(quads.size)
    mesh.loops.foreach_set("vertex_index", quads.ravel())
    mesh.polygons.add(len(quads))
    mesh.polygons.foreach_set("loop_start", np.arange(0, quads.size, 4, dtype=np.int32))
    mesh.polygons.foreach_set("loop_total", np.full(len(quads), 4, dtype=np.int32))
    mesh.update(calc_edges=True)
    mesh.validate()

    obj = bpy.data.objects.get(name) or bpy.data.objects.new(name, mesh)
    if obj.name not in scene.collection.all_objects:
        scene.collection.objects.link(obj)
    obj.is_holdout = holdout
    obj["reel_terrain"] = meta["name"]
    print("[reel] land %s: %dx%d at %d-block steps (%d loaded, %d from the generator), %s" % (
        meta["name"], nx, nz, step, meta["loaded"], meta["generated"], "holdout" if holdout else "visible"))
    return obj


def _main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not argv:
        return
    folder = argv[0]
    opts = {}
    flags = set()
    k = 1
    while k < len(argv):
        key = argv[k].lstrip("-")
        if key == "holdout":
            flags.add(key)
            k += 1
        else:
            opts[key] = argv[k + 1]
            k += 2
    origin = tuple(float(v) for v in opts["origin"].split(",")) if "origin" in opts else None
    import_terrain(folder, origin=origin, layer=opts.get("layer", "surface"), holdout="holdout" in flags)
    if "save" in opts:
        bpy.ops.wm.save_as_mainfile(filepath=opts["save"])
        print("[reel] saved", opts["save"])


if __name__ == "__main__":
    _main()
