package dev.voidmc.hearth.npc.place;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.person.NpcData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * One resident's own places: their bed, their home's table, seats, fire and door, and their place of work. Beds and
 * job sites are shared out among the people of a house or workplace in a stable order (by id), so two folk never
 * aim for the same bed and a person keeps the same one night after night.
 *
 * <p>Resolved from the plots' POIs ({@link SettlementPlaces}); a resident without plots (the sandbox, a folk living
 * in a player's house) uses the bed, job site and anchor recorded in their npc data.
 */
public final class ResidentPlaces {
	public @Nullable BlockPos bed;
	public @Nullable Direction bedFacing;
	public final List<Poi> homeSeats = new ArrayList<>();
	public final List<Poi> homeTables = new ArrayList<>();
	public final List<Poi> homeHearths = new ArrayList<>();
	public @Nullable Poi homeDoor;
	/** Somewhere inside or at their home, always set. */
	public BlockPos home;
	public @Nullable Poi workSite;
	public final List<Poi> workSpots = new ArrayList<>();
	public @Nullable Poi workStorage;
	public @Nullable Poi workDoor;
	/** The middle of their workplace, or their home if they have none. */
	public BlockPos work;
	/** False while a plot's POIs are not known yet: resolve again later. */
	public boolean complete = true;

	private ResidentPlaces(BlockPos home) {
		this.home = home;
		this.work = home;
	}

	public static ResidentPlaces resolve(ServerLevel level, Settlement settlement, Resident resident, SettlementPlaces places) {
		NpcData data = NpcData.of(resident);
		BlockPos anchor = data.anchor().orElse(null);
		PlotView homePlot = resident.home != Resident.NO_PLOT ? settlement.plot(resident.home) : null;
		PlotView workPlot = resident.work != Resident.NO_PLOT ? settlement.plot(resident.work) : null;
		BlockPos home = anchor != null ? anchor : homePlot != null ? centre(homePlot.spec()) : settlement.center();
		ResidentPlaces r = new ResidentPlaces(home);

		List<Poi> homePois = places.plot(level, settlement, homePlot);
		if (homePlot != null && homePois.isEmpty()) {
			r.complete = false;
		}
		if (homePlot == null && anchor != null) {
			// No home plot (a house a player built): the seats, tables, fire and door round where they live.
			homePois = new ArrayList<>();
			for (Poi poi : places.near(anchor, 9)) {
				if (poi.type() != PoiType.BED) {
					homePois.add(poi);
				}
			}
			homePois.sort(Comparator.comparingDouble(p -> p.pos().distSqr(anchor)));
		}
		List<Poi> beds = new ArrayList<>();
		for (Poi poi : homePois) {
			switch (poi.type()) {
				case BED -> beds.add(poi);
				case SEAT -> r.homeSeats.add(poi);
				case TABLE -> r.homeTables.add(poi);
				case HEARTH -> r.homeHearths.add(poi);
				case DOOR -> {
					if (r.homeDoor == null) {
						r.homeDoor = poi;
					}
				}
				default -> {
				}
			}
		}
		// a bed that is gone (broken, burnt, taken) is no bed to go to: the others are shared out instead
		beds.removeIf(p -> level.isLoaded(p.pos()) && !(level.getBlockState(p.pos()).getBlock() instanceof net.minecraft.world.level.block.BedBlock));
		if (beds.isEmpty() && homePlot != null && !homePois.isEmpty() && data.bed().isEmpty()) {
			// a home whose known points name no bed standing (a house rebuilt since its points were read, a drawing that marks
			// none, its beds gone): the beds standing in it, so nobody with a home sleeps sitting up while there is one
			net.minecraft.world.level.levelgen.structure.BoundingBox box = SettlementPlaces.bounds(homePlot.spec());
			if (PoiScanner.loaded(level, box) && SettlementPlaces.takeScanBudget(level)) {
				for (Poi poi : PoiScanner.scan(level, box)) {
					if (poi.type() == PoiType.BED) {
						beds.add(poi);
					}
				}
			} else {
				// (looked for again soon, when the chunks are in or the tick's scans not spent)
				r.complete = false;
			}
		}
		if (data.bed().isPresent()) {
			r.bed = data.bed().get();
		} else if (!beds.isEmpty()) {
			beds.sort(Comparator.comparingLong(p -> p.pos().asLong()));
			int rank = rank(settlement, resident, true);
			Poi bed = beds.get(rank % beds.size());
			r.bed = bed.pos();
			r.bedFacing = bed.facing();
		}
		if (!r.homeHearths.isEmpty()) {
			r.home = r.homeHearths.get(0).pos();
		} else if (r.bed != null) {
			r.home = r.bed;
		}

		List<Poi> workPois = places.plot(level, settlement, workPlot);
		if (workPlot != null && workPois.isEmpty()) {
			r.complete = false;
		}
		r.work = workPlot != null ? centre(workPlot.spec()) : data.workPos().orElse(r.home);
		List<Poi> sites = new ArrayList<>();
		for (Poi poi : workPois) {
			switch (poi.type()) {
				case JOB_SITE, COUNTER, MARKET_STALL, LECTERN, ALTAR, GUARD_POST, DOCK -> {
					if (poi.job() == null || poi.job() == resident.job) {
						sites.add(poi);
					}
					r.workSpots.add(poi);
				}
				case STORAGE -> {
					if (r.workStorage == null) {
						r.workStorage = poi;
					}
				}
				case DOOR -> {
					if (r.workDoor == null) {
						r.workDoor = poi;
					}
				}
				default -> {
				}
			}
		}
		if (data.workPos().isPresent()) {
			BlockPos site = data.workPos().get();
			r.workSite = new Poi(PoiType.JOB_SITE, site, Direction.NORTH, resident.job);
			for (Poi poi : places.near(site, 1)) {
				if (poi.pos().equals(site)) {
					// The scanned POI knows which way the workstation is.
					r.workSite = poi;
				}
			}
			if (r.workStorage == null) {
				for (Poi poi : places.near(site, 10)) {
					if (poi.type() == PoiType.STORAGE && (r.workStorage == null || poi.pos().distSqr(site) < r.workStorage.pos().distSqr(site))) {
						r.workStorage = poi;
					}
				}
			}
		} else if (!sites.isEmpty()) {
			sites.sort(Comparator.comparingLong(p -> p.pos().asLong()));
			r.workSite = sites.get(rank(settlement, resident, false) % sites.size());
		}
		if (r.workSite != null) {
			r.work = r.workSite.pos();
		}
		return r;
	}

	/** This resident's place in line among those sharing their home (or workplace and trade), ordered by id. */
	private static int rank(Settlement settlement, Resident resident, boolean home) {
		int rank = 0;
		UUID id = resident.id;
		for (Resident other : settlement.residents()) {
			if (other == resident) {
				continue;
			}
			boolean shares = home ? other.home == resident.home && other.home != Resident.NO_PLOT
				: other.work == resident.work && other.job == resident.job && other.work != Resident.NO_PLOT;
			if (shares && other.id.compareTo(id) < 0) {
				rank++;
			}
		}
		return rank;
	}

	public static BlockPos centre(PlotSpec spec) {
		return spec.origin().offset(spec.sizeX() / 2, 0, spec.sizeZ() / 2);
	}

	/** The job a POI type is for when a trade has no job site of its own. */
	public static boolean suits(Poi poi, JobType job) {
		return poi.job() == null || poi.job() == job;
	}
}
