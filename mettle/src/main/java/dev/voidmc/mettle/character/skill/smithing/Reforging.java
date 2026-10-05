package dev.voidmc.mettle.character.skill.smithing;

import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import dev.voidmc.mettle.character.skill.smithing.Forged.Reforge;
import java.util.List;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The smithing table's Smithing perks. <b>Reforge</b> (mastery): with no template, armour plus a feather, an ingot or an
 * amethyst shard comes out Light, Sturdy or Warded (one reforge per piece; a new one replaces the old). <b>Black
 * Iron</b> (feat, earned by nine netherite upgrades): an upgrade hands its template back one time in three.
 */
public final class Reforging {
	private Reforging() {
	}

	public static Reforge kind(ItemStack material) {
		if (material.is(Items.FEATHER)) {
			return Reforge.LIGHT;
		}
		if (material.is(ConventionalItemTags.INGOTS)) {
			return Reforge.STURDY;
		}
		if (material.is(Items.AMETHYST_SHARD)) {
			return Reforge.WARDED;
		}
		return Reforge.NONE;
	}

	/** The reforged piece for an empty-template smithing table, or empty when this isn't a reforge. */
	public static ItemStack result(Player player, ItemStack template, ItemStack base, ItemStack material) {
		if (!template.isEmpty() || base.isEmpty() || !Forging.isArmour(base) || !Perks.has(player, SmithingSkill.REFORGE)) {
			return ItemStack.EMPTY;
		}
		Reforge kind = kind(material);
		Forged forged = base.get(SmithingComponents.FORGED);
		if (kind == Reforge.NONE || (forged != null && forged.reforge() == kind)) {
			return ItemStack.EMPTY;
		}
		ItemStack out = base.copyWithCount(1);
		out.set(SmithingComponents.FORGED, (forged == null ? new Forged(player.getName().getString(), List.of(), Reforge.NONE) : forged).withReforge(kind));
		Forging.refresh(out);
		return out;
	}

	public static void reforged(ServerPlayer player, ItemStack carried) {
		Forged forged = carried.get(SmithingComponents.FORGED);
		if (forged != null && forged.reforge() != Reforge.NONE) {
			SmithingSkill.chip(player, SmithingSkill.REFORGE, "reforge", Component.translatable("mettle.smithing.reforge." + forged.reforge().getSerializedName()));
		}
	}

	public static boolean isNetheriteUpgrade(ItemStack template, ItemStack result) {
		return template.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
			&& BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().startsWith("netherite_");
	}

	/** A netherite upgrade was taken: tally it, and decide whether Black Iron hands the template back. */
	public static boolean upgraded(ServerPlayer player) {
		FeatCounters.add(player, SmithingSkill.NETHERITE_UPGRADES, 1);
		return Perks.has(player, SmithingSkill.BLACK_IRON) && player.getRandom().nextDouble() < SmithingRules.BLACK_IRON_CHANCE;
	}
}
