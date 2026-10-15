package dev.voidpvp.client.cosmetic;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * Hangs a {@link Cloth} from a player's shoulders and draws it as a two-sided, one-pixel-thick
 * sheet with the vanilla cape texture layout: the outer face (seen from behind) at (1,1), the
 * lining at (12,1), and one-pixel edge strips round the border.
 *
 * <p>The cloth simulates in world space ({@link Cloth}); every frame this class pins its top row
 * to the torso, collides it against the torso and both legs in their posed, possibly tilted
 * frames, and converts it back into model space to draw — so it is drawn inside the player's own
 * transform and lit, fogged and depth-sorted exactly like the skin.</p>
 */
final class CapeRenderer {

    /** Cloth grids: the wearer's own cape and close players get one quad per texel. */
    static final int HI_COLS = 10, HI_ROWS = 16, LO_COLS = 5, LO_ROWS = 8;

    private final double[] tmp = new double[3];
    private final double[] tmp2 = new double[3];
    final PartFrame body = new PartFrame();
    final PartFrame rightLeg = new PartFrame();
    final PartFrame leftLeg = new PartFrame();
    final BodyFrame frame = new BodyFrame();
    /** Extra clearance when a chestplate is worn: the armour layer is a pixel proud of the torso. */
    double armour;

    /** Builds a cloth for a cape style at a grid resolution. */
    static Cloth newCloth(boolean hi, CapeStyle style) {
        int cols = hi ? HI_COLS : LO_COLS, rows = hi ? HI_ROWS : LO_ROWS;
        double spacingBlocks = 16.0 / rows / 16.0 * 0.9375;
        Cloth c = new Cloth(cols, rows, spacingBlocks, style == null ? 0.9 : style.stiffness);
        c.iterations = hi ? 4 : 3;
        if (style != null) {
            c.gravity = 16.0 * style.weight;
            c.drag = 7.0 / Math.max(0.5, style.weight);
        }
        return c;
    }

    // ------------------------------------------------------------------ anchors + collision

    /** The top row's anchor for column {@code c} of {@code cols}, in body-local pixels. */
    void anchorLocal(int c, int cols, double[] out) {
        out[0] = 5.0 - 10.0 * c / cols;
        out[1] = 0.25;
        out[2] = 2.35 + armour;
    }

    /** World positions of the top row this frame. */
    void anchors(Cloth cloth, double[] ax, double[] ay, double[] az) {
        for (int c = 0; c <= cloth.cols; c++) {
            anchorLocal(c, cloth.cols, tmp);
            body.toModel(tmp[0], tmp[1], tmp[2], tmp2);
            frame.toWorld(tmp2[0], tmp2[1], tmp2[2], tmp);
            ax[c] = tmp[0];
            ay[c] = tmp[1];
            az[c] = tmp[2];
        }
    }

    /** The world-space "behind the player" direction, for hanging a fresh cloth. */
    void back(double[] out) {
        frame.dirToWorld(0, 0, 1, out);
    }

    final Cloth.Collider collider = new Cloth.Collider() {
        private final double[] m = new double[3];
        private final double[] l = new double[3];

        @Override
        public void collide(double[] xs, double[] ys, double[] zs, int i) {
            frame.toModel(xs[i], ys[i], zs[i], m);
            boolean moved = pushOut(body, -4.6 - armour, 4.6 + armour, -1.0, 12.4, -2.6 - armour, 2.6 + armour, m, l, true);
            // Legs only matter below the hips: most of a cape never gets there.
            if (m[1] > 8) {
                moved |= pushOut(rightLeg, -2.5, 2.5, -0.5, 12.5, -2.6, 2.6, m, l, false);
                moved |= pushOut(leftLeg, -2.5, 2.5, -0.5, 12.5, -2.6, 2.6, m, l, false);
            }
            if (moved) {
                frame.toWorld(m[0], m[1], m[2], l);
                xs[i] = l[0];
                ys[i] = l[1];
                zs[i] = l[2];
            }
        }
    };

    /**
     * Pushes a model-space point out of a part's box along the cheapest face. The torso prefers
     * its back face (a cape is behind the player), the legs prefer front/back, never up.
     */
    private static boolean pushOut(PartFrame p, double x0, double x1, double y0, double y1,
                                   double z0, double z1, double[] m, double[] l, boolean preferBack) {
        p.toLocal(m[0], m[1], m[2], l);
        if (l[0] <= x0 || l[0] >= x1 || l[1] <= y0 || l[1] >= y1 || l[2] <= z0 || l[2] >= z1) {
            return false;
        }
        double dBack = z1 - l[2], dFront = l[2] - z0, dL = l[0] - x0, dR = x1 - l[0];
        // The torso prefers its back face, but only for a point already in the back half: a
        // point near the front (a cape swung over a bowing player's shoulders) goes out the
        // front, not through the body and up out of the back.
        if (preferBack && l[2] > -0.5) {
            dBack *= 0.45;
        }
        double best = Math.min(Math.min(dBack, dFront), Math.min(dL, dR));
        if (best == dBack) {
            l[2] = z1;
        } else if (best == dFront) {
            l[2] = z0;
        } else if (best == dL) {
            l[0] = x0;
        } else {
            l[0] = x1;
        }
        p.toModel(l[0], l[1], l[2], m);
        return true;
    }

    // -------------------------------------------------------------------------- drawing

    private double[] mx = new double[0], my = new double[0], mz = new double[0];
    private double[] nx = new double[0], ny = new double[0], nz = new double[0];

    /** Converts the cloth into model pixels and per-vertex normals, ready to draw. */
    void prepare(Cloth cloth) {
        int n = (cloth.cols + 1) * (cloth.rows + 1);
        if (mx.length < n) {
            mx = new double[n];
            my = new double[n];
            mz = new double[n];
            nx = new double[n];
            ny = new double[n];
            nz = new double[n];
        }
        for (int i = 0; i < n; i++) {
            frame.toModel(cloth.rx(i), cloth.ry(i), cloth.rz(i), tmp);
            mx[i] = tmp[0];
            my[i] = tmp[1];
            mz[i] = tmp[2];
        }
        int w = cloth.cols + 1;
        for (int r = 0; r <= cloth.rows; r++) {
            for (int c = 0; c <= cloth.cols; c++) {
                int i = r * w + c;
                int l = r * w + Math.max(0, c - 1), rr = r * w + Math.min(cloth.cols, c + 1);
                int u = Math.max(0, r - 1) * w + c, d = Math.min(cloth.rows, r + 1) * w + c;
                // Columns run toward model -x, rows toward +y (down); outward (+z, away from the
                // body) is h x g with h = left - right (+x) and g = down - up (+y).
                double hx = mx[l] - mx[rr], hy = my[l] - my[rr], hz = mz[l] - mz[rr];
                double gx = mx[d] - mx[u], gy = my[d] - my[u], gz = mz[d] - mz[u];
                double ox = hy * gz - hz * gy, oy = hz * gx - hx * gz, oz = hx * gy - hy * gx;
                double len = Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (len < 1e-9) {
                    ox = 0;
                    oy = 0;
                    oz = 1;
                    len = 1;
                }
                nx[i] = ox / len;
                ny[i] = oy / len;
                nz[i] = oz / len;
            }
        }
    }

    /** Model-pixel position of a point on the outer face at cape pixel (u, v), into out. */
    void pointOnOuter(Cloth cloth, double u, double v, double[] out) {
        double fc = Math.max(0, Math.min(cloth.cols, u / 10.0 * cloth.cols));
        double fr = Math.max(0, Math.min(cloth.rows, v / 16.0 * cloth.rows));
        int c = Math.min(cloth.cols - 1, (int) fc), r = Math.min(cloth.rows - 1, (int) fr);
        double a = fc - c, b = fr - r;
        int w = cloth.cols + 1;
        int i00 = r * w + c, i10 = i00 + 1, i01 = i00 + w, i11 = i01 + 1;
        for (int k = 0; k < 3; k++) {
            double[] arr = k == 0 ? mx : k == 1 ? my : mz;
            double[] nrm = k == 0 ? nx : k == 1 ? ny : nz;
            double p = (arr[i00] * (1 - a) + arr[i10] * a) * (1 - b) + (arr[i01] * (1 - a) + arr[i11] * a) * b;
            double q = (nrm[i00] * (1 - a) + nrm[i10] * a) * (1 - b) + (nrm[i01] * (1 - a) + nrm[i11] * a) * b;
            out[k] = p + q * 0.6;
        }
    }

    // Packed vertex data, 6 ints per vertex (3 floats position, 2 floats uv, 3 normal bytes and a
    // pad), built once per frame by mesh() and uploaded once by upload(); the albedo, tint and
    // emissive passes all draw from the same vertex buffer.
    private int[] outer;
    private int[] rest;

    /** Builds this frame's mesh from the prepared model-space sheet. */
    void mesh(Cloth cloth) {
        int w = cloth.cols + 1;
        double half = 0.5;
        int quads = cloth.cols * cloth.rows;
        // Exactly sized and owned by the cloth, so a frame drawing capes of two resolutions
        // allocates nothing.
        if (cloth.meshOuter == null) {
            cloth.meshOuter = new int[quads * 4 * 6];
            cloth.meshRest = new int[(quads + 2 * cloth.rows + 2 * cloth.cols) * 4 * 6];
        }
        outer = cloth.meshOuter;
        rest = cloth.meshRest;
        double iu = 1.0 / 64.0, iv = 1.0 / 32.0;
        int o = 0, r = 0;
        for (int row = 0; row < cloth.rows; row++) {
            for (int c = 0; c < cloth.cols; c++) {
                int a = row * w + c, b = a + 1, d = a + w, e = d + 1;
                double u0 = 10.0 * c / cloth.cols, u1 = 10.0 * (c + 1) / cloth.cols;
                double v0 = 16.0 * row / cloth.rows, v1 = 16.0 * (row + 1) / cloth.rows;
                // Outer face: offset along +n, u = 1 + column.
                o = vtx(outer, o, a, half, (1 + u0) * iu, (1 + v0) * iv, 1);
                o = vtx(outer, o, d, half, (1 + u0) * iu, (1 + v1) * iv, 1);
                o = vtx(outer, o, e, half, (1 + u1) * iu, (1 + v1) * iv, 1);
                o = vtx(outer, o, b, half, (1 + u1) * iu, (1 + v0) * iv, 1);
                // Lining: offset along -n, u = 22 - column, normal reversed.
                r = vtx(rest, r, a, -half, (22 - u0) * iu, (1 + v0) * iv, -1);
                r = vtx(rest, r, b, -half, (22 - u1) * iu, (1 + v0) * iv, -1);
                r = vtx(rest, r, e, -half, (22 - u1) * iu, (1 + v1) * iv, -1);
                r = vtx(rest, r, d, -half, (22 - u0) * iu, (1 + v1) * iv, -1);
            }
        }
        {
            // Side strips (column 0 and the last column), then the hem and the top.
            for (int row = 0; row < cloth.rows; row++) {
                double v0 = (1 + 16.0 * row / cloth.rows) * iv, v1 = (1 + 16.0 * (row + 1) / cloth.rows) * iv;
                r = strip(rest, r, row * w, (row + 1) * w, half, 0.5 * iu, v0, 0.5 * iu, v1, 1, 0, 0);
                r = strip(rest, r, row * w + cloth.cols, (row + 1) * w + cloth.cols, half, 11.5 * iu, v0, 11.5 * iu, v1, 1, 0, 0);
            }
            for (int c = 0; c < cloth.cols; c++) {
                int a = cloth.rows * w + c;
                double u0 = (11 + 10.0 * c / cloth.cols) * iu, u1 = (11 + 10.0 * (c + 1) / cloth.cols) * iu;
                r = strip(rest, r, a, a + 1, half, u0, 0.5 * iv, u1, 0.5 * iv, 0, 1, 0);
                r = strip(rest, r, c, c + 1, half, (1 + 10.0 * c / cloth.cols) * iu, 0.5 * iv,
                        (1 + 10.0 * (c + 1) / cloth.cols) * iu, 0.5 * iv, 0, -1, 0);
            }
        }
    }

    private int vtx(int[] buf, int o, int i, double off, double u, double v, float sign) {
        buf[o] = Float.floatToRawIntBits((float) (mx[i] + nx[i] * off));
        buf[o + 1] = Float.floatToRawIntBits((float) (my[i] + ny[i] * off));
        buf[o + 2] = Float.floatToRawIntBits((float) (mz[i] + nz[i] * off));
        buf[o + 3] = Float.floatToRawIntBits((float) u);
        buf[o + 4] = Float.floatToRawIntBits((float) v);
        buf[o + 5] = normal(nx[i] * sign, ny[i] * sign, nz[i] * sign);
        return o + 6;
    }

    /** A strip across the sheet's thickness between particles i and j (an edge or the hem). */
    private int strip(int[] buf, int o, int i, int j, double half, double ui, double vi, double uj, double vj,
                      float fx, float fy, float fz) {
        int n = normal(fx, fy, fz);
        o = put(buf, o, mx[i] + nx[i] * half, my[i] + ny[i] * half, mz[i] + nz[i] * half, ui, vi, n);
        o = put(buf, o, mx[i] - nx[i] * half, my[i] - ny[i] * half, mz[i] - nz[i] * half, ui, vi, n);
        o = put(buf, o, mx[j] - nx[j] * half, my[j] - ny[j] * half, mz[j] - nz[j] * half, uj, vj, n);
        return put(buf, o, mx[j] + nx[j] * half, my[j] + ny[j] * half, mz[j] + nz[j] * half, uj, vj, n);
    }

    private static int put(int[] buf, int o, double x, double y, double z, double u, double v, int n) {
        buf[o] = Float.floatToRawIntBits((float) x);
        buf[o + 1] = Float.floatToRawIntBits((float) y);
        buf[o + 2] = Float.floatToRawIntBits((float) z);
        buf[o + 3] = Float.floatToRawIntBits((float) u);
        buf[o + 4] = Float.floatToRawIntBits((float) v);
        buf[o + 5] = n;
        return o + 6;
    }

    /** Three signed normal bytes and a padding byte, in the buffer's native (little-endian) order. */
    private static int normal(double x, double y, double z) {
        int bx = (int) Math.round(x * 127) & 255, by = (int) Math.round(y * 127) & 255, bz = (int) Math.round(z * 127) & 255;
        return bx | by << 8 | bz << 16;
    }

    /**
     * Draws the mesh built by {@link #mesh}: the whole sheet, or with {@code outerOnly} just the
     * outer face (the emissive pass: nothing on the lining glows). Current matrix: model pixels.
     */
    void draw(Cloth cloth, boolean outerOnly) {
        int verts = (outerOnly ? cloth.meshOuter.length : cloth.meshOuter.length + cloth.meshRest.length) / 6;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cloth.vbo);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 24, 0L);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 24, 12L);
        GL11.glNormalPointer(GL11.GL_BYTE, 24, 20L);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDrawArrays(GL11.GL_QUADS, 0, verts);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Uploads this frame's mesh into the cloth's own vertex buffer: one stream upload per frame,
     * then the albedo, tint and emissive passes draw from it. Drawing straight from client memory
     * (what the tessellator does) made the driver copy and sync on every pass, which measured at
     * eight times the cost of simulating the cloth.
     */
    void upload(Cloth cloth) {
        int ints = cloth.meshOuter.length + cloth.meshRest.length;
        if (cloth.vbo < 0) {
            cloth.vbo = GL15.glGenBuffers();
            cloth.upload = BufferUtils.createByteBuffer(ints * 4);
        }
        java.nio.IntBuffer view = cloth.upload.asIntBuffer();
        view.put(cloth.meshOuter).put(cloth.meshRest);
        cloth.upload.position(0).limit(ints * 4);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cloth.vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, cloth.upload, GL15.GL_STREAM_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /** Frees a cloth's vertex buffer. Render thread. */
    static void release(Cloth cloth) {
        if (cloth != null && cloth.vbo >= 0) {
            GL15.glDeleteBuffers(cloth.vbo);
            cloth.vbo = -1;
        }
    }
}
