package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.actuator.ChatTweaks;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Calendar;

/**
 * The Chat mod (§6.7, {@code schema/mods/chat.json}): timestamps, stacked repeats, history length
 * and the line band's opacity, all on vanilla's own {@code ChatHud}. The decisions are
 * {@link ChatTweaks}'; this only reads and writes the game.
 *
 * <p>Read off the named 1.8.9 jar with {@code javap -p -c}, not remembered:</p>
 * <ul>
 *   <li>{@code public void addMessage(Text, int)} is the one entry for a new message —
 *       {@code addMessage(Text)} calls it with id 0 — and it hands straight to
 *       {@code private void addMessage(Text, int id, int tick, boolean refresh)}, then logs
 *       {@code "[CHAT] " + text.asUnformattedString()}. The private one is also what
 *       {@code reset()} replays the stored messages through, with {@code refresh = true}, so
 *       decorating at the public entry decorates each message exactly once and a chat-width
 *       change re-wraps the decorated text rather than decorating it again.</li>
 *   <li>The private one opens {@code if (id != 0) removeMessage(id)} (offsets 0-6), which is the
 *       replace-by-id stacking rides on; and it caps both lists with {@code bipush 100} — at
 *       offset 133 for {@code visibleMessages} and 196 for {@code messages}. Those are the only
 *       two {@code 100}s in the method.</li>
 *   <li>{@code render(int)}'s first {@code fill(IIIII)V} (offset 295, owner {@code ChatHud},
 *       inherited static from {@code DrawableHelper}) is the per-line band, coloured
 *       {@code (alpha / 2) << 24}. The two after it (477, 499) are the scroll bar.</li>
 * </ul>
 *
 * <p><b>With the mod off, every hook here returns what vanilla had</b>: the HEAD inject returns
 * before touching anything, the constant answers 100 and the band colour passes through — and
 * {@link ChatTweaks#backgroundColor} is the identity at the default 0.5 even with it on.</p>
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    /** Set while this mixin re-enters {@code addMessage} with the decorated message. */
    @Unique
    private boolean void$reentry;

    /**
     * Timestamps, stacking and the synthetic id, at the public entry.
     *
     * <p>Re-invoking {@code addMessage} with the decorated text and the chosen id, rather than
     * rewriting the arguments in place, keeps both changes in one place and lets vanilla's own
     * {@code removeMessage(id)} do the stacking. The guard is what stops the re-entry from being
     * decorated a second time. Side effect, accepted: the {@code [CHAT]} log line then carries
     * the timestamp and the count too, which is if anything more useful in a log.</p>
     */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;I)V", at = @At("HEAD"),
            cancellable = true)
    private void void$decorate(Text text, int id, CallbackInfo ci) {
        if (void$reentry || text == null) {
            return;
        }
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        LiveState state = LiveState.get();
        ChatTweaks chat = client.chat();
        if (!state.chatOn) {
            chat.forget();
            return;
        }
        ChatTweaks.Placement placed = chat.place(text.asUnformattedString(), id,
                state.chatStackRepeats);
        Text out = decorated(text, state.chatTimestamps, placed.count);
        if (out == text && placed.id == id) {
            return; // nothing to change: leave vanilla's call exactly as it was
        }
        ci.cancel();
        void$reentry = true;
        try {
            ((ChatHud) (Object) this).addMessage(out, placed.id);
        } finally {
            void$reentry = false;
        }
    }

    /**
     * The message with its grey timestamp in front and grey count behind.
     *
     * <p>Built as siblings of an empty root rather than by appending to the timestamp: in this
     * Text API a sibling's style inherits its parent's ({@code BaseText.append} sets the child
     * style's parent), so appending the message to a grey prefix would turn every uncoloured
     * word of it grey. An empty root has an empty style, so the message keeps exactly the
     * colours it arrived with.</p>
     */
    @Unique
    private static Text decorated(Text text, boolean timestamps, int count) {
        if (!timestamps && count <= 1) {
            return text;
        }
        Text root = new LiteralText("");
        if (timestamps) {
            Calendar now = Calendar.getInstance();
            root.append(grey(ChatTweaks.timestamp(now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE))));
        }
        root.append(text);
        if (count > 1) {
            root.append(grey(ChatTweaks.stackSuffix(count)));
        }
        return root;
    }

    @Unique
    private static Text grey(String s) {
        Text t = new LiteralText(s);
        t.setStyle(new Style().setFormatting(Formatting.GRAY));
        return t;
    }

    /** A cleared chat has no line left to stack onto. */
    @Inject(method = "clear", at = @At("HEAD"))
    private void void$clear(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.chat().forget();
        }
    }

    /** {@code history_length}: both of the private {@code addMessage}'s caps. */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V",
            constant = @Constant(intValue = 100), require = 2)
    private int void$historyLength(int vanilla) {
        LiveState state = LiveState.get();
        return state.chatOn ? state.chatHistoryLength : vanilla;
    }

    /** {@code background_opacity}: the per-line band, the first {@code fill} in the method. */
    @Redirect(method = "render(I)V", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/hud/ChatHud;fill(IIIII)V"))
    private void void$lineBand(int x1, int y1, int x2, int y2, int color) {
        LiveState state = LiveState.get();
        DrawableHelper.fill(x1, y1, x2, y2,
                state.chatOn ? ChatTweaks.backgroundColor(color, state.chatBackgroundOpacity)
                        : color);
    }
}
