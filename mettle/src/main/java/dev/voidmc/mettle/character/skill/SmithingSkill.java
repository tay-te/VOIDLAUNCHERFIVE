package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.smithing.Forged;
import dev.voidmc.mettle.character.skill.smithing.Forging;
import dev.voidmc.mettle.character.skill.smithing.Grinding;
import dev.voidmc.mettle.character.skill.smithing.SmithingComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Smithing (TREES.md §10). XP (BALANCE.md §3): 8 per metal unit in a crafted tool, weapon or armour piece (an ingot is
 * 1, a diamond 2), 2 for wooden and stone tools, 1 per ingot taken from a furnace, 50 for a smithing upgrade, 10 + 5 per
 * level for an anvil job. Only the crafting grid, the furnace, the smithing table and the anvil teach it: a villager's
 * iron sword or nine ingots knocked out of a block are not smithing.
 *
 * <p>The perks live in {@code skill.smithing}: crafted pieces are stamped ({@link Forging}, component
 * {@code mettle:forged}), the anvil ({@code Anvils}), the smithing table ({@code Reforging}) and the grindstone
 * ({@link Grinding}) are hooked from {@code mixin.smithing}.
 */
public final class SmithingSkill {
	public static final ResourceKey<Skill> SMITHING = Progress.skill("smithing");

	public static final Identifier TINKER = Mettle.id("smithing/tinker");
	public static final Identifier THRIFTY = Mettle.id("smithing/thrifty");
	public static final Identifier LIGHT_HAND = Mettle.id("smithing/light_hand");
	public static final Identifier RIVETED = Mettle.id("smithing/riveted");
	public static final Identifier PROOFED = Mettle.id("smithing/proofed");
	public static final Identifier SHIELDWRIGHT = Mettle.id("smithing/shieldwright");
	public static final Identifier FORGED = Mettle.id("smithing/forged");
	public static final Identifier SALVAGER = Mettle.id("smithing/salvager");
	public static final Identifier KEEN_EDGE = Mettle.id("smithing/keen_edge");
	public static final Identifier TRUE_TEMPER = Mettle.id("smithing/true_temper");
	public static final Identifier REFORGE = Mettle.id("smithing/reforge");
	public static final Identifier MASTERWORK = Mettle.id("smithing/masterwork");
	public static final Identifier HONING = Mettle.id("smithing/honing");
	public static final Identifier HUNDRED_HAMMERS = Mettle.id("smithing/hundred_hammers");
	public static final Identifier BLACK_IRON = Mettle.id("smithing/black_iron");
	public static final Identifier FOLDED_STEEL = Mettle.id("smithing/folded_steel");

	/** Feat counters. */
	public static final String IRON_PIECES = "smithing_iron_pieces";
	public static final String NETHERITE_UPGRADES = "smithing_netherite_upgrades";

	public static final double REFORGE_XP = 10;
	/** XP (BALANCE.md §3): per metal unit in a crafted tool, weapon or armour piece; a diamond unit counts double. */
	public static final double XP_PER_UNIT = 25;
	public static final double XP_PER_DIAMOND_UNIT = 2 * XP_PER_UNIT;
	/** A wooden, stone or leather piece (no metal in it). */
	public static final double XP_PLAIN_PIECE = 5;
	/** An ingot taken from a furnace or blast furnace. */
	public static final double XP_INGOT = 4;
	/** A smithing-table upgrade (netherite). */
	public static final double XP_UPGRADE = 150;
	/** An anvil job: a flat amount plus this much per level it cost. */
	public static final double XP_ANVIL = 25;
	public static final double XP_ANVIL_PER_LEVEL = 10;

	private static boolean initialised;

	private SmithingSkill() {
	}

	/** Registers the stamp components and Light Hand's weight modifier. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		SmithingComponents.init();
		MettleServices.addWeightModifier(Forging::weight);
	}

	/** A feedback chip, {@code mettle.feedback.smithing.<key>}. */
	public static void chip(ServerPlayer player, Identifier perk, String key, Object... args) {
		SignaturePerks.feedback(player, perk, Component.translatable("mettle.feedback.smithing." + key, args));
	}

	/** From ItemStack#onCraftedBy, after the piece was stamped: XP, the feat tallies, and what went into it. */
	public static void crafted(ServerPlayer player, ItemStack result, int count) {
		AbstractContainerMenu menu = player.containerMenu;
		String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
		int units = units(result);
		if (menu instanceof SmithingMenu smithing) {
			if (smithing.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()) {
				Progress.award(player, SMITHING, REFORGE_XP);
				return;
			}
			if (units > 0) {
				Progress.award(player, SMITHING, XP_UPGRADE * count);
				made(player, result, path, count, false);
			}
			return;
		}
		if (menu instanceof AbstractFurnaceMenu) {
			if (path.endsWith("_ingot")) {
				Progress.award(player, SMITHING, XP_INGOT * count);
			}
			return;
		}
		if (!(menu instanceof AbstractCraftingMenu) || units <= 0) {
			return;
		}
		double perUnit = path.startsWith("diamond_") ? XP_PER_DIAMOND_UNIT
			: path.startsWith("iron_") || path.startsWith("golden_") || path.startsWith("copper_") || path.startsWith("chainmail_") ? XP_PER_UNIT : 0;
		Progress.award(player, SMITHING, perUnit > 0 ? perUnit * units * count : XP_PLAIN_PIECE * count);
		made(player, result, path, count, true);
	}

	/** A piece made at the grid or upgraded at the table: move the Masterwork count, tally the feat, say what went in. */
	private static void made(ServerPlayer player, ItemStack result, String path, int count, boolean fresh) {
		FeatCounters.add(player, Forging.PIECES, count);
		if (path.startsWith("iron_") || path.startsWith("chainmail_") || path.startsWith("diamond_") || path.startsWith("netherite_")) {
			FeatCounters.add(player, IRON_PIECES, count);
		}
		Forged forged = result.get(SmithingComponents.FORGED);
		if (!fresh || forged == null) {
			return;
		}
		if (forged.has(Forged.Mark.MASTERWORK)) {
			chip(player, MASTERWORK, "masterwork", result.getItem().getName(result.getItem().getDefaultInstance()));
		}
		List<Component> names = new ArrayList<>();
		for (Forged.Mark mark : forged.marks()) {
			if (mark != Forged.Mark.MASTERWORK) {
				names.add(Component.translatable("mettle.perk." + perk(mark).getPath().replace('/', '.')));
			}
		}
		if (!names.isEmpty()) {
			chip(player, perk(forged.marks().getFirst()), "stamped", ComponentUtils.formatList(names, Component.literal(", ")));
		}
	}

	/** The perk behind a stamp mark. */
	public static Identifier perk(Forged.Mark mark) {
		return switch (mark) {
			case LIGHT_HAND -> LIGHT_HAND;
			case RIVETED -> RIVETED;
			case PROOFED -> PROOFED;
			case SHIELDWRIGHT -> SHIELDWRIGHT;
			case FORGED -> FORGED;
			case KEEN_EDGE -> KEEN_EDGE;
			case TRUE_TEMPER -> TRUE_TEMPER;
			case FOLDED_STEEL -> FOLDED_STEEL;
			case MASTERWORK -> MASTERWORK;
		};
	}

	/** Anvil job taken: XP grows with the levels it cost. */
	public static void anvil(ServerPlayer player, int cost) {
		Progress.award(player, SMITHING, XP_ANVIL + XP_ANVIL_PER_LEVEL * Math.max(0, cost));
	}

	/** Metal units in a tool, weapon, armour piece or shield (as in its recipe), or 0. */
	public static int units(ItemStack stack) {
		if (stack.is(ItemTags.CHEST_ARMOR)) {
			return 8;
		}
		if (stack.is(ItemTags.LEG_ARMOR)) {
			return 7;
		}
		if (stack.is(ItemTags.HEAD_ARMOR)) {
			return 5;
		}
		if (stack.is(ItemTags.FOOT_ARMOR)) {
			return 4;
		}
		if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)) {
			return 3;
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.HOES)) {
			return 2;
		}
		if (stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || Forging.isShield(stack)) {
			return 1;
		}
		return 0;
	}
}
