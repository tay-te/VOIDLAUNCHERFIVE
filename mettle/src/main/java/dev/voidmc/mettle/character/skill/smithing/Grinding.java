package dev.voidmc.mettle.character.skill.smithing;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jspecify.annotations.Nullable;

/**
 * The grindstone's two Smithing uses. Both run on both sides (the client predicts the result slot; perks are synced).
 *
 * <ul>
 * <li><b>Honing</b> (mastery): a weapon or tool alone in the <i>top</i> slot comes out honed, enchantments kept and no
 * experience given (nothing was ground off). The bottom slot still grinds as vanilla does, so a honer can disenchant.</li>
 * <li><b>Salvager</b>: an unenchanted iron, gold or copper piece alone comes out as nuggets, up to half its metal, less
 * the more worn it is.</li>
 * </ul>
 */
public final class Grinding {
	public enum Mode {
		NONE, HONE, SALVAGE
	}

	public record Outcome(ItemStack result, Mode mode) {
	}

	private Grinding() {
	}

	/** What the grindstone shows for these inputs, given vanilla's own answer. */
	public static Outcome result(@Nullable Player player, ItemStack top, ItemStack bottom, ItemStack vanilla) {
		if (player == null || (top.isEmpty() == bottom.isEmpty())) {
			return new Outcome(vanilla, Mode.NONE);
		}
		ItemStack item = top.isEmpty() ? bottom : top;
		if (item.getCount() != 1) {
			return new Outcome(vanilla, Mode.NONE);
		}
		if (!top.isEmpty() && Perks.has(player, SmithingSkill.HONING) && honeable(item)) {
			Honed current = item.get(SmithingComponents.HONED);
			if (current != null && current.full()) {
				return new Outcome(ItemStack.EMPTY, Mode.HONE);
			}
			ItemStack honed = item.copy();
			honed.set(SmithingComponents.HONED, Honed.fresh(Forging.isWeapon(item)));
			Forging.refresh(honed);
			return new Outcome(honed, Mode.HONE);
		}
		if (Perks.has(player, SmithingSkill.SALVAGER) && !EnchantmentHelper.hasAnyEnchantments(item)) {
			ItemStack nuggets = salvage(item);
			double more = dev.voidmc.mettle.character.cards.CardHooks.pct(player, "smithing/salvaging");
			if (!nuggets.isEmpty() && more > 0) {
				nuggets.setCount(Math.min(nuggets.getMaxStackSize(), (int) Math.round(nuggets.getCount() * (1 + more))));
			}
			if (!nuggets.isEmpty()) {
				return new Outcome(nuggets, Mode.SALVAGE);
			}
		}
		return new Outcome(vanilla, Mode.NONE);
	}

	public static boolean honeable(ItemStack stack) {
		return stack.isDamageableItem() && (Forging.isWeapon(stack) || Forging.isDigTool(stack));
	}

	/** The nuggets an iron, gold or copper piece grinds down to, or empty. */
	public static ItemStack salvage(ItemStack stack) {
		Item nugget = nugget(stack);
		if (nugget == null) {
			return ItemStack.EMPTY;
		}
		int count = SmithingRules.salvage(SmithingSkill.units(stack), stack.getDamageValue(), stack.getMaxDamage());
		return count <= 0 ? ItemStack.EMPTY : new ItemStack(nugget, Math.min(count, nugget.getDefaultMaxStackSize()));
	}

	private static @Nullable Item nugget(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.startsWith("iron_") || path.startsWith("chainmail_")) {
			return Items.IRON_NUGGET;
		}
		if (path.startsWith("golden_")) {
			return Items.GOLD_NUGGET;
		}
		if (path.startsWith("copper_")) {
			return Items.COPPER_NUGGET;
		}
		return null;
	}

	/** The result was taken: say so, and a salvage teaches a little Smithing. */
	public static void taken(ServerPlayer player, Mode mode, ItemStack top, ItemStack bottom, ItemStack carried) {
		if (mode == Mode.HONE) {
			Honed honed = carried.get(SmithingComponents.HONED);
			if (honed != null) {
				SmithingSkill.chip(player, SmithingSkill.HONING, honed.weapon() ? "honing.weapon" : "honing.tool", honed.left());
			}
		} else if (mode == Mode.SALVAGE) {
			ItemStack piece = top.isEmpty() ? bottom : top;
			Progress.award(player, SmithingSkill.SMITHING, SmithingSkill.units(piece));
			SmithingSkill.chip(player, SmithingSkill.SALVAGER, "salvager", carried.getCount(), carried.getHoverName());
		}
	}

	// ---------------------------------------------------------------- honing wears off

	/** A honed weapon landed a hit. */
	public static void hit(ServerPlayer player, ItemStack weapon) {
		wear(player, weapon, true);
	}

	/** A honed tool broke a block. */
	public static void mined(ServerPlayer player, ItemStack tool) {
		wear(player, tool, false);
	}

	private static void wear(ServerPlayer player, ItemStack stack, boolean weapon) {
		Honed honed = stack.get(SmithingComponents.HONED);
		if (honed == null || honed.weapon() != weapon) {
			return;
		}
		int left = honed.left() - 1;
		if (left > 0) {
			stack.set(SmithingComponents.HONED, new Honed(honed.weapon(), left));
			return;
		}
		stack.remove(SmithingComponents.HONED);
		Forging.refresh(stack);
		SmithingSkill.chip(player, SmithingSkill.HONING, "honing.worn", stack.getHoverName());
	}
}
