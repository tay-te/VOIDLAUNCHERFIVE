package dev.voidmc.mettle.character.cards.legend;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.cards.CardCooldowns;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.mixin.legend.LegendFurnaceAccess;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** The Ever-Ember and the Mender's Hand: the smith's fire and the smith's touch. */
public final class LegendCraft {
	public static final String MENDERS_HAND = "menders_hand";

	/** The Ever-Ember: who last filled this furnace, smoker or blast furnace (its input or fuel). Saved with it. */
	public static final AttachmentType<UUID> FILLED_BY = AttachmentRegistry.create(Mettle.id("legend/filled_by"), b -> b.persistent(UUIDUtil.CODEC));

	private LegendCraft() {
	}

	private static boolean has(Player player, String id) {
		return Mythics.active(player, id);
	}

	static void init() {
		FILLED_BY.toString();
	}

	// ---------------------------------------------------------------- the Ever-Ember

	/** A player put something into a furnace's input or fuel (AbstractContainerMenu#clicked): they last filled it. */
	public static void filled(AbstractFurnaceBlockEntity furnace, ServerPlayer player) {
		UUID before = furnace.getAttached(FILLED_BY);
		if (!player.getUUID().equals(before)) {
			furnace.setAttached(FILLED_BY, player.getUUID());
			furnace.setChanged();
			if (has(player, "ever_ember")) {
				LegendEffects.chip(player, "ever_ember", Component.translatable("mettle.legend.ever_ember"));
			}
		}
		// The item in the fire is timed again for whoever filled it last, keeping how far along it is (as vanilla does on a
		// new fuel's speed).
		if (furnace.getLevel() instanceof ServerLevel level) {
			LegendFurnaceAccess access = (LegendFurnaceAccess) furnace;
			int total = access.mettle$cookingTotalTime();
			int now = LegendFurnaceAccess.mettle$totalCookTime(level, furnace);
			if (total > 0 && now != total) {
				float done = (float) access.mettle$cookingTimer() / total;
				access.mettle$setCookingTotalTime(now);
				access.mettle$setCookingTimer((int) Math.ceil(done * now));
			}
		}
	}

	/**
	 * AbstractFurnaceBlockEntity#getTotalCookTime: twice as fast while the one who last filled it is here with the
	 * Ever-Ember. The fuel burns at its own pace, so each item takes half the fuel.
	 */
	public static int cookTime(AbstractFurnaceBlockEntity furnace, int ticks) {
		UUID by = furnace.getAttached(FILLED_BY);
		if (by == null || !(furnace.getLevel() instanceof ServerLevel level)) {
			return ticks;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(by);
		return p != null && has(p, "ever_ember") ? Math.max(1, (ticks + 1) / 2) : ticks;
	}

	// ---------------------------------------------------------------- the Mender's Hand

	/**
	 * Whether this anvil job is the Mender's Hand's: a holder, ready today, repairing one damaged item with its repair
	 * material or a plain copy of itself (an enchanted second item or a book is a combine, not a repair, and is left to
	 * vanilla). Both sides, so the client shows what the server will allow.
	 */
	public static boolean mends(Player player, ItemStack input, ItemStack addition) {
		if (input.isEmpty() || addition.isEmpty() || input.getCount() != 1 || !input.isDamageableItem() || !input.isDamaged()
			|| !has(player, "menders_hand") || !CardCooldowns.ready(player, MENDERS_HAND)) {
			return false;
		}
		return input.isValidRepairItem(addition) || addition.is(input.getItem()) && !addition.has(DataComponents.STORED_ENCHANTMENTS)
			&& EnchantmentHelper.getEnchantmentsForCrafting(addition).isEmpty();
	}

	/** The mended item: whole, and its prior-work cost as it came in (the job adds none). */
	public static ItemStack mended(ItemStack input, ItemStack vanilla) {
		ItemStack result = vanilla.isEmpty() ? input.copy() : vanilla.copy();
		result.setDamageValue(0);
		result.set(DataComponents.REPAIR_COST, input.getOrDefault(DataComponents.REPAIR_COST, 0));
		return result;
	}

	/** The mended item was taken: the day's mend is spent. */
	public static void took(ServerPlayer player) {
		CardCooldowns.start(player, MENDERS_HAND, 24000);
		LegendEffects.chip(player, MENDERS_HAND, Component.translatable("mettle.legend.menders_hand"));
	}
}
