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
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(StallWares::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			TOWNS.clear();
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
		if (count > serverCap) {
			shed(server, count - serverCap);
		}
	}

	private static void dress(ServerLevel level, Settlement s, FieldWorkConfig.Displays cfg, int serverCap, boolean admitting) {
		EconomyService economy = HearthServices.economy();
		Map<Integer, Stall> stalls = TOWNS.computeIfAbsent(s.id(), k -> new HashMap<>());
		Map<Integer, List<Poi>> counters = counters(s);
		int townCap = cfg.maxPerTown();
		int inTown = 0;
		for (Stall st : stalls.values()) {
			inTown += st.entities.size();
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

	// TODO(lane A4, §6.3): market-day stalls get their wares when the social module's Dressings sets a stall up, through
	// the stall callback lane A2 adds (not landed yet; docs/requests/lwA4.md). Built counters and stalls are dressed now.

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
	private static void lay(ServerLevel level, Settlement s, int plot, List<Poi> pois, List<Goods> wares, Stall st) {
		if (wares.isEmpty()) {
			return;
		}
		List<BlockPos> tops = tops(level, pois);
		if (tops.isEmpty()) {
			return;
		}
		dev.voidmc.hearth.api.GoodsItems items = HearthServices.goodsItems();
		for (int i = 0; i < wares.size(); i++) {
			BlockPos top = tops.get(i % tops.size());
			int layer = i / tops.size();
			ItemStack stack = items.sample(wares.get(i), s.culture(), s.id() * 31 + plot * 7L + i);
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
