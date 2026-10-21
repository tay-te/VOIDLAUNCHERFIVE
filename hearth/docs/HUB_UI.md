# Hearth on the VOID Hub (Ultralight)

Decided by the user, 2026-10-19: all of Hearth's player-facing UI moves to the VOID Hub's Ultralight HTML layer, the
way Mettle's Classes and Gear pages work. Hearth's own Java screens stay as a fallback when the Hub is not installed.
Designs go to Figma first (file `3c0Wgc4ZKFSpm98fWJoARS`, page 295:2 "In game · All screens"), the user picks, then
the Hub pages are built and page 295:2 is kept in sync with what ships.

Screens to move: `ConversationScreen` (talk and dialogue), `JournalScreen` and `QuestHud` (quests), `NoticeBoardScreen`,
`MarketScreen` (shop and haggling), `BankScreen`, `ConfrontScreen` (a guard stops you), `LockpickScreen`,
`PickpocketScreen`, `DiceScreen`, `ReputationScreen`, `LawHud`.

## The pattern (from the Mettle/Hub session, mettle/docs/OVERLAY.md on branch `mettle`)

Hearth side (client), all static and reached by reflection, so neither mod depends on the other:
- `HearthState.build()`: the whole page state as a JSON-able tree; `HearthState.revision()`: a cheap counter bumped
  whenever build()'s output would change.
- `HearthActions.*`: overlay -> Java calls, each only sends a C2S packet; the server re-validates everything
  (ownership, distance, costs, standing). The page only asks.
- `HearthScreens.set...Opener(BooleanSupplier)`: the Hub registers openers; Hearth calls the opener instead of its own
  screen, and falls back to its own screen if it returns false or none is registered.

Hub side (branch `claude/hub-hearth` off `claude/voidhub`, its own worktree; never edit the voidhub worktree directly):
- `hub/.../hearth/HearthLink` (MethodHandles behind `FabricLoader.isModLoaded("hearth")`, every handle optional) and
  `HearthSync` (the render thread checks revision once a tick; a worker serialises and diffs; nothing resent on reopen).
  Generalise `MettleSync` and `CardFx` into mod-neutral classes first, in a separate small commit, so both mods share
  one copy.
- `model/Bridge.java`: a `HEARTH_OPS` whitelist with argument validation.
- Page files `hearth.js` / `hearth.css`; the shared touch points are index.html, hub.js, Bridge and HubScreen.
- Event-opened screens (right-click an NPC, a guard stops you): the server tells the client, Hearth calls the registered
  Hub opener, and the Hub opens a screen on a Hearth page. Dialogue wants a lighter HubScreen variant without slots.
  In-page modals report their state to Java (the `ui` message) so Esc/Tab go to the page; re-send the modal state on
  every reopen, count every popover as modal, pause page timers while closed, drop stale queued messages on close.
- Landing: merge `claude/voidhub` into the branch; run the unit tests, `hubFunctional` (also `-Patlas`), `hubMettle`,
  `hubAudit`, `hubPerf -Patlas`; then `git merge --ff-only` in the voidhub worktree after checking it is clean.

Performance: a small change paints in about 5-6 ms, DOM-heavy changes 20-40 ms, and CSS-animated layers with masks or
gradients 30-60 ms per frame. So minigames (lockpicking, pickpocketing timing, dice) draw their moving parts natively
in Java at full frame rate (the `CardFx` model: the page draws static chrome and reports rects), read input in the
screen's own key and mouse handlers, keep timing and results authoritative in Java and on the server, and update the
page only on discrete state changes. Never animate with CSS. Glows only as radial gradients or baked PNGs.

## The chrome (current Hub)

Window 987x606 design px (radius 32); top islands 42 px high at y 18 as pills; a pill segment bar 36 px high, 20 px from
the window top; content from 76 px down. Panels #191A1C on #0B0B0C, 1 px hairlines at about 10 % white, cards radius
20, Outfit Medium, spaced uppercase eyebrows 8.5-9.5 px, text floor 10.5 px, a 16 px text safe area inside cards.
Figma: section 295:397 holds the Hub pages (an older "Hub · Quests (Hearth panel)" frame 295:1242 already on the
current chrome); section 295:1770 Kit has the hub components frame 268:50520 (slot set 269:50573, segments 268:50529);
Mettle's cards are sections 2f (510:18631), 2g and 2h (546:23595). The user's UI rules: flat, rounded only, no blurred
shadows, subtle colour (gamified screens may use colour and glow), pixel-art cards.

## Decisions (user, 2026-10-19; Figma section 651:37945 on page 295:2)

- Conversation: direction **A, the portrait card** (frame 653:37976) is the default, with **B, the cinematic bar**
  (655:38485) as a per-player toggle in settings. Every other Hearth screen uses A's language.
- Coins: **crowns, marks and bits with coin icons everywhere** prices and costs appear (not a single bits total).
- Haggling: the merchant's limit band is **hidden by default and appears with higher Mettle Speech** (KCD-style).
- Mettle training: **pay and train inline** in the conversation (the 300-399 training band; EconomyService.charge).
- Coordinator's calls on the designer's other open questions: the quest HUD and law HUD stay Hearth-native (cheap, they
  already reserve their rects for Atlas), restyled to the Hub look; event screens keep the VOID mark island and Esc;
  portraits are generated per resident from their own skin (SkinComposer) at 16x16; wanted posters can be tracked;
  Mettle skill names show on odds chips ("Persuade - Speech - 62 %").

## Keeping Mettle green when generalising MettleSync and CardFx (from the Mettle session, 2026-10-19)

Keep green: `hubMettle`, `hubAudit` (fails on any of the 13 UI audit regressions), `hubFunctional` (plain and
`-Patlas`) and `hubPerf -Patlas`, and the card PERF budgets in MettleTest's PERF lines: draft idle 0 page paints,
settled Mythic 0 paints, hover p95 under 10 ms, pick p95 under 50 ms, 400-card binder idle 0 state rebuilds.
Subtleties: CardFx rects are fractional (getBoundingClientRect, within 0.6 px at ultrawide/4K) and textures must clear on
F3+T; MettleSync keeps what the page holds across screen opens (one shared view), so reopening sends nothing. Ping the
Mettle session with the generalisation commit for review before it lands in `claude/voidhub`.

## Keys (default bindings, checked 2026-10-19)

Hearth's own keys, set so none collides with Mettle, the Hub, Atlas, VOID LOD or vanilla defaults:

| Mapping | Default | Opens |
|---|---|---|
| `key.hearth.journal` | J | The quest journal (vanilla, Mettle, Atlas and the Hub leave J free) |
| `key.hearth.standing` | unbound | The reputation page; reputation now lives on the Hub's Quests page, so it needs no key |

Taken by others (do not reuse as a Hearth default): Right Shift `key.voidhub.open`; K `key.mettle.character`, R
`key.mettle.belt_use` (belt pockets unbound); M `key.atlas.map`, N `key.atlas.minimap`, B `key.atlas.waypoint`, = and -
Atlas zoom (`key.atlas.settings` unbound); F8 `key.void_lod.toggle`; plus vanilla's (WASD, Space, Shift, Ctrl, E, Q, F,
T, /, L advancements, P social, C and X hotbar save/load, Tab, 1-9, F1-F3, F5, F11). Until K was given back to Mettle,
`key.hearth.standing` also defaulted to K and won the press, opening reputation instead of Mettle's Classes.

## Open follow-ups (2026-10-19)

- Hub under CSS zoom (GUI scales 1-2): spaces between inline elements collapse ("fromHearth"); use explicit gaps or
  non-breaking spaces, and add a hubAudit text check at GUI 2.
- Mettle draft hover at 1080p GUI 2 measures 13.9 ms UI-thread CPU per paint, above the 10 ms card hover budget (fine at
  GUI 3+). Make the perf gate measure that UI-thread paint cost at GUI 2, not only the render thread; accepted for now.
- Hearth conversation: with training offered, two options share key 8.
- Not yet in game captures for Figma: the quest HUD and law HUD (still DESIGN on 295:2).

## Living world screens (explore, 2026-10-21; Figma section 708:43644 on page 295:2)

Designed for the user to pick from (LIVING_WORLD_2.md §10, requests/lw0.md F1-F7; F8 is Atlas's own UI). Nothing is
built; lanes keep shipping through conversation options until one is picked.

| # | Frame | Id |
|---|---|---|
| F1 | Conversation with a stranger (hooded bandit's demand in coin icons; pedlar, pilgrim, tollkeeper inset) | 708:43645 |
| F2 | Household A, Ledger (houses and companions side by side, a Quests segment) | 709:44433 |
| F2 | Household B, Roster (everyone in your service, one list, one card) | 710:45834 |
| F3 | Companion gear (worn, held, a small pack; "Take this" from your hotbar) | 711:47250 |
| F4 | People A, List and story (beats, your choice, endings, relations) | 713:48066 |
| F4 | People B, Cards (pixel cards, story pips) | 714:49356 |
| F5 | Reputation with burgher status per town and retinue ranks per lord | 712:47881 |
| F6 | Contest line under the quest HUD (archery table, melee rounds) | 715:50448 |
| F7 | Event line under the arrival banner | 715:50620 |

## Living-world screens: decisions (user, 2026-10-21)

From the "Hearth - Living world screens (explore)" section on 295:2 (PNGs in the scratchpad lwfig/): **Household B, the
roster** (everyone in your service on the left, the selected companion or servant in full on the right); **People A, the
list with story** (folk of note by town on the left, the selected character's story beats and choices on the right); and
build the other five as designed: F1 conversation with a stranger, F3 companion gear, F5 retinue ranks and burgher status on
the reputation page, F6 contest line HUD, F7 event line under the arrival banner. F8 (roads and posts on Atlas) is Atlas's.
