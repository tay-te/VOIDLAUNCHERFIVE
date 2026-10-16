package dev.voidpvp.client.cosmetic;

/**
 * A position-based cloth grid: the cape's physics, with nothing of Minecraft in it so a test can
 * hold it.
 *
 * <p><b>Why world space.</b> The particles live in world coordinates and only the top row is
 * pinned to the wearer, so everything a cape should do falls out of the integration instead of
 * being faked: it trails when you run because the anchors leave the cloth behind, it lifts because
 * air pushes on the face of a moving sheet, it swings when you stop, it falls over your shoulders
 * when you turn on the spot. Vanilla's cape (and every client that copied it) is a rigid plank
 * rotated by a smoothed velocity term; this is the thing that plank is pretending to be.</p>
 *
 * <p><b>Integration.</b> Verlet at a fixed {@link #STEP} driven by an accumulator, so the motion
 * is the same at 30 fps and at 400: {@link #advance} runs however many whole steps the frame time
 * covers, interpolating the pinned row across them so a long frame is many small steps of a moving
 * anchor rather than one big teleport, and {@link #rx} bridges the remainder at draw time. Constraints are relaxed a few
 * times per step (structural, shear, and bend springs one cell apart), and the body collider runs
 * inside the relaxation loop so a sheet pulled tight against the back stays outside it.</p>
 *
 * <p><b>Aerodynamics.</b> Per particle, the component of its velocity relative to the air along
 * the local surface normal is damped — the classic cloth-in-wind force. It is what makes a cape
 * billow and settle rather than swing like a chain.</p>
 *
 * <p><b>Safety.</b> A step that moves a particle further than {@link #MAX_STEP_MOVE} is clamped,
 * and any non-finite value resets the whole sheet to hang from its anchors. A teleport (anchors
 * moving further than {@link #TELEPORT} in one call) resets too, rather than dragging the cape
 * across the map.</p>
 */
public final class Cloth {

    /** Longest single integration step, seconds. 120 Hz is smooth at every refresh rate. */
    public static final double STEP = 1.0 / 120.0;
    /** At most this many sub-steps per call: past it the step itself grows (a hitch, not a hang). */
    public static final int MAX_STEPS = 8;
    /** Anchors moving further than this in one call (blocks) count as a teleport. */
    public static final double TELEPORT = 2.5;
    /** No particle moves further than this in one step (blocks). */
    public static final double MAX_STEP_MOVE = 0.35;

    /** Collision against whatever the cloth hangs from. Called inside the relaxation loop. */
    public interface Collider {
        /** Push particle {@code i} (xs/ys/zs are the cloth's own arrays) out of the body. */
        void collide(double[] xs, double[] ys, double[] zs, int i);
    }

    public final int cols;
    public final int rows;
    final int n;
    final double[] x, y, z;
    final double[] px, py, pz;
    final double[] ax, ay, az; // anchor targets of the top row, last call
    private final double[] nax, nay, naz; // anchor targets this call
    private final int[] ca, cb;
    private final double[] rest;
    private final double[] stiff;
    private final boolean[] bend;
    private final boolean[] structural;
    private final double spacing;
    private boolean placed;
    /** The renderer's packed mesh for this sheet (see CapeRenderer.mesh); sized once. */
    int[] meshOuter, meshRest;
    /** Its vertex buffer and upload staging, or -1 before the first draw. */
    int vbo = -1;
    java.nio.ByteBuffer upload;
    /** Glow anchors in model pixels, stored with the mesh. */
    float[] glowAt;
    /** Time not yet simulated, seconds; and that as a fraction of a step. */
    private double acc;
    private double alpha;

    /**
     * This sheet's integration step, seconds: {@link #STEP} by default; distant low-resolution
     * capes run at half that rate, which halves their cost and cannot be seen at their distance.
     */
    public double step = STEP;

    /** Gravity, blocks/s^2 (Minecraft's own feels close to 20 for a falling player). */
    public double gravity = 16.0;
    /** Velocity kept per step, at {@link #STEP}. */
    public double damping = 0.992;
    /** Normal drag against the air, per second. */
    public double drag = 7.0;
    /** Constraint relaxation iterations per step. */
    public int iterations = 4;
    /** Wind velocity, blocks/s. */
    public double windX, windY, windZ;
    /** A travelling ripple down the sheet (a kill flourish): strength, and seconds since it began. */
    private double waveAmp, waveT;
    private double[] waveRow;
    /** How long a ripple runs, seconds. */
    public static final double WAVE_LENGTH = 0.9;

    /**
     * @param cols    quads across (particles = cols + 1)
     * @param rows    quads down (particles = rows + 1)
     * @param spacing rest distance between neighbours, blocks
     */
    public Cloth(int cols, int rows, double spacing, double stiffness) {
        this(cols, rows, spacing, stiffness, 0);
    }

    /**
     * @param flare how much wider the hem's rest width is than the collar's (0.25 = a quarter):
     *              the horizontal springs lengthen down the sheet, so with the top row pinned
     *              the cloth hangs as a gentle bell and the excess falls into soft folds instead
     *              of a flat plank.
     */
    public Cloth(int cols, int rows, double spacing, double stiffness, double flare) {
        this.cols = cols;
        this.rows = rows;
        this.spacing = spacing;
        int w = cols + 1;
        n = w * (rows + 1);
        x = new double[n];
        y = new double[n];
        z = new double[n];
        px = new double[n];
        py = new double[n];
        pz = new double[n];
        ax = new double[w];
        ay = new double[w];
        az = new double[w];
        nax = new double[w];
        nay = new double[w];
        naz = new double[w];
        // Structural (h, v), shear (both diagonals), bend (two cells apart, h and v).
        int count = 0;
        int[] tmpA = new int[n * 8];
        int[] tmpB = new int[n * 8];
        double[] tmpR = new double[n * 8];
        double[] tmpS = new double[n * 8];
        boolean[] tmpK = new boolean[n * 8];
        boolean[] tmpT = new boolean[n * 8];
        for (int r = 0; r <= rows; r++) {
            double h = spacing * (1 + flare * Math.pow((double) r / rows, 1.6));
            double h1 = spacing * (1 + flare * Math.pow((double) Math.min(rows, r + 1) / rows, 1.6));
            double diag = Math.sqrt(((h + h1) / 2) * ((h + h1) / 2) + spacing * spacing);
            for (int c = 0; c <= cols; c++) {
                int i = idx(c, r);
                if (c < cols) { tmpA[count] = i; tmpB[count] = idx(c + 1, r); tmpR[count] = h; tmpT[count] = true; tmpS[count++] = 1.0; }
                if (r < rows) { tmpA[count] = i; tmpB[count] = idx(c, r + 1); tmpR[count] = spacing; tmpT[count] = true; tmpS[count++] = 1.0; }
                if (c < cols && r < rows) {
                    tmpA[count] = i; tmpB[count] = idx(c + 1, r + 1); tmpR[count] = diag; tmpS[count++] = 0.6;
                    tmpA[count] = idx(c + 1, r); tmpB[count] = idx(c, r + 1); tmpR[count] = diag; tmpS[count++] = 0.6;
                }
                if (c + 2 <= cols) { tmpA[count] = i; tmpB[count] = idx(c + 2, r); tmpR[count] = 2 * h; tmpK[count] = true; tmpS[count++] = 0.35 * stiffness; }
                if (r + 2 <= rows) { tmpA[count] = i; tmpB[count] = idx(c, r + 2); tmpR[count] = 2 * spacing; tmpK[count] = true; tmpS[count++] = 0.25 * stiffness; }
            }
        }
        ca = java.util.Arrays.copyOf(tmpA, count);
        cb = java.util.Arrays.copyOf(tmpB, count);
        rest = java.util.Arrays.copyOf(tmpR, count);
        stiff = java.util.Arrays.copyOf(tmpS, count);
        bend = java.util.Arrays.copyOf(tmpK, count);
        structural = java.util.Arrays.copyOf(tmpT, count);
    }

    public int idx(int c, int r) {
        return r * (cols + 1) + c;
    }

    public double x(int i) { return x[i]; }
    public double y(int i) { return y[i]; }
    public double z(int i) { return z[i]; }

    /**
     * Where to draw particle {@code i} now: the pinned row exactly on its current anchors, the
     * rest extrapolated by the un-simulated remainder of a step along their velocity.
     */
    public double rx(int i) {
        return i <= cols ? ax[i] : x[i] + (x[i] - px[i]) * alpha;
    }

    public double ry(int i) {
        return i <= cols ? ay[i] : y[i] + (y[i] - py[i]) * alpha;
    }

    public double rz(int i) {
        return i <= cols ? az[i] : z[i] + (z[i] - pz[i]) * alpha;
    }

    /** True once the cloth has been hung from a set of anchors. */
    public boolean placed() {
        return placed;
    }

    /**
     * Hangs the sheet from the anchors with no motion: each column straight down from its anchor,
     * nudged {@code back} along ({@code bx}, {@code bz}) so it starts behind the body, not in it.
     */
    public void reset(double[] axs, double[] ays, double[] azs, double bx, double bz, double back) {
        for (int c = 0; c <= cols; c++) {
            for (int r = 0; r <= rows; r++) {
                int i = idx(c, r);
                double k = r * spacing;
                x[i] = px[i] = axs[c] + bx * back * Math.min(1, k * 4);
                y[i] = py[i] = ays[c] - k;
                z[i] = pz[i] = azs[c] + bz * back * Math.min(1, k * 4);
            }
            ax[c] = axs[c];
            ay[c] = ays[c];
            az[c] = azs[c];
        }
        placed = true;
        acc = 0;
        alpha = 0;
        waveAmp = 0;
    }

    /**
     * Advances the cloth by {@code dt} seconds while the pinned row moves from where it was last
     * call to ({@code axs}, {@code ays}, {@code azs}).
     *
     * @return false if the sheet had to be reset (teleport or blow-up); the caller need not care
     */
    public boolean advance(double dt, double[] axs, double[] ays, double[] azs, Collider collider,
                           double bx, double bz) {
        if (!placed) {
            reset(axs, ays, azs, bx, bz, 0.05);
            return false;
        }
        double jump = 0;
        for (int c = 0; c <= cols; c++) {
            double dx = axs[c] - ax[c], dy = ays[c] - ay[c], dz = azs[c] - az[c];
            jump = Math.max(jump, dx * dx + dy * dy + dz * dz);
        }
        if (jump > TELEPORT * TELEPORT || !(dt >= 0)) {
            reset(axs, ays, azs, bx, bz, 0.05);
            return false;
        }
        if (dt <= 0) {
            return true;
        }
        dt = Math.min(dt, 0.25);
        // Fixed steps from an accumulator: the same step length and the same number of
        // relaxation passes per second at every frame rate. What is left over in the
        // accumulator is bridged at draw time by extrapolating along each particle's velocity
        // (see #rx), so the sheet is drawn where it is now, not up to a step ago.
        acc = Math.min(acc + dt, MAX_STEPS * step);
        int steps = (int) (acc / step);
        double keep = Math.pow(damping, step / STEP);
        double accStart = acc;
        for (int s = 1; s <= steps; s++) {
            // Where the anchors were at this step's time: interpolated across this call's frame.
            double t = Math.max(0, Math.min(1, (dt - (accStart - s * step)) / dt));
            for (int c = 0; c <= cols; c++) {
                nax[c] = ax[c] + (axs[c] - ax[c]) * t;
                nay[c] = ay[c] + (ays[c] - ay[c]) * t;
                naz[c] = az[c] + (azs[c] - az[c]) * t;
            }
            integrate(step, keep);
            for (int c = 0; c <= cols; c++) {
                int i = idx(c, 0);
                x[i] = nax[c];
                y[i] = nay[c];
                z[i] = naz[c];
            }
            relax(collider);
            acc -= step;
        }
        alpha = acc / step;
        for (int c = 0; c <= cols; c++) {
            ax[c] = axs[c];
            ay[c] = ays[c];
            az[c] = azs[c];
        }
        for (int i = 0; i < n; i++) {
            if (!(Math.abs(x[i]) < 1e8 && Math.abs(y[i]) < 1e8 && Math.abs(z[i]) < 1e8)) {
                reset(axs, ays, azs, bx, bz, 0.05);
                return false;
            }
        }
        return true;
    }

    /**
     * Adds a velocity change (blocks/s, world space) to every free particle, weighted by how far
     * down the sheet it hangs, {@code (row / rows)^power}: the hem takes all of it, the collar
     * none. Verlet keeps velocity as the step's displacement, so this moves the previous
     * positions. Safe to call between steps; non-finite input is ignored.
     */
    public void kick(double vx, double vy, double vz, double power) {
        if (!placed || !(Math.abs(vx) + Math.abs(vy) + Math.abs(vz) < 1e3)) {
            return;
        }
        int w = cols + 1;
        for (int r = 1; r <= rows; r++) {
            double k = Math.pow((double) r / rows, power) * step;
            double dx = vx * k, dy = vy * k, dz = vz * k;
            for (int c = 0; c <= cols; c++) {
                int i = r * w + c;
                px[i] -= dx;
                py[i] -= dy;
                pz[i] -= dz;
            }
        }
    }

    /**
     * Starts a ripple that runs from the collar to the hem over {@link #WAVE_LENGTH} seconds, a
     * push along the sheet's normal that travels down it: a cape rippling outward. {@code amp}
     * is the peak force, blocks/s^2 (about 60 reads clearly).
     */
    public void wave(double amp) {
        if (amp > waveAmp * Math.max(0, 1 - waveT / WAVE_LENGTH)) {
            waveAmp = amp;
            waveT = 0;
        }
    }

    /** The ripple's push on each row this step, or null when none is running. */
    private double[] waveForces(double h) {
        if (waveAmp <= 0) {
            return null;
        }
        if (waveT >= WAVE_LENGTH) {
            waveAmp = 0;
            return null;
        }
        if (waveRow == null) {
            waveRow = new double[rows + 1];
        }
        double env = Math.sin(Math.PI * waveT / WAVE_LENGTH);
        env *= env;
        for (int r = 0; r <= rows; r++) {
            double u = (double) r / rows;
            // One and a bit wavelengths on the sheet, travelling hem-ward.
            waveRow[r] = waveAmp * env * Math.sin(u * 7.5 - waveT * 18.0) * (0.35 + 0.65 * u);
        }
        waveT += h;
        return waveRow;
    }

    private void integrate(double h, double keep) {
        int w = cols + 1;
        double h2 = h * h;
        double[] wave = waveForces(h);
        for (int r = 1; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                int i = r * w + c;
                double vx = (x[i] - px[i]) * keep;
                double vy = (y[i] - py[i]) * keep;
                double vz = (z[i] - pz[i]) * keep;
                // Surface normal from the neighbours (central differences, clamped at edges).
                int l = r * w + Math.max(0, c - 1), rr = r * w + Math.min(cols, c + 1);
                int u = (r - 1) * w + c, d = Math.min(rows, r + 1) * w + c;
                double hx = x[rr] - x[l], hy = y[rr] - y[l], hz = z[rr] - z[l];
                double gx = x[d] - x[u], gy = y[d] - y[u], gz = z[d] - z[u];
                double nx = hy * gz - hz * gy, ny = hz * gx - hx * gz, nz = hx * gy - hy * gx;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double fx = 0, fy = -gravity, fz = 0;
                if (nl > 1e-9) {
                    nx /= nl;
                    ny /= nl;
                    nz /= nl;
                    // Velocity per second relative to the air, projected on the normal.
                    double rvx = vx / h - windX, rvy = vy / h - windY, rvz = vz / h - windZ;
                    double vn = rvx * nx + rvy * ny + rvz * nz;
                    fx -= drag * vn * nx;
                    fy -= drag * vn * ny;
                    fz -= drag * vn * nz;
                    if (wave != null) {
                        double f = wave[r];
                        fx += f * nx;
                        fy += f * ny;
                        fz += f * nz;
                    }
                }
                double mx = vx + fx * h2, my = vy + fy * h2, mz = vz + fz * h2;
                double m2 = mx * mx + my * my + mz * mz;
                if (m2 > MAX_STEP_MOVE * MAX_STEP_MOVE) {
                    double k = MAX_STEP_MOVE / Math.sqrt(m2);
                    mx *= k;
                    my *= k;
                    mz *= k;
                }
                px[i] = x[i];
                py[i] = y[i];
                pz[i] = z[i];
                x[i] += mx;
                y[i] += my;
                z[i] += mz;
            }
        }
        // The pinned row's previous position follows its anchor, so it carries no velocity.
        for (int c = 0; c <= cols; c++) {
            px[c] = x[c];
            py[c] = y[c];
            pz[c] = z[c];
        }
    }

    private void relax(Collider collider) {
        int w = cols + 1;
        for (int it = 0; it < iterations; it++) {
            for (int k = 0; k < ca.length; k++) {
                int a = ca[k], b = cb[k];
                double dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1e-9) {
                    continue;
                }
                double diff = (d - rest[k]) / d * stiff[k];
                // Bend springs only resist compression weakly, so the hem can fold.
                if (diff < 0 && bend[k]) {
                    diff *= 0.4;
                }
                boolean pa = a < w, pb = b < w;
                if (pa && pb) {
                    continue;
                }
                double wa = pa ? 0 : pb ? 1 : 0.5;
                double wb = pb ? 0 : pa ? 1 : 0.5;
                x[a] += dx * diff * wa;
                y[a] += dy * diff * wa;
                z[a] += dz * diff * wa;
                x[b] -= dx * diff * wb;
                y[b] -= dy * diff * wb;
                z[b] -= dz * diff * wb;
            }
            // The body collider runs on the last pass and the middle one: often enough that a
            // sheet pulled against the back stays outside it, a fraction of the cost of every pass.
            if (collider != null && (it == iterations - 1 || it == iterations / 2)) {
                for (int i = w; i < n; i++) {
                    collider.collide(x, y, z, i);
                }
            }
        }
    }

    /** Longest constraint stretch ratio (current / rest) over the structural springs; for tests. */
    double maxStretch() {
        double worst = 0;
        for (int k = 0; k < ca.length; k++) {
            if (!structural[k]) {
                continue;
            }
            int a = ca[k], b = cb[k];
            double dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
            worst = Math.max(worst, Math.sqrt(dx * dx + dy * dy + dz * dz) / rest[k]);
        }
        return worst;
    }
}
