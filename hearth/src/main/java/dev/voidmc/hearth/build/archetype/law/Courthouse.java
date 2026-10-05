package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:law/courthouse}: where the town's judge sits. Built as an institution of its culture:
 *
 * <ul>
 *   <li><b>A towered court</b> ({@link CourtHall#towered}: temperate and wisteria, alpine, moorland and maritime,
 *   mediterranean, steppe): a two-storey hall on the square, its offices and records below, the court room above under a
 *   tall roof, a five-square tower standing half out of its front with the portal at its foot and the loggia from which
 *   sentences are proclaimed. The Bohemian court is rendered white over a stone plinth with dressed quoins and window
 *   frames, barred windows below, tall paired lights above, a cornice under a hipped tile roof with dormers, and the
 *   crown of the Prague bridge towers on its tower; the others wear their tradition's walls and caps (an onion in the
 *   Alps, a slated spire on the tolbooths of the north, a rusticated palazzo and a campanile in the south, a cone on the
 *   steppe).</li>
 *   <li><b>A court in a courtyard</b> ({@link CourtYard}: desert, mesa): a walled compound entered through a tall iwan,
 *   a paved court with a basin and a palm round which an arcade shades the petitioners' benches, and at its back the
 *   qadi's hall under a dome.</li>
 *   <li><b>A magistrate's yamen</b> ({@link Yamen}: karst): a gate hall on the street with the petitioners' drum, a
 *   courtyard of stone, and the main hall on a raised terrace with red columns under a double curved roof, the
 *   magistrate's table on a dais before a screen.</li>
 *   <li><b>A single hall</b> ({@link CourtHall#single}: northern and tundra, bayou and tropical): the north's lawspeaker's
 *   hall of logs with its high seat, a turret on the ridge; the raised gallery court house of the bayou and the tropics.</li>
 * </ul>
 *
 * <p>Inside every one: the judge's dais with the high seat (THRONE and the JUDGE's job site) between assessors, the
 * clerk's lectern, the court's chest of records, the dock facing the bench, the bar of the court and benches for the
 * public (SEATs), the town's arms; outside, the notice board where the court's sentences and the wanted are posted.
 *
 * <p>POIs: THRONE, JOB_SITE (JUDGE), SEAT, LECTERN, STORAGE, NOTICE_BOARD, VIEWPOINT (the loggia), DOOR.
 *
 * <p>Params: {@code banner} (string: the town's arms, see the town hall), {@code tower} ({@code centre} or
 * {@code corner}; towered courts), {@code variant} (int).
 */
public final class Courthouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("law/courthouse"), ArchetypeCategory.LAW, Set.of(JobType.JUDGE), 0,
		13, 19, 13, 19, 28, 36, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Tradition t = Tradition.of(d);
		switch (t) {
			case LOG, STILT -> CourtSingle.draw(d, t);
			case ADOBE -> CourtYard.draw(d);
			case KARST -> Yamen.draw(d);
			default -> CourtHall.towered(d, t);
		}
	}
}
