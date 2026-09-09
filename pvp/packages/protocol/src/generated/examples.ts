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
    "version": 10,
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
      },
      "combo": {
        "id": "combo",
        "kind": "hud",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "Combo counter",
        "icon": "sword",
        "description": "Consecutive hits landed without being hit back.",
        "source": "derived in JS from the monotonic `hits.dealt`/`hits.taken` counters on the tick payload",
        "defaults": {
          "on": true,
          "scale": 1,
          "opacity": 1,
          "background": "none",
          "border": false,
          "padding": "normal",
          "reset_ms": 3000,
          "show_label": true
        },
        "default_placement": {
          "anchor": "top-left",
          "dx": 23,
          "dy": 217
        }
      },
      "saturation": {
        "id": "saturation",
        "kind": "hud",
        "category": "hud",
        "hypixel_safe": "safe",
        "label": "Saturation",
        "icon": "heart",
        "description": "The hidden half of the hunger bar — what actually decides whether you regenerate.",
        "source": "`FoodStats#getSaturationLevel`, via the tick sensor",
        "defaults": {
          "on": false,
          "scale": 1,
          "opacity": 1,
          "background": "none",
          "border": false,
          "padding": "normal",
          "style": "number",
          "decimals": 1,
          "show_label": true
        },
        "default_placement": {
          "anchor": "top-left",
          "dx": 23,
          "dy": 255
        }
      },
      "momentum": {
        "id": "momentum",
        "kind": "hud",
        "category": "hud",
        "hypixel_safe": "safe",
        "label": "Momentum",
        "icon": "move",
        "description": "How fast you are actually travelling across the ground.",
        "source": "horizontal ground speed (`speed`) on the tick payload",
        "defaults": {
          "on": false,
          "scale": 1,
          "opacity": 1,
          "background": "none",
          "border": false,
          "padding": "normal",
          "unit": "bps",
          "decimals": 2,
          "show_label": true
        },
        "default_placement": {
          "anchor": "top-left",
          "dx": 23,
          "dy": 293
        }
      },
      "memory": {
        "id": "memory",
        "kind": "hud",
        "category": "hud",
        "hypixel_safe": "safe",
        "label": "Memory",
        "icon": "layers",
        "description": "JVM heap in use, against the ceiling the launcher gave the game.",
        "source": "heap in use and `Runtime.maxMemory` (`memory`), via the tick sensor",
        "defaults": {
          "on": false,
          "scale": 1,
          "opacity": 1,
          "background": "none",
          "border": false,
          "padding": "normal",
          "style": "used_of_max",
          "show_bar": false,
          "show_label": true
        },
        "default_placement": {
          "anchor": "bottom-right",
          "dx": -25,
          "dy": -23
        }
      },
      "server_address": {
        "id": "server_address",
        "kind": "hud",
        "category": "utility",
        "hypixel_safe": "safe",
        "label": "Server address",
        "icon": "wifi",
        "description": "The host you are actually connected to.",
        "source": "`host` on the `server` bridge event",
        "defaults": {
          "on": false,
          "scale": 1,
          "opacity": 1,
          "background": "none",
          "border": false,
          "padding": "normal",
          "style": "short"
        },
        "default_placement": {
          "anchor": "bottom-right",
          "dx": -25,
          "dy": -61
        }
      },
      "item_counter": {
        "id": "item_counter",
        "kind": "hud",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "Item counter",
        "icon": "box",
        "description": "How many of the item in your hand you have left.",
        "source": "`held_count` on the tick payload — the stack size of the held item",
        "defaults": {
          "on": false,
          "scale": 1,
          "opacity": 1,
          "background": "none",
          "border": false,
          "padding": "normal",
          "show_label": true,
          "low_threshold": 0
        },
        "default_placement": {
          "anchor": "bottom-left",
          "dx": 175,
          "dy": -146
        }
      },
      "stopwatch": {
        "id": "stopwatch",
        "kind": "hud",
        "category": "utility",
        "hypixel_safe": "safe",
        "label": "Stopwatch",
        "icon": "clock",
        "description": "A manual timer, started and zeroed from the keyboard.",
        "source": "no game field — the overlay's own clock, driven by the `modaction` bridge event",
        "defaults": {
          "on": false,
          "scale": 1,
          "opacity": 1,
          "background": "none",
          "border": false,
          "padding": "normal",
          "show_millis": false,
          "format": "auto",
          "start_key": "NONE",
          "reset_key": "NONE"
        },
        "default_placement": {
          "anchor": "bottom-right",
          "dx": -25,
          "dy": -99
        }
      },
      "fov": {
        "id": "fov",
        "kind": "gameplay",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "FOV changer",
        "icon": "eye",
        "description": "Holds your field of view still, so sprint and speed stop punching the camera.",
        "source": "GameOptions.fov, with the movement-speed multiplier suppressed",
        "defaults": {
          "on": false,
          "fov": 90,
          "lock_sprint": true,
          "lock_bow": false
        }
      },
      "toggle_sneak": {
        "id": "toggle_sneak",
        "kind": "gameplay",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "Toggle sneak",
        "icon": "chevron-down",
        "description": "Latches sneak instead of holding the key.",
        "source": "KeyBinding override in onLivingUpdate",
        "defaults": {
          "on": false,
          "mode": "toggle",
          "keybind": "NONE"
        }
      },
      "overlay": {
        "id": "overlay",
        "kind": "gameplay",
        "category": "visual",
        "hypixel_safe": "grey",
        "label": "Overlay",
        "icon": "sparkle",
        "description": "Turns off the vanilla overlays that sit between you and the fight.",
        "source": "the first-person fire, pumpkin, own-armour and stuck-arrow render passes, plus `GameSettings.viewBobbing`",
        "defaults": {
          "on": false,
          "hide_fire": true,
          "view_bobbing": "vanilla",
          "hide_own_armor": false,
          "hide_stuck_arrows": true,
          "hide_pumpkin": true
        }
      },
      "freelook": {
        "id": "freelook",
        "kind": "gameplay",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "Freelook",
        "icon": "orbit",
        "description": "Detaches the camera from your facing, so you can look around without turning.",
        "source": "camera yaw and pitch detached from the player's own in GameRendererMixin; key polled per frame as ZoomController's is",
        "defaults": {
          "on": false,
          "keybind": "NONE",
          "mode": "hold",
          "perspective": "third_back",
          "snap_back": true
        }
      },
      "hit_color": {
        "id": "hit_color",
        "kind": "gameplay",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "Hit colour",
        "icon": "droplet",
        "description": "Recolours the red flash the game draws on an entity you hit.",
        "source": "the entity hurt overlay applied in RenderLivingBase#setBrightness",
        "defaults": {
          "on": false,
          "color": "#2FB8A6",
          "own_hits_only": true,
          "intensity": 1
        }
      },
      "damage_tint": {
        "id": "damage_tint",
        "kind": "gameplay",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "Damage tint",
        "icon": "heart-pulse",
        "description": "Vignettes the screen when your health is low, and owns the vanilla hurt-camera shake.",
        "source": "EntityLivingBase#getHealth for the vignette; EntityRenderer#hurtCameraEffect for the shake",
        "defaults": {
          "on": false,
          "threshold": 6,
          "strength": 0.6,
          "camera_shake": "vanilla"
        }
      },
      "old_animations": {
        "id": "old_animations",
        "kind": "gameplay",
        "category": "pvp",
        "hypixel_safe": "safe",
        "label": "Old animations",
        "icon": "reset",
        "description": "Puts the 1.7 blocking animation back: the sword moves with your swing instead of freezing.",
        "source": "HeldItemRenderer#renderArmHoldingItem and BiPedModel#setAngles",
        "defaults": {
          "on": false,
          "block_hit": "one_seven",
          "swing_during_delay": false
        }
      },
      "old_input": {
        "id": "old_input",
        "kind": "gameplay",
        "category": "pvp",
        "hypixel_safe": "grey",
        "label": "Old input",
        "icon": "tap",
        "description": "Removes the input interlocks 1.8 added, so a click is not swallowed by what your other hand is doing.",
        "source": "MinecraftClient#doUse, #handleBlockBreaking and #doAttack",
        "defaults": {
          "on": false,
          "use_while_digging": false,
          "dig_while_using": false,
          "no_miss_delay": false
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
      "watermark": {
        "on": true,
        "scale": 1,
        "opacity": 0.9,
        "style": "full"
      },
      "toggle_sprint": {
        "on": true
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
        "id": "watermark",
        "anchor": "top-left",
        "dx": 20,
        "dy": 58,
        "scale": 1
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
    "v": 2,
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
    "t": "globals",
    "patch": {
      "hud_editor_grid": 8
    }
  },
  {
    "t": "globals",
    "patch": {
      "menu_key": "F1",
      "theme": "void-light"
    }
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
    "t": "hotkey",
    "id": "loadout.next"
  },
  {
    "t": "hotkey",
    "id": "overlay"
  },
  {
    "t": "init",
    "v": 2,
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
    "returns": null
  },
  {
    "c": "openKeybindCapture",
    "returns": "V"
  },
  {
    "e": "setting",
    "payload": {
      "id": "keystrokes",
      "key": "on",
      "value": false
    }
  },
  {
    "e": "loadouts",
    "payload": [
      {
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
    ]
  },
  {
    "c": "setSurfaces",
    "params": [
      [
        {
          "id": "panel",
          "x": 18,
          "y": 18,
          "w": 1184,
          "h": 654,
          "radius": 20,
          "shadow": {
            "dx": 0,
            "dy": 30,
            "blur": 70,
            "spread": -20,
            "color": [
              0,
              0,
              0,
              0.6
            ]
          }
        }
      ]
    ]
  },
  {
    "c": "setSurfaces",
    "returns": 1
  },
  {
    "e": "session",
    "payload": {
      "name": "Notch",
      "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
      "kind": "microsoft"
    }
  },
  {
    "e": "settings",
    "payload": {
      "menu_key": "RSHIFT",
      "cycle_loadout_key": "L",
      "theme": "void-dark",
      "ui_scale": 1,
      "hud_editor_grid": 4
    }
  },
  {
    "c": "setGlobal",
    "params": [
      "ui_scale",
      1.25
    ]
  },
  {
    "c": "setGlobal",
    "returns": 1.25
  },
  {
    "c": "setGlobal",
    "params": [
      "menu_key",
      "RSHIFT"
    ]
  },
  {
    "c": "setGlobal",
    "returns": "RSHIFT"
  },
  {
    "e": "modaction",
    "payload": {
      "mod": "stopwatch",
      "action": "start_stop"
    }
  },
  {
    "e": "modaction",
    "payload": {
      "mod": "stopwatch",
      "action": "reset"
    }
  }
];

/** Every documented example, keyed by source document. */
export const SCHEMA_EXAMPLES = {
  mods: MODS_EXAMPLES,
  loadout: LOADOUT_EXAMPLES,
  protocol: PROTOCOL_EXAMPLES,
  bridge: BRIDGE_EXAMPLES,
} as const;
