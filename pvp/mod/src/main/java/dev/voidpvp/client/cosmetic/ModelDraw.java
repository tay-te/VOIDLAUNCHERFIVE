package dev.voidpvp.client.cosmetic;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a {@link CosmeticModel}: one display list per bone, compiled on first use and kept for
 * the session — the same thing {@code ModelPart} does, so a hat costs what a skin layer costs.
 * Bone transforms (rest + pose) are applied with fixed-function calls around each list, and are
 * mirrored in Java ({@link Mat4}) only for bones carrying glow anchors.
 *
 * <p>Layers, in order: albedo (lit like the skin), tint (the tint mask multiplied by the slot's
 * colour, blended over), emissive (full-bright, additive). The emissive pass is why the items
 * glow at night without looking like they have a light source on them by day.</p>
 */
final class ModelDraw {

    private static final Map<CosmeticModel, int[]> LISTS = new HashMap<CosmeticModel, int[]>();
    private static final float RAD = 57.29578f;
    /** Scratch for {@link #glow}; render thread only. */
    private static final float[] P = new float[3];
    private static final int[] CHAIN = new int[16];

    private ModelDraw() {
    }

    private static int[] lists(CosmeticModel m) {
        int[] l = LISTS.get(m);
        if (l != null) {
            return l;
        }
        l = new int[m.bones.length];
        int base = GL11.glGenLists(m.bones.length);
        for (int i = 0; i < m.bones.length; i++) {
            l[i] = base + i;
            GL11.glNewList(l[i], GL11.GL_COMPILE);
            emit(m, m.bones[i]);
            GL11.glEndList();
        }
        LISTS.put(m, l);
        return l;
    }

    /** Emits a bone's cubes, positions in model pixels, UVs normalised to one frame. */
    private static void emit(CosmeticModel m, CosmeticModel.Bone b) {
        if (b.cubes.length == 0) {
            return;
        }
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_NORMAL);
        float iw = 1f / m.texW, ih = 1f / m.texH;
        for (CosmeticModel.Cube c : b.cubes) {
            for (int f = 0; f < 6; f++) {
                float[] r = c.uv[f];
                if (r == null) {
                    continue;
                }
                float u0 = r[0] * iw, v0 = r[1] * ih, u1 = r[2] * iw, v1 = r[3] * ih;
                // Each face: four corners in order (u0,v0) (u1,v0) (u1,v1) (u0,v1), following the
                // conventions documented on Atlas in mod/cosmetics-art/lib.mjs.
                switch (f) {
                    case 0: // north, -z: u +x, v +y
                        quad(buf, c.x0, c.y0, c.z0, c.x1, c.y0, c.z0, c.x1, c.y1, c.z0, c.x0, c.y1, c.z0, u0, v0, u1, v1, 0, 0, -1);
                        break;
                    case 1: // south, +z: u -x, v +y
                        quad(buf, c.x1, c.y0, c.z1, c.x0, c.y0, c.z1, c.x0, c.y1, c.z1, c.x1, c.y1, c.z1, u0, v0, u1, v1, 0, 0, 1);
                        break;
                    case 2: // east, +x: u +z, v +y
                        quad(buf, c.x1, c.y0, c.z0, c.x1, c.y0, c.z1, c.x1, c.y1, c.z1, c.x1, c.y1, c.z0, u0, v0, u1, v1, 1, 0, 0);
                        break;
                    case 3: // west, -x: u -z, v +y
                        quad(buf, c.x0, c.y0, c.z1, c.x0, c.y0, c.z0, c.x0, c.y1, c.z0, c.x0, c.y1, c.z1, u0, v0, u1, v1, -1, 0, 0);
                        break;
                    case 4: // up, -y: u +x, v -z
                        quad(buf, c.x0, c.y0, c.z1, c.x1, c.y0, c.z1, c.x1, c.y0, c.z0, c.x0, c.y0, c.z0, u0, v0, u1, v1, 0, -1, 0);
                        break;
                    default: // down, +y: u +x, v -z
                        quad(buf, c.x0, c.y1, c.z1, c.x1, c.y1, c.z1, c.x1, c.y1, c.z0, c.x0, c.y1, c.z0, u0, v0, u1, v1, 0, 1, 0);
                        break;
                }
            }
        }
        t.draw();
    }

    private static void quad(BufferBuilder buf,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        buf.vertex(ax, ay, az).texture(u0, v0).normal(nx, ny, nz).next();
        buf.vertex(dx, dy, dz).texture(u0, v1).normal(nx, ny, nz).next();
        buf.vertex(cx, cy, cz).texture(u1, v1).normal(nx, ny, nz).next();
        buf.vertex(bx, by, bz).texture(u1, v0).normal(nx, ny, nz).next();
    }

    /**
     * Draws every bone with {@code pose}. The current GL matrix must already be in the model's
     * pixel space (attachment applied, scaled by 1/16).
     */
    static void draw(CosmeticModel m, CosmeticModel.Pose pose) {
        int[] l = lists(m);
        for (int root : m.roots) {
            drawBone(m, l, pose, root);
        }
    }

    private static void drawBone(CosmeticModel m, int[] l, CosmeticModel.Pose pose, int i) {
        CosmeticModel.Bone b = m.bones[i];
        int k = i * 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(b.px + pose.pos[k], b.py + pose.pos[k + 1], b.pz + pose.pos[k + 2]);
        float rz = b.rz + pose.rot[k + 2], ry = b.ry + pose.rot[k + 1], rx = b.rx + pose.rot[k];
        if (rz != 0) {
            GlStateManager.rotate(rz * RAD, 0, 0, 1);
        }
        if (ry != 0) {
            GlStateManager.rotate(ry * RAD, 0, 1, 0);
        }
        if (rx != 0) {
            GlStateManager.rotate(rx * RAD, 1, 0, 0);
        }
        if (pose.scale[k] != 1 || pose.scale[k + 1] != 1 || pose.scale[k + 2] != 1) {
            GlStateManager.scale(pose.scale[k], pose.scale[k + 1], pose.scale[k + 2]);
        }
        GlStateManager.translate(-b.px, -b.py, -b.pz);
        if (b.cubes.length > 0) {
            GlStateManager.callList(l[i]);
        }
        for (int c : b.children) {
            drawBone(m, l, pose, c);
        }
        GlStateManager.popMatrix();
    }

    /** Follows {@link #drawBone}'s transform in Java to put glow anchors in eye space. */
    static void glow(CosmeticModel m, CosmeticModel.Pose pose, float[] modelview, GlowBatch out,
                     float strength, float[] scratch) {
        if (m.glow.length == 0 || strength <= 0) {
            return;
        }
        float[] p = P;
        int[] chain = CHAIN;
        for (CosmeticModel.Glow g : m.glow) {
            // Chain of bones from the root down to the anchor's bone.
            int depth = 0;
            for (int b = g.bone; b >= 0 && depth < 16; b = m.bones[b].parent) {
                chain[depth++] = b;
            }
            Mat4.copy(modelview, scratch);
            for (int d = depth - 1; d >= 0; d--) {
                CosmeticModel.Bone b = m.bones[chain[d]];
                int k = chain[d] * 3;
                Mat4.translate(scratch, b.px + pose.pos[k], b.py + pose.pos[k + 1], b.pz + pose.pos[k + 2]);
                Mat4.rotate(scratch, b.rz + pose.rot[k + 2], 0, 0, 1);
                Mat4.rotate(scratch, b.ry + pose.rot[k + 1], 0, 1, 0);
                Mat4.rotate(scratch, b.rx + pose.rot[k], 1, 0, 0);
                Mat4.scale(scratch, pose.scale[k], pose.scale[k + 1], pose.scale[k + 2]);
                Mat4.translate(scratch, -b.px, -b.py, -b.pz);
            }
            Mat4.transform(scratch, g.x, g.y, g.z, p);
            float half = g.size * Mat4.scaleOf(scratch);
            out.add(p[0], p[1], p[2], half, g.rgb, g.strength * strength);
        }
    }
}
