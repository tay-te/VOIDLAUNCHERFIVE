#!/usr/bin/env node
// Compiles every schema in this directory and validates each schema's `examples`
// array against it. Run with:  npm i --no-save ajv && node validate.mjs
// Requires only `ajv` (draft-07). Exits non-zero on the first failure.
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import Ajv from "ajv";

const here = dirname(fileURLToPath(import.meta.url));
const FILES = ["mods.json", "loadout.json", "protocol.json", "bridge.json"];

const ajv = new Ajv({ allErrors: true, strict: false, validateSchema: true });
const docs = new Map();

for (const f of FILES) {
  const doc = JSON.parse(readFileSync(join(here, f), "utf8"));
  docs.set(f, doc);
  ajv.addSchema(doc, doc.$id);
}

let failures = 0;
let examples = 0;

for (const f of FILES) {
  const doc = docs.get(f);
  let validate;
  try {
    validate = ajv.getSchema(doc.$id);
    if (!validate) throw new Error("schema did not compile");
  } catch (e) {
    console.log(`FAIL  ${f}  schema does not compile: ${e.message}`);
    failures++;
    continue;
  }
  console.log(`ok    ${f}  compiles (${doc.$id})`);

  const list = Array.isArray(doc.examples) ? doc.examples : [];
  if (list.length === 0) {
    console.log(`FAIL  ${f}  has no top-level examples array`);
    failures++;
    continue;
  }
  list.forEach((ex, i) => {
    examples++;
    const label = ex && (ex.t || ex.e || ex.c || ex.id || `#${i}`);
    if (validate(ex)) {
      console.log(`ok    ${f}  examples[${i}] (${label})`);
    } else {
      failures++;
      console.log(`FAIL  ${f}  examples[${i}] (${label})`);
      for (const err of validate.errors) {
        console.log(`        ${err.instancePath || "/"} ${err.message} ${JSON.stringify(err.params)}`);
      }
    }
  });
}

// Cross-checks that JSON Schema cannot express but the contract requires.
const registry = docs.get("mods.json").examples[0].mods;
const ids = Object.keys(registry);
const enumIds = docs.get("mods.json").definitions.mod_id.enum;
const hudEnum = docs.get("mods.json").definitions.hud_mod_id.enum;
const gpEnum = docs.get("mods.json").definitions.gameplay_mod_id.enum;

const check = (name, cond, detail = "") => {
  if (cond) console.log(`ok    cross-check  ${name}`);
  else { failures++; console.log(`FAIL  cross-check  ${name}  ${detail}`); }
};

// The count is derived, not typed in. It was `ids.length === 13` and the fourteenth mod
// tripped it — a hard-coded total in the gate that exists to catch hard-coded totals. What
// this check is actually for is that the registry and the `mod_id` enum agree *both ways*,
// which is a set comparison and has no number in it at all.
check(
  `registry and mod_id enum agree (${ids.length} mods)`,
  ids.length === enumIds.length &&
    enumIds.every((i) => ids.includes(i)) &&
    ids.every((i) => enumIds.includes(i)),
  `registry [${ids}] vs enum [${enumIds}]`,
);
check("every entry.id equals its key", ids.every((k) => registry[k].id === k));
check("hud_mod_id == entries with kind hud", JSON.stringify(ids.filter((k) => registry[k].kind === "hud")) === JSON.stringify(hudEnum));
check("gameplay_mod_id == entries with kind gameplay", JSON.stringify(ids.filter((k) => registry[k].kind === "gameplay")) === JSON.stringify(gpEnum));
check("every mod has a <id>_settings definition", ids.every((k) => docs.get("mods.json").definitions[`${k}_settings`]));
check("loadout.mods keys == mod_id enum", JSON.stringify(Object.keys(docs.get("loadout.json").definitions.mod_states.properties)) === JSON.stringify(enumIds));

// `category` is the Mods panel's filter taxonomy (frame 244:538). It is deliberately
// independent of `kind`, so the only thing to assert is that every entry carries one,
// that it is in the enum, and that each entry's narrowing agrees with the entry itself.
const categoryEnum = docs.get("mods.json").definitions.category.enum;
check(
  "every entry carries a category from the enum",
  ids.every((k) => categoryEnum.includes(registry[k].category)),
  ids.filter((k) => !categoryEnum.includes(registry[k].category)).join(", "),
);
check(
  "every <id>_entry narrows category to the registry's value",
  ids.every((k) => {
    const narrowed = docs.get("mods.json").definitions[`${k}_entry`].allOf?.[1]?.properties?.category?.const;
    return narrowed === registry[k].category;
  }),
  ids.filter((k) => docs.get("mods.json").definitions[`${k}_entry`].allOf?.[1]?.properties?.category?.const !== registry[k].category).join(", "),
);
check("every category has at least one mod", categoryEnum.every((c) => ids.some((k) => registry[k].category === c)));
check("labels are unique", new Set(ids.map((k) => registry[k].label)).size === ids.length);

// `default_placement` is per-`kind`, and the `<id>_entry` narrowings say so — a HUD entry
// `required`s it, a gameplay entry forbids it with `not`. Both are enforced by the schema, so
// the registry above cannot be wrong; what JSON Schema cannot say is that the *narrowings
// themselves* were generated for the right kind. A `<id>_entry` that forgot the constraint is
// a schema that accepts a HUD mod with nowhere to be, and nothing else here would notice.
// Walks all 14, not the 9 that have one, for the reason rendering-invariants §15 gives.
const entryOf = (k) => docs.get("mods.json").definitions[`${k}_entry`].allOf?.[1] ?? {};
const requiresPlacement = (k) => (entryOf(k).required ?? []).includes("default_placement");
const forbidsPlacement = (k) => (entryOf(k).not?.required ?? []).includes("default_placement");
check(
  "every <id>_entry requires default_placement iff the mod is kind hud",
  ids.every((k) =>
    registry[k].kind === "hud"
      ? requiresPlacement(k) && !forbidsPlacement(k)
      : forbidsPlacement(k) && !requiresPlacement(k),
  ),
  ids.filter((k) => (registry[k].kind === "hud") !== requiresPlacement(k)).join(", "),
);
check(
  `every hud entry carries a default_placement and no gameplay entry does (${hudEnum.length} placed)`,
  ids.every((k) => ("default_placement" in registry[k]) === (registry[k].kind === "hud")),
  ids.filter((k) => ("default_placement" in registry[k]) !== (registry[k].kind === "hud")).join(", "),
);
// The anchor set is written once, in loadout.json, and copied into mods.json by build.mjs.
// This is the assertion that the copy is still the original.
const anchorEnum = docs.get("loadout.json").definitions.anchor.enum;
check(
  "default_placement's anchors are loadout.json's anchors",
  JSON.stringify(docs.get("mods.json").definitions.hud_placement.properties.anchor.enum) ===
    JSON.stringify(anchorEnum),
);

// The two bridge channels that carry a whole loadout and the protocol's `init.loadouts`
// must agree, or the in-game library would be shaped differently depending on where it
// came from.
const initItems = docs.get("protocol.json").definitions.msg_init.properties.loadouts.items.$ref;
const loadoutsPayload = docs.get("bridge.json").definitions.loadouts_payload.items.$ref;
check(
  "init.loadouts and the loadouts bridge event carry the same thing",
  initItems === loadoutsPayload && initItems.endsWith("loadout.json#/definitions/loadout"),
  `${initItems} vs ${loadoutsPayload}`,
);

// Every registry `defaults` must validate against that mod's own settings sub-schema.
for (const k of ids) {
  const v = ajv.getSchema(`https://schema.void.dev/pvp/mods.json#/definitions/${k}_settings`);
  if (v(registry[k].defaults)) console.log(`ok    defaults  ${k}`);
  else { failures++; console.log(`FAIL  defaults  ${k}  ${ajv.errorsText(v.errors)}`); }
}

// ---------------------------------------------------------------------------------------------
// The cosmetics catalog. `cosmetics.schema.json` is a schema like the four above (its own
// `examples` must validate); `cosmetics.json` is the *data* it describes — the single source the
// Java catalog, the TS module and the Supabase seed are generated from — so it is validated as a
// document, then cross-checked for what JSON Schema cannot say.
{
  const schema = JSON.parse(readFileSync(join(here, "cosmetics.schema.json"), "utf8"));
  const catalog = JSON.parse(readFileSync(join(here, "cosmetics.json"), "utf8"));
  ajv.addSchema(schema, schema.$id);
  const v = ajv.getSchema(schema.$id);
  console.log(`ok    cosmetics.schema.json  compiles (${schema.$id})`);
  (schema.examples ?? []).forEach((ex, i) => {
    examples++;
    if (v(ex)) console.log(`ok    cosmetics.schema.json  examples[${i}]`);
    else { failures++; console.log(`FAIL  cosmetics.schema.json  examples[${i}]  ${ajv.errorsText(v.errors)}`); }
  });
  if (v(catalog)) console.log(`ok    cosmetics.json  validates (${catalog.items.length} items)`);
  else { failures++; console.log(`FAIL  cosmetics.json  ${ajv.errorsText(v.errors)}`); }

  const items = catalog.items ?? [];
  const ids = items.map((i) => i.id);
  const slots = schema.definitions.slot.enum;
  check("cosmetics: ids are unique", new Set(ids).size === ids.length,
    ids.filter((id, i) => ids.indexOf(id) !== i).join(", "));
  check("cosmetics: kind enum is the slot enum plus emote",
    JSON.stringify(schema.definitions.kind.enum) === JSON.stringify([...slots, "emote"]));
  check("cosmetics: free and grant items cost 0, paid items cost something",
    items.every((i) => (i.availability === "paid") === (i.price_cents > 0)),
    items.filter((i) => (i.availability === "paid") !== (i.price_cents > 0)).map((i) => i.id).join(", "));
  check("cosmetics: every tintable item has a default_tint and only those",
    items.every((i) => !!i.tintable === ("default_tint" in i)),
    items.filter((i) => !!i.tintable !== ("default_tint" in i)).map((i) => i.id).join(", "));
  check("cosmetics: `loop` only on emotes", items.every((i) => !("loop" in i) || i.kind === "emote"));
  check("cosmetics: preview style is the item's kind", items.every((i) => i.preview.style === i.kind),
    items.filter((i) => i.preview.style !== i.kind).map((i) => i.id).join(", "));
  check("cosmetics: emote previews carry rows, nothing else does",
    items.every((i) => (i.kind === "emote") === Array.isArray(i.preview.rows)));
  check("cosmetics: the free VOID player marker badge exists",
    items.some((i) => i.id === "void-badge-player" && i.kind === "badge" && i.availability === "free"));
  // The wire's slot enum is the catalog's: an equip for a slot the catalog does not have, or a
  // catalog slot the wire cannot equip, is a cosmetic nobody can wear.
  const wire = docs.get("protocol.json").definitions.cosmetic_slot?.enum;
  check("cosmetics: protocol.json cosmetic_slot is the catalog's slot enum",
    !wire || JSON.stringify(wire) === JSON.stringify(slots), `${wire} vs ${slots}`);
}

console.log(`\n${FILES.length} schemas, ${examples} examples, ${failures} failure(s)`);
process.exit(failures ? 1 : 0);
