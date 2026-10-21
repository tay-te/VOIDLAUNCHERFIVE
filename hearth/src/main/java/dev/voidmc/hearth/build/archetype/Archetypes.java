package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.house.Cottage;
import dev.voidmc.hearth.build.archetype.house.Hut;
import dev.voidmc.hearth.build.archetype.house.Townhouse;
import dev.voidmc.hearth.build.archetype.landscape.Well;
import dev.voidmc.hearth.build.archetype.work.BuildersYard;
import dev.voidmc.hearth.build.archetype.commerce.MarketStall;

/** Registers every archetype the build module ships. */
public final class Archetypes {
	private Archetypes() {
	}

	public static void registerAll(ArchetypeRegistry registry) {
		registry.register(new Hut());
		registry.register(new Cottage());
		registry.register(new Townhouse());
		registry.register(new BuildersYard());
		registry.register(new MarketStall());
		registry.register(new Well());
		// the catalogue, one family per architect (phase 2)
		HomesteadArchetypes.register(registry);
		TownHomeArchetypes.register(registry);
		TradeArchetypes.register(registry);
		CivicArchetypes.register(registry);
		LandmarkArchetypes.register(registry);
		InfrastructureArchetypes.register(registry);
		LandscapeArchetypes.register(registry);
		// the living world's catalogue (stage B, LIVING_WORLD.md §8.5)
		StationHomeArchetypes.register(registry);
		NobleArchetypes.register(registry);
		LearningArchetypes.register(registry);
		LawHouseArchetypes.register(registry);
		SocialLifeArchetypes.register(registry);
		IndustryArchetypes.register(registry);
		CraftArchetypes.register(registry);
		ExtractionArchetypes.register(registry);
		DeepArchetypes.register(registry);
		// the small places in the wild (docs/LIVING_WORLD_2.md §7, lane B3)
		dev.voidmc.hearth.build.archetype.wild.WildArchetypes.register(registry);
	}
}
