# VOID Mettle — the overlay contract

**Decision (user, 2026-10-05):** Mettle's player-facing UI is built as an **Ultralight HTML/CSS overlay**, the same
technology as the VOID PvP client's in-game menu (`docs/PVP_ARCHITECTURE.md` §6). Screens are designed in Figma first
(page `264:2`, section "Classes · overlay (Ultralight)"), then built from the `mettle / ui / *` components. The Java
screens in `client.character` (picker, K screen) stay as a **dev fallback** until the overlay ships, and they read the
same sources described here.

The overlay needs an Ultralight host for 26.3 (pvp's binding targets 1.8.9; `mod/native` and the GL compositing must
be ported to the 26.3 renderer). That port is its own task; this contract is what Mettle gives it.

Ultralight's limits apply to every screen (`pvp/design/quiet-cell-system.md` §9): flexbox and absolute positioning
only; no CSS grid, backdrop-filter, blur, text-shadow, mix-blend-mode, 3D transforms, canvas or rAF loops.

---

## Java → overlay (pushed events)

Same shape as pvp's `window.void`: `void.on(event, handler)`. Mettle pushes through
`dev.voidmc.mettle.client.overlay.OverlayBridge`.

| Event | Payload | When |
|---|---|---|
| `mettle` | the state below | on join, and whenever it changes (today: every 10 ticks while a listener is attached; change detection comes with the host) |
| `mettleResult` | `{ ok: boolean, reason?: "already_chosen" \| "unknown_background" \| "points_not_placed" \| "points_invalid" \| "skills_not_picked" \| "skill_invalid" }` | after `chooseBackground` |
| `mettleFeedback` | `{ perk: "mettle:rally", text: "Rally · +1 ♥" }` | a signature perk fired (HUD chip; merge repeats, fade after ~2 s) |
| `mettlePicker` | `{ byOperator: boolean }` | the server asks for the picker (first join, or an op reset) |
| `mettleMoment` | `{ kind: "level" \| "tier" \| "character" \| "learned" \| "mastery" \| "feat" \| "synergy" \| "teaching", skill?: "mettle:mining", perk?: "mettle:mining/quarryman", level: number, tier: number, rank: number, title: "Mining 9 · Apprentice Miner", subtitle?: "1 point to spend · press K", colour: "#F5B03C" }` | a progress moment (UI.md §6b): show a toast; queue them, merge rapid repeats of one skill or perk. The matching `mettleFeedback` chip follows it and can be skipped. Java plays the sound. |

### The `mettle` state (`client.overlay.MettleState`)

All text is already translated. Ids are namespaced strings.

```ts
type MettleState = {
  phase: "offline" | "undecided" | "chosen";
  character: {
    background: string | null;                    // "mettle:ranger"
    attributes: Record<Attr, number>;             // current values, 1–20
    spent: Partial<Record<Attr, number>>;          // points the player placed
    favoured: string[];                            // skill ids
    capacityKg: number;                            // the carry_capacity attribute
    level: number; xp: number;                     // character level, total XP
    levelXp: number; nextXp: number;               // XP at this level's start and at the next
    points: number;                                // attribute points waiting to be placed
    maxAttribute: number;                          // 20
  };
  backgrounds: {
    id: string; name: string; tagline: string;
    art: string;                                   // "mettle:textures/gui/background/ranger.png" (48×24, scale by whole numbers)
    bonus: Partial<Record<Attr, number>>;
    favoured: string[]; freePoints: number; freeFavoured: number;
    signature: { id: string; name: string; description: string } | null;
    kit: { item: string; count: number; name: string }[];
    kitSummary: string;
  }[];
  skills: { id: string; name: string; attribute: Attr; released: boolean; icon: string;     // icons 16×16
            colour: string;                       // "#F5B03C", the skill's signature hue (skill data "colour")
            title: string; lore: string }[];      // "Journeyman Miner"; one line of flavour
  attributes: { id: Attr; name: string; perPoint: string }[];
  burden: {                                        // {} until the first sync
    load: number; capacity: number;                // kg
    band: "light" | "burdened" | "overburdened" | "pinned";
    exempt: boolean;                               // creative/spectator: show "—"
    burdenedAt: number; overburdenedAt: number;    // fractions of capacity, meter marks (0.75 / 1.0 by default)
    pinnedAt: number;                              // -1 when this player can't be pinned
    heaviest?: { item: string; kg: number; stacks: number; text: string };  // "Cobblestone ×3 stacks · 48.0 kg"
  };
  gear: Record<"back" | "belt" | "quiver", { item: string; name: string; pockets: number; used: number } | null>;
  tiers: { tier: 1 | 2 | 3 | 4; skillLevel: number; attribute: number }[];   // TREES.md §1 gates
  feedback: { perk: string; text: string; ageMs: number }[];  // chips from the last 4 s (for screens that hide the HUD)
  moments: object[];                                         // mettleMoment payloads from the last 4 s, each with ageMs
};
type Attr = "strength" | "agility" | "vitality" | "wits" | "presence";
```

Skill trees add, per skill: `skills[].level`, `skills[].xp`, `skills[].levelXp`, `skills[].nextXp`,
`skills[].points`, `skills[].paths {a, b}`, and `perks[]`: `{ id, skill, kind: "root"|"path"|"mastery"|"feat"|"synergy"|"teaching",
tier, path, side (masteries: a | b | any), rank, maxRank, cost, order, name, description (at the next rank), rankValues (e.g. ["5 %",
"10 %", "15 %"]), synergy ({skill: level}, synergies only), hint (feats, until earned), locked: reason | null }`.
Actions: `learnPerk(id)`, `spendAttribute(attribute)`; answers come back as `mettleFeedback` chips.

Band changes also arrive as `mettleFeedback` chips with perk ids `mettle:burden/{light,burdened,overburdened,pinned}`
(the text says what happened and names the heaviest stack), so the HUD can show the chip without polling.

Still planned: `state.signature { id, ready, readyInSeconds }` (Rally's cooldown, from `SignaturePerks.rallyCooldown`)
and perk cooldowns (docs/requests/defense.md).

## Overlay → Java (calls)

`client.overlay.MettleActions`:

| Call | Effect |
|---|---|
| `chooseBackground({ background, spent, favoured })` | sends the request; the server validates (`BackgroundRules`) and answers with `mettleResult` |
| `closeMenu()` | closes the overlay; an undecided player gets the reminder chip |

Textures referenced by `art` and `icon` are the mod's own resources; the host serves them to Ultralight from the
resource manager (a `mettle://` scheme or data URLs).

## Hosts

The VOID Hub (`hub/`, branch `claude/hub-mettle`) is the first host: its window gains a **Gear** page (pack pockets,
belt and quiver, load) and a **Classes** page (overview, skill trees) over the player's own `InventoryMenu`. It reaches
this API by reflection (`MettleState.build`, `MettleActions.learnPerk` / `spendAttribute`,
`MettleScreens.setCharacterOpener`), so neither mod needs the other to build. A host like this draws Mettle's
screens but not the HUD, so it does **not** register with `OverlayBridge` (a listener there takes over the picker,
chips and the dev burden line); it pulls `MettleState.build` while its pages are open.

`MettleScreens.setCharacterOpener(BooleanSupplier)`: K opens the host's character page instead of the native
`CharacterScreen` (the supplier returns false when it cannot, and the native screen opens). A character without a
background still gets Mettle's picker.
