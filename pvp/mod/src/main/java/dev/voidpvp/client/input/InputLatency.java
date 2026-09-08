package dev.voidpvp.client.input;

import dev.voidpvp.client.VoidLog;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where an input event spends its time on the way to the page, off unless
 * {@code VOID_UI_INPUTLOG} is set.
 *
 * <p>"The menu runs at 20 Hz" is a claim about a clock, and it was made by reading
 * {@code MinecraftClient.tick} rather than by timing anything. This turns it into numbers, at the
 * four places an event can be delayed:</p>
 *
 * <ol>
 *   <li><b>source</b> — {@code Mouse.getEventNanoseconds()} / {@code Keyboard.getEventNanoseconds()},
 *       stamped by LWJGL's Cocoa listener on the AppKit thread as the event arrives from the window
 *       server, i.e. before Minecraft has any say in it.</li>
 *   <li><b>callback</b> — {@code System.nanoTime()} inside {@code VoidMenuScreen.mouseClicked} and
 *       friends. {@code source -> callback} is the whole of Minecraft's own scheduling: LWJGL's
 *       queue is filled by {@code Mouse.poll()} inside {@code Display.update()}, once per rendered
 *       frame, and drained by {@code Screen.handleInput()}, from wherever that is called.</li>
 *   <li><b>dispatch</b> — when the job posted to {@link dev.voidpvp.client.ui.UiHost} actually runs
 *       on the UI thread and reaches {@code view.fireMouseEvent}. This is the thread hop.</li>
 *   <li><b>page</b> — a counter the page increments from a capture-phase listener, read back on the
 *       UI thread. Not a latency so much as an arrival check: it answers "did every event get
 *       there", which is the other half of "is the input path lossy".</li>
 * </ol>
 *
 * <p>Alongside those, {@link #frame()} and {@link #drain()} record the <em>cadence</em> of the
 * queue drain, which needs no input at all to measure: if {@code Screen.handleInput()} really runs
 * on the tick then drains land ~20 times a second with ~5 rendered frames between them, and an
 * event that arrived just after one waits for the next. That is the quantisation, stated without
 * having to synthesise a click.</p>
 *
 * <p>Everything here is dead when {@link #ON} is false — the callers guard on it, so a shipped
 * client does not even read the clock.</p>
 */
public final class InputLatency {

    public static final boolean ON = System.getenv("VOID_UI_INPUTLOG") != null;

    /** How often the histograms are printed. */
    private static final long DUMP_NANOS = 3_000_000_000L;

    private InputLatency() {
    }

    // -----------------------------------------------------------------
    // Histograms
    // -----------------------------------------------------------------

    private static final class Bucket {
        private final double[] samples = new double[2048];
        private int kept;
        private int total;
        private double sum;
        private double max;
        private double min = Double.MAX_VALUE;

        void add(double ms) {
            total++;
            sum += ms;
            if (ms > max) {
                max = ms;
            }
            if (ms < min) {
                min = ms;
            }
            if (kept < samples.length) {
                samples[kept++] = ms;
            }
        }

        String line() {
            double[] s = Arrays.copyOf(samples, kept);
            Arrays.sort(s);
            return String.format("n=%-4d min=%5.1f  mean=%5.1f  p50=%5.1f  p95=%5.1f  max=%5.1f",
                    total, min, sum / total, pct(s, 0.50), pct(s, 0.95), max);
        }

        private static double pct(double[] sorted, double p) {
            if (sorted.length == 0) {
                return 0;
            }
            int i = (int) Math.floor(p * (sorted.length - 1));
            return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
        }
    }

    /** Insertion-ordered so the dump reads press, release, drag, wheel, key, not alphabetically. */
    private static final Map<String, Bucket> BUCKETS = new LinkedHashMap<String, Bucket>();

    private static synchronized void record(String key, double ms) {
        Bucket b = BUCKETS.get(key);
        if (b == null) {
            b = new Bucket();
            BUCKETS.put(key, b);
        }
        b.add(ms);
    }

    // -----------------------------------------------------------------
    // 1 -> 2: the LWJGL event's own timestamp against the callback
    // -----------------------------------------------------------------

    /**
     * Whether LWJGL's event clock and {@link System#nanoTime()} are the same clock.
     *
     * <p>They should be — both are mach uptime on macOS, {@code NSEvent.timestamp} being seconds
     * since boot and {@code nanoTime} being {@code mach_absolute_time} — but "should be" is how
     * this whole investigation started, so the first sample of each device prints both raw values
     * and the age is only trusted when it lands in a plausible range.</p>
     */
    private static boolean mouseClockChecked;
    private static boolean keyClockChecked;
    private static boolean mouseClockOk;
    private static boolean keyClockOk;

    /** Game thread, from a {@code Screen} input callback. {@code kind} is press/release/drag/... */
    public static void source(String kind, long eventNanos, boolean keyboard) {
        if (!ON) {
            return;
        }
        long now = System.nanoTime();
        boolean checked = keyboard ? keyClockChecked : mouseClockChecked;
        if (!checked) {
            double ms = (now - eventNanos) / 1_000_000.0;
            // A real event is at most a few frames old. Anything outside this is a different clock
            // (or an epoch), and reporting it as latency would be worse than reporting nothing.
            boolean ok = eventNanos != 0 && ms >= -1.0 && ms < 2000.0;
            VoidLog.info("inputlog: " + (keyboard ? "keyboard" : "mouse") + " event clock "
                    + (ok ? "agrees with nanoTime" : "DOES NOT agree with nanoTime — ages suppressed")
                    + " (event=" + eventNanos + " now=" + now + " delta=" + String.format("%.1f", ms)
                    + " ms)");
            if (keyboard) {
                keyClockChecked = true;
                keyClockOk = ok;
            } else {
                mouseClockChecked = true;
                mouseClockOk = ok;
            }
        }
        if (keyboard ? keyClockOk : mouseClockOk) {
            record("1 source->callback  " + kind, (now - eventNanos) / 1_000_000.0);
        }
    }

    // -----------------------------------------------------------------
    // 2 -> 3: the callback against the UI thread actually firing it
    // -----------------------------------------------------------------

    /** Game thread, at the moment the job is handed to {@code UiHost.post}. */
    public static long stamp() {
        return ON ? System.nanoTime() : 0L;
    }

    /** UI thread, inside the posted job, immediately before the view call. */
    public static void dispatched(String kind, long stamped) {
        if (!ON || stamped == 0L) {
            return;
        }
        record("2 callback->dispatch " + kind, (System.nanoTime() - stamped) / 1_000_000.0);
    }

    // -----------------------------------------------------------------
    // Drain cadence — the quantisation, measurable with nobody at the keyboard
    // -----------------------------------------------------------------

    private static long framesSinceDrain;
    private static long framesInWindow;
    private static long drainsInWindow;
    private static long worstFramesPerDrain;
    private static long lastDrainNanos;

    /** Game thread, once per rendered frame, while the menu is up. */
    public static synchronized void frame() {
        if (!ON) {
            return;
        }
        framesSinceDrain++;
        framesInWindow++;
    }

    /** Game thread, every time {@code Screen.handleInput} drains LWJGL's queues. */
    public static synchronized void drain() {
        if (!ON) {
            return;
        }
        long now = System.nanoTime();
        drainsInWindow++;
        if (framesSinceDrain > worstFramesPerDrain) {
            worstFramesPerDrain = framesSinceDrain;
        }
        if (lastDrainNanos != 0) {
            record("0 drain gap", (now - lastDrainNanos) / 1_000_000.0);
        }
        lastDrainNanos = now;
        framesSinceDrain = 0;
    }

    // -----------------------------------------------------------------
    // 4: the page's own arrival counters
    // -----------------------------------------------------------------

    private static volatile String pageCounts = "";

    /** UI thread: whatever {@code window.__voidIn} last reported. */
    public static void page(String counts) {
        if (ON) {
            pageCounts = counts;
        }
    }

    /** How many of each kind we handed to the view, so the page's counts have something to match. */
    private static final Map<String, int[]> SENT = new LinkedHashMap<String, int[]>();

    /** UI thread, inside the posted job. */
    public static synchronized void sent(String kind) {
        if (!ON) {
            return;
        }
        int[] n = SENT.get(kind);
        if (n == null) {
            SENT.put(kind, new int[] {1});
        } else {
            n[0]++;
        }
    }

    // -----------------------------------------------------------------
    // Dump
    // -----------------------------------------------------------------

    private static long lastDumpNanos;

    /** Game thread, once per frame. Prints at most every {@link #DUMP_NANOS}. */
    public static synchronized void maybeDump() {
        if (!ON) {
            return;
        }
        long now = System.nanoTime();
        if (lastDumpNanos == 0) {
            lastDumpNanos = now;
            return;
        }
        if (now - lastDumpNanos < DUMP_NANOS) {
            return;
        }
        double secs = (now - lastDumpNanos) / 1_000_000_000.0;
        lastDumpNanos = now;

        VoidLog.info(String.format(
                "inputlog: %.1f drains/s over %.1f fps — %.2f frames per drain, worst %d",
                drainsInWindow / secs, framesInWindow / secs,
                drainsInWindow == 0 ? 0.0 : (double) framesInWindow / drainsInWindow,
                worstFramesPerDrain));
        for (Map.Entry<String, Bucket> e : BUCKETS.entrySet()) {
            VoidLog.info("inputlog:   " + pad(e.getKey()) + " " + e.getValue().line());
        }
        if (!SENT.isEmpty() || !pageCounts.isEmpty()) {
            StringBuilder sb = new StringBuilder("inputlog:   sent");
            for (Map.Entry<String, int[]> e : SENT.entrySet()) {
                sb.append(' ').append(e.getKey()).append('=').append(e.getValue()[0]);
            }
            sb.append("   page ").append(pageCounts.isEmpty() ? "(not read)" : pageCounts);
            VoidLog.info(sb.toString());
        }
        framesInWindow = 0;
        drainsInWindow = 0;
        worstFramesPerDrain = 0;
        BUCKETS.clear();
    }

    private static String pad(String s) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 24) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
