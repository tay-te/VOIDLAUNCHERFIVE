package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.client.gear.GearClient;
import dev.voidmc.mettle.core.MettleAttributes;
import dev.voidmc.mettle.gear.GearComponents;
import dev.voidmc.mettle.gear.GearEffects;
import dev.voidmc.mettle.gear.GearInventory;
import dev.voidmc.mettle.gear.GearItems;
import dev.voidmc.mettle.gear.GearModule;
import dev.voidmc.mettle.gear.WornGear;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

/**
 * Gear played for real in the client tour: crafting (and an upgrade keeping its pockets and dye), wearing by slot click,
 * right-click and shift-click, carry capacity, pockets and shift-click routing, a reload of the stored attachment,
 * pickups overflowing into the pack and arrows into the quiver, a bow drawing from the quiver, a belt quick use that
 * eats and gives the sword back, creative's slot check, and death with keepInventory off (drops, Curse of Vanishing) and
 * on. Screenshots of the inventory and of the gear on the character. Leaves the player as it found them.
 */
final class GearJourney {
	private static final int BACK = 46;
	private static final int BELT = 47;
	private static final int QUIVER = 48;
	private static final int BACK_POCKET = 49;
	private static final int BELT_POCKET = 76;
	private static final int QUIVER_POCKET = 82;

	private GearJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		context.setScreen(() -> null);
		server.runCommand("gamemode survival @a");
		server.runCommand("gamerule keep_inventory false");
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=item]");
		on(server, p -> {
			GearInventory.of(p).takeAll();
			return null;
		});

		recipes(server);
		float base = on(server, p -> (float) p.getAttributeValue(MettleAttributes.CARRY_CAPACITY));
		wearing(server, base);
		pockets(server);
		reload(server);
		pickups(context, server);
		quiver(server);
		belt(context, server);
		creativeCheck(context, server);
		screenshots(context, server);
		containers(context, server);
		death(context, server, base);
		keepInventory(context, server, base);

		// Leave the world as we found it.
		on(server, p -> {
			GearInventory.of(p).takeAll();
			p.getFoodData().setFoodLevel(20);
			p.setHealth(p.getMaxHealth());
			return null;
		});
		server.runCommand("gamerule keep_inventory false");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=item]");
		server.runCommand("kill @e[type=arrow]");
		server.runCommand("mettle background set mettle:ranger @a");
		context.setScreen(() -> null);
	}

	// ---------------------------------------------------------------- crafting

	private static void recipes(TestServerContext server) {
		ItemStack l = new ItemStack(Items.LEATHER);
		ItemStack s = new ItemStack(Items.STRING);
		ItemStack n = new ItemStack(Items.IRON_NUGGET);
		ItemStack e = ItemStack.EMPTY;
		check(server, craft(List.of(s, e, s, l, e, l, l, l, l), 3), GearItems.SATCHEL, "satchel");
		check(server, craft(List.of(l, l, l, e, n, e), 2), GearItems.BELT, "belt");
		check(server, craft(List.of(l, s, l, l, e, l), 2), GearItems.QUIVER, "quiver");

		// An upgrade keeps the pockets and the dye.
		on(server, p -> {
			MinecraftServer mc = p.level().getServer();
			ItemStack satchel = new ItemStack(GearItems.SATCHEL);
			satchel.set(GearComponents.POCKETS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.BREAD, 5))));
			satchel.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3355AA));
			CraftingInput input = CraftingInput.of(3, 3, List.of(n, s, n, l, satchel, l, l, s, l));
			ItemStack pack = mc.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, p.level())
				.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
			if (!pack.is(GearItems.LEATHER_PACK)) {
				return "satchel upgrade made " + pack;
			}
			ItemContainerContents kept = pack.get(GearComponents.POCKETS);
			if (kept == null || kept.nonEmptyItemCopyStream().noneMatch(i -> i.is(Items.BREAD) && i.getCount() == 5)) {
				return "the leather pack lost the satchel's pockets: " + kept;
			}
			DyedItemColor dye = pack.get(DataComponents.DYED_COLOR);
			return dye != null && dye.rgb() == 0x3355AA ? null : "the leather pack lost the dye: " + dye;
		});
		// Dyeing works like leather armour.
		on(server, p -> {
			CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(GearItems.BELT), new ItemStack(Items.DYE.red())));
			ItemStack dyed = p.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, p.level())
				.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
			return dyed.is(GearItems.BELT) && dyed.has(DataComponents.DYED_COLOR) ? null : "dyeing a belt made " + dyed;
		});
	}

	private static Function<ServerPlayer, ItemStack> craft(List<ItemStack> grid, int width) {
		return p -> {
			CraftingInput input = CraftingInput.of(width, grid.size() / width, grid);
			return p.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, p.level())
				.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
		};
	}

	private static void check(TestServerContext server, Function<ServerPlayer, ItemStack> make, net.minecraft.world.item.Item expected, String what) {
		on(server, p -> {
			ItemStack made = make.apply(p);
			return made.is(expected) ? null : "the " + what + " recipe made " + made;
		});
	}

	// ---------------------------------------------------------------- wearing and capacity

	private static void wearing(TestServerContext server, float base) {
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			// By slot clicks, the way a player drags it on.
			p.getInventory().setItem(9, new ItemStack(GearItems.LEATHER_PACK));
			p.inventoryMenu.clicked(9, 0, ContainerInput.PICKUP, p);
			p.inventoryMenu.clicked(BACK, 0, ContainerInput.PICKUP, p);
			if (!gear.worn(GearSlot.BACK).is(GearItems.LEATHER_PACK) || !p.inventoryMenu.getCarried().isEmpty()) {
				return "clicking the pack onto the back slot left " + gear.worn(GearSlot.BACK) + ", carrying " + p.inventoryMenu.getCarried();
			}
			// A belt does not go on the back.
			p.getInventory().setItem(10, new ItemStack(GearItems.BELT));
			if (p.inventoryMenu.getSlot(BACK).mayPlace(p.getInventory().getItem(10)) || p.inventoryMenu.getSlot(QUIVER).mayPlace(new ItemStack(Items.STONE))) {
				return "a worn slot took the wrong thing";
			}
			p.getInventory().setItem(10, ItemStack.EMPTY);

			// By right-click, like armour.
			ItemStack belt = new ItemStack(GearItems.BELT);
			belt.set(GearComponents.POCKETS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.BREAD, 4))));
			p.setItemInHand(InteractionHand.MAIN_HAND, belt);
			p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			if (!gear.worn(GearSlot.BELT).is(GearItems.BELT) || !p.getMainHandItem().isEmpty()) {
				return "right-clicking a belt did not put it on: worn " + gear.worn(GearSlot.BELT) + ", hand " + p.getMainHandItem();
			}
			if (!gear.pocket(GearSlot.BELT, 0).is(Items.BREAD)) {
				return "the belt's bread did not come with it: " + gear.pockets(GearSlot.BELT);
			}

			// By shift-click: gear goes to its slot.
			p.getInventory().setItem(10, new ItemStack(GearItems.QUIVER));
			p.inventoryMenu.clicked(10, 0, ContainerInput.QUICK_MOVE, p);
			if (!gear.worn(GearSlot.QUIVER).is(GearItems.QUIVER) || !p.getInventory().getItem(10).isEmpty()) {
				return "shift-clicking the quiver did not wear it";
			}
			float now = (float) p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			if (Math.abs(now - (base + 18f)) > 0.01f) {
				return "capacity " + now + " with a leather pack and belt, expected " + (base + 18f);
			}
			AttributeInstance capacity = p.getAttribute(MettleAttributes.CARRY_CAPACITY);
			if (capacity.getModifier(GearEffects.modifierId(GearSlot.BACK)) == null || capacity.getModifier(Identifier.fromNamespaceAndPath("mettle", "gear/belt")) == null) {
				return "gear modifiers missing: " + capacity.getModifiers();
			}
			if (!MettleServices.gear().worn(p, GearSlot.BACK).is(GearItems.LEATHER_PACK)) {
				return "GearService does not see the pack";
			}
			return null;
		});
	}

	// ---------------------------------------------------------------- pockets and routing

	private static void pockets(TestServerContext server) {
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			if (!p.inventoryMenu.getSlot(BACK_POCKET + 17).isActive() || p.inventoryMenu.getSlot(BACK_POCKET + 18).isActive()) {
				return "a leather pack should open exactly 18 pocket slots";
			}
			if (!p.inventoryMenu.getSlot(QUIVER_POCKET + 1).isActive() || p.inventoryMenu.getSlot(QUIVER_POCKET + 2).isActive()) {
				return "a quiver should open exactly 2 pocket slots";
			}
			// Shift-click with a pack worn: into the pack first.
			p.getInventory().setItem(11, new ItemStack(Items.COBBLESTONE, 64));
			p.inventoryMenu.clicked(11, 0, ContainerInput.QUICK_MOVE, p);
			if (!gear.pocket(GearSlot.BACK, 0).is(Items.COBBLESTONE) || gear.pocket(GearSlot.BACK, 0).getCount() != 64 || !p.getInventory().getItem(11).isEmpty()) {
				return "shift-click did not send cobblestone into the pack: " + gear.pockets(GearSlot.BACK);
			}
			// Arrows go to the quiver.
			p.getInventory().setItem(12, new ItemStack(Items.ARROW, 32));
			p.inventoryMenu.clicked(12, 0, ContainerInput.QUICK_MOVE, p);
			if (!gear.pocket(GearSlot.QUIVER, 0).is(Items.ARROW) || gear.pocket(GearSlot.QUIVER, 0).getCount() != 32) {
				return "shift-click did not send arrows into the quiver: " + gear.pockets(GearSlot.QUIVER);
			}
			// A pack takes no gear; a quiver takes no stone.
			if (p.inventoryMenu.getSlot(BACK_POCKET + 1).mayPlace(new ItemStack(GearItems.SATCHEL))
				|| p.inventoryMenu.getSlot(QUIVER_POCKET + 1).mayPlace(new ItemStack(Items.STONE))) {
				return "a pocket took what it should refuse";
			}
			// Belt pockets fill by hand (click), never by shift-click.
			p.inventoryMenu.setCarried(new ItemStack(Items.TORCH, 8));
			p.inventoryMenu.clicked(BELT_POCKET + 1, 0, ContainerInput.PICKUP, p);
			if (!gear.pocket(GearSlot.BELT, 1).is(Items.TORCH) || !p.inventoryMenu.getCarried().isEmpty()) {
				return "clicking torches into a belt pocket failed";
			}
			float pickKg = MettleServices.burden().weight(new ItemStack(Items.IRON_PICKAXE));
			if (pickKg > 1f && p.inventoryMenu.getSlot(BELT_POCKET + 2).mayPlace(new ItemStack(Items.IRON_PICKAXE))) {
				return "a " + pickKg + " kg pickaxe fit a belt pocket";
			}
			// Out of a pocket by shift-click: back to the inventory.
			p.inventoryMenu.clicked(BELT_POCKET + 1, 0, ContainerInput.QUICK_MOVE, p);
			if (!gear.pocket(GearSlot.BELT, 1).isEmpty() || p.getInventory().countItem(Items.TORCH) != 8) {
				return "shift-clicking torches out of the belt failed";
			}
			for (int i = 0; i < 36; i++) {
				if (p.getInventory().getItem(i).is(Items.TORCH)) {
					p.getInventory().setItem(i, ItemStack.EMPTY);
				}
			}

			// Taking the pack off takes its contents along; putting it back on brings them back.
			p.inventoryMenu.clicked(BACK, 0, ContainerInput.QUICK_MOVE, p);
			if (!gear.worn(GearSlot.BACK).isEmpty() || p.inventoryMenu.getSlot(BACK_POCKET).isActive()) {
				return "shift-clicking the worn pack did not take it off";
			}
			int at = -1;
			for (int i = 0; i < 36; i++) {
				if (p.getInventory().getItem(i).is(GearItems.LEATHER_PACK)) {
					at = i;
				}
			}
			if (at < 0) {
				return "the pack did not land in the inventory";
			}
			ItemContainerContents inside = p.getInventory().getItem(at).get(GearComponents.POCKETS);
			if (inside == null || inside.nonEmptyItemCopyStream().noneMatch(i -> i.is(Items.COBBLESTONE))) {
				return "the pack taken off lost its contents: " + inside;
			}
			float off = (float) p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			int menuIndex = at < 9 ? 36 + at : at;
			p.inventoryMenu.clicked(menuIndex, 0, ContainerInput.QUICK_MOVE, p);
			if (!gear.worn(GearSlot.BACK).is(GearItems.LEATHER_PACK) || gear.pocket(GearSlot.BACK, 0).getCount() != 64) {
				return "wearing the pack again did not bring its contents: " + gear.pockets(GearSlot.BACK);
			}
			float on = (float) p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			return Math.abs(on - off - 15f) < 0.01f ? null : "the pack's +15 kg did not come and go: " + off + " → " + on;
		});
	}

	/** The stored attachment survives a save and load (what a relog does). */
	private static void reload(TestServerContext server) {
		on(server, p -> {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, p.level().registryAccess());
			WornGear stored = p.getAttached(WornGear.TYPE);
			Tag saved = WornGear.CODEC.encodeStart(ops, stored).getOrThrow();
			WornGear loaded = WornGear.CODEC.parse(ops, saved).getOrThrow();
			p.setAttached(WornGear.TYPE, loaded);
			GearInventory gear = GearInventory.of(p);
			if (!gear.worn(GearSlot.BACK).is(GearItems.LEATHER_PACK) || gear.pocket(GearSlot.BACK, 0).getCount() != 64
				|| gear.pocket(GearSlot.QUIVER, 0).getCount() != 32 || !gear.pocket(GearSlot.BELT, 0).is(Items.BREAD)) {
				return "gear changed through a save and load: " + saved;
			}
			return null;
		});
	}

	// ---------------------------------------------------------------- pickups

	private static void pickups(ClientGameTestContext context, TestServerContext server) {
		on(server, p -> {
			for (int i = 0; i < 36; i++) {
				p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
			}
			return null;
		});
		server.runCommand("execute at @p run summon item ~ ~ ~ {Item:{id:\"minecraft:cobblestone\",count:16},PickupDelay:0}");
		server.runCommand("execute at @p run summon item ~ ~ ~ {Item:{id:\"minecraft:arrow\",count:8},PickupDelay:0}");
		context.waitTicks(10);
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			int cobble = gear.pockets(GearSlot.BACK).stream().filter(s -> s.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum();
			if (cobble != 80) {
				return "a full inventory's pickup did not overflow into the pack: " + cobble + " cobblestone in it, expected 80";
			}
			int arrows = gear.pocket(GearSlot.QUIVER, 0).getCount();
			if (arrows != 40) {
				return "picked-up arrows did not go into the quiver: " + arrows + ", expected 40";
			}
			for (int i = 0; i < 36; i++) {
				p.getInventory().setItem(i, ItemStack.EMPTY);
			}
			return null;
		});
	}

	// ---------------------------------------------------------------- quiver and belt

	private static void quiver(TestServerContext server) {
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			ItemStack bow = new ItemStack(Items.BOW);
			p.setItemInHand(InteractionHand.MAIN_HAND, bow);
			ItemStack ammo = p.getProjectile(bow);
			if (!GearModule.fromQuiver(p, ammo)) {
				return "the bow did not draw from the quiver: " + ammo;
			}
			int before = gear.pocket(GearSlot.QUIVER, 0).getCount();
			AABB around = p.getBoundingBox().inflate(64);
			int arrowsBefore = p.level().getEntitiesOfClass(AbstractArrow.class, around).size();
			p.startUsingItem(InteractionHand.MAIN_HAND);
			p.getMainHandItem().releaseUsing(p.level(), p, p.getMainHandItem().getUseDuration(p) - 20);
			p.stopUsingItem();
			int arrowsAfter = p.level().getEntitiesOfClass(AbstractArrow.class, around).size();
			if (arrowsAfter <= arrowsBefore) {
				return "the bow loosed nothing with arrows only in the quiver";
			}
			int after = GearInventory.of(p).pocket(GearSlot.QUIVER, 0).getCount();
			boolean quiverwise = Perks.has(p, dev.voidmc.mettle.character.skill.MarksmanshipSkill.QUIVERWISE);
			if (after != before - 1 && !(quiverwise && after == before)) {
				return "the quiver went from " + before + " to " + after + " arrows for one shot";
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return null;
		});
		server.runCommand("kill @e[type=arrow]");
	}

	private static void belt(ClientGameTestContext context, TestServerContext server) {
		context.setScreen(() -> null);
		on(server, p -> {
			p.getFoodData().setFoodLevel(10);
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.IRON_SWORD));
			return null;
		});
		context.waitTicks(3);
		context.runOnClient(mc -> GearClient.useBelt(mc, 0));
		context.waitTicks(4);
		on(server, p -> p.getMainHandItem().is(Items.BREAD) && p.isUsingItem() ? null
			: "belt pocket 1 did not start eating: hand " + p.getMainHandItem() + ", using " + p.isUsingItem());
		context.waitTicks(45);
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			if (!p.getMainHandItem().is(Items.IRON_SWORD)) {
				return "the sword did not come back after the belt use: hand " + p.getMainHandItem();
			}
			if (gear.pocket(GearSlot.BELT, 0).getCount() != 3 || p.getFoodData().getFoodLevel() <= 10) {
				return "the bread was not eaten: pocket " + gear.pocket(GearSlot.BELT, 0) + ", food " + p.getFoodData().getFoodLevel();
			}
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), ItemStack.EMPTY);
			p.getFoodData().setFoodLevel(20);
			return null;
		});
	}

	// ---------------------------------------------------------------- creative

	private static void creativeCheck(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode creative @a");
		context.waitTicks(2);
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			// What creative's inventory tab sends for a gear slot: a wrong item is refused, a right one accepted.
			p.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(BELT, new ItemStack(Items.DIRT)));
			if (!gear.worn(GearSlot.BELT).is(GearItems.BELT)) {
				return "creative put dirt in the belt slot";
			}
			p.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(BELT, new ItemStack(GearItems.TOOL_BELT)));
			if (!gear.worn(GearSlot.BELT).is(GearItems.TOOL_BELT)) {
				return "creative could not set the belt slot (index 47)";
			}
			p.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(BELT, gearBelt()));
			return null;
		});
		context.setScreen(() -> {
			Minecraft mc = Minecraft.getInstance();
			return new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false);
		});
		context.waitTicks(2);
		context.runOnClient(mc -> ((CreativeModeInventoryScreen) mc.gui.screen())
			.setSelectedTab(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.INVENTORY)));
		context.waitTicks(3);
		String placed = context.computeOnClient(mc -> {
			for (Slot slot : ((CreativeModeInventoryScreen) mc.gui.screen()).getMenu().slots) {
				int index = slot.getContainerSlot();
				if (slot.container instanceof dev.voidmc.mettle.gear.menu.GearSlots.GearContainer) {
					if (index >= BACK && index <= QUIVER && slot.y > 100) {
						return "creative put worn slot " + index + " at y " + slot.y + " (the hotbar row)";
					}
					if (index >= BACK_POCKET && slot.x > -1000) {
						return "creative shows pocket " + index + " at " + slot.x + "," + slot.y;
					}
				}
			}
			return null;
		});
		if (placed != null) {
			throw new AssertionError(placed);
		}
		context.takeScreenshot("mettle_gear_creative_inventory");
		context.setScreen(() -> null);
		server.runCommand("gamemode survival @a");
		context.waitTicks(2);
	}

	private static ItemStack gearBelt() {
		ItemStack belt = new ItemStack(GearItems.BELT);
		belt.set(GearComponents.POCKETS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.BREAD, 3))));
		return belt;
	}

	// ---------------------------------------------------------------- screenshots

	private static void screenshots(ClientGameTestContext context, TestServerContext server) {
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			ItemStack frame = new ItemStack(GearItems.FRAME_PACK);
			frame.set(GearComponents.POCKETS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.COBBLESTONE, 64),
				new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.IRON_INGOT, 9))));
			gear.setWorn(GearSlot.BACK, frame);
			gear.setWorn(GearSlot.QUIVER, new ItemStack(GearItems.HUNTERS_QUIVER));
			gear.setPocket(GearSlot.QUIVER, 0, new ItemStack(Items.ARROW, 40));
			gear.setPocket(GearSlot.BELT, 1, new ItemStack(Items.POTION));
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			return null;
		});
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 30 12");
		context.waitTicks(3);
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(3);
		context.takeScreenshot("mettle_gear_inventory");
		context.setScreen(() -> null);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(5);
		context.takeScreenshot("mettle_gear_worn_back");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(5);
		context.takeScreenshot("mettle_gear_worn_front");
		on(server, p -> {
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
			ItemStack satchel = new ItemStack(GearItems.SATCHEL);
			satchel.set(DataComponents.DYED_COLOR, new DyedItemColor(0x2F5D8A));
			GearInventory.of(p).setWorn(GearSlot.BACK, satchel);
			return null;
		});
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(5);
		context.takeScreenshot("mettle_gear_worn_satchel_dyed");
		// "Show my gear" off: a cosmetic flag every client hears.
		server.runCommand("execute as @p run mettle gear hide");
		context.waitTicks(3);
		if (!context.computeOnClient(mc -> dev.voidmc.mettle.gear.GearVisibility.hidden(mc.player))) {
			throw new AssertionError("Gear: /mettle gear hide did not reach the client");
		}
		context.takeScreenshot("mettle_gear_worn_hidden");
		server.runCommand("execute as @p run mettle gear show");
		context.waitTicks(2);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		on(server, p -> {
			GearInventory.of(p).takeAll();
			return null;
		});
	}

	// ---------------------------------------------------------------- containers

	/** A chest carries the pockets too: looting into a full inventory spills into the pack, and back out by shift-click. */
	private static void containers(ClientGameTestContext context, TestServerContext server) {
		BlockPos[] at = new BlockPos[1];
		net.minecraft.world.level.block.state.BlockState[] was = new net.minecraft.world.level.block.state.BlockState[1];
		on(server, p -> {
			GearInventory.of(p).setWorn(GearSlot.BACK, new ItemStack(GearItems.LEATHER_PACK));
			for (int i = 0; i < 36; i++) {
				p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
			}
			at[0] = p.blockPosition().offset(2, 0, 0);
			was[0] = p.level().getBlockState(at[0]);
			p.level().setBlock(at[0], net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
			if (!(p.level().getBlockEntity(at[0]) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) {
				return "no chest to open";
			}
			chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
			p.openMenu(chest);
			return null;
		});
		context.waitTicks(5);
		on(server, p -> {
			if (!(p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu)) {
				return "the chest did not open: " + p.containerMenu;
			}
			int vanillaEnd = 27 + 36;
			if (menu.slots.size() != vanillaEnd + 37) {
				return "a chest menu has " + menu.slots.size() + " slots, expected " + (vanillaEnd + 37);
			}
			GearInventory gear = GearInventory.of(p);
			menu.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
			if (!gear.pocket(GearSlot.BACK, 0).is(Items.IRON_INGOT) || !menu.getSlot(0).getItem().isEmpty()) {
				return "looting into a full inventory did not reach the pack: " + gear.pockets(GearSlot.BACK);
			}
			menu.clicked(vanillaEnd, 0, ContainerInput.QUICK_MOVE, p);
			if (!menu.getSlot(0).getItem().is(Items.IRON_INGOT) || !gear.pocket(GearSlot.BACK, 0).isEmpty()) {
				return "shift-clicking a pocket did not put the ingots back in the chest";
			}
			menu.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
			return null;
		});
		String client = context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen screen
			&& screen.getMenu().slots.size() == 100 ? null : "the client's chest screen has no pockets: " + mc.gui.screen());
		if (client != null) {
			throw new AssertionError("Gear: " + client);
		}
		context.waitTicks(2);
		context.takeScreenshot("mettle_gear_chest");
		on(server, p -> {
			p.closeContainer();
			if (p.level().getBlockEntity(at[0]) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
				chest.clearContent();
			}
			p.level().setBlock(at[0], was[0], 3);
			GearInventory.of(p).takeAll();
			for (int i = 0; i < 36; i++) {
				p.getInventory().setItem(i, ItemStack.EMPTY);
			}
			return null;
		});
		context.waitTicks(2);
	}

	// ---------------------------------------------------------------- death

	private static void death(ClientGameTestContext context, TestServerContext server, float base) {
		BlockPos[] at = new BlockPos[1];
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			ItemStack pack = new ItemStack(GearItems.LEATHER_PACK);
			pack.set(GearComponents.POCKETS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.COBBLESTONE, 20))));
			gear.setWorn(GearSlot.BACK, pack);
			ItemStack cursed = new ItemStack(GearItems.BELT);
			cursed.enchant(p.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.VANISHING_CURSE), 1);
			gear.setWorn(GearSlot.BELT, cursed);
			at[0] = p.blockPosition();
			return null;
		});
		server.runCommand("kill @p");
		on(server, p -> {
			List<ItemEntity> drops = p.level().getEntitiesOfClass(ItemEntity.class, new AABB(at[0]).inflate(6));
			ItemEntity pack = drops.stream().filter(e -> e.getItem().is(GearItems.LEATHER_PACK)).findFirst().orElse(null);
			if (pack == null) {
				return "the worn pack did not drop at death: " + drops;
			}
			ItemContainerContents inside = pack.getItem().get(GearComponents.POCKETS);
			if (inside == null || inside.nonEmptyItemCopyStream().noneMatch(i -> i.is(Items.COBBLESTONE) && i.getCount() == 20)) {
				return "the dropped pack lost its contents";
			}
			if (drops.stream().anyMatch(e -> e.getItem().is(GearItems.BELT))) {
				return "a belt with Curse of Vanishing dropped";
			}
			return null;
		});
		respawn(context);
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			if (!gear.worn(GearSlot.BACK).isEmpty() || !gear.worn(GearSlot.BELT).isEmpty()) {
				return "gear stayed on through a death without keepInventory";
			}
			float cap = (float) p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			return Math.abs(cap - base) < 0.01f ? null : "capacity after death " + cap + ", expected " + base;
		});
		server.runCommand("kill @e[type=item]");
	}

	private static void keepInventory(ClientGameTestContext context, TestServerContext server, float base) {
		server.runCommand("gamerule keep_inventory true");
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			ItemStack pack = new ItemStack(GearItems.FRAME_PACK);
			pack.set(GearComponents.POCKETS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT, 7))));
			gear.setWorn(GearSlot.BACK, pack);
			gear.setWorn(GearSlot.QUIVER, new ItemStack(GearItems.QUIVER));
			return null;
		});
		server.runCommand("kill @p");
		respawn(context);
		on(server, p -> {
			GearInventory gear = GearInventory.of(p);
			if (!gear.worn(GearSlot.BACK).is(GearItems.FRAME_PACK) || !gear.pocket(GearSlot.BACK, 0).is(Items.IRON_INGOT)
				|| !gear.worn(GearSlot.QUIVER).is(GearItems.QUIVER)) {
				return "keepInventory lost the gear: " + p.getAttached(WornGear.TYPE);
			}
			float cap = (float) p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			return Math.abs(cap - (base + 25f)) < 0.01f ? null : "capacity after a keepInventory death " + cap + ", expected " + (base + 25f);
		});
		context.waitTicks(3);
		String client = context.computeOnClient(mc -> GearInventory.of(mc.player).worn(GearSlot.BACK).is(GearItems.FRAME_PACK) ? null
			: "the client never heard the kept gear after respawn");
		if (client != null) {
			throw new AssertionError(client);
		}
		server.runCommand("gamerule keep_inventory false");
	}

	private static void respawn(ClientGameTestContext context) {
		context.waitForScreen(DeathScreen.class);
		context.runOnClient(mc -> mc.player.respawn());
		context.waitFor(mc -> mc.player != null && mc.player.isAlive() && mc.player.getHealth() > 0, 200);
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- helpers

	private static <T> T on(TestServerContext server, Function<ServerPlayer, T> action) {
		T result = server.computeOnServer(s -> action.apply(s.getPlayerList().getPlayers().getFirst()));
		if (result instanceof String problem) {
			throw new AssertionError("Gear: " + problem);
		}
		return result;
	}
}
