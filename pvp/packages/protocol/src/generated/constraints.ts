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
  "decimals": {
    "min": 0,
    "max": 2
  },
  "warn_below": {
    "min": 0,
    "max": 1
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
  "sensitivity": {
    "min": 0.2,
    "max": 1
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
  "window_s": {
    "min": 10,
    "max": 60
  },
  "sidebar_scale": {
    "min": 0.5,
    "max": 1.5
  },
  "offset_x": {
    "min": -200,
    "max": 200
  },
  "offset_y": {
    "min": -200,
    "max": 200
  },
  "warn_above": {
    "min": 0,
    "max": 6
  },
  "nametag_scale": {
    "min": 0.5,
    "max": 2
  },
  "window": {
    "min": 5,
    "max": 40
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
    "bare",
    "subtle",
    "solid"
  ],
  "fps.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "keystrokes.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "keystrokes.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
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
    "bare",
    "subtle",
    "solid"
  ],
  "cps.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "cps.mode": [
    "left",
    "right",
    "both"
  ],
  "ping.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "ping.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "coordinates.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "coordinates.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "coordinates.layout": [
    "stacked",
    "inline"
  ],
  "armor_status.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "armor_status.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "armor_status.orientation": [
    "horizontal",
    "vertical"
  ],
  "potion_effects.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "potion_effects.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "watermark.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "watermark.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
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
    "bare",
    "subtle",
    "solid"
  ],
  "direction.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "direction.style": [
    "letter",
    "word",
    "axis"
  ],
  "combo.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "combo.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "saturation.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "saturation.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "saturation.style": [
    "number",
    "bar",
    "both"
  ],
  "momentum.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "momentum.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "momentum.unit": [
    "bps",
    "kmh"
  ],
  "memory.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "memory.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "memory.style": [
    "used",
    "used_of_max",
    "percent"
  ],
  "server_address.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "server_address.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "server_address.style": [
    "short",
    "full"
  ],
  "item_counter.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "item_counter.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "item_counter.source": [
    "held",
    "inventory"
  ],
  "stopwatch.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "stopwatch.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
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
  "hit_trade.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "hit_trade.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "hit_trade.style": [
    "traded",
    "ratio",
    "dealt"
  ],
  "clock.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "clock.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "clock.format": [
    "h24",
    "h12"
  ],
  "cps_graph.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "cps_graph.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "cps_graph.mode": [
    "left",
    "right",
    "both"
  ],
  "reach.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "reach.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "potion_counter.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "potion_counter.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "potion_counter.effect": [
    "healing",
    "speed",
    "strength",
    "fire_resistance",
    "any"
  ],
  "sprint_reset.background": [
    "bare",
    "subtle",
    "solid"
  ],
  "sprint_reset.padding": [
    "none",
    "tight",
    "normal",
    "roomy",
    "wide"
  ],
  "sprint_reset.style": [
    "percent",
    "ratio"
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
  "freelook.keybind"
] as const;
