package dev.voidmc.mettle.mixin.character;

import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.MiningSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Green Thumb (Homesteader): a crop you plant sometimes sprouts a stage early. Also records placed ore and stone. */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void mettle$greenThumb(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction() || !(context.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		Level level = context.getLevel();
		BlockState state = level.getBlockState(context.getClickedPos());
		// Anti-farm: blocks that give Mining XP remember they were placed.
		if (MiningSkill.baseXp(state) > 0) {
			PlacedBlocks.mark(level, context.getClickedPos());
		}
		if (!(state.getBlock() instanceof CropBlock crop) || crop.getAge(state) != 0 || crop.getMaxAge() < 2) {
			return;
		}
		if (level.getRandom().nextDouble() < SignaturePerks.GREEN_THUMB_CHANCE && SignaturePerks.has(player, SignaturePerks.GREEN_THUMB)) {
			level.setBlock(context.getClickedPos(), crop.getStateForAge(1), Block.UPDATE_CLIENTS);
			SignaturePerks.feedback(player, SignaturePerks.GREEN_THUMB, Component.translatable("mettle.feedback.green_thumb"));
		}
	}
}
