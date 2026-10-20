package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.sim.apply.ChunkLedgers;
import dev.voidmc.hearth.sim.command.SimCommands;
import dev.voidmc.hearth.sim.dev.DevHarness;
import dev.voidmc.hearth.sim.dev.DevCatalogue;
import dev.voidmc.hearth.sim.dev.PlaceholderPlanner;
import dev.voidmc.hearth.sim.gen.SettlementFeature;
import dev.voidmc.hearth.sim.gen.WorldgenGuard;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.net.ArrivalPayload;
import dev.voidmc.hearth.sim.settlement.PlainResidents;
import dev.voidmc.hearth.sim.terrain.CultureMap;
import dev.voidmc.hearth.sim.world.SimWorld;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.jspecify.annotations.Nullable;

/**
 * The sim module: settlement records, persistence, the simulation clock, the atlas and its history, world generation,
 * the chunk applier, embodiment, commands and the dev harness. See docs/SIM.md.
 */
public final class SimModule {
	private static @Nullable SimRules rules;

	private SimModule() {
	}

	public static SimRules rules() {
		SimRules r = rules;
		if (r == null) {
			r = rules = new SimRules(HearthConfig.load("sim", SimConfig.CODEC, SimConfig.DEFAULTS));
		}
		return r;
	}

	/** The resource economy's data (chains, gathering, sites, the land of each biome) from the server's data packs. */
	static void loadData(net.minecraft.server.packs.resources.ResourceManager resources) {
		dev.voidmc.hearth.sim.chains.SimData data = dev.voidmc.hearth.sim.chains.SimData.load(resources);
		dev.voidmc.hearth.sim.chains.SimData.setCurrent(data);
		dev.voidmc.hearth.sim.land.BiomeResources.reset(data.land());
	}

	public static void init() {
		SimRules r = rules();
		HearthServices.setWorlds(SimServer::world);
		HearthServices.setCulture(new CultureMap());
		// the plain default; the npc module replaces it with folk who have personalities
		HearthServices.setResidents(PlainResidents.INSTANCE);
		ChunkLedgers.init();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("expanse")) {
			dev.voidmc.hearth.sim.terrain.ExpanseClaims.register();   // Expanse's area features stay off planned land
		}
		// the land's and the roads' saved state: keys registered before any world loads
		java.util.Objects.requireNonNull(dev.voidmc.hearth.sim.land.LandState.KEY);
		java.util.Objects.requireNonNull(dev.voidmc.hearth.sim.trade.Caravans.KEY);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Hearth.id("settlement"), SettlementFeature.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
			ResourceKey.create(Registries.PLACED_FEATURE, Hearth.id("settlement")));
		PayloadTypeRegistry.clientboundPlay().register(ArrivalPayload.TYPE, ArrivalPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			// before any level exists: so world generation (spawn chunks included) can write history
			if (!HearthServices.hasBlueprints()) {
				// only before the build module exists: the planner's whole catalogue as walkable stand-ins
				DevCatalogue dev = new DevCatalogue(null);
				Hearth.LOG.warn("hearth: no BlueprintService registered; using stand-in buildings for all {} catalogue archetypes", dev.size());
				HearthServices.setBlueprints(dev);
			} else if (Boolean.getBoolean("hearth.dev.fillCatalogue") && !(HearthServices.blueprints() instanceof DevCatalogue)) {
				// dev switch: the build module's archetypes where it has them, stand-ins for the rest of the catalogue
				DevCatalogue dev = new DevCatalogue(HearthServices.blueprints());
				Hearth.LOG.warn("hearth: hearth.dev.fillCatalogue: {} registered archetypes, stand-ins for the other {} of the catalogue", dev.realCount(),
					dev.size() - dev.realCount());
				HearthServices.setBlueprints(dev);
			}
			if (!HearthServices.hasPlanner()) {
				Hearth.LOG.warn("hearth: no SettlementPlanner registered; using the sim module's placeholder planner");
				HearthServices.setPlanner(new PlaceholderPlanner());
			}
			WorldgenGuard.reset();
			loadData(server.getResourceManager());
			SimServer.start(server, r);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				loadData(server.getResourceManager());
			}
		});
		ServerLevelEvents.LOAD.register((server, level) -> {
			SimServer s = SimServer.current();
			if (s != null && s.wants(level)) {
				s.addWorld(new SimWorld(level, s));
			}
		});
		ServerLevelEvents.UNLOAD.register((server, level) -> {
			SimServer s = SimServer.current();
			if (s != null) {
				s.removeWorld(level);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SimServer s = SimServer.current();
			if (s != null) {
				s.tick();
			}
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			SimWorld w = SimServer.world(level);
			if (w != null) {
				w.onChunkLoad(chunk);
			}
		});
		ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level, chunk, from, to) -> {
			// a chunk becomes reachable (getChunkNow) only after its load event: one the applier looked for too early, or
			// a border chunk promoted later, is queued again now
			if (to.isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL) && !from.isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) {
				SimWorld w = SimServer.world(level);
				if (w != null) {
					w.onChunkReachable(chunk);
				}
			}
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			SimServer s = SimServer.current();
			if (s != null) {
				s.save(flush);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SimServer.stop();
			WorldgenGuard.reset();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SimCommands.register(dispatcher));
		DevHarness.install();

		// the living world, part 2 (docs/LIVING_WORLD_2.md §11.2): each lane fills in its own entry point
		java.util.Objects.requireNonNull(dev.voidmc.hearth.sim.settlement.AwayBook.KEY); // residents away (§0.2)
		dev.voidmc.hearth.sim.roads.Roads.init();      // A1 roads
		dev.voidmc.hearth.sim.lives.Lives.init();      // B2 lives
		dev.voidmc.hearth.sim.wild.Wild.init();        // B3 posts in the wild
	}
}
