package dev.voidmc.mettle.mixin.core;

import dev.voidmc.mettle.character.cards.LedgerFile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The card ledger is saved before any player file (autosave, a player leaving, the server stopping): a player's cached
 * cards are then never newer than the ledger on disk (docs/CARDS.md §2.1), so a crash can't lose or duplicate a card.
 */
@Mixin(PlayerDataStorage.class)
public class LedgerSaveMixin {
	@Inject(method = "save", at = @At("HEAD"))
	private void mettle$ledgerFirst(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer p) {
			LedgerFile.save(p.level().getServer());
		}
	}
}
