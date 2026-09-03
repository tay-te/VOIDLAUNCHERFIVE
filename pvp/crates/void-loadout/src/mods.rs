//! The closed registry of the 12 mods — `schema/mods.json`.
//!
//! `mods.json` is a JSON Schema *document*; the registry VOID actually ships is its
//! `examples[0]`. That document is compiled into the binary with [`include_str!`] and
//! parsed once, so [`registry()`] is the single source of factory defaults and of every
//! mod's [`Kind`] and [`HypixelSafe`] class.

use std::fmt;
use std::sync::OnceLock;

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::keybind::{HexColor, Keybind};
use crate::Error;

/// The raw `schema/mods.json` document, compiled in.
pub const MODS_SCHEMA_JSON: &str = include_str!("../../../schema/mods.json");

// ---------------------------------------------------------------------------
// identity
// ---------------------------------------------------------------------------

/// One of the 12 mods of PVP_ARCHITECTURE.md §3.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ModId {
    /// Frames per second readout.
    Fps,
    /// WASD / mouse / spacebar tiles.
    Keystrokes,
    /// Clicks per second over a sliding window.
    Cps,
    /// Round-trip time to the current server.
    Ping,
    /// Player position and facing.
    Coordinates,
    /// Worn armor and held item durability.
    ArmorStatus,
    /// Active potion effects.
    PotionEffects,
    /// Latching sprint.
    ToggleSprint,
    /// Gamma override.
    Fullbright,
    /// Entity bounding boxes.
    Hitboxes,
    /// FOV override while a key is held.
    Zoom,
    /// Replacement for the vanilla crosshair pass.
    Crosshair,
}

impl ModId {
    /// Every mod id, in registry order.
    pub const ALL: [ModId; 12] = [
        ModId::Fps,
        ModId::Keystrokes,
        ModId::Cps,
        ModId::Ping,
        ModId::Coordinates,
        ModId::ArmorStatus,
        ModId::PotionEffects,
        ModId::ToggleSprint,
        ModId::Fullbright,
        ModId::Hitboxes,
        ModId::Zoom,
        ModId::Crosshair,
    ];

    /// The snake_case id used as a `loadout.mods` key and in `mods.<id>.<key>` paths.
    pub fn as_str(self) -> &'static str {
        match self {
            ModId::Fps => "fps",
            ModId::Keystrokes => "keystrokes",
            ModId::Cps => "cps",
            ModId::Ping => "ping",
            ModId::Coordinates => "coordinates",
            ModId::ArmorStatus => "armor_status",
            ModId::PotionEffects => "potion_effects",
            ModId::ToggleSprint => "toggle_sprint",
            ModId::Fullbright => "fullbright",
            ModId::Hitboxes => "hitboxes",
            ModId::Zoom => "zoom",
            ModId::Crosshair => "crosshair",
        }
    }

    /// Parses a snake_case mod id.
    pub fn parse(s: &str) -> Option<Self> {
        ModId::ALL.into_iter().find(|m| m.as_str() == s)
    }

    /// Whether this mod draws (`hud`) or mutates a client-side option (`gameplay`).
    pub fn kind(self) -> Kind {
        registry().info(self).kind
    }

    /// The §11 anti-cheat class of this mod.
    pub fn hypixel_safe(self) -> HypixelSafe {
        registry().info(self).hypixel_safe
    }

    /// The `hud_mod_id` narrowing, when this mod owns a draggable HUD item.
    pub fn as_hud(self) -> Option<HudModId> {
        HudModId::from_mod_id(self)
    }
}

impl fmt::Display for ModId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// The subset of [`ModId`] whose `kind` is `hud`: the mods that own a HUD item.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HudModId {
    /// Frames per second readout.
    Fps,
    /// WASD / mouse / spacebar tiles.
    Keystrokes,
    /// Clicks per second.
    Cps,
    /// Round-trip time.
    Ping,
    /// Player position.
    Coordinates,
    /// Armor durability row.
    ArmorStatus,
    /// Active potion effects.
    PotionEffects,
}

impl HudModId {
    /// Every HUD mod id, in registry order.
    pub const ALL: [HudModId; 7] = [
        HudModId::Fps,
        HudModId::Keystrokes,
        HudModId::Cps,
        HudModId::Ping,
        HudModId::Coordinates,
        HudModId::ArmorStatus,
        HudModId::PotionEffects,
    ];

    /// Widens to the full mod id enum.
    pub fn as_mod_id(self) -> ModId {
        match self {
            HudModId::Fps => ModId::Fps,
            HudModId::Keystrokes => ModId::Keystrokes,
            HudModId::Cps => ModId::Cps,
            HudModId::Ping => ModId::Ping,
            HudModId::Coordinates => ModId::Coordinates,
            HudModId::ArmorStatus => ModId::ArmorStatus,
            HudModId::PotionEffects => ModId::PotionEffects,
        }
    }

    /// Narrows a mod id, returning `None` for a gameplay mod.
    pub fn from_mod_id(id: ModId) -> Option<Self> {
        HudModId::ALL.into_iter().find(|h| h.as_mod_id() == id)
    }

    /// The snake_case id.
    pub fn as_str(self) -> &'static str {
        self.as_mod_id().as_str()
    }
}

impl fmt::Display for HudModId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// The subset of [`ModId`] whose `kind` is `gameplay`: the ids `void.setGameplay` takes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum GameplayModId {
    /// Latching sprint.
    ToggleSprint,
    /// Gamma override.
    Fullbright,
    /// Entity bounding boxes.
    Hitboxes,
    /// FOV override.
    Zoom,
    /// Crosshair replacement.
    Crosshair,
}

impl GameplayModId {
    /// Every gameplay mod id, in registry order.
    pub const ALL: [GameplayModId; 5] = [
        GameplayModId::ToggleSprint,
        GameplayModId::Fullbright,
        GameplayModId::Hitboxes,
        GameplayModId::Zoom,
        GameplayModId::Crosshair,
    ];

    /// Widens to the full mod id enum.
    pub fn as_mod_id(self) -> ModId {
        match self {
            GameplayModId::ToggleSprint => ModId::ToggleSprint,
            GameplayModId::Fullbright => ModId::Fullbright,
            GameplayModId::Hitboxes => ModId::Hitboxes,
            GameplayModId::Zoom => ModId::Zoom,
            GameplayModId::Crosshair => ModId::Crosshair,
        }
    }

    /// Narrows a mod id, returning `None` for a HUD mod.
    pub fn from_mod_id(id: ModId) -> Option<Self> {
        GameplayModId::ALL.into_iter().find(|g| g.as_mod_id() == id)
    }

    /// The snake_case id.
    pub fn as_str(self) -> &'static str {
        self.as_mod_id().as_str()
    }
}

/// Data direction of a mod (§3).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Kind {
    /// Reads game state and draws.
    Hud,
    /// Mutates a documented client-side option through an actuator Mixin.
    Gameplay,
}

/// Anti-cheat posture of a mod (§11).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HypixelSafe {
    /// Unambiguously allowed.
    Safe,
    /// Tolerated but not endorsed; disqualifies the HYPIXEL-READY badge.
    Grey,
}

// ---------------------------------------------------------------------------
// per-mod settings
// ---------------------------------------------------------------------------

/// FPS display settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct FpsSettings {
    /// Whether the FPS display is enabled.
    pub on: bool,
    /// Size multiplier of the FPS tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
    /// Alpha of the FPS tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f64>,
    /// Text colour of the readout.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub color: Option<HexColor>,
    /// Whether to render the trailing "FPS" label.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_label: Option<bool>,
}

/// Keystrokes settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct KeystrokesSettings {
    /// Whether the keystrokes overlay is enabled.
    pub on: bool,
    /// Size multiplier of the key tiles.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
    /// Alpha of the key tiles when a key is not pressed.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f64>,
    /// Optional in-game toggle key; `NONE` leaves the overlay always visible.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub keybind: Option<Keybind>,
    /// Whether to render the LMB and RMB tiles.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_mouse: Option<bool>,
    /// Whether to render the spacebar tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_spacebar: Option<bool>,
    /// Whether to print CPS inside the mouse tiles.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_cps: Option<bool>,
}

/// Which mouse buttons the CPS counter counts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CpsMode {
    /// Left button only.
    Left,
    /// Right button only.
    Right,
    /// Both, shown side by side.
    Both,
}

/// CPS counter settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CpsSettings {
    /// Whether the CPS counter is enabled.
    pub on: bool,
    /// Size multiplier of the CPS tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
    /// Alpha of the CPS tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f64>,
    /// Which buttons to count.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub mode: Option<CpsMode>,
    /// Sliding-window length in milliseconds.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub window_ms: Option<i64>,
}

/// Ping display settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PingSettings {
    /// Whether the ping display is enabled.
    pub on: bool,
    /// Size multiplier of the ping tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
    /// Alpha of the ping tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f64>,
    /// Whether to render the trailing "ms" unit.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_label: Option<bool>,
    /// Ping at or below this renders in the good colour.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub good_ms: Option<i64>,
    /// Ping at or above this renders in the bad colour.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub bad_ms: Option<i64>,
}

/// Whether coordinates are stacked on three lines or printed on one.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CoordinatesLayout {
    /// X, Y and Z on three lines.
    Stacked,
    /// X, Y and Z on one line.
    Inline,
}

/// Coordinates settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CoordinatesSettings {
    /// Whether the coordinates display is enabled.
    pub on: bool,
    /// Size multiplier of the coordinates tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
    /// Alpha of the coordinates tile.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f64>,
    /// Decimal places printed for X, Y and Z.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub decimals: Option<i64>,
    /// Whether to append the cardinal direction.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_direction: Option<bool>,
    /// Stacked or inline layout.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub layout: Option<CoordinatesLayout>,
}

/// Whether armor pieces are laid out left to right or top to bottom.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Orientation {
    /// Left to right.
    Horizontal,
    /// Top to bottom.
    Vertical,
}

/// Armor status settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ArmorStatusSettings {
    /// Whether the armor status display is enabled.
    pub on: bool,
    /// Size multiplier of the armor row.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
    /// Alpha of the armor row.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f64>,
    /// Row or column layout.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub orientation: Option<Orientation>,
    /// Whether to print remaining durability under each piece.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_durability: Option<bool>,
    /// Whether to include the held item as a sixth slot.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_held_item: Option<bool>,
}

/// Potion effects settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PotionEffectsSettings {
    /// Whether the potion effects display is enabled.
    pub on: bool,
    /// Size multiplier of the effect list.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
    /// Alpha of the effect list.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f64>,
    /// Whether to print the remaining duration.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_duration: Option<bool>,
    /// Whether to print the roman-numeral amplifier.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_amplifier: Option<bool>,
    /// Whether to omit ambient effects.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub hide_ambient: Option<bool>,
}

/// Whether toggle sprint latches or restores vanilla hold-to-sprint.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ToggleSprintMode {
    /// Latch sprint until the key is pressed again.
    Toggle,
    /// Vanilla hold-to-sprint, status readout kept.
    Hold,
}

/// Toggle sprint settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ToggleSprintSettings {
    /// Whether toggle sprint is enabled.
    pub on: bool,
    /// Latching or hold behaviour.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub mode: Option<ToggleSprintMode>,
    /// Whether the same latching applies to sneak.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sneak_too: Option<bool>,
    /// Whether the mod draws its own status line.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_status: Option<bool>,
}

/// Fullbright settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct FullbrightSettings {
    /// Whether fullbright is enabled.
    pub on: bool,
    /// Value written to `gammaSetting` while on.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub gamma: Option<f64>,
}

/// Hitboxes settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct HitboxesSettings {
    /// Whether entity hitboxes are drawn.
    pub on: bool,
    /// GL line width of the wireframe.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub line_width: Option<f64>,
    /// Colour of the wireframe.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub color: Option<HexColor>,
    /// Whether to draw the eye-direction ray.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub show_eye_line: Option<bool>,
}

/// Zoom settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ZoomSettings {
    /// Whether zoom is enabled.
    pub on: bool,
    /// Key held to zoom.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub key: Option<Keybind>,
    /// FOV divisor while zoomed.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub fov_divisor: Option<f64>,
    /// Whether the FOV change is eased.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub smooth: Option<bool>,
    /// Whether smooth-camera damping is applied while zoomed.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cinematic: Option<bool>,
}

/// Shape drawn at the screen centre.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CrosshairStyle {
    /// The vanilla crosshair texture.
    Default,
    /// Four arms around a gap.
    Cross,
    /// A single centre dot.
    Dot,
    /// A ring.
    Circle,
    /// A cross with the top arm removed.
    TShape,
    /// Nothing drawn.
    None,
}

/// Crosshair settings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CrosshairSettings {
    /// Whether the vanilla crosshair pass is replaced.
    pub on: bool,
    /// Shape drawn at the centre.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub style: Option<CrosshairStyle>,
    /// Half-length in pixels of each arm.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub size: Option<i64>,
    /// Stroke thickness in pixels.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub thickness: Option<i64>,
    /// Gap between the centre and each arm.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub gap: Option<i64>,
    /// Colour of the crosshair.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub color: Option<HexColor>,
    /// Whether a one-pixel outline is drawn.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub outline: Option<bool>,
    /// Whether the gap widens with the attack cooldown and while sprinting.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub dynamic: Option<bool>,
}

// ---------------------------------------------------------------------------
// registry
// ---------------------------------------------------------------------------

/// One row of the §3 table plus its §11 class and factory defaults.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ModEntry<S> {
    /// The mod's snake_case id; equals the key it is stored under.
    pub id: ModId,
    /// Whether this mod draws or mutates.
    pub kind: Kind,
    /// Anti-cheat class.
    pub hypixel_safe: HypixelSafe,
    /// Human-readable name as it appears in the Mods panel.
    pub label: String,
    /// One-line explanation shown under the label.
    pub description: String,
    /// The 1.8.9 field, method or injection point the mod reads or writes.
    pub source: String,
    /// Factory settings, used when a loadout omits this mod.
    pub defaults: S,
}

/// Classification and copy for one mod, without its typed defaults.
///
/// Borrows from the registry it came from; [`registry()`] hands out `ModInfo<'static>`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ModInfo<'a> {
    /// The mod's id.
    pub id: ModId,
    /// Whether this mod draws or mutates.
    pub kind: Kind,
    /// Anti-cheat class.
    pub hypixel_safe: HypixelSafe,
    /// Human-readable name.
    pub label: &'a str,
    /// One-line explanation.
    pub description: &'a str,
    /// The injection point or field.
    pub source: &'a str,
}

/// Every mod VOID ships, keyed by id. Closed set of 12.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
#[allow(missing_docs)]
pub struct ModRegistryEntries {
    pub fps: ModEntry<FpsSettings>,
    pub keystrokes: ModEntry<KeystrokesSettings>,
    pub cps: ModEntry<CpsSettings>,
    pub ping: ModEntry<PingSettings>,
    pub coordinates: ModEntry<CoordinatesSettings>,
    pub armor_status: ModEntry<ArmorStatusSettings>,
    pub potion_effects: ModEntry<PotionEffectsSettings>,
    pub toggle_sprint: ModEntry<ToggleSprintSettings>,
    pub fullbright: ModEntry<FullbrightSettings>,
    pub hitboxes: ModEntry<HitboxesSettings>,
    pub zoom: ModEntry<ZoomSettings>,
    pub crosshair: ModEntry<CrosshairSettings>,
}

/// A registry document: `{ version, mods }`, i.e. `mods.json`'s `examples[0]`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Registry {
    /// Integer revision, bumped when a mod is added, removed or reclassified.
    pub version: u32,
    /// The 12 entries.
    pub mods: ModRegistryEntries,
}

#[derive(Deserialize)]
struct SchemaDocument {
    examples: Vec<Registry>,
}

static REGISTRY: OnceLock<Registry> = OnceLock::new();
static DEFAULTS_JSON: OnceLock<Vec<(ModId, Map<String, Value>)>> = OnceLock::new();

/// The compiled-in mod registry: `schema/mods.json`'s `examples[0]`.
///
/// Panics only if the compiled-in schema document is malformed, which a build can never
/// observe without the file changing under it — the round-trip test in
/// `tests/schema_roundtrip.rs` is the guard.
pub fn registry() -> &'static Registry {
    REGISTRY.get_or_init(|| {
        let doc: SchemaDocument = serde_json::from_str(MODS_SCHEMA_JSON)
            .expect("schema/mods.json must parse as a registry document");
        doc.examples
            .into_iter()
            .next()
            .expect("schema/mods.json must carry the shipped registry as examples[0]")
    })
}

impl Registry {
    /// Classification and copy for one mod.
    pub fn info(&self, id: ModId) -> ModInfo<'_> {
        macro_rules! info {
            ($e:expr) => {
                ModInfo {
                    id: $e.id,
                    kind: $e.kind,
                    hypixel_safe: $e.hypixel_safe,
                    label: $e.label.as_str(),
                    description: $e.description.as_str(),
                    source: $e.source.as_str(),
                }
            };
        }
        let m = &self.mods;
        match id {
            ModId::Fps => info!(m.fps),
            ModId::Keystrokes => info!(m.keystrokes),
            ModId::Cps => info!(m.cps),
            ModId::Ping => info!(m.ping),
            ModId::Coordinates => info!(m.coordinates),
            ModId::ArmorStatus => info!(m.armor_status),
            ModId::PotionEffects => info!(m.potion_effects),
            ModId::ToggleSprint => info!(m.toggle_sprint),
            ModId::Fullbright => info!(m.fullbright),
            ModId::Hitboxes => info!(m.hitboxes),
            ModId::Zoom => info!(m.zoom),
            ModId::Crosshair => info!(m.crosshair),
        }
    }

    /// Every mod's classification, in registry order.
    pub fn all_info(&self) -> Vec<ModInfo<'_>> {
        ModId::ALL.into_iter().map(|id| self.info(id)).collect()
    }
}

/// The factory defaults of one mod, as a JSON object.
///
/// This is the merge base for [`crate::ModStates::effective`] and the fallback that keeps
/// an old loadout valid when a mod is added (`loadout.json#/definitions/mod_states`).
pub fn defaults_json(id: ModId) -> &'static Map<String, Value> {
    let table = DEFAULTS_JSON.get_or_init(|| {
        let r = registry();
        let m = &r.mods;
        macro_rules! obj {
            ($e:expr) => {
                match serde_json::to_value(&$e.defaults)
                    .expect("registry defaults must serialize")
                {
                    Value::Object(o) => o,
                    _ => unreachable!("mod settings are always objects"),
                }
            };
        }
        vec![
            (ModId::Fps, obj!(m.fps)),
            (ModId::Keystrokes, obj!(m.keystrokes)),
            (ModId::Cps, obj!(m.cps)),
            (ModId::Ping, obj!(m.ping)),
            (ModId::Coordinates, obj!(m.coordinates)),
            (ModId::ArmorStatus, obj!(m.armor_status)),
            (ModId::PotionEffects, obj!(m.potion_effects)),
            (ModId::ToggleSprint, obj!(m.toggle_sprint)),
            (ModId::Fullbright, obj!(m.fullbright)),
            (ModId::Hitboxes, obj!(m.hitboxes)),
            (ModId::Zoom, obj!(m.zoom)),
            (ModId::Crosshair, obj!(m.crosshair)),
        ]
    });
    table
        .iter()
        .find(|(k, _)| *k == id)
        .map(|(_, v)| v)
        .expect("every mod id has defaults")
}

/// Deserializes a settings object for `id`, rejecting unknown keys and bad values.
///
/// This is how a JSON blob assembled by [`crate::apply_patch`] is checked against the
/// mod's settings sub-schema before it is written back into a loadout.
pub(crate) fn validate_settings(id: ModId, value: Value) -> Result<Value, Error> {
    fn check<T: serde::de::DeserializeOwned + Serialize>(
        id: ModId,
        value: Value,
    ) -> Result<Value, Error> {
        let typed: T = serde_json::from_value(value)
            .map_err(|e| Error::InvalidSettings { mod_id: id, source: e })?;
        Ok(serde_json::to_value(typed).expect("mod settings always serialize"))
    }
    match id {
        ModId::Fps => check::<FpsSettings>(id, value),
        ModId::Keystrokes => check::<KeystrokesSettings>(id, value),
        ModId::Cps => check::<CpsSettings>(id, value),
        ModId::Ping => check::<PingSettings>(id, value),
        ModId::Coordinates => check::<CoordinatesSettings>(id, value),
        ModId::ArmorStatus => check::<ArmorStatusSettings>(id, value),
        ModId::PotionEffects => check::<PotionEffectsSettings>(id, value),
        ModId::ToggleSprint => check::<ToggleSprintSettings>(id, value),
        ModId::Fullbright => check::<FullbrightSettings>(id, value),
        ModId::Hitboxes => check::<HitboxesSettings>(id, value),
        ModId::Zoom => check::<ZoomSettings>(id, value),
        ModId::Crosshair => check::<CrosshairSettings>(id, value),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn registry_holds_all_twelve_mods() {
        let r = registry();
        assert_eq!(r.version, 1);
        assert_eq!(r.all_info().len(), 12);
        for id in ModId::ALL {
            assert_eq!(r.info(id).id, id, "entry `id` must equal its key");
        }
    }

    #[test]
    fn kind_agrees_with_the_narrowed_id_enums() {
        for id in ModId::ALL {
            match id.kind() {
                Kind::Hud => {
                    assert!(HudModId::from_mod_id(id).is_some(), "{id} missing from hud_mod_id");
                    assert!(GameplayModId::from_mod_id(id).is_none());
                }
                Kind::Gameplay => {
                    assert!(GameplayModId::from_mod_id(id).is_some());
                    assert!(HudModId::from_mod_id(id).is_none());
                }
            }
        }
    }

    #[test]
    fn grey_mods_are_exactly_fullbright_and_hitboxes() {
        let grey: Vec<ModId> = ModId::ALL
            .into_iter()
            .filter(|id| id.hypixel_safe() == HypixelSafe::Grey)
            .collect();
        assert_eq!(grey, vec![ModId::Fullbright, ModId::Hitboxes]);
    }

    #[test]
    fn defaults_validate_against_their_own_settings_schema() {
        for id in ModId::ALL {
            let d = Value::Object(defaults_json(id).clone());
            validate_settings(id, d).unwrap_or_else(|e| panic!("{id}: {e}"));
        }
    }
}
