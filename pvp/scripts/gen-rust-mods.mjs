#!/usr/bin/env node
/**
 * Generates the mechanical half of `crates/void-loadout/src/mods.rs` into
 * `crates/void-loadout/src/mods/generated.rs`, from `schema/mods.json`.
 *
 * ## Why this exists
 *
 * `mods.rs` already `include_str!`s `schema/mods.json`, so the registry *data* — labels,
 * icons, classifications, factory defaults — has never been able to drift. The **type
 * surface around it** could, and did. Per mod, by hand:
 *
 * - a `ModId` variant, plus its entry in `ModId::ALL` and an arm of `ModId::as_str`;
 * - a `HudModId` **or** a `GameplayModId` variant, with the same three edits again;
 * - a `<Name>Settings` struct with one field per setting;
 * - a Rust enum per `enum`-typed setting;
 * - a field on `ModRegistryEntries`;
 * - an arm each in `Registry::info`, `defaults_json` and `validate_settings`.
 *
 * Every settings struct is `#[serde(deny_unknown_fields)]`, which is deliberate — a
 * loadout must never quietly carry a setting no version of VOID understands (`store.rs`'s
 * `an_unknown_setting_that_was_never_ours_still_fails_loudly`). The cost of that choice is
 * that a field **omitted** from a struct is not a warning: it is a parse failure of the
 * entire compiled-in registry, at runtime, for every mod at once. Adding `icon` to the
 * entry and the shared HUD chrome block to the eight HUD mods each hit exactly that.
 *
 * So the rule is the same one `schema/build.mjs` applies one level up: **the mechanical
 * edits that fail quietly are derived, and the ones that carry reasoning are written by
 * hand.** `mods.rs` keeps `registry()`, `validate_settings()`, `defaults_json()`, `Kind`,
 * `Category`, `HypixelSafe`, `ModEntry`, `ModInfo` and every doc comment that explains a
 * decision. This script emits the rest.
 *
 * ## Design decision: a committed file, not a `build.rs`
 *
 * Both work. A `build.rs` with `include!(concat!(env!("OUT_DIR"), "/mods.rs"))` could never
 * be stale. It was rejected for four reasons, in descending order of weight:
 *
 * 1. **Precedent, twice.** `schema/mods.json` is generated *and committed* because it is
 *    the contract three languages read (`schema/build.mjs`'s own header says so), and
 *    `packages/protocol/src/generated/*` is generated and committed "so consumers never
 *    need to run this". A third generated artefact that behaves differently from the other
 *    two makes the repo harder to reason about than staleness ever could, and the same
 *    `--check` gate that guards those two guards this one — a stale file fails CI in the
 *    same way, in the same job, with the same fix printed.
 * 2. **A Rust build must not need Node.** `crates/` is buildable today with nothing but a
 *    Rust toolchain: `cargo test -p void-loadout` on a clean checkout, no `pnpm install`,
 *    no `node`. A `build.rs` that shells out to `node` would make the JS toolchain a hard
 *    dependency of every Rust build — including the desktop app's, which is not even a
 *    workspace member — and re-implementing this in Rust to avoid that means a
 *    `serde_json` build-dependency and a second copy of these rules in a second language.
 * 3. **Generated code you cannot open is generated code you cannot debug.** The failure
 *    mode this file exists to prevent is a *runtime* registry parse error whose message
 *    names a field. Reading the struct that rejected it must not require finding
 *    the right `OUT_DIR` under `target/debug/build/`. `deny_unknown_fields` makes the generated
 *    text itself the thing you go and read.
 * 4. **Reviewability.** A schema change that adds a settings field shows up in the PR as
 *    the Rust field it produced. Under `build.rs`, the Rust half of that diff is invisible.
 *
 * The cost of the committed file is exactly one failure mode — someone edits the schema and
 * forgets to re-run — and `--check` in CI is what converts it from a silent drift into a
 * red build. That is the same trade `schema/build.mjs` documents, taken for the same reason.
 *
 * ## Design decision: every enum is generated, including the shared ones
 *
 * `HudBackground` and `HudPadding` come from `schema/mods/_shared.json#/hud` and are shared
 * by all eight HUD mods; `KeySwatch` and `PressedSwatch` are `$ref`s to `mods.json`
 * definitions; the other six are inline in one mod's settings. All of them are generated,
 * rather than the shared ones staying hand-written and being referenced by name, because a
 * hand-written enum whose variants must equal a schema `enum` is the same tax with the same
 * failure mode: add `"blur"` to `_shared.json`'s `background` and every hand-written
 * `HudBackground` becomes an unknown-variant parse failure of the whole registry, exactly as
 * a missing struct field does. Two mechanisms for one job is also a mechanism to forget.
 *
 * The seam between generated and hand-written is instead drawn at *validation*: a `$ref` to
 * a definition that carries an `enum` becomes a generated Rust enum, and a `$ref` to one of
 * the string definitions with a `pattern` (`keybind`, `hex_color`, `hex_color_rgb`) becomes the
 * hand-written validating newtype from `keybind.rs`. That table is `NEWTYPE_REFS` below and
 * it is the *only* hard-coded name in this script; an unrecognised `$ref` is a hard error
 * rather than a guess.
 *
 * ## Naming
 *
 * Everything is derived, so nothing has to be remembered:
 *
 * - `armor_status` → `ArmorStatusSettings`, `toggle_sprint` → `ToggleSprintSettings`,
 *   `fps` → `FpsSettings`. snake_case → PascalCase, one rule, no acronym table.
 * - an inline `enum` setting → `<Mod><Property>`: `crosshair.style` → `CrosshairStyle`,
 *   `cps.mode` → `CpsMode`, `armor_status.orientation` → `ArmorStatusOrientation`.
 * - a shared inline `enum` → `<Kind><Property>`, once for all mods of that kind:
 *   `_shared.json#/hud/background` → `HudBackground`.
 * - a `$ref`'d enum definition → its own name: `key_swatch` → `KeySwatch`.
 *
 * ## Doc comments
 *
 * Every generated item carries the schema's own `description`, which is richer than the
 * one-liners the hand-written structs had — `schema/README.md` requires a `description` on
 * every property, so there is always one. Enum *variants* are the one place the schema has
 * less to say than a human did: JSON Schema has no per-value description, so a variant's doc
 * is the clause of the property description that begins with that value in backticks (which
 * recovers most of `background`, `style` and `mode` verbatim), and the bare value where
 * there is no such clause. Giving `enum` values their own descriptions in `schema/mods/` is
 * the follow-up that would close the gap; it is a schema change, so it is not made here.
 *
 * ## Usage
 *
 *   node scripts/gen-rust-mods.mjs           # write the file
 *   node scripts/gen-rust-mods.mjs --check   # exit 1 if it is out of date. The CI gate.
 *
 * Run it after `node schema/build.mjs`, whose output is this script's input. Dependency
 * free, like every other generator in this repo.
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SCHEMA_PATH = join(ROOT, 'schema', 'mods.json');
const SHARED_PATH = join(ROOT, 'schema', 'mods', '_shared.json');
const OUT_PATH = join(ROOT, 'crates', 'void-loadout', 'src', 'mods', 'generated.rs');

/**
 * The two `$ref` targets that map to a hand-written type instead of a generated one.
 *
 * Both are `type: string` with a `pattern`, and both are parsed and validated by
 * `crates/void-loadout/src/keybind.rs` — a newtype that rejects a bad value on
 * deserialization, which is more than the pattern can do from here. This is the whole seam
 * between what this script writes and what a person writes; an unknown `$ref` is an error.
 */
const NEWTYPE_REFS = {
  keybind: 'Keybind',
  hex_color: 'HexColor',
  hex_color_rgb: 'HexColorRgb',
};

/** Scalar JSON types, for a `$ref` or an inline property without an `enum`. */
const SCALARS = { boolean: 'bool', integer: 'i64', number: 'f64' };

/** Reserved words that cannot be a bare Rust field name. A hit is an error, not a rename. */
const RUST_KEYWORDS = new Set(
  `as break const continue crate dyn else enum extern false fn for if impl in let loop match mod
   move mut pub ref return self Self static struct super trait true type unsafe use where while
   async await abstract become box do final macro override priv typeof unsized virtual yield try`.split(
    /\s+/,
  ),
);

const schema = JSON.parse(readFileSync(SCHEMA_PATH, 'utf8'));
const shared = JSON.parse(readFileSync(SHARED_PATH, 'utf8'));
const defs = schema.definitions;

/* -------------------------------------------------------------------------- */
/* Naming                                                                     */
/* -------------------------------------------------------------------------- */

/** `armor_status` → `ArmorStatus`, `t_shape` → `TShape`, `fps` → `Fps`. */
function pascal(snake) {
  return snake
    .split('_')
    .filter((w) => w.length > 0)
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join('');
}

/* -------------------------------------------------------------------------- */
/* Doc comments                                                               */
/* -------------------------------------------------------------------------- */

const WIDTH = 96;

/** Greedy wrap. `indent` is the Rust indentation the `///` will sit at. */
function wrap(text, indent) {
  const budget = WIDTH - indent.length - 4;
  const out = [];
  let line = '';
  for (const word of text.split(/\s+/)) {
    if (line === '') line = word;
    else if (line.length + 1 + word.length <= budget) line += ` ${word}`;
    else {
      out.push(line);
      line = word;
    }
  }
  if (line !== '') out.push(line);
  return out;
}

/** A `///` block. `paragraphs` are joined by a blank doc line. */
function doc(paragraphs, indent = '') {
  const lines = [];
  const list = (Array.isArray(paragraphs) ? paragraphs : [paragraphs]).filter(Boolean);
  list.forEach((p, i) => {
    if (i > 0) lines.push(`${indent}///`);
    for (const l of wrap(String(p), indent)) lines.push(`${indent}/// ${l}`);
  });
  return lines.join('\n');
}

/**
 * Schema prose on its way into a doc comment.
 *
 * `[` is escaped because rustdoc reads `[foo]` as an intra-doc link and warns when it does
 * not resolve. No `description` in `mods.json` carries one today; this is here so the first
 * one that does is not a surprise warning in someone else's build. Doc text this script
 * writes itself is *not* passed through here, because there `[`Registry`]` is a link on
 * purpose.
 */
const prose = (text) => String(text ?? '').replace(/\[/g, '\\[');

/**
 * The doc for one enum variant: the clause of the property description that begins with
 * that value in backticks, or the bare value.
 *
 * The description is cut at sentence and clause boundaries (`.`, `;`, `:`) and then at a
 * comma **only when a backtick follows it**, so `"…the ring alone, `word` is the wordmark
 * alone"` splits into two variant docs while `"…at low alpha, which is enough to hold a chip
 * together"` stays one. See the header: JSON Schema has nowhere to put a per-value
 * description, and this recovers most of what a human would have written.
 */
function variantDoc(description, value) {
  const segments = (description ?? '')
    .split(/[.;:]\s+/)
    .flatMap((s) => s.split(/,\s+(?=`)/))
    .map((s) => s.trim());
  const hit = segments.find((s) => s.startsWith(`\`${value}\``));
  if (!hit) return `\`${value}\`.`;
  return hit.endsWith('.') ? hit : `${hit}.`;
}

/* -------------------------------------------------------------------------- */
/* The mod set, read out of the schema                                        */
/* -------------------------------------------------------------------------- */

const ids = defs.mod_id.enum;
const hudIds = defs.hud_mod_id.enum;
const gameplayIds = defs.gameplay_mod_id.enum;
const registry = schema.examples[0].mods;

const mods = ids.map((id) => {
  const entry = registry[id];
  const settings = defs[`${id}_settings`];
  if (!entry) throw new Error(`mods.json examples[0] has no entry for "${id}"`);
  if (!settings) throw new Error(`mods.json has no definition for "${id}_settings"`);
  return { id, entry, settings, kind: entry.kind };
});

/** The property names `schema/mods/_shared.json` contributes to a `kind`. */
function sharedKeys(kind) {
  return Object.keys(shared[kind] ?? {}).filter((k) => !k.startsWith('$'));
}

/**
 * Fill a `_shared.json` description's placeholders generically.
 *
 * `build.mjs` substitutes each mod's own `noun`, which is what the per-mod field docs use.
 * A *shared type* has no one mod to speak for, so it speaks for the kind.
 */
function fillShared(text, kind) {
  return text
    .replaceAll('{noun}', kind === 'hud' ? 'a HUD item' : 'the mod')
    .replaceAll('{on_description}', 'the mod is enabled');
}

/* -------------------------------------------------------------------------- */
/* Enum collection                                                            */
/* -------------------------------------------------------------------------- */

/** name → { name, values, docs: [paragraph…] }, in emission order. */
const enums = new Map();

function addEnum(name, values, paragraphs, description) {
  const existing = enums.get(name);
  const variants = values.map((v) => ({
    name: pascal(v),
    value: v,
    doc: prose(variantDoc(description, v)),
  }));
  if (existing) {
    const same = JSON.stringify(existing.variants.map((v) => v.value)) === JSON.stringify(values);
    if (!same) throw new Error(`two different enums both want the Rust name \`${name}\``);
    return name;
  }
  enums.set(name, { name, variants, paragraphs });
  return name;
}

// `$ref`'d definitions that carry an `enum` — one Rust enum for the definition, not per use.
for (const [defName, def] of Object.entries(defs)) {
  if (!Array.isArray(def.enum)) continue;
  if (!NEWTYPE_REFS[defName] && defName.endsWith('_id')) continue; // mod_id/hud_mod_id/…
  if (['kind', 'category', 'hypixel_safe'].includes(defName)) continue; // hand-written
  const used = mods.some((m) =>
    Object.values(m.settings.properties).some((p) => p.$ref === `#/definitions/${defName}`),
  );
  if (used) {
    addEnum(
      pascal(defName),
      def.enum,
      [prose(def.description), `\`mods.json#/definitions/${defName}\`.`],
      def.description,
    );
  }
}

// Shared inline enums — one Rust enum per (kind, property), named `<Kind><Property>`.
const sharedEnumName = new Map(); // `${kind}:${key}` → Rust name
for (const kind of ['hud', 'gameplay']) {
  for (const key of sharedKeys(kind)) {
    const def = shared[kind][key];
    if (!Array.isArray(def.enum)) continue;
    const description = fillShared(def.description, kind);
    const name = addEnum(
      pascal(kind) + pascal(key),
      def.enum,
      [
        prose(description),
        `Shared by every \`kind: ${kind}\` mod — \`schema/mods/_shared.json#/${kind}/${key}\`.`,
      ],
      description,
    );
    sharedEnumName.set(`${kind}:${key}`, name);
  }
}

// Per-mod inline enums, named `<Mod><Property>`.
for (const mod of mods) {
  const skip = new Set(sharedKeys(mod.kind));
  for (const [key, prop] of Object.entries(mod.settings.properties)) {
    if (skip.has(key) || !Array.isArray(prop.enum)) continue;
    addEnum(
      pascal(mod.id) + pascal(key),
      prop.enum,
      [
        prose(prop.description),
        `\`mods.json#/definitions/${mod.id}_settings/properties/${key}\`.`,
      ],
      prop.description,
    );
  }
}

/* -------------------------------------------------------------------------- */
/* Property → Rust type                                                       */
/* -------------------------------------------------------------------------- */

function rustType(mod, key, prop) {
  if (typeof prop.$ref === 'string') {
    const target = prop.$ref.replace('#/definitions/', '');
    const def = defs[target];
    if (!def) throw new Error(`${mod.id}.${key}: $ref to unknown definition "${target}"`);
    if (NEWTYPE_REFS[target]) return NEWTYPE_REFS[target];
    if (Array.isArray(def.enum)) return pascal(target);
    const scalar = SCALARS[def.type];
    if (!scalar) {
      throw new Error(
        `${mod.id}.${key}: $ref "${target}" is a ${def.type} with no enum and no newtype — ` +
          'add it to NEWTYPE_REFS in scripts/gen-rust-mods.mjs',
      );
    }
    return scalar;
  }
  if (Array.isArray(prop.enum)) {
    const sharedName = sharedEnumName.get(`${mod.kind}:${key}`);
    return sharedName ?? pascal(mod.id) + pascal(key);
  }
  const scalar = SCALARS[prop.type];
  if (!scalar) throw new Error(`${mod.id}.${key}: no Rust type for JSON type "${prop.type}"`);
  return scalar;
}

/* -------------------------------------------------------------------------- */
/* Emit                                                                       */
/* -------------------------------------------------------------------------- */

const ID_DERIVE =
  '#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]';
const VALUE_DERIVE = '#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]';
const SETTINGS_DERIVE = '#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]';

const out = [];
const push = (...lines) => out.push(...lines);
const rule = (title) =>
  push(
    '',
    '// ---------------------------------------------------------------------------',
    `// ${title}`,
    '// ---------------------------------------------------------------------------',
    '',
  );

push(
  '//! GENERATED by `scripts/gen-rust-mods.mjs` from `schema/mods.json` — do not edit by hand.',
  '//!',
  '//! A mod is added by writing one file, `schema/mods/<id>.json`, then running',
  '//! `node schema/build.mjs && node scripts/gen-rust-mods.mjs`. Everything below is',
  '//! mechanical: the id enums, one `deny_unknown_fields` settings struct per mod, a Rust',
  '//! enum per `enum`-typed setting, the registry entry struct, and the three per-mod',
  '//! dispatches [`Registry::info`], [`Registry::defaults_object`] and [`check_settings`].',
  '//! The reasoning lives next door in `mods.rs`, which is written by hand.',
  '//!',
  '//! `node scripts/gen-rust-mods.mjs --check` is the CI gate that this file still matches',
  '//! the schema. Editing it by hand is the one way the type surface can drift from the',
  '//! contract again — and because every settings struct is `deny_unknown_fields`, a field',
  '//! that drifts out is a runtime parse failure of the *entire* registry, not a warning.',
  '',
  'use std::fmt;',
  '',
  'use serde::{Deserialize, Serialize};',
  'use serde_json::{Map, Value};',
  '',
  // Derived from NEWTYPE_REFS rather than written out, so adding a fourth validating newtype is
  // one table edit instead of two. The hardcoded pair went stale the first time a third arrived
  // (`hex_color_rgb`) and the generated file failed to compile with an unresolved type — a loud
  // failure, but one the generator had no reason to hand anybody.
  `use crate::keybind::{${Object.values(NEWTYPE_REFS).slice().sort().join(', ')}};`,
  'use crate::loadout::Anchor;',
  'use crate::Error;',
  '',
  'use super::{ModEntry, ModInfo, Registry};',
);

/* ------------------------------- identity ------------------------------- */

rule('identity');

push(
  doc([
    `One of the ${ids.length} mods VOID ships — the closed \`mod_id\` enum of \`schema/mods.json\`.`,
    'Used as the key of `loadout.mods`, as the `id` argument of `void.setModSetting`, and as the id of a HUD item.',
  ]),
  ID_DERIVE,
  '#[serde(rename_all = "snake_case")]',
  'pub enum ModId {',
);
for (const mod of mods) {
  push(doc(prose(mod.entry.description), '    '), `    ${pascal(mod.id)},`);
}
push('}', '', 'impl ModId {');
push(
  doc('Every mod id, in registry order.', '    '),
  `    pub const ALL: [ModId; ${ids.length}] = [`,
  ...mods.map((m) => `        ModId::${pascal(m.id)},`),
  '    ];',
  '',
  doc('The snake_case id used as a `loadout.mods` key and in `mods.<id>.<key>` paths.', '    '),
  '    pub fn as_str(self) -> &\'static str {',
  '        match self {',
  ...mods.map((m) => `            ModId::${pascal(m.id)} => "${m.id}",`),
  '        }',
  '    }',
  '}',
);

/** The `hud_mod_id` / `gameplay_mod_id` narrowings — same shape, different subset. */
function narrowed(name, subset, docs) {
  const rows = mods.filter((m) => subset.includes(m.id));
  push(
    '',
    doc(docs, ''),
    ID_DERIVE,
    '#[serde(rename_all = "snake_case")]',
    `pub enum ${name} {`,
    ...rows.flatMap((m) => [doc(prose(m.entry.description), '    '), `    ${pascal(m.id)},`]),
    '}',
    '',
    `impl ${name} {`,
    doc(`Every ${name === 'HudModId' ? 'HUD' : 'gameplay'} mod id, in registry order.`, '    '),
    `    pub const ALL: [${name}; ${rows.length}] = [`,
    ...rows.map((m) => `        ${name}::${pascal(m.id)},`),
    '    ];',
    '',
    doc('Widens to the full mod id enum.', '    '),
    '    pub fn as_mod_id(self) -> ModId {',
    '        match self {',
    ...rows.map((m) => `            ${name}::${pascal(m.id)} => ModId::${pascal(m.id)},`),
    '        }',
    '    }',
    '',
    doc(
      `Narrows a mod id, returning \`None\` for a ${name === 'HudModId' ? 'gameplay' : 'HUD'} mod.`,
      '    ',
    ),
    '    pub fn from_mod_id(id: ModId) -> Option<Self> {',
    `        ${name}::ALL.into_iter().find(|m| m.as_mod_id() == id)`,
    '    }',
    '',
    doc('The snake_case id.', '    '),
    '    pub fn as_str(self) -> &\'static str {',
    '        self.as_mod_id().as_str()',
    '    }',
    '}',
  );
}

narrowed('HudModId', hudIds, [
  `The subset of [\`ModId\`] whose \`kind\` is \`hud\`: the ${hudIds.length} mods that own a draggable HUD item.`,
  'A mod may only appear in `loadout.hud` if it is listed here.',
]);
narrowed('GameplayModId', gameplayIds, [
  `The subset of [\`ModId\`] whose \`kind\` is \`gameplay\`: the ${gameplayIds.length} mods an actuator Mixin reads every frame.`,
  'These are the only ids accepted by `void.setGameplay`.',
]);

/* --------------------------- factory placement --------------------------- */

/*
 * `mod_entry.default_placement` — the factory HUD layout, one entry per HUD mod.
 *
 * The *values* are not generated: they arrive with the rest of the registry, because
 * `mods.rs` already `include_str!`s `schema/mods.json` and parses `examples[0]`. What has to
 * be generated is the **type**, for the reason the header gives — `ModEntry` is
 * `deny_unknown_fields`, so a field the schema has and Rust does not is a runtime parse
 * failure of the entire registry, for every mod at once, the moment `registry()` is first
 * touched. Adding `default_placement` to the schema without this struct is exactly that.
 *
 * `Anchor` is `crate::loadout::Anchor` rather than a tenth generated enum: a factory
 * placement is a `hud_item` without its `id` and `scale`, and it must be the same anchor a
 * stored one is, or a layout could not round-trip through `Loadout`.
 */
rule('the factory HUD layout');

{
  const placement = defs.hud_placement;
  if (!placement) {
    throw new Error('mods.json has no hud_placement definition — run `node schema/build.mjs`');
  }
  push(
    doc([
      prose(placement.description),
      'The values come from the compiled-in registry, not from this file; what is generated here is the type the entry needs in order to parse at all.',
    ]),
    '#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]',
    '#[serde(deny_unknown_fields)]',
    'pub struct HudPlacement {',
    doc(prose(placement.properties.anchor.description), '    '),
    '    pub anchor: Anchor,',
    '',
    doc(prose(placement.properties.dx.description), '    '),
    '    pub dx: f64,',
    '',
    doc(prose(placement.properties.dy.description), '    '),
    '    pub dy: f64,',
    '}',
    '',
    'impl Registry {',
    doc(
      [
        "One HUD mod's factory placement — where its widget starts on a HUD nobody has touched.",
        'Infallible over [`HudModId`]: the schema `required`s `default_placement` on every `kind: hud` entry and forbids it on every gameplay one, so the `Option` on [`ModEntry`] can only be `None` for a mod this method cannot be called with.',
      ],
      '    ',
    ),
    '    pub fn default_placement(&self, id: HudModId) -> HudPlacement {',
    '        let entry = match id {',
    ...hudIds.map((id) => `            HudModId::${pascal(id)} => self.mods.${id}.default_placement,`),
    '        };',
    '        entry.expect("schema/mods.json requires default_placement on every kind: hud entry")',
    '    }',
    '}',
  );
}

/* --------------------------- settings enums ----------------------------- */

rule('settings enums');

for (const e of enums.values()) {
  push(
    doc(e.paragraphs),
    VALUE_DERIVE,
    '#[serde(rename_all = "snake_case")]',
    `pub enum ${e.name} {`,
    ...e.variants.flatMap((v) => [doc(v.doc, '    '), `    ${v.name},`]),
    '}',
    '',
  );
}
out.pop();

/* -------------------------- settings structs ---------------------------- */

rule('per-mod settings');

push(
  '// Every struct below is `#[serde(deny_unknown_fields)]`: a loadout must never quietly',
  '// carry a setting no version of VOID understands, and `store.rs` has a test that says so.',
  '// The cost is that the field list here must be exactly the schema\'s property list, which',
  '// is why it is generated rather than typed.',
);

for (const mod of mods) {
  const required = new Set(mod.settings.required ?? []);
  push(
    '',
    doc([`${prose(mod.settings.title)}.`, prose(mod.settings.description)]),
    SETTINGS_DERIVE,
    '#[serde(deny_unknown_fields)]',
    `pub struct ${pascal(mod.id)}Settings {`,
  );
  const entries = Object.entries(mod.settings.properties);
  entries.forEach(([key, prop], i) => {
    if (RUST_KEYWORDS.has(key)) {
      throw new Error(`${mod.id}.${key} is a Rust keyword and needs a hand-written #[serde(rename)]`);
    }
    if (i > 0) push('');
    push(doc(prose(prop.description), '    '));
    const ty = rustType(mod, key, prop);
    if (required.has(key)) {
      push(`    pub ${key}: ${ty},`);
    } else {
      push(
        '    #[serde(default, skip_serializing_if = "Option::is_none")]',
        `    pub ${key}: Option<${ty}>,`,
      );
    }
  });
  push('}');
}

/* ------------------------------- registry ------------------------------- */

rule('the registry entries, and the three per-mod dispatches');

push(
  doc([
    `Every mod VOID ships, keyed by id. Closed set of ${ids.length}.`,
    '`deny_unknown_fields` here is what makes a mod added to `mods.json` but not to this file a loud failure rather than a silently missing entry.',
  ]),
  SETTINGS_DERIVE,
  '#[serde(deny_unknown_fields)]',
  'pub struct ModRegistryEntries {',
);
mods.forEach((m, i) => {
  if (i > 0) push('');
  push(doc(prose(`${m.entry.label} — ${m.entry.description}`), '    '));
  push(`    pub ${m.id}: ModEntry<${pascal(m.id)}Settings>,`);
});
push('}');

/* ---------------------------------------------------------------------------
 * ModStates — the same per-mod field list, one struct further out.
 *
 * It lived hand-written in `loadout.rs` and was missed by the first codegen pass, which
 * covered `mods.rs` only. That gap surfaced the way this kind always does: the fourteenth
 * mod's registry defaults failed to store with `unknown field \`direction\`, expected one
 * of …`, because `ModStates` is `deny_unknown_fields` too and had thirteen fields.
 *
 * Only the fields move here. `impl ModStates` stays in `loadout.rs` and is entirely generic
 * — `get`, `set` and `apply_patch` all go through `as_object()` and a string id, with no
 * per-mod arm anywhere — which is exactly why the struct was safe to generate and the impl
 * was never the tax.
 * ------------------------------------------------------------------------- */

push('');
push(
  doc([
    'Enabled state plus settings for each mod. Every key is optional: an omitted mod falls back to its registry `defaults`, which is what keeps old loadouts valid as mods are added.',
    '`deny_unknown_fields` makes a mod the schema has and this struct does not a loud failure at the first `set`, rather than a setting that silently will not store.',
  ]),
  '#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]',
  '#[serde(deny_unknown_fields)]',
  '#[allow(missing_docs)]',
  'pub struct ModStates {',
);
mods.forEach((m) => {
  push('    #[serde(default, skip_serializing_if = "Option::is_none")]');
  push(`    pub ${m.id}: Option<${pascal(m.id)}Settings>,`);
});
push('}');

push(
  '',
  'impl Registry {',
  doc('Classification and copy for one mod.', '    '),
  '    pub fn info(&self, id: ModId) -> ModInfo<\'_> {',
  '        match id {',
  ...mods.map((m) => `            ModId::${pascal(m.id)} => self.mods.${m.id}.info(),`),
  '        }',
  '    }',
  '',
  doc(
    [
      'One mod\'s factory defaults as a JSON object.',
      'The dispatch behind [`super::defaults_json`], which is where the reasoning is.',
    ],
    '    ',
  ),
  '    pub(crate) fn defaults_object(&self, id: ModId) -> Map<String, Value> {',
  '        match id {',
  ...mods.map((m) => `            ModId::${pascal(m.id)} => self.mods.${m.id}.defaults_object(),`),
  '        }',
  '    }',
  '}',
);

push(
  '',
  doc([
    'Round-trips a settings object through the type for `id`.',
    'The dispatch behind [`super::validate_settings`], which is where the reasoning is.',
  ]),
  'pub(crate) fn check_settings(id: ModId, value: Value) -> Result<Value, Error> {',
  '    match id {',
  ...mods.map(
    (m) => `        ModId::${pascal(m.id)} => super::check::<${pascal(m.id)}Settings>(id, value),`,
  ),
  '    }',
  '}',
);

/* ------------------------------- Display -------------------------------- */

rule('Display');

for (const name of ['ModId', 'HudModId', 'GameplayModId']) {
  push(
    `impl fmt::Display for ${name} {`,
    '    fn fmt(&self, f: &mut fmt::Formatter<\'_>) -> fmt::Result {',
    '        f.write_str(self.as_str())',
    '    }',
    '}',
    '',
  );
}
out.pop();

/* -------------------------------------------------------------------------- */
/* Write or check                                                             */
/* -------------------------------------------------------------------------- */

const content = `${out.join('\n')}\n`;

if (process.argv.includes('--check')) {
  let current = null;
  try {
    current = readFileSync(OUT_PATH, 'utf8');
  } catch {
    /* missing counts as stale */
  }
  if (current !== content) {
    console.error(
      `stale: ${OUT_PATH} does not match schema/mods.json — run \`node scripts/gen-rust-mods.mjs\``,
    );
    process.exit(1);
  }
  console.log(
    `crates/void-loadout/src/mods/generated.rs is up to date ` +
      `(${ids.length} mods, ${enums.size} settings enums).`,
  );
} else {
  writeFileSync(OUT_PATH, content);
  console.log(
    `wrote crates/void-loadout/src/mods/generated.rs — ${ids.length} mods ` +
      `(${hudIds.length} hud, ${gameplayIds.length} gameplay), ${enums.size} settings enums.`,
  );
}
