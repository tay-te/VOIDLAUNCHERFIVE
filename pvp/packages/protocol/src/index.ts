/**
 * `@void/protocol` — the TypeScript face of `pvp/schema/*.json`.
 *
 * Nothing here talks to the network or the game. It is types, one reference shim, one
 * fake bridge and the mod registry, shared by `apps/desktop`, `packages/ingame` and
 * `packages/ui` so all three compile against the same contract.
 *
 * - **Generated types** — every definition in `mods.json`, `loadout.json`,
 *   `protocol.json` and `bridge.json`, compiled by `pnpm gen` into `src/generated/`.
 * - **{@link installVoidShim}** — the reference implementation of `void-shim.js`, the
 *   shim that builds `window.void` on top of the Java-installed `window.__void_native`.
 * - **{@link createFakeVoid}** — an in-memory `window.void` for browser development.
 * - **{@link MOD_REGISTRY}** and friends — the closed registry of the 13 mods.
 */

export type * from './generated/schema.js';
export { MOD_REGISTRY_DOCUMENT } from './generated/registry.js';
// The bounds and enum tables of every settings property, generated from the `<id>_settings`
// sub-schemas. The UI used to transcribe both by hand; see `generated/constraints.ts`.
export { SETTING_BOUNDS, SETTING_OPTIONS } from './generated/constraints.js';
// The glyph each registry entry names, generated with its literal type intact so that
// `@void/ui` can check the whole table against its closed `IconName` union in one
// `satisfies`. Applications should use `@void/ui`'s `MOD_ICONS`; this is the contract
// underneath it. See `generated/icons.ts`.
export { MOD_ICON_NAMES } from './generated/icons.js';
export {
  BRIDGE_EXAMPLES,
  LOADOUT_EXAMPLES,
  MODS_EXAMPLES,
  PROTOCOL_EXAMPLES,
  SCHEMA_EXAMPLES,
} from './generated/examples.js';

export * from './void-bridge.js';
export * from './fake-void.js';
export * from './mods.js';
