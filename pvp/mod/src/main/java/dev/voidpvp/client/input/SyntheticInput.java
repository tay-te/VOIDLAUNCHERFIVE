package dev.voidpvp.client.input;

import dev.voidpvp.client.VoidLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Puts real events into LWJGL's own input queue, off unless {@code VOID_UI_INPUTDRIVE} is set.
 *
 * <p>Measuring input latency needs input, and the obvious way to get it — synthesising events at
 * the window server — does not work here. Mouse <em>motion</em> posted with
 * {@code CGEventPostToPid} reaches the client whatever the desktop is doing, but buttons and keys
 * do not: with the screen locked, or the window not key, the window server drops them. So an
 * unattended before/after measurement of a press cannot be driven from outside the process.</p>
 *
 * <p>This drives it from inside, one layer below Minecraft. LWJGL's Cocoa listener runs on the
 * AppKit thread and hands each event to {@code MacOSXNativeMouse.setButton} /
 * {@code MacOSXNativeKeyboard.putKeyEvent}, which append to the same {@code EventQueue} that
 * {@code Mouse.poll()} — called from {@code Display.update()}, once per rendered frame — copies
 * into the buffer {@code Mouse.next()} walks. Calling those two methods is therefore not a
 * simulation of the path under test: it <em>is</em> the path under test, entered at the point the
 * window server would have entered it, with a source timestamp we get to know exactly. Both are
 * safe to call off-thread — they are the synchronized methods the AppKit thread already uses.</p>
 *
 * <p>The one thing it does not exercise is the Cocoa listener itself, which nothing here changes.
 * Mouse motion is left to the outside driver, where the real thing works.</p>
 *
 * <p><b>Timing is deliberately not round.</b> The thing being measured is how long an event waits
 * for the next drain, so a driver that fires on a multiple of the drain period would sample one
 * phase of it over and over and report either the best case or the worst as if it were the mean.
 * The gaps below are jittered and prime-ish for that reason.</p>
 */
public final class SyntheticInput {

    public static final boolean ON = System.getenv("VOID_UI_INPUTDRIVE") != null;

    /** macOS virtual key code for {@code A} — a letter, so it is nobody's hotkey. */
    private static final int MAC_KEY_A = 0;

    /** macOS virtual key code for Right Shift, which is the menu key. */
    private static final int MAC_KEY_RSHIFT = 60;

    /**
     * {@code VOID_UI_INPUTDRIVE=rshift} drives the menu key instead of the latency cycle.
     *
     * <p>The toggle is worth driving on its own because it is the one path where an input event and
     * a polled key state have to agree: {@code VoidMenuScreen.keyPressed} latches the tap and
     * {@code VoidClient.pollHotkeys} acts on it a few statements later in the same frame, and
     * pumping input at frame rate changes when the first of those happens. Tapping it a few
     * hundred times unattended is the only way to see a toggle that fires twice or not at all.</p>
     */
    private static final boolean RSHIFT_MODE =
            "rshift".equals(System.getenv("VOID_UI_INPUTDRIVE"));

    private static Object nativeMouse;
    private static Object nativeKeyboard;
    private static Method setButton;
    private static Method mouseMoved;
    private static Method putKeyEvent;
    private static boolean started;

    private SyntheticInput() {
    }

    /** Game thread. Resolves the queue handles once the display exists, then drives forever. */
    public static synchronized void start() {
        if (!ON || started) {
            return;
        }
        started = true;
        try {
            resolve();
        } catch (Throwable t) {
            VoidLog.warn("inputdrive: cannot reach LWJGL's event queues (" + t + "); disabled");
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                drive();
            }
        }, "void-inputdrive");
        t.setDaemon(true);
        t.start();
        VoidLog.info("inputdrive: driving presses, wheel and keys into LWJGL's queue");
    }

    private static void resolve() throws Exception {
        Field impl = Class.forName("org.lwjgl.input.Keyboard").getDeclaredField("implementation");
        impl.setAccessible(true);
        Object display = impl.get(null);
        if (display == null) {
            throw new IllegalStateException("no input implementation yet");
        }
        Class<?> displayClass = display.getClass();
        if (!displayClass.getName().contains("MacOSX")) {
            throw new IllegalStateException("not the macOS backend: " + displayClass.getName());
        }
        Field mf = displayClass.getDeclaredField("mouse");
        mf.setAccessible(true);
        nativeMouse = mf.get(display);
        Field kf = displayClass.getDeclaredField("keyboard");
        kf.setAccessible(true);
        nativeKeyboard = kf.get(display);
        if (nativeMouse == null || nativeKeyboard == null) {
            throw new IllegalStateException("mouse or keyboard not created yet");
        }
        setButton = nativeMouse.getClass()
                .getMethod("setButton", int.class, int.class, long.class);
        mouseMoved = nativeMouse.getClass().getMethod("mouseMoved", float.class, float.class,
                float.class, float.class, float.class, long.class);
        putKeyEvent = nativeKeyboard.getClass().getMethod("putKeyEvent", int.class, byte.class,
                int.class, long.class, boolean.class);
        setButton.setAccessible(true);
        mouseMoved.setAccessible(true);
        putKeyEvent.setAccessible(true);
    }

    private static final Random RNG = new Random(20250907L);

    private static void nap(int baseMs, int jitterMs) {
        try {
            Thread.sleep(baseMs + RNG.nextInt(jitterMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void drive() {
        // Let the menu settle before the first event.
        nap(6000, 1);
        while (true) {
            try {
                if (RSHIFT_MODE) {
                    // A tap, not a hold: down and up close together, the way a player closes the
                    // menu. The gap is jittered for the same reason as everything else here.
                    putKeyEvent.invoke(nativeKeyboard, MAC_KEY_RSHIFT, Byte.valueOf((byte) 1),
                            0, Long.valueOf(System.nanoTime()), Boolean.FALSE);
                    nap(40, 30);
                    putKeyEvent.invoke(nativeKeyboard, MAC_KEY_RSHIFT, Byte.valueOf((byte) 0),
                            0, Long.valueOf(System.nanoTime()), Boolean.FALSE);
                    nap(900, 400);
                    continue;
                }
                for (int i = 0; i < 40; i++) {
                    setButton.invoke(nativeMouse, 0, 1, Long.valueOf(System.nanoTime()));
                    nap(53, 37);
                    setButton.invoke(nativeMouse, 0, 0, Long.valueOf(System.nanoTime()));
                    nap(71, 43);
                }
                for (int i = 0; i < 60; i++) {
                    // dz != 0 selects the wheel branch; the fourth argument x 120 is the delta,
                    // so 1.0 is one detent. Alternating, so the page does not simply hit an end.
                    float notch = (i / 15) % 2 == 0 ? 1.0f : -1.0f;
                    mouseMoved.invoke(nativeMouse, Float.valueOf(0f), Float.valueOf(0f),
                            Float.valueOf(0f), Float.valueOf(notch), Float.valueOf(1f),
                            Long.valueOf(System.nanoTime()));
                    nap(29, 19);
                }
                for (int i = 0; i < 40; i++) {
                    putKeyEvent.invoke(nativeKeyboard, MAC_KEY_A, Byte.valueOf((byte) 1),
                            (int) 'a', Long.valueOf(System.nanoTime()), Boolean.FALSE);
                    nap(53, 37);
                    putKeyEvent.invoke(nativeKeyboard, MAC_KEY_A, Byte.valueOf((byte) 0),
                            (int) 'a', Long.valueOf(System.nanoTime()), Boolean.FALSE);
                    nap(71, 43);
                }
            } catch (Throwable t) {
                VoidLog.warn("inputdrive: stopped (" + t + ")");
                return;
            }
        }
    }
}
