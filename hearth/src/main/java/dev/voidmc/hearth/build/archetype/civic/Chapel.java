package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:civic/chapel}: the place of worship of a village or a town, on the rise. A nave of pews in two banks
 * either side of an aisle runs from the street door back to the altar with its candles and the priest's lectern; tall
 * windows of coloured glass light it; a bell rings over it from a bell-cote, a turret on the ridge or a tower; a
 * churchyard of graves under a dark tree lies beside or behind it. A village's chapel is humble (a bell-cote, a short
 * nave); a town's has a tower; a city's is tall and buttressed (the cathedral is a landmark of its own).
 *
 * <p>Each tradition builds its own: a timber or stone parish church with a spire (temperate, wisteria, moorland,
 * maritime), a whitewashed chapel with an onion-domed tower (alpine), a stave church of stacked shingle roofs and carved
 * ridges (northern), a log chapel with onion cupolas (tundra), a whitewashed chapel with a bell wall or a campanile and a
 * blue dome (mediterranean), an adobe mission with a pierced bell wall and a walled campo santo (desert, mesa), a round
 * chapel under a cone (steppe), a shrine-temple on a stone terrace under a double curved roof (karst), and a raised
 * chapel with a gallery and a belfry (bayou, tropical).
 *
 * <p>POIs: SEAT on the pews, ALTAR before the altar, the PRIEST's JOB_SITE at the lectern, DOOR at the entrance,
 * VIEWPOINT where there is a terrace.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code tower} (boolean): a bell tower rather than a bell-cote (default by tier: towns and cities have one)</li>
 *   <li>{@code graveyard} (boolean): a churchyard on the plot (default true where there is room)</li>
 *   <li>{@code variant} (int): the style variant</li>
 * </ul>
 */
public final class Chapel implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/chapel"), ArchetypeCategory.CIVIC, Set.of(JobType.PRIEST), 0,
		9, 13, 13, 19, 12, 30, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Tradition t = Tradition.of(d);
		Culture c = d.style().culture();
		switch (t) {
			case LOG -> {
				if (c == Culture.TUNDRA) {
					ChapelWestern.draw(d, t);
				} else {
					ChapelStave.draw(d);
				}
			}
			case KARST -> ChapelShrine.draw(d);
			case ADOBE -> ChapelMission.draw(d);
			case STEPPE -> ChapelRotunda.draw(d);
			case MEDITERRANEAN -> ChapelWhite.draw(d);
			default -> ChapelWestern.draw(d, t);
		}
	}
}
