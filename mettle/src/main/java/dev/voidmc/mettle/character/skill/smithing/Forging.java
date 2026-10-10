package dev.voidmc.mettle.character.skill.smithing;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import dev.voidmc.mettle.character.skill.smithing.Forged.Mark;
import dev.voidmc.mettle.character.skill.smithing.Forged.Reforge;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

/**
 * Stamping what a smith makes, and keeping every stamped item's numbers true to its stamp. The stamp goes on in
 * {@code Item#onCraftedBy} (crafting table and smithing table, never a villager's trade or a stonecutter), and
 * {@link #refresh} rebuilds the derived components from the item's own defaults each time, so a netherite upgrade of a
 * Proofed diamond chestplate becomes a Proofed netherite chestplate rather than keeping diamond's numbers.
 *
 * <p>Bonuses fold into the item's own vanilla modifiers where it has one (a Keen Edge iron sword reads "7 Attack
 * Damage", not "6" and "+1"); otherwise they get ids {@code mettle:smithing/<attribute>[/<slot>]}, one per slot group so
 * two worn pieces never collide.
 */
public final class Forging {
	/** Feat counter that numbers a smith's pieces; Masterwork rolls on it (see {@link SmithingRules#masterwork}). */
	public static final String PIECES = "smithing_pieces";

	private Forging() {
	}

	// ---------------------------------------------------------------- what an item is

	@Nullable
	public static EquipmentSlot armourSlot(DataComponentGetter stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
			return null;
		}
		boolean[] armour = new boolean[1];
		ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (mods != null) {
			mods.forEach(EquipmentSlotGroup.bySlot(equippable.slot()), (attribute, modifier) -> armour[0] |= attribute.equals(Attributes.ARMOR) && modifier.amount() > 0);
		}
		return armour[0] ? equippable.slot() : null;
	}

	public static boolean isArmour(ItemStack stack) {
		return armourSlot(stack) != null;
	}

	public static boolean isShield(ItemStack stack) {
		return stack.has(DataComponents.BLOCKS_ATTACKS) && stack.isDamageableItem();
	}

	/** Swords, spears, axes and maces: Keen Edge, weapon honing, a Masterwork's +1 damage. */
	public static boolean isWeapon(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE);
	}

	/** Pickaxes, axes, shovels and hoes: True Temper. */
	public static boolean isDigTool(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
	}

	public static boolean isIron(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.startsWith("iron_") || path.startsWith("chainmail_");
	}

	/** The tool's own top digging speed (6 for an iron pickaxe), what True Temper and honing take a share of. */
	public static double digSpeed(DataComponentMap components) {
		Tool tool = components.get(DataComponents.TOOL);
		if (tool == null) {
			return 0;
		}
		double best = tool.defaultMiningSpeed();
		for (Tool.Rule rule : tool.rules()) {
			best = Math.max(best, rule.speed().orElse(0f));
		}
		return best;
	}

	// ---------------------------------------------------------------- stamping

	/**
	 * Item#onCraftedBy on the server, for pieces taken from a crafting grid or a smithing table upgrade. Called twice for
	 * a shift-click (the moved stack and the copy vanilla reports), and both come out the same: the Masterwork roll reads
	 * the piece count that {@link SmithingSkill#crafted} only advances after.
	 */
	public static void onCrafted(ServerPlayer player, ItemStack stack) {
		AbstractContainerMenu menu = player.containerMenu;
		boolean upgrade = menu instanceof SmithingMenu smithing && !smithing.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty();
		if (!(menu instanceof AbstractCraftingMenu) && !upgrade) {
			return;
		}
		if (stack.has(SmithingComponents.FORGED)) {
			refresh(stack);
			return;
		}
		Forged forged = stamp(player, stack, FeatCounters.get(player, PIECES));
		if (forged != null) {
			stack.set(SmithingComponents.FORGED, forged);
			refresh(stack);
		} else if (stack.has(SmithingComponents.HONED)) {
			refresh(stack);
		}
	}

	/** What a smith's perks fold into one piece (the {@code n}-th they made), or null when nothing does. */
	public static @Nullable Forged stamp(ServerPlayer player, ItemStack stack, long n) {
		boolean armour = isArmour(stack);
		boolean shield = isShield(stack);
		boolean weapon = isWeapon(stack);
		boolean dig = isDigTool(stack);
		if (!armour && !shield && !weapon && !dig) {
			return null;
		}
		List<Mark> marks = new ArrayList<>();
		if (armour && Perks.has(player, SmithingSkill.LIGHT_HAND)) {
			marks.add(Mark.LIGHT_HAND);
		}
		if ((armour || shield) && Perks.has(player, SmithingSkill.RIVETED)) {
			marks.add(Mark.RIVETED);
		}
		if (armour && Perks.has(player, SmithingSkill.PROOFED)) {
			marks.add(Mark.PROOFED);
		}
		if (shield && Perks.has(player, SmithingSkill.SHIELDWRIGHT)) {
			marks.add(Mark.SHIELDWRIGHT);
		}
		if ((weapon || dig) && Perks.has(player, SmithingSkill.FORGED)) {
			marks.add(Mark.FORGED);
		}
		if (weapon && Perks.has(player, SmithingSkill.KEEN_EDGE)) {
			marks.add(Mark.KEEN_EDGE);
		}
		if (dig && Perks.has(player, SmithingSkill.TRUE_TEMPER)) {
			marks.add(Mark.TRUE_TEMPER);
		}
		if ((armour || weapon || dig) && isIron(stack) && Perks.has(player, SmithingSkill.FOLDED_STEEL)) {
			marks.add(Mark.FOLDED_STEEL);
		}
		if ((armour || weapon || dig) && Perks.has(player, SmithingSkill.MASTERWORK) && SmithingRules.masterwork(seed(player), n)) {
			marks.add(Mark.MASTERWORK);
		}
		if (weapon && Perks.has(player, dev.voidmc.mettle.character.draft.Cursed.BRITTLE_EDGE)) {
			marks.add(Mark.BRITTLE_EDGE);
		}
		if (Perks.has(player, dev.voidmc.mettle.character.draft.Cursed.SLAPDASH)) {
			marks.add(Mark.SLAPDASH);
		}
		return marks.isEmpty() ? null : new Forged(player.getName().getString(), marks, Reforge.NONE);
	}

	public static long seed(Player player) {
		return player.getUUID().getMostSignificantBits() ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 17);
	}

	// ---------------------------------------------------------------- derived components

	/** Rebuilds durability, attribute modifiers, name and shield function from the item's defaults plus its stamps. */
	public static void refresh(ItemStack stack) {
		DataComponentMap proto = stack.getPrototype();
		Forged forged = stack.get(SmithingComponents.FORGED);
		Honed honed = stack.get(SmithingComponents.HONED);

		Integer protoMax = proto.get(DataComponents.MAX_DAMAGE);
		if (protoMax != null) {
			int max = forged == null ? protoMax : SmithingRules.maxDamage(protoMax, forged);
			stack.set(DataComponents.MAX_DAMAGE, max);
			if (stack.getDamageValue() >= max) {
				stack.setDamageValue(max - 1);
			}
		}

		boolean weapon = isWeapon(stack);
		boolean dig = isDigTool(stack);
		EquipmentSlot armourSlot = armourSlot(proto);
		double damage = 0;
		double armor = 0;
		double toughness = 0;
		double digBonus = 0;
		double speed = digSpeed(proto);
		if (forged != null) {
			if (forged.has(Mark.KEEN_EDGE) && weapon) {
				damage += 1;
			}
			if (forged.has(Mark.BRITTLE_EDGE) && weapon) {
				damage += dev.voidmc.mettle.character.draft.Cursed.BRITTLE_EDGE_DAMAGE;
			}
			if (forged.has(Mark.PROOFED) && armourSlot != null) {
				armor += 1;
			}
			if (forged.reforge() == Reforge.WARDED && armourSlot != null) {
				toughness += 1;
			}
			if (forged.has(Mark.TRUE_TEMPER) && dig) {
				digBonus += SmithingRules.TRUE_TEMPER * speed;
			}
			if (forged.has(Mark.MASTERWORK)) {
				if (weapon) {
					damage += 1;
				} else if (armourSlot != null) {
					armor += 1;
				} else if (dig) {
					digBonus += 1;
				}
			}
		}
		if (honed != null && honed.left() > 0) {
			if (honed.weapon() && weapon) {
				damage += SmithingRules.HONED_DAMAGE;
			} else if (!honed.weapon() && dig) {
				digBonus += SmithingRules.HONED_DIG * speed;
			}
		}
		ItemAttributeModifiers base = proto.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		EquipmentSlotGroup armourGroup = armourSlot == null ? null : EquipmentSlotGroup.bySlot(armourSlot);
		List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(base.modifiers());
		fold(entries, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND, Item.BASE_ATTACK_DAMAGE_ID, damage);
		if (armourGroup != null) {
			fold(entries, Attributes.ARMOR, armourGroup, null, armor);
			fold(entries, Attributes.ARMOR_TOUGHNESS, armourGroup, null, toughness);
		}
		if (digBonus > 0) {
			entries.add(new ItemAttributeModifiers.Entry(Attributes.MINING_EFFICIENCY,
				new AttributeModifier(Mettle.id("smithing/mining_efficiency"), digBonus, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));

		Component protoName = proto.get(DataComponents.ITEM_NAME);
		if (protoName != null) {
			stack.set(DataComponents.ITEM_NAME, forged != null && forged.has(Mark.MASTERWORK)
				? Component.translatable("mettle.smithing.masterwork_name", protoName).withStyle(ChatFormatting.GOLD)
				: protoName);
		}

		BlocksAttacks blocks = proto.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks != null) {
			stack.set(DataComponents.BLOCKS_ATTACKS, forged != null && forged.has(Mark.SHIELDWRIGHT) ? shieldwright(blocks) : blocks);
		}
	}

	/** Shieldwright: no raise delay, half the wear per block. */
	public static BlocksAttacks shieldwright(BlocksAttacks b) {
		BlocksAttacks.ItemDamageFunction wear = b.itemDamage();
		return new BlocksAttacks(0f, b.disableCooldownScale(), b.damageReductions(),
			new BlocksAttacks.ItemDamageFunction(wear.threshold(), SmithingRules.shieldWear(wear.base()), SmithingRules.shieldWear(wear.factor())),
			b.bypassedBy(), b.blockSound(), b.disableSound());
	}

	/**
	 * Adds {@code amount} to the item's own flat modifier for an attribute in a slot group (the one with {@code id} when
	 * given), or adds a new one with a Mettle id when it has none.
	 */
	private static void fold(List<ItemAttributeModifiers.Entry> entries, Holder<Attribute> attribute, EquipmentSlotGroup group, @Nullable Identifier id, double amount) {
		if (amount == 0) {
			return;
		}
		for (int i = 0; i < entries.size(); i++) {
			ItemAttributeModifiers.Entry e = entries.get(i);
			AttributeModifier m = e.modifier();
			if (e.attribute().equals(attribute) && e.slot() == group && m.operation() == AttributeModifier.Operation.ADD_VALUE && (id == null || m.is(id))) {
				entries.set(i, new ItemAttributeModifiers.Entry(attribute, new AttributeModifier(m.id(), m.amount() + amount, m.operation()), group, e.display()));
				return;
			}
		}
		String path = attribute.unwrapKey().map(k -> k.identifier().getPath()).orElse("bonus");
		entries.add(new ItemAttributeModifiers.Entry(attribute,
			new AttributeModifier(Mettle.id("smithing/" + path + "/" + group.getSerializedName()), amount, AttributeModifier.Operation.ADD_VALUE), group));
	}

	// ---------------------------------------------------------------- weight and Thrifty

	/** Light Hand and a Light reforge, as a {@code WeightModifier}: the stamp travels, so it is lighter for anyone. */
	public static float weight(Player player, ItemInstance stack) {
		Forged forged = stack.get(SmithingComponents.FORGED);
		return forged == null ? 1f : SmithingRules.weight(forged);
	}

	/**
	 * Thrifty: one of the ingots in the grid comes back. Never for storage blocks, ingots or nuggets made from ingots,
	 * so it can't be looped into free metal.
	 */
	public static void thrifty(ServerPlayer player, Container grid, ItemStack result) {
		int rank = Perks.rank(player, SmithingSkill.THRIFTY);
		if (rank <= 0 || result.isEmpty() || result.is(ConventionalItemTags.INGOTS) || result.is(ConventionalItemTags.NUGGETS)
			|| result.is(ConventionalItemTags.STORAGE_BLOCKS)) {
			return;
		}
		List<ItemStack> ingots = new ArrayList<>();
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack s = grid.getItem(i);
			if (s.is(ConventionalItemTags.INGOTS)) {
				ingots.add(s);
			}
		}
		if (ingots.isEmpty() || player.getRandom().nextDouble() >= rank * SmithingRules.THRIFTY_PER_RANK) {
			return;
		}
		ItemStack back = ingots.get(player.getRandom().nextInt(ingots.size())).copyWithCount(1);
		Component name = back.getHoverName();
		if (!player.getInventory().add(back)) {
			player.drop(back, false, Prediction.SERVER_ONLY);
		}
		SmithingSkill.chip(player, SmithingSkill.THRIFTY, "thrifty", name);
	}
}
