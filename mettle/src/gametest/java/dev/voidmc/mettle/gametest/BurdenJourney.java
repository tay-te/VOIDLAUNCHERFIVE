package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.burden.BandEffects;
import dev.voidmc.mettle.burden.LoadState;
import dev.voidmc.mettle.burden.WeightPerks;
import dev.voidmc.mettle.burden.Weights;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.mixin.burden.FoodDataAccessor;
import dev.voidmc.mettle.mixin.burden.PlayerSprintGate;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Burden played for real in the client tour: the weight table reaching the client, {@code /mettle weight}, the tooltip,
 * cobblestone carried across every band with its speed, jump, fall, hunger and sprint effects checked on both sides,
 * nested contents, Rock Hauler and Armour Training lowering the load, and creative being exempt. Ends with the player's
 * inventory, game mode and perks as they were.
 */
final class BurdenJourney {
	private static final float COBBLE = 0.25f;

	private BurdenJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		List<ItemStack> saved = new ArrayList<>();
		float[] capacity = new float[1];
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @p run mettle respec");
		server.runCommand("effect clear @a");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				saved.add(p.getInventory().getItem(i).copy());
			}
			p.getInventory().clearContent();
			p.getFoodData().setFoodLevel(20);
			return null;
		}));
		context.waitTicks(5);

		// The table: derived on the server, explained by /mettle weight, synced to the client, shown in tooltips.
		check(server.computeOnServer(s -> {
			List<String> out = run(s, "mettle weight minecraft:iron_block");
			String want = "iron_block = 9 × iron_ingot (leaf 0.5) = 4.5 kg";
			return out.contains(want) ? null : "/mettle weight said " + out + ", expected " + want;
		}));
		check(context.computeOnClient(mc -> {
			float iron = Weights.client().get(Items.IRON_BLOCK);
			if (Math.abs(iron - 4.5f) > 1e-4f) {
				return "client table has iron_block at " + iron + " kg (not synced?)";
			}
			List<Component> lines = new ItemStack(Items.COBBLESTONE, 64).getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
			String line = lines.size() > 1 ? lines.get(1).getString() : "";
			return line.equals("0.25 kg · stack 16.0 kg") ? null : "tooltip weight line: " + lines.stream().map(Component::getString).toList();
		}));

		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			capacity[0] = MettleServices.burden().capacity(p);
			if (capacity[0] <= 0f) {
				return "capacity " + capacity[0];
			}
			LoadBand band = MettleServices.burden().band(p);
			return band == LoadBand.LIGHT && modifier(p, BandEffects.SPEED) == null ? null : "empty-handed: " + band;
		}));

		// Burdened (85 %): slower, hungrier, harder falls; jumping and sprinting as usual.
		carryShare(context, server, capacity[0], 0.85f);
		expectBand(server, LoadBand.BURDENED, -0.10, 0.0);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			AttributeModifier fall = p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).getModifier(BandEffects.FALL);
			if (fall == null || Math.abs(fall.amount() - 0.25) > 1e-6) {
				return "Burdened fall damage modifier " + fall;
			}
			FoodDataAccessor food = (FoodDataAccessor) p.getFoodData();
			food.mettle$setExhaustion(0f);
			p.causeFoodExhaustion(1f);
			float drained = food.mettle$exhaustion();
			food.mettle$setExhaustion(0f);
			return Math.abs(drained - 1.5f) < 1e-4f ? null : "Burdened hunger: 1.0 exhaustion became " + drained + ", expected 1.5";
		}));
		check(context.computeOnClient(mc -> ((PlayerSprintGate) mc.player).mettle$mayExert() ? null : "Burdened should still sprint"));

		// Overburdened (110 %): no sprinting on either side, short jumps.
		carryShare(context, server, capacity[0], 1.10f);
		expectBand(server, LoadBand.OVERBURDENED, -0.30, -0.40);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setSprinting(true);
			return p.isSprinting() ? "Overburdened player could sprint on the server" : null;
		}));
		check(context.computeOnClient(mc -> {
			LoadState synced = LoadState.of(mc.player);
			if (synced == null || synced.band() != LoadBand.OVERBURDENED) {
				return "client sees band " + (synced == null ? "nothing" : synced.band());
			}
			return ((PlayerSprintGate) mc.player).mettle$mayExert() ? "client still allows sprinting when Overburdened" : null;
		}));
		context.waitTicks(10);
		context.takeScreenshot("mettle_burden_overburdened");

		// Pinned (140 %): barely moving, no jumping.
		carryShare(context, server, capacity[0], 1.40f);
		expectBand(server, LoadBand.PINNED, -0.70, -1.0);
		check(server.computeOnServer(s -> {
			LoadState state = state(s);
			return state.heaviest().equals(Identifier.withDefaultNamespace("cobblestone")) && state.heaviestStacks() >= 2 ? null
				: "heaviest should be several stacks of cobblestone: " + state;
		}));

		// Rock Hauler: the same cobblestone weighs 40 % less.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float before = MettleServices.burden().load(p);
			float stone = count(p, Items.COBBLESTONE) * COBBLE;
			return Math.abs(before - stone) < 0.01f ? null : "load " + before + " kg, expected only the cobblestone's " + stone;
		}));
		server.runCommand("mettle perk grant mettle:mining/rock_hauler @a");
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float load = MettleServices.burden().load(p);
			float want = count(p, Items.COBBLESTONE) * COBBLE * WeightPerks.HAULER;
			return Math.abs(load - want) < 0.01f ? null : "Rock Hauler: load " + load + " kg, expected " + want;
		}));
		server.runCommand("execute as @p run mettle respec");

		// A shulker box full of cobblestone weighs the box plus all of it.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			List<ItemStack> stone = new ArrayList<>();
			for (int i = 0; i < 27; i++) {
				stone.add(new ItemStack(Items.COBBLESTONE, 64));
			}
			ItemStack box = new ItemStack(Items.SHULKER_BOX);
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stone));
			p.getInventory().add(box);
			return null;
		}));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float load = MettleServices.burden().load(p);
			float want = Weights.server().get(Items.SHULKER_BOX) + 27 * 64 * COBBLE;
			return Math.abs(load - want) < 0.05f && MettleServices.burden().band(p) == LoadBand.PINNED ? null
				: "shulker of cobblestone: load " + load + " kg, expected " + want;
		}));

		// Creative is exempt: no band, no penalties, whatever is carried.
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			LoadState state = p.getAttached(LoadState.ATTACHMENT);
			if (state == null || !state.exempt() || state.band() != LoadBand.LIGHT) {
				return "creative not exempt: " + state;
			}
			return modifier(p, BandEffects.SPEED) == null && modifier(p, BandEffects.JUMP) == null ? null : "creative player still slowed";
		}));
		server.runCommand("gamemode survival @a");

		// Armour Training: worn armour weighs 10 % less at rank 1 (and half again with Marching Order, if earned).
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			return null;
		}));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float share = Perks.has(p, WeightPerks.MARCHING_ORDER) ? WeightPerks.MARCHING_ORDER_SHARE : 1f;
			float load = MettleServices.burden().load(p);
			return Math.abs(load - 12f * share) < 0.01f ? null : "iron armour worn: load " + load + " kg, expected " + 12f * share;
		}));
		server.runCommand("mettle perk grant mettle:defense/armour_training @a");
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			float share = Perks.has(p, WeightPerks.MARCHING_ORDER) ? WeightPerks.MARCHING_ORDER_SHARE : 1f;
			float load = MettleServices.burden().load(p);
			float want = 12f * (1f - WeightPerks.ARMOUR_TRAINING_PER_RANK) * share;
			if (Math.abs(load - want) > 0.01f) {
				return "Armour Training I: load " + load + " kg, expected " + want;
			}
			// Carried, not worn, the same armour weighs full.
			ItemStack helmet = p.getItemBySlot(EquipmentSlot.HEAD).copy();
			float carried = MettleServices.burden().weight(p, helmet);
			return Math.abs(carried - 2.4f) < 1e-4f ? null : "a carried iron helmet weighs " + carried + " kg for the player, expected 2.4";
		}));

		// Clean up: perks, armour, inventory and food as they were; nothing slowed.
		server.runCommand("execute as @p run mettle respec");
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				p.setItemSlot(slot, ItemStack.EMPTY);
			}
			p.getInventory().clearContent();
			for (int i = 0; i < saved.size(); i++) {
				p.getInventory().setItem(i, saved.get(i));
			}
			p.getFoodData().setFoodLevel(20);
			return null;
		}));
		context.waitTicks(5);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			LoadBand band = MettleServices.burden().band(p);
			return band == LoadBand.LIGHT && modifier(p, BandEffects.SPEED) == null ? null : "after clean-up still " + band;
		}));
	}

	/** Fills the inventory with cobblestone to this share of capacity (and nothing else). */
	private static void carryShare(ClientGameTestContext context, TestServerContext server, float capacity, float share) {
		int count = (int) Math.ceil(capacity * share / COBBLE);
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			int left = count;
			while (left > 0) {
				int n = Math.min(64, left);
				p.getInventory().add(new ItemStack(Items.COBBLESTONE, n));
				left -= n;
			}
			return count(p, Items.COBBLESTONE) == count ? null : "could only carry " + count(p, Items.COBBLESTONE) + " of " + count + " cobblestone";
		}));
		// The inventory trigger fires from the menu's per-tick diff; give it a few ticks and the sync a moment.
		context.waitTicks(5);
	}

	private static void expectBand(TestServerContext server, LoadBand band, double speed, double jump) {
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			LoadBand now = MettleServices.burden().band(p);
			if (now != band) {
				return "expected " + band + " at " + MettleServices.burden().load(p) + " / " + MettleServices.burden().capacity(p) + " kg, got " + now;
			}
			LoadState synced = p.getAttached(LoadState.ATTACHMENT);
			if (synced == null || synced.band() != band) {
				return "synced state " + synced + " for " + band;
			}
			String bad = amount(p, BandEffects.SPEED, speed, "speed");
			return bad != null ? bad : amount(p, BandEffects.JUMP, jump, "jump");
		}));
	}

	private static String amount(ServerPlayer p, Identifier id, double want, String what) {
		AttributeModifier m = modifier(p, id);
		double have = m == null ? 0.0 : m.amount();
		if (m != null && m.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
			return what + " modifier uses " + m.operation();
		}
		return Math.abs(have - want) < 1e-6 ? null : what + " modifier " + have + ", expected " + want;
	}

	private static AttributeModifier modifier(Player p, Identifier id) {
		AttributeModifier speed = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(id);
		return speed != null ? speed : p.getAttribute(Attributes.JUMP_STRENGTH).getModifier(id);
	}

	private static LoadState state(MinecraftServer s) {
		return dev.voidmc.mettle.burden.LoadTracker.state(player(s));
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Runs a command as the console and returns what it said. */
	private static List<String> run(MinecraftServer s, String command) {
		List<String> out = new ArrayList<>();
		CommandSource capture = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				out.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSource(capture), command);
		return out;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError("Burden: " + problem);
		}
	}
}
