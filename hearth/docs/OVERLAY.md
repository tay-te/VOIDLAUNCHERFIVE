# VOID Hearth — the overlay contract

**Decision (user, 2026-10-19, docs/HUB_UI.md):** all of Hearth's player-facing UI moves to the VOID Hub's Ultralight
HTML layer, the way Mettle's Classes and Gear pages work (`mettle/docs/OVERLAY.md` on branch `mettle`). Designs: Figma
file `3c0Wgc4ZKFSpm98fWJoARS`, page 295:2, section 651:37945 (notes frame 665:43614 lists what each screen needs).
Hearth's own Java screens stay as the fallback when the Hub is not installed or an opener declines.

This is what Hearth gives a host. Everything lives in `dev.voidmc.hearth.client.overlay` (client source set), is static,
and is reached by reflection, so neither mod needs the other to build:

| Class | What a host calls |
|---|---|
| `HearthState` | `JsonObject build()`, `String buildJson()`, `long revision()`, `int VERSION` (1) |
| `HearthActions` | the calls below; every one returns `boolean` (true: a packet went out) |
| `HearthScreens` | `set<X>Opener(BooleanSupplier)`, `setOpener(String, BooleanSupplier)`, `hosted()`, `nativeScreen()`, `ended(String)` |
| `HearthClientOptions` | `conversationStyle()`, `setConversationStyle(String)` (also via `HearthActions`) |

The client decides nothing. The state is what the server last sent (conversation, board, confrontation, minigames,
reputation page, law HUD), the open Hearth menu's view (market, bank, dice) and the synced quests. Every action only
sends a packet Hearth's own screens send; `ActionRules` drops a call that cannot be right for what is on screen (a locked
option, a row not listed, a negative offer), and **the server re-validates everything** (ownership, distance, coin,
standing, whose turn).

Ultralight's limits apply (`pvp/design/quiet-cell-system.md` §9). Per docs/HUB_UI.md, minigames' moving parts (the pick
and pins, the pickpocket timer, the tumbling dice) stay native Java at full frame rate; the page draws static chrome and
updates only on discrete state changes. No CSS animation.

---

## Hosts

**`HearthState.revision()`** (`public static long`, client thread): a cheap change counter. A host keeps the last value,
checks it once a tick, and calls `build()` only when it changed. It never goes down within a session. It goes up with
every Hearth payload (talk, talk detail, standing line, board, confrontation and its detail, lockpick start/end,
pockets, reputation page, law hint/status/stealth, town arrival), any change of the open menu or its view, the synced
quests, the inventory's change count (the purse), the client options and the hosted screen, the tracked wanted poster;
while a quest or a wanted poster is tracked also with the player's block, facing (5° steps) and the in-game hour (compass and time left); once a second while a jail
sentence counts down.

**`HearthState.build()`**: the whole tree below. Text is translated; ids are namespaced strings; every amount of coin
is a `Coins` object (the user's decision: crowns, marks and bits with coin icons wherever a price shows).

---

## The state (`HearthState.build()`)

```ts
type HearthState = {
  version: 1;
  phase: "offline" | "online";              // offline: every section below is null
  hosted: null | ScreenName;                // the event screen the host took last (it is showing it now)
  settings: { conversationStyle: "card" | "cinematic" };   // A (portrait card, default) or B (cinematic bar), per player
  player: {
    purse: Coins;                            // coin carried (pouches included)
    town: null | { id: number; name: string; standing: number; title: Title; titleName: string; fame: number; renown: number };
  };
  talk: null | Talk;                         // a conversation is open (by Hearth's screen or the host's)
  journal: Journal;                          // always (the player's synced quests)
  questHud: null | QuestHud;                 // a quest is tracked
  board: null | Board;                       // a notice board is open
  market: null | Market;                     // the player's open menu is a market or shop counter
  bank: null | Bank;                         // ... a bank or town hall counter
  dice: null | Dice;                         // ... a dice table
  confront: null | Confront;                 // a guard or witness has stopped the player
  lockpick: null | Lockpick;                 // a lock is being picked
  pickpocket: null | Pickpocket;             // pockets are being picked
  reputation: null | Reputation;             // the reputation page arrived (K, /hearth standing)
  law: Law;                                  // the law HUD's facts (the HUD itself stays native)
  wantedTrack: null | WantedTrack;           // a wanted poster is tracked (trackPoster); 2026-10-19
};
type ScreenName = "talk" | "market" | "bank" | "board" | "journal" | "confront" | "lockpick" | "pickpocket" | "dice" | "reputation";
type Coins = { total: number /* bits, may be negative */; crowns: number; marks: number; bits: number; text: string /* "4 crowns, 5 marks, 4 bits" */ };
type Title = "outlaw" | "distrusted" | "stranger" | "known" | "friend" | "burgher" | "hero";
type Trait = { id: string /* "generous" */; name: string /* "Generous" */ };
type Pos = { x: number; y: number; z: number };
```

Portraits: `portrait` is a 16×16 PNG as `data:image/png;base64,...` (a bust cut from the resident's own composed skin,
`npc/look/Portrait`; scale by whole numbers with `image-rendering: pixelated`), or null when the folk is not loaded on
the client. `portraitKey` (the look's key, 16 hex digits, "" for none) is stable per face, so a host can cache images
by it and skip resending. A resident whose folk is not loaded on the client (a shopkeeper away from the counter, a
dice partner out of view) still has a portrait: the server sends their look (`FolkLook.key()`) and the client composes the
face from it, `portraitKey` then being that key. Every resident-backed screen carries one: `talk.resident`,
`market.merchant`, `confront`, `confront.folk`, `dice.opponent` and `pickpocket` (2026-10-19).

### Talk (`talk`) — Figma 653:37976 (A, default) and 655:38485 (B)

```ts
type Talk = {
  entity: number;                            // the folk's entity id
  resident: {
    name: string; job: string; jobKey: string;            // "Innkeeper", "hearth.npc.job.innkeeper"
    town: string;
    place: string; placeKey: string;         // where they work: "Tavern", "commerce/tavern" ("" when they have no workplace)
    traits: Trait[];
    mood: number; moodWord: "bitter" | "glum" | "even" | "content" | "glad";   // −100…100
    opinion: number; opinionWord: "hates" | "dislikes" | "neutral" | "likes" | "loves";   // of the player, −100…100
    memory: null | { kind: string /* deed kind */; weight: number /* < 0: a bad one */; text: string };
    portrait: string | null; portraitKey: string;
  };
  standing: { town: string; value: number /* −100…100 */; title: Title | ""; titleName: string;
              next: null | { title: Title; titleName: string; at: number } };   // the next title up and the standing it takes
  time: { minute: number /* 0–1439, −1 unknown */; part: "dawn" | "morning" | "midday" | "afternoon" | "evening" | "night" | "" };
  speech: number;                            // the player's Mettle Speech level (0 without Mettle)
  line: string;                              // what they say now (reveal it as you like; Hearth's screen types it out)
  lastCheck: string;                         // the last speech check's outcome line ("Persuade 62% passed — ..."), or ""
  options: {
    key: number;                             // 1–9 in order, 0 for goodbye (kind "leave"), −1 past the ninth
    id: string;                              // what choose() takes
    label: string; hint: string; tooltip: string;          // hint: the provider's own chip text ("Persuade · 62 %"), may be ""
    kind: string;                            // talk | trade | bank | quest | persuade | intimidate | bribe | flatter | train |
                                             // service | law | fence | dice | repair | leave (others may appear: draw a default)
    odds: number;                            // a check's chance 0–100, −1 when not a check
    cost: Coins | null;                      // what it costs, null when free or unknown
    skill: null | { id: string /* "mettle:speech" */; name: string /* "Speech" */; level: number };   // odds chips: "Persuade · Speech · 62 %"
    locked: boolean;
    lockedReason: string | null;             // locked: the tooltip, else the hint ("Needs Friend of Bradfield · you are Known")
  }[];
};
```

`kind`, `odds`, `cost` and `skill` come from the server's `talk detail` payload. Hearth's speech checks, fines, bribes, services and repairs describe their
options (`DialogueProvider.describe`, an API addition); others are classified by id and order band (`npc/talk/OptionKinds`:
`mettle:train/<skill>` is `train` with skill `mettle:<skill>`, 300–399 is `train`, 200–299 `quest`, 10 000 `leave`).
Mettle's trainer options are inline training: when Mettle does not describe them, the server reads the fee back from
the option's label (or hint), which Mettle writes with Hearth's `EconomyService.format`, so `cost` is the lesson's fee
(2026-10-19); the level is the server's.

Options that ask the same thing from different providers are sent once, the better kept (`npc/talk/OptionIntents`,
2026-10-19): "What's new in town?" gives way to town life's "Anything on in town?" (it has the coming events and the
market day), the law's fence line to the fence's shop, an open option beats a locked one, and two providers offering the
very same words collapse. One provider's own options are never folded. A server without the detail payload leaves every option `talk` with `odds` −1.

### Journal (`journal`), quest HUD (`questHud`) and notice board (`board`)

```ts
type Quest = {
  id: string /* uuid */; type: string /* "supply", "bounty"... */; template: string;
  title: string; summary: string; story: string /* the offer text */; progressText: string; completeText: string;
  giver: { name: string; job: string };
  town: string; settlementId: number;
  state: "offered" | "active" | "ready" | "completed" | "failed";
  minStanding: number;
  objectives: { kind: string; text: string; have: number; need: number; done: boolean;
                current: boolean /* can be worked on now (sequential quests: only the first open) */;
                optional: false; carried: number /* deliveries: how many the player carries */ }[];
  deadline: boolean; ticksLeft: number /* −1 none */; timeLeft: string /* "1 day, 4 hours" or "" */;
  reward: { coins: Coins; standing: number; items: { item: string; count: number }[]; text: string };
  tracked: boolean;
  waypoint: Pos | null;                      // where it wants the player now (the open objective's place, or the hand-in)
  distance: number;                          // blocks (horizontal) to the waypoint, −1 none
};
type Journal = {
  tracked: string | null;
  active: Quest[];                           // the tracked one first
  finished: { completed: number; failed: number; total: number;
              recent: { title: string; type: string; town: string; giver: string; outcome: string; day: number; reward: Coins }[] };
};
type QuestHud = {                            // the HUD stays Hearth-native (it reserves its rect for Atlas); this is for pages
  id: string; title: string; town: string; handIn: boolean;
  objectives: string[];                      // lines as the HUD shows them ("Bring 24 × Spruce Log (12/24)")
  step: string; progress: number; count: number;
  target: Pos | null; distance: number;
  bearing: number | null;                    // degrees from where the player looks, clockwise, −180…180
  timeLeft: string;
};
type Board = {
  settlementId: number; pos: Pos; town: string; tier: string; reputation: number; title: string /* "Friend of the town" */;
  posts: { state: "open" | "locked" | "taken"; lock: string /* why locked, "" */; quest: Quest }[];
  handIns: string[];                         // quest ids this board takes in
  wanted: { name: string; owed: string; outlaw: boolean; crime: string; crimeKey: string;
            player: string /* the quarry's uuid: what trackPoster takes, "" from an older server */; tracked: boolean }[];
  counts: { open: number; locked: number; wanted: number; handIns: number };
};
```

```ts
type WantedTrack = {                         // the tracked wanted poster, followed like a quest (2026-10-19)
  player: string; name: string; owed: Coins; outlaw: boolean; crime: string; crimeKey: string;
  settlementId: number; town: string;        // whose posters it came from
  online: boolean;                           // the quarry is on the server
  near: Pos | null;                          // where they are, to the nearest 32 blocks, only in the player's dimension
  distance: number;                          // blocks (horizontal), -1 while not placed
  bearing: number | null;                    // degrees from where the player looks, clockwise, -180...180
};
```

Tracking a poster lets go of the tracked quest and tracking a quest lets go of the poster (one thing tracked at a time);
the same poster again untracks it. The server checks the warrant once a second and drops the track when it is paid or
gone (`wantedTrack` goes null). Not saved: a poster is tracked for the session. With Atlas, the tracked poster is the
tracked marker (`hearth:wanted:<uuid>`, on the quests layer), ahead of the quest. The HUD tracker stays the quest's.

Not yet sent by the server: per-objective `optional` (always false).

### Market (`market`) — Figma 3_market

```ts
type Market = {
  containerId: number; town: string; culture: string; shop: boolean /* a shop with its own till, else the town's market */;
  reputation: number; purse: Coins; till: Coins;
  note: string;                              // the merchant's word, bare: no quote marks, the page adds its own ("Iron's dear this month.")
  status: string;                            // the last trade's result, or ""
  sour: boolean;                             // soured by haggling: dearer, no haggling for a day
  merchant: null | { name: string; job: string; jobKey: string; entity: number; traits: Trait[]; mood: number; moodWord: string;
                     opinion: number; opinionWord: string; sour: boolean; portrait: string | null; portraitKey: string };   // null at the town's market
  sells: Row[];                              // the player buys
  buys: Row[];                               // the player sells (ids from 10 000)
  haggle: null | {
    row: number; item: string; name: string; count: number; playerBuys: boolean;
    list: Coins;                             // the posted price of the whole deal
    word: Coins;                             // the merchant's last word
    offer: Coins;                            // the player's last offer
    patience: number;                        // 0–100
    offersLeft: number;                      // offers before they have had enough, whatever their patience
    line: string;
    band: null | { low: Coins; high: Coins };   // where the merchant's limit lies, as the player's Speech reads it; null = hidden
    speech: number; bandSpeech: 5; exactSpeech: 15;   // the band appears at Speech 5 (30 % of the price wide) and closes on the limit at 15
    takes: { bits: number; odds: number /* 0-100 */ }[];   // "he takes it" odds for offers from the far end of his possible limit
                                             // up to his word, ascending by bits (up to 17 points): read the point at or below the offer
    takesLast: number;                       // the odds the player's last offer had, -1 before any offer
  };
};
type Row = { id: number; item: string; name: string; lot: number /* items per lot */; price: Coins /* a lot */;
             available: number /* lots that can trade now ("to spare") */; owned: number /* selling: carried */; goods: string;
             flags: { needed; townCannotPay; noRoom; cannotAfford; refused; stolen; fenced; sour: boolean };
             canTrade: boolean; haggling: boolean };
```

The limit band is the user's decision: hidden by default, shown with higher Mettle Speech (KCD-style). It is fixed per
deal (looking again tells nothing new). "58 % he takes it" (2026-10-19): haggling itself is deterministic
(`economy/haggle/Haggle`), so the doubt is only where his limit lies, taken as even across the band the player reads, or
across every limit a merchant could have while the band is hidden (`Haggle.takesOdds`: an offer as good as his word is
100 %; within his limit and a fifth of his room from his word, he closes). Better Speech, sharper odds; at Speech 15
they are 0 or 100. `merchant.portrait` is there even when the keeper's folk is not loaded (`Merchant.look`).

### Bank (`bank`)

```ts
type Bank = {
  containerId: number; town: string; tab: "bank" | "property" | "works" | "contracts" /* the tab the server wants first */;
  hasBank: boolean /* deposits and the changer; false at a town hall */; hasWorks: boolean;
  deposit: Coins; carried: Coins; treasury: Coins;
  emeralds: { carried: number; reserve: number; buy: Coins /* the changer pays */; sell: Coins /* the changer asks */ };
  reputation: number; status: string;
  owned: Plot[]; forSale: Plot[];
  projects: { plot: number; archetype: string; name: string; rising: boolean; stage: number; stages: number;
              cost: Coins; given: Coins; yours: Coins; fund: Coins; stalled: boolean }[];
  contracts: { id: number; goods: string; goodsName: string; units: number; delivered: number; pays: Coins; daysLeft: number;
               taken: boolean; carrying: boolean }[];
  ledger: LedgerLine[];                      // the player's last 24 bank dealings, newest first, at every bank alike (2026-10-19)
};
type LedgerLine = {
  day: number;                               // the game day
  kind: "deposit" | "withdraw" | "sell_emeralds" | "buy_emeralds" | "buy_plot" | "hire" | "fund" | "deliver";
  purse: Coins;                              // into (+) or out of (-) the coin carried
  deposit: Coins;                            // onto (+) or off (-) the deposit
  town: string;                              // where
  subject: string; subjectName: string;      // the building (buy_plot, hire, fund: archetype path / its name), the goods (deliver),
                                             // the emeralds' count (sell/buy_emeralds), or ""
  text: string;                              // the line in words ("Deposited 2 marks", "Gave 5 marks toward the Chapel")
};
type Plot = { plot: number; archetype: string; name: string; built: boolean; sizeX: number; sizeZ: number; storeys: number;
              price: Coins /* yours + business: the hiring cost */; minStanding: number; yours: boolean; lowStanding: boolean;
              cannotAfford: boolean; business: boolean; canHire: boolean; income: Coins; workers: number };
```

Only actions that moved coin make a ledger line (a refused deposit does not). Kept with the player
(`economy/bank/BankLedger`, an attachment that survives death).

### Guard confrontation (`confront`) — Figma 4_guard_confrontation

```ts
type Confront = {
  entity: number; kind: "arrest" | "search" | "witness" | "";   // "" when the detail payload has not arrived
  name: string; line: string; bounty: Coins;
  folk: { name: string; job: string; jobKey: string; traits: Trait[]; opinion: number; opinionWord: string; portrait; portraitKey };
  charge: { offence: string; offenceKey: string /* "hearth.offence.theft" */; offences: number; witnesses: string; town: string };
  standing: { town: string; value: number; title: Title | ""; titleName: string };
  choices: { key: number /* 1… */; id: string; label: string; enabled: boolean;
             kind: string;                   // pay | cell | submit | pillory | persuade | bribe | threaten | run | fight | allow | refuse | leave
             odds: number; cost: Coins | null; minutes: number /* the cell or the pillory, −1 */ }[];
  portrait: string | null; portraitKey: string;
};
```

Esc does nothing here (he is waiting): answer or run. Not sent: where and when it was witnessed, the standing change if
fined, the stolen item count.

### Minigames (`lockpick`, `pickpocket`, `dice`) — discrete state only

```ts
type Lockpick = { session: number; what: string /* "Edda Miller's chest" */; tier: number /* 1–5 */; tierKey: string;
                  skill: number; picks: number; state: "picking" | "opened" | "spotted" | "noPicks" | "gone" };
type Pickpocket = { session: number; victim: string; asleep: boolean;
                    items: { slot: number; item: string; name: string; count: number; empty: boolean /* taken */ }[];
                    taken: number;
                    entity: number /* the victim's folk, -1 */; portrait: string | null; portraitKey: string;
                    awareness: number; awarenessRate: number;   // 0–1 and per tick: animate natively between updates
                    lifting: number /* slot, −1 */; liftProgress: number; liftRate: number;
                    state: "open" | "caught" | "done" };
type Dice = { containerId: number; phase: "stake" | "player_rolled" | "player_bust" | "folk_turn" | "over";
              opponent: { name: string; temper: string; entity: number /* -1 */; portrait: string | null; portraitKey: string };
              stake: Coins; target: number;
              scores: { player: number; folk: number }; turnPoints: number; turn: number;
              dice: { index: number; face: number /* 1–6 */; kept: boolean; selected: boolean; scoring: boolean; rolled: boolean }[];
              selectionScore: number; rollSerial: number /* changes with every roll: tumble the rolled dice */;
              stakes: Coins[]; line: string; status: string; winner: number; cheatCaught: boolean;
              loaded: "none" | "carried" | "inPlay"; purse: Coins; canRoll: boolean; canBank: boolean };
```

### Reputation (`reputation`) and the law HUD (`law`)

```ts
type Reputation = { fame: number; renown: number; fameWord: string; renownWord: string;
                    charisma: number; charismaWord: string; menace: number; menaceWord: string;
                    looks: string[] /* what folk notice */; company: string; members: string[]; companies: boolean;
                    towns: { id: number; name: string; culture: string; tier: string; value: number; title: Title; titleName: string;
                             owns: boolean; distance: number /* −1 other dimension */; here: boolean }[] };
type Law = {
  hint: null | { owner: string; animal: boolean; lockTier: number; locked: boolean; hasKey: boolean; yours: boolean };   // the crosshair's owner
  status: { town: string; bounty: Coins; wanted: boolean; outlaw: boolean; suspicion: number;
            jailTicks: number; jailSeconds: number /* counted down since the last update */; pillory: boolean; wantedElsewhere: boolean };
  stealth: { active: boolean; visibility: number /* 0 hidden … 1 seen */; noticed: boolean };
};
```

Not sent: per-town folk opinions and the deed log on the reputation page.

---

## Actions (`HearthActions`)

All static, client thread, plain arguments; each returns whether a packet went out.

| Call | Effect |
|---|---|
| `choose(optionId)` | picks a conversation option (`talk.options[].id`); locked or unknown ids send nothing |
| `train(skill)` | pays and trains inline: picks the trainer's lesson option for `skill` (`"mettle:speech"` or `"speech"`); the server charges the fee |
| `close(screen)` | the host closed an event screen (`ScreenName`): ends the conversation, walks away from a confrontation, leaves the lock or the pockets, closes the Hearth menu (market, bank, dice), forgets the board. Never closes a screen itself: call it from the host screen's close |
| `trade(row, bulk)` | buys or sells one lot of a market row (`bulk`: a stack, or all you carry) |
| `haggle(row, bulk)` · `haggleOffer(bits)` · `haggleAccept()` · `haggleCancel()` | starts haggling over a row; names a price for the whole deal; takes the merchant's word; walks away |
| `deposit(bits)` · `withdraw(bits)` | the bank (`-1`: all) |
| `sellEmeralds(n)` · `buyEmeralds(n)` | the money changer |
| `buyPlot(plot)` · `hire(plot)` · `fund(plot, bits)` | property, your business, public works |
| `acceptContract(id)` · `deliver(id)` | supply contracts |
| `accept(questId)` · `handIn(questId)` · `refreshBoard()` | the open notice board |
| `track(questId)` · `abandon(questId)` | the journal |
| `trackPoster(player)` · `untrackPoster()` | tracks the quarry of a wanted poster on the open board (`board.wanted[].player`; the same one again untracks), or stops; `state.wantedTrack` follows (2026-10-19) |
| `confront(choiceId)` | answers a guard or witness (`""` walks away) |
| `lift(slot)` · `stopLifting()` · `slipAway()` | pickpocketing |
| `stopPicking()` | gives up on a lock (the pick itself is played natively, see `nativeScreen`) |
| `diceStake(index)` · `diceToggle(die)` · `diceRoll()` · `diceBank()` · `dicePass()` · `diceLoaded(play)` · `diceAgain()` | the dice table |
| `requestReputation()` | asks for the reputation page (it opens through the opener) |
| `setConversationStyle(style)` | `"card"` or `"cinematic"`; saved in `config/hearth-client.properties` |

Answers arrive as state (a tracked poster as `wantedTrack` and `board.wanted[].tracked`): a new `talk.line` and `talk.lastCheck`, `market.status` and a new `market.haggle`, `bank.status`,
board and journal changes, and so on. There are no separate result events; watch `revision()`.

---

## Openers (`HearthScreens`)

`set<X>Opener(BooleanSupplier)`: `setTalkOpener`, `setMarketOpener`, `setBankOpener`, `setNoticeBoardOpener`,
`setJournalOpener`, `setConfrontOpener`, `setLockpickOpener`, `setPickpocketOpener`, `setDiceOpener`,
`setReputationOpener` (or `setOpener(name, supplier)`; null removes one). When the event comes, Hearth calls the opener;
it opens the host's screen and returns true, or returns false (Hearth's own Java screen opens). An opener that throws
counts as false. Nothing registered: Hearth's screens, exactly as before.

When each opener runs:

| Screen | Event | Note |
|---|---|---|
| `talk` | the server opens a conversation (right-click a folk) | later lines update `state.talk`; when it goes null (the server ended it, the folk left earshot), close |
| `board` | a notice board's view arrives for a board not already hosted | `state.board`; `close("board")` on close |
| `journal` | J, or the journal item | `state.journal` is always there |
| `reputation` | the reputation page arrives (K, `/hearth standing`, `requestReputation()`) | |
| `confront` | a guard or witness stops the player | `state.confront` null: the server closed it; Esc should not close it |
| `lockpick` | a lock is started | state `picking` until the server's end |
| `pickpocket` | pockets open | |
| `market`, `bank`, `dice` | the Hearth menu opens | called from the Java screen's first `init()`; open the host screen from the opener (a nested `setScreen` is fine). `player.containerMenu` stays the Hearth menu under the host's screen, so the menu's views keep arriving; `close(...)` closes it |

While a host shows an event, `HearthScreens.hosted()` names it and `HearthScreens.nativeScreen()` is the Java screen
Hearth made for it (never set as Minecraft's screen). A host may `init(w, h)` it and forward `extractRenderState` and
input to it inside a rect to draw the native parts (the lockpick's pins and pick, the pickpocket timer, the dice), as
docs/HUB_UI.md asks; such a screen may close the current screen itself when its attempt ends. A host that closes calls
`HearthActions.close(name)`, which also clears `hosted()`. A hosted conversation is ended by Hearth when the folk goes
out of earshot, as the native screen does.

## The server side (for reference)

Additions only; nothing a partner mod copies changed signature.

- `api/DialogueProvider`: `record Info(kind, odds, cost, skill)`, `static describe(optionId, Info)` (a provider calls it
  next to `out.add` in `collect`), `Registry.collectInfo(map)` (the conversation's own use). A partner mod (Mettle's
  trainers) can describe its options too: `cost` then shows on the chip.
- `npc/talk/TalkPayloads.Detail` (`hearth:npc/conversation_detail`), sent after every `Show`: the resident card and each
  option's facts. `npc/talk/OptionKinds` classifies the rest.
- `law/net/LawPayloads.ConfrontDetail` (`hearth:law/confront_detail`), sent after every open `Confront`.
- `economy/market/MarketView`: `merchant` (the keeper's card); `HaggleView`: `offersLeft`, `bandLow`/`bandHigh` (−1 hidden),
  `speech` (`Haggle.band`, `Haggle.BAND_SPEECH` 5, `Haggle.EXACT_SPEECH` 15). The old constructors remain.
- `standing/speech/TalkCard`, `standing/logic/Titles.next`: the standing facts for the cards.
- `npc/look/Portrait`: the 16×16 bust and its PNG encoding; the client caches per look (`overlay/Portraits`, 256 faces).

2026-10-19 (the Hub's gaps), additions only:
- `npc/look/FolkLook.fromKey`, `npc/look/ResidentLooks.key`: a resident's look from the resident alone; sent as
  `MarketView.Merchant.look`, `DiceView.folkEntity`/`folkLook`, `LawPayloads.Pocket.entity`/`look` (old constructors remain).
- `economy/haggle/Haggle.takesOdds`: the "he takes it" odds.
- `economy/bank/BankLedger` (`hearth:economy/bank_ledger` player attachment) and `BankView.ledger`.
- `economy/coin/CoinFormat.read` and `npc/talk/OptionKinds.withCost`: a lesson's fee read back from a partner's label.
- `npc/talk/OptionIntents`: one option per intent across providers.
- `quest/net/QuestPayloads.PosterAction` (`hearth:quest/poster_action`, C2S) and `PosterTrack` (`hearth:quest/poster_track`,
  S2C), `Board.Poster.player`; `quest/service/PosterTracks` (once a second, only what changed).
- Hearth's own fallback screens read mouse buttons through `InputConstants.MOUSE_BUTTON_LEFT`/`_RIGHT` (26.x sends 1
  for left and 3 for right, not GLFW's 0 and 1): the lockpick hold-to-turn, the dice, the market's right-click haggle, the
  pickpocket's right-click stop, the bank, the board and the journal. A host forwarding a click passes the event as it came;
  the extra button-0 copy the Hub sends the lockpick is now ignored.
