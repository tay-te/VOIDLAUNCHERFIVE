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
  "version": 14,
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
        "dx": 20,
        "dy": 20
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
        "pressed_color": "accent",
        "animate": true,
        "fade_ms": 120
      },
      "default_placement": {
        "anchor": "bottom-left",
        "dx": 20,
        "dy": -20
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
        "show_peak": false,
        "ignore_cancelled": false
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 122,
        "dy": 20
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
        "show_host": true,
        "show_average": false,
        "show_jitter": false,
        "jitter_warn_ms": 20,
        "spike_ms": 80,
        "window": 10
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 256,
        "dy": 20
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
        "dx": 20,
        "dy": 128
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
        "orientation": "vertical",
        "show_durability": true,
        "show_held_item": true,
        "warn_below": 0.5
      },
      "default_placement": {
        "anchor": "bottom-right",
        "dx": -20,
        "dy": -20
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
        "hide_ambient": false,
        "hidden_effects": 0,
        "blink_below_s": 5
      },
      "default_placement": {
        "anchor": "top-right",
        "dx": -20,
        "dy": 20
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
        "dx": 20,
        "dy": 84
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
        "keybind": "NONE",
        "state_colors": false,
        "hittable_color": "#2FB8A6FF",
        "damaged_color": "#FF5A5AFF"
      }
    },
    "zoom": {
      "id": "zoom",
      "kind": "gameplay",
      "category": "utility",
      "hypixel_safe": "safe",
      "label": "Zoom",
      "icon": "zoom",
      "description": "Narrows FOV while the zoom key is held; the mouse wheel adjusts it.",
      "source": "FOV override while key held",
      "defaults": {
        "on": true,
        "key": "C",
        "fov_divisor": 4,
        "smooth": true,
        "cinematic": false,
        "scroll_adjust": true,
        "scale_sensitivity": true
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
        "dx": 20,
        "dy": 192
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
        "anchor": "center",
        "dx": 40,
        "dy": 24
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
        "dx": 20,
        "dy": 256
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
        "dx": 20,
        "dy": 320
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
        "dx": -20,
        "dy": -188
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
        "dx": -20,
        "dy": -252
      }
    },
    "item_counter": {
      "id": "item_counter",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "Item counter",
      "icon": "box",
      "description": "How many of the item in your hand, or a chosen item, you have left.",
      "source": "`held_count` and `counts` on the tick payload: the held stack, or an inventory-wide count",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "show_label": true,
        "low_threshold": 0,
        "track": "held_stack"
      },
      "default_placement": {
        "anchor": "bottom-left",
        "dx": 184,
        "dy": -20
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
        "dx": -20,
        "dy": -316
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
    "culling": {
      "id": "culling",
      "kind": "gameplay",
      "category": "utility",
      "hypixel_safe": "safe",
      "label": "Entity culling",
      "icon": "eye",
      "description": "Skips drawing entities and chests, signs and banners that are hidden behind walls or too far away.",
      "source": "an async voxel ray test against `EntityRenderDispatcher#shouldRender` and `BlockEntityRenderDispatcher#renderEntity`",
      "defaults": {
        "on": true,
        "occlude_entities": true,
        "occlude_block_entities": true,
        "always_draw_within": 4,
        "nametags_through_walls": true,
        "block_entity_distance": 64,
        "armor_stand_distance": 64,
        "item_frame_distance": 64,
        "sign_text_distance": 32
      }
    },
    "particles": {
      "id": "particles",
      "kind": "gameplay",
      "category": "utility",
      "hypixel_safe": "safe",
      "label": "Particle limiter",
      "icon": "sparkle",
      "description": "Caps live particles, skips the ones outside your view, and thins out block-break and explosion bursts.",
      "source": "`ParticleManager#addParticle`, `#addBlockBreakParticles` and `#renderParticles`",
      "defaults": {
        "on": true,
        "frustum_cull": true,
        "particle_cap": 4000,
        "block_break_particles": 1,
        "explosion_particles": 1
      }
    },
    "frame_limiter": {
      "id": "frame_limiter",
      "kind": "gameplay",
      "category": "utility",
      "hypixel_safe": "safe",
      "label": "Frame limiter",
      "icon": "gauge",
      "description": "Lowers the frame rate while the game is in the background, in a menu, or minimised.",
      "source": "`MinecraftClient#getMaxFramerate`, which feeds vanilla's own `Display.sync`",
      "defaults": {
        "on": true,
        "unfocused_fps": 30,
        "menu_fps": 60,
        "idle_minimized": true
      }
    },
    "block_outline": {
      "id": "block_outline",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Block outline",
      "icon": "cube",
      "description": "Recolours, thickens, fills or removes the outline on the block you are looking at.",
      "source": "`WorldRenderer#drawBlockOutline`",
      "defaults": {
        "on": false,
        "outline_style": "outline",
        "outline_color": "#EDEEEFCC",
        "outline_width": 2.5,
        "fill_color": "#EDEEEF26"
      }
    },
    "shiny_pots": {
      "id": "shiny_pots",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Shiny pots",
      "icon": "flask",
      "description": "Gives every potion a strong enchantment glint so pots read at a glance in the hotbar.",
      "source": "`ItemRenderer#renderItem` and its private glint pass",
      "defaults": {
        "on": false,
        "all_potions": true,
        "glint_passes": 2,
        "tint_glint": false
      }
    },
    "clear_glass": {
      "id": "clear_glass",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Clear glass",
      "icon": "box",
      "description": "Draws glass as clear air, without a resource pack.",
      "source": "`BlockRenderManager#renderBlock` during chunk builds",
      "defaults": {
        "on": false,
        "clear_panes": true,
        "clear_stained": false
      }
    },
    "nametags": {
      "id": "nametags",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Nametags",
      "icon": "users",
      "description": "Shows your own nametag in third person and restyles every nametag's background, size and shadow.",
      "source": "`EntityRenderer#renderLabelIfPresent` and `LivingEntityRenderer#hasLabel`",
      "defaults": {
        "on": false,
        "show_own": true,
        "hide_others": false,
        "tag_background": 0.25,
        "tag_scale": 1,
        "tag_shadow": true
      }
    },
    "scoreboard": {
      "id": "scoreboard",
      "kind": "hud",
      "category": "hud",
      "hypixel_safe": "safe",
      "label": "Scoreboard",
      "icon": "star",
      "description": "The server's sidebar scoreboard, redrawn as a HUD chip you can move, scale or hide.",
      "source": "the sidebar `ScoreboardObjective`, via the `scoreboard` bridge channel",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "hidden": false,
        "show_numbers": false,
        "show_title": true,
        "server_colors": true
      },
      "default_placement": {
        "anchor": "right",
        "dx": -20,
        "dy": 0
      }
    },
    "gui_scale": {
      "id": "gui_scale",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "GUI scale",
      "icon": "move",
      "description": "Sizes the hotbar and inventories independently of Minecraft's GUI scale.",
      "source": "`InGameHud#render` (hotbar, bars, experience, held-item name) and `Screen#init` for container screens",
      "defaults": {
        "on": false,
        "hotbar_scale": 2,
        "inventory_scale": 3
      }
    },
    "fog": {
      "id": "fog",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "grey",
      "label": "Fog",
      "icon": "droplet",
      "description": "Reduces or removes distance, water and lava fog. Blindness is never touched.",
      "source": "`GameRenderer#renderFog`",
      "defaults": {
        "on": false,
        "distance_fog": "reduced",
        "water_fog": "reduced",
        "lava_fog": "vanilla"
      }
    },
    "menu_blur": {
      "id": "menu_blur",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Menu blur",
      "icon": "layers",
      "description": "Blurs the world behind inventories, chests and the pause menu.",
      "source": "`Screen#renderBackground`, through `screen/BlurBackdrop.java`",
      "defaults": {
        "on": false,
        "blur_radius": 4,
        "blur_fade": true,
        "menu_dim": 0.5
      }
    },
    "time_weather": {
      "id": "time_weather",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Time & weather",
      "icon": "clock",
      "description": "Fixes the time of day and clears the rain, on your screen only.",
      "source": "`World#getSkyAngle`, `#getRainGradient` and `#getThunderGradient` on the client world",
      "defaults": {
        "on": false,
        "lock_time": true,
        "time_ticks": 6000,
        "clear_weather": true
      }
    },
    "potion_counter": {
      "id": "potion_counter",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "safe",
      "label": "Potion counter",
      "icon": "heart",
      "description": "Healing potions or soup left in your inventory.",
      "source": "`counts.health_potion` and `counts.soup` on the tick payload",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "track": "health_potion",
        "show_label": true,
        "low_threshold": 3
      },
      "default_placement": {
        "anchor": "bottom-left",
        "dx": 184,
        "dy": -84
      }
    },
    "reach": {
      "id": "reach",
      "kind": "hud",
      "category": "pvp",
      "hypixel_safe": "grey",
      "label": "Reach display",
      "icon": "sword",
      "description": "How far away your last hit landed, in blocks.",
      "source": "`reach` on the tick payload: eye-to-hit-point distance at the moment of each landed attack",
      "defaults": {
        "on": false,
        "scale": 1,
        "opacity": 1,
        "background": "none",
        "border": false,
        "padding": "normal",
        "decimals": 2,
        "mode": "last",
        "show_label": true,
        "hide_after_ms": 0
      },
      "default_placement": {
        "anchor": "top-left",
        "dx": 20,
        "dy": 384
      }
    },
    "chat": {
      "id": "chat",
      "kind": "gameplay",
      "category": "utility",
      "hypixel_safe": "safe",
      "label": "Chat",
      "icon": "users",
      "description": "Timestamps, stacked repeats, click to copy and a longer history.",
      "source": "ChatHud: message add, line render and history cap; ChatScreen: mouse click",
      "defaults": {
        "on": false,
        "timestamps": true,
        "stack_repeats": true,
        "click_to_copy": true,
        "background_opacity": 0.5,
        "history_length": 1000
      }
    },
    "cosmetics": {
      "id": "cosmetics",
      "kind": "gameplay",
      "category": "visual",
      "hypixel_safe": "safe",
      "label": "Cosmetics",
      "icon": "star",
      "description": "Capes, wings, hats, halos, trails and emotes, on you and on other VOID players.",
      "source": "the cosmetic renderer's player-model layer (cape, back, head, face, halo, badge), the trail emitter, and the emote animator",
      "defaults": {
        "on": true,
        "others": true,
        "own_first_person": false,
        "glow_level": "high",
        "emote_camera": true,
        "reactive": "full",
        "emote_key": "B"
      }
    }
  }
} as const satisfies ModRegistryDocument;
