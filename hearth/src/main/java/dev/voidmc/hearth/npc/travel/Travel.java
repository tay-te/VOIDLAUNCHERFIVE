package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.npc.caravan.Caravans;
import dev.voidmc.hearth.npc.travel.cart.Carts;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.roads.Roads;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * Travellers on the roads: parties, strangers, bandits and carts (§1); settings in {@link dev.voidmc.hearth.sim.roads.Roads#config()}.
 * Lane A1 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 *
 * <p>The sim's roads keep the books (journeys, bands, roads being built); this puts them on the road near players
 * ({@link Parties}), and takes over the old caravan parties: a sim caravan is a journey like any other.
 */
public final class Travel {
	private Travel() {
	}

	/** Called once from {@code NpcModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		Carts.init();
		// "pay toward the road" at the reeve or the steward
		dev.voidmc.hearth.api.DialogueProvider.register(new RoadTalk());
		// the caravans of the sim are journeys now: the old npc caravan parties stand down while the roads are on
		Caravans.setStandDown(() -> Roads.config().roads().enabled());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SimServer.current() != null) {
				Parties.tick(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && entity.entityTags().contains(Caravans.TAG)) {
				Parties.beastDied(level, entity, source);
			}
		});
		ServerLevelEvents.LOAD.register((server, level) -> server.execute(() -> Parties.sweepCamps(level)));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Parties.foldAll());
		dev.voidmc.hearth.sim.roads.Traffic.setEmbodied(Parties::embodied);
	}
}
