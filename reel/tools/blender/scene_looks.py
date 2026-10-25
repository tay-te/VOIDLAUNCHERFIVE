"""
Stylized looks over a reel.sh export, one script, a look per run.

Light and weather:
  storybook  warm low sun against a cool sky, light haze, tilt-shift miniature (the first liked look)
  bluehour   deep blue evening, every lantern a real warm light, cool haze
  mist       morning fog lying in the streets, roofs and towers standing out of it, low sun through it
  alpenglow  winter: snow on every roof and ledge, a low pink-gold sun raking across it, blue shadows, lanterns lit
  autumn     the broadleaf trees turned red, orange and gold, the grass gone to straw, a warm low sun behind them
Styles:
  island     an export cut as an island (reel.sh export cut=island) floating on a dark stage, lit like a studio model
  clay       a hand-made clay miniature: each block its texture's average colour, edges softly rounded
  ink        an illustration: ink outlines on toon colour, on cream paper (best on an island export)
  pixel      the storybook look as pixel art: a quarter the resolution, a small palette, hard pixels
Minecraft Legends (style "legends": painted blocks with dark painted borders, rich colour; lit gold on blue-purple):
  legends     the look's light; use with --cam/--at, an island, or --land for any shot
Scenes:
  island-snow the floating island under snow at night, warm windows and lanterns, snow drifting round it
  street      down a Bradhurst street at dusk, at eye height, the lanterns lit
  forest      in the wood south of the town, a low sun behind the trees, light falling through haze
Vistas (an export at full size in the middle of the land around it, to the horizon: reel_land.py):
  vista      Bradhurst across its valley at golden hour, the mountains to the north-west behind it
  vista-mist the same at sunrise, fog lying in the low land, roofs and hills standing out of it
  vista-high high over the town toward the snowy peaks to the north-west, an afternoon sky
Tales (a small island export of one place, on a dark stage tinted to its hour, in the animated-film style):
  tale-dawn  first light and ground mist      tale-forge  blue hour, the forge burning   tale-dusk  the last of the sun
  tale-gold  golden hour                      tale-candle night, by candlelight          tale-day   a clear afternoon
  tale-clay  a clay model on grey

    blender -b --factory-startup -P tools/blender/scene_looks.py -- EXPORT_DIR OUT.png --look bluehour
        [--cam x,y,z --at x,y,z]   Minecraft coordinates (defaults: the look's own view, else the Bradhurst diorama);
                                   on an island look, a close-up in place of the whole island
        [--lens 35 --fstop 5.6]    the look's lens and aperture
        [--paint 6]                finish the picture as a painting: strokes about that many pixels (at 1920) across
        [--preview 1]              half size, few samples
        [--style anim]             the look's light with another material style (toon, clay, anim)
        [--land lod:NAME]          the land around the export from these sources instead (grid names or lod:NAME, + between)
        [--build 0.6]              the town part-built: what stands above the bare land (an export's bare.f32) goes up
                                   from the middle outward, each building from the ground up; 0 is the bare clearing
        [--frames 144 --orbit 25]  a movie of the town going up, OUT a folder of frames, the camera turning as it goes
"""
import json
import time
import math
import os
import sys

import bmesh
import bpy
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import reel_camera as rc  # noqa: E402
import reel_world  # noqa: E402
import voxkit as vk  # noqa: E402

argv = sys.argv[sys.argv.index("--") + 1:]
EXPORT, OUT = argv[0], argv[1]
opt = {argv[i].lstrip("-"): argv[i + 1] for i in range(2, len(argv) - 1, 2)}
LOOK = opt.get("look", "storybook")
PREVIEW = opt.get("preview") == "1"
vec = lambda s: tuple(float(v) for v in s.split(","))

# sun: (elevation, colour, strength, angle), from "back" (behind the subject), "side" or "front" (behind the camera)
# sky: (colour, strength); lantern: watts as at full size; frame: "view" (cam/at) or "island" (its own silhouette)
# haze: "falloff" thins with height above the water (or above `floor`); "layer" is a fog bank with a soft top at a
#   world height, its top heaved by `billow` blocks of noise; `box` (y0, y1, half width) sets the volume's extent,
#   `centre` (Minecraft x, z) where it stands (default: the export's middle)
# sky_ramp: a sky from the horizon (0) to overhead (1) instead of one colour; fill: how much of the frame an island fills
# backdrop: what the camera sees behind it, while the sky still lights it; azimuth: the island camera's bearing
#   (degrees from east, counter-clockwise seen from above: -90 looks north from the south)
# key art: rim (colour, strength, elevation) a back light that outlines the silhouette; under (colour, strength) light
#   from below on the rock; ao (0..1) contact shadow between blocks; flatten (0..1) each block toward its texture's
#   average colour, so it reads as colour rather than pixel noise; pool (gain) a soft lighter patch of backdrop behind
#   the subject; finish: bloom (threshold, strength, radius as a share of the width), shadows and highlights tints,
#   split (how much), vignette, grain, all on the finished picture; outline: ink where the depth or the surface's
#   facing breaks (silhouettes, block edges, steps), from two quick extra renders; finish palette: (hues in degrees,
#   strength) pulls the picture's colours toward a few chosen hues, as an art-directed film does; paint: a brush size
#   in pixels at 1920 wide, the picture smoothed within strokes but not across edges (a Kuwahara filter)
# land: terrain grids (renders/_terrain/NAME), finest first, built as block columns around the export to the horizon;
#   clip: how far the camera sees, in blocks
# wet: rain on the film style: every face that looks up turns glossy and a little darker, so lights pool in it;
#   ortho: an orthographic camera this many blocks across (a map-like view without perspective)
# windows: how bright rooms glow through glass (film style); motes: (count, colour, strength) glowing specks drifting
#   in the light around an island (dust, sparks, fireflies), soft in the lens
# style: "toon", "clay", "anim" (Minecraft as an animated film: soft bounced light, rounded edges, foliage that lets
#   light through) or "legends" (anim made painterly as Minecraft Legends is: the texture's noise painted out, each
#   face edged in a darker painted border, tops lit a little brighter, colour pushed); season: None, "winter" or "autumn"; lines: ink outlines; grade: "paper" or "pixel"
BASE = dict(scale=0.02, frame="view", sun=None, sun_from="back", sky=((0.36, 0.5, 0.75), 1.1), haze=None, studio=False,
            lantern=0, glow=5.0, exposure=0.0, fstop=2.0, lens=55, look="AgX - Punchy", style="toon", season=None,
            lines=None, grade=None, sky_ramp=None, fill=0.68, elevation=17, azimuth=-48, backdrop=None, rim=None, under=None,
            ao=0.0, flatten=0.0, pool=None, finish=None, outline=None, windows=0.0, motes=None, land=None, clip=4000,
            wet=False, ortho=None)
FINISH = dict(bloom=(0.62, 0.55, 0.02), shadows=(0.1, 0.16, 0.32), highlights=(1.0, 0.86, 0.66), split=0.1, vignette=0.28, grain=0.008)
OUTLINE = dict(depth=0.025, normal=0.75, strength=0.5, width=1)
TALE = dict(scale=0.02, frame="island", fill=0.74, elevation=24, lens=70, fstop=4.0, style="anim", ao=0.3, flatten=0.0, pool=1.7,
            finish=dict(FINISH, bloom=(0.6, 0.75, 0.022), split=0.08, vignette=0.18, grain=0.0))
film = lambda hues, **kw: dict(TALE["finish"], palette=(hues, 0.22), **kw)
LOOKS = {
    "storybook": dict(sun=(17, (1.0, 0.8, 0.55), 5.5, 1.5), sky=((0.36, 0.5, 0.75), 1.1),
                      haze=dict(kind="falloff", density=0.0025, colour=(1.0, 0.9, 0.78), falloff=-0.025, floor=64, bounces=0)),
    "bluehour": dict(sun=(26, (0.55, 0.68, 1.0), 0.45, 6), sky=((0.04, 0.08, 0.24), 1.25),
                     haze=dict(kind="falloff", density=0.006, colour=(0.5, 0.6, 0.9), falloff=-0.05),
                     lantern=30, glow=10.0, exposure=1.1, look="AgX - Medium High Contrast"),
    "mist": dict(sun=(8, (1.0, 0.76, 0.5), 5.5, 3), sky=((0.46, 0.58, 0.8), 1.15),
                 haze=dict(kind="layer", density=0.05, colour=(1.0, 0.97, 0.94), top=99, soft=4, above=0.004, anisotropy=0.3),
                 cam="-3655,113,-3500", at="-3795,109,-3612", exposure=0.45, fstop=2.8, look="AgX - Medium Low Contrast"),
    "alpenglow": dict(sun=(9, (1.0, 0.5, 0.34), 8.0, 2), sun_from="side", sky=((0.22, 0.32, 0.62), 1.1),
                      haze=dict(kind="falloff", density=0.003, colour=(1.0, 0.82, 0.84), falloff=-0.03),
                      season="winter", lantern=30, glow=8.0, exposure=0.4),
    "autumn": dict(sun=(14, (1.0, 0.76, 0.48), 5.0, 2), sky=((0.4, 0.52, 0.75), 1.0),
                   haze=dict(kind="falloff", density=0.0015, colour=(1.0, 0.85, 0.65), falloff=-0.03, bounces=0),
                   cam="-3700,175,-3380", at="-3780,100,-3510", lens=60, fstop=4.0, season="autumn", exposure=0.1),
    "island": dict(scale=0.005, frame="island", studio=True, sky=((0.012, 0.014, 0.02), 1.0), lantern=12, glow=10.0,
                   exposure=0.6, fstop=2.8, lens=70, windows=0.6, motes=(140, (1.0, 0.82, 0.55), 4.0),
                   finish=dict(FINISH, palette=((30, 95, 210), 0.2), bloom=(0.6, 0.6, 0.02), split=0.06, vignette=0.15, grain=0.0)),
    "clay": dict(sun=(32, (1.0, 0.9, 0.78), 4.0, 14), sun_from="front", sky=((0.62, 0.68, 0.78), 1.0),
                 haze=dict(kind="falloff", density=0.002, colour=(1.0, 0.95, 0.88), falloff=-0.025),
                 style="clay", exposure=0.2, look="AgX - Base Contrast"),
    "ink": dict(scale=0.005, frame="island", sun=(40, (1.0, 0.95, 0.86), 3.5, 8), sun_from="side",
                sky=((0.86, 0.8, 0.68), 1.0), fstop=4.0, lens=70, look="AgX - Base Contrast",
                lines=dict(colour=(0.14, 0.1, 0.07), thickness=1.4), grade="paper"),
    "island-snow": dict(scale=0.005, frame="island", style="anim", season="winter", fill=0.7, elevation=20, lens=70, fstop=2.8,
                        sun=(35, (0.55, 0.66, 1.0), 0.7, 4), sun_from="side", sky=((0.05, 0.07, 0.15), 1.3),
                        backdrop=(0.01, 0.013, 0.025), pool=1.7, lantern=14, glow=10.0, windows=1.6, ao=0.3, exposure=1.7,
                        rim=((0.5, 0.62, 1.0), 1.2, 25), under=((1.0, 0.6, 0.35), 0.15),
                        motes=(500, (0.9, 0.94, 1.0), 1.5, 4.0),
                        finish=dict(FINISH, palette=((215, 30), 0.2), bloom=(0.6, 0.7, 0.022), split=0.08, vignette=0.2, grain=0.0)),
    "legends": dict(scale=1.0, style="legends", lens=35, fstop=5.6, sun=(30, (1.0, 0.76, 0.42), 10.0, 2), sun_from="side",
                    sky=((0.34, 0.36, 0.75), 0.8),
                    sky_ramp=((0.0, (0.75, 0.9, 1.0)), (0.12, (0.5, 0.72, 0.98)), (0.45, (0.3, 0.5, 0.95)), (1.0, (0.2, 0.3, 0.8))),
                    rim=((1.0, 0.84, 0.55), 5.0, 18), under=((0.45, 0.4, 0.9), 0.5),
                    haze=dict(kind="falloff", density=0.0006, colour=(0.45, 0.72, 0.9), falloff=-0.008, floor=90, box=(60, 300, 400), bounces=0),
                    lantern=20, windows=0.5, ao=0.45, exposure=0.25,
                    finish=dict(FINISH, palette=((42, 115, 215, 270), 0.25), shadows=(0.3, 0.22, 0.62), highlights=(1.0, 0.85, 0.55),
                                bloom=(0.6, 0.6, 0.025), split=0.22, vignette=0.18, grain=0.0)),
    "rain": dict(scale=1.0, style="anim", wet=True, cam="-3804.5,95.6,-3689.5", at="-3774.5,97.1,-3689.5", lens=28, fstop=4.0,
                 sun=(30, (0.6, 0.68, 0.85), 0.6, 20), sky=((0.16, 0.2, 0.28), 1.0),
                 haze=dict(kind="falloff", density=0.008, colour=(0.6, 0.66, 0.76), falloff=-0.02, floor=90, box=(80, 160, 260), bounces=0),
                 lantern=40, glow=9.0, windows=1.4, ao=0.3, exposure=1.0,
                 finish=dict(FINISH, palette=((212, 28), 0.25), bloom=(0.55, 0.9, 0.025), split=0.1, vignette=0.25, grain=0.0)),
    "overcast": dict(scale=1.0, style="anim", lens=40, fstop=8.0, sun=(50, (1.0, 0.98, 0.95), 1.5, 45),
                     sky=((0.72, 0.76, 0.82), 1.6), lantern=0, ao=0.35, exposure=0.1, look="AgX - Base Contrast",
                     haze=dict(kind="falloff", density=0.003, colour=(0.85, 0.88, 0.92), falloff=-0.02, floor=90, box=(80, 200, 260), bounces=0),
                     finish=dict(FINISH, palette=((40, 110, 205), 0.15), bloom=None, split=0.05, vignette=0.12, grain=0.0)),
    "sunset": dict(scale=1.0, style="anim", lens=50, fstop=5.6, sun=(3, (1.0, 0.5, 0.25), 10.0, 1.5), sun_from="back",
                   sky=((0.35, 0.3, 0.45), 0.8),
                   sky_ramp=((0.0, (1.0, 0.45, 0.2)), (0.06, (1.0, 0.62, 0.38)), (0.25, (0.55, 0.42, 0.6)), (1.0, (0.16, 0.18, 0.36))),
                   haze=dict(kind="falloff", density=0.006, colour=(1.0, 0.6, 0.35), falloff=-0.02, floor=90, box=(80, 220, 260), bounces=0,
                             anisotropy=0.75),
                   lantern=25, windows=0.8, ao=0.3, exposure=0.3,
                   finish=dict(FINISH, palette=((22, 300), 0.2), bloom=(0.55, 0.8, 0.03), split=0.1, vignette=0.25, grain=0.0)),
    "moon": dict(scale=1.0, style="anim", lens=35, fstop=4.0, sun=(40, (0.5, 0.62, 1.0), 0.35, 1), sun_from="side",
                 sky=((0.02, 0.03, 0.07), 1.0), lantern=35, glow=10.0, windows=1.6, ao=0.3, exposure=2.0,
                 finish=dict(FINISH, palette=((218, 32), 0.25), bloom=(0.55, 0.8, 0.025), split=0.08, vignette=0.3, grain=0.0)),
    "fog": dict(scale=1.0, style="anim", lens=35, fstop=4.0, sun=(25, (0.9, 0.92, 1.0), 0.8, 30), sky=((0.42, 0.45, 0.5), 0.8),
                haze=dict(kind="falloff", density=0.03, colour=(0.82, 0.84, 0.88), falloff=-0.01, floor=90, box=(70, 200, 260), bounces=4),
                lantern=45, glow=10.0, windows=1.2, ao=0.3, exposure=0.8, look="AgX - Base Contrast",
                finish=dict(FINISH, palette=((30, 210), 0.3), bloom=(0.5, 1.0, 0.03), split=0.1, vignette=0.3, grain=0.0)),
    "street": dict(scale=1.0, style="anim", cam="-3757,94.6,-3566", at="-3772,97.5,-3608", lens=28, fstop=4.0,
                   sun=(5, (1.0, 0.62, 0.4), 5.0, 4), sun_from="back", sky=((0.3, 0.38, 0.6), 0.8),
                   sky_ramp=((0.0, (1.0, 0.6, 0.38)), (0.1, (0.85, 0.6, 0.55)), (0.35, (0.36, 0.42, 0.66)), (1.0, (0.12, 0.16, 0.34))),
                   haze=dict(kind="falloff", density=0.004, colour=(1.0, 0.82, 0.68), falloff=-0.03, floor=92, box=(85, 140, 70),
                             centre=(-3770, -3590), bounces=0),
                   lantern=30, glow=8.0, windows=1.2, ao=0.3, exposure=0.6,
                   finish=dict(FINISH, palette=((24, 220), 0.2), bloom=(0.58, 0.8, 0.02), split=0.08, vignette=0.2, grain=0.0)),
    "forest": dict(scale=1.0, style="anim", cam="-3818,102.8,-3418", at="-3832,106,-3458", lens=28, fstop=2.8,
                   sun=(7, (1.0, 0.72, 0.45), 8.0, 1.5), sun_from="back", sky=((0.4, 0.5, 0.7), 0.5),
                   haze=dict(kind="falloff", density=0.014, colour=(1.0, 0.9, 0.75), falloff=-0.04, floor=100, box=(95, 135, 70),
                             centre=(-3825, -3445), bounces=0, anisotropy=0.75),
                   ao=0.3, exposure=0.4,
                   finish=dict(FINISH, palette=((36, 110), 0.18), bloom=(0.6, 0.6, 0.025), split=0.06, vignette=0.22, grain=0.0)),
    "vista": dict(scale=1.0, style="anim", cam="-3640,138,-3440", at="-3840,112,-3700", lens=32, fstop=11.0,
                  sun=(9, (1.0, 0.7, 0.45), 6.0, 4), sun_from="side", sky=((0.5, 0.62, 0.85), 1.0),
                  sky_ramp=((0.0, (1.0, 0.66, 0.42)), (0.08, (0.95, 0.78, 0.6)), (0.3, (0.5, 0.62, 0.85)), (1.0, (0.25, 0.38, 0.68))),
                  haze=dict(kind="falloff", density=0.0009, colour=(1.0, 0.86, 0.7), falloff=-0.006, floor=64, box=(0, 700, 6500),
                            bounces=0),
                  land=("bradhurst", "bradhurst-mid", "region"), clip=20000, lantern=30, windows=0.5, ao=0.3, exposure=0.2,
                  finish=dict(FINISH, palette=((30, 100, 210), 0.18), bloom=(0.6, 0.5, 0.02), split=0.06, vignette=0.15, grain=0.0)),
    "vista-mist": dict(scale=1.0, style="anim", cam="-3640,138,-3440", at="-3840,112,-3700", lens=32, fstop=11.0,
                       sun=(6, (1.0, 0.78, 0.55), 5.0, 5), sun_from="back", sky=((0.55, 0.65, 0.82), 1.0),
                       sky_ramp=((0.0, (1.0, 0.8, 0.6)), (0.1, (0.86, 0.84, 0.8)), (0.35, (0.55, 0.66, 0.85)), (1.0, (0.32, 0.45, 0.72))),
                       haze=dict(kind="layer", density=0.02, colour=(1.0, 0.97, 0.94), top=96, soft=6, above=0.0012, box=(0, 700, 6500),
                                 billow=8, billow_size=120, anisotropy=0.4),
                       land=("bradhurst", "bradhurst-mid", "region"), clip=20000, lantern=20, windows=0.3, ao=0.3, exposure=0.3,
                       finish=dict(FINISH, palette=((34, 205), 0.15), bloom=(0.62, 0.45, 0.02), split=0.05, vignette=0.12, grain=0.0)),
    "vista-high": dict(scale=1.0, style="anim", cam="-3600,340,-3330", at="-3880,120,-3950", lens=26, fstop=16.0,
                       sun=(22, (1.0, 0.86, 0.66), 5.5, 4), sun_from="side", sky=((0.45, 0.6, 0.88), 1.0),
                       sky_ramp=((0.0, (0.95, 0.85, 0.72)), (0.1, (0.7, 0.78, 0.9)), (0.4, (0.42, 0.58, 0.86)), (1.0, (0.22, 0.38, 0.72))),
                       haze=dict(kind="falloff", density=0.0007, colour=(0.85, 0.9, 1.0), falloff=-0.004, floor=64, box=(0, 900, 6500),
                                 bounces=0),
                       land=("bradhurst", "bradhurst-mid", "region"), clip=20000, lantern=0, windows=0.0, ao=0.3, exposure=0.1,
                       finish=dict(FINISH, palette=((38, 105, 212), 0.15), bloom=(0.65, 0.4, 0.02), split=0.06, vignette=0.15, grain=0.0)),
    "tale-dawn": dict(TALE, azimuth=-62, sun=(13, (1.0, 0.64, 0.4), 9.0, 7), sun_from="side", sky=((0.42, 0.52, 0.72), 0.8),
                      backdrop=(0.035, 0.04, 0.055), lantern=30, exposure=0.6, rim=((0.62, 0.76, 1.0), 4.0, 20),
                      under=((1.0, 0.72, 0.62), 0.5), windows=0.5, motes=(70, (1.0, 0.85, 0.62), 5.0), finish=film((32, 212)),
                      haze=dict(kind="layer", density=0.05, colour=(1.0, 0.95, 0.9), top=99.5, soft=1.5, above=0.0, box=(94, 104, 14),
                                radius=10, anisotropy=0.4)),
    "tale-forge": dict(TALE, azimuth=-28, sun=(24, (0.55, 0.68, 1.0), 0.7, 6), sky=((0.05, 0.09, 0.24), 1.1),
                       backdrop=(0.012, 0.018, 0.036), lantern=45, glow=12.0, exposure=1.4, look="AgX - Medium High Contrast",
                       rim=((0.5, 0.66, 1.0), 1.6, 25), under=((1.0, 0.5, 0.2), 0.35), windows=1.4,
                       motes=(90, (1.0, 0.45, 0.15), 14.0), finish=film((218, 24))),
    "tale-dusk": dict(TALE, azimuth=-24, sun=(13, (1.0, 0.5, 0.32), 6.5, 7), sun_from="side", sky=((0.22, 0.26, 0.45), 0.9),
                      backdrop=(0.035, 0.025, 0.035), lantern=25, glow=8.0, exposure=1.0, rim=((1.0, 0.56, 0.72), 3.0, 12),
                      under=((0.5, 0.42, 0.85), 0.35), windows=1.0, motes=(60, (1.0, 0.74, 0.5), 5.0), finish=film((22, 335, 190))),
    "tale-gold": dict(TALE, azimuth=-24, sun=(16, (1.0, 0.76, 0.48), 7.5, 6), sun_from="side", sky=((0.36, 0.5, 0.75), 0.9),
                      backdrop=(0.035, 0.03, 0.028), lantern=10, exposure=0.7, rim=((1.0, 0.86, 0.62), 4.0, 18),
                      under=((0.42, 0.52, 0.85), 0.35), windows=0.4, motes=(70, (1.0, 0.86, 0.56), 6.0), finish=film((36, 210))),
    "tale-candle": dict(TALE, azimuth=-62, sun=(35, (0.45, 0.55, 1.0), 0.9, 4), sky=((0.05, 0.07, 0.15), 1.4),
                        backdrop=(0.008, 0.009, 0.016), lantern=90, glow=12.0, exposure=2.4, rim=((0.45, 0.58, 1.0), 0.8, 30),
                        under=((1.0, 0.6, 0.3), 0.08), windows=1.8, motes=(45, (0.95, 1.0, 0.55), 10.0), finish=film((28, 225))),
    "tale-clay": dict(TALE, azimuth=-150, style="clay", sun=(35, (1.0, 0.92, 0.82), 3.5, 15), sun_from="front",
                      sky=((0.62, 0.66, 0.72), 1.0), backdrop=(0.42, 0.4, 0.37), exposure=0.1, look="AgX - Base Contrast",
                      rim=((1.0, 0.97, 0.92), 2.0, 25), flatten=0.0, pool=1.25, outline=dict(OUTLINE, strength=0.35),
                      finish=dict(FINISH, bloom=None, split=0.05, vignette=0.2)),
    "tale-day": dict(TALE, azimuth=-150, sun=(38, (1.0, 0.93, 0.8), 5.0, 8), sun_from="front", sky=((0.5, 0.62, 0.85), 1.1),
                     backdrop=(0.3, 0.37, 0.48), pool=1.35, exposure=0.3, rim=((1.0, 0.96, 0.9), 2.0, 25),
                     under=((1.0, 0.85, 0.7), 0.3), lantern=8, motes=(30, (1.0, 1.0, 0.95), 2.0), finish=film((42, 100, 205))),
    "pixel": dict(sun=(17, (1.0, 0.8, 0.55), 5.5, 1.5), sky=((0.36, 0.5, 0.75), 1.1),
                  haze=dict(kind="falloff", density=0.0025, colour=(1.0, 0.9, 0.78), falloff=-0.025, floor=64, bounces=0),
                  fstop=64.0,
                  grade="pixel"),
}
L = dict(BASE, **LOOKS[LOOK])
if "style" in opt:
    L["style"] = opt["style"]
# a close-up: a hand-placed camera on any look (an island still keeps its tidying, its motes and its stage)
if "cam" in opt:
    L["frame"] = "view"
for key in ("lens", "fstop"):
    if key in opt:
        L[key] = float(opt[key])
if "wet" in opt:
    L["wet"] = opt["wet"] == "1"
if "paint" in opt:
    L["finish"] = dict(L["finish"] or FINISH, paint=float(opt["paint"]))
ISLAND = json.load(open(os.path.join(EXPORT, "meta.json"))).get("cut") == "island"
# Legends paints the noise out harder and pushes colour further than the film style
PAINTED = L["style"] == "legends"
QUIET = 0.6 if PAINTED else 1.0
SATURATE = 1.55 if PAINTED else 1.25
S = L["scale"]
W, H = (960, 540) if PREVIEW else (1920, 1080)

CLOCK = [time.time()]


def tick(stage):
    """How long the stage just finished took, for profiling a look."""
    now = time.time()
    print("[looks] time %-10s %6.1f s" % (stage, now - CLOCK[0]))
    CLOCK[0] = now


vk.clear()
world_obj = reel_world.load(EXPORT, glow=L["glow"], style="toon")
tick("import")
origin = tuple(bpy.context.scene["reel_origin"])
world_obj.scale = (S, S, S)
sc = bpy.context.scene
sc.render.resolution_x, sc.render.resolution_y = W, H
if "land" in opt:
    L["land"] = tuple(opt["land"].split("+"))
if L["land"]:
    # the land around the export to the horizon, each grid with a hole where the export or a finer grid already is
    import reel_land
    meta = json.load(open(os.path.join(EXPORT, "meta.json")))
    r = meta["radius"]
    holes = [(origin[0] - r, origin[2] - r, origin[0] + r + 1, origin[2] + r + 1)]
    renders = os.path.dirname(os.path.dirname(os.path.abspath(EXPORT)))
    for grid in L["land"]:
        if grid.startswith("lod:"):
            # VOID LOD's own far land (reel.sh lod): the real colours to the horizon, around the export's hole
            for ob in reel_land.build_lod(os.path.join(renders, "_lod", grid[4:]), origin, holes=holes[:1], name=grid[4:]):
                ob.scale = (S, S, S)
            continue
        folder = os.path.join(renders, "_terrain", grid)
        for ob in reel_land.build(folder, origin, holes=holes, seed=len(holes)):
            ob.scale = (S, S, S)
        g = json.load(open(os.path.join(folder, "meta.json")))
        holes.append((g["x0"] + g["step"], g["z0"] + g["step"], g["x0"] + (g["nx"] - 1) * g["step"], g["z0"] + (g["nz"] - 1) * g["step"]))
me = world_obj.data
mat_index = np.zeros(len(me.polygons), np.int32)
me.polygons.foreach_get("material_index", mat_index)
centres = np.zeros(len(me.polygons) * 3, np.float32)
me.polygons.foreach_get("center", centres)
# the OBJ importer keeps the mesh Y-up and turns the object instead: bring the centres into Blender's axes
centres = centres.reshape(-1, 3) @ np.array(world_obj.rotation_euler.to_matrix(), np.float32).T
GLOWS = ("lantern", "torch", "glowstone", "lamp", "shroomlight", "candle", "magma", "fire")
EVERGREEN = ("spruce", "pine", "fir", "cedar", "redwood", "azalea", "mangrove")


NEIGHBOURS = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]


def tidy(share=0.01):
    """An island without loose bits: blocks not joined to the land (touching by a face, an edge or a corner: the
    rock's stepped underside often touches only at edges) in a piece of any size, such as a leaf block left floating
    by the cut, are shrunk away. Returns which faces stay."""
    n = len(me.polygons)
    loc = np.zeros(n * 3, np.float32)
    me.polygons.foreach_get("center", loc)
    nrm = np.zeros(n * 3, np.float32)
    me.polygons.foreach_get("normal", nrm)
    blk = np.floor(loc.reshape(-1, 3) - nrm.reshape(-1, 3) * 0.01).astype(np.int64)
    keys, inverse = np.unique(blk, axis=0, return_inverse=True)
    inverse = inverse.ravel()
    where = {tuple(k): i for i, k in enumerate(keys.tolist())}
    group = np.full(len(keys), -1)
    sizes = []
    for start in range(len(keys)):
        if group[start] >= 0:
            continue
        g = len(sizes)
        group[start] = g
        todo, size = [start], 0
        while todo:
            i = todo.pop()
            size += 1
            x, y, z = keys[i]
            for d in NEIGHBOURS:
                j = where.get((x + d[0], y + d[1], z + d[2]))
                if j is not None and group[j] < 0:
                    group[j] = g
                    todo.append(j)
        sizes.append(size)
    sizes = np.array(sizes)
    keep_group = sizes >= max(8, share * sizes.max())
    keep = keep_group[group[inverse]]
    loops = np.zeros(len(me.loops), np.int64)
    me.loops.foreach_get("vertex_index", loops)
    co = np.zeros(len(me.vertices) * 3, np.float32)
    me.vertices.foreach_get("co", co)
    co = co.reshape(-1, 3)
    quads = loops.reshape(-1, 4)[~keep]
    co[quads] = co[quads].mean(1, keepdims=True)
    me.vertices.foreach_set("co", co.ravel())
    me.update()
    print("[looks] tidied %d loose faces in %d bits" % (int((~keep).sum()), int((~keep_group).sum())))
    return keep


def faces_of(test):
    """Centres (object space, a block a unit) of the faces whose material name passes the test."""
    want = [i for i, m in enumerate(me.materials) if m and test(m.name.split(".")[0])]
    return centres[np.isin(mat_index, want)]


def materials():
    for m in me.materials:
        if m and m.node_tree:
            yield m, m.node_tree, m.name.split(".")[0]


def node_of(nt, kind):
    return next((n for n in nt.nodes if n.type == kind), None)


def node(nt, kind, **inputs):
    n = nt.nodes.new(kind)
    for k, v in inputs.items():
        n.inputs[k].default_value = v
    return n


def math_node(nt, op, a, b):
    n = nt.nodes.new("ShaderNodeMath")
    n.operation = op
    for i, v in enumerate((a, b)):
        if isinstance(v, (int, float)):
            n.inputs[i].default_value = v
        else:
            nt.links.new(v, n.inputs[i])
    return n.outputs["Value"]


def band(nt, value, lo, hi, to=(0.0, 1.0)):
    """value mapped from lo..hi to to[0]..to[1], smoothly, clamped."""
    n = nt.nodes.new("ShaderNodeMapRange")
    n.interpolation_type = "SMOOTHSTEP"
    n.clamp = True
    n.inputs["From Min"].default_value, n.inputs["From Max"].default_value = lo, hi
    n.inputs["To Min"].default_value, n.inputs["To Max"].default_value = to
    nt.links.new(value, n.inputs["Value"])
    return n.outputs["Result"]


def noise(nt, blocks, detail=2.0):
    """Noise over world position, features about `blocks` blocks across."""
    geo = node_of(nt, "NEW_GEOMETRY") or nt.nodes.new("ShaderNodeNewGeometry")
    n = node(nt, "ShaderNodeTexNoise", Scale=1.0 / (blocks * S), Detail=detail)
    nt.links.new(geo.outputs["Position"], n.inputs["Vector"])
    return n.outputs["Fac"]


def srgb_to_linear(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


# ---------------------------------------------------------------- seasons and styles
def winter():
    """Snow on every face that looks up, in drifts with bare patches, cut out like the block under it."""
    for m, nt, name in materials():
        out, tex = node_of(nt, "OUTPUT_MATERIAL"), node_of(nt, "TEX_IMAGE")
        links = out.inputs["Surface"].links
        if not links or tex is None or name in ("water", "lava") or any(k in name for k in GLOWS):
            continue
        final = links[0].from_socket
        geo = nt.nodes.new("ShaderNodeNewGeometry")
        up_z = nt.nodes.new("ShaderNodeSeparateXYZ")
        nt.links.new(geo.outputs["Normal"], up_z.inputs["Vector"])
        up = band(nt, up_z.outputs["Z"], 0.5, 0.9)
        cover = band(nt, noise(nt, 7.0), 0.36, 0.42)
        mask = math_node(nt, "MULTIPLY", up, cover)
        snow = node(nt, "ShaderNodeBsdfToon", Color=(0.9, 0.93, 1.0, 1), Size=0.8, Smooth=0.15)
        clear = nt.nodes.new("ShaderNodeBsdfTransparent")
        cut = nt.nodes.new("ShaderNodeMixShader")
        nt.links.new(tex.outputs["Alpha"], cut.inputs["Fac"])
        nt.links.new(clear.outputs["BSDF"], cut.inputs[1])
        nt.links.new(snow.outputs["BSDF"], cut.inputs[2])
        both = nt.nodes.new("ShaderNodeMixShader")
        nt.links.new(mask, both.inputs["Fac"])
        nt.links.new(final, both.inputs[1])
        nt.links.new(cut.outputs["Shader"], both.inputs[2])
        nt.links.new(both.outputs["Shader"], out.inputs["Surface"])


def autumn():
    """Broadleaf leaves red to gold in tree-sized patches (evergreens stay), grass and ferns gone to straw."""
    for m, nt, name in materials():
        vc = node_of(nt, "VERTEX_COLOR")
        if vc is None or not vc.outputs["Color"].links:
            continue
        into = vc.outputs["Color"].links[0].to_socket
        if "leaves" in name and not any(k in name for k in EVERGREEN):
            ramp = nt.nodes.new("ShaderNodeValToRGB")
            stops = ((0.0, (0.5, 0.05, 0.02)), (0.3, (0.85, 0.18, 0.03)), (0.55, (0.95, 0.45, 0.05)),
                     (0.8, (0.95, 0.7, 0.14)), (1.0, (0.45, 0.5, 0.12)))
            el = ramp.color_ramp.elements
            el[0].position, el[0].color = stops[0][0], (*stops[0][1], 1)
            el[1].position, el[1].color = stops[-1][0], (*stops[-1][1], 1)
            for at, col in stops[1:-1]:
                e = el.new(at)
                e.color = (*col, 1)
            # birch goes gold; the rest spread over the whole ramp, a little varied block to block
            patch = math_node(nt, "ADD", band(nt, noise(nt, 10.0), 0.3, 0.7), math_node(nt, "MULTIPLY", noise(nt, 1.2), 0.25))
            nt.links.new(math_node(nt, "ADD", patch, 0.55) if "birch" in name else patch, ramp.inputs["Fac"])
            nt.links.new(ramp.outputs["Color"], into)
        elif any(k in name for k in ("grass_block_top", "grass_block_side_overlay", "short_grass", "tall_grass", "fern")):
            mix = nt.nodes.new("ShaderNodeMix")
            mix.data_type = "RGBA"
            mix.inputs["Factor"].default_value = 0.55
            nt.links.new(vc.outputs["Color"], mix.inputs["A"])
            mix.inputs["B"].default_value = (0.62, 0.52, 0.2, 1)
            nt.links.new(mix.outputs["Result"], into)


def clay():
    """Each block as its texture's average colour (tinted), matte, with edges rounded by a bevel; leaves solid."""
    for m, nt, name in materials():
        tex = node_of(nt, "TEX_IMAGE")
        if tex is None or name in ("water", "lava"):
            continue
        px = np.array(tex.image.pixels[:], np.float32).reshape(-1, 4)
        alpha = px[:, 3]
        mean = (px[:, :3] * alpha[:, None]).sum(0) / max(alpha.sum(), 1e-6)
        colour = srgb_to_linear(mean)
        holes = alpha.mean() < 0.97 and "leaves" not in name
        for n in [n for n in nt.nodes if n.type not in ("TEX_IMAGE", "VERTEX_COLOR", "OUTPUT_MATERIAL")]:
            nt.nodes.remove(n)
        out, vc = node_of(nt, "OUTPUT_MATERIAL"), node_of(nt, "VERTEX_COLOR")
        tint = nt.nodes.new("ShaderNodeMix")
        tint.data_type = "RGBA"
        tint.blend_type = "MULTIPLY"
        tint.inputs["Factor"].default_value = 1.0
        tint.inputs["A"].default_value = (*colour, 1)
        nt.links.new(vc.outputs["Color"], tint.inputs["B"])
        hs = node(nt, "ShaderNodeHueSaturation", Saturation=1.12, Value=1.0)
        nt.links.new(tint.outputs["Result"], hs.inputs["Color"])
        bsdf = node(nt, "ShaderNodeBsdfPrincipled", Roughness=0.55)
        nt.links.new(hs.outputs["Color"], bsdf.inputs["Base Color"])
        bevel = node(nt, "ShaderNodeBevel", Radius=0.09 * S)
        bevel.samples = 6
        nt.links.new(bevel.outputs["Normal"], bsdf.inputs["Normal"])
        if holes:
            nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
        if any(k in name for k in GLOWS):
            nt.links.new(hs.outputs["Color"], bsdf.inputs["Emission Color"])
            bsdf.inputs["Emission Strength"].default_value = L["glow"]
        nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])


tick("land")
if ISLAND:
    kept = tidy()
    centres = centres[kept]
    mat_index = mat_index[kept]
# blocks that give light glow in their own colour; foliage lets light through
BURNING = ("lantern", "torch", "glowstone", "lamp", "shroomlight", "candle_lit", "magma", "campfire_fire", "campfire_log_lit", "fire_0",
           "fire_1", "_front_on", "lava_still", "firefly_bush_emissive")
FOLIAGE = ("leaves", "grass", "fern", "vine", "bush", "sapling", "flower", "tulip", "poppy", "dandelion", "wheat", "carrots", "potatoes",
           "beetroots", "lily", "moss_carpet", "azalea")


# how each kind of block takes light in the film style: (roughness, specular, metallic)
SURFACES = {"matte": (0.85, 0.25, 0.0), "wood": (0.55, 0.4, 0.0), "metal": (0.32, 0.5, 0.85), "foliage": (0.6, 0.3, 0.0)}
METALS = ("lantern", "anvil", "iron", "chain", "bell", "gold_block", "copper", "cauldron", "hopper")
WOODS = ("planks", "_log", "_wood", "door", "trapdoor", "fence", "barrel", "bookshelf", "crafting", "chest", "ladder", "lectern")


def surface_of(name):
    if "glass" in name:
        return "glass"
    for kind, keys in (("metal", METALS), ("foliage", FOLIAGE), ("wood", WOODS)):
        if any(k in name for k in keys):
            return kind
    return "matte"


def animated():
    """Minecraft as an animated film shows it: each block's own texture with its brightness quietened toward its
    average (the pattern stays, the noise goes); stone matte, wood with a soft sheen, metal catching highlights,
    windows glowing from inside at dusk and night; edges rounded off; leaves and grass letting light through and
    varying softly across the land; the rock under the land darker, cooler and plainer toward the island's tip."""
    sc.cycles.diffuse_bounces = 6
    # how deep each face lies under its own column's surface (blocks), as a face attribute the materials read: the
    # land stays as it is wherever it lies, the rock under it darkens with depth
    n = len(me.polygons)
    loc = np.zeros(n * 3, np.float32)
    me.polygons.foreach_get("center", loc)
    nrm = np.zeros(n * 3, np.float32)
    me.polygons.foreach_get("normal", nrm)
    loc, nrm = loc.reshape(-1, 3), nrm.reshape(-1, 3)  # mesh space: Minecraft's axes, y up
    blk = np.floor(loc - nrm * 0.01).astype(np.int64)
    column = blk[:, 0] * 100003 + blk[:, 2]
    order = np.argsort(column)
    uniq, first = np.unique(column[order], return_index=True)
    up_faces = nrm[:, 1] > 0.5
    top_of = np.maximum.reduceat(np.where(up_faces, blk[:, 1] + 1, blk[:, 1] + 1)[order], first)
    depth = top_of[np.searchsorted(uniq, column)] - (blk[:, 1] + 0.5)
    if "depth" in me.attributes:
        me.attributes.remove(me.attributes["depth"])
    me.attributes.new("depth", "FLOAT", "FACE").data.foreach_set("value", depth.astype(np.float32))
    for m, nt, name in materials():
        tex, vc = node_of(nt, "TEX_IMAGE"), node_of(nt, "VERTEX_COLOR")
        if tex is None or vc is None or name in ("water", "lava"):
            continue
        px = np.array(tex.image.pixels[:], np.float32).reshape(-1, 4)
        alpha = px[:, 3]
        value = srgb_to_linear(px[:, :3]).max(1)
        mean_value = float((value * alpha).sum() / max(alpha.sum(), 1e-6))
        for n in [n for n in nt.nodes if n.type not in ("TEX_IMAGE", "VERTEX_COLOR", "OUTPUT_MATERIAL")]:
            nt.nodes.remove(n)
        out = node_of(nt, "OUTPUT_MATERIAL")
        under = nt.nodes.new("ShaderNodeAttribute")
        under.attribute_name = "depth"
        # 1 on the land, falling to 0 some way down into the rock
        up = band(nt, under.outputs["Fac"], 3.0, 30.0, (1.0, 0.0))
        # quieter texture: brightness pulled toward its mean, less so on the land than in the rock
        hsv = nt.nodes.new("ShaderNodeSeparateColor")
        hsv.mode = "HSV"
        nt.links.new(tex.outputs["Color"], hsv.inputs["Color"])
        keep = math_node(nt, "ADD", math_node(nt, "MULTIPLY", up, 0.35 * QUIET), 0.25 * QUIET)
        spread = math_node(nt, "MULTIPLY", math_node(nt, "SUBTRACT", hsv.outputs[2], mean_value), keep)
        back = nt.nodes.new("ShaderNodeCombineColor")
        back.mode = "HSV"
        nt.links.new(hsv.outputs[0], back.inputs[0])
        nt.links.new(hsv.outputs[1], back.inputs[1])
        nt.links.new(math_node(nt, "ADD", spread, mean_value), back.inputs[2])
        colour = back.outputs["Color"]
        tint = nt.nodes.new("ShaderNodeMix")
        tint.data_type, tint.blend_type = "RGBA", "MULTIPLY"
        tint.inputs["Factor"].default_value = 1.0
        nt.links.new(colour, tint.inputs["A"])
        nt.links.new(vc.outputs["Color"], tint.inputs["B"])
        colour = tint.outputs["Result"]
        kind = surface_of(name)
        if kind == "foliage" or "grass_block" in name:
            # soft patches across the land, a little warmer here, a little cooler there
            ramp = nt.nodes.new("ShaderNodeValToRGB")
            ramp.color_ramp.elements[0].color = (1.1, 1.02, 0.8, 1)
            ramp.color_ramp.elements[1].color = (0.84, 1.0, 0.95, 1)
            nt.links.new(band(nt, noise(nt, 9.0), 0.35, 0.65), ramp.inputs["Fac"])
            vary = nt.nodes.new("ShaderNodeMix")
            vary.data_type, vary.blend_type = "RGBA", "MULTIPLY"
            vary.inputs["Factor"].default_value = 1.0
            nt.links.new(colour, vary.inputs["A"])
            nt.links.new(ramp.outputs["Color"], vary.inputs["B"])
            colour = vary.outputs["Result"]
        # the rock under the land: darker and cooler toward the tip, so the island reads as a floating gem, not a slab
        deep = nt.nodes.new("ShaderNodeMix")
        deep.data_type, deep.blend_type = "RGBA", "MULTIPLY"
        deep.inputs["A"].default_value = (1, 1, 1, 1)
        nt.links.new(colour, deep.inputs["A"])
        cool = nt.nodes.new("ShaderNodeMix")
        cool.data_type = "RGBA"
        cool.inputs["A"].default_value = (0.34, 0.4, 0.55, 1)
        cool.inputs["B"].default_value = (1, 1, 1, 1)
        nt.links.new(up, cool.inputs["Factor"])
        nt.links.new(cool.outputs["Result"], deep.inputs["B"])
        deep.inputs["Factor"].default_value = 1.0
        hs = node(nt, "ShaderNodeHueSaturation", Saturation=SATURATE, Value=1.05)
        nt.links.new(deep.outputs["Result"], hs.inputs["Color"])
        if PAINTED and kind != "glass":
            # painted, not pixel-sharp: the texture read smoothly; each face's very edge a shade darker and its top a
            # shade brighter (the bevel's own highlight does the rest)
            tex.interpolation = "Cubic"
            uv = nt.nodes.new("ShaderNodeUVMap")
            u = nt.nodes.new("ShaderNodeSeparateXYZ")
            nt.links.new(uv.outputs["UV"], u.inputs["Vector"])
            edge = math_node(nt, "MINIMUM", math_node(nt, "MINIMUM", u.outputs["X"], math_node(nt, "SUBTRACT", 1.0, u.outputs["X"])),
                             math_node(nt, "MINIMUM", u.outputs["Y"], math_node(nt, "SUBTRACT", 1.0, u.outputs["Y"])))
            border = band(nt, edge, 0.0, 0.07, (0.84, 1.0))
            facing = nt.nodes.new("ShaderNodeNewGeometry")
            fz = nt.nodes.new("ShaderNodeSeparateXYZ")
            nt.links.new(facing.outputs["Normal"], fz.inputs["Vector"])
            lift = band(nt, fz.outputs["Z"], 0.5, 0.9, (1.0, 1.12))
            shade = math_node(nt, "MULTIPLY", border, lift)
            grey = nt.nodes.new("ShaderNodeCombineColor")
            for ch in ("Red", "Green", "Blue"):
                nt.links.new(shade, grey.inputs[ch])
            painted = nt.nodes.new("ShaderNodeMix")
            painted.data_type, painted.blend_type = "RGBA", "MULTIPLY"
            painted.inputs["Factor"].default_value = 1.0
            nt.links.new(hs.outputs["Color"], painted.inputs["A"])
            nt.links.new(grey.outputs["Color"], painted.inputs["B"])
            colour_out = painted.outputs["Result"]
        else:
            colour_out = hs.outputs["Color"]
        bsdf = node(nt, "ShaderNodeBsdfPrincipled")
        nt.links.new(colour_out, bsdf.inputs["Base Color"])
        bevel = node(nt, "ShaderNodeBevel", Radius=0.14 * S)
        bevel.samples = 6
        nt.links.new(bevel.outputs["Normal"], bsdf.inputs["Normal"])
        rough, spec, metal = SURFACES.get(kind, (0.05, 0.5, 0.0))
        bsdf.inputs["Roughness"].default_value = rough
        bsdf.inputs["Specular IOR Level"].default_value = spec
        if L["wet"] and kind != "glass":
            # rain: faces that look up go glossy (puddles hold the lights) and a shade darker
            facing = nt.nodes.new("ShaderNodeNewGeometry")
            up_z = nt.nodes.new("ShaderNodeSeparateXYZ")
            nt.links.new(facing.outputs["Normal"], up_z.inputs["Vector"])
            puddle = band(nt, up_z.outputs["Z"], 0.6, 0.9)
            nt.links.new(band(nt, puddle, 0.0, 1.0, (rough, 0.05)), bsdf.inputs["Roughness"])
            nt.links.new(band(nt, puddle, 0.0, 1.0, (spec, 0.9)), bsdf.inputs["Specular IOR Level"])
            damp = nt.nodes.new("ShaderNodeMix")
            damp.data_type, damp.blend_type = "RGBA", "MULTIPLY"
            damp.inputs["Factor"].default_value = 1.0
            damp.inputs["B"].default_value = (0.72, 0.72, 0.75, 1)
            nt.links.new(colour_out, damp.inputs["A"])
            nt.links.new(damp.outputs["Result"], bsdf.inputs["Base Color"])
        bsdf.inputs["Metallic"].default_value = metal
        if kind == "foliage":
            bsdf.inputs["Subsurface Weight"].default_value = 0.3
            bsdf.inputs["Subsurface Radius"].default_value = (1.0, 0.6, 0.3)
            bsdf.inputs["Subsurface Scale"].default_value = 0.2 * S
        if any(k in name for k in BURNING):
            nt.links.new(colour_out, bsdf.inputs["Emission Color"])
            bsdf.inputs["Emission Strength"].default_value = L["glow"]
        final = bsdf.outputs["BSDF"]
        if kind == "glass":
            # the frame from the texture; in the panes either a room lit inside (dusk, night) or clear glass (day)
            if L["windows"]:
                pane = nt.nodes.new("ShaderNodeEmission")
                pane.inputs["Color"].default_value = (1.0, 0.64, 0.34, 1)
                pane.inputs["Strength"].default_value = L["windows"] * 4.0
            else:
                pane = node(nt, "ShaderNodeBsdfPrincipled", Roughness=0.05)
                pane.inputs["Transmission Weight"].default_value = 1.0
            either = nt.nodes.new("ShaderNodeMixShader")
            nt.links.new(tex.outputs["Alpha"], either.inputs["Fac"])
            nt.links.new(pane.outputs[0], either.inputs[1])
            nt.links.new(final, either.inputs[2])
            final = either.outputs["Shader"]
        else:
            nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
        nt.links.new(final, out.inputs["Surface"])


if L["style"] == "clay":
    clay()
elif L["style"] in ("anim", "legends"):
    animated()
# seasons after the style, which rebuilds the materials: snow and autumn colour go on the finished ones
if L["season"] == "winter":
    winter()
elif L["season"] == "autumn":
    autumn()


def flatten(amount):
    """Each block partway to its texture's average colour (still tinted), so a small model reads as colour, not noise."""
    for m, nt, name in materials():
        tex, hs, vc = node_of(nt, "TEX_IMAGE"), node_of(nt, "HUE_SAT"), node_of(nt, "VERTEX_COLOR")
        if tex is None or hs is None or vc is None or not hs.inputs["Color"].links:
            continue
        px = np.array(tex.image.pixels[:], np.float32).reshape(-1, 4)
        alpha = px[:, 3]
        mean = srgb_to_linear((px[:, :3] * alpha[:, None]).sum(0) / max(alpha.sum(), 1e-6))
        detailed = hs.inputs["Color"].links[0].from_socket
        plain = nt.nodes.new("ShaderNodeMix")
        plain.data_type, plain.blend_type = "RGBA", "MULTIPLY"
        plain.inputs["Factor"].default_value = 1.0
        plain.inputs["A"].default_value = (*mean, 1)
        nt.links.new(vc.outputs["Color"], plain.inputs["B"])
        blend = nt.nodes.new("ShaderNodeMix")
        blend.data_type = "RGBA"
        blend.inputs["Factor"].default_value = amount
        nt.links.new(detailed, blend.inputs["A"])
        nt.links.new(plain.outputs["Result"], blend.inputs["B"])
        nt.links.new(blend.outputs["Result"], hs.inputs["Color"])


def ambient_occlusion(amount, blocks=0.8):
    """Contact shadow where blocks meet, darkening a surface's colour by up to `amount` (light, not glow)."""
    for m, nt, name in materials():
        if node_of(nt, "HUE_SAT") is None:
            continue
        surfaces = [n for n in nt.nodes if n.type in ("BSDF_TOON", "BSDF_PRINCIPLED")]
        feeds = [n.inputs["Base Color" if n.type == "BSDF_PRINCIPLED" else "Color"].links for n in surfaces]
        for link in [ls[0] for ls in feeds if ls]:
            source = link.from_socket
            ao = nt.nodes.new("ShaderNodeAmbientOcclusion")
            ao.samples = 8
            ao.inputs["Distance"].default_value = blocks * S
            dim = nt.nodes.new("ShaderNodeMix")
            dim.data_type, dim.blend_type = "RGBA", "MULTIPLY"
            dim.inputs["Factor"].default_value = 1.0
            nt.links.new(source, dim.inputs["A"])
            shade = band(nt, ao.outputs["AO"], 0.0, 1.0, (1.0 - amount, 1.0))
            grey = nt.nodes.new("ShaderNodeCombineColor")
            for ch in ("Red", "Green", "Blue"):
                nt.links.new(shade, grey.inputs[ch])
            nt.links.new(grey.outputs["Color"], dim.inputs["B"])
            nt.links.new(dim.outputs["Result"], link.to_socket)


if L["flatten"]:
    flatten(L["flatten"])
if L["ao"]:
    ambient_occlusion(L["ao"])

tick("materials")
# ---------------------------------------------------------------- camera
lo, hi = centres.min(axis=0), centres.max(axis=0)
centre = Vector(((lo + hi) / 2).tolist()) * S
size = Vector((hi - lo).tolist()) * S
if L["frame"] == "island":
    # three-quarter, a little above, far enough back for the whole island and some room around it
    el, az = math.radians(L["elevation"]), math.radians(L["azimuth"])
    vfov = 2 * math.atan(36 * 9 / 16 / 2 / L["lens"])
    dist = 1.3 * max(size.z, max(size.x, size.y) * 9 / 16) / (2 * math.tan(vfov / 2))
    cam_b = centre + dist * Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
    look_b = centre
else:
    cam_mc = vec(opt.get("cam", L.get("cam", "-3688,146,-3512")))
    look_mc = vec(opt.get("at", L.get("at", "-3778,98,-3598")))
    cam_b = rc.mc_to_blender(*cam_mc, origin) * S
    look_b = rc.mc_to_blender(*look_mc, origin) * S
cam = vk.camera(cam_b, look_b, lens=L["lens"], clip=L["clip"] * S, dof=((look_b - cam_b).length, L["fstop"]))
cam.data.clip_start = 0.05 * S
if "ortho" in opt:
    L["ortho"] = float(opt["ortho"])
if L["ortho"]:
    # no perspective: a map-like view, as a model seen from far off through a long lens
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = L["ortho"] * S
    cam.data.dof.use_dof = False
if L["frame"] == "island":
    # frame what the camera actually sees of it: centre the silhouette with lens shift, then move in until it
    # fills about two thirds of the height
    sample = [Vector(c.tolist()) * S for c in centres[::25]]
    for _ in range(3):
        bpy.context.view_layer.update()
        ndc = np.array([tuple(world_to_camera_view(sc, cam, v))[:2] for v in sample])
        (x0, y0), (x1, y1) = ndc.min(axis=0), ndc.max(axis=0)
        cam.data.shift_x += (x0 + x1) / 2 - 0.5
        cam.data.shift_y += ((y0 + y1) / 2 - 0.5) * 9 / 16
        grow = max((y1 - y0) / L["fill"], (x1 - x0) / (L["fill"] + 0.02))
        cam.location = look_b + (cam.location - look_b) * grow
        cam.data.shift_x *= 1 / grow
        cam.data.shift_y *= 1 / grow
    cam.data.dof.focus_distance = (look_b - cam.location).length
view = (look_b - cam_b).normalized()


# the land itself: these stand from the start wherever they are (the generator's bare land can sit a block or two
# under the real surface), so only what was built or planted goes up
GROUND = {"grass_block_top", "grass_block_side", "grass_block_side_overlay", "grass_block_snow", "dirt", "coarse_dirt",
          "rooted_dirt", "podzol_top", "podzol_side", "mud", "moss_block", "stone", "granite", "diorite", "andesite", "tuff",
          "deepslate", "deepslate_top", "calcite", "gravel", "sand", "red_sand", "clay", "snow", "short_grass", "tall_grass_top",
          "tall_grass_bottom", "fern", "large_fern_top", "large_fern_bottom", "water", "lava"}


POP = 0.012  # how long a block takes to pop into place, in build time (0..1)
SLOW_START = 1.8  # build time to days: day = days * u ** SLOW_START, so the first days have room to be told


def backout(x):
    """0 to 1 with a small overshoot, as a block set down with a little bounce."""
    c = 1.70158
    return 1 + (c + 1) * (x - 1) ** 3 + c * (x - 1) ** 2


def build_plan():
    """When each block on the bare land went up. With the town's Hearth save beside the export (hearth.dat) every
    block belongs to a plot and goes up with it, in the order the town really built them (each plot finishing on
    its completed day), a building course by course from its footing, a road along its line. Without one, the town
    grows from its middle outward in 8-block lots. Leaves, the logs under them, water and the land's own blocks near
    the bare surface were there before, except inside a plot (an orchard is planted)."""
    meta = json.load(open(os.path.join(EXPORT, "meta.json")))
    r = meta["radius"]
    side = 2 * r + 3
    bare = np.fromfile(os.path.join(EXPORT, "bare.f32"), "<f4").reshape(side, side)
    n = len(me.polygons)
    loc = np.zeros(n * 3, np.float32)
    me.polygons.foreach_get("center", loc)
    loc = loc.reshape(-1, 3)  # mesh space is Minecraft's axes less the origin
    nrm = np.zeros(n * 3, np.float32)
    me.polygons.foreach_get("normal", nrm)
    blk = np.floor(loc - nrm.reshape(-1, 3) * 0.01).astype(np.int64)
    ii = np.clip(blk[:, 0] + r + 1, 0, side - 1)
    jj = np.clip(blk[:, 2] + r + 1, 0, side - 1)
    wx, wy, wz = blk[:, 0] + origin[0], blk[:, 1] + origin[1], blk[:, 2] + origin[2]
    names = np.array([m.name.split(".")[0] if m else "" for m in me.materials])[mat_index]
    kind = np.array([n.split("_block_", 1)[-1] for n in names])
    leaves = np.char.find(names, "leaves") >= 0
    tree = np.zeros((side, side), bool)
    tree[jj[leaves], ii[leaves]] = True
    tree = tree | np.roll(tree, 1, 0) | np.roll(tree, -1, 0) | np.roll(tree, 1, 1) | np.roll(tree, -1, 1)
    above = blk[:, 1] - (bare[jj, ii] - origin[1])
    land = np.isin(kind, ["water", "lava"]) | (np.isin(kind, list(GROUND)) & (above <= 2))
    wild = leaves | ((np.char.find(names, "_log") >= 0) & tree[jj, ii])
    owner = np.full(n, -1)
    plots = []
    save = os.path.join(EXPORT, "hearth.dat")
    if os.path.isfile(save):
        sys.path.insert(0, os.path.dirname(HERE))
        import nbt
        town = nbt.load(save)
        lived = town["clock"]["days_lived"]
        for p in town["plots"]:
            if p.get("state") not in ("built", "building"):
                continue
            spec = p["spec"]
            ox, oy_, oz = spec["origin"]
            done = p.get("completed_day")
            plots.append(dict(id=p["id"], kind=spec["archetype"].split("/")[-1], facing=spec["facing"],
                              box=(ox, oz, ox + spec["size_x"], oz + spec["size_z"]),
                              day=lived if done is None else done + lived, points=spec["params"].get("points"),
                              width=spec["params"].get("width", 3)))
        # buildings, farms and works claim their footprint; roads then claim what lies along their line
        for k, pl in enumerate(plots):
            if pl["kind"] == "road":
                continue
            x0, z0, x1, z1 = pl["box"]
            inside = (owner < 0) & (wx >= x0) & (wx < x1) & (wz >= z0) & (wz < z1)
            owner[inside] = k
        for k, pl in enumerate(plots):
            if pl["kind"] != "road" or not pl["points"]:
                continue
            pts = np.array(pl["points"], np.float64).reshape(-1, 3)[:, [0, 2]] + 0.5
            free = np.nonzero(owner < 0)[0]
            px, pz = wx[free] + 0.5, wz[free] + 0.5
            best = np.full(len(free), 1e9)
            for a, b in zip(pts[:-1], pts[1:]):
                ab = b - a
                t_ = np.clip(((px - a[0]) * ab[0] + (pz - a[1]) * ab[1]) / max(ab @ ab, 1e-9), 0, 1)
                best = np.minimum(best, np.hypot(px - a[0] - t_ * ab[0], pz - a[1] - t_ * ab[1]))
            owner[free[best <= pl["width"] / 2 + 0.5]] = k
        days = float(lived)
    else:
        days = 1.0
    built = (above >= 0) & ((owner >= 0) | (~land & ~wild))
    # what no plot claims goes up with the nearest plot (lamps, fences, the odd path); without a save, by 8-block lots
    loose = built & (owner < 0)
    if plots:
        centre = np.array([((pl["box"][0] + pl["box"][2]) / 2, (pl["box"][1] + pl["box"][3]) / 2) for pl in plots])
        idx = np.nonzero(loose)[0]
        for chunk in np.array_split(idx, max(1, len(idx) // 4000)):
            if not len(chunk):
                continue
            d2 = (wx[chunk, None] + 0.5 - centre[None, :, 0]) ** 2 + (wz[chunk, None] + 0.5 - centre[None, :, 1]) ** 2
            owner[chunk] = d2.argmin(1)
    else:
        lots = {}
        for f in np.nonzero(loose)[0]:
            owner[f] = lots.setdefault((int(wx[f] // 8), int(wz[f] // 8)), len(lots))
        mid = np.array([wx[built].mean(), wz[built].mean()])
        for (lx, lz), k in lots.items():
            plots.append(dict(id=k, kind="lot", facing="north", points=None, width=0, box=(lx * 8, lz * 8, lx * 8 + 8, lz * 8 + 8),
                              day=float(np.hypot(lx * 8 + 4 - mid[0], lz * 8 + 4 - mid[1]))))
        days = max(pl["day"] for pl in plots) or 1.0
    to_u = lambda d: (min(max(d, 0.0), days) / days) ** (1 / SLOW_START)
    appear = np.full(n, -1.0)
    for k, pl in enumerate(plots):
        mine = np.nonzero(built & (owner == k))[0]
        if not len(mine):
            continue
        # a plot takes a few days by its size, and at least half a second of the film so it can be seen going up
        span = min(max(len(mine) / 120.0, 1.0), 8.0)
        end_u = to_u(pl["day"])
        dur = max(end_u - to_u(pl["day"] - span), 0.05)
        start_u = max(end_u - dur, 0.0)
        if pl["kind"] == "road" and pl["points"]:
            pts = np.array(pl["points"], np.float64).reshape(-1, 3)[:, [0, 2]] + 0.5
            run = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(pts, axis=0).T))])
            near = ((wx[mine, None] + 0.5 - pts[None, :, 0]) ** 2 + (wz[mine, None] + 0.5 - pts[None, :, 1]) ** 2).argmin(1)
            order = run[near] / max(run[-1], 1)
        else:
            # course by course from the footing; along each course from the front of the plot to the back
            y = wy[mine]
            courses = max(y.max() - y.min() + 1, 1)
            across = {"north": -wz[mine], "south": wz[mine], "east": wx[mine], "west": -wx[mine]}.get(pl["facing"], wx[mine])
            sweep = (across - across.min()) / max(across.max() - across.min() + 1, 1)
            order = (y - y.min() + sweep) / courses
        appear[mine] = start_u + np.clip(order, 0, 1) * (dur - POP)
    loops = np.zeros(len(me.loops), np.int64)
    me.loops.foreach_get("vertex_index", loops)
    co = np.zeros(len(me.vertices) * 3, np.float32)
    me.vertices.foreach_get("co", co)
    # a lantern's light comes on once its block is down; keyed by the block in Blender's axes, as the lights are
    lamp = (np.char.find(names, "lantern") >= 0) & (np.char.find(names, "sea") < 0)
    lantern_t = {}
    for b, tb in zip(blk[lamp], appear[lamp]):
        key = (int(b[0]), int(-b[2] - 1), int(b[1]))
        lantern_t[key] = max(lantern_t.get(key, -1.0), tb + POP)
    print("[looks] build plan: %d plots, %d of %d built faces claimed by a plot, %.0f days" % (
        len(plots), int((built & (owner >= 0)).sum()), int(built.sum()), days))
    return dict(appear=appear, built=built, quads=loops.reshape(-1, 4), co0=co.reshape(-1, 3), centre=(blk + 0.5).astype(np.float32),
                lantern_t=lantern_t, days=days)


def build_at(plan, u):
    """The town at build time u (0..1): what is down stands, what is going down pops into place, the rest is not
    there yet (shrunk to a point, which renders as nothing). Returns which faces are there."""
    appear, built, quads, co0 = plan["appear"], plan["built"], plan["quads"], plan["co0"]
    grow = np.where(built, backout(np.clip((u - appear) / POP, 0, 1)), 1.0).astype(np.float32)
    c = plan["centre"][:, None, :]
    co = co0.copy()
    co[quads] = c + (co0[quads] - c) * grow[:, None, None]
    me.vertices.foreach_set("co", co.ravel())
    me.update()
    return grow > 1e-4


def day_at(plan, u):
    return plan["days"] * min(max(u, 0.0), 1.0) ** SLOW_START


plan = build_plan() if ("build" in opt or "frames" in opt) else None
if plan is not None and "frames" not in opt:
    standing = build_at(plan, float(opt["build"]))
    print("[looks] build %.2f (day %d): %d of %d built faces standing" % (float(opt["build"]), day_at(plan, float(opt["build"])) + 1,
                                                                       (standing & plan["built"]).sum(), plan["built"].sum()))
    centres = centres[standing]
    mat_index = mat_index[standing]

# ---------------------------------------------------------------- light
if L["sun"]:
    elev, colour, energy, angle = L["sun"]
    flat_view = Vector((view.x, view.y, 0)).normalized()
    side = Vector((-flat_view.y, flat_view.x, 0))
    # where the sun stands, seen from the subject: ahead of the camera for rim light and shadows toward it, beside
    # the view to rake across the land, or behind the camera's shoulder to show every face
    towards = {"back": flat_view * 0.7 + side * 0.7, "side": flat_view * 0.2 + side,
               "front": -flat_view * 0.6 + side * 0.8}[L["sun_from"]].normalized()
    travel = Vector((-towards.x, -towards.y, -math.tan(math.radians(elev)))).normalized()
    sd = bpy.data.lights.new("sun", "SUN")
    sd.energy, sd.color, sd.angle = energy, colour, math.radians(angle * (1.6 if L["style"] == "anim" else 1.0))
    sun = bpy.data.objects.new("sun", sd)
    sun.rotation_euler = travel.to_track_quat("-Z", "Y").to_euler()
    sc.collection.objects.link(sun)
if L["rim"]:
    # a kicker: from behind and to the side opposite the key, low: the far side faces and the edges catch it
    colour, energy, elev = L["rim"]
    flat_view = Vector((view.x, view.y, 0)).normalized()
    side = Vector((-flat_view.y, flat_view.x, 0))
    towards = (flat_view * 0.6 - side * 0.8).normalized()
    rd = bpy.data.lights.new("rim", "SUN")
    rd.energy, rd.color, rd.angle = energy, colour, math.radians(3)
    rim = bpy.data.objects.new("rim", rd)
    rim.rotation_euler = Vector((-towards.x, -towards.y, -math.tan(math.radians(elev)))).normalized().to_track_quat("-Z", "Y").to_euler()
    sc.collection.objects.link(rim)
if L["under"]:
    # light from below, as off clouds or a fire under the island: it finds the rock's underside
    colour, energy = L["under"]
    ud = bpy.data.lights.new("under", "SUN")
    ud.energy, ud.color, ud.angle = energy, colour, math.radians(25)
    under = bpy.data.objects.new("under", ud)
    under.rotation_euler = Vector((0.15, -0.2, 1.0)).normalized().to_track_quat("-Z", "Y").to_euler()
    sc.collection.objects.link(under)
if L["studio"]:
    # a studio around the island's middle: warm key above-left, cool rim from behind, faint fill from the camera side
    reach = 3.2 * max(size.x, size.y)
    for name, d, light_size, colour, power in (("key", Vector((-0.55, -0.6, 0.6)), 1.6, (1.0, 0.86, 0.7), 260),
                                               ("rim", Vector((-0.45, 0.8, 0.35)), 1.2, (0.6, 0.72, 1.0), 320),
                                               ("fill", Vector((0.4, -0.85, 0.25)), 2.0, (0.9, 0.92, 1.0), 30)):
        ld = bpy.data.lights.new(name, "AREA")
        ld.shape, ld.size, ld.color, ld.energy = "DISK", light_size, colour, power
        lo_ = bpy.data.objects.new(name, ld)
        # lamps, not mirrors: a studio light seen in the river reads as a white hole in the island
        lo_.visible_glossy = False
        lo_.location = centre + d.normalized() * reach
        lo_.rotation_euler = (centre - lo_.location).to_track_quat("-Z", "Y").to_euler()
        sc.collection.objects.link(lo_)

w = bpy.data.worlds.new("sky")
w.use_nodes = True
bg = w.node_tree.nodes["Background"]
bg.inputs["Color"].default_value = (*L["sky"][0], 1)
bg.inputs["Strength"].default_value = L["sky"][1]
if L["sky_ramp"]:
    # in a world, generated coordinates are the direction looked in: its height picks the colour
    wt = w.node_tree
    coord = wt.nodes.new("ShaderNodeTexCoord")
    up = wt.nodes.new("ShaderNodeSeparateXYZ")
    wt.links.new(coord.outputs["Generated"], up.inputs["Vector"])
    ramp = wt.nodes.new("ShaderNodeValToRGB")
    el = ramp.color_ramp.elements
    stops = L["sky_ramp"]
    el[0].position, el[0].color = stops[0][0], (*stops[0][1], 1)
    el[1].position, el[1].color = stops[-1][0], (*stops[-1][1], 1)
    for at, col in stops[1:-1]:
        el.new(at).color = (*col, 1)
    wt.links.new(up.outputs["Z"], ramp.inputs["Fac"])
    wt.links.new(ramp.outputs["Color"], bg.inputs["Color"])
if L["backdrop"]:
    # the camera sees the backdrop; everything else (light, reflections) still sees the sky
    wt = w.node_tree
    out = wt.nodes["World Output"]
    path = wt.nodes.new("ShaderNodeLightPath")
    back = wt.nodes.new("ShaderNodeBackground")
    back.inputs["Color"].default_value = (*L["backdrop"], 1)
    if L["pool"]:
        # a soft lighter patch behind the subject: the backdrop brightens toward the middle of the view
        coord = wt.nodes.new("ShaderNodeTexCoord")
        facing = wt.nodes.new("ShaderNodeVectorMath")
        facing.operation = "DOT_PRODUCT"
        facing.inputs[1].default_value = tuple(view)
        wt.links.new(coord.outputs["Generated"], facing.inputs[0])
        reach = band(wt, facing.outputs["Value"], math.cos(math.radians(24)), math.cos(math.radians(2)))
        pool = wt.nodes.new("ShaderNodeMix")
        pool.data_type = "RGBA"
        pool.inputs["A"].default_value = (*L["backdrop"], 1)
        pool.inputs["B"].default_value = (*(min(1.0, c * L["pool"]) for c in L["backdrop"]), 1)
        wt.links.new(reach, pool.inputs["Factor"])
        wt.links.new(pool.outputs["Result"], back.inputs["Color"])
    pick = wt.nodes.new("ShaderNodeMixShader")
    wt.links.new(path.outputs["Is Camera Ray"], pick.inputs["Fac"])
    wt.links.new(bg.outputs["Background"], pick.inputs[1])
    wt.links.new(back.outputs["Background"], pick.inputs[2])
    wt.links.new(pick.outputs["Shader"], out.inputs["Surface"])
sc.world = w

# every block that burns a small light of its own: a lantern at the look's `lantern` watts, the rest by how they burn
FLAMES = (("lantern", 1.0, (1.0, 0.6, 0.28), 0.4), ("candle_lit", 0.3, (1.0, 0.55, 0.25), 0.3), ("campfire_fire", 3.0, (1.0, 0.45, 0.15), 0.4),
          ("_front_on", 1.5, (1.0, 0.45, 0.15), 0.5), ("lava_still", 2.0, (1.0, 0.35, 0.08), 0.9),
          ("firefly_bush_emissive", 0.15, (0.85, 1.0, 0.5), 0.6))
lamps = []
if L["lantern"]:
    lit = set()
    for key, share, colour, height in FLAMES:
        blocks = {tuple(int(v) for v in np.floor(c)) for c in faces_of(lambda n: key in n and "sea_lantern" not in n)} - lit
        lit |= blocks
        for b in blocks:
            ld = bpy.data.lights.new(key, "POINT")
            # watts as at full size: a model S times smaller needs S^2 the power for the same light at S times the distance
            ld.energy = L["lantern"] * share * S * S
            ld.color = colour
            ld.shadow_soft_size = 0.25 * S
            lo_ = bpy.data.objects.new(key, ld)
            lo_.location = (Vector(b) + Vector((0.5, 0.5, height))) * S
            sc.collection.objects.link(lo_)
            lamps.append((lo_, b))
    print("[looks] %d flame lights" % len(lamps))

# specks of light drifting around an island: dust in the sun, sparks off a forge, fireflies; soft in the lens
if L["motes"] and ISLAND:
    count, colour, strength = L["motes"][:3]
    grown = L["motes"][3] if len(L["motes"]) > 3 else 1.0
    # a close-up stands among them: fewer, smaller, and none in front of the lens (they would fill the frame)
    close = (look_b - cam.location).length / S < 40
    front = 0 if close else count // 3
    if close:
        count //= 2
    rng = np.random.default_rng(11)
    lo3, hi3 = centres.min(axis=0), centres.max(axis=0)
    mid = (lo3 + hi3) / 2
    reach = max(hi3[0] - lo3[0], hi3[1] - lo3[1]) / 2
    top = float(np.percentile(centres[:, 2], 80))
    speck = bpy.data.meshes.new("mote")
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=1, radius=1.0)
    bm.to_mesh(speck)
    bm.free()
    glowing = bpy.data.materials.new("mote")
    glowing.use_nodes = True
    gn = glowing.node_tree
    gn.nodes.clear()
    em = gn.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (*colour, 1)
    em.inputs["Strength"].default_value = strength * 0.2
    gn.links.new(em.outputs["Emission"], gn.nodes.new("ShaderNodeOutputMaterial").inputs["Surface"])
    speck.materials.append(glowing)
    # a third of them between the camera and the island, out of focus: soft discs of light in the foreground
    right = view.cross(Vector((0, 0, 1))).normalized()
    upward = right.cross(view).normalized()
    far = (look_b - cam.location).length
    half = math.tan(math.radians(14))
    for _ in range(front):
        t = rng.uniform(0.3, 0.75)
        spot = cam.location + view * far * t + right * far * t * half * rng.uniform(-1.6, 1.6) + upward * far * t * half * rng.uniform(-0.9, 0.9)
        ob = bpy.data.objects.new("mote", speck)
        ob.location = spot
        size = rng.uniform(0.05, 0.11) * S
        ob.scale = (size, size, size)
        ob.visible_shadow = False
        sc.collection.objects.link(ob)
    placed = front
    while placed < count:
        p_ = np.array([rng.uniform(-1.15, 1.15) * reach + mid[0], rng.uniform(-1.15, 1.15) * reach + mid[1], rng.uniform(top - 4, top + 12)])
        if np.hypot(p_[0] - mid[0], p_[1] - mid[1]) < reach * 0.8 and p_[2] < top + 2:
            continue  # not inside the land
        ob = bpy.data.objects.new("mote", speck)
        ob.location = Vector(p_.tolist()) * S
        size = rng.uniform(0.03, 0.08) * S * (0.5 if close else 1.0) * grown
        ob.scale = (size, size, size)
        ob.visible_shadow = False
        sc.collection.objects.link(ob)
        placed += 1

# ---------------------------------------------------------------- haze / mist
Hz = L["haze"]
if Hz:
    by0, by1, half = Hz.get("box", (origin[1] - 25, origin[1] + 145, 220))
    hx, hz = Hz.get("centre", (origin[0], origin[2]))
    bpy.ops.mesh.primitive_cube_add(size=1, location=((hx - origin[0]) * S, (origin[2] - hz) * S, ((by0 + by1) / 2 - origin[1]) * S))
    vol = bpy.context.active_object
    vol.scale = (2 * half * S, 2 * half * S, (by1 - by0) * S)
    m = bpy.data.materials.new("haze")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    outn = nt.nodes.new("ShaderNodeOutputMaterial")
    pv = nt.nodes.new("ShaderNodeVolumePrincipled")
    pv.inputs["Color"].default_value = (*Hz["colour"], 1)
    pv.inputs["Anisotropy"].default_value = Hz.get("anisotropy", 0.5)
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Position"], sep.inputs["Vector"])
    if Hz["kind"] == "layer":
        # full density up to the soft top, then a trace of it above for air; noise heaves the top into billows
        height = sep.outputs["Z"]
        if Hz.get("billow"):
            heave = math_node(nt, "MULTIPLY", math_node(nt, "SUBTRACT", noise(nt, Hz["billow_size"], 4.0), 0.5), -2 * Hz["billow"] * S)
            height = math_node(nt, "ADD", height, heave)
        density = band(nt, height, (Hz["top"] - Hz["soft"] - origin[1]) * S, (Hz["top"] + Hz["soft"] - origin[1]) * S,
                       (Hz["density"] / S, Hz["density"] * Hz.get("above", 0.03) / S))
        if Hz.get("radius"):
            # kept to the island: gone a few blocks past `radius` from the middle, so it hangs on the land, not the stage
            flat = nt.nodes.new("ShaderNodeVectorMath")
            flat.operation = "LENGTH"
            xy = nt.nodes.new("ShaderNodeCombineXYZ")
            nt.links.new(sep.outputs["X"], xy.inputs["X"])
            nt.links.new(sep.outputs["Y"], xy.inputs["Y"])
            nt.links.new(xy.outputs["Vector"], flat.inputs[0])
            keep = band(nt, flat.outputs["Value"], (Hz["radius"] - 3) * S, (Hz["radius"] + 1) * S, (1.0, 0.0))
            density = math_node(nt, "MULTIPLY", density, keep)
    else:
        # thinning with height above the water: the river's surface is the valley floor
        zs = faces_of(lambda n: n == "water")[:, 2]
        floor = Hz["floor"] - origin[1] if "floor" in Hz else float(np.median(zs)) if len(zs) else 16.0
        print("[looks] valley floor y %.1f" % (floor + origin[1]))
        above = math_node(nt, "SUBTRACT", sep.outputs["Z"], floor * S)
        thin = math_node(nt, "EXPONENT", math_node(nt, "MULTIPLY", above, Hz["falloff"] / S), 0)
        density = math_node(nt, "MULTIPLY", math_node(nt, "MINIMUM", thin, 1.0), Hz["density"] / S)
    nt.links.new(density, pv.inputs["Density"])
    nt.links.new(pv.outputs["Volume"], outn.inputs["Volume"])
    vol.data.materials.append(m)
    sc.cycles.volume_step_rate = 4.0
    # light scattered more than once is what makes thick fog glow rather than sit grey
    sc.cycles.volume_bounces = Hz.get("bounces", 4)
    sc.cycles.volume_max_steps = 256

# ---------------------------------------------------------------- ink
if L["lines"]:
    # outlines need faces that share edges: the export writes each quad on its own, so weld them first
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
    bm.to_mesh(me)
    bm.free()
    sc.render.use_freestyle = True
    bpy.context.view_layer.use_freestyle = True
    sc.render.line_thickness_mode = "ABSOLUTE"
    fs = bpy.context.view_layer.freestyle_settings
    fs.use_culling = True
    fs.crease_angle = math.radians(134)
    ls = fs.linesets[0] if len(fs.linesets) else fs.linesets.new("ink")
    ls.select_by_visibility = True
    ls.select_silhouette, ls.select_crease, ls.select_external_contour = True, True, True
    ls.select_border, ls.select_material_boundary = False, False
    ls.linestyle.color = L["lines"]["colour"]
    ls.linestyle.thickness = max(1.0, L["lines"]["thickness"] * W / 1920)

for look in (L["look"], "AgX - Punchy", "Medium High Contrast"):
    try:
        sc.view_settings.look = look
        break
    except TypeError:
        continue
sc.view_settings.exposure = L["exposure"]
if "frames" in opt:
    # the town going up: a beat of bare land, the build (slow through the first days), a beat of the finished town;
    # days.txt beside the frames gives each frame's day for a counter
    os.makedirs(OUT, exist_ok=True)
    frames = int(opt["frames"])
    hold_in, hold_out = int(frames * 0.07), int(frames * 0.1)
    orbit = math.radians(float(opt.get("orbit", 0)))
    cam0 = cam.location.copy()
    with open(os.path.join(OUT, "days.txt"), "w") as days_out:
        for f in range(frames):
            u = min(max((f - hold_in) / max(frames - hold_in - hold_out - 1, 1), 0.0), 1.0)
            build_at(plan, u)
            for lo_, b in (lamps if L["lantern"] else []):
                lo_.hide_render = bool(plan["lantern_t"].get(b, -1.0) > u)
            spin = f / max(frames - 1, 1) - 0.5
            cam.location = look_b + Matrix.Rotation(orbit * spin, 3, "Z") @ (cam0 - look_b)
            cam.rotation_euler = (look_b - cam.location).to_track_quat("-Z", "Y").to_euler()
            vk.render(os.path.join(OUT, "f%04d.png" % f), w=W, h=H, samples=48 if PREVIEW else 128)
            days_out.write("%d %d\n" % (f, int(day_at(plan, u)) + 1))
    sys.exit(0)
tick("dressing")
vk.render(OUT, w=W, h=H, samples=48 if PREVIEW else 192)
tick("render")


# ---------------------------------------------------------------- grades on the finished picture
def grade(path, kind):
    img = bpy.data.images.load(path)
    w_, h_ = img.size
    px = np.empty(w_ * h_ * 4, np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h_, w_, 4)
    rgb = px[..., :3]
    grey = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    rng = np.random.default_rng(7)
    if kind == "paper":
        # watercolour on paper: a little less colour, a warm sheet, a fine grain
        rgb = grey[..., None] + (rgb - grey[..., None]) * 0.8
        fine = rng.random((h_, w_))
        coarse = rng.random((h_ // 24 + 1, w_ // 24 + 1)).repeat(24, 0).repeat(24, 1)[:h_, :w_]
        sheet = 0.975 + 0.02 * fine + 0.012 * coarse
        rgb = rgb * sheet[..., None] * np.array([1.0, 0.975, 0.92], np.float32)
    elif kind == "pixel":
        # a quarter the size, more colour, then a 48-colour palette by k-means, blown back up hard
        f = 4
        hs, ws = h_ // f, w_ // f
        small = rgb[:hs * f, :ws * f].reshape(hs, f, ws, f, 3).mean((1, 3))
        g = small @ np.array([0.2126, 0.7152, 0.0722], np.float32)
        small = np.clip(g[..., None] + (small - g[..., None]) * 1.3, 0, 1)
        flat = small.reshape(-1, 3)
        palette = flat[rng.choice(len(flat), 48, replace=False)]
        pick = flat[rng.choice(len(flat), min(len(flat), 30000), replace=False)]
        for _ in range(12):
            near = ((pick[:, None, :] - palette[None]) ** 2).sum(-1).argmin(1)
            for k in range(len(palette)):
                if (near == k).any():
                    palette[k] = pick[near == k].mean(0)
        near = ((flat[:, None, :] - palette[None]) ** 2).sum(-1).argmin(1)
        small = palette[near].reshape(hs, ws, 3)
        rgb = rgb.copy()
        rgb[:hs * f, :ws * f] = small.repeat(f, 0).repeat(f, 1)
    px[..., :3] = np.clip(rgb, 0, 1)
    img.pixels.foreach_set(px.ravel())
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    print("[looks] graded", kind)


if L["grade"]:
    grade(OUT, L["grade"])


def aux_pass(path, emit):
    """The same view again, every surface emitting `emit` (a socket maker given the node tree), no lights, no haze,
    no tone mapping: a picture of a quantity rather than of light."""
    m = bpy.data.materials.new("aux")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    nt.links.new(emit(nt), em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    vl = bpy.context.view_layer
    vl.material_override = m
    sc.world = None
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.view_settings.exposure = 0.0
    sc.cycles.samples = 4
    sc.cycles.use_denoising = False
    sc.render.image_settings.color_depth = "16"
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    vl.material_override = None
    img = bpy.data.images.load(path)
    px = np.empty(img.size[0] * img.size[1] * 4, np.float32)
    img.pixels.foreach_get(px)
    return px.reshape(img.size[1], img.size[0], 4)[..., :3]


def outline(path, O):
    """Ink where the depth jumps (relative to its distance) or the surface turns sharply, darkening the picture's own
    colour there, so lines are coloured by what they edge rather than black."""
    for o in sc.objects:
        if o.type == "MESH" and o is not world_obj:
            o.hide_render = True  # the haze: a solid box once every surface emits
    far = 3.0 * (look_b - cam.location).length

    def depth(nt):
        cam_data = nt.nodes.new("ShaderNodeCameraData")
        scale = nt.nodes.new("ShaderNodeMath")
        scale.operation = "DIVIDE"
        scale.inputs[1].default_value = far
        nt.links.new(cam_data.outputs["View Distance"], scale.inputs[0])
        return scale.outputs["Value"]

    def facing(nt):
        geo = nt.nodes.new("ShaderNodeNewGeometry")
        half = nt.nodes.new("ShaderNodeVectorMath")
        half.operation = "MULTIPLY_ADD"
        half.inputs[1].default_value = (0.5, 0.5, 0.5)
        half.inputs[2].default_value = (0.5, 0.5, 0.5)
        nt.links.new(geo.outputs["Normal"], half.inputs[0])
        return half.outputs["Vector"]

    base = os.path.splitext(path)[0]
    d = aux_pass(base + "_depth.png", depth)[..., 0]
    n = aux_pass(base + "_normal.png", facing) * 2 - 1
    edge = np.zeros(d.shape, np.float32)
    for dy, dx in ((0, 1), (1, 0), (1, 1), (1, -1)):
        d2 = np.roll(d, (dy, dx), (0, 1))
        n2 = np.roll(n, (dy, dx), (0, 1))
        near = np.maximum(np.minimum(d, d2), 1e-4)
        jump = np.clip((np.abs(d - d2) / near - O["depth"]) / O["depth"], 0, 1)
        jump[(d <= 0) != (d2 <= 0)] = 1.0  # a silhouette against the stage
        turn = np.clip((O["normal"] - (n * n2).sum(-1)) / 0.2, 0, 1)
        turn[(d <= 0) | (d2 <= 0)] = 0.0
        edge = np.maximum(edge, np.maximum(jump, turn))
    for _ in range(O.get("width", 1) - 1):
        edge = np.maximum(edge, np.maximum(np.roll(edge, 1, 0), np.roll(edge, 1, 1)))
    img = bpy.data.images.load(path)
    px = np.empty(img.size[0] * img.size[1] * 4, np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(img.size[1], img.size[0], 4)
    px[..., :3] *= (1 - O["strength"] * edge)[..., None]
    img.pixels.foreach_set(px.ravel())
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    for f in (base + "_depth.png", base + "_normal.png"):
        os.remove(f)
    print("[looks] outlined")


if L["outline"]:
    outline(OUT, L["outline"])


def blur(a, radius, passes=3):
    """A box blur run three times (near enough a gaussian), along both axes, by running sums."""
    for _ in range(passes):
        for axis in (0, 1):
            pad = [(0, 0)] * a.ndim
            pad[axis] = (radius + 1, radius)
            c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis, dtype=np.float64)
            hi = np.take(c, range(2 * radius + 1, c.shape[axis]), axis=axis)
            lo = np.take(c, range(0, c.shape[axis] - 2 * radius - 1), axis=axis)
            a = ((hi - lo) / (2 * radius + 1)).astype(np.float32)
    return a


def kuwahara(rgb, r):
    """A painting from the picture: each pixel takes the mean of whichever of its four corner squares (r + 1 pixels a
    side) is calmest, so colour is laid in smooth strokes but never smeared across an edge."""
    h_, w_ = rgb.shape[:2]
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)

    def box(a):
        # the sum over the (r + 1) square whose bottom-right corner is each pixel, by an integral image
        c = np.pad(a, ((r + 1, 0), (r + 1, 0)) + ((0, 0),) * (a.ndim - 2)).cumsum(0).cumsum(1)
        return (c[r + 1:, r + 1:] - c[:-r - 1, r + 1:] - c[r + 1:, :-r - 1] + c[:-r - 1, :-r - 1]) / (r + 1) ** 2

    m, m2, l1 = box(rgb), box(lum * lum), box(lum)
    var = m2 - l1 * l1
    best = np.full((h_, w_), np.inf, np.float32)
    out = np.zeros_like(rgb)
    for dy in (0, r):
        for dx in (0, r):
            # the square from (y - r + dy .. y + dy, x - r + dx .. x + dx): shift the corner-anchored sums by (dy, dx)
            mm = np.pad(m, ((0, r), (0, r), (0, 0)), mode="edge")[dy:dy + h_, dx:dx + w_]
            vv = np.pad(var, ((0, r), (0, r)), mode="edge")[dy:dy + h_, dx:dx + w_]
            take = vv < best
            best = np.where(take, vv, best)
            out = np.where(take[..., None], mm, out)
    return out.astype(np.float32)


def harmonise(rgb, hues, strength):
    """Every coloured pixel's hue pulled part of the way to the nearest of a few chosen hues (degrees), more where the
    colour is stronger: the scene keeps its colours but they agree, as in an art-directed film."""
    mx, mn = rgb.max(-1), rgb.min(-1)
    span = mx - mn
    sat = np.where(mx > 1e-6, span / np.maximum(mx, 1e-6), 0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    safe = np.maximum(span, 1e-6)
    h = np.where(mx == r, ((g - b) / safe) % 6, np.where(mx == g, (b - r) / safe + 2, (r - g) / safe + 4)) / 6.0
    targets = np.array(hues, np.float32) / 360.0
    diff = (targets[None, None, :] - h[..., None] + 0.5) % 1.0 - 0.5
    nearest = np.take_along_axis(diff, np.abs(diff).argmin(-1)[..., None], -1)[..., 0]
    h = (h + nearest * strength * np.clip((sat - 0.08) / 0.3, 0, 1)) % 1.0
    i = np.floor(h * 6).astype(np.int32) % 6
    f = h * 6 - np.floor(h * 6)
    p_, q, t = mx * (1 - sat), mx * (1 - sat * f), mx * (1 - sat * (1 - f))
    out = np.stack([np.choose(i, [mx, q, p_, p_, t, mx]), np.choose(i, [t, mx, mx, q, p_, p_]), np.choose(i, [p_, p_, t, mx, mx, q])], -1)
    return out.astype(np.float32)


def finish(path, F):
    """The picture finished like a painting lit for a poster: lights that bloom, cool shadows and warm highlights,
    a little darkening at the corners and a fine grain."""
    img = bpy.data.images.load(path)
    w_, h_ = img.size
    px = np.empty(w_ * h_ * 4, np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h_, w_, 4)
    rgb = px[..., :3].copy()
    if F.get("paint"):
        rgb = kuwahara(rgb, max(2, int(round(F["paint"] * w_ / 1920))))
    if F.get("bloom"):
        threshold, strength, reach = F["bloom"]
        f = 4
        hs, ws = h_ // f, w_ // f
        bright = np.clip(rgb - threshold, 0, None)[:hs * f, :ws * f].reshape(hs, f, ws, f, 3).mean((1, 3))
        glow = blur(bright, max(1, int(reach * w_ / f)))
        glow = glow.repeat(f, 0).repeat(f, 1)
        rgb[:hs * f, :ws * f] += glow * strength
    if F.get("palette"):
        rgb = harmonise(rgb, *F["palette"])
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    if F.get("split"):
        rgb += ((1 - lum) ** 2)[..., None] * (np.array(F["shadows"], np.float32) - 0.5) * F["split"]
        rgb += (lum ** 2)[..., None] * (np.array(F["highlights"], np.float32) - 0.5) * F["split"]
    if F.get("vignette"):
        yy, xx = np.mgrid[0:h_, 0:w_].astype(np.float32)
        r2 = ((xx - w_ / 2) / (w_ / 2)) ** 2 * 0.6 + ((yy - h_ / 2) / (h_ / 2)) ** 2 * 0.4
        rgb *= (1 - F["vignette"] * np.clip(r2, 0, 1.5) ** 1.5)[..., None]
    if F.get("grain"):
        rgb += np.random.default_rng(3).normal(0, F["grain"], (h_, w_, 1)).astype(np.float32)
    px[..., :3] = np.clip(rgb, 0, 1)
    img.pixels.foreach_set(px.ravel())
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    print("[looks] finished")


if L["finish"]:
    finish(OUT, L["finish"])
