package dev.voidpvp.client.screen;

import dev.voidpvp.client.VoidClient;
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

    private final VoidClient client;
    private final BlurBackdrop backdrop = new BlurBackdrop();

    public VoidMenuScreen(VoidClient client) {
        this.client = client;
    }

    /** The game keeps running behind the menu; this is a PVP client (§6.4). */
    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void init() {
        client.onMenuOpened();
        UiHost ui = client.ui();
        ui.setFocus(true);
    }

    @Override
    public void removed() {
        backdrop.release();
        client.ui().setFocus(false);
        client.onMenuClosed();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        MinecraftClient mc = this.client.minecraft();
        UiHost ui = this.client.ui();
        int fbWidth = Math.max(1, mc.width);
        int fbHeight = Math.max(1, mc.height);

        // 1-3. framebuffer copy, two-pass blur, draw back with the tint
        backdrop.draw(this.width, this.height, fbWidth, fbHeight, TINT);

        // 4. the menu layer, from the same view the HUD uses
        this.client.pumpUi();
        ui.paint(this.width, this.height);

        forwardMouseMove(ui);
    }

    private void forwardMouseMove(UiHost ui) {
        double scale = ui.deviceScale();
        if (scale <= 0) {
            return;
        }
        int x = (int) (Mouse.getX() / scale);
        int y = (int) ((this.client.minecraft().height - Mouse.getY()) / scale);
        ui.mouseMoved(x, y);
    }

    // -----------------------------------------------------------------
    // Input forwarding (§6.3). Minecraft has released the mouse; every event
    // here belongs to the view unless it is the key that closes the menu.
    // -----------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        UiHost ui = client.ui();
        if (client.captureActive()) {
            client.finishKeybindCapture("MOUSE" + button);
            return;
        }
        ui.mouseDown(viewX(ui), viewY(ui), button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        UiHost ui = client.ui();
        ui.mouseUp(viewX(ui), viewY(ui), button);
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long heldMs) {
        UiHost ui = client.ui();
        ui.mouseMoved(viewX(ui), viewY(ui));
    }

    @Override
    public void handleMouse() {
        // Read the wheel for the event Minecraft is about to consume.
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            // LWJGL reports 120 per notch; Ultralight wants pixels.
            client.ui().scroll(0, wheel / 120 * 48);
        }
        super.handleMouse();
    }

    @Override
    public void handleKeyboard() {
        // Minecraft only delivers presses to keyPressed; key-up has to be read
        // from the event itself or the view would never see a key released.
        if (Keyboard.getEventKey() != 0 && !Keyboard.getEventKeyState()) {
            int code = Keyboard.getEventKey();
            client.ui().keyUp(KeyNames.virtualKey(code), modifiers());
        }
        super.handleKeyboard();
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        UiHost ui = client.ui();

        if (client.captureActive()) {
            client.finishKeybindCapture(
                    keyCode == KeyNames.KEY_ESCAPE ? null : KeyNames.nameOf(keyCode));
            return;
        }
        if (keyCode == client.state().menuKeyCode) {
            // The hotkey poll owns open/close; swallow it here so the view
            // does not also see it.
            return;
        }
        if (keyCode == KeyNames.KEY_ESCAPE && !ui.hasFocusedInput()) {
            client.closeMenu();
            return;
        }
        int mods = modifiers();
        ui.keyDown(KeyNames.virtualKey(keyCode), mods);
        if (character >= 32 && character != 127) {
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

    private int viewX(UiHost ui) {
        double scale = ui.deviceScale();
        return scale <= 0 ? 0 : (int) (Mouse.getX() / scale);
    }

    private int viewY(UiHost ui) {
        double scale = ui.deviceScale();
        return scale <= 0 ? 0 : (int) ((client.minecraft().height - Mouse.getY()) / scale);
    }

    /** The scaled-GUI size, so callers do not have to build a {@link Window}. */
    public static int[] scaledSize(MinecraftClient mc) {
        Window window = new Window(mc);
        return new int[] {(int) window.getScaledWidth(), (int) window.getScaledHeight()};
    }
}
