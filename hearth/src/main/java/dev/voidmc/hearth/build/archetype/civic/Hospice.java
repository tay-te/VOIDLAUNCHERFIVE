package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.archetype.civic.Precinct.Kind;
import dev.voidmc.hearth.build.archetype.civic.Precinct.Want;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:civic/hospice}: the refuge on a mountain pass (docs/LIVING_WORLD_2.md §7.1), kept by a few brothers for
 * whoever comes over the saddle: the cloister plan of the religious houses ({@link Precinct}) drawn close and stout, its
 * ranges walling a little court against the weather, its chapel on one side with the bell that is rung in fog and snow
 * to guide travellers in (a ridge turret or a bell-cote, never a great tower).
 * <ul>
 *   <li><b>The gate range</b> on the way in: the gate passage, the lodge of the brother who keeps the door, the
 *   travellers' room of beds; on a shallow plot a wall with a gateway closes the court and the lodge moves to the side.</li>
 *   <li><b>The side range</b>: the refectory with its long table and the kitchen where the pot is always on.</li>
 *   <li><b>The back range</b>: the infirmary (the healer's bench, sickbeds for the frozen and the hurt) and the brothers'
 *   dormitory over it.</li>
 *   <li><b>The kitchen garden</b>, walled, where the plot is wide.</li>
 * </ul>
 * Folk: MONKs at the door and the kitchen, the HEALER at the infirmary's bench, a PRIEST at the chapel's lectern; three
 * beds in the dormitory, one for a traveller, a sickbed in the infirmary.
 *
 * <p>Laid out only by a post's planner ({@code plan/wild}): a weight of 0 and a city's tier keep towns from choosing it.
 *
 * <p>Params: {@code side} ({@code "left"} or {@code "right"}: where the chapel stands), {@code variant} (int).
 */
public final class Hospice implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/hospice"), ArchetypeCategory.CIVIC, Set.of(JobType.MONK), 4,
		19, 27, 19, 27, 10, 20, SettlementTier.CITY, Set.of(Culture.ALPINE), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 0.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Precinct p = new Precinct(d, true, true);
		boolean two = p.storeys() > 1;
		if (p.front != null) {
			p.front.wants.add(List.of(Want.stair(Kind.PORTER, 1), Want.of(Kind.GATE, 0), Want.of(Kind.GUESTS, 1.4)));
			if (two) {
				p.front.wants.add(List.of(Want.of(Kind.STORE, 1)));
			}
			p.far.wants.add(List.of(Want.of(Kind.REFECTORY, 1.4), Want.stair(Kind.KITCHEN, 1)));
		} else {
			p.far.wants.add(List.of(Want.of(Kind.GUESTS, 1), Want.of(Kind.REFECTORY, 1.2), Want.stair(Kind.KITCHEN, 1)));
		}
		if (two) {
			p.far.wants.add(List.of(Want.of(Kind.STORE, 1)));
			p.back.wants.add(List.of(Want.of(Kind.INFIRMARY, 1.3), Want.stair(Kind.CHAPTER, 0.8)));
			p.back.wants.add(List.of(Want.of(Kind.DORMITORY, 1)));
		} else {
			p.back.wants.add(List.of(Want.of(Kind.DORMITORY, 1.3), Want.of(Kind.INFIRMARY, 1)));
		}
		p.shell();
		p.rooms();
		// a few brothers and a bed or two for whoever is caught by the night (the post's people fill its beds, §7.2)
		p.beds(Kind.DORMITORY, 3);
		p.beds(Kind.GUESTS, 1);
		p.beds(Kind.INFIRMARY, 1);
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
		p.ensureJob(JobType.HEALER, Kind.INFIRMARY, Kind.KITCHEN, Kind.REFECTORY, Kind.DORMITORY);
		p.ensureJob(JobType.MONK, Kind.PORTER, Kind.KITCHEN, Kind.REFECTORY, Kind.GUESTS, Kind.STORE, Kind.DORMITORY);
		p.garden();
		p.forecourt(church.doorX());
		p.finish();
	}
}
