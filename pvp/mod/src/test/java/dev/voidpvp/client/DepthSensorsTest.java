package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidpvp.client.sensor.HitTally;
import dev.voidpvp.client.sensor.InventoryCounts;
import dev.voidpvp.client.sensor.ReachMeter;
import dev.voidpvp.client.sensor.TickCoalescer;
import dev.voidpvp.client.sensor.TickInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The depth workstream's sensors: `counts`, `reach` and `hits.swings` (bridge.json). */
class DepthSensorsTest {

    private static final int SPLASH_HEALING_I = 16421;
    private static final int SPLASH_HEALING_II = 16453;
    private static final int DRINK_HEALING_I = 8261;

    // -----------------------------------------------------------------
    // counts
    // -----------------------------------------------------------------

    @Test
    @DisplayName("every named key is present, zeros included, and held_total leads")
    void countsShape() {
        InventoryCounts c = new InventoryCounts();
        c.begin("stone", 0, false);
        c.add("stone", 0, 10);
        Map<String, Integer> out = c.result();
        assertEquals("held_total", out.keySet().iterator().next());
        assertEquals(10, out.get("held_total").intValue());
        for (String key : InventoryCounts.NAMED) {
            assertEquals(0, out.get(key).intValue(), key);
        }
        assertEquals(InventoryCounts.NAMED.length + 1, out.size());
    }

    @Test
    @DisplayName("an empty hand omits held_total rather than reporting 0")
    void countsEmptyHand() {
        InventoryCounts c = new InventoryCounts();
        c.begin(null, 0, false);
        c.add("ender_pearl", 0, 3);
        Map<String, Integer> out = c.result();
        assertFalse(out.containsKey("held_total"));
        assertEquals(3, out.get("ender_pearl").intValue());
    }

    @Test
    @DisplayName("held_total matches item and variant, and ignores wear")
    void countsHeldVariant() {
        InventoryCounts wool = new InventoryCounts();
        wool.begin("wool", 14, true); // red wool, a subtype item
        wool.add("wool", 14, 64);
        wool.add("wool", 14, 20);
        wool.add("wool", 0, 64); // white is a different variant
        Map<String, Integer> out = wool.result();
        assertEquals(84, out.get("held_total").intValue());
        assertEquals(148, out.get("wool").intValue(), "wool counts every colour");

        InventoryCounts sword = new InventoryCounts();
        sword.begin("diamond_sword", 0, false); // damage is wear, not a variant
        sword.add("diamond_sword", 0, 1);
        sword.add("diamond_sword", 300, 1);
        assertEquals(2, sword.result().get("held_total").intValue());
    }

    @Test
    @DisplayName("the named keys map 1.8.9's registry ids, golden apples of both kinds")
    void countsNamedIds() {
        InventoryCounts c = new InventoryCounts();
        c.begin(null, 0, false);
        c.add("golden_apple", 0, 5);
        c.add("golden_apple", 1, 2); // enchanted
        c.add("web", 0, 4);          // cobweb's item id is its block's
        c.add("mushroom_stew", 0, 1);
        c.add("mushroom_stew", 0, 1);
        c.add("arrow", 0, 64);
        c.add("arrow", 0, 0);        // an empty stack is not a stack
        c.add(null, 0, 9);
        Map<String, Integer> out = c.result();
        assertEquals(7, out.get("golden_apple").intValue());
        assertEquals(4, out.get("cobweb").intValue());
        assertEquals(2, out.get("soup").intValue());
        assertEquals(64, out.get("arrow").intValue());
    }

    @Test
    @DisplayName("health_potion is splash Healing I and II only")
    void countsHealthPotions() {
        InventoryCounts c = new InventoryCounts();
        c.begin(null, 0, false);
        c.add("potion", SPLASH_HEALING_I, 1);
        c.add("potion", SPLASH_HEALING_II, 1);
        c.add("potion", DRINK_HEALING_I, 1);
        c.add("potion", 16386, 1); // splash swiftness
        assertEquals(2, c.result().get("health_potion").intValue());
    }

    @Test
    @DisplayName("counts are clamped to the schema's 2304")
    void countsClamp() {
        InventoryCounts c = new InventoryCounts();
        c.begin(null, 0, false);
        c.add("tnt", 0, 5000);
        assertEquals(InventoryCounts.MAX, c.result().get("tnt").intValue());
    }

    @Test
    @DisplayName("begin forgets the previous walk")
    void countsBeginResets() {
        InventoryCounts c = new InventoryCounts();
        c.begin(null, 0, false);
        c.add("egg", 0, 16);
        c.result();
        c.begin(null, 0, false);
        assertEquals(0, c.result().get("egg").intValue());
    }

    @Test
    @DisplayName("counts are sent whole, only when something changed, and again after reset")
    void countsCoalesce() {
        TickCoalescer ticks = new TickCoalescer();
        TickInput in = base();
        in.counts = counts(64, 3);
        JsonObject first = ticks.build(in);
        assertTrue(first.has("counts"));
        assertEquals(3, first.getAsJsonObject("counts").get("ender_pearl").getAsInt());

        in.clearOptional();
        in.counts = counts(64, 3);
        assertFalse(ticks.build(in).has("counts"), "unchanged is omitted");

        in.counts = counts(63, 3);
        JsonObject changed = ticks.build(in);
        assertTrue(changed.has("counts"));
        assertEquals(3, changed.getAsJsonObject("counts").get("ender_pearl").getAsInt(),
                "the whole object, not just the key that moved");

        Map<String, Integer> emptyHand = counts(63, 3);
        emptyHand.remove("held_total");
        in.counts = emptyHand;
        JsonObject dropped = ticks.build(in);
        assertTrue(dropped.has("counts"), "held_total going absent is a change");
        assertFalse(dropped.getAsJsonObject("counts").has("held_total"));

        in.counts = null;
        assertFalse(ticks.build(in).has("counts"), "no reading, no field");

        ticks.reset();
        in.counts = emptyHand;
        assertTrue(ticks.build(in).has("counts"), "reset forgets it");
    }

    // -----------------------------------------------------------------
    // hits.swings
    // -----------------------------------------------------------------

    @Test
    @DisplayName("every swing counts, landed or not, and swung() says which")
    void swingsCountEverything() {
        HitTally t = new HitTally();
        assertFalse(t.swung(HitTally.Swing.AIR, false, false, false));
        assertFalse(t.swung(HitTally.Swing.BLOCK, false, false, false));
        assertTrue(t.swung(HitTally.Swing.ENTITY, true, true, false));
        assertFalse(t.swung(HitTally.Swing.ENTITY, true, true, true), "spectating");
        assertEquals(4, t.swings());
        assertEquals(1, t.dealt());
    }

    @Test
    @DisplayName("hits is resent when only swings moved, and swings rides inside it")
    void swingsCoalesce() {
        TickCoalescer ticks = new TickCoalescer();
        TickInput in = base();
        hits(in, 1, 0, 1);
        JsonObject first = ticks.build(in);
        assertEquals(1, first.getAsJsonObject("hits").get("swings").getAsInt());

        hits(in, 1, 0, 1);
        assertFalse(ticks.build(in).has("hits"));

        hits(in, 1, 0, 2);
        JsonObject swung = ticks.build(in);
        assertTrue(swung.has("hits"), "a whiff moves swings and nothing else");
        assertEquals(1, swung.getAsJsonObject("hits").get("dealt").getAsInt());
        assertEquals(2, swung.getAsJsonObject("hits").get("swings").getAsInt());

        hits(in, 2, 0, 2);
        in.hitsSwings = null;
        JsonObject without = ticks.build(in);
        assertFalse(without.getAsJsonObject("hits").has("swings"),
                "a sensor without swings still sends a valid hits object");
    }

    // -----------------------------------------------------------------
    // reach
    // -----------------------------------------------------------------

    @Test
    @DisplayName("reach to a hit point is the straight-line distance")
    void reachToPoint() {
        assertEquals(3.0, ReachMeter.toPoint(0, 1.62, 0, 0, 1.62, 3), 1e-12);
        assertEquals(5.0, ReachMeter.toPoint(1, 1, 1, 4, 5, 1), 1e-12);
    }

    @Test
    @DisplayName("the fallback is the closest point of the box, 0 from inside it")
    void reachToBox() {
        // A player-sized box 0.6 wide, centred 3 blocks down +z, expanded by vanilla's 0.1.
        double minX = -0.4, minY = 64 - 0.1, minZ = 2.6, maxX = 0.4, maxY = 65.9, maxZ = 3.4;
        assertEquals(2.6, ReachMeter.toBox(0, 65.62, 0, minX, minY, minZ, maxX, maxY, maxZ),
                1e-12, "straight ahead, at eye height inside the box's span");
        assertEquals(Math.sqrt(2.6 * 2.6 + 1.0 * 1.0),
                ReachMeter.toBox(0, 66.9, 0, minX, minY, minZ, maxX, maxY, maxZ), 1e-12,
                "above the box: the corner edge is the closest point");
        assertEquals(0.0, ReachMeter.toBox(0, 65, 3, minX, minY, minZ, maxX, maxY, maxZ),
                1e-12, "an eye inside the box measures 0, as vanilla's pick does");
    }

    @Test
    @DisplayName("readings round to 3 decimals, clamp to 0-16 and count every measurement")
    void reachRecord() {
        ReachMeter m = new ReachMeter();
        assertEquals(0, m.n());
        assertEquals(2.874, m.record(2.87449), 1e-12);
        assertEquals(2.875, m.record(2.8745), 1e-12);
        assertEquals(2.875, m.record(2.8745), 1e-12);
        assertEquals(3, m.n(), "two hits at the same reach are two readings");
        assertEquals(16.0, m.record(40), 1e-12);
        assertEquals(0.0, m.record(-1), 1e-12);
        assertEquals(0.0, m.record(Double.NaN), 1e-12);
        assertEquals(6, m.n());
        assertEquals(0.0, m.last(), 1e-12);
        assertEquals(1.235, ReachMeter.round3(1.2345), 1e-12);
    }

    @Test
    @DisplayName("reach is sent only on the tick n changes")
    void reachCoalesce() {
        TickCoalescer ticks = new TickCoalescer();
        TickInput in = base();
        assertFalse(ticks.build(in).has("reach"), "absent until the first measurement");

        in.reachBlocks = Double.valueOf(2.874);
        in.reachN = Integer.valueOf(1);
        JsonObject first = ticks.build(in);
        assertEquals(2.874, first.getAsJsonObject("reach").get("blocks").getAsDouble(), 1e-12);
        assertEquals(1, first.getAsJsonObject("reach").get("n").getAsInt());

        assertFalse(ticks.build(in).has("reach"), "the same reading is not resent");

        in.reachN = Integer.valueOf(2); // same distance, a new hit
        assertTrue(ticks.build(in).has("reach"));

        in.clearOptional();
        assertNull(in.reachN);
        ticks.reset();
        in.reachBlocks = Double.valueOf(3.1);
        in.reachN = Integer.valueOf(2);
        assertTrue(ticks.build(in).has("reach"), "reset forgets what was reported");
    }

    // -----------------------------------------------------------------
    // bridge.json's second tick example
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the hits/counts/reach tick example is what the sensors produce")
    void bridgeExample() {
        JsonObject expected = null;
        JsonArray examples = Schemas.examples("bridge.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if (o.has("e") && "tick".equals(o.get("e").getAsString())
                    && o.getAsJsonObject("payload").has("counts")) {
                expected = o.getAsJsonObject("payload");
                break;
            }
        }
        assertTrue(expected != null, "bridge.json lost its counts/reach tick example");

        JsonObject h = expected.getAsJsonObject("hits");
        JsonObject c = expected.getAsJsonObject("counts");
        JsonObject r = expected.getAsJsonObject("reach");

        // Built through the real sensors, not by copying the example's numbers into the input.
        HitTally tally = new HitTally();
        int landed = h.get("dealt").getAsInt();
        int swings = h.get("swings").getAsInt();
        for (int i = 0; i < swings; i++) {
            tally.swung(i < landed ? HitTally.Swing.ENTITY : HitTally.Swing.AIR, true, true, false);
        }
        int taken = h.get("taken").getAsInt();
        for (int i = 0; i < taken; i++) {
            tally.sawHurtTime(10);
            tally.sawHurtTime(0);
        }

        InventoryCounts counts = new InventoryCounts();
        counts.begin("wool", 0, true);
        counts.add("wool", 0, c.get("held_total").getAsInt());
        counts.add("wool", 5, c.get("wool").getAsInt() - c.get("held_total").getAsInt());
        counts.add("ender_pearl", 0, c.get("ender_pearl").getAsInt());
        counts.add("golden_apple", 0, c.get("golden_apple").getAsInt());
        for (int i = 0; i < c.get("health_potion").getAsInt(); i++) {
            counts.add("potion", SPLASH_HEALING_II, 1);
        }

        ReachMeter reach = new ReachMeter();
        for (int i = 1; i < r.get("n").getAsInt(); i++) {
            reach.record(3.0);
        }
        reach.record(r.get("blocks").getAsDouble());

        TickInput in = base();
        in.hitsDealt = Integer.valueOf(tally.dealt());
        in.hitsTaken = Integer.valueOf(tally.taken());
        in.hitsSwings = Integer.valueOf(tally.swings());
        in.counts = counts.result();
        in.reachBlocks = Double.valueOf(reach.last());
        in.reachN = Integer.valueOf(reach.n());
        JsonObject payload = new TickCoalescer().build(in);
        JsonObject only = new JsonObject();
        only.add("hits", payload.get("hits"));
        only.add("counts", payload.get("counts"));
        only.add("reach", payload.get("reach"));
        Schemas.assertContains(expected, only, "tick payload");
        assertEquals(expected.getAsJsonObject("hits").entrySet().size(),
                payload.getAsJsonObject("hits").entrySet().size());
        assertEquals(expected.getAsJsonObject("reach").entrySet().size(),
                payload.getAsJsonObject("reach").entrySet().size());
    }

    // -----------------------------------------------------------------

    private static TickInput base() {
        TickInput in = new TickInput();
        in.fps = 60;
        in.ping = 20;
        return in;
    }

    private static void hits(TickInput in, int dealt, int taken, int swings) {
        in.hitsDealt = Integer.valueOf(dealt);
        in.hitsTaken = Integer.valueOf(taken);
        in.hitsSwings = Integer.valueOf(swings);
    }

    private static Map<String, Integer> counts(int held, int pearls) {
        InventoryCounts c = new InventoryCounts();
        c.begin("wool", 0, true);
        c.add("wool", 0, held);
        c.add("ender_pearl", 0, pearls);
        return new LinkedHashMap<String, Integer>(c.result());
    }
}
