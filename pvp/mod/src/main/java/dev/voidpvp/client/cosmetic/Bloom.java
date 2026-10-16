package dev.voidpvp.client.cosmetic;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.VoidLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.IntBuffer;

/**
 * The cosmetics' glow: a real bloom, cheap enough to leave on.
 *
 * <h2>How</h2>
 * <ol>
 *   <li><b>Mask.</b> A texture the size of the game's framebuffer is attached to Minecraft's own
 *       FBO as a second colour attachment. While a player with something emissive draws its
 *       cosmetics, the draw buffers are both attachments and {@code cosmetic.fsh} writes emissive
 *       light into the second (and black from every other cosmetic surface, so what stands in
 *       front of a glow hides it). Same matrices, same depth test as the visible pass, so the
 *       mask is exact and nothing glows through a wall.</li>
 *   <li><b>Blur.</b> After the world, before the hand and the HUD: the mask is reduced to a
 *       quarter of the screen (sixteen taps, keeping the peak so a one-pixel light survives), then
 *       dual-filtered down to an eighth and a sixteenth and back up, each level added onto the one
 *       above: a wide, round blur in a handful of single-texture passes.</li>
 *   <li><b>Composite.</b> The quarter level through a soft knee, added onto the frame.</li>
 * </ol>
 * Strength is per player and written into the mask: strong at night, subtle against daylight.
 * The {@code cosmetics} mod's {@code glow} setting picks off (no mask, nothing here runs), low
 * (two levels, gentler) or high.
 *
 * <h2>When it costs nothing</h2>
 * Nothing is attached, cleared or blurred until a player whose outfit has an emissive part is
 * drawn in the world pass; a frame where none is drawn skips the whole pass. Everything here is
 * best-effort: no FBOs, fewer than two draw buffers or a program that will not build disables it
 * once, logged, and the cosmetics fall back to the soft sprites of {@link GlowBatch}.
 *
 * <h2>State</h2>
 * The mask clear and the composite run inside {@code glPushAttrib}/{@code glPopAttrib} with the
 * matrices pushed, the framebuffer rebound and the program unbound afterwards, so
 * {@code GlStateManager}'s cache is told the truth. Render thread only.
 */
public final class Bloom {

    private static boolean disabled;
    private static boolean checked;
    private static int progDown, progUp, progComp;
    private static int uDownTex, uDownTexel, uDownPeak, uUpTex, uUpHp, uUpW, uCompTex, uCompGain;

    private static int maskTex = -1;
    private static int maskFbo = -1;
    private static int texW, texH, viewW, viewH;
    /** Pyramid: 1/4, 1/8, 1/16 of the screen. */
    private static final int LEVELS = 3;
    private static final int[] TEX = {-1, -1, -1};
    private static final int[] FBO = {-1, -1, -1};
    private static final int[] W = new int[LEVELS], H = new int[LEVELS];

    /** Dev: {@code -Dvoid.dev.bloom.view=true} draws the mask and the first level on screen. */
    private static final boolean DEBUG_VIEW = Boolean.getBoolean("void.dev.bloom.view");
    private static boolean inWorld;
    /** For the dev log line ({@link #diagnostic}): composites run, and why the pass is off. */
    private static int composites, players;
    /** Dev attribution: time in the per-player draw-buffer switches and in the composite. */
    static long mrtNanos, compNanos;
    private static String offReason = "";
    private static boolean cleared;
    private static boolean mrt;
    private static boolean wrote;
    private static int level;

    private static final IntBuffer BOTH = BufferUtils.createIntBuffer(2);
    private static final IntBuffer MAIN = BufferUtils.createIntBuffer(1);
    private static final IntBuffer ONLY_MASK = BufferUtils.createIntBuffer(1);

    private Bloom() {
    }

    /** Head of {@code GameRenderer.renderWorld(int, float, long)}. */
    public static void worldStart() {
        inWorld = true;
    }

    /** The {@code glow} setting: 0 off, 1 low, 2 high. Read once a frame by the renderer. */
    static void level(int l) {
        level = l;
    }

    /** True when the bloom pass is usable at all (the sprites stand in when it is not). */
    static boolean usable() {
        return !disabled && level > 0 && GLX.supportsFbo() && GlProgram.available();
    }

    /**
     * Before a player's cosmetic passes, when the outfit has something emissive: routes the
     * cosmetic shader's second output into the mask. False when the mask is not available here
     * (then the passes draw as usual and nothing is written).
     */
    static boolean beginPlayer(MinecraftClient mc) {
        if (!inWorld || !usable()) {
            return false;
        }
        long t0 = System.nanoTime();
        try {
            return beginPlayer0(mc);
        } finally {
            mrtNanos += System.nanoTime() - t0;
        }
    }

    private static boolean beginPlayer0(MinecraftClient mc) {
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.fbo < 0 || !ensureMask(fb)) {
            return false;
        }
        if (!cleared) {
            cleared = true;
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GL20.glDrawBuffers(ONLY_MASK);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glPopAttrib();
        }
        GL20.glDrawBuffers(BOTH);
        mrt = true;
        players++;
        return true;
    }

    /** An emissive pass just drew into the mask. */
    static void drew() {
        if (mrt) {
            wrote = true;
        }
    }

    static void endPlayer() {
        if (mrt) {
            long t0 = System.nanoTime();
            GL20.glDrawBuffers(MAIN);
            mrt = false;
            mrtNanos += System.nanoTime() - t0;
        }
    }

    // ------------------------------------------------------------------------ resources

    private static boolean ensureMask(Framebuffer fb) {
        if (!checked) {
            checked = true;
            if (GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS) < 2) {
                return disable("fewer than two draw buffers");
            }
            progDown = GlProgram.load("bloom.vsh", "bloom_down.fsh");
            progUp = GlProgram.load("bloom.vsh", "bloom_up.fsh");
            progComp = GlProgram.load("bloom.vsh", "bloom_comp.fsh");
            if (progDown == 0 || progUp == 0 || progComp == 0) {
                return disable("bloom programs did not build");
            }
            uDownTex = GL20.glGetUniformLocation(progDown, "u_tex");
            uDownTexel = GL20.glGetUniformLocation(progDown, "u_texel");
            uDownPeak = GL20.glGetUniformLocation(progDown, "u_peak");
            uUpTex = GL20.glGetUniformLocation(progUp, "u_tex");
            uUpHp = GL20.glGetUniformLocation(progUp, "u_hp");
            uUpW = GL20.glGetUniformLocation(progUp, "u_w");
            uCompTex = GL20.glGetUniformLocation(progComp, "u_tex");
            uCompGain = GL20.glGetUniformLocation(progComp, "u_gain");
            // Samplers are program state: set once.
            GL20.glUseProgram(progDown);
            GL20.glUniform1i(uDownTex, 0);
            GL20.glUseProgram(progUp);
            GL20.glUniform1i(uUpTex, 0);
            GL20.glUseProgram(progComp);
            GL20.glUniform1i(uCompTex, 0);
            GL20.glUseProgram(0);
            int a0 = GLX.colorAttachment;
            BOTH.put(0, a0).put(1, a0 + 1);
            MAIN.put(0, a0);
            ONLY_MASK.put(0, a0 + 1);
        }
        if (disabled) {
            return false;
        }
        if (maskFbo == fb.fbo && texW == fb.textureWidth && texH == fb.textureHeight
                && viewW == fb.viewportWidth && viewH == fb.viewportHeight) {
            return true;
        }
        release();
        texW = fb.textureWidth;
        texH = fb.textureHeight;
        viewW = fb.viewportWidth;
        viewH = fb.viewportHeight;
        maskTex = texture(texW, texH);
        // The main FBO is bound (the world pass draws into it); the mask joins it as attachment 1.
        GLX.advancedFrameBufferTexture2D(GLX.framebuffer, GLX.colorAttachment + 1, GL11.GL_TEXTURE_2D, maskTex, 0);
        int status = GLX.advancedCheckFrameBufferStatus(GLX.framebuffer);
        if (status != GLX.completeFramebuffer) {
            GLX.advancedFrameBufferTexture2D(GLX.framebuffer, GLX.colorAttachment + 1, GL11.GL_TEXTURE_2D, 0, 0);
            GL11.glDeleteTextures(maskTex);
            maskTex = -1;
            return disable("mask attachment incomplete (0x" + Integer.toHexString(status) + ")");
        }
        maskFbo = fb.fbo;
        for (int i = 0; i < LEVELS; i++) {
            int div = 4 << i;
            W[i] = Math.max(1, viewW / div);
            H[i] = Math.max(1, viewH / div);
            TEX[i] = texture(W[i], H[i]);
            FBO[i] = GLX.advancedGenFrameBuffers();
            GLX.advancedBindFramebuffer(GLX.framebuffer, FBO[i]);
            GLX.advancedFrameBufferTexture2D(GLX.framebuffer, GLX.colorAttachment, GL11.GL_TEXTURE_2D, TEX[i], 0);
        }
        GLX.advancedBindFramebuffer(GLX.framebuffer, fb.fbo);
        return true;
    }

    private static int texture(int w, int h) {
        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                (java.nio.ByteBuffer) null);
        GlStateManager.bindTexture(0);
        return id;
    }

    /** Minecraft rebuilt its framebuffer (a resize): the old attachment went with it. */
    public static void invalidate() {
        maskFbo = -1;
    }

    private static void release() {
        if (maskTex >= 0) {
            GL11.glDeleteTextures(maskTex);
            maskTex = -1;
        }
        for (int i = 0; i < LEVELS; i++) {
            if (TEX[i] >= 0) {
                GL11.glDeleteTextures(TEX[i]);
                GLX.advancedDeleteFrameBuffers(FBO[i]);
                TEX[i] = -1;
                FBO[i] = -1;
            }
        }
        maskFbo = -1;
    }

    private static boolean disable(String why) {
        disabled = true;
        offReason = why;
        VoidLog.warn("cosmetics: bloom off (" + why + "); glow falls back to sprites");
        return false;
    }

    /** One line for the dev log: whether the pass is running, and since the last line how much. */
    static String diagnostic() {
        int c = Math.max(1, composites);
        String d = disabled ? "bloom off (" + offReason + ")"
                : String.format(java.util.Locale.ROOT, "bloom level %d, %d composites (%.0f us each), %d player passes (draw buffers %.0f us/frame)",
                level, composites, compNanos / 1000.0 / c, players, mrtNanos / 1000.0 / c);
        d += String.format(java.util.Locale.ROOT, "; shader bind %.0f us/frame", CosmeticShader.bindNanos / 1000.0 / c);
        composites = 0;
        players = 0;
        mrtNanos = 0;
        compNanos = 0;
        CosmeticShader.bindNanos = 0;
        return d;
    }

    // ------------------------------------------------------------------------- composite

    /** After the world, before the hand. Skips everything when no emissive cosmetic drew. */
    public static void composite(MinecraftClient mc) {
        inWorld = false;
        endPlayer();
        boolean run = wrote && !disabled && maskTex >= 0;
        cleared = false;
        wrote = false;
        if (!run) {
            return;
        }
        long t0 = System.nanoTime();
        composites++;
        Framebuffer fb = mc.getFramebuffer();
        // Only the groups this pass changes are saved; the texture binding is handed back to
        // GlStateManager's cache explicitly below, the matrices are pushed, the FBO rebound.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_VIEWPORT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TRANSFORM_BIT | GL11.GL_CURRENT_BIT);
        GlStateManager.activeTexture(GLX.textureUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            boolean high = level >= 2;
            float su = (float) viewW / texW, sv = (float) viewH / texH;
            // Down: the mask to 1/4 (keeping the peak), then 1/8 and, on high, 1/16.
            pass(progDown, maskTex, 0, false);
            GL20.glUniform2f(uDownTexel, 1f / texW, 1f / texH);
            GL20.glUniform1f(uDownPeak, 1f);
            quad(su, sv);
            GL20.glUniform1f(uDownPeak, 0f);
            int deepest = high ? 2 : 1;
            for (int l = 1; l <= deepest; l++) {
                pass(progDown, TEX[l - 1], l, false);
                GL20.glUniform2f(uDownTexel, 0.5f / W[l], 0.5f / H[l]);
                quad(1, 1);
            }
            // Up: each level blurred again and added onto the one above it.
            GL20.glUseProgram(progUp);
            GL20.glUniform1f(uUpW, high ? 1.0f : 0.8f);
            for (int l = deepest; l >= 1; l--) {
                pass(progUp, TEX[l], l - 1, true);
                GL20.glUniform2f(uUpHp, 0.5f / W[l], 0.5f / H[l]);
                quad(1, 1);
            }
            // Onto the frame.
            GLX.advancedBindFramebuffer(GLX.framebuffer, fb.fbo);
            GL11.glViewport(0, 0, viewW, viewH);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glUseProgram(progComp);
            GL20.glUniform1f(uCompGain, high ? 2.6f : 1.5f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX[0]);
            quad(1, 1);
            if (DEBUG_VIEW) {
                // Dev: the raw mask and the finished quarter level in the lower left.
                GL20.glUseProgram(0);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1, 1, 1, 1);
                GL11.glViewport(0, 0, viewW / 3, viewH / 3);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
                quad(su, sv);
                GL11.glViewport(viewW / 3, 0, viewW / 3, viewH / 3);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX[0]);
                quad(1, 1);
            }
        } finally {
            GL20.glUseProgram(0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GlStateManager.bindTexture(0);
            GLX.advancedBindFramebuffer(GLX.framebuffer, fb.fbo);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        long dt = System.nanoTime() - t0;
        Budget.add(dt);
        Budget.split(2, dt);
        compNanos += dt;
    }

    /** Targets level {@code dst}, binds {@code prog} and samples {@code src}; adds when {@code add}. */
    private static void pass(int prog, int src, int dst, boolean add) {
        GLX.advancedBindFramebuffer(GLX.framebuffer, FBO[dst]);
        GL11.glViewport(0, 0, W[dst], H[dst]);
        if (add) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL20.glUseProgram(prog);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, src);
    }

    private static void quad(float su, float sv) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(su, 0);
        GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(su, sv);
        GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, sv);
        GL11.glVertex2f(0, 1);
        GL11.glEnd();
    }
}
