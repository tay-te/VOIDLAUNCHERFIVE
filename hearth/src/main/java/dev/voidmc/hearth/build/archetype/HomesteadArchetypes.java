package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.farm.Field;
import dev.voidmc.hearth.build.archetype.farm.Orchard;
import dev.voidmc.hearth.build.archetype.farm.Pasture;
import dev.voidmc.hearth.build.archetype.house.Longhouse;
import dev.voidmc.hearth.build.archetype.house.RoundHouse;
import dev.voidmc.hearth.build.archetype.house.StiltHouse;
import dev.voidmc.hearth.build.archetype.work.Barn;

/** Homes and farms of the countryside and small settlements: farmhouses, longhouses, round and stilt houses, fields, orchards, pastures, paddies, barns. Owned by the homestead architect; see docs/ARCHITECTURE.md §6.1 and docs/BUILDING_GUIDE.md. */
public final class HomesteadArchetypes {
	private HomesteadArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Field());
		registry.register(new Orchard());
		registry.register(new Pasture());
		registry.register(new Barn());
		registry.register(new Longhouse());
		registry.register(new RoundHouse());
		registry.register(new StiltHouse());
	}
}
