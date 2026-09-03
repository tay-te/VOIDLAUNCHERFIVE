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
  "version": 1,
  "mods": {
    "fps": {
      "id": "fps",
      "kind": "hud",
      "hypixel_safe": "safe",
      "label": "FPS",
      "description": "Frames per second, updated once per tick.",
      "source": "Minecraft.debugFPS",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "color": "#FFFFFF",
        "show_label": true
      }
    },
    "keystrokes": {
      "id": "keystrokes",
      "kind": "hud",
      "hypixel_safe": "safe",
      "label": "Keystrokes",
      "description": "WASD, mouse and spacebar tiles that light up as you press them.",
      "source": "KeyBinding.setKeyBindState, edge-triggered",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 0.85,
        "keybind": "NONE",
        "show_mouse": true,
        "show_spacebar": true,
        "show_cps": false
      }
    },
    "cps": {
      "id": "cps",
      "kind": "hud",
      "hypixel_safe": "safe",
      "label": "CPS",
      "description": "Clicks per second over a sliding window.",
      "source": "derived from clicks in JS",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "mode": "left",
        "window_ms": 1000
      }
    },
    "ping": {
      "id": "ping",
      "kind": "hud",
      "hypixel_safe": "safe",
      "label": "Ping",
      "description": "Round-trip time to the current server.",
      "source": "own NetworkPlayerInfo.responseTime",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "show_label": true,
        "good_ms": 60,
        "bad_ms": 150
      }
    },
    "coordinates": {
      "id": "coordinates",
      "kind": "hud",
      "hypixel_safe": "safe",
      "label": "Coordinates",
      "description": "Player position and facing direction.",
      "source": "EntityPlayerSP pos/yaw",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "decimals": 1,
        "show_direction": true,
        "layout": "stacked"
      }
    },
    "armor_status": {
      "id": "armor_status",
      "kind": "hud",
      "hypixel_safe": "safe",
      "label": "Armor status",
      "description": "Worn armor and held item with remaining durability.",
      "source": "InventoryPlayer.armorInventory durability",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "orientation": "horizontal",
        "show_durability": true,
        "show_held_item": true
      }
    },
    "potion_effects": {
      "id": "potion_effects",
      "kind": "hud",
      "hypixel_safe": "safe",
      "label": "Potion effects",
      "description": "Active potion effects with amplifier and remaining duration.",
      "source": "getActivePotionEffects",
      "defaults": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "show_duration": true,
        "show_amplifier": true,
        "hide_ambient": false
      }
    },
    "toggle_sprint": {
      "id": "toggle_sprint",
      "kind": "gameplay",
      "hypixel_safe": "safe",
      "label": "Toggle sprint",
      "description": "Latches sprint instead of holding the key.",
      "source": "KeyBinding override in onLivingUpdate",
      "defaults": {
        "on": true,
        "mode": "toggle",
        "sneak_too": false,
        "show_status": true
      }
    },
    "fullbright": {
      "id": "fullbright",
      "kind": "gameplay",
      "hypixel_safe": "grey",
      "label": "Fullbright",
      "description": "Raises gamma so caves and shadows are fully lit.",
      "source": "gammaSetting override (client-side, Watchdog-tolerated)",
      "defaults": {
        "on": false,
        "gamma": 10
      }
    },
    "hitboxes": {
      "id": "hitboxes",
      "kind": "gameplay",
      "hypixel_safe": "grey",
      "label": "Hitboxes",
      "description": "Draws entity bounding boxes.",
      "source": "RenderManager.debugBoundingBox",
      "defaults": {
        "on": false,
        "line_width": 2,
        "color": "#FFFFFFFF",
        "show_eye_line": false
      }
    },
    "zoom": {
      "id": "zoom",
      "kind": "gameplay",
      "hypixel_safe": "safe",
      "label": "Zoom",
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
      "hypixel_safe": "safe",
      "label": "Crosshair",
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
        "dynamic": false
      }
    }
  }
} as const satisfies ModRegistryDocument;
