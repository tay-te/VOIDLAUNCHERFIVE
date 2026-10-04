package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.house.CliffDwelling;
import dev.voidmc.hearth.build.archetype.house.CourtyardHouse;
import dev.voidmc.hearth.build.archetype.house.Manor;
import dev.voidmc.hearth.build.archetype.house.Rowhouse;
import dev.voidmc.hearth.build.archetype.house.Shophouse;
import dev.voidmc.hearth.build.archetype.house.Tenement;
import dev.voidmc.hearth.build.archetype.house.TowerHouse;

/** Town homes, dense and tall: rowhouses, tenements, shophouses, manors, tower houses, cliff dwellings, courtyard houses. Owned by the townhome architect; see docs/ARCHITECTURE.md §6.1 and docs/BUILDING_GUIDE.md. */
public final class TownHomeArchetypes {
	private TownHomeArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Rowhouse());
		registry.register(new Shophouse());
		registry.register(new Tenement());
		registry.register(new TowerHouse());
		registry.register(new CliffDwelling());
		registry.register(new CourtyardHouse());
		registry.register(new Manor());
	}
}
