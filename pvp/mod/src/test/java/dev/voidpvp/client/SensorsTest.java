package dev.voidpvp.client;

import com.google.gson.JsonObject;
import dev.voidpvp.client.sensor.ArmorSlot;
import dev.voidpvp.client.sensor.HitTally;
import dev.voidpvp.client.sensor.KeyStateTracker;
import dev.voidpvp.client.sensor.PotionFx;
import dev.voidpvp.client.sensor.ServerWatcher;
import dev.voidpvp.client.sensor.TickCoalescer;
import dev.voidpvp.client.sensor.TickInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plain half of the Mixin sensors (§6.6). */
class SensorsTest {

    private static KeyStateTracker tracker() {
        KeyStateTracker keys = new KeyStateTracker();
        keys.setBindings(17, 30, 31, 32, -100, -99, 57, 42);
        return keys;
    }

    @Test
    @DisplayName("keys are edge-triggered: only changes are events")
    void keysAreEdgeTriggered() {
        KeyStateTracker keys = tracker();
        assertTrue(keys.update(17, true));
        assertFalse(keys.update(17, true), "a repeat is not an edge");
        assertTrue(keys.update(17, false));
        assertFalse(keys.update(99, true), "a key we do not report is not an event");
        assertEquals(0, keys.payload().get("w").getAsInt());
    }

    @Test
    @DisplayName("a rebound key follows the binding, not the letter")
    void keysFollowBindings() {
        KeyStateTracker keys = new KeyStateTracker();
        keys.setBindings(200, 30, 31, 32, -100, -99, 57, 42);
        assertTrue(keys.update(200, true), "forward is bound to UP here");
        assertEquals(1, keys.payload().get("w").getAsInt());
        assertFalse(keys.update(17, true), "W itself is bound to nothing");
    }

    @Test
    @DisplayName("releaseAll reports once and only when something was down")
    void releaseAll() {
        KeyStateTracker keys = tracker();
        keys.update(17, true);
        assertTrue(keys.releaseAll());
        assertFalse(keys.releaseAll());
        assertEquals(0, keys.payload().get("w").getAsInt());
    }

    @Test
    @DisplayName("armor and fx ride only on the ticks where they changed")
    void tickOmitsUnchangedArmorAndFx() {
        TickCoalescer ticks = new TickCoalescer();
        List<ArmorSlot> armor = new ArrayList<ArmorSlot>(Arrays.asList(
                new ArmorSlot("helmet", "diamond_helmet", 12, 363, 1, true),
                ArmorSlot.empty("chestplate")));
        List<PotionFx> fx = new ArrayList<PotionFx>(Arrays.asList(
                new PotionFx(1, "potion.moveSpeed", 1, 41500, false)));

        JsonObject first = ticks.build(input(142, 38, 1, 2, 3, 90f, armor, fx));
        assertTrue(first.has("armor") && first.has("fx"), "the first tick carries everything");

        JsonObject second = ticks.build(input(140, 39, 1, 2, 3, 90f, armor, fx));
        assertFalse(second.has("armor"), "unchanged armor is absent, not null");
        assertFalse(second.has("fx"));
        // This used to assert that fps, ping and position "always ride along". They no longer do,
        // and that assertion was encoding the bug: each one re-rendered a HUD chip, and because
        // Ultralight reports damage as a single bounding rectangle, a chip in one corner dragged
        // that rectangle over the whole surface — a 49 ms repaint, 20 times a second.
        assertFalse(second.has("pos"), "an unmoved player sends no position");
        assertTrue(second.has("ping"), "ping moved 38 -> 39");

        List<ArmorSlot> damaged = new ArrayList<ArmorSlot>(Arrays.asList(
                new ArmorSlot("helmet", "diamond_helmet", 13, 363, 1, true),
                ArmorSlot.empty("chestplate")));
        assertTrue(ticks.build(input(140, 39, 1, 2, 3, 90f, damaged, fx)).has("armor"),
                "a durability change is a change");
    }

    @Test
    @DisplayName("a potion counting down is not a change, a refreshed one is")
    void fxChangeDetection() {
        TickCoalescer ticks = new TickCoalescer();
        List<PotionFx> first = Arrays.asList(new PotionFx(1, "potion.moveSpeed", 0, 10000, false));
        ticks.build(input(60, 10, 0, 0, 0, 0f, null, new ArrayList<PotionFx>(first)));

        List<PotionFx> ticking = Arrays.asList(
                new PotionFx(1, "potion.moveSpeed", 0, 9950, false));
        assertFalse(ticks.build(input(60, 10, 0, 0, 0, 0f, null, new ArrayList<PotionFx>(ticking)))
                .has("fx"), "the UI counts down on its own");

        List<PotionFx> refreshed = Arrays.asList(
                new PotionFx(1, "potion.moveSpeed", 0, 180000, false));
        assertTrue(ticks.build(input(60, 10, 0, 0, 0, 0f, null, new ArrayList<PotionFx>(refreshed)))
                .has("fx"), "a re-applied effect resets the clock and must be pushed");
    }

    @Test
    @DisplayName("reset makes the next tick carry everything again")
    void tickReset() {
        TickCoalescer ticks = new TickCoalescer();
        List<ArmorSlot> armor = Arrays.asList(ArmorSlot.empty("helmet"));
        ticks.build(input(60, 10, 0, 0, 0, 0f, new ArrayList<ArmorSlot>(armor), null));
        ticks.reset();
        assertTrue(ticks.build(input(60, 10, 0, 0, 0, 0f, new ArrayList<ArmorSlot>(armor), null))
                .has("armor"));
    }

    @Test
    @DisplayName("yaw is normalised to the range the schema declares")
    void yawNormalisation() {
        assertEquals(-87.4, TickCoalescer.normaliseYaw(-87.4), 1e-9);
        assertEquals(-90.0, TickCoalescer.normaliseYaw(270.0), 1e-9);
        assertEquals(0.0, TickCoalescer.normaliseYaw(720.0), 1e-9);
        assertEquals(-180.0, TickCoalescer.normaliseYaw(180.0), 1e-9,
                "the range is half-open at +180");
        assertEquals(-179.0, TickCoalescer.normaliseYaw(181.0), 1e-9);
        assertEquals(179.0, TickCoalescer.normaliseYaw(-181.0), 1e-9);
    }

    @Test
    @DisplayName("fps and ping are clamped to the schema's bounds")
    void tickClamps() {
        JsonObject payload = new TickCoalescer().build(input(-5, -99, 0, 0, 0, 0f, null, null));
        assertEquals(0, payload.get("fps").getAsInt());
        assertEquals(-1, payload.get("ping").getAsInt(), "-1 is 'unknown', nothing lower");
    }

    @Test
    @DisplayName("fps is rate-limited, not sent on every tick")
    void tickRateLimitsFps() {
        final long[] now = { 1_000L };
        TickCoalescer ticks = new TickCoalescer(new TickCoalescer.Clock() {
            @Override
            public long millis() {
                return now[0];
            }
        });

        assertTrue(ticks.build(input(100, 38, 0, 0, 0, 0f, null, null)).has("fps"), "the first tick sets it");

        // 20 Hz means a tick every 50 ms. Four of them inside the 250 ms window carry no fps at
        // all, even though the value changes every time — which is exactly the case that made the
        // menu stutter, because the live frame rate never repeats.
        for (int i = 1; i <= 4; i++) {
            now[0] += 50L;
            assertFalse(ticks.build(input(100 + i, 38, 0, 0, 0, 0f, null, null)).has("fps"),
                    "fps inside the rate limit is withheld");
        }

        now[0] += 50L;
        assertTrue(ticks.build(input(120, 38, 0, 0, 0, 0f, null, null)).has("fps"),
                "past the window it rides again");

        // A value that has not moved is withheld regardless of how long it has been.
        now[0] += 10_000L;
        assertFalse(ticks.build(input(120, 38, 0, 0, 0, 0f, null, null)).has("fps"),
                "an unchanged fps is never worth a repaint");
    }

    @Test
    @DisplayName("position rides only when the rounded value moves")
    void tickOmitsUnchangedPosition() {
        TickCoalescer ticks = new TickCoalescer();
        assertTrue(ticks.build(input(60, 20, 1.0, 64.0, 1.0, 0f, null, null)).has("pos"));
        assertFalse(ticks.build(input(60, 20, 1.0, 64.0, 1.0, 0f, null, null)).has("pos"),
                "standing still sends nothing");
        // Below the rounding the wire uses, so it is not a move.
        assertFalse(ticks.build(input(60, 20, 1.0001, 64.0, 1.0, 0f, null, null)).has("pos"),
                "a movement too small to encode is not a change");
        assertTrue(ticks.build(input(60, 20, 2.0, 64.0, 1.0, 0f, null, null)).has("pos"),
                "a real step rides");
    }

    @Test
    @DisplayName("the server sensor deduplicates and splits host from port")
    void serverWatcher() {
        ServerWatcher watcher = new ServerWatcher();
        assertFalse(watcher.connected());
        assertTrue(watcher.update(true, "mc.hypixel.net", 25565));
        assertFalse(watcher.update(true, "mc.hypixel.net", 25565));
        assertTrue(watcher.update(false, null, 0));
        assertEquals("", watcher.host(), "disconnect reports an empty host");
        assertFalse(watcher.payload().get("connected").getAsBoolean());

        assertEquals("mc.hypixel.net", ServerWatcher.stripPort("mc.hypixel.net:25577"));
        assertEquals(25577, ServerWatcher.portOf("mc.hypixel.net:25577"));
        assertEquals("mc.hypixel.net", ServerWatcher.stripPort("mc.hypixel.net"));
        assertEquals(25565, ServerWatcher.portOf("mc.hypixel.net"));
        assertEquals(25565, ServerWatcher.portOf("mc.hypixel.net:notaport"));
    }

    @Test
    @DisplayName("potion durations cross the bridge in milliseconds")
    void potionDurations() {
        assertEquals(50, PotionFx.ticksToMs(1));
        assertEquals(0, PotionFx.ticksToMs(-3));
        assertEquals(41500, PotionFx.ticksToMs(830));
    }


    /* ---------------------------------------------------------------- Wave 2 readings */

    /**
     * The five fields Wave 2's mods read, and the thing that actually matters about them.
     *
     * Each is tested for its OWN coalescing rule rather than merely for being present, because
     * the rules genuinely differ and getting one wrong costs a full-surface Ultralight repaint
     * twenty times a second — the failure `TickCoalescer`'s header is entirely about. A test
     * that only asserted "the field is on the payload" would pass for a field that republishes
     * every tick, which is the one thing it must not do.
     */
    @Test
    @DisplayName("saturation and held_count are value-checked, and absent when unread")
    void wave2ValueChecked() {
        TickCoalescer ticks = new TickCoalescer();
        TickInput in = input(60, 10, 0, 0, 0, 0f, null, null);
        in.saturation = Double.valueOf(12.0);
        in.heldCount = Integer.valueOf(64);
        JsonObject first = ticks.build(in);
        assertEquals(12.0, first.get("saturation").getAsDouble(), 1e-9);
        assertEquals(64, first.get("held_count").getAsInt());

        // Unchanged: both go absent. This is the whole point of the class.
        assertFalse(ticks.build(in).has("saturation"), "an unchanged saturation must not resend");
        assertFalse(ticks.build(in).has("held_count"), "an unchanged stack size must not resend");

        in.saturation = Double.valueOf(11.5);
        assertEquals(11.5, ticks.build(in).get("saturation").getAsDouble(), 1e-9);

        // An empty hand is not a count of zero: the field goes away rather than reading 0.
        in.heldCount = null;
        assertFalse(ticks.build(in).has("held_count"), "an empty hand omits the field");
    }

    @Test
    @DisplayName("speed and memory are rate-limited, not just value-checked")
    void wave2RateLimited() {
        final long[] clock = {0L};
        TickCoalescer ticks = new TickCoalescer(new TickCoalescer.Clock() {
            @Override
            public long millis() {
                return clock[0];
            }
        });
        TickInput in = input(60, 10, 0, 0, 0, 0f, null, null);
        in.speed = Double.valueOf(4.3);
        in.memoryUsedMb = Integer.valueOf(512);
        in.memoryMaxMb = Integer.valueOf(4096);
        JsonObject first = ticks.build(in);
        assertEquals(4.3, first.get("speed").getAsDouble(), 1e-9);
        assertEquals(512, first.getAsJsonObject("memory").get("used_mb").getAsInt());
        assertEquals(4096, first.getAsJsonObject("memory").get("max_mb").getAsInt());

        // A different value, one tick later. Both are suppressed by their interval — which a
        // value check alone would not do, and which is the entire reason they have one.
        clock[0] = 50L;
        in.speed = Double.valueOf(5.1);
        in.memoryUsedMb = Integer.valueOf(600);
        JsonObject soon = ticks.build(in);
        assertFalse(soon.has("speed"), "speed changes every tick; the limit must hold it");
        assertFalse(soon.has("memory"), "heap is restless; its limit is the loosest here");

        clock[0] = 400L;
        assertEquals(5.1, ticks.build(in).get("speed").getAsDouble(), 1e-9);
        // Still inside memory's second, so speed is through and memory is not.
        assertFalse(ticks.build(in).has("memory"), "memory's interval is longer than speed's");

        clock[0] = 1200L;
        assertEquals(600, ticks.build(in).getAsJsonObject("memory").get("used_mb").getAsInt());
    }

    @Test
    @DisplayName("hit counters are never rate-limited — a merged hit is a wrong combo")
    void wave2Hits() {
        final long[] clock = {0L};
        TickCoalescer ticks = new TickCoalescer(new TickCoalescer.Clock() {
            @Override
            public long millis() {
                return clock[0];
            }
        });
        TickInput in = input(60, 10, 0, 0, 0, 0f, null, null);
        in.hitsDealt = Integer.valueOf(0);
        in.hitsTaken = Integer.valueOf(0);
        ticks.build(in);

        // Two hits in consecutive ticks. Both must arrive: this is the one new field where
        // dropping an update is a wrong answer rather than a stale one, because the client
        // derives the combo by watching the counter move.
        in.hitsDealt = Integer.valueOf(1);
        assertEquals(1, ticks.build(in).getAsJsonObject("hits").get("dealt").getAsInt());
        clock[0] = 1L;
        in.hitsDealt = Integer.valueOf(2);
        assertEquals(2, ticks.build(in).getAsJsonObject("hits").get("dealt").getAsInt());

        // Nothing landed: absent, like every other field.
        assertFalse(ticks.build(in).has("hits"), "an unchanged pair must not resend");

        // Being hit moves the other counter, which is what breaks a combo on the client.
        in.hitsTaken = Integer.valueOf(1);
        assertEquals(1, ticks.build(in).getAsJsonObject("hits").get("taken").getAsInt());
    }

    @Test
    @DisplayName("a swing at air is not a hit — the counter is hits, not clicks")
    void aSwingAtAirIsNotAHit() {
        HitTally hits = new HitTally();

        // The bug this class exists for. 1.8.9's `MinecraftClient.tick` calls `doAttack()` on
        // every `attackKey.wasPressed()` with no look at the crosshair target, and `doAttack`
        // swings the hand before it examines anything and then falls through its MISS arm to the
        // same trailing `return` the ENTITY arm reaches. The old sensor incremented on reaching
        // that return, so `hits.dealt` counted clicks — a second CPS readout with a different
        // window, on a field `bridge.json` defines as "attacks the player has landed".
        hits.swung(HitTally.Swing.AIR, false, false, false);
        hits.swung(HitTally.Swing.AIR, false, false, false);
        assertEquals(0, hits.dealt(), "a swing at air must not advance the counter");

        // Mining is not fighting either, and it took the same path to TAIL.
        hits.swung(HitTally.Swing.BLOCK, false, false, false);
        assertEquals(0, hits.dealt(), "a swing at a block must not advance the counter");

        hits.swung(HitTally.Swing.ENTITY, true, true, false);
        assertEquals(1, hits.dealt(), "a swing that resolved onto a live target is a hit");
    }

    @Test
    @DisplayName("landed means vanilla would have delivered the attack, and nothing more")
    void landedFollowsVanillasOwnGate() {
        HitTally hits = new HitTally();

        // `Entity.isAlive()` is `!removed`. The crosshair raycast still resolves onto an entity
        // that has already been removed client-side, and a swing through a corpse deals nothing.
        hits.swung(HitTally.Swing.ENTITY, false, true, false);
        assertEquals(0, hits.dealt(), "a dead target is not a hit");

        // `PlayerEntity.attack` opens with `if (!target.isAttackable()) return;`, so this is
        // vanilla's own definition of an attack rather than one invented here.
        hits.swung(HitTally.Swing.ENTITY, true, false, false);
        assertEquals(0, hits.dealt(), "an unattackable target is not a hit");

        // `ClientPlayerInteractionManager.attackEntity` sends the ATTACK packet either way but
        // skips `PlayerEntity.attack` in spectator, and the server ignores it.
        hits.swung(HitTally.Swing.ENTITY, true, true, true);
        assertEquals(0, hits.dealt(), "a spectator does not land hits");

        hits.swung(HitTally.Swing.ENTITY, true, true, false);
        assertEquals(1, hits.dealt());
        assertEquals(0, hits.taken(), "swinging is not being hit");
    }

    @Test
    @DisplayName("hits taken ride the rising edge of hurtTime, not the level")
    void hitsTakenAreEdgeTriggered() {
        HitTally hits = new HitTally();

        // `hurtTime` is set to 10 on damage and counts down. Counting the level would count one
        // hit ten times.
        hits.sawHurtTime(0);
        hits.sawHurtTime(10);
        hits.sawHurtTime(9);
        hits.sawHurtTime(8);
        assertEquals(1, hits.taken(), "one hit, however long the animation lasts");

        // Re-hit before the animation finished: the value rises again, and that is a second hit.
        hits.sawHurtTime(10);
        assertEquals(2, hits.taken());

        hits.sawHurtTime(0);
        assertEquals(2, hits.taken(), "the animation ending is not a hit");
    }

    @Test
    @DisplayName("reset() forgets the Wave 2 fields too, so a reconnect replays them")
    void wave2Reset() {
        TickCoalescer ticks = new TickCoalescer();
        TickInput in = input(60, 10, 0, 0, 0, 0f, null, null);
        in.saturation = Double.valueOf(9.0);
        in.heldCount = Integer.valueOf(12);
        in.hitsDealt = Integer.valueOf(3);
        in.hitsTaken = Integer.valueOf(1);
        ticks.build(in);
        assertFalse(ticks.build(in).has("saturation"), "precondition: coalesced away");

        // §6.1: on reconnect the mod flushes everything it knows. A field the coalescer still
        // believes it has sent would be missing from that flush and the chip would stay empty
        // until the value happened to change — which for a stack size can be a whole match.
        ticks.reset();
        JsonObject after = ticks.build(in);
        assertTrue(after.has("saturation"), "reset must replay saturation");
        assertTrue(after.has("held_count"), "reset must replay the stack size");
        assertTrue(after.has("hits"), "reset must replay the hit counters");
    }

    /**
     * A {@link TickInput} from the eight readings these tests care about.
     *
     * `build` takes an object now rather than thirteen positional arguments (see
     * `TickInput`), and this keeps the call sites here reading as the eight-value fixtures they
     * are. Test-local on purpose: a convenience overload in production would be a second entry
     * point into the coalescer, and the whole reason for the object is that there should be one.
     */
    private static TickInput input(int fps, int ping, double x, double y, double z, float yaw,
                                   List<ArmorSlot> armor, List<PotionFx> fx) {
        TickInput in = new TickInput();
        in.fps = fps;
        in.ping = ping;
        in.x = x;
        in.y = y;
        in.z = z;
        in.yaw = yaw;
        in.armor = armor;
        in.fx = fx;
        return in;
    }
}
