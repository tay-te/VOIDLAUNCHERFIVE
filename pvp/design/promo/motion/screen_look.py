"""Look-dev: rebuild a Figma screen natively and light it like a premium product render.

  Blender -b --factory-startup --python screen_look.py -- SCREEN OUTDIR [RES_PCT] [SAMPLES] [VIEW]
  SCREEN: play | mods | setup     VIEW: AgX | Standard
"""
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib, vmg as V, figma_build as FB
importlib.reload(FB)
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SCREEN = argv[0] if argv else "play"
OUT = argv[1] if len(argv) > 1 else os.path.join(V.HERE, "out", "look")
PCT = int(argv[2]) if len(argv) > 2 else 50
SAMPLES = int(argv[3]) if len(argv) > 3 else 64
VIEW = argv[4] if len(argv) > 4 else "AgX"

sc = V.reset(1.0, pct=PCT, samples=SAMPLES)
e = sc.eevee
for k, v in dict(use_raytracing=True, ray_tracing_method="SCREEN", use_shadows=True, shadow_ray_count=2,
                 shadow_step_count=8, use_fast_gi=True, fast_gi_method="GLOBAL_ILLUMINATION", fast_gi_distance=0.25).items():
    V.setp(e, k, v)
V.setp(e.ray_tracing_options, "trace_max_roughness", 0.6)
sc.view_settings.view_transform = VIEW
if VIEW == "AgX":
    V.setp(sc.view_settings, "look", "AgX - Base Contrast")
sc.render.filter_size = 1.2

w = sc.world
bg = w.node_tree.nodes["Background"]
bg.inputs["Color"].default_value = V.lin("#060607")

G = V.coll("Screen")
scr = FB.Screen(os.path.join(V.HERE, "figma", f"{SCREEN}.txt"), G, name=f"Screen_{SCREEN}")
scr.root.rotation_euler = (math.radians(90), 0, 0)     # stand the screen up, facing -Y
scr.root.location = (0, 0, 0.49)

L = V.coll("Lights")


def area(name, power, loc, size, sy=None, col="#FFFFFF", target=(0, 0, 0.49), spread=None):
    ld = bpy.data.lights.new(name, "AREA")
    ld.energy = power
    ld.color = V.lin(col)[:3]
    ld.shape = "RECTANGLE" if sy else "SQUARE"
    ld.size = size
    if sy:
        ld.size_y = sy
    if spread:
        ld.spread = math.radians(spread)
    o = bpy.data.objects.new(name, ld)
    L.objects.link(o)
    o.location = loc
    V.look_at(o, target)
    return o


area("Key", 55, (-1.4, -1.6, 1.7), 1.4, 0.9, "#FFF6EC")
area("Fill", 10, (1.8, -1.4, 0.6), 1.6, 1.0, "#E8EEFF")
area("Top", 70, (0.0, 0.25, 1.55), 2.2, 0.12, "#FFFFFF", target=(0, 0.2, 0.9))
area("Graze", 60, (1.9, -0.25, 0.49), 0.08, 1.2, "#FFFFFF", spread=40)

cam = V.camera("Cam", G, lens=50.0, clip_start=0.05, clip_end=50.0)
cam.data.dof.use_dof = True
os.makedirs(OUT, exist_ok=True)
shots = {
    "front": dict(loc=(0.0, -2.95, 0.49), tgt=(0.0, 0.0, 0.49), lens=50.0, f=16.0, focus=(0, 0, 0.49)),
    "hero": dict(loc=(-1.05, -1.55, 0.95), tgt=(0.1, 0.0, 0.36), lens=55.0, f=2.8, focus=(-0.05, -0.006, 0.1)),
    "macro": dict(loc=(-0.30, -0.62, 0.30), tgt=(0.02, 0.0, 0.08), lens=70.0, f=2.0, focus=(0.01, -0.006, 0.075)),
}
for name, s in shots.items():
    cam.location = s["loc"]
    V.look_at(cam, s["tgt"], up=(0, 0, 1))
    cam.data.lens = s["lens"]
    cam.data.dof.aperture_fstop = s["f"]
    cam.data.dof.focus_distance = (Vector(s["focus"]) - Vector(s["loc"])).length
    sc.render.filepath = os.path.join(OUT, f"{SCREEN}_{name}_{VIEW}.png")
    bpy.ops.render.render(write_still=True)
V.save(os.path.join(OUT, f"{SCREEN}_look.blend"))
