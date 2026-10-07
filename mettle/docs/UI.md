# VOID Mettle — UI

> **Target: an Ultralight HTML/CSS overlay (user, 2026-10-05).** Screens are designed in Figma and built later as an
> overlay, like the PvP client's menu; the bridge contract is [OVERLAY.md](OVERLAY.md). The native Java screens
> described in §1's "Text and scale" note are the dev fallback until then.

Owned by the **ui** module, except the gear panel's slot handling (gear). **Designed in Figma first:** VOID Platform
file (`3c0Wgc4ZKFSpm98fWJoARS`), page `264:2` "Mettle · Character & Gear (explore)". The frames there are the
reference; this doc is the behaviour.

| Frame | Node |
|---|---|
| Read me · accent · notes | `284:2980` · `284:3019` · `284:3086` |
| 1a / 1b / 1c: background picker (none, Ranger, Wanderer) | `268:51389` / `269:50938` / `271:567` |
| 2: character screen, Attributes | `272:54828` |
| 3: character screen, Skills | `274:52503` |
| 4a / 4b: Mining perk tree (early, mid) | `275:53076` / `276:1251` |
| 5a / 5b / 5c: inventory (no gear, full gear, Overburdened) | `280:1854` / `279:1376` / `280:2604` |
| 6a / 6b: HUD chips | `281:58476` / `281:58614` |
| 7: belt radial | `283:58532` |
| Pixel kit (`pixel / Mettle / *`, 48 sprites) · vanilla references | `264:10` · `266:2` |

---

## 1. Rules (VOID panels)

- **Flat.** No glass, blur, gradients or shadows. State is carried by fill steps: shell #0B0B0C → base #131315 →
  card #191A1C → raised #212225.
- **Text** is #EDEEEF / #9A9DA1 / #626569. Hairline borders are white at low alpha.
- **Rounded everything**: panel 24, card 14, control 9, chip 8, all in Figma pixels at GUI scale 3. The exceptions
  are pixel art, the Minecraft character and vanilla slots inside the vanilla inventory frame.
- **Colour encodes a value or a state, never decoration.**
  - **Accent "Temper" #A7B9D8**, a muted tempered-steel blue, marks live values only: XP fill, the weight fill, the
    current meter cell, available perks, the selected item, a +1 preview. It is cool so that warning states can never
    read as progress, and it stays clear of the four VOID category hues.
  - **State colours:** Burdened amber #E2B866; Overburdened and Pinned red #C05B54.
- **Layout:** a centred main panel, with secondary content in **fixed** side panels. Nothing is draggable.
- **Cards carry pixel art** (sprites `pixel / Mettle / <name>`): backgrounds, skills, perks, gear items.
- **Rounded panels are nine-slice GUI sprites** (`GuiSpriteScaling.NineSlice` via `.png.mcmeta`), because 26.3 has no
  rounded-rectangle draw call. The ui module owns them in `client.ui.kit`, along with panel, card, cell, meter, chip,
  button, tooltip, tab and the radial.
- **Screens never pause** in multiplayer. Esc closes. Every screen works at GUI scale 2–4 and down to an 854×480
  window, scrolling where it must.
- **Colour is never the only signal.** Locked perks show a lock glyph and the reason; load bands show their name.
- **Text and scale: native resolution (built 2026-10-04 with the background picker; user to confirm).**
  - The frames use Outfit at 10–34 px on a 1920 px screen, smaller than Minecraft's own font can go in GUI units. So
    VOID panels are drawn at native screen resolution: `client.ui.kit.UiCanvas` scales the pose by 1 ÷ GUI scale and
    every coordinate is a whole screen pixel.
  - Outfit (OFL, from `pvp/design/promo/motion/fonts`) ships as TrueType font providers generated at build time,
    one per weight (300/400/500) and whole pixel size (7–72), oversample 1. Minecraft samples font atlases with
    nearest filtering, so glyphs stay crisp only when raster pixels land 1:1 on the screen.
  - Panels follow the window, not the GUI scale: a Figma pixel is `u = m ÷ 6` screen pixels, where `m` (screen
    pixels per pixel of a 48 × 24 scene) is `ceil(6 × height ÷ 1080 − 0.15)`, limited so the panels fit. 1080p gets
    the frames exactly; 1440p ×1.33; 768p ×0.83; 720p ×0.67. Pixel art is always drawn at a whole multiple.
  - Rounded corners are anti-aliased masks generated per pixel radius (`RoundCorners`), not nine-slice sprites.
  - Minecraft hints Outfit to whole pixels, so text runs a few percent wider than in Figma; `UiCanvas.fit` shrinks
    a label up to 15 % where it must fit a chip. Outfit has no arrow glyphs; keycap arrows are drawn.

## 1b. Where the screens live now (2026-10-07)

The player-facing Classes and Gear screens are built into the **VOID Hub** (`hub/`, branch `claude/hub-mettle`),
reading this mod's overlay state (OVERLAY.md "Hosts"). The direction the user picked after several Figma rounds
(page 264:2, sections 11–15) is **O, "every skill has its own colour"**:

- **Hue = which skill.** Each skill's data names a signature colour (`"colour"` in `data/mettle/mettle/skill/*.json`):
  Mining amber, Blades steel blue, Heavy Weapons crimson, Marksmanship green, Defense azure, Athletics teal, Survival
  moss, Smithing ember; wave 2 has its own. Cards, trees, XP bars and the Learn button use it.
- **Glow = how far you have come.** Locked perks dark and dashed; available ones ringed with a dot; learned ones lit
  with a halo and glowing links; the selected perk brightest.
- **Champagne gold = earned** (Feats, Teachings, Synergies), the same in every skill.
- Layout: a **gallery** of skill cards under a character strip (background, level, attributes with +), and a **skill
  tree** with a coloured skill rail, the tree (tiers as rows, two path panels, an earned column) and the selected perk
  in a bottom action bar (effect at the next rank, per-rank values, requirements, Learn / Rank up / Choose).
- Rank titles by level band (Untried, Novice 1–4, Apprentice 5–9, Journeyman 10–14, Expert 15–19, Master 20) with a
  noun per skill ("Journeyman Miner"), and one line of lore per skill, all in the lang files.

The rules in §1 stay for chrome; the colour and glow are for the skill content only (the user asked for "more color
or glow" there).

## 2. Background picker

- **When it opens:**
  - on first join, after the world has loaded
  - from the K screen while no background is chosen
  - after an op reset
- **Layout:** a centred panel, "Choose your background", subtitled "Every skill can be learned. This is only where
  you start." It holds nine pixel-art cards.
- **Selecting a card** fills a fixed side panel with:
  - the five attributes as small cell meters, with the bonus highlighted
  - favoured skills as chips
  - the signature perk in one sentence
  - the kit as real item icons
  - a **Begin** button
- **Wanderer** shows a pick-two skill grid instead of fixed chips. Coming skills are shown but can't be picked. Its
  4 points are placed by dragging along an attribute's meter (the quiet-cell meter interaction) or with +/− keys
  while a row is focused.
- **Closing without choosing is allowed** (CHARACTER.md §1). A "Choose your background" button stays on the K screen.
- **On Begin:**
  - the server validates the choice, applies the background, gives the kit and closes the picker
  - a single hint chip appears: "Press K to see your character"

## 3. Character screen (K)

A centred panel with a header that shows:
- the background card (small pixel art)
- the character level and its XP bar
- an unspent-points badge
- two tabs: **Attributes** and **Skills**
- a small **Reset** text button (free until level 5, then states its cost)

**Attributes tab.** Five rows. Each row has:
- the name and value
- a 20-cell meter (quiet-cell meter: 12 px cells on a 17 px step)
- a **+** control, shown while points are unspent
- plain effect lines ("+3 kg carry · +2 % melee damage · +2 % mining speed per point")
- the skills it governs, as small icons

**Skills tab.** A grid of the 15 skills. Each tile has:
- a pixel-art icon, name and level
- an XP bar toward the next level
- a perk-point badge when points are unspent

Wave 2 and 3 skills appear dimmed as "coming" until their phase ships, and are hidden by config after that if a
server disables them.

**Perk tree.** Opened by clicking a skill. It replaces the grid inside the same panel, with a back arrow.
- **Layout:** four tier rows of three rounded cells, each with a pixel-art icon.
- **Tier labels** show what opens them ("Tier II · Mining 6 · Strength 6").
- **Cell states:**
  - **learned:** raised fill and a check
  - **available:** accent ring
  - **locked:** dim, with a lock glyph
- **Side panel** (fixed): the selected perk's name, a one-sentence description, its cost, and either **Learn · N points** or the exact reason it is locked.
- **No confirmation dialog.** Respec makes mistakes cheap.

## 4. Inventory

- **Three gear slots** sit in the column between the player figure and the crafting grid, above the offhand slot. They are vanilla-styled because they live inside the vanilla frame, with a faint outline icon when empty (BURDEN.md §5).
- **Tooltip weight line** on every item (BURDEN.md §4).
- **Restyling the vanilla frame is an open question** for the Figma review.

## 5. Gear panel

A fixed VOID panel docked to the right of the inventory (and of the common container screens, BURDEN.md §6).

| Section | Content |
|---|---|
| Weight meter | "38.4 / 64.0 kg", fill in the accent colour, band marks at 75, 100 and 125 %, band name when not Light |
| Heaviest line | Only when Burdened or worse: "Heaviest: cobblestone ×3 stacks · 48.0 kg" |
| Backpack | Its pockets as slots (rows of 9) |
| Belt | Its pockets, numbered in radial order |
| Quiver | Its pockets |

- **With no gear worn,** the panel collapses to the compact weight meter alone.
- **Slots** are VOID-styled cells, the same size as vanilla slots, so dragging between the two feels the same.
- **Small screens.** The panel is about 180 GUI pixels wide. At GUI scale 4 on a 1920 px screen, it doesn't fit
  beside a centred inventory.
  - When it doesn't fit, the inventory shifts left, the way vanilla shifts it for the recipe book.
  - If the recipe book is also open, the two share that space as a toggle: the recipe book or the gear panel, never both.

## 6. HUD chips

These are flat, rounded chips (padding 6/10, radius 8, background rgba(10,11,12,.55)) that appear only when they
matter. They are registered with `HudElementRegistry`. Space above the food and air bars is reserved through
`HudStatusBarHeightRegistry`.

| Chip | When | Where |
|---|---|---|
| **Burdened · 41 / 49 kg** | load band is Burdened | above the food bar |
| **Overburdened** / **Pinned** | stronger state (raised fill, plus band name) | same |
| **+12 Mining** | XP gained | above the hearts (left), so it balances the load chip on the right; merges per skill, fades after 2 s |
| **Mining 6 · Apprentice Miner** | skill level-up, tier opened, perk learned, feat/synergy/teaching earned, character level | a toast, top centre (§6b) |
| **◆ 3 points  K** | unspent points, after a level-up | small, just past the hotbar's right end, fades after 20 s (§6b) |
| Quiver count | holding a bow or crossbow with a quiver worn | just past the hotbar's left end (the offhand side when it's empty) |

## 6b. Progress moments (built 2026-10-07)

Levelling a skill, opening a tier, learning a perk or earning a feat is a **moment**: a chime and a toast, not a line
of action-bar text. The server sends a typed `Moment` payload (`MettlePayloads.Moment` {kind, skill, level, tier, perk,
rank}, sent by `character.moment.Moments` just before the matching feedback chip); the client (`client.moment`) plays
the sound, queues the toast and leaves that chip off the action bar. Other chips (XP gains, signature perks, burden
bands) stay on the action bar until the overlay's chips exist.

- **Kinds:** `level`, `tier` (a level that opened a tier), `character`, `learned`, `mastery`, `feat`, `synergy`,
  `teaching`.
- **Toast** (`MomentHud`, native resolution with the UI kit): top centre, below any boss bars. A flat rounded dark
  panel (base #131315 at 94 %, radius 14) with a hairline in the accent, the icon in a tinted well (skill icon for
  levels, perk icon for perks, a Temper diamond for the character level), a title whose lead is in the accent and a
  small second line:
  - "Mining 9 · Apprentice Miner" / "1 point to spend · press K"
  - "Tier II open · Mining" / "3 points to spend · press K"
  - "Character level 7" / "1 attribute point to place · press K"
  - "Learned · Quarryman II" / the perk's effect at that rank
  - "Feat earned · Diamond Hands" / its effect (likewise "Mastery", "Synergy unlocked", "Teaching learned")
  - **Accent:** the skill's hue (§1b); champagne #E8DDBC for earned perks; Temper for the character level. Tiers,
    masteries and earned perks get a halo ring around the panel.
  - Slides down and fades in (0.22 s), stays 3.2 s (2 s when others wait), fades out (0.38 s).
- **Queue** (`character.moment.MomentQueue`, unit-tested): one toast at a time, in order. Rapid repeats **merge**: a
  skill levelling again while its toast is up shows the new level (a level that opens a tier upgrades it to the tier
  toast), ranks of one perk show the highest rank. More than 5 waiting drops the oldest level/bought ones first.
- **Screens:** while any screen is open the toast is hidden and the queue **waits** (it does not expire unseen). A perk
  bought from a screen gets the click-chime but no toast, because the screen already shows it. The VOID Hub hides the
  HUD while open, so its toasts wait for it to close; it can read `moments` from the state meanwhile (OVERLAY.md).
- **Sounds** (UI category, local, `MomentSounds`): level, a soft rising note-block chime triad over an amethyst
  shimmer; tier, brighter, up to the octave with a bell and the level-up sparkle; character level, the level-up sound;
  perk learned, a click then one high chime (mastery adds a bell); feat/synergy, the challenge-complete fanfare
  (synergy pitched up); teaching, a page turn then the fanfare. **Throttled** (`SoundThrottle`, unit-tested): level
  sounds share 1.5 s and the stronger wins (a skill level and the character level in the same tick chime once; a tier
  still rings over a level); earned share 2.5 s; perk clicks 90 ms.
- **Points nudge:** "◆ 3 points  K" (perk points in every released skill plus attribute points) just past the hotbar's
  right end, clear of the offhand slot and the hotbar attack indicator. It appears on a level-up (and once on joining
  with points unspent), stays 20 s, fades, and comes back on the next level-up; gone as soon as nothing is unspent.
- With an `OverlayBridge` listener attached the overlay draws these instead; the native toast and nudge stay hidden,
  and sounds still play.

## 7. Belt radial (R)

- **Hold R:** a radial of the belt pockets appears around the crosshair. The segments are flat and rounded; the hovered one is raised, shows the item, and gives its name below.
- **Release** over a segment to use it. **Release in the centre** to cancel. **Tap R** to reuse the last pocket.
- **Input:** mouse movement selects; keys 1–6 select while R is held.
- **What "use" does** is defined in BURDEN.md §7.

## 8. Keys (category "VOID Mettle", all rebindable)

| Key | Action |
|---|---|
| K | character screen (Hearth's journal is J) |
| R (hold) | belt radial |

## 9. First-time hints (once per player, remembered in the journal)

| Moment | Hint chip |
|---|---|
| Background chosen | "Press K to see your character" |
| First perk point | "1 perk point to spend · K" |
| First time Burdened | "Heavy load · drop something or wear a backpack" |
| First gear item picked up | "Wear it in the slots beside your character" |

## 10. Client options

These are reached from a small settings glyph in the K screen's corner:

- show my gear on my character
- XP chips on/off
- hints on/off
- progress toasts on/off, progress sounds on/off, points nudge on/off (§6b)

Until that glyph exists, the options live in `config/mettle-client.properties` (`toasts`, `sounds`, `pointsNudge`,
all `true` by default), read and written by `client.MettleClientOptions`; a host can call its setters. Turning toasts
off brings back the action-bar line for those moments.
