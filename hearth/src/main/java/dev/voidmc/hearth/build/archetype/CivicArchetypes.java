package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.civic.Bathhouse;
import dev.voidmc.hearth.build.archetype.civic.Chapel;
import dev.voidmc.hearth.build.archetype.civic.Guardhouse;
import dev.voidmc.hearth.build.archetype.civic.Library;
import dev.voidmc.hearth.build.archetype.civic.School;
import dev.voidmc.hearth.build.archetype.civic.TownHall;
import dev.voidmc.hearth.build.archetype.commerce.Bank;
import dev.voidmc.hearth.build.archetype.commerce.Inn;
import dev.voidmc.hearth.build.archetype.commerce.MarketHall;

/** Civic and commercial buildings: market hall, inn, bank, chapel, town hall, library, school, bathhouse, guardhouse. Owned by the civic architect; see docs/ARCHITECTURE.md §6.1 and docs/BUILDING_GUIDE.md. */
public final class CivicArchetypes {
	private CivicArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Chapel());
		registry.register(new MarketHall());
		registry.register(new Inn());
		registry.register(new TownHall());
		registry.register(new Bank());
		registry.register(new Library());
		registry.register(new School());
		registry.register(new Bathhouse());
		registry.register(new Guardhouse());
	}
}
