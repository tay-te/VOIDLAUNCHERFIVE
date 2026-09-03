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

import type { ModRegistryDocument, Loadout, ProtocolMessage, BridgeEnvelope } from './schema.js';

/** `mods.json` `examples`. */
export const MODS_EXAMPLES: ModRegistryDocument[] = [
  {
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
  }
];

/** `loadout.json` `examples`. */
export const LOADOUT_EXAMPLES: Loadout[] = [
  {
    "id": "sword-pvp",
    "name": "Sword PvP",
    "icon": "sword",
    "server": "hypixel",
    "mc": "1.8.9",
    "mods": {
      "fps": {
        "on": true,
        "scale": 1,
        "opacity": 1,
        "color": "#FFFFFF",
        "show_label": true
      },
      "keystrokes": {
        "on": true,
        "scale": 1,
        "opacity": 0.85,
        "keybind": "NONE",
        "show_mouse": true,
        "show_spacebar": true,
        "show_cps": true
      },
      "cps": {
        "on": true,
        "mode": "left",
        "window_ms": 1000
      },
      "ping": {
        "on": true,
        "good_ms": 60,
        "bad_ms": 150
      },
      "coordinates": {
        "on": false
      },
      "armor_status": {
        "on": true,
        "orientation": "horizontal",
        "show_durability": true,
        "show_held_item": true
      },
      "potion_effects": {
        "on": true
      },
      "toggle_sprint": {
        "on": true,
        "mode": "toggle",
        "show_status": true
      },
      "fullbright": {
        "on": false,
        "gamma": 10
      },
      "hitboxes": {
        "on": false
      },
      "zoom": {
        "on": true,
        "key": "C",
        "fov_divisor": 4,
        "smooth": true
      },
      "crosshair": {
        "on": true,
        "style": "cross",
        "size": 5,
        "gap": 2,
        "color": "#FFFFFFFF",
        "outline": true
      }
    },
    "hud": [
      {
        "id": "keystrokes",
        "anchor": "bottom-left",
        "dx": 32,
        "dy": -40,
        "scale": 1
      },
      {
        "id": "cps",
        "anchor": "bottom-left",
        "dx": 32,
        "dy": -8,
        "scale": 1
      },
      {
        "id": "fps",
        "anchor": "top-left",
        "dx": 20,
        "dy": 20
      },
      {
        "id": "ping",
        "anchor": "top-left",
        "dx": 20,
        "dy": 38
      },
      {
        "id": "armor_status",
        "anchor": "right",
        "dx": -20,
        "dy": 0
      },
      {
        "id": "potion_effects",
        "anchor": "top-right",
        "dx": -20,
        "dy": 20
      }
    ],
    "stats": {
      "played_ms": 15600000,
      "fps_avg": 142
    }
  },
  {
    "id": "bedwars",
    "name": "Bedwars",
    "icon": "bed",
    "server": null,
    "mc": "1.8.9",
    "mods": {
      "keystrokes": {
        "on": true
      },
      "cps": {
        "on": true,
        "mode": "both"
      },
      "toggle_sprint": {
        "on": true
      },
      "zoom": {
        "on": true,
        "key": "V"
      }
    },
    "hud": [
      {
        "id": "keystrokes",
        "anchor": "bottom-left",
        "dx": 24,
        "dy": -24
      },
      {
        "id": "cps",
        "anchor": "bottom",
        "dx": 0,
        "dy": -60,
        "scale": 0.75
      }
    ]
  }
];

/** `protocol.json` `examples`. */
export const PROTOCOL_EXAMPLES: ProtocolMessage[] = [
  {
    "t": "hello",
    "v": 1,
    "mc": "1.8.9",
    "mod": "0.1.0",
    "token": "b7f1c0a94e2d43aa9c1e5f6b8d0a2c34"
  },
  {
    "t": "state",
    "loadout": "sword-pvp",
    "patch": {
      "mods.fullbright.on": true
    }
  },
  {
    "t": "state",
    "loadout": "sword-pvp",
    "patch": {
      "mods.zoom.key": "V",
      "mods.cps.window_ms": 2000
    }
  },
  {
    "t": "hud",
    "loadout": "sword-pvp",
    "items": [
      {
        "id": "keystrokes",
        "anchor": "bottom-left",
        "dx": 32,
        "dy": -40,
        "scale": 1
      },
      {
        "id": "fps",
        "anchor": "top-left",
        "dx": 20,
        "dy": 20
      },
      {
        "id": "armor_status",
        "anchor": "right",
        "dx": -20,
        "dy": 0
      }
    ]
  },
  {
    "t": "session",
    "fps_avg": 142,
    "played_ms": 812000,
    "server": "mc.hypixel.net"
  },
  {
    "t": "server",
    "host": "mc.hypixel.net",
    "connected": true
  },
  {
    "t": "init",
    "v": 1,
    "loadout": {
      "id": "sword-pvp",
      "name": "Sword PvP",
      "icon": "sword",
      "server": "hypixel",
      "mc": "1.8.9",
      "mods": {
        "fps": {
          "on": true
        },
        "keystrokes": {
          "on": true,
          "scale": 1,
          "opacity": 0.85
        },
        "cps": {
          "on": true
        },
        "toggle_sprint": {
          "on": true
        },
        "fullbright": {
          "on": false
        },
        "zoom": {
          "on": true,
          "key": "C"
        }
      },
      "hud": [
        {
          "id": "keystrokes",
          "anchor": "bottom-left",
          "dx": 32,
          "dy": -40,
          "scale": 1
        },
        {
          "id": "fps",
          "anchor": "top-left",
          "dx": 20,
          "dy": 20
        }
      ],
      "stats": {
        "played_ms": 15600000,
        "fps_avg": 142
      }
    },
    "loadouts": [
      {
        "id": "sword-pvp",
        "name": "Sword PvP",
        "icon": "sword",
        "server": "hypixel",
        "stats": {
          "played_ms": 15600000,
          "fps_avg": 142
        }
      },
      {
        "id": "bedwars",
        "name": "Bedwars",
        "icon": "bed",
        "server": null
      }
    ],
    "settings": {
      "menu_key": "RSHIFT",
      "cycle_loadout_key": "L",
      "theme": "void-dark",
      "ui_scale": 1,
      "hud_editor_grid": 4
    }
  },
  {
    "t": "loadout",
    "loadout": {
      "id": "bedwars",
      "name": "Bedwars",
      "icon": "bed",
      "server": null,
      "mc": "1.8.9",
      "mods": {
        "keystrokes": {
          "on": true
        },
        "cps": {
          "on": true,
          "mode": "both"
        },
        "zoom": {
          "on": true,
          "key": "V"
        }
      },
      "hud": [
        {
          "id": "keystrokes",
          "anchor": "bottom-left",
          "dx": 24,
          "dy": -24
        }
      ]
    }
  },
  {
    "t": "settings",
    "settings": {
      "menu_key": "RSHIFT",
      "cycle_loadout_key": "L",
      "theme": "void-dark",
      "ui_scale": 1
    }
  }
];

/** `bridge.json` `examples`. */
export const BRIDGE_EXAMPLES: BridgeEnvelope[] = [
  {
    "e": "keys",
    "payload": {
      "w": 1,
      "a": 0,
      "s": 0,
      "d": 0,
      "lmb": 1,
      "rmb": 0,
      "space": 0,
      "shift": 1
    }
  },
  {
    "e": "tick",
    "payload": {
      "fps": 142,
      "ping": 38,
      "pos": {
        "x": -142.31,
        "y": 71,
        "z": 88.06,
        "yaw": -87.4
      },
      "armor": [
        {
          "slot": "helmet",
          "item": "diamond_helmet",
          "damage": 12,
          "max_damage": 363,
          "count": 1,
          "enchanted": true
        },
        {
          "slot": "chestplate",
          "item": "diamond_chestplate",
          "damage": 40,
          "max_damage": 528,
          "count": 1,
          "enchanted": true
        },
        {
          "slot": "leggings",
          "item": null
        },
        {
          "slot": "boots",
          "item": "diamond_boots",
          "damage": 0,
          "max_damage": 429,
          "count": 1,
          "enchanted": false
        },
        {
          "slot": "held",
          "item": "diamond_sword",
          "damage": 3,
          "max_damage": 1561,
          "count": 1,
          "enchanted": true
        }
      ],
      "fx": [
        {
          "id": 1,
          "name": "potion.moveSpeed",
          "amplifier": 1,
          "duration_ms": 41500,
          "ambient": false
        },
        {
          "id": 5,
          "name": "potion.damageBoost",
          "amplifier": 0,
          "duration_ms": 8000,
          "ambient": false
        }
      ]
    }
  },
  {
    "e": "server",
    "payload": {
      "host": "mc.hypixel.net",
      "connected": true
    }
  },
  {
    "e": "menu",
    "payload": true
  },
  {
    "e": "loadout",
    "payload": {
      "id": "sword-pvp",
      "name": "Sword PvP",
      "icon": "sword",
      "server": "hypixel",
      "mc": "1.8.9",
      "mods": {
        "keystrokes": {
          "on": true,
          "scale": 1,
          "opacity": 0.85
        },
        "cps": {
          "on": true
        },
        "zoom": {
          "on": true,
          "key": "C"
        }
      },
      "hud": [
        {
          "id": "keystrokes",
          "anchor": "bottom-left",
          "dx": 32,
          "dy": -40,
          "scale": 1
        }
      ]
    }
  },
  {
    "c": "setGameplay",
    "params": [
      "fullbright",
      true
    ]
  },
  {
    "c": "setGameplay",
    "returns": true
  },
  {
    "c": "setHud",
    "params": [
      "keystrokes",
      {
        "anchor": "bottom-left",
        "dx": 32,
        "dy": -40,
        "scale": 1.25
      }
    ]
  },
  {
    "c": "setHud",
    "returns": {
      "id": "keystrokes",
      "anchor": "bottom-left",
      "dx": 32,
      "dy": -40,
      "scale": 1.25
    }
  },
  {
    "c": "setModSetting",
    "params": [
      "keystrokes",
      "opacity",
      0.6
    ]
  },
  {
    "c": "setModSetting",
    "returns": 0.6
  },
  {
    "c": "switchLoadout",
    "params": [
      "bedwars"
    ]
  },
  {
    "c": "switchLoadout",
    "returns": true
  },
  {
    "c": "closeMenu",
    "params": []
  },
  {
    "c": "closeMenu",
    "returns": null
  },
  {
    "c": "openKeybindCapture",
    "params": [
      "zoom"
    ]
  },
  {
    "c": "openKeybindCapture",
    "returns": "V"
  }
];

/** Every documented example, keyed by source document. */
export const SCHEMA_EXAMPLES = {
  mods: MODS_EXAMPLES,
  loadout: LOADOUT_EXAMPLES,
  protocol: PROTOCOL_EXAMPLES,
  bridge: BRIDGE_EXAMPLES,
} as const;
