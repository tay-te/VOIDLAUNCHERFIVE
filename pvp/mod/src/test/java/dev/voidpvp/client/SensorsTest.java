package dev.voidpvp.client;

import com.google.gson.JsonObject;
import dev.voidpvp.client.sensor.ArmorSlot;
import dev.voidpvp.client.sensor.KeyStateTracker;
import dev.voidpvp.client.sensor.PotionFx;
import dev.voidpvp.client.sensor.ServerWatcher;
import dev.voidpvp.client.sensor.TickCoalescer;
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

        JsonObject first = ticks.build(142, 38, 1, 2, 3, 90f, armor, fx);
        assertTrue(first.has("armor") && first.has("fx"), "the first tick carries everything");

        JsonObject second = ticks.build(140, 39, 1, 2, 3, 90f, armor, fx);
        assertFalse(second.has("armor"), "unchanged armor is absent, not null");
        assertFalse(second.has("fx"));
        assertTrue(second.has("fps") && second.has("ping") && second.has("pos"),
                "fps, ping and position always ride along");

        List<ArmorSlot> damaged = new ArrayList<ArmorSlot>(Arrays.asList(
                new ArmorSlot("helmet", "diamond_helmet", 13, 363, 1, true),
                ArmorSlot.empty("chestplate")));
        assertTrue(ticks.build(140, 39, 1, 2, 3, 90f, damaged, fx).has("armor"),
                "a durability change is a change");
    }

    @Test
    @DisplayName("a potion counting down is not a change, a refreshed one is")
    void fxChangeDetection() {
        TickCoalescer ticks = new TickCoalescer();
        List<PotionFx> first = Arrays.asList(new PotionFx(1, "potion.moveSpeed", 0, 10000, false));
        ticks.build(60, 10, 0, 0, 0, 0f, null, new ArrayList<PotionFx>(first));

        List<PotionFx> ticking = Arrays.asList(
                new PotionFx(1, "potion.moveSpeed", 0, 9950, false));
        assertFalse(ticks.build(60, 10, 0, 0, 0, 0f, null, new ArrayList<PotionFx>(ticking))
                .has("fx"), "the UI counts down on its own");

        List<PotionFx> refreshed = Arrays.asList(
                new PotionFx(1, "potion.moveSpeed", 0, 180000, false));
        assertTrue(ticks.build(60, 10, 0, 0, 0, 0f, null, new ArrayList<PotionFx>(refreshed))
                .has("fx"), "a re-applied effect resets the clock and must be pushed");
    }

    @Test
    @DisplayName("reset makes the next tick carry everything again")
    void tickReset() {
        TickCoalescer ticks = new TickCoalescer();
        List<ArmorSlot> armor = Arrays.asList(ArmorSlot.empty("helmet"));
        ticks.build(60, 10, 0, 0, 0, 0f, new ArrayList<ArmorSlot>(armor), null);
        ticks.reset();
        assertTrue(ticks.build(60, 10, 0, 0, 0, 0f, new ArrayList<ArmorSlot>(armor), null)
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
        JsonObject payload = new TickCoalescer().build(-5, -99, 0, 0, 0, 0f, null, null);
        assertEquals(0, payload.get("fps").getAsInt());
        assertEquals(-1, payload.get("ping").getAsInt(), "-1 is 'unknown', nothing lower");
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
}
