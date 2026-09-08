package dev.voidpvp.client.render;

import dev.voidpvp.client.VoidLog;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Draws the shadows the overlay's CSS does not (§6.4).
 *
 * <p>Ultralight's CPU rasteriser is fast at text, fills and vectors and slow at exactly one thing:
 * a blurred {@code box-shadow}. It is a blur pass per element per repaint, and because the shadow
 * extends past its element it also enlarges the damage rectangle, so a change to one card repaints
 * far more than the card. Measured in game on a mod toggle — 43-80 ms a repaint with the authored
 * ramp against 15-25 ms without, more than every other optimisation combined. Tightening the radii
 * changed nothing, because the cost is having a pass at all rather than how wide it is.
 *
 * <p>So the design keeps its shadows and the rasteriser stops drawing them: the page reports where
 * the shadowed surfaces are through {@code void.setSurfaces}, and they are drawn here instead, on
 * the GPU that is already drawing the game, underneath the view. The values come from the authored
 * {@code --shadow-*-gl} tokens, which the token build copies verbatim out of
 * {@code design/tokens.css} — so this draws the Figma shadow, and changing that file changes both
 * the launcher's CSS and this, with nothing to keep in sync by hand.
 *
 * <p>Best-effort, like {@link dev.voidpvp.client.screen.BlurBackdrop}: if the program will not come
 * up the shadows are skipped and the menu still works. A shadow is worth exactly zero crashes.</p>
 */
public final class ShadowPass {

    private boolean disabled;
    private int program;
    private int uniformHalf;
    private int uniformRadius;
    private int uniformBlur;
    private int uniformColor;

    /**
     * Draws every visible surface's shadow, in order.
     *
     * @param surfaces  what the page reported, in CSS pixels
     * @param scale     device pixels per CSS pixel — the same factor the view rasterises at
     */
    public void draw(List<EffectSurface> surfaces, double scale) {
        if (disabled || surfaces == null || surfaces.isEmpty()) {
            return;
        }
        if (!ensure()) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        // Straight alpha: the shader emits the colour unmultiplied, matching how CSS composites a
        // shadow over what is behind it.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glUseProgram(program);

        try {
            for (EffectSurface s : surfaces) {
                if (s.visible()) {
                    drawOne(s, scale);
                }
            }
        } catch (RuntimeException e) {
            disabled = true;
            VoidLog.warn("shadow pass failed, disabling: " + e);
        } finally {
            GL20.glUseProgram(0);
            GL11.glPopAttrib();
        }
    }

    private void drawOne(EffectSurface s, double scale) {
        // Everything the shader works in is device pixels, so convert once here.
        float spread = (float) (s.spread * scale);
        float halfW = (float) (s.width * scale) * 0.5f + spread;
        float halfH = (float) (s.height * scale) * 0.5f + spread;
        if (halfW <= 0 || halfH <= 0) {
            return;
        }
        float blur = (float) Math.max(0.0, s.blur * scale);
        // The radius follows the spread, the way CSS grows or shrinks a rounded shadow, and can
        // never exceed the half-extent or the SDF turns inside out.
        float radius = Math.max(0f, Math.min(Math.min(halfW, halfH),
                (float) (s.radius * scale) + spread));

        float centreX = (float) ((s.x + s.width * 0.5) * scale + s.dx * scale);
        float centreY = (float) ((s.y + s.height * 0.5) * scale + s.dy * scale);

        // The quad has to cover the whole falloff, not just the box: the shader fades out across
        // `blur` either side of the edge, so anything tighter would clip the shadow square.
        float padding = blur + 2f;
        float quadW = halfW + padding;
        float quadH = halfH + padding;

        GL20.glUniform2f(uniformHalf, halfW, halfH);
        GL20.glUniform1f(uniformRadius, radius);
        GL20.glUniform1f(uniformBlur, blur);
        GL20.glUniform4f(uniformColor, s.red, s.green, s.blue, s.alpha);

        // Texture coordinates carry the position in the shadow's own space, origin at its centre,
        // which is what the fragment stage evaluates the distance field against.
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(-quadW, -quadH);
        GL11.glVertex2f(centreX - quadW, centreY - quadH);
        GL11.glTexCoord2f(-quadW, quadH);
        GL11.glVertex2f(centreX - quadW, centreY + quadH);
        GL11.glTexCoord2f(quadW, quadH);
        GL11.glVertex2f(centreX + quadW, centreY + quadH);
        GL11.glTexCoord2f(quadW, -quadH);
        GL11.glVertex2f(centreX + quadW, centreY - quadH);
        GL11.glEnd();
    }

    private boolean ensure() {
        if (program != 0) {
            return true;
        }
        try {
            if (!GLContext.getCapabilities().OpenGL20) {
                disabled = true;
                VoidLog.warn("no GLSL available; panel shadows will not be drawn");
                return false;
            }
            program = compileProgram();
            uniformHalf = GL20.glGetUniformLocation(program, "u_half");
            uniformRadius = GL20.glGetUniformLocation(program, "u_radius");
            uniformBlur = GL20.glGetUniformLocation(program, "u_blur");
            uniformColor = GL20.glGetUniformLocation(program, "u_color");
            return true;
        } catch (RuntimeException e) {
            disabled = true;
            VoidLog.warn("shadow program did not come up, shadows disabled: " + e);
            return false;
        }
    }

    /** Frees the program. Requires a current context. */
    public void shutdown() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }

    private static int compileProgram() {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, readResource("shadow.vsh"));
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, readResource("shadow.fsh"));
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vertex);
        GL20.glAttachShader(id, fragment);
        GL20.glLinkProgram(id);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("shadow program did not link: "
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
            throw new IllegalStateException("shadow shader did not compile: "
                    + GL20.glGetShaderInfoLog(id, 512));
        }
        return id;
    }

    private static String readResource(String name) {
        InputStream in = ShadowPass.class.getClassLoader()
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
                // Nothing useful to do; the source is already read or already lost.
            }
        }
    }
}
