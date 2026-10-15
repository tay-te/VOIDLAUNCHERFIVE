package dev.voidpvp.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The three private {@code ChatHud} fields {@code chat.click_to_copy} reads to find the line under
 * the pointer and the message it belongs to. All three are declared on {@code ChatHud} itself in
 * the named jar: {@code messages} (whole messages, newest first), {@code visibleMessages} (wrapped
 * lines, newest first) and {@code scrolledLines}.
 */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {

    @Accessor("messages")
    List<ChatHudLine> void$messages();

    @Accessor("visibleMessages")
    List<ChatHudLine> void$visibleMessages();

    @Accessor("scrolledLines")
    int void$scrolledLines();
}
