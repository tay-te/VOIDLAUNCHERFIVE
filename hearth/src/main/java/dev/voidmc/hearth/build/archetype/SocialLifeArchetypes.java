package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.civic.Almshouse;
import dev.voidmc.hearth.build.archetype.civic.Cemetery;
import dev.voidmc.hearth.build.archetype.civic.Infirmary;
import dev.voidmc.hearth.build.archetype.civic.Orphanage;
import dev.voidmc.hearth.build.archetype.civic.Playhouse;
import dev.voidmc.hearth.build.archetype.commerce.Hostel;
import dev.voidmc.hearth.build.archetype.commerce.Tavern;
import dev.voidmc.hearth.build.archetype.commerce.ThievesDen;
import dev.voidmc.hearth.build.archetype.landscape.FestivalGround;

/** Social life: tavern, hostel, caravanserai, thieves' den, playhouse, tilt yard, infirmary, almshouse, orphanage, cemetery, festival ground. Owned by the sociallife architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class SocialLifeArchetypes {
	private SocialLifeArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Tavern());
		registry.register(new Cemetery());
		registry.register(new FestivalGround());
		registry.register(new Hostel());
		registry.register(new Infirmary());
		registry.register(new Almshouse());
		registry.register(new Orphanage());
		registry.register(new ThievesDen());
		registry.register(new Playhouse());
	}
}
