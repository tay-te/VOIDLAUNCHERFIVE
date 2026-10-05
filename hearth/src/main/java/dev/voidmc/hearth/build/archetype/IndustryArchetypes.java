package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.commerce.CaravanYard;
import dev.voidmc.hearth.build.archetype.work.BrickKiln;
import dev.voidmc.hearth.build.archetype.work.CharcoalKiln;
import dev.voidmc.hearth.build.archetype.work.Glassworks;
import dev.voidmc.hearth.build.archetype.work.LoggingCamp;
import dev.voidmc.hearth.build.archetype.work.SaltWorks;
import dev.voidmc.hearth.build.archetype.work.Shipyard;
import dev.voidmc.hearth.build.archetype.work.Smelter;

/** Heavy workshops of the chains: logging camp, charcoal kiln, brick kiln, glassworks, smelter, salt works, shipyard, caravan yard. Owned by the industry architect (stage B); see docs/LIVING_WORLD.md §8.5 and docs/buildings/industry.md. */
public final class IndustryArchetypes {
	private IndustryArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new LoggingCamp());
		registry.register(new CharcoalKiln());
		registry.register(new Smelter());
		registry.register(new BrickKiln());
		registry.register(new Glassworks());
		registry.register(new SaltWorks());
		registry.register(new Shipyard());
		registry.register(new CaravanYard());
	}
}
