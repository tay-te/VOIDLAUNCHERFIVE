package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Binding to {@code mod/native}'s Java-facing Ultralight API.
 *
 * <p>{@code mod/native/} belongs to another owner (CONTRACTS.md) and its Java
 * sources are compiled into this JAR from {@code ../native/java}; this class
 * is the only place that touches them. It reaches them by reflection for two
 * reasons:</p>
 *
 * <ol>
 *   <li>The JAR must build and run when {@code mod/native/} is absent — that is
 *       what {@link NullWebView} is for, and a hard import would make the
 *       absence a compile error rather than a runtime fallback.</li>
 *   <li>The package the architecture specifies, {@code dev.void.ultralight},
 *       <em>cannot exist</em>: {@code void} is a Java keyword and is not a legal
 *       package name segment. Both owners have to rename that segment, and
 *       reflection means the two halves do not have to guess the same new name
 *       at compile time. The name is resolved at runtime from, in order:
 *       {@code -Dvoid.ultralight.package}, the build-generated resource
 *       {@code assets/void/native-package.txt}, then the candidates below.</li>
 * </ol>
 */
final class NativeUltralight {

    /** Where {@code Ultralight.load()} extracts natives from, and resolves URLs against. */
    static final String RESOURCE_PREFIX = "assets/void/ui/";

    private static final String PACKAGE_PROPERTY = "void.ultralight.package";
    private static final String PACKAGE_RESOURCE = "assets/void/native-package.txt";
    private static final List<String> CANDIDATES = Arrays.asList(
            "dev.voidpvp.ultralight",
            "dev.voidclient.ultralight",
            "dev.ultralight");

    private static boolean resolved;
    private static String failure;

    static Method loadNatives;
    static Method createRenderer;
    static Method rendererUpdate;
    static Method rendererRender;
    static Method rendererCreateView;
    static Method rendererClose;
    static Method viewLoadUrl;
    static Method viewResize;
    static Method viewSetDeviceScale;
    static Method viewGlTextureId;
    static Method viewIsDirty;
    static Method viewFireMouseEvent;
    static Method viewFireKeyEvent;
    static Method viewFireScrollEvent;
    static Method viewEvaluateScript;
    static Method viewSetMessageHandler;
    static Method viewSetFocus;
    static Method viewClose;

    private NativeUltralight() {
    }

    /** True when the binding's classes are on the classpath and shaped as expected. */
    static synchronized boolean isPresent() {
        resolve();
        return failure == null;
    }

    /** Why the binding is unusable, or null when it is fine. */
    static synchronized String failure() {
        resolve();
        return failure;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        String pkg = findPackage();
        if (pkg == null) {
            failure = "no dev.*.ultralight binding on the classpath (mod/native not built in)";
            return;
        }
        try {
            Class<?> ultralight = Class.forName(pkg + ".Ultralight");
            Class<?> renderer = Class.forName(pkg + ".Renderer");
            Class<?> view = Class.forName(pkg + ".View");

            loadNatives = ultralight.getMethod("load");
            createRenderer = ultralight.getMethod("createRenderer", String.class);

            rendererUpdate = renderer.getMethod("update");
            rendererRender = renderer.getMethod("render");
            rendererCreateView = renderer.getMethod("createView",
                    int.class, int.class, boolean.class);
            rendererClose = renderer.getMethod("close");

            viewLoadUrl = view.getMethod("loadUrl", String.class);
            viewResize = view.getMethod("resize", int.class, int.class);
            viewSetDeviceScale = view.getMethod("setDeviceScale", double.class);
            viewGlTextureId = view.getMethod("glTextureId");
            viewIsDirty = view.getMethod("isDirty");
            viewFireMouseEvent = view.getMethod("fireMouseEvent",
                    int.class, int.class, int.class, int.class);
            viewFireKeyEvent = view.getMethod("fireKeyEvent",
                    int.class, int.class, int.class, String.class);
            viewFireScrollEvent = view.getMethod("fireScrollEvent", int.class, int.class);
            viewEvaluateScript = view.getMethod("evaluateScript", String.class);
            viewSetMessageHandler = view.getMethod("setMessageHandler", Function.class);
            viewSetFocus = view.getMethod("setFocus", boolean.class);
            viewClose = view.getMethod("close");
            VoidLog.info("Ultralight binding found in package " + pkg);
        } catch (ClassNotFoundException e) {
            failure = "incomplete Ultralight binding in " + pkg + ": " + e.getMessage();
        } catch (NoSuchMethodException e) {
            failure = "Ultralight binding in " + pkg + " does not match the agreed API: "
                    + e.getMessage();
        }
    }

    private static String findPackage() {
        String declared = System.getProperty(PACKAGE_PROPERTY);
        if (declared != null && exists(declared)) {
            return declared;
        }
        String fromBuild = readPackageResource();
        if (fromBuild != null && exists(fromBuild)) {
            return fromBuild;
        }
        for (String candidate : CANDIDATES) {
            if (exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean exists(String pkg) {
        try {
            Class.forName(pkg + ".Ultralight", false, NativeUltralight.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Written at build time from the package {@code ../native/java} actually declares. */
    private static String readPackageResource() {
        InputStream in = NativeUltralight.class.getClassLoader()
                .getResourceAsStream(PACKAGE_RESOURCE);
        if (in == null) {
            return null;
        }
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, Charset.forName("UTF-8")));
            try {
                String line = r.readLine();
                return line == null || line.trim().isEmpty() ? null : line.trim();
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@code Ultralight.load()} then {@code Ultralight.createRenderer(prefix)}.
     *
     * @throws UnsatisfiedLinkError when the natives for this platform are not
     *         in the JAR or will not load — the caller falls back to
     *         {@link NullWebView}
     */
    static Object createRenderer() {
        String why = failure();
        if (why != null) {
            throw new UnsatisfiedLinkError(why);
        }
        try {
            loadNatives.invoke(null);
            return createRenderer.invoke(null, RESOURCE_PREFIX);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnsatisfiedLinkError) {
                throw (UnsatisfiedLinkError) cause;
            }
            throw new UnsatisfiedLinkError("Ultralight.createRenderer failed: " + cause);
        } catch (IllegalAccessException e) {
            throw new UnsatisfiedLinkError("Ultralight binding is not accessible: " + e);
        }
    }

    /** Invokes a resolved binding method, turning checked reflection into runtime failure. */
    static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Ultralight." + method.getName() + " threw",
                    e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Ultralight." + method.getName()
                    + " is not accessible", e);
        }
    }
}
