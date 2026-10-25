"""
Brings a recorded movie's people into Blender (renders/<shot>/actors.json, from `reel.sh render shot=.. actors=96`):
every actor's model rebuilt from its textured cubes in its own skin, one object per part, keyed on every frame to
where the game drew it, so the game's own animation (walking, looking, Hearth's work poses) plays back exactly. The
same space as reel_world.py and reel_camera.py, given the export's origin; parented to one empty ("actors") that a
model-scale scene scales like the world.

    import reel_actors; root = reel_actors.load(movie_dir, origin, scale=S, style="toon")

Part objects sit at their part's pivot, so a recorded actor can also be posed or re-animated by hand.
"""
import json
import os

import bpy
import numpy as np
from mathutils import Matrix, Quaternion

import reel_world

# Minecraft's axes to Blender's: (x, y, z) -> (x, -z, y)
AXES = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))


def part_mesh(name, faces):
    """A part's faces (four corners of x, y, z, u, v each, in blocks in the part's own space) as a mesh."""
    verts, polys, uvs = [], [], []
    for face in faces:
        corners = np.array(face, np.float32).reshape(-1, 5)
        base = len(verts)
        verts.extend(corners[:, :3].tolist())
        polys.append(tuple(range(base, base + len(corners))))
        uvs.extend([(u, 1 - v) for u, v in corners[:, 3:5]])
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    layer = me.uv_layers.new(name="UVMap")
    layer.data.foreach_set("uv", np.array(uvs, np.float32).ravel())
    me.update()
    return me


def skin_material(movie, skin, style):
    m = bpy.data.materials.new(os.path.splitext(os.path.basename(skin))[0] if skin else "actor")
    m.use_nodes = True
    # reel_world's material for a block texture: crisp pixels, alpha cut out (the hat and sleeve layers), toon or real;
    # a skin has no biome tint, so the vertex colour it multiplies by is left white
    reel_world.rebuild(m, movie, glow=0.0, style=style, image=os.path.join(movie, skin) if skin else None)
    for n in m.node_tree.nodes:
        if n.type == "VERTEX_COLOR":
            for link in list(n.outputs["Color"].links):
                link.to_socket.default_value = (1, 1, 1, 1)
                m.node_tree.links.remove(link)
    return m


def load(movie, origin, scale=1.0, style="toon", frame_start=1, radius=None, centre=None):
    """Every recorded actor (or those within `radius` blocks of `centre`, Minecraft x z, on their first frame)."""
    data = json.load(open(os.path.join(movie, "actors.json")))
    sc = bpy.context.scene
    sc.render.fps = data["fps"]
    root = bpy.data.objects.new("actors", None)
    sc.collection.objects.link(root)
    root.scale = (scale, scale, scale)
    to_scene = AXES @ Matrix.Translation((-origin[0], -origin[1], -origin[2]))
    meshes, mats = {}, {}
    frames_total = data["frames"]
    kept = 0
    for actor in data["actors"]:
        frames = {int(f): parts for f, parts in actor["frames"].items()}
        if not frames:
            continue
        if radius is not None and centre is not None:
            first = frames[min(frames)]
            any_part = next(iter(first.values()))
            if (any_part[9] - centre[0]) ** 2 + (any_part[11] - centre[1]) ** 2 > radius * radius:
                continue
        kept += 1
        skin = actor.get("skin")
        if skin not in mats:
            mats[skin] = skin_material(movie, skin, style)
        model = data["models"][actor["model"]]
        for path, faces in model.items():
            key = (actor["model"], path)
            if key not in meshes:
                meshes[key] = part_mesh("%s%s" % (actor["model"], path.replace("/", ".")), faces)
            me = meshes[key].copy() if meshes[key].users else meshes[key]
            ob = bpy.data.objects.new("%s %s%s" % (actor["name"] or actor["type"], actor["id"], path.replace("/", ".")), me)
            ob.data.materials.clear()
            ob.data.materials.append(mats[skin])
            ob.parent = root
            ob.rotation_mode = "QUATERNION"
            sc.collection.objects.link(ob)
            key_part(ob, path, frames, frames_total, to_scene, frame_start)
    print("[reel_actors] %d actors, %d models from %s" % (kept, len(data["models"]), movie))
    return root


def key_part(ob, path, frames, total, to_scene, frame_start):
    """The part's matrix on each recorded frame; shrunk to nothing on frames it was not seen."""
    loc = np.zeros((total, 3), np.float32)
    rot = np.zeros((total, 4), np.float32)
    rot[:, 0] = 1
    size = np.zeros((total, 3), np.float32)
    last = None
    for f in range(total):
        parts = frames.get(f)
        m = parts.get(path) if parts else None
        if m is None:
            continue
        mc = Matrix(((m[0], m[3], m[6], m[9]), (m[1], m[4], m[7], m[10]), (m[2], m[5], m[8], m[11]), (0, 0, 0, 1)))
        t, q, s = (to_scene @ mc).decompose()
        if last is not None and q.dot(last) < 0:
            q.negate()
        last = q
        loc[f], rot[f], size[f] = t, q, s
    curves = fcurves_for(ob)
    frame_numbers = np.arange(total, dtype=np.float32) + frame_start
    for prop, values in (("location", loc), ("rotation_quaternion", rot), ("scale", size)):
        for i in range(values.shape[1]):
            fc = curves.new(prop, index=i)
            fc.keyframe_points.add(total)
            fc.keyframe_points.foreach_set("co", np.column_stack([frame_numbers, values[:, i]]).ravel())
            fc.keyframe_points.foreach_set("interpolation", np.full(total, 1, np.int32))  # linear
            fc.update()


def fcurves_for(ob):
    """A fresh action's curves for the object: in a slot's channel bag (Blender 4.4 and on), else the old list."""
    ob.animation_data_create()
    action = bpy.data.actions.new(ob.name)
    ob.animation_data.action = action
    if hasattr(action, "slots"):
        from bpy_extras import anim_utils
        slot = action.slots.new(id_type="OBJECT", name=ob.name)
        ob.animation_data.action_slot = slot
        return anim_utils.action_ensure_channelbag_for_slot(action, slot).fcurves
    return action.fcurves
