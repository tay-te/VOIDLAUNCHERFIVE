package dev.voidpvp.client.cosmetic;

import dev.voidpvp.client.VoidLog;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Compiles the cosmetics' GLSL 1.20 programs from {@code assets/void/shaders/}. Best-effort, like
 * every other program in the mod: a shader that will not compile is logged once and the caller
 * falls back to the fixed-function path, so a driver quirk costs looks, never a crash.
 */
final class GlProgram {

    private GlProgram() {
    }

    /** True when the context can run GLSL 1.20 at all. */
    static boolean available() {
        try {
            return GLContext.getCapabilities().OpenGL20;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The linked program, or 0 (logged) when it will not build. Render thread. */
    static int load(String vsh, String fsh) {
        int v = 0, f = 0, p = 0;
        try {
            v = shader(GL20.GL_VERTEX_SHADER, vsh);
            f = shader(GL20.GL_FRAGMENT_SHADER, fsh);
            p = GL20.glCreateProgram();
            GL20.glAttachShader(p, v);
            GL20.glAttachShader(p, f);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("link: " + GL20.glGetProgramInfoLog(p, 2048));
            }
            return p;
        } catch (RuntimeException e) {
            VoidLog.warn("cosmetics: program " + vsh + "/" + fsh + " unavailable, using the fixed-function path: " + e.getMessage());
            if (p != 0) {
                GL20.glDeleteProgram(p);
            }
            return 0;
        } finally {
            if (v != 0) {
                GL20.glDeleteShader(v);
            }
            if (f != 0) {
                GL20.glDeleteShader(f);
            }
        }
    }

    private static int shader(int type, String name) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, read(name));
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(id, 2048);
            GL20.glDeleteShader(id);
            throw new IllegalStateException(name + ": " + log);
        }
        return id;
    }

    private static String read(String name) {
        InputStream in = GlProgram.class.getClassLoader().getResourceAsStream("assets/void/shaders/" + name);
        if (in == null) {
            throw new IllegalStateException("missing shader " + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read shader " + name, e);
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
                // nothing to do
            }
        }
    }
}
