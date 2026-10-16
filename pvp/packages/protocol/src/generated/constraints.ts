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

/**
 * Numeric bounds per settings property name, from each `<id>_settings` sub-schema.
 *
 * Keyed by property name alone: a name means the same thing in every mod, and `gen.mjs`
 * throws if two mods ever disagree. The UI's step and unit are presentation and stay in
 * `packages/ingame/src/registry.ts`; what cannot be invented locally is the bound Java
 * will clamp to, and that is what this carries.
 */
export const SETTING_BOUNDS: Readonly<Record<string, { readonly min: number; readonly max: number }>> =
  {
  "scale": {
    "min": 0.25,
    "max": 4
  },
  "opacity": {
    "min": 0,
    "max": 1
  },
  "corner_radius": {
    "min": 0,
    "max": 20
  },
  "fade_ms": {
    "min": 0,
    "max": 500
  },
  "window_ms": {
    "min": 200,
    "max": 5000
  },
  "good_ms": {
    "min": 0,
    "max": 1000
  },
  "bad_ms": {
    "min": 0,
    "max": 2000
  },
  "jitter_warn_ms": {
    "min": 0,
    "max": 500
  },
  "spike_ms": {
    "min": 0,
    "max": 1000
  },
  "window": {
    "min": 3,
    "max": 60
  },
  "decimals": {
    "min": 0,
    "max": 2
  },
  "warn_below": {
    "min": 0,
    "max": 1
  },
  "hidden_effects": {
    "min": 0,
    "max": 16777215
  },
  "blink_below_s": {
    "min": 0,
    "max": 30
  },
  "gamma": {
    "min": 1,
    "max": 15
  },
  "line_width": {
    "min": 0.5,
    "max": 5
  },
  "max_distance": {
    "min": 4,
    "max": 64
  },
  "fov_divisor": {
    "min": 1.1,
    "max": 10
  },
  "size": {
    "min": 1,
    "max": 20
  },
  "thickness": {
    "min": 1,
    "max": 5
  },
  "gap": {
    "min": 0,
    "max": 10
  },
  "reset_ms": {
    "min": 500,
    "max": 10000
  },
  "low_threshold": {
    "min": 0,
    "max": 64
  },
  "fov": {
    "min": 30,
    "max": 110
  },
  "intensity": {
    "min": 0,
    "max": 1
  },
  "threshold": {
    "min": 1,
    "max": 20
  },
  "strength": {
    "min": 0,
    "max": 1
  },
  "always_draw_within": {
    "min": 1,
    "max": 16
  },
  "block_entity_distance": {
    "min": 16,
    "max": 64
  },
  "armor_stand_distance": {
    "min": 8,
    "max": 64
  },
  "item_frame_distance": {
    "min": 8,
    "max": 64
  },
  "sign_text_distance": {
    "min": 4,
    "max": 64
  },
  "particle_cap": {
    "min": 100,
    "max": 4000
  },
  "block_break_particles": {
    "min": 0,
    "max": 1
  },
  "explosion_particles": {
    "min": 0,
    "max": 1
  },
  "unfocused_fps": {
    "min": 5,
    "max": 120
  },
  "menu_fps": {
    "min": 15,
    "max": 240
  },
  "outline_width": {
    "min": 0.5,
    "max": 6
  },
  "glint_passes": {
    "min": 1,
    "max": 3
  },
  "tag_background": {
    "min": 0,
    "max": 1
  },
  "tag_scale": {
    "min": 0.5,
    "max": 1.5
  },
  "hotbar_scale": {
    "min": 0,
    "max": 6
  },
  "inventory_scale": {
    "min": 0,
    "max": 6
  },
  "blur_radius": {
    "min": 1,
    "max": 10
  },
  "menu_dim": {
    "min": 0,
    "max": 1
  },
  "time_ticks": {
    "min": 0,
    "max": 23999
  },
  "hide_after_ms": {
    "min": 0,
    "max": 30000
  },
  "background_opacity": {
    "min": 0,
    "max": 1
  },
  "history_length": {
    "min": 100,
    "max": 5000
  }
} as const;

/**
 * Enum options per `<mod>.<key>`, from each `<id>_settings` sub-schema.
 *
 * Keyed by mod *and* key, because `mode` differs between `cps` and `toggle_sprint` and
 * `style` between `crosshair` and `watermark`. A missing table draws a property row with
 * no control at all, so completeness here is what makes that unrepresentable.
 */
export const SETTING_OPTIONS: Readonly<Record<string, readonly string[]>> =
  {
  "fps.background": [
    "none",
    "subtle",
    "solid"
  ],
  "fps.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "keystrokes.background": [
    "none",
    "subtle",
    "solid"
  ],
  "keystrokes.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "keystrokes.key_color": [
    "shell",
    "raised",
    "pill",
    "sky",
    "teal"
  ],
  "keystrokes.pressed_color": [
    "accent",
    "sky",
    "warn",
    "fear",
    "teal"
  ],
  "cps.background": [
    "none",
    "subtle",
    "solid"
  ],
  "cps.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "cps.mode": [
    "left",
    "right",
    "both"
  ],
  "ping.background": [
    "none",
    "subtle",
    "solid"
  ],
  "ping.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "coordinates.background": [
    "none",
    "subtle",
    "solid"
  ],
  "coordinates.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "coordinates.layout": [
    "stacked",
    "inline"
  ],
  "armor_status.background": [
    "none",
    "subtle",
    "solid"
  ],
  "armor_status.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "armor_status.orientation": [
    "horizontal",
    "vertical"
  ],
  "potion_effects.background": [
    "none",
    "subtle",
    "solid"
  ],
  "potion_effects.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "watermark.background": [
    "none",
    "subtle",
    "solid"
  ],
  "watermark.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "watermark.style": [
    "full",
    "mark",
    "word"
  ],
  "crosshair.style": [
    "default",
    "cross",
    "dot",
    "circle",
    "t_shape",
    "none"
  ],
  "direction.background": [
    "none",
    "subtle",
    "solid"
  ],
  "direction.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "direction.style": [
    "letter",
    "word",
    "axis"
  ],
  "combo.background": [
    "none",
    "subtle",
    "solid"
  ],
  "combo.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "saturation.background": [
    "none",
    "subtle",
    "solid"
  ],
  "saturation.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "saturation.style": [
    "number",
    "bar",
    "both"
  ],
  "momentum.background": [
    "none",
    "subtle",
    "solid"
  ],
  "momentum.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "momentum.unit": [
    "bps",
    "kmh"
  ],
  "memory.background": [
    "none",
    "subtle",
    "solid"
  ],
  "memory.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "memory.style": [
    "used",
    "used_of_max",
    "percent"
  ],
  "server_address.background": [
    "none",
    "subtle",
    "solid"
  ],
  "server_address.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "server_address.style": [
    "short",
    "full"
  ],
  "item_counter.background": [
    "none",
    "subtle",
    "solid"
  ],
  "item_counter.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "item_counter.track": [
    "held_stack",
    "held_total",
    "ender_pearl",
    "golden_apple",
    "arrow",
    "snowball",
    "egg",
    "cobweb",
    "wool",
    "tnt",
    "fire_charge",
    "iron_ingot",
    "gold_ingot",
    "diamond",
    "emerald"
  ],
  "stopwatch.background": [
    "none",
    "subtle",
    "solid"
  ],
  "stopwatch.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "stopwatch.format": [
    "auto",
    "mmss",
    "hmmss"
  ],
  "toggle_sneak.mode": [
    "toggle",
    "hold"
  ],
  "overlay.view_bobbing": [
    "vanilla",
    "minimal",
    "off"
  ],
  "freelook.mode": [
    "hold",
    "toggle"
  ],
  "freelook.perspective": [
    "third_back",
    "third_front",
    "free"
  ],
  "damage_tint.camera_shake": [
    "vanilla",
    "reduced",
    "off"
  ],
  "old_animations.block_hit": [
    "vanilla",
    "one_seven"
  ],
  "block_outline.outline_style": [
    "outline",
    "fill",
    "both",
    "none"
  ],
  "scoreboard.background": [
    "none",
    "subtle",
    "solid"
  ],
  "scoreboard.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "fog.distance_fog": [
    "vanilla",
    "reduced",
    "off"
  ],
  "fog.water_fog": [
    "vanilla",
    "reduced",
    "off"
  ],
  "fog.lava_fog": [
    "vanilla",
    "reduced",
    "off"
  ],
  "potion_counter.background": [
    "none",
    "subtle",
    "solid"
  ],
  "potion_counter.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "potion_counter.track": [
    "health_potion",
    "soup",
    "both"
  ],
  "reach.background": [
    "none",
    "subtle",
    "solid"
  ],
  "reach.padding": [
    "tight",
    "normal",
    "roomy"
  ],
  "reach.mode": [
    "last",
    "average"
  ],
  "cosmetics.glow_level": [
    "off",
    "low",
    "high"
  ],
  "cosmetics.reactive": [
    "off",
    "subtle",
    "full"
  ]
} as const;

/**
 * Every settings property that is a keybind, as `<mod>.<key>`.
 *
 * Derived from the property's `$ref` at `mods.json#/definitions/keybind`, which is the only
 * thing that actually makes a setting a keybind. `ModRegistry.java` already classified by that
 * type and says why in as many words — "not by the property's name, which is `keybind` on one
 * mod and `key` on another" — while the TypeScript side matched those two names literally.
 * The stopwatch's `start_key` and `reset_key` matched neither, so they fell through to the
 * enum branch, which draws a chip row over an empty options table: a label and no control,
 * in game only. That is the same silent shape `watermark.style` shipped as.
 */
export const KEYBIND_SETTINGS: readonly string[] =
  [
  "keystrokes.keybind",
  "toggle_sprint.keybind",
  "fullbright.keybind",
  "hitboxes.keybind",
  "zoom.key",
  "stopwatch.start_key",
  "stopwatch.reset_key",
  "toggle_sneak.keybind",
  "freelook.keybind",
  "cosmetics.emote_key"
] as const;
