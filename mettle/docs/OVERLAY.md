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
            title: string; lore: string;          // "Journeyman Miner"; one line of flavour
            levelsBy: string }[];                 // how the skill gains XP, one line ("Break stone, deepslate and ore.")
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
`skills[].paths {a, b}`, and `perks[]`: `{ id, skill, kind: "root"|"path"|"mastery"|"feat"|"synergy"|"teaching"|"cursed",
tier, path, side (masteries: a | b | any), rank, maxRank, cost (depth only), order, name, description (at the next rank),
rankValues (e.g. ["5 %", "10 %", "15 %"]), synergy ({skill: level}, synergies only), hint (feats, until earned),
downside (cursed only), rarity + rarityColour (drafted kinds), locked: reason | null (null = in the deck now),
inDeck: boolean }`.

**The perk draft (DRAFT.md, 2026-10-10)** adds, per skill:

```ts
skills[]: {
  drafts: number;          // drafts waiting (badge); a hand is dealt for the first of them
  points: number;          // the same number, kept for hosts written for the old trees
  rerolls: number;         // the character's rerolls (one pool), repeated here for convenience
  odds: { common, rare, epic, cursed };   // % chance per slot of a hand dealt now (after empty rarities and the soft gate)
  hand: {                  // [] when nothing can be dealt yet (the draft waits) or nothing waits
    index: number;         // 0–2, what pickCard takes
    perk: string;          // "mettle:mining/tunneler"
    name: string; description: string;    // at the rank the card gives
    rank: number; maxRank: number;        // the rank keeping it gives (roots 1–3)
    rarity: "common" | "rare" | "epic" | "cursed"; frame: "#4DA3F5";   // frame colour by rarity
    cursed: boolean; downside: string | null;
    wildcard: boolean;     // from another skill used lately; dealt into this skill's hand
    skill: string; skillName: string; colour: string;   // the card's own skill (differs for a wildcard) and its hue
    kind: string; tier: number; path: string; icon: string;
    claim: boolean;        // a Mythic offer: claimed on top of the keep, the hand stays open (claimMythic)
  }[];
  mythicOffer: number;     // the hand's Mythic offer index, or -1 (CARDS.md §3.5)
}[];
draft: { rerolls: number; maxRerolls: 5; waiting: number /* all skills */; handSize: 3; wildcardChance: 0.12;
         rarities: { id, name, colour }[] };
```

Actions: `pickCard(skill, index)` (or `pickCard("mettle:mining#0")` for a one-string bridge), `reroll(skill)`,
`spendAttribute(attribute)`; answers come back as `mettleFeedback` chips (ids `mettle:draft/<skill>` and the perk). The
old `learnPerk(id)` sends nothing and adds a `mettle:draft/no_buying` chip ("Perks come from drafts now"); the
`character/learn_perk` packet is still registered and answered the same way. The level moment's subtitle now says
"A draft to pick · press K", and the hotbar nudge counts drafts plus attribute points ("3 waiting").

**Cards v2 (CARDS.md, wave A built 2026-10-10)** changes and adds. Built in `client.overlay.MettleCards`; every number
comes from the synced card cache (`mettle:character/cards`) and the data registries, so the Hub only reads.

- `skills[].hand[]` cards are **rolled offers** now: the v1 fields stay, and each card also has `keywords: {id, name,
  icon}[]`, `traits: {id, name, kind, attribute, value, valueText, boosted, boostedText, cap, description, arsenal?}[]`
  (`boosted` = the value after the viewer's attributes, CARDS.md §4.3), `needs` (skill level to slot), `needsText`
  ("Needs Mining 7"), `meetsRequirement`, `slotsAt` ("Slots at Mining 7", empty when it can be slotted now),
  `rarityName`, `baseName` and `name` (a root's name carries its rank: "Quarryman III"), `tierName` ("I", "II", "III",
  "M"). A root's `rank` is its rarity (Common I, Rare II, Epic III).
- `skills[].odds` is each **frame's** chance per card of a hand dealt now: `cursed` by depth, `common` / `rare` / `epic`
  by the rolled rarity odds (Common 70 − 1.5 L, Rare 24 + L, Epic 6 + 0.5 L).
- `perks[]`: `rank` is the **active** rank (earned, or from a slotted card), `owned` counts cards of the perk the player
  holds anywhere, and `locked` / `inDeck` follow cards v2's dealing (owning a perk never takes it out of the deck).

```ts
cards: {
  version: 0 | 1;                          // 1 once the character went through the v2 migration
  binder: { count: number; max: 300;
            cards: Card[] };               // every card the player owns (binder and decks), newest first
  decks: {                                 // one per released skill
    skill: string; name: string; colour: string; level: number;
    baseSlots: number; witsSlots: number; slots: number;   // slots open now (3 at skill 2 … 8, +1 Wits 8, +2 Wits 14)
    row: { index: number; card: string | null;            // card id in this slot
           dormant: boolean; open: boolean;
           locked?: string }[];                             // closed slots: "Opens at Mining 11" or "Wits 14"
    nextSlotAt: number;                    // skill level of the next base slot (0 = all open)
    freeRebuild: boolean;                  // this deck's first rebuild after the migration is still free
    rebuildHint: string;                   // "Visit a Mining trainer or a Card Shrine"
  }[];
  sets: {                                  // all fourteen keywords, for the set tracker strip
    id: string; name: string; theme: string; icon: string; colour: string;
    count: number;                         // slotted, non-dormant cards carrying it (each keyword of a card counts)
    decks: number;                         // how many decks those cards sit in (the 6 tier needs two)
    tier: 0 | 2 | 4 | 6;                   // reached
    active: boolean; pinned: boolean;      // one of the three active sets; pinned by the player
    bonuses: { tier: 2 | 4 | 6; description: string; reached: boolean; active: boolean }[];
  }[];
  traits: { id; name; skill; value: number; cap: number;   // active totals against twice the card cap
            text: string;                  // "Deepborn 24 % of 30 %"
            description: string }[];
  costs: { fees: { needsFrom; needsTo; bits; emeralds }[];  // rebuild fee per card by requirement (§7.1)
           mythicBits: 1000; mythicEmeralds: 24;
           freeSwaps: number; maxFreeSwaps: 3; freeRebuilds: string[];  // skills whose first rebuild is free
           freeBelowLevel: 5; lowLevel: boolean;           // below character level 5 every rebuild is free
           cityMasterScarChance: 0.5 };
  pins: { keywords: string[]; max: 3; canChange: boolean }; // pins change once an in-game day
  keywords: { id; name; icon; colour }[];  // the legend (one neutral glyph colour, white ink to tint)
  atShrine: boolean;                       // the player is at a Card Shrine (a shrine sitting is open)
  sitting: null | { kind: "shrine" | "trainer"; skills: string[];   // empty = any deck
                    cap: number;                                   // highest requirement it can take out (trainer tier)
                    master: boolean;                               // a city master: one card in two keeps its trait
                    coin: boolean };                               // fees in Hearth coin (else emeralds)
  attuneTicks: 24000;
  trade: null | {                          // an open trade, or a request this player made
    open: boolean; asking: boolean;        // asking: waiting for `partner` to accept
    partner: string;
    me: TradeSide; them: TradeSide;
    countdownSeconds: number;              // -1 when the countdown isn't running
    coin: boolean;                         // coin can be offered (Hearth); else emeralds go in as items
    maxCards: 9; maxItems: 9;
  };
};
// Wave D (Mythics)
cards.mythics: { all: { id; name; lore; effect; deed; source: "world_first" | "deed" | "drawn"; icon; skill?;
                        claimed: boolean; holder: string; day: number; copies: number }[];   // the Hall of Firsts
                 drawnWaiting: number; slots: 2 };
cards.reveal: null | { id; name; lore; effect; deed; source; icon; how: string; ageMs };       // a Mythic just earned (30 s)
cards.players: { name; mythics: (as above, without claimed)[] }[];   // everyone in sight: their Mythics (name cards)
// Mythic cards appear in the binder with rarity "mythic", kind "mythic", lore, the effect as description, no traits.
type TradeSide = {
  cards: (Card-like: id, code, perk, name, rarity, frame, keywords, traits, needs, scars, owners, drawnBy;
          on `them`: warning "Needs Mining 12 · you have Mining 9 · attunes in 20 min", meetsRequirement)[];
  items: { item; name; count }[]; coin: number; locked: boolean;
};
type Card = {
  id: string;                              // the ledger id (a number, as a string)
  code: string;                            // printed id, "MN-7Q4K-2F"
  perk; skill; skillName; colour; rarity; rarityName; frame; rank; icon;
  name; baseName; description; kind; tier; tierName; path; cursed; downside;
  needs; needsText; meetsRequirement;
  keywords: { id; name; icon }[]; traits: (as in the hand)[];
  place: "binder" | "slotted" | "trade" | "listed";
  slot: number;                            // -1 unless slotted
  deck: string;                            // the deck it belongs to (its own skill)
  dormant: boolean; dormantReason?: string; // "Needs two cards of its side", "Slot lost with Wits"…
  canSlot: boolean; lockedReason: string | null;           // why it can't go in its deck now ("Slots at Mining 7")
  attuned: boolean; attuningMinutes: number;
  scars: number;
  drawn: { by; level; day; millis; how; howText; text };   // "Drawn by Ashford at Mining 9 · day 41 · …"
  owners: number; lastOwners: { name; day }[];
  rebuild?: {                              // slotted cards: what taking it out costs at a rebuild (CARDS.md §7)
    bits: number; emeralds: number;        // 0 for a dormant card
    loses: { id; text } | null;            // the trait its scar takes ("Echoing 8 %"), null for none
    spared: boolean;                       // dormant, or a master's hand passes it over
    overCap: boolean;                      // beyond the open trainer sitting's cap
  };
};
```

Card actions (`MettleActions`, all answered by a `mettleFeedback` chip; card ids are the `id` strings, lists are
comma-separated):

| Call | Effect |
|---|---|
| `keepCard(skill, index, slot)` | keeps a hand's card: `slot` false = "Keep" (binder), true = "Keep & slot" (a free legal slot) |
| `pickCard(skill, index)` | the old keep: slots the card when a legal slot is free, else the binder |
| `claimMythic(skill)` | claims the hand's Mythic offer (`skills[].mythicOffer` ≥ 0): binder, Hall of Firsts and reveal (`cards.reveal`) as before, but it does **not** use the draft's keep: the Mythic leaves the hand and the hand stays open, so the player still keeps one of the other cards with `keepCard`. Nothing is sent without an offer. `keepCard` / `pickCard` at the Mythic's index do the same (and play no pick/burn) |
| `slotCard(id)` / `slotCard(id, slot)` | puts a binder card in the first free slot (or slot `slot`, 0-based) of its deck; free, anywhere |
| `unslotCard(id)` / `unslotCards("12,15")` | takes slotted cards of one deck out: free below character level 5, for dormant cards, on a deck's first free rebuild, or with free swaps; otherwise refused with "visit a trainer or a Card Shrine" |
| `pinSets("mettle:swift,mettle:keen")` | pins up to three sets (empty string: automatic); once an in-game day |
| `releaseCards("3,8,9")` | at a Card Shrine: three binder cards of one skill for a reroll |
| `rebuildCards("12,15")` | at a sitting: takes cards of one deck out, paying a free way if one covers them, else the Draught of Forgetting (once a sitting), the fees and the scars. `unslotCards` does the same when a sitting is open |
| `slotCard(id, slot, deck)` | a Mythic into any deck (`"mettle:blades"`); two slotted at most, never two in one deck |
| `fuseCards("3,8,9")` | at a Card Shrine (or a trainer of the skill): three binder copies of one perk and rarity into one of the next rarity, for its rebuild fee |
| `tradeRequest(name)` · `tradeAccept()` | ask a player within 8 blocks to trade; accept the waiting request |
| `tradeOfferCard(id)` · `tradeTakeBackCard(id)` | a binder card (attuned, not slotted) into or out of the trade; any change unlocks both |
| `tradeOfferItem(slot)` · `tradeTakeBackItem(index)` | the stack in an inventory slot (-1: the main hand) into escrow; back out by its index in `trade.me.items` |
| `tradeCoin(bits)` · `tradeLock()` · `tradeConfirm()` · `tradeCancel()` | Hearth coin; lock your side; with both locked start the 3 s countdown; call it off (everything goes back) |
| `closeSitting()` | ends the sitting (walking 8 blocks away does too) |
| `cardSound(cue, arg)` | the card sounds timed to the overlay's animations (SOUNDS.md §5); timing in `draft.sound` |
| `setCardSounds(on)` · `setCardSoundsVolume(percent)` | the client option for card sounds (SOUNDS.md §8): on or off, and 0-100 % (default on, 100); saved in `mettle-client.properties` and shown as `draft.sound.enabled` / `draft.sound.volume` |

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

**`MettleState.revision()`** (`public static long`, client thread): a cheap change counter. It goes up whenever anything
`MettleState.build` outputs may have changed: a new synced character, cards or load (each sync is a new value), the
inventory's change count, worn gear, carry capacity, any visible player's public Mythics, the server's card config
(`mettle:cards/config`), a client option, and every Mettle payload (feedback, moments, trade view, Hall of Firsts,
sitting, reveal, picker results). Where the output shows time it also goes up with it: once a second while a card
attunes, four times a second while feedback, moments or a Mythic reveal show (their `ageMs`). A host keeps the last
value and calls `build` only when it changed; it never goes down within a session. The Hub reaches it by reflection
like `build`.

`cards.binder.count` counts the cards that fill the binder against `cards.binder.max`: Mythics always fit, so they are
left out (they are still listed in `cards.binder.cards`). `cards.binder.max` and the other card numbers are the
server's (`config/mettle/cards.json`, sent on join).

The VOID Hub (`hub/`, branch `claude/hub-mettle`) is the first host: its window gains a **Gear** page (pack pockets,
belt and quiver, load) and a **Classes** page (overview, skill trees) over the player's own `InventoryMenu`. It reaches
this API by reflection (`MettleState.build`, `MettleActions.pickCard` / `reroll` / `spendAttribute` (and the old `learnPerk`),
`MettleScreens.setCharacterOpener`), so neither mod needs the other to build. A host like this draws Mettle's
screens but not the HUD, so it does **not** register with `OverlayBridge` (a listener there takes over the picker,
chips and the dev burden line); it pulls `MettleState.build` while its pages are open.

`MettleScreens.setCharacterOpener(BooleanSupplier)`: K opens the host's character page instead of the native
`CharacterScreen` (the supplier returns false when it cannot, and the native screen opens). A character without a
background still gets Mettle's picker.
