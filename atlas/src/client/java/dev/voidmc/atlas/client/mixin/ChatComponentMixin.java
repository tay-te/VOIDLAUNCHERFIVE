package dev.voidmc.atlas.client.mixin;

import dev.voidmc.atlas.client.ChatCoordinates;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every chat line on its way in: coordinates in it become clickable (see {@link ChatCoordinates}). */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component atlas$coordinates(Component message) {
		try {
			return ChatCoordinates.decorate(message);
		} catch (RuntimeException e) {
			// never lose a chat line to Atlas
			return message;
		}
	}
}
