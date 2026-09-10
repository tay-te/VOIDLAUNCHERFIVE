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
  "version": 18,
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
        "background": "subtle",
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
        "background": "subtle",
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
        "background": "subtle",
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
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "show_label": true,
        "good_ms": 60,
        "bad_ms": 150,
        "show_host": true,
        "show_jitter": false
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
        "background": "subtle",
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
        "background": "subtle",
        "border": true,
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
        "background": "subtle",
        "border": true,
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
        "background": "subtle",
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
        "cinematic": false,
        "sensitivity": 1
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
        "background": "subtle",
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
        "background": "subtle",
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
        "background": "subtle",
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
        "background": "subtle",
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
        "background": "subtle",
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
        "background": "subtle",
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
      "description": "How many of the item in your hand you have — in the stack, or across the whole inventory.",
      "source": "`held_count` on the tick payload — the stack size of the held item",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "source": "held",
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
        "background": "subtle",
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
    },
    "hit_trade": {
      "id": "hit_trade",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "Trade counter",
      "icon": "sword",
      "description": "Hits you have landed against hits you have taken, this session.",
      "source": "the monotonic `hits.dealt` / `hits.taken` counters, via the tick sensor",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "style": "traded",
        "show_bar": false,
        "show_label": true
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 255
      }
    },
    "clock": {
      "id": "clock",
      "kind": "hud",
      "category": "utility",
      "hypixel_safe": "safe",
      "label": "Clock",
      "icon": "clock",
      "description": "The real-world time, for a session with somewhere to be after it.",
      "source": "the machine's own clock, in the page — no sensor",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "format": "h24",
        "show_seconds": false
      },
      "default_placement": {
        "anchor": "top-right",
        "dx": -25,
        "dy": 62
      }
    },
    "cps_graph": {
      "id": "cps_graph",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "CPS graph",
      "icon": "cursor-click",
      "description": "The shape of your clicking over the last few seconds, not just the current rate.",
      "source": "the same click edges as the CPS counter, summarised once a second in the page",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "mode": "left",
        "window_s": 20,
        "show_figure": true
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 293
      }
    },
    "scoreboard": {
      "id": "scoreboard",
      "kind": "gameplay",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Scoreboard",
      "icon": "users",
      "description": "Hide, shrink or move the server's sidebar, which vanilla nails to the right of the screen.",
      "source": "vanilla's own `InGameHud.renderScoreboardObjective`, wrapped",
      "defaults": {
        "on": false,
        "hide": false,
        "sidebar_scale": 1,
        "offset_x": 0,
        "offset_y": 0
      }
    },
    "reach": {
      "id": "reach",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "grey",
      "label": "Reach display",
      "icon": "sword",
      "description": "How far away your last landed hit was — the swing that connected, never the one you are lining up.",
      "source": "the `reach` field, latched from a landed attack by the tick sensor",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "show_label": true,
        "warn_above": 0
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 293
      }
    },
    "potion_counter": {
      "id": "potion_counter",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "Potion counter",
      "icon": "flask",
      "description": "How many potions of one effect you are carrying, which in pot PvP is what you plan around.",
      "source": "the `inventory` field, via the tick sensor",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "effect": "healing",
        "splash_only": true,
        "show_label": true
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 331
      }
    },
    "block_outline": {
      "id": "block_outline",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Block outline",
      "icon": "cube",
      "description": "The box vanilla draws round the block you are looking at — recoloured, thickened, or gone.",
      "source": "vanilla's own `WorldRenderer.drawBlockOutline`, redirected",
      "defaults": {
        "on": false,
        "hide": false,
        "color": "#00000066",
        "line_width": 2
      }
    },
    "nametags": {
      "id": "nametags",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Nametags",
      "icon": "users",
      "description": "Shrink, thin out or hide the floating names, which in a team mode are most of what is on screen.",
      "source": "vanilla's own `EntityRenderer.renderLabelIfPresent`, redirected",
      "defaults": {
        "on": false,
        "hide": false,
        "nametag_scale": 1,
        "plate": true,
        "max_distance": 64
      }
    },
    "sprint_reset": {
      "id": "sprint_reset",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "Sprint reset",
      "icon": "bolt",
      "description": "How many of your recent hits landed with a sprint behind them — whether the W-tap is working.",
      "source": "the `hits.dealt` and `hits.sprint_dealt` counters, via the tick sensor",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "window": 20,
        "style": "percent",
        "show_bar": false,
        "show_label": true,
        "warn_below": 0
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 331
      }
    },
    "knockback": {
      "id": "knockback",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "Knockback meter",
      "icon": "move",
      "description": "How far the last hit sent you — the half-second after it landed, in blocks.",
      "source": "the `knockback` field, measured over a fixed window by the tick sensor",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "subtle",
        "border": false,
        "padding": "normal",
        "show_label": true,
        "warn_above": 0
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 23,
        "dy": 369
      }
    }
  }
} as const satisfies ModRegistryDocument;
