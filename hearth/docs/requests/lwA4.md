# Lane A4 (visible work, LIVING_WORLD_2.md §6): what landed, and what it asks of others

From lane A4, 2026-10-20 (updated 2026-10-21), branch `hearth-lwA4`.

## No new screens

Everything a player sees of this lane is in the world (flocks, droves, smoke, boats, wares on counters, the Great
Mine's pithead) or said aloud (barks in chat, `hearth_dialogue/barks/work.json`). No Figma screen is needed.

## Waiting on other lanes (the design's last two steps)

| From | What | Where it plugs in | Status |
|---|---|---|---|
| **A2** daily rhythm | the stall callback in `social/life/Dressings`: when a market day sets a stall up (`setUpMarket`), tell listeners the stall's keeper, counter and customer cells and the shop's plot (a `Dressings.onStall(Consumer<StallSetUp>)` or the like) | `social/life/StallWares` lays wares on the market-day stalls' counters the same way it does for built counters (`StallWares.lay` takes the counter points; a market-day stall needs only its counter cell and the keeper's shop) | **not landed** (A2 commit 8b3d7a78 has laundry and winter dressings, no stall callback). TODO in `StallWares` ("market-day stalls"). |
| **A1** roads and travellers | the cart entity (`hearth:cart`, `npc/travel/cart/Carts`, `CartEntity.setup/hitchTo`) | `npc/work/HaulerWork` through `npc/work/field/Handcarts` (handcarts on the town's streets, the logging camp's logs to the sawmill) | **landed and used**: A4 merged `hearth-lwA1` at 9a230767 (A1 part 3 plus hearth), so **merge A1 into `hearth` before A4**. |

When A2's callback lands the step is small: `StallWares.lay` already takes counter points and is caps-checked and
noSave-tagged (`FieldWork.NO_SAVE`).

## Asked of A1

- The handcart is drawn from behind (the cart trails its puller), which reads as a hauler pulling it; a pushed handcart
  (cart ahead of the folk) would suit `Kind.HANDCART` better if A1 ever adds a "pushed" hitch.

## Found by the work tour (2026-10-21, Ulfdal, a historic northern city on seed 20261003)

- **Economy (shop keepers)**: in an adopted historic town every shop with a counter (bakery, market stalls, market hall,
  inn, tavern, shophouses, chandler) had **no keeper** (`Shop.keeper == null`, no resident's workplace on its plot), so
  `Shops.daily` never restocks them and their counters stay empty although the stores hold thousands of units; only the
  workshops' shops (sawmill, quarry, watermill, logging camp) were stocked. `StallWares` shows that faithfully (empty
  boards). The tour fills the bakery from the stores as a logged fixture to check wares against the books. Asked: give
  counter shops keepers when a historic town is adopted (or let `Shops.keeper` fall back to a merchant of the town).
- **The first block set of a pass is dear**: laying the logging camp's timber stack (12 logs, `Clamps.camp`), the first
  `level.setBlock` in the pass cost the server 7-25 ms and the rest a few µs each; a probe setting six logs at the town's
  heart cost 41 µs for the first. Something pays a one-off price on the first change to that chunk in a tick (a chunk
  status, a listener, light?). It happens only when a stack or clamp dressing changes (a few times a sim day), so A4
  lays a stack in one pass; worth a profile by whoever owns chunk-level hooks, since A2's and B1's dressings meet it too.
- **Cart ground (A1)**: `CartEntity.groundY` scans down from the puller's height, so a cart whose puller walks under
  eaves or up a stair lands on the roof. A4's haulers park the cart whenever they are under a roof; travel parties may
  want the same guard.

## Asked of owners outside the lane (worked around meanwhile)

- **Deep towns' docks on cavern water** (`plan/growth/Docks`, plan owner): `UndergroundProbe.waterY` now answers from
  Expanse's cavern model (`sim/terrain/ExpanseUnderground`). The deep-town planner can site landings and docks on it;
  once a deep town has a `DOCK` POI on real water, `Boats` moors its boats there with no further change.
- **The shared bark engine** (§0.2; lane A3 owns the crowd barks and their cooldown config): A4's `WorkBarks` keeps the
  §0.2 cooldowns itself (60 s a folk, 4 s a player a town, 10 min a line) over the dialogue templates' categories
  `bark.work.*`. When a shared engine lands, `WorkBarks.maybe` can call it instead; the data needs no change.
- **FolkBrain** (A2): a behaviour that holds a folk stops the routine's labour count, so the drove is the herder's
  routine work (priority 50, through `HerderWork`) rather than a 110 behaviour, and its time is credited as the sim
  credits it abstractly. If FolkBrain ever credits labour under behaviours, the drove can move to a behaviour.
- **gametest `fabric.mod.json`**: A4 adds the `WorkTour` entrypoint to the one-line list (merge by hand if another lane
  adds one too).

## Batch A polish (2026-10-21)

The open items above were taken by the batch A polish pass: what was done, and what it asks of others, is in
`lwApolish.md`.
