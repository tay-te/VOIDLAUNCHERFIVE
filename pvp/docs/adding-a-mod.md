# Adding a mod

**Four files.** It used to be about twenty-five (`mod-roster.md` §9), and the difference is
almost entirely the edits that used to fail *quietly* — a mod missing from an enum, a table or
a `switch` did not break the build, it shipped a mod that could not be placed, could not be
configured, or drew its own id in a box.

Read this in order. Steps 1 and 2 are mandatory and enforced. Step 3 is only needed when the
mod wants a glyph nobody has drawn yet. Step 4 is the actual game code, and is the only part
that is still genuinely per-mod work.

Java and Rust are **generated**. You do not write either.

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
  `packages/ingame/src/hud/widgets.tsx` added to `HudLayer`'s `WIDGETS`.
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

## 5. Check it

```sh
cd pvp
node schema/build.mjs --check          # the schema is its sources
node scripts/gen-java-registry.mjs --check
node scripts/gen-rust-mods.mjs --check
pnpm -r typecheck
pnpm -r test
pnpm --filter @void/ingame build       # runs the Ultralight guard — see below
cd crates && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings
```

**Run the ingame build, not just the tests.** `scripts/check-ultralight.mjs` enforces 22 rules
from `design/ultralight-notes.md` — the engine drops `text-shadow`, `backdrop-filter`,
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
| `@void/protocol`: `MOD_IDS`, `HUD_MOD_IDS`, types | already generated |
| `@void/ui`: `MOD_ICONS` | generated from the entry's `icon` |
| `ModRegistry.java`: the whole registry table | generated (`gen-java-registry.mjs`) |
| `mods.rs`: `ModId`, the enums, 13 settings structs | generated (`gen-rust-mods.mjs`) |

Add a `scale`- or `opacity`-style property that every HUD mod should have? It goes in
`schema/mods/_shared.json#/hud` **once**, and all eight get it. That is what the HUD chrome
block (`background`, `border`, `padding`) is, and `mod-roster.md` §9's advice —
settle the shared HUD property set before the ninth HUD widget, or retrofit it into twenty
settings pages later — is why it exists.
