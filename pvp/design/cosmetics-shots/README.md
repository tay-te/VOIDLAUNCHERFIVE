# Cosmetics shots

The launch line, **VOID luxe** (catalog v4, after the polish round; the art bible is
`docs/cosmetics.md`, "The collection"), rendered in game by the showcase with the LOD pinned at full
quality (`-Pvoid.showcase.quality=1`), the material shader and bloom on, and `-PvoidGlDebug` (zero GL
errors). Six mannequins wear the collection's sets, each one fabric, one metal and one jewel:
Atelier (aubergine, silver, amethyst), Regent (charcoal, gold, ember-garnet), Nocturne (navy,
silver and steel, ice-topaz), Meridian (bottle green, brass, jade) and the House pieces (camel
Origin, oxblood Bespoke; pearl).

| file | what |
|---|---|
| `items-day.png`, `items-night.png` | every worn item alone on a mannequin, framed for its slot (capes, back items, hats, halos, masks) |
| `lineup-front.png`, `lineup-back.png`, `night-front.png`, `night-back.png` | the six sets abreast, by day and by night |
| `far-back.png`, `far-back-night.png` (+ `-2x`) | the line-up at ten blocks, the distance an item has to read at in a fight |
| `capes-close*.png`, `back-close*.png`, `heads-day.png`, `heads-night.png`, `badges.png` | close-ups per slot |
| `hero-night.mp4`, `hero-dusk.mp4`, `night-loop.mp4` | hero clips: three sets breathing, by night and at dusk |
| `run-capes.mp4`, `run-dusk.mp4`, `run-capes6.mp4`, `run-capes6-night.mp4`, `run-atelier.mp4`, `sprint-jump.mp4`, `wings-*.mp4` | running, turning, jumping, gliding (`run-capes6`: all six capes with their sets' trails) |
| `trails.mp4`, `trails-night.mp4` | the four trails behind sprinters, side on, five to eight blocks off |
| `fight.mp4`, `fight-night.mp4`, `fight-atelier.mp4`, `brawl-20.mp4` | the scripted duel (a hit taken, a six-hit combo, a kill): jewels flare with the combo and pulse on the kill |
| `emotes.mp4`, `emotes-a.mp4`, `emotes-b.mp4`, `emote-cam.mp4` | the six emotes (`emotes-a`/`-b`: three at a time, close, from their first frame), and the local player's emote camera |
| `compare/` | before/after sheets of the polish round: one per changed item (`<id>.png`: day, night, ten blocks by day and night at 2x, close, side, behind) and the line-ups (`lineup-*.png`, `far-back*.png`: the pre-polish shot above, the new one below) |
| `before-v4/` | the luxe line's shots before the polish round (grey and beige capes, the boxed hood, the wide-brimmed fedora, the spoked ring) |
| `c-fix/` | the style test's C beside the shipped Atelier pieces, view by view |
| `before-v3/` | the v3 line's shots (with `combined/`, the merged motion + shader build on v3 art); `before-v2/`, `before/` older sets |

Rebuild: `bash make-shots.sh <run>` after a showcase run (the header of `make-shots.sh` has the
command); the per-item sheets come from two runs of the polish views (`pv-*`, `ShowcasePolish`) through
`bash make-compare.sh <before-run> <after-run> compare <id ...>`.
