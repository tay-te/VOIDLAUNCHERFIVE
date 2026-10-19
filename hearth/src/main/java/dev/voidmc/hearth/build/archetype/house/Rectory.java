package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:house/rectory} (docs/LIVING_WORLD.md §8.1): the priest's house by the chapel. A detached house of two
 * storeys and a garret behind a front garden of flowers with a little wayside shrine by the gate; behind it the
 * priest's garden: beds of herbs and roots, a fruit tree, the bees by the back fence, a bench in the sun. Inside, the
 * study at the front (the lectern with the breviary open on it where the window's light falls, the desk with its
 * candles, books up the walls, the chest of the plate and the tithe), the kitchen behind with the hearth and the stair;
 * the priest's chamber and the housekeeper's above.
 *
 * <p>Params: {@code station}, {@code household} (shared), {@code roof} (RoofForm name), {@code variant} (int).
 */
public final class Rectory implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/rectory"), ArchetypeCategory.HOUSING, Set.of(), 3, 9, 12, 14, 17, 12,
		17, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 2, 1.0);

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
		SeatHouses.draw(d, SeatHouses.Office.RECTORY, INFO.beds());
	}
}
