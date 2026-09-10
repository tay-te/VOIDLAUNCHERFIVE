//! The loadout created on first run, and three curated ones that are no longer seeded.
//!
//! **Only [`starter`] is created on first run, as of 2026-09-10.** It used to be all three of
//! [`sword_pvp`], [`bedwars`] and [`uhc`] — a launcher that met a new player with three
//! opinionated loadouts, two of them bound to a named server through the `server` slug that
//! open question §16.3 was going to key an auto-switch on. §16.3 is cut and the seeding went
//! with it: curated loadouts are still coming, and when they do they will be something a player
//! *chooses*, not three rows already in their library before they have launched the game once.
//!
//! The three remain as functions because half the workspace uses them as fixtures — the bridge
//! handshake test, the launch smoke test, two doc examples — and because they are the shape a
//! curated loadout will take when there is a place to offer one from.
//!
//! These are the cards on the Figma **Loadouts** frame (`244:1130`,
//! `design/screens/Overlay-Loadouts.png`): Sword PvP on Hypixel, Bedwars on Hypixel and
//! UHC on Minemen. Every one of the 13 mods is written explicitly rather than left to
//! fall back to the registry, so "off" really means off — the registry turns most HUD
//! mods on by default, and an omitted key would silently re-enable them.
//!
//! The watermark is the one mod every loadout turns on: it is VOID's own mark, its
//! registry default is `on`, and `states()` forces `on` from the list below — so leaving
//! it out would switch it off everywhere while the registry still said otherwise.

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

/// **Sword PvP** — the eight original HUD mods, plus toggle sprint, zoom and the custom
/// crosshair.
///
/// The Figma's "24 mods on" is the marketing count across the whole client; in this
/// registry the equivalent is those eight plus the 3 safe gameplay mods, with every
/// `grey` mod (fullbright, hitboxes, and now overlay) off — which is what makes this
/// loadout HYPIXEL-READY. The list is named rather than counted on purpose: it was "the two
/// grey mods" until `overlay` joined the class, and a count in a doc comment is a fact that
/// goes stale without failing anything.
///
/// It used to say "every HUD mod on", and that stopped being true at the ninth: `direction`
/// ships disabled and is not in the list below. Deliberately left that way rather than
/// quietly added — **which mods a curated loadout turns on is a product decision, not a
/// consequence of the registry growing.** The three loadouts here are the ones a new player
/// meets, so a mod joins them because somebody chose it, and the honest way to keep that
/// choice visible is a comment that stops claiming the set is automatic.
///
/// The corollary, worth stating because it is easy to get wrong: a mod absent from this list
/// is *off*, so it needs no `hud` placement here either. That is why the layout below is
/// eight rows and not nine, and why nothing broke when the fourteenth mod shipped.
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
                ModId::Watermark,
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
            (HudModId::Watermark, Anchor::TopLeft, 20.0, 74.0),
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
                ModId::Watermark,
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
            (HudModId::Watermark, Anchor::TopLeft, 20.0, 38.0),
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
                ModId::Watermark,
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
            (HudModId::Watermark, Anchor::TopLeft, 20.0, 38.0),
            (HudModId::PotionEffects, Anchor::TopRight, -20.0, 20.0),
            (HudModId::ArmorStatus, Anchor::Right, -20.0, 0.0),
        ]),
        stats: None,
    }
}

/// The library created on first run, in Figma order. The first entry is the one made
/// active.
pub fn default_library() -> Vec<Loadout> {
    vec![starter()]
}

/// The one loadout a fresh install gets: **the registry, and nothing added to it.**
///
/// Every other loadout in this file states each mod's `on` explicitly and overrides settings,
/// because each was a designed set. This one deliberately states nothing: `ModStates::default()`
/// is empty, so every mod resolves to the factory settings its own schema entry argues for, and
/// the HUD is each mod's own `default_placement`. There is no choice in it to disagree with.
///
/// **That is the point rather than laziness.** "Do not create loadouts for people out of the
/// gate" cannot mean *zero* loadouts — the store's own invariant is at least one (`Error::
/// LastLoadout` refuses to delete the last), and a launcher with nothing to launch is not a
/// launcher. So the honest reading is one loadout that has made no decisions, and the least
/// opinionated set available is the one the registry already ships: every mod's default is
/// argued in its own `schema/mods/<id>.json`, by somebody thinking about that mod, which is a
/// better provenance than a curated list assembled here.
///
/// **No `server`.** The slug is advisory and its one intended consumer — a per-server default
/// loadout — was cut. A fresh loadout pointed at Hypixel would be an opinion about where the
/// player plays, formed before they have played anywhere.
///
/// The name is deliberately plain. "Sword PvP" tells a player what the loadout is *for*, which
/// is a claim this one is not making.
pub fn starter() -> Loadout {
    Loadout {
        id: LoadoutId::new("default").expect("a literal id"),
        name: "Default".to_string(),
        icon: "sword".to_string(),
        server: None,
        mc: DEFAULT_MC.to_string(),
        // Empty, which resolves to the registry's own defaults for every mod. See above.
        mods: ModStates::default(),
        // **Every HUD mod is placed, including the ones that ship off — and that asymmetry with
        // `mods` is deliberate.** `mods` being empty is safe because an absent mod resolves to
        // its registry default; an absent *placement* does not resolve to anything. The in-game
        // `HudLayer` says so in as many words: "a mod that is on but unplaced draws nothing, and
        // nothing says so", which `design/rendering-invariants.md` §15 files under silent
        // failure. An empty `hud` here would ship a first run whose HUD mods are all on and none
        // of them visible.
        //
        // Placing a mod that is off costs one small object and means that turning it on later
        // puts it somewhere sensible rather than nowhere.
        hud: HudModId::ALL
            .iter()
            .map(|id| {
                let at = crate::mods::registry().default_placement(*id);
                HudItem::new(*id, at.anchor, at.dx, at.dy)
            })
            .collect(),
        stats: None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::loadout::hypixel_ready;

    #[test]
    fn a_fresh_install_gets_one_loadout_and_it_is_nobody_s_opinion() {
        // The seed used to be all three curated loadouts, two of them bound to a named server.
        // It is one, and the assertion is on the count as much as the id: "we do not create
        // loadouts for people out of the gate" is the product decision this encodes, and a test
        // that only checked the name would pass again the day somebody added a second.
        let lib = default_library();
        assert_eq!(lib.len(), 1);
        assert_eq!(lib[0].id.as_str(), "default");
        assert!(lib[0].server.is_none(), "a fresh loadout has no opinion about where you play");
        // Nothing overridden: every mod resolves to what its own schema entry argues for.
        // Nothing stored for any mod: `effective` still answers, from the registry.
        assert_eq!(lib[0].mods, ModStates::default(), "the starter stores no settings of its own");
    }

    #[test]
    fn the_starter_places_every_hud_mod_including_the_ones_that_ship_off() {
        // The asymmetry with `mods` is the point. An absent mod resolves to its registry
        // default; an absent *placement* resolves to nothing, and the in-game `HudLayer` draws
        // nothing and says nothing — `design/rendering-invariants.md` §15's silent failure. An
        // empty `hud` would ship a first run with its HUD mods on and none of them visible.
        let starter = starter();
        starter.validate().unwrap();
        assert_eq!(starter.hud.len(), HudModId::ALL.len());
        for id in HudModId::ALL {
            assert!(starter.hud.iter().any(|i| i.id == id), "{id} is unplaced");
        }
    }

    #[test]
    fn the_three_curated_loadouts_are_still_valid_even_though_nothing_seeds_them() {
        // Kept as fixtures — half the workspace uses them — and as the shape a curated loadout
        // will take when there is somewhere to offer one from. So they stay under test.
        for l in [sword_pvp(), bedwars(), uhc()] {
            l.validate().unwrap_or_else(|e| panic!("{}: {e}", l.id));
            assert_eq!(l.mc, "1.8.9");
            // Every HUD item positions a mod that is actually on. True of a *designed* set,
            // where a placement for an off mod would be a leftover; not true of the starter,
            // which places everything on purpose.
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
                ModId::Watermark,
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
                ModId::Watermark,
                ModId::Fullbright,
            ]
        );
        assert_eq!(
            uhc().enabled_mods(),
            vec![
                ModId::Coordinates,
                ModId::ArmorStatus,
                ModId::PotionEffects,
                ModId::Watermark,
                ModId::Hitboxes,
                ModId::Zoom,
            ]
        );
    }
}
