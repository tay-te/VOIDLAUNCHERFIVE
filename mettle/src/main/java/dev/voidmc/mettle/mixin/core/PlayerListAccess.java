package dev.voidmc.mettle.mixin.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Saves one player's file now (after a trade, a Mythic or a migration, right after the ledger). */
@Mixin(PlayerList.class)
public interface PlayerListAccess {
	@Invoker("save")
	void mettle$save(ServerPlayer player);
}
