package dev.voidpvp.client.render;

import dev.voidpvp.client.VoidLog;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.KHRDebugCallback;

/**
 * Dev-only GL error tracing, off unless the JVM gets {@code -Dvoid.dev.gldebug=true}
 * ({@code ./gradlew runBench -PvoidGlDebug}).
 *
 * <p>Vanilla only learns about a GL error once a frame, in {@code checkGLError("Post render")},
 * which says that something went wrong but not what. With this on, the driver's debug output is
 * switched on in synchronous mode and every error it reports is logged with the Java stack of
 * the call that raised it, so the offending call is named rather than guessed at. Drivers report
 * errors through the callback even in a context that was not created as a debug context (NVIDIA
 * and Mesa do); where the extension is missing, this says so and does nothing.</p>
 */
public final class GlDebug {

    public static final boolean ON = Boolean.getBoolean("void.dev.gldebug");

    /** The callback must stay reachable: LWJGL holds it by a global JNI reference only while set. */
    private static KHRDebugCallback callback;
    private static boolean installed;
    private static int reported;

    private GlDebug() {
    }

    /** Render thread, GL context current. Idempotent; a no-op unless {@link #ON}. */
    public static void install() {
        if (!ON || installed) {
            return;
        }
        installed = true;
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL43 && !caps.GL_KHR_debug) {
            VoidLog.error("gldebug: no KHR_debug in this context; errors stay anonymous");
            return;
        }
        callback = new KHRDebugCallback(new KHRDebugCallback.Handler() {
            @Override
            public void handleMessage(int source, int type, int id, int severity, String message) {
                if (type != GL43.GL_DEBUG_TYPE_ERROR || reported >= 20) {
                    return;
                }
                reported++;
                StringBuilder sb = new StringBuilder("gldebug: GL error 0x")
                        .append(Integer.toHexString(id)).append(": ").append(message);
                StackTraceElement[] stack = new Throwable().getStackTrace();
                for (int i = 1; i < Math.min(stack.length, 24); i++) {
                    sb.append("\n    at ").append(stack[i]);
                }
                VoidLog.error(sb.toString());
            }
        });
        // Anything already pending belongs to whoever ran before us; clear it so the first
        // report is a real one.
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // drain
        }
        GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
        GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        GL43.glDebugMessageCallback(callback);
        VoidLog.error("gldebug: synchronous debug output on (not an error; logged at error level so a dev run shows it)");
    }

    /**
     * Logs and clears any pending error, naming {@code where}. For bracketing a block of our own
     * GL calls when the driver's callback is not available. A no-op unless {@link #ON}.
     */
    public static void check(String where) {
        if (!ON) {
            return;
        }
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            VoidLog.error("gldebug: glGetError " + error + " at " + where);
        }
    }
}
