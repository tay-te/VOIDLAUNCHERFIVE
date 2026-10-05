package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.deep.DeepDwelling;
import dev.voidmc.hearth.build.archetype.deep.DeepForge;
import dev.voidmc.hearth.build.archetype.deep.DeepHall;
import dev.voidmc.hearth.build.archetype.deep.DeepLift;
import dev.voidmc.hearth.build.archetype.deep.DeepMarket;
import dev.voidmc.hearth.build.archetype.deep.GlowGarden;

/** Underground towns in the realms: hall, dwelling, market, forge, glow garden, lift. Owned by the deep architect (stage B); see docs/LIVING_WORLD.md §8.5 and docs/buildings/deep.md. */
public final class DeepArchetypes {
	private DeepArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new DeepHall());
		registry.register(new DeepDwelling());
		registry.register(new DeepMarket());
		registry.register(new DeepForge());
		registry.register(new GlowGarden());
		registry.register(new DeepLift());
	}
}
