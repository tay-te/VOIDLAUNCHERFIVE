/**
 * Every number the launcher illustrations draw, in one place.
 *
 * The registry is `pvp/schema/mods.json` (29 mods, `examples[0]`); ids, labels and
 * categories are copied from it verbatim, in the launcher's `MOD_GRID_ORDER`.
 * The loadouts are illustrative but internally consistent: each count is the length
 * of its own mod list, and every id is a real registry id.
 */

export type Category = "hud" | "pvp" | "visual" | "utility"

export const MODS = [
  { id: "fps", label: "FPS display", cat: "hud" },
  { id: "keystrokes", label: "Keystrokes", cat: "hud" },
  { id: "cps", label: "CPS counter", cat: "hud" },
  { id: "armor_status", label: "Armor status", cat: "hud" },
  { id: "crosshair", label: "Crosshair", cat: "visual" },
  { id: "potion_effects", label: "Potion effects", cat: "hud" },
  { id: "zoom", label: "Zoom", cat: "utility" },
  { id: "toggle_sprint", label: "Toggle sprint", cat: "pvp" },
  { id: "hitboxes", label: "Hitboxes", cat: "pvp" },
  { id: "fullbright", label: "Fullbright", cat: "visual" },
  { id: "ping", label: "Ping display", cat: "hud" },
  { id: "coordinates", label: "Coordinates", cat: "hud" },
  { id: "direction", label: "Direction", cat: "hud" },
  { id: "watermark", label: "Watermark", cat: "visual" },
  { id: "combo", label: "Combo counter", cat: "pvp" },
  { id: "saturation", label: "Saturation", cat: "hud" },
  { id: "momentum", label: "Momentum", cat: "hud" },
  { id: "memory", label: "Memory", cat: "hud" },
  { id: "server_address", label: "Server address", cat: "utility" },
  { id: "item_counter", label: "Item counter", cat: "pvp" },
  { id: "stopwatch", label: "Stopwatch", cat: "utility" },
  { id: "fov", label: "FOV changer", cat: "pvp" },
  { id: "toggle_sneak", label: "Toggle sneak", cat: "pvp" },
  { id: "overlay", label: "Overlay", cat: "visual" },
  { id: "freelook", label: "Freelook", cat: "pvp" },
  { id: "hit_color", label: "Hit colour", cat: "pvp" },
  { id: "damage_tint", label: "Damage tint", cat: "pvp" },
  { id: "old_animations", label: "Old animations", cat: "pvp" },
  { id: "old_input", label: "Old input", cat: "pvp" },
] as const satisfies readonly { id: string; label: string; cat: Category }[]

export type ModId = (typeof MODS)[number]["id"]

export const MOD_TOTAL = MODS.length // 29

export type Loadout = {
  id: string
  name: string
  /** Average fps the Play screen quotes under the name. */
  fps: number
  mods: readonly ModId[]
}

/** Idle order is array order. Counts: 11 · 9 · 6 · 14 of 29. */
export const LOADOUTS: readonly Loadout[] = [
  {
    id: "sword-pvp",
    name: "Sword PvP",
    fps: 142,
    mods: [
      "fps", "keystrokes", "cps", "armor_status", "crosshair", "potion_effects",
      "zoom", "toggle_sprint", "ping", "combo", "watermark",
    ],
  },
  {
    id: "bedwars",
    name: "Bedwars",
    fps: 138,
    mods: [
      "fps", "keystrokes", "armor_status", "zoom", "toggle_sprint", "fullbright",
      "ping", "item_counter", "coordinates",
    ],
  },
  {
    id: "sumo",
    name: "Sumo",
    fps: 151,
    mods: ["fps", "keystrokes", "cps", "toggle_sprint", "hitboxes", "ping"],
  },
  {
    id: "uhc",
    name: "UHC",
    fps: 131,
    mods: [
      "fps", "armor_status", "crosshair", "potion_effects", "zoom", "toggle_sprint",
      "fullbright", "coordinates", "direction", "saturation", "ping", "freelook",
      "watermark", "stopwatch",
    ],
  },
]

/** The tiles the crop shows — a 4 × 2 window onto the grid, chosen so every switch flips several. */
export const TILE_MODS: readonly ModId[] = [
  "fps", "keystrokes", "cps", "armor_status",
  "crosshair", "zoom", "hitboxes", "fullbright",
]

export const TILE_COLS = 4

export function modMeta(id: ModId) {
  return MODS.find((m) => m.id === id)!
}

/* --------------------------------------------------------------------------
   PLACEHOLDER — MEASURE BEFORE SHIP

   The frame model behind chapter 04. These are board 35's and board 22's numbers
   (Figma 400:8, 384:8): a 7.0 ms average frame, a 1% low of about 8.4 ms, and a
   frame split as game tick 2.1 + world render 4.6 + VOID 0.3 of a 16.7 ms budget.
   README "Before this goes public" item 4: the 0.3 ms figure is unmeasured.
   The histogram is generated from this model with a fixed seed, so the server and
   the client draw the same first frame.
   -------------------------------------------------------------------------- */
export const PERF = {
  /** Mean frame time, ms. With this seed the first frame reads 7.0 ms avg, 142 fps
   *  (the Play screen's "142 fps avg") and a 1% low of 119 fps (8.4 ms, board 22). */
  frameMs: 7.08,
  /** Frame-to-frame jitter, ms (≈ one standard deviation). */
  jitterMs: 0.36,
  /** Chance a frame carries a GC / chunk-load hitch, and how long it adds. */
  hitchChance: 0.03,
  hitchMs: [0.9, 1.6] as const,
  /** The budget the histogram's line and the budget row are drawn against. */
  budgetMs: 16.7,
  budgetFps: 60,
  /** One frame, split — board 35. Sums to the 7.0 ms average. */
  split: { tick: 2.1, render: 4.6, void: 0.3 },
  /** Cells in the budget row — one cell ≈ 0.33 ms, so VOID is exactly one. */
  budgetCells: 50,
  seed: 0x5eed,
} as const
