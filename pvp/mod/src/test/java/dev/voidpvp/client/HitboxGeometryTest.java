package dev.voidpvp.client;

import dev.voidpvp.client.render.HitboxGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hitbox mod's arithmetic.
 *
 * <p>This is the half of the mod that can be wrong while looking right: a box in the wrong place
 * still renders, and a mis-typed corner still draws twelve lines. So the assertions below are
 * mostly <em>invariants</em> — every corner used three times, every edge axis-aligned — rather
 * than twelve hand-written segments, because a hand-written expectation transcribed from the
 * same typo agrees with it.</p>
 */
class HitboxGeometryTest {

    @Test
    @DisplayName("a box is rebased from world space into the render pass's camera space")
    void rebaseMovesTheBoxByTheInterpolationDelta() {
        // A player standing at world x=100 whose interpolated render position is 0.5 further on:
        // the box has to move with it, or it lags the entity it is drawn around.
        HitboxGeometry.Aabb b = HitboxGeometry.rebase(
                99.7, 64.0, 199.7, 100.3, 65.8, 200.3,
                100.0, 64.0, 200.0,
                0.5, 1.0, -2.0);

        assertEquals(0.2, b.minX, 1e-9);
        assertEquals(1.0, b.minY, 1e-9);
        assertEquals(-2.3, b.minZ, 1e-9);
        assertEquals(0.8, b.maxX, 1e-9);
        assertEquals(2.8, b.maxY, 1e-9);
        assertEquals(-1.7, b.maxZ, 1e-9);
    }

    @Test
    @DisplayName("rebasing preserves the box's size, whatever the camera is doing")
    void rebaseIsATranslation() {
        HitboxGeometry.Aabb b = HitboxGeometry.rebase(
                -0.3, 0, -0.3, 0.3, 1.8, 0.3,
                12.5, 70.0, -400.25,
                -3.75, 2.5, 18.0);
        assertEquals(0.6, b.maxX - b.minX, 1e-9);
        assertEquals(1.8, b.maxY - b.minY, 1e-9);
        assertEquals(0.6, b.maxZ - b.minZ, 1e-9);
    }

    @Test
    @DisplayName("a box is twelve edges, and every one of them is axis-aligned")
    void edgesAreTwelveAxisAlignedSegments() {
        HitboxGeometry.Aabb b = new HitboxGeometry.Aabb(0, 0, 0, 1, 2, 3);
        List<HitboxGeometry.Edge> edges = HitboxGeometry.edges(b);

        assertEquals(12, edges.size(), "a cuboid has twelve edges");
        for (HitboxGeometry.Edge e : edges) {
            int moving = 0;
            if (e.x1 != e.x2) {
                moving++;
                assertEquals(1.0, Math.abs(e.x2 - e.x1), 1e-9, "x edges span the box's width");
            }
            if (e.y1 != e.y2) {
                moving++;
                assertEquals(2.0, Math.abs(e.y2 - e.y1), 1e-9, "y edges span the box's height");
            }
            if (e.z1 != e.z2) {
                moving++;
                assertEquals(3.0, Math.abs(e.z2 - e.z1), 1e-9, "z edges span the box's depth");
            }
            assertEquals(1, moving, "an edge of an axis-aligned box moves along exactly one axis");
        }
    }

    @Test
    @DisplayName("the twelve edges touch all eight corners, three times each")
    void edgesCoverEveryCornerExactlyThreeTimes() {
        // The invariant that catches a transcription slip: a cube's every vertex has degree 3.
        // A duplicated or missing edge breaks this even when the count is still twelve.
        HitboxGeometry.Aabb b = new HitboxGeometry.Aabb(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
        Map<String, Integer> degree = new HashMap<String, Integer>();
        for (HitboxGeometry.Edge e : HitboxGeometry.edges(b)) {
            bump(degree, e.x1, e.y1, e.z1);
            bump(degree, e.x2, e.y2, e.z2);
        }
        assertEquals(8, degree.size(), "a cuboid has eight corners");
        for (Map.Entry<String, Integer> entry : degree.entrySet()) {
            assertEquals(3, entry.getValue().intValue(),
                    "corner " + entry.getKey() + " should meet three edges");
        }
    }

    @Test
    @DisplayName("every corner of the box is one the box actually has")
    void edgesUseOnlyTheBoxesOwnCoordinates() {
        HitboxGeometry.Aabb b = new HitboxGeometry.Aabb(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
        for (HitboxGeometry.Edge e : HitboxGeometry.edges(b)) {
            assertTrue(isMinOrMax(e.x1, b.minX, b.maxX) && isMinOrMax(e.x2, b.minX, b.maxX));
            assertTrue(isMinOrMax(e.y1, b.minY, b.maxY) && isMinOrMax(e.y2, b.minY, b.maxY));
            assertTrue(isMinOrMax(e.z1, b.minZ, b.maxZ) && isMinOrMax(e.z2, b.minZ, b.maxZ));
        }
    }

    @Test
    @DisplayName("the eye line starts at the eyes and runs the way the entity is looking")
    void eyeLineStartsAtEyeHeightAndFollowsTheLookVector() {
        // Looking due south (+Z) from a player 1.62 above their feet.
        HitboxGeometry.Edge eye = HitboxGeometry.eyeLine(2.0, 3.0, 4.0, 1.62, 0, 0, 1);

        assertEquals(2.0, eye.x1, 1e-9);
        assertEquals(4.62, eye.y1, 1e-9, "the ray leaves from the eye, not from the feet");
        assertEquals(4.0, eye.z1, 1e-9);
        assertEquals(4.0 + HitboxGeometry.EYE_LINE_LENGTH, eye.z2, 1e-9);
        assertEquals(4.62, eye.y2, 1e-9, "a level look stays level");
    }

    @Test
    @DisplayName("the eye line is EYE_LINE_LENGTH long for any look direction")
    void eyeLineHasAConstantLength() {
        // A unit look vector pointing up-and-away; the ray must not get longer because the
        // player looked diagonally.
        double k = 1.0 / Math.sqrt(3.0);
        HitboxGeometry.Edge eye = HitboxGeometry.eyeLine(0, 0, 0, 1.62, k, k, k);
        double len = Math.sqrt(sq(eye.x2 - eye.x1) + sq(eye.y2 - eye.y1) + sq(eye.z2 - eye.z1));
        assertEquals(HitboxGeometry.EYE_LINE_LENGTH, len, 1e-9);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static boolean isMinOrMax(double v, double min, double max) {
        return Math.abs(v - min) < 1e-9 || Math.abs(v - max) < 1e-9;
    }

    private static void bump(Map<String, Integer> counts, double x, double y, double z) {
        String key = x + "/" + y + "/" + z;
        Integer at = counts.get(key);
        counts.put(key, Integer.valueOf(at == null ? 1 : at.intValue() + 1));
    }
}
