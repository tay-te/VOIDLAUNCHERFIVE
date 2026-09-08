#!/usr/bin/env node
/**
 * Generate TypeScript types from pvp/schema/{mods,loadout,protocol,bridge}.json.
 *
 * The four schemas $ref each other by absolute id (https://schema.void.dev/pvp/<f>.json).
 * Those URLs are identifiers, not locations — nothing is ever fetched. This script
 * bundles all four documents into one self-contained schema (every $ref rewritten to a
 * local pointer) and compiles that in a single pass, so a definition shared between
 * documents — `keybind`, `hud_item`, `loadout` — produces exactly one TypeScript type
 * instead of one copy per document.
 *
 * Output is committed to src/generated/ so consumers never need to run this.
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { compile } from 'json-schema-to-typescript';

const here = path.dirname(fileURLToPath(import.meta.url));
const pkgRoot = path.resolve(here, '..');
const schemaDir = path.resolve(pkgRoot, '../../schema');
const outDir = path.resolve(pkgRoot, 'src/generated');

const DOCS = ['mods', 'loadout', 'protocol', 'bridge'];
/** TypeScript name given to each document's root schema. */
const ROOT_NAME = {
  mods: 'ModRegistryDocument',
  loadout: 'Loadout',
  protocol: 'ProtocolMessage',
  bridge: 'BridgeEnvelope',
};
const NS = { mods: 'Mods', loadout: 'Loadout', protocol: 'Protocol', bridge: 'Bridge' };
const EXT_RE = /^https:\/\/schema\.void\.dev\/pvp\/([a-z]+)\.json(?:#\/definitions\/(.+))?$/;

const source = {};
for (const name of DOCS) {
  source[name] = JSON.parse(await readFile(path.join(schemaDir, `${name}.json`), 'utf8'));
}

/**
 * Deep clone, dropping keys that are data rather than type information.
 * `examples` in particular is mangled by json-schema-to-typescript's draft-04
 * normaliser (it renames `id` keys to `$id`), and we never want it in the output.
 * A node carrying `$ref` keeps only `$ref`: in draft-07 every sibling is ignored, and
 * keeping a sibling `description` makes the compiler mint a redundant alias per use.
 */
function strip(node) {
  if (Array.isArray(node)) return node.map(strip);
  if (node && typeof node === 'object') {
    if (typeof node.$ref === 'string') return { $ref: node.$ref };
    const out = {};
    for (const [k, v] of Object.entries(node)) {
      if (k === 'examples' || k === '$id' || k === '$schema') continue;
      out[k] = strip(v);
    }
    return out;
  }
  return node;
}

const definitions = {};
const seen = new Set();
const keyFor = (doc, def) => `${NS[doc]}_${def}`;

function rewrite(node, doc) {
  if (Array.isArray(node)) return node.map((n) => rewrite(n, doc));
  if (!node || typeof node !== 'object') return node;
  if (typeof node.$ref === 'string') {
    const ext = EXT_RE.exec(node.$ref);
    if (ext) {
      const [, otherDoc, def] = ext;
      if (!def) throw new Error(`whole-document $ref is not supported: ${node.$ref}`);
      pull(otherDoc, def);
      return { $ref: `#/definitions/${keyFor(otherDoc, def)}` };
    }
    if (node.$ref.startsWith('#/definitions/')) {
      const def = node.$ref.slice('#/definitions/'.length);
      pull(doc, def);
      return { $ref: `#/definitions/${keyFor(doc, def)}` };
    }
    throw new Error(`unsupported $ref: ${node.$ref}`);
  }
  const out = {};
  for (const [k, v] of Object.entries(node)) out[k] = rewrite(v, doc);
  return out;
}

function pull(doc, def) {
  const key = keyFor(doc, def);
  if (seen.has(key)) return key;
  seen.add(key);
  const raw = source[doc]?.definitions?.[def];
  if (!raw) throw new Error(`unknown definition ${doc}#/definitions/${def}`);
  definitions[key] = {}; // placeholder so reference cycles terminate
  definitions[key] = rewrite(strip(raw), doc);
  return key;
}

// Pull every definition of every document, then add the four document roots.
for (const doc of DOCS) {
  for (const def of Object.keys(source[doc].definitions ?? {})) pull(doc, def);
}

const rootKeys = [];
for (const doc of DOCS) {
  const root = strip(source[doc]);
  delete root.definitions;
  const node = rewrite(root, doc);
  // loadout.json's root is a bare `$ref` into its own definitions. Point the bundle at
  // that definition rather than copying it, so `Loadout` is declared exactly once.
  if (typeof node.$ref === 'string') {
    const key = node.$ref.slice('#/definitions/'.length);
    definitions[key].title = ROOT_NAME[doc];
    rootKeys.push(key);
    continue;
  }
  node.title = ROOT_NAME[doc];
  const key = `root_${doc}`;
  definitions[key] = node;
  rootKeys.push(key);
}

const bundled = {
  title: 'VoidSchemaDocument',
  description:
    'Union of the four VOID PVP schema document roots. Generated; see pvp/schema/.',
  oneOf: rootKeys.map((k) => ({ $ref: `#/definitions/${k}` })),
  definitions,
};

const BANNER = `/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 *
 * Source: pvp/schema/mods.json, loadout.json, protocol.json, bridge.json
 * Generator: json-schema-to-typescript, via \`pnpm --filter @void/protocol gen\`.
 *
 * The four documents are compiled together as one bundle so that a definition shared
 * between them (keybind, hud_item, loadout, …) yields exactly one TypeScript type.
 */
`;

await mkdir(outDir, { recursive: true });
const ts = await compile(bundled, 'VoidSchemaDocument', {
  bannerComment: '',
  additionalProperties: false,
  declareExternallyReferenced: true,
  enableConstEnums: false,
  unknownAny: false,
  strictIndexSignatures: true,
  ignoreMinAndMaxItems: true,
  style: { singleQuote: true, semi: true, printWidth: 96 },
  $refOptions: { resolve: { http: false, file: false } },
});
await writeFile(path.join(outDir, 'schema.ts'), BANNER + ts, 'utf8');
process.stdout.write('generated src/generated/schema.ts\n');

// ---------------------------------------------------------------------------
// registry.ts — the shipped registry (mods.json examples[0]) as a typed constant.
// ---------------------------------------------------------------------------
const registry = source.mods.examples?.[0];
if (!registry) throw new Error('mods.json is missing examples[0], the shipped registry');
await writeFile(
  path.join(outDir, 'registry.ts'),
  `${BANNER}
import type { ModRegistryDocument } from './schema.js';

/**
 * The registry VOID actually ships — \`mods.json\` \`examples[0]\`, verbatim.
 * Prefer the helpers in \`src/mods.ts\` over reading this directly.
 */
export const MOD_REGISTRY_DOCUMENT = ${JSON.stringify(registry, null, 2)} as const satisfies ModRegistryDocument;
`,
  'utf8',
);
process.stdout.write('generated src/generated/registry.ts\n');

// ---------------------------------------------------------------------------
// icons.ts — the glyph each registry entry names, with its literal type intact.
//
// `mod_entry.icon` is the last piece of mod *identity* that lived in a hand-maintained
// table: `MOD_ICONS` in `packages/ui/src/components/Icon.tsx`, thirteen rows transcribed
// from nowhere. It sat in `@void/ui` rather than beside the art because *two* applications
// need it — the overlay's Mods list and quick palette, and the launcher's `ModSetup` and
// command palette — and neither can import the other. That is an argument for a contract,
// which is what the schema now carries; `packages/ingame/src/mods/types.ts` predicted this
// change and said so.
//
// Why a generated table rather than one line of `Object.fromEntries` over `MOD_REGISTRY` in
// `@void/ui`: **the literal types**. `MOD_REGISTRY` is typed `ModRegistry`, so its `icon` is
// `string`, and a table derived from it at module load types every entry `string` too. Its
// consumers pass `MOD_ICONS[id]` straight to `<Icon name>`, whose `IconName` is a closed
// union that **throws** on a name it cannot draw, so `string` there would move a compile
// error to a runtime one — precisely the silent-lookup shape of
// `design/rendering-invariants.md` §15. Emitting the object literally keeps each value at
// its own literal type, and `@void/ui` closes the loop with one
// `satisfies Record<ModId, IconName>`: a schema naming a glyph that has no drawing fails
// `pnpm typecheck` in the package that owns the drawing, without this package ever learning
// what an icon looks like.
//
// This is deliberately not folded into `registry.ts`, whose job is to be `examples[0]`
// verbatim, nor exposed as `ModEntry.icon` alone — that field exists and is `string`, which
// is right for the registry and not enough for the icon table.
// ---------------------------------------------------------------------------
{
  const ICON_RE = /^[a-z][a-z0-9-]*$/;
  const icons = {};
  const bad = [];
  for (const [id, entry] of Object.entries(registry.mods)) {
    if (typeof entry.icon !== 'string' || !ICON_RE.test(entry.icon)) {
      bad.push(`${id}: ${JSON.stringify(entry.icon)}`);
      continue;
    }
    icons[id] = entry.icon;
  }
  // `icon` is `required` in `mod_entry` and pattern-constrained, so this can only fire on a
  // registry that never went through `schema/build.mjs`. It fires loudly anyway: the failure
  // it replaces is a mod whose row draws an empty box, which no test of the schema can see.
  if (bad.length > 0) {
    throw new Error(`registry entries with a missing or malformed icon:\n  ${bad.join('\n  ')}`);
  }

  await writeFile(
    path.join(outDir, 'icons.ts'),
    `${BANNER}
import type { ModId } from './schema.js';

/**
 * The glyph each mod names — \`mod_entry.icon\`, lifted out of the shipped registry.
 *
 * Every value keeps its **literal** type, which is the whole reason this is a generated
 * table and not a fold over \`MOD_REGISTRY\`: \`@void/ui\` re-exports it as
 * \`MOD_ICONS\` under a \`satisfies Record<ModId, IconName>\`, so a schema that names a glyph
 * the icon set cannot draw is a type error there rather than an empty box in game.
 *
 * Not to be confused with \`ICON_NAMES\` in \`@void/ui\`, which is the set of glyphs that
 * *can* be drawn. This is the mapping from mod to one of them, and nothing in this package
 * knows the difference — by design: \`@void/protocol\` must not depend on \`@void/ui\`.
 *
 * Prefer \`@void/ui\`'s \`MOD_ICONS\` in an application. Reach for this only where \`@void/ui\`
 * is not a dependency, and note it is the *registry's* view: the overlay's \`?fake=\` padding
 * extends \`MOD_ICONS\`, never this.
 */
export const MOD_ICON_NAMES = ${JSON.stringify(icons, null, 2)} as const satisfies Record<ModId, string>;
`,
    'utf8',
  );
  process.stdout.write('generated src/generated/icons.ts\n');
}

// ---------------------------------------------------------------------------
// constraints.ts — the numeric ranges and enum tables of every settings property.
//
// These were transcribed by hand into `packages/ingame/src/registry.ts` as
// `SETTING_RANGES` and `SETTING_ENUMS`, with a comment saying so. Two tables copied off
// this schema is two tables that can drift from it, and both failure modes are silent:
// a missing range makes a slider clamp to the wrong end, and a missing enum table draws
// a property row with a label and *no control at all* — which shipped, on
// `watermark.style`. `docs/mod-roster.md` §9 names hand-transcription as the per-mod tax
// worth removing first, so it is removed here.
//
// A range is keyed by the property name alone (`scale`, `gamma`) because a name means the
// same thing everywhere in this schema — that is the invariant the shared `$ref`
// definitions already encode. An enum is keyed `<mod>.<key>`, because `mode` genuinely
// differs between `cps` and `toggle_sprint`, and `style` between `crosshair` and
// `watermark`.
// ---------------------------------------------------------------------------
{
  const ranges = {};
  const enums = {};
  const conflicts = [];
  const modIds = Object.keys(source.mods.examples[0].mods);

  // Resolve one property node through its `$ref` into mods.json's own definitions.
  const resolveProp = (node) => {
    let n = node;
    for (let hops = 0; hops < 4 && n && typeof n.$ref === 'string'; hops += 1) {
      n = { ...source.mods.definitions[n.$ref.replace('#/definitions/', '')], ...n };
      delete n.$ref;
    }
    return n ?? {};
  };

  for (const id of modIds) {
    const props = source.mods.definitions[`${id}_settings`]?.properties ?? {};
    for (const [key, node] of Object.entries(props)) {
      const p = resolveProp(node);
      if (Array.isArray(p.enum)) enums[`${id}.${key}`] = p.enum;
      if (typeof p.minimum === 'number' && typeof p.maximum === 'number') {
        const range = { min: p.minimum, max: p.maximum };
        const prior = ranges[key];
        if (prior && (prior.min !== range.min || prior.max !== range.max)) {
          conflicts.push(`${key}: ${JSON.stringify(prior)} vs ${JSON.stringify(range)} (${id})`);
        }
        ranges[key] = range;
      }
    }
  }
  // A name that means two different things in two mods would silently give one of them the
  // other's slider. Nothing in JSON Schema forbids it, so this does.
  if (conflicts.length > 0) {
    throw new Error(`settings property ranges disagree across mods:\n  ${conflicts.join('\n  ')}`);
  }

  await writeFile(
    path.join(outDir, 'constraints.ts'),
    `${BANNER}
/**
 * Numeric bounds per settings property name, from each \`<id>_settings\` sub-schema.
 *
 * Keyed by property name alone: a name means the same thing in every mod, and \`gen.mjs\`
 * throws if two mods ever disagree. The UI's step and unit are presentation and stay in
 * \`packages/ingame/src/registry.ts\`; what cannot be invented locally is the bound Java
 * will clamp to, and that is what this carries.
 */
export const SETTING_BOUNDS: Readonly<Record<string, { readonly min: number; readonly max: number }>> =
  ${JSON.stringify(ranges, null, 2)} as const;

/**
 * Enum options per \`<mod>.<key>\`, from each \`<id>_settings\` sub-schema.
 *
 * Keyed by mod *and* key, because \`mode\` differs between \`cps\` and \`toggle_sprint\` and
 * \`style\` between \`crosshair\` and \`watermark\`. A missing table draws a property row with
 * no control at all, so completeness here is what makes that unrepresentable.
 */
export const SETTING_OPTIONS: Readonly<Record<string, readonly string[]>> =
  ${JSON.stringify(enums, null, 2)} as const;
`,
    'utf8',
  );
  process.stdout.write('generated src/generated/constraints.ts\n');
}

// ---------------------------------------------------------------------------
// ids.ts — MOD_IDS / HUD_MOD_IDS / GAMEPLAY_MOD_IDS as literal tuples.
//
// These were hand-written arrays in `src/mods.ts` under `satisfies readonly ModId[]`,
// which sounds like a completeness check and is not one: `satisfies` proves every
// element IS a ModId, never that every ModId is an element. So a mod added to the
// schema stayed absent from the arrays with nothing failing to compile — and MOD_IDS
// is what `enabledMods`, `modsInCategory`, `hypixelReady`, `enabledModCount` and the
// overlay's own MOD_ORDER assertion all iterate. The mod would simply not exist to any
// of them.
//
// Found by adding the fourteenth mod and watching three tests fail on the count. Loud,
// but only because someone had written those tests; the types said nothing.
//
// Emitted as `as const` tuples rather than derived at runtime so that consumers keep the
// literal element types they had — `MOD_IDS[0]` is `'fps'`, not `string`.
// ---------------------------------------------------------------------------
{
  const reg = source.mods.examples[0].mods;
  const ids = Object.keys(reg);
  const of = (kind) => ids.filter((id) => reg[id].kind === kind);
  const tuple = (list) => `[\n${list.map((id) => `  '${id}',`).join('\n')}\n]`;

  await writeFile(
    path.join(outDir, 'ids.ts'),
    `${BANNER}
import type { GameplayModId, HUDModId, ModId } from './schema.js';

/** Every mod id, in registry order. */
export const MOD_IDS = ${tuple(ids)} as const satisfies readonly ModId[];

/** The mods that own a draggable HUD item, in registry order. */
export const HUD_MOD_IDS = ${tuple(of('hud'))} as const satisfies readonly HUDModId[];

/** The mods an actuator Mixin reads every frame, in registry order. */
export const GAMEPLAY_MOD_IDS = ${tuple(of('gameplay'))} as const satisfies readonly GameplayModId[];
`,
    'utf8',
  );
  process.stdout.write('generated src/generated/ids.ts\n');
}

// ---------------------------------------------------------------------------
// examples.ts — every `examples` entry of every document, for tests and fixtures.
// ---------------------------------------------------------------------------
const ex = (doc) => JSON.stringify(source[doc].examples ?? [], null, 2);
await writeFile(
  path.join(outDir, 'examples.ts'),
  `${BANNER}
import type { ModRegistryDocument, Loadout, ProtocolMessage, BridgeEnvelope } from './schema.js';

/** \`mods.json\` \`examples\`. */
export const MODS_EXAMPLES: ModRegistryDocument[] = ${ex('mods')};

/** \`loadout.json\` \`examples\`. */
export const LOADOUT_EXAMPLES: Loadout[] = ${ex('loadout')};

/** \`protocol.json\` \`examples\`. */
export const PROTOCOL_EXAMPLES: ProtocolMessage[] = ${ex('protocol')};

/** \`bridge.json\` \`examples\`. */
export const BRIDGE_EXAMPLES: BridgeEnvelope[] = ${ex('bridge')};

/** Every documented example, keyed by source document. */
export const SCHEMA_EXAMPLES = {
  mods: MODS_EXAMPLES,
  loadout: LOADOUT_EXAMPLES,
  protocol: PROTOCOL_EXAMPLES,
  bridge: BRIDGE_EXAMPLES,
} as const;
`,
  'utf8',
);
process.stdout.write('generated src/generated/examples.ts\n');

await writeFile(
  path.join(outDir, 'index.ts'),
  `${BANNER}
export type * from './schema.js';
export * from './registry.js';
export * from './examples.js';
export * from './constraints.js';
export * from './ids.js';
export * from './icons.js';
`,
  'utf8',
);
process.stdout.write('generated src/generated/index.ts\n');
