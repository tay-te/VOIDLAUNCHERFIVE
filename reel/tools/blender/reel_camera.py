"""
Brings a Reel shot's camera into Blender, so anything built there (labels on the hills, rings on the land, a map
that becomes the valley) lines up with the filmed frames to the pixel.

    blender -b [scene.blend] -P tools/blender/reel_camera.py -- renders/<shot>/camera.json
        [--origin x,y,z]     the Minecraft block that becomes Blender's origin (default: the first camera position,
                             rounded to 16); keep one origin for every shot of a place so their overlays share it
        [--plate DIR]        show the shot's frames (DIR/*.png) behind the camera in the viewport
        [--check x,y,z]      print where that Minecraft point lands in the frame at the first, middle and last frame
        [--save FILE.blend]  save the scene

Or from Blender's own Python: exec this file, then import_camera(path, origin=None).

Conventions. Reel writes Minecraft's (x east, y up, z south, a block a metre). Blender is z up, so a Minecraft
point (x, y, z) is Blender's (x, -z, y) less the origin: Minecraft north is Blender +y. Minecraft draws the view
as rotationYXZ(pi - yaw, -pitch, roll) with the camera looking down its own -z and +y up, which is exactly a Blender
camera's own axes, so the camera's rotation is that same rotation turned by the one fixed change of axes. The
field of view is vertical. Frames keep Reel's numbers: Blender frame f is Reel frame f (from 0).
"""
import json
import math
import sys

import bpy
from mathutils import Matrix, Vector

# Minecraft world axes to Blender's: a quarter turn about x, (x, y, z) -> (x, -z, y)
MC_TO_BLENDER = Matrix.Rotation(math.radians(90), 4, 'X')
SENSOR_HEIGHT = 24.0


def mc_to_blender(x, y, z, origin=(0, 0, 0)):
    """A Minecraft position as a Blender location, relative to the origin block."""
    return Vector((x - origin[0], -(z - origin[2]), y - origin[1]))


def scene_origin(scene=None):
    """The Minecraft block the scene's Blender origin stands for (set by import_camera)."""
    scene = scene or bpy.context.scene
    return tuple(scene.get("reel_origin", (0, 0, 0)))


def view_rotation(yaw, pitch, roll):
    """The Blender rotation of a camera with Minecraft's yaw, pitch and roll (degrees)."""
    r = (Matrix.Rotation(math.pi - math.radians(yaw), 4, 'Y')
         @ Matrix.Rotation(-math.radians(pitch), 4, 'X')
         @ Matrix.Rotation(math.radians(roll), 4, 'Z'))
    return MC_TO_BLENDER @ r


def lens_for(fov):
    """Focal length (mm) giving this vertical field of view on the 24 mm sensor height."""
    return (SENSOR_HEIGHT / 2) / math.tan(math.radians(fov) / 2)


def import_camera(path, origin=None, name=None, plate=None):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    rows = data["camera"]
    if origin is None:
        x, y, z = rows[0][1:4]
        origin = (16 * math.floor(x / 16), 16 * math.floor(y / 16), 16 * math.floor(z / 16))
    scene = bpy.context.scene
    scene["reel_origin"] = list(origin)
    scene.render.fps = int(data["fps"])
    scene.render.fps_base = 1
    scene.render.resolution_x, scene.render.resolution_y = data["size"]
    scene.render.resolution_percentage = 100
    scene.frame_start = 0
    scene.frame_end = len(rows) - 1

    name = name or "Reel " + data["shot"]
    cam_data = bpy.data.cameras.get(name) or bpy.data.cameras.new(name)
    cam_data.sensor_fit = 'VERTICAL'
    cam_data.sensor_height = SENSOR_HEIGHT
    cam_data.sensor_width = SENSOR_HEIGHT * data["size"][0] / data["size"][1]
    cam_data.clip_start = 0.05
    cam_data.clip_end = 20000
    cam = bpy.data.objects.get(name) or bpy.data.objects.new(name, cam_data)
    if cam.name not in scene.collection.all_objects:
        scene.collection.objects.link(cam)
    cam.rotation_mode = 'XYZ'
    cam.animation_data_clear()
    cam_data.animation_data_clear()
    cam["reel_shot"] = data["shot"]
    cam["reel_origin"] = list(origin)

    last = None
    for frame, (t, x, y, z, yaw, pitch, roll, fov) in enumerate(rows):
        cam.location = mc_to_blender(x, y, z, origin)
        euler = view_rotation(yaw, pitch, roll).to_euler('XYZ', last) if last else view_rotation(yaw, pitch, roll).to_euler('XYZ')
        cam.rotation_euler = euler
        last = euler
        cam_data.lens = lens_for(fov)
        cam.keyframe_insert("location", frame=frame)
        cam.keyframe_insert("rotation_euler", frame=frame)
        cam_data.keyframe_insert("lens", frame=frame)
    for owner in (cam, cam_data):
        for fcurve in _fcurves(owner):
            for key in fcurve.keyframe_points:
                key.interpolation = 'LINEAR'
    scene.camera = cam

    if plate:
        _plate(cam_data, plate, len(rows))
    return cam


def _fcurves(owner):
    """Every f-curve of an object's action, in both the old and the layered (Blender 4.4+) action layouts."""
    anim = owner.animation_data
    if not anim or not anim.action:
        return []
    action = anim.action
    if hasattr(action, "fcurves") and len(action.fcurves):
        return list(action.fcurves)
    curves = []
    for layer in getattr(action, "layers", []):
        for strip in layer.strips:
            for bag in getattr(strip, "channelbags", []):
                curves.extend(bag.fcurves)
    return curves


def _plate(cam_data, folder, count):
    import glob
    import os
    files = sorted(glob.glob(os.path.join(folder, "*.png")))
    if not files:
        print("[reel] no frames in", folder)
        return
    image = bpy.data.images.load(files[0], check_existing=True)
    image.source = 'SEQUENCE'
    cam_data.show_background_images = True
    bg = cam_data.background_images.new() if not cam_data.background_images else cam_data.background_images[0]
    bg.image = image
    bg.image_user.frame_duration = count
    bg.image_user.frame_start = 0
    bg.image_user.frame_offset = -1


def check(cam, point, origin):
    """Where a Minecraft point lands in the frame (pixels from the top left) at the first, middle and last frame."""
    from bpy_extras.object_utils import world_to_camera_view
    scene = bpy.context.scene
    w, h = scene.render.resolution_x, scene.render.resolution_y
    p = mc_to_blender(*point, origin)
    for frame in (scene.frame_start, (scene.frame_start + scene.frame_end) // 2, scene.frame_end):
        scene.frame_set(frame)
        u = world_to_camera_view(scene, cam, p)
        print("[reel] frame %d: %s at (%.1f, %.1f) px, depth %.1f" % (frame, point, u.x * w, (1 - u.y) * h, u.z))


def _main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not argv:
        return
    path = argv[0]
    opts = {}
    i = 1
    while i < len(argv):
        opts[argv[i].lstrip("-")] = argv[i + 1]
        i += 2
    origin = tuple(float(v) for v in opts["origin"].split(",")) if "origin" in opts else None
    cam = import_camera(path, origin=origin, plate=opts.get("plate"))
    origin = scene_origin()
    print("[reel] camera %s: %d frames, origin %s" % (cam.name, bpy.context.scene.frame_end + 1, origin))
    if "check" in opts:
        check(cam, tuple(float(v) for v in opts["check"].split(",")), origin)
    if "save" in opts:
        bpy.ops.wm.save_as_mainfile(filepath=opts["save"])
        print("[reel] saved", opts["save"])


if __name__ == "__main__":
    _main()
