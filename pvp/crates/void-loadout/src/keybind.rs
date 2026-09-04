//! LWJGL 2 key names, the `keybind` type of `schema/mods.json`.

use std::fmt;

use serde::{Deserialize, Deserializer, Serialize, Serializer};

/// Multi-character key names accepted by `mods.json#/definitions/keybind`.
///
/// Single characters (`A`-`Z`, `0`-`9`) are accepted separately; everything else is an
/// exact match against this table, which is the literal expansion of the schema's
/// pattern. Keeping it a table rather than a regex avoids a dependency and makes the
/// contract greppable.
const NAMED_KEYS: &[&str] = &[
    "NONE", "SPACE", "TAB", "ESCAPE", "RETURN", "BACK", "DELETE", "INSERT", "HOME", "END",
    "PRIOR", "NEXT", "UP", "DOWN", "LEFT", "RIGHT", "LSHIFT", "RSHIFT", "LCONTROL", "RCONTROL",
    "LMENU", "RMENU", "CAPITAL", "LBRACKET", "RBRACKET", "SEMICOLON", "APOSTROPHE", "COMMA",
    "PERIOD", "SLASH", "BACKSLASH", "MINUS", "EQUALS", "GRAVE", "F1", "F2", "F3", "F4", "F5",
    "F6", "F7", "F8", "F9", "F10", "F11", "F12", "NUMPAD0", "NUMPAD1", "NUMPAD2", "NUMPAD3",
    "NUMPAD4", "NUMPAD5", "NUMPAD6", "NUMPAD7", "NUMPAD8", "NUMPAD9", "MOUSE0", "MOUSE1",
    "MOUSE2", "MOUSE3", "MOUSE4", "MOUSE5", "MOUSE6", "MOUSE7",
];

/// An upper-case LWJGL 2 key name as Minecraft 1.8.9's `Keyboard.getKeyName` produces,
/// a mouse button as `MOUSE0`..`MOUSE7`, or `NONE` for unbound.
///
/// Validated on deserialization, so an invalid keybind never reaches a loadout file or
/// the wire.
#[derive(Debug, Clone, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Keybind(String);

impl Keybind {
    /// The unbound keybind.
    pub const NONE: &'static str = "NONE";

    /// Whether `s` matches the schema's `keybind` pattern.
    pub fn is_valid(s: &str) -> bool {
        if s.len() == 1 {
            let c = s.as_bytes()[0];
            return c.is_ascii_uppercase() || c.is_ascii_digit();
        }
        NAMED_KEYS.contains(&s)
    }

    /// Builds a keybind, returning `None` if `s` is not a valid LWJGL 2 key name.
    pub fn new(s: impl Into<String>) -> Option<Self> {
        let s = s.into();
        Self::is_valid(&s).then_some(Self(s))
    }

    /// The key name as it appears on the wire and on disk.
    pub fn as_str(&self) -> &str {
        &self.0
    }

    /// Whether this keybind is `NONE`.
    pub fn is_none(&self) -> bool {
        self.0 == Self::NONE
    }
}

impl Default for Keybind {
    fn default() -> Self {
        Self(Self::NONE.to_string())
    }
}

impl fmt::Display for Keybind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl Serialize for Keybind {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&self.0)
    }
}

impl<'de> Deserialize<'de> for Keybind {
    fn deserialize<D: Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        let s = String::deserialize(d)?;
        Self::new(s.clone())
            .ok_or_else(|| serde::de::Error::custom(format!("`{s}` is not an LWJGL 2 key name")))
    }
}

/// An sRGB colour as `#RRGGBB` or `#RRGGBBAA` (`mods.json#/definitions/hex_color`).
#[derive(Debug, Clone, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct HexColor(String);

impl HexColor {
    /// Whether `s` matches the schema's `hex_color` pattern.
    pub fn is_valid(s: &str) -> bool {
        let Some(hex) = s.strip_prefix('#') else {
            return false;
        };
        matches!(hex.len(), 6 | 8) && hex.bytes().all(|b| b.is_ascii_hexdigit())
    }

    /// Builds a colour, returning `None` if `s` is not `#RRGGBB` or `#RRGGBBAA`.
    pub fn new(s: impl Into<String>) -> Option<Self> {
        let s = s.into();
        Self::is_valid(&s).then_some(Self(s))
    }

    /// The colour as it appears on the wire and on disk.
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for HexColor {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl Serialize for HexColor {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&self.0)
    }
}

impl<'de> Deserialize<'de> for HexColor {
    fn deserialize<D: Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        let s = String::deserialize(d)?;
        Self::new(s.clone())
            .ok_or_else(|| serde::de::Error::custom(format!("`{s}` is not #RRGGBB or #RRGGBBAA")))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_the_schema_pattern() {
        for ok in ["NONE", "C", "V", "7", "F1", "F12", "MOUSE3", "NUMPAD0", "RSHIFT", "GRAVE"] {
            assert!(Keybind::is_valid(ok), "{ok} should be valid");
        }
        for bad in ["", "c", "F0", "F13", "F01", "MOUSE8", "NUMPAD10", "CTRL", "SHIFT+A"] {
            assert!(!Keybind::is_valid(bad), "{bad} should be invalid");
        }
    }

    #[test]
    fn colours_are_six_or_eight_hex_digits() {
        assert!(HexColor::is_valid("#FFFFFF"));
        assert!(HexColor::is_valid("#ffffffff"));
        assert!(!HexColor::is_valid("#FFF"));
        assert!(!HexColor::is_valid("FFFFFF"));
        assert!(!HexColor::is_valid("#GGGGGG"));
    }
}
