# Requests from the systems track, core (wave 7)

What the core changed is in `docs/review/systems/core.md` and `docs/SIM.md` §6, §8, §10. These belong to others.

## 1. Build: `civic/cemetery` fails the invariants (the full build is red)

**Seen** (`./gradlew build` on `hearth-sys-core`, cut from `hearth` at dc19d9ad, 2026-10-16): 4 of 2242 tests fail, all
`civic/cemetery`: `BlueprintInvariantsTest.everyVariantHoldsTheInvariants` (steppe, thatched, seed 41462, west: "1 job
sites/stores cannot be walked to from a door", JOB_SITE at -2,63,-1 on packed mud) and `SocialLifeTest` (on slope, on steep,
every variant). Nothing in the core touches blueprints; the same tests fail on the branch it was cut from.

Also logged (not a failure): `archetype hearth:infra/stairs failed in style hearth:mesa (seed 1369396, 1x9 facing east):
IllegalArgumentException: a course needs at least two x,y,z vertices` (`StairStreet.draw:67`): a 1-wide stair plot.

## 2. Every tour: leave a world without `TestSingleplayerContext.close()`

**Seen**: on 26.3 the client game test framework can deadlock in `close()`: the client halts the integrated server with
a blocking call (`IntegratedServer.halt` → `executeBlocking`) while the server thread waits in the framework's tick phase
for the client. Two of five PersistTour runs hung there for good (render thread in `IntegratedServer.halt`, server
thread in `ThreadingImpl.enterPhase`), and the run then never ends, holding the machine's game slot.

**Asked**: tours that leave a world mid-run (or at the end) halt the server from its own thread first and let the client
follow, as `PersistTour.quit` does:

```java
MinecraftServer server = world.getServer().computeOnServer(s -> s);
world.getServer().runOnServer(s -> s.halt(false));
context.waitFor(mc -> server.isStopped(), 2400);
context.waitFor(mc -> mc.level == null, 2400);
context.waitTicks(2);
context.setScreen(TitleScreen::new);
```
