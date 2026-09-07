package dev.voidpvp.client.render;

import java.util.ArrayList;
import java.util.List;

/**
 * The hitbox mod's shape, in camera-relative world units.
 *
 * <p>Split from {@link HitboxRenderer} for the same reason {@link CrosshairGeometry} is split
 * from {@link CrosshairRenderer}: a Mixin cannot be unit-tested and neither can a GL call, so
 * everything that can be arithmetic is arithmetic and lives here, and the renderer only pushes
 * the vertices it is handed.</p>
 *
 * <p><b>The coordinate space is the one the entity render pass is already in.</b> By the time
 * {@code EntityRenderDispatcher.renderHitbox} runs, the modelview has been set up so the camera
 * sits at the origin, and the {@code x/y/z} it is passed are the entity's <em>interpolated</em>
 * position in that space. The entity's own {@code getBoundingBox()} is in absolute world
 * coordinates, so the box has to be rebased: subtract the entity's untick-interpolated position
 * and add the interpolated one. Vanilla does exactly this, and getting it wrong is the failure
 * that looks right in code — the box renders, it is simply in the wrong place, and it lags the
 * entity by up to one tick of movement while looking perfectly solid.</p>
 */
public final class HitboxGeometry {

    /** One line segment, camera-relative. */
    public static final class Edge {
        public final double x1;
        public final double y1;
        public final double z1;
        public final double x2;
        public final double y2;
        public final double z2;

        Edge(double x1, double y1, double z1, double x2, double y2, double z2) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
        }

        @Override
        public String toString() {
            return "(" + x1 + "," + y1 + "," + z1 + " -> " + x2 + "," + y2 + "," + z2 + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Edge)) {
                return false;
            }
            Edge e = (Edge) o;
            return Double.compare(x1, e.x1) == 0 && Double.compare(y1, e.y1) == 0
                    && Double.compare(z1, e.z1) == 0 && Double.compare(x2, e.x2) == 0
                    && Double.compare(y2, e.y2) == 0 && Double.compare(z2, e.z2) == 0;
        }

        @Override
        public int hashCode() {
            return (int) (Double.doubleToLongBits(x1) ^ Double.doubleToLongBits(y2));
        }
    }

    /** An axis-aligned box, already rebased into camera space. */
    public static final class Aabb {
        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        public Aabb(double minX, double minY, double minZ,
                    double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        @Override
        public String toString() {
            return "Aabb[" + minX + "," + minY + "," + minZ
                    + " .. " + maxX + "," + maxY + "," + maxZ + "]";
        }
    }

    private HitboxGeometry() {
    }

    /**
     * Rebases an entity's world-space bounding box into the camera space the render pass uses.
     *
     * @param entityX  the entity's own {@code x}, i.e. where its box is measured from
     * @param renderX  the interpolated {@code x} the render pass was handed
     */
    public static Aabb rebase(double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ,
                              double entityX, double entityY, double entityZ,
                              double renderX, double renderY, double renderZ) {
        double dx = renderX - entityX;
        double dy = renderY - entityY;
        double dz = renderZ - entityZ;
        return new Aabb(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /**
     * The twelve edges of a box, as line segments.
     *
     * <p>Twelve segments rather than a line loop: a loop cannot describe a cube without
     * retracing edges, and a retraced edge is drawn twice, which is visible the moment the
     * colour has any alpha below 1 — the doubled edges come out darker than the rest.</p>
     */
    public static List<Edge> edges(Aabb b) {
        List<Edge> out = new ArrayList<Edge>(12);
        // Bottom face.
        out.add(new Edge(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ));
        out.add(new Edge(b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ));
        out.add(new Edge(b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ));
        out.add(new Edge(b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ));
        // Top face.
        out.add(new Edge(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ));
        out.add(new Edge(b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ));
        out.add(new Edge(b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ));
        out.add(new Edge(b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ));
        // The four uprights.
        out.add(new Edge(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ));
        out.add(new Edge(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ));
        out.add(new Edge(b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ));
        out.add(new Edge(b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ));
        return out;
    }

    /** How far the {@code show_eye_line} ray is drawn, in blocks. Vanilla uses 2. */
    public static final double EYE_LINE_LENGTH = 2.0;

    /**
     * The {@code show_eye_line} ray: from the entity's eyes along the way it is looking.
     *
     * <p>Its origin is the eye, not the centre of the box — the line answers "what is this
     * player aiming at", and a line from the navel answers a different question.</p>
     *
     * @param lookX unit look vector, as {@code Entity.getRotationVector(tickDelta)} gives it
     */
    public static Edge eyeLine(double renderX, double renderY, double renderZ, double eyeHeight,
                               double lookX, double lookY, double lookZ) {
        double ox = renderX;
        double oy = renderY + eyeHeight;
        double oz = renderZ;
        return new Edge(ox, oy, oz,
                ox + lookX * EYE_LINE_LENGTH,
                oy + lookY * EYE_LINE_LENGTH,
                oz + lookZ * EYE_LINE_LENGTH);
    }
}
