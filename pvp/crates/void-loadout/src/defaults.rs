//! The three loadouts created on first run.
//!
//! These are the cards on the Figma **Loadouts** frame (`244:1130`,
//! `design/screens/Overlay-Loadouts.png`): Sword PvP on Hypixel, Bedwars on Hypixel and
//! UHC on Minemen. Every one of the 12 mods is written explicitly rather than left to
//! fall back to the registry, so "off" really means off — the registry turns most HUD
//! mods on by default, and an omitted key would silently re-enable them.

use serde_json::Value;

use crate::loadout::{Anchor, HudItem, Loadout, LoadoutId, ModStates, DEFAULT_MC};
use crate::mods::{defaults_json, HudModId, ModId};

/// Builds a fully explicit [`ModStates`]: registry defaults, with `on` forced for every
/// mod and the listed settings overridden.
fn states(on: &[ModId], overrides: &[(ModId, &str, Value)]) -> ModStates {
    let mut out = ModStates::default();
    for id in ModId::ALL {
        let mut settings = defaults_json(id).clone();
        settings.insert("on".to_string(), Value::Bool(on.contains(&id)));
        for (over_id, key, value) in overrides {
            if *over_id == id {
                settings.insert((*key).to_string(), value.clone());
            }
        }
        out.set(id, settings).expect("built from registry defaults, so always valid");
    }
    out
}

fn hud(items: &[(HudModId, Anchor, f64, f64)]) -> Vec<HudItem> {
    items.iter().map(|(id, a, dx, dy)| HudItem::new(*id, *a, *dx, *dy)).collect()
}

/// **Sword PvP** — every HUD mod on, plus toggle sprint, zoom and the custom crosshair.
///
/// The Figma's "24 mods on" is the marketing count across the whole client; in this
/// registry the equivalent is all 7 HUD mods plus the 3 safe gameplay mods, with the two
/// `grey` mods (fullbright, hitboxes) off — which is what makes this loadout
/// HYPIXEL-READY.
pub fn sword_pvp() -> Loadout {
    Loadout {
        id: LoadoutId::new("sword-pvp").expect("valid slug"),
        name: "Sword PvP".to_string(),
        icon: "sword".to_string(),
        server: Some("hypixel".to_string()),
        mc: DEFAULT_MC.to_string(),
        mods: states(
            &[
                ModId::Fps,
                ModId::Keystrokes,
                ModId::Cps,
                ModId::Ping,
                ModId::Coordinates,
                ModId::ArmorStatus,
                ModId::PotionEffects,
                ModId::ToggleSprint,
                ModId::Zoom,
                ModId::Crosshair,
            ],
            &[
                (ModId::Keystrokes, "opacity", Value::from(0.85)),
                (ModId::Keystrokes, "show_cps", Value::Bool(true)),
                (ModId::Cps, "mode", Value::from("both")),
                (ModId::Zoom, "key", Value::from("C")),
                (ModId::Crosshair, "style", Value::from("cross")),
            ],
        ),
        hud: hud(&[
            (HudModId::Fps, Anchor::TopLeft, 20.0, 20.0),
            (HudModId::Ping, Anchor::TopLeft, 20.0, 38.0),
            (HudModId::Coordinates, Anchor::TopLeft, 20.0, 56.0),
            (HudModId::PotionEffects, Anchor::TopRight, -20.0, 20.0),
            (HudModId::ArmorStatus, Anchor::Right, -20.0, 0.0),
            (HudModId::Keystrokes, Anchor::BottomLeft, 32.0, -40.0),
            (HudModId::Cps, Anchor::BottomLeft, 32.0, -8.0),
        ]),
        stats: None,
    }
}

/// **Bedwars** — keystrokes, armor status, potion effects, fullbright and ping.
///
/// Fullbright is `grey` (§11), so this loadout is deliberately *not* HYPIXEL-READY; it
/// is the case the badge exists to catch.
pub fn bedwars() -> Loadout {
    Loadout {
        id: LoadoutId::new("bedwars").expect("valid slug"),
        name: "Bedwars".to_string(),
        icon: "bed".to_string(),
        server: Some("hypixel".to_string()),
        mc: DEFAULT_MC.to_string(),
        mods: states(
            &[
                ModId::Keystrokes,
                ModId::ArmorStatus,
                ModId::PotionEffects,
                ModId::Fullbright,
                ModId::Ping,
            ],
            &[
                (ModId::Fullbright, "gamma", Value::from(10.0)),
                (ModId::ArmorStatus, "orientation", Value::from("horizontal")),
            ],
        ),
        hud: hud(&[
            (HudModId::Ping, Anchor::TopLeft, 20.0, 20.0),
            (HudModId::PotionEffects, Anchor::TopRight, -20.0, 20.0),
            (HudModId::ArmorStatus, Anchor::Right, -20.0, 0.0),
            (HudModId::Keystrokes, Anchor::BottomLeft, 24.0, -24.0),
        ]),
        stats: None,
    }
}

/// **UHC** — armor status, potion effects, coordinates, hitboxes and zoom, on Minemen.
///
/// Hitboxes is `grey`, so this loadout is not HYPIXEL-READY either — as the Figma
/// implies by pairing it with a non-Hypixel server.
pub fn uhc() -> Loadout {
    Loadout {
        id: LoadoutId::new("uhc").expect("valid slug"),
        name: "UHC".to_string(),
        icon: "shield".to_string(),
        server: Some("minemen".to_string()),
        mc: DEFAULT_MC.to_string(),
        mods: states(
            &[
                ModId::ArmorStatus,
                ModId::PotionEffects,
                ModId::Coordinates,
                ModId::Hitboxes,
                ModId::Zoom,
            ],
            &[
                (ModId::Coordinates, "layout", Value::from("stacked")),
                (ModId::Coordinates, "show_direction", Value::Bool(true)),
                (ModId::Zoom, "key", Value::from("C")),
                (ModId::ArmorStatus, "orientation", Value::from("vertical")),
            ],
        ),
        hud: hud(&[
            (HudModId::Coordinates, Anchor::TopLeft, 20.0, 20.0),
            (HudModId::PotionEffects, Anchor::TopRight, -20.0, 20.0),
            (HudModId::ArmorStatus, Anchor::Right, -20.0, 0.0),
        ]),
        stats: None,
    }
}

/// The library created on first run, in Figma order. The first entry is the one made
/// active.
pub fn default_library() -> Vec<Loadout> {
    vec![sword_pvp(), bedwars(), uhc()]
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::loadout::hypixel_ready;

    #[test]
    fn the_three_defaults_are_valid_and_match_the_figma() {
        let lib = default_library();
        assert_eq!(
            lib.iter().map(|l| l.id.as_str().to_string()).collect::<Vec<_>>(),
            ["sword-pvp", "bedwars", "uhc"]
        );
        for l in &lib {
            l.validate().unwrap_or_else(|e| panic!("{}: {e}", l.id));
            assert_eq!(l.mc, "1.8.9");
            // Every HUD item positions a mod that is actually on.
            for item in &l.hud {
                assert!(
                    l.mods.is_on(item.id.as_mod_id()),
                    "{}: hud item {} is not enabled",
                    l.id,
                    item.id
                );
            }
        }
    }

    #[test]
    fn only_sword_pvp_is_hypixel_ready() {
        assert!(hypixel_ready(&sword_pvp()));
        assert!(!hypixel_ready(&bedwars()), "fullbright is grey");
        assert!(!hypixel_ready(&uhc()), "hitboxes is grey");
    }

    #[test]
    fn enabled_sets_are_exactly_what_the_loadouts_frame_lists() {
        assert_eq!(
            sword_pvp().enabled_mods(),
            vec![
                ModId::Fps,
                ModId::Keystrokes,
                ModId::Cps,
                ModId::Ping,
                ModId::Coordinates,
                ModId::ArmorStatus,
                ModId::PotionEffects,
                ModId::ToggleSprint,
                ModId::Zoom,
                ModId::Crosshair,
            ]
        );
        assert_eq!(
            bedwars().enabled_mods(),
            vec![
                ModId::Keystrokes,
                ModId::Ping,
                ModId::ArmorStatus,
                ModId::PotionEffects,
                ModId::Fullbright,
            ]
        );
        assert_eq!(
            uhc().enabled_mods(),
            vec![
                ModId::Coordinates,
                ModId::ArmorStatus,
                ModId::PotionEffects,
                ModId::Hitboxes,
                ModId::Zoom,
            ]
        );
    }
}
