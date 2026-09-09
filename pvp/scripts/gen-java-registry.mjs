#!/usr/bin/env node
/**
 * Generates `mod/src/main/java/dev/voidpvp/client/state/ModRegistry.java` from
 * `schema/mods.json` — the id set, the `kind` and `category` splits, the panel labels, the
 * factory defaults, the clamp descriptor of every setting of every mod, and the factory HUD
 * layout (`mod_entry.default_placement`).
 *
 * ## Why this exists
 *
 * The mod ships no config files (PVP_ARCHITECTURE.md §6.1) and cannot read `schema/` at
 * runtime, so the parts of the registry the game needs have to be *in the JAR*. Until now
 * they were in the JAR because someone typed them there: ~380 lines of hand-transcribed
 * table, one `Setting` descriptor per property per mod, with `ModRegistryTest` diffing it
 * against `schema/mods.json` on every build.
 *
 * That test is the tell. `ModRegistry.java`'s own header said it out loud — *"schema/mods.json
 * stays the source of truth: when it changes, this file changes with it and ModRegistryTest is
 * what catches the drift"* — which is a test compensating for hand-transcription.
 * `docs/mod-roster.md` §7 makes generating this file "Wave 0", ahead of the twenty mods of
 * waves 1–5, for the obvious reason: the transcription is ~12 lines per mod, every line of it
 * mechanical, and the two ways it fails are both quiet.
 *
 * - A **missing** setting is not a compile error. `clamp()` returns null for an unknown key, so
 *   the setting simply cannot be changed: the page draws the control, the player drags it, the
 *   value is refused and the slider springs back. That is what happened to `watermark.style`
 *   on the TypeScript side, and it is what `packages/protocol`'s `constraints.ts` was generated
 *   to stop.
 * - A **wrong bound** is worse, because nothing looks broken at all. A `maximum` typed as 5
 *   instead of 4 clamps a value the schema forbids straight into a stored loadout, which
 *   `void-loadout` then refuses to parse — a failure that surfaces two processes away from
 *   the typo.
 *
 * `@void/protocol` already generates its TypeScript from the same schema
 * (`packages/protocol/scripts/gen.mjs`), and `schema/mods.json` is itself generated from
 * `schema/mods/<id>.json` by `schema/build.mjs`. This script is the third consumer, and it
 * closes the last hand-written copy of the registry in the repo.
 *
 * ## What is generated, and what is emphatically not
 *
 * **Only the data** — the `static { ... }` block that fills `KINDS`, `CATEGORIES`, `LABELS`,
 * `SETTINGS` and `PLACEMENTS`. The machinery around it (`Setting`, `Type`, `Placement`,
 * `bool`/`number`/`enumOf`, the `clamp` state machine, the public accessors) is hand-written
 * Java, and it lives in the
 * `JAVA` template at the bottom of this file. Edit Java behaviour there and re-run; edit mod
 * data in `schema/mods/<id>.json` and re-run `schema/build.mjs` first.
 *
 * Keeping the machinery hand-written is deliberate: it is the part a human reads in a
 * debugger at 2am with the game running, and it is the part where the decisions are (why
 * `clamp` returns null rather than a default, why `INT` rounds, why a keybind is upper-cased
 * before it is validated). A generator that also emitted `clamp` would have to encode all of
 * that as string templates, and nobody would ever read it.
 *
 * ## The output is COMMITTED
 *
 * Same rule as `schema/build.mjs` and `packages/protocol/scripts/gen.mjs`, for the same
 * reason: the Fabric/Gradle build cannot run a Node script — there is no Node on the build
 * path, and `mod/build.gradle` has no business acquiring one — so a `ModRegistry.java` that
 * only exists after a build step is not a contract, it is a build artefact that half the
 * repo depends on. It is checked in, it is readable, and `--check` is what proves the
 * committed copy still matches its source.
 *
 * ## Usage
 *
 *   node scripts/gen-java-registry.mjs           # write ModRegistry.java
 *   node scripts/gen-java-registry.mjs --check   # exit 1 if it is stale. This is the CI gate.
 *
 * Run it after `node schema/build.mjs`, never before: this script reads the *generated*
 * `schema/mods.json`, not `schema/mods/`, because `mods.json` is where the shared HUD chrome
 * block has already been merged into each mod's settings and where every `$ref` can be
 * resolved locally.
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..');
const SCHEMA = join(ROOT, 'schema', 'mods.json');
const TARGET = join(ROOT, 'mod', 'src', 'main', 'java', 'dev', 'voidpvp', 'client', 'state', 'ModRegistry.java');

const schema = JSON.parse(readFileSync(SCHEMA, 'utf8'));
const DEFS = schema.definitions;
const REGISTRY = schema.examples?.[0]?.mods;
if (!REGISTRY) throw new Error('schema/mods.json has no examples[0].mods — run `node schema/build.mjs`');

/* -------------------------------------------------------------------------- */
/* Notes that belong to Java rather than to the schema                        */
/* -------------------------------------------------------------------------- */

/**
 * Reasoning the generated file should carry that has **no home in `schema/mods/<id>.json`**.
 *
 * Almost all of the old hand-written commentary in `ModRegistry.java` turned out to be
 * reasoning about the *mod*, and reasoning about the mod now lives in the schema — richer
 * there than it ever was here, because the schema is what the UI and Rust read too. Those
 * comments are not repeated in this table; they are emitted from each property's own
 * `description`, which is why the generated file is longer than the file it replaces rather
 * than shorter.
 *
 * What is left is the residue that is genuinely about *this side*: a Java class name the
 * schema can only describe in prose, or a decision about the Java type system. Anything you
 * are tempted to add here that a UI author or a Rust author would also want to know belongs
 * in `schema/mods/<id>.json` instead — that is the whole point of the split.
 *
 * Keys are `<mod>` for a note above the mod, `<mod>.<key>` for a note above one setting.
 */
const JAVA_NOTES = {
  'coordinates.decimals':
    'The "tick sensor" the description names is `sensor/TickCoalescer` on this side; that is the class to change if 2 dp ever stops being enough.',
  toggle_sprint:
    '`show_status` used to be here and is gone. A sprint indicator is still wanted, but as its own placeable HUD mod: a gameplay mod has no `hud[]` entry, so anything this drew would have been the only fixed, un-movable thing on the HUD — the one property the HUD editor exists to remove. Promoting this mod to `Kind.HUD` for one boolean is a structural change deserving its own decision, and `Kind` and `Category` are already independent, so the machinery supports it.',
  watermark:
    "The only HUD mod with no game field behind it — every other widget reads something (fps, ping, the armour slots) and this one is drawn from nothing but its own settings. Java's whole job for it is that the id exists, that its settings clamp and that its HUD placement round-trips; the overlay page draws it.",
};

/* -------------------------------------------------------------------------- */
/* Reading the schema                                                         */
/* -------------------------------------------------------------------------- */

/**
 * Resolve a settings property through its `$ref` into `mods.json`'s own definitions.
 *
 * Local keys win over the target's, which is how `keystrokes.opacity` carries
 * `{"$ref": "#/definitions/opacity", "default": 0.85}` — `schema/mods/<id>.json`'s
 * `shared_overrides`, flattened by `schema/build.mjs`. Same shape as `resolveProp` in
 * `packages/protocol/scripts/gen.mjs`; the hop limit is there so a cyclic `$ref` fails as a
 * bad descriptor rather than as a hang.
 */
function resolve(node) {
  let n = node;
  for (let hops = 0; hops < 4 && n && typeof n.$ref === 'string'; hops += 1) {
    const target = DEFS[n.$ref.replace('#/definitions/', '')];
    if (!target) throw new Error(`unresolvable $ref ${n.$ref}`);
    n = { ...target, ...n };
    delete n.$ref;
  }
  return n ?? {};
}

/** The `$ref` target name of a property, if it is a straight reference. Comment fodder only. */
const refName = (node) =>
  typeof node.$ref === 'string' ? node.$ref.replace('#/definitions/', '') : null;

/**
 * The `Setting` factory call for one resolved property — `bool(true)`, `number(0.25, 4, 1)`,
 * `enumOf("cross", "default", "cross", …)`. One arm per `ModRegistry.Type`.
 *
 * The discriminator between the two `Type`s that are otherwise the same Java shape —
 * `COLOR` and `KEYBIND`, both "a string matching a regex" — is the **`pattern` itself**,
 * compared against `mods.json#/definitions/hex_color` and `#/definitions/keybind`. Not the
 * `$ref` name, because a property that inlined the same pattern must map the same way and
 * would silently fall through; and emphatically not the property *name*, because the
 * keybind properties are already called two different things (`keystrokes.keybind` and
 * `zoom.key`) and `color` is a name a future enum could reasonably want.
 *
 * The other four fall out of `type` and the presence of `enum`, and the mapping is total
 * over today's schema: every one of the 13 mods' properties lands in exactly one arm. An
 * unrecognised shape throws rather than defaulting, because the failure mode of guessing is
 * a setting that silently cannot be changed in game.
 */
function descriptorOf(modId, key, node, factory) {
  const p = resolve(node);
  const where = `${modId}.${key}`;

  if (p.type === 'boolean') {
    if (typeof factory !== 'boolean') throw new Error(`${where}: boolean default expected`);
    return `bool(${factory})`;
  }
  if (Array.isArray(p.enum)) {
    if (!p.enum.includes(factory)) throw new Error(`${where}: default ${factory} is not in the enum`);
    return `enumOf(${[factory, ...p.enum].map(str).join(', ')})`;
  }
  if (p.type === 'integer' || p.type === 'number') {
    if (typeof p.minimum !== 'number' || typeof p.maximum !== 'number') {
      throw new Error(`${where}: numeric property without both minimum and maximum`);
    }
    if (typeof factory !== 'number') throw new Error(`${where}: numeric default expected`);
    const fn = p.type === 'integer' ? 'integer' : 'number';
    return `${fn}(${num(p.minimum)}, ${num(p.maximum)}, ${num(factory)})`;
  }
  if (p.type === 'string' && typeof p.pattern === 'string') {
    if (p.pattern === DEFS.hex_color.pattern) return `color(${str(factory)})`;
    if (p.pattern === DEFS.keybind.pattern) return `keybind(${str(factory)})`;
    throw new Error(`${where}: string pattern is neither hex_color's nor keybind's — teach descriptorOf about it`);
  }
  throw new Error(`${where}: no ModRegistry.Type for ${JSON.stringify(p.type)}`);
}

/* -------------------------------------------------------------------------- */
/* Formatting helpers                                                         */
/* -------------------------------------------------------------------------- */

/** A JSON string as a Java string literal. */
const str = (s) => JSON.stringify(String(s));

/** A JSON number as a Java numeric literal — `4`, not `4.0`, matching what was typed before. */
const num = (n) => {
  if (!Number.isFinite(n)) throw new Error(`non-finite number in the schema: ${n}`);
  return String(n);
};

const WORDS = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine',
  'ten', 'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen',
  'eighteen', 'nineteen', 'twenty'];
/**
 * `13` as `thirteen`, for the prose in the class javadoc.
 *
 * The old file said "the thirteen mods" in three places and would have gone on saying it
 * through wave 2's nine new mods. A number that appears in prose is still a number that can
 * drift, so it is derived like every other one.
 */
const word = (n) => (n < WORDS.length ? WORDS[n] : String(n));

/** Wrap prose into `//` comment lines at `indent`, never breaking a word. */
function comment(text, indent, width = 98) {
  const lines = [];
  let line = '';
  for (const w of String(text).split(/\s+/).filter(Boolean)) {
    const next = line ? `${line} ${w}` : w;
    if (line && indent.length + 3 + next.length > width) {
      lines.push(line);
      line = w;
    } else {
      line = next;
    }
  }
  if (line) lines.push(line);
  return lines.map((l) => `${indent}// ${l}`);
}

/* -------------------------------------------------------------------------- */
/* The shared block, and which mods deviate from it                           */
/* -------------------------------------------------------------------------- */

const ids = Object.keys(REGISTRY);
const settingsOf = (id) => {
  const def = DEFS[`${id}_settings`];
  if (!def) throw new Error(`mods.json has no ${id}_settings definition`);
  return def.properties;
};

/**
 * The keys every mod of a `kind` shares, minus `on`.
 *
 * For `hud` that is `scale`/`opacity` plus the four-property chrome block; for `gameplay` it
 * is empty, because `on` is the whole gameplay shared block.
 *
 * Derived by intersecting the settings key sets of every mod of that kind rather than by
 * reading `schema/mods/_shared.json`, because `mods.json` is what this script is allowed to
 * depend on and the intersection is the same set by construction: `build.mjs` writes the
 * shared block into every mod of the kind, first, in order.
 *
 * `on` is excluded deliberately. It is shared in *shape* but never in *value* — `build.mjs`
 * gives it a whole field of its own (`enabled`) precisely because "Hitboxes ships off, FPS
 * ships on" and there is no sensible shared default — so treating it as shared would print a
 * spurious "overrides the shared default" note on most of the registry and hide the one
 * genuinely per-mod boolean behind a group comment.
 *
 * The set is used only to decide which properties get a per-key comment: repeating the chrome
 * block's four paragraphs on all eight HUD mods is 32 paragraphs of identical text, which is
 * how a generated file stops being readable.
 */
function sharedKeys(kind) {
  const sets = ids.filter((id) => REGISTRY[id].kind === kind).map((id) => Object.keys(settingsOf(id)));
  if (sets.length === 0) return new Set();
  return new Set(sets[0].filter((k) => k !== 'on' && sets.every((s) => s.includes(k))));
}
const SHARED = { hud: sharedKeys('hud'), gameplay: sharedKeys('gameplay') };

/**
 * The factory value a shared property carries on *most* mods of its kind.
 *
 * `shared_overrides` is a `schema/mods/<id>.json` concept that `build.mjs` flattens away, so
 * by the time it reaches `mods.json` an override is indistinguishable from an ordinary
 * default except that it disagrees with its siblings. Finding it by disagreement is
 * therefore both the only way and the right way — and worth doing, because a dropped
 * override is a real bug that has already happened once: the last migration lost
 * `keystrokes.opacity` 0.85 and only structural diffing caught it. Marking the two survivors
 * (`keystrokes` 0.85, `watermark` 0.9) in the generated file makes the next one visible by
 * reading.
 */
function majorityDefault(kind, key) {
  const counts = new Map();
  for (const id of ids) {
    if (REGISTRY[id].kind !== kind) continue;
    const v = JSON.stringify(REGISTRY[id].defaults[key]);
    counts.set(v, (counts.get(v) ?? 0) + 1);
  }
  let best = null;
  let bestN = -1;
  for (const [v, n] of counts) if (n > bestN) { best = v; bestN = n; }
  return best;
}

/* -------------------------------------------------------------------------- */
/* Emit the static block                                                      */
/* -------------------------------------------------------------------------- */

const I = ' '.repeat(8);
const I2 = ' '.repeat(16);

function modBlock(id) {
  const row = REGISTRY[id];
  const props = settingsOf(id);
  const shared = SHARED[row.kind] ?? new Set();
  const out = [];

  out.push(`${I}// ${row.label} — kind ${row.kind}, ${row.category} tab, §11 ${row.hypixel_safe}.`);
  out.push(...comment(row.description, I));
  out.push(...comment(`Source: ${row.source}.`, I));
  if (JAVA_NOTES[id]) out.push(...comment(JAVA_NOTES[id], I));

  const head = `${I}mod(${str(id)}, Kind.${row.kind.toUpperCase()}, Category.${row.category.toUpperCase()}, ${str(row.label)},`;
  out.push(head);

  const entries = Object.entries(props);
  let firstShared = true;
  entries.forEach(([key, node], i) => {
    const factory = row.defaults[key];
    if (factory === undefined) {
      throw new Error(`${id}.${key} has no factory default in examples[0] — mods.json is inconsistent`);
    }
    const call = descriptorOf(id, key, node, factory);
    const tail = i === entries.length - 1 ? ');' : ',';

    if (key === 'on') {
      // Shared in shape, per-mod in value — see `sharedKeys`. Its own one-line description
      // says which mod it enables, which is the whole of what a reader wants here.
      out.push(...comment(resolve(node).description ?? 'Whether the mod is enabled.', I2));
    } else if (shared.has(key)) {
      if (firstShared) {
        // One line for the whole block rather than a paragraph per key: the descriptions are
        // long, identical across every mod of the kind, and they live in one place already.
        out.push(...comment(
          `The shared ${row.kind} block, schema/mods/_shared.json#/${row.kind} — the same keys, with the same meaning, on every ${row.kind} mod.`,
          I2,
        ));
        firstShared = false;
      }
      const majority = majorityDefault(row.kind, key);
      if (majority !== null && JSON.stringify(factory) !== majority) {
        out.push(...comment(
          `shared_overrides: ships ${JSON.stringify(factory)} where every other ${row.kind} mod ships ${majority}. ${resolve(node).description ?? ''}`,
          I2,
        ));
      }
    } else {
      const p = resolve(node);
      const ref = refName(node);
      if (p.description) out.push(...comment(p.description, I2));
      if (ref) out.push(...comment(`Type: mods.json#/definitions/${ref}.`, I2));
      if (JAVA_NOTES[`${id}.${key}`]) out.push(...comment(JAVA_NOTES[`${id}.${key}`], I2));
    }
    out.push(`${I2}${str(key)}, ${call}${tail}`);
  });

  return out.join('\n');
}

const hudIds = ids.filter((id) => REGISTRY[id].kind === 'hud');
const gameplayIds = ids.filter((id) => REGISTRY[id].kind === 'gameplay');

/**
 * The factory HUD layout — one `place(...)` per HUD mod, from `mod_entry.default_placement`.
 *
 * This table existed twice, by hand: `Loadout.DEFAULT_HUD` here and `DEFAULT_HUD` in
 * `packages/ingame/src/store/hud-geometry.ts`, kept in step by a test that read *this* Java
 * source and diffed it. That is the shape `docs/mod-roster.md` §9 names as the problem rather
 * than the fix — a test compensating for hand-transcription — and adding the fourteenth mod
 * meant editing both tables by hand, which is how it got flagged. Both are now generated from
 * the one place a mod is declared.
 *
 * Emitted as its own block rather than folded into each mod's, because it is a *layout*: the
 * rows are read against each other (the left column's 38-42 px rhythm is only visible if the
 * rows are adjacent), and that is exactly what the two tables it replaces looked like.
 */
function placementBlock() {
  const placement = DEFS.hud_placement;
  const out = [];
  out.push(...comment(placement.description, I));
  out.push(
    `${I}// Anchors: ${placement.properties.anchor.enum.join(' | ')}.`,
    `${I}// Read by Loadout.defaults(), which seeds a new loadout's hud[] from it.`,
  );
  for (const id of hudIds) {
    const place = REGISTRY[id].default_placement;
    if (!place) throw new Error(`${id} is kind hud with no default_placement — run schema/build.mjs`);
    // The per-mod argument, on the rows that have one. It is the `$comment` of the
    // `<id>_entry` narrowing's `default_placement` — prose about the schema rather than data
    // the game reads, so it never enters the registry row. Rows without one print nothing:
    // the block's own paragraph above already says what a placement is.
    const note = DEFS[`${id}_entry`]?.allOf?.[1]?.properties?.default_placement?.$comment;
    if (note) {
      out.push('', ...comment(`${REGISTRY[id].label}: ${note}`, I));
    }
    out.push(`${I}place(${str(id)}, ${str(place.anchor)}, ${num(place.dx)}, ${num(place.dy)});`);
  }
  return out.join('\n');
}

const blocks = [];
let lastKind = null;
for (const id of ids) {
  const kind = REGISTRY[id].kind;
  if (kind !== lastKind) {
    const heading = kind === 'hud'
      ? `HUD mods (${hudIds.length}) — they read game state and draw`
      : `Gameplay mods (${gameplayIds.length}) — they mutate a client-side option`;
    // Padded to a fixed width so the two rules line up in a diff and on screen.
    blocks.push(`${I}// --- ${heading} ${'-'.repeat(Math.max(3, 82 - heading.length))}`);
    lastKind = kind;
  }
  blocks.push(modBlock(id));
}
{
  const heading = `The factory HUD layout (${hudIds.length}) — where each widget starts`;
  blocks.push(`${I}// --- ${heading} ${'-'.repeat(Math.max(3, 82 - heading.length))}`);
  blocks.push(placementBlock());
}
const DATA = blocks.join('\n\n');

/* -------------------------------------------------------------------------- */
/* The Java template — HAND-WRITTEN. Edit the Java here.                      */
/* -------------------------------------------------------------------------- */

/**
 * Everything in `ModRegistry.java` that is not the table.
 *
 * This is ordinary Java that happens to live inside a template literal, and it is the file
 * you edit when you want `clamp` to behave differently. Three escaping rules, and the first
 * two are the only ones that have ever bitten: a literal backtick must be written `\``, a
 * literal `${` must be escaped, and a backslash in the Java source doubles here (there are
 * none today — the colour regex happens to need no escapes).
 */
const JAVA = (data, count) => `package dev.voidpvp.client.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The closed registry of the ${word(count)} mods.
 *
 * <p><b>GENERATED — do not edit.</b> The table in the static initialiser below is written by
 * {@code scripts/gen-java-registry.mjs} from {@code schema/mods.json} (registry document
 * {@code examples[0]} plus each mod's settings sub-schema). Change a mod in
 * {@code schema/mods/<id>.json}, re-run {@code node schema/build.mjs} and then
 * {@code node scripts/gen-java-registry.mjs}. Everything outside the static block — the
 * {@link Setting} descriptors, {@link #clamp} and the accessors — is hand-written, and it is
 * hand-written <i>in the generator</i>, in its {@code JAVA} template.</p>
 *
 * <p>The mod ships no config files (PVP_ARCHITECTURE.md §6.1) and cannot read {@code schema/}
 * at runtime, so the parts of the registry the game actually needs — the id set, the
 * {@code kind} split, the factory defaults, the clamp ranges and the factory HUD layout —
 * have to be in the JAR.
 * They are checked in rather than produced by the Gradle build because there is no Node on
 * the build path, and a contract that only exists after a build step is not a contract.
 * {@code node scripts/gen-java-registry.mjs --check} is the CI gate that this committed copy
 * still matches the schema; {@code ModRegistryTest} is the same check expressed against the
 * schema's meaning rather than against the file's bytes, so it still fails if someone edits
 * this file by hand and forgets to re-run anything.</p>
 */
public final class ModRegistry {

    /** Data direction of a mod, {@code mods.json#/definitions/kind}. */
    public enum Kind { HUD, GAMEPLAY }

    /**
     * Mods-panel filter taxonomy, {@code mods.json#/definitions/category}.
     *
     * <p>Deliberately not derivable from {@link Kind}: kind is a data-direction
     * split, category is the product one the panel tabs across. Crosshair is
     * {@code GAMEPLAY} but {@code VISUAL}; Zoom is {@code GAMEPLAY} but
     * {@code UTILITY}. The mod itself never filters anything — it carries the
     * value so {@code ModRegistryTest} can prove the generated table matches the
     * schema the UI reads.</p>
     */
    public enum Category { HUD, PVP, VISUAL, UTILITY }

    private ModRegistry() {
    }

    // -----------------------------------------------------------------
    // Setting descriptors — hand-written machinery
    // -----------------------------------------------------------------

    /**
     * The six shapes a setting can have, one per shape the schema can express.
     *
     * <p>{@code COLOR} and {@code KEYBIND} are both "a string matching a regex" and are told
     * apart in the generator by the {@code pattern} itself, compared against
     * {@code mods.json#/definitions/hex_color} and {@code #/definitions/keybind} — not by the
     * property's name, which is {@code keybind} on one mod and {@code key} on another.</p>
     */
    private enum Type { BOOL, INT, NUMBER, ENUM, COLOR, KEYBIND }

    private static final class Setting {
        final Type type;
        final double min;
        final double max;
        final Set<String> values;
        final JsonElement fallback;

        Setting(Type type, double min, double max, Set<String> values, JsonElement fallback) {
            this.type = type;
            this.min = min;
            this.max = max;
            this.values = values;
            this.fallback = fallback;
        }
    }

    /**
     * Where one HUD mod's widget starts, {@code mods.json#/definitions/hud_placement}.
     *
     * <p>Anchor plus offsets, never absolute pixels (PVP_ARCHITECTURE.md §8.1). Immutable and
     * public: {@link Loadout#defaults} copies it straight into a new loadout's {@code hud[]},
     * and the overlay's {@code Reset layout} restores the same numbers from its own generated
     * copy of this table.</p>
     */
    public static final class Placement {
        /** Screen anchor the offsets are measured from. */
        public final String anchor;
        /** Horizontal offset from the anchor, in the overlay's design-canvas pixels. */
        public final double dx;
        /** Vertical offset from the anchor, in the overlay's design-canvas pixels. */
        public final double dy;

        Placement(String anchor, double dx, double dy) {
            this.anchor = anchor;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private static final Map<String, Kind> KINDS = new LinkedHashMap<String, Kind>();
    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<String, Category>();
    private static final Map<String, String> LABELS = new LinkedHashMap<String, String>();
    private static final Map<String, Map<String, Setting>> SETTINGS =
            new LinkedHashMap<String, Map<String, Setting>>();
    private static final Map<String, Placement> PLACEMENTS =
            new LinkedHashMap<String, Placement>();

    private static final Pattern COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    private static Setting bool(boolean def) {
        return new Setting(Type.BOOL, 0, 0, null, new JsonPrimitive(Boolean.valueOf(def)));
    }

    private static Setting number(double min, double max, double def) {
        return new Setting(Type.NUMBER, min, max, null, new JsonPrimitive(Double.valueOf(def)));
    }

    private static Setting integer(double min, double max, long def) {
        return new Setting(Type.INT, min, max, null, new JsonPrimitive(Long.valueOf(def)));
    }

    private static Setting enumOf(String def, String... values) {
        return new Setting(Type.ENUM, 0, 0,
                new LinkedHashSet<String>(Arrays.asList(values)), new JsonPrimitive(def));
    }

    private static Setting color(String def) {
        return new Setting(Type.COLOR, 0, 0, null, new JsonPrimitive(def));
    }

    private static Setting keybind(String def) {
        return new Setting(Type.KEYBIND, 0, 0, null, new JsonPrimitive(def));
    }

    private static Map<String, Setting> mod(String id, Kind kind, Category category,
                                            String label, Object... pairs) {
        Map<String, Setting> map = new LinkedHashMap<String, Setting>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Setting) pairs[i + 1]);
        }
        KINDS.put(id, kind);
        CATEGORIES.put(id, category);
        LABELS.put(id, label);
        SETTINGS.put(id, Collections.unmodifiableMap(map));
        return map;
    }

    private static void place(String id, String anchor, double dx, double dy) {
        PLACEMENTS.put(id, new Placement(anchor, dx, dy));
    }

    // =================================================================
    // BEGIN GENERATED DATA — scripts/gen-java-registry.mjs, from schema/mods.json
    //
    // Every comment below this line is the corresponding \`description\` from the schema,
    // reproduced so that the reasoning for a setting is readable where the setting is. It is
    // not the place to add reasoning: write it in schema/mods/<id>.json, where Rust and the
    // UI get it too, and re-generate.
    // =================================================================

    static {
${data}
    }

    // =================================================================
    // END GENERATED DATA
    // =================================================================

    /** The ${word(count)} mod ids, in registry order. */
    public static List<String> modIds() {
        return Collections.unmodifiableList(new java.util.ArrayList<String>(KINDS.keySet()));
    }

    public static boolean isMod(String id) {
        return KINDS.containsKey(id);
    }

    public static Kind kind(String id) {
        return KINDS.get(id);
    }

    public static boolean isGameplay(String id) {
        return KINDS.get(id) == Kind.GAMEPLAY;
    }

    public static boolean isHud(String id) {
        return KINDS.get(id) == Kind.HUD;
    }

    /** Mods-panel filter category of a mod, or {@code null} for an unknown id. */
    public static Category category(String id) {
        return CATEGORIES.get(id);
    }

    /** Panel copy for a mod, e.g. {@code FPS display}; {@code null} for an unknown id. */
    public static String label(String id) {
        return LABELS.get(id);
    }

    /**
     * The factory HUD layout: every HUD mod's starting placement, in registry order.
     *
     * <p>The order is paint order for the {@code hud[]} array {@link Loadout#defaults} builds
     * from it, so it is the registry's and not a set's.</p>
     *
     * <p>Mods that ship off are in here too — a placement is where a widget <em>would</em> go,
     * not whether it is drawn. {@code on} decides that, in the page, in one place.</p>
     */
    public static Map<String, Placement> defaultHud() {
        return Collections.unmodifiableMap(PLACEMENTS);
    }

    /** Factory placement of one HUD mod, or {@code null} for a gameplay mod or unknown id. */
    public static Placement defaultPlacement(String id) {
        return PLACEMENTS.get(id);
    }

    /** Setting keys of a mod, in schema order. */
    public static Set<String> settingKeys(String modId) {
        Map<String, Setting> m = SETTINGS.get(modId);
        return m == null ? Collections.<String>emptySet() : m.keySet();
    }

    /** Factory defaults for one mod as a fresh mutable object. */
    public static JsonObject defaults(String modId) {
        JsonObject out = new JsonObject();
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null) {
            return out;
        }
        for (Map.Entry<String, Setting> e : m.entrySet()) {
            out.add(e.getKey(), e.getValue().fallback);
        }
        return out;
    }

    /** Factory default of a single setting, or {@code null} if unknown. */
    public static JsonElement defaultOf(String modId, String key) {
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null) {
            return null;
        }
        Setting s = m.get(key);
        return s == null ? null : s.fallback;
    }

    /**
     * Clamps an incoming setting value to what the schema allows.
     *
     * @return the value to store, or {@code null} when the mod, the key or the
     *         value's type make it unusable — the caller then keeps whatever it
     *         already had, which is what {@code setModSetting} returns
     *         ({@code bridge.json#/definitions/setModSetting_returns}).
     */
    public static JsonElement clamp(String modId, String key, JsonElement value) {
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null || value == null || value.isJsonNull()) {
            return null;
        }
        Setting s = m.get(key);
        if (s == null || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = value.getAsJsonPrimitive();
        switch (s.type) {
            case BOOL:
                if (p.isBoolean()) {
                    return p;
                }
                return null;
            case INT:
            case NUMBER: {
                if (!p.isNumber()) {
                    return null;
                }
                double d = p.getAsDouble();
                if (Double.isNaN(d)) {
                    return null;
                }
                d = Math.max(s.min, Math.min(s.max, d));
                if (s.type == Type.INT) {
                    return new JsonPrimitive(Long.valueOf(Math.round(d)));
                }
                return new JsonPrimitive(Double.valueOf(d));
            }
            case ENUM:
                if (p.isString() && s.values.contains(p.getAsString())) {
                    return p;
                }
                return null;
            case COLOR:
                if (p.isString() && COLOR.matcher(p.getAsString()).matches()) {
                    return p;
                }
                return null;
            case KEYBIND:
                if (p.isString()) {
                    String up = p.getAsString().toUpperCase(java.util.Locale.ROOT);
                    if (dev.voidpvp.client.input.KeyNames.isValidKeybind(up)) {
                        return new JsonPrimitive(up);
                    }
                }
                return null;
            default:
                return null;
        }
    }

    /** {@code JsonNull} for absent values, so callers never hand out Java null. */
    public static JsonElement nullValue() {
        return JsonNull.INSTANCE;
    }
}
`;

/* -------------------------------------------------------------------------- */
/* Write or check                                                             */
/* -------------------------------------------------------------------------- */

const content = JAVA(DATA, ids.length);
const rel = relative(ROOT, TARGET);

if (process.argv.includes('--check')) {
  let current = null;
  try {
    current = readFileSync(TARGET, 'utf8');
  } catch (err) {
    console.error(`missing: ${rel} — run \`node scripts/gen-java-registry.mjs\``);
    process.exit(1);
  }
  if (current !== content) {
    console.error(
      `stale: ${rel} does not match schema/mods.json — run \`node scripts/gen-java-registry.mjs\``,
    );
    process.exit(1);
  }
  console.log(`${rel} is up to date (${ids.length} mods).`);
} else {
  writeFileSync(TARGET, content);
  const settings = ids.reduce((n, id) => n + Object.keys(settingsOf(id)).length, 0);
  console.log(
    `generated ${rel} — ${ids.length} mods (${hudIds.length} hud, ${gameplayIds.length} gameplay), ` +
      `${settings} setting descriptors`,
  );
}
