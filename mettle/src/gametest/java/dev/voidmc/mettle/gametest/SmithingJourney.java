package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.WeightModifier;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import dev.voidmc.mettle.character.skill.smithing.Forged;
import dev.voidmc.mettle.character.skill.smithing.Forging;
import dev.voidmc.mettle.character.skill.smithing.Honed;
import dev.voidmc.mettle.character.skill.smithing.SmithingComponents;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Smithing tree played for real on the server: pieces crafted through a crafting table's result slot (clicks and
 * shift-clicks), anvil, smithing table and grindstone menus driven the way a player's clicks drive them, and every
 * stamp, number, refund and feat checked. Three careers (Armourer with Reforge, Toolwright with Honing, Masterwork)
 * with a respec between, then the earned perks. The work benches stand in the air above the player and go afterwards.
 */
final class SmithingJourney {
	private static final String LEARN = "execute as @p run mettle perk learn mettle:smithing/";

	private SmithingJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("mettle background set mettle:smith @a");
		server.runCommand("effect clear @a");
		// The player's things, kept aside and given back at the end; the benches' blocks likewise.
		List<ItemStack> kept = new ArrayList<>();
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		int[] levels = new int[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				kept.add(p.getInventory().getItem(i).copy());
			}
			p.getInventory().clearContent();
			for (Bench b : Bench.values()) {
				BlockPos pos = b.pos(p);
				blocks.put(pos, p.level().getBlockState(pos));
				p.level().setBlock(pos, b.block.defaultBlockState(), 3);
			}
			levels[0] = p.experienceLevel;
			p.giveExperienceLevels(500);
			return null;
		}));
		server.runCommand("mettle xp mettle:smithing 30000 @a");
		strength(server);

		armourer(context, world, server);
		server.runCommand("execute as @p run mettle respec");
		strength(server);
		toolwright(context, world, server);
		server.runCommand("execute as @p run mettle respec");
		strength(server);
		masterwork(world, server);
		earned(world, server);

		// Leave the character as found: bought perks refunded, earned Smithing perks and tallies taken back, the player's
		// things returned, the benches gone.
		server.runCommand("execute as @p run mettle respec");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.closeContainer();
			CharacterState state = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Integer> held = new HashMap<>(state.perks());
			held.keySet().removeIf(k -> k.identifier().getPath().startsWith("smithing/"));
			CharacterStore.set(p, state.withPerks(held));
			EffectsApplier.apply(p);
			Map<String, Long> counters = new HashMap<>(p.getAttachedOrElse(FeatCounters.COUNTERS, Map.of()));
			counters.keySet().removeIf(k -> k.startsWith("smithing_"));
			p.setAttached(FeatCounters.COUNTERS, Map.copyOf(counters));
			p.getInventory().clearContent();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				p.setItemSlot(slot, ItemStack.EMPTY);
			}
			for (int i = 0; i < kept.size(); i++) {
				p.getInventory().setItem(i, kept.get(i));
			}
			blocks.forEach((pos, state0) -> p.level().setBlock(pos, state0, 3));
			p.stopUsingItem();
			p.setExperienceLevels(levels[0]);
			p.setHealth(p.getMaxHealth());
			return null;
		}));
		server.runCommand("kill @e[type=item]");
		server.runCommand("kill @e[type=experience_orb]");
		server.runCommand("kill @e[tag=smithing_t]");
		server.runCommand("mettle background set mettle:ranger @a");
		wait(world, 5);
	}

	/** Strength to 14, the Mastery gate. */
	private static void strength(TestServerContext server) {
		for (int i = 0; i < 8; i++) {
			server.runCommand("execute as @p run mettle attribute strength");
		}
	}

	// ---------------------------------------------------------------- Career 1: Armourer, Reforge, Tinker

	private static void armourer(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server) {
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "tinker");
		}
		for (String perk : new String[] {"light_hand", "riveted", "proofed", "shieldwright", "reforge"}) {
			server.runCommand(LEARN + perk);
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (Identifier id : new Identifier[] {SmithingSkill.LIGHT_HAND, SmithingSkill.RIVETED, SmithingSkill.PROOFED, SmithingSkill.SHIELDWRIGHT, SmithingSkill.REFORGE}) {
				if (!Perks.has(p, id)) {
					return "Armourer: " + id + " not learned: " + CharacterStore.get(p).perks() + " strength " + CharacterStore.get(p).spent();
				}
			}
			return Perks.rank(p, SmithingSkill.TINKER) == 3 ? null : "Tinker not at rank 3";
		}));

		// A full iron set and a shield through the crafting table: two by click, the rest by shift-click.
		ItemStack[] set = new ItemStack[4];
		ItemStack[] shield = new ItemStack[1];
		ItemStack[] diamond = new ItemStack[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CraftingMenu m = crafting(p);
			set[0] = craft(p, m, "###" + "# #" + "   ", false, '#', Items.IRON_INGOT);
			set[1] = craft(p, m, "# #" + "###" + "###", false, '#', Items.IRON_INGOT);
			set[2] = craft(p, m, "###" + "# #" + "# #", true, '#', Items.IRON_INGOT);
			set[3] = craft(p, m, "# #" + "# #" + "   ", true, '#', Items.IRON_INGOT);
			shield[0] = craft(p, m, "WIW" + "WWW" + " W ", true, 'W', Items.OAK_PLANKS, 'I', Items.IRON_INGOT);
			diamond[0] = craft(p, m, "# #" + "###" + "###", false, '#', Items.DIAMOND);
			p.closeContainer();
			return null;
		}));
		check(server.computeOnServer(s -> {
			ItemStack chest = set[1];
			Forged forged = chest.get(SmithingComponents.FORGED);
			if (forged == null || !forged.has(Forged.Mark.LIGHT_HAND) || !forged.has(Forged.Mark.RIVETED) || !forged.has(Forged.Mark.PROOFED)) {
				return "Armourer: chestplate stamp " + forged;
			}
			if (!forged.smith().equals(player(s).getName().getString())) {
				return "Armourer: smith " + forged.smith();
			}
			for (ItemStack piece : set) {
				if (!piece.has(SmithingComponents.FORGED)) {
					return "Armourer: " + piece + " (shift-clicked or not) has no stamp";
				}
			}
			// Riveted: +30 % durability, armour and shields.
			if (chest.getMaxDamage() != 312) {
				return "Riveted: iron chestplate lasts " + chest.getMaxDamage() + ", expected 312";
			}
			if (shield[0].getMaxDamage() != Math.round(336 * 1.3)) {
				return "Riveted: shield lasts " + shield[0].getMaxDamage() + ", expected " + Math.round(336 * 1.3);
			}
			// Proofed: +1 armour folded into the piece's own modifier.
			double armour = modifier(chest, Attributes.ARMOR, EquipmentSlotGroup.CHEST);
			if (armour != 7) {
				return "Proofed: iron chestplate armour " + armour + ", expected 7";
			}
			if (modifier(diamond[0], Attributes.ARMOR, EquipmentSlotGroup.CHEST) != 9 || diamond[0].getMaxDamage() != Math.round(528 * 1.3)) {
				return "Proofed/Riveted diamond chestplate: " + diamond[0].get(DataComponents.ATTRIBUTE_MODIFIERS) + " max " + diamond[0].getMaxDamage();
			}
			// Light Hand, through the burden module's modifier list: 25 % lighter for anyone, a plain piece unchanged.
			float light = weight(player(s), chest);
			float plain = weight(player(s), new ItemStack(Items.IRON_CHESTPLATE));
			if (Math.abs(light - 0.75f) > 1e-4 || plain != 1f) {
				return "Light Hand: weight multiplier " + light + " (plain " + plain + "), expected 0.75";
			}
			// Shieldwright: no raise delay, half the wear.
			var blocks = shield[0].get(DataComponents.BLOCKS_ATTACKS);
			if (blocks == null || blocks.blockDelayTicks() != 0) {
				return "Shieldwright: raise delay " + (blocks == null ? "none" : blocks.blockDelayTicks());
			}
			return null;
		}));

		// Proofed, worn: a crafted set gives 4 more armour than a plain one.
		double[] plainArmour = new double[1];
		server.runOnServer(s -> wear(player(s), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)));
		wait(world, 3);
		server.runOnServer(s -> {
			plainArmour[0] = player(s).getAttributeValue(Attributes.ARMOR);
			wear(player(s), set[0].copy(), set[1].copy(), set[2].copy(), set[3].copy());
		});
		wait(world, 3);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			double armour = p.getAttributeValue(Attributes.ARMOR);
			wear(p, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
			return armour == plainArmour[0] + 4 ? null : "Proofed: full set armour " + armour + ", plain " + plainArmour[0] + ", expected +4";
		}));

		// Shieldwright: raised the moment it is used, and a 10-damage block wears it 5, not 11.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			p.startUsingItem(InteractionHand.OFF_HAND);
			boolean plainUp = p.isBlocking();
			p.stopUsingItem();
			ItemStack mine = shield[0].copy();
			p.setItemInHand(InteractionHand.OFF_HAND, mine);
			p.startUsingItem(InteractionHand.OFF_HAND);
			boolean mineUp = p.isBlocking();
			p.stopUsingItem();
			mine.get(DataComponents.BLOCKS_ATTACKS).hurtBlockingItem(p.level(), mine, p, InteractionHand.OFF_HAND, 10f);
			ItemStack plain = new ItemStack(Items.SHIELD);
			plain.get(DataComponents.BLOCKS_ATTACKS).hurtBlockingItem(p.level(), plain, p, InteractionHand.OFF_HAND, 10f);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			if (plainUp || !mineUp) {
				return "Shieldwright: plain shield up at once " + plainUp + ", crafted " + mineUp;
			}
			return mine.getDamageValue() == 5 && plain.getDamageValue() == 11 ? null
				: "Shieldwright: a 10-damage block wore " + mine.getDamageValue() + " (plain " + plain.getDamageValue() + "), expected 5 and 11";
		}));

		// Reforge: Light with a feather, Warded with an amethyst shard (replacing Light), Sturdy with an ingot.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			SmithingMenu m = smithing(p);
			if (!m.getSlot(SmithingMenu.ADDITIONAL_SLOT).mayPlace(new ItemStack(Items.FEATHER))) {
				return "Reforge: a feather doesn't fit the smithing table";
			}
			ItemStack light = reforge(p, m, set[1].copy(), new ItemStack(Items.FEATHER));
			Forged f = light.get(SmithingComponents.FORGED);
			if (f == null || f.reforge() != Forged.Reforge.LIGHT || Math.abs(weight(p, light) - 0.375f) > 1e-4) {
				p.closeContainer();
				return "Reforge Light: " + f + " weight " + weight(p, light) + ", expected 0.375 with Light Hand";
			}
			if (!m.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty() || !m.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty()) {
				p.closeContainer();
				return "Reforge: the piece and the feather were not used up";
			}
			ItemStack warded = reforge(p, m, light, new ItemStack(Items.AMETHYST_SHARD));
			ItemStack sturdy = reforge(p, m, warded.copy(), new ItemStack(Items.IRON_INGOT));
			m.getSlot(SmithingMenu.BASE_SLOT).set(sturdy.copy());
			m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.GOLD_INGOT));
			boolean again = !m.getSlot(3).getItem().isEmpty();
			m.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(Items.IRON_SWORD));
			m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.FEATHER));
			boolean sword = !m.getSlot(3).getItem().isEmpty();
			m.getSlot(SmithingMenu.BASE_SLOT).set(ItemStack.EMPTY);
			m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(ItemStack.EMPTY);
			p.closeContainer();
			if (modifier(warded, Attributes.ARMOR_TOUGHNESS, EquipmentSlotGroup.CHEST) != 1 || warded.get(SmithingComponents.FORGED).reforge() != Forged.Reforge.WARDED
				|| Math.abs(weight(p, warded) - 0.75f) > 1e-4) {
				return "Reforge Warded: toughness " + modifier(warded, Attributes.ARMOR_TOUGHNESS, EquipmentSlotGroup.CHEST) + " weight " + weight(p, warded);
			}
			if (sturdy.getMaxDamage() != 432) {
				return "Reforge Sturdy: lasts " + sturdy.getMaxDamage() + ", expected 432 (Riveted +30 %, Sturdy +50 %)";
			}
			if (again || sword) {
				return "Reforge: a second Sturdy (" + again + ") or a sword (" + sword + ") was offered";
			}
			return null;
		}));

		// A netherite upgrade keeps the stamp and takes netherite's own numbers: 8 + 1 armour, 3 toughness, 592 × 1.3.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			SmithingMenu m = smithing(p);
			m.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
			m.getSlot(SmithingMenu.BASE_SLOT).set(diamond[0].copy());
			m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.NETHERITE_INGOT));
			ItemStack up = take(p, m, 3);
			p.closeContainer();
			Forged f = up.get(SmithingComponents.FORGED);
			double armour = modifier(up, Attributes.ARMOR, EquipmentSlotGroup.CHEST);
			double tough = modifier(up, Attributes.ARMOR_TOUGHNESS, EquipmentSlotGroup.CHEST);
			double knockback = modifier(up, Attributes.KNOCKBACK_RESISTANCE, EquipmentSlotGroup.CHEST);
			if (!up.is(Items.NETHERITE_CHESTPLATE) || f == null || !f.has(Forged.Mark.PROOFED)) {
				return "Upgrade lost the stamp: " + up + " " + f;
			}
			return armour == 9 && tough == 3 && Math.abs(knockback - 0.1) < 1e-6 && up.getMaxDamage() == Math.round(592 * 1.3) ? null
				: "Upgrade numbers: armour " + armour + " toughness " + tough + " knockback " + knockback + " lasts " + up.getMaxDamage();
		}));

		// Tinker III: a 1-ingot repair of a sword with 7 levels of prior work costs 5, not 8; an enchanting job keeps its price.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(100);
			sword.set(DataComponents.REPAIR_COST, 7);
			AnvilMenu m = anvil(p);
			m.getSlot(0).set(sword.copy());
			m.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
			int cost = m.getCost();
			int before = p.experienceLevel;
			ItemStack repaired = take(p, m, 2);
			int paid = before - p.experienceLevel;
			m.getSlot(0).set(sword.copy());
			m.getSlot(1).set(EnchantmentHelper.createBook(new EnchantmentInstance(enchantment(p, Enchantments.SHARPNESS), 1)));
			int enchantCost = m.getCost();
			m.getSlot(0).set(ItemStack.EMPTY);
			m.getSlot(1).set(ItemStack.EMPTY);
			p.closeContainer();
			if (cost != 5 || paid != 5) {
				return "Tinker III: repair cost " + cost + ", paid " + paid + ", expected 5";
			}
			if (repaired.getOrDefault(DataComponents.REPAIR_COST, 0) != 15) {
				return "Tinker: prior work after " + repaired.get(DataComponents.REPAIR_COST) + ", expected vanilla's 15";
			}
			return enchantCost == 8 ? null : "Tinker discounted an enchanting job: " + enchantCost + ", expected 8";
		}));

		// The tooltip says what went in.
		server.runOnServer(s -> player(s).getInventory().setItem(0, set[1].copy()));
		wait(world, 5);
		check(context.computeOnClient(mc -> {
			String text = tooltip(mc, mc.player.getInventory().getItem(0));
			return text.contains("Light Hand") && text.contains("Riveted") && text.contains("Proofed") ? null : "Armourer tooltip: " + text;
		}));
		server.runOnServer(s -> player(s).getInventory().clearContent());
	}

	// ---------------------------------------------------------------- Career 2: Toolwright, Honing, Thrifty, Folded Steel

	private static void toolwright(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server) {
		for (int i = 0; i < 3; i++) {
			server.runCommand(LEARN + "thrifty");
		}
		for (String perk : new String[] {"forged", "salvager", "keen_edge", "true_temper", "honing"}) {
			server.runCommand(LEARN + perk);
		}
		server.runCommand("mettle perk grant mettle:smithing/folded_steel @a");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (Identifier id : new Identifier[] {SmithingSkill.FORGED, SmithingSkill.SALVAGER, SmithingSkill.KEEN_EDGE, SmithingSkill.TRUE_TEMPER,
				SmithingSkill.HONING, SmithingSkill.FOLDED_STEEL}) {
				if (!Perks.has(p, id)) {
					return "Toolwright: " + id + " not held: " + CharacterStore.get(p).perks();
				}
			}
			return Perks.has(p, SmithingSkill.REFORGE) ? "respec kept Reforge" : null;
		}));

		ItemStack[] made = new ItemStack[3];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CraftingMenu m = crafting(p);
			made[0] = craft(p, m, " # " + " # " + " | ", false, '#', Items.IRON_INGOT, '|', Items.STICK);
			made[1] = craft(p, m, "###" + " | " + " | ", true, '#', Items.IRON_INGOT, '|', Items.STICK);
			made[2] = craft(p, m, " # " + " # " + " | ", false, '#', Items.DIAMOND, '|', Items.STICK);
			p.getInventory().clearContent();
			p.closeContainer();
			ItemStack sword = made[0];
			ItemStack pick = made[1];
			Forged f = sword.get(SmithingComponents.FORGED);
			if (f == null || !f.has(Forged.Mark.FORGED) || !f.has(Forged.Mark.KEEN_EDGE) || !f.has(Forged.Mark.FOLDED_STEEL)) {
				return "Toolwright: sword stamp " + f;
			}
			// Forged +25 % and Folded Steel +100 %: 250 × 2.25.
			if (sword.getMaxDamage() != 563 || pick.getMaxDamage() != 563) {
				return "Forged + Folded Steel: sword lasts " + sword.getMaxDamage() + ", pickaxe " + pick.getMaxDamage() + ", expected 563";
			}
			// Folded Steel is iron only: the diamond sword gets Forged's 25 %.
			if (made[2].getMaxDamage() != Math.round(1561 * 1.25)) {
				return "Folded Steel touched a diamond sword: " + made[2].getMaxDamage();
			}
			// Keen Edge: +1 on the sword's own damage modifier (iron sword: 5 → 6).
			double damage = modifier(sword, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND);
			return damage == 6 ? null : "Keen Edge: iron sword damage modifier " + damage + ", expected 6";
		}));

		// Keen Edge and True Temper, held: the sword hits 1 harder, the pickaxe digs stone 15 % faster than plain ones.
		double[] plain = new double[2];
		hold(server, new ItemStack(Items.IRON_SWORD));
		wait(world, 3);
		server.runOnServer(s -> plain[0] = player(s).getAttributeValue(Attributes.ATTACK_DAMAGE));
		hold(server, made[0].copy());
		wait(world, 3);
		check(server.computeOnServer(s -> {
			double damage = player(s).getAttributeValue(Attributes.ATTACK_DAMAGE);
			// The sword's own 6 becomes 7; Strength then scales the whole (so compare the ratio).
			return Math.abs(damage / plain[0] - 7.0 / 6.0) < 1e-4 ? null : "Keen Edge held: attack damage " + damage + ", plain " + plain[0] + ", expected ×7/6";
		}));
		hold(server, new ItemStack(Items.IRON_PICKAXE));
		wait(world, 3);
		server.runOnServer(s -> plain[1] = player(s).getDestroySpeed(Blocks.STONE.defaultBlockState()));
		hold(server, made[1].copy());
		wait(world, 3);
		check(server.computeOnServer(s -> {
			double speed = player(s).getDestroySpeed(Blocks.STONE.defaultBlockState());
			return Math.abs(speed / plain[1] - 1.15) < 1e-3 ? null : "True Temper: stone at " + speed + ", plain " + plain[1] + ", expected ×1.15";
		}));

		// Thrifty III: buckets (3 ingots) give one back about 15 % of the time; iron blocks never do.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			CraftingMenu m = crafting(p);
			for (int i = 0; i < 200; i++) {
				craft(p, m, "# #" + " # " + "   ", i % 2 == 0, '#', Items.IRON_INGOT);
			}
			int back = count(p, Items.IRON_INGOT);
			p.getInventory().clearContent();
			for (int i = 0; i < 60; i++) {
				craft(p, m, "###" + "###" + "###", false, '#', Items.IRON_INGOT);
			}
			int fromBlocks = count(p, Items.IRON_INGOT);
			p.getInventory().clearContent();
			p.closeContainer();
			if (back < 12 || back > 52) {
				return "Thrifty III: " + back + " ingots back from 200 buckets, expected about 30";
			}
			return fromBlocks == 0 ? null : "Thrifty refunded " + fromBlocks + " ingots for iron blocks";
		}));

		// Salvager: an unworn iron chestplate in the bottom slot grinds to 36 nuggets; a half-worn iron pickaxe to 6.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			GrindstoneMenu m = grindstone(p);
			m.getSlot(1).set(new ItemStack(Items.IRON_CHESTPLATE));
			ItemStack nuggets = take(p, m, 2);
			boolean consumed = m.getSlot(1).getItem().isEmpty();
			ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
			pick.setDamageValue(125);
			m.getSlot(1).set(pick);
			ItemStack few = take(p, m, 2);
			ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
			sword.enchant(enchantment(p, Enchantments.SHARPNESS), 1);
			m.getSlot(1).set(sword);
			ItemStack ground = m.getSlot(2).getItem().copy();
			m.getSlot(1).set(ItemStack.EMPTY);
			p.closeContainer();
			if (!nuggets.is(Items.IRON_NUGGET) || nuggets.getCount() != 36 || !consumed) {
				return "Salvager: chestplate gave " + nuggets + ", consumed " + consumed;
			}
			if (!few.is(Items.IRON_NUGGET) || few.getCount() != 6) {
				return "Salvager: half-worn pickaxe gave " + few + ", expected 6 iron nuggets";
			}
			return ground.is(Items.GOLDEN_SWORD) && !EnchantmentHelper.hasAnyEnchantments(ground) ? null
				: "Salvager touched an enchanted piece: " + ground;
		}));

		// Honing: an enchanted sword in the top slot comes out honed, enchantment kept, no experience; the bottom slot still
		// disenchants (with experience).
		BlockPos stone = server.computeOnServer(s -> Bench.GRINDSTONE.pos(player(s)).above(2));
		ItemStack[] honedTools = new ItemStack[2];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Holder<Enchantment> sharpness = enchantment(p, Enchantments.SHARPNESS);
			GrindstoneMenu m = grindstone(p);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.enchant(sharpness, 2);
			p.level().getEntitiesOfClass(ExperienceOrb.class, new AABB(Bench.GRINDSTONE.pos(p)).inflate(4)).forEach(ExperienceOrb::discard);
			m.getSlot(0).set(sword.copy());
			ItemStack honed = take(p, m, 2);
			int honingOrbs = orbs(p);
			m.getSlot(1).set(sword.copy());
			ItemStack ground = take(p, m, 2);
			int grindingOrbs = orbs(p);
			ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
			m.getSlot(0).set(pick);
			ItemStack honedPick = take(p, m, 2);
			p.closeContainer();
			p.level().getEntitiesOfClass(ExperienceOrb.class, new AABB(Bench.GRINDSTONE.pos(p)).inflate(4)).forEach(ExperienceOrb::discard);
			Honed h = honed.get(SmithingComponents.HONED);
			if (h == null || !h.weapon() || h.left() != 60 || EnchantmentHelper.getItemEnchantmentLevel(sharpness, honed) != 2) {
				return "Honing: sword came out " + honed + " " + h;
			}
			if (modifier(honed, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) != 7) {
				return "Honing: honed sword damage modifier " + modifier(honed, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) + ", expected 7";
			}
			if (honingOrbs != 0 || grindingOrbs == 0 || EnchantmentHelper.hasAnyEnchantments(ground)) {
				return "Honing: experience from honing " + honingOrbs + ", from grinding " + grindingOrbs + "; ground " + ground;
			}
			Honed hp = honedPick.get(SmithingComponents.HONED);
			if (hp == null || hp.weapon() || hp.left() != 300 || Math.abs(modifier(honedPick, Attributes.MINING_EFFICIENCY, EquipmentSlotGroup.MAINHAND) - 1.8) > 1e-6) {
				return "Honing: pickaxe came out " + hp + " " + honedPick.get(DataComponents.ATTRIBUTE_MODIFIERS);
			}
			honedTools[0] = honed;
			honedTools[1] = honedPick;
			return null;
		}));

		// Three real hits on a husk, a second apart, wear the sword's edge three; then the rest of the 60 run out.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, honedTools[0]);
			Mob husk = EntityTypes.HUSK.create(p.level(), EntitySpawnReason.COMMAND);
			husk.setPos(p.getX() + 1.5, p.getY(), p.getZ());
			husk.setNoAi(true);
			husk.addTag("smithing_t");
			p.level().addFreshEntity(husk);
		});
		for (int i = 0; i < 3; i++) {
			wait(world, 20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Mob husk = husk(p);
				husk.setHealth(husk.getMaxHealth());
				p.attack(husk);
			});
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob husk = husk(p);
			ItemStack held = p.getMainHandItem();
			int afterHits = held.get(SmithingComponents.HONED) == null ? -1 : held.get(SmithingComponents.HONED).left();
			for (int i = 0; i < 57; i++) {
				held.hurtEnemy(husk, p);
			}
			husk.discard();
			if (afterHits != 57) {
				return "Honing: 3 hits left " + afterHits + ", expected 57";
			}
			if (held.has(SmithingComponents.HONED) || modifier(held, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) != 5) {
				return "Honing didn't wear off after 60 hits: " + held.get(SmithingComponents.HONED);
			}
			ItemStack honedPick = honedTools[1];
			// The pickaxe: two real blocks, then the rest of the 300.
			p.setItemInHand(InteractionHand.MAIN_HAND, honedPick);
			for (int i = 0; i < 2; i++) {
				p.level().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
				p.gameMode.destroyBlock(stone);
			}
			ItemStack tool = p.getMainHandItem();
			int afterBlocks = tool.get(SmithingComponents.HONED) == null ? -1 : tool.get(SmithingComponents.HONED).left();
			for (int i = 0; i < 298; i++) {
				tool.mineBlock(p.level(), Blocks.STONE.defaultBlockState(), stone, p);
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			if (afterBlocks != 298) {
				return "Honing: 2 blocks left " + afterBlocks + ", expected 298";
			}
			return tool.has(SmithingComponents.HONED) || modifier(tool, Attributes.MINING_EFFICIENCY, EquipmentSlotGroup.MAINHAND) != 0
				? "Honing didn't wear off the pickaxe: " + tool.get(DataComponents.ATTRIBUTE_MODIFIERS) : null;
		}));

		// Reforge went with the respec: a feather and a chestplate make nothing now.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			SmithingMenu m = smithing(p);
			m.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(Items.IRON_CHESTPLATE));
			m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.FEATHER));
			boolean offered = !m.getSlot(3).getItem().isEmpty();
			m.getSlot(SmithingMenu.BASE_SLOT).set(ItemStack.EMPTY);
			m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(ItemStack.EMPTY);
			p.closeContainer();
			return offered ? "Reforge offered without the perk" : null;
		}));

		// Forged: the tooltip carries the maker's name.
		server.runOnServer(s -> player(s).getInventory().setItem(0, made[0].copy()));
		wait(world, 5);
		check(context.computeOnClient(mc -> {
			String text = tooltip(mc, mc.player.getInventory().getItem(0));
			return text.contains("Forged by " + mc.player.getName().getString()) && text.contains("Keen Edge") && text.contains("Folded Steel")
				? null : "Toolwright tooltip: " + text;
		}));
		server.runOnServer(s -> player(s).getInventory().clearContent());
	}

	// ---------------------------------------------------------------- Career 3: Masterwork; A Hundred Hammers on the way

	private static void masterwork(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand(LEARN + "forged");
		server.runCommand(LEARN + "riveted");
		server.runCommand(LEARN + "masterwork");
		check(server.computeOnServer(s -> Perks.has(player(s), SmithingSkill.MASTERWORK) ? null
			: "Masterwork not learned: " + CharacterStore.get(player(s)).perks()));

		// 400 iron swords, half clicked and half shift-clicked: about one in ten is a Masterwork, every piece counts once.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			long pieces = FeatCounters.get(p, Forging.PIECES);
			long iron = FeatCounters.get(p, SmithingSkill.IRON_PIECES);
			boolean hadHammers = Perks.has(p, SmithingSkill.HUNDRED_HAMMERS);
			CraftingMenu m = crafting(p);
			int masterworks = 0;
			ItemStack sample = ItemStack.EMPTY;
			for (int i = 0; i < 400; i++) {
				ItemStack sword = craft(p, m, " # " + " # " + " | ", i % 2 == 1, '#', Items.IRON_INGOT, '|', Items.STICK);
				Forged f = sword.get(SmithingComponents.FORGED);
				if (f != null && f.has(Forged.Mark.MASTERWORK)) {
					masterworks++;
					sample = sword;
				}
			}
			p.closeContainer();
			p.getInventory().clearContent();
			if (masterworks < 20 || masterworks > 62) {
				return "Masterwork: " + masterworks + " of 400, expected about 40";
			}
			if (FeatCounters.get(p, Forging.PIECES) - pieces != 400 || FeatCounters.get(p, SmithingSkill.IRON_PIECES) - iron != 400) {
				return "Masterwork: piece count moved " + (FeatCounters.get(p, Forging.PIECES) - pieces) + ", iron " + (FeatCounters.get(p, SmithingSkill.IRON_PIECES) - iron);
			}
			// +1 damage (5 → 6); Forged 25 %, Folded Steel (a Teaching, kept through the respec) and a third more: 250 × 2.5833 = 646;
			// a gold "Masterwork" name.
			Component name = sample.getHoverName();
			if (modifier(sample, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) != 6 || sample.getMaxDamage() != 646
				|| !(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("mettle.smithing.masterwork_name")) || sample.getStyledHoverName().getSiblings().getFirst().getStyle().getColor() == null
				|| !"gold".equals(sample.getStyledHoverName().getSiblings().getFirst().getStyle().getColor().serialize())) {
				return "Masterwork sword: damage " + modifier(sample, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) + " lasts " + sample.getMaxDamage()
					+ " name " + sample.getStyledHoverName();
			}
			// A Hundred Hammers came from the hundredth iron piece.
			return hadHammers || Perks.has(p, SmithingSkill.HUNDRED_HAMMERS) ? null : "A Hundred Hammers not earned after " + FeatCounters.get(p, SmithingSkill.IRON_PIECES) + " iron pieces";
		}));

		// Not smithing: nine ingots knocked out of an iron block teach nothing.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			double xp = CharacterStore.get(p).xp(SmithingSkill.SMITHING);
			CraftingMenu m = crafting(p);
			craft(p, m, "#        ", false, '#', Items.IRON_BLOCK);
			p.closeContainer();
			p.getInventory().clearContent();
			double after = CharacterStore.get(p).xp(SmithingSkill.SMITHING);
			return after == xp ? null : "ingots from a block gave " + (after - xp) + " Smithing XP";
		}));
	}

	// ---------------------------------------------------------------- Earned: A Hundred Hammers at the anvil, Black Iron

	private static void earned(TestSingleplayerContext world, TestServerContext server) {
		// A Hundred Hammers (no Tinker now): the 8-level repair costs 1, and the prior work stays at 7.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (Perks.has(p, SmithingSkill.TINKER) || !Perks.has(p, SmithingSkill.HUNDRED_HAMMERS)) {
				return "Hammers check: Tinker " + Perks.has(p, SmithingSkill.TINKER) + ", Hammers " + Perks.has(p, SmithingSkill.HUNDRED_HAMMERS);
			}
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(100);
			sword.set(DataComponents.REPAIR_COST, 7);
			AnvilMenu m = anvil(p);
			m.getSlot(0).set(sword);
			m.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
			int cost = m.getCost();
			ItemStack repaired = take(p, m, 2);
			p.closeContainer();
			return cost == 1 && repaired.getOrDefault(DataComponents.REPAIR_COST, 0) == 7 ? null
				: "A Hundred Hammers: cost " + cost + ", prior work after " + repaired.get(DataComponents.REPAIR_COST) + ", expected 1 and 7";
		}));

		// Black Iron: nine netherite upgrades earn it; after that about one upgrade in three hands the template back.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			SmithingMenu m = smithing(p);
			// The armourer's upgrade already counted one; upgrades go on until the ninth earns the feat. A template can
			// only come back from the upgrade that earns it on (the feat is granted as the upgrade is taken).
			int early = 0;
			for (int i = 0; i < 9 && !Perks.has(p, SmithingSkill.BLACK_IRON); i++) {
				boolean last = FeatCounters.get(p, SmithingSkill.NETHERITE_UPGRADES) == 8;
				early += upgrade(p, m) && !last ? 1 : 0;
			}
			if (!Perks.has(p, SmithingSkill.BLACK_IRON)) {
				p.closeContainer();
				return "Black Iron not earned after " + FeatCounters.get(p, SmithingSkill.NETHERITE_UPGRADES) + " upgrades";
			}
			if (FeatCounters.get(p, SmithingSkill.NETHERITE_UPGRADES) != 9) {
				p.closeContainer();
				return "Black Iron earned at " + FeatCounters.get(p, SmithingSkill.NETHERITE_UPGRADES) + " upgrades, expected 9";
			}
			int kept = 0;
			for (int i = 0; i < 60; i++) {
				kept += upgrade(p, m) ? 1 : 0;
			}
			m.getSlot(SmithingMenu.TEMPLATE_SLOT).set(ItemStack.EMPTY);
			p.closeContainer();
			p.getInventory().clearContent();
			if (early != 0) {
				return "Black Iron returned " + early + " templates before it was earned";
			}
			return kept >= 8 && kept <= 34 ? null : "Black Iron: " + kept + " of 60 templates back, expected about 20";
		}));
	}

	// ---------------------------------------------------------------- benches

	private enum Bench {
		CRAFTING(Blocks.CRAFTING_TABLE, 0), SMITHING(Blocks.SMITHING_TABLE, 1), GRINDSTONE(Blocks.GRINDSTONE, 2), ANVIL(Blocks.ANVIL, 3);

		final net.minecraft.world.level.block.Block block;
		final int offset;

		Bench(net.minecraft.world.level.block.Block block, int offset) {
			this.block = block;
			this.offset = offset;
		}

		BlockPos pos(ServerPlayer p) {
			return p.blockPosition().above(4).east(offset - 1).north(2);
		}
	}

	private static ContainerLevelAccess access(ServerPlayer p, Bench bench) {
		return ContainerLevelAccess.create(p.level(), bench.pos(p));
	}

	private static CraftingMenu crafting(ServerPlayer p) {
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CraftingMenu(id, inv, access(p, Bench.CRAFTING)), Component.literal("craft")));
		return (CraftingMenu) p.containerMenu;
	}

	private static SmithingMenu smithing(ServerPlayer p) {
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new SmithingMenu(id, inv, access(p, Bench.SMITHING)), Component.literal("smith")));
		return (SmithingMenu) p.containerMenu;
	}

	private static GrindstoneMenu grindstone(ServerPlayer p) {
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new GrindstoneMenu(id, inv, access(p, Bench.GRINDSTONE)), Component.literal("grind")));
		return (GrindstoneMenu) p.containerMenu;
	}

	private static AnvilMenu anvil(ServerPlayer p) {
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AnvilMenu(id, inv, access(p, Bench.ANVIL)), Component.literal("anvil")));
		return (AnvilMenu) p.containerMenu;
	}

	/**
	 * One craft through the table's result slot. {@code pattern} is 9 characters, row by row; {@code key} pairs a
	 * character with an item. A click takes the piece onto the cursor; a shift-click moves it into the inventory, where
	 * it is picked back out.
	 */
	private static ItemStack craft(ServerPlayer p, CraftingMenu m, String pattern, boolean shift, Object... key) {
		Map<Character, Item> items = new HashMap<>();
		for (int i = 0; i < key.length; i += 2) {
			items.put((Character) key[i], (Item) key[i + 1]);
		}
		for (int i = 0; i < 9; i++) {
			Item item = items.get(pattern.charAt(i));
			m.getSlot(1 + i).set(item == null ? ItemStack.EMPTY : new ItemStack(item));
		}
		ItemStack shown = m.getSlot(0).getItem();
		if (shown.isEmpty()) {
			throw new AssertionError("no recipe for " + pattern + " " + items);
		}
		Item made = shown.getItem();
		if (shift) {
			m.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (s.is(made)) {
					p.getInventory().setItem(i, ItemStack.EMPTY);
					return s;
				}
			}
			throw new AssertionError("shift-click crafted no " + made);
		}
		m.clicked(0, 0, ContainerInput.PICKUP, p);
		ItemStack out = m.getCarried();
		m.setCarried(ItemStack.EMPTY);
		return out;
	}

	/** Clicks a result slot and keeps what lands on the cursor. */
	private static ItemStack take(ServerPlayer p, net.minecraft.world.inventory.AbstractContainerMenu m, int slot) {
		m.clicked(slot, 0, ContainerInput.PICKUP, p);
		ItemStack out = m.getCarried();
		m.setCarried(ItemStack.EMPTY);
		return out;
	}

	private static ItemStack reforge(ServerPlayer p, SmithingMenu m, ItemStack piece, ItemStack material) {
		m.getSlot(SmithingMenu.BASE_SLOT).set(piece);
		m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(material);
		ItemStack out = take(p, m, 3);
		if (out.isEmpty()) {
			throw new AssertionError("Reforge offered nothing for " + piece + " and " + material);
		}
		return out;
	}

	/** One netherite sword upgrade with one template in the slot; true when the template came back. */
	private static boolean upgrade(ServerPlayer p, SmithingMenu m) {
		m.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
		m.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
		m.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.NETHERITE_INGOT));
		ItemStack out = take(p, m, 3);
		if (!out.is(Items.NETHERITE_SWORD)) {
			throw new AssertionError("netherite upgrade gave " + out);
		}
		return m.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
	}

	// ---------------------------------------------------------------- reading results

	/** The sum of an item's flat modifiers for an attribute in a slot group. */
	private static double modifier(ItemStack stack, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, EquipmentSlotGroup group) {
		double sum = 0;
		for (ItemAttributeModifiers.Entry e : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (e.attribute().equals(attribute) && e.slot() == group) {
				sum += e.modifier().amount();
			}
		}
		return sum;
	}

	/** What every registered weight modifier makes of a stack, together (the burden module multiplies them). */
	private static float weight(ServerPlayer p, ItemStack stack) {
		float m = 1f;
		for (WeightModifier modifier : MettleServices.weightModifiers()) {
			m *= modifier.multiplier(p, stack);
		}
		return m;
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	private static int orbs(ServerPlayer p) {
		return p.level().getEntitiesOfClass(ExperienceOrb.class, new AABB(Bench.GRINDSTONE.pos(p)).inflate(4)).size();
	}

	private static Mob husk(ServerPlayer p) {
		return p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8), e -> e.entityTags().contains("smithing_t")).getFirst();
	}

	private static Holder<Enchantment> enchantment(ServerPlayer p, ResourceKey<Enchantment> key) {
		return p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static String tooltip(net.minecraft.client.Minecraft mc, ItemStack stack) {
		StringBuilder out = new StringBuilder();
		for (Component line : stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
			out.append(line.getString()).append(" | ");
		}
		return out.toString();
	}

	private static void wear(ServerPlayer p, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		p.setItemSlot(EquipmentSlot.HEAD, head);
		p.setItemSlot(EquipmentSlot.CHEST, chest);
		p.setItemSlot(EquipmentSlot.LEGS, legs);
		p.setItemSlot(EquipmentSlot.FEET, feet);
	}

	private static void hold(TestServerContext server, ItemStack stack) {
		server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, stack));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void wait(TestSingleplayerContext world, int ticks) {
		int target = world.getServer().computeOnServer(s -> s.getTickCount()) + ticks;
		world.getServer().waitFor(s -> s.getTickCount() >= target, ticks + 200);
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
