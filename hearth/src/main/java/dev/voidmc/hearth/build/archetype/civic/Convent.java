package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.archetype.civic.Precinct.Kind;
import dev.voidmc.hearth.build.archetype.civic.Precinct.Want;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:civic/convent}: a house of sisters on quiet ground outside the town, smaller than an abbey and turned
 * toward the sick: the same cloister plan ({@link Precinct}) round a garth of herbs, its chapel on one side (a ridge
 * turret or a bell-cote, never a great tower; a bell wall in the dry lands, a pavilion in the karst).
 * <ul>
 *   <li><b>The gate range</b> on the street where the plot is deep enough (the parlour where the sisters meet the world
 *   through the grille, the gate, the portress's lodge; the workroom of looms over); on a shallow plot a wall with a
 *   gateway closes the court instead and the parlour moves to the side range.</li>
 *   <li><b>The side range</b>: the refectory and the kitchen, the store over.</li>
 *   <li><b>The east range</b>: the infirmary (sickbeds along the windows, the healer's bench with its brewing stand and
 *   cauldron, a chest of herbs and linen) and the chapter room; the dormitory over.</li>
 *   <li><b>The physic garden</b>, walled, beside the side range where the plot is wide.</li>
 * </ul>
 * Folk: PRIEST at the chapel's lectern, the HEALER at the infirmary's bench; eight beds in the dormitory, SICKBEDs in
 * the infirmary.
 *
 * <p>Params: {@code side} ({@code "left"} or {@code "right"}: where the chapel stands), {@code variant} (int).
 */
public final class Convent implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/convent"), ArchetypeCategory.CIVIC,
		Set.of(JobType.PRIEST, JobType.HEALER), 8, 17, 27, 17, 27, 10, 18, SettlementTier.TOWN, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Precinct p = new Precinct(d, true, true);
		boolean two = p.storeys() > 1;
		if (p.front != null) {
			p.front.wants.add(List.of(Want.stair(Kind.PARLOUR, 1), Want.of(Kind.GATE, 0), Want.of(Kind.PORTER, 1)));
			if (two) {
				p.front.wants.add(List.of(Want.of(Kind.WORKROOM, 1)));
			}
			p.far.wants.add(List.of(Want.of(Kind.REFECTORY, 1.4), Want.stair(Kind.KITCHEN, 1)));
		} else {
			p.far.wants.add(List.of(Want.of(Kind.PARLOUR, 0.8), Want.of(Kind.REFECTORY, 1.4), Want.stair(Kind.KITCHEN, 1)));
		}
		if (two) {
			p.far.wants.add(List.of(Want.of(Kind.STORE, 1)));
			p.back.wants.add(List.of(Want.of(Kind.INFIRMARY, 1.3), Want.stair(Kind.CHAPTER, 1)));
			p.back.wants.add(List.of(Want.of(Kind.DORMITORY, 1)));
		} else {
			p.back.wants.add(List.of(Want.of(Kind.DORMITORY, 1.3), Want.of(Kind.INFIRMARY, 1)));
		}
		p.shell();
		p.rooms();
		p.beds(Kind.DORMITORY, 8);
		p.beds(Kind.INFIRMARY, 3);
		p.skyline = Math.max(p.skyline, p.roofs());
		PrecinctChurch.Built church = PrecinctChurch.draw(p, true);
		p.skyline = Math.max(p.skyline, church.skyline());
		p.walk();
		p.garth(!church.pavilion());
		if (church.pavilion()) {
			p.skyline = Math.max(p.skyline, p.pavilion());
		}
		p.furnish();
		p.ensureSickbed();
		p.ensureJob(JobType.HEALER, Kind.INFIRMARY, Kind.KITCHEN, Kind.STORE, Kind.REFECTORY, Kind.PARLOUR, Kind.CHAPTER, Kind.DORMITORY);
		p.garden();
		p.forecourt(church.doorX());
		p.finish();
	}
}
