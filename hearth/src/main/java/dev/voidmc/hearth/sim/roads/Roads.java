package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.Claims;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The roads between settlements: network, routes, traffic and bandits (§1).
 * Lane A1 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 *
 * <p>Per level: the {@link RoadWorld} network (made with the level's {@link SimWorld}), the {@link LiveRoads} built
 * since day 0, the {@link RoadApplier} that writes roads into loaded chunks, and the {@link Traffic} on them. Their tick
 * work is timed under {@code roads} and {@code traffic} in {@code /hearth sim stats}.
 */
public final class Roads {
	private static RoadsConfig config = RoadsConfig.DEFAULTS;
	private static final Map<SimWorld, State> STATES = new IdentityHashMap<>();
	private static final LivingWorldStats.Timer ROADS = LivingWorldStats.timer("roads");
	private static final LivingWorldStats.Timer TRAFFIC = LivingWorldStats.timer("traffic");

	private Roads() {
	}

	public static RoadsConfig config() {
		return config;
	}

	/** Replaces the settings at runtime (dev tools, game tests). */
	public static void setConfig(RoadsConfig newConfig) {
		config = newConfig;
	}

	/** One level's roads. */
	public record State(SimWorld world, RoadWorld net, LiveRoads live, RoadApplier applier, Traffic traffic) {
	}

	/** Makes a level's network (from the {@link SimWorld} constructor, once its atlas exists). */
	public static RoadWorld create(SimWorld world) {
		Map<Culture, Identifier> styles = new EnumMap<>(Culture.class);
		return new RoadWorld(world.level().dimension(), world.atlas(), world.probe(), world.probe().rough(), c -> {
			synchronized (styles) {
				return styles.computeIfAbsent(c, k -> HearthServices.hasBlueprints() ? HearthServices.blueprints().style(k) : Hearth.id(k.getSerializedName()));
			}
		}, config, world.spatial()::isPlanned);
	}

	/** A level's roads (made on first use, on the server thread). */
	public static @Nullable State state(@Nullable SimWorld world) {
		if (world == null || world.roadWorld() == null) {
			return null;
		}
		synchronized (STATES) {
			State s = STATES.get(world);
			if (s == null) {
				RoadWorld net = world.roadWorld();
				LiveRoads live = new LiveRoads(world, net);
				RoadApplier applier = new RoadApplier(world, net, live);
				Traffic traffic = new Traffic(world, net);
				s = new State(world, net, live, applier, traffic);
				STATES.put(world, s);
			}
			return s;
		}
	}

	/** Every level's roads now. */
	public static List<State> states() {
		synchronized (STATES) {
			return new ArrayList<>(STATES.values());
		}
	}

	/**
	 * The historic road plots world generation writes into a chunk (any thread): those of roads built in history over
	 * it, in order along each road. Never throws: a road that cannot be worked out is left for the runtime applier.
	 */
	public static List<RoadWriter.Job> historicJobs(SimWorld world, int chunkX, int chunkZ) {
		RoadWorld roads = world.roadWorld();
		if (roads == null || !roads.enabled()) {
			return List.of();
		}
		try {
			RoadWorld.Region r = roads.region(chunkX >> (Atlas.REGION_BITS - 4), chunkZ >> (Atlas.REGION_BITS - 4));
			long[] refs = r.byChunk().get(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ));
			if (refs == null) {
				return List.of();
			}
			List<RoadWriter.Job> out = new ArrayList<>(refs.length);
			for (long ref : refs) {
				Way w = r.ways().get((int) (ref >>> 32));
				if (w.edge().historic()) {
					out.add(new RoadWriter.Job(w, (int) ref, Long.MAX_VALUE));
				}
			}
			out.sort(java.util.Comparator.comparingLong((RoadWriter.Job j) -> j.way().id()).thenComparingInt(RoadWriter.Job::index));
			return out;
		} catch (RuntimeException e) {
			Hearth.LOG.error("hearth roads: the roads over chunk {},{} could not be worked out", chunkX, chunkZ, e);
			return List.of();
		}
	}

	/** Called once from {@code SimModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		config = HearthConfig.load("roads", RoadsConfig.CODEC, RoadsConfig.DEFAULTS);
		// saved state registered before any world loads
		java.util.Objects.requireNonNull(RoadBook.KEY);
		java.util.Objects.requireNonNull(Traffic.KEY);
		// roads' strips are planned ground: trees, boulders and Expanse's features keep off them, towns do not build there
		Claims.register((world, x, z, now) -> {
			RoadWorld net = world.roadWorld();
			return net != null && net.isPlanned(x, z, now);
		});
		HearthServices.setTravel(Traffic.SERVICE);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			State s = state(SimServer.world(level));
			if (s != null) {
				s.applier().onChunkLoad(chunk);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SimServer sim = SimServer.current();
			if (sim == null) {
				return;
			}
			for (SimWorld w : sim.worlds()) {
				State s = state(w);
				if (s == null || !s.net().enabled()) {
					continue;
				}
				long t0 = System.nanoTime();
				try {
					s.live().tick();
					s.applier().tick();
				} catch (RuntimeException e) {
					Hearth.LOG.error("hearth roads: the roads' tick failed in {}", w.level().dimension().identifier(), e);
				}
				ROADS.add(System.nanoTime() - t0);
				long t1 = System.nanoTime();
				try {
					s.traffic().tick();
				} catch (RuntimeException e) {
					Hearth.LOG.error("hearth roads: the traffic's tick failed in {}", w.level().dimension().identifier(), e);
				}
				TRAFFIC.add(System.nanoTime() - t1);
			}
		});
		SettlementEvents.DAY_PASSED.register((world, settlement, day) -> {
			if (world instanceof SimWorld w && settlement instanceof SimSettlement s) {
				State st = state(w);
				if (st != null && st.net().enabled()) {
					st.live().dayPassed(s, day);
					st.traffic().dayPassed(s, day);
				}
			}
		});
		SettlementEvents.CARAVAN.register((world, from, to, kind, id, cargo) -> {
			if (world instanceof SimWorld w) {
				State st = state(w);
				if (st != null) {
					st.traffic().onCaravan(from, to, kind, id, cargo);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			synchronized (STATES) {
				STATES.clear();
			}
		});
		// caravans go by road and meet the road's bandits; news goes along the roads, carried by messengers
		dev.voidmc.hearth.sim.trade.Caravans.setRoads(new dev.voidmc.hearth.sim.trade.Caravans.Roads() {
			@Override
			public long arrival(SimSettlement from, SimSettlement to, long now) {
				State st = stateOf(from);
				return st == null ? 0 : st.traffic().caravanArrival(from, to, now);
			}

			@Override
			public double risk(SimSettlement from, SimSettlement to, int guards) {
				State st = stateOf(from);
				return st == null ? Double.NaN : st.traffic().caravanRisk(from, to, guards);
			}
		});
		dev.voidmc.hearth.standing.logic.GossipRoutes.setRoads((a, b) -> {
			for (State st : states()) {
				if (st.world().settlement(a) != null || st.net().historicNode(a) != null) {
					dev.voidmc.hearth.api.RoadNetwork.RoadView v = st.net().between(a, b);
					if (v != null) {
						return v.length();
					}
				}
			}
			return -1;
		});
		dev.voidmc.hearth.standing.logic.GossipRoutes.setMessengers((from, to, delay) -> {
			for (State st : states()) {
				if (st.world().settlement(from) != null) {
					st.traffic().messenger(from, to, delay);
					return;
				}
			}
		});
		dev.voidmc.hearth.quest.service.LivingQuests.setRobbedAt((from, to, caravanId) -> {
			for (State st : states()) {
				net.minecraft.core.BlockPos camp = st.traffic().robbedAt(caravanId);
				if (camp != null) {
					return camp;
				}
			}
			return null;
		});
		SettlementEvents.FOUNDED.register((world, settlement) -> {
			if (world instanceof SimWorld w && settlement instanceof SimSettlement s && !Atlas.isHistoric(s.id()) && s.clockState().parent() != 0) {
				State st = state(w);
				SimSettlement parent = w.settlement(s.clockState().parent());
				if (st != null && parent != null && st.net().enabled()) {
					st.traffic().settlers(parent, s);
				}
			}
		});
		RoadCommands.register();
	}

	/** The roads of the level a settlement is in. */
	static @Nullable State stateOf(SimSettlement s) {
		for (State st : states()) {
			if (st.world().settlement(s.id()) == s) {
				return st;
			}
		}
		return null;
	}
}
