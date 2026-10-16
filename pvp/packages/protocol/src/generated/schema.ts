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
 * Closed enum of the 45 mods of §3, snake_case. Used as the key of `loadout.mods`, as the `id` argument of `void.setModSetting`, and as the id of a HUD item.
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
  | 'culling'
  | 'particles'
  | 'frame_limiter'
  | 'block_outline'
  | 'shiny_pots'
  | 'clear_glass'
  | 'nametags'
  | 'scoreboard'
  | 'gui_scale'
  | 'fog'
  | 'menu_blur'
  | 'time_weather'
  | 'potion_counter'
  | 'reach'
  | 'chat'
  | 'cosmetics';
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
 * Registry entry for Entity culling, narrowed to its constant classification.
 */
export type EntityCullingEntry = RegistryEntry & {
  /**
   * Always `culling`.
   */
  id?: 'culling';
  /**
   * Always `eye`.
   */
  icon?: 'eye';
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
  defaults?: EntityCullingSettings;
};
/**
 * Registry entry for the Particle limiter, narrowed to its constant classification.
 */
export type ParticleLimiterEntry = RegistryEntry & {
  /**
   * Always `particles`.
   */
  id?: 'particles';
  /**
   * Always `sparkle`.
   */
  icon?: 'sparkle';
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
  defaults?: ParticleLimiterSettings;
};
/**
 * Registry entry for the Frame limiter, narrowed to its constant classification.
 */
export type FrameLimiterEntry = RegistryEntry & {
  /**
   * Always `frame_limiter`.
   */
  id?: 'frame_limiter';
  /**
   * Always `gauge`.
   */
  icon?: 'gauge';
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
  defaults?: FrameLimiterSettings;
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
 * Registry entry for Shiny pots, narrowed to its constant classification.
 */
export type ShinyPotsEntry = RegistryEntry & {
  /**
   * Always `shiny_pots`.
   */
  id?: 'shiny_pots';
  /**
   * Always `flask`.
   */
  icon?: 'flask';
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
  defaults?: ShinyPotsSettings;
};
/**
 * Registry entry for Clear glass, narrowed to its constant classification.
 */
export type ClearGlassEntry = RegistryEntry & {
  /**
   * Always `clear_glass`.
   */
  id?: 'clear_glass';
  /**
   * Always `box`.
   */
  icon?: 'box';
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
  defaults?: ClearGlassSettings;
};
/**
 * Registry entry for Nametags, narrowed to its constant classification.
 */
export type NametagsEntry = RegistryEntry & {
  /**
   * Always `nametags`.
   */
  id?: 'nametags';
  /**
   * Always `users`.
   */
  icon?: 'users';
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
  defaults?: NametagsSettings;
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
   * Always `star`.
   */
  icon?: 'star';
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
  defaults?: ScoreboardSettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for GUI scale, narrowed to its constant classification.
 */
export type GUIScaleEntry = RegistryEntry & {
  /**
   * Always `gui_scale`.
   */
  id?: 'gui_scale';
  /**
   * Always `move`.
   */
  icon?: 'move';
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
  defaults?: GUIScaleSettings;
};
/**
 * Registry entry for Fog, narrowed to its constant classification.
 */
export type FogEntry = RegistryEntry & {
  /**
   * Always `fog`.
   */
  id?: 'fog';
  /**
   * Always `droplet`.
   */
  icon?: 'droplet';
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
  defaults?: FogSettings;
};
/**
 * Registry entry for Menu blur, narrowed to its constant classification.
 */
export type MenuBlurEntry = RegistryEntry & {
  /**
   * Always `menu_blur`.
   */
  id?: 'menu_blur';
  /**
   * Always `layers`.
   */
  icon?: 'layers';
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
  defaults?: MenuBlurSettings;
};
/**
 * Registry entry for Time & weather, narrowed to its constant classification.
 */
export type TimeWeatherEntry = RegistryEntry & {
  /**
   * Always `time_weather`.
   */
  id?: 'time_weather';
  /**
   * Always `clock`.
   */
  icon?: 'clock';
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
  defaults?: TimeWeatherSettings;
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
   * Always `heart`.
   */
  icon?: 'heart';
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
  defaults?: ReachDisplaySettings;
  default_placement: FactoryHUDPlacement;
};
/**
 * Registry entry for Chat, narrowed to its constant classification.
 */
export type ChatEntry = RegistryEntry & {
  /**
   * Always `chat`.
   */
  id?: 'chat';
  /**
   * Always `users`.
   */
  icon?: 'users';
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
  defaults?: ChatSettings;
};
/**
 * Registry entry for Cosmetics, narrowed to its constant classification.
 */
export type CosmeticsEntry = RegistryEntry & {
  /**
   * Always `cosmetics`.
   */
  id?: 'cosmetics';
  /**
   * Always `star`.
   */
  icon?: 'star';
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
  defaults?: CosmeticsSettings;
};
/**
 * Lower-case slug: letters, digits and single hyphens, e.g. `sword-pvp`. Unique within a user's library.
 */
export type LoadoutId = string;
/**
 * The subset of mod ids whose `kind` is `hud`, i.e. the 19 mods that own a draggable HUD item. A mod may only appear in `loadout.hud` if it is listed here.
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
  | 'scoreboard'
  | 'potion_counter'
  | 'reach';
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
 * Ordered list of HUD item placements. Order is paint order, back to front. At most one entry per mod id — so at most 19, one per `hud_mod_id`; that uniqueness is a `void-loadout` invariant rather than a schema constraint, since JSON Schema cannot express uniqueness by key.
 *
 * @maxItems 19
 */
export type HUDLayout = HUDItem[];
/**
 * Up to eight distinct mod ids, in quick-panel order. An id this build does not know is dropped on read rather than refused, so a loadout pinned on a newer client still loads.
 *
 * @maxItems 8
 */
export type QuickPins = ModId[];
/**
 * Every message on the localhost WebSocket between the Rust launcher (server, `void-bridge`) and the Java mod (client, `net/`), as specified in PVP_ARCHITECTURE.md §7. One JSON object per WS text frame, discriminated on `t`. The link carries state and telemetry summaries only, never per-frame data (§2): telemetry that is drawn never leaves the JVM. Forward compatibility rule of §7: a receiver ignores an unknown `t` and ignores unknown fields, which is why every message here sets additionalProperties true. `v` is the protocol version and is carried only on the two handshake messages, `hello` and `init`; a mismatch makes the launcher refuse to launch and prompt for an update, since mod and launcher ship together. Connection parameters reach the JVM as -Dvoid.port and -Dvoid.token (§6.9).
 */
export type ProtocolMessage = JavaToRust | RustToJava;
/**
 * The fourteen messages the mod sends to the launcher. Validate an inbound frame in `void-bridge` against this.
 */
export type JavaToRust =
  | HelloJavaToRust
  | StateJavaToRust
  | HudJavaToRust
  | GlobalsJavaToRust
  | SessionJavaToRust
  | ServerJavaToRust
  | HotkeyJavaToRust
  | CosmeticsQueryJavaToRust
  | EmoteJavaToRust
  | EquipJavaToRust
  | LoadoutSaveJavaToRust
  | LoadoutDeleteJavaToRust
  | LoadoutSelectJavaToRust
  | OpenStoreJavaToRust;
/**
 * Version of this message set. Bumped on any breaking change. Carried on `hello` and `init` only; both sides compare and refuse to proceed on a mismatch (§7). **2** since `init.loadouts` changed from `loadout_summary` to whole loadouts: a v1 mod would read a v2 `init` without complaint, but a v2 mod against a v1 launcher would receive summaries, materialise every mod at its factory default, and silently apply the wrong loadout on a switch. That is exactly the mixed-halves case `v` exists to refuse.
 */
export type ProtocolVersion = 2;
/**
 * Closed set of the global hotkeys the mod reports. `loadout.next` is the L-key cycle of §6.3, already applied when this is sent. `overlay` is the menu key (Right Shift by default) opening or closing VoidMenuScreen; the launcher uses it only for presence, since the menu is entirely in-game.
 */
export type HotkeyId = 'loadout.next' | 'overlay';
/**
 * A Minecraft profile UUID. Rust always sends the dashed lowercase form and accepts the undashed form from the mod.
 */
export type MinecraftUUID = string;
/**
 * Kebab-case, globally unique, e.g. `void-cape-rift`. Defined in `cosmetics.json`.
 */
export type CosmeticItemId = string;
/**
 * A wearable slot, one item each. `back` is wings and other back items, `head` hats, `face` masks, `halo` floats above the head and may coexist with a hat, `trail` particles while moving, `badge` the icon beside the nametag. Emotes are not a slot.
 */
export type CosmeticSlot = 'cape' | 'back' | 'head' | 'face' | 'halo' | 'trail' | 'badge';
/**
 * The six messages the launcher sends to the mod. Validate an inbound frame in the mod's `net/` package against this.
 */
export type RustToJava =
  | InitRustToJava
  | LoadoutRustToJava
  | SettingsRustToJava
  | CosmeticsRustToJava
  | EmoteRustToJava
  | LibraryRustToJava;
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
  | ModactionEvent
  | ScoreboardEvent
  | ToastEvent
  | CosmeticsEvent
  | EmoteEvent
  | WheelEvent
  | BindsEvent;
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
 * Minecraft's own key bindings (`GameOptions.allKeys`) as the player has them set, so the Keybinds tab and the rebind conflict check warn against the player's real binds rather than the 1.8.9 defaults. Pushed on `pushWholeState()` and again when the menu opens with a table that changed since the last push (the player rebound something in vanilla Controls). Whole table, never a delta. A page that has not had it falls back to the 1.8.9 defaults. Keys vanilla hard-codes (F1 hide HUD, F3 debug) are not bindings and are not listed.
 *
 * @maxItems 64
 */
export type BindsPayload = VanillaBind[];
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
  | SetGlobalCall
  | CreateLoadoutCall
  | RenameLoadoutCall
  | DuplicateLoadoutCall
  | DeleteLoadoutCall
  | ExportLoadoutCall
  | ImportLoadoutCall
  | SetQuickPinsCall
  | SetClipboardCall
  | GetClipboardCall
  | EquipCosmeticCall
  | SetCosmeticTintCall
  | SetEmoteWheelCall
  | PlayEmoteCall
  | OpenStoreCall;
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
  | 'culling'
  | 'particles'
  | 'frame_limiter'
  | 'block_outline'
  | 'shiny_pots'
  | 'clear_glass'
  | 'nametags'
  | 'gui_scale'
  | 'fog'
  | 'menu_blur'
  | 'time_weather'
  | 'chat'
  | 'cosmetics';
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
 * [name].
 *
 * @minItems 1
 * @maxItems 1
 */
export type CreateLoadoutParams = [string];
/**
 * [id, name].
 *
 * @minItems 2
 * @maxItems 2
 */
export type RenameLoadoutParams = [LoadoutId, string];
/**
 * [id].
 *
 * @minItems 1
 * @maxItems 1
 */
export type DuplicateLoadoutParams = [LoadoutId];
/**
 * [id].
 *
 * @minItems 1
 * @maxItems 1
 */
export type DeleteLoadoutParams = [LoadoutId];
/**
 * [id].
 *
 * @minItems 1
 * @maxItems 1
 */
export type ExportLoadoutParams = [LoadoutId];
/**
 * [code].
 *
 * @minItems 1
 * @maxItems 1
 */
export type ImportLoadoutParams = [string];
/**
 * [ids].
 *
 * @minItems 1
 * @maxItems 1
 */
export type SetQuickPinsParams = [ModId[]];
/**
 * [text].
 *
 * @minItems 1
 * @maxItems 1
 */
export type SetClipboardParams = [string];
/**
 * No arguments.
 *
 * @maxItems 0
 */
export type GetClipboardParams = any[];
/**
 * [slot, id].
 *
 * @minItems 2
 * @maxItems 2
 */
export type EquipCosmeticParams = [CosmeticSlot, CosmeticItemId | null];
/**
 * [slot, hex].
 *
 * @minItems 2
 * @maxItems 2
 */
export type SetCosmeticTintParams = [CosmeticSlot, CosmeticTint | null];
/**
 * A per-slot recolour, `#rrggbb`. The same pattern `protocol.json`'s `equipped_set.tints` uses; Java accepts either case and answers lower case.
 */
export type CosmeticTint = string;
/**
 * [ids].
 *
 * @minItems 1
 * @maxItems 1
 */
export type SetEmoteWheelParams = [CosmeticItemId[]];
/**
 * [id].
 *
 * @minItems 1
 * @maxItems 1
 */
export type PlayEmoteParams = [CosmeticItemId];
/**
 * [id].
 *
 * @minItems 1
 * @maxItems 1
 */
export type OpenStoreParams = [CosmeticItemId];
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
  | SetGlobalResult
  | CreateLoadoutResult
  | RenameLoadoutResult
  | DuplicateLoadoutResult
  | DeleteLoadoutResult
  | ExportLoadoutResult
  | ImportLoadoutResult
  | SetQuickPinsResult
  | SetClipboardResult
  | GetClipboardResult
  | EquipCosmeticResult
  | SetCosmeticTintResult
  | SetEmoteWheelResult
  | PlayEmoteResult
  | OpenStoreResult;
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
 * The new loadout, or null when the name was empty or the library is full (128).
 */
export type CreateLoadoutReturns = Loadout | null;
/**
 * The name actually stored, after trimming, or null when the id is unknown or the name empty.
 */
export type RenameLoadoutReturns = string | null;
/**
 * The copy, or null when the id is unknown or the library is full.
 */
export type DuplicateLoadoutReturns = Loadout | null;
/**
 * True when the loadout was deleted; false when the id was unknown or it was the last one.
 */
export type DeleteLoadoutReturns = boolean;
/**
 * The share code, or null when the id is unknown.
 */
export type ExportLoadoutReturns = string | null;
/**
 * The answer of `importLoadout`.
 */
export type ImportResult =
  | {
      ok: true;
      loadout: Loadout;
      /**
       * Settings, mods, HUD items and pins the code carried that this build could not use.
       */
      dropped: number;
    }
  | {
      ok: false;
      /**
       * Why the code was refused: `empty` nothing was pasted; `format` it is not a share code or its data does not decode; `version` it was made by a newer format; `checksum` it was damaged in transit; `invalid` it decoded but carries no usable loadout; `full` the library already holds 128.
       */
      error: 'empty' | 'format' | 'version' | 'checksum' | 'invalid' | 'full';
    };
/**
 * The pins actually stored.
 *
 * @maxItems 8
 */
export type SetQuickPinsReturns = ModId[];
/**
 * True when the host put it on the clipboard.
 */
export type SetClipboardReturns = boolean;
/**
 * The clipboard's text, cut to 16384 characters; empty when there is none.
 */
export type GetClipboardReturns = string;
/**
 * The id now worn in that slot, or null when it is empty. Equals the request on success; on a refusal it is whatever was already there.
 */
export type EquipCosmeticReturns = CosmeticItemId | null;
/**
 * The tint now applied, lower case, or null when the slot has none.
 */
export type SetCosmeticTintReturns = CosmeticTint | null;
/**
 * The wheel as stored, after dropping what could not be kept.
 *
 * @maxItems 8
 */
export type SetEmoteWheelReturns = CosmeticItemId[];
/**
 * True when the emote started; false when it was refused.
 */
export type PlayEmoteReturns = boolean;
/**
 * True when the launcher was told; false when there is no launcher link.
 */
export type OpenStoreReturns = boolean;

/**
 * The closed registry of every mod VOID ships — the 12 defined in PVP_ARCHITECTURE.md §3, the VOID watermark, and the readouts added since — together with the per-mod settings sub-schema, the anti-cheat classification of §11, the Mods-panel `category` taxonomy of Figma 244:538 and the factory defaults. This file is the single source of truth for mod identity, display copy and classification: `loadout.json` and `bridge.json` both $ref its `mod_id` enum and its `<id>_settings` definitions, so a mod is added in exactly one place, and no consumer re-declares a label or a filter tab. An instance of this schema is a registry document; the registry VOID actually ships is `examples[0]`.
 */
export interface ModRegistryDocument {
  version: RegistryVersion;
  mods: Mods;
}
/**
 * Every mod VOID ships, keyed by its snake_case mod id. Closed set: all 45 keys are required and no others are permitted.
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
  culling: EntityCullingEntry;
  particles: ParticleLimiterEntry;
  frame_limiter: FrameLimiterEntry;
  block_outline: BlockOutlineEntry;
  shiny_pots: ShinyPotsEntry;
  clear_glass: ClearGlassEntry;
  nametags: NametagsEntry;
  scoreboard: ScoreboardEntry;
  gui_scale: GUIScaleEntry;
  fog: FogEntry;
  menu_blur: MenuBlurEntry;
  time_weather: TimeWeatherEntry;
  potion_counter: PotionCounterEntry;
  reach: ReachDisplayEntry;
  chat: ChatEntry;
  cosmetics: CosmeticsEntry;
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
   * Ground drawn behind the FPS tile, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the FPS tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the FPS tile — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the key tiles, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the key tiles, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the key tiles — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
  /**
   * Whether keys ease between the resting and pressed treatment instead of switching on the frame. The press itself is always immediate; this only shapes the release.
   */
  animate?: boolean;
  /**
   * How long a released key takes to fade back to rest, in milliseconds, when `animate` is on. Long fades read as a trail of recent presses; short ones as a crisp tap.
   */
  fade_ms?: number;
}
/**
 * Settings for the CPS counter HUD mod. Derived entirely in JS from mouse edges on the `keys` event (§3), so no Java sensor exists for it.
 */
export interface CPSCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the CPS tile, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the CPS tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the CPS tile — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
  /**
   * Whether only clicks the game acted on are counted. A left click swallowed by 1.8's miss delay, or made while a screen was open, is a press of the button but not a swing, and with this on it does not count. Left button only; right clicks are always counted as pressed. Fed by `hits.swings` on the tick payload rather than by the mouse edge, so a counted click is one `doAttack` ran for.
   */
  ignore_cancelled?: boolean;
}
/**
 * Settings for the Ping display HUD mod. Reads the player's own `NetworkPlayerInfo.responseTime`.
 */
export interface PingDisplaySettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the ping tile, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the ping tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the ping tile — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Whether the rolling average of the last `window` readings is drawn as a trailing aside, `avg 54`.
   */
  show_average?: boolean;
  /**
   * Whether jitter is drawn as a trailing aside, `±7`: the mean change between consecutive readings over the last `window`. A steady 90 ms plays better than a 40 ms that swings by 30, and only this figure tells the two apart.
   */
  show_jitter?: boolean;
  /**
   * Jitter at or above this many milliseconds draws in the warn treatment. 0 disables the warning.
   */
  jitter_warn_ms?: number;
  /**
   * A reading this many milliseconds above the rolling average counts as a spike and briefly marks the chip. 0 disables spike detection.
   */
  spike_ms?: number;
  /**
   * How many readings the average, the jitter and the spike baseline are taken over. The server sends a new latency every few seconds, so 10 readings is roughly the last half minute.
   */
  window?: number;
}
/**
 * Settings for the Coordinates HUD mod. Reads `EntityPlayerSP` position and yaw once per tick.
 */
export interface CoordinatesSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the coordinates tile, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the coordinates tile, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the coordinates tile — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the armor row, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the armor row, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the armor row — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the effect list, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the effect list, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the effect list — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
  /**
   * Effects never listed, as a bit mask over vanilla's status effect ids: bit 1 is Speed, bit 16 Night Vision, up to bit 23 Saturation. Set from the settings page's effect chips rather than by hand. 0 lists everything.
   */
  hidden_effects?: number;
  /**
   * An effect with this many seconds or fewer left blinks, once a second, on the tick that redraws its timer. 0 disables blinking.
   */
  blink_below_s?: number;
}
/**
 * Settings for the VOID watermark HUD mod. It reads nothing from the game: the overlay draws the mark itself, which is why its `source` says so rather than naming a 1.8.9 field. Modelled on `fps_settings` and `crosshair_settings`, and deliberately without a `color`: `design/quiet-cell-system.md` §1 reserves colour for a live value or a selected item, and a watermark is neither.
 */
export interface VOIDWatermarkSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the mark, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the mark, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the mark — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
  /**
   * Whether a box changes colour with what you can do to its entity: `damaged_color` while it is in its hurt animation, `hittable_color` while your crosshair is on it within attack range, and `color` otherwise.
   */
  state_colors?: boolean;
  hittable_color?: Colour;
  damaged_color?: Colour;
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
   * Whether the mouse wheel changes the zoom while the key is held, between 1.1x and 10x in multiplicative steps. The hotbar does not scroll while this is in use, and the level goes back to `fov_divisor` on release. Eased when `smooth` is on.
   */
  scroll_adjust?: boolean;
  /**
   * Whether mouse sensitivity is scaled down with the zoom, so a flick moves the crosshair across the same share of the screen zoomed as unzoomed. Off is vanilla: at 4x every mouse movement turns the view four times as far across what you can see.
   */
  scale_sensitivity?: boolean;
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
   * Ground drawn behind the direction chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the direction chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the direction chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the combo chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the combo chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the combo chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the saturation readout, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the saturation readout, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the saturation readout — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the speed chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the speed chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the speed chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the memory chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the memory chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the memory chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
   * Ground drawn behind the host chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the host chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the host chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
  /**
   * How much of the host is printed. `short` keeps the part players actually say out loud — `hypixel` out of `mc.hypixel.net` — and is the default because on a chip the leading `mc.` and the trailing `.net` are the two pieces that never differ between the servers a player switches between, so they cost width and carry no information. `full` prints the address exactly as it was connected to, which is what you want on a network with several proxies, on a bare IP, or in a screenshot that has to be reproducible by somebody else.
   */
  style?: 'short' | 'full';
}
/**
 * Settings for the Item counter HUD mod. `track` picks what is counted. `held_stack` is the stack in the hand (`held_count` on the tick payload) and is the default, because it is what the chip has always shown. Every other value reads `counts`, the inventory-wide sensor: `held_total` adds up every stack in the inventory that matches the held item (same item, same variant, so white wool is not red wool), and the named items count that item wherever it is, whether or not it is in your hand. That last group is the Lunar and Badlion meaning of an item counter: tell it pearls and it counts your pearls while you hold a sword.
 *
 * The counts are value-checked at the sensor rather than rate-limited: they change on use and pickup, not on a clock, so every push is news.
 */
export interface ItemCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the count chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the count chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the count chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
  /**
   * Whether the count is prefixed with the multiplication sign — `x12` rather than `12`. On by default: the chip sits next to a CPS figure and above a keystrokes block, so a bare integer in that corner is a number among numbers, and the `x` is the cheapest thing that says it is a quantity of something rather than a rate.
   */
  show_label?: boolean;
  /**
   * A count at or below this many draws in the warn treatment instead of the normal ink. **0 disables the warning entirely, and 0 is the default**, because what counts as low depends completely on what is in the hand: eight is nearly out of blocks and a generous stash of pearls, and a client that guessed one number would be wrong for every player who does not build the way it assumed. So VOID does not guess — a player who knows what they are counting sets it, and everybody else gets a chip that never cries wolf. The ceiling is 64: a full stack, above which the warning would be permanently on.
   */
  low_threshold?: number;
  /**
   * What the chip counts. `held_stack` is the stack in your hand; `held_total` is every stack in the inventory matching the held item; the rest count that item across the whole inventory whatever you are holding. `wool` is every colour together. Healing potions and soup are the Potion counter's.
   */
  track?:
    | 'held_stack'
    | 'held_total'
    | 'ender_pearl'
    | 'golden_apple'
    | 'arrow'
    | 'snowball'
    | 'egg'
    | 'cobweb'
    | 'wool'
    | 'tnt'
    | 'fire_charge'
    | 'iron_ingot'
    | 'gold_ingot'
    | 'diamond'
    | 'emerald';
}
/**
 * Settings for the Stopwatch HUD mod. Along with the watermark it is one of two mods with no game field behind it: the elapsed time is the overlay's own, counted from `Date.now()` exactly as the combo chip already counts its own timeout, and no sensor field, no tick payload and no wire message carries it. What does cross the bridge is the *input*. `start_key` and `reset_key` are dispatched by Java and arrive as `modaction` — `{mod: 'stopwatch', action: 'start_stop' | 'reset'}` — because a timer's keys ask the page to do something rather than flipping a boolean, which is all the existing per-mod `keybind` hotkeys can say. Java therefore owns the key and the page owns the clock, and the two never disagree about elapsed time because only one of them counts it.
 */
export interface StopwatchSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the stopwatch chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the stopwatch chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the stopwatch chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
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
 * Settings for the Entity culling gameplay mod — the FPS boost. A background thread casts rays from the camera through the block grid to each entity and block entity in view, and the render pass skips anything no ray reaches through air. Results trail the camera by one pass (a few milliseconds), so everything within `always_draw_within` is drawn regardless — a player who steps round a corner beside you is never one pass late. The distance cut-offs are plain distance culling on top, for the cheap-but-numerous things a lobby is full of. Turns itself off for occlusion in third person, where the camera is not where the rays start; distance culling still applies.
 */
export interface EntityCullingSettings {
  on: Enabled;
  /**
   * Whether mobs, players, items and other entities that no ray from the camera can reach are skipped. The bulk of the win in a crowded lobby. Never culls the entity you are riding or your own player.
   */
  occlude_entities?: boolean;
  /**
   * Whether chests, signs, banners, skulls and the other tile entities that no ray reaches are skipped. Tile entities are the expensive half of a 1.8.9 frame in a built-up map — every chest is a model drawn every frame — so this is on by default.
   */
  occlude_block_entities?: boolean;
  /**
   * Radius in blocks inside which nothing is ever occlusion-culled. The ray test runs on its own thread and its answer is one pass old by the time a frame reads it; inside this radius that lag could show as a player appearing a moment after stepping into view, so the test is not consulted at all. 4 covers melee range with a margin.
   */
  always_draw_within?: number;
  /**
   * Whether a culled entity still draws its nametag. Vanilla draws a player's name through walls (unless they sneak), so culling the body must not take the name with it — on by default, which keeps exactly what vanilla shows. Off also skips the label of anything culled, which is a little faster and shows less than vanilla, never more.
   */
  nametags_through_walls?: boolean;
  /**
   * Farthest a tile entity is drawn, in blocks. 64 is vanilla's own limit, so the default changes nothing; lower it on a weak machine in chest-heavy maps.
   */
  block_entity_distance?: number;
  /**
   * Farthest an armour stand is drawn, in blocks. Lobby holograms are stacks of invisible armour stands with a name each, and hundreds of them is common. 64 is vanilla; lowering it hides far hologram text along with the stands.
   */
  armor_stand_distance?: number;
  /**
   * Farthest the item inside an item frame is drawn, in blocks. The frame itself is unaffected. 64 is vanilla.
   */
  item_frame_distance?: number;
  /**
   * Farthest a sign's text is drawn, in blocks; the board is drawn to the tile-entity distance either way. Sign text is a font draw per line per sign per frame, and past about 32 blocks a line of it is two or three pixels tall at 1080p — unreadable, but still paid for. 32 by default for that reason; 64 is vanilla.
   */
  sign_text_distance?: number;
}
/**
 * Settings for the Particle limiter gameplay mod. Vanilla builds the vertices of every live particle every frame, including the half behind you, and a TNT run or a Bedwars bed break can put thousands on screen at once. `frustum_cull` is free — a particle outside the view cone is never seen — and is the default win. The two fractions thin a burst at the moment it is spawned; they only ever remove particles, never add one, which is the direction Hypixel's allowlist has no objection to. Crit, sharpness and hit particles are untouched by both: they are the cue that a hit landed.
 */
export interface ParticleLimiterSettings {
  on: Enabled;
  /**
   * Whether particles outside the camera's view cone skip their draw. Invisible by construction, which is why it is on by default and why it is a switch rather than a number.
   */
  frustum_cull?: boolean;
  /**
   * Most particles alive per render layer; when full, the oldest goes first, exactly as vanilla does at its own limit. 4000 is vanilla's limit, so the default changes nothing — lower it if explosions stall your frame.
   */
  particle_cap?: number;
  /**
   * Fraction of block-break and block-hit particles kept. 1 is vanilla's 64-piece burst; 0 breaks blocks with no debris. A bridging or Bedwars player breaks hundreds of blocks a game and every one used to spawn 64 particles.
   */
  block_break_particles?: number;
  /**
   * Fraction of explosion particles kept — TNT, fireballs and creepers. 1 is vanilla. The blast itself, its sound and its knockback are untouched; only the smoke is thinned.
   */
  explosion_particles?: number;
}
/**
 * Settings for the Frame limiter gameplay mod. None of these caps ever applies while you are in the world with the window focused — that frame rate is the one you set in Minecraft's options, untouched. Vanilla 1.8.9 renders a backgrounded or minimised window as fast as it can, so a game left on a second monitor heats the GPU the player is about to need for something else. The menu cap covers the title screen, the server list and the pause menu; it never covers an inventory or a chest, because the fight carries on behind those.
 */
export interface FrameLimiterSettings {
  on: Enabled;
  /**
   * Frame cap while the game window does not have focus. 30 keeps a game on a second monitor watchable while giving most of the GPU back.
   */
  unfocused_fps?: number;
  /**
   * Frame cap on the title screen, the server list and the pause menu. Vanilla already holds the title screen at 30; 60 is smoother than that and still a fraction of an uncapped menu.
   */
  menu_fps?: number;
  /**
   * Whether a minimised window drops to 5 frames a second. Nothing is visible, so nothing is lost; sound and the connection carry on.
   */
  idle_minimized?: boolean;
}
/**
 * Settings for the Block outline gameplay mod. It restyles the box vanilla already draws round the targeted block — same block, same shape, same moment — and nothing else; it never outlines a block the game would not, so it says nothing the vanilla outline did not. `kind: gameplay` for `hitboxes`' reason: it is a world mark, not a placeable HUD item.
 */
export interface BlockOutlineSettings {
  on: Enabled;
  /**
   * What is drawn round the targeted block. `outline` is vanilla's box in your colour; `fill` shades the faces and drops the lines; `both` does both; `none` draws nothing, for players who aim off the crosshair alone.
   */
  outline_style?: 'outline' | 'fill' | 'both' | 'none';
  outline_color?: Colour;
  /**
   * Line width in screen pixels. Vanilla's is 2.
   */
  outline_width?: number;
  fill_color?: Colour;
}
/**
 * Settings for the Shiny pots gameplay mod. 1.8.9 gives a potion the glint only when its effect list resolves, at vanilla strength, so on a busy hotbar a pot reads no differently from a water bottle. This draws the same glint pass vanilla uses, more times and on every potion, and can tint it with the potion's own colour. Purely the item's look: it changes nothing about which potion is which or what is in your inventory.
 */
export interface ShinyPotsSettings {
  on: Enabled;
  /**
   * Whether potions with no listed effect — water bottles, and server pots whose effects live only in their lore — glint too. On by default: on most servers that is exactly the pot you were trying to find.
   */
  all_potions?: boolean;
  /**
   * How many times the glint is laid over a potion. 1 is vanilla's strength; 2 is the classic 1.7 look; 3 is as shiny as it gets before the item's own colour washes out.
   */
  glint_passes?: number;
  /**
   * Whether the glint takes the potion's own liquid colour instead of vanilla's purple, so a healing pot shimmers red and a speed pot blue.
   */
  tint_glint?: boolean;
}
/**
 * Settings for the Clear glass gameplay mod. Glass in 1.8.9 is a cut-out texture whose frame and streaks cover a quarter of what is behind it; this skips glass when chunks are built, so the block is still there, still solid and still targetable — the block outline still finds it — but nothing is drawn. It is what a clear-glass resource pack does, without the pack. Changing a setting rebuilds the loaded chunks once.
 */
export interface ClearGlassSettings {
  on: Enabled;
  /**
   * Whether glass panes are cleared as well as full glass blocks.
   */
  clear_panes?: boolean;
  /**
   * Whether stained glass is cleared too. Off by default: stained glass carries team colour in Bedwars and bases, and clearing it would throw that away.
   */
  clear_stained?: boolean;
}
/**
 * Settings for the Nametags gameplay mod. Restyles the label vanilla already draws over a player or named mob — the same labels, at the same moments, through the same walls — so it never shows a name the game would not. `tag_scale` tops out at 1.5 for that reason: a bigger label is legible farther, but it cannot appear anywhere vanilla's would not.
 */
export interface NametagsSettings {
  on: Enabled;
  /**
   * Whether your own nametag is drawn over your head in third person. Vanilla never draws it; on by default because that is the one thing players switch this mod on for.
   */
  show_own?: boolean;
  /**
   * Whether other players' and named mobs' nametags are hidden. Armour stands are exempt: lobby holograms are stacks of named stands, and they are world text rather than nametags. Off by default — a name is how you tell a teammate from an enemy — and on for recording and for crowded lobbies.
   */
  hide_others?: boolean;
  /**
   * Alpha of the dark plate behind each name. 0.25 is vanilla's; 0 draws the text alone.
   */
  tag_background?: number;
  /**
   * Size of every nametag as a multiple of vanilla's.
   */
  tag_scale?: number;
  /**
   * Whether the name is drawn with Minecraft's text shadow, which keeps it legible against sky and snow. Vanilla draws it without one.
   */
  tag_shadow?: boolean;
}
/**
 * Settings for the Scoreboard HUD mod. Reads the sidebar objective the server set — title, lines and scores — exactly as vanilla would draw it, team prefixes and suffixes included, and draws it on the HUD where the editor puts it. It adds nothing the sidebar did not say.
 */
export interface ScoreboardSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the scoreboard, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the scoreboard, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the scoreboard — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
  /**
   * Whether the scoreboard is hidden entirely — vanilla's and this one. For recording, and for servers whose sidebar is an advert.
   */
  hidden?: boolean;
  /**
   * Whether each line's score is drawn at its right edge. Off by default: on nearly every server the score is a sort key the server uses to order the lines, the red numbers carry no meaning, and hiding them is the single most requested scoreboard tweak.
   */
  show_numbers?: boolean;
  /**
   * Whether the objective's title is drawn above the lines.
   */
  show_title?: boolean;
  /**
   * Whether the server's own colour codes are honoured. On by default: on team modes the colour is the information. Off draws every line in the HUD's own ink.
   */
  server_colors?: boolean;
}
/**
 * Settings for the GUI scale gameplay mod. Minecraft has one GUI scale for everything, so at 1080p the choice is between a hotbar that fills the bottom of the screen and an inventory too small to read. Each value here is a whole GUI-scale step, exactly like Minecraft's own setting, so textures stay pixel-crisp; 0 means "follow Minecraft". Like vanilla, a value larger than the window allows is clamped to the largest that fits.
 */
export interface GUIScaleSettings {
  on: Enabled;
  /**
   * GUI scale step for the hotbar and the health, hunger, armour and experience bars above it, which move as one block. 0 follows Minecraft. 2 by default: a compact hotbar at 1080p, where Minecraft's own auto scale is 4.
   */
  hotbar_scale?: number;
  /**
   * GUI scale step for the inventory, chests, furnaces and every other container screen. 0 follows Minecraft. Other menus — chat, the pause menu, VOID's own — are unaffected.
   */
  inventory_scale?: number;
}
/**
 * Settings for the Fog gameplay mod. Each kind of fog has three steps: `vanilla`, `reduced` and `off`. Distance fog hides the edge of the loaded world, so turning it off shows the chunk border instead of a haze — `reduced` pushes the haze out to the edge. Classified `grey`; see the top of this file.
 */
export interface FogSettings {
  on: Enabled;
  /**
   * Fog at the edge of your render distance, and the thick fog of the nether. `reduced` starts it at the last chunk rather than three quarters of the way out.
   */
  distance_fog?: 'vanilla' | 'reduced' | 'off';
  /**
   * Fog while your head is under water. `reduced` is roughly what Respiration III gives.
   */
  water_fog?: 'vanilla' | 'reduced' | 'off';
  /**
   * Fog while your head is in lava. `vanilla` by default: seeing out of lava is the clearest advantage on this page.
   */
  lava_fog?: 'vanilla' | 'reduced' | 'off';
}
/**
 * Settings for the Menu blur gameplay mod. Runs the backdrop blur VOID already carries — a quarter-resolution copy of the frame through a separable Gaussian — under Minecraft's own screens. Chat and VOID's own menu are not blurred. Costs a few frames a second while a screen is open and nothing otherwise.
 */
export interface MenuBlurSettings {
  on: Enabled;
  /**
   * How soft the blur is, in passes of the Gaussian. 1 is a light softening; 10 is unrecognisable.
   */
  blur_radius?: number;
  /**
   * Whether the blur eases in over a fifth of a second as the screen opens, rather than snapping on.
   */
  blur_fade?: boolean;
  /**
   * How much of vanilla's dark wash is kept over the blur, as a fraction of vanilla's own. 1 is vanilla's darkness on top of the blur; 0 is the blur alone.
   */
  menu_dim?: number;
}
/**
 * Settings for the Time & weather gameplay mod. Client-side only: the server's clock and weather are unchanged, mobs still burn when the server says it is day, and nobody else sees a difference. It changes the sky, the light and the rain you see.
 */
export interface TimeWeatherSettings {
  on: Enabled;
  /**
   * Whether the time of day is held at `time_ticks` instead of following the server.
   */
  lock_time?: boolean;
  /**
   * Time of day to hold, in ticks: 0 is sunrise, 6000 noon, 12000 sunset, 18000 midnight.
   */
  time_ticks?: number;
  /**
   * Whether rain, snow and thunder are hidden. The rain also stops darkening the sky.
   */
  clear_weather?: boolean;
}
/**
 * Settings for the Potion counter HUD mod. Reads `counts` on the tick payload, the same inventory-wide sensor the Item counter's named items use: `health_potion` is every splash potion of Healing (I and II) in the inventory and `soup` is every bowl of mushroom stew. A separate mod rather than two more `item_counter.track` values because the two are read at different moments: the item counter answers what you are building or throwing with, and this answers how many heals are left, which is the one number a pot or soup fight is decided by and the one worth a chip of its own.
 */
export interface PotionCounterSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the potion chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the potion chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the potion chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
  /**
   * What is counted: splash potions of Healing, bowls of mushroom stew, or both side by side.
   */
  track?: 'health_potion' | 'soup' | 'both';
  /**
   * Whether the count is followed by its unit, `pots` or `soup`. With `both` the units are what tell the two figures apart, so they are always drawn there.
   */
  show_label?: boolean;
  /**
   * A count at or below this many draws in the warn treatment. Unlike the Item counter this has a non-zero default, because what is in the slot is known: three heals is low in every pot and soup mode. 0 disables the warning.
   */
  low_threshold?: number;
}
/**
 * Settings for the Reach display HUD mod. Reads `reach` on the tick payload. The figure is measured on the client at the moment the attack is sent: from your eye position, interpolated to the frame the crosshair ray was cast on, to the exact point where that ray met the target's hitbox. That is the distance the game itself used to decide the hit, not an entity-centre distance (which reads about 0.3 blocks long) and not the server's view (which it never tells the client). Only attacks the hit counter counts as landed are measured, so a swing at air never moves the number.
 */
export interface ReachDisplaySettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Ground drawn behind the reach chip, as a step on the system's own scale rather than a colour. `none` is the vanilla treatment and the default — the readout sits on the game. `subtle` is the card ground at low alpha, which is enough to hold a chip together over a busy texture; `solid` is the opaque card ground, for a player who wants the HUD to read as a panel. A step rather than a hex value because a per-mod background colour is what §1 names as the far side of the line.
   */
  background?: 'none' | 'subtle' | 'solid';
  /**
   * Whether a hairline is drawn around the reach chip, at the system's own `--border-panel` alpha. Boolean rather than a colour or a width for the same reason as `background`: the edge either separates the chip from the game or it does not, and the one useful answer is already a token.
   */
  border?: boolean;
  /**
   * Density of the reach chip — the inset between its content and its edge, as one of three steps. `density` is named in §1 as legitimate customisation, and it is what a player actually means by 'make the HUD smaller' when `scale` has already made the text too small to read.
   */
  padding?: 'tight' | 'normal' | 'roomy';
  /**
   * Digits after the decimal point. Two is the precision the measurement actually has; one reads faster, and none is the whole-block figure.
   */
  decimals?: number;
  /**
   * Which figure is drawn: the last landed hit, or the mean of the last ten.
   */
  mode?: 'last' | 'average';
  /**
   * Whether the figure is followed by its unit, `blocks`.
   */
  show_label?: boolean;
  /**
   * Hide the chip this long after the last landed hit; 0 keeps it on screen. A reach figure is only interesting while you are trading hits.
   */
  hide_after_ms?: number;
}
/**
 * Settings for the Chat gameplay mod. Every setting changes how vanilla's own chat (`ChatHud`) stores and draws messages on this client; nothing is sent, and no message anyone else sees is altered. With the mod off the chat is vanilla, byte for byte.
 */
export interface ChatSettings {
  on: Enabled;
  /**
   * Whether each message is prefixed with the time it arrived, `[14:03]`, in grey.
   */
  timestamps?: boolean;
  /**
   * Whether a message identical to the one before it replaces it with a count, `(x3)`, instead of adding another line. Lobby spam and repeated server notices collapse to one line.
   */
  stack_repeats?: boolean;
  /**
   * Whether right-clicking a message in the open chat copies the whole message, without colour codes, to the clipboard.
   */
  click_to_copy?: boolean;
  /**
   * Opacity of the dark band behind chat lines, as a fraction of the line's own fade. 0.5 is vanilla; 0 draws text only.
   */
  background_opacity?: number;
  /**
   * How many messages the chat keeps for scrolling back. Vanilla keeps 100.
   */
  history_length?: number;
}
/**
 * Settings for the Cosmetics gameplay mod. What you wear is chosen in the wardrobe and stored by the launcher against your account; these settings are only about what this client draws. Off hides every cosmetic, yours included, and stops answering other players' emotes — but your own set is untouched, and other VOID players still see it.
 */
export interface CosmeticsSettings {
  on: Enabled;
  /**
   * Whether other VOID players' cosmetics are drawn. On by default, because seeing who else plays VOID is half the point. Off draws only your own — for a crowded lobby, or for a player who wants the fight in front of them to look like vanilla. Their emotes still animate their model; only what they wear is hidden.
   */
  others?: boolean;
  /**
   * Whether your own trail is drawn while you are in first person. Off by default: a trail is something other people see you leave, and in first person it is particles at your own feet, on the edge of the frame you aim through. On is for the player who likes to watch it. Your cape, halo and the rest are never drawn in first person — the game does not draw your model there either.
   */
  own_first_person?: boolean;
  /**
   * How strongly emissive cosmetics glow — the bloom around halos, wing edges, lit trims and bright trails. `high` by default, because those items are designed around it: a soft, layered glow that is strong at night and barely there in daylight. `low` keeps a gentler, tighter glow at a little less cost. `off` draws the same shapes with their lit parts but no bloom at all, which is cheapest on a weak GPU and easiest on the eyes in a dark cave. Replaces the earlier on/off `glow` switch.
   */
  glow_level?: 'off' | 'low' | 'high';
  /**
   * Whether the camera swings to third person and orbits slowly while you emote, so you can see what you are doing. On by default. Off keeps your own view where it is: other players still see the emote, you just do not watch it. The camera never moves while you move — taking a step ends the emote and gives the view straight back.
   */
  emote_camera?: boolean;
  /**
   * How much cosmetics react to the fight. `full` by default: your cape whips when you are hit, your wings flare and your trail thickens as a combo builds, your halo brightens a step per hit and pulses on a kill, and everything dims a little when you are low on health — and other VOID players' cosmetics flinch and flare the same way from what your client already sees of their fight (hurt flashes, swings, deaths). `subtle` plays the same reactions at under half the size. `off` keeps cosmetics on their idle motion whatever happens. Purely visual either way: nothing here tells you anything the fight on screen does not.
   */
  reactive?: 'off' | 'subtle' | 'full';
  emote_key?: Keybind;
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
  quick?: QuickPins;
  stats?: LoadoutStats;
}
/**
 * Enabled state plus settings for each mod, keyed by the mod ids of mods.json. Every key is optional: a mod omitted here falls back to its `defaults` in the registry, which is what keeps old loadouts valid when a mod is added. No key outside the closed 45 is permitted.
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
  culling?: EntityCullingSettings;
  particles?: ParticleLimiterSettings;
  frame_limiter?: FrameLimiterSettings;
  block_outline?: BlockOutlineSettings;
  shiny_pots?: ShinyPotsSettings;
  clear_glass?: ClearGlassSettings;
  nametags?: NametagsSettings;
  scoreboard?: ScoreboardSettings;
  gui_scale?: GUIScaleSettings;
  fog?: FogSettings;
  menu_blur?: MenuBlurSettings;
  time_weather?: TimeWeatherSettings;
  potion_counter?: PotionCounterSettings;
  reach?: ReachDisplaySettings;
  chat?: ChatSettings;
  cosmetics?: CosmeticsSettings;
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
 * Telemetry summary the mod sends every 60 seconds and once more on exit (§7). Feeds the played-time, average-fps and average-ping numbers on the launcher's Home (Last session). Values are cumulative for the current game session, not deltas.
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
   * Mean of the player's own ping readings (`NetworkPlayerInfo.responseTime`) over the session so far, in milliseconds. Only readings taken on a server count, so it is absent until there has been one and singleplayer never sends it. Optional and additive: a launcher that predates it ignores it, as it does any unknown field.
   */
  ping_avg?: number;
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
 * The UUIDs of players the mod currently sees (tab list and rendered entities), whose cosmetics it wants. The mod batches, debounces and caches on its side; the launcher batch-fetches from the online backend (`cosmetics-lookup`) behind its own LRU cache and answers with one or more `cosmetics` messages. UUIDs that are not VOID players never appear in a reply: absence is the answer. The mod never talks HTTP; Rust owns network and auth (docs/online-contract.md).
 */
export interface CosmeticsQueryJavaToRust {
  /**
   * Message discriminator; always `cosmetics_query`.
   */
  t: 'cosmetics_query';
  /**
   * Minecraft UUIDs, dashed or undashed. At most 200 per message; send more as several.
   *
   * @maxItems 200
   */
  uuids: MinecraftUUID[];
  [k: string]: any | undefined;
}
/**
 * The local player played an emote from their wheel. Java has already started the animation locally; the launcher relays it to the other VOID players on the same server through the online backend, which checks ownership and stamps the sender's identity. Same `t` as the launcher's `emote`; the direction tells them apart, and this one carries no `uuid`.
 */
export interface EmoteJavaToRust {
  /**
   * Message discriminator; always `emote`.
   */
  t: 'emote';
  id: CosmeticItemId;
  [k: string]: any | undefined;
}
/**
 * The player equipped an item, or took one off with `id: null`, in the in-game wardrobe (`window.void.equipCosmetic`). Java has already applied it locally; the launcher persists it to the online backend, which validates ownership. If the backend refuses (not owned, wrong slot), the launcher answers with a `cosmetics` message whose `self` is the stored set, which reverts the local change.
 */
export interface EquipJavaToRust {
  /**
   * Message discriminator; always `equip`.
   */
  t: 'equip';
  slot: CosmeticSlot;
  /**
   * The item id, or null to empty the slot.
   */
  id: CosmeticItemId | null;
  [k: string]: any | undefined;
}
/**
 * Sent when the in-game Loadouts screen created, renamed, duplicated or imported a loadout, or changed its quick pins (`window.void` calls of the same names). Carries the whole loadout: Rust creates it at the end of the library order when the id is new and overwrites it otherwise, validating it first. Like `state`, a notification of what Java already did, not a request; if Rust cannot store it, it answers with `library` so the game shows what is actually on disk.
 */
export interface LoadoutSaveJavaToRust {
  /**
   * Message discriminator; always `loadout_save`.
   */
  t: 'loadout_save';
  loadout: Loadout;
  [k: string]: any | undefined;
}
/**
 * Sent when the in-game Loadouts screen deleted a loadout. Java has already removed it, and switched away from it first when it was active (that switch travels as its own `loadout_select`). Rust never deletes the last loadout on disk.
 */
export interface LoadoutDeleteJavaToRust {
  /**
   * Message discriminator; always `loadout_delete`.
   */
  t: 'loadout_delete';
  id: LoadoutId;
  [k: string]: any | undefined;
}
/**
 * Sent when the player picked a loadout in game other than with the cycle key, which reports itself as `hotkey` `loadout.next`. Moves Rust's active pointer so the tray and the next launch agree with the running game.
 */
export interface LoadoutSelectJavaToRust {
  /**
   * Message discriminator; always `loadout_select`.
   */
  t: 'loadout_select';
  id: LoadoutId;
  [k: string]: any | undefined;
}
/**
 * The player pressed *Get in launcher Store* on a locked item in game (`window.void.openStore`). Nothing is bought in game: the launcher shows and focuses its window and opens the Store on that item's sheet, where the purchase happens. A launcher that does not know the id opens the Store gallery alone.
 */
export interface OpenStoreJavaToRust {
  /**
   * Message discriminator; always `open_store`.
   */
  t: 'open_store';
  item: CosmeticItemId;
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
 * Equipped cosmetics. `self` is the local player's own set, sent after every `hello` and after any change (an equip from the launcher Store, a refused in-game equip, a purchase); null when the player has no online session (offline account, backend unreachable or not configured). `players` is a delta: other VOID players' sets that are new or changed since the last message, in reply to `cosmetics_query`; never a full roster, the mod keeps its own cache. Assets are bundled in the mod jar under `assets/void/cosmetics/<id>/`, so an id is all the mod needs; an id it does not have is skipped.
 */
export interface CosmeticsRustToJava {
  /**
   * Message discriminator; always `cosmetics`.
   */
  t: 'cosmetics';
  /**
   * The local player's equipped set, or null with no online session.
   */
  self: EquippedSet | null;
  /**
   * Other VOID players' sets, new or changed.
   */
  players: EquippedSet[];
  [k: string]: any | undefined;
}
/**
 * What one player wears. Every slot is present, null when empty. `emotes` is the player's wheel, at most 8, in wheel order. `tints` is an optional per-slot recolour for items flagged `tintable`, omitted when empty.
 */
export interface EquippedSet {
  uuid: MinecraftUUID;
  /**
   * Cape item id or null.
   */
  cape: CosmeticItemId | null;
  /**
   * Back item id or null.
   */
  back: CosmeticItemId | null;
  /**
   * Hat item id or null.
   */
  head: CosmeticItemId | null;
  /**
   * Mask item id or null.
   */
  face: CosmeticItemId | null;
  /**
   * Halo item id or null.
   */
  halo: CosmeticItemId | null;
  /**
   * Trail item id or null.
   */
  trail: CosmeticItemId | null;
  /**
   * Badge item id or null.
   */
  badge: CosmeticItemId | null;
  /**
   * Emote ids on the wheel, in order.
   *
   * @maxItems 8
   */
  emotes: CosmeticItemId[];
  /**
   * Slot name to `#rrggbb` recolour, for tintable items only.
   */
  tints?: {
    /**
     * The colour.
     */
    [k: string]: string | undefined;
  };
  [k: string]: any | undefined;
}
/**
 * Another VOID player on the same server played an emote. Relayed from the server's Realtime channel; the sender's identity was stamped by the backend, not claimed by the sender. The mod plays it on that player if it can see them and drops it otherwise.
 */
export interface EmoteRustToJava {
  /**
   * Message discriminator; always `emote`.
   */
  t: 'emote';
  uuid: MinecraftUUID;
  id: CosmeticItemId;
  /**
   * When it was played, Unix milliseconds on the backend's clock, so the mod can drop one that arrives stale.
   */
  at: number;
  [k: string]: any | undefined;
}
/**
 * The whole loadout library, in full and in library order, exactly as `init.loadouts`. Sent when the launcher created, deleted, imported or edited a loadout while the game is running, and as the answer to an in-game `loadout_save` or `loadout_delete` Rust could not apply. Java replaces its library with it, keeping its own live copy of the active loadout, and pushes the `loadouts` bridge event.
 */
export interface LibraryRustToJava {
  /**
   * Message discriminator; always `library`.
   */
  t: 'library';
  /**
   * Every loadout, in full and in library order.
   *
   * @minItems 1
   * @maxItems 128
   */
  loadouts: Loadout[];
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
   * Stack size of the held item, for the item counter. Absent when the hand is empty — a count of 0 and an empty hand are different states, and the widget draws nothing rather than a zero. Value-checked; a stack size changes on use, not on a clock.
   */
  held_count?: number;
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
    /**
     * Times `MinecraftClient.doAttack` has run since the session began, whatever it hit. Monotonic. A left click the game swallowed (1.8's miss delay, a screen open) never reaches `doAttack`, so the difference between this and the mouse edges on `keys` is exactly the cancelled clicks; `cps.ignore_cancelled` counts these instead of the edges. Optional so a sensor that predates it still validates.
     */
    swings?: number;
  };
  /**
   * Inventory-wide item counts, for the Item counter's named `track` values and the Potion counter. Every key is the total across the 36 main inventory slots (hotbar included, armour and the cursor stack excluded). `held_total` is every stack matching the held item, same item and same variant, and is absent with an empty hand; `wool` is every colour together; `health_potion` is splash potions of Healing I and II; `soup` is mushroom stew. Sent whole whenever any key changes and omitted otherwise, like `armor`: an absent field means unchanged. Value-checked, never rate-limited, because a count changes on use and pickup rather than on a clock.
   */
  counts?: {
    held_total?: number;
    ender_pearl?: number;
    golden_apple?: number;
    arrow?: number;
    snowball?: number;
    egg?: number;
    cobweb?: number;
    wool?: number;
    tnt?: number;
    fire_charge?: number;
    iron_ingot?: number;
    gold_ingot?: number;
    diamond?: number;
    emerald?: number;
    health_potion?: number;
    soup?: number;
  };
  /**
   * The reach of the most recent landed attack, for the Reach display. Measured on the client when the attack is sent: from the eye position interpolated to the frame the crosshair ray was cast on, to the point where that ray met the target's hitbox. `n` counts measured attacks and is monotonic, so two hits at the same distance are still two readings; the page keeps the history it averages over. Absent until the first measured attack, and after that sent only on the tick a new one lands.
   */
  reach?: {
    /**
     * Eye-to-hit-point distance in blocks, rounded to 3 decimals.
     */
    blocks: number;
    /**
     * Measured attacks since the session began. Monotonic.
     */
    n: number;
  };
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
 * Envelope for the `scoreboard` event.
 */
export interface ScoreboardEvent {
  /**
   * Event discriminator; always `scoreboard`.
   */
  e: 'scoreboard';
  payload: ScoreboardPayload;
}
/**
 * The sidebar scoreboard, read the way `InGameHud#renderScoreboardObjective` reads it: the objective in the sidebar display slot, its scores sorted as vanilla sorts them, names starting with `#` dropped, the last fifteen kept, and each name decorated with its team prefix and suffix. `visible: false` when there is no sidebar objective — the page draws nothing then.
 */
export interface ScoreboardPayload {
  /**
   * Whether a sidebar objective is set.
   */
  visible: boolean;
  /**
   * The objective's display name, raw, `§` formatting codes included. Empty when `visible` is false.
   */
  title: string;
  /**
   * The lines top to bottom — highest score first, exactly the order vanilla draws them. At most fifteen.
   *
   * @maxItems 15
   */
  lines: {
    /**
     * The line as vanilla prints it: team prefix + player name + team suffix, raw, `§` codes included.
     */
    text: string;
    /**
     * The line's score — the red number vanilla draws at the right edge.
     */
    score: number;
  }[];
}
/**
 * Envelope for the `toast` event.
 */
export interface ToastEvent {
  /**
   * Event discriminator; always `toast`.
   */
  e: 'toast';
  payload: ToastPayload;
}
/**
 * One short notice, drawn over the game for a moment and then gone. Purely informational: nothing waits on it and nothing is lost if the page misses it.
 */
export interface ToastPayload {
  /**
   * What the notice is about, so the page can pick its mark: `loadout` for a switch or a library change, `mod` for a mod a key flipped, `info` for anything else.
   */
  kind: 'loadout' | 'mod' | 'info';
  /**
   * The main line, e.g. the loadout's name or `Fullbright on`.
   */
  title: string;
  /**
   * An optional second line, e.g. `Loadout switched`.
   */
  detail?: string;
  mod?: ModId;
}
/**
 * Envelope for the `cosmetics` event.
 */
export interface CosmeticsEvent {
  /**
   * Event discriminator; always `cosmetics`.
   */
  e: 'cosmetics';
  payload: CosmeticsPayload;
}
/**
 * What the in-game wardrobe needs: the local player's whole equipped set, every item the account owns, and the version of the catalog this jar was built with. Whole state, so a newer one in the same frame replaces an older one. Pushed on `pushWholeState()` (first paint, a launcher `init`, a reloaded document) and whenever the launcher changes either half underneath the game — a `cosmetics` message after an equip from the launcher Store, or a purchase landing. **Not** pushed as an echo of the page's own `equipCosmetic`, `setCosmeticTint` or `setEmoteWheel`: each of those already returned what was stored, and §6.5 does not push a change back to the page that made it.
 */
export interface CosmeticsPayload {
  self: EquippedSet;
  /**
   * Every item the account owns, free items included, in catalog order. An item not listed shows as locked; equipping it is refused.
   *
   * @maxItems 512
   */
  owned: CosmeticItemId[];
  /**
   * `cosmetics.json#/version` as compiled into this jar. The page's catalog is generated from the same file, so a mismatch means the bundle and the jar were not built together.
   */
  catalog_version: number;
}
/**
 * Envelope for the `emote` event.
 */
export interface EmoteEvent {
  /**
   * Event discriminator; always `emote`.
   */
  e: 'emote';
  payload: EmotePayload;
}
/**
 * The local player's emote started (`id`) or ended (`null`), so the wheel can light the one playing and the HUD can step aside for the third-person camera. Not coalesced: a start and an end in one slow frame are two facts, and the page should see both in order. The start is **not** pushed when the page's own `playEmote` started it — that call returned `true` and is the notification — but the end always is, because only Java knows when a clip finishes or is cancelled by movement.
 */
export interface EmotePayload {
  /**
   * The emote now playing, or null when none is.
   */
  id: CosmeticItemId | null;
}
/**
 * Envelope for the `wheel` event.
 */
export interface WheelEvent {
  /**
   * Event discriminator; always `wheel`.
   */
  e: 'wheel';
  payload: WheelPayload;
}
/**
 * The emote wheel held in the world (`cosmetics.emote_key`, default B), with no screen open — the player keeps moving, and Java freezes mouse look for as long as the key is down so the mouse picks a slot instead of turning. `open` when the key goes down; `aim` with the mouse travel since `open` in screen pixels (x right, y down), at most ~30 times a second; `key` when one of 1-8 is pressed while it is held (`slot` 0-7, the hotbar is not moved); `release` when the key comes up — the page plays the slot it was pointing at, if any, through `playEmote`; `cancel` when the wheel closes without playing (a screen opened, the player died, the world unloaded). The page draws the wheel over the HUD layer and owns the geometry; Java only reports input.
 */
export interface WheelPayload {
  /**
   * Which step.
   */
  phase: 'open' | 'aim' | 'key' | 'release' | 'cancel';
  /**
   * `aim`: horizontal mouse travel since `open`, screen pixels, right positive.
   */
  x?: number;
  /**
   * `aim`: vertical mouse travel since `open`, screen pixels, down positive.
   */
  y?: number;
  /**
   * `key`: the wheel slot whose number was pressed, 0-7.
   */
  slot?: number;
}
/**
 * Envelope for the `binds` event.
 */
export interface BindsEvent {
  /**
   * Event discriminator; always `binds`.
   */
  e: 'binds';
  payload: BindsPayload;
}
/**
 * One vanilla key binding.
 */
export interface VanillaBind {
  /**
   * The binding's translation key, which is stable across languages, e.g. `key.drop` or `key.hotbar.1`.
   */
  id: string;
  /**
   * The binding's name in the game's language, as vanilla Controls prints it.
   */
  label: string;
  key: Keybind;
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
 * `void.createLoadout(name)`. Adds a loadout to the end of the library with every mod at its registry defaults and the factory HUD layout, and does not switch to it. Java picks the id (the name slugified, with a numeric suffix when taken) and tells Rust with `loadout_save`, which is what persists it. A `loadouts` event follows with the new library.
 */
export interface CreateLoadoutCall {
  /**
   * Call discriminator; always `createLoadout`.
   */
  c: 'createLoadout';
  params: CreateLoadoutParams;
}
/**
 * `void.renameLoadout(id, name)`. Changes a loadout's display name; the id is stable and does not follow it. Persisted with `loadout_save`; a `loadouts` event follows, and a `loadout` event too when the renamed loadout is the active one.
 */
export interface RenameLoadoutCall {
  /**
   * Call discriminator; always `renameLoadout`.
   */
  c: 'renameLoadout';
  params: RenameLoadoutParams;
}
/**
 * `void.duplicateLoadout(id)`. Appends a copy of a loadout, mods, settings, HUD layout and quick pins, named `<name> copy`, with no stats. Does not switch to it. Persisted with `loadout_save`; a `loadouts` event follows.
 */
export interface DuplicateLoadoutCall {
  /**
   * Call discriminator; always `duplicateLoadout`.
   */
  c: 'duplicateLoadout';
  params: DuplicateLoadoutParams;
}
/**
 * `void.deleteLoadout(id)`. Removes a loadout from the library. Refused for the last one. When it was the active loadout, the first remaining one is applied exactly as a switch is, and a `loadout` event follows. Persisted with `loadout_delete`; a `loadouts` event follows.
 */
export interface DeleteLoadoutCall {
  /**
   * Call discriminator; always `deleteLoadout`.
   */
  c: 'deleteLoadout';
  params: DeleteLoadoutParams;
}
/**
 * `void.exportLoadout(id)`. Encodes a loadout as a share code: `VOID1.` + base64url (no padding) of the raw-DEFLATE-compressed UTF-8 JSON of the loadout's name, icon, server, mc, registry version, HUD layout, quick pins and *only the settings that differ from the registry defaults*, then `.` + the CRC-32 of that JSON as 8 lowercase hex digits. Ids and stats are never in a code. The same format is produced and read by `void_loadout::share` in Rust and `ShareCode` in Java; `schema/share-code.fixtures.json` holds codes each side made, and both test suites decode all of them.
 */
export interface ExportLoadoutCall {
  /**
   * Call discriminator; always `exportLoadout`.
   */
  c: 'exportLoadout';
  params: ExportLoadoutParams;
}
/**
 * `void.importLoadout(code)`. Decodes a share code and appends the loadout it carries to the library under a fresh id, without switching to it. Structure is strict: an unknown version, a bad checksum, undecodable data or a missing name refuses the whole code. Content is lenient: a mod or setting this build does not know, or a value outside its schema range, is dropped (the mod falls back to its default) and counted in `dropped`, so a code made on a newer client still imports. The result is a valid `loadout.json` loadout. Persisted with `loadout_save`; a `loadouts` event follows.
 */
export interface ImportLoadoutCall {
  /**
   * Call discriminator; always `importLoadout`.
   */
  c: 'importLoadout';
  params: ImportLoadoutParams;
}
/**
 * `void.setQuickPins(ids)`. Replaces the active loadout's quick-panel pins (`loadout.json#/definitions/quick_pins`). Unknown and repeated ids are dropped and the list is cut to eight. Persisted with `loadout_save`; nothing is pushed back, the return is the stored list.
 */
export interface SetQuickPinsCall {
  /**
   * Call discriminator; always `setQuickPins`.
   */
  c: 'setQuickPins';
  params: SetQuickPinsParams;
}
/**
 * `void.setClipboard(text)`. Puts plain text on the system clipboard — what the Loadouts screen's `Copy code` does with a share code. Why the bridge carries a clipboard at all: the Ultralight binding implements no platform clipboard, so Ctrl+C / Ctrl+V do nothing inside the in-game page and `navigator.clipboard` does not exist there. A share code is a line of text a player has to get out of the game and into Discord, and back. Java owns the system clipboard (`Screen.setClipboard` / `getClipboard`), so the page asks it.
 */
export interface SetClipboardCall {
  /**
   * Call discriminator; always `setClipboard`.
   */
  c: 'setClipboard';
  params: SetClipboardParams;
}
/**
 * `void.getClipboard()`. The system clipboard's plain text, for the Loadouts screen's `Paste code`. Empty when the clipboard holds no text or cannot be read. Only ever called on a click, never polled.
 */
export interface GetClipboardCall {
  /**
   * Call discriminator; always `getClipboard`.
   */
  c: 'getClipboard';
  params: GetClipboardParams;
}
/**
 * `void.equipCosmetic(slot, id)`. Wears `id` in `slot`, or takes the slot off when `id` is null. Applied in Java at once — the next frame draws it — and forwarded to the launcher as an `equip` protocol message, which persists it to the backend. Java refuses only what it can see is wrong: an id the catalog does not know, an item for another slot, or one it knows the account does not own. When it does not know what the account owns, the backend validates and the launcher reverts a refused equip with a `cosmetics` message, which reaches the page as a `cosmetics` event. Synchronous and authoritative like every call here: an item the account does not own, an id the catalog does not know, or an item for another slot is **refused**, and the answer is then what the slot still holds. Bind the wardrobe to the return, never to what was sent. No `cosmetics` event follows.
 */
export interface EquipCosmeticCall {
  /**
   * Call discriminator; always `equipCosmetic`.
   */
  c: 'equipCosmetic';
  params: EquipCosmeticParams;
}
/**
 * `void.setCosmeticTint(slot, hex)`. Recolours the item worn in `slot`, or clears the tint when `hex` is null. Only items flagged `tintable` in `cosmetics.json` take one; for any other slot the call is refused and answers the tint already set. Applied in game only for now: the wire's `equip` carries a slot and an id, not a tint, so a tint chosen here lasts the session until the protocol grows one. No `cosmetics` event follows.
 */
export interface SetCosmeticTintCall {
  /**
   * Call discriminator; always `setCosmeticTint`.
   */
  c: 'setCosmeticTint';
  params: SetCosmeticTintParams;
}
/**
 * `void.setEmoteWheel(ids)`. Replaces the whole emote wheel. Ids that are not emotes, are not owned, or repeat are dropped, and the wheel keeps at most eight. Applied in game only for now: the wire has no message for the wheel, so it lasts the session until the protocol grows one; the launcher Store edits the stored wheel. No `cosmetics` event follows.
 */
export interface SetEmoteWheelCall {
  /**
   * Call discriminator; always `setEmoteWheel`.
   */
  c: 'setEmoteWheel';
  params: SetEmoteWheelParams;
}
/**
 * `void.playEmote(id)`. Starts an emote for the local player and tells the launcher, which relays it to the other VOID players on the server as a remote `emote`. Refused — `false`, and nothing plays — for an id that is not an emote, is not owned, or has no bundled animation. The `emote` event's start is not pushed for a call that succeeded, since this answer is the notification; its end is.
 */
export interface PlayEmoteCall {
  /**
   * Call discriminator; always `playEmote`.
   */
  c: 'playEmote';
  params: PlayEmoteParams;
}
/**
 * `void.openStore(id)`. *Get in launcher Store* on a locked item: Java sends the launcher `open_store` (protocol.json), which shows and focuses the launcher window on that item's Store sheet. Nothing is bought in game and the menu stays open. Returns whether the request reached the launcher; `false` when the launcher link is down (a dev run, or the launcher was closed), and the page then tells the player to open the Store themselves.
 */
export interface OpenStoreCall {
  /**
   * Call discriminator; always `openStore`.
   */
  c: 'openStore';
  params: OpenStoreParams;
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
/**
 * Envelope for a createLoadout return value.
 */
export interface CreateLoadoutResult {
  /**
   * Call discriminator; always `createLoadout`.
   */
  c: 'createLoadout';
  returns: CreateLoadoutReturns;
}
/**
 * Envelope for a renameLoadout return value.
 */
export interface RenameLoadoutResult {
  /**
   * Call discriminator; always `renameLoadout`.
   */
  c: 'renameLoadout';
  returns: RenameLoadoutReturns;
}
/**
 * Envelope for a duplicateLoadout return value.
 */
export interface DuplicateLoadoutResult {
  /**
   * Call discriminator; always `duplicateLoadout`.
   */
  c: 'duplicateLoadout';
  returns: DuplicateLoadoutReturns;
}
/**
 * Envelope for a deleteLoadout return value.
 */
export interface DeleteLoadoutResult {
  /**
   * Call discriminator; always `deleteLoadout`.
   */
  c: 'deleteLoadout';
  returns: DeleteLoadoutReturns;
}
/**
 * Envelope for a exportLoadout return value.
 */
export interface ExportLoadoutResult {
  /**
   * Call discriminator; always `exportLoadout`.
   */
  c: 'exportLoadout';
  returns: ExportLoadoutReturns;
}
/**
 * Envelope for a importLoadout return value.
 */
export interface ImportLoadoutResult {
  /**
   * Call discriminator; always `importLoadout`.
   */
  c: 'importLoadout';
  returns: ImportResult;
}
/**
 * Envelope for a setQuickPins return value.
 */
export interface SetQuickPinsResult {
  /**
   * Call discriminator; always `setQuickPins`.
   */
  c: 'setQuickPins';
  returns: SetQuickPinsReturns;
}
/**
 * Envelope for a setClipboard return value.
 */
export interface SetClipboardResult {
  /**
   * Call discriminator; always `setClipboard`.
   */
  c: 'setClipboard';
  returns: SetClipboardReturns;
}
/**
 * Envelope for a getClipboard return value.
 */
export interface GetClipboardResult {
  /**
   * Call discriminator; always `getClipboard`.
   */
  c: 'getClipboard';
  returns: GetClipboardReturns;
}
/**
 * Envelope for a equipCosmetic return value.
 */
export interface EquipCosmeticResult {
  /**
   * Call discriminator; always `equipCosmetic`.
   */
  c: 'equipCosmetic';
  returns: EquipCosmeticReturns;
}
/**
 * Envelope for a setCosmeticTint return value.
 */
export interface SetCosmeticTintResult {
  /**
   * Call discriminator; always `setCosmeticTint`.
   */
  c: 'setCosmeticTint';
  returns: SetCosmeticTintReturns;
}
/**
 * Envelope for a setEmoteWheel return value.
 */
export interface SetEmoteWheelResult {
  /**
   * Call discriminator; always `setEmoteWheel`.
   */
  c: 'setEmoteWheel';
  returns: SetEmoteWheelReturns;
}
/**
 * Envelope for a playEmote return value.
 */
export interface PlayEmoteResult {
  /**
   * Call discriminator; always `playEmote`.
   */
  c: 'playEmote';
  returns: PlayEmoteReturns;
}
/**
 * Envelope for an openStore return value.
 */
export interface OpenStoreResult {
  /**
   * Call discriminator; always `openStore`.
   */
  c: 'openStore';
  returns: OpenStoreReturns;
}
