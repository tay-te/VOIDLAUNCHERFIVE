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
  "toggle_sprint.mode": [
    "toggle",
    "hold"
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
  ]
} as const;
