package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.infra.Bridge;
import dev.voidmc.hearth.build.archetype.infra.Dock;
import dev.voidmc.hearth.build.archetype.infra.Road;
import dev.voidmc.hearth.build.archetype.infra.StairStreet;
import dev.voidmc.hearth.build.archetype.infra.Terrace;
import dev.voidmc.hearth.build.archetype.infra.Walkway;

/** Infrastructure: roads, stairs, bridges, terraces, walkways, docks, built to the planner's geometry (PLANNING.md section 7). Owned by the infra architect; see docs/ARCHITECTURE.md §6.1 and docs/buildings/infra.md. */
public final class InfrastructureArchetypes {
	private InfrastructureArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Road());
		registry.register(new StairStreet());
		registry.register(new Bridge());
		registry.register(new Terrace());
		registry.register(new Walkway());
		registry.register(new Dock());
	}
}
