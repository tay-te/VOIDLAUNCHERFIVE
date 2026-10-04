package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.repair.command.RepairCommands;
import dev.voidmc.hearth.repair.command.SelfTest;
import dev.voidmc.hearth.repair.folk.BrigadeBehaviour;
import dev.voidmc.hearth.repair.folk.InspectBehaviour;
import dev.voidmc.hearth.repair.folk.MendBehaviour;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The repair module: damage tracking against blueprints, repair queues, builders mending, inspection rounds, fires and
 * their aftermath, vandalism reported to the law (docs/LIVING_WORLD.md §2, docs/REPAIRS.md). Registers the
 * {@link dev.voidmc.hearth.api.RepairService} and three folk behaviours (mending, inspecting, the bucket brigade).
 */
public final class RepairModule {
	public static final Logger LOG = LoggerFactory.getLogger("hearth/repair");

	/** Each settlement's repair queue (damaged blocks, the abstract labour clock, scaffolding up for repairs). */
	public static final HearthDataKey<RepairQueue> QUEUE = HearthDataKey.register(Hearth.id("repair/queue"), HearthDataKey.Scope.SETTLEMENT,
		RepairQueue.CODEC, RepairQueue::new);

	public static final MendBehaviour MEND = new MendBehaviour();
	public static final InspectBehaviour INSPECT = new InspectBehaviour();
	public static final BrigadeBehaviour BRIGADE = new BrigadeBehaviour();

	private static volatile RepairConfig config = RepairConfig.DEFAULTS;
	private static boolean behavioursRegistered;

	private RepairModule() {
	}

	public static void init() {
		config = HearthConfig.load("repair", RepairConfig.CODEC, RepairConfig.DEFAULTS);
		HearthServices.setRepairs(new RepairServiceImpl());
		DialogueProvider.register(new RepairDialogue());
		// the NPC module registers its service in its own init, before ours; if it has not, try again at server start
		registerBehaviours();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			registerBehaviours();
			RepairRuntime.start(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RepairRuntime rt = RepairRuntime.current();
			if (rt != null) {
				rt.scanAll();
			}
			SelfTest.maybeStart(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RepairRuntime rt = RepairRuntime.current();
			if (rt != null) {
				rt.vandalism.flushAll();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RepairRuntime.stop());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			RepairRuntime rt = RepairRuntime.current();
			if (rt != null) {
				try {
					rt.tick();
					SelfTest.tick(rt);
				} catch (RuntimeException e) {
					LOG.error("repair tick failed", e);
				}
			}
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, fresh) -> {
			RepairRuntime rt = RepairRuntime.current();
			if (rt != null) {
				rt.chunkLoaded(level, chunk.getPos().x(), chunk.getPos().z());
			}
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			RepairRuntime rt = RepairRuntime.current();
			if (rt != null) {
				rt.chunkUnloaded(level, chunk.getPos().x(), chunk.getPos().z());
			}
		});

		SettlementEvents.PLOT_ABANDONED.register((world, settlement, plot, why) -> {
			RepairQueue q = settlement.data(QUEUE);
			if (q.removePlot(plot.id()) > 0) {
				settlement.markDirty();
			}
			RepairRuntime rt = RepairRuntime.current();
			if (rt != null) {
				rt.locator.invalidateGrid(settlement.id());
			}
		});
		SettlementEvents.BUILDING_COMPLETED.register((world, settlement, plot) -> {
			RepairRuntime rt = RepairRuntime.current();
			if (rt != null) {
				rt.locator.invalidateGrid(settlement.id());
			}
		});
		SettlementEvents.EMBODIMENT_CHANGED.register((world, settlement, embodied) -> {
			RepairQueue q = settlement.data(QUEUE);
			if (embodied && q.scaffoldCount() > 0) {
				// scaffolding of builders folded away mid-repair (or left by a crash): nobody is on it now
				Scaffolds.clearAll(world.level(), settlement, q);
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> RepairCommands.register(dispatcher));
		SelfTest.listen();
	}

	private static synchronized void registerBehaviours() {
		NpcService npc = HearthServices.npc();
		if (behavioursRegistered || npc == null) {
			return;
		}
		npc.registerBehaviour(MEND);
		npc.registerBehaviour(INSPECT);
		npc.registerBehaviour(BRIGADE);
		behavioursRegistered = true;
	}

	public static RepairConfig config() {
		return config;
	}

	/** Replaces the settings (commands and tests). */
	public static void setConfig(RepairConfig c) {
		config = c;
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null) {
			rt.locator.configure(c.tracking().indexMaxEntries(), c.tracking().gridTtlTicks());
		}
	}

	public static Identifier id(String name) {
		return Hearth.id("repair/" + name);
	}

	/** Whether the folk behaviours are registered with an NPC module. */
	public static boolean behavioursRegistered() {
		return behavioursRegistered;
	}
}
