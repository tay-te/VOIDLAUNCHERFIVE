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

import type { ModRegistryDocument } from './schema.js';

/**
 * The registry VOID actually ships — `mods.json` `examples[0]`, verbatim.
 * Prefer the helpers in `src/mods.ts` over reading this directly.
 */
export const MOD_REGISTRY_DOCUMENT = {
  "version": 6,
  "mods": {
    "fps": {
      "id": "fps",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "FPS display",
      "icon": "gauge",
      "description": "Frames per second, updated once per tick.",
      "source": "Minecraft.debugFPS",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "color": "#FFFFFF",
        "show_label": true,
        "show_low": true
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 23
      }
    },
    "keystrokes": {
      "id": "keystrokes",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Keystrokes",
      "icon": "keyboard",
      "description": "WASD, mouse and spacebar tiles that light up as you press them.",
      "source": "KeyBinding.setKeyBindState, edge-triggered",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 0.85,
        "background": "none",
        "border": false,
        "padding": "normal",
        "keybind": "NONE",
        "show_mouse": true,
        "show_spacebar": true,
        "show_sneak": false,
        "show_cps": false,
        "corner_radius": 8,
        "key_color": "shell",
        "pressed_color": "accent"
      },
      "default_placement": {
        "anchor": "bottom-left",
        "dx": 31,
        "dy": -109
      }
    },
    "cps": {
      "id": "cps",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "CPS counter",
      "icon": "cursor-click",
      "description": "Clicks per second over a sliding window.",
      "source": "derived from clicks in JS",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "mode": "left",
        "show_label": true,
        "window_ms": 1000,
        "show_peak": false
      },
      "default_placement": {
        "anchor": "bottom-left",
        "dx": 175,
        "dy": -108
      }
    },
    "ping": {
      "id": "ping",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Ping display",
      "icon": "wifi",
      "description": "Round-trip time to the current server.",
      "source": "own NetworkPlayerInfo.responseTime",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "show_label": true,
        "good_ms": 60,
        "bad_ms": 150,
        "show_host": true
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 65
      }
    },
    "coordinates": {
      "id": "coordinates",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Coordinates",
      "icon": "compass",
      "description": "Player position and facing direction.",
      "source": "EntityPlayerSP pos/yaw",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "decimals": 1,
        "show_direction": true,
        "layout": "inline",
        "color": "#FFFFFF"
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 103
      }
    },
    "armor_status": {
      "id": "armor_status",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Armor status",
      "icon": "shield",
      "description": "Worn armor and held item with remaining durability.",
      "source": "InventoryPlayer.armorInventory durability",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "orientation": "horizontal",
        "show_durability": true,
        "show_held_item": true,
        "warn_below": 0.5
      },
      "default_placement": {
        "anchor": "top-right",
        "dx": -25,
        "dy": 299
      }
    },
    "potion_effects": {
      "id": "potion_effects",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Potion effects",
      "icon": "flask",
      "description": "Active potion effects with amplifier and remaining duration.",
      "source": "getActivePotionEffects",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "show_duration": true,
        "show_amplifier": true,
        "hide_ambient": false
      },
      "default_placement": {
        "anchor": "top-right",
        "dx": -25,
        "dy": 23
      }
    },
    "watermark": {
      "id": "watermark",
      "kind": "hud",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Watermark",
      "icon": "watermark",
      "description": "The VOID mark, drawn over the game.",
      "source": "drawn by the overlay; no game field",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 0.9,
        "background": "none",
        "border": false,
        "padding": "normal",
        "style": "full"
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 141
      }
    },
    "toggle_sprint": {
      "id": "toggle_sprint",
      "kind": "gameplay",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "Toggle sprint",
      "icon": "bolt",
      "description": "Latches sprint instead of holding the key.",
      "source": "KeyBinding override in onLivingUpdate",
      "defaults": {
        "on": true,
        "mode": "toggle",
        "sneak_too": false,
        "keybind": "NONE"
      }
    },
    "fullbright": {
      "id": "fullbright",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "grey",
      "label": "Fullbright",
      "icon": "sun",
      "description": "Raises gamma so caves and shadows are fully lit.",
      "source": "gammaSetting override (client-side, Watchdog-tolerated)",
      "defaults": {
        "on": false,
        "gamma": 10,
        "keybind": "NONE"
      }
    },
    "hitboxes": {
      "id": "hitboxes",
      "kind": "gameplay",
      "category": "pvp",
      "hypixel_safe": "grey",
      "label": "Hitboxes",
      "icon": "cube",
      "description": "Draws entity bounding boxes.",
      "source": "RenderManager.debugBoundingBox",
      "defaults": {
        "on": false,
        "line_width": 2,
        "color": "#FFFFFFFF",
        "show_eye_line": false,
        "eye_line_color": "#7ADFFFFF",
        "max_distance": 64,
        "keybind": "NONE"
      }
    },
    "zoom": {
      "id": "zoom",
      "kind": "gameplay",
      "category": "utility",
      "hypixel_safe": "safe",
      "label": "Zoom",
      "icon": "zoom",
      "description": "Narrows FOV while the zoom key is held.",
      "source": "FOV override while key held",
      "defaults": {
        "on": true,
        "key": "C",
        "fov_divisor": 4,
        "smooth": true,
        "cinematic": false
      }
    },
    "crosshair": {
      "id": "crosshair",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Crosshair",
      "icon": "crosshair",
      "description": "Replaces the vanilla crosshair with a configurable one at the exact screen centre.",
      "source": "replaces vanilla crosshair pass; drawn in GL at exact center",
      "defaults": {
        "on": false,
        "style": "cross",
        "size": 5,
        "thickness": 1,
        "gap": 2,
        "color": "#FFFFFFFF",
        "outline": true,
        "dynamic": false,
        "center_dot": false
      }
    },
    "direction": {
      "id": "direction",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Direction",
      "icon": "compass",
      "description": "Which way you are facing, as its own placeable readout.",
      "source": "`pos.yaw` on the `tick` payload; no new sensor",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "style": "letter",
        "show_degrees": false,
        "color": "#FFFFFF"
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 179
      }
    }
  }
} as const satisfies ModRegistryDocument;
