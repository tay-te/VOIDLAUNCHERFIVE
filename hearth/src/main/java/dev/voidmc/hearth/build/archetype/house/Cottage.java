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
 * {@code hearth:house/cottage}: a one-and-a-half storey cottage with its garden, the house most settlements are made of.
 * A ground floor of living room and kitchen round a hearth (a bedroom off it when there is room), a bedroom in the roof
 * lit by dormers or gable windows and reached by a stair, a chimney, a front garden with a path to the door, a fence and
 * gate, and a yard with a woodpile and barrels. Roof form, door position, porch, bay window, dormers, chimney side,
 * window rhythm and garden all vary with the seed within the culture's habits; flat-roofed cultures get a roof terrace.
 *
 * <p>Params: none required.
 * <ul>
 *   <li>{@code storeys} (int, 1–2): full storeys under the roof (default: 1, or 2 for flat roofs on big plots)</li>
 *   <li>{@code garden} (boolean, default true): lay out the front garden</li>
 * </ul>
 */
public final class Cottage implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/cottage"), ArchetypeCategory.HOUSING, Set.of(), 2,
		7, 11, 7, 11, 6, 16, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.HILLSIDE), List.of(Hearth.id("house/townhouse")), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		HouseBuilder.cottage(d);
	}
}
