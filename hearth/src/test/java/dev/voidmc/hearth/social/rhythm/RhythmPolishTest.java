package dev.voidmc.hearth.social.rhythm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Batch A polish of the rhythm (§2.8): a town with guards always keeps a night watch; tavern seats fill two tables. */
class RhythmPolishTest {
	private static int next;

	static Resident guard(boolean nightBySeed, Random r) {
		while (true) {
			Resident g = new Resident(new UUID(r.nextLong(), r.nextLong()), "Wat" + ++next, "Guard", false, AgeStage.ADULT, -100, JobType.GUARD, 1,
				Resident.NO_PLOT, next, new CompoundTag());
			if (RhythmSchedule.nightBySeed(g) == nightBySeed) {
				return g;
			}
		}
	}

	static Resident farmer(Random r) {
		return new Resident(new UUID(r.nextLong(), r.nextLong()), "Ann" + ++next, "Smith", true, AgeStage.ADULT, -100, JobType.FARMER, 1, Resident.NO_PLOT,
			next, new CompoundTag());
	}

	/** The guard's working activity at a minute, with the rhythm's windows over their day. */
	static Activity at(Resident g, boolean appointed, int minute, long day) {
		long seed = g.id.getMostSignificantBits() ^ g.id.getLeastSignificantBits();
		Schedule base = Schedule.of(g.job, g.age, EnumSet.noneOf(Trait.class), Culture.TEMPERATE, seed, day);
		List<ScheduleOverrides.Window> ws = RhythmSchedule.windows(g.job, g.age, List.of(), RhythmData.of(Culture.TEMPERATE), false, false, false, seed, day,
			appointed);
		return (ws.isEmpty() ? base : ScheduleOverrides.overlay(base, ws)).at(minute).activity();
	}

	@Test
	void aTownWithGuardsKeepsANightWatch() {
		Random r = new Random(7);
		for (int town = 0; town < 300; town++) {
			List<Resident> people = new ArrayList<>();
			int guards = 1 + r.nextInt(3);
			for (int i = 0; i < guards; i++) {
				people.add(guard(r.nextInt(3) == 0, r));
			}
			for (int i = 0; i < 6; i++) {
				people.add(farmer(r));
			}
			UUID appointed = RhythmSchedule.pickWatch(people, town);
			assertEquals(appointed, RhythmSchedule.pickWatch(people, town), "the same guard all day");
			for (int minute : new int[]{22 * 60, 23 * 60 + 30, 2 * 60, 5 * 60}) {
				int onWatch = 0;
				for (Resident p : people) {
					if (p.job == JobType.GUARD && at(p, p.id.equals(appointed), minute, town) == Activity.WORK) {
						onWatch++;
					}
				}
				assertTrue(onWatch >= 1, "town " + town + " (" + guards + " guards) has nobody on the watch at " + minute);
			}
		}
	}

	@Test
	void noAppointmentWhereTheRuleGivesOneOrThereIsNoGuard() {
		Random r = new Random(9);
		assertNull(RhythmSchedule.pickWatch(List.of(guard(true, r), guard(false, r)), 1));
		assertNull(RhythmSchedule.pickWatch(List.of(farmer(r), farmer(r)), 1));
		Resident g = guard(false, r);
		assertEquals(g.id, RhythmSchedule.pickWatch(List.of(farmer(r), g), 1));
		// the appointed sleeps by day
		assertEquals(Activity.SLEEP, at(g, true, 10 * 60, 3));
		assertEquals(Activity.WORK, at(g, true, 23 * 60, 3));
	}

	static Poi seat(int x, int z) {
		return new Poi(PoiType.SEAT, new BlockPos(x, 64, z), Direction.NORTH, null);
	}

	@Test
	void seatsFillRoundTheTables() {
		List<Poi> tables = List.of(new Poi(PoiType.TABLE, new BlockPos(0, 64, 0), Direction.NORTH, null),
			new Poi(PoiType.TABLE, new BlockPos(10, 64, 0), Direction.NORTH, null));
		List<Poi> seats = List.of(seat(-1, 0), seat(1, 0), seat(0, 1), seat(0, -1), seat(9, 0), seat(11, 0), seat(10, 1), seat(10, -1));
		List<Poi> order = TavernNight.seatOrder(seats, tables);
		assertEquals(seats.size(), order.size());
		assertNotEquals(order.get(0).pos().getX() < 5, order.get(1).pos().getX() < 5, "the first two drinkers sit at two tables");
		assertNotNull(order.get(7));
	}
}
