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

check("registry has all 12 mod_id values", ids.length === 12 && enumIds.every((i) => ids.includes(i)));
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

console.log(`\n${FILES.length} schemas, ${examples} examples, ${failures} failure(s)`);
process.exit(failures ? 1 : 0);
