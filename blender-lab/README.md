# MC FILM LAB

A Blender script that makes a Minecraft scene stop reading as "Minecraft with shaders" and start reading as filmed. The pixel textures stay. The world gets a real camera, sculpted light, physical materials, air and a lens.

`standin/lab_renders/` shows every step on a stand-in world (`contact_sheet.jpg` is the quick look).

## Run it on your scene

1. Open your `.blend` in Blender 4.2 or newer. It's tested on 4.5 LTS and 5.2 LTS.
2. Go to the Scripting tab, open `mc_film_lab.py`, and click **Run Script**. Blender blocks while it renders; progress prints to the system console.
   Headless: `blender -b MyWorld.blend --python mc_film_lab.py`
3. The script saves `MyWorld_LAB.blend` next to your file and works only in that copy. Renders go to `//lab_renders/` and the write-up goes to the `LAB_LOG` text block.

Options live in `CONFIG` at the top of the script. You can also pass them as JSON after `--`:

| key | default | what it does |
|---|---|---|
| `hero_camera` | `CAM_Low` | camera used for levers 2–5 (`CAM_Low` / `CAM_Long`) |
| `hour` | `golden` | `golden` or `blue` |
| `hero_structure`, `subject`, `practical_source` | auto | object names that override auto-detection |
| `giant_mesh_policy` | `stop` | if the world is one mesh: `stop` (report, no bevel) or `mask` (bevel near-camera edges through a Geometry Nodes weight mask; still no splitting) |
| `stages` | `all` | e.g. `"light,materials,air,lens,final"`: rebuilds those levers in place when the `_LAB` file is open |
| `render` | `true` | `false` builds every lever without rendering |
| `test` / `final` | 64 samples at 50 % / 128 samples at 100 % | render quality |

Example: `blender -b MyWorld_LAB.blend --python mc_film_lab.py -- '{"hero_camera": "CAM_Long", "stages": "light,materials,air,lens,final"}'`

## Toggle a lever

Everything the script adds lives in `LAB` > `LAB_01_Camera` … `LAB_05_Lens`.

To turn a lever off, click the **camera icon** (Disable in Renders) on its collection. If you don't see the icon, enable it in the Outliner's filter menu. Drivers read that icon, so turning a lever off hides its objects and also flips its settings back: the world strength, the original sun, the Weld/Bevel modifiers, the material mixes and the compositor switch.

There are two exceptions:
- **Lever 1:** set Scene > Camera back to your own camera.
- **Volume shadows:** Blender can't put a driver on this setting, so it stays on. It only affects volumes.

## What each lever does

| lever | what it adds |
|---|---|
| 01 camera | `CAM_Low`: 24 mm, 0.45 m off the ground, prefers corner-on views of the tallest structure. `CAM_Long`: 150 mm from ~35 m, aimed so terrain stacks up behind the subject. Both focus on `FOCUS_target`, CAM_Low at f/2 and CAM_Long at f/2.8, with a 7-blade iris. |
| 02 light | Low sun: every candidate direction is ray-tested against the terrain. World lighting ×0.2, visible sky ×0.4. A warm practical at the nearest torch or lantern, and a cool rim light. |
| 03 materials | Weld at 1 mm, then Bevel (2.5 cm, 2 segments, 30° angle limit, clamp overlap) on near-camera meshes only. Texture filtering stays on Closest. Noise-driven roughness snapped to the 16 px texel grid on stone and wood. Translucency on leaves. |
| 04 air | Ground fog: a height gradient × noise that pools in the low ground, with volume shadows on. 320 emissive dust motes, instanced with Geometry Nodes, drifting through the sun-lit air nearest the focus plane. |
| 05 lens | Compositor group: bloom, halation, chromatic aberration, vignette and grain, all kept below the threshold of noticing. |

If your scene already has fog, wind or a compositor, the script keeps them and layers on top. Your compositor nodes stay upstream of the LAB group, and if you already have bloom it doesn't add a second one.

## Files

- `mc_film_lab.py`: the pipeline.
- `build_standin_scene.py`: builds the stand-in scene used for these renders. It's a Mineways-style export (unwelded chunk meshes, 16 px textures on Closest) with procedural textures, so there are no Mojang assets. Run it with `blender -b --factory-startup --python build_standin_scene.py -- standin`.
- `contact_sheet.py`: builds the labelled contact sheet (needs Pillow).
- `standin/`: the stand-in `.blend`, its `_LAB` copy, textures, renders and `LAB_LOG.txt`.
