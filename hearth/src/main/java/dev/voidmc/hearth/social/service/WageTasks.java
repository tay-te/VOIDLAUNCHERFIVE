package dev.voidmc.hearth.social.service;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.life.Venues;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Work for wages (docs/SOCIAL.md §4): a farmer asks for a hand with the harvest (bring in so many ripe crops from the
 * town's fields, re-sowing as you go, then hand the produce over), a quarrier for a load of stone carried to the
 * builders (the stone is the town's, marked as such; keep it and you are a thief). Paid from the treasury, the goods go
 * to the stockpile. One job at a time, a few a day per town, each against the clock.
 */
public final class WageTasks {
	static final String HAUL_TAG = "hearth_haul";
	private static final Map<UUID, WageTask> TASKS = new HashMap<>();
	/** player/settlement/day → jobs taken. */
	private static final Map<String, Integer> TAKEN = new HashMap<>();

	private WageTasks() {
	}

	public static @Nullable WageTask task(Player player) {
		return TASKS.get(player.getUUID());
	}

	public static int takenToday(UUID player, long settlementId, long day) {
		return TAKEN.getOrDefault(player + "/" + settlementId + "/" + day, 0);
	}

	public enum Result {
		OK, BUSY, LIMIT, NOTHING, NO_ROOM, TOWN_CANNOT_PAY
	}

	/** Takes a harvest job from a farmer. */
	public static Result takeHarvest(ServerPlayer player, HearthWorld w, Settlement s, Resident farmer) {
		SocialConfig.Wages cfg = SocialModule.config().wages();
		Result check = canTake(player, w, s);
		if (check != Result.OK) {
			return check;
		}
		if (Venues.built(s, ServiceSites.FARMS.toArray(String[]::new)).isEmpty()) {
			return Result.NOTHING;
		}
		if (s.treasury() < (long) cfg.harvestCrops() * cfg.harvestWage()) {
			return Result.TOWN_CANNOT_PAY;
		}
		WageTask t = new WageTask(WageTask.Kind.HARVEST, s.id(), farmer.id, farmer.givenName, cfg.harvestCrops(),
			w.level().getGameTime() + cfg.timeLimitTicks(), Items.AIR);
		start(player, s, w, t);
		return Result.OK;
	}

	/** Takes a hauling job: the load is put in the player's arms. */
	public static Result takeHaul(ServerPlayer player, HearthWorld w, Settlement s, Resident quarrier) {
		SocialConfig.Wages cfg = SocialModule.config().wages();
		Result check = canTake(player, w, s);
		if (check != Result.OK) {
			return check;
		}
		if (s.treasury() < cfg.haulWage()) {
			return Result.TOWN_CANNOT_PAY;
		}
		Item stone = stoneOf(s.culture());
		int free = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) {
				free += stone.getDefaultMaxStackSize();
			}
		}
		if (free < cfg.haulLoad()) {
			return Result.NO_ROOM;
		}
		WageTask t = new WageTask(WageTask.Kind.HAUL, s.id(), quarrier.id, quarrier.givenName, cfg.haulLoad(),
			w.level().getGameTime() + cfg.timeLimitTicks(), stone);
		int left = cfg.haulLoad();
		while (left > 0) {
			int n = Math.min(left, stone.getDefaultMaxStackSize());
			ItemStack load = new ItemStack(stone, n);
			CompoundTag tag = new CompoundTag();
			tag.putLong(HAUL_TAG, s.id());
			CustomData.set(DataComponents.CUSTOM_DATA, load, tag);
			player.getInventory().add(load);
			if (!load.isEmpty()) {
				net.minecraft.world.Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), load);
			}
			left -= n;
		}
		start(player, s, w, t);
		return Result.OK;
	}

	private static Result canTake(ServerPlayer player, HearthWorld w, Settlement s) {
		SocialConfig.Wages cfg = SocialModule.config().wages();
		if (TASKS.containsKey(player.getUUID())) {
			return Result.BUSY;
		}
		if (takenToday(player.getUUID(), s.id(), w.day()) >= cfg.perDay()) {
			return Result.LIMIT;
		}
		return Result.OK;
	}

	private static void start(ServerPlayer player, Settlement s, HearthWorld w, WageTask t) {
		TASKS.put(player.getUUID(), t);
		TAKEN.merge(player.getUUID() + "/" + s.id() + "/" + w.day(), 1, Integer::sum);
		if (TAKEN.size() > 2048) {
			TAKEN.clear();
		}
	}

	/** Hands in a harvest to a farmer; returns the wage paid, or -1 if there was nothing to hand in. */
	public static long handInHarvest(ServerPlayer player, Settlement s) {
		WageTask t = TASKS.get(player.getUUID());
		if (t == null || t.kind != WageTask.Kind.HARVEST || t.settlementId != s.id() || t.progress == 0) {
			return -1;
		}
		SocialConfig.Wages cfg = SocialModule.config().wages();
		int handed = 0;
		for (Map.Entry<Item, Integer> e : t.produce.entrySet()) {
			int took = take(player, e.getKey(), e.getValue(), null);
			handed += took;
			Goods g = e.getKey() == Items.WHEAT ? Goods.GRAIN : Goods.PRODUCE;
			s.stockpile().add(g, took);
		}
		if (handed == 0) {
			return -1;
		}
		long wage = Math.min(s.treasury(), WageTask.wage(t.kind, handed, t.progress, cfg.harvestWage(), cfg.haulWage()));
		if (wage > 0 && s.withdraw(wage)) {
			Hooks.pay(player, wage);
		} else {
			wage = 0;
		}
		Hooks.deed(player, s, "social:wages", 1, t.giver, player.blockPosition(), true);
		TASKS.remove(player.getUUID());
		s.markDirty();
		return wage;
	}

	/** Delivers a load of stone to the builders; returns the wage paid, or -1 if the player does not carry the load. */
	public static long handInHaul(ServerPlayer player, Settlement s) {
		WageTask t = TASKS.get(player.getUUID());
		if (t == null || t.kind != WageTask.Kind.HAUL || t.settlementId != s.id()) {
			return -1;
		}
		if (count(player, t.load, s.id()) < t.target) {
			return -1;
		}
		take(player, t.load, t.target, s.id());
		s.stockpile().add(Goods.STONE, t.target);
		SocialConfig.Wages cfg = SocialModule.config().wages();
		long wage = Math.min(s.treasury(), cfg.haulWage());
		if (wage > 0 && s.withdraw(wage)) {
			Hooks.pay(player, wage);
		} else {
			wage = 0;
		}
		Hooks.deed(player, s, "social:wages", 1, t.giver, player.blockPosition(), true);
		TASKS.remove(player.getUUID());
		s.markDirty();
		return wage;
	}

	/** Gives up the current job (a load carried is handed back first if it is in the inventory). */
	public static boolean quit(ServerPlayer player, @Nullable Settlement s) {
		WageTask t = TASKS.remove(player.getUUID());
		if (t == null) {
			return false;
		}
		if (t.kind == WageTask.Kind.HAUL) {
			take(player, t.load, t.target, t.settlementId);
		}
		return true;
	}

	/** A crop broken by a player with a harvest job, in the job's town's fields: counted and re-sown. */
	public static void onBreak(Level level, Player player, BlockPos pos, BlockState state) {
		WageTask t = TASKS.get(player.getUUID());
		if (t == null || t.kind != WageTask.Kind.HARVEST || !(level instanceof ServerLevel server) || !(state.getBlock() instanceof CropBlock crop)
			|| !crop.isMaxAge(state)) {
			return;
		}
		HearthWorld w = HearthWorld.of(server);
		Settlement s = w == null ? null : w.settlement(t.settlementId);
		if (s == null || !inFields(s, pos)) {
			return;
		}
		t.progress++;
		t.produce.merge(produce(state.getBlock()), 1, Integer::sum);
		// sow again behind you, as the farmer would
		if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
			level.setBlock(pos, crop.defaultBlockState(), Block.UPDATE_ALL);
		}
		if (t.progress == t.target && player instanceof ServerPlayer sp) {
			sp.sendSystemMessage(Component.translatable("hearth.social.wages.harvest_done", t.giverName).withStyle(ChatFormatting.GREEN));
		}
	}

	static boolean inFields(Settlement s, BlockPos pos) {
		for (PlotView p : s.plots()) {
			if (p.spec().archetype().getPath().startsWith("farm/") && Venues.inside(p.spec(), pos)) {
				return true;
			}
		}
		return false;
	}

	static Item produce(Block crop) {
		if (crop == Blocks.WHEAT) {
			return Items.WHEAT;
		}
		if (crop == Blocks.BEETROOTS) {
			return Items.BEETROOT;
		}
		if (crop == Blocks.CARROTS) {
			return Items.CARROT;
		}
		if (crop == Blocks.POTATOES) {
			return Items.POTATO;
		}
		return crop.asItem();
	}

	/** Run every second: time runs out, and the HUD shows how the job goes. */
	public static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, WageTask>> it = TASKS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			WageTask t = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			long left = t.deadline - now;
			if (left <= 0) {
				it.remove();
				expired(player, t);
				continue;
			}
			String time = (left / 1200) + ":" + String.format(java.util.Locale.ROOT, "%02d", (left / 20) % 60);
			Component line = t.kind == WageTask.Kind.HARVEST
				? Component.translatable("hearth.social.wages.hud.harvest", t.progress, t.target, time)
				: Component.translatable("hearth.social.wages.hud.haul", t.target, time);
			player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static void expired(ServerPlayer player, WageTask t) {
		player.sendSystemMessage(Component.translatable("hearth.social.wages.expired", t.giverName).withStyle(ChatFormatting.RED));
		if (t.kind != WageTask.Kind.HAUL || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		int kept = count(player, t.load, t.settlementId);
		HearthWorld w = HearthWorld.of(level);
		Settlement s = w == null ? null : w.settlement(t.settlementId);
		if (kept > 0 && s != null) {
			// the town's stone, carried off: a suspicion, not a conviction
			Hooks.deed(player, s, "social:haul_kept", -3, t.giver, player.blockPosition(), false);
			Hooks.report(new LawService.Crime(LawService.Offence.THEFT, player.getUUID(), s.id(), player.blockPosition(), t.giver, Math.max(1, kept / 8), false,
				level.getGameTime()));
		}
	}

	/** Items of a kind the player carries (only those marked for this town's haul when {@code haul} is set). */
	static int count(Player player, Item item, @Nullable Long haul) {
		int n = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item) && (haul == null || isHaul(st, haul))) {
				n += st.getCount();
			}
		}
		return n;
	}

	/** Takes up to {@code max} of an item; returns how many. */
	static int take(Player player, Item item, int max, @Nullable Long haul) {
		int left = max;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item) && (haul == null || isHaul(st, haul))) {
				int n = Math.min(left, st.getCount());
				st.shrink(n);
				left -= n;
			}
		}
		inv.setChanged();
		return max - left;
	}

	public static boolean isHaul(ItemStack st, long settlementId) {
		CustomData d = st.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().getLongOr(HAUL_TAG, Long.MIN_VALUE) == settlementId;
	}

	/** The building stone a culture's quarry gives. */
	static Item stoneOf(Culture c) {
		String id = switch (c) {
			case DESERT -> "minecraft:sandstone";
			case MESA -> "minecraft:red_sandstone";
			case KARST -> "expanse:limestone";
			default -> "minecraft:cobblestone";
		};
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(Items.COBBLESTONE);
	}

	public static void clear() {
		TASKS.clear();
		TAKEN.clear();
	}
}
