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
};
/**
 * Closed enum of the 12 mods of §3, snake_case. Used as the key of `loadout.mods`, as the `id` argument of `void.setModSetting`, and as the id of a HUD item.
 */
export type ModId =
  | 'fps'
  | 'keystrokes'
  | 'cps'
  | 'ping'
  | 'coordinates'
  | 'armor_status'
  | 'potion_effects'
  | 'toggle_sprint'
  | 'fullbright'
  | 'hitboxes'
  | 'zoom'
  | 'crosshair';
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
   * Always `hud`.
   */
  kind?: 'hud';
  /**
   * Always `hud`; the Mods panel tabs it under HUD (frame 244:538).
   */
  category?: 'hud';
  /**
   * Always `safe`; §11 does not list ping explicitly, and a read of the player's own responseTime cannot affect play.
   */
  hypixel_safe?: 'safe';
  defaults?: PingDisplaySettings;
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
   * Always `gameplay`.
   */
  kind?: 'gameplay';
  /**
   * Always `utility`; the Mods panel tabs it under Utility (frame 244:538).
   */
  category?: 'utility';
  /**
   * Always `safe`; §11 does not list zoom explicitly, and an FOV override is the long-standing allowed Optifine behaviour.
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
   * Always `gameplay`; §3 marks it Gameplay* because it is drawn in GL rather than HTML, but its data direction is that of an actuator.
   */
  kind?: 'gameplay';
  /**
   * Always `visual`; the Mods panel tabs it under Visual (frame 244:538).
   */
  category?: 'visual';
  /**
   * Always `safe`; §11 does not list crosshair explicitly, and it is a purely cosmetic replacement of the vanilla crosshair pass.
   */
  hypixel_safe?: 'safe';
  defaults?: CrosshairSettings;
};
/**
 * Lower-case slug: letters, digits and single hyphens, e.g. `sword-pvp`. Unique within a user's library.
 */
export type LoadoutId = string;
/**
 * The subset of mod ids whose `kind` is `hud`, i.e. the mods that own a draggable HUD item. A mod may only appear in `loadout.hud` if it is listed here.
 */
export type HUDModId =
  'fps' | 'keystrokes' | 'cps' | 'ping' | 'coordinates' | 'armor_status' | 'potion_effects';
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
 * Ordered list of HUD item placements. Order is paint order, back to front. At most one entry per mod id; that uniqueness is a `void-loadout` invariant rather than a schema constraint, since JSON Schema cannot express uniqueness by key.
 *
 * @maxItems 7
 */
export type HUDLayout = HUDItem[];
/**
 * Every message on the localhost WebSocket between the Rust launcher (server, `void-bridge`) and the Java mod (client, `net/`), as specified in PVP_ARCHITECTURE.md §7. One JSON object per WS text frame, discriminated on `t`. The link carries state and telemetry summaries only, never per-frame data (§2): telemetry that is drawn never leaves the JVM. Forward compatibility rule of §7: a receiver ignores an unknown `t` and ignores unknown fields, which is why every message here sets additionalProperties true. `v` is the protocol version and is carried only on the two handshake messages, `hello` and `init`; a mismatch makes the launcher refuse to launch and prompt for an update, since mod and launcher ship together. Connection parameters reach the JVM as -Dvoid.port and -Dvoid.token (§6.9).
 */
export type ProtocolMessage = JavaToRust | RustToJava;
/**
 * The six messages the mod sends to the launcher. Validate an inbound frame in `void-bridge` against this.
 */
export type JavaToRust =
  | HelloJavaToRust
  | StateJavaToRust
  | HudJavaToRust
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
 * The single `window.void` object that joins the Java mod and the in-game React app, as specified in PVP_ARCHITECTURE.md §6.5. Java to JS is push, delivered through `void.on(event, handler)` and batched once per frame; JS to Java is a direct call. The bridge is in-process (Ultralight lives inside the JVM, §6.2), so calls are synchronous and return real applied state -- there is no ack, no optimistic UI and no request id. This file therefore does not describe a transport; it describes, per event, the shape of the payload handed to the handler, and per call, the positional `params` and the `returns` value. An instance of this schema is one enveloped event, call or call result, which is exactly the form the browser `?debug` harness of §9 records and replays against a fake window.void. Payloads are additionalProperties:false: unlike protocol.json this surface is not forward-compatible across versions, because the mod JAR embeds the UI bundle and the two always ship as one binary.
 */
export type BridgeEnvelope = Event | Call | CallResult;
/**
 * One push from Java to JS, enveloped as {e, payload}. In the real bridge the envelope does not exist on the wire: `payload` is the single argument the `void.on(e, handler)` handler receives.
 */
export type Event =
  KeysEvent | TickEvent | ServerEvent | LoadoutEvent | LoadoutsEvent | SettingEvent | MenuEvent;
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
  | OpenKeybindCaptureCall;
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
export type GameplayModId = 'toggle_sprint' | 'fullbright' | 'hitboxes' | 'zoom' | 'crosshair';
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
 * The value one JS to Java call returned, enveloped as {c, returns}. Every call except `openKeybindCapture` returns synchronously, because the bridge is in-process (§6.5).
 */
export type CallResult =
  | SetGameplayResult
  | SetHudResult
  | SetModSettingResult
  | SwitchLoadoutResult
  | CloseMenuResult
  | OpenKeybindCaptureResult;
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
 * The closed registry of the 12 mods defined in PVP_ARCHITECTURE.md §3, together with the per-mod settings sub-schema, the anti-cheat classification of §11, the Mods-panel `category` taxonomy of Figma 244:538 and the factory defaults. This file is the single source of truth for mod identity, display copy and classification: `loadout.json` and `bridge.json` both $ref its `mod_id` enum and its `<id>_settings` definitions, so a mod is added in exactly one place, and no consumer re-declares a label or a filter tab. An instance of this schema is a registry document; the registry VOID actually ships is `examples[0]`.
 */
export interface ModRegistryDocument {
  version: RegistryVersion;
  mods: Mods;
}
/**
 * Every mod VOID ships, keyed by its snake_case mod id. Closed set: all 12 keys are required and no others are permitted.
 */
export interface Mods {
  fps: FPSDisplayEntry;
  keystrokes: KeystrokesEntry;
  cps: CPSCounterEntry;
  ping: PingDisplayEntry;
  coordinates: CoordinatesEntry;
  armor_status: ArmorStatusEntry;
  potion_effects: PotionEffectsEntry;
  toggle_sprint: ToggleSprintEntry;
  fullbright: FullbrightEntry;
  hitboxes: HitboxesEntry;
  zoom: ZoomEntry;
  crosshair: CrosshairEntry;
}
/**
 * One row of the §3 table plus its §11 classification and factory defaults. Every key is listed here; the per-mod entry definitions narrow `id`, `kind`, `hypixel_safe` and `defaults` to constants.
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
}
/**
 * Settings for the FPS display HUD mod. Reads `Minecraft.debugFPS` once per tick.
 */
export interface FPSDisplaySettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  color?: Colour;
  /**
   * Whether to render the trailing "FPS" label after the number.
   */
  show_label?: boolean;
}
/**
 * Settings for the Keystrokes HUD mod. Fed by the edge-triggered `keys` bridge event (§6.5), never by polling.
 */
export interface KeystrokesSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
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
   * Which mouse buttons to count: left only, right only, or both shown side by side.
   */
  mode?: 'left' | 'right' | 'both';
  /**
   * Length of the sliding window in milliseconds over which clicks are counted before being scaled to clicks per second.
   */
  window_ms?: number;
}
/**
 * Settings for the Ping display HUD mod. Reads the player's own `NetworkPlayerInfo.responseTime`.
 */
export interface PingDisplaySettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
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
}
/**
 * Settings for the Coordinates HUD mod. Reads `EntityPlayerSP` position and yaw once per tick.
 */
export interface CoordinatesSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
  /**
   * Number of decimal places printed for X, Y and Z.
   */
  decimals?: number;
  /**
   * Whether to append the cardinal direction derived from yaw.
   */
  show_direction?: boolean;
  /**
   * Whether X, Y and Z are stacked on three lines or printed on one.
   */
  layout?: 'stacked' | 'inline';
}
/**
 * Settings for the Armor status HUD mod. Reads `InventoryPlayer.armorInventory` durability, pushed only when it changes.
 */
export interface ArmorStatusSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
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
}
/**
 * Settings for the Potion effects HUD mod. Reads `getActivePotionEffects`, pushed only when the set changes.
 */
export interface PotionEffectsSettings {
  on: Enabled;
  scale?: Scale;
  opacity?: Opacity;
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
 * Settings for the Toggle sprint gameplay mod. Overrides the sprint `KeyBinding` in `onLivingUpdate`.
 */
export interface ToggleSprintSettings {
  on: Enabled;
  /**
   * `toggle` latches sprint until the key is pressed again; `hold` restores vanilla hold-to-sprint but keeps the status readout.
   */
  mode?: 'toggle' | 'hold';
  /**
   * Whether the same latching behaviour is applied to sneak.
   */
  sneak_too?: boolean;
  /**
   * Whether the mod draws its own [Sprinting] status line above the hotbar.
   */
  show_status?: boolean;
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
}
/**
 * Settings for the Crosshair mod. Uniquely among the 12 it is drawn in GL rather than HTML (§3 footnote) because it must sit at the exact pixel centre, but it is configured through the same loadout model as everything else.
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
 * Enabled state plus settings for each mod, keyed by the mod ids of mods.json. Every key is optional: a mod omitted here falls back to its `defaults` in the registry, which is what keeps old loadouts valid when a mod is added. No key outside the closed 12 is permitted.
 */
export interface ModStates {
  fps?: FPSDisplaySettings;
  keystrokes?: KeystrokesSettings;
  cps?: CPSCounterSettings;
  ping?: PingDisplaySettings;
  coordinates?: CoordinatesSettings;
  armor_status?: ArmorStatusSettings;
  potion_effects?: PotionEffectsSettings;
  toggle_sprint?: ToggleSprintSettings;
  fullbright?: FullbrightSettings;
  hitboxes?: HitboxesSettings;
  zoom?: ZoomSettings;
  crosshair?: CrosshairSettings;
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
 * All per-tick telemetry, coalesced into one push per game tick, i.e. 20 Hz (§6.6). Every HUD mod other than keystrokes and CPS reads from this. Fields whose sensor has nothing to report are omitted rather than sent as null, so a handler must treat an absent field as unchanged.
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
