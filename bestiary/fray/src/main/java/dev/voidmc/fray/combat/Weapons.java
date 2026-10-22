package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.combat.action.WeaponFamily;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vanilla weapons in Fray's terms (CONTRACTS.md §5): families by item tag and their default damage split. Arsenal's
 * {@code fray:damage_split} component wins over the defaults.
 */
public final class Weapons {
	private Weapons() {
	}

	public static WeaponFamily family(ItemStack stack) {
		if (stack.isEmpty()) {
			return WeaponFamily.UNARMED;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return WeaponFamily.SWORD;
		}
		if (stack.is(ItemTags.AXES)) {
			return WeaponFamily.AXE;
		}
		if (stack.is(Items.MACE)) {
			return WeaponFamily.MACE;
		}
		if (stack.is(ItemTags.SPEARS) || stack.is(Items.TRIDENT)) {
			return WeaponFamily.SPEAR;
		}
		if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
			return WeaponFamily.BOW;
		}
		return WeaponFamily.UNARMED;
	}

	/** True for items the attack key mines with (tools: axe, pickaxe, shovel, hoe); a sword never mines. */
	public static boolean isTool(ItemStack stack) {
		return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
	}

	/** True when the off hand (or main hand) holds something that blocks like a shield. */
	public static boolean hasShield(LivingEntity e) {
		return e.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) || e.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	public static DamageSplit split(ItemStack stack) {
		DamageSplit own = stack.get(DamageSplit.TYPE);
		if (own != null) {
			return own;
		}
		return switch (family(stack)) {
			case SWORD -> DamageSplit.of(DamageKind.SLASH, 1.0F);
			case AXE -> DamageSplit.of(DamageKind.SLASH, 0.7F, DamageKind.BLUNT, 0.3F, 1.3F);
			case MACE -> DamageSplit.of(DamageKind.BLUNT, 1.8F);
			case SPEAR -> DamageSplit.of(DamageKind.PIERCE, 1.0F);
			case BOW -> DamageSplit.of(DamageKind.BLUNT, 0.6F);
			default -> stack.isEmpty() ? DamageSplit.of(DamageKind.BLUNT, 0.6F) : DamageSplit.of(DamageKind.BLUNT, 0.8F);
		};
	}

	/** Armour class by worn armour value (Mettle's weights later): light ≤ 7 (leather), medium ≤ 12 (chain), heavy above. */
	public static int armourClass(Player p) {
		int a = p.getArmorValue();
		return a <= 7 ? 0 : a <= 12 ? 1 : 2;
	}
}
