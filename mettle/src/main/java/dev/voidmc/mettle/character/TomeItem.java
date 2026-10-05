package dev.voidmc.mettle.character;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** A teaching tome: hold use for {@link #READ_TICKS} to read it. Kept afterwards; each reader learns its perk once. */
public final class TomeItem extends Item {
	public static final int READ_TICKS = 100;

	public TomeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Identifier perk = stack.get(Tomes.TEACHES);
		if (perk == null) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && Perks.has(sp, perk)) {
			SignaturePerks.feedback(sp, perk, Component.translatable("mettle.tome.known", stack.getHoverName()));
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		if (!level.isClientSide() && ticksRemaining % 25 == 0 && ticksRemaining > 0) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		Identifier perk = stack.get(Tomes.TEACHES);
		if (perk != null && entity instanceof ServerPlayer player) {
			ResourceKey<Perk> key = Tomes.perk(perk);
			if (Perks.grant(player, key)) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
			}
		}
		return stack;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return READ_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.SPYGLASS;
	}

	/** The perk's name under the title, so a find says what it teaches. */
	public static void tooltip(ItemStack stack, Level level, Consumer<Component> out) {
		Identifier perk = stack.get(Tomes.TEACHES);
		if (perk == null || level == null) {
			return;
		}
		Optional<Perk> p = level.registryAccess().lookupOrThrow(dev.voidmc.mettle.api.MettleRegistries.PERK).getOptional(perk);
		p.ifPresent(value -> out.accept(Component.translatable("mettle.tome.teaches", value.name()).withStyle(ChatFormatting.GRAY)));
	}
}
