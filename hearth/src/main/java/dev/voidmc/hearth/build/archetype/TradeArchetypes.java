package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.storage.Granary;
import dev.voidmc.hearth.build.archetype.storage.Storehouse;
import dev.voidmc.hearth.build.archetype.storage.Warehouse;
import dev.voidmc.hearth.build.archetype.work.Bakery;
import dev.voidmc.hearth.build.archetype.work.Brewery;
import dev.voidmc.hearth.build.archetype.work.Carpentry;
import dev.voidmc.hearth.build.archetype.work.Fishery;
import dev.voidmc.hearth.build.archetype.work.HuntersLodge;
import dev.voidmc.hearth.build.archetype.work.Masonry;
import dev.voidmc.hearth.build.archetype.work.Quarry;
import dev.voidmc.hearth.build.archetype.work.Sawmill;
import dev.voidmc.hearth.build.archetype.work.Smithy;
import dev.voidmc.hearth.build.archetype.work.Tailor;
import dev.voidmc.hearth.build.archetype.work.Watermill;
import dev.voidmc.hearth.build.archetype.work.Windmill;

/** Workplaces and storage: smithy, carpentry, masonry, bakery, brewery, tailor, sawmill, quarry, fishery, mills, hunters' lodge, storehouses. Owned by the trade architect; see docs/ARCHITECTURE.md §6.1 and docs/BUILDING_GUIDE.md. */
public final class TradeArchetypes {
	private TradeArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Smithy());
		registry.register(new Storehouse());
		registry.register(new Bakery());
		registry.register(new Tailor());
		registry.register(new Carpentry());
		registry.register(new Masonry());
		registry.register(new Sawmill());
		registry.register(new Quarry());
		registry.register(new Fishery());
		registry.register(new HuntersLodge());
		registry.register(new Brewery());
		registry.register(new Windmill());
		registry.register(new Watermill());
		registry.register(new Granary());
		registry.register(new Warehouse());
	}
}
