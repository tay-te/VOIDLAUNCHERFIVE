package dev.voidmc.hearth.quest.item;

import dev.voidmc.hearth.quest.QuestHooks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * {@code hearth:journal}: a leather-bound book of the quests you have taken. Using it opens the journal screen (the
 * same screen as the J key); the quests themselves are the player's own synced data, not the item's, so a lost journal
 * loses nothing.
 */
public class JournalItem extends Item {
	public JournalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			QuestHooks.openJournal();
		} else {
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}
}
