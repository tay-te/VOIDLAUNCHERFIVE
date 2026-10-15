package dev.voidpvp.client.cosmetic;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Trail particles: cheap camera-facing sprites in a fixed pool, emitted while a player moves —
 * density by speed, nothing at all standing still — and drawn once per frame after vanilla's
 * particles, full-bright, with an optional additive bloom.
 *
 * <p>The pool is struct-of-arrays and never allocates after start-up. {@link #POOL} bounds the
 * whole client; each style's {@code cap} bounds one player, so a lobby of sprinting players cannot
 * fill the screen or the frame.</p>
 */
public final class Trails {

    static final int POOL = 640;

    private static final float[] px = new float[POOL], py = new float[POOL], pz = new float[POOL];
    private static final double[] ox = new double[POOL], oy = new double[POOL], oz = new double[POOL];
    private static final float[] vx = new float[POOL], vy = new float[POOL], vz = new float[POOL];
    private static final float[] age = new float[POOL], life = new float[POOL], size = new float[POOL];
    private static final float[] floorY = new float[POOL];
    private static final int[] color = new int[POOL], owner = new int[POOL];
    private static final TrailStyle[] style = new TrailStyle[POOL];
    private static final String[] styleId = new String[POOL];
    private static int live;
    private static final Map<Integer, int[]> COUNTS = new HashMap<Integer, int[]>();
    private static final Random RNG = new Random(7);
    private static long lastNanos;
    private static final float[] MV = new float[16];
    private static final String[] BATCH = new String[16];

    private Trails() {
    }

    /**
     * Emits for one player this frame. {@code x/y/z} is the interpolated feet position; the
     * caller has already decided the player is visible and wears {@code id}.
     */
    static void emit(PlayerFx fx, String id, Entity e, double x, double y, double z, float dt,
                     boolean onGround) {
        TrailStyle s = CosmeticAssets.trail(id);
        if (s == null || dt <= 0) {
            return;
        }
        if (!fx.trailHasLast) {
            fx.trailLastX = x;
            fx.trailLastY = y;
            fx.trailLastZ = z;
            fx.trailHasLast = true;
            return;
        }
        double dx = x - fx.trailLastX, dz = z - fx.trailLastZ;
        fx.trailLastX = x;
        fx.trailLastY = y;
        fx.trailLastZ = z;
        double horiz = Math.sqrt(dx * dx + dz * dz) / dt;
        if (horiz > 40 || horiz < 0.9) {
            // A teleport, or standing still: no trail.
            fx.trailCarry = 0;
            return;
        }
        float density = (float) Math.min(1.25, horiz / 5.6);
        fx.trailCarry += s.rate * density * dt;
        int[] count = COUNTS.get(Integer.valueOf(fx.entityId));
        if (count == null) {
            count = new int[1];
            COUNTS.put(Integer.valueOf(fx.entityId), count);
        }
        double dirX = horiz > 0 ? dx / (horiz * dt) : 0, dirZ = horiz > 0 ? dz / (horiz * dt) : 0;
        while (fx.trailCarry >= 1) {
            fx.trailCarry -= 1;
            if (count[0] >= s.cap || live >= POOL) {
                continue;
            }
            spawn(fx, s, id, x, y, z, dirX, dirZ, (float) horiz, count, onGround);
        }
    }

    private static void spawn(PlayerFx fx, TrailStyle s, String id, double x, double y, double z,
                              double dirX, double dirZ, float speed, int[] count, boolean onGround) {
        int i = live++;
        count[0]++;
        owner[i] = fx.entityId;
        style[i] = s;
        styleId[i] = id;
        age[i] = 0;
        float jitter = (RNG.nextFloat() - 0.5f);
        life[i] = s.life * (0.75f + RNG.nextFloat() * 0.5f);
        color[i] = s.colors[RNG.nextInt(s.colors.length)];
        size[i] = s.size * (0.8f + RNG.nextFloat() * 0.4f);
        // Spawn a little behind the feet, spread across the body's width.
        double sideX = -dirZ, sideZ = dirX;
        double back = 0.25 + RNG.nextFloat() * 0.2;
        ox[i] = x - dirX * back + sideX * jitter * 0.5;
        oz[i] = z - dirZ * back + sideZ * jitter * 0.5;
        oy[i] = y;
        floorY[i] = (float) (onGround ? y : y - 3);
        px[i] = py[i] = pz[i] = 0;
        switch (s.motion) {
            case TrailStyle.DRIFT:
                py[i] = 0.15f + RNG.nextFloat() * 0.9f;
                vx[i] = (RNG.nextFloat() - 0.5f) * 0.3f;
                vz[i] = (RNG.nextFloat() - 0.5f) * 0.3f;
                vy[i] = 0.35f + RNG.nextFloat() * 0.3f;
                break;
            case TrailStyle.SETTLE:
                py[i] = 0.5f + RNG.nextFloat() * 0.8f;
                vx[i] = (RNG.nextFloat() - 0.5f) * 0.15f;
                vz[i] = (RNG.nextFloat() - 0.5f) * 0.15f;
                vy[i] = -0.35f - RNG.nextFloat() * 0.2f;
                break;
            case TrailStyle.STAIR:
                fx.stairStep = (fx.stairStep + 1) % 5;
                py[i] = 0.06f + fx.stairStep * 0.0625f;
                vx[i] = vz[i] = 0;
                vy[i] = 0.5f;
                break;
            case TrailStyle.SPARK:
            default:
                py[i] = 0.1f + RNG.nextFloat() * 0.5f;
                vx[i] = (float) (-dirX * 0.4 + (RNG.nextFloat() - 0.5f) * 0.25f);
                vz[i] = (float) (-dirZ * 0.4 + (RNG.nextFloat() - 0.5f) * 0.25f);
                vy[i] = 0.12f + RNG.nextFloat() * 0.2f;
                break;
        }
        if (speed > 7) {
            vy[i] *= 0.7f;
        }
    }

    private static void kill(int i) {
        int[] count = COUNTS.get(Integer.valueOf(owner[i]));
        if (count != null && count[0] > 0) {
            count[0]--;
        }
        int last = --live;
        if (i != last) {
            px[i] = px[last];
            py[i] = py[last];
            pz[i] = pz[last];
            ox[i] = ox[last];
            oy[i] = oy[last];
            oz[i] = oz[last];
            vx[i] = vx[last];
            vy[i] = vy[last];
            vz[i] = vz[last];
            age[i] = age[last];
            life[i] = life[last];
            size[i] = size[last];
            floorY[i] = floorY[last];
            color[i] = color[last];
            owner[i] = owner[last];
            style[i] = style[last];
            styleId[i] = styleId[last];
        }
        style[last] = null;
        styleId[last] = null;
    }

    private static void step(float dt) {
        for (int i = live - 1; i >= 0; i--) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                kill(i);
                continue;
            }
            TrailStyle s = style[i];
            float t = age[i];
            switch (s.motion) {
                case TrailStyle.DRIFT:
                    vy[i] *= (float) Math.pow(0.6, dt);
                    vx[i] += (float) Math.sin(t * 5.0 + i) * 0.4f * dt;
                    break;
                case TrailStyle.SETTLE:
                    vx[i] += (float) Math.sin(t * 3.0 + i) * 0.25f * dt;
                    break;
                case TrailStyle.STAIR:
                    break;
                default:
                    vx[i] *= (float) Math.pow(0.2, dt);
                    vz[i] *= (float) Math.pow(0.2, dt);
                    break;
            }
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
            if (s.motion == TrailStyle.SETTLE && oy[i] + py[i] < floorY[i] + 0.02) {
                py[i] = (float) (floorY[i] + 0.02 - oy[i]);
                vx[i] = vy[i] = vz[i] = 0;
            }
        }
    }

    /** Forgets everything (world change). */
    public static void clear() {
        for (int i = 0; i < live; i++) {
            style[i] = null;
            styleId[i] = null;
        }
        live = 0;
        COUNTS.clear();
    }

    public static int live() {
        return live;
    }

    /**
     * Steps and draws every particle. Called right after vanilla's particles, with the camera
     * transform applied and the world origin at the camera ({@code camX/Y/Z}).
     */
    public static void render(double camX, double camY, double camZ, boolean glow) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        step(dt);
        if (live == 0) {
            return;
        }
        Gl.view(MV);
        // Camera right and up in world space: the rows of the modelview's rotation.
        float rx = MV[0], ry = MV[4], rz = MV[8];
        float ux = MV[1], uy = MV[5], uz = MV[9];

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003f);
        Gl.fullBright();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();

        // One batch per sprite texture: styles are few, so find each distinct one with a scan.
        int styles = 0;
        for (int i = 0; i < live && styles < BATCH.length; i++) {
            boolean seen = false;
            for (int k = 0; k < styles; k++) {
                if (BATCH[k].equals(styleId[i])) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                BATCH[styles++] = styleId[i];
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < styles; k++) {
                String id = BATCH[k];
                TrailStyle style0 = null;
                for (int i = 0; i < live; i++) {
                    if (id.equals(styleId[i])) {
                        style0 = style[i];
                        break;
                    }
                }
                if (style0 == null) {
                    continue;
                }
                if (pass == 0) {
                    CosmeticAssets.Tex tex = CosmeticAssets.texture(id, "sprite.png", 1, 0);
                    if (tex == null) {
                        continue;
                    }
                    GlStateManager.bindTexture(tex.glId);
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else {
                    if (!glow || style0.glow <= 0) {
                        continue;
                    }
                    GlStateManager.bindTexture(Gl.bloomTexture());
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                }
                buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (int i = 0; i < live; i++) {
                    if (!id.equals(styleId[i])) {
                        continue;
                    }
                    TrailStyle s = style[i];
                    float f = age[i] / life[i];
                    float alpha = Math.min(1, f * 8) * (1 - f * f);
                    float sz = size[i];
                    if (s.motion == TrailStyle.SPARK) {
                        sz *= 1 - f * 0.75f;
                    } else if (s.motion == TrailStyle.DRIFT) {
                        sz *= 1 - f * 0.5f;
                    }
                    if (pass == 1) {
                        sz *= 3.2f;
                        alpha *= s.glow * 0.5f;
                    }
                    float x = (float) (ox[i] + px[i] - camX), y = (float) (oy[i] + py[i] - camY), z = (float) (oz[i] + pz[i] - camZ);
                    if (s.motion == TrailStyle.STAIR) {
                        // Climb in whole pixels: the position snaps to a 1/16-block grid.
                        y = (float) (Math.floor((oy[i] + py[i]) * 16) / 16 - camY);
                    }
                    int c = color[i];
                    float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
                    float h = sz * 0.5f;
                    buf.vertex(x - rx * h - ux * h, y - ry * h - uy * h, z - rz * h - uz * h).texture(0, 1).color(r, g, b, alpha).next();
                    buf.vertex(x + rx * h - ux * h, y + ry * h - uy * h, z + rz * h - uz * h).texture(1, 1).color(r, g, b, alpha).next();
                    buf.vertex(x + rx * h + ux * h, y + ry * h + uy * h, z + rz * h + uz * h).texture(1, 0).color(r, g, b, alpha).next();
                    buf.vertex(x - rx * h + ux * h, y - ry * h + uy * h, z - rz * h + uz * h).texture(0, 0).color(r, g, b, alpha).next();
                }
                t.draw();
            }
        }
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.color(1, 1, 1, 1);
    }
}
