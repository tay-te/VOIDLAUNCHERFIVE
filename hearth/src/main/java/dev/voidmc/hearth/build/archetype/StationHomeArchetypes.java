package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.house.Croft;
import dev.voidmc.hearth.build.archetype.house.HermitHut;
import dev.voidmc.hearth.build.archetype.house.Hovel;
import dev.voidmc.hearth.build.archetype.house.MerchantHouse;
import dev.voidmc.hearth.build.archetype.house.Shack;

/** Homes by station: hovel, shack, croft, merchant's house, reeve's house, rectory, scribe's house, hermit's hut. Owned by the station architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class StationHomeArchetypes {
	private StationHomeArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Hovel());
		registry.register(new HermitHut());
		registry.register(new Shack());
		registry.register(new Croft());
		registry.register(new MerchantHouse());
	}
}
