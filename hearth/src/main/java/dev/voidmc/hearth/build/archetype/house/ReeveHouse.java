package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:house/reeve_house} (docs/LIVING_WORLD.md §8.1): the house of the reeve (the village's headman, the
 * lord's man who keeps the rolls and gathers the dues) by the square. A detached house of two storeys and a garret, its
 * broad front to the square (or, in Bohemia, the rendered and boarded gable of the {@code rychta}), the door in the
 * middle under its hood, often a balcony over it from which he speaks to the village; before it a paved forecourt with
 * the notice board where the square reads it, a bench for those who wait, pots by the door. Inside, the office at the
 * front where folk are received: the reeve's desk facing the door with the rolls on shelves behind (JOB_SITE for the
 * REEVE), the strongbox of the dues and the records (STRONGBOX); behind a partition the kitchen with the hearth and the
 * stair; the family's chambers above.
 *
 * <p>Params: {@code station}, {@code household} (shared), {@code roof} (RoofForm name), {@code variant} (int).
 */
public final class ReeveHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/reeve_house"), ArchetypeCategory.HOUSING, Set.of(JobType.REEVE), 4,
		10, 13, 13, 16, 13, 18, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 2, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		raise(d);
		dev.voidmc.hearth.build.kit.townhome.Settle.finish(d);
	}

	/** The house as drawn, before its last walk-round ({@link dev.voidmc.hearth.build.kit.townhome.Settle}). */
	private void raise(Draft d) {
		SeatHouses.draw(d, SeatHouses.Office.REEVE, INFO.beds());
	}
}
