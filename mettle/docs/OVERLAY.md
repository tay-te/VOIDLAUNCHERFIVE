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
  skills: { id: string; name: string; attribute: Attr; released: boolean; icon: string }[];  // icons 16×16
  attributes: { id: Attr; name: string; perPoint: string }[];
};
type Attr = "strength" | "agility" | "vitality" | "wits" | "presence";
```

Skill trees (built for Mining; the others follow) add, per skill: `skills[].level`, `skills[].xp`, `skills[].nextXp`,
`skills[].points`, `skills[].paths {a, b}`, and `perks[]`: `{ id, skill, kind: "root"|"path"|"mastery"|"feat"|"synergy"|"teaching",
tier, path, rank, maxRank, cost, name, description (at the next rank), hint (feats, until earned), locked: reason | null }`.
Actions: `learnPerk(id)`, `spendAttribute(attribute)`; answers come back as `mettleFeedback` chips.

Other planned additions, as their systems land: `state.signature { id, ready, readyInSeconds }` (Rally's cooldown, from
`SignaturePerks.rallyCooldown`), skill XP and levels, perk trees, load and capacity.

## Overlay → Java (calls)

`client.overlay.MettleActions`:

| Call | Effect |
|---|---|
| `chooseBackground({ background, spent, favoured })` | sends the request; the server validates (`BackgroundRules`) and answers with `mettleResult` |
| `closeMenu()` | closes the overlay; an undecided player gets the reminder chip |

Textures referenced by `art` and `icon` are the mod's own resources; the host serves them to Ultralight from the
resource manager (a `mettle://` scheme or data URLs).
