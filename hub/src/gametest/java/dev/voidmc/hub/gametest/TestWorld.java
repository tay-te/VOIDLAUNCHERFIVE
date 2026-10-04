package dev.voidmc.hub.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The test world's furniture: a stone platform at the surface, the player on it, a chest in front
 * of them, and a realistic inventory (tools, armour, blocks, food, stacks, enchanted gear).
 */
public final class TestWorld {

	private TestWorld() {
	}

	/** Where the player stands and where the chest is; computed on the server. */
	public record Spot(BlockPos player, BlockPos chest, BlockPos chest2) {
	}

	public static Spot build(TestSingleplayerContext world) {
		Spot spot = world.getServer().computeOnServer(server -> {
			// Where the player spawned is loaded and on the surface (the heightmap of a chunk
			// nobody has generated yet says -64, and the player falls out of the world).
			ServerLevel level = server.overworld();
			BlockPos at = server.getPlayerList().getPlayers().get(0).blockPosition();
			level.getChunk(at);
			int y = Math.max(at.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()));
			BlockPos base = new BlockPos(at.getX(), y, at.getZ());
			return new Spot(base, base.offset(0, 0, 2), base.offset(4, 0, 2));
		});
		BlockPos p = spot.player();
		world.getServer().runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", p.getX() - 5, p.getY() - 1, p.getZ() - 5, p.getX() + 5, p.getY() - 1, p.getZ() + 5));
		world.getServer().runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", p.getX() - 5, p.getY(), p.getZ() - 5, p.getX() + 5, p.getY() + 4, p.getZ() + 5));
		world.getServer().runCommand(String.format("tp @a %d %d %d 0 20", p.getX(), p.getY(), p.getZ()));
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			level.setBlock(spot.chest(), Blocks.CHEST.defaultBlockState(), 3);
			if (level.getBlockEntity(spot.chest()) instanceof ChestBlockEntity chest) {
				ItemStack[] contents = chestContents(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
				for (int i = 0; i < contents.length; i++) {
					chest.setItem(i, contents[i]);
				}
			}
			// A second, empty-ish chest for the move-all round trip.
			level.setBlock(spot.chest2(), Blocks.CHEST.defaultBlockState(), 3);
		});
		return spot;
	}

	/** Fills the player's inventory: the "realistic mix" every scenario is measured with. */
	public static void fillPlayer(TestSingleplayerContext world) {
		world.getServer().runCommand("xp set @a 23 levels");
		world.getServer().runCommand("xp set @a 40 points");
		fillItems(world);
	}

	private static void fillItems(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			var enchants = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			Inventory inv = player.getInventory();
			inv.clearContent();
			// Hotbar 0-8.
			inv.setItem(0, enchanted(new ItemStack(Items.DIAMOND_SWORD), 357, enchants, Enchantments.SHARPNESS, 4, Enchantments.LOOTING, 2, Enchantments.UNBREAKING, 3));
			inv.setItem(1, new ItemStack(Items.BOW));
			inv.setItem(2, enchanted(new ItemStack(Items.IRON_PICKAXE), 40, enchants, Enchantments.EFFICIENCY, 3));
			inv.setItem(3, new ItemStack(Items.IRON_SHOVEL));
			inv.setItem(4, new ItemStack(Items.TORCH, 30));
			inv.setItem(5, new ItemStack(Items.BREAD, 9));
			inv.setItem(6, new ItemStack(Items.COBBLESTONE, 64));
			inv.setItem(7, new ItemStack(Items.OAK_PLANKS, 16));
			// Storage 9-35.
			Item[] storage = {
				Items.STONE, Items.STONE, Items.DIRT, Items.OAK_LOG, Items.COAL, Items.IRON_INGOT, null, null, Items.APPLE,
				Items.BREAD, null, Items.ARROW, null, Items.STONE, null, Items.DIAMOND, Items.TORCH, null,
				Items.DIRT, null, null, Items.MAP, Items.POTION, Items.COPPER_INGOT, Items.IRON_NUGGET, Items.GOLD_INGOT, null
			};
			int[] counts = {64, 64, 32, 48, 23, 9, 0, 0, 6, 12, 0, 41, 0, 17, 0, 3, 30, 0, 64, 0, 0, 1, 1, 34, 12, 3, 0};
			for (int i = 0; i < storage.length; i++) {
				if (storage[i] != null) {
					inv.setItem(9 + i, new ItemStack(storage[i], counts[i]));
				}
			}
			// Armour (36 feet .. 39 head) and offhand (40).
			inv.setItem(39, enchanted(new ItemStack(Items.IRON_HELMET), 20, enchants, Enchantments.PROTECTION, 2));
			inv.setItem(38, new ItemStack(Items.IRON_CHESTPLATE));
			inv.setItem(37, new ItemStack(Items.IRON_LEGGINGS));
			inv.setItem(36, new ItemStack(Items.IRON_BOOTS));
			inv.setItem(40, new ItemStack(Items.SHIELD));
			player.containerMenu.broadcastChanges();
			player.inventoryMenu.broadcastFullState();
		});
	}

	static ItemStack[] chestContents(net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> enchants) {
		Object[][] spec = {
			{Items.STONE, 64}, {Items.STONE, 64}, {Items.STONE, 64}, {Items.DIRT, 64}, {Items.OAK_PLANKS, 64}, {Items.OAK_PLANKS, 32},
			{Items.IRON_INGOT, 18}, {Items.COAL, 40}, {Items.DIAMOND, 5}, {Items.ARROW, 64}, {Items.BOW, 1}, {Items.GOLD_INGOT, 3},
			{Items.COPPER_INGOT, 12}, {Items.BREAD, 20}, {Items.APPLE, 9}, {Items.POTION, 1}, {Items.TORCH, 16}, {Items.MAP, 1},
			{Items.COBBLESTONE, 64}, {Items.SAND, 33}, {Items.GRAVEL, 21}, {Items.STICK, 30}, {Items.STRING, 7}, {Items.FEATHER, 11},
			{Items.LEATHER, 6}, {Items.BONE, 14}, {Items.REDSTONE, 50}
		};
		ItemStack[] out = new ItemStack[spec.length];
		for (int i = 0; i < spec.length; i++) {
			out[i] = new ItemStack((Item) spec[i][0], (Integer) spec[i][1]);
		}
		out[10] = enchanted(out[10], 12, enchants, Enchantments.POWER, 3);
		return out;
	}

	@SuppressWarnings("unchecked")
	static ItemStack enchanted(ItemStack stack, int damage, net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> enchants, Object... pairs) {
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			Holder<Enchantment> h = enchants.getOrThrow((ResourceKey<Enchantment>) pairs[i]);
			stack.enchant(h, (Integer) pairs[i + 1]);
		}
		if (damage > 0 && stack.isDamageableItem()) {
			stack.setDamageValue(damage);
		}
		return stack;
	}
}
