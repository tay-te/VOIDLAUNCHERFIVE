package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.npc.crowd.CrowdConfig;
import dev.voidmc.hearth.npc.work.field.FieldWorkConfig;
import dev.voidmc.hearth.sim.lives.LivesConfig;
import dev.voidmc.hearth.sim.roads.RoadsConfig;
import dev.voidmc.hearth.sim.wild.WildConfig;
import dev.voidmc.hearth.social.events.EventsConfig;
import dev.voidmc.hearth.social.rhythm.RhythmConfig;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Lane 0's shared pieces (docs/LIVING_WORLD_2.md §11.2, §11.4): caps, configs, the api's defaults. */
class LivingWorldSharedTest {
	@Test
	void capsShrinkAsTheServerSlows() {
		assertEquals(1.0, LoadCaps.scale(10, 40));
		assertEquals(1.0, LoadCaps.scale(24, 40));
		assertEquals(0.5, LoadCaps.scale(32, 40), 1e-9);
		assertEquals(0.0, LoadCaps.scale(40, 40));
		assertEquals(120, LoadCaps.cap(120, 0, 20, 40));
		assertEquals(60, LoadCaps.cap(120, 0, 32, 40));
		assertEquals(4, LoadCaps.cap(120, 4, 45, 40), "never below the floor");
		assertTrue(LoadCaps.admitting(24, 40));
		assertFalse(LoadCaps.admitting(25, 40));
		assertFalse(LoadCaps.shedding(40, 40));
		assertTrue(LoadCaps.shedding(41, 40));
	}

	private static <T> void roundTrips(Codec<T> codec, T defaults) {
		var json = codec.encodeStart(JsonOps.INSTANCE, defaults).getOrThrow();
		assertEquals(defaults, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
		assertEquals(defaults, codec.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow(), "an empty file is the defaults");
	}

	@Test
	void everyLaneConfigRoundTripsAndKeepsTheDesignDefaults() {
		roundTrips(RoadsConfig.CODEC, RoadsConfig.DEFAULTS);
		roundTrips(RhythmConfig.CODEC, RhythmConfig.DEFAULTS);
		roundTrips(CrowdConfig.CODEC, CrowdConfig.DEFAULTS);
		roundTrips(FieldWorkConfig.CODEC, FieldWorkConfig.DEFAULTS);
		roundTrips(EventsConfig.CODEC, EventsConfig.DEFAULTS);
		roundTrips(LivesConfig.CODEC, LivesConfig.DEFAULTS);
		roundTrips(WildConfig.CODEC, WildConfig.DEFAULTS);
		roundTrips(PlaceConfig.CODEC, PlaceConfig.DEFAULTS);
		// §9.2 caps and §13's decisions
		assertEquals(16, RoadsConfig.DEFAULTS.parties().maxParties());
		assertEquals(120, RoadsConfig.DEFAULTS.parties().maxEntities());
		assertEquals(0.05, RoadsConfig.DEFAULTS.bandits().killChance());
		assertEquals(96, FieldWorkConfig.DEFAULTS.flocks().maxPerServer());
		assertEquals(24, FieldWorkConfig.DEFAULTS.boats().maxPerServer());
		assertEquals(1500, FieldWorkConfig.DEFAULTS.displays().maxPerServer());
		assertEquals(8, RhythmConfig.DEFAULTS.lights().maxPerTown());
		assertEquals(2, PlaceConfig.DEFAULTS.companions().perPlayer());
		assertEquals(64, PlaceConfig.DEFAULTS.companions().maxPerServer());
		assertTrue(PlaceConfig.DEFAULTS.commissions().knighthood());
		assertEquals("auto", EventsConfig.DEFAULTS.events().liveFires());
		assertEquals("staged", EventsConfig.DEFAULTS.events().executions());
		assertEquals(3, WildConfig.DEFAULTS.wild().perRegion());
	}

	@Test
	void theNewTradesHaveTheirStations() {
		assertEquals(Station.POOR, Station.of(JobType.BANDIT));
		assertEquals(Station.CLERGY, Station.of(JobType.MONK));
		assertEquals(Station.OFFICIAL, Station.of(JobType.BAILIFF));
		assertEquals(Station.POOR, Station.of(JobType.SERVANT));
		for (JobType j : JobType.values()) {
			Station.of(j);
		}
	}

	@Test
	void theApiDefaultsAreNeutral() {
		assertEquals(SettlementCalendar.NONE, HearthServices.calendar());
		assertEquals(-1, RoadNetwork.NONE.roadDistance(1, 2));
		var road = new RoadNetwork.RoadView(1, 1, 2, RoadNetwork.RoadClass.TRACK, new int[] {0, 64, 0, 10, 64, 0, 10, 64, 10}, 1, 20);
		assertEquals(new BlockPos(0, 64, 0), road.at(-5));
		assertEquals(new BlockPos(5, 64, 0), road.at(5));
		assertEquals(new BlockPos(10, 64, 5), road.at(15));
		assertEquals(new BlockPos(10, 64, 10), road.at(99));
	}
}
