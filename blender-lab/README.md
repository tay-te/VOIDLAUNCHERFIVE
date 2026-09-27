# MC FILM LAB

Blender scripts that make a Minecraft scene stop reading as "Minecraft with shaders" and start reading as filmed. The pixel textures stay. The work comes in two layers:

- **The floor** (`mc_shaderpack.py`, lever 0) does what a shader pack does automatically, rebuilt once as node groups you own: PBR maps, animated textures, lamps that emit light, wind, clouds.
- **The ceiling** (`mc_film_lab.py` levers 1–5, plus `mc_looks.py`) covers what a shader pack can't do: camera, sculpted light, physical materials, air, lens, and stylization.

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
| `stages` | `all` | e.g. `"light,materials,air,lens,final"`: rebuilds those levers in place when the `_LAB` file is open. Opt-in extras: `pack`, `look`, `quality`, `cycles` |
| `pack` | `{}` | overrides for the shader pack, e.g. `{"labpbr_dir": "/path/to/pack", "relief": 0.5, "hdri": "sky.exr"}` (see `PACK` in `mc_shaderpack.py`) |
| `look` | `toon+ink` | for stage `look`: any of `toon`, `ink`, `paint` joined with `+` |
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

## Lever 0: the shader pack (`"stages": "baseline,pack,..."`)

Runs right after the baseline render and renders `00b_pack` from your own camera. Its collection is `LAB_00_Pack`.

| part | what it does |
|---|---|
| PBR | Finds LabPBR maps next to each texture (`stone_n.png`, `stone_s.png`) or in `labpbr_dir`. `_n`: normal (converted from DirectX to Blender's OpenGL), AO from blue. `_s`: roughness = (1 − smoothness)², F0 → IOR, green 230–255 = metal, SSS from blue ≥ 65, emission from alpha < 255. Solid blocks without a normal map get **per-texel relief**: every pixel becomes a tiny bevelled tile, brighter pixels stand taller, fading out between 6 and 30 m from the camera. |
| Animated textures | Vertical strips (water, lava, fire…) play at 20 ticks/s using the frametime from their `.mcmeta` file. Works whether faces map the whole strip or only frame 0. |
| Lights | Torches and lanterns get point lights. Glowing blocks get an area light on each exposed face, so they can't leak through walls. Lights are shared per type (edit one, retune all), capped at the nearest 48 to the camera. If a light is already there (yours, or MCprep's Meshswap), the script skips it. |
| Wind | Geometry Nodes sway: leaves flutter, grass tips move, bases stay pinned. Objects that already have a wind modifier are left alone. |
| Sky | A cloud deck in the world shader over your sky or an HDRI. It's self-shadowed toward the sun, gets a silver lining near it, and warms at a low sun. It follows the sky texture's sun, so lever 2 re-aims it too. |

Volumetric clouds are out on purpose: EEVEE's froxel fog can't resolve clouds 1–2 km away, and a world-shader deck costs nothing and matches in Cycles.

Asset library: `blender -b --factory-startup --python mc_shaderpack.py -- --export mc_shaderpack_library.blend`, then add the file under Preferences > File Paths > Asset Libraries.

## Stage `look`: stylization

The look stage (collection `LAB_07_Look`) applies on top of the filmed shot and renders `09_look_<name>` from the hero camera.

- **`toon`**: Shader to RGB banding, EEVEE only. There are three light bands measured relative to the key light, so the shot keeps its exposure. Each light keeps its colour, shadows lean cool, there's a hard specular band, and lamps are left glowing.
- **`ink`**: Line Art outlines on Blender 5.x, with a 2.5 cm world-space pen, so lines are heavier up close. On 4.x a compositor edge ink stands in, because Line Art can't be created from Python there.
- **`paint`**: anisotropic Kuwahara before the lens effects, so the grain sits on the "paint".

## Optional stages: `quality` and `cycles`

Neither runs by default. Add them with `"stages": "quality,cycles"`.

- **`quality`** (collection `LAB_06_Quality`) bakes a Light Probe Volume over what the hero camera sees. It also turns on 3 % overscan, traces rough surfaces up to 0.8, runs Fast GI at full resolution, uses 2 px volume tiles and turns on jittered DOF. It renders `07_eevee_quality`.
- **`cycles`** renders the same frame path-traced as `08_cycles`. It uses the GPU if one is configured, and the saved scene stays on EEVEE.

What the comparison showed on the stand-in, with Cycles as the reference:

- **Sky leak:** plain EEVEE lets sky light fall where terrain and trees should block it. The shadow under the big tree measured 16 in EEVEE against 3.6 in Cycles (0–255 brightness). The baked probe fixes most of that.
- **Bounce light:** EEVEE misses most of it. In Cycles, the tower's shaded face picks up warm light reflected off the sunlit ground.
- **Low sun through haze:** Cycles dims a low sun as it crosses the haze. EEVEE's fog doesn't dim light arriving at surfaces, so the sunlit face comes out about 35 % brighter in EEVEE.

After changing the light, re-bake the probe: select `LAB_GIProbe`, then Object Data > Bake Light Cache.

## Files

- `mc_film_lab.py`: the pipeline.
- `mc_shaderpack.py`: lever 0, the shader pack. It's also runnable on its own and can export an asset library.
- `mc_looks.py`: stylization (toon, ink, paint).
- `build_standin_scene.py`: builds the stand-in scene used for these renders. It's a Mineways-style export (unwelded chunk meshes, 16 px textures on Closest) with procedural textures, so there are no Mojang assets. Run it with `blender -b --factory-startup --python build_standin_scene.py -- standin`.
- `contact_sheet.py`: builds the labelled contact sheet (needs Pillow).
- `standin/`: the stand-in `.blend`, its `_LAB` copy, textures, renders and `LAB_LOG.txt`.
