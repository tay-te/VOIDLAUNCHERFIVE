package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.burden.LoadState;
import dev.voidmc.mettle.burden.Loads;
import dev.voidmc.mettle.burden.Weights;
import dev.voidmc.mettle.client.character.BackgroundPickerScreen;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.gear.GearComponents;
import dev.voidmc.mettle.gear.GearInventory;
import dev.voidmc.mettle.gear.GearItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The weight half of the balance report, measured in a real game (BALANCE.md §5): four realistic inventories for a
 * Ranger at Strength 3, 6 and 10, with no pack, a Satchel or a Leather Pack worn, read through the real load code
 * ({@link Loads#compute} and the live tracker) and the real capacity attribute. Report-only: it runs only with
 * {@code -Dmettle.balance=true} (and then the tour itself is skipped), and writes {@code mettle-balance.md} in the run
 * directory. JUnit's WeightReportTest computes the same tables without a game.
 */
public class BalanceReport implements FabricClientGameTest {
	public static boolean enabled() {
		return Boolean.getBoolean("mettle.balance");
	}

	record Scenario(String name, List<ItemStack> worn, List<ItemStack> carried, ItemStack offhand, int quiverArrows) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("8675309");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
			})
			.create()) {
			TestServerContext server = world.getServer();
			context.waitForScreen(BackgroundPickerScreen.class);
			server.runCommand("mettle background set mettle:ranger @a");
			context.setScreen(() -> null);
			context.waitTicks(5);

			StringBuilder out = new StringBuilder("# VOID Mettle balance report (in game)\n");
			String itemTable = server.computeOnServer(s -> items());
			out.append(itemTable);
			out.append("\n## Loads and bands (Ranger; live tracker after 5 ticks, checked against Loads.compute)\n\n");
			out.append("| Inventory | Pack | Strength | Load kg | Capacity kg | Share | Band |\n|---|---|---|---|---|---|---|\n");
			List<String> problems = new ArrayList<>();
			for (Scenario scenario : scenarios()) {
				for (ItemStack pack : new ItemStack[] {ItemStack.EMPTY, new ItemStack(GearItems.SATCHEL), new ItemStack(GearItems.LEATHER_PACK)}) {
					for (int strength : new int[] {3, 6, 10}) {
						server.runOnServer(s -> dress(player(s), scenario, pack.copy(), strength));
						context.waitTicks(5);
						String row = server.computeOnServer(s -> {
							ServerPlayer p = player(s);
							LoadState direct = Loads.compute(p);
							float load = MettleServices.burden().load(p);
							float capacity = MettleServices.burden().capacity(p);
							LoadBand band = MettleServices.burden().band(p);
							if (Math.abs(load - direct.load()) > 0.05f || band != direct.band()) {
								problems.add(scenario.name() + ": tracker " + load + " " + band + ", compute " + direct);
							}
							return String.format(Locale.ROOT, "| %s | %s | %d | %.1f | %.0f | %.0f%% | %s |%n", scenario.name(),
								pack.isEmpty() ? "none" : Weights.name(pack.getItem()), strength, load, capacity, 100 * load / capacity, band);
						});
						out.append(row);
					}
				}
			}
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().clearContent();
				GearInventory.of(p).takeAll();
				CharacterStore.set(p, CharacterStore.get(p).withSpent(Map.of()));
				EffectsApplier.apply(p);
			});
			String text = out.toString();
			System.out.println(text);
			try {
				Files.writeString(Path.of("mettle-balance.md"), text);
			} catch (IOException e) {
				throw new AssertionError(e);
			}
			if (!problems.isEmpty()) {
				throw new AssertionError("live load disagrees with Loads.compute: " + problems);
			}
		}
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void dress(ServerPlayer p, Scenario scenario, ItemStack pack, int strength) {
		p.getInventory().clearContent();
		GearInventory gear = GearInventory.of(p);
		gear.takeAll();
		CharacterState state = CharacterStore.get(p);
		CharacterStore.set(p, state.withSpent(Map.of(CharacterAttribute.STRENGTH, strength - 3)));
		EffectsApplier.apply(p);
		EquipmentSlot[] armour = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (ItemStack worn : scenario.worn()) {
			for (EquipmentSlot slot : armour) {
				if (worn.get(net.minecraft.core.component.DataComponents.EQUIPPABLE) instanceof net.minecraft.world.item.equipment.Equippable e && e.slot() == slot) {
					p.setItemSlot(slot, worn.copy());
				}
			}
		}
		p.setItemSlot(EquipmentSlot.OFFHAND, scenario.offhand().copy());
		for (ItemStack stack : scenario.carried()) {
			p.getInventory().add(stack.copy());
		}
		if (scenario.quiverArrows() > 0) {
			ItemStack quiver = new ItemStack(GearItems.QUIVER);
			quiver.set(GearComponents.POCKETS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.ARROW, scenario.quiverArrows()))));
			gear.setWorn(GearSlot.QUIVER, quiver);
		}
		if (!pack.isEmpty()) {
			gear.setWorn(GearSlot.BACK, pack);
		}
	}

	private static List<ItemStack> stacks(Item item, int count) {
		List<ItemStack> out = new ArrayList<>();
		while (count > 0) {
			int n = Math.min(count, item.getDefaultMaxStackSize());
			out.add(new ItemStack(item, n));
			count -= n;
		}
		return out;
	}

	private static List<ItemStack> armour(String metal) {
		List<ItemStack> out = new ArrayList<>();
		for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
			out.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(metal + "_" + piece))));
		}
		return out;
	}

	/** The same four inventories as WeightReportTest. */
	static List<Scenario> scenarios() {
		List<Scenario> out = new ArrayList<>();
		List<ItemStack> kit = new ArrayList<>();
		kit.add(new ItemStack(Items.BOW));
		kit.addAll(stacks(Items.COOKED_MUTTON, 4));
		out.add(new Scenario("(a) fresh Ranger kit", List.of(new ItemStack(Items.LEATHER_HELMET)), kit, ItemStack.EMPTY, 32));

		List<ItemStack> mine = new ArrayList<>();
		mine.add(new ItemStack(Items.IRON_PICKAXE));
		mine.addAll(stacks(Items.TORCH, 32));
		mine.addAll(stacks(Items.COOKED_BEEF, 8));
		mine.addAll(stacks(Items.COBBLESTONE, 192));
		mine.addAll(stacks(Items.COBBLED_DEEPSLATE, 192));
		mine.addAll(stacks(Items.DIRT, 64));
		mine.addAll(stacks(Items.GRAVEL, 64));
		mine.addAll(stacks(Items.RAW_IRON, 40));
		mine.addAll(stacks(Items.COAL, 25));
		out.add(new Scenario("(b) 20-minute strip-mine", armour("iron"), mine, ItemStack.EMPTY, 0));

		List<ItemStack> build = new ArrayList<>();
		build.addAll(stacks(Items.OAK_PLANKS, 192));
		build.addAll(stacks(Items.OAK_LOG, 192));
		build.addAll(stacks(Items.STONE_BRICKS, 128));
		build.addAll(stacks(Items.BREAD, 8));
		build.add(new ItemStack(Items.IRON_AXE));
		build.add(new ItemStack(Items.IRON_PICKAXE));
		out.add(new Scenario("(c) builder, 8 stacks", List.of(), build, ItemStack.EMPTY, 0));

		List<ItemStack> explore = new ArrayList<>();
		explore.add(new ItemStack(Items.IRON_SWORD));
		explore.add(new ItemStack(Items.IRON_PICKAXE));
		explore.add(new ItemStack(Items.IRON_AXE));
		explore.add(new ItemStack(Items.BOW));
		explore.addAll(stacks(Items.ARROW, 128));
		explore.addAll(stacks(Items.COOKED_BEEF, 16));
		explore.addAll(stacks(Items.BREAD, 16));
		explore.addAll(stacks(Items.COBBLESTONE, 64));
		explore.addAll(stacks(Items.DIRT, 64));
		explore.addAll(stacks(Items.OAK_PLANKS, 64));
		explore.addAll(stacks(Items.OAK_LOG, 64));
		explore.addAll(stacks(Items.TORCH, 32));
		out.add(new Scenario("(d) mid-game explorer", armour("iron"), explore, new ItemStack(Items.SHIELD), 0));
		return out;
	}

	/** The server's derived weight of about forty common items. */
	private static String items() {
		Item[] common = {Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL, Items.SAND, Items.SANDSTONE, Items.GLASS,
			Items.STONE_BRICKS, Items.BRICKS, Items.OAK_LOG, Items.OAK_PLANKS, Items.STICK, Items.TORCH, Items.CHEST, Items.CRAFTING_TABLE, Items.FURNACE,
			Items.COAL, Items.RAW_IRON, Items.IRON_INGOT, Items.IRON_BLOCK, Items.RAW_GOLD, Items.GOLD_INGOT, Items.RAW_COPPER, Items.COPPER_INGOT,
			Items.DIAMOND, Items.REDSTONE, Items.IRON_ORE, Items.OBSIDIAN, Items.ARROW, Items.BOW, Items.SHIELD, Items.IRON_SWORD, Items.IRON_PICKAXE,
			Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.LEATHER_CHESTPLATE, Items.BREAD, Items.COOKED_BEEF, Items.APPLE, Items.WATER_BUCKET,
			Items.ANVIL, Items.RAIL, Items.LEATHER, Items.STRING, GearItems.SATCHEL, GearItems.LEATHER_PACK, GearItems.QUIVER};
		var table = Weights.server();
		StringBuilder out = new StringBuilder("\n## Derived weights (server table)\n\n| Item | kg | Stack | How |\n|---|---|---|---|\n");
		for (Item item : common) {
			float kg = table.get(item);
			int max = item.getDefaultMaxStackSize();
			out.append(String.format(Locale.ROOT, "| %s | %s | %s | %s |%n", Weights.name(item), Weights.num(kg),
				max > 1 ? Weights.total(kg * max) : "—", Weights.explain(table, item).replace("|", "/")));
		}
		return out.toString();
	}
}
