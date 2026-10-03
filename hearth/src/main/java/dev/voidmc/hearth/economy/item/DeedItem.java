package dev.voidmc.hearth.economy.item;

import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.SettlementLookup;
import dev.voidmc.hearth.economy.property.PropertyNames;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The title to a house or plot bought from a settlement. The settlement records the owner ({@code Settlement#setPlotOwner})
 * and its folk leave the place alone; the deed is the paper that proves it.
 *
 * <p>A deed is a bearer document, as old deeds were: whoever holds it can claim the title by using it on the property,
 * so land can be sold or given between players by handing over the deed.
 */
public class DeedItem extends Item {
	public DeedItem(Properties properties) {
		super(properties);
	}

	public static ItemStack create(DeedData data) {
		ItemStack stack = new ItemStack(EconomyItems.DEED);
		stack.set(EconomyComponents.DEED, data);
		return stack;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		DeedData deed = player.getItemInHand(hand).get(EconomyComponents.DEED);
		if (deed == null || !(level instanceof ServerLevel server)) {
			return InteractionResult.PASS;
		}
		Settlement settlement = SettlementLookup.find(server.getServer(), deed.settlement());
		PlotView plot = settlement == null ? null : settlement.plot(deed.plot());
		if (plot == null) {
			player.sendOverlayMessage(Component.translatable("hearth.economy.deed.void"));
			return InteractionResult.FAIL;
		}
		if (player.getUUID().equals(plot.owner())) {
			player.sendOverlayMessage(Component.translatable("hearth.economy.deed.yours", PropertyNames.lower(deed.building()), deed.settlementName()));
			return InteractionResult.SUCCESS;
		}
		if (!deed.contains(player.blockPosition())) {
			player.sendOverlayMessage(Component.translatable("hearth.economy.deed.go_there", deed.origin().getX(), deed.origin().getZ()));
			return InteractionResult.FAIL;
		}
		settlement.setPlotOwner(plot.id(), player.getUUID());
		settlement.markDirty();
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		player.sendSystemMessage(Component.translatable("hearth.economy.deed.claimed", PropertyNames.lower(deed.building()), deed.settlementName())
			.withStyle(ChatFormatting.GOLD));
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		DeedData deed = stack.get(EconomyComponents.DEED);
		if (deed == null) {
			builder.accept(Component.translatable("hearth.economy.deed.blank").withStyle(ChatFormatting.GRAY));
			return;
		}
		builder.accept(Component.translatable(deed.built() ? "hearth.economy.deed.house" : "hearth.economy.deed.plot",
			PropertyNames.lower(deed.building()), deed.settlementName()).withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("hearth.economy.deed.size", deed.sizeX(), deed.sizeZ(), deed.origin().getX(),
			deed.origin().getY(), deed.origin().getZ()).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("hearth.economy.deed.bearer").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
