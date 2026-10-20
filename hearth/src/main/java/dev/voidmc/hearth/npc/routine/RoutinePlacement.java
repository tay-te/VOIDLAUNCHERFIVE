package dev.voidmc.hearth.npc.routine;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.TownState;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Where a resident who is only a record is right now, by their routine: in bed at night, at work in working hours,
 * at the square in the evening, on their doorstep as they set out. The embodiment manager uses it to measure distance
 * to players and to put the entity in the right place when it appears.
 */
public final class RoutinePlacement {
	private RoutinePlacement() {
	}

	/**
	 * @param pos     where they are (the caller finds standing room near it)
	 * @param bed     set when they are asleep in it
	 */
	public record Placement(BlockPos pos, @Nullable BlockPos bed, Activity activity) {
	}

	public static Placement where(ServerLevel level, Settlement settlement, Resident resident, ResidentPlaces places, TownState town) {
		return where(level, settlement, resident, places, town, schedule(settlement, resident, NpcTime.today()));
	}

	/** The resident's day, as their brain would have it. */
	public static Schedule schedule(Settlement settlement, Resident resident, long day) {
		long seed = resident.id.getMostSignificantBits() ^ resident.id.getLeastSignificantBits();
		return ScheduleOverrides.apply(Schedule.of(resident.job, resident.age, NpcData.of(resident).traits(), settlement.culture(), seed, day), settlement,
			resident, day);
	}

	/** As above, with the resident's schedule for today already at hand (the embodiment manager keeps them). */
	public static Placement where(ServerLevel level, Settlement settlement, Resident resident, ResidentPlaces places, TownState town, Schedule schedule) {
		NpcData data = NpcData.of(resident);
		long seed = resident.id.getMostSignificantBits() ^ resident.id.getLeastSignificantBits();
		int minute = NpcTime.minuteOfDay(level);
		Schedule.Block block = schedule.at(minute);
		// Folded away only moments ago (a chunk reloading, a player stepping back): they are where they were.
		long since = level.getGameTime() - data.lastPosTime();
		if (data.lastPos().isPresent() && since >= 0 && since < 20 * 60 && block.activity() != Activity.SLEEP) {
			return new Placement(data.lastPos().get(), null, block.activity());
		}
		// Out at a remote work site, or on the way there or home (docs/LIVING_WORLD_2.md §6.3).
		BlockPos remote = dev.voidmc.hearth.npc.work.field.FieldPlacement.where(level, settlement, resident, places, minute, block);
		if (remote != null) {
			return new Placement(remote, null, block.activity());
		}
		// Just setting out: still at the door of where they were.
		if (schedule.minutesInto(minute) < 15 && block.activity() != Activity.SLEEP) {
			Schedule.Block previous = schedule.at(block.start() - 1);
			BlockPos from = spot(level, settlement, places, town, previous, seed);
			if (previous.activity() == Activity.SLEEP || previous.activity() == Activity.HOME || previous.activity() == Activity.WAKE) {
				from = places.homeDoor != null ? places.homeDoor.pos() : from;
			}
			return new Placement(from, null, block.activity());
		}
		if (block.activity() == Activity.SLEEP) {
			return new Placement(places.bed != null ? places.bed : places.home, places.bed, Activity.SLEEP);
		}
		return new Placement(spot(level, settlement, places, town, block, seed), null, block.activity());
	}

	private static BlockPos spot(ServerLevel level, Settlement settlement, ResidentPlaces p, TownState town, Schedule.Block block, long seed) {
		return switch (block.activity()) {
			case SLEEP -> p.bed != null ? p.bed : p.home;
			case WAKE, HOME, SHELTER, FLEE, ALARM -> p.home;
			case EAT -> switch (block.where() != null ? block.where() : Schedule.Where.HOME) {
				case INN -> first(town.places.inn(settlement), p.home);
				case WORK -> p.work;
				case HOME -> p.home;
			};
			case WORK -> p.workSite != null ? p.workSite.pos() : p.work;
			case LEISURE -> {
				Leisure l = block.leisure() != null ? block.leisure() : Leisure.PLAZA;
				yield switch (l) {
					case DOORSTEP, READ -> p.homeDoor != null ? p.homeDoor.pos() : p.home;
					case INN -> first(town.places.inn(settlement), settlement.center());
					case PRAY -> first(town.places.all(level, settlement, PoiType.ALTAR), settlement.center());
					case VIEWPOINT -> first(town.places.all(level, settlement, PoiType.VIEWPOINT), settlement.center());
					default -> {
						List<Poi> meet = town.places.all(level, settlement, PoiType.MEETING_POINT);
						yield meet.isEmpty() ? settlement.center() : meet.get((int) Math.floorMod(seed, meet.size())).pos();
					}
				};
			}
		};
	}

	private static BlockPos first(List<Poi> pois, BlockPos fallback) {
		return pois.isEmpty() ? fallback : pois.get(0).pos();
	}
}
