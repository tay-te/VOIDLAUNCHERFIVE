# VOID — promotional film

Boards live in Figma: file `RShlfbx2TfxrdleKPIry8w`, page **Promo — film boards** (`316:2`).
Fifty-two frames, 1920 × 1080, built in the quiet cell system. §2 is the film — ten boards
that cut together. §2a–§2f are a campaign library: alternates, product beats, pixel boards
and brand assets to draw on for social, store shots, site heroes and cutdowns. `quiet-cell-system.md` is still the
authority; where a board and the contract disagree, the contract wins.

**This file is the treatment — what each shot is and how it moves. The build is
`promo-film-ae.md`:** project setup, the cell-grid rigs, the expression library, which AE
effects are allowed, the capture spec, and the order to work in. It overrules §3's implied
24 fps in favour of a 60 fps master, and explains why.

---

## 1. The idea

Every client in this category sells the same way: fast cuts, neon, glow, a drop. VOID's
whole design position is the opposite — flat, monochrome, and coloured **only where
something is live**. A loud trailer would contradict the product on screen.

So the film is the design system played in time. Long holds, hard cuts, no dissolves, no
camera shake, no glow. The picture is grayscale except for one hue per shot, and that hue
only ever lands on the live value — the meter cell under the pointer, the selected loadout,
the crosshair centre, an fps figure past its threshold. **That arrival is the beat.** It is
the only thing in the film that "pops", which is precisely why it does.

Restraint is the flex. The film should feel like it was cut by someone who did not need to
convince you.

---

## 2. Shot list

| # | Board | In | Out | What it is |
|---|---|---|---|---|
| 01 | Cold open | 0:00 | 0:05 | Mark assembles out of the cell field |
| 02 | Title card | 0:05 | 0:11 | "Nothing you didn't ask for." |
| 03 | The launcher | 0:11 | 0:19 | Hard crop — Sword PvP, the dock, Launch |
| 04 | The rule | 0:19 | 0:27 | Macro meter, hue on the live cell |
| 05 | Loadout | 0:27 | 0:32 | Three names, one live dot |
| 06 | The grid | 0:32 | 0:39 | Mod tiles, full bleed |
| 07 | Setup | 0:39 | 0:45 | Properties column, full bleed |
| 08 | In game | 0:45 | 0:51 | HUD over gameplay |
| 09 | The numbers | 0:51 | 0:56 | 142 fps, held |
| 10 | End card | 0:56 | 1:02 | Mark, wordmark, void.gg |

Rhythm alternates product / word / product / word. Boards 03, 06 and 07 are deliberately
composed alike — the match between them is the point, because it is the same shell every
time.

### Per-shot motion

**01 Cold open.** Field is already up at 3%. Mark builds cell by cell — 16 cells, 40 ms
stagger, start from the top row and go clockwise, each cell scaling 0.8 → 1.0 over 140 ms
ease-out. Wordmark wipes in left-to-right after the last cell lands. The field's density
ramp toward centre rises from 0 to full over the whole 5 s. Eyebrow last, straight fade.

**02 Title card.** Type sets by word, not by letter — four holds, 90 ms apart, each word
140 ms ease-out from 8 px below. Hold the finished line for at least 2.5 s. Cut on the hold,
not on the settle.

**03 The launcher.** Push in 3 % over the full 8 s, linear. The shell layers are separated,
so give the dock a touch more travel than the hero title for parallax. At 0:16 the cursor
reaches Launch and the button steps to its pressed fill — a 100 ms step, no bounce, no glow.

**04 The rule.** Meter is already up, monochrome. At 0:21 the hue arrives on the live cell
over 140 ms ease-out, with ±1 at 40 % and ±2 at 16 % arriving 40 ms behind it. The ambient
field behind ramps 3 % → 22 % over the same 140 ms. Then drag the live cell two steps right
and let the bleed travel with it. Release at 0:25 and drain the field over 200 ms ease-in,
leaving the hue only on the cell that was set. This is the film's thesis shot — hold it.

**05 Loadout.** Dot travels between rows, 200 ms ease-out; the name it lands on lifts from
`--text-muted` to `--text-primary` over the same 200 ms, the one it leaves drops. Nothing
else moves.

**06 The grid.** Tiles wipe in on a 40 ms column stagger, left to right — the same stagger
the inspector uses in the app. Each tile steps from `--card-bg` to its resting fill; no fade
from zero, no scale-up.

**07 Setup.** Rows build top-down, 40 ms apart. The two meters fill left-to-right cell by
cell, 30 ms per cell, live cell last. Toggles flip on a 140 ms ease-out with the knob
travelling and the track stepping fill at the midpoint.

**08 In game.** Drop the gameplay capture into the `01 FOOTAGE PLATE` group and delete the
placeholder. HUD elements are already separated: fps and cps count up over 800 ms, keystroke
caps press to a real input recording, armour bars drain, the amber bar crosses its threshold
on camera. Crosshair is static — never animate the crosshair.

**09 The numbers.** `142` counts up from 0 over 900 ms ease-out, then everything stops for
three full seconds. The stat row below wipes in on a 60 ms stagger. Absolute stillness here
is what makes it land after eight shots of movement.

**10 End card.** Mark and wordmark cut in already assembled — do not repeat the 01 build.
URL fades 200 ms behind, footer 200 ms behind that. Hold to black.

---

## 2a. Hero boards — 11, 12, 13

Three alternates, each an interchangeable heavier version of a shot in the cut above. They
exist to carry the film's biggest moves.

**The principle that makes them possible: the product is flat, the camera is not.** The
system forbids VOID from *rendering* blur, glow, gradient, shadow or 3D transforms. It says
nothing about where a flat plane sits in space or how a camera moves around it. So all the
depth in these boards is geometry — planes at different Z, parallax, scale — and every
surface stays flat-shaded. That is the only way to get spectacle here without the film
contradicting the thing it is selling.

### 11 Exploded — *"Depth, without a single shadow."* (alternate for 04)

The shell's four fill steps — shell, ground, card, raised — plus its ink, pulled apart into
five sheets. It is the literal argument of the heading: the system carries depth in fill
steps, so here are the fill steps, in depth.

The 2D offset on the canvas (+120, −100 per plane) is a stand-in for an oblique projection.
**In AE, zero those offsets, enable 3D on each `PLANE` group and give them real Z** — 0,
−400, −800, −1200, −1600 works — then let a camera make the parallax properly. Every
plane's content is positioned in the same local coordinates, so **animating Z to zero
collapses the five sheets into one correct launcher screen.** That is the shot: the interface
assembling itself out of its own fill steps, with a slow ±12° camera orbit across the
collapse. Fade `03 TRAILS` out as the planes close.

### 12 Dissolve — *"One shape, all the way down."* (alternate for 06)

A tile coming apart into the cells it is built from. Three separate groups so you can time
the disintegration in stages: `05 EDGE BITE` (shell-coloured holes eating the tile's edge)
goes first, `06 EDGE FRAGMENTS` (chunks of the tile's own material) second, `07 DISSOLVE`
(the light specks) last and longest.

Every cell is its own layer, so a single slider plus a stagger expression drives the whole
thing. **Run it in reverse for the better shot** — several hundred cells converging into a
finished, solid tile reads as construction rather than destruction, and it lands the heading
harder. If you displace the cells with fractal noise, keep it to position and scale; no blur.

### 13 The mark — *"You will forget it is running."* (alternate for 10)

The VOID mark drawn at the resolution of the cell field itself, with the enclosed interior
left as a true void. `02 FIELD`, `03 HALO` and `04 MARK` are three layers: ramp the halo and
the mark up out of the field and the logo resolves out of noise without a single effect.

The 3D move here is the best one in the set — **put the three layers at different Z and fly
the camera through the field**, holding the mark at the focal plane while the field streaks
past. Parallax through a few thousand cells is the whole shot. Resist depth of field; get the
separation from Z and scale, not from blur.

---

## 2b. Product boards — 14, 15, 16

Three beats the first pass left on the table. **14 is the important one**: the film showed the
HUD in play but never showed the in-game menu, which is the thing that actually separates this
client from a launcher with a mod list.

### 14 The overlay — *"No alt-tab. No relog."*

The mod menu open over a live match: scrimmed gameplay, centred nav, the grid, and the
inspector beside it. The potion timers and armour bars keep burning at the edges of frame —
that detail is the whole argument, because it is the proof the match did not stop.

**Cut it straight after 08, not instead of it.** Same framing, same footage plate, same HUD
positions: 08 establishes the HUD in play, then the menu opens over that exact frame. It is
a real match cut and it costs nothing to shoot.

Motion: `02 SCRIM` up over 140 ms, `05 CHROME` wipes in, then `06 GRID` on the 40 ms column
stagger the app already uses, then `07 INSPECTOR` slides in from the right. **`03 HUD
REMNANT` never moves or fades** — it is the anchor that proves the game is still running.
ESC reverses the whole thing in 100 ms; per the contract, closing the inspector must not
reflow the first three columns.

### 15 HUD editor — *"Anywhere you want it. To the pixel."* (alternate for 07)

A module mid-placement on the editor canvas, with snap guides live and the coordinate
readout ticking. The guides and the readout are in the hue because a drag in progress is a
live value — the same rule that colours the meter's current cell.

Motion: `06 MODULE` travels under a cursor; `05 GUIDES` **snap** on and off at alignment —
step them in at 100 ms, never tween their position; `08 COORDS` counts continuously. Finish
on four arrow-key taps that nudge the module one pixel each, with the readout ticking 428 →
427 → 426. That last beat sells the precision better than the drag does.

### 16 Party — *"Never queue alone."* (new, optional)

The only human beat in the film. Four identities, three online, one greyed. Deliberately the
quietest board in the set — after eight dense product frames it functions as a rest.

Motion: avatars step in on a 60 ms stagger, names 100 ms behind, status dots last. Nothing
else. If the cut is running long, this is the first board to lose.

---

## 2c. Pixel boards — 17, 18, 19

The most Minecraft-native thing in this system is that **the cell already is a pixel** — a
square with a 30 % radius. Board 13 proved it: draw something at the resolution of the cell
grid and you get a sprite that is still, strictly, the design system. These three do that
deliberately. All three are original artwork drawn on an explicit grid; none of them
reproduces a Mojang texture.

**One hard rule for all pixel boards.** Scale them by whole-number multiples only, and do not
rotate them at all. A 1.3× scale or a 7° rotation resamples the grid and the whole idea
collapses into mush — it is the one thing that will make these look cheap. If a shot needs to
push in, push in by 2× and hold, or re-render the sprite at a larger cell size.

### 17 The sword — *"Every tick accounted for."*

An original 16 × 16 sword on the cell grid, its pixel-art tones mapped straight onto the
system's cell alphas: bright edge 1.0, blade 0.90, shadow 0.50, guard 0.32, handle 0.26. The
tonal ramp a sprite artist would use and the fill steps the design system already has turn
out to be the same ladder.

Motion: build it cell by cell from the pommel up the blade to the tip, 30 ms apart, bright
edge landing last so the highlight reads as the sword catching light — without any light.

### 18 Hearts — *"Half a heart. Full information."*

Ten hearts, nine of them spent ghosts at 13 %, one amber half. The amber is honest under the
accent rule — low health is a *state*, the same justification the armour bar's warn threshold
already uses — and it is the only colour on the board.

Motion: the ghosts never move. Pulse the amber half on a two-frame flicker at roughly 4 Hz,
the way the game does at low health, then bring the readouts in underneath on a 60 ms
stagger. The whole point of the frame is that the client stays calm while the player is one
hit from dead.

### 19 The block — *"Same grid as the game."*

An isometric block whose three faces are three cell alphas — 0.72 top, 0.40 left, 0.22 right.
The argument is in the image: the game's atom drawn in the client's atom, on one shared grid.

It is a true 2 : 1 dimetric projection, so a real 3D camera can match it exactly. Two ways to
shoot it: build it face by face on a 40 ms offset (top, left, right) so it assembles like a
block being placed; or put the three faces on real 3D planes at 90° and orbit — the drawn
projection and the camera projection will line up when the camera hits dimetric.

---

## 2d. Boards 20, 21, 22

The last three gaps: a surface reachable only by typing, the one nav item the film never
showed, and the performance claim as a picture instead of a number.

### 20 Command — *"One keystroke to anything."*

The Ctrl-K palette over a dimmed launcher, query half-typed, five results. The shell behind
is a real clone at 74 % scrim — enough to say *this is inside the app* without competing.

Motion: type the query a character at a time (~110 ms apart) and **re-filter the list on
every keystroke** — the list churning under the typing is the entire shot, and it is the one
place in this film where fast motion is correct, because typing is fast. Then the selection
travels down two rows, 100 ms each, and Enter dismisses. The launcher behind never moves.

Note: the caret is monochrome on purpose. A caret is chrome, not a value or a state, and the
hue is already doing real work on the selected row.

### 21 Cosmetics — *"The one place decoration belongs."*

Five capes as 10 × 16 pixel patterns — solid, checker, Bayer-dithered halftone, the ring
mark, bands — with one selected. The heading is the board's argument: the system bans
decoration everywhere, so the single place it *is* allowed is worth saying out loud.

Motion: the selection rim and hue dot travel between capes, 200 ms, and the name it lands on
lifts to `--text-primary`. Or build each cape cell by cell on a diagonal wipe. Same pixel
rule as §2c — whole-number scaling, never rotate.

### 22 Frame time — *"No spikes. That's the feature."*

Sixty seconds of frame time as a cell histogram, sitting a labelled 9.7 ms under the 60 fps
line. Historical data is monochrome exactly as the accent rule demands; only the newest
column carries the hue.

**The animation writes itself, and it is the accent rule made literal:** scroll the graph
left, admit a new column at the right in the hue, and let it drop to grey the instant the
next one arrives. Colour marks the live value; the moment that value becomes history it
loses the colour. Run it for the full five seconds and never let a bar reach the line.

---

## 2e. Boards 23, 24

### 23 Keybinds — *"Every mod, on a key."*

A full keyboard as keycaps. Bound keys are lifted one fill step; the key currently being
rebound — right shift, which the app already spells `R-SHIFT` for Keystrokes — takes the hue.
The system's keycap and its cell are the same object, so this board needed no new vocabulary
at all.

Motion: the best version is the smallest one. Press a key, let it step to the bound fill in
100 ms, and increment the caption from 5 to 6 mods bound. Then hold it and let the hue arrive
over 140 ms as it enters rebind. Do not ripple the whole keyboard — a keyboard where every
key animates is a screensaver, not a feature.

### 24 Servers — *"Sorted by the only thing that matters."*

The last nav item the film had not shown. Selection is carried by a fill step, so **the only
colour on the board is the ping dots** — green under the 80 ms threshold, amber over it,
which is exactly the behaviour `ping.good_ms` already has in the contract.

Motion: let the pings tick live and **let one dot cross the threshold on camera**. A row
going amber as its ping climbs past 80 is the accent rule doing real work, unprompted, in a
list that is otherwise completely still. Sorting the rows by ping on entry is the other half
of the shot.

**One thing to check before this goes public:** `Hypixel` is a real server and a third-party
mark. It is already in the app's own copy — the Play screen says `HYPIXEL-READY` and
`12 ms to Hypixel` — so this board is consistent with the product, not inventing a claim. The
other four names are invented. If legal wants no third-party marks in the film at all, this
board and the Play hero both need a copy pass.

---

## 2f. Boards 27, 28, 29

At this point the set has outgrown a single sixty-second cut. **Treat §2a–§2f as a campaign
library** — social posts, store shots, site heroes, cutdowns — and §2 as the film. These
three were picked on that basis rather than as shots.

### 27 Left out — *"The features we didn't build."*

Six absences, each marked with an **empty cell** at the system's own `.08` — the empty cell
is the message. It names no competitor and makes no comparison; it just lists what is not
there, which for a client whose entire pitch is restraint is the strongest positioning board
in the set.

Motion: items arrive on a 90 ms stagger, the closer lands 400 ms after the last one, nothing
else moves. A board about absence should not be busy.

**This one makes promises.** Only ship it if *no store, no pass, no ads, no background
service, no telemetry, no paid tier* is true and intended to stay true. It is the one board
here that could become a liability rather than just a design problem.

### 28 Pixel wordmark

VOID as a logotype on a 23 × 7 cell grid, with a halo echoing the letterforms. A reusable
asset, not a shot — title cards, avatars, store icons, stickers.

**The gotcha worth keeping:** with 30 % rounded cells, a one-cell diagonal touches only at
the corners and visually snaps apart. The V read as a U until its taper was widened to
overlap on every step. Any future pixel-glyph work in this system has to avoid thin
diagonals, or thicken them.

### 29 Four hues — *"Four hues. That's the entire palette."*

The one system fact the film never states. Four real controls, each with its current cell in
its category hue — legitimate precisely because the hue sits on a **live value**, which is
the only place §1 permits it.

**Do not restage this as four colour swatches.** Four chips in a row is exactly the "colour
becomes decoration" failure the contract names, and it would make this board argue against
the film's own thesis. The hue has to be doing a job.

Motion: the four live cells arrive top to bottom, 120 ms apart. Nothing else on the board
moves at all.

---

## 2g. Boards 30, 31, 32

### 30 Loadout switch — *"One key. A different game."*

The same screen under two loadouts, side by side: Sword PvP running fps, cps, reach, potions
and keystrokes; Bedwars running coordinates, ping, a team panel and armour. It is the only
board that shows what a loadout *does* rather than what it is called.

Motion: cross-fade is wrong here. **Hard-cut the right pane's widgets in on a 40 ms stagger
while the left pane's cut out**, so the change reads as instantaneous rather than as a
transition. The pane frames and the labels never move.

### 31 The toggle — *"Nothing takes longer than 140 milliseconds."*

The signature control at fifteen times size, caught mid-flip, with four ghost knobs trailing
behind it. The ghosts are spaced on the actual ease-out curve, so **the easing is drawn into
the still** — they bunch toward the end because that is what ease-out does. The claim and the
picture are the same fact.

Motion: run the knob from the first ghost to rest in exactly 140 ms and drop the trail. If
you are compositing at 24 fps that is 3.4 frames, which is the point — it should be over
before it registers as motion.

### 32 Footprint — *"What it costs you."*

Deliberately the mirror of board 27: that one lists what is absent, this one lists what is
spent. Same left margin, same rule, same closing line position — cut them back to back and
they read as one statement.

---

## 2i. Boards 33, 34, 35

Same instinct as 30–32: draw the thing you cannot normally see — a state model, an
interaction, a budget.

### 33 Four states — *"There is no back button."*

The overlay's entire layout model in one frame: two switches, `layout grid|list` and
`inspector open|closed`, and the four states they produce. Four, not three modes — the
distinction §7 of the contract insists on.

Motion: animate *between* the four. Grid → list reflows tiles into rows; opening the
inspector wipes columns out on the 40 ms stagger and slides the panel in. **The first three
columns must not move in any of these transitions** — that is the rule the whole layout
exists to protect, so breaking it in the film would be worse than not showing it.

### 34 Hover trail — *"Dead quiet until you touch it."*

§5 of the contract, drawn: a monochrome cell field warmed in the hue along the path a pointer
took, brightest at the head, draining back along the tail, with the tile it arrived at lifted
one fill step.

In AE this is one null moving along a path with every cell's opacity driven by distance to
it — 1,755 cells, one expression, no pre-comps. Warm in over 140 ms, drain over 200 ms.

**Build note:** the falloff has to be linear in distance and weighted hard toward the head
(`age^2.5`). Squaring the distance falloff crushes the whole trail to invisibility — I built
it that way twice before it read.

### 35 Frame budget — *"We take three tenths of a millisecond."*

One frame at 60 fps as fifty cells. Game tick takes six, world render takes fourteen,
headroom takes twenty-nine, and VOID takes **one**. The smallness is the entire argument, so
resist any urge to make the hue cell bigger or add a callout ring — the moment it looks
important, the point is gone.

Motion: fill the bar left to right at ~30 ms per cell, then stop dead on the single hue cell
and hold for three seconds. Numbers are placeholders — see §2h.

---

## 2j. Generic layouts — 36 to 43

Workhorses, not concepts. The library was heavy on one-off ideas and thin on the ordinary
layouts a marketing site or deck actually runs on, so these three are deliberately plain:
**swap the copy and they carry any feature set.** Treat them as templates.

- **36 Feature trio** — eyebrow, heading, rule, three columns of glyph + title + body. The
  glyphs are built from cells rather than drawn as icons, so a new one is a five-minute job
  in the same vocabulary.
- **37 Three steps** — numbered getting-started row with cell connectors between the steps.
- **38 Download** — centred CTA: mark, heading, subline, a primary and a secondary button,
  meta line. The only centred composition in the whole set, which is what makes it read as a
  CTA rather than another content board.

Two copy notes, both load-bearing:

- Step 02 says **"Your Microsoft account, the same one the game uses."** The obvious generic
  line here is "no account needed" — it would be false. The client signs in through Microsoft
  like every Minecraft launcher, and claiming otherwise in a download flow is the kind of
  thing that gets a client dismissed on its first review.
- Board 38's buttons name macOS and Windows because the end card already commits to both. If
  Windows slips, that board and board 10 change together.

**Deliberately not built: a testimonial board.** The generic version needs a quote and a
name, and inventing either would be a fabricated endorsement rather than a placeholder like
the numbers in §2h. If you want one, give me a real quote and I will set it.

### The rest of the site sections — 39, 40, 41, 42, 43

- **39 FAQ** — two columns of three question/answer pairs. Questions in `--text-primary`,
  answers in `--text-muted`; that alone carries the hierarchy, no cards needed.
- **40 Feature row** — the alternating section archetype: text block left, live product crop
  right. The crop is a real cloned shell inside a clipping frame, so re-pointing it at a
  different screen is a two-line change rather than a redraw. Mirror it for the next section.
- **41 Requirements** — a plain spec table. Label left, value right, hairline between.
- **42 Changelog** — release list with the newest entry carrying the hue as the live one.
- **43 Footer** — lockup, four link columns, and the line that has to be there:
  **NOT AFFILIATED WITH MOJANG OR MICROSOFT.** Any third-party Minecraft client needs it, and
  it is easier to design in now than to retrofit under the footer later.

Placeholder content, same rule as §2h: 41's specs and 42's release notes and dates are
invented. Both groups are named `— PLACEHOLDER` in Figma. 42 in particular should be
generated from the real tag log rather than hand-kept.

**A Figma gotcha worth keeping.** Board 41 shipped broken on the first pass because I
selected the value nodes by comparing a fill's colour channel to a constant — Figma stores
those as floats that do not survive an exact `===`. Nothing threw; the values silently stayed
at x=0 and sat on top of the labels. **Track node references in an array as you create
them; never re-find nodes by colour equality.**

---

## 2k. Promotional screens — 44, 45, 46

**Website sections and promotional screens are different objects,** and §2j drifted into the
first while the brief asked for the second. A section — an FAQ, a spec table, a footer — only
works inside a page; it has no job on its own. A promotional screen has to sell unaccompanied
and survive being seen at thumbnail size: product hero'd, one claim, nothing else. Keep §2j
for building a site; reach for these to post, announce, or fill a store listing.

- **44 Product hero** — the whole launcher window, centred, one line above it. The most
  ordinary promotional screen there is, and the set somehow did not have one: board 03 is a
  hard crop for the film and board 40 is a small section crop. This is the default answer to
  "send me a screenshot of it."
- **45 The stack** — Play, Mods and Setup layered into one receding image. The argument of
  board 11 made cheap and legible: three screens, visibly the same window.
- **46 Announce** — mark, claim, product rising into frame from the bottom edge. Launch-post
  composition; the copy is meant to be swapped (*Out now* → *1.5 is out* → whatever ships).

All three are clean clones of the source-of-truth shells, so they stay accurate as the
product changes. Re-cloning is a one-line edit.

---

## 2l. The pixel world — 47 to 52

**The correction that produced these:** the boards had drifted technical — spec annotations,
millisecond bars, state maps, hex values. Engineering diagrams with nice typography. What was
wanted was screens that *showcase* rather than explain. So: no annotations, no numbers except
the HUD's own, no headings on most of them. Scenes.

They also fix the oldest weakness in the set. Every in-game board sat on a flat grey
placeholder plate; **47 and 48 supersede 08 and 14** with an actual world behind the UI.

- **47 The arena** — two figures on a bridge over the void, HUD live over it.
- **48 Menu in play** — the same frame, scrimmed, with the overlay open. A true match cut:
  48 clones 47's scene groups outright, so the worlds are identical by construction.
- **49 Void sky** — the mark hanging over a blocky horizon, one small figure. No type at all.
- **50 Depth** — the same world staged in five tonal layers: three ranges receding at 3 %,
  5 % and 7.8 %, the play area at 14–20 %, and a **near-black foreground at 1.6 %**. Closest
  reads darkest because it is backlit. That inversion is what makes the depth convincing.
- **51 Torchlight** — an enclosed cave where every block's own fill steps up with proximity to
  one torch. **Light rendered entirely through cell opacity — no gradient, no glow, nothing
  the system bans.** The cave is what sells it: an open field gave the light nothing to fall
  on, and the first version read as an empty plain.
- **52 The map** — four floating islands over the void, tapered undersides, tiny figures.
  Deliberately textless, with dead space up top for a logo or title to sit over.

### How the scenes are built

One 44 px block grid, `cornerRadius: 3` so blocks tile with just a hint of the cell's
softness — the 30 % cell radius turns terrain into bubbles at this scale, so scenery gets its
own radius while HUD and sprites keep the real cell. Figures are an 8 × 16 sprite at cell
9–14. Terrain opacity is `surface ≈ 0.20, fill ≈ 0.135`; going wider than that gap makes each
surface row read as a floating bar rather than ground.

**The world stays monochrome.** The only colour in any of these is the HUD's own state
colour — green fps, amber hearts, the crosshair's category hue. A scene is depicting the
game, not the client's chrome, so colour there would be defensible; it is still the wrong
call, because the moment the world carries colour the HUD stops being the thing your eye
goes to.

---

## 2h. The numbers on these boards are invented

**Every figure in this set is a placeholder I made up to compose the layouts** — 142 fps,
1 % low 118, 7.0 ms average, 9.7 ms headroom, 12 ms to Hypixel, 48 MB resident, 11 s to
spawn, 48,203 online. None of it is measured.

Three groups are renamed `— PLACEHOLDER, MEASURE BEFORE SHIP` in the Figma layer list
(board 09's hero figure, board 22's bars, board 32's figures) so they are hard to miss, but
the same caution applies to every number on every board.

Before any of this is cut into something public, replace them with real measurements on a
named machine and keep the machine spec somewhere the claims can be defended. A performance
claim in a trailer is a promise, and 1 % lows in particular are the number this audience will
actually test.

---

## 3. Motion grammar

Use the app's own numbers so the film and the product move identically:

| Action | Duration | Curve |
|---|---|---|
| Arrive / hover in | 140 ms | ease-out |
| Leave | 100 ms | ease-in |
| Release drain | 200 ms | ease-in |
| Column / row stagger | 40 ms | — |
| Push-in across a shot | 3–5 % over the shot | linear |

Rules that are not negotiable, because the product cannot do them either:

- **No blur, no glow, no drop shadow, no gradient, no blend mode.** The system has none.
- **No cross-dissolves.** Hard cuts, or a cell-grid wipe.
- Elements arrive by **stepping fill** (`--card-bg` → `--surface-raised`), never by fading
  up from zero with a scale bounce.
- **No colour that is not a live value.** If a hue is on screen, it is on the current meter
  cell, the selected item, a drag handle, or an HUD figure past its threshold. Nothing else.
- Cells are the animation unit. When something builds, it builds cell by cell.

---

## 4. Layers and export

Every board's top-level groups are numbered in z-order (`01 BG`, `02 …`, `09 SLATE`), so an
alphabetical export gives a correct stacking order.

- Everything is vector and live text — export at 2× or 4× with no quality loss, or bring the
  boards in as SVG if you want to keep paths editable.
- **`09 SLATE`** on each board is the shot number and timecode. It is already hidden; it is
  reference only and must never appear in the render.
- **`08 In game / 01 FOOTAGE PLATE`** is a placeholder for gameplay capture. Replace it.
  Everything above it is already separated into HUD groups.
- Boards 03, 06 and 07 contain a full clone of the real launcher shell, cropped by the frame.
  The whole shell is present outside the frame edge, so you can reframe or push further in
  AE without asking for a new export.
- Safe margin is 96 px. Type never comes closer than 160 px to an edge.
- Type is **Outfit** (Light / Regular / Medium). It must be installed before opening the
  boards, or AE will substitute silently.

---

## 5. Sound

Room tone throughout, low and close. A soft tick on each cell stagger — the same 40 ms
spacing as the picture. One bass swell on the Launch press at 0:16 and nothing else until
the end card. No music bed with a drop. If the track has a build, it is the wrong track.

---

## 6. Copy

In the cut, only two boards carry words:

- 02 — *Nothing you didn't ask for.*
- 10 — *void.gg* · MINECRAFT 1.8.9 · MAC & WINDOWS · FREE

The boards in §2a–§2f each carry a heading, since each replaces or extends a
wordless shot:

- 11 — *Depth, without a single shadow.*
- 12 — *One shape, all the way down.*
- 13 — *You will forget it is running.* (eyebrow: RENDERED INSIDE THE GAME · NOT OVER IT)
- 14 — *No alt-tab. No relog.*
- 15 — *Anywhere you want it. To the pixel.*
- 16 — *Never queue alone.*
- 17 — *Every tick accounted for.* (eyebrow: 1.8.9 COMBAT)
- 18 — *Half a heart. Full information.*
- 19 — *Same grid as the game.*
- 20 — *One keystroke to anything.*
- 21 — *The one place decoration belongs.* (eyebrow: COSMETICS · 5 OF 24 UNLOCKED)
- 22 — *No spikes. That's the feature.*
- 23 — *Every mod, on a key.* (eyebrow: KEYBINDS)
- 24 — *Sorted by the only thing that matters.* (eyebrow: SERVERS)
- 25 — *From click to spawn in eleven seconds.* (eyebrow: LAUNCHING · SWORD PVP · 1.8.9)
- 26 — *Twenty-four. That's the whole list.* (eyebrow: MODS)
- 27 — *The features we didn't build.* (eyebrow: WHAT WE LEFT OUT)
- 29 — *Four hues. That's the entire palette.* (eyebrow: CATEGORY HUES)

- 30 — *One key. A different game.*
- 31 — *Nothing takes longer than 140 milliseconds.*
- 32 — *What it costs you.* (eyebrow: FOOTPRINT)
- 33 — *There is no back button.* (eyebrow: LAYOUT × INSPECTOR)
- 34 — *Dead quiet until you touch it.*
- 35 — *We take three tenths of a millisecond.* (eyebrow: ONE FRAME AT 60 FPS · 16.7 MS)
- 36 — *Three things, done properly.* (eyebrow: WHAT YOU GET)
- 37 — *Three steps to the first match.* (eyebrow: GETTING STARTED)
- 38 — *Get VOID.* (centred CTA)
- 39 — *Things people ask.* (eyebrow: QUESTIONS)
- 40 — *It opens over the game.* (eyebrow: THE OVERLAY)
- 41 — *What it needs.* (eyebrow: REQUIREMENTS)
- 42 — *What changed.* (eyebrow: RELEASES)
- 43 — footer, no heading
- 44 — *Everything in one window.* (eyebrow: VOID · MINECRAFT PVP CLIENT)
- 45 — *Five screens. One window.* (eyebrow: ONE SHELL)
- 46 — *Out now.* (announce, copy meant to be swapped)

Boards 47–52 carry no headings at all — they are scenes (§2l).

Board 28 carries no heading — it is the pixel wordmark itself.

Everything else on screen is real product text. Nothing in the film explains the design
system; the film demonstrates it and lets the viewer notice.
