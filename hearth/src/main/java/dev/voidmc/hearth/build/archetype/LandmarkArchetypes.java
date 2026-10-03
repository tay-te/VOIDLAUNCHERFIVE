package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.landmark.BellTower;
import dev.voidmc.hearth.build.archetype.landmark.ClockTower;

/** Landmarks, one per settlement: bell tower, great hall, cathedral, temple, lighthouse, pagoda, clock tower. Owned by the landmark architect; see docs/ARCHITECTURE.md §6.1, docs/BUILDING_GUIDE.md and docs/buildings/landmark.md. */
public final class LandmarkArchetypes {
	private LandmarkArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new BellTower());
		registry.register(new ClockTower());
	}
}
