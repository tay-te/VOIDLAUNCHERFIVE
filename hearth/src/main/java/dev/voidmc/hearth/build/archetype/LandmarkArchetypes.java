package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.landmark.BellTower;
import dev.voidmc.hearth.build.archetype.landmark.Cathedral;
import dev.voidmc.hearth.build.archetype.landmark.ClockTower;
import dev.voidmc.hearth.build.archetype.landmark.GreatHall;
import dev.voidmc.hearth.build.archetype.landmark.Lighthouse;
import dev.voidmc.hearth.build.archetype.landmark.Pagoda;
import dev.voidmc.hearth.build.archetype.landmark.Temple;

/** Landmarks, one per settlement: bell tower, great hall, cathedral, temple, lighthouse, pagoda, clock tower. Owned by the landmark architect; see docs/ARCHITECTURE.md §6.1, docs/BUILDING_GUIDE.md and docs/buildings/landmark.md. */
public final class LandmarkArchetypes {
	private LandmarkArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new BellTower());
		registry.register(new ClockTower());
		registry.register(new Cathedral());
		registry.register(new Pagoda());
		registry.register(new Temple());
		registry.register(new GreatHall());
		registry.register(new Lighthouse());
	}
}
