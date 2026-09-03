# `@void/protocol`

The TypeScript face of `pvp/schema/*.json`.

Nothing here talks to the network or to the game. It is **types**, one **reference
shim**, one **fake bridge** and the **mod registry** — shared by `apps/desktop`,
`packages/ingame` and `packages/ui` so all three compile against the same contract.

```ts
import {
  createFakeVoid,   // an in-memory window.void for browser development
  installVoidShim,  // the reference implementation of void-shim.js
  MOD_REGISTRY,     // the closed registry of the 12 mods
  hypixelReady,     // the rule behind the HYPIXEL-READY badge
  type Loadout,     // …and every generated type
  type VoidBridge,
} from '@void/protocol';
```

---

## Generated types

`pnpm gen` compiles `schema/{mods,loadout,protocol,bridge}.json` with
`json-schema-to-typescript` into `src/generated/`. The output is **committed**, so
consumers never run the generator.

The four documents `$ref` each other by absolute id
(`https://schema.void.dev/pvp/<file>.json`). Those URLs are identifiers, not locations —
nothing is ever fetched. `scripts/gen.mjs` bundles all four into one self-contained
schema before compiling, so a definition shared between documents (`keybind`,
`hud_item`, `loadout`) yields **exactly one** TypeScript type instead of one copy per
document.

| File | What it holds |
|---|---|
| `src/generated/schema.ts` | Every type: `ModId`, `Loadout`, `HUDItem`, `TickPayload`, `ProtocolMessage`, `GlobalSettings`, … |
| `src/generated/registry.ts` | `MOD_REGISTRY_DOCUMENT` — `mods.json` `examples[0]`, the registry VOID ships |
| `src/generated/examples.ts` | Every `examples` entry of every document, typed |

Because `examples.ts` annotates each array with its generated type, a drift between the
schema and the generated types fails `pnpm typecheck` before any test runs.
`test/schema-examples.test.ts` then validates every example against its own schema with
Ajv, and asserts the cross-checks JSON Schema cannot state — that the registry contains
exactly the 12 ids in the `mod_id` enum, that `hud_mod_id`/`gameplay_mod_id` agree with
each entry's `kind`, and that every `defaults` object satisfies its own settings
sub-schema.

---

## `window.void`

`src/void-bridge.ts` is the typed bridge surface, exactly matching `bridge.json` (§6.5):

```ts
interface VoidBridge {
  on<E>(event: E, cb): () => void;   // returns an unsubscribe
  off<E>(event: E, cb): void;
  __emit(envelope: VoidEnvelope | string): void;
  __hasFocus(): boolean;

  setGameplay(id: GameplayModId, on: boolean): boolean;
  setHud(id: HUDModId, placement: HudPlacement): HUDItem;
  setModSetting(id: ModId, key: string, value: ModSettingValue): ModSettingValue;
  switchLoadout(id: LoadoutId): boolean;
  closeMenu(): null;
  openKeybindCapture(modId: ModId): Promise<Keybind | null>;
}
```

Five push channels — `keys`, `tick`, `server`, `loadout`, `menu` — and six calls. The
bridge is in-process (Ultralight lives inside the JVM), so calls are **synchronous and
authoritative**: they return the state actually applied. Bind your control to the return
value, never to what you sent.

### How it is assembled at runtime

1. The Java host installs one function: `window.__void_native(json: string): string`.
   It takes a JSON call envelope `{"c": …, "params": [...]}` and returns a JSON
   call-result envelope `{"c": …, "returns": …}`.
2. `void-shim.js` — written by the **mod** owner, shipped inside the JAR — runs before
   the app bundle and builds `window.void` on top of it.
3. Java pushes events by calling `window.void.__emit(envelope)`, as an object or as its
   JSON encoding.

**`installVoidShim()` is the TypeScript reference implementation of step 2.** The
browser harness and the JAR therefore run the same shim semantics; if the two ever
disagree, `src/void-bridge.ts` is the specification that settles it.

```ts
const bridge = installVoidShim();           // uses window.__void_native, installs window.void
const bridge = installVoidShim({ native, target: null }); // or drive it yourself, in a test
```

`__hasFocus()` reports whether the web layer owns input. It tracks the `menu` channel,
because in HUD mode Ultralight receives no input events at all (§6.3).

**`openKeybindCapture` is the one asynchronous call.** Java takes over key input until
the next key press, so `__void_native` acknowledges without a `returns` field and Java
later delivers the resolution through the same `__emit` channel as a call-result
envelope: `__emit({ c: 'openKeybindCapture', returns: 'V' })`, or `returns: null` when
the player pressed Escape. The shim keeps pending resolvers in FIFO order. The promise
never rejects.

---

## `createFakeVoid()`

There are no devtools in game (§9), so the in-game bundle must also run in a normal
browser against a fake bridge. This is that fake.

```ts
const fake = createFakeVoid({ seed: 7 });
fake.install();          // window.void = fake
fake.start();            // 20 Hz pushes on a real timer
```

It emits a realistic `tick` at 20 Hz — fps wandering in 130–160, ping in 40–50,
coordinates drifting as the player walks, armour durability ticking down while LMB is
held, and two potion effects (`Speed II` and `Strength`) counting down — plus random,
**edge-triggered** `keys`: a push happens only when a key actually changes. It answers
all six calls with the same clamping Java applies (`setHud` snaps to the 4px grid and
clamps scale to 0.25–4; `setModSetting` clamps to each setting's range in `mods.json`),
holds three loadouts (`loadout.json`'s two examples plus a UHC card matching the
Loadouts frame), and toggles `menu` on Right Shift keydown.

It is **deterministic**. Give it a `seed` and drive it with `advance()` instead of
`start()`, and every number it produces is reproducible:

```ts
const fake = createFakeVoid({ seed: 7, attachKeyboard: false });
fake.on('tick', (t) => console.log(t.fps));
fake.emitInitialState();  // loadout, then server, then menu — the hello order
fake.advance(1000);       // exactly 20 ticks
```

Beyond `VoidBridge` it adds `start` / `stop` / `advance` / `tickOnce`,
`emitInitialState`, `setMenuOpen` / `isMenuOpen`, `setKeys` / `getKeys`, `setServer`,
`getLoadout` / `getLoadouts`, `resolveKeybindCapture` / `isCapturingKeybind`,
`getCalls` / `clearCalls` (the `?debug` recording), `attachKeyboard`, `install` and
`destroy`.

---

## The mod registry

`src/mods.ts` exposes the registry as a typed constant plus the predicates every surface
needs:

| Export | What it answers |
|---|---|
| `MOD_REGISTRY`, `MOD_REGISTRY_VERSION` | the 12 rows, keyed by id |
| `MOD_IDS`, `HUD_MOD_IDS`, `GAMEPLAY_MOD_IDS` | deterministic iteration order |
| `isModId`, `isHudMod`, `isGameplayMod` | type guards — `isGameplayMod` gates `setGameplay`, `isHudMod` gates `setHud` |
| `getModEntry`, `getModDefaults`, `getModLabel` | one row, its factory settings, its display name |
| `resolveModSettings(loadout, id)` | the loadout's own state merged over the defaults |
| `isModEnabled`, `enabledMods`, `enabledModCount` | the `24 mods on` line |
| `hypixelReady(loadout)`, `greyMods(loadout)` | the **HYPIXEL-READY** badge |

`hypixelReady` implements §11 literally: the badge shows only when *every enabled mod*
is classified `safe`. A `grey` mod sitting in the loadout but switched off — Fullbright
or Hitboxes — does not disqualify it, and a mod the loadout omits entirely falls back to
its registry default.

---

## Scripts

| Script | What it does |
|---|---|
| `pnpm gen` | Regenerate `src/generated/` from `pvp/schema/` |
| `pnpm build` | `tsc` → `dist/`, ES2022, ESM, with declarations |
| `pnpm typecheck` | `tsc --noEmit` across `src`, `test`, `scripts` |
| `pnpm test` | Vitest — schema examples, the registry cross-checks, the fake bridge and the shim |

Run `pnpm gen` whenever `schema/` changes (it is owned by **core**; a change there is a
contract change, and everyone recompiles against it).
