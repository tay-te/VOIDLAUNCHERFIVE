/**
 * The mod registry as a typed constant, plus the predicates every surface needs.
 *
 * The data comes from `pvp/schema/mods.json` `examples[0]` — the registry VOID actually
 * ships — via the generated `src/generated/registry.ts`, so a mod is added in exactly
 * one place (see `pvp/schema/README.md`).
 */

import { MOD_REGISTRY_DOCUMENT } from './generated/registry.js';
import type {
  GameplayModId,
  HUDModId,
  HypixelSafetyClass,
  Loadout,
  ModId,
  ModKind,
  ModStates,
} from './generated/schema.js';

/** Every setting object in the registry, narrowed per mod. */
export type ModSettingsFor<I extends ModId> = NonNullable<ModStates[I]>;

/** One registry row: identity, classification, copy and factory defaults. */
export interface ModEntry<I extends ModId = ModId> {
  /** The mod's snake_case id; equals the key it is stored under. */
  readonly id: I;
  /** Whether this mod draws (`hud`) or mutates a client-side option (`gameplay`). */
  readonly kind: ModKind;
  /** Anti-cheat class of §11. */
  readonly hypixel_safe: HypixelSafetyClass;
  /** Human-readable name, as it appears in the Mods panel. */
  readonly label: string;
  /** One-line explanation shown under the label. */
  readonly description: string;
  /** The 1.8.9 field or injection point the sensor reads / the actuator writes. */
  readonly source: string;
  /** Factory settings, used when a loadout omits this mod. */
  readonly defaults: ModSettingsFor<I>;
}

/** The whole registry, keyed by mod id. */
export type ModRegistry = { readonly [I in ModId]: ModEntry<I> };

/** Integer revision of the registry document. */
export const MOD_REGISTRY_VERSION: number = MOD_REGISTRY_DOCUMENT.version;

/**
 * The closed registry of the 12 mods (§3). Keys are stable; iterate {@link MOD_IDS}
 * when you need a deterministic order.
 */
export const MOD_REGISTRY = MOD_REGISTRY_DOCUMENT.mods as unknown as ModRegistry;

/** Every mod id, in registry order. */
export const MOD_IDS = [
  'fps',
  'keystrokes',
  'cps',
  'ping',
  'coordinates',
  'armor_status',
  'potion_effects',
  'toggle_sprint',
  'fullbright',
  'hitboxes',
  'zoom',
  'crosshair',
] as const satisfies readonly ModId[];

/** The mods that own a draggable HUD item, in registry order. */
export const HUD_MOD_IDS = [
  'fps',
  'keystrokes',
  'cps',
  'ping',
  'coordinates',
  'armor_status',
  'potion_effects',
] as const satisfies readonly HUDModId[];

/** The mods an actuator Mixin reads every frame, in registry order. */
export const GAMEPLAY_MOD_IDS = [
  'toggle_sprint',
  'fullbright',
  'hitboxes',
  'zoom',
  'crosshair',
] as const satisfies readonly GameplayModId[];

/** True when `id` is one of the 12 mod ids. */
export function isModId(id: string): id is ModId {
  return (MOD_IDS as readonly string[]).includes(id);
}

/**
 * True when the mod draws a HUD item — i.e. it may appear in `loadout.hud` and is a
 * legal first argument to `void.setHud`.
 */
export function isHudMod(id: string): id is HUDModId {
  return (HUD_MOD_IDS as readonly string[]).includes(id);
}

/**
 * True when the mod mutates a client-side option — i.e. it is a legal first argument to
 * `void.setGameplay`.
 */
export function isGameplayMod(id: string): id is GameplayModId {
  return (GAMEPLAY_MOD_IDS as readonly string[]).includes(id);
}

/** The registry row for a mod. */
export function getModEntry<I extends ModId>(id: I): ModEntry<I> {
  return MOD_REGISTRY[id];
}

/** The factory settings for a mod. */
export function getModDefaults<I extends ModId>(id: I): ModSettingsFor<I> {
  return MOD_REGISTRY[id].defaults;
}

/** Human-readable name for a mod, e.g. `Armor status`. */
export function getModLabel(id: ModId): string {
  return MOD_REGISTRY[id].label;
}

/**
 * The settings a loadout actually runs a mod with: its own state merged over the
 * registry defaults. A mod the loadout omits falls back to `defaults`, which is what
 * keeps old loadouts valid when a mod is added.
 */
export function resolveModSettings<I extends ModId>(
  loadout: Pick<Loadout, 'mods'>,
  id: I,
): ModSettingsFor<I> {
  const own = loadout.mods[id] as ModSettingsFor<I> | undefined;
  return { ...getModDefaults(id), ...(own ?? {}) };
}

/** Whether a mod is enabled in a loadout, falling back to the registry default. */
export function isModEnabled(loadout: Pick<Loadout, 'mods'>, id: ModId): boolean {
  return resolveModSettings(loadout, id).on === true;
}

/** Every mod enabled in a loadout, in registry order. */
export function enabledMods(loadout: Pick<Loadout, 'mods'>): ModId[] {
  return MOD_IDS.filter((id) => isModEnabled(loadout, id));
}

/**
 * Whether the loadout earns the **HYPIXEL-READY** badge.
 *
 * §11: the badge is shown only when *every enabled mod in the loadout* is classified
 * `safe`. A disabled `grey` mod — Fullbright or Hitboxes sitting in the loadout but
 * switched off — does not disqualify it.
 */
export function hypixelReady(loadout: Pick<Loadout, 'mods'>): boolean {
  return enabledMods(loadout).every((id) => MOD_REGISTRY[id].hypixel_safe === 'safe');
}

/** Every enabled mod that is *not* `safe`; empty exactly when {@link hypixelReady}. */
export function greyMods(loadout: Pick<Loadout, 'mods'>): ModId[] {
  return enabledMods(loadout).filter((id) => MOD_REGISTRY[id].hypixel_safe !== 'safe');
}

/** Count of enabled mods — the `24 mods on` line on a loadout card. */
export function enabledModCount(loadout: Pick<Loadout, 'mods'>): number {
  return enabledMods(loadout).length;
}
