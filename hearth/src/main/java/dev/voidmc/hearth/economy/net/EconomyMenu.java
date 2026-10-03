package dev.voidmc.hearth.economy.net;

import net.minecraft.server.level.ServerPlayer;

/** A menu of the economy module that takes clicks from its screen as {@link EconomyActionPayload}s. */
public interface EconomyMenu {
	/** On the server thread, for the player whose open menu this is. */
	void handle(ServerPlayer player, EconomyActionPayload action);
}
