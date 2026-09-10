# Adding a mod

**Four files.** It used to be about twenty-five (`mod-roster.md` §9), and the difference is
almost entirely the edits that used to fail *quietly* — a mod missing from an enum, a table or
a `switch` did not break the build, it shipped a mod that could not be placed, could not be
configured, or drew its own id in a box.

Read this in order. Steps 1 and 2 are mandatory and enforced. Step 3 is only needed when the
mod wants a glyph nobody has drawn yet. Step 4 is the actual game code, and is the only part
that is still genuinely per-mod work.

Java and Rust are **generated**. You do not write either. Neither is the factory HUD
placement: it is `default_placement` in step 1, and both `DEFAULT_HUD` tables come from it.

---

## 1. Declare it — `schema/mods/<id>.json`

One file. Everything else in `mods.json` and `loadout.json` is derived from it.

```jsonc
{
  "id": "combo",
  "kind": "hud",              // hud = it draws; gameplay = it mutates a client-side option
  "category": "pvp",          // the Mods panel's filter tab — a product split, not a data one
  "hypixel_safe": "safe",     // §11
  "label": "Combo counter",
  "icon": "swords",           // a glyph from @void/ui's sheet — MOD_ICONS is generated from this
  "the": true,                // does the label take "the" in prose? "the Combo counter"
  "noun": "the combo chip",   // the thing it draws, for the shared scale/opacity descriptions
  "on_description": "the combo counter is enabled",
  "description": "Consecutive hits landed without being hit back.",
  "source": "LivingEntity#hurtTime, via the tick sensor",
  "enabled": true,            // the factory `on`
  // Where the chip starts on a fresh install. REQUIRED on a `kind: hud` mod and FORBIDDEN on
  // a gameplay one — the schema enforces both, and `build.mjs` refuses either mistake by name.
  // These are the overlay's design-canvas pixels on a 38-42px column rhythm, NOT the tighter
  // 18-20px offsets in `crates/void-loadout`'s curated loadouts; `_base.json`'s
  // `hud_placement` description has the argument. `note` is optional prose, printed above the
  // row in both generated tables — use it when the number needs defending, not to restate it.
  "default_placement": {
    "note": "Under Coordinates, the mod players confuse it with.",
    "anchor": "top-left", "dx": 23, "dy": 217
  },
  "defaults": { "reset_ms": 3000 },
  "settings": {
    "reset_ms": {
      "description": "How long without a hit before the count resets.",
      "type": "integer", "minimum": 500, "maximum": 10000, "default": 3000
    }
  }
}
```

Do **not** write `on`, `scale`, `opacity`, `background`, `border` or `padding`. Those are the
shared block for `kind: hud` (`mods/_shared.json`) and every HUD mod gets them. If yours
genuinely needs a different default or wording for one, use `shared_overrides` — the
watermark's `opacity: 0.9` and keystrokes' `0.85` are the only two today, and each says why in
its own file.

Adding a property that *every* HUD mod should have goes in `_shared.json#/hud`, once. Read its
`$comment` first: it records what the block deliberately excludes and why, including one
property that was added and removed inside a day.

Then add the id to `ORDER` in `schema/build.mjs` (append, never insert) and:

```sh
cd pvp/schema
node build.mjs            # regenerates mods.json, patches loadout.json
npm i --no-save ajv && node validate.mjs
```

Bump `registry_version` in `mods/_base.json` if you added, removed or reclassified a mod. It
stays a human decision because "did this break a stored loadout" is not something a diff can
answer.

`node build.mjs --check` is the CI gate. It will fail if you edit `mods.json` by hand.

## 2. Draw it — `packages/ingame/src/mods/<id>.tsx`

One `ModArt`: a `Thumbnail` for the grid and a `Preview` for the page. **This is not
optional** — `mods/index.ts` is `satisfies Record<ModId, ModArt>`, so step 1 without step 2
does not compile. Read `mods/types.ts` first; it explains why the two pictures are different
jobs and what each is allowed to do.

- **Thumbnail** answers *which mod is this*, at 145px, in a grid of siblings. Monochrome
  always — the accent marks a live value or the selection and nothing else, and that rule is
  absolute on the menu's own surface. It may read live store values and the mod's own
  settings; it may not read the mod's *colour*.
- **Preview** answers *what will this look like*, and it has to **move when a setting moves**.
  For a mod that draws HTML, this is the identical component `HudLayer` places — not a copy.
  For one that draws into the world, it is a diagram fed by the game's own numbers
  (`menu/gameplay-previews.tsx`).

`test/preview.test.tsx` walks every setting of every mod and asserts the drawing changed. A
setting that provably cannot show in a preview goes in `NOT_IN_THE_PREVIEW` **with a reason**.
`ModArt.staticPreview` is the same escape hatch one level up, and it is empty today.

Then add the id to `MOD_ORDER` in `mods/order.ts` — where it should *read* in the grid, which
is a layout decision and deliberately not the registry's order. Forgetting throws at import
time, in every build, rather than silently dropping the tile.

## 3. Only if the glyph is new — draw it

The *mapping* is step 1: `icon` in `schema/mods/<id>.json` is the whole declaration, and
`MOD_ICONS` is generated from it. You only come here when you named a glyph that does not
exist yet:

- `packages/ui/src/components/Icon.tsx` — the path data, and `ICON_NAMES`
- `packages/ingame/scripts/build-icons.py` — a cell in the in-game sprite sheet

`MOD_ICONS` is `satisfies Record<ModId, IconName>`, so naming a glyph that has no drawing
fails `pnpm -r typecheck` rather than throwing in game.

## 4. Make it work — the game code

This is the only step that is still writing, and it is only about *behaviour*:

- A **HUD** mod: a sensor field if it needs one, and a widget in
  `packages/ingame/src/hud/<id>.tsx` added to `HudLayer`'s `WIDGETS`.

  **Its own module, not `widgets.tsx`.** `watermark.tsx` set that precedent and the Wave 2
  sweep followed it for all six, because `mods/art.tsx` already states the principle for the
  other half of a mod's drawing: what a *single* mod draws lives in that mod's own file, so the
  shared file stops growing when the roster does. `widgets.tsx` holds the eight that predate the
  rule plus the shared `HudWidgetProps`; a new mod imports that type and exports one `Hud<Name>`
  of its own. Six more appended to one file would have been six agents editing one file, which
  is the practical half of the same argument.
- A **gameplay** mod: an actuator in `LiveState.applyActuatorFields`, and the Mixin it writes
  through.

**You do not touch `ModRegistry.java` or `crates/void-loadout/src/mods/generated.rs`.** Both
are generated from `schema/mods.json`:

```sh
cd pvp
node scripts/gen-java-registry.mjs     # mod/.../state/ModRegistry.java
node scripts/gen-rust-mods.mjs         # crates/void-loadout/src/mods/generated.rs
```

Both take `--check` and both are CI gates. Run them after step 1, every time — the Java
registry's clamp table and every Rust settings struct come out of the schema, and Rust's are
`#[serde(deny_unknown_fields)]`, so a field the schema has and Rust does not is a **runtime
parse failure of the whole registry**.

The hand-written halves are still hand-written and still yours to read: the `Setting`/`clamp`
machinery in `ModRegistry.java` (inside the generator's template, between the `BEGIN/END
GENERATED DATA` markers), and `registry()` / `validate_settings()` / the validating newtypes in
`mods.rs`.

### Reading the game, instead of remembering it

**Do not write a Mixin from memory about what Minecraft does.** Three defects were shipped that
way and each was found the same way — by disassembling the method and looking. Two of them were
*defended by a confident doc comment*, which is what stopped anyone checking:

| Shipped claim | What the bytecode said |
|---|---|
| "`doAttack` runs only when there is something to attack, so reaching TAIL is the swing having connected" | `swingHand()` is called at offset 12, before the hit result is read; MISS reaches TAIL too. The combo counter was counting clicks. |
| `toggle_sprint.mode: "hold"` restores vanilla hold-to-sprint | `setKeyPressed` writes the same `pressed` field the sprint tests read, so the latch is indistinguishable from a held key. `hold` had no implementation but "write nothing", which is `on: false`. |
| `old_animations.swing` reverts "the shorter, flatter arc 1.7 drew" | `getMiningSpeedMultiplier`, `swingHand`, `tickHandSwing` and the first-person arc are byte-for-byte identical in 1.7.10 and 1.8.9. There is no such arc. |

**And one that was caught rather than shipped, because it is the variant the table above cannot
warn you about: a fact that is true when you sample it and false where you report it.** Every
other thing a swing knows — what it hit, whether the target was alive, whether you were spectating
— can be read at `doAttack` TAIL, so TAIL is where a fifth reading naturally goes. `sprint_reset`
needed `isSprinting()`, and `PlayerEntity.attack` calls `setSprinting(false)` at offset 339 in the
branch that applies the knockback the sprint earned. The client reaches it:
`ClientPlayerInteractionManager.attackEntity` runs `PlayerEntity.attack` at its own offsets 32-34.
So a TAIL reading is false for exactly the hits the counter exists to count.

The reason it belongs in this section is what the failure would have looked like: not a wrong
number, but a **counter that stayed at zero**, which is a legal value and reads as a player who
never resets. The three defects above were all found because something looked wrong. This one
would not have. **Before reading a mutable flag off the game, check whether the method you are
injected into writes it** — `javap -c` the whole method and grep the disassembly for the setter,
not just for the getter you want.

The 1.8.9 named jar is already in the Gradle cache, put there by Loom:

```sh
find ~/.gradle/caches -name '*minecraft-merged-legacy-intermediary*-v2.jar'
javap -p -c -cp <that jar> net.minecraft.client.MinecraftClient | less
```

Take the jar **without** the `-intermediary` suffix — the sibling is the intermediary one and its
members are named `method_6644`, not `doAttack`.

**Another version is about five minutes, not a blocker.** `old_animations` was withdrawn once for
want of a 1.7.10 mapping, and it need not have been:

```sh
mkdir -p /tmp/mc17 && cd /tmp/mc17
curl -sSL -o vm.json https://launchermeta.mojang.com/mc/game/version_manifest_v2.json
#   -> the version's own json -> downloads.client.url ; verify the published sha1
curl -sSL -o client.jar <that url>
#   Legacy Fabric publishes a `mergedv2` yarn classifier — official+intermediary+named in one,
#   so no separate intermediary download is needed. Follow redirects: the host 301s.
curl -sSL -o yarn.jar \
  "https://repo.legacyfabric.net/repository/legacyfabric/net/legacyfabric/yarn/<ver>+build.<n>/yarn-<ver>+build.<n>-mergedv2.jar"
unzip -oq yarn.jar -d yarnmerged        # yarnmerged/mappings/mappings.tiny
#   tiny-remapper, mapping-io and asm are ALREADY in ~/.gradle/caches/modules-2 — Loom put them
#   there. No new dependency, no Gradle run.
java -cp "<those jars>" net.fabricmc.tinyremapper.Main \
  client.jar client-named.jar yarnmerged/mappings/mappings.tiny official named
```

Two traps worth knowing before you read the result:

- **A `tableswitch` over an enum does not use the enum's ordinals.** javac emits a synthetic
  `$SwitchMap` class; read *that* `<clinit>` to learn which arm is which. `BlockHitResult$Type`
  declares `MISS, BLOCK, ENTITY`, so guessing from declaration order points at the wrong arm.
- **Legacy Fabric names `ModelPart`'s rotation fields `posX/posY/posZ`.** The translation is
  `pivotX/pivotY/pivotZ`. Implementing `rightArm.posY = -0.5235988f` as a translate gets nothing;
  it is a −30° yaw.

Then prove the targets exist rather than that the code compiles: `./gradlew remapJar` and read
`build/libs/` back. A member the mapping does not know is left as the yarn string, so an
intermediary name in the output *is* the proof.

## 5. Check it

```sh
cd pvp
node schema/build.mjs --check          # the schema is its sources
node scripts/gen-java-registry.mjs --check
node scripts/gen-rust-mods.mjs --check
node packages/protocol/scripts/gen.mjs  # regenerate BEFORE the rest — see below
pnpm -r build                          # runs the Ultralight guard, and refreshes the dist
pnpm -r typecheck
pnpm -r test
cd crates && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings
```

**`pnpm -r build` before `test`, and it is not a preference.** `apps/desktop` resolves
`@void/protocol` through its `exports` map to `dist/`, not to `src/` — so its typecheck and its
tests both run against whatever was last *built*. Add a mod, run `pnpm -r test`, and the launcher
half passes: it is checking your new mod against a `MOD_IDS` that does not contain it yet. Build
first and the two hand-written total tables in `apps/desktop/src/local/registry.ts` and
`src/screens/Mods.tsx` fail as they are designed to. **This has already shipped a broken launcher
build once**, in the commit that added `sprint_reset`.

**The build also carries the Ultralight guard**, which is a second and unrelated reason to run
it. `scripts/check-ultralight.mjs` enforces 22 rules from `design/ultralight-notes.md` — the engine drops `text-shadow`, `backdrop-filter`,
`mix-blend-mode` and more — and it runs in `build`, not in `test`. A rule violation is a style
that renders in jsdom, passes every test, and does nothing in game. That has already happened
once: a `text_shadow` setting reached the shared HUD block and would have shipped a switch that
drew nothing.

---

## What you do not have to do any more

Worth listing, because the habit is to go looking for these:

| Was | Now |
|---|---|
| `mods.json`: `required`, `properties`, `mod_id`, `hud_mod_id`/`gameplay_mod_id`, `<id>_settings`, `<id>_entry`, `examples[0]` | derived from `mods/<id>.json` |
| `loadout.json`: `mod_states.properties`, `hud_layout.maxItems` | derived |
| `TilePreview.tsx`: a `switch` arm | `mods/<id>.tsx` |
| `ModSettingsScreen.tsx`: `LIVE_WIDGETS`, `PREVIEW_ZOOM` | `mods/<id>.tsx` |
| `registry.ts`: `SETTING_RANGES`, `SETTING_ENUMS` | generated into `@void/protocol` from the sub-schemas |
| `registry.ts`: `MOD_ORDER` | moved to `mods/order.ts`, and now asserts completeness |
| `hud-geometry.ts` + `Loadout.java`: the two `DEFAULT_HUD` tables | generated from `default_placement` |
| `apps/desktop`: the mod-card preview `switch` | `Record<ModId, …>` — a compile error, not a `default` arm |
| `@void/protocol`: `MOD_IDS`, `HUD_MOD_IDS`, types | already generated |
| `@void/ui`: `MOD_ICONS` | generated from the entry's `icon` |
| `ModRegistry.java`: the whole registry table | generated (`gen-java-registry.mjs`) |
| `mods.rs`: `ModId`, the enums, 13 settings structs | generated (`gen-rust-mods.mjs`) |

Add a `scale`- or `opacity`-style property that every HUD mod should have? It goes in
`schema/mods/_shared.json#/hud` **once**, and all eight get it. That is what the HUD chrome
block (`background`, `border`, `padding`) is, and `mod-roster.md` §9's advice —
settle the shared HUD property set before the ninth HUD widget, or retrofit it into twenty
settings pages later — is why it exists.
