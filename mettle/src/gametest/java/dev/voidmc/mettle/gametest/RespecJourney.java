package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.PerkRules;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.respec.Draughts;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

/**
 * The player's respec, drunk for real with the use key: a Mining career with a Mastery is forgotten (every rank back as a
 * draft, perks gone, earned perks and XP kept), an empty draught is refused and kept, the Draught of Unbinding returns placed
 * attribute points, and the recipes brew what they say.
 */
final class RespecJourney {
	private static final Identifier MINING = MiningSkill.MINING.identifier();
	/** Ranks held: Quarryman 3, Prospector, Tunneler, Rock Hauler, Strike the Seam, Motherlode; each comes back as a draft. */
	private static final int DRAFTS = 8;
	private static final int STRENGTH = 11;

	private RespecJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("mettle background set mettle:ranger @a");
		ItemStack[] held = new ItemStack[1];
		double[] xp = new double[1];
		int[] level = new int[1];
		int[] queued = new int[1];
		float[] baseSpeed = new float[1];
		List<ResourceKey<Perk>> earned = new ArrayList<>();

		// A Mining career: Mining 18, Strength 14 (a Ranger starts at 3), roots, both paths, the Motherlode mastery.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Perks.respec(p, true);
			held[0] = p.getMainHandItem().copy();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			return null;
		}));
		if (server.computeOnServer(s -> CharacterStore.get(player(s)).level(MiningSkill.MINING)) < 18) {
			server.runCommand("mettle xp mettle:mining 22000 @a");
		}
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (Perks.attributePoints(p) < STRENGTH) {
				return "only " + Perks.attributePoints(p) + " attribute points to place, need " + STRENGTH;
			}
			for (int i = 0; i < STRENGTH; i++) {
				Perks.spendAttribute(p, CharacterAttribute.STRENGTH);
			}
			// Strength speeds mining too (+2 % a point), so the base is taken after placing it.
			baseSpeed[0] = p.getDestroySpeed(Blocks.STONE.defaultBlockState());
			for (String perk : List.of("quarryman", "quarryman", "quarryman", "prospector", "tunneler", "rock_hauler", "strike_the_seam", "motherlode")) {
				Optional<PerkRules.Reason> blocked = Perks.learn(p, Perks.key(Mettle.id("mining/" + perk)));
				if (blocked.isPresent()) {
					return "could not learn " + perk + ": " + blocked.get();
				}
			}
			FeatCounters.add(p, "mine_diamond_ore", 64);
			if (!Perks.has(p, MiningSkill.DIAMOND_HANDS)) {
				return "Diamond Hands not earned";
			}
			CharacterState state = CharacterStore.get(p);
			Map<ResourceKey<Perk>, Perk> all = Perks.registry(p).listElements().collect(Collectors.toMap(r -> r.key(), r -> r.value()));
			state.perks().keySet().stream().filter(k -> all.containsKey(k) && !all.get(k).kind().bought()).forEach(earned::add);
			xp[0] = state.xp(MiningSkill.MINING);
			level[0] = state.level(MiningSkill.MINING);
			queued[0] = state.drafts().queued(MiningSkill.MINING);
			if (p.getDestroySpeed(Blocks.STONE.defaultBlockState()) <= baseSpeed[0]) {
				return "Quarryman III did not speed up mining";
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, Draughts.forgetting(MINING));
			return null;
		}));
		context.waitTicks(3);
		context.takeScreenshot("mettle_respec_1_draught_in_hand");

		// Drink it: hold use for the vanilla 1.6 s.
		drink(context);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			if (!p.getMainHandItem().is(Items.GLASS_BOTTLE)) {
				return "after drinking the hand holds " + p.getMainHandItem() + ", expected the empty bottle";
			}
			List<Identifier> bought = state.perks().keySet().stream()
				.filter(k -> Perks.registry(p).getOptional(k).map(x -> x.kind().bought() && x.skill().equals(MiningSkill.MINING)).orElse(false))
				.map(ResourceKey::identifier).toList();
			if (!bought.isEmpty()) {
				return "bought Mining perks survived the draught: " + bought;
			}
			int drafts = state.drafts().queued(MiningSkill.MINING);
			if (drafts != queued[0] + DRAFTS) {
				return "Mining drafts " + drafts + " after drinking, expected " + queued[0] + " + " + DRAFTS;
			}
			if (state.drafts().hand(MiningSkill.MINING).isEmpty()) {
				return "no Mining hand dealt after forgetting";
			}
			for (ResourceKey<Perk> k : earned) {
				if (state.rank(k) == 0) {
					return "the draught took an earned perk: " + k.identifier();
				}
			}
			if (state.xp(MiningSkill.MINING) != xp[0] || state.level(MiningSkill.MINING) != level[0]) {
				return "the draught touched Mining XP: " + state.xp(MiningSkill.MINING) + ", was " + xp[0];
			}
			if (state.spent().getOrDefault(CharacterAttribute.STRENGTH, 0) != STRENGTH) {
				return "Forgetting touched attributes: " + state.spent();
			}
			// Effects re-applied: Quarryman's speed is gone.
			ItemStack bottle = p.getMainHandItem();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			float speed = p.getDestroySpeed(Blocks.STONE.defaultBlockState());
			p.setItemInHand(InteractionHand.MAIN_HAND, bottle);
			if (Math.abs(speed - baseSpeed[0]) > 0.01f) {
				return "stone mining speed " + speed + " after forgetting, expected " + baseSpeed[0];
			}
			// The drafts are dealt again: keep a card.
			return dev.voidmc.mettle.character.draft.Drafts.pick(p, MiningSkill.MINING, 0).map(r -> "could not keep a card again: " + r).orElse(null);
		}));
		chip(context, "You forget the ways of the Mining tree · " + DRAFTS + " drafts returned");

		// With nothing bought in the tree the draught is refused, and kept.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Perks.forget(p, MiningSkill.MINING);
			p.setItemInHand(InteractionHand.MAIN_HAND, Draughts.forgetting(MINING));
			return null;
		}));
		drink(context);
		check(server.computeOnServer(s -> {
			ItemStack hand = player(s).getMainHandItem();
			return hand.is(Draughts.FORGETTING) && hand.getCount() == 1 && MINING.equals(hand.get(Draughts.FORGETS))
				? null : "an empty draught was not kept: " + hand;
		}));
		chip(context, "You have learned nothing in Mining to forget");

		// The Draught of Unbinding returns the eleven Strength points to be placed again.
		int[] before = new int[1];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			before[0] = Perks.attributePoints(p);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Draughts.UNBINDING, 2));
			return null;
		}));
		drink(context);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			CharacterState state = CharacterStore.get(p);
			if (!state.spent().isEmpty()) {
				return "Unbinding left placed points: " + state.spent();
			}
			if (Perks.attributePoints(p) != before[0] + STRENGTH) {
				return "attribute points " + Perks.attributePoints(p) + " after unbinding, expected " + (before[0] + STRENGTH);
			}
			ItemStack hand = p.getMainHandItem();
			boolean bottle = p.getInventory().contains(st -> st.is(Items.GLASS_BOTTLE));
			return hand.is(Draughts.UNBINDING) && hand.getCount() == 1 && bottle ? null : "Unbinding not used up once: " + hand + ", bottle " + bottle;
		}));
		chip(context, "Your body forgets its training · " + STRENGTH + " attribute points to place again");
		drink(context);
		check(server.computeOnServer(s -> {
			ItemStack hand = player(s).getMainHandItem();
			return hand.is(Draughts.UNBINDING) && hand.getCount() == 1 ? null : "an Unbinding with nothing to unbind was used: " + hand;
		}));
		chip(context, "You have placed no attribute points to unbind");

		// The recipes brew what they say, and the draughts look right on the hotbar.
		ItemStack[] hotbar = new ItemStack[9];
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ItemStack mining = craft(p, Items.GLASS_BOTTLE, Items.AMETHYST_SHARD, Items.POPPY, Items.RAW_GOLD);
			if (!mining.is(Draughts.FORGETTING) || !MINING.equals(mining.get(Draughts.FORGETS))
				|| !mining.getHoverName().getString().equals("Draught of Forgetting (Mining)")) {
				return "the Mining recipe brewed " + mining + " named " + mining.getHoverName().getString();
			}
			ItemStack unbinding = craft(p, Items.GLASS_BOTTLE, Items.AMETHYST_SHARD, Items.FERMENTED_SPIDER_EYE, Items.ENDER_PEARL);
			if (!unbinding.is(Draughts.UNBINDING)) {
				return "the Unbinding recipe brewed " + unbinding;
			}
			for (String skill : Draughts.SKILLS) {
				Identifier id = Mettle.id("draught_of_forgetting_" + skill);
				var recipe = s.getRecipeManager().byKey(ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id));
				if (recipe.isEmpty()) {
					return "no recipe " + id;
				}
			}
			for (int i = 0; i < 9; i++) {
				hotbar[i] = p.getInventory().getItem(i).copy();
				p.getInventory().setItem(i, i < 8 ? Draughts.forgetting(Mettle.id(Draughts.SKILLS.get(i))) : new ItemStack(Draughts.UNBINDING));
			}
			return null;
		}));
		context.waitTicks(5);
		context.takeScreenshot("mettle_respec_2_hotbar");

		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < 9; i++) {
				p.getInventory().setItem(i, hotbar[i]);
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, held[0]);
			p.getInventory().clearOrCountMatchingItems(st -> st.is(Items.GLASS_BOTTLE) || st.is(Draughts.FORGETTING) || st.is(Draughts.UNBINDING), false, -1, p.inventoryMenu.getCraftSlots());
			Perks.respec(p, true);
			return null;
		}));
	}

	/** Holds the use key, looking at the sky, long enough for one 1.6 s drink, then lets go. */
	private static void drink(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.player.setXRot(-90f));
		context.waitTicks(2);
		context.getInput().holdKeyFor(o -> o.keyUse, 40);
		context.waitTicks(4);
	}

	/** The feedback chip reached the client (the overlay state keeps chips for a few seconds). */
	private static void chip(ClientGameTestContext context, String expected) {
		String feedback = context.computeOnClient(mc -> MettleState.build(mc).getAsJsonArray("feedback").toString());
		if (!feedback.contains(expected)) {
			throw new AssertionError("no feedback chip \"" + expected + "\"; recent chips: " + feedback);
		}
	}

	private static ItemStack craft(ServerPlayer p, net.minecraft.world.item.Item... items) {
		List<ItemStack> grid = new ArrayList<>();
		for (net.minecraft.world.item.Item item : items) {
			grid.add(new ItemStack(item));
		}
		CraftingInput input = CraftingInput.of(2, 2, grid);
		return p.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, p.level())
			.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
