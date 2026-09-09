/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 *
 * Source: pvp/schema/mods.json, loadout.json, protocol.json, bridge.json
 * Generator: json-schema-to-typescript, via `pnpm --filter @void/protocol gen`.
 *
 * The four documents are compiled together as one bundle so that a definition shared
 * between them (keybind, hud_item, loadout, …) yields exactly one TypeScript type.
 */
/**
 * Union of the four VOID PVP schema document roots. Generated; see pvp/schema/.
 */
export type VoidSchemaDocument =
  ModRegistryDocument | Loadout | ProtocolMessage | BridgeEnvelope;
/**
 * Integer revision of the registry document. Bumped whenever a mod is added, removed or reclassified. Independent of the wire protocol version `v` in protocol.json.
 */
export type RegistryVersion = number;
/**
 * Registry entry for the FPS display, narrowed to its constant classification.
 */
export type FPSDisplayEntry = RegistryEntry & {
  /**
   * Always `fps`.
   */
  id?: 'fps';
  /**
   * Always `gauge`.
   */
  icon?: 'gauge';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: FPSDisplaySettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Closed enum of the 36 mods of §3, snake_case. Used as the key of `loadout.mods`, as the `id` argument of `void.setModSetting`, and as the id of a HUD item.
 */
export type ModId =
  | 'fps'
  | 'keystrokes'
  | 'cps'
  | 'ping'
  | 'coordinates'
  | 'armor_status'
  | 'potion_effects'
  | 'watermark'
  | 'toggle_sprint'
  | 'fullbright'
  | 'hitboxes'
  | 'zoom'
  | 'crosshair'
  | 'direction'
  | 'combo'
  | 'saturation'
  | 'momentum'
  | 'memory'
  | 'server_address'
  | 'item_counter'
  | 'stopwatch'
  | 'fov'
  | 'toggle_sneak'
  | 'overlay'
  | 'freelook'
  | 'hit_color'
  | 'damage_tint'
  | 'old_animations'
  | 'old_input'
  | 'hit_trade'
  | 'clock'
  | 'cps_graph'
  | 'scoreboard'
  | 'reach'
  | 'potion_counter'
  | 'block_outline';
/**
 * Data direction of the mod, per §3. `hud` mods only read game state and draw; `gameplay` mods mutate a documented client-side option through an actuator Mixin.
 */
export type ModKind = 'hud' | 'gameplay';
/**
 * The product taxonomy the Mods panel filters across — the tabs `All / HUD / PvP / Visual / Utility` of Figma 244:538, and the tag printed on each tile. Distinct from `kind`, which is a data-direction split (does the mod draw, or does it mutate a client-side option): Crosshair is `kind: gameplay` but `category: visual`, and Zoom is `kind: gameplay` but `category: utility`. Carried here so no consumer has to hard-code the mapping.
 */
export type ModCategory = 'hud' | 'pvp' | 'visual' | 'utility';
/**
 * Anti-cheat posture of §11. `safe` mods are unambiguously allowed; `grey` mods change what the player can see and are tolerated but not endorsed. The HYPIXEL-READY badge is shown only when every enabled mod in the loadout is `safe`.
 */
export type HypixelSafetyClass = 'safe' | 'grey';
/**
 * Whether the mod is enabled. Present in every mod state inside a loadout, and in the registry `defaults` as the factory enabled state.
 */
export type Enabled = boolean;
/**
 * Multiplier applied to the mod's rendered size, on top of MC GUI scale x window DPI (§6.2). 1 is the design size.
 */
export type Scale = number;
/**
 * Alpha of the mod's rendered element, 0 fully transparent to 1 fully opaque.
 */
export type Opacity = number;
/**
 * sRGB colour as #RRGGBB or #RRGGBBAA. Lower case or upper case hex both accepted.
 */
export type Colour = string;
/**
 * Registry entry for Keystrokes, narrowed to its constant classification.
 */
export type KeystrokesEntry = RegistryEntry & {
  /**
   * Always `keystrokes`.
   */
  id?: 'keystrokes';
  /**
   * Always `keyboard`.
   */
  icon?: 'keyboard';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: KeystrokesSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * An LWJGL 2 key name in upper case as used by Minecraft 1.8.9 `Keyboard.getKeyName`, a mouse button as MOUSE0..MOUSE7, or NONE for unbound. Produced by `void.openKeybindCapture`.
 */
export type Keybind = string;
/**
 * One of the five unpressed-key swatches on the Mod settings frame, named by the design token it resolves to (`--bg-shell`, `--surface-2`, `--surface-3`, `--sky`, `--teal`). A name rather than a hex value so the choice survives a theme change.
 */
export type KeyColourSwatch = 'shell' | 'raised' | 'pill' | 'sky' | 'teal';
/**
 * One of the five pressed-key swatches on the Mod settings frame, named by the design token it resolves to (`--accent`, `--sky`, `--warn`, `--danger`, `--teal`). `accent` is the frame's default and follows the loadout accent.
 */
export type PressedColourSwatch = 'accent' | 'sky' | 'warn' | 'fear' | 'teal';
/**
 * Registry entry for the CPS counter, narrowed to its constant classification.
 */
export type CPSCounterEntry = RegistryEntry & {
  /**
   * Always `cps`.
   */
  id?: 'cps';
  /**
   * Always `cursor-click`.
   */
  icon?: 'cursor-click';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: CPSCounterSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Ping display, narrowed to its constant classification.
 */
export type PingDisplayEntry = RegistryEntry & {
  /**
   * Always `ping`.
   */
  id?: 'ping';
  /**
   * Always `wifi`.
   */
  icon?: 'wifi';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: PingDisplaySettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Coordinates, narrowed to its constant classification.
 */
export type CoordinatesEntry = RegistryEntry & {
  /**
   * Always `coordinates`.
   */
  id?: 'coordinates';
  /**
   * Always `compass`.
   */
  icon?: 'compass';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: CoordinatesSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Armor status, narrowed to its constant classification.
 */
export type ArmorStatusEntry = RegistryEntry & {
  /**
   * Always `armor_status`.
   */
  id?: 'armor_status';
  /**
   * Always `shield`.
   */
  icon?: 'shield';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ArmorStatusSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Potion effects, narrowed to its constant classification.
 */
export type PotionEffectsEntry = RegistryEntry & {
  /**
   * Always `potion_effects`.
   */
  id?: 'potion_effects';
  /**
   * Always `flask`.
   */
  icon?: 'flask';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: PotionEffectsSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Watermark, narrowed to its constant classification.
 */
export type WatermarkEntry = RegistryEntry & {
  /**
   * Always `watermark`.
   */
  id?: 'watermark';
  /**
   * Always `watermark`.
   */
  icon?: 'watermark';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `visual`; the Mods panel tabs it under Visual (frame 244:538).
   */
  category?: 'visual';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: VOIDWatermarkSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Toggle sprint, narrowed to its constant classification.
 */
export type ToggleSprintEntry = RegistryEntry & {
  /**
   * Always `toggle_sprint`.
   */
  id?: 'toggle_sprint';
  /**
   * Always `bolt`.
   */
  icon?: 'bolt';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ToggleSprintSettings;
};
/**
 * Registry entry for Fullbright, narrowed to its constant classification.
 */
export type FullbrightEntry = RegistryEntry & {
  /**
   * Always `fullbright`.
   */
  id?: 'fullbright';
  /**
   * Always `sun`.
   */
  icon?: 'sun';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `visual`; the Mods panel tabs it under Visual (frame 244:538).
   */
  category?: 'visual';
  /**
   * Always `grey` (§11).
   */
  hypixel_safe?: 'grey';
  defaults?: FullbrightSettings;
};
/**
 * Registry entry for Hitboxes, narrowed to its constant classification.
 */
export type HitboxesEntry = RegistryEntry & {
  /**
   * Always `hitboxes`.
   */
  id?: 'hitboxes';
  /**
   * Always `cube`.
   */
  icon?: 'cube';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `grey` (§11).
   */
  hypixel_safe?: 'grey';
  defaults?: HitboxesSettings;
};
/**
 * Registry entry for Zoom, narrowed to its constant classification.
 */
export type ZoomEntry = RegistryEntry & {
  /**
   * Always `zoom`.
   */
  id?: 'zoom';
  /**
   * Always `zoom`.
   */
  icon?: 'zoom';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `utility`; the Mods panel tabs it under Utility (frame 244:538).
   */
  category?: 'utility';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ZoomSettings;
};
/**
 * Registry entry for Crosshair, narrowed to its constant classification.
 */
export type CrosshairEntry = RegistryEntry & {
  /**
   * Always `crosshair`.
   */
  id?: 'crosshair';
  /**
   * Always `crosshair`.
   */
  icon?: 'crosshair';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `visual`; the Mods panel tabs it under Visual (frame 244:538).
   */
  category?: 'visual';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: CrosshairSettings;
};
/**
 * Registry entry for Direction, narrowed to its constant classification.
 */
export type DirectionEntry = RegistryEntry & {
  /**
   * Always `direction`.
   */
  id?: 'direction';
  /**
   * Always `compass`.
   */
  icon?: 'compass';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: DirectionSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Combo counter, narrowed to its constant classification.
 */
export type ComboCounterEntry = RegistryEntry & {
  /**
   * Always `combo`.
   */
  id?: 'combo';
  /**
   * Always `sword`.
   */
  icon?: 'sword';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ComboCounterSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Saturation, narrowed to its constant classification.
 */
export type SaturationEntry = RegistryEntry & {
  /**
   * Always `saturation`.
   */
  id?: 'saturation';
  /**
   * Always `heart`.
   */
  icon?: 'heart';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: SaturationSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Momentum, narrowed to its constant classification.
 */
export type MomentumEntry = RegistryEntry & {
  /**
   * Always `momentum`.
   */
  id?: 'momentum';
  /**
   * Always `move`.
   */
  icon?: 'move';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: MomentumSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Memory, narrowed to its constant classification.
 */
export type MemoryEntry = RegistryEntry & {
  /**
   * Always `memory`.
   */
  id?: 'memory';
  /**
   * Always `layers`.
   */
  icon?: 'layers';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: MemorySettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Server address, narrowed to its constant classification.
 */
export type ServerAddressEntry = RegistryEntry & {
  /**
   * Always `server_address`.
   */
  id?: 'server_address';
  /**
   * Always `wifi`.
   */
  icon?: 'wifi';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `utility`; the Mods panel tabs it under Utility (frame 244:538).
   */
  category?: 'utility';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ServerAddressSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Item counter, narrowed to its constant classification.
 */
export type ItemCounterEntry = RegistryEntry & {
  /**
   * Always `item_counter`.
   */
  id?: 'item_counter';
  /**
   * Always `box`.
   */
  icon?: 'box';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ItemCounterSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Stopwatch, narrowed to its constant classification.
 */
export type StopwatchEntry = RegistryEntry & {
  /**
   * Always `stopwatch`.
   */
  id?: 'stopwatch';
  /**
   * Always `clock`.
   */
  icon?: 'clock';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `utility`; the Mods panel tabs it under Utility (frame 244:538).
   */
  category?: 'utility';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: StopwatchSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the FOV changer, narrowed to its constant classification.
 */
export type FOVChangerEntry = RegistryEntry & {
  /**
   * Always `fov`.
   */
  id?: 'fov';
  /**
   * Always `eye`.
   */
  icon?: 'eye';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: FOVChangerSettings;
};
/**
 * Registry entry for Toggle sneak, narrowed to its constant classification.
 */
export type ToggleSneakEntry = RegistryEntry & {
  /**
   * Always `toggle_sneak`.
   */
  id?: 'toggle_sneak';
  /**
   * Always `chevron-down`.
   */
  icon?: 'chevron-down';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ToggleSneakSettings;
};
/**
 * Registry entry for Overlay, narrowed to its constant classification.
 */
export type OverlayEntry = RegistryEntry & {
  /**
   * Always `overlay`.
   */
  id?: 'overlay';
  /**
   * Always `sparkle`.
   */
  icon?: 'sparkle';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `visual`; the Mods panel tabs it under Visual (frame 244:538).
   */
  category?: 'visual';
  /**
   * Always `grey` (§11).
   */
  hypixel_safe?: 'grey';
  defaults?: OverlaySettings;
};
/**
 * Registry entry for Freelook, narrowed to its constant classification.
 */
export type FreelookEntry = RegistryEntry & {
  /**
   * Always `freelook`.
   */
  id?: 'freelook';
  /**
   * Always `orbit`.
   */
  icon?: 'orbit';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: FreelookSettings;
};
/**
 * Registry entry for Hit colour, narrowed to its constant classification.
 */
export type HitColourEntry = RegistryEntry & {
  /**
   * Always `hit_color`.
   */
  id?: 'hit_color';
  /**
   * Always `droplet`.
   */
  icon?: 'droplet';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: HitColourSettings;
};
/**
 * sRGB colour as #RRGGBB, with no alpha byte. The narrow half of `hex_color`, for a mod where something else owns the transparency: `hit_color.intensity` is defined as a fraction of vanilla's own hurt-overlay alpha, and a second way to set alpha would be two owners of one value — the shape `toggle_sneak` was split out of `toggle_sprint` to remove. A separate definition rather than a `pattern` written beside `$ref: hex_color`, because draft-07 ignores keywords sitting next to a `$ref`: that spelling validates nothing while reading as though it does. Both generators resolve string types by definition name, so a named definition is also the only shape they can be taught.
 */
export type ColourOpaque = string;
/**
 * Registry entry for Damage tint, narrowed to its constant classification.
 */
export type DamageTintEntry = RegistryEntry & {
  /**
   * Always `damage_tint`.
   */
  id?: 'damage_tint';
  /**
   * Always `heart-pulse`.
   */
  icon?: 'heart-pulse';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: DamageTintSettings;
};
/**
 * Registry entry for Old animations, narrowed to its constant classification.
 */
export type OldAnimationsEntry = RegistryEntry & {
  /**
   * Always `old_animations`.
   */
  id?: 'old_animations';
  /**
   * Always `reset`.
   */
  icon?: 'reset';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: OldAnimationsSettings;
};
/**
 * Registry entry for Old input, narrowed to its constant classification.
 */
export type OldInputEntry = RegistryEntry & {
  /**
   * Always `old_input`.
   */
  id?: 'old_input';
  /**
   * Always `tap`.
   */
  icon?: 'tap';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `grey` (§11).
   */
  hypixel_safe?: 'grey';
  defaults?: OldInputSettings;
};
/**
 * Registry entry for the Trade counter, narrowed to its constant classification.
 */
export type TradeCounterEntry = RegistryEntry & {
  /**
   * Always `hit_trade`.
   */
  id?: 'hit_trade';
  /**
   * Always `sword`.
   */
  icon?: 'sword';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: TradeCounterSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Clock, narrowed to its constant classification.
 */
export type ClockEntry = RegistryEntry & {
  /**
   * Always `clock`.
   */
  id?: 'clock';
  /**
   * Always `clock`.
   */
  icon?: 'clock';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `utility`; the Mods panel tabs it under Utility (frame 244:538).
   */
  category?: 'utility';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ClockSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the CPS graph, narrowed to its constant classification.
 */
export type CPSGraphEntry = RegistryEntry & {
  /**
   * Always `cps_graph`.
   */
  id?: 'cps_graph';
  /**
   * Always `cursor-click`.
   */
  icon?: 'cursor-click';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: CPSGraphSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Scoreboard, narrowed to its constant classification.
 */
export type ScoreboardEntry = RegistryEntry & {
  /**
   * Always `scoreboard`.
   */
  id?: 'scoreboard';
  /**
   * Always `users`.
   */
  icon?: 'users';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: ScoreboardSettings;
};
/**
 * Registry entry for the Reach display, narrowed to its constant classification.
 */
export type ReachDisplayEntry = RegistryEntry & {
  /**
   * Always `reach`.
   */
  id?: 'reach';
  /**
   * Always `sword`.
   */
  icon?: 'sword';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `grey` (§11).
   */
  hypixel_safe?: 'grey';
  defaults?: ReachSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Potion counter, narrowed to its constant classification.
 */
export type PotionCounterEntry = RegistryEntry & {
  /**
   * Always `potion_counter`.
   */
  id?: 'potion_counter';
  /**
   * Always `flask`.
   */
  icon?: 'flask';
  /**
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `pvp`; the Mods panel tabs it under PvP (frame 244:538).
   */
  category?: 'pvp';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: PotionCounterSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for the Block outline, narrowed to its constant classification.
 */
export type BlockOutlineEntry = RegistryEntry & {
  /**
   * Always `block_outline`.
   */
  id?: 'block_outline';
  /**
   * Always `cube`.
   */
  icon?: 'cube';
  /**
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `visual`; the Mods panel tabs it under Visual (frame 244:538).
   */
  category?: 'visual';
  /**
   * Always `safe` (§11).
   */
  hypixel_safe?: 'safe';
  defaults?: BlockOutlineSettings;
};
/**
 * Lower-case slug: letters, digits and single hyphens, e.g. `sword-pvp`. Unique within a user's library.
 */
export type LoadoutId = string;
/**
 * The subset of mod ids whose `kind` is `hud`, i.e. the 21 mods that own a draggable HUD item. A mod may only appear in `loadout.hud` if it is listed here.
 */
export type HUDModId =
  | 'fps'
  | 'keystrokes'
  | 'cps'
  | 'ping'
  | 'coordinates'
  | 'armor_status'
  | 'potion_effects'
  | 'watermark'
  | 'direction'
  | 'combo'
  | 'saturation'
  | 'momentum'
  | 'memory'
  | 'server_address'
  | 'item_counter'
  | 'stopwatch'
  | 'hit_trade'
  | 'clock'
  | 'cps_graph'
  | 'reach'
  | 'potion_counter';
/**
 * The screen edge or corner a HUD item is pinned to. `dx`/`dy` are measured from that anchor, so the layout survives GUI-scale, resolution and fullscreen changes (§8.1).
 */
export type HUDAnchor =
  | 'top-left'
  | 'top'
  | 'top-right'
  | 'left'
  | 'center'
  | 'right'
  | 'bottom-left'
  | 'bottom'
  | 'bottom-right';
/**
 * Ordered list of HUD item placements. Order is paint order, back to front. At most one entry per mod id — so at most 21, one per `hud_mod_id`; that uniqueness is a `void-loadout` invariant rather than a schema constraint, since JSON Schema cannot express uniqueness by key.
 *
 * @maxItems 21
 */
export type HUDLayout = HUDItem[];
/**
 * Every message on the localhost WebSocket between the Rust launcher (server, `void-bridge`) and the Java mod (client, `net/`), as specified in PVP_ARCHITECTURE.md §7. One JSON object per WS text frame, discriminated on `t`. The link carries state and telemetry summaries only, never per-frame data (§2): telemetry that is drawn never leaves the JVM. Forward compatibility rule of §7: a receiver ignores an unknown `t` and ignores unknown fields, which is why every message here sets additionalProperties true. `v` is the protocol version and is carried only on the two handshake messages, `hello` and `init`; a mismatch makes the launcher refuse to launch and prompt for an update, since mod and launcher ship together. Connection parameters reach the JVM as -Dvoid.port and -Dvoid.token (§6.9).
 */
export type ProtocolMessage = JavaToRust | RustToJava;
/**
 * The seven messages the mod sends to the launcher. Validate an inbound frame in `void-bridge` against this.
 */
export type JavaToRust =
  | HelloJavaToRust
  | StateJavaToRust
  | HudJavaToRust
  | GlobalsJavaToRust
  | SessionJavaToRust
  | ServerJavaToRust
  | HotkeyJavaToRust;
/**
 * Version of this message set. Bumped on any breaking change. Carried on `hello` and `init` only; both sides compare and refuse to proceed on a mismatch (§7). **2** since `init.loadouts` changed from `loadout_summary` to whole loadouts: a v1 mod would read a v2 `init` without complaint, but a v2 mod against a v1 launcher would receive summaries, materialise every mod at its factory default, and silently apply the wrong loadout on a switch. That is exactly the mixed-halves case `v` exists to refuse.
 */
export type ProtocolVersion = 2;
/**
 * Closed set of the global hotkeys the mod reports. `loadout.next` is the L-key cycle of §6.3, already applied when this is sent. `overlay` is the menu key (Right Shift by default) opening or closing VoidMenuScreen; the launcher uses it only for presence, since the menu is entirely in-game.
 */
export type HotkeyId = 'loadout.next' | 'overlay';
/**
 * The three messages the launcher sends to the mod. Validate an inbound frame in the mod's `net/` package against this.
 */
export type RustToJava = InitRustToJava | LoadoutRustToJava | SettingsRustToJava;
/**
 * The single `window.void` object that joins the Java mod and the in-game React app, as specified in PVP_ARCHITECTURE.md §6.5. Java to JS is push, delivered through `void.on(event, handler)` and batched once per frame; JS to Java is a direct call. The bridge is in-process (Ultralight lives inside the JVM, §6.2), so calls are synchronous and return real applied state -- there is no ack, no optimistic UI and no request id. This file therefore does not describe a transport; it describes, per event, the shape of the payload handed to the handler, and per call, the positional `params` and the `returns` value. An instance of this schema is one enveloped event, call or call result, which is exactly the form the browser `?debug` harness of §9 records and replays against a fake window.void. Payloads are additionalProperties:false: unlike protocol.json this surface is not forward-compatible across versions, because the mod JAR embeds the UI bundle and the two always ship as one binary. The channel list below is the whole contract and both shims close over it: `on` returns a no-op subscription for a name it does not know and `__emit` drops an envelope whose channel has no list, so a channel Java pushes and this file does not declare is lost in total silence. That is how `session` went missing between Java and the page the first time, and why it is written down here now.
 */
export type BridgeEnvelope = Event | Call | CallResult;
/**
 * One push from Java to JS, enveloped as {e, payload}. In the real bridge the envelope does not exist on the wire: `payload` is the single argument the `void.on(e, handler)` handler receives.
 */
export type Event =
  | KeysEvent
  | TickEvent
  | ServerEvent
  | LoadoutEvent
  | LoadoutsEvent
  | SettingEvent
  | MenuEvent
  | SessionEvent
  | SettingsEvent
  | ModactionEvent;
/**
 * 0 released, 1 pressed.
 */
export type KeyState = 0 | 1;
/**
 * Every loadout in the library, in full and in library order, including the active one — exactly what Rust sent in `init.loadouts`. Pushed once on `init` and again whenever the library changes, so the in-game Loadouts frame (Figma 244:1130) can list and compare loadouts, and the quick palette can offer `Turn on in <other> loadout`, without a bridge accessor and without waiting to observe a `loadout` event per entry. Whole-state replacement, like `loadout`.
 *
 * @maxItems 128
 */
export type LoadoutsPayload = Loadout[];
/**
 * True when VoidMenuScreen opened, false when it closed. The React app draws the HUD layer and the menu layer into the same Ultralight view (§6.2); this flag is how it decides which is visible.
 */
export type MenuPayload = boolean;
/**
 * One JS to Java call, enveloped as {c, params}. In the real bridge `params` is the positional argument list of the method.
 */
export type Call =
  | SetGameplayCall
  | SetHudCall
  | SetModSettingCall
  | SwitchLoadoutCall
  | CloseMenuCall
  | OpenKeybindCaptureCall
  | SetSurfacesCall
  | SetGlobalCall;
/**
 * [id, on].
 *
 * @minItems 2
 * @maxItems 2
 */
export type SetGameplayParams = [GameplayModId, boolean];
/**
 * The subset of mod ids whose `kind` is `gameplay`, i.e. the mods an actuator Mixin reads every frame. These are the only ids accepted by `void.setGameplay`.
 */
export type GameplayModId =
  | 'toggle_sprint'
  | 'fullbright'
  | 'hitboxes'
  | 'zoom'
  | 'crosshair'
  | 'fov'
  | 'toggle_sneak'
  | 'overlay'
  | 'freelook'
  | 'hit_color'
  | 'damage_tint'
  | 'old_animations'
  | 'old_input'
  | 'scoreboard'
  | 'block_outline';
/**
 * [id, { anchor, dx, dy, scale }].
 *
 * @minItems 2
 * @maxItems 2
 */
export type SetHudParams = [
  HUDModId,
  {
    anchor: HUDAnchor;
    /**
     * Horizontal offset in unscaled GUI pixels from the anchor.
     */
    dx: number;
    /**
     * Vertical offset in unscaled GUI pixels from the anchor.
     */
    dy: number;
    /**
     * Per-item size multiplier; omitted means leave unchanged.
     */
    scale?: number;
  }
];
/**
 * [id, key, value].
 *
 * @minItems 3
 * @maxItems 3
 */
export type SetModSettingParams = [ModId, string, boolean | number | string | null];
/**
 * [id].
 *
 * @minItems 1
 * @maxItems 1
 */
export type SwitchLoadoutParams = [LoadoutId];
/**
 * No arguments.
 *
 * @maxItems 0
 */
export type CloseMenuParams = any[];
/**
 * [modId].
 *
 * @minItems 1
 * @maxItems 1
 */
export type OpenKeybindCaptureParams = [ModId];
/**
 * [surfaces]. Replaces the whole set; an empty array clears it.
 *
 * @minItems 1
 * @maxItems 1
 */
export type SetSurfacesParams = [EffectSurface[]];
/**
 * [key, value].
 *
 * @minItems 2
 * @maxItems 2
 */
export type SetGlobalParams = [string, boolean | number | string | null];
/**
 * The value one JS to Java call returned, enveloped as {c, returns}. Every call except `openKeybindCapture` returns synchronously, because the bridge is in-process (§6.5).
 */
export type CallResult =
  | SetGameplayResult
  | SetHudResult
  | SetModSettingResult
  | SwitchLoadoutResult
  | CloseMenuResult
  | OpenKeybindCaptureResult
  | SetSurfacesResult
  | SetGlobalResult;
/**
 * The state actually applied. Normally equals the requested value; differs only if the mod refused the change.
 */
export type SetGameplayReturns = boolean;
/**
 * The value actually stored, after clamping to the setting's range or enum. The control binds to this, not to what it sent.
 */
export type SetModSettingReturns = boolean | number | string | null;
/**
 * True when a loadout with that id existed and was applied; false when it did not, in which case nothing changed.
 */
export type SwitchLoadoutReturns = boolean;
/**
 * Always null; the call has no result.
 */
export type CloseMenuReturns = null;
/**
 * Two different things travel in this shape, which is why it admits null twice over. The **synchronous** answer of `__void_native` is always null and means "capture armed". The **deferred** envelope, delivered later through `__emit`, carries the captured key — or null again when the player cancelled with Escape. A shim distinguishes them by channel, never by value: the synchronous answer opens a Promise, the `__emit` envelope resolves it.
 */
export type OpenKeybindCaptureReturns = Keybind | null;
/**
 * How many surfaces the host kept, so the page can tell the call arrived.
 */
export type SetSurfacesReturns = number;
/**
 * The value actually stored, after validation and clamping — `null` when the key is not one Java knows, or when the value cannot be made usable (a `menu_key` that is not a legal key name, say). Null therefore means *nothing was stored*, so a control that gets it should keep showing the value it had. Exactly `setModSetting_returns`, one level up.
 */
export type SetGlobalReturns = boolean | number | string | null;

/**
 * The closed registry of every mod VOID ships — the 12 defined in PVP_ARCHITECTURE.md §3, the VOID watermark, and the readouts added since — together with the per-mod settings sub-schema, the anti-cheat classification of §11, the Mods-panel `category` taxonomy of Figma 244:538 and the factory defaults. This file is the single source of truth for mod identity, display copy and classification: `loadout.json` and `bridge.json` both $ref its `mod_id` enum and its `<id>_settings` definitions, so a mod is added in exactly one place, and no consumer re-declares a label or a filter tab. An instance of this schema is a registry document; the registry VOID actually ships is `examples[0]`.
 */
export interface ModRegistryDocument {
  version: RegistryVersion;
  mods: Mods;
}
/**
 * Every mod VOID ships, keyed by its snake_case mod id. Closed set: all 36 keys are required and no others are permitted.
 */
export interface Mods {
  fps: FPSDisplayEntry;
  keystrokes: KeystrokesEntry;
  cps: CPSCounterEntry;
  ping: PingDisplayEntry;
  coordinates: CoordinatesEntry;
  armor_status: ArmorStatusEntry;
  potion_effects: PotionEffectsEntry;
  watermark: WatermarkEntry;
  toggle_sprint: ToggleSprintEntry;
  fullbright: FullbrightEntry;
  hitboxes: HitboxesEntry;
  zoom: ZoomEntry;
  crosshair: CrosshairEntry;
  direction: DirectionEntry;
  combo: ComboCounterEntry;
  saturation: SaturationEntry;
  momentum: MomentumEntry;
  memory: MemoryEntry;
  server_address: ServerAddressEntry;
  item_counter: ItemCounterEntry;
  stopwatch: StopwatchEntry;
  fov: FOVChangerEntry;
  toggle_sneak: ToggleSneakEntry;
  overlay: OverlayEntry;
  freelook: FreelookEntry;
  hit_color: HitColourEntry;
  damage_tint: DamageTintEntry;
  old_animations: OldAnimationsEntry;
  old_input: OldInputEntry;
  hit_trade: TradeCounterEntry;
  clock: ClockEntry;
  cps_graph: CPSGraphEntry;
  scoreboard: ScoreboardEntry;
  reach: ReachDisplayEntry;
  potion_counter: PotionCounterEntry;
  block_outline: BlockOutlineEntry;
}
/**
 * One row of the §3 table plus its §11 classification and factory defaults. Every key is listed here; the per-mod entry definitions narrow `id`, `kind`, `hypixel_safe` and `defaults` to constants, and require or forbid `default_placement` according to the mod's `kind`.
 */
export interface RegistryEntry {
  id: ModId;
  kind: ModKind;
  category: ModCategory;
  hypixel_safe: HypixelSafetyClass;
  /**
   * Human-readable name as it appears in the Mods panel of the Figma.
   */
  label: string;
  /**
   * Name of the glyph the Mods list and the quick palette draw for this mod, from `@void/ui`'s icon sheet. Carried here rather than in a table per application: the in-game overlay and the desktop launcher both need it and neither can import the other, so before this it was one hand-maintained `MOD_ICONS` object that no schema knew about. Not consumed by the game — the mod draws no icons — but the registry is where mod *identity* lives, and an icon is identity.
   */
  icon: string;
  /**
   * One-line explanation shown under the label in the Mods panel.
   */
  description: string;
  /**
   * The Minecraft 1.8.9 field, method or injection point the sensor reads or the actuator writes, quoted from the §3 table. Documentation only; not consumed at runtime.
   */
  source: string;
  /**
   * Factory settings for this mod, used when a loadout omits it. Validates against the mod's own settings sub-schema.
   */
  defaults: {};
  default_placement?: FactoryHUDPlacement;
}
/**
 * Where this mod's widget sits on a HUD nobody has touched — the layout of Figma frame 244:1722, which is what a new loadout is seeded with and what the HUD editor's `Reset layout` restores. Anchor plus `dx`/`dy`, exactly as `loadout.json#/definitions/hud_item`, minus the `id` (it is the entry's own) and the per-item `scale` (a factory layout is always 1). The numbers are in the **overlay's own design-canvas pixels** — `VoidClient.pumpUi` fits the view to a 1300 x 820 canvas — because that is the space the page actually lays out in, and they are on a **38-42 px vertical rhythm**, which is what it takes to stack chips that are taller than that without overlapping. They are therefore NOT the tighter offsets in `crates/void-loadout`'s `defaults.rs` library or in `loadout.json`'s own `examples`, whose 18-20 px rhythm belongs to hand-authored product loadouts rather than to the factory layout. Mods that ship off (`coordinates`, `direction`) are placed too: a placement is where a widget *would* go, not whether it is drawn — the `on` setting decides that. Required on every `kind: hud` mod and forbidden on every `kind: gameplay` mod; the per-mod `<id>_entry` definitions are where that is enforced, so a HUD mod with no placement, or a gameplay mod with one, is a schema error rather than a silent default.
 */
export interface FactoryHUDPlacement {
  /**
   * Screen anchor the offsets are measured from. The anchor names a point on the viewport *and* the matching point on the widget box, which is why `dx` is negative on right-hand anchors and `dy` negative on bottom ones.
   */
  anchor:
    | 'top-left'
    | 'top'
    | 'top-right'
    | 'left'
    | 'center'
    | 'right'
    | 'bottom-left'
    | 'bottom'
    | 'bottom-right';
  /**
   * Horizontal offset in design-canvas pixels from the anchor. Positive is right.
   */
  dx: number;
  /**
   * Vertical offset in design-canvas pixels from the anchor. Positive is down.
   */
  dy: number;
}
/**
 * Settings for the FPS display HUD mod. Reads `Minecraft.debugFPS` once per tick.
 */
export interface FPSDisplaySettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the FPS tile, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the FPS tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the FPS tile — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  color?: Colour;
  /**
   * Whether to render the trailing "FPS" label after the number.
   */
  show_label?: boolean;
  /**
   * Whether the 1% low is drawn as a trailing aside. It is the figure that says whether a frame rate is actually smooth, and it is also a third number on the chip a player reads mid-match — so it is a switch rather than something that appears whenever a low has been measured.
   */
  show_low?: boolean;
}
/**
 * Settings for the Keystrokes HUD mod. Fed by the edge-triggered `keys` bridge event (§6.5), never by polling.
 */
export interface KeystrokesSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the key tiles, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the key tiles, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the key tiles — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  keybind?: Keybind;
  /**
   * Whether to render the LMB and RMB tiles under the WASD block.
   */
  show_mouse?: boolean;
  /**
   * Whether to render the spacebar tile.
   */
  show_spacebar?: boolean;
  /**
   * Whether to render the sneak (shift) tile beside the space bar. The `keys` event has always carried `shift` and the widget has always been handed it; this is the switch that draws it. A sneak key is as much a part of reading a PvP player's inputs as the space bar, which is why the two sit together.
   */
  show_sneak?: boolean;
  /**
   * Whether to print the current CPS inside the LMB and RMB tiles.
   */
  show_cps?: boolean;
  /**
   * Corner radius of a key tile in unscaled GUI pixels, drawn as the `Corner radius` slider on the Mod settings frame (Figma 244:834). 0 is a square tile.
   */
  corner_radius?: number;
  key_color?: KeyColourSwatch;
  pressed_color?: PressedColourSwatch;
}
/**
 * Settings for the CPS counter HUD mod. Derived entirely in JS from mouse edges on the `keys` event (§3), so no Java sensor exists for it.
 */
export interface CPSCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the CPS tile, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the CPS tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the CPS tile — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Which mouse buttons to count: left only, right only, or both shown side by side.
   */
  mode?: 'left' | 'right' | 'both';
  /**
   * Whether to render the trailing "CPS" unit after the figures. Every other readout on the HUD can drop its unit; this one could not, which left it the widest chip on screen for a player who already knows what the number is.
   */
  show_label?: boolean;
  /**
   * Length of the sliding window in milliseconds over which clicks are counted before being scaled to clicks per second.
   */
  window_ms?: number;
  /**
   * Whether the highest rate seen this session is drawn as a trailing aside. `window_ms` already averages, so the live figure answers "how fast am I clicking"; this answers "how fast can I", which is the number worth comparing against and the one the live figure never sits still long enough to show.
   */
  show_peak?: boolean;
}
/**
 * Settings for the Ping display HUD mod. Reads the player's own `NetworkPlayerInfo.responseTime`.
 */
export interface PingDisplaySettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the ping tile, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the ping tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the ping tile — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Whether to render the trailing "ms" unit after the number.
   */
  show_label?: boolean;
  /**
   * Ping at or below this many milliseconds renders in the good colour.
   */
  good_ms?: number;
  /**
   * Ping at or above this many milliseconds renders in the bad colour. Must be greater than `good_ms`; not enforced by the schema.
   */
  bad_ms?: number;
  /**
   * Whether the shortened server name is drawn after the figure. A player who only ever plays one server is being told something they already know, on the chip they look at most often.
   */
  show_host?: boolean;
  /**
   * Whether the mean change between consecutive readings is drawn as a trailing aside — `· ± 8 ms`. Derived in JS from the `ping` field over a ~1.5 s window, the way `fps.show_low` and the whole CPS counter are; the wire carries readings, and a statistic over readings is a policy over them (`bridge.json`, `hits`, makes the same argument about the combo).
   *
   * **It is the reading the figure beside it cannot give.** 40 ms that never moves plays better than 25 that swings, and a single latency number says nothing about which you have — which is why "my ping says 30 and it feels awful" is a complaint every client gets and none of them answer. `docs/mod-roster.md` §8 names ping jitter as one of three items in its third gap: depth on mods that already exist, which never shows up on a feature-count comparison and is what a returning player notices in the first ten minutes.
   *
   * The statistic is the mean absolute *step*, not a standard deviation about the mean, and the distinction is the point rather than a detail: a link that sits at 30 for a second and then at 90 has a large deviation and feels fine, while one alternating 30, 90, 30, 90 has the same deviation and is unplayable. What a player feels is the step. RFC 3550 defines interarrival jitter the same way for the same reason.
   *
   * Off by default. The figure is the reading and this is the gloss on it; a chip that ships with both has decided for you, which is the argument `memory.show_bar` and `hit_trade.show_bar` already make.
   */
  show_jitter?: boolean;
}
/**
 * Settings for the Coordinates HUD mod. Reads `EntityPlayerSP` position and yaw once per tick.
 */
export interface CoordinatesSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the coordinates tile, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the coordinates tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the coordinates tile — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Number of decimal places printed for X, Y and Z. Capped at 2 because that is what the wire carries: the `tick` sensor rounds the position to 2 dp before publishing it, so a third place could only ever print a zero. Widening it is a bridge change, not a settings change, and a costly one — 3 dp makes ten times as many positions distinct, and every distinct position is a HUD repaint.
   */
  decimals?: number;
  /**
   * Whether to append the cardinal direction derived from yaw.
   */
  show_direction?: boolean;
  /**
   * Whether X, Y and Z are stacked on three lines or printed on one. Defaults to `inline`, which is what the HUD frame draws; `stacked` holds the three numbers against one left edge, which is easier to read while moving.
   */
  layout?: 'stacked' | 'inline';
  color?: Colour;
}
/**
 * Settings for the Armor status HUD mod. Reads `InventoryPlayer.armorInventory` durability, pushed only when it changes.
 */
export interface ArmorStatusSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the armor row, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the armor row, at the system's own `--border-panel` alpha. Ships **on** for this mod, unlike the other fifteen. This is a panel rather than a chip — 150 px of rows over live game — and the sheet has always drawn it with a `--border-dock` edge, which is what separates a list of rows from the world behind it. That edge used to be written into the rule unconditionally, so the setting existed and could not turn it off; moving it onto the shared `border` switch is what makes the control real, and this default is what stops that fix from silently stripping the edge off every loadout on disk.
   */
  border?: boolean;
  /**
   * Density of the armor row — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Whether armor pieces are laid out left to right or top to bottom.
   */
  orientation?: 'horizontal' | 'vertical';
  /**
   * Whether to print remaining durability as a number under each piece.
   */
  show_durability?: boolean;
  /**
   * Whether to include the currently held item as a sixth slot.
   */
  show_held_item?: boolean;
  /**
   * Fraction of maximum durability under which a piece's bar turns amber. A threshold on a live value, the same species as `ping.good_ms`: the point at which a player wants to be told their gear is going is a matter of how they play, not a constant. 0 never warns.
   */
  warn_below?: number;
}
/**
 * Settings for the Potion effects HUD mod. Reads `getActivePotionEffects`, pushed only when the set changes.
 */
export interface PotionEffectsSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the effect list, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the effect list, at the system's own `--border-panel` alpha. Ships **on** for this mod, unlike the other fifteen. This is a panel rather than a chip — 150 px of rows over live game — and the sheet has always drawn it with a `--border-dock` edge, which is what separates a list of rows from the world behind it. That edge used to be written into the rule unconditionally, so the setting existed and could not turn it off; moving it onto the shared `border` switch is what makes the control real, and this default is what stops that fix from silently stripping the edge off every loadout on disk.
   */
  border?: boolean;
  /**
   * Density of the effect list — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Whether to print the remaining duration next to each effect.
   */
  show_duration?: boolean;
  /**
   * Whether to print the roman-numeral amplifier next to each effect name.
   */
  show_amplifier?: boolean;
  /**
   * Whether to omit ambient effects such as a beacon aura from the list.
   */
  hide_ambient?: boolean;
}
/**
 * Settings for the VOID watermark HUD mod. It reads nothing from the game: the overlay draws the mark itself, which is why its `source` says so rather than naming a 1.8.9 field. Modelled on `fps_settings` and `crosshair_settings`, and deliberately without a `color`: `design/quiet-cell-system.md` §1 reserves colour for a live value or a selected item, and a watermark is neither.
 */
export interface VOIDWatermarkSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the mark, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the mark, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the mark — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Which parts of the mark are drawn: `full` is the ring plus the VOID wordmark, `mark` is the ring alone, `word` is the wordmark alone.
   */
  style?: 'full' | 'mark' | 'word';
}
/**
 * Settings for the Toggle sprint gameplay mod. Overrides the sprint `KeyBinding` in `onLivingUpdate`. One setting now, and the `$comment` at the top of this file is mostly about the three that have left: `show_status` to a HUD mod that does not exist yet, `sneak_too` to `toggle_sneak`, and `mode` to nothing at all, because it turned out to have no behaviour behind it. The mod itself is unchanged — it latches, or it is off.
 */
export interface ToggleSprintSettings {
  on: Enabled;
  keybind?: Keybind;
}
/**
 * Settings for the Fullbright gameplay mod. Overrides `gameSettings.gammaSetting`; client-side only and Watchdog-tolerated (§3), but classified `grey` in §11.
 */
export interface FullbrightSettings {
  on: Enabled;
  /**
   * Value written to `gammaSetting` while the mod is on. Vanilla's slider tops out at 1; 10 is the conventional fullbright value.
   */
  gamma?: number;
  keybind?: Keybind;
}
/**
 * Settings for the Hitboxes gameplay mod. Forces `RenderManager.debugBoundingBox`. Classified `grey` in §11.
 */
export interface HitboxesSettings {
  on: Enabled;
  /**
   * GL line width used for the bounding box wireframe.
   */
  line_width?: number;
  color?: Colour;
  /**
   * Whether to draw the vanilla eye-direction ray along with the box.
   */
  show_eye_line?: boolean;
  eye_line_color?: Colour;
  /**
   * Furthest an entity can be, in blocks, and still be drawn. The point of a box in a fight is the entity you are fighting; every box past that is a wireframe over the scenery. 64 is vanilla's own entity render distance, so the default draws what the game was already drawing.
   */
  max_distance?: number;
  keybind?: Keybind;
}
/**
 * Settings for the Zoom gameplay mod. Overrides FOV while `key` is held.
 */
export interface ZoomSettings {
  on: Enabled;
  key?: Keybind;
  /**
   * The player's FOV is divided by this factor while zoomed. 4 approximates the familiar Optifine zoom.
   */
  fov_divisor?: number;
  /**
   * Whether the FOV change is eased over a few frames rather than snapping.
   */
  smooth?: boolean;
  /**
   * Whether smooth-camera mouse damping is applied while zoomed.
   */
  cinematic?: boolean;
  /**
   * Mouse sensitivity while the zoom is engaged, as a fraction of your normal sensitivity. 1 leaves it alone, which is the default because it is what the mod did before this existed.
   *
   * **Why it needs a setting at all.** Zoom divides the field of view without changing what a mouse count does to your yaw, so at 4x every millimetre of desk turns you four times as far *across the visible scene* — the aim that lands a bow shot at 1x is unusable at 4x. `docs/mod-roster.md` §3.4 #1 names exactly this: "the absence of sensitivity scaling is felt every single time you zoom", and files it under finishing a mod that already exists rather than under a new one.
   *
   * **Why the honest default is not `1 / fov_divisor`.** That is the value which keeps the *on-screen* angular rate identical, and it is what a player who has never tried it asks for; in practice it is too slow, because at 4x you are also making a smaller correction. The range bottoms out at 0.2 so that answer is reachable at every divisor the mod offers, and the default stays out of the way. It multiplies vanilla's own sensitivity rather than replacing it, so a player who has already tuned their sensitivity keeps that tuning and scales it.
   *
   * Applied where the game computes its look step — `GameRenderer.render`'s read of `GameOptions.sensitivity`, the one the cubic curve is built from — so this is a fraction of the *setting*, not of the resulting angle, and it eases in and out with the zoom rather than snapping when the key goes down.
   */
  sensitivity?: number;
}
/**
 * Settings for the Crosshair mod. Uniquely among the 13 it is drawn in GL rather than HTML (§3 footnote) because it must sit at the exact pixel centre, but it is configured through the same loadout model as everything else.
 */
export interface CrosshairSettings {
  on: Enabled;
  /**
   * Shape drawn at the screen centre.
   */
  style?: 'default' | 'cross' | 'dot' | 'circle' | 't_shape' | 'none';
  /**
   * Half-length in pixels of each crosshair arm, before GUI scale.
   */
  size?: number;
  /**
   * Stroke thickness in pixels, before GUI scale.
   */
  thickness?: number;
  /**
   * Empty gap in pixels between the centre point and the start of each arm.
   */
  gap?: number;
  color?: Colour;
  /**
   * Whether a one-pixel black outline is drawn around the shape for contrast.
   */
  outline?: boolean;
  /**
   * Whether the gap widens while the attack cooldown is not full and while sprinting.
   */
  dynamic?: boolean;
  /**
   * Whether a dot of `thickness` square is drawn on the centre point, under whatever `style` draws around it. `dot` is a style, so without this a player must choose between a cross and a centre reference; every crosshair configurator worth the name lets them have both. Ignored by `none`, and by `default`, which is the vanilla pass.
   */
  center_dot?: boolean;
}
/**
 * Settings for the Direction HUD mod. Reads the same `pos.yaw` the tick sensor already sends for Coordinates, so it needs no sensor of its own — which is the whole reason it is cheap to ship. `coordinates.show_direction` is deliberately kept: that is the inline form, a suffix on the coordinate rows, and this is the standalone one a player places on its own and reads at a glance. Both are wanted, they are not duplicates of each other, and neither reads the other's settings.
 */
export interface DirectionSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the direction chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the direction chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the direction chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * How the facing is written. `letter` is the compass abbreviation the Coordinates mod already prints (`N`, `NE`, `SW`) and is what fits a small chip. `word` spells it out (`North`), which is what a player reading at a glance across a screen actually parses. `axis` prints the Minecraft world axis instead (`+X`, `-Z`) — not a compass reading at all, and the one a player wants while running a nether tunnel or lining up a build, because it is the notation coordinates themselves are in.
   */
  style?: 'letter' | 'word' | 'axis';
  /**
   * Whether the raw yaw angle is printed after the facing. Off by default: it is a second number on a chip whose whole job is to be read without reading, and it is only wanted by players aligning something precisely.
   */
  show_degrees?: boolean;
  color?: Colour;
}
/**
 * Settings for the Combo counter HUD mod. Derived in JS from the monotonic `hits` counters on the tick payload rather than sent as a combo: `bridge.json`'s `hits` records why. The sensor ships `dealt` and `taken` as counters that only ever go up, so a tick lost to coalescing leaves a counter that jumps by two rather than a hit that never happened — and it deliberately holds no opinion about when a combo expires, because the expiry is `reset_ms` below, a setting on this mod. A sensor that timed out on its own would be a UI policy baked into the wire, and two readers with different `reset_ms` values could not share it. `cps` set the precedent: it is derived entirely in JS from click edges and has no Java sensor either. What is deliberately *not* here is a switch for hiding the chip at zero — the widget does that on its own, and the `$comment` at the top of this file is why.
 */
export interface ComboCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the combo chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the combo chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the combo chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * How long without landing a hit before the count drops back to zero. This is the timeout the sensor refuses to have — it sends counters, and the policy lives here. 3000 ms because a 1.8 combo is bounded by knockback recovery rather than by a clock: consecutive hits in a real chase are well under a second apart, so three seconds forgives one whiffed swing and the sprint back into range, while still clearing the chip before the fight it described is over. Lower makes the counter honest about a broken chain; higher leaves a stale number on screen after the target is already dead.
   */
  reset_ms?: number;
  /**
   * Whether the trailing "COMBO" unit is drawn after the figure. A bare number on a chip of its own is ambiguous in a way the other readouts are not — there is no unit that gives it away the way `ms` or `FPS` do — so this defaults on and is worth turning off only once the chip's position has taught you what it is.
   */
  show_label?: boolean;
}
/**
 * Settings for the Saturation HUD mod. Reads `FoodStats#getSaturationLevel` off the tick payload, which value-checks it rather than rate-limiting it: saturation moves when you eat and when you exert yourself, not on a clock, so every push is news. Vanilla draws the hunger bar and hides the number underneath it, which is the whole argument for the mod — see `description`.
 */
export interface SaturationSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the saturation readout, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the saturation readout, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the saturation readout — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * How the value is drawn. `number` prints the figure, and it is the default because saturation is read against a *threshold* rather than as a quantity: in 1.8 combat regeneration runs while saturation is above zero and stops the instant it is not, so the only reading that matters is how close to zero you are, and a bar cannot be read to that precision at a glance. `bar` draws it as a fill, in the shape of the hunger row it is the hidden half of, for a player who wants it to look like part of the vanilla HUD. `both` puts the figure beside the fill for the player who wants the shape *and* the cliff.
   */
  style?: 'number' | 'bar' | 'both';
  /**
   * Decimal places on the figure. 1 by default, because saturation drains in fractions of a point and the difference between `0.4` and `0` is the difference between regenerating and not — rounding that to a whole number hides the one transition the readout exists to show. 0 is for a player who only wants to know roughly how much food is left in the tank and would rather the chip stopped twitching. The bound is 0-2 rather than the 0-1 this readout would pick on its own: `decimals` means the same thing in `coordinates` and in `momentum` and both are capped at 2, and `packages/protocol`'s generator keys `SETTING_BOUNDS` by property *name* precisely so that one name cannot mean two ranges — it throws rather than hand one mod the other's slider. A second place is honest here anyway (the sensor sends a raw float, unrounded), it is just rarely worth the width.
   */
  decimals?: number;
  /**
   * Whether the trailing "SAT" unit is drawn after the figure. Saturation shares its range with hunger (both 0-20) and sits near it on most layouts, so the label is what stops the two being read as each other.
   */
  show_label?: boolean;
}
/**
 * Settings for the Momentum HUD mod. Reads `speed` off the tick payload, which is **horizontal** ground speed and horizontal on purpose: falling is not momentum a player is steering, and folding the vertical component in would spike the number on every drop and off every jump, which is exactly when the readout is least useful. The sensor rounds to 2 dp and rate-limits, because speed changes every tick while you are moving and an uncoalesced field costs a full-surface repaint — so `decimals` below cannot ask for precision the wire does not carry.
 */
export interface MomentumSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the speed chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the speed chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the speed chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Which unit the figure is printed in. `bps` — blocks per second — is the default because the block is the unit every other thing a player reasons about is already in: reach, knockback, sprint-jump distance, the gap you are trying to clear. A number in blocks can be compared against the world without arithmetic. `kmh` is the same reading multiplied by 3.6 and it exists because it is the number players quote at each other; it reads as faster and it is genuinely easier to see small differences in, which is what a movement-mechanics player wants out of it.
   */
  unit?: 'bps' | 'kmh';
  /**
   * Decimal places on the figure. 2 by default, and 2 is also the ceiling — the sensor rounds to 2 dp before it sends, so a third place would be inventing digits the wire never carried. 2 is where the differences a player is chasing actually live: sprint-jumping and plain sprinting are about 0.4 blocks/second apart, and ice, soul sand and a speed potion each move the last two places rather than the first. 0 or 1 is for a player who wants the magnitude without a chip that flickers every tick.
   */
  decimals?: number;
  /**
   * Whether the trailing unit (`bps` or `km/h`) is drawn after the figure. Worth keeping while `unit` is anything but the one you always use: the two readings differ by 3.6x and a bare number is silently ambiguous between them.
   */
  show_label?: boolean;
}
/**
 * Settings for the Memory HUD mod. Reads the `memory` object on the tick payload — heap in use and `Runtime.maxMemory`, both in mebibytes. That field is **rate-limited hard** at the sensor, and `bridge.json` says why: heap moves constantly, nobody reads it twenty times a second, and it is the single field most able to undo the coalescing the whole tick payload exists for. So this readout is deliberately not live to the tick; it is a gauge you glance at when the game stutters, which is what `show_bar` below is defaulted against.
 */
export interface MemorySettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the memory chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the memory chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the memory chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * What the chip prints. `used_of_max` — `1400/4096 MB` — is the default because a heap figure on its own answers nothing: 1400 MB is idle on an 8 G allocation and terminal on a 2 G one, so the ceiling is half the reading and the player is the only one who knows which they launched with. `used` is the bare figure, for a player who already knows their ceiling and wants the narrowest chip. `percent` is the same comparison pre-done, which is the smallest form that still means something, at the cost of the absolute numbers you would quote in a bug report.
   */
  style?: 'used' | 'used_of_max' | 'percent';
  /**
   * Whether a fill bar is drawn under the figure. Off by default, and the reason is the sensor: `memory` is rate-limited hard, so the bar would move in visible steps rather than sweep, and a bar that jumps reads as a broken bar rather than as a coarse one. A bar also invites watching, and heap is not a number worth watching — the useful reading is a glance after a stutter, which the figure alone already answers. On for a player who wants headroom legible without parsing two numbers.
   */
  show_bar?: boolean;
  /**
   * Whether the trailing unit is drawn — `MB` on `used` and `used_of_max`, `%` on `percent`. Off makes the chip narrower at the cost of leaving a four-digit number with nothing to say what it counts.
   */
  show_label?: boolean;
}
/**
 * Settings for the Server address HUD mod. Reads `host` from the `server` bridge event, which is pushed on connect and on disconnect and carries an empty string on the way out — so "not connected" is a state this mod can see, and the chip simply draws nothing in it rather than offering a switch about it. `ping.show_host` is deliberately kept alongside this mod: that is the inline form, a shortened host printed after the latency figure, and this is the standalone one a player places on its own — the same split Coordinates and Direction already make between a suffix and a chip. Neither reads the other's settings. One setting is all a hostname supports; the file's `$comment` says why that is the honest count.
 */
export interface ServerAddressSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the host chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the host chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the host chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * How much of the host is printed. `short` keeps the part players actually say out loud — `hypixel` out of `mc.hypixel.net` — and is the default because on a chip the leading `mc.` and the trailing `.net` are the two pieces that never differ between the servers a player switches between, so they cost width and carry no information. `full` prints the address exactly as it was connected to, which is what you want on a network with several proxies, on a bare IP, or in a screenshot that has to be reproducible by somebody else.
   */
  style?: 'short' | 'full';
}
/**
 * Settings for the Item counter HUD mod. Reads `held_count` off the tick payload, which is the stack size of the **held** item and nothing else.
 *
 * Be clear about the scope, because the name is borrowed from mods that do more: Lunar's and Badlion's item counters track a *chosen* item across the whole inventory — tell it pearls, and it counts your pearls whether or not they are in your hand. This one counts the hand, because `held_count` is the sensor that exists. It answers "how many blocks are left" while you are bridging with them and "how many gapples" while you are holding one; it does not answer "how many pearls do I have" while you are holding a sword. An inventory-wide counter needs a slot-scanning sensor and a way to pick the item it watches, and neither exists yet; when they do, that is a superset of this mod rather than a rewrite of it.
 *
 * The stack size is value-checked at the sensor rather than rate-limited: it changes on use, not on a clock, so every push is news.
 */
export interface ItemCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the count chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the count chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the count chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * What the count covers. `held` is the stack in your hand and is the default, because it is what the mod has always meant and what a player watching a stack of blocks run down is asking about. `inventory` sums **every** slot holding the same item, which is `docs/mod-roster.md` §3.1 #5's "counts a chosen item (blocks, pearls, gapples)".
   *
   * **There is no item picker, and its absence is the design.** The roster's phrasing invites a dropdown of item ids, which would be a list somebody has to maintain against a game that has hundreds and a setting a player has to re-open every time they change what they are carrying. The item you are holding *is* the choice, and it is the one a player makes with their scroll wheel a hundred times a match. Hold a pearl and the chip counts pearls; hold blocks and it counts blocks. The setting is one switch instead of an enum nobody could finish.
   *
   * It reads the `inventory` field of the tick payload, which is value-checked at the sensor and sent only when something actually moved — so this costs nothing between pickups.
   */
  source?: 'held' | 'inventory';
  /**
   * Whether the count is prefixed with the multiplication sign — `x12` rather than `12`. On by default: the chip sits next to a CPS figure and above a keystrokes block, so a bare integer in that corner is a number among numbers, and the `x` is the cheapest thing that says it is a quantity of something rather than a rate.
   */
  show_label?: boolean;
  /**
   * A count at or below this many draws in the warn treatment instead of the normal ink. **0 disables the warning entirely, and 0 is the default**, because what counts as low depends completely on what is in the hand: eight is nearly out of blocks and a generous stash of pearls, and a client that guessed one number would be wrong for every player who does not build the way it assumed. So VOID does not guess — a player who knows what they are counting sets it, and everybody else gets a chip that never cries wolf. The ceiling is 64: a full stack, above which the warning would be permanently on.
   */
  low_threshold?: number;
}
/**
 * Settings for the Stopwatch HUD mod. Along with the watermark it is one of two mods with no game field behind it: the elapsed time is the overlay's own, counted from `Date.now()` exactly as the combo chip already counts its own timeout, and no sensor field, no tick payload and no wire message carries it. What does cross the bridge is the *input*. `start_key` and `reset_key` are dispatched by Java and arrive as `modaction` — `{mod: 'stopwatch', action: 'start_stop' | 'reset'}` — because a timer's keys ask the page to do something rather than flipping a boolean, which is all the existing per-mod `keybind` hotkeys can say. Java therefore owns the key and the page owns the clock, and the two never disagree about elapsed time because only one of them counts it.
 */
export interface StopwatchSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the stopwatch chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the stopwatch chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the stopwatch chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Whether a fraction of a second is drawn after the seconds, as hundredths. Off by default, because a digit that never stops moving is the most expensive thing a HUD chip can do to a player's attention and a stopwatch is usually read *after* it stops. Two places rather than three: the overlay repaints per frame, so a thousandths digit would be a digit nobody can read and the chip would be claiming a precision the page's own clock does not have. On for timing something short enough that a whole second is too coarse — a potion window, a bridge run, a respawn.
   */
  show_millis?: boolean;
  /**
   * How the elapsed time is written. `auto` grows the field as the clock does — `4:07` until an hour has passed and `1:04:07` after — which keeps the chip as narrow as the reading allows and is right for almost everyone. `mmss` pins it to minutes and seconds and lets the minutes run past sixty (`64:07`), so the chip never changes width mid-read, which is what a player timing repeated attempts against each other wants. `hmmss` always prints the hour, for a session timer that is meant to be read as a duration rather than as a count.
   */
  format?: 'auto' | 'mmss' | 'hmmss';
  start_key?: Keybind;
  reset_key?: Keybind;
}
/**
 * Settings for the FOV changer gameplay mod. Overrides `GameOptions.fov` and suppresses the movement-speed multiplier vanilla applies on top of it. `docs/mod-roster.md` §3.2 #2 is explicit about the size of this: Lunar ships twenty-five options here and the useful ones number about four, so there are three below and no per-state override table. Everything the extra twenty would buy is a different constant for a state a fight does not give you time to notice. The one thing this mod must get right is not a setting at all — it is keeping the override out of `options.txt`; the `$comment` at the top of this file is the whole briefing.
 */
export interface FOVChangerSettings {
  on: Enabled;
  /**
   * Field of view held while the mod is on, in degrees. The range is exactly vanilla's own slider — 30 to 110 — and that is the whole §11 argument for classing this mod `safe`: it moves a number the game already lets the player move, where `fullbright.gamma` runs to 15 against a vanilla slider that stops at 1. 90 by default rather than vanilla's 70 because a player who turns this on is turning it on for peripheral vision in a duel, and 70 is the value they are leaving.
   */
  fov?: number;
  /**
   * Whether the movement-speed FOV modifier is suppressed. This is the mod. Vanilla scales the field of view by how fast the player is moving, so sprinting, a speed potion and every knockback you take zoom the camera in the middle of a fight — a change of framing you did not ask for, at the moment framing matters most. On by default, because a player who wanted a different field of view and *not* this would have used the vanilla slider and never opened the Mods panel.
   */
  lock_sprint?: boolean;
  /**
   * Whether the bow-pull zoom is suppressed as well. Off by default, and deliberately a second switch rather than part of `lock_sprint`: the speed modifier is noise, but the bow zoom is *feedback*. It is how far the shot is drawn, and on 1.8 it is close to the only cue the client gives for a charge that decides whether the arrow travels. On for a player who reads draw from the arm animation instead and wants the camera to stop moving at all.
   */
  lock_bow?: boolean;
}
/**
 * Settings for the Toggle sneak gameplay mod. Overrides the sneak `KeyBinding` in `onLivingUpdate`, exactly as Toggle sprint overrides the sprint one — the same actuator shape, deliberately, so the two read the same in `LiveState` and the next latch after them costs nothing new. It is a mod of its own and not a boolean on Toggle sprint because `docs/mod-roster.md` §3.2 #3 says both competitors ship it as one: a sneak latch and a sprint latch are bound to different keys, turned on in different game modes and turned off for different reasons, and a boolean cannot carry a keybind. The boolean it replaces is gone; the top of this file records the migration that made removing it safe.
 */
export interface ToggleSneakSettings {
  on: Enabled;
  /**
   * `toggle` latches sneak until the key is pressed again, which is the mod. `hold` restores hold-to-sneak on this mod's own bind — not a null setting, because the bind below is *not* vanilla's sneak key: `hold` is how a player moves sneak onto a key their hand can reach without giving up the latch behaviour of every other key they have bound.
   */
  mode?: 'toggle' | 'hold';
  keybind?: Keybind;
}
/**
 * Settings for the Overlay gameplay mod — five switches over render passes that already exist, and `docs/mod-roster.md` §3.3 #1 calls it "the highest value-per-hour on this entire page" for exactly that reason: none of the five needs a sensor, a new render pass or a number, only a settings-driven `return` in a draw the game is already doing. Lunar bundles roughly fifty of these into one mod; the roster's instruction is to "ship the six that matter as one VOID mod and stop", and stopping is the design. It is five and not six, and the difference is worth naming rather than rounding: §3.3 #1's list reads "minimal view bobbing, lower/hide fire overlay, hide own armour pieces, hide stuck & ground arrows, disable damage overlay", and the last of those is not here. The damage overlay is the red flash that tells you that you were hit, so suppressing it removes a combat cue rather than an occluder — the opposite trade from the other four — and `hide_pumpkin` took the slot because it is the same one-line suppression of a self-inflicted view block that `hide_fire` is. If damage tint comes back it comes back as §3.2 #7's own mod, where a vignette and a heartbeat can be argued together. What is deliberately not here is a colour, a strength or an opacity for any of them: a suppression that is half-applied is a worse picture than either end of it, and the moment one of these grows a slider this mod is on its way to fifty. Classified `grey`, which is not the obvious call and is argued at the top of this file rather than assumed.
 */
export interface OverlaySettings {
  on: Enabled;
  /**
   * Whether the first-person fire overlay is drawn while you are burning. The one the roster singles out: flames cover the middle of the screen for the duration of a fire-aspect hit or a lava dip, which is precisely the window in which you cannot afford to lose the other player. On by default, because a player enabling this mod at all is enabling it for this. It is also the switch that classes the whole mod `grey` — see the top of this file — and the honest reading is that it removes a cost the game imposed rather than revealing something the game hid.
   */
  hide_fire?: boolean;
  /**
   * How much the camera and the held item move as you walk. `vanilla` is the game's own bob, unchanged, and is the default because bobbing is a motion cue for your own speed and taking it away is a preference rather than an improvement. `minimal` keeps the held item moving and holds the camera still, which is what most players actually want out of the vanilla switch and cannot get from a boolean. `off` is the vanilla switch off — both still, hand included.
   */
  view_bobbing?: 'vanilla' | 'minimal' | 'off';
  /**
   * Whether your own armour is drawn on your own player model. Off by default, because unlike the rest of this mod it changes nothing you see in a first-person fight — your armour is on screen only in third person and in the inventory preview — so it is a cosmetic preference for players who want to see their skin, not a visibility fix. Included because it is one line in the same render path and leaving it out means a second mod later.
   */
  hide_own_armor?: boolean;
  /**
   * Whether arrows stuck in your own model, and arrows lying where they landed, are drawn. On by default: a bow exchange leaves a thicket of arrow entities around the fight and several sticking out of you, and neither tells you anything a moment after it lands. This is the one switch here that removes *information* rather than an occluder, which is the direction §6.1 has no objection to — the objection is to mods that add data the player could not otherwise have.
   */
  hide_stuck_arrows?: boolean;
  /**
   * Whether the carved-pumpkin blur is drawn while one is worn. On by default, because a player who has put a pumpkin on has done it for the head slot and not for the view. It is the second of the two switches that class this mod `grey`: the blur is the price of the helmet, and removing the price locally is not something Hypixel's allowlist has a category for.
   */
  hide_pumpkin?: boolean;
}
/**
 * Settings for the Freelook gameplay mod, which is also Snaplook — the top of this file is the argument for that, and the settings below are the difference between them. §3.2 #4 flags the shape of the work rather than the size of it: this needs a camera detach in `GameRendererMixin` territory, not a HUD widget, so it is `kind: gameplay` and the only thing it exposes to the loadout is which key, which mode, which offset and what happens on release.
 *
 * The key does not go through the bridge, and that is worth stating because the previous wave added a channel for exactly this problem and this mod is not it. `stopwatch` needed `modaction` because the key is a *verb* for a widget the **page** owns; freelook's camera is owned entirely by Java, so its key is polled the way `zoom.key` already is — `VoidClient` reads `isKeyDown` each frame and `ZoomController` eases from the level — and a round trip through the overlay would put a frame of latency inside a hold. **This wave changes no bridge channel and no protocol message.**
 *
 * Classified `safe`. §3.2 #4's researched verdict is "Camera-only, allowed, universally used", and §6.1's allowlist test is about *information*, which this adds none of: every camera position freelook can reach is one vanilla's own F5 reaches, and vanilla's front view already shows a player what is behind them while their body faces forward. What the mod adds is a continuous sweep in place of two fixed offsets, and a key that is not F5. It is worth saying why that is not `overlay`'s trade, since `overlay` shipped `grey` today: its two grey switches remove **occluders** — the fire overlay and the pumpkin blur are world pixels the game covered up, and suppressing them shows you world you could not see. Freelook covers nothing and uncovers nothing. Its nearest classified sibling is `fov.lock_sprint`, which suppresses a camera change the game imposes on you and is `safe`.
 */
export interface FreelookSettings {
  on: Enabled;
  keybind?: Keybind;
  /**
   * `hold` engages freelook while the key is down and ends it on release. That is Snaplook (§3.2 #9) and it is the default, because it is the behaviour a fight can afford: the camera comes back without a second decision from a player who is already making several. `toggle` latches it until the key is pressed again, for the case a hold is wrong for — crossing a bridge or running a chase while watching what is behind you, which is a length of time no thumb wants to hold a key for.
   */
  mode?: 'hold' | 'toggle';
  /**
   * Where the camera sits while freelook is engaged. `third_back` and `third_front` are vanilla's own two F5 offsets and nothing more — the same pivot, the same distance, reached by a different key. `free` is an orbit about that same pivot rather than a snap to either offset, and the top of this file pins what that word may and may not mean, because the difference between an orbit and a freecam is the difference between this mod and one VOID will not ship. `third_back` by default: it is the view players already have muscle memory for, and the one that keeps your own model out of the middle of the frame.
   */
  perspective?: 'third_back' | 'third_front' | 'free';
  /**
   * What happens to your facing when freelook ends. `true` restores the view to the direction your body was already pointing, so the mod is a look and never a turn — that is the "snap back" of Snaplook, and it is the default because a camera that quietly rotated your aim while you were watching your back is the single worst thing this mod could do in a duel. `false` keeps the direction the camera ended on and brings the body round to match, which turns freelook into an input for turning around rather than for looking around. Both are legitimate and only one of them is safe to be surprised by, which is what picks the default rather than which is more useful.
   */
  snap_back?: boolean;
}
/**
 * Settings for the Hit colour gameplay mod. §3.2 #5's verdict is "Entity-render tint; genuinely helps read whether a hit landed", and the reason it helps is not that the flash is missing — it is that vanilla drew it in the one colour a 1.8 PvP screen is already full of. The mod is a hue swap in the entity hurt overlay and an alpha the player can lower; it changes nothing about when the flash fires, how long it lasts, or which entities get one, and the `$comment` at the top of this file is the whole §11 briefing for why those three are the constraints rather than the settings.
 *
 * It is `kind: gameplay` although it draws, for `hitboxes`' reason: `kind` splits on whether the mod owns a draggable HUD item, and a tint on somebody else's model has nowhere to be placed. `_shared.json`'s gameplay block says so directly.
 */
export interface HitColourSettings {
  on: Enabled;
  color?: ColourOpaque;
  /**
   * Whether the recolour applies only to entities you damaged, or to every entity the game tints. On by default, because the mod is hit *confirmation* and a confirmation that also fires when two other players hit each other across the arena is not one — you would be reading somebody else's fight in your own colour, in your peripheral vision, during yours. Off is for spectating and for the team modes where knowing a teammate connected is worth the noise. It is not what makes this mod `safe`, and the top of this file says why in as many words: a setting everybody believes is load-bearing is a setting nobody dares change.
   */
  own_hits_only?: boolean;
  /**
   * Alpha of the recoloured overlay, as a fraction of the alpha vanilla already draws it at — 1 is the game's own, 0 draws nothing. **Not an absolute strength**, and that distinction is the entire §11 argument at the top of this file: there is no value here that makes a landed hit more visible than Minecraft made it, which is what keeps a recolour inside "purely aesthetic". 1 by default, because a player enabling this wants the flash they already had, in a colour they can pick out. Lower values are for the player who finds a full-strength tint on a model they are standing next to more distracting than useful; 0 is the honest way to say "recolour nothing", and it costs you the cue rather than buying you anything — which is the direction §6.1 has never had an objection to.
   */
  intensity?: number;
}
/**
 * Settings for the Damage tint gameplay mod, which is also Hurt cam control — the top of this file argues that bundle and names what would split it. §3.2 #7's case for the vignette is one sentence and it is the right one: "I did not notice I was at 3 hearts" is a real way to lose. The hearts are already on screen; what they are not is in your peripheral vision while you are reading somebody else's, and a vignette is the cheapest way to put a number you already have where your eyes already are.
 *
 * Three settings and no fourth. There is no colour here, deliberately: a low-health vignette that is not red is a low-health vignette a player has to learn, and this is the one cue in the wave that has to work the first time it fires. `hit_color` is the mod that owns a colour, and it defaults away from red partly so that this one can keep it — the two most decision-shaped cues on a 1.8 screen should not be the same hue, and that trade is made once, here, rather than left to whichever settings page a player opens second.
 */
export interface DamageTintSettings {
  on: Enabled;
  /**
   * Health at or below which the vignette draws, in half-hearts on vanilla's own 0-20 scale — so 6 is three hearts and 20 is "always on". 6 by default because §3.2 #7's whole case for this mod is that sentence about three hearts, and three hearts is where a 1.8 fight stops being about landing damage and starts being about whether you can still disengage. Compared against `getHealth()`, which is a float rather than an integer, so the boundary is inclusive and a player regenerating past it crosses out of the vignette at exactly the value they set rather than half a heart later.
   */
  threshold?: number;
  /**
   * Peak alpha of the vignette, reached at zero health. 0.6 by default: enough to be unmissable at the edge of vision, and not enough to cost you the corners of the screen at the moment you most need them, which is what drawing this at 1 does. The vignette ramps from nothing at `threshold` to this value at zero rather than switching on flat, and that is a design decision worth stating because the two numbers alone do not imply it — a mark that pops on at one value is a mark you stop seeing after an hour, and the slide from three hearts to dead is exactly the interval this should be describing. A player who wants the alarm rather than the gradient sets a low `threshold` instead; the ramp then has nowhere to run and it is a pop.
   */
  strength?: number;
  /**
   * What happens to vanilla's hurt-camera roll — up to fourteen degrees about the view axis, oriented by `attackedAtYaw`, which is to say by the direction the hit came from. `vanilla` is unchanged and is the default, deliberately and against the grain of what most players think they want: the roll is a handicap on your aim, but it is also close to the only thing 1.8.9's client tells you about *where* you were hit from, and a player who deletes it from a settings page without knowing that has traded a cue for a fraction of a degree of accuracy. `reduced` keeps the direction and takes most of the amplitude, which is what that player usually meant. `off` removes it entirely. Removing it is not `overlay`'s grey trade and the top of this file is the argument for why — a rotated frame hides nothing, where the fire overlay hid the other player.
   */
  camera_shake?: 'vanilla' | 'reduced' | 'off';
}
/**
 * Settings for the Old animations gameplay mod. `docs/mod-roster.md` §3.2 #1 ranks this first on the whole roster — "the single most-noticed absence in a 1.8 PvP client" — and the honest thing to say about the version that shipped is that it is **smaller than that row implies, because most of the row is not real**. The roster describes reverting "swing, block-hit and item-use animation". Disassembled against a real 1.7.10 jar, the swing is identical in the two versions down to the constants, and the item-use animation differs only in the blocking case. So there is one revert here and it is the block hit, in both the first-person and the third-person path, plus one animation that belongs to neither version and says so.
 *
 * The reason that is worth stating on the settings page rather than only in the changelog: a player enables a mod called Old animations expecting their swing to change, and it will not. What they are remembering is 1.8's held-item **render pipeline** — `BakedModel` and the JSON `firstperson` transforms replacing fixed sprite quads — which is a renderer rewrite and not a setting. The `$comment` at the top of this file is the full briefing and the reason no `swing` key exists.
 *
 * What this mod is *not* allowed to grow into is the other half of the withdrawn draft. `use_while_digging` and the two switches found beside it live in `old_input`, which is `grey`; a mod that reverts an animation and a mod that changes what a click does are different claims about the same word "1.7", and only one of them is free.
 */
export interface OldAnimationsSettings {
  on: Enabled;
  /**
   * Which version's blocking animation is drawn. `one_seven` is the mod and is the default: while you are holding right-click with a sword, your swing still moves the sword. 1.8.9 discards it — `renderArmHoldingItem`'s BLOCK branch calls `applyEquipAndSwingOffset(equip, 0.0F)`, hard-coding the swing progress to zero — so a 1.8 block-hit is a frozen arm with a hit landing somewhere behind it, and that stationary sword is the thing 1.7 players say the client "feels wrong" for. The fix is that one argument: pass the live `getHandSwingProgress(tickDelta)` instead of `0.0F`. **Do not also call `translateSwingProgress`** — 1.7 skipped that translate while an item was in use exactly as 1.8.9 does, so adding it back overshoots 1.7 rather than restoring it. `one_seven` also drops the −30° right-arm yaw (`rightArm.posY = -0.5235988f`) that 1.8 added to the blocking pose in `BiPedModel.setAngles`, which is how *other* players' blocking looks on your screen — your own body is drawn by their client, so nothing about how you appear to anyone else changes. That third-person half is folded in here rather than given a switch of its own on purpose: it is the same revert in the other render path, no player wants 1.7 blocking in their hands and 1.8 blocking on the model in front of them, and a boolean whose entire visible effect is a 30° arm rotation on an entity would have to be drawn in `test/preview.test.tsx` or exempted from it. If it is ever split, this sentence is the split list. `vanilla` leaves both alone and is what a player picks to compare.
   *
   * One interaction worth knowing, because neither mod's page can show it: with `old_input.use_while_digging` off — its factory state — 1.8.9's `doUse` guard discards every right click made while you are mining, so you cannot *raise* the sword mid-break at all, and this setting has nothing to draw for that click. The two mods are separate on purpose (one is animation and `safe`, the other is input and `grey`), which is exactly why the dependency has to be written down rather than inferred from sitting next to each other in the grid.
   */
  block_hit?: 'vanilla' | 'one_seven';
  /**
   * Whether your arm still swings during the half-second of dead time 1.8 imposes after a click that hit nothing. **Read the second half of this before assuming what it does.** On a miss, 1.8.9's `doAttack` sets `attackCooldown = 10` in survival, and for those ten ticks every further click returns at the top of the method: no swing, no attack, nothing. 1.7.10's switch has no MISS entry at all and falls straight to `return`, so a 1.7 player clicking into open air sees an arm that keeps up with the mouse. Turning this on reproduces `LivingEntity.swingHand()`'s body — the two public fields `handSwinging` and `handSwingTicks` — **without** `ClientPlayerEntity.swingHand()`'s outbound `HandSwingC2SPacket`. Nothing reaches the server and nothing about the fight changes.
   *
   * So be plain about what is restored and what is not: **the click is still swallowed.** A left click inside the dead window does not attack, does not start mining and is not sent anywhere, whether this is on or off. Only the animation comes back. That means this is not a 1.7 revert either — in 1.7 the click also *worked* — it is a third behaviour that existed in no version, and the honest description of it is "the arm agrees with the mouse". Off by default for exactly that reason: an arm that swings on a click the game ate can be read as a hit that landed. The half that actually restores 1.7's click cadence is `old_input.no_miss_delay`, and it is in a `grey` mod because it changes how many swing packets leave the client, which is the number a CPS-based anticheat is watching. Note for whoever writes the mixin: `getMiningSpeedMultiplier()` is **private** on `LivingEntity`, so the re-entry guard needs an `@Invoker` rather than a straight copy.
   */
  swing_during_delay?: boolean;
}
/**
 * Settings for the Old input gameplay mod — three guards 1.8 added to `MinecraftClient` that 1.7.10 did not have, each one a click the newer client refuses to act on. They are the non-animation remainder of the withdrawn Old animations draft, and they are a mod of their own because they are classed `grey` while the animations are `safe`; the `$comment` at the top of this file argues that split, argues the classification against §6.1's allowlist, and records why "it ships off" is an argument about defaults rather than about classification.
 *
 * Every switch here is off by default and the mod ships off, which is not a hedge — it is the shape the classification demands. A player who wants these has decided something about the server they play on, and the settings page should not decide it for them.
 *
 * The first two are mirrors of each other and were found together: 1.8 blocks *using an item while mining* and also blocks *mining while using an item*, in two different methods, with the same one-term difference from 1.7. Only the first was on the original roster line, and the second is arguably the more-felt half in a Bedwars rush.
 */
export interface OldInputSettings {
  on: Enabled;
  /**
   * Whether right-click is allowed to act while you are mining. 1.8.9's `doUse` opens `if (this.interactionManager.isBreakingBlock()) return;` at offsets 0–10, so every right click during a break is discarded: no block placed, no bow drawn, no potion thrown, no block-hit started. 1.7.10 has no such guard and no `isBreakingBlock()` accessor at all — only the private `breakingBlock` field the 1.8 method was written to expose — so the two clicks were simply independent. One `@Redirect` on that call is the entire feature, which is why `docs/mod-roster.md` §7 calls it the one setting of the four that was ready and provable. Off by default: it is the switch a Bedwars player turns on to bridge out of a block they are still breaking, and it is a click reaching the server that vanilla would have dropped.
   */
  use_while_digging?: boolean;
  /**
   * Whether left-click is allowed to keep mining while you are holding an item in use. The mirror of the setting above, and it was not on any roster line — it came out of reading the method next door. 1.8.9's `handleBlockBreaking` opens `if (this.attackCooldown > 0 || this.player.isUsingItem()) return;`; 1.7.10 has the identical line with only the cooldown term. So in 1.8 a drawn bow, a raised sword or a potion at the lips stops your pickaxe, and in 1.7 it did not. Same shape as `use_while_digging`, same one-term change, same classification, and in a rush it is the half you notice more often — blocking with a sword is a resting state, and mining through it is what 1.7 hands expect. Off by default for the same reason as its mirror.
   */
  dig_while_using?: boolean;
  /**
   * Whether the half-second dead time after a whiffed click is removed. On a click that hits nothing, 1.8.9's `doAttack` reaches `hasLimitedAttackSpeed()` and sets `attackCooldown = 10`, and for those ten ticks every click returns at the top of the method — a whiff costs you the next half second of clicking, and in survival only. 1.7.10's switch has no MISS entry and falls through to `return`, so a whiff cost nothing. **This is the setting the withdrawn draft got wrong**, and the correction is worth keeping: that draft called it `always_swing` and said 1.8 only swings when a click connects. It does not. Both versions call `swingHand()` unconditionally at offset 12, before the hit result is even read, so the arm swings on a miss in 1.8 exactly as it does in 1.7. The only difference was ever this cooldown. Turning this on restores 1.7's click cadence and, because a click that is not swallowed is a click that sends `HandSwingC2SPacket`, raises outbound swing volume on whiffs in proportion to CPS — which is what classes this mod `grey`, and is the reason the purely local, packetless version of this lives in `old_animations.swing_during_delay` instead. Off by default. Scope it to the MISS branch: 1.7 still set the same cooldown on a null hit result and on a BLOCK hit that resolved to air, so suppressing the field outright goes past 1.7 rather than back to it.
   */
  no_miss_delay?: boolean;
}
/**
 * Settings for the Trade counter HUD mod. Reads the `hits` object on the tick payload — `dealt` and `taken`, both monotonic since the client started — and does no arithmetic the sensor could have done, for the reason `bridge.json` gives for sending counters rather than events: a counter that jumps by two after a dropped tick is still exactly right, where a lost event is wrong forever.
 *
 * The reading is the whole session and there is deliberately no window over it. Combo counter is the per-fight reading and owns the only timeout in the registry; this is the one you look at between games.
 */
export interface TradeCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the trade chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the trade chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the trade chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * What the chip prints. `traded` — `12 / 4` — is the default because both figures are the reading: a ratio of 3 is the same number at 3/1 as at 30/10, and only one of those is a session worth reviewing. `ratio` is that comparison pre-done, which is the narrowest form that still means something and the right one for a player who already knows roughly how long they have been playing. `dealt` is the bare count of landed hits, for a player who wants the chip to be one figure wide.
   */
  style?: 'traded' | 'ratio' | 'dealt';
  /**
   * Whether a fill bar is drawn under the figure, showing the share of hits in the session that were yours — `dealt / (dealt + taken)`. Off by default because the figures are the reading and the bar is the gloss on it, and a HUD that ships with both is a HUD that has decided for you. On, it is the fastest form there is: half full is an even session, and which side of half you are on is legible without reading a digit. Monochrome, like the two chips that already draw this bar: a share has no threshold, so there is no state for a colour to mark (`design/quiet-cell-system.md` §1).
   */
  show_bar?: boolean;
  /**
   * Whether the trailing `TRADE` unit is drawn. Off makes the chip narrower at the cost of leaving `12 / 4` with nothing to say what it counts — which on a HUD that may also be carrying a combo count and a CPS pair is a real ambiguity, so this ships on.
   */
  show_label?: boolean;
}
/**
 * Settings for the Clock HUD mod. It reads the machine's own clock and nothing on the wire — there is no sensor and there could not be one, since the game does not know what time it is where you are. It is therefore the one HUD readout that is correct at the main menu, in singleplayer and on a server alike.
 *
 * It owns a timer, which almost nothing in this bundle does. `hud/second-edge.ts` carries the rule and the budget: one timeout armed for the instant the drawn figure next changes, re-armed from the clock when it fires — never an interval and never a frame loop — and nothing armed at all while a menu covers the HUD. With `show_seconds` off the step is a whole minute, so the chip repaints fifty-nine times less often than a player would guess.
 */
export interface ClockSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the clock, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the clock, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the clock — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * How the time is written. `h24` — `21:41` — is the default because it is one width at every hour, which is what a readout anchored by its corner wants: a twelve-hour chip changes width at one o'clock and at ten, and a HUD item that reflows twice a day is a HUD item that moves. `h12` is `9:41 PM`, for a player who reads that form faster than they can subtract twelve.
   */
  format?: 'h24' | 'h12';
  /**
   * Whether seconds are drawn. Off by default, and the reason is the repaint rather than the width: with seconds off the drawn figure changes once a *minute*, so the chip's timer is armed for the next minute boundary and sleeps through the other fifty-nine seconds. On, it repaints once a second — still the cheapest live readout on the HUD, but sixty times the cost of a figure nobody is watching tick.
   */
  show_seconds?: boolean;
}
/**
 * Settings for the CPS graph HUD mod. It reads the same click edges the CPS counter does — `keys.lmb` and `keys.rmb`, derived in JS with no Java sensor — and keeps one figure per second rather than the raw timestamps, because the drawing has one column per second and a fast hand puts several hundred timestamps into thirty of them.
 *
 * What it is for: a rate tells you what your hand is doing now, and a fight is not now. The number every player quotes is their peak, and the thing that actually decides a fight is whether the rate held through it — a hand that opens at 12 and is at 7 by the end has a different problem from one that sat at 9 throughout, and the CPS counter draws both of those identically.
 *
 * It repaints once a second, which is the column rate. That is a fact about the mod worth knowing before enabling it beside a HUD that is otherwise driven entirely by the 20 Hz sensor push: `hud/second-edge.ts` carries the rule and the budget, and nothing is armed at all while a menu covers the HUD.
 */
export interface CPSGraphSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the CPS graph, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the CPS graph, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the CPS graph — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Which button the graph plots. `left` is the default because it is the attack button and the one a fight is fought with; `right` is for a player training a block-hit or a bow rhythm; `both` sums them, which is the only honest way to draw two hands in one row of columns — two overlaid series in one 60px-wide widget is two shapes nobody can separate over live game pixels.
   */
  mode?: 'left' | 'right' | 'both';
  /**
   * How many seconds the graph covers, one column per second. 20 is the default because it is about the length of a fight: 10 is a burst and shows nothing about whether a rate held, and 60 puts a whole minute into a widget narrow enough to sit on a HUD, where each column is under a pixel of information. The columns get narrower as this grows rather than the widget wider — a HUD item that changed width with a setting would move under an anchor that is a corner.
   */
  window_s?: number;
  /**
   * Whether the current rate is drawn as a figure beside the graph. On by default: the shape is what this mod adds and the figure is what makes the shape readable, since a column chart with no scale on it is a picture of a rate rather than a reading of one. Off for a player who already has the CPS counter on their HUD and does not want the number twice.
   */
  show_figure?: boolean;
}
/**
 * Settings for the Scoreboard gameplay mod. It draws nothing of its own: every pixel is vanilla's `InGameHud.renderScoreboardObjective`, and this changes whether that runs and what transform it runs under.
 *
 * Why it is worth a mod at all — `docs/mod-roster.md` §3.1 #7: the vanilla sidebar covers the right third of the screen, at a size chosen for a 2011 resolution, and on most servers it is a scoreboard you have already read. It is also exactly where a right-handed player's eye goes for their own HUD.
 *
 * The geometry was read out of the method rather than remembered. The sidebar's left edge is `window.getWidth() - maxWidth - 3` and its top is `window.getHeight() / 2 + rows * fontHeight / 3`, so it hangs from a point on the right edge at half height and grows left and down from there. `scale` is applied about that point, which is why shrinking it keeps it in its corner instead of sliding it into the middle of the screen.
 */
export interface ScoreboardSettings {
  on: Enabled;
  /**
   * Whether the sidebar is drawn at all. Off by default, because a scoreboard is the only thing telling you the score in half the game modes it appears in and a mod that ships hiding it would be a mod that breaks Bedwars for anyone who enables it without reading. On, nothing is drawn — the whole method is skipped, so it costs less than vanilla rather than more.
   */
  hide?: boolean;
  /**
   * Size of the sidebar, as a multiplier on vanilla's. 1 is untouched. Applied about the sidebar's own anchor — the point on the right edge at half height that the method hangs it from — so shrinking it keeps it in its corner rather than sliding it towards the middle. The floor is 0.5 because vanilla's font is a bitmap: below half size the glyphs stop resolving into letters, and a scoreboard nobody can read is `hide` with extra steps.
   */
  sidebar_scale?: number;
  /**
   * Horizontal nudge in scaled screen pixels, positive to the right. The useful direction is negative — pulling the sidebar in off the edge — but both are offered because a player who has shrunk it may want it flush again.
   */
  offset_x?: number;
  /**
   * Vertical nudge in scaled screen pixels, positive downwards. This is the setting most players actually want: the sidebar sits at half height, which on a 16:9 screen is straight through the middle of a fight, and moving it up puts it above the horizon where a bow arc lives instead.
   *
   * It is a row rather than a drag handle on a preview, and that is a stated limitation rather than an oversight — see this mod's `$comment`. The HUD editor places *this client's* widgets, and every pixel here is vanilla's.
   */
  offset_y?: number;
}
/**
 * Settings for the Reach display HUD mod. It reads `reach` on the tick payload, which the sensor moves **only when an attack lands** — that is the whole contract, and `docs/mod-roster.md` §6.1 is why: a readout of your own attack distance is allowed and a live distance to a target you have not hit is not, and the two draw the same figure. The mod is classed `grey` on that account.
 *
 * The figure is the distance from your eye to the point on the target's hitbox the crosshair ray struck, taken in the frame that made the pick rather than recomputed when the swing resolves — the eye moves up to about 0.28 blocks between the two, which on a two-decimal figure is a different number rather than a rounding error.
 *
 * It draws nothing until something has been hit. A reach of zero is not a point-blank swing, it is a session in which nothing has connected, and a chip reading `0.00` would be a figure nobody earned.
 */
export interface ReachSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the reach chip, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the reach chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the reach chip — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Whether the trailing `blocks` unit is drawn. On by default: a bare `3.14` on a HUD that may also be carrying a CPS pair, a combo count and a trade ratio is a number with no subject, and this is the one figure on that stack whose unit is not obvious from its magnitude.
   */
  show_label?: boolean;
  /**
   * A reach at or above this many blocks draws in the warn treatment. `0` is off and is the default.
   *
   * **It marks your own swings, and there is nothing else it could mark.** The field this reads is only ever your own landed attack, so this cannot become a flag on somebody else's play — which is the shape a threshold on a reach figure would otherwise be reaching for, and the shape §6.1 rules out. What it is for is the opposite direction: 1.8 gives you about 3 blocks of reach, and a swing reported well past that is a sign your connection is behind rather than a sign you are good, so a player who wants to see when the number stops being believable can ask for it.
   *
   * The ceiling is 6 because that is vanilla's own creative-mode reach, which is the furthest the client will ever raycast.
   */
  warn_above?: number;
}
/**
 * Settings for the Potion counter HUD mod. It reads the `inventory` field of the tick payload — one entry per distinct thing, with a potion's effect carried as the same numeric id the `fx` array uses — and sums the entries matching the effect below.
 *
 * Why it needs its own field rather than the held stack: `held_count` sees the hand and nothing else, which is why `item_counter` counted the held stack and said so. Pot PvP is `docs/mod-roster.md` §3.1 #4's 'first-class 1.8.9 mode', and what you plan around is what is in the inventory, not what happens to be in your hand.
 *
 * The count is sent only when the inventory actually changes, which between pickups is never — so this readout is free in the sense that matters on this surface: it does not repaint while you fight, it repaints when you drink.
 */
export interface PotionCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the potion count, as a step on the system's own scale rather than a colour. `subtle` is the card ground at low alpha — enough to hold a chip together over a busy texture — and it is the default because it is what every HUD readout has always been drawn on. `bare` is nothing at all: glyphs on the game, which is the vanilla treatment and is legible over sky and unreadable over snow, so it is a choice rather than a default. `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   *
   * **The step sets the widget's own ground; it does not paint a second one behind it.** This was `background` on the *slot*, and every widget already had a ground of its own underneath — so all three steps composited over `rgba(10,11,12,0.55)` and the visible difference between them was a two-pixel halo where the slot's padding stuck out past the chip's corner. Once density moved onto the widget the halo went and the three steps became one drawing. They resolve to `--hud-chip-bg` and `--hud-chip-bg-strong` now, the variables the chip, the editor chip and both list panels actually paint from.
   *
   * **`none` was renamed to `bare`, and the rename is the migration.** The old value was the default *and* it drew a ground, so it never meant what it said and no player can have chosen it deliberately: there was no way to get a bare readout at all. Every loadout on disk therefore carries `none` meaning "I took the default", and the honest remap is to `subtle`, which is exactly what those players have been looking at. Renaming rather than redefining is what makes that remap safe to run once and never again — a stored `none` can only have been written before this, where a redefined `none` would be indistinguishable from a player who has since chosen it. `crates/void-loadout`'s `REMAPPED_VALUES` does the remap on read; Java's `ModRegistry.clamp` already rejects an unknown enum value and keeps the default, which is the same answer arrived at for free.
   */
  background?: 'bare' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the potion count, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the potion count — the inset between its content and its edge, as one of five steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   *
   * **This step drives the widget's own inset, not a box around it.** For one release it set padding on the *slot* — the box `HudSlot` puts round the widget — while the widget kept its own hard-coded padding underneath. With the default `background: none` that outer box is transparent, so the setting moved an invisible edge and the drawn chip never changed size. It passed `preview.test.tsx` because the class name on the slot changed, which is exactly the erosion that file's own doc comment warns the exemption list about: a gate that compares markup cannot tell a class that draws from a class that does not. The steps now resolve to `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`, the three variables every HUD surface actually reads its density from, so the chip, the two list panels and the keycap cluster all move together and all move at every background step.
   *
   * Five steps rather than three because three could not say what players asked for at either end. `none` is the setting off — glyphs on the game with nothing round them — which is what a player who has already turned the ground off is after; `wide` is the panel treatment, for a HUD read at a glance across a room. `tight`, `normal` and `roomy` keep the values they had.
   */
  padding?: 'none' | 'tight' | 'normal' | 'roomy' | 'wide';
  /**
   * Which potion is counted. `healing` is the default because it is the one a fight is planned around. `speed`, `strength` and `fire_resistance` are the other three a 1.8.9 kit is built on; `any` counts every potion that grants an effect, which is the reading for 'how much have I got left to throw' rather than 'have I got a heal'.
   *
   * A water bottle is never counted under any value, `any` included: the sensor reports no effect for it, so there is nothing to match. That is a fact about the item rather than a filter this mod applies.
   */
  effect?: 'healing' | 'speed' | 'strength' | 'fire_resistance' | 'any';
  /**
   * Whether only throwable potions count. On by default: in a duel a drinkable takes 32 ticks of standing still and a splash takes none, so a count that merged them would promise heals that cost the fight. Off for pot UHC and Skywars kits, which do carry drinkables and where a player means both.
   */
  splash_only?: boolean;
  /**
   * Whether the trailing unit is drawn — the effect's own short name, so the chip reads `6 heals` rather than a bare figure on a HUD that may also be carrying an item count. Off makes it narrower for a player who has only one counter on screen.
   */
  show_label?: boolean;
}
/**
 * Settings for the Block outline gameplay mod. It draws nothing of its own: every line is vanilla's `WorldRenderer.drawBlockOutline`, and these change whether it runs and what two of its instructions are handed.
 *
 * Why it is worth a mod — `docs/mod-roster.md` §3.3 #3 calls it table stakes on both competing clients. In practice it is a Bedwars and a build-fight setting: black at 40% over a dark block is a box you cannot see, and the same box in white or at three pixels is the difference between placing where you meant to and placing one block over.
 *
 * Vanilla's own values are the defaults, so a loadout that has never touched this draws exactly what the game draws.
 */
export interface BlockOutlineSettings {
  on: Enabled;
  /**
   * Whether the outline is drawn at all. Off by default. On, the method is skipped entirely rather than drawn transparent — a fully transparent line is still a line the GPU rasterises, and there is no reason to pay for one nobody can see.
   */
  hide?: boolean;
  color?: Colour;
  /**
   * Width of the outline in GL line units. 2 is vanilla's.
   *
   * The range is `hitboxes.line_width`'s exactly, and it is shared rather than chosen: `SETTING_BOUNDS` is keyed by the bare setting name and the generator refuses two mods that disagree about one. That rule is right here — this is the same quantity passed to the same `glLineWidth`, and a client where a line width of 3 means one thickness on a hitbox and another on an outline would be a client with two ideas of what a pixel is.
   *
   * The ceiling is 5 because `glLineWidth` above about that is not portable: drivers clamp it, and a value the player sets and the driver ignores is a setting that does nothing on their machine and works on yours. The floor is 0.5 for the same reason from the other end.
   */
  line_width?: number;
}
/**
 * A complete, hot-swappable template. Applying it writes every actuator field and re-renders the HUD in under a frame (§8.2).
 */
export interface Loadout {
  id: LoadoutId;
  /**
   * Display name shown in the launcher's loadout library and in the in-game loadout switcher.
   */
  name: string;
  /**
   * Name of the icon rendered on the loadout card. Resolved by the UI against the shared icon set in packages/ui; not a file path.
   */
  icon: string;
  /**
   * Slug of the server profile this loadout is intended for, or null when it is not server-specific. Purely advisory today; whether servers get a default loadout is open question §16.3.
   */
  server?: string | null;
  /**
   * Minecraft version this loadout targets. Only 1.8.9 exists today (§15).
   */
  mc: string;
  mods: ModStates;
  hud: HUDLayout;
  stats?: LoadoutStats;
}
/**
 * Enabled state plus settings for each mod, keyed by the mod ids of mods.json. Every key is optional: a mod omitted here falls back to its `defaults` in the registry, which is what keeps old loadouts valid when a mod is added. No key outside the closed 36 is permitted.
 */
export interface ModStates {
  fps?: FPSDisplaySettings;
  keystrokes?: KeystrokesSettings;
  cps?: CPSCounterSettings;
  ping?: PingDisplaySettings;
  coordinates?: CoordinatesSettings;
  armor_status?: ArmorStatusSettings;
  potion_effects?: PotionEffectsSettings;
  watermark?: VOIDWatermarkSettings;
  toggle_sprint?: ToggleSprintSettings;
  fullbright?: FullbrightSettings;
  hitboxes?: HitboxesSettings;
  zoom?: ZoomSettings;
  crosshair?: CrosshairSettings;
  direction?: DirectionSettings;
  combo?: ComboCounterSettings;
  saturation?: SaturationSettings;
  momentum?: MomentumSettings;
  memory?: MemorySettings;
  server_address?: ServerAddressSettings;
  item_counter?: ItemCounterSettings;
  stopwatch?: StopwatchSettings;
  fov?: FOVChangerSettings;
  toggle_sneak?: ToggleSneakSettings;
  overlay?: OverlaySettings;
  freelook?: FreelookSettings;
  hit_color?: HitColourSettings;
  damage_tint?: DamageTintSettings;
  old_animations?: OldAnimationsSettings;
  old_input?: OldInputSettings;
  hit_trade?: TradeCounterSettings;
  clock?: ClockSettings;
  cps_graph?: CPSGraphSettings;
  scoreboard?: ScoreboardSettings;
  reach?: ReachSettings;
  potion_counter?: PotionCounterSettings;
  block_outline?: BlockOutlineSettings;
}
/**
 * The placement of one HUD mod. Written by the HUD editor (Figma 244:1722) on drop via `void.setHud`, and mirrored to Rust in the `hud` protocol message.
 */
export interface HUDItem {
  id: HUDModId;
  anchor: HUDAnchor;
  /**
   * Horizontal offset in unscaled GUI pixels from the anchor. Positive is right; negative values are normal on right-hand anchors.
   */
  dx: number;
  /**
   * Vertical offset in unscaled GUI pixels from the anchor. Positive is down; negative values are normal on bottom anchors.
   */
  dy: number;
  /**
   * Per-item size multiplier applied on top of the mod's own `scale` setting. Omitted means 1.
   */
  scale?: number;
}
/**
 * Session statistics accumulated by Rust from the `session` protocol message and shown on the launcher's Loadouts frame (§5).
 */
export interface LoadoutStats {
  /**
   * Total milliseconds played with this loadout active.
   */
  played_ms?: number;
  /**
   * Average frames per second across all sessions with this loadout active.
   */
  fps_avg?: number;
}
/**
 * First frame the mod sends after the WS connects. Identifies the game and mod build and authenticates with the session token the launcher passed as -Dvoid.token. The launcher answers with `init`.
 */
export interface HelloJavaToRust {
  /**
   * Message discriminator; always `hello`.
   */
  t: 'hello';
  v: ProtocolVersion;
  /**
   * Minecraft version the mod is running inside, e.g. `1.8.9`.
   */
  mc: string;
  /**
   * Semantic version of the void-client mod build.
   */
  mod: string;
  /**
   * Session token the launcher generated for this spawn and passed as -Dvoid.token. The launcher rejects and closes the socket if it does not match.
   */
  token: string;
  [k: string]: any | undefined;
}
/**
 * Sent by the mod on every change to live loadout state, as a flat patch of dotted paths (§7). Java is authoritative for live state (§6.1); this message tells Rust what already happened, it never asks for permission. Batched per change, not per frame.
 */
export interface StateJavaToRust {
  /**
   * Message discriminator; always `state`.
   */
  t: 'state';
  loadout: LoadoutId;
  patch: StatePatch;
  [k: string]: any | undefined;
}
/**
 * Flat map of dotted paths into the loadout to their new values, e.g. `{"mods.fullbright.on": true}`. Flat rather than nested so that concurrent changes to sibling settings merge without conflict. Paths address `mods.<mod_id>.<setting>` only; HUD layout changes travel in the `hud` message instead.
 */
export interface StatePatch {
  /**
   * The new value at that path. Type depends on the setting; validate against the mod's settings sub-schema in mods.json after applying.
   */
  [k: string]: (boolean | number | string | null) | undefined;
}
/**
 * Sent by the mod when the HUD editor commits a drag (§9, Figma 244:1722). Carries the whole layout for the loadout rather than a delta, because the editor already holds the full list and a whole-list write cannot leave Rust with a partially moved layout.
 */
export interface HudJavaToRust {
  /**
   * Message discriminator; always `hud`.
   */
  t: 'hud';
  loadout: LoadoutId;
  items: HUDLayout;
  [k: string]: any | undefined;
}
/**
 * Sent by the mod when a global setting is written in game — the in-game Settings page rebinding the menu key, the HUD editor's Snap toggle writing `hud_editor_grid`. The exact counterpart of `state` for the non-loadout half of §8.3: Java is authoritative, has already applied the change, and this tells Rust so it reaches `settings.json` and survives the process. Without it a global written in game lives only in `LiveState` and is gone at the next launch, which is what `hud_editor_grid` did for as long as the Snap toggle existed.
 *
 * A **delta, not the whole object**, and that is load-bearing rather than stylistic. `global_settings` is `additionalProperties: true` so the launcher may add a global without a protocol bump, but the mod's `GlobalSettings` is a fixed five-field class that cannot carry one — so a mod that echoed the whole object back would silently erase every global it does not model. Rust merges the named keys into what it already has and leaves the rest alone.
 */
export interface GlobalsJavaToRust {
  /**
   * Message discriminator; always `globals`.
   */
  t: 'globals';
  patch: GlobalSettingsPatch;
  [k: string]: any | undefined;
}
/**
 * Flat map of `global_settings` property names to their new values, e.g. `{"hud_editor_grid": 8}`. Keys are the top-level property names of `global_settings`, not dotted paths: globals are flat, so there is nothing to path into. Unlike `state_patch` there is no `null`: `setGlobal` refuses a value it cannot store and returns null to the caller rather than sending one, so a null here would mean nothing a receiver could act on.
 */
export interface GlobalSettingsPatch {
  /**
   * The new value. Validate against the property's sub-schema in `global_settings` after applying; the mod has already clamped it to the same bounds.
   */
  [k: string]: (boolean | number | string) | undefined;
}
/**
 * Telemetry summary the mod sends every 60 seconds and once more on exit (§7). Feeds the played-time and average-fps numbers on the launcher's Loadouts frame. Values are cumulative for the current game session, not deltas.
 */
export interface SessionJavaToRust {
  /**
   * Message discriminator; always `session`.
   */
  t: 'session';
  /**
   * Mean frames per second over the session so far.
   */
  fps_avg: number;
  /**
   * Milliseconds elapsed since the game window opened.
   */
  played_ms: number;
  /**
   * Host of the server the player is on, or null in the main menu or singleplayer.
   */
  server?: string | null;
  loadout?: LoadoutId;
  [k: string]: any | undefined;
}
/**
 * Sent by the mod on connect to and disconnect from a multiplayer server, mirroring the `server` bridge event. Lets the launcher show current presence and, later, server-bound default loadouts (open question §16.3).
 */
export interface ServerJavaToRust {
  /**
   * Message discriminator; always `server`.
   */
  t: 'server';
  /**
   * Hostname of the server, without port, e.g. `mc.hypixel.net`. Empty string when disconnecting.
   */
  host: string;
  /**
   * True on connect, false on disconnect.
   */
  connected: boolean;
  /**
   * Port of the server when it is not the default 25565.
   */
  port?: number;
  [k: string]: any | undefined;
}
/**
 * Sent when the player pressed one of the two global hotkeys of §6.3 in game. It is a notification, not a request: Java has already done the thing — cycled the loadout, opened or closed the overlay — and this tells the launcher so the tray and the launcher window can follow. The loadout that the L key selected still travels in its own `state` message; this one carries no state of its own, which is why the payload is a single id.
 */
export interface HotkeyJavaToRust {
  /**
   * Message discriminator; always `hotkey`.
   */
  t: 'hotkey';
  id: HotkeyId;
  [k: string]: any | undefined;
}
/**
 * The launcher's answer to `hello` (§6.9). Delivers the entire world of persisted state in one frame: the active loadout, **every other loadout in full**, and global settings. The mod keeps no config files of its own (§6.1); everything it knows arrives here. `loadouts` carries whole loadouts rather than summaries on purpose: a loadout is roughly 1 KB and a library is capped at 128, so the whole library is a few hundred kilobytes at worst, sent once per launch — and in exchange `void.switchLoadout` and the L-key cycle can apply any loadout in under a frame (§8.2) with no round trip, and the in-game Loadouts screen can list the library without a bridge accessor of its own. There is deliberately no `request_loadout` message: nothing the mod does needs one.
 */
export interface InitRustToJava {
  /**
   * Message discriminator; always `init`.
   */
  t: 'init';
  v: ProtocolVersion;
  loadout: Loadout;
  /**
   * Every loadout in the library, in full and in library order, including the active one. Backs the L-key cycle, the in-game switcher and the `loadouts` bridge event.
   *
   * @maxItems 128
   */
  loadouts: Loadout[];
  settings: GlobalSettings;
  [k: string]: any | undefined;
}
/**
 * The subset of §8.3 global settings the game needs. Account, Java path and RAM stay on the Rust side and are deliberately absent: they are launcher concerns and the mod has no use for them. additionalProperties is true so the launcher can add a global without a protocol bump.
 */
export interface GlobalSettings {
  menu_key?: Keybind;
  cycle_loadout_key?: Keybind;
  /**
   * Name of the design-token theme both renderers use.
   */
  theme?: string;
  /**
   * Extra multiplier on the in-game UI, applied on top of MC GUI scale x window DPI (§6.2). 1 means follow the game.
   */
  ui_scale?: number;
  /**
   * Snap grid size in unscaled GUI pixels used by the HUD editor. 0 disables snapping.
   */
  hud_editor_grid?: number;
  [k: string]: any | undefined;
}
/**
 * Sent when the loadout was switched outside the game, from the launcher UI or the tray's Switch loadout submenu (§5, §8.2). Java applies it exactly as it applies an in-game switch, then pushes the `loadout` bridge event to the UI. Java does not answer with `state`; the launcher already knows.
 */
export interface LoadoutRustToJava {
  /**
   * Message discriminator; always `loadout`.
   */
  t: 'loadout';
  loadout: Loadout;
  [k: string]: any | undefined;
}
/**
 * Sent when global settings changed in the launcher while the game is running, e.g. the player rebound the menu key. Carries the whole settings object, not a delta.
 */
export interface SettingsRustToJava {
  /**
   * Message discriminator; always `settings`.
   */
  t: 'settings';
  settings: GlobalSettings;
  [k: string]: any | undefined;
}
/**
 * Envelope for the `keys` event.
 */
export interface KeysEvent {
  /**
   * Event discriminator; always `keys`.
   */
  e: 'keys';
  payload: KeysPayload;
}
/**
 * Edge-triggered key state, pushed only when a key changes, from `KeyBinding.setKeyBindState` (§6.6). Each field is 0 for released and 1 for pressed -- integers, not booleans, so the UI can index a sprite row and so that a future analogue axis fits without a shape change. In HUD mode Ultralight receives no input events at all (§6.3); this event is how the player's keys reach the UI, as data. The renderer must touch only the changed key's node (§9).
 */
export interface KeysPayload {
  w: KeyState;
  a: KeyState;
  s: KeyState;
  d: KeyState;
  lmb: KeyState;
  rmb: KeyState;
  space: KeyState;
  shift: KeyState;
}
/**
 * Envelope for the `tick` event.
 */
export interface TickEvent {
  /**
   * Event discriminator; always `tick`.
   */
  e: 'tick';
  payload: TickPayload;
}
/**
 * All per-tick telemetry, coalesced into one push per game tick, i.e. 20 Hz (§6.6). Every HUD mod other than keystrokes and CPS reads from this. Fields whose sensor has nothing to report are omitted rather than sent as null, so a handler must treat an absent field as unchanged. Every field is optional and absent means unchanged; the sensor coalesces, because an uncoalesced field costs a full-surface Ultralight repaint (`TickCoalescer`'s header has the measurements).
 */
export interface TickPayload {
  /**
   * Current frames per second from `Minecraft.debugFPS`.
   */
  fps?: number;
  /**
   * Round-trip time in milliseconds from the player's own `NetworkPlayerInfo.responseTime`; -1 when unknown, such as in singleplayer or before the player list arrives.
   */
  ping?: number;
  pos?: Position;
  /**
   * Worn armor and, when enabled, the held item. Present only on the ticks where durability or the equipped set changed.
   *
   * @maxItems 5
   */
  armor?: ArmorSlot[];
  /**
   * Active potion effects. Present only on the ticks where the effect set changed.
   *
   * @maxItems 32
   */
  fx?: PotionEffect[];
  /**
   * Food saturation, the hidden half of the hunger bar. Vanilla never draws it and it is what decides whether you regenerate, which is why every competing client ships a readout for it. Value-checked only, not rate-limited: it moves on eating and on exertion, not every tick.
   */
  saturation?: number;
  /**
   * Stack size of the held item, for the item counter. **`0` is the empty hand**, and everything else is a real stack.
   *
   * **It used to be absent for an empty hand, and that was a bug with a one-tick symptom.** This payload's own rule is that "a handler must treat an absent field as unchanged" — and this field is value-checked at the sensor, so it is *also* absent on every tick where the count did not move. The two meanings are indistinguishable downstream, and the page resolved the ambiguity as "empty": the chip drew the count for exactly one tick after each change and then blanked itself until the next one. Nothing caught it, because both halves are individually correct and the payload rule is stated one paragraph above the field that broke it.
   *
   * So emptiness is a *value* now. That is the only shape that keeps the payload rule true: a field cannot mean both "nothing changed" and "the thing is gone", and of the two only the second can be said explicitly. `held_item` goes with it — it is omitted on an empty hand, and the page clears it from this field's zero rather than from that one's absence, so there is one source of truth for "the hand is empty".
   *
   * Value-checked, so the transition to empty is sent once and the field is then quiet. The widget still draws nothing at 0: a count of zero is not a stack, and the argument for that was always about the drawing rather than about the wire.
   */
  held_count?: number;
  /**
   * Registry name of the held item, such as `minecraft:ender_pearl`. Absent when the hand is empty, on the same terms as `held_count`.
   *
   * **It exists so that `item_counter.source: inventory` can name what it is counting.** The count of the held *stack* needs no identity — it is the number in your hand — but summing every slot holding the same thing needs to know what that thing is, and the alternative was an item picker in the settings page: a list somebody maintains against a game with hundreds of items, and a setting a player re-opens every time they change what they are carrying. The item you are holding is the choice, made with a scroll wheel a hundred times a match.
   *
   * Value-checked, so it crosses only when you change slots. Deliberately *not* merged into `held_count`: they go absent together but they change on different events — a count moves as you use the stack and this moves only as you scroll — and one object would republish both every time either moved.
   */
  held_item?: string;
  /**
   * Horizontal ground speed in blocks per second, for the momentum readout. Horizontal on purpose: falling is not momentum a player is steering, and including it would make the number spike on every drop. Rounded to 2 dp at the sensor and rate-limited, because it changes every tick while moving and an uncoalesced field costs a full-surface repaint (see `TickCoalescer`'s header).
   */
  speed?: number;
  /**
   * JVM heap, for the memory readout. Rate-limited hard: it changes constantly, nobody reads it twenty times a second, and it is the field most able to undo the coalescing this payload exists for.
   */
  memory?: {
    /**
     * Heap in use, mebibytes.
     */
    used_mb: number;
    /**
     * Heap ceiling, mebibytes — `Runtime.maxMemory`.
     */
    max_mb: number;
  };
  /**
   * Monotonic hit counters, and the raw material for the combo counter. NOT the combo itself: a combo is a count with a *timeout policy* on it, and the timeout is a mod setting, so deriving it here would put a UI policy in the sensor. `cps` already sets that precedent — `mods.json` records that it is derived entirely in JS from click edges and has no Java sensor. Counters rather than hit *events* because an event lost to a dropped tick leaves the combo wrong forever, whereas a counter that jumps by two is still exactly right.
   */
  hits?: {
    /**
     * Attacks the player has landed since the session began. Monotonic; never reset on the wire.
     */
    dealt: number;
    /**
     * Times the player has been hit since the session began. Monotonic. A combo breaks when this moves, which is why it is sent rather than derived from health.
     */
    taken: number;
  };
  /**
   * Distance in blocks of the last attack that landed, eye to the point on the target's hitbox the ray struck. Absent until one has landed — a reach of 0 is not a point-blank swing, it is a session in which nothing has been hit, and the widget draws nothing rather than a figure nobody earned.
   *
   * **Only ever moved by a landed attack.** `docs/mod-roster.md` §6.1 allows a reach display as a readout of your own attack distance and forbids one that reports a distance to something you have not hit — a *reach indicator* is the disallowed thing, and the difference is entirely in when the number is allowed to change. `ReachTally` in the mod carries that rule and is where it is tested; this field is its output, so a reader can take the value at face value and does not need to know the rule to use it honestly. It is the reason the mod is classed `grey`.
   *
   * The figure is vanilla's own arithmetic: `GameRenderer.updateTargetedEntity` evaluates `result.pos.distanceTo(cameraPos)` for its own three-block cutoff, and the sensor reads the same two vectors in the same frame rather than reconstructing a distance at attack time — the eye moves up to about 0.28 blocks between the frame that picked the target and the tick that swings. Rounded to 2 dp at the sensor, which is as much precision as an interpolated position supports. Value-checked and deliberately **not** rate-limited: two swings at the same distance are indistinguishable from one, so a dropped update is a wrong answer rather than a stale one — the same argument `hits` makes.
   */
  reach?: number;
  /**
   * The main inventory, merged into one entry per distinct thing. Present only on the ticks where it changed — value-checked at the sensor and deliberately **not** rate-limited, because an inventory is the largest thing on this payload and also the stillest: it does not move while you fight, only when you pick something up, throw a pearl or drink a pot. A rate limit would delay the update that matters most while doing nothing about a case that does not exist, since this field cannot flicker, only change.
   *
   * An empty array is a real reading — an empty inventory — and is different from the field being absent, which is "no reading". A counter must draw nothing in the second case rather than a zero it did not measure.
   *
   * The merge is by *identity*, and `InventoryTally` in the mod owns what that means. Three stacks of pearls in three slots are sixteen pearls; a water bottle and a splash of healing are not eight potions.
   *
   * @maxItems 36
   */
  inventory?: InventoryEntry[];
}
/**
 * Player position and yaw from `EntityPlayerSP`, read once per tick. Pitch is deliberately absent: no mod in §3 uses it.
 */
export interface Position {
  /**
   * World X in blocks.
   */
  x: number;
  /**
   * World Y in blocks, feet level.
   */
  y: number;
  /**
   * World Z in blocks.
   */
  z: number;
  /**
   * Facing in degrees, normalised to [-180, 180). The coordinates mod derives the cardinal direction from this.
   */
  yaw: number;
}
/**
 * One equipment slot from `InventoryPlayer.armorInventory`, or the held item when the armor status mod has `show_held_item` on.
 */
export interface ArmorSlot {
  /**
   * Which slot this entry describes.
   */
  slot: 'helmet' | 'chestplate' | 'leggings' | 'boots' | 'held';
  /**
   * Minecraft item id such as `diamond_chestplate`, or null when the slot is empty.
   */
  item: string | null;
  /**
   * Damage taken by the item. 0 is undamaged.
   */
  damage?: number;
  /**
   * Maximum durability of the item; 0 for items that do not take damage.
   */
  max_damage?: number;
  /**
   * Stack size, relevant only for the held slot.
   */
  count?: number;
  /**
   * Whether the item has any enchantment, so the UI can draw the glint treatment.
   */
  enchanted?: boolean;
}
/**
 * One entry from `getActivePotionEffects`.
 */
export interface PotionEffect {
  /**
   * Numeric potion id as used by 1.8.9.
   */
  id: number;
  /**
   * Unlocalised effect name such as `potion.moveSpeed`, for the UI to map to a label and icon.
   */
  name?: string;
  /**
   * Amplifier level, 0-based: 0 renders as I, 1 as II.
   */
  amplifier: number;
  /**
   * Remaining duration in milliseconds, converted from ticks by the sensor so the UI never needs to know the tick rate.
   */
  duration_ms: number;
  /**
   * Whether the effect comes from an ambient source such as a beacon; hidden when `hide_ambient` is set.
   */
  ambient?: boolean;
}
/**
 * One distinct thing in the inventory and how many of it, summed across every slot holding it.
 */
export interface InventoryEntry {
  /**
   * Registry name, such as `minecraft:ender_pearl`.
   */
  item: string;
  /**
   * Total across every slot holding this thing.
   */
  count: number;
  /**
   * For a potion, the numeric potion id of the effect it grants — the same id `potion_effect.id` carries on the `fx` array, so the page needs one table and not two. Absent on anything that is not a potion with an effect, which includes a water bottle.
   *
   * **It is here because every potion in 1.8.9 shares one registry name.** A water bottle, a splash of healing II and a lingering weakness are all `minecraft:potion`, so `item` alone would tell a player they had eight heals when three were water — and pot PvP is the mode this whole field was added for. The effect is resolved by the sensor rather than sent as the stack's metadata, because metadata is identity on a potion and *wear* on a sword: sending the raw number would fragment every tool into as many entries as it has durability values.
   */
  effect?: number;
  /**
   * Whether the potion is throwable. Present exactly when `effect` is, and part of the same identity: a drinkable healing potion and a splash of healing are different things to a player mid-fight, and merging them would overcount the ones they can actually use in a duel.
   */
  splash?: boolean;
}
/**
 * Envelope for the `server` event.
 */
export interface ServerEvent {
  /**
   * Event discriminator; always `server`.
   */
  e: 'server';
  payload: ServerPayload;
}
/**
 * Pushed on connect and disconnect (§6.6). The same information also goes to Rust as the `server` protocol message.
 */
export interface ServerPayload {
  /**
   * Hostname of the server without port; empty string on disconnect.
   */
  host: string;
  /**
   * True on connect, false on disconnect.
   */
  connected: boolean;
}
/**
 * Envelope for the `loadout` event.
 */
export interface LoadoutEvent {
  /**
   * Event discriminator; always `loadout`.
   */
  e: 'loadout';
  payload: Loadout;
}
/**
 * Envelope for the `loadouts` event.
 */
export interface LoadoutsEvent {
  /**
   * Event discriminator; always `loadouts`.
   */
  e: 'loadouts';
  payload: LoadoutsPayload;
}
/**
 * Envelope for the `setting` event.
 */
export interface SettingEvent {
  /**
   * Event discriminator; always `setting`.
   */
  e: 'setting';
  payload: SettingPayload;
}
/**
 * One mod setting Java changed on its own — an in-game hotkey toggling a mod (the `keystrokes.keybind` overlay key), or a launcher-side `state` echo. It is *not* pushed for a change the page itself made through `setModSetting`: that call already returned the stored value, and re-pushing it would fight the control the player is holding. The UI applies it exactly as it applies the return value of `setModSetting`, so a whole-loadout replacement is not needed for a one-key change.
 */
export interface SettingPayload {
  id: ModId;
  /**
   * Name of the setting, a property of that mod's settings sub-schema. `on` included.
   */
  key: string;
  /**
   * The value Java stored, after clamping — the same value `setModSetting` would have returned.
   */
  value: boolean | number | string | null;
}
/**
 * Envelope for the `menu` event.
 */
export interface MenuEvent {
  /**
   * Event discriminator; always `menu`.
   */
  e: 'menu';
  payload: MenuPayload;
}
/**
 * Envelope for the `session` event.
 */
export interface SessionEvent {
  /**
   * Event discriminator; always `session`.
   */
  e: 'session';
  payload: SessionPayload;
}
/**
 * Who is playing: the account the launcher signed in with, as Java already knows it from the JVM arguments it was spawned with. Pushed on `pushWholeState()` — first paint, a launcher `init`, and a reloaded document (design/rendering-invariants.md §9a) — and never again in between: the account cannot change while the game runs. The About screen prints it, and the HUD greets by name.
 */
export interface SessionPayload {
  /**
   * The player's Minecraft name, as the launcher signed in.
   */
  name: string;
  /**
   * The account uuid. For an offline launch this is the uuid derived from the name, not a Mojang one.
   */
  uuid: string;
  /**
   * `microsoft` for a signed-in Xbox account; `offline` when the uuid is the one an offline launch derives from the name.
   */
  kind: 'offline' | 'microsoft';
}
/**
 * Envelope for the `settings` event.
 */
export interface SettingsEvent {
  /**
   * Event discriminator; always `settings`.
   */
  e: 'settings';
  payload: GlobalSettings;
}
/**
 * Envelope for the `modaction` event.
 */
export interface ModactionEvent {
  /**
   * Event discriminator; always `modaction`.
   */
  e: 'modaction';
  payload: ModactionPayload;
}
/**
 * One mod-scoped key press, dispatched by Java for the page to act on.
 *
 * The existing per-mod `keybind` hotkeys can say exactly one thing — flip this mod's `on` — and they say it by pushing `setting`. Java polls them from a table in `VoidClient.toggleMod`, edging each with `input/EdgeKey` against a code mirrored into `LiveState.applyActuatorFields`. That covers every mod whose key is a switch and no mod whose key is a verb, which is why `docs/mod-roster.md` §7 held Stopwatch back from the last wave: a `start_key` would have stored a key nothing acted on. This event is the second row of that table — same poll, same edge detection, same mirrored code — emitting a named action instead of writing a setting.
 *
 * It is a notification and not a call: there is no return value and nothing is stored. Java has not changed any state by sending it, so a page that is not listening loses only the press. Deliver it in the frame the edge was seen; do not coalesce two presses of the same action into one, because two taps of a stopwatch's start key are a stop and a start.
 */
export interface ModactionPayload {
  mod: ModId;
  /**
   * What the key asked for: a short snake_case verb naming the *request*, never the key that made it. `start_stop`, not `bound_key`; `reset`, not `key_r`. Java knows which key is bound and the page must not care — a page that branched on key names would break the moment a player rebound one, and the same action can reach it from a keybind today and from a menu button tomorrow. The vocabulary is per mod and lives in that mod's own settings prose, which is where the Java wave and the page wave both read it: `stopwatch.start_key` names `start_stop` and `stopwatch.reset_key` names `reset`, and those two are the whole set today. An action the page does not recognise is ignored, not an error.
   */
  action: string;
}
/**
 * `void.setGameplay(id, on)`. Writes the boolean field the mod's actuator Mixin reads every frame (§6.7). Synchronous and authoritative: the toggle in the UI shows the returned value, never an optimistic one.
 */
export interface SetGameplayCall {
  /**
   * Call discriminator; always `setGameplay`.
   */
  c: 'setGameplay';
  params: SetGameplayParams;
}
/**
 * `void.setHud(id, placement)`. Called by the HUD editor on drop (§9). Java applies it to the live layout and mirrors the whole layout to Rust as the `hud` protocol message, which is what persists it.
 */
export interface SetHudCall {
  /**
   * Call discriminator; always `setHud`.
   */
  c: 'setHud';
  params: SetHudParams;
}
/**
 * `void.setModSetting(id, key, value)`. The generic writer behind every control in a mod's settings pane. The value must satisfy that mod's settings sub-schema in mods.json; Java clamps rather than throws when it does not.
 */
export interface SetModSettingCall {
  /**
   * Call discriminator; always `setModSetting`.
   */
  c: 'setModSetting';
  params: SetModSettingParams;
}
/**
 * `void.switchLoadout(id)`. Writes every actuator field and re-renders the HUD in under a frame (§8.2), then reports `state` to Rust. A `loadout` event follows with the new loadout, so the caller does not need the returned object.
 */
export interface SwitchLoadoutCall {
  /**
   * Call discriminator; always `switchLoadout`.
   */
  c: 'switchLoadout';
  params: SwitchLoadoutParams;
}
/**
 * `void.closeMenu()`. Closes VoidMenuScreen and returns the mouse to the game, the same thing the menu key does. A `menu` event with payload false follows.
 */
export interface CloseMenuCall {
  /**
   * Call discriminator; always `closeMenu`.
   */
  c: 'closeMenu';
  params: CloseMenuParams;
}
/**
 * `void.openKeybindCapture(modId)`. The one asynchronous call on the bridge. It is still one synchronous hop like every other call, but the hop only **arms** the capture: Java answers `{c: 'openKeybindCapture', returns: null}` immediately, meaning "armed", and keeps key input until the player presses something. The key itself arrives later on the push channel, as a *call-result* envelope delivered through `__emit`: `__emit({c: 'openKeybindCapture', returns: 'V'})`, or `returns: null` when the player cancelled with Escape. The shim turns the armed answer into a pending Promise and the later envelope into its resolution, in FIFO order; the Promise never rejects. Because a null arrives on both channels, a shim must never read the synchronous null as the resolution — that is the one way to get this wrong. The captured key is not stored by this call; the UI writes it with `setModSetting(modId, 'key', captured)`.
 */
export interface OpenKeybindCaptureCall {
  /**
   * Call discriminator; always `openKeybindCapture`.
   */
  c: 'openKeybindCapture';
  params: OpenKeybindCaptureParams;
}
/**
 * `void.setSurfaces(surfaces)`. Tells the host where the elements that carry an expensive effect are, so it can draw that effect in GL beneath the view instead. The overlay's CSS sets every `box-shadow` to `none` because a blurred shadow is the costliest thing a CPU rasteriser does; this is how the design keeps them anyway. The page sends the *authored* Figma values (the `--shadow-*-gl` tokens), so what the host draws is the design, not an approximation. Sent on layout change, not per frame.
 */
export interface SetSurfacesCall {
  /**
   * Call discriminator; always `setSurfaces`.
   */
  c: 'setSurfaces';
  params: SetSurfacesParams;
}
/**
 * One rectangle the host draws a shadow behind. Geometry is in CSS pixels of the view, the same space the page lays out in — the host scales by the device scale it already knows, so the page never has to reason about device pixels or the Retina factor.
 */
export interface EffectSurface {
  /**
   * Which surface this is, for logging. Not interpreted.
   */
  id: string;
  /**
   * Left edge in CSS pixels.
   */
  x: number;
  /**
   * Top edge in CSS pixels.
   */
  y: number;
  /**
   * Width in CSS pixels.
   */
  w: number;
  /**
   * Height in CSS pixels.
   */
  h: number;
  /**
   * Corner radius in CSS pixels.
   */
  radius: number;
  shadow: EffectShadow;
}
/**
 * A CSS `box-shadow` decomposed into numbers, so neither Java nor GLSL has to parse CSS. Taken from the authored `--shadow-*-gl` token, which the token build copies verbatim out of `design/tokens.css` — so this is the Figma value.
 */
export interface EffectShadow {
  /**
   * Horizontal offset in CSS pixels.
   */
  dx: number;
  /**
   * Vertical offset in CSS pixels.
   */
  dy: number;
  /**
   * Blur radius in CSS pixels.
   */
  blur: number;
  /**
   * Spread in CSS pixels; negative shrinks.
   */
  spread: number;
  /**
   * Straight-alpha RGBA, each channel 0-1.
   *
   * @minItems 4
   * @maxItems 4
   */
  color: number[];
}
/**
 * `void.setGlobal(key, value)`. The generic writer behind every control in the in-game Settings pane, and the exact mirror of `setModSetting` one level up: the loadout holds what changes how the game *plays*, and these are the globals of §8.3 that do not (`protocol.json#/definitions/global_settings`). Synchronous, like every call but `openKeybindCapture`, because the bridge is in-process. Java validates and clamps rather than throwing, stores the result, and returns **what it stored**; the control binds to that return value, never to what it sent. Java then reports the change to Rust, which is the store of record between sessions (§6.1) — the page does not persist anything itself.
 */
export interface SetGlobalCall {
  /**
   * Call discriminator; always `setGlobal`.
   */
  c: 'setGlobal';
  params: SetGlobalParams;
}
/**
 * Envelope for a setGameplay return value.
 */
export interface SetGameplayResult {
  /**
   * Call discriminator; always `setGameplay`.
   */
  c: 'setGameplay';
  returns: SetGameplayReturns;
}
/**
 * Envelope for a setHud return value.
 */
export interface SetHudResult {
  /**
   * Call discriminator; always `setHud`.
   */
  c: 'setHud';
  returns: HUDItem;
}
/**
 * Envelope for a setModSetting return value.
 */
export interface SetModSettingResult {
  /**
   * Call discriminator; always `setModSetting`.
   */
  c: 'setModSetting';
  returns: SetModSettingReturns;
}
/**
 * Envelope for a switchLoadout return value.
 */
export interface SwitchLoadoutResult {
  /**
   * Call discriminator; always `switchLoadout`.
   */
  c: 'switchLoadout';
  returns: SwitchLoadoutReturns;
}
/**
 * Envelope for a closeMenu return value.
 */
export interface CloseMenuResult {
  /**
   * Call discriminator; always `closeMenu`.
   */
  c: 'closeMenu';
  returns: CloseMenuReturns;
}
/**
 * Envelope for an openKeybindCapture answer. Both the synchronous armed answer and the deferred resolution use it; only the channel tells them apart.
 */
export interface OpenKeybindCaptureResult {
  /**
   * Call discriminator; always `openKeybindCapture`.
   */
  c: 'openKeybindCapture';
  returns: OpenKeybindCaptureReturns;
}
/**
 * Envelope for a setSurfaces return value.
 */
export interface SetSurfacesResult {
  /**
   * Call discriminator; always `setSurfaces`.
   */
  c: 'setSurfaces';
  returns: SetSurfacesReturns;
}
/**
 * Envelope for a setGlobal return value.
 */
export interface SetGlobalResult {
  /**
   * Call discriminator; always `setGlobal`.
   */
  c: 'setGlobal';
  returns: SetGlobalReturns;
}
