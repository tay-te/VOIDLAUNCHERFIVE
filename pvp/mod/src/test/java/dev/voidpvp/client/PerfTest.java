package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.bench.BenchPath;
import dev.voidpvp.client.bench.FrameStats;
import dev.voidpvp.client.perf.BlockOpacity;
import dev.voidpvp.client.perf.CullingHooks;
import dev.voidpvp.client.perf.FrameCap;
import dev.voidpvp.client.perf.OpacityCache;
import dev.voidpvp.client.perf.ParticleThinner;
import dev.voidpvp.client.perf.PerfConfig;
import dev.voidpvp.client.perf.VoxelRaycast;
import dev.voidpvp.client.sensor.ScoreboardLines;
import dev.voidpvp.client.state.Loadout;
import dev.voidpvp.client.visual.FogMath;
import dev.voidpvp.client.visual.GuiScaleMath;
import dev.voidpvp.client.visual.VisualConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plain halves of the Wave 7 mods: the FPS boost, the benchmark and the visual mods. */
class PerfTest {

    /** A hand-drawn voxel grid. */
    private static final class Grid implements BlockOpacity {
        final Set<Long> solid = new HashSet<Long>();
        int lookups;

        Grid fill(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        solid.add(key(x, y, z));
                    }
                }
            }
            return this;
        }

        Grid clear(int x, int y, int z) {
            solid.remove(key(x, y, z));
            return this;
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            lookups++;
            return solid.contains(key(x, y, z));
        }

        private static long key(int x, int y, int z) {
            return ((long) x << 40) ^ ((long) y << 20) ^ (z & 0xFFFFF);
        }
    }

    @AfterEach
    void resetConfig() {
        PerfConfig.alwaysDrawWithinSq = 16;
        PerfConfig.benchForceOff = false;
    }

    // -- the ray -------------------------------------------------------------------------------

    @Test
    @DisplayName("a ray through open air is clear, and one through a wall is not")
    void rayThroughWall() {
        Grid g = new Grid();
        assertTrue(VoxelRaycast.clear(g, 0.5, 1.6, 0.5, 10.5, 1.6, 0.5));
        g.fill(5, 0, -5, 5, 10, 5);
        assertFalse(VoxelRaycast.clear(g, 0.5, 1.6, 0.5, 10.5, 1.6, 0.5));
    }

    @Test
    @DisplayName("a one-block wall cannot be stepped over, at any angle")
    void noTunnelling() {
        Grid g = new Grid().fill(5, -20, -20, 5, 20, 20);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32 * 0.45;
            double tx = 12.5;
            double tz = 0.5 + Math.tan(a) * 12;
            double ty = 1.6 + Math.sin(i) * 6;
            if (Math.abs(tz) < 20 && Math.abs(ty) < 20) {
                assertFalse(VoxelRaycast.clear(g, 0.5, 1.6, 0.5, tx, ty, tz), "angle " + i);
            }
        }
    }

    @Test
    @DisplayName("neither end's own cell is tested")
    void endCellsNotTested() {
        Grid g = new Grid().fill(0, 1, 0, 0, 1, 0).fill(9, 1, 0, 9, 1, 0);
        assertTrue(VoxelRaycast.clear(g, 0.5, 1.5, 0.5, 9.5, 1.5, 0.5));
    }

    @Test
    @DisplayName("an axis-aligned ray and a ray of zero length behave")
    void degenerateRays() {
        Grid g = new Grid().fill(0, 5, 0, 0, 5, 0);
        assertFalse(VoxelRaycast.clear(g, 0.5, 0.5, 0.5, 0.5, 9.5, 0.5), "straight up into it");
        assertTrue(VoxelRaycast.clear(g, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5), "no length");
        assertTrue(VoxelRaycast.clear(g, -3.5, 0.5, -3.5, -3.5, 0.5, 9.5), "beside it");
    }

    // -- the box -------------------------------------------------------------------------------

    @Test
    @DisplayName("a mob fully behind a wall is hidden; through a one-block gap it is seen")
    void boxBehindWall() {
        Grid g = new Grid().fill(5, 0, -10, 5, 10, 10);
        double[] mob = {9.7, 1.0, -0.3, 10.3, 2.95, 0.3};
        assertFalse(VoxelRaycast.boxVisible(g, 0.5, 2.6, 0.5,
                mob[0], mob[1], mob[2], mob[3], mob[4], mob[5]));
        g.clear(5, 2, 0);
        assertTrue(VoxelRaycast.boxVisible(g, 0.5, 2.6, 0.5,
                mob[0], mob[1], mob[2], mob[3], mob[4], mob[5]));
    }

    @Test
    @DisplayName("a mob standing on the ground is seen over flat ground")
    void boxOnGround() {
        Grid g = new Grid().fill(-30, 0, -30, 30, 0, 30);
        assertTrue(VoxelRaycast.boxVisible(g, 0.5, 2.62, 0.5, 19.7, 1.0, 9.7, 20.3, 2.95, 10.3));
    }

    @Test
    @DisplayName("a box half round a corner is seen")
    void boxPeekingRoundCorner() {
        // Wall along x = 5 for z <= 0 only; the box straddles z = 0.
        Grid g = new Grid().fill(5, 0, -10, 5, 10, 0);
        assertTrue(VoxelRaycast.boxVisible(g, 0.5, 1.6, 0.5, 9.7, 1.0, -0.3, 10.3, 2.8, 2.3));
    }

    @Test
    @DisplayName("an eye inside the box always sees it")
    void eyeInsideBox() {
        Grid g = new Grid().fill(-5, -5, -5, 5, 5, 5);
        assertTrue(VoxelRaycast.boxVisible(g, 0.5, 0.5, 0.5, 0, 0, 0, 1, 1, 1));
    }

    @Test
    @DisplayName("glass is not a wall: only opaque cells block, so a window shows the room")
    void windowShowsRoom() {
        // A closed stone room with one window cell left non-opaque, as glass is.
        Grid g = new Grid().fill(10, 0, -3, 16, 6, 3).fill(11, 1, -2, 15, 5, 2);
        for (int x = 11; x <= 15; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = -2; z <= 2; z++) {
                    g.clear(x, y, z);
                }
            }
        }
        double[] chest = {12.5, 1.0, -0.5, 13.5, 2.0, 0.5};
        assertFalse(VoxelRaycast.boxVisible(g, 0.5, 2.6, 0.5,
                chest[0], chest[1], chest[2], chest[3], chest[4], chest[5]));
        g.clear(10, 2, 0);
        assertTrue(VoxelRaycast.boxVisible(g, 0.5, 2.6, 0.5,
                chest[0], chest[1], chest[2], chest[3], chest[4], chest[5]));
    }

    @Test
    @DisplayName("the per-pass cache answers like its source and saves lookups")
    void cacheMatchesSource() {
        Grid g = new Grid().fill(5, 0, -10, 5, 10, 10).clear(5, 2, 0);
        OpacityCache cache = new OpacityCache();
        cache.reset(g);
        for (int x = -2; x < 8; x++) {
            for (int y = -2; y < 6; y++) {
                for (int z = -3; z < 3; z++) {
                    assertEquals(g.solid.contains(((long) x << 40) ^ ((long) y << 20) ^ (z & 0xFFFFF)),
                            cache.opaque(x, y, z));
                }
            }
        }
        int first = g.lookups;
        for (int i = 0; i < 20; i++) {
            VoxelRaycast.boxVisible(cache, 0.5, 2.6, 0.5, 9.7, 1.0, -0.3, 10.3, 2.95, 0.3);
        }
        assertTrue(g.lookups - first < 50, "repeated passes hit the memo: " + (g.lookups - first));
        cache.reset(g);
        assertEquals(0, cache.sourceLookups(), "a reset forgets the count");
    }

    // -- culling decisions ---------------------------------------------------------------------

    @Test
    @DisplayName("culling never hides anything inside always_draw_within, and distance wins first")
    void verdicts() {
        PerfConfig.alwaysDrawWithinSq = 16;
        assertEquals(CullingHooks.DRAW, CullingHooks.verdict(9, Double.POSITIVE_INFINITY, true, true));
        assertEquals(CullingHooks.OCCLUDED, CullingHooks.verdict(400, Double.POSITIVE_INFINITY, true, true));
        assertEquals(CullingHooks.DRAW, CullingHooks.verdict(400, Double.POSITIVE_INFINITY, false, true));
        assertEquals(CullingHooks.DRAW, CullingHooks.verdict(400, Double.POSITIVE_INFINITY, true, false));
        assertEquals(CullingHooks.TOO_FAR, CullingHooks.verdict(5000, 4096, false, false));
    }

    // -- particles and frame cap ---------------------------------------------------------------

    @Test
    @DisplayName("a keep fraction keeps exactly that share of a burst, spread through it")
    void thinnerIsExact() {
        ParticleThinner t = new ParticleThinner();
        int kept = 0;
        int longestGap = 0;
        int gap = 0;
        for (int i = 0; i < 64; i++) {
            if (t.keep(0.25)) {
                kept++;
                longestGap = Math.max(longestGap, gap);
                gap = 0;
            } else {
                gap++;
            }
        }
        assertEquals(16, kept);
        assertTrue(longestGap <= 3, "evenly spread");
        ParticleThinner all = new ParticleThinner();
        ParticleThinner none = new ParticleThinner();
        for (int i = 0; i < 10; i++) {
            assertTrue(all.keep(1));
            assertFalse(none.keep(0));
        }
        assertTrue(ParticleThinner.isExplosion(0) && ParticleThinner.isExplosion(2));
        assertFalse(ParticleThinner.isExplosion(9), "crit particles are never thinned");
    }

    @Test
    @DisplayName("focused and in the world, the frame cap is vanilla's, always")
    void frameCapInWorld() {
        assertEquals(260, FrameCap.cap(260, true, true, false, 30, 60, true));
        assertEquals(144, FrameCap.cap(144, true, true, false, 5, 15, true));
    }

    @Test
    @DisplayName("background, menu and minimised each cap, and the lowest wins")
    void frameCapStates() {
        assertEquals(30, FrameCap.cap(260, false, true, false, 30, 60, true), "background");
        assertEquals(60, FrameCap.cap(30, true, true, true, 30, 60, true), "title screen");
        assertEquals(30, FrameCap.cap(260, false, true, true, 30, 60, true), "menu, background");
        assertEquals(FrameCap.MINIMIZED_FPS, FrameCap.cap(260, false, false, false, 30, 60, true));
        assertEquals(30, FrameCap.cap(260, false, false, false, 30, 60, false), "idle off");
    }

    // -- benchmark arithmetic ------------------------------------------------------------------

    @Test
    @DisplayName("1% low is the FPS of the 99th-percentile frame, average is frames over time")
    void frameStats() {
        float[] frames = new float[1000];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = i < 990 ? 5f : 20f;
        }
        double[] s = FrameStats.summarize(frames);
        assertEquals(1000 * 1000.0 / (990 * 5 + 10 * 20), s[0], 1e-6);
        assertEquals(200.0, s[1], 1e-6, "the 990th frame is still a 5 ms one");
        assertEquals(50.0, s[2], 1e-6);
        assertEquals(0.0, FrameStats.summarize(new float[0])[0]);
    }

    @Test
    @DisplayName("the bench path is closed, continuous and faces the way it moves")
    void benchPath() {
        double[][] square = {{0, 0}, {10, 0}, {10, 10}, {0, 10}};
        double[] start = BenchPath.pose(square, 5, 0);
        assertEquals(0, start[0], 1e-9);
        assertEquals(0, start[1], 1e-9);
        assertEquals(-90, start[2], 1e-9, "moving +x is yaw -90");
        double[] lap = BenchPath.pose(square, 5, 8);
        assertEquals(0, lap[0], 1e-9);
        assertEquals(0, lap[1], 1e-9);
        double[] mid = BenchPath.pose(square, 5, 3);
        assertEquals(10, mid[0], 1e-9);
        assertEquals(5, mid[1], 1e-9);
        assertEquals(0, mid[2], 1e-9, "moving +z is yaw 0");
    }

    // -- visual arithmetic ---------------------------------------------------------------------

    @Test
    @DisplayName("a GUI-scale step resolves exactly as vanilla's option does")
    void guiScale() {
        assertEquals(4, GuiScaleMath.resolve(0, 1920, 1080, false), "auto at 1080p");
        assertEquals(2, GuiScaleMath.resolve(2, 1920, 1080, false));
        assertEquals(2, GuiScaleMath.resolve(6, 854, 480, false), "clamped to what fits");
        assertEquals(2, GuiScaleMath.resolve(3, 1920, 1080, true), "unicode forces even");
        assertEquals(4, GuiScaleMath.effective(0, 4, 1920, 1080, false), "0 follows Minecraft");
        assertEquals(480, GuiScaleMath.scaled(1920, 4));
        assertEquals(641, GuiScaleMath.scaled(1921, 3));
    }

    @Test
    @DisplayName("fog transforms vanilla's own values and never touches them at vanilla")
    void fog() {
        assertEquals(96f, FogMath.start(VisualConfig.FOG_VANILLA, 96f, 128f));
        assertEquals(112f, FogMath.start(VisualConfig.FOG_REDUCED, 96f, 128f));
        assertTrue(FogMath.start(VisualConfig.FOG_OFF, 96f, 128f) > 10000f);
        assertEquals(128f, FogMath.end(VisualConfig.FOG_REDUCED, 128f));
        assertEquals(0.1f, FogMath.density(VisualConfig.FOG_VANILLA, 0.1f));
        assertEquals(0.025f, FogMath.density(VisualConfig.FOG_REDUCED, 0.1f), 1e-7);
        assertEquals(0f, FogMath.density(VisualConfig.FOG_OFF, 2f));
        // Thick nether fog starts very near; reduced still never moves it closer.
        assertEquals(6.4f, FogMath.start(VisualConfig.FOG_REDUCED, 6.4f, 16f), 1e-6);
    }

    // -- scoreboard -----------------------------------------------------------------------------

    @Test
    @DisplayName("the sidebar is vanilla's: # names dropped, the top fifteen, highest first")
    void scoreboardLines() {
        List<ScoreboardLines.Entry> asc = new ArrayList<ScoreboardLines.Entry>();
        asc.add(new ScoreboardLines.Entry("#hidden", "#hidden", -5));
        for (int i = 1; i <= 18; i++) {
            asc.add(new ScoreboardLines.Entry("p" + i, "§a" + i, i));
        }
        JsonObject o = ScoreboardLines.payload("§e§lTITLE", asc);
        assertTrue(o.get("visible").getAsBoolean());
        assertEquals("§e§lTITLE", o.get("title").getAsString());
        JsonArray lines = o.getAsJsonArray("lines");
        assertEquals(ScoreboardLines.MAX_LINES, lines.size());
        assertEquals(18, lines.get(0).getAsJsonObject().get("score").getAsInt(), "highest first");
        assertEquals("§a4", lines.get(14).getAsJsonObject().get("text").getAsString());
        JsonObject none = ScoreboardLines.hidden();
        assertFalse(none.get("visible").getAsBoolean());
        assertEquals(0, none.getAsJsonArray("lines").size());
    }

    @Test
    @DisplayName("the scoreboard payloads satisfy bridge.json's schema examples' shape")
    void scoreboardMatchesSchemaExample() {
        JsonArray examples = Schemas.examples("bridge.json");
        int seen = 0;
        for (int i = 0; i < examples.size(); i++) {
            JsonObject e = examples.get(i).getAsJsonObject();
            if (!e.has("e") || !"scoreboard".equals(e.get("e").getAsString())) {
                continue;
            }
            JsonObject payload = e.getAsJsonObject("payload");
            List<ScoreboardLines.Entry> asc = new ArrayList<ScoreboardLines.Entry>();
            JsonArray lines = payload.getAsJsonArray("lines");
            for (int j = lines.size() - 1; j >= 0; j--) {
                JsonObject l = lines.get(j).getAsJsonObject();
                asc.add(new ScoreboardLines.Entry("n" + j, l.get("text").getAsString(),
                        l.get("score").getAsInt()));
            }
            JsonObject built = payload.get("visible").getAsBoolean()
                    ? ScoreboardLines.payload(payload.get("title").getAsString(), asc)
                    : ScoreboardLines.hidden();
            assertEquals(payload, built, "rebuilt from its own lines");
            seen++;
        }
        assertEquals(2, seen, "bridge.json carries a live and a hidden board");
    }

    // -- the mirrors -----------------------------------------------------------------------------

    @Test
    @DisplayName("the factory loadout mirrors the registry defaults into both config classes")
    void configMirrorsDefaults() {
        Loadout l = Loadout.defaults("t", "t");
        PerfConfig.apply(l);
        VisualConfig.apply(l);
        assertTrue(PerfConfig.cullingOn && PerfConfig.particlesOn && PerfConfig.frameLimiterOn,
                "the FPS boost ships on");
        assertEquals(16.0, PerfConfig.alwaysDrawWithinSq);
        assertEquals(32.0 * 32.0, PerfConfig.signTextDistanceSq);
        assertEquals(4000, PerfConfig.particleCap);
        assertEquals(30, PerfConfig.unfocusedFps);
        assertFalse(VisualConfig.outlineOn || VisualConfig.fogOn || VisualConfig.menuBlurOn,
                "visual mods ship off");
        assertTrue(VisualConfig.outlineLines && !VisualConfig.outlineFill);
        assertEquals(0xCCEDEEEF, VisualConfig.outlineColor);
        assertEquals(VisualConfig.FOG_REDUCED, VisualConfig.distanceFog);
        assertEquals(VisualConfig.FOG_VANILLA, VisualConfig.lavaFog);
        assertEquals(6000L, VisualConfig.timeTicks);
    }

    @Test
    @DisplayName("changing clear glass bumps the rebuild epoch; an unrelated change does not")
    void glassEpoch() {
        Loadout l = Loadout.defaults("t", "t");
        VisualConfig.apply(l);
        int before = VisualConfig.clearGlassEpoch;
        l.putSetting("clear_glass", "on", new JsonPrimitive(true));
        VisualConfig.apply(l);
        assertEquals(before + 1, VisualConfig.clearGlassEpoch);
        l.putSetting("fog", "on", new JsonPrimitive(true));
        VisualConfig.apply(l);
        assertEquals(before + 1, VisualConfig.clearGlassEpoch);
        l.putSetting("clear_glass", "clear_stained", new JsonPrimitive(true));
        VisualConfig.apply(l);
        assertEquals(before + 2, VisualConfig.clearGlassEpoch);
        l.putSetting("clear_glass", "on", new JsonPrimitive(false));
        VisualConfig.apply(l);
        assertEquals(before + 3, VisualConfig.clearGlassEpoch);
    }
}
