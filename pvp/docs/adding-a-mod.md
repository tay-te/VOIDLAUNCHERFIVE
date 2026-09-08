# Adding a mod

**Six files.** It used to be about twenty-five (`mod-roster.md` §9), and the difference is
almost entirely the edits that used to fail *quietly* — a mod missing from an enum, a table or
a `switch` did not break the build, it shipped a mod that could not be placed, could not be
configured, or drew its own id in a box.

Read this in order. Steps 1 and 2 are mandatory and enforced; 3–6 are per-mod work that
depends on what the mod does.

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

Do **not** write `on`, `scale`, `opacity`, `background`, `border`, `text_shadow` or `padding`.
Those are the shared block for `kind: hud` (`mods/_shared.json`) and every HUD mod gets them.
If yours genuinely needs a different default or wording for one, use `shared_overrides` — the
watermark's `opacity: 0.9` and keystrokes' `0.85` are the only two today, and each says why in
its own file.

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

## 3. Give it an icon — `packages/ui/src/components/Icon.tsx`

`MOD_ICONS[id]`, plus a cell in `packages/ingame/scripts/build-icons.py` if the name is new.
This lives in `@void/ui` rather than beside the art because the desktop launcher needs it too
and cannot import from `packages/ingame`.

## 4. Make it work — Java

- `mod/src/main/java/.../state/ModRegistry.java` — transcribe the entry. `ModRegistryTest`
  cross-checks it against `schema/mods.json` and will tell you what you missed.
- A **HUD** mod: a sensor field if it needs one, and a widget in
  `packages/ingame/src/hud/widgets.tsx` added to `HudLayer`'s `WIDGETS`.
- A **gameplay** mod: an actuator in `LiveState.applyActuatorFields`, and the Mixin it writes
  through.

`ModRegistry.java` is still hand-transcribed, and that is the largest remaining piece of the
per-mod tax. Generating it from `schema/mods.json` is Wave 0 in `mod-roster.md` §7.

## 5. Make it a type — Rust

`crates/void-loadout/src/mods.rs`: a `ModId` variant and a `<Name>Settings` struct. Every
settings struct is `#[serde(deny_unknown_fields)]`, so a field you forget is a **runtime parse
failure of the whole registry**, not a warning. `cargo test -p void-loadout` catches it.

## 6. Check it

```sh
cd pvp
node schema/build.mjs --check
pnpm -r typecheck
pnpm -r test
cargo test -p void-loadout --manifest-path crates/Cargo.toml
```

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

Add a `scale`- or `opacity`-style property that every HUD mod should have? It goes in
`schema/mods/_shared.json#/hud` **once**, and all eight get it. That is what the HUD chrome
block (`background`, `border`, `text_shadow`, `padding`) is, and `mod-roster.md` §9's advice —
settle the shared HUD property set before the ninth HUD widget, or retrofit it into twenty
settings pages later — is why it exists.
