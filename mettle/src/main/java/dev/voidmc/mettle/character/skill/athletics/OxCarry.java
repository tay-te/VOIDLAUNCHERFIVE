package dev.voidmc.mettle.character.skill.athletics;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.AthleticsSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Ox mastery's other half: sneak-use an empty hand on a chest or barrel to pick it up whole. The item carries the
 * contents the way a shulker box does (the container component), so it weighs what's inside and placing it puts
 * everything back. A chest full of things doesn't stack. Loot chests are rolled first, as if opened.
 */
public final class OxCarry {
	private OxCarry() {
	}

	/** UseBlockCallback, both sides: the client claims the click so the chest doesn't open, the server does the lifting. */
	public static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator()
			|| !carriable(level.getBlockState(hit.getBlockPos())) || !Perks.has(player, AthleticsSkill.OX)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		return lift(sp, level, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	public static boolean carriable(BlockState state) {
		return state.getBlock() instanceof ChestBlock || state.is(Blocks.BARREL);
	}

	private static boolean lift(ServerPlayer player, Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockEntity entity = level.getBlockEntity(pos);
		if (!player.mayBuild() || !level.mayInteract(player, pos) || !(entity instanceof BaseContainerBlockEntity container)) {
			return false;
		}
		if (container instanceof RandomizableContainer loot) {
			loot.unpackLootTable(player);
		}
		int stacks = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty()) {
				stacks++;
			}
		}
		ItemStack carried = new ItemStack(state.getBlock());
		carried.applyComponents(entity.collectComponents());
		if (stacks > 0) {
			carried.set(DataComponents.MAX_STACK_SIZE, 1);
		} else {
			carried.remove(DataComponents.CONTAINER);
		}
		// Emptied first, so removing the block drops nothing.
		container.clearContent();
		level.removeBlock(pos, false);
		player.setItemInHand(InteractionHand.MAIN_HAND, carried);
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 0.7f);
		AthleticsSkill.chip(player, AthleticsSkill.OX, "ox", carried.getHoverName(), stacks);
		return true;
	}
}
