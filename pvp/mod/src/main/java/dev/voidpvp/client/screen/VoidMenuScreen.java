package dev.voidpvp.client.screen;

import dev.voidpvp.client.HiDpi;
import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.input.InputLatency;
import dev.voidpvp.client.input.KeyNames;
import dev.voidpvp.client.ui.UiHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The Right-Shift menu (§6.4).
 *
 * <p>It exists for two reasons only. First, opening a {@code Screen} is how
 * Minecraft releases the mouse, and the menu needs a cursor. Second, the
 * blurred backdrop of §6.4 is GL work that has to happen before the UI is
 * painted, because Ultralight has no {@code backdrop-filter} (§9).</p>
 *
 * <p>Everything else on screen is HTML: the same Ultralight view the HUD uses,
 * painted here on top of the backdrop, with the React app deciding which layer
 * is visible from the {@code menu} bridge event.</p>
 */
public final class VoidMenuScreen extends Screen {

    /** The {@code rgba(0, 0, 0, 0.45)} tint of §6.4. */
    private static final int TINT = 0x73000000;

    private final VoidClient voidClient;
    private final BlurBackdrop backdrop = new BlurBackdrop();
    private final dev.voidpvp.client.render.ShadowPass shadows =
            new dev.voidpvp.client.render.ShadowPass();

    public VoidMenuScreen(VoidClient voidClient) {
        // Deliberately not called `client`: Screen already has a field of that
        // name holding the MinecraftClient, and shadowing it reads badly.
        this.voidClient = voidClient;
    }

    /** The game keeps running behind the menu; this is a PVP client (§6.4). */
    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void init() {
        voidClient.onMenuOpened();
        UiHost ui = voidClient.ui();
        ui.setFocus(true);
        // Drop whatever the wheel accumulated while the menu was shut. Nothing in 1.8.9 reads
        // Mouse.getDWheel() outside a container screen, so it has been adding up since the last
        // one was closed — hotbar scrolling included — and the first read here would otherwise
        // deliver all of it as one jump. See pollWheel.
        Mouse.getDWheel();
        wheelRemainder = 0;
    }

    @Override
    public void removed() {
        backdrop.release();
        shadows.shutdown();
        voidClient.ui().setFocus(false);
        voidClient.onMenuClosed();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        MinecraftClient mc = voidClient.minecraft();
        UiHost ui = voidClient.ui();
        int fbWidth = Math.max(1, mc.width);
        int fbHeight = Math.max(1, mc.height);

        // 1-3. framebuffer copy, two-pass blur, draw back with the tint
        backdrop.draw(this.width, this.height, fbWidth, fbHeight, TINT);

        // 3b. the shadows the overlay's CSS deliberately does not draw. They go between the
        // backdrop and the UI so they fall on the blurred game, exactly where a CSS box-shadow
        // would have landed — but on the GPU, where a blur is free. See ShadowPass for why.
        //
        // The page measures in its own CSS pixels; this draws under Minecraft's GUI projection.
        // Those are two different spaces — the view is sized by a design-fit factor and the GUI by
        // the player's GUI scale — so convert by the ratio the two actually have, rather than by
        // the view's device scale, which would land the shadow at the wrong size and offset.
        int cssWidth = ui.logicalWidth();
        if (cssWidth > 0) {
            shadows.draw(voidClient.surfaces(), (double) this.width / cssWidth);
        }

        // 4. the menu layer, from the same view the HUD uses
        voidClient.pumpUi();
        ui.paint(this.width, this.height);

        forwardMouseMove(ui);
    }

    private void forwardMouseMove(UiHost ui) {
        double scale = ui.deviceScale();
        if (scale <= 0) {
            return;
        }
        ui.mouseMoved(viewX(ui), viewY(ui));
    }

    // -----------------------------------------------------------------
    // Input forwarding (§6.3). Minecraft has released the mouse; every event
    // here belongs to the view unless it is the key that closes the menu.
    // -----------------------------------------------------------------

    /**
     * Drains LWJGL's input queues into this screen, once per rendered frame.
     *
     * <p>Called from the head of {@code MinecraftClient.runGameLoop} ({@code MinecraftClientMixin}
     * -> {@code VoidClient.pumpMenuInput}), <b>not</b> from {@link #handleInput}, which is where a
     * screen's input naturally lives and which is the whole problem.</p>
     *
     * <p>Every mouse and key event the menu receives arrives through one LWJGL ring buffer. That
     * buffer is <em>filled</em> by {@code Mouse.poll()}/{@code Keyboard.poll()} inside
     * {@code Display.update()} — once per rendered frame, so 110 times a second here — and
     * <em>emptied</em> by {@code Screen.handleInput()}, which 1.8.9 calls from
     * {@code MinecraftClient.tick()}. The tick runs at 20 Hz. Measured, with nobody at the
     * keyboard: 20.0 drains a second at 112 fps, 5.6 rendered frames per drain, mean gap 50.0 ms.
     * So an event that landed in the buffer waited a uniform 0-50 ms for someone to come and get
     * it — measured at the callback, 27 ms mean and 55 ms worst, for every class of input alike:
     * press, release, drag, key down, key up. That is the 50 ms quantisation floor, and it was
     * under a click as much as under a scroll.</p>
     *
     * <p>Once per frame is the floor, not a choice: the buffer only receives anything at
     * {@code Display.update()}, so draining more often than that would find it empty. Frame rate
     * is therefore as fast as this path can be made to go, and the head of the game loop is where
     * in the frame it is cheapest — it is a few statements ahead of where the tick would have run
     * {@code handleInput} anyway, which is what makes every expectation vanilla has of these
     * callbacks still hold. In particular {@code handleKeyboard} ends in
     * {@code MinecraftClient.handleKeyInput}, which can take a screenshot or toggle full screen;
     * doing that from here is doing it exactly where 1.8.9 already does it, and doing it from
     * inside {@link #render} — the other obvious place to pump from — would have been doing it
     * with the frame's framebuffer bound.</p>
     *
     * <p>{@code handleInput} is deliberately left alone rather than emptied. It still runs on the
     * tick and still drains, and will find the queues empty except for the sliver of a frame
     * between this call and the tick. Nothing is delivered twice: a queue read consumes.</p>
     *
     * <p>The screen is re-checked on every iteration because an event can close the menu —
     * Right Shift, Escape, or a keybind capture finishing — and the events behind it then belong
     * to whatever screen replaced this one, which is what the tick's own drain will do with them.</p>
     */
    public void pumpInput() {
        InputLatency.frame();
        InputLatency.maybeDump();
        InputLatency.drain();
        MinecraftClient mc = voidClient.minecraft();
        if (mc == null) {
            return;
        }
        if (Mouse.isCreated()) {
            while (mc.currentScreen == this && Mouse.next()) {
                handleMouse();
            }
        }
        if (Keyboard.isCreated()) {
            while (mc.currentScreen == this && Keyboard.next()) {
                handleKeyboard();
            }
        }
        if (mc.currentScreen != this) {
            return;
        }
        UiHost ui = voidClient.ui();
        // After the events, so a notch and the click that follows it stay in the order they were
        // made. Here rather than in render() for the same reason as everything else above: this is
        // half a frame earlier, and it keeps the menu's whole input path in one place.
        pollWheel(ui);
        // Profiling only (VOID_UI_CLICKTEST). Here so that a synthetic click enters mouseDown at
        // the same point in the frame a real one now does, which is what makes its numbers
        // comparable to a player's.
        ui.driveClickTest();
    }

    /**
     * Tracing only ({@code VOID_UI_INPUTLOG}): the tick's own drain, which now finds the queues
     * empty. Left in place — see {@link #pumpInput} for why it is not overridden away.
     */
    @Override
    public void handleInput() {
        InputLatency.drain();
        super.handleInput();
    }

    /**
     * Tracing only ({@code VOID_UI_INPUTLOG}): every mouse event, timed against LWJGL's own stamp.
     *
     * <p>Overridden here rather than measured in the individual callbacks because this is where
     * every class of mouse input arrives, including the ones that produce no callback at all: a
     * cursor move with no button held reaches {@code super.handleMouse} and stops there, and a
     * wheel delta is carried on a move event that {@code Screen} does not read.</p>
     */
    @Override
    public void handleMouse() {
        if (InputLatency.ON) {
            int button = Mouse.getEventButton();
            String kind;
            if (button != -1) {
                kind = Mouse.getEventButtonState() ? "press" : "release";
            } else {
                // A drag is a move with a button down; LWJGL does not distinguish them, and the
                // two wait in the same queue for the same drain, so this only names them apart.
                kind = Mouse.isButtonDown(0) ? "drag" : "move";
            }
            InputLatency.source(kind, Mouse.getEventNanoseconds(), false);
            if (Mouse.getEventDWheel() != 0) {
                InputLatency.source("wheel", Mouse.getEventNanoseconds(), false);
            }
        }
        super.handleMouse();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        UiHost ui = voidClient.ui();
        if (voidClient.captureActive()) {
            voidClient.finishKeybindCapture("MOUSE" + button);
            return;
        }
        ui.mouseDown(viewX(ui), viewY(ui), button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        UiHost ui = voidClient.ui();
        ui.mouseUp(viewX(ui), viewY(ui), button);
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long heldMs) {
        UiHost ui = voidClient.ui();
        ui.mouseMoved(viewX(ui), viewY(ui));
    }

    /** CSS pixels one full wheel detent scrolls. */
    private static final double WHEEL_NOTCH_PX = 48.0;

    /** LWJGL wheel units in one detent. */
    private static final double UNITS_PER_NOTCH = 120.0;

    /**
     * LWJGL wheel units in one point of precise (trackpad) scrolling, on macOS.
     *
     * <p>AppKit reports a precise gesture in points on {@code scrollingDeltaY} and mirrors it on
     * the legacy {@code deltaY} as {@code scrollingDeltaY / 10}; LWJGL 2 then multiplies
     * {@code deltaY} by 120 to make its integer. So one point of finger travel is twelve units.
     * Measured against synthetic {@code kCGScrollEventUnitPixel} events of 3, 7, 12, 20, 28, 34,
     * 38 and 40 points, LWJGL reported 35, 83, 143, 240, 335, 407, 455 and 480 — twelve to the
     * point, to the rounding.</p>
     */
    private static final double UNITS_PER_POINT = 12.0;

    /**
     * How long one non-detent delta keeps the stream classified as precise.
     *
     * <p>Long enough to cover the gaps inside a gesture many times over — the deltas of one
     * arrive about ten milliseconds apart — and short enough that a wheel used after a trackpad
     * is read as a wheel again before the next flick. It doubles as the idle gap after which
     * {@link #pollWheel} drops the banked sub-pixel.</p>
     */
    private static final long PRECISE_STREAM_NANOS = 400L * 1_000_000L;

    /** While {@code now < this}, wheel deltas are read as points rather than as detents. */
    private long preciseUntilNanos;

    /**
     * Wheel travel under a whole pixel, kept for the next event.
     *
     * <p>This used to be {@code wheel / 120 * 48} in integers, which throws away everything
     * smaller than a notch: LWJGL reports 120 per detent on a mouse but a trackpad or a free
     * wheel sends a stream of much smaller deltas, and every one of those divided to zero. The
     * overlay therefore did not scroll at all under a gesture's worth of movement and then
     * jumped a whole line when the deltas happened to add up to 120 inside one event. Banking
     * the remainder is what makes a slow gesture move slowly.</p>
     *
     * <p>Bounded by construction: whatever is left after the integer part is taken is under one
     * pixel, so this can never hold back enough to be felt when it is finally paid — which is
     * what separates it from the payout budget {@code UiHost.driveScroll} used to keep.</p>
     */
    private double wheelRemainder;

    /** Scroll tracing only ({@code VOID_UI_SCROLLLOG}): when the previous wheel poll had travel. */
    private long lastWheelNanos;

    /**
     * Turns one LWJGL wheel delta into CSS pixels of page travel.
     *
     * <p>Two devices come through this one integer and they do not mean the same thing by it. A
     * mouse detent is a discrete request for "about a line" and is always a whole multiple of 120;
     * a trackpad is a position report, twelve units to the point, and its gesture is a dense
     * stream of arbitrary values. Reading a trackpad as detents is what made a flick cover about
     * 2.6x the distance the fingers did — an ordinary flick measured 2073 CSS pixels of scroll
     * against the ~800 the same movement would produce in a browser, which on any of the
     * overlay's short scrollers is indistinguishable from "it jumps to the bottom".</p>
     *
     * <p>The tell is the multiple. Detents are exact; a precise stream is not, and one inexact
     * delta latches the whole stream as precise for {@link #PRECISE_STREAM_NANOS} so the members
     * of it that happen to land on 120 are not read as a detent mid-gesture. A hi-res wheel that
     * reports fractions of a detent classifies as precise too, which is the right answer for it
     * as well.</p>
     */
    private double wheelPixels(int wheel, UiHost ui, long now) {
        if (wheel % (int) UNITS_PER_NOTCH != 0) {
            preciseUntilNanos = now + PRECISE_STREAM_NANOS;
        }
        double scale = ui.deviceScale();
        if (now < preciseUntilNanos && scale > 0) {
            // Points -> framebuffer pixels -> CSS pixels, the same two steps viewX/viewY take, so
            // a gesture moves the page by as much as it would have moved the cursor.
            return HiDpi.toPixels(wheel / UNITS_PER_POINT) / scale;
        }
        return wheel * (WHEEL_NOTCH_PX / UNITS_PER_NOTCH);
    }

    /**
     * Hands the frame's wheel travel to the view. Called from {@link #render}, once per rendered
     * frame.
     *
     * <p><b>Not from {@code handleMouse}</b>, which is where it used to be and where a screen's
     * input naturally lives. 1.8.9 pumps a screen's input from {@code runTick}, and {@code runTick}
     * runs at the <em>tick</em> rate — so a trackpad's 120 Hz stream reached the page as one lump
     * every 50 ms and the overlay scrolled in twenty steps a second while the fingers moved
     * smoothly. Measured on a steady 660 px/s drag: 33 px, wait 50 ms, 33 px. A rendered frame is
     * the right clock for something whose only job is to move pixels, and there are five or six of
     * those per tick.</p>
     *
     * <p>{@code getDWheel}, not {@code getEventDWheel}: the accumulator is filled by
     * {@code Mouse.poll()} inside {@code Display.update()} — i.e. once per rendered frame — and is
     * a different reading of the same input from the event queue {@code handleMouse} walks, so
     * taking it here neither steals events from the game nor counts anything twice. It is reset by
     * the read, so each frame gets exactly the travel since the last one. {@link #init} drains it
     * once on open; see there for why.</p>
     */
    private void pollWheel(UiHost ui) {
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            return;
        }
        long now = System.nanoTime();
        if (now >= preciseUntilNanos + PRECISE_STREAM_NANOS) {
            // A new gesture starts clean. The sub-pixel left over from the last one is under a
            // pixel and could never be felt, but nothing should survive a pause on the way to the
            // page: carrying travel across an idle gap is the shape of bug this whole path had.
            wheelRemainder = 0;
        }
        wheelRemainder += wheelPixels(wheel, ui, now);
        int pixels = (int) wheelRemainder;
        if (UiHost.SCROLLLOG) {
            long gap = lastWheelNanos == 0L ? -1L : (now - lastWheelNanos) / 1_000_000L;
            lastWheelNanos = now;
            VoidLog.info("wheel: raw=" + wheel
                    + (now < preciseUntilNanos ? " precise" : " detent") + " -> px=" + pixels
                    + " rem=" + (Math.round((wheelRemainder - pixels) * 100.0) / 100.0)
                    + " gap=" + gap + "ms");
        }
        if (pixels != 0) {
            wheelRemainder -= pixels;
            ui.scroll(0, pixels);
        }
    }

    @Override
    public void handleKeyboard() {
        if (InputLatency.ON) {
            InputLatency.source(Keyboard.getEventKeyState() ? "keydown" : "keyup",
                    Keyboard.getEventNanoseconds(), true);
        }
        // Minecraft only delivers presses to keyPressed; key-up has to be read
        // from the event itself or the view would never see a key released.
        if (Keyboard.getEventKey() != 0 && !Keyboard.getEventKeyState()) {
            int code = Keyboard.getEventKey();
            // Not isMenuKey(): on key-up the state API already reads false, so the sibling
            // test cannot match. Clear on either half of the pair.
            int want = voidClient.state().menuKeyCode;
            if (code == want || code == siblingModifier(want)) {
                menuKeyHeld = false;
            }
            voidClient.ui().keyUp(KeyNames.virtualKey(code), modifiers());
        }
        super.handleKeyboard();
    }

    /**
     * Whether this key event is the menu key.
     *
     * <p>Not just {@code ==}. LWJGL 2 on macOS reports the *event* for either Shift as
     * {@code LSHIFT} (42) while {@code Keyboard.isKeyDown} still answers correctly for
     * {@code RSHIFT} (54) — measured: pressing Right Shift logs `code=42` here and
     * `isKeyDown(54)=true` in the same frame. So a left/right pair is matched by asking the
     * state API which side is physically down; the sibling only counts when it really is. The
     * same conflation applies to Control and Alt, so all three pairs are handled.</p>
     */
    /** True while the menu key is physically held, so auto-repeat cannot re-latch it. */
    private boolean menuKeyHeld;

    private boolean isMenuKey(int keyCode) {
        int want = voidClient.state().menuKeyCode;
        if (keyCode == want) {
            return true;
        }
        int sibling = siblingModifier(want);
        return sibling != 0 && keyCode == sibling && Keyboard.isKeyDown(want);
    }

    /** The other half of a left/right modifier pair, or 0. */
    private static int siblingModifier(int code) {
        switch (code) {
            case 42: return 54;   // LSHIFT   <-> RSHIFT
            case 54: return 42;
            case 29: return 157;  // LCONTROL <-> RCONTROL
            case 157: return 29;
            case 56: return 184;  // LMENU    <-> RMENU
            case 184: return 56;
            default: return 0;
        }
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        UiHost ui = voidClient.ui();

        if (voidClient.captureActive()) {
            voidClient.finishKeybindCapture(
                    keyCode == KeyNames.KEY_ESCAPE ? null : KeyNames.nameOf(keyCode));
            return;
        }
        if (isMenuKey(keyCode)) {
            // The hotkey poll still owns open/close — closing here would let the poll reopen on
            // the next frame while the key is still held. But the poll samples once per frame and
            // cannot see a tap shorter than a frame, which with the menu open is most taps. So
            // latch the event and let the poll act on it: the decision stays in one place and
            // stops depending on frame rate. Swallowed either way, so the view never sees it.
            // Once per physical press. `GuiScreen` turns on key repeat, so holding the key
            // delivers keyPressed again and again; latching each one toggled the menu every
            // frame the key was held, which is a flicker, not a close. Cleared on key-up in
            // handleKeyboard().
            if (!menuKeyHeld) {
                menuKeyHeld = true;
                voidClient.latchMenuKeyTap();
            }
            return;
        }
        // Escape means **up one level**, and the page owns every level but the last one: a
        // focused field gives up focus, the quick palette closes, a mod's properties page returns
        // to the grid, and only from the grid does Escape close the menu. So the question here is
        // not "is a field focused" but "will the page handle this", which is what `keepsEscape`
        // answers — and it is false whenever the bridge is not ready, so a page that has broken
        // can never trap the player inside an open screen.
        //
        // The keybind capture above still wins over all of it, deliberately: Escape while binding
        // a key cancels the capture, and that branch returns before this one is reached.
        if (keyCode == KeyNames.KEY_ESCAPE && !ui.keepsEscape()) {
            voidClient.closeMenu();
            return;
        }
        int mods = modifiers();
        ui.keyDown(KeyNames.virtualKey(keyCode), mods);
        // Not every key event carries text, and two different things can be wrong with the
        // character LWJGL hands over. On macOS the navigation keys arrive with an AppKit
        // private-use character (Down is U+F701), and a chord arrives with the plain character
        // of the key it was struck with (Cmd-K's is 'k'). Both used to be forwarded as typed
        // text and land in whatever field had focus: the first reset the palette's selection on
        // every arrow press, the second put a `k` in the field Cmd-K had just opened.
        if (KeyNames.isTypedText(character) && !KeyNames.isCommandChord(mods)) {
            ui.keyChar(String.valueOf(character), mods);
        }
    }

    /** Ultralight's modifier bits: 1 alt, 2 ctrl, 4 meta, 8 shift. */
    private static int modifiers() {
        int mods = 0;
        if (Keyboard.isKeyDown(56) || Keyboard.isKeyDown(184)) {
            mods |= 1;
        }
        if (Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157)) {
            mods |= 2;
        }
        if (Keyboard.isKeyDown(219) || Keyboard.isKeyDown(220)) {
            mods |= 4;
        }
        if (Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54)) {
            mods |= 8;
        }
        return mods;
    }

    // Mouse still reports points even when the drawable is 2x, while deviceScale and
    // MinecraftClient.height are both in pixels now (HiDpi) — so the raw position has to be
    // converted before it can be divided by the one and subtracted from the other.
    private int viewX(UiHost ui) {
        double scale = ui.deviceScale();
        return scale <= 0 ? 0 : (int) (HiDpi.toPixels((double) Mouse.getX()) / scale);
    }

    private int viewY(UiHost ui) {
        double scale = ui.deviceScale();
        if (scale <= 0) {
            return 0;
        }
        double fromTop = voidClient.minecraft().height - HiDpi.toPixels((double) Mouse.getY());
        return (int) (fromTop / scale);
    }

    /** The scaled-GUI size, so callers do not have to build a {@link Window}. */
    public static int[] scaledSize(MinecraftClient mc) {
        Window window = new Window(mc);
        return new int[] {(int) window.getScaledWidth(), (int) window.getScaledHeight()};
    }
}
