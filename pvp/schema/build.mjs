#!/usr/bin/env node
/**
 * Assembles `mods.json` from `mods/` — one file per mod — and patches the parts of
 * `loadout.json` that are derived from the mod set.
 *
 * ## Why this exists
 *
 * A mod id used to be spelled out in **seven places inside `mods.json` alone**: the
 * `mods.required` array, `mods.properties`, the `mod_id` enum, either the `hud_mod_id` or the
 * `gameplay_mod_id` enum, its `<id>_settings` definition, its `<id>_entry` definition, and the
 * shipped registry in `examples[0]`. Two more in `loadout.json`: `mod_states.properties`, and
 * `hud_layout.maxItems`, which is a *count* of HUD mods written as a number.
 *
 * Nine edits per mod, all of them mechanical, all of them silent when wrong — a mod missing
 * from `hud_mod_id` is not a schema error, it is a mod that cannot be placed on the HUD, and
 * you find out in game. `docs/mod-roster.md` §9 costs the whole per-mod tax at ~25 files and
 * says the roster problem "is mostly volume, not difficulty". This file removes nine of those
 * twenty-five and, more to the point, removes the nine that fail quietly.
 *
 * **One file per mod, and everything else is derived.** `mods/<id>.json` is the only place a
 * mod is written down on this side of the repo.
 *
 * ## What is generated and what is not
 *
 * `mods.json` and the two derived blocks of `loadout.json` are **generated and committed**.
 * Committed, not built on demand, because they are the contract: `crates/void-loadout`
 * `include_str!`s `mods.json` into the binary, `packages/protocol` generates its TypeScript
 * from it, and `mod/.../ModRegistry.java` is transcribed from it by hand. None of those can
 * run a Node script, and a contract that only exists after a build step is not a contract.
 *
 * So the header on the generated file tells you not to edit it, `--check` proves in CI that
 * the committed output matches the sources, and `validate.mjs` still runs afterwards to prove
 * the result is a legal schema that says what it should.
 *
 * ## Usage
 *
 *   node build.mjs            # write mods.json + patch loadout.json
 *   node build.mjs --check    # exit 1 if either is out of date. This is the CI gate.
 */

import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const MODS_DIR = join(HERE, 'mods');

/**
 * Reading order of the registry — which is the order every consumer iterates, so it is a real
 * decision rather than whatever `readdir` returns.
 *
 * HUD mods first, then gameplay; within each, the order they were added. A mod is appended,
 * never inserted, so that `MOD_IDS` in `@void/protocol` and the `mod_id` enum stay stable for
 * anything that has serialised a position rather than an id.
 *
 * This is NOT the grid's reading order — that is a layout decision and it lives in
 * `packages/ingame/src/mods/index.ts`, where the layout is.
 */
const ORDER = [
  'fps', 'keystrokes', 'cps', 'ping', 'coordinates', 'armor_status', 'potion_effects',
  'watermark', 'toggle_sprint', 'fullbright', 'hitboxes', 'zoom', 'crosshair',
  // Wave 2 (docs/mod-roster.md §7). Appended, never inserted.
  'direction',
  // Wave 3 — six more HUD readouts, every one fed by a `tick_payload` field that already
  // existed. Appended, never inserted, for the same reason as `direction`.
  'combo', 'saturation', 'momentum', 'memory', 'server_address', 'item_counter',
  // Wave 4 — the four §3.2/§3.3 gameplay mods that change how the client *feels*, plus the one
  // HUD readout Wave 3 could not ship because no key could reach it (`bridge.json`'s
  // `modaction` is what opened that path). Appended, never inserted. Note that the "HUD first,
  // then gameplay" reading of the header stopped describing this list at `direction`: the two
  // narrowed enums are *filters* over this order, not slices of it, so a wave appends in
  // whatever order its mods were decided and neither enum notices.
  'stopwatch', 'fov', 'toggle_sneak', 'overlay',
  // Wave 5 — three `kind: gameplay` PvP mods, two of which are two roster rows each: `freelook`
  // absorbs Snaplook (§3.2 #9) and `damage_tint` absorbs Hurt cam control (§3.2 #8). Each mod's
  // own `$comment` argues its bundle and names what would split it. Appended, never inserted.
  'freelook', 'hit_color', 'damage_tint',
  // Wave 6 — the two mods the withdrawn `old_animations` turned into once its four settings were
  // read out of real 1.7.10 bytecode instead of guessed at. They are two and not one because the
  // animation half sends nothing and the input half changes what leaves the client, and
  // `hypixel_safe` is per mod: `old_animations` is `safe`, `old_input` is `grey`. Each file argues
  // its own half. Appended, never inserted.
  'old_animations', 'old_input',
  // Wave 7 — one HUD readout off counters the wire already carried. `hits.dealt` and
  // `hits.taken` have been on the tick payload since the combo counter shipped and only the
  // combo read them, which meant the client knew how a session was going and had nowhere to
  // say so. No sensor, no Java, no Rust: the whole mod is a schema entry, a widget, an art
  // module and a row in three shared tables, which is what §9's per-mod tax looks like once
  // the generators exist. Appended, never inserted.
  'hit_trade',
  // Wave 8 — two readouts the page can compute for itself. `clock` reads the machine's clock,
  // which no sensor could carry because the game does not know what time it is where you are;
  // `cps_graph` is the click edges the CPS counter already derives from, kept for longer and
  // summarised once a second. Both own a timer, which almost nothing here does —
  // `packages/ingame/src/hud/second-edge.ts` is the one place that is allowed to and carries the
  // budget. Appended, never inserted.
  'clock', 'cps_graph',
  // Wave 9 — the two the 1.8.9 bytecode was needed for. `scoreboard` wraps vanilla's own sidebar
  // draw; `reach` reports the distance of an attack that has already landed. Both were blocked on
  // reading the game rather than on time (`docs/adding-a-mod.md`, "Reading the game, instead of
  // remembering it"), and the injection points in each are cited by offset in their own files.
  // Appended, never inserted.
  'scoreboard', 'reach',
  // Wave 9, second half — the readout §3.1 has been asking for since Wave 2's note, and the
  // sensor it needed. `item_counter`'s new `source` is the other half of #5's "same engine as
  // #4 — do them together". Appended, never inserted.
  'potion_counter',
  // Wave 9, third: §4's ninth table-stakes row. Vanilla already draws the outline, so every
  // setting is one instruction in `WorldRenderer.drawBlockOutline` — the roster's M estimate was
  // for drawing one rather than for redirecting the one that exists. Appended, never inserted.
  'block_outline',
];

const read = (p) => JSON.parse(readFileSync(p, 'utf8'));

/* -------------------------------------------------------------------------- */
/* Load the sources                                                           */
/* -------------------------------------------------------------------------- */

const base = read(join(MODS_DIR, '_base.json'));
const shared = read(join(MODS_DIR, '_shared.json'));

const onDisk = readdirSync(MODS_DIR)
  .filter((f) => f.endsWith('.json') && !f.startsWith('_'))
  .map((f) => f.slice(0, -5));

// A mod file that is not in ORDER would be silently dropped from the registry, which is the
// exact class of quiet failure this script exists to remove. So it is an error, both ways.
const missing = onDisk.filter((id) => !ORDER.includes(id));
const phantom = ORDER.filter((id) => !onDisk.includes(id));
if (missing.length > 0) {
  throw new Error(`mods/${missing.join('.json, mods/')}.json exists but is not in ORDER (build.mjs)`);
}
if (phantom.length > 0) {
  throw new Error(`ORDER names ${phantom.join(', ')} but mods/<id>.json does not exist`);
}

const mods = ORDER.map((id) => {
  const mod = read(join(MODS_DIR, `${id}.json`));
  if (mod.id !== id) throw new Error(`mods/${id}.json declares id "${mod.id}"`);
  return mod;
});

const byKind = (kind) => mods.filter((m) => m.kind === kind);

/* -------------------------------------------------------------------------- */
/* loadout.json — read early, because two derivations run in both directions   */
/* -------------------------------------------------------------------------- */

/**
 * `loadout.json` is both an input and an output of this script.
 *
 * It is patched at the bottom (`mod_states.properties`, `hud_layout.maxItems`), and it is
 * *read* here for one thing: the anchor set. `default_placement.anchor` is the same anchor a
 * `hud_item` carries, and the enum is defined in `loadout.json#/definitions/anchor` — so it is
 * copied from there rather than restated in `mods/_base.json`. A second hand-written copy of
 * nine strings would be exactly the duplication `default_placement` exists to remove, and a
 * `$ref` the other way would invert the documents' dependency: the registry is the root.
 */
const loadoutPath = join(HERE, 'loadout.json');
const loadout = read(loadoutPath);
const ANCHORS = loadout.definitions?.anchor?.enum;
if (!Array.isArray(ANCHORS) || ANCHORS.length === 0) {
  throw new Error('loadout.json#/definitions/anchor has no enum — cannot type default_placement');
}

/**
 * The factory HUD placement of one mod, checked against its `kind`.
 *
 * A HUD mod must carry one and a gameplay mod must not: a gameplay mod mutates a client-side
 * option and draws nothing, so it has nowhere to be. Both halves are enforced in the emitted
 * schema too (`entrySchema` below), which is what makes a bad entry a *schema* error for every
 * consumer rather than something only this script would have noticed.
 *
 * `note` is prose, not data. It is lifted out of the value and onto the `<id>_entry`'s
 * `default_placement` description, so that the argument for a number travels with the schema —
 * and reaches the generated Java and TypeScript tables as a comment — without the registry
 * document growing a field that every language then has to model.
 */
function placementOf(mod) {
  const place = mod.default_placement;
  if (mod.kind === 'hud') {
    if (!place) {
      throw new Error(
        `mods/${mod.id}.json is kind "hud" and has no default_placement — every HUD mod owns a ` +
          'draggable item and the factory layout has to say where it starts',
      );
    }
  } else if (place) {
    throw new Error(
      `mods/${mod.id}.json is kind "${mod.kind}" and carries a default_placement — a gameplay ` +
        'mod draws nothing, so it has nowhere to be placed',
    );
  }
  if (!place) return null;
  if (!ANCHORS.includes(place.anchor)) {
    throw new Error(`mods/${mod.id}.json: anchor "${place.anchor}" is not one of ${ANCHORS.join(', ')}`);
  }
  if (typeof place.dx !== 'number' || typeof place.dy !== 'number') {
    throw new Error(`mods/${mod.id}.json: default_placement needs numeric dx and dy`);
  }
  const extra = Object.keys(place).filter((k) => !['anchor', 'dx', 'dy', 'note'].includes(k));
  if (extra.length > 0) {
    throw new Error(`mods/${mod.id}.json: default_placement has unknown key(s) ${extra.join(', ')}`);
  }
  return { value: { anchor: place.anchor, dx: place.dx, dy: place.dy }, note: place.note ?? null };
}

const placements = new Map(mods.map((m) => [m.id, placementOf(m)]));

/* -------------------------------------------------------------------------- */
/* Derivation                                                                 */
/* -------------------------------------------------------------------------- */

/** The mod's name in running prose: `the FPS display`, or `Keystrokes`. */
const named = (m) => (m.the ? `the ${m.label}` : m.label);

/** Fill `{noun}` / `{on_description}` in a shared property's description. */
function fill(text, mod) {
  return text
    .replaceAll('{noun}', mod.noun ?? named(mod))
    .replaceAll('{on_description}', mod.on_description);
}

/**
 * One mod's settings sub-schema: the shared block for its kind, then its own properties.
 *
 * Shared first so that `on`, `scale` and `opacity` are the first three keys of every HUD mod —
 * which is the order the settings page draws them in, and the order they were in when they
 * were written out by hand thirteen times.
 */
function settingsSchema(mod) {
  const sharedProps = shared[mod.kind];
  if (!sharedProps) throw new Error(`no shared block for kind "${mod.kind}" (${mod.id})`);

  const properties = {};
  for (const [key, def] of Object.entries(sharedProps)) {
    if (key.startsWith('$')) continue;
    const override = mod.shared_overrides?.[key] ?? {};
    properties[key] = { ...def, ...override, description: fill(override.description ?? def.description, mod) };
  }
  for (const [key, def] of Object.entries(mod.settings)) {
    if (key in properties) {
      throw new Error(`${mod.id}.json redeclares shared property "${key}" — put it in shared_overrides`);
    }
    properties[key] = def;
  }

  return {
    title: mod.settings_title,
    description: mod.settings_description,
    type: 'object',
    required: ['on'],
    additionalProperties: false,
    properties,
  };
}

/**
 * The `<id>_entry` definition — `mod_entry` narrowed to this mod's constants.
 *
 * It is also where `default_placement` is made per-`kind`. `mod_entry` can only say the
 * property is *allowed*, because the two kinds disagree about it; the narrowing is where a
 * per-kind constraint already lives, so a HUD mod `required`s it and a gameplay mod refuses it
 * with `not: { required: [...] }`. Both directions matter and both used to be unrepresentable:
 * a HUD mod with no placement is a widget that falls back to whatever the consumer guesses,
 * and a gameplay mod with one is a number nothing will ever read.
 */
function entrySchema(mod) {
  const CATEGORY_NOTE = {
    hud: 'the Mods panel tabs it under HUD (frame 244:538)',
    pvp: 'the Mods panel tabs it under PvP (frame 244:538)',
    visual: 'the Mods panel tabs it under Visual (frame 244:538)',
    utility: 'the Mods panel tabs it under Utility (frame 244:538)',
  };
  const placement = placements.get(mod.id);
  const narrowed = {
    properties: {
      id: { description: `Always \`${mod.id}\`.`, const: mod.id },
      icon: { description: `Always \`${mod.icon}\`.`, const: mod.icon },
      kind: { description: `Always \`${mod.kind}\`.`, const: mod.kind },
      category: {
        description: `Always \`${mod.category}\`; ${CATEGORY_NOTE[mod.category]}.`,
        const: mod.category,
      },
      hypixel_safe: { description: `Always \`${mod.hypixel_safe}\` (§11).`, const: mod.hypixel_safe },
      defaults: { description: `Factory ${mod.label} settings.`, $ref: `#/definitions/${mod.id}_settings` },
    },
  };
  if (placement) {
    narrowed.required = ['default_placement'];
    narrowed.properties.default_placement = {
      // `$comment` only where the number needs an argument — `mods/<id>.json`'s
      // `default_placement.note`. Two mods have one today and both are about the *column*
      // they join, which is a thing no single row can say. The generators print it above the
      // row; the ones with no `$comment` print nothing, because a paragraph restating the
      // field's own description on seven rows is how a generated table stops being read.
      ...(placement.note ? { $comment: placement.note } : {}),
      description: `Where ${named(mod)} starts on an untouched HUD. Required, because ${named(mod)} is \`kind: hud\`.`,
      $ref: '#/definitions/hud_placement',
    };
  } else {
    // A gameplay mod mutates a client-side option and draws nothing of its own, so there is no
    // widget to place. `not` rather than silence, because `mod_entry` lists the property and
    // `additionalProperties: false` would otherwise let one through.
    narrowed.not = {
      $comment: `${named(mod)} is \`kind: gameplay\`: it draws nothing, so it has nowhere to be placed.`,
      required: ['default_placement'],
    };
  }
  return {
    title: `${mod.label} entry`,
    description: `Registry entry for ${named(mod)}, narrowed to its constant classification.`,
    allOf: [{ $ref: '#/definitions/mod_entry' }, narrowed],
  };
}

/**
 * The shipped registry row, with `defaults` completed from the shared block.
 *
 * A shared property's factory value is its schema `default` — stated once, in `_shared.json` —
 * unless the mod overrode it. That is why `enabled` is a field of its own: `on` genuinely
 * differs per mod (Hitboxes ships off, FPS ships on) and there is no sensible shared default
 * for it.
 */
/**
 * A shared property's factory value.
 *
 * Stated on the property itself where the property is written out in full (`background`,
 * `border`, `text_shadow`, `padding`), and on the **`$ref` target** where it is a reference to
 * one of the reusable definitions (`scale`, `opacity`) — `#/definitions/scale` is where `1`
 * belongs, because every `scale` in the schema means the same thing and should not be able to
 * default differently in two places.
 */
function sharedDefault(key, def) {
  if ('default' in def) return def.default;
  const ref = typeof def.$ref === 'string' ? def.$ref.replace('#/definitions/', '') : null;
  return ref ? base.definitions[ref]?.default : undefined;
}

function registryRow(mod) {
  const sharedProps = shared[mod.kind];
  const defaults = { on: mod.enabled };
  for (const [key, def] of Object.entries(sharedProps)) {
    if (key.startsWith('$') || key === 'on') continue;
    const override = mod.shared_overrides?.[key];
    const value =
      override && 'default' in override ? override.default : sharedDefault(key, def);
    if (value === undefined) throw new Error(`shared property "${key}" has no default`);
    defaults[key] = value;
  }
  const placement = placements.get(mod.id);
  return {
    id: mod.id,
    kind: mod.kind,
    category: mod.category,
    hypixel_safe: mod.hypixel_safe,
    label: mod.label,
    icon: mod.icon,
    description: mod.description,
    source: mod.source,
    defaults: { ...defaults, ...mod.defaults },
    // HUD mods only, and last, so an entry reads as classification, then copy, then the two
    // factory blocks: what the mod is set to, and where it sits.
    ...(placement ? { default_placement: placement.value } : {}),
  };
}

/* -------------------------------------------------------------------------- */
/* Assemble mods.json                                                         */
/* -------------------------------------------------------------------------- */

const ids = mods.map((m) => m.id);
const hudIds = byKind('hud').map((m) => m.id);
const gameplayIds = byKind('gameplay').map((m) => m.id);

const definitions = {
  mod_id: {
    title: 'Mod id',
    description: `Closed enum of the ${ids.length} mods of §3, snake_case. Used as the key of \`loadout.mods\`, as the \`id\` argument of \`void.setModSetting\`, and as the id of a HUD item.`,
    type: 'string',
    enum: ids,
  },
  hud_mod_id: {
    title: 'HUD mod id',
    description: `The subset of mod ids whose \`kind\` is \`hud\`, i.e. the ${hudIds.length} mods that own a draggable HUD item. A mod may only appear in \`loadout.hud\` if it is listed here.`,
    type: 'string',
    enum: hudIds,
  },
  gameplay_mod_id: {
    title: 'Gameplay mod id',
    description: 'The subset of mod ids whose `kind` is `gameplay`, i.e. the mods an actuator Mixin reads every frame. These are the only ids accepted by `void.setGameplay`.',
    type: 'string',
    enum: gameplayIds,
  },
  ...base.definitions,
};
// The anchor set is `loadout.json`'s, copied rather than restated — see the note on ANCHORS.
definitions.hud_placement = {
  ...base.definitions.hud_placement,
  properties: {
    ...base.definitions.hud_placement.properties,
    anchor: {
      ...base.definitions.hud_placement.properties.anchor,
      enum: ANCHORS,
    },
  },
};
for (const mod of mods) definitions[`${mod.id}_settings`] = settingsSchema(mod);
for (const mod of mods) definitions[`${mod.id}_entry`] = entrySchema(mod);


const out = {
  $schema: base.$schema,
  $id: base.$id,
  title: base.title,
  description: base.description,
  $comment:
    'GENERATED by schema/build.mjs from schema/mods/*.json — do not edit by hand. ' +
    'A mod is added by writing one file, schema/mods/<id>.json, and re-running the script. ' +
    '`node build.mjs --check` is the CI gate that this file matches its sources.',
  type: base.type,
  required: base.required,
  additionalProperties: base.additionalProperties,
  properties: {
    version: base.version,
    mods: {
      title: base.mods_title,
      description: `Every mod VOID ships, keyed by its snake_case mod id. Closed set: all ${ids.length} keys are required and no others are permitted.`,
      type: 'object',
      required: ids,
      additionalProperties: false,
      properties: Object.fromEntries(
        mods.map((m) => [m.id, { description: `${m.label} registry entry.`, $ref: `#/definitions/${m.id}_entry` }]),
      ),
    },
  },
  definitions,
  examples: [
    {
      version: REGISTRY_VERSION(),
      mods: Object.fromEntries(mods.map((m) => [m.id, registryRow(m)])),
    },
  ],
};

/**
 * The registry document's own revision, read from `mods/_base.json`.
 *
 * Bumped by hand when a mod is added, removed or reclassified — `schema/README.md` says so and
 * it stays a human decision, because "did this change break a stored loadout" is not something
 * a diff can answer.
 */
function REGISTRY_VERSION() {
  if (typeof base.registry_version !== 'number') {
    throw new Error('mods/_base.json must carry a numeric `registry_version`');
  }
  return base.registry_version;
}

/* -------------------------------------------------------------------------- */
/* Patch loadout.json                                                         */
/* -------------------------------------------------------------------------- */

loadout.definitions.mod_states.description = `Enabled state plus settings for each mod, keyed by the mod ids of mods.json. Every key is optional: a mod omitted here falls back to its \`defaults\` in the registry, which is what keeps old loadouts valid when a mod is added. No key outside the closed ${ids.length} is permitted.`;
loadout.definitions.mod_states.properties = Object.fromEntries(
  mods.map((m) => [
    m.id,
    {
      description: `${m.label} state and settings.`,
      $ref: `https://schema.void.dev/pvp/mods.json#/definitions/${m.id}_settings`,
    },
  ]),
);
// The bound is one item per HUD mod, so it tracks `hud_mod_id` rather than being a number of
// its own — which is exactly why it is derived here instead of being typed in.
loadout.definitions.hud_layout.maxItems = hudIds.length;
loadout.definitions.hud_layout.description = `Ordered list of HUD item placements. Order is paint order, back to front. At most one entry per mod id — so at most ${hudIds.length}, one per \`hud_mod_id\`; that uniqueness is a \`void-loadout\` invariant rather than a schema constraint, since JSON Schema cannot express uniqueness by key.`;

/* -------------------------------------------------------------------------- */
/* Write or check                                                             */
/* -------------------------------------------------------------------------- */

const targets = [
  [join(HERE, 'mods.json'), JSON.stringify(out, null, 2) + '\n'],
  [loadoutPath, JSON.stringify(loadout, null, 2) + '\n'],
];

if (process.argv.includes('--check')) {
  let stale = 0;
  for (const [path, content] of targets) {
    if (readFileSync(path, 'utf8') !== content) {
      console.error(`stale: ${path} does not match schema/mods/ — run \`node schema/build.mjs\``);
      stale += 1;
    }
  }
  if (stale > 0) process.exit(1);
  console.log(`schema/mods.json and loadout.json are up to date (${ids.length} mods).`);
} else {
  for (const [path, content] of targets) writeFileSync(path, content);
  console.log(
    `built mods.json (${ids.length} mods: ${hudIds.length} hud, ${gameplayIds.length} gameplay) ` +
      `and patched loadout.json`,
  );
}
