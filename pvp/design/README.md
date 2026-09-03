# VOID PVP — design reference

Extracted from Figma file `ks5kpynF3otxC5t1gNKfvg`, page `244:2` "★ PVP — direction".
11 frames, each **1300 × 820**. Screenshots: `screens/*.png` (1:1, 1300 × 820 PNG).
Tokens: `tokens.css` / `tokens.json`. Renderer constraints: `ultralight-notes.md`.

Two surfaces share one visual language:

| Surface | Frames | Root background | Panel geometry |
|---|---|---|---|
| **Launcher** (Electron window) | Play, Mods, Cosmetics, Servers, Friends | `--bg-shell` frame → recessed `--bg-base` canvas at `14, 62` sized `1272 × 744`, radius 18, inner shadow `--inset-canvas` | Panel `960 × 596` at `155, 19` **inside the canvas** |
| **In-game overlay** (Ultralight) | Mods, Mod settings, Loadouts, Party, HUD layout, Quick palette | Full-bleed game render, then a dim layer | Panel `960 × 600` at `170, 110` **in frame coords** |

All 11 frames use radius **22** on the outer frame and carry a 32.64 px tiled
noise PNG at `mix-blend-mode: overlay` over every surface (opacity per
`--noise-opacity-*`).

---

## 1. Launcher — Play · `244:3` · `screens/Launcher-Play.png`

**Purpose.** The default landing screen. Big identity moment: the hero states
which loadout is active, and one accent button launches the game.

**Layout grid**

| Region | Node | Box (x, y, w, h) | Notes |
|---|---|---|---|
| Chrome (TopNav) | `244:4` | `0, 0, 1300, 62` | flex row, gap 6, padding-x 16 |
| Canvas — recessed | `244:57` | `14, 62, 1272, 744` | radius 18, border `--rim`, `--inset-canvas` |
| ↳ backdrop art | `244:58` | `-1, -1, 1272, 744` | object-fit cover |
| ↳ scrim | `244:59` | `-1, -1, 1272, 744` | `--scrim-launcher` |
| ↳ eyebrow | `244:60` | `31, 27` (hug) | pill, radius 9 |
| ↳ "ACTIVE LOADOUT" | `244:63` | `35, 403` | mono 10.5, `--accent-ink`, tracking 1.47 |
| ↳ hero title | `244:64` | `29, 419` | display 104 / 104, tracking −4.16 |
| ↳ hero meta | `244:65` | `35, 531` | mono 12.5, `--text-secondary` |
| ↳ Dock | `244:66` | `209, 635` (hug) | radius 24, padding 12, gap 12 |

Dock children, left → right: identity (44 px avatar r13 + name/level), 1 × 36 px
divider, LoadoutPicker pill (h 52, r 15), VersionPicker pill (h 52, r 15),
LaunchButton (232 × 56, r 16), divider, FriendsOnline (3 × 32 px heads
overlapped 24 px inside an 80 px box + label), settings button 44 × 44 r 13.

**Text (verbatim)**

- Nav: `Play` `Mods` `Cosmetics` `Servers` `Friends`
- Search: `Ask VOID anything` + kbd `⌘K`
- Eyebrow: `VOID PVP   ·   1.8.9   ·   HYPIXEL-READY`
- `ACTIVE LOADOUT`
- `Sword PvP`
- `24 mods on   ·   142 fps avg   ·   12 ms to Hypixel`
- Dock identity: `Searge` / `Lvl 42`
- Loadout pill: `LOADOUT` / `Sword PvP`
- Version pill: `VERSION` / `1.8.9`
- CTA: `Launch` + kbd `⌘↵`
- Friends: `3 online`

**Interaction notes.** `⌘↵` launches (the kbd chip sits inside the button).
`⌘K` opens the launcher search. Both dock pills carry a chevron-down → they are
menus. The eyebrow dot is a live status LED.

---

## 2. Launcher — Mods · `244:110` · `screens/Launcher-Mods.png`

**Purpose.** Browse and toggle mods, and edit the selected mod inline.
Chrome + canvas + dock are identical to Play (dock at `209, 635`); a Panel is
layered over the hero.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Panel / Mods | `252:2` | `155, 19, 960, 596` (canvas coords), r 24, `--panel-bg`, border `--border-panel`, `--shadow-panel`, backdrop-blur 15 |
| ↳ Title `Mods` | `252:3` | `25, 21` — display 26 / 30, tracking −0.52 |
| ↳ SearchBar | `252:4` | `109, 19, 230, 34` — r 10, `--field-bg`, `--inset-field-panel` |
| ↳ FilterTabs | `252:10` | `359, 19` — flex gap 4 |
| ↳ Grid | `252:26` | `25, 71` — 3 cols × 4 rows of 200 × 96 tiles, gap 10 (total 620 × 414) |
| ↳ ModSettingsPanel "Keystrokes" | `252:189` | `655, 71, 278, 410` — r 16, padding 16, gap 12 |
| ↳ footer hint | `252:238` | `25, 565` |

**Mod tiles (reading order, 3 per row)**

| Tile | Category tag | Switch | Icon well |
|---|---|---|---|
| FPS display | `HUD` | on | accent tint |
| Keystrokes | `HUD` | on | accent tint — **selected**, 1.5 px `--accent` border |
| CPS counter | `HUD` | on | accent tint |
| Toggle sprint | `PVP` | on | accent tint |
| Crosshair | `VISUAL` | on | accent tint |
| Zoom | `UTILITY` | on | accent tint |
| Fullbright | `VISUAL` | **off** | `--tint-08` |
| Hitboxes | `PVP` | **off** | `--tint-08` |
| Armor status | `HUD` | on | accent tint |
| Potion effects | `HUD` | on | accent tint |
| Ping display | `HUD` | **off** | `--tint-08` |
| Coordinates | `HUD` | **off** | `--tint-08` |

**Text (verbatim)** — Panel: `Mods`; Search placeholder `Search`; tabs `All`
`HUD` `PvP` `Visual` `Utility` (All selected). Settings pane: `Keystrokes`,
`Scale` `1.0×`, `Opacity` `85%`, `Keybind` `R-Shift`, `Edit position`.
Preview keys: `W` `A` `S` `D` `LMB` `RMB` (W, D, LMB pressed = accent).
Footer: `Changes apply on next launch   ·   drag any tile onto the HUD editor to place it   ·   ⌘K search`

**Interaction notes.** Changes made here apply on **next launch** (launcher
context, unlike the in-game panel which is instant). Tiles are drag sources —
dragging a tile onto the HUD editor places that widget. `⌘K` searches.

---

## 3. Launcher — Cosmetics · `244:217` · `screens/Launcher-Cosmetics.png`

**Purpose.** Preview and equip capes / hats / wings / emotes / bundles against a
3D-ish character stage.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Panel / Cosmetics | `246:2` | `155, 19, 960, 596` |
| ↳ Title `Cosmetics` | `246:3` | `25, 21` |
| ↳ FilterTabs | `246:4` | `169, 19` |
| ↳ counter | `246:15` | `744, 29` |
| ↳ stage | `246:16` | `25, 71, 300, 452` — r 18, `--card-bg`; 320 px glow ellipse at `-11, 89`; skin 160 × 376 at `69, 29`; "equipped" pill at `73, 406` |
| ↳ cape grid | `246:22…59` | 3 cols × 2 rows of **186 × 216** at x = 349 / 547 / 745, y = 71 / 299 (gap 12) |
| ↳ footer | `246:60` | `349, 537` — flex gap 10 |

Each cape card: 186 × 140 preview strip on top (62 × 98 cape swatch at `61, 23`
with a coloured glow `0 10px 24px -4px <hue>/0.45`, plus a 70 × 8 hanger bar at
`57, 19`), title at `13, 151` (14 px semibold), state at `13, 175` (10 px mono).

**Cape set**

| Cape | Glow colour | State line | State colour |
|---|---|---|---|
| Void Trail | `rgba(115,89,242,0.45)` | `Equipped` | `--accent-ink` — card has 1.5 px `--accent` border |
| Ember | `rgba(250,140,51,0.45)` | `Owned` | `--ok-ink` |
| Frost | `rgba(191,242,255,0.45)` | `1,200 coins` | `--text-secondary` |
| Midnight | `rgba(64,77,128,0.45)` | `Owned` | `--ok-ink` |
| Aurora | `rgba(77,242,178,0.45)` | `900 coins` | `--text-secondary` — carries a `NEW` badge (accent fill, `--accent-fg` text) at `9, 9` |
| Solar | `rgba(255,217,77,0.45)` | `1,500 coins` | `--text-secondary` |

**Text (verbatim).** Tabs `Capes` `Hats` `Wings` `Emotes` `Bundles`;
`12 owned   ·   3 new this week`; stage pill `Equipped  ·  Void Trail`;
buttons `Equipped` (secondary, check icon) and `Preview in lobby` (accent, eye
icon); note `Cosmetics show to everyone on VOID`.

**Interaction notes.** The `Equipped` button is a disabled/current state, not an
action; `Preview in lobby` is the primary action. `NEW` marks items added this
week (matching the header count).

---

## 4. Launcher — Servers · `244:324` · `screens/Launcher-Servers.png`

**Purpose.** Favourite / recent / browse server list with a detail pane that
shows ping history and can auto-switch loadout on join.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Panel / Servers | `247:2` | `155, 19, 960, 596` |
| ↳ Title `Servers` | `247:3` | `25, 21` |
| ↳ SearchBar | `247:4` | `139, 19, 230, 34` |
| ↳ FilterTabs | `247:7` | `389, 19` |
| ↳ Add server button | `247:14` | `828, 19` |
| ↳ List | `247:17` | `25, 71, 580, —` — rows h 62, r 14, gap 4 |
| ↳ Pane / Hypixel | `247:74` | `625, 71, 308, 500` — r 16, padding 16, gap 12 |

Row anatomy (h 62, padding-x 12, gap 12): 40 px server icon r 11 → name (14
semibold) + address (10.5 mono muted) → player count → 6 px ping dot + ping
value → Join button (r 8, padding `6px 12px`).

**Server list**

| Server | Address | Online | Ping | Ping colour | Join |
|---|---|---|---|---|---|
| Hypixel | `mc.hypixel.net` | `24,118 online` | `42 ms` | `--ok-ink` | accent (**row selected**, 1.5 px `--accent` border) |
| Minemen Club NA | `na.minemen.club` | `1,204 online` | `68 ms` | `--ok-ink` | `--tint-08` |
| CubeCraft | `play.cubecraft.net` | `8,412 online` | `51 ms` | `--ok-ink` | `--tint-08` |
| Minemen Club EU | `eu.minemen.club` | `987 online` | `112 ms` | `--warn-ink` | `--tint-08` |
| PvP Land | `pvp.land` | `612 online` | `74 ms` | `--ok-ink` | `--tint-08` (icon is a `PL` monogram on `--surface-2`) |

Detail pane, top → bottom: 52 px icon r 14 + `Hypixel` (22 display) +
`mc.hypixel.net   ·   1.8 – 1.21`; three stat tiles (`42 ms`/`ping`,
`24,118`/`online`, `37`/`wins here`) r 10, `--stat-tile-bg`; caption
`PING  ·  LAST 12 H`; a 12-bar sparkline (10 px wide bars, gap 4, heights
18/20/16/22/18/**32**/25/18/16/18/17/18 — the 32 px spike is `--warn`, the last
bar is full-opacity accent, the rest are accent at 60 %); a row
`Auto-switch loadout` / `Bedwars when joining here` with a 40 × 22 switch **on**;
CTA `Join with Sword PvP`; ghost button `Favourited` with a star icon.

**Interaction notes.** Search placeholder is `Search or paste an address` — the
field doubles as a direct-connect input. `Auto-switch loadout` binds a loadout
to a server so joining swaps it.

---

## 5. Launcher — Friends · `244:431` · `screens/Launcher-Friends.png`

**Purpose.** Friends list split by presence, with a party pane that queues.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Panel / Friends | `248:2` | `155, 19, 960, 596` |
| ↳ SearchBar | `248:4` | `135, 19, 200, 34` |
| ↳ FilterTabs (with counts) | `248:7` | `355, 19` |
| ↳ Add friend (accent) | `248:17` | `827, 19` |
| ↳ List | `248:20` | `25, 71, 580, —` — group caption rows + friend rows h 56, r 14, gap 4 |
| ↳ Pane / Party | `248:72` | `625, 71, 308, 500` |

Friend row (h 56, padding-x 12, gap 12): 36 px avatar r 11 with an 11 px
presence dot at `27, 27` → name (14 semibold) + status (10.5 mono muted) →
action button r 8.

**List content**

- Caption `ONLINE` `·  3`
  - `marrow` — `Bedwars  ·  Hypixel  ·  2h` — **Join** (accent)
  - `pilot_ash` — `Sword duels  ·  Minemen  ·  40m` — **Join** (accent)
  - `nine` — `In lobby  ·  Hypixel` — **Invite** (`--tint-08`)
- Caption `OFFLINE` `·  5`
  - `doorframe` — `Last seen 4 hours ago` — **Message** (avatar at 50 % opacity, name in `--text-secondary`)
  - `kestrel` — `Last seen yesterday` — **Message**

Party pane: heading `Your party` + `2 / 4`; member rows (r 12,
`--party-row-bg`, 32 px avatar r 10) `Searge` / `Leader` (`--accent-ink`) and
`marrow` / `Ready` (`--ok-ink`), each with an 8 px status dot; ghost button
`Invite 2 more`; caption `QUEUE`; three chips `Bedwars 4v4` (selected: accent
tint + `--accent-border-strong`), `Duels`, `UHC`; CTA `Queue with party`; text
button `Leave party`.

**Text (verbatim).** Search `Find a friend`; tabs `Online` `3`, `All` `8`,
`Requests` `2` (the `2` is `--ok-ink`); `Add friend`.

**Interaction notes.** Tab counts are live. `Requests` count is tinted green to
read as "needs your attention". Online friends who are in a match offer **Join**;
friends in a lobby offer **Invite**.

---

## 6. Overlay — Mods · `244:538` · `screens/Overlay-Mods.png`

**Purpose.** The same mod browser, opened over the running game with R-Shift.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Canvas (game render) | `244:592` | `0, 0, 1300, 820` — rasterised game scene, r 22 |
| ↳ dim | `244:593` | `0, 0, 1300, 820` — `--dim-overlay` |
| ↳ HUD / FPS | `244:594` | `24, 24` (hug) — r 8, `--hud-chip-bg`, opacity 0.70 |
| ↳ Mod menu (Panel) | `244:597` | `170, 110, 960, 600` — r 24, `--panel-bg`, `--shadow-panel` |
| ↳ Title `Mods` | `244:598` | `25, 21` |
| ↳ SearchBar | `244:599` | `109, 19, 230, 34` |
| ↳ FilterTabs | `244:605` | `359, 19` |
| ↳ close (X) | `244:616` | `901, 19, 32, 32` — r 9 |
| ↳ Grid | `244:621` | `25, 71` — same 3 × 4 × (200 × 96) grid as frame 2 |
| ↳ ModSettingsPanel | `244:784` | `655, 71, 278, 414` |
| ↳ footer hint | `244:833` | `25, 565` |

Tile set, tags and on/off states are **identical** to Launcher — Mods, including
Keystrokes being the selected tile.

**Text (verbatim).** HUD chip `142` `fps`. Panel identical to frame 2 except the
footer: `R-Shift closes   ·   drag any tile onto the game to place it   ·   ⌘K search`

**Interaction notes.** R-Shift is the open/close key. There is an explicit close
X here (the launcher panel has none). Drag target is the live game, not an
editor canvas. Changes here are instant (see frame 7's footer).

---

## 7. Overlay — Mod settings · `244:834` · `screens/Overlay-Mod-settings.png`

**Purpose.** Full settings for one mod (Keystrokes), with a live preview strip
and two grouped setting columns.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Canvas + dim + HUD/FPS | `244:888…892` | as frame 6 |
| Panel | `244:893` | `170, 110, 960, 600` |
| ↳ Back button | `249:2` | `25, 21` — `←` + `Mods` |
| ↳ Title `Keystrokes` | `249:5` | `119, 19` |
| ↳ subtitle | `249:6` | `121, 51` |
| ↳ controls cluster | `249:7` | `656, 19` — `Enabled` + 44 × 24 switch, `Keybind` `R-Shift` chip group, close 32 × 32 |
| ↳ Preview | `249:18` | `25, 81, 908, 168` — r 16, game still + `--dim-preview`, border `--tint-10` |
| ↳ Group / Appearance | `249:38` | `25, 265, 448, 290` — r 16, `--card-bg`, rows h 48, seams 1 px |
| ↳ Group / Behaviour | `249:90` | `485, 265, 448, 290` |
| ↳ footer hint | `249:133` | `25, 563` |
| ↳ actions | `249:134` | `730, 547` — `Edit position` (accent) + `Reset` |

Live preview contains a full-size KeystrokesWidget centred at `394, 28`:
36 px square keys, `gap 5`, r 8, in three rows — `W` / `A S D` / `LMB RMB`
(LMB and RMB are 56.5 × 36). Pressed keys (`W`, `D`, `LMB`) are `--accent` with a
`--tint-35` border and `--shadow-key-on`; unpressed are `rgba(10,11,12,0.72)`
with a `--tint-14` border. Labels 12 px Outfit Bold.

**Group / Appearance rows** (label left, control right, value right-aligned in a
48 px column):

| Row | Control | Value |
|---|---|---|
| `Scale` | 180 px slider, fill 76 px, thumb 10 × 20 at x 71 | `1.0×` |
| `Opacity` | 180 px slider, fill 153 px, thumb at x 148 | `85%` |
| `Corner radius` | 180 px slider, fill 90 px, thumb at x 85 | `8 px` |
| `Key colour` — sub `Background of an unpressed key` | 5 swatches 22 × 22 r 7 | shell (**selected**, 2 px `--text-primary` ring), raised, pill, sky, teal |
| `Pressed colour` — sub `Follows the loadout accent by default` | 5 swatches | accent (**selected**), sky, warn, fear, teal |

**Group / Behaviour rows**

| Row | Sub | Control |
|---|---|---|
| `Show mouse buttons` | `LMB and RMB under the arrows` | 44 × 24 switch **on** |
| `Show CPS` | `Clicks per second for both buttons` | switch **on** |
| `Show space bar` | `A wide key under the block` | switch **off** |
| `Show sneak key` | — | switch **off** |
| `Position` | — | 4 chips: `Top left`, `Top right`, `Bottom left` (**selected**), `Bottom right` |

**Text (verbatim).** `← Mods`; `Keystrokes`; `HUD   ·   on in 3 loadouts`;
`Enabled`; `Keybind` `R-Shift`; `LIVE PREVIEW`; `Bottom left   ·   1.0×   ·   85%`;
`APPEARANCE`; `BEHAVIOUR`; `Edit position`; `Reset`;
footer `R-Shift closes   ·   changes apply instantly`.

**Interaction notes.** In-game changes are **instant** (contrast with the
launcher's "next launch"). The preview header mirrors the current position /
scale / opacity so the reader can verify the sliders. `Edit position` jumps to
the HUD editor (frame 10).

---

## 8. Overlay — Loadouts · `244:1130` · `screens/Overlay-Loadouts.png`

**Purpose.** Compare and switch between saved loadouts. Explains up front what
a loadout is.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Panel | `244:1189` | `170, 110, 960, 600` |
| ↳ Title `Loadouts` | `250:2` | `25, 21` |
| ↳ definition line | `250:3` | `25, 53` |
| ↳ controls | `250:4` | `778, 19` — `New loadout` + close |
| ↳ LoadoutCard ×3 | `250:10 / 53 / 90` | `25, 89` · `333, 89` · `641, 89`, each **292 × 428**, r 18, padding 18, gap 12 |
| ↳ footer hint | `250:127` | `25, 563` |

LoadoutCard anatomy: 44 px icon block r 13 (accent fill when active, `--tint-08`
otherwise) → title (22 display) + meta (10 mono) → optional `ACTIVE` badge →
caption `INCLUDES` → wrapped includes-chips (r 8, `--hud-chip-bg`, 6 px dot) →
`+ N more` → flex spacer → two stat columns → full-width button.

| Card | Icon | Meta | Includes chips | More | Stats | Button |
|---|---|---|---|---|---|---|
| **Sword PvP** (active: 1.5 px accent border, `--shadow-card-active`, `ACTIVE` badge) | sword, accent | `24 mods on   ·   Hypixel  ·  1.8.9` | Keystrokes, CPS, Toggle sprint, Crosshair, Zoom, Armor status (dots + labels in `--text-primary`) | `+ 18 more` | `142` `fps avg`, `4h 20m` `played` | secondary `Active` w/ check |
| **Bedwars** | box | `19 mods on   ·   Hypixel  ·  1.8.9` | Keystrokes, Armor status, Potion effects, Fullbright, Ping (labels `--text-secondary`) | `+ 14 more` | `—` `fps avg`, `2h 05m` `played` | accent `Switch to Bedwars` |
| **UHC** | heart | `16 mods on   ·   Minemen  ·  1.8.9` | Armor status, Potion effects, Coordinates, Hitboxes, Zoom | `+ 11 more` | `—` `fps avg`, `48m` `played` | accent `Switch to UHC` |

**Text (verbatim).** `Loadouts`;
`A loadout is which mods are on, their settings and HUD layout.`;
`New loadout`; `INCLUDES`; `ACTIVE`;
footer `Switching applies instantly   ·   settings and HUD layout are per loadout   ·   L cycles loadouts in game`

**Interaction notes.** `L` cycles loadouts without opening this panel. Switching
is instant. Settings and HUD layout are scoped per loadout — the copy says so
explicitly, which is the model UI agents must implement.

---

## 9. Overlay — Party · `244:1426` · `screens/Overlay-Party.png`

**Purpose.** In-game party management and queueing.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Panel | `244:1485` | `170, 110, 960, 600` |
| ↳ Title `Party` | `251:2` | `25, 21` |
| ↳ FilterTabs | `251:3` | `119, 19` |
| ↳ close | `251:12` | `901, 19, 32, 32` |
| ↳ left column | `251:14` | `25, 75, 580, —` — flex col, gap 6 |
| ↳ Pane / Queue | `251:51` | `625, 75, 308, 500` — r 16, padding 16, gap 12 |
| ↳ footer hint | `251:81` | `25, 563` |

Left column: caption `IN YOUR PARTY` `·  2 of 4`; two **member rows** h 64, r 14
(44 px avatar r 13, name 15 semibold, meta 10.5 mono, right-hand badge);
caption `INVITE` `·  3 online`; two **invite rows** h 54, r 12 (34 px avatar
r 10, name 13.5, meta 10, `Invite` button with users icon).

| Row | Name | Meta | Badge / action |
|---|---|---|---|
| member | `Searge` | `Sword PvP  ·  1.8.9` | `LEADER` — accent tint fill, `--accent-ink` |
| member | `marrow` | `Sword PvP  ·  1.8.9` | `READY` — `--ok-tint` fill, `--ok-ink` |
| invite | `pilot_ash` | `Sword duels  ·  Minemen` | `Invite` |
| invite | `nine` | `In lobby  ·  Hypixel` | `Invite` |

Queue pane: heading `Queue`; caption `GAME`; three rows r 10 —
`Bedwars 4v4` / `Hypixel  ·  avg 3:40 queue` (**selected**: `--accent-tint-weak`
+ `--accent-border-strong`), `Sword duels` / `Minemen  ·  instant`,
`UHC` / `Minemen  ·  next round 12:00` (unselected: `--tint-04`); caption
`LOADOUT`; a LoadoutPicker row (24 px accent sword icon r 7 + `Sword PvP` +
chevron); spacer; CTA `Queue with party`; bar `Leave party`.

**Text (verbatim).** Tabs `Party` (selected), `Friends` `3`, `Requests` `2`
(green count). Footer: `Party chat  T   ·   push to talk  V   ·   R-Shift closes`

**Interaction notes.** `T` opens party chat, `V` is push-to-talk, `R-Shift`
closes the overlay. The queue pane picks the loadout the whole party queues with.

---

## 10. Overlay — HUD layout · `244:1722` · `screens/Overlay-HUD-layout.png`

**Purpose.** Direct-manipulation editor for the in-game HUD. No panel — the game
is only lightly dimmed so the real widget positions are legible.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Canvas | `244:1776` | `0, 0, 1300, 820` — r 22, `--bg-base` + backdrop art + `--dim-hud-editor` scrim |
| HUD / FPS | `244:1779` | `23, 23` (hug) — r 10, `--hud-chip-bg-strong`, border `--border-dock` |
| HUD / Ping | `244:1784` | `23, 65` |
| HUD / Coordinates | `244:1789` | `23, 103` |
| HUD / Potion effects | `244:1791` | `1125, 23, 150, —` |
| HUD / Armor status | `244:1800` | `1105, 299, 170, —` |
| HUD / Crosshair | `244:1833` | `626, 362, 18, 18` |
| HUD / Keystrokes | `244:1836` | `31, 581` — 40 px keys, gap 5, r 9 |
| HUD / CPS | `244:1852` | `175, 678` |
| selection box | `244:1858` | `23, 573, 146, 146` — r 12, 1.5 px **dashed** `--accent`, fill `--accent-tint-faint` |
| handles ×4 | `244:1859…1862` | 8 × 8 at `19,569` `165,569` `19,715` `165,715` — `--text-primary` fill, 1.5 px accent border, r 2 |
| selection label | `244:1863` | `23, 543` — accent fill, r 6 |
| Toolbar | `244:1866` | `436, 15` (hug) — r 14, `--dock-bg`, `--shadow-toolbar`, padding `6px 8px`, gap 6 |
| hint | `244:1898` | `472, 773` — raised chip, r 8 |

**HUD widget content (verbatim)**

- FPS: `142` `fps` `·  1% low 96`
- Ping: 7 px dot + `42 ms` `Hypixel`
- Coordinates: `X 118   Y 64   Z -212   ·   NE`
- Potion effects (150 px): `Speed II` `1:24` with a `#7aebb5` 10 px swatch;
  `Strength` `0:48` with a `#d9a93a` swatch
- Armor status (170 px), 4 rows of 16 px icon + label/value + 4 px bar:
  `Helmet` `231 / 363` (bar 64 %, `--ok`), `Chestplate` `412 / 528` (78 %, `--ok`),
  `Leggings` `188 / 495` (38 %, `--warn`), `Boots` `341 / 429` (79 %, `--ok`)
- Keystrokes: `W` / `A` `S` `D` / `LMB` `RMB` — W, D, LMB pressed (accent,
  `--shadow-cta`); unpressed keys `--key-bg` + `--inset-key`. Rows 40 px tall,
  LMB/RMB 62.5 px wide.
- CPS: `12` (accent-ink) `|` `9` `CPS`
- Crosshair: two 2 px `--text-primary` bars forming a plus in an 18 px box

**Toolbar** (left → right): `HUD layout` (mode label, move icon), 1 px × 18
divider, `Snap` (**active**: accent tint + `--accent-border` + check icon),
`Grid` (layers icon), `Reset` (rotate-ccw icon), `Done` (accent fill,
`--accent-fg` text). Each tool is h 32, padding-x 12, r 9.

**Selection label:** `Keystrokes` + `x 32  ·  y 580  ·  1.0×`
**Hint:** `Drag to move   ·   ⌥ drag to scale   ·   Esc to exit`

**Interaction notes.** Drag moves a widget; `⌥`+drag scales it; `Esc` exits the
editor. `Snap` is on by default. The selection readout is live (x / y / scale)
and the 4 corner handles are the scale grips.

---

## 11. Overlay — Quick palette · `244:1900` · `screens/Overlay-Quick-palette.png`

**Purpose.** ⌘K-style command palette over the game: type, get ranked actions,
run with `↵`.

**Layout grid**

| Region | Node | Box |
|---|---|---|
| Game render | `244:1900` | full-bleed 1300 × 820, r 22 |
| vignette | `244:1901` | `0, 0, 1300, 820` — `--scrim-vignette` |
| HUD / FPS | `244:1902` | `24, 24` — r 8, `--hud-chip-bg` @0.60 |
| HUD / Crosshair | `244:1905` | `641, 401, 18, 18` — `rgba(255,255,255,0.9)` |
| HUD / Hotbar | `244:1908` | `450, 750` — r 8, padding 4, gap 4, 9 slots of 40 × 40 r 6 |
| dim | `244:1922` | `0, 0, 1300, 820` — `--dim-palette` + backdrop-blur 3 |
| Palette | `244:1923` | `330, 190, 640, —` — r 18, `--palette-bg`, `--shadow-panel`, backdrop-blur 15 |

Hotbar slot fills (first 4 filled, 5 empty): `rgba(158,158,168,0.9)`,
`rgba(140,92,51,0.9)`, `rgba(217,51,51,0.9)`, `rgba(77,153,229,0.9)` — 20 × 20 r 3
centred at `9, 9`.

Palette stack, top → bottom:

1. **input** h 58, padding `0 14px 0 18px`, gap 12 — 18 px search icon, query
   text `fullb` at 17 px, a 2 × 22 accent caret, and an `esc` kbd chip.
2. 1 px seam.
3. Caption `ACTIONS` (padding `12px 0 6px 18px`).
4. Results list, padding-x 6, gap 2, rows **h 48**, r 10, padding-x 12, gap 12:

   | Result | Sub | Trailing kbd | State |
   |---|---|---|---|
   | `Toggle Fullbright` | `Visual  ·  currently off  →  on` (in `--accent-ink`) | `↵` | **selected** — `--accent-tint-weak`, `--accent-border`, `--inset-row-selected`, icon well `--accent-tint-strong` |
   | `Fullbright settings` | `Open in the mod menu` | `⌘` `↵` | default |
   | `Turn on in Bedwars loadout` | `Fullbright is off in that loadout` | — | default |

5. Caption `ALSO`, second list:

   | Result | Sub | Trailing kbd |
   |---|---|---|
   | `Fullscreen` | `Window  ·  F11` | `F11` |
   | `Brightness  ·  Gamma 100%` | `Video settings` | — |

6. 6 px spacer, 1 px seam, **footer** (padding `10px 14px 10px 18px`, gap 14):
   `↑↓` `move` · `↵` `run` · `⌘↵` `settings` · `esc` `close` · spacer ·
   sword icon + `Sword PvP`.

**Interaction notes.** `↑↓` moves the selection, `↵` runs the highlighted
action, `⌘↵` opens that action's settings instead of running it, `esc` closes.
The selected result previews the state change inline (`currently off  →  on`).
The footer right-hand side always shows the active loadout.

---

# Component inventory

Sizes are the authored pixel values. "Tokens" name entries from `tokens.css`.

## Shell / chrome

### TopNav (`Chrome`) — `1300 × 62`
Flex row, `gap 6`, `padding-x 16`, transparent over `--bg-shell`.
Children: Mark (30 × 30, r 9, `--surface-2`, `--inset-raised-soft`, 12 px accent
square outline inside), 4 px gap spacer, 5 × NavTab, flex spacer, SearchBar,
6 px gap, settings icon button, Avatar.

**NavTab** — hug × 34 (`padding 9px 16px 9px 13px`), `gap 9`, r 10, 14 px icon +
13 px label, `--leading-nav`, `--tracking-nav`.
- *active*: `--surface-3` fill, `--hairline` border, `--shadow-raised`, `--inset-raised-soft`, label `--text-primary` @500.
- *default*: transparent, label `--text-secondary` @400, icon strokes `--text-secondary`.

**Btn / Settings** — 34 × 34, r 9, `--surface-1`, `--hairline` border, 14 px icon.
**Avatar** — 32 × 32 image.

### SearchBar "Ask VOID anything ⌘K" — `300 × 34`
r 10, `--surface-1`, `--hairline` border, `--inset-field`,
`padding 8px 8px 8px 13px`, `gap 10`.
Children: 8 × 8 accent dot (r 2) → placeholder 13 px `--text-muted` → kbd chip
(`--surface-2`, r 4, `padding 2px 5px`, 9.5 px mono, `--tracking-caps-kbd`,
uppercase, `--leading-kbd`) reading `⌘K`.

*Panel variant* (`Search`) — 230 × 34 (or 200 in Friends), r 10, `--field-bg`,
`rgba(255,255,255,0.07)` border, `--inset-field-panel`, `padding-left 12`,
`gap 8`, 13 px search icon + 12.5 px `--text-muted` placeholder. Placeholders:
`Search`, `Search or paste an address`, `Find a friend`.

## Launcher dock

### Dock — hug × hug at `209, 635`
r 24, `--dock-bg`, `--border-dock`, `--shadow-dock`, `--inset-dock`,
backdrop-blur 12, `padding 12`, `gap 12`.

**PlayerChip (`identity`)** — `padding-left 4`, `gap 10`: 44 × 44 avatar r 13 +
column (`Searge` 14 px semibold `--tracking-name`; `Lvl 42` 10.5 px mono muted).

**Divider** — 1 × 36, `--divider`.

**LoadoutPicker (`pill / loadout`)** — hug × 52, r 15, `--raised-bg`,
`--border-raised`, `--shadow-raised`, `--inset-raised`,
`padding 0 14px 0 16px`, `gap 8`.
Children: 14 px sword icon → column (`LOADOUT` 8.5 mono `--tracking-caps-xs`
muted; `Sword PvP` 13.5 medium `--text-primary`) → 14 px chevron-down.
*States*: default as above; hover → lighten fill toward `--surface-3`; open →
accent border. In-game variant (Party pane) is a full-width row, r 10,
`--tint-06` fill, `--tint-10` border, 24 px accent icon well r 7, 12.5 px label.

**VersionPicker (`pill / version`)** — identical geometry to LoadoutPicker minus
the leading icon; `VERSION` / `1.8.9` (both DM Mono).

**LaunchButton (`CTA / Launch`)** — `232 × 56`, r 16, `--accent`, `--shadow-cta`,
`--inset-accent`, `gap 12`, centred.
Children: 16 × 18 play glyph (rotated 90°) → `Launch` 19 px bold `--accent-fg`
`--tracking-cta` → kbd chip `⌘↵` (`rgba(0,0,0,0.2)`, r 6, `padding 3px 7px`,
11 px mono `rgba(10,11,12,0.85)`, `--inset-kbd`).
*States*: rest as above; hover → raise glow to `rgba(159,139,255,0.55)`;
active → drop `--inset-accent` top highlight, translateY 1 px; disabled →
`--surface-2` fill, `--text-muted` label, no glow.

**FriendsOnline (`friends`)** — `gap 8`: an 80 × 32 `heads` box holding three
32 × 32 avatars r 10 at x = 0 / 24 / 48, each with a 2 px `#0a0b0c` ring;
then `3 online` 12.5 px medium `--text-secondary`.

**Button / settings** — 44 × 44, r 13, `--raised-bg`, `--border-raised`,
`--shadow-raised`, `--inset-raised`, 16 px gear icon at `13, 13`.

## Panels

### Panel — `960 × 596` (launcher) / `960 × 600` (overlay)
r 24, `--panel-bg`, 1 px `--border-panel`, `--shadow-panel`, backdrop-blur 15.
Header band ≈ 52 px: title at `25, 21` (display 26 / 30, `--tracking-title`),
optional SearchBar / FilterTabs / right-hand controls all at `y 19`.
Footer hint at `25, 563–565` (10.5 px mono `--text-muted`).
Overlay panels add a **close** button: 32 × 32, r 9, `--raised-bg`,
`--border-raised`, `--shadow-raised`, `--inset-raised`, 14 px X at `8, 8`,
positioned `901, 19` (or inside the right-hand control cluster).

### FilterTabs — flex row, `gap 4`
**Tab** — hug × 33 (`padding 8px 12px`), r 9, label 12.5 px.
- *selected*: `--accent-tint` fill, `--accent-border` border, `--inset-accent-tint`, label `--accent-ink` @500.
- *default*: transparent, label `--text-secondary` @400.
- *with count*: `gap 6`, count in 10 px mono — `--accent-ink` when selected, `--text-muted` when not, `--ok-ink` for `Requests`.

Tab sets: `All/HUD/PvP/Visual/Utility`, `Capes/Hats/Wings/Emotes/Bundles`,
`Favourites/Recent/Browse`, `Online/All/Requests`, `Party/Friends/Requests`.

### Button variants

| Variant | Geometry | Fill | Border | Text |
|---|---|---|---|---|
| **Accent CTA (full width)** | `py 11–12`, r 11–12 | `--accent` | — | `--accent-fg` 13–13.5 bold |
| **Accent inline** | `padding 8–10px 12–16px`, r 9–11 | `--accent` | — | `--accent-fg` 12.5–13 bold |
| **Raised** | `padding 8–11px 12–16px`, r 9–11 | `--raised-bg` | `--border-raised` | `--text-primary` 12.5–13 medium |
| **Ghost** | `py 9`, r 10 | `--tint-07` | `--tint-10` | `--text-secondary` 12.5 medium |
| **Chip / Join** | `padding 6px 12px`, r 8 | `--accent` or `--tint-08` | — | `--accent-fg` or `--text-primary` 12 bold/medium |
| **Text** | — | — | — | `--text-muted` 12 medium (`Leave party`) |

Accent buttons always carry `--shadow-cta` + `--inset-accent`; raised buttons
`--shadow-raised` + `--inset-raised`.

## Mods

### ModTile (`tile / <name>`) — `200 × 96`
r 14, `--card-bg`, `--shadow-tile`, `--inset-card`, `padding-x 12`, `gap 10`,
items centred.
Children: **icon well** 34 × 34 r 10 (fill `--accent-tint-icon` when the mod is
on, `--tint-08` when off) holding a 16 px lucide-style icon at `9, 9`; then a
column (`gap 6`) with the mod name (13 px medium `--text-primary`) and a row
(`gap 8`) of Switch + **category tag**.
**Category tag** — 8.5 px DM Mono medium, `--text-muted`, `--tracking-tag`,
uppercase; one of `HUD` `PVP` `VISUAL` `UTILITY`.
*States*: default border `rgba(255,255,255,0.07)`; **selected** border
`1.5px --accent` (no fill change); on/off is carried by the Switch and the icon
well tint. Tiles are drag sources.

### Switch
Three sizes, all pill-shaped:

| Size | Track | Knob | Knob x | Used on |
|---|---|---|---|---|
| S | 36 × 20, r 10 | 14 px | 19 (on) / 3 (off) | ModTile |
| M | 40 × 22, r 11 | 16 px | 21 / 3 | ModSettingsPanel header, Servers auto-switch |
| L | 44 × 24, r 12 | 18 px | 23 / 3 | Mod-settings Behaviour rows |

- *on*: `--accent` track, `--shadow-switch-on`, `--inset-switch`, white knob.
- *off*: `--tint-12`-class neutral track (exported as a flat SVG in the design), knob left, no glow.

### ModSettingsPanel (`Pane / Keystrokes`) — `278 × 410–414`
r 16, `--card-bg`, `--shadow-tile`, `padding 16`, `gap 12`, flex column.

1. **Header row** — `Keystrokes` 16 px semibold `--tracking-pane` + M Switch.
2. **KeystrokesPreview** — 200 × 128, r 12, `--card-bg`, `rgba(255,255,255,0.07)`
   border, `--shadow-tile`. Absolutely-placed 28 × 28 keycaps r 6:
   `W` at `108, 13`; `A` `76,45`, `S` `108,45`, `D` `140,45`; `LMB` 44 × 28 at
   `76, 77`, `RMB` 44 × 28 at `124, 77`. Pressed = `--accent` fill +
   `--tint-30` border + `--accent-fg` label; unpressed = `--kbd-bg-strong` +
   `--tint-12` border + `--text-primary` label. Labels 10.5 px Outfit Bold.
3. **Slider ×2** (`slider / Scale`, `slider / Opacity`) — `gap 6`:
   a label row (label 12 px `--text-secondary`, value 11.5 px mono
   `--text-primary`, `justify-between`) over a **track** 200 × 14 containing a
   5 px `--tint-12` rail (r 3, top 4), a 5 px `--accent` fill, and a
   10 × 16 `--text-primary` thumb (r 4, top −1).
4. **Keybind row** — `Keybind` label + **KeybindChip**.
5. Spacer.
6. **EditPositionButton** — full-width, `py 10`, r 10, `--accent`,
   `--shadow-cta`, `--inset-accent`, 13 px move icon + `Edit position`
   12.5 px bold `--accent-fg`.

**KeybindChip** — hug, r 5, `--kbd-bg`, `--tint-12` border,
`padding 3px 7px`, 10 px DM Mono `--text-secondary`. Value shown: `R-Shift`.

### Settings Group (mod-settings frame) — `448 × 290`
r 16, `--card-bg`, `rgba(255,255,255,0.07)` border, `--shadow-tile`,
`--inset-card`, flex column.
- **cap** row — `padding 14px 16px 8px`, 9.5 px mono `--text-muted`,
  `--tracking-caps`, uppercase (`APPEARANCE`, `BEHAVIOUR`).
- **row** — h 48, `padding-x 16`, `gap 12`: a flexible label column (title 13 px
  medium `--text-primary`, optional sub 11 px `--text-muted`) then the control.
- **seam** — 1 px full-width `--seam` between rows.
- **wide slider** — 180 × 16: 6 px `--tint-10` rail r 3 (top 5), 6 px `--accent`
  fill, 10 × 20 `--text-primary` thumb r 5 (top −2) with `--shadow-thumb`;
  value right-aligned in a 48 px column, 12 px mono.
- **swatch** — 22 × 22, r 7; unselected 1 px `--tint-14` border, selected 2 px
  `--text-primary` border.
- **position chip** — `padding 5px 10px`, r 8; default `--raised-bg` +
  `--border-raised` + `--shadow-raised` + `--inset-raised`, label 11.5 px
  `--text-secondary`; selected `--accent-tint` + `--accent-border` +
  `--inset-accent-tint`, label `--accent-ink` @500.

## Lists

### ServerRow — full width (580) × 62
r 14, `--card-bg`, `--shadow-tile`, `--inset-card`, `padding-x 12`, `gap 12`.
40 px icon r 11 → name/address column (`gap 2`) → player count (10.5 mono muted)
→ ping group (`gap 5`: 6 px dot + 11 px mono value, `--ok-ink` under ~100 ms,
`--warn-ink` above) → Join chip.
*States*: default border `rgba(255,255,255,0.07)`; **selected** 1.5 px `--accent`
border and the Join chip becomes accent.

### FriendRow — 580 × 56
Same shell as ServerRow at r 14. 36 px avatar r 11 with an 11 px presence dot at
`27, 27` → name (14 semibold `--tracking-name-tight`) + status (10.5 mono muted)
→ action chip.
*Offline state*: avatar `opacity 0.5`, name in `--text-secondary`, action is
`Message` on `--tint-08`.

### PartyMemberRow — 580 × 64 (overlay) / full-width r 12 (launcher pane)
Overlay: r 14, `--card-bg`, 44 px avatar r 13, name 15 px semibold
`--tracking-member`, meta 10.5 mono, trailing **status badge** —
`padding 4px 8px`, r 6, 9.5 px mono `--tracking-badge`:
`LEADER` (`--accent-tint` / `--accent-ink`) or `READY` (`--ok-tint` / `--ok-ink`).
Launcher pane: r 12, `--party-row-bg`, 32 px avatar r 10, 13 px name, 9.5 px
mono role line, trailing 8 px status dot.

### InviteRow — 580 × 54
r 12, `--card-bg`, 34 px avatar r 10, name 13.5 medium, meta 10 mono, trailing
`Invite` chip (`--tint-08`, `--tint-10` border, r 8, 12 px icon + label).

### GroupCaption
`padding 8px 0 4px 12px`, `gap 8`: label 9.5 px DM Mono medium `--tracking-caps`
uppercase `--text-muted` + count 9.5 px DM Mono regular. Seen as
`ONLINE · 3`, `OFFLINE · 5`, `IN YOUR PARTY · 2 of 4`, `INVITE · 3 online`.

## Cards

### CosmeticCard (`cape / <name>`) — `186 × 216`
r 16, `--card-bg`, `--shadow-tile`, `--inset-card`. 186 × 140 preview strip on
top (`--card-bg`, `rgba(255,255,255,0.07)` border) holding a 62 × 98 cape swatch
at `61, 23` with a coloured drop glow, and a 70 × 8 `--tint-20` hanger bar at
`57, 19`. Title 14 px semibold at `13, 151`; state 10 px mono at `13, 175`
(`Equipped` → `--accent-ink`, `Owned` → `--ok-ink`, price → `--text-secondary`).
Optional `NEW` badge — accent fill, r 5, `padding 2px 6px`, 8.5 px mono
`--accent-fg`, `--tracking-tag` — at `9, 9`.
*Selected*: 1.5 px `--accent` border.

### LoadoutCard — `292 × 428`
r 18, `--card-bg`, `padding 18`, `gap 12`, flex column.
Header row (`gap 12`): 44 × 44 icon block r 13 (`--accent` fill when active,
`--tint-08` otherwise, 22 px glyph at `11, 11`) → title 22 px display
`--leading-subtitle` `--tracking-subtitle` + meta 10 px mono → optional
`ACTIVE` badge (`--accent-tint`, r 6, `padding 3px 7px`, 9 px mono
`--accent-ink`, `--tracking-badge-lg`).
Caption `INCLUDES` (9 px mono `--tracking-caps-sm`).
**Includes chips** — wrapped rows, `gap 6`: `padding 5px 9px`, r 8,
`--hud-chip-bg`, `--tint-10` border, 6 px dot + 11.5 px label (chips read
`--text-primary` on the active card, `--text-secondary` elsewhere).
`+ N more` 10 px mono muted. Flex spacer. Two equal stat columns
(value 13 px mono `--text-primary`, unit 10 px `--text-muted`).
Full-width button: `Active` (raised, check icon, `--text-secondary` label) on the
current loadout, `Switch to <name>` (accent) on the others.
*Active card*: 1.5 px `--accent` border + `--shadow-card-active`.

### Pane — `308 × 500` (or `278 × 410` for the mods variant)
r 16, `--card-bg`, `rgba(255,255,255,0.07)` border, `--shadow-tile`,
`--inset-card`, `padding 16`, `gap 12`. Heading 16 px semibold
`--tracking-pane`. Section captions 9 px mono `--tracking-caps-sm`.
**StatTile** — `padding 9px 10px`, r 10, `--stat-tile-bg`: value 13 px mono over
unit 10 px `--text-muted`.
**Sparkline** — 10 px bars, `gap 4`, r 2, heights 16–32 px, `--accent` at
`opacity 0.6` with the current bar at full opacity and outliers in `--warn`.

## HUD widgets (in-game)

Common shell: `padding 6px 10px` (compact, r 8, `--hud-chip-bg`) or
`padding 8px 10px` (editor, r 10, `--hud-chip-bg-strong`, `--border-dock`), plus
`--inset-hud`. Compact chips render at `opacity 0.7` when the overlay panel is up.

| Widget | Node | Size | Content |
|---|---|---|---|
| **FpsChip** | `244:1779` | hug | `142` 13–15 px mono `--text-primary` + `fps` 10–10.5 px `--text-muted` (+ `·  1% low 96`) |
| **PingChip** | `244:1784` | hug | 7 px status dot + `42 ms` 13 px mono + `Hypixel` 10.5 px muted |
| **CoordsChip** | `244:1789` | hug | `X 118   Y 64   Z -212   ·   NE` 12 px mono `--text-primary` |
| **PotionList** | `244:1791` | 150 wide | rows `gap 4`, `gap 8` inside: 10 px colour swatch r 3 + name 12.5 px medium + timer 12 px mono |
| **ArmorList** | `244:1800` | 170 wide | rows `gap 4`: 16 px item icon r 4 (`--surface-2`) + a column of (label 11.5 `--text-secondary` / value 10.5 mono muted, `justify-between`) over a 4 px bar r 2 on `--tint-10` with `--ok` or `--warn` fill |
| **KeystrokesWidget** | `244:1836` | hug | 40 px keys, `gap 5`, r 9; LMB/RMB 62.5 × 40; pressed = `--accent` + `--tint-35` border + `--shadow-cta` + `--inset-accent`; unpressed = `--key-bg` + `--tint-10` border + `--shadow-key` + `--inset-key`; labels 13 px Outfit Bold |
| **CpsChip** | `244:1852` | hug | `12` 13 px mono `--accent-ink` · `|` 12 px muted · `9` 13 px mono `--text-primary` · `CPS` 10 px muted `--tracking-cps` |
| **Crosshair** | `244:1833` | 18 × 18 | two 2 px bars, `--text-primary` (or `rgba(255,255,255,0.9)` over live game) |
| **Hotbar** | `244:1908` | hug | r 8, `padding 4`, `gap 4`, 9 slots 40 × 40 r 6 (`--key-bg`, `--tint-10` border, `--shadow-key`, `--inset-key`), filled slots hold a 20 × 20 r 3 colour block at `9, 9` |

## HUD editor

**Toolbar** — hug × 46 at `436, 15`; r 14, `--dock-bg`, `--border-dock`,
`--shadow-toolbar`, `--inset-hud`, `padding 6px 8px`, `gap 6`.
**tool** — h 32, `padding-x 12`, `gap 7`, r 9, 13 px icon + 12.5 px label.
- *label/mode* (`HUD layout`): transparent, `--text-secondary`.
- *default* (`Grid`, `Reset`): transparent, `--text-secondary`.
- *active* (`Snap`): `--accent-tint-weak` fill, `--accent-border` border,
  `--inset-accent-tint`, label `--accent-ink`, check icon.
- *primary* (`Done`): `--accent` fill, `--shadow-cta`, `--inset-accent`,
  `--accent-fg` label (no icon).
A 1 × 18 `--divider` sits between the mode label and the tools.

**Selection** — dashed box: 1.5 px dashed `--accent`, r 12,
`--accent-tint-faint` fill, sized to the widget + 8 px bleed
(146 × 146 around a 130 × 130 keystrokes block at `23, 573`).
**Handle** — 8 × 8, r 2, `--text-primary` fill, 1.5 px `--accent` border, one per
corner, centred on the selection corners (4 px outside the box).
**Selection label / position readout** — hug, r 6, `--accent` fill,
`padding 4px 8px`, `gap 8`, sitting 30 px above the selection:
widget name 11 px Outfit SemiBold `--accent-fg` + `x 32  ·  y 580  ·  1.0×`
10 px DM Mono `rgba(10,11,12,0.75)`.
**Hint chip** — raised chip, r 8, `padding 6px 12px`, 10.5 px mono `--text-muted`.

## Quick palette

**Palette** — 640 wide (height hugs), r 18, `--palette-bg`, `--border-panel`,
`--shadow-panel`, `--inset-hud`, backdrop-blur 15.
**input** — h 58, `padding 0 14px 0 18px`, `gap 12`: 18 px search icon, query
17 px `--text-primary`, 2 × 22 `--accent` caret, `esc` kbd chip.
**seam** — 1 px `--tint-08`.
**caption** — `padding 12px 0 6px 18px`, 9.5 px mono `--tracking-caps` muted.
**result row** — h 48, r 10, `padding-x 12`, `gap 12`, list `padding-x 6`,
`gap 2`: 30 × 30 icon well r 8 (16 px glyph at `7, 7`) → title 13.5 px medium +
sub 10.5 px mono → 0–2 trailing kbd chips (`--tint-07`, `--tint-10` border, r 5,
`padding 3px 7px`, 10 px mono).
- *default*: transparent, icon well `--tint-07`, sub `--text-muted`.
- *selected*: `--accent-tint-weak` fill, `--accent-border` border,
  `--inset-row-selected`, icon well `--accent-tint-strong`, sub `--accent-ink`,
  trailing kbd text `--text-primary`.
**footer** — `padding 10px 14px 10px 18px`, `gap 14`: repeated
(`kbd` 10 px mono `--text-secondary` + word 11 px `--text-muted`) pairs, a flex
spacer, then a 14 px loadout icon + loadout name 10 px mono muted.

## Shared atoms

- **Eyebrow pill** — hug, r 9, `rgba(34,38,43,0.97)`, `--border-eyebrow`,
  `--shadow-raised`, `--inset-raised`, `padding 6px 12px`, `gap 10`:
  7 px status dot + 10.5 px DM Mono `--text-secondary` `--tracking-caps-lg`.
- **kbd chip** — three flavours: nav (`--surface-2`, r 4, 9.5 px, uppercase,
  `--tracking-caps-kbd`), on-accent (`rgba(0,0,0,0.2)`, r 6, 11 px,
  `--inset-kbd`), palette/settings (`--tint-07` + `--tint-10`, r 5, 10 px).
- **Status dot** — 6–11 px circle; `--ok` online/ready, `--warn` degraded,
  `--text-muted` offline. Avatar presence dots are 11 px with a shell-coloured ring.
- **Seam** — 1 px `--seam` between rows inside a Group.
- **Divider** — 1 px `--divider`, 36 px tall in the dock, 18 px in the toolbar.
- **Icon well** — 24 / 30 / 34 / 44 px squares at r 7 / 8 / 10 / 13, filled with
  `--accent-tint-icon` (active) or `--tint-07` / `--tint-08` (inactive).
- **Noise overlay** — every surface gets the 32.64 px noise tile at
  `mix-blend-mode: overlay`, opacity per `--noise-opacity-*`. Small controls use
  the 24.96 px tile; nav mark and nav tabs use 28.8 px.
