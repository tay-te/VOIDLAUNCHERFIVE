package dev.voidmc.hearth.social.life;

import com.mojang.math.Transformation;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.npc.work.field.FieldWork;
import dev.voidmc.hearth.npc.work.field.FieldWorkConfig;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Stalls stocked with the town's real goods (docs/LIVING_WORLD_2.md §6.1, §6.3): every market stall, market hall counter,
 * bakery and shop counter of an embodied town shows what that shop holds, as item displays lying on its counter tops —
 * the culture's own items for its goods ({@link dev.voidmc.hearth.api.GoodsItems}), most-stocked first, a ware for every
 * {@link #UNITS_PER_WARE} units on the shelves. A shop running out shows empty boards; restocking fills them again.
 *
 * <p>Displays are views: tagged {@link #TAG} (never saved with the chunk), they do not tick, and they are put up again
 * from the books whenever the stock moves by a quarter or a sim hour passes, and taken down when the town goes abstract.
 * At most 6 a stall, 60 a town and 1 500 on the server (config {@code displays}), fewer as the server slows.
 */
public final class StallWares {
	/** Entity tag of a stall's wares: never written into a chunk. */
	public static final String TAG = "hearth.display";
	/** Shelf units one ware on the counter stands for. */
	public static final int UNITS_PER_WARE = 4;
	/** Server ticks between looks at the towns (a slice of them each time). */
	static final int PASS_TICKS = 100;
	/** Game ticks after which a stall's wares are laid out again even if the stock held (a sim hour). */
	static final long REFRESH_TICKS = 1000;
	/** How near a player a stall must be to be dressed. */
	static final int PLAYER_RANGE = 48;

	private StallWares() {
	}

	// ---------------------------------------------------------------- what a stall shows (pure)

	/**
	 * The goods on a stall's slots, most-stocked first: one ware for every {@link #UNITS_PER_WARE} units held (at least one
	 * for any goods held), shared among the goods in proportion to their stock (largest remainders), at most {@code max}.
	 * Every goods shown is held; empty when the shop is sold out.
	 */
	public static List<Goods> shown(Map<Goods, Integer> stock, int max) {
		List<Map.Entry<Goods, Integer>> held = new ArrayList<>();
		int total = 0;
		for (Map.Entry<Goods, Integer> e : stock.entrySet()) {
			if (e.getValue() != null && e.getValue() > 0) {
				held.add(e);
				total += e.getValue();
			}
		}
		if (held.isEmpty() || max <= 0) {
			return List.of();
		}
		held.sort(Comparator.comparingInt((Map.Entry<Goods, Integer> e) -> -e.getValue()).thenComparingInt(e -> e.getKey().ordinal()));
		int slots = Math.min(max, Math.max(1, (total + UNITS_PER_WARE - 1) / UNITS_PER_WARE));
		int n = Math.min(held.size(), slots);
		// the top goods each get a ware; the rest of the slots go by share of what they hold
		int[] count = new int[n];
		double[] rem = new double[n];
		int topTotal = 0;
		for (int i = 0; i < n; i++) {
			topTotal += held.get(i).getValue();
		}
		int given = 0;
		for (int i = 0; i < n; i++) {
			double share = (slots - n) * held.get(i).getValue() / (double) Math.max(1, topTotal);
			count[i] = 1 + (int) Math.floor(share);
			rem[i] = share - Math.floor(share);
			given += count[i];
		}
		while (given < slots) {
			int best = 0;
			for (int i = 1; i < n; i++) {
				if (rem[i] > rem[best]) {
					best = i;
				}
			}
			count[best]++;
			rem[best] = -1;
			given++;
		}
		List<Goods> out = new ArrayList<>(slots);
		for (int i = 0; i < n; i++) {
			for (int k = 0; k < count[i]; k++) {
				out.add(held.get(i).getKey());
			}
		}
		return out;
	}

	/** Which quarter of {@code full} the stock is at: the wares are laid out again when it moves by one. */
	static int quarter(int total, int full) {
		return Math.min(4, total * 4 / Math.max(1, full));
	}

	// ---------------------------------------------------------------- the displays (server thread)

	/** One stall dressed: its plot, its wares' entities and what they showed. */
	private static final class Stall {
		final List<UUID> entities = new ArrayList<>();
		List<Goods> shown = List.of();
		int quarter;
		long laidAt;
	}

	/** Dressed stalls by settlement, then by plot. */
	private static final Map<Long, Map<Integer, Stall>> TOWNS = new HashMap<>();
	private static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("stall wares");
	private static int count;
	private static long ticks;
	private static int cursor;

	/** How many ware displays stand now, server-wide. */
	public static int count() {
		return count;
	}

	/** From {@code FieldWork.init}. */
	public static void init() {
		Dressings.listen(MARKET_LISTENER);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(StallWares::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			TOWNS.clear();
			MARKETS.clear();
			COUNTERS.clear();
			count = 0;
		});
		dev.voidmc.hearth.api.SettlementEvents.EMBODIMENT_CHANGED.register((w, s, embodied) -> {
			if (!embodied && w.level() instanceof ServerLevel level) {
				takeDown(level, s.id());
			}
		});
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % PASS_TICKS != 0 || HearthServices.economy() == null) {
			return;
		}
		TIMER.time(() -> pass(server));
	}

	private static void pass(MinecraftServer server) {
		FieldWorkConfig.Displays cfg = FieldWork.config().displays();
		double mspt = LoadCaps.mspt(server);
		double high = LoadCaps.highMspt();
		int serverCap = LoadCaps.cap(cfg.maxPerServer(), 0, mspt, high);
		boolean admitting = LoadCaps.admitting(mspt, high);
		Set<Long> live = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			List<Settlement> towns = SettlementSources.embodied(level);
			for (Settlement s : towns) {
				live.add(s.id());
			}
			if (towns.isEmpty()) {
				continue;
			}
			// a few towns a pass, round robin
			int n = Math.min(4, towns.size());
			for (int i = 0; i < n; i++) {
				Settlement s = towns.get(Math.floorMod(cursor + i, towns.size()));
				dress(level, s, cfg, serverCap, admitting);
			}
			cursor += n;
		}
		// towns gone abstract or unloaded: their wares are taken down
		Iterator<Map.Entry<Long, Map<Integer, Stall>>> it = TOWNS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Map<Integer, Stall>> e = it.next();
			if (!live.contains(e.getKey())) {
				for (ServerLevel level : server.getAllLevels()) {
					for (Stall st : e.getValue().values()) {
						discard(level, st);
					}
				}
				it.remove();
			}
		}
		for (Iterator<Map.Entry<Long, List<MarketStall>>> mt = MARKETS.entrySet().iterator(); mt.hasNext(); ) {
			Map.Entry<Long, List<MarketStall>> e = mt.next();
			if (!live.contains(e.getKey())) {
				for (ServerLevel level : server.getAllLevels()) {
					for (MarketStall ms : e.getValue()) {
						discard(level, ms.dressed);
					}
				}
				mt.remove();
			}
		}
		if (count > serverCap) {
			shed(server, count - serverCap);
		}
	}

	private static void dress(ServerLevel level, Settlement s, FieldWorkConfig.Displays cfg, int serverCap, boolean admitting) {
		EconomyService economy = HearthServices.economy();
		Map<Integer, Stall> stalls = TOWNS.computeIfAbsent(s.id(), k -> new HashMap<>());
		Map<Integer, List<Poi>> counters = cachedCounters(s, level.getGameTime());
		int townCap = cfg.maxPerTown();
		int inTown = 0;
		for (Stall st : stalls.values()) {
			inTown += st.entities.size();
		}
		List<MarketStall> market = MARKETS.get(s.id());
		if (market != null) {
			for (MarketStall ms : market) {
				inTown += ms.dressed.entities.size();
			}
		}
		long now = level.getGameTime();
		List<ServerPlayer> players = level.players();
		for (Map.Entry<Integer, List<Poi>> e : counters.entrySet()) {
			int plot = e.getKey();
			List<Poi> pois = e.getValue();
			BlockPos at = pois.get(0).pos();
			Stall st = stalls.get(plot);
			boolean near = false;
			for (ServerPlayer p : players) {
				if (p.blockPosition().distSqr(at) < PLAYER_RANGE * PLAYER_RANGE) {
					near = true;
					break;
				}
			}
			if (!near || !level.isLoaded(at)) {
				if (st != null && !near) {
					inTown -= st.entities.size();
					discard(level, st);
					stalls.remove(plot);
				}
				continue;
			}
			Map<Goods, Integer> stock = economy.shopStock(s, plot);
			int total = 0;
			for (int v : stock.values()) {
				total += v;
			}
			int max = cfg.maxPerStall();
			List<Goods> wanted = shown(stock, max);
			int q = quarter(total, max * UNITS_PER_WARE);
			if (!wanted.isEmpty()) {
				call(level, s, plot, pois, wanted.get(0));
			}
			if (st != null && st.shown.equals(wanted) && st.quarter == q && now - st.laidAt < REFRESH_TICKS) {
				continue;
			}
			if (st != null && st.shown.equals(wanted) && st.quarter == q) {
				st.laidAt = now;
				continue;
			}
			int had = st == null ? 0 : st.entities.size();
			int room = Math.min(townCap - (inTown - had), serverCap - (count - had));
			if (!admitting && wanted.size() > had) {
				room = Math.min(room, had);
			}
			if (st == null) {
				st = new Stall();
				stalls.put(plot, st);
			} else {
				inTown -= st.entities.size();
				discard(level, st);
			}
			List<Goods> lay = wanted.subList(0, Math.max(0, Math.min(wanted.size(), room)));
			lay(level, s, plot, pois, lay, st);
			st.shown = wanted;
			st.quarter = q;
			st.laidAt = now;
			inTown += st.entities.size();
		}
		if (market != null && !market.isEmpty()) {
			inTown = dressMarket(level, s, market, cfg, townCap, inTown, serverCap, admitting, now, players);
		}
		// stalls whose shop is gone
		stalls.keySet().removeIf(plot -> {
			if (!counters.containsKey(plot)) {
				discard(level, stalls.get(plot));
				return true;
			}
			return false;
		});
	}

	/** The stallholder calls the real wares to a player passing (docs/LIVING_WORLD_2.md §6.7): the shop's top goods by name. */
	private static void call(ServerLevel level, Settlement s, int plot, List<Poi> pois, Goods top) {
		dev.voidmc.hearth.api.NpcService npc = HearthServices.npc();
		if (npc == null) {
			return;
		}
		BlockPos at = pois.get(0).pos();
		for (dev.voidmc.hearth.api.NpcService.FolkHandle h : npc.folkNear(level, net.minecraft.world.phys.Vec3.atCenterOf(at), 4)) {
			if (h.resident().work == plot && h.entity() instanceof dev.voidmc.hearth.npc.entity.Folk f && f.brain() != null) {
				ItemStack shown = HearthServices.goodsItems().sample(top, s.culture(), s.id() * 31 + plot * 7L);
				String name = shown.isEmpty() ? top.getSerializedName() : shown.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
				dev.voidmc.hearth.npc.work.field.WorkBarks.maybe(f.brain(), level, "stall", 3, Map.of("goods", name));
				return;
			}
		}
	}

	// ---------------------------------------------------------------- market-day stalls (A2's stall callback)

	/** A stall the market set up today: its counter (the wares lie on top of it) and what is laid there. */
	static final class MarketStall {
		final String event;
		final BlockPos counter;
		final int index;
		final Stall dressed = new Stall();

		MarketStall(String event, BlockPos counter, int index) {
			this.event = event;
			this.counter = counter;
			this.index = index;
		}
	}

	/** The market stalls standing in each town, by settlement. */
	private static final Map<Long, List<MarketStall>> MARKETS = new HashMap<>();

	/** Told by {@link Dressings#setUpMarket} when stalls go up and when their blocks come down (docs/LIVING_WORLD_2.md §6.3). */
	static final Dressings.StallListener MARKET_LISTENER = new Dressings.StallListener() {
		@Override
		public void up(ServerLevel level, Settlement s, TownEvent market, List<Dressings.Stall> stalls) {
			List<MarketStall> list = MARKETS.computeIfAbsent(s.id(), k -> new ArrayList<>());
			for (Dressings.Stall st : stalls) {
				list.removeIf(ms -> ms.counter.equals(st.counter()) && discardMarket(level, ms));
				list.add(new MarketStall(market.key(), st.counter().immutable(), st.index()));
			}
		}

		@Override
		public void down(ServerLevel level, String eventKey, Set<Long> positions) {
			for (List<MarketStall> list : MARKETS.values()) {
				list.removeIf(ms -> ms.event.equals(eventKey) && positions.contains(ms.counter.asLong()) && discardMarket(level, ms));
			}
		}

		@Override
		public boolean dressesCounters() {
			return true;
		}
	};

	private static boolean discardMarket(ServerLevel level, MarketStall ms) {
		discard(level, ms.dressed);
		return true;
	}

	/**
	 * What a market-day stall offers: its keeper's own shop (the resident the stall keeper behaviour sends to it), or, where
	 * the keeper keeps none or it is bare, the town's stores (the market sells what the town has).
	 */
	static Map<Goods, Integer> marketStock(Settlement s, TownEvent market, int index, EconomyService economy) {
		for (dev.voidmc.hearth.api.Resident r : s.residents()) {
			if (dev.voidmc.hearth.social.folk.StallKeeperBehaviour.stallOf(s, market, r) == index) {
				if (r.work != dev.voidmc.hearth.api.Resident.NO_PLOT) {
					Map<Goods, Integer> own = economy.shopStock(s, r.work);
					for (Integer v : own.values()) {
						if (v != null && v > 0) {
							return own;
						}
					}
				}
				break;
			}
		}
		return stores(s);
	}

	/** The town's stores of the goods a market sells (what a keeper with no shop of their own brings to the stall). */
	public static Map<Goods, Integer> stores(Settlement s) {
		Map<Goods, Integer> stores = new java.util.EnumMap<>(Goods.class);
		for (Goods g : MARKET_GOODS) {
			int n = s.stockpile().get(g);
			if (n > 0) {
				stores.put(g, n);
			}
		}
		return stores;
	}

	/** What the town's stores bring to a market stall when its keeper has no shop of their own. */
	static final List<Goods> MARKET_GOODS = List.of(Goods.FOOD, Goods.PRODUCE, Goods.FISH, Goods.MEAT, Goods.GRAIN, Goods.FLOUR, Goods.CLOTH, Goods.WOOL,
		Goods.SALT, Goods.WAX, Goods.TOOLS);

	/** Lays (or refreshes) the wares on today's market stalls near a player; returns the town's display count after. */
	private static int dressMarket(ServerLevel level, Settlement s, List<MarketStall> market, FieldWorkConfig.Displays cfg, int townCap, int inTown,
		int serverCap, boolean admitting, long now, List<ServerPlayer> players) {
		EconomyService economy = HearthServices.economy();
		TownEvent e = TownLife.current(s, TownEvent.Kind.MARKET);
		for (Iterator<MarketStall> it = market.iterator(); it.hasNext(); ) {
			MarketStall ms = it.next();
			if (e == null || !e.key().equals(ms.event)) {
				// the market is over (its blocks come down with the day's dressings): its wares go now
				inTown -= ms.dressed.entities.size();
				discard(level, ms.dressed);
				it.remove();
				continue;
			}
			boolean near = false;
			for (ServerPlayer p : players) {
				if (p.blockPosition().distSqr(ms.counter) < PLAYER_RANGE * PLAYER_RANGE) {
					near = true;
					break;
				}
			}
			if (!near || !level.isLoaded(ms.counter)) {
				inTown -= ms.dressed.entities.size();
				discard(level, ms.dressed);
				continue;
			}
			Map<Goods, Integer> stock = marketStock(s, e, ms.index, economy);
			int total = 0;
			for (int v : stock.values()) {
				total += v;
			}
			int max = cfg.maxPerStall();
			List<Goods> wanted = shown(stock, max);
			int q = quarter(total, max * UNITS_PER_WARE);
			Stall st = ms.dressed;
			if (!st.entities.isEmpty() && st.shown.equals(wanted) && st.quarter == q) {
				if (now - st.laidAt >= REFRESH_TICKS) {
					st.laidAt = now;
				}
				continue;
			}
			int had = st.entities.size();
			int room = Math.min(townCap - (inTown - had), serverCap - (count - had));
			if (!admitting && wanted.size() > had) {
				room = Math.min(room, had);
			}
			inTown -= had;
			discard(level, st);
			BlockPos top = ms.counter.above();
			if (level.getBlockState(top).isAir()) {
				layOn(level, s, keeperPlot(s, e, ms.index), List.of(top), wanted.subList(0, Math.max(0, Math.min(wanted.size(), room))), st);
			}
			st.shown = wanted;
			st.quarter = q;
			st.laidAt = now;
			inTown += st.entities.size();
		}
		return inTown;
	}

	/** The work plot of a market stall's keeper (for the trade's own items: a baker's loaves), or -1. */
	private static int keeperPlot(Settlement s, TownEvent market, int index) {
		for (dev.voidmc.hearth.api.Resident r : s.residents()) {
			if (dev.voidmc.hearth.social.folk.StallKeeperBehaviour.stallOf(s, market, r) == index) {
				return r.work;
			}
		}
		return -1;
	}

	/** The market stalls with wares on them now, for the tour and commands: counter → goods. */
	public static Map<BlockPos, List<Goods>> marketShowing(long settlementId) {
		Map<BlockPos, List<Goods>> out = new HashMap<>();
		List<MarketStall> list = MARKETS.get(settlementId);
		if (list != null) {
			for (MarketStall ms : list) {
				if (!ms.dressed.entities.isEmpty()) {
					out.put(ms.counter, ms.dressed.shown);
				}
			}
		}
		return out;
	}

	/** Each town's counters, worked out a sim hour at a time (every plot against every counter point is dear in a city). */
	private static final Map<Long, Object[]> COUNTERS = new HashMap<>();

	private static Map<Integer, List<Poi>> cachedCounters(Settlement s, long now) {
		Object[] c = COUNTERS.get(s.id());
		if (c == null || (long) c[0] < now) {
			if (COUNTERS.size() > 512) {
				COUNTERS.clear();
			}
			c = new Object[]{now + REFRESH_TICKS, counters(s)};
			COUNTERS.put(s.id(), c);
		}
		@SuppressWarnings("unchecked")
		Map<Integer, List<Poi>> m = (Map<Integer, List<Poi>>) c[1];
		return m;
	}

	/** The counter and stall points of each built plot with any, by plot id. */
	public static Map<Integer, List<Poi>> counters(Settlement s) {
		Map<Integer, List<Poi>> out = new HashMap<>();
		List<Poi> pois = new ArrayList<>(s.pois(PoiType.COUNTER));
		pois.addAll(s.pois(PoiType.MARKET_STALL));
		if (pois.isEmpty()) {
			return out;
		}
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.BUILT) {
				continue;
			}
			PlotSpec spec = p.spec();
			BlockPos o = spec.origin();
			for (Poi poi : pois) {
				BlockPos q = poi.pos();
				if (q.getX() >= o.getX() && q.getX() < o.getX() + spec.sizeX() && q.getZ() >= o.getZ() && q.getZ() < o.getZ() + spec.sizeZ()) {
					out.computeIfAbsent(p.id(), k -> new ArrayList<>()).add(poi);
				}
			}
		}
		return out;
	}

	/** Lays the wares out on the counter tops round the stall's points. */
	/**
	 * What a trade's own shop shows of a goods, where the trade says more than the goods do: loaves on a bakery's counter,
	 * meat at the butcher's, fish at the fishery. Empty where the culture's table ({@code GoodsItems}) answers.
	 */
	static ItemStack trade(Settlement s, int plot, Goods goods) {
		if (goods != Goods.FOOD) {
			return ItemStack.EMPTY;
		}
		dev.voidmc.hearth.api.PlotView p = null;
		for (dev.voidmc.hearth.api.PlotView q : s.plots()) {
			if (q.id() == plot) {
				p = q;
				break;
			}
		}
		if (p == null) {
			return ItemStack.EMPTY;
		}
		String path = p.spec().archetype().getPath();
		if (path.endsWith("bakery")) {
			return new ItemStack(net.minecraft.world.item.Items.BREAD);
		}
		if (path.endsWith("butcher")) {
			return new ItemStack(net.minecraft.world.item.Items.COOKED_MUTTON);
		}
		if (path.endsWith("fishery") || path.endsWith("fishmonger")) {
			return new ItemStack(net.minecraft.world.item.Items.COD);
		}
		return ItemStack.EMPTY;
	}

	private static void lay(ServerLevel level, Settlement s, int plot, List<Poi> pois, List<Goods> wares, Stall st) {
		if (wares.isEmpty()) {
			return;
		}
		layOn(level, s, plot, tops(level, pois), wares, st);
	}

	/** Lays wares as displays on these counter tops (round them, then a second row). */
	private static void layOn(ServerLevel level, Settlement s, int plot, List<BlockPos> tops, List<Goods> wares, Stall st) {
		if (wares.isEmpty() || tops.isEmpty()) {
			return;
		}
		dev.voidmc.hearth.api.GoodsItems items = HearthServices.goodsItems();
		for (int i = 0; i < wares.size(); i++) {
			BlockPos top = tops.get(i % tops.size());
			int layer = i / tops.size();
			ItemStack stack = trade(s, plot, wares.get(i));
			if (stack.isEmpty()) {
				stack = items.sample(wares.get(i), s.culture(), s.id() * 31 + plot * 7L + i);
			}
			if (stack.isEmpty()) {
				continue;
			}
			Display.ItemDisplay d = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
			if (d == null) {
				continue;
			}
			d.addTag(TAG);
			d.setItemStack(stack);
			d.setItemTransform(ItemDisplayContext.FIXED);
			// lying on the board, a little turned each, a second row a little apart
			double jx = ((i * 37) % 7 - 3) * 0.06 + (layer == 0 ? 0 : 0.22);
			double jz = ((i * 53) % 7 - 3) * 0.06;
			float yaw = (float) Math.toRadians((i * 67) % 360);
			Quaternionf flat = new Quaternionf().rotateY(yaw).rotateX((float) Math.toRadians(90));
			d.setTransformation(new Transformation(new Vector3f(0, 0, 0), flat, new Vector3f(0.42F, 0.42F, 0.42F), new Quaternionf()));
			d.setViewRange(0.6F);
			d.snapTo(top.getX() + 0.5 + jx, top.getY() + 0.03 + layer * 0.05, top.getZ() + 0.5 + jz, 0, 0);
			if (level.addFreshEntity(d)) {
				st.entities.add(d.getUUID());
				count++;
			}
		}
	}

	/**
	 * The counter tops by a stall's points: solid blocks beside them (or under the board in front of the keeper) with air
	 * over them, nearest first, never the floor a folk stands on.
	 */
	static List<BlockPos> tops(ServerLevel level, List<Poi> pois) {
		List<BlockPos> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (Poi poi : pois) {
			BlockPos p = poi.pos();
			for (int r = 1; r <= 2; r++) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos c = p.relative(d, r);
					if (!level.isLoaded(c) || !seen.add(c.asLong())) {
						continue;
					}
					BlockState st = level.getBlockState(c);
					// a counter: something solid at the height a folk stands (waist high to them), open above (not a wall)
					boolean board = st.isCollisionShapeFullBlock(level, c) || st.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
						|| st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock || st.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock;
					if (board && level.getBlockState(c.above()).isAir()) {
						out.add(c.above());
					}
				}
			}
		}
		return out;
	}

	private static void discard(ServerLevel level, @Nullable Stall st) {
		if (st == null) {
			return;
		}
		for (UUID id : st.entities) {
			Entity e = level.getEntity(id);
			if (e != null) {
				e.discard();
			}
			count = Math.max(0, count - 1);
		}
		st.entities.clear();
		st.shown = List.of();
	}

	/** Takes down every ware of a town (it went abstract). */
	public static void takeDown(ServerLevel level, long settlementId) {
		List<MarketStall> market = MARKETS.remove(settlementId);
		if (market != null) {
			for (MarketStall ms : market) {
				discard(level, ms.dressed);
			}
		}
		Map<Integer, Stall> stalls = TOWNS.remove(settlementId);
		if (stalls != null) {
			for (Stall st : stalls.values()) {
				discard(level, st);
			}
		}
	}

	/** Over the server's cap (it slowed): the stalls dressed longest ago go first. */
	private static void shed(MinecraftServer server, int n) {
		List<Stall> all = new ArrayList<>();
		for (Map<Integer, Stall> m : TOWNS.values()) {
			all.addAll(m.values());
		}
		for (List<MarketStall> m : MARKETS.values()) {
			for (MarketStall ms : m) {
				all.add(ms.dressed);
			}
		}
		all.sort(Comparator.comparingLong(st -> st.laidAt));
		for (Stall st : all) {
			if (n <= 0) {
				break;
			}
			n -= st.entities.size();
			for (ServerLevel level : server.getAllLevels()) {
				discard(level, st);
			}
		}
	}

	/** The goods a town's stalls show now, by plot (for the tour and commands). */
	public static Map<Integer, List<Goods>> showing(long settlementId) {
		Map<Integer, List<Goods>> out = new HashMap<>();
		Map<Integer, Stall> stalls = TOWNS.get(settlementId);
		if (stalls != null) {
			stalls.forEach((plot, st) -> out.put(plot, st.shown));
		}
		return out;
	}
}
