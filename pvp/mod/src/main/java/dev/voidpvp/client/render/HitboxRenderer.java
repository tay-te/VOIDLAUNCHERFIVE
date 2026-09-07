package dev.voidpvp.client.render;

import dev.voidpvp.client.state.LiveState;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws the hitbox mod's boxes, in place of the vanilla debug pass.
 *
 * <p>The mod used to switch vanilla's own {@code renderHitboxes} flag on and leave the drawing
 * to it, which worked — and left {@code line_width}, {@code color} and {@code show_eye_line}
 * describing a renderer that did not exist, because vanilla's has no such knobs. This is that
 * renderer. It is deliberately the same shape vanilla draws (the entity's own bounding box, plus
 * the look ray) so that turning the settings to their defaults gives back what vanilla gave.</p>
 *
 * <p><b>It reads what the client already knows and draws it.</b> Nothing here consults or alters
 * hit detection, reach, or anything the client reports to a server — the box is drawn from
 * {@code Entity.getBoundingBox()}, which is the same value the client had already computed to
 * render the entity at all.</p>
 *
 * <p>GL discipline as {@link GlBlit}: talk to LWJGL directly, but bracket everything with
 * {@code glPushAttrib}/{@code glPopAttrib} so Minecraft's {@code GlStateManager} cache is not
 * desynced by state left behind.</p>
 */
public final class HitboxRenderer {

    /** Everything this touches, so the pop restores the game exactly. */
    private static final int ATTRIB_MASK = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT
            | GL11.GL_TEXTURE_BIT;

    private HitboxRenderer() {
    }

    /**
     * Draws one entity's hitbox.
     *
     * @param box      the entity's bounding box, already rebased into camera space
     * @param eye      the look ray, or {@code null} when {@code show_eye_line} is off
     */
    public static void draw(LiveState state, HitboxGeometry.Aabb box,
                            HitboxGeometry.Edge eye) {
        if (!state.hitboxesOn) {
            return;
        }
        int argb = state.hitboxColor;
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        GL11.glPushAttrib(ATTRIB_MASK);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            // Depth test stays ON and the mask stays OFF: the box is occluded by the world the
            // way vanilla's is, so a hitbox through a wall is not something this mod hands you.
            // Writing depth would let the lines occlude the entity they are drawn around.
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            // The schema bounds are 0.5-5; clamping again here means a state field written by
            // some future path that skipped the registry still cannot ask GL for a width of 0.
            GL11.glLineWidth(Math.max(0.5f, Math.min(5f, state.hitboxLineWidth)));
            GL11.glColor4f(r, g, b, a);

            GL11.glBegin(GL11.GL_LINES);
            List<HitboxGeometry.Edge> edges = HitboxGeometry.edges(box);
            for (int i = 0; i < edges.size(); i++) {
                HitboxGeometry.Edge e = edges.get(i);
                GL11.glVertex3d(e.x1, e.y1, e.z1);
                GL11.glVertex3d(e.x2, e.y2, e.z2);
            }
            if (eye != null) {
                GL11.glVertex3d(eye.x1, eye.y1, eye.z1);
                GL11.glVertex3d(eye.x2, eye.y2, eye.z2);
            }
            GL11.glEnd();
        } finally {
            // glPopAttrib restores colour, blend, depth mask, line width and the enables.
            GL11.glPopAttrib();
        }
    }
}
