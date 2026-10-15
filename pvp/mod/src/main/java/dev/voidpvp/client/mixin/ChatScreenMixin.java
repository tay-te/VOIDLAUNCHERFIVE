package dev.voidpvp.client.mixin;

import dev.voidpvp.client.HiDpi;
import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.actuator.ChatTweaks;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * {@code chat.click_to_copy}: right-click a chat line to copy its whole message.
 *
 * <p>{@code ChatScreen.mouseClicked(III)V} in the named jar handles only button 0 itself — it
 * asks {@code ChatHud.getTextAt(Mouse.getX(), Mouse.getY())} for a click event — and passes
 * every click on to the text field and {@code Screen.mouseClicked}, neither of which does
 * anything with button 1 over the chat. So taking button 1 at HEAD, and only when it is over a
 * line, removes nothing vanilla did.</p>
 *
 * <p>The line under the pointer is found with {@code getTextAt}'s own coordinate math
 * ({@link ChatTweaks#lineIndexAt}, replicated from its bytecode) rather than by calling it:
 * {@code getTextAt} returns the one <em>component</em> under the pointer, not the line, and the
 * line is what carries the message id. The id is unique per message because
 * {@code ChatHudMixin} gives every message a synthetic one, so the whole message is found in
 * {@code messages} from any of its wrapped lines.</p>
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void void$copyLine(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button != 1) {
            return;
        }
        LiveState state = LiveState.get();
        VoidClient client = VoidClient.get();
        if (!state.chatOn || !state.chatClickToCopy || client == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null || mc.textRenderer == null) {
            return;
        }
        ChatHud hud = mc.inGameHud.getChatHud();
        ChatHudAccessor lists = (ChatHudAccessor) hud;
        List<ChatHudLine> visible = lists.void$visibleMessages();
        // Vanilla hands getTextAt the raw LWJGL position; HiDpi.toPixels is the identity at a
        // scale factor of 1 and the same correction the mod's other pointer reads apply on Retina.
        int index = ChatTweaks.lineIndexAt(HiDpi.toPixels(Mouse.getX()),
                HiDpi.toPixels(Mouse.getY()), new Window(mc).getScaleFactor(),
                hud.getChatScale(), hud.getWidth(), mc.textRenderer.fontHeight,
                hud.getVisibleLineCount(), visible.size(), lists.void$scrolledLines());
        if (index < 0) {
            return; // not over a line: the click is vanilla's
        }
        ChatHudLine line = visible.get(index);
        String text = wholeMessage(client.chat(), lists.void$messages(), line);
        if (text == null || text.isEmpty()) {
            return;
        }
        ci.cancel();
        Screen.setClipboard(text);
        client.bridge().emitToast(VoidBridge.TOAST_INFO, "Copied to clipboard",
                text.length() > 96 ? text.substring(0, 93) + "..." : text, null);
    }

    /**
     * The message the line belongs to, without colour codes: as it arrived (no timestamp, no
     * stack count) when {@link ChatTweaks} saw it, else the stored message, else the line.
     */
    private static String wholeMessage(ChatTweaks chat, List<ChatHudLine> messages,
                                       ChatHudLine line) {
        int id = line.getId();
        String original = id == 0 ? null : chat.original(id);
        if (original != null) {
            return ChatTweaks.stripFormatting(original);
        }
        if (id != 0) {
            for (int i = 0; i < messages.size(); i++) {
                ChatHudLine m = messages.get(i);
                if (m != null && m.getId() == id && m.getText() != null) {
                    return ChatTweaks.stripFormatting(m.getText().asUnformattedString());
                }
            }
        }
        return line.getText() == null ? null
                : ChatTweaks.stripFormatting(line.getText().asUnformattedString());
    }
}
