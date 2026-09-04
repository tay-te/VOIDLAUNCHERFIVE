package dev.voidpvp.client.screen;

import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.render.GlBlit;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * The menu backdrop of §6.4: copy the main framebuffer to a quarter-resolution
 * FBO, blur it in two passes, draw it back with a tint. The same technique
 * Vape and Lunar use, and the reason the panel can look like the Figma without
 * {@code backdrop-filter}, which Ultralight does not have (§9).
 *
 * <p>Everything is best-effort. If the FBO or the shaders will not come up the
 * backdrop degrades to a flat tint and the menu still works — a blur is worth
 * exactly zero crashes.</p>
 */
final class BlurBackdrop {

    private static final int DOWNSCALE = 4;

    private final boolean coreFbo;
    private boolean disabled;
    private boolean ready;

    private int sceneTexture;
    private int sceneWidth;
    private int sceneHeight;
    private int quarterWidth;
    private int quarterHeight;
    private int fboA;
    private int fboB;
    private int texA;
    private int texB;
    private int program;
    private int uniformDirection;
    private int uniformTexture;

    BlurBackdrop() {
        boolean core;
        try {
            core = GLContext.getCapabilities().OpenGL30;
        } catch (RuntimeException e) {
            core = false;
        }
        this.coreFbo = core;
        try {
            if (!core && !GLContext.getCapabilities().GL_EXT_framebuffer_object) {
                disabled = true;
                VoidLog.warn("no framebuffer objects available; menu backdrop will be a flat tint");
            }
            if (!GLContext.getCapabilities().OpenGL20) {
                disabled = true;
                VoidLog.warn("no GLSL available; menu backdrop will be a flat tint");
            }
        } catch (RuntimeException e) {
            disabled = true;
        }
    }

    /**
     * Draws the blurred, tinted copy of whatever is currently in the colour
     * buffer over the whole screen.
     *
     * @param screenWidth  scaled GUI width, the coordinate space of the quad
     * @param fbWidth      real framebuffer width, the resolution of the copy
     * @param tintArgb     the {@code rgba(0,0,0,0.45)} of §6.4, packed
     */
    void draw(int screenWidth, int screenHeight, int fbWidth, int fbHeight, int tintArgb) {
        if (disabled || fbWidth <= 0 || fbHeight <= 0) {
            GlBlit.begin2d(screenWidth, screenHeight);
            try {
                GlBlit.fill(0, 0, screenWidth, screenHeight, tintArgb);
            } finally {
                GlBlit.end2d();
            }
            return;
        }
        try {
            ensure(fbWidth, fbHeight);
            int previousFbo = currentFramebuffer();

            // 1. the main framebuffer, as a texture
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fbWidth, fbHeight);

            // 2. downsample into the quarter-res target
            blitInto(fboA, sceneTexture, 0f, 0f);
            // 3. two-pass Gaussian, horizontal then vertical
            blitInto(fboB, texA, 1f / quarterWidth, 0f);
            blitInto(fboA, texB, 0f, 1f / quarterHeight);

            bindFramebuffer(previousFbo);
            GL11.glViewport(0, 0, fbWidth, fbHeight);
            GL20.glUseProgram(0);

            // 4. back over the screen, plus the tint
            GlBlit.begin2d(screenWidth, screenHeight);
            try {
                GlBlit.drawTexture(texA, 0, 0, screenWidth, screenHeight, false, true, 1f);
                GlBlit.fill(0, 0, screenWidth, screenHeight, tintArgb);
            } finally {
                GlBlit.end2d();
            }
        } catch (RuntimeException e) {
            VoidLog.warn("menu backdrop blur failed, falling back to a flat tint: " + e);
            disabled = true;
        }
    }

    /** Renders {@code source} into {@code target}; a zero direction means no blur. */
    private void blitInto(int target, int source, float dirX, float dirY) {
        bindFramebuffer(target);
        GL11.glViewport(0, 0, quarterWidth, quarterHeight);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, source);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        boolean blur = dirX != 0f || dirY != 0f;
        if (blur) {
            GL20.glUseProgram(program);
            GL20.glUniform1i(uniformTexture, 0);
            GL20.glUniform2f(uniformDirection, dirX, dirY);
        } else {
            GL20.glUseProgram(0);
        }

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(1f, 0f);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(1f, 1f);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(0f, 1f);
        GL11.glEnd();

        GL20.glUseProgram(0);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private void ensure(int fbWidth, int fbHeight) {
        if (ready && fbWidth == sceneWidth && fbHeight == sceneHeight) {
            return;
        }
        release();
        sceneWidth = fbWidth;
        sceneHeight = fbHeight;
        quarterWidth = Math.max(1, fbWidth / DOWNSCALE);
        quarterHeight = Math.max(1, fbHeight / DOWNSCALE);

        sceneTexture = createTexture(fbWidth, fbHeight);
        texA = createTexture(quarterWidth, quarterHeight);
        texB = createTexture(quarterWidth, quarterHeight);
        fboA = createFramebuffer(texA);
        fboB = createFramebuffer(texB);
        if (program == 0) {
            program = compileProgram();
            uniformDirection = GL20.glGetUniformLocation(program, "u_dir");
            uniformTexture = GL20.glGetUniformLocation(program, "u_tex");
        }
        ready = true;
    }

    private static int createTexture(int width, int height) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return id;
    }

    private int createFramebuffer(int texture) {
        int fbo;
        if (coreFbo) {
            fbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, fbo);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(
                    org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,
                    org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, texture, 0);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        } else {
            fbo = EXTFramebufferObject.glGenFramebuffersEXT();
            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT, fbo);
            EXTFramebufferObject.glFramebufferTexture2DEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,
                    GL11.GL_TEXTURE_2D, texture, 0);
            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
        }
        return fbo;
    }

    private int currentFramebuffer() {
        return GL11.glGetInteger(coreFbo
                ? org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING
                : EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
    }

    private void bindFramebuffer(int fbo) {
        if (coreFbo) {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, fbo);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT, fbo);
        }
    }

    private int compileProgram() {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, readResource("blur.vsh"));
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, readResource("blur.fsh"));
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vertex);
        GL20.glAttachShader(id, fragment);
        GL20.glLinkProgram(id);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("blur program did not link: "
                    + GL20.glGetProgramInfoLog(id, 512));
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return id;
    }

    private static int compileShader(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("blur shader did not compile: "
                    + GL20.glGetShaderInfoLog(id, 512));
        }
        return id;
    }

    private static String readResource(String name) {
        InputStream in = BlurBackdrop.class.getClassLoader()
                .getResourceAsStream("assets/void/shaders/" + name);
        if (in == null) {
            throw new IllegalStateException("missing shader assets/void/shaders/" + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), Charset.forName("UTF-8"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read shader " + name, e);
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
                // Nothing useful to do.
            }
        }
    }

    /** Drops the GL objects; called when the screen closes or the size changes. */
    void release() {
        if (!ready) {
            return;
        }
        ready = false;
        try {
            GL11.glDeleteTextures(sceneTexture);
            GL11.glDeleteTextures(texA);
            GL11.glDeleteTextures(texB);
            if (coreFbo) {
                org.lwjgl.opengl.GL30.glDeleteFramebuffers(fboA);
                org.lwjgl.opengl.GL30.glDeleteFramebuffers(fboB);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(fboA);
                EXTFramebufferObject.glDeleteFramebuffersEXT(fboB);
            }
        } catch (RuntimeException e) {
            VoidLog.warn("could not release backdrop resources: " + e);
        }
    }
}
