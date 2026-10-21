package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.resources.Identifier;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.terrain.Ground;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.jspecify.annotations.Nullable;

/**
 * The plan module: settlement layouts, roads, plots and growth decisions (ARCHITECTURE.md §5–7, docs/PLANNING.md).
 * Registers the planner service, its per-settlement state key {@code hearth:plan/state}, its config
 * ({@code config/hearth/plan.json}) and the {@code /hearth plan} debug commands.
 */
public final class PlanModule {
	/** The planner's per-settlement state. Registered when this class loads (mod initialisation). */
	public static final HearthDataKey<PlanState> STATE = HearthDataKey.register(Hearth.id("plan/state"), HearthDataKey.Scope.SETTLEMENT,
		PlanState.CODEC, PlanState::new);

	private static volatile HearthPlanner planner;

	private PlanModule() {
	}

	public static void init() {
		PlanConfig config = HearthConfig.load("plan", PlanConfig.CODEC, PlanConfig.DEFAULTS);
		Ground.setStoreCapacity(config.cacheTiles());
		planner = new HearthPlanner(STATE, config);
		HearthServices.setPlanner(planner);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> PlanCommands.register(dispatcher));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> serverThread = server.getRunningThread());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> serverThread = null);
		// after the sim has made the level's Hearth world (it listens in the default phase), pair its probes for the planner
		Identifier late = Hearth.id("plan/after_sim");
		ServerLevelEvents.LOAD.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		ServerLevelEvents.LOAD.register(late, (server, level) -> {
			HearthWorld world = HearthServices.world(level);
			if (world != null) {
				registerUnderground(world.terrain(), world.underground());
			}
		});

		// the living world, part 2 (docs/LIVING_WORLD_2.md §11.2): each lane fills in its own entry point
		dev.voidmc.hearth.plan.wild.WildPlanner.init();       // B3 posts in the wild
	}

	private static volatile @Nullable Thread serverThread;

	/** Underground probes by the terrain probe of the same world (the planner only ever sees the terrain probe). */
	private static final java.util.Map<TerrainProbe, UndergroundProbe> UNDERGROUND = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/**
	 * The open spaces under the land a terrain probe describes (Expanse realms), or null: the probe itself if it is one,
	 * else the one registered for it — by this module from each world's {@link HearthWorld#underground()} as its level
	 * loads, or by tests and previews with {@link #registerUnderground}. Thread-safe.
	 */
	public static @Nullable UndergroundProbe underground(TerrainProbe terrain) {
		if (terrain instanceof UndergroundProbe u) {
			return u;
		}
		UndergroundProbe own = terrain.underground();
		return own != null ? own : UNDERGROUND.get(terrain);
	}

	/** Pairs a terrain probe with the underground probe of the same world (tests, previews, a sim that wires its own). */
	public static void registerUnderground(TerrainProbe terrain, @Nullable UndergroundProbe underground) {
		if (underground == null) {
			UNDERGROUND.remove(terrain);
		} else {
			UNDERGROUND.put(terrain, underground);
		}
	}

	/** Whether this is the running server's main thread (live planning, time-limited) rather than a history's worker. */
	public static boolean isServerThread() {
		return Thread.currentThread() == serverThread;
	}

	/**
	 * Dev worlds: {@code -Dhearth.dev.plan.layout=walled} (any {@link LayoutType} name) forces every settlement founded in
	 * this run to that layout, on whichever thread founds it (live founding, histories on the atlas workers). Null when
	 * unset, as in every normal run.
	 */
	public static @Nullable LayoutType devLayout() {
		return DEV_LAYOUT;
	}

	private static final @Nullable LayoutType DEV_LAYOUT = devLayoutProperty();

	private static @Nullable LayoutType devLayoutProperty() {
		String name = System.getProperty("hearth.dev.plan.layout");
		if (name == null || name.isBlank()) {
			return null;
		}
		for (LayoutType t : LayoutType.values()) {
			if (t.getSerializedName().equals(name.trim())) {
				Hearth.LOG.warn("hearth: every settlement founded in this run is laid out as '{}' (hearth.dev.plan.layout)", name);
				return t;
			}
		}
		Hearth.LOG.warn("hearth: unknown layout '{}' in hearth.dev.plan.layout; ignored", name);
		return null;
	}

	public static HearthPlanner planner() {
		return planner;
	}
}
