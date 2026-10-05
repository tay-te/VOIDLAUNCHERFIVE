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
 * {@code hearth:civic/monastery}: an abbey on quiet ground outside the town, built round its cloister the way the orders
 * built them, in each culture's manner ({@link dev.voidmc.hearth.build.kit.learning.Rite}).
 * <ul>
 *   <li><b>The church</b> on one side of the cloister, its west front on the road beside the gate range, its east end in
 *   an apse (a square end in the stone, timber and dry lands): people's pews in the nave, the monks' choir stalls before
 *   the chancel, the altar with the priest's lectern; its bell in a ridge turret (the Cistercian manner, temperate), a
 *   west tower capped in the culture's way where the plot is tall, a bell wall in the dry lands, a pavilion in the karst.</li>
 *   <li><b>The cloister</b>: a walk on arcades round the garth under a lean-to, the garth of grass and herb beds about a
 *   well, a low wall in the bays, open in the middle bay of each side.</li>
 *   <li><b>The ranges</b> of two storeys (one on a low plot), stone below and the culture's own above in the timber
 *   lands, under one roof hipped at the corners: the <i>gate range</i> on the street (the gate passage, the porter's
 *   lodge, the cellarer's store; guest chambers over), the <i>refectory range</i> (the refectory with its long table and
 *   reader's pulpit, the kitchen, the brewhouse; the granary over), the <i>east range</i> against the church's chancel
 *   (the chapter house, the scriptorium with desks under its windows and the masters' lecterns; the dormitory over).</li>
 *   <li><b>The herb garden</b>: walled, beside the refectory range where the plot is wide: rows of herbs, beehives, a
 *   water butt.</li>
 * </ul>
 * Folk: PRIEST at the altar's lectern, SCHOLARs at the scriptorium's lecterns, the BREWER at the brewhouse's stand; ten
 * beds in the dormitory (and the guests').
 *
 * <p>Params: {@code side} ({@code "left"} or {@code "right"}: where the church stands), {@code variant} (int).
 */
public final class Monastery implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/monastery"), ArchetypeCategory.CIVIC,
		Set.of(JobType.PRIEST, JobType.SCHOLAR, JobType.BREWER), 10, 21, 31, 21, 31, 12, 26, SettlementTier.TOWN, Set.of(),
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
			if (two) {
				p.front.wants.add(List.of(Want.stair(Kind.BREWHOUSE, 1.2), Want.of(Kind.GATE, 0), Want.of(Kind.PORTER, 1), Want.of(Kind.CELLAR, 0.8)));
				p.front.wants.add(List.of(Want.of(Kind.GUESTS, 1), Want.of(Kind.STORE, 1)));
			} else {
				p.front.wants.add(List.of(Want.of(Kind.BREWHOUSE, 1.2), Want.of(Kind.GATE, 0), Want.of(Kind.PORTER, 1), Want.of(Kind.CELLAR, 0.8)));
			}
		}
		if (two) {
			p.far.wants.add(List.of(Want.stair(Kind.REFECTORY, 2), Want.of(Kind.KITCHEN, 1.2)));
			p.far.wants.add(List.of(Want.of(Kind.STORE, 1)));
			p.back.wants.add(List.of(Want.of(Kind.CHAPTER, 1), Want.stair(Kind.SCRIPTORIUM, 1.4)));
			p.back.wants.add(List.of(Want.of(Kind.DORMITORY, 1)));
		} else {
			p.far.wants.add(List.of(Want.of(Kind.REFECTORY, 1.4), Want.of(Kind.KITCHEN, 1)));
			p.back.wants.add(List.of(Want.of(Kind.DORMITORY, 1.5), Want.of(Kind.SCRIPTORIUM, 1)));
		}
		p.shell();
		p.rooms();
		p.beds(Kind.DORMITORY, 10);
		p.beds(Kind.GUESTS, 2);
		p.skyline = Math.max(p.skyline, p.roofs());
		PrecinctChurch.Built church = PrecinctChurch.draw(p, false);
		p.skyline = Math.max(p.skyline, church.skyline());
		p.walk();
		p.garth(!church.pavilion());
		if (church.pavilion()) {
			p.skyline = Math.max(p.skyline, p.pavilion());
		}
		p.furnish();
		p.ensureJob(JobType.BREWER, Kind.KITCHEN, Kind.CELLAR, Kind.STORE, Kind.REFECTORY, Kind.PORTER);
		p.garden();
		p.forecourt(church.doorX());
		p.finish();
	}
}
