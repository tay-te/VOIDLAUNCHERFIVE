package dev.voidpvp.client.cosmetic;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

/**
 * The cosmetics' material program ({@code cosmetic.vsh}/{@code cosmetic.fsh}): Minecraft's own
 * entity lighting and lightmap, plus contact occlusion against the wearer, a rim of sky light and
 * a per-part material — matte, cloth, metal, glass, emissive. See the fragment shader for what
 * each material does and {@code docs/cosmetics.md} for how an item names them.
 *
 * <p>Bound for one player's cosmetic passes ({@link #begin} .. {@link #end}); every setter caches
 * its last value, so a pass that changes nothing costs no GL call. When the program will not
 * build, {@link #ready} is false and the renderer draws the fixed-function way it always has.</p>
 */
final class CosmeticShader {

    static final int MATTE = 0, CLOTH = 1, METAL = 2, GLASS = 3, EMISSIVE = 4;
    static final String[] MATERIALS = {"matte", "cloth", "metal", "glass", "emissive"};

    /** Contact occluders (the boxes are in the vertex shader): none, the torso, the head. */
    static final int NONE = 0, TORSO = 1, HEAD = 2;

    /** Fog as {@code GlStateManager} last set it (CosmeticsFogMixin), so nothing reads GL back. */
    private static volatile boolean fogOn;
    private static volatile int fogMode = GL11.GL_LINEAR;

    private static int program = -1;
    private static int uAlphaMat, uTint, uBloom, uFog, uRim, uEyeToBody, uEyeToHead, uOccluder,
            uMatSrc, uMat, uEmiK, uTintOn, uTintRgb;
    private static final FloatBuffer MAT = BufferUtils.createFloatBuffer(16);
    private static boolean bound;

    // Cached uniform state; NaN forces the first write.
    private static final float[] CACHE = {Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
        Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    private static float ctr = Float.NaN, ctg, ctb;
    private static float cFog = Float.NaN;
    private static float cr = Float.NaN, cg, cb, ca;

    private CosmeticShader() {
    }

    public static void fogEnabled(boolean on) {
        fogOn = on;
    }

    public static void fogMode(int mode) {
        fogMode = mode;
    }

    /** Dev (the showcase's A/B measurement): draw the fixed-function way while set. */
    static volatile boolean devOff;

    /** Builds the program on first use; false when it is unavailable. Render thread. */
    static boolean ready() {
        if (devOff) {
            return false;
        }
        if (program < 0) {
            // Dev: -Dvoid.dev.cosmetics.shader=false measures or compares the fixed-function path.
            boolean off = "false".equals(System.getProperty("void.dev.cosmetics.shader"));
            program = !off && GlProgram.available() ? GlProgram.load("cosmetic.vsh", "cosmetic.fsh") : 0;
            if (program > 0) {
                uAlphaMat = loc("u_alphaMat");
                uTint = loc("u_tint");
                uBloom = loc("u_bloom");
                uFog = loc("u_fog");
                uRim = loc("u_rim");
                uEyeToBody = loc("u_eyeToBody");
                uEyeToHead = loc("u_eyeToHead");
                uOccluder = loc("u_occluder");
                uMatSrc = loc("u_matSrc");
                uMat = loc("u_mat");
                uEmiK = loc("u_emiK");
                uTintOn = loc("u_tintOn");
                uTintRgb = loc("u_tintRgb");
                GL20.glUseProgram(program);
                GL20.glUniform1i(loc("u_tex"), 0);
                GL20.glUniform1i(loc("u_light"), 1);
                GL20.glUniform1i(loc("u_emi"), 2);
                GL20.glUniform1i(loc("u_tintTex"), 3);
                GL20.glUseProgram(0);
            }
        }
        return program > 0;
    }

    private static int loc(String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    /** Dev attribution: time spent binding and unbinding the program. */
    static long bindNanos;

    static void begin() {
        long t0 = System.nanoTime();
        GL20.glUseProgram(program);
        bindNanos += System.nanoTime() - t0;
        bound = true;
        float fog = !fogOn ? 0 : fogMode == GL11.GL_LINEAR ? 1 : fogMode == GL11.GL_EXP ? 2 : 3;
        if (fog != cFog) {
            cFog = fog;
            GL20.glUniform1f(uFog, fog);
        }
        tint(1, 1, 1, 1);
    }

    static void end() {
        if (bound) {
            long t0 = System.nanoTime();
            GL20.glUseProgram(0);
            bound = false;
            bindNanos += System.nanoTime() - t0;
        }
    }

    static boolean bound() {
        return bound;
    }

    /** Lit pass. {@code vertexMat}: models carry a material per cube; otherwise {@code mat}. */
    static void lit(boolean vertexMat, int mat, boolean alphaMat) {
        set(1, uMatSrc, vertexMat ? 1 : 0);
        set(2, uMat, mat);
        set(3, uAlphaMat, alphaMat ? 1 : 0);
    }

    /**
     * The item's other layers for the one combined pass: the emissive layer's strength (0 when
     * there is none; its texture is on unit 2) and the tint (null when there is no tint mask; the
     * mask is on unit 3).
     */
    static void layers(float emissiveK, float[] tintRgb) {
        set(8, uEmiK, emissiveK);
        set(9, uTintOn, tintRgb == null ? 0 : 1);
        if (tintRgb != null && (tintRgb[0] != ctr || tintRgb[1] != ctg || tintRgb[2] != ctb)) {
            ctr = tintRgb[0];
            ctg = tintRgb[1];
            ctb = tintRgb[2];
            GL20.glUniform3f(uTintRgb, ctr, ctg, ctb);
        }
    }

    static void tint(float r, float g, float b, float a) {
        if (r != cr || g != cg || b != cb || a != ca) {
            cr = r;
            cg = g;
            cb = b;
            ca = a;
            GL20.glUniform4f(uTint, r, g, b, a);
        }
    }

    static void bloom(float k) {
        set(4, uBloom, k);
    }

    static void rim(float k) {
        set(5, uRim, k);
    }

    /** This player's eye-to-part matrices for contact occlusion: once per player. */
    static void parts(float[] eyeToBody, float[] eyeToHead) {
        MAT.clear();
        MAT.put(eyeToBody).flip();
        GL20.glUniformMatrix4(uEyeToBody, false, MAT);
        MAT.clear();
        MAT.put(eyeToHead).flip();
        GL20.glUniformMatrix4(uEyeToHead, false, MAT);
    }

    /** Which part the next draws are occluded by: {@link #NONE}, {@link #TORSO} or {@link #HEAD}. */
    static void occluder(int which) {
        set(6, uOccluder, which);
    }

    private static void set(int which, int loc, float v) {
        if (CACHE[which] == v) {
            return;
        }
        CACHE[which] = v;
        GL20.glUniform1f(loc, v);
    }

    /** Material index for a name, or -1. */
    static int material(String name) {
        for (int i = 0; i < MATERIALS.length; i++) {
            if (MATERIALS[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
