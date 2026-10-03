package dev.voidmc.hearth.npc.routine;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Trait;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * One resident's day, as blocks of activity from a start minute (0 = midnight) to the next block's start. A pure
 * function of who they are and the day ({@link #of}): the same person keeps the same hours, while where they spend
 * their free time changes from day to day.
 *
 * <p>Minecraft's day starts at 06:00 (day time 0); a sim hour is 1000 ticks ({@link #minuteOfDay}).
 */
public final class Schedule {
	public static final int DAY = 24 * 60;

	private final List<Block> blocks;

	private Schedule(List<Block> blocks) {
		List<Block> sorted = new ArrayList<>(blocks);
		sorted.sort(null);
		this.blocks = Collections.unmodifiableList(sorted);
	}

	/**
	 * A stretch of the day.
	 *
	 * @param start   minute of the day it starts
	 * @param leisure where free time goes (LEISURE blocks), else null
	 * @param where   for meals: home, inn or at work
	 */
	public record Block(int start, Activity activity, Leisure leisure, Where where) implements Comparable<Block> {
		@Override
		public int compareTo(Block o) {
			return Integer.compare(this.start, o.start);
		}
	}

	public enum Where {
		HOME, INN, WORK
	}

	public List<Block> blocks() {
		return this.blocks;
	}

	/** The block that covers this minute of the day. */
	public Block at(int minute) {
		minute = Math.floorMod(minute, DAY);
		Block current = this.blocks.get(this.blocks.size() - 1);
		for (Block b : this.blocks) {
			if (b.start <= minute) {
				current = b;
			} else {
				break;
			}
		}
		return current;
	}

	/** Minutes since the block covering {@code minute} began. */
	public int minutesInto(int minute) {
		Block b = at(minute);
		return Math.floorMod(Math.floorMod(minute, DAY) - b.start, DAY);
	}

	/** The block after the one covering this minute. */
	public Block next(int minute) {
		Block b = at(minute);
		int i = this.blocks.indexOf(b);
		return this.blocks.get((i + 1) % this.blocks.size());
	}

	/** The minute of the day for a level's day time (day time 0 is 06:00). */
	public static int minuteOfDay(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		return (int) ((t * DAY / 24000L + 6 * 60) % DAY);
	}

	/** The level day time (within the day) of a minute of the day. */
	public static long dayTimeOf(int minute) {
		return Math.floorMod((long) (minute - 6 * 60) * 24000L / DAY, 24000L);
	}

	/**
	 * Builds the day.
	 *
	 * @param seed the resident's own seed (from their id): fixes their habits
	 * @param day  the sim day: varies their free time
	 */
	public static Schedule of(JobType job, AgeStage age, Set<Trait> traits, Culture culture, long seed, long day) {
		List<Block> b = new ArrayList<>();
		long daySeed = mix(seed ^ day * 0x9E3779B97F4A7C15L);
		boolean siesta = culture == Culture.MEDITERRANEAN || culture == Culture.DESERT || culture == Culture.MESA;
		boolean early = culture == Culture.TUNDRA || culture == Culture.NORTHERN || culture == Culture.ALPINE;
		int bedtime = siesta ? 23 * 60 : early ? 21 * 60 : 21 * 60 + 30;
		int wake = siesta ? 6 * 60 + 30 : 6 * 60;
		if (traits.contains(Trait.INDUSTRIOUS)) {
			wake -= 30;
		}
		if (traits.contains(Trait.LAZY)) {
			wake += 45;
			bedtime += 30;
		}
		if (traits.contains(Trait.SOCIABLE)) {
			bedtime += 30;
		}
		// Everyone's habits wobble a little so a town does not move in lockstep.
		wake += (int) (Math.floorMod(seed, 5) * 5) - 10;
		bedtime += (int) (Math.floorMod(seed >> 8, 7) * 5) - 15;

		switch (age) {
			case CHILD -> {
				wake = 7 * 60 + (int) Math.floorMod(seed, 4) * 5;
				b.add(new Block(wake, Activity.WAKE, null, Where.HOME));
				b.add(new Block(wake + 20, Activity.EAT, null, Where.HOME));
				b.add(new Block(8 * 60, Activity.LEISURE, childPlay(daySeed, 0), null));
				b.add(new Block(12 * 60, Activity.EAT, null, Where.HOME));
				b.add(new Block(12 * 60 + 45, Activity.LEISURE, childPlay(daySeed, 1), null));
				b.add(new Block(15 * 60 + 30, Activity.LEISURE, childPlay(daySeed, 2), null));
				b.add(new Block(18 * 60 + 30, Activity.EAT, null, Where.HOME));
				b.add(new Block(19 * 60, Activity.HOME, null, Where.HOME));
				b.add(new Block(20 * 60 + (int) Math.floorMod(seed >> 4, 4) * 10, Activity.SLEEP, null, Where.HOME));
				return new Schedule(b);
			}
			case ELDER -> {
				wake = Math.max(wake, 6 * 60 + 15);
				b.add(new Block(wake, Activity.WAKE, null, Where.HOME));
				b.add(new Block(wake + 40, Activity.EAT, null, Where.HOME));
				b.add(new Block(9 * 60, job == JobType.NONE ? Activity.LEISURE : Activity.WORK, elderLeisure(traits, daySeed, 0), null));
				b.add(new Block(12 * 60, Activity.EAT, null, Where.HOME));
				b.add(new Block(12 * 60 + 45, Activity.HOME, null, Where.HOME));
				b.add(new Block(14 * 60 + 30, Activity.LEISURE, elderLeisure(traits, daySeed, 1), null));
				b.add(new Block(18 * 60, Activity.EAT, null, Where.HOME));
				b.add(new Block(18 * 60 + 45, Activity.HOME, null, Where.HOME));
				b.add(new Block(Math.min(bedtime, 21 * 60 + 15) - 30, Activity.SLEEP, null, Where.HOME));
				return new Schedule(b);
			}
			default -> {
			}
		}

		// Night watch: a third of the guards keep the dark hours.
		if (job == JobType.GUARD && Math.floorMod(seed >> 12, 3) == 0) {
			b.add(new Block(7 * 60, Activity.SLEEP, null, Where.HOME));
			b.add(new Block(15 * 60, Activity.WAKE, null, Where.HOME));
			b.add(new Block(15 * 60 + 30, Activity.EAT, null, Where.HOME));
			b.add(new Block(16 * 60 + 15, Activity.LEISURE, leisure(traits, daySeed, 0), null));
			b.add(new Block(18 * 60, Activity.WORK, null, null));
			b.add(new Block(6 * 60 + 15, Activity.EAT, null, Where.HOME));
			return new Schedule(b);
		}

		int workStart = wake + 60;
		int lunch = 12 * 60;
		int lunchEnd = siesta ? 14 * 60 + 30 : 13 * 60;
		int workEnd = 17 * 60;
		Where lunchAt = switch (job) {
			case BUILDER, FARMER, FORESTER, QUARRIER, FISHER, HERDER, HUNTER, GUARD -> Where.WORK;
			default -> traits.contains(Trait.SOCIABLE) ? Where.INN : Where.HOME;
		};
		switch (job) {
			case BAKER -> {
				// Up before dawn for the ovens, done by mid-afternoon, early to bed.
				wake = 4 * 60;
				workStart = 4 * 60 + 20;
				lunch = 11 * 60;
				lunchEnd = 11 * 60 + 45;
				workEnd = 14 * 60;
				bedtime = Math.min(bedtime, 20 * 60 + 30);
			}
			case MERCHANT, TAILOR, MINTER -> workEnd = 18 * 60;
			case INNKEEPER -> {
				wake += 90;
				workStart = wake + 45;
				lunch = 15 * 60;
				lunchEnd = 15 * 60 + 30;
				workEnd = Math.max(bedtime, 23 * 60);
				bedtime = workEnd + 15;
			}
			case SCHOLAR -> {
				workStart = Math.max(workStart, 8 * 60);
				workEnd = 16 * 60 + 30;
			}
			case FISHER -> {
				wake = Math.min(wake, 5 * 60);
				workStart = wake + 30;
				workEnd = 15 * 60;
			}
			case NONE -> {
				// No trade: the morning is chores about the house.
			}
			default -> {
			}
		}
		if (traits.contains(Trait.INDUSTRIOUS)) {
			workEnd += 30;
		} else if (traits.contains(Trait.LAZY)) {
			workEnd -= 30;
		}
		// The day ends before midnight: nobody's bedtime wraps round into the next one.
		bedtime = Math.min(bedtime, 23 * 60 + 45);
		workEnd = Math.min(workEnd, bedtime - 30);

		workStart = Math.max(workStart, wake + 45);
		b.add(new Block(wake, Activity.WAKE, null, Where.HOME));
		b.add(new Block(wake + 20, Activity.EAT, null, Where.HOME));
		b.add(new Block(workStart, job == JobType.NONE ? Activity.LEISURE : Activity.WORK, job == JobType.NONE ? Leisure.DOORSTEP : null, null));
		if (lunch < workEnd) {
			b.add(new Block(lunch, Activity.EAT, null, lunchAt));
			if (siesta && lunchEnd - lunch > 60) {
				b.add(new Block(lunch + 60, Activity.HOME, null, Where.HOME));
			}
			b.add(new Block(lunchEnd, job == JobType.NONE ? Activity.LEISURE : Activity.WORK, job == JobType.NONE ? leisure(traits, daySeed, 2) : null, null));
		}
		int supper = Math.min(siesta ? 21 * 60 : 19 * 60, bedtime - 75);
		if (job == JobType.INNKEEPER) {
			b.add(new Block(bedtime, Activity.SLEEP, null, Where.HOME));
			return new Schedule(trim(b));
		}
		if (workEnd < supper) {
			b.add(new Block(workEnd, Activity.LEISURE, leisure(traits, daySeed, 0), null));
			if (supper - workEnd > 150) {
				b.add(new Block(workEnd + (supper - workEnd) / 2, Activity.LEISURE, leisure(traits, daySeed, 1), null));
			}
		}
		Leisure evening = leisure(traits, daySeed, 3);
		boolean innSupper = evening == Leisure.INN;
		b.add(new Block(Math.max(supper, workEnd), Activity.EAT, null, innSupper ? Where.INN : Where.HOME));
		int after = Math.max(supper, workEnd) + 45;
		if (innSupper || traits.contains(Trait.SOCIABLE) && evening == Leisure.PLAZA) {
			// An evening out before home.
			b.add(new Block(after, Activity.LEISURE, evening, null));
			after = Math.min(after + 90, bedtime - 30);
		}
		if (after < bedtime) {
			b.add(new Block(after, Activity.HOME, null, Where.HOME));
		}
		b.add(new Block(bedtime, Activity.SLEEP, null, Where.HOME));
		return new Schedule(trim(b));
	}

	/**
	 * Puts the blocks in order and drops those that cannot happen: a block starting at the same minute as an earlier
	 * one, and anything planned after bedtime (odd trait and trade combinations can push supper past it).
	 */
	private static List<Block> trim(List<Block> blocks) {
		int bed = Schedule.DAY;
		for (Block b : blocks) {
			if (b.activity == Activity.SLEEP) {
				bed = Math.min(bed, b.start);
			}
		}
		List<Block> out = new ArrayList<>();
		int last = -1;
		for (Block b : blocks) {
			int start = Math.floorMod(b.start, DAY);
			if (b.activity != Activity.SLEEP && start >= bed) {
				continue;
			}
			if (start > last) {
				out.add(new Block(start, b.activity, b.leisure, b.where));
				last = start;
			} else if (b.activity == Activity.SLEEP && !out.isEmpty()) {
				out.set(out.size() - 1, new Block(out.get(out.size() - 1).start, Activity.SLEEP, null, Where.HOME));
			}
		}
		return out;
	}

	static Leisure leisure(Set<Trait> traits, long daySeed, int slot) {
		int[] w = new int[Leisure.values().length];
		w[Leisure.PLAZA.ordinal()] = 3;
		w[Leisure.INN.ordinal()] = 1;
		w[Leisure.VISIT.ordinal()] = 2;
		w[Leisure.DOORSTEP.ordinal()] = 2;
		w[Leisure.BENCH.ordinal()] = 1;
		w[Leisure.WANDER.ordinal()] = 1;
		w[Leisure.VIEWPOINT.ordinal()] = 1;
		for (Trait t : traits) {
			switch (t) {
				case SOCIABLE -> {
					w[Leisure.PLAZA.ordinal()] += 3;
					w[Leisure.INN.ordinal()] += 3;
					w[Leisure.VISIT.ordinal()] += 2;
				}
				case SHY -> {
					w[Leisure.DOORSTEP.ordinal()] += 3;
					w[Leisure.VIEWPOINT.ordinal()] += 2;
					w[Leisure.READ.ordinal()] += 2;
					w[Leisure.PLAZA.ordinal()] = 1;
				}
				case PIOUS -> w[Leisure.PRAY.ordinal()] += 4;
				case CURIOUS -> {
					w[Leisure.WANDER.ordinal()] += 3;
					w[Leisure.VIEWPOINT.ordinal()] += 2;
				}
				case DREAMY -> {
					w[Leisure.VIEWPOINT.ordinal()] += 3;
					w[Leisure.READ.ordinal()] += 2;
				}
				case GOSSIP -> {
					w[Leisure.PLAZA.ordinal()] += 3;
					w[Leisure.VISIT.ordinal()] += 2;
				}
				case LAZY -> {
					w[Leisure.BENCH.ordinal()] += 2;
					w[Leisure.INN.ordinal()] += 2;
				}
				default -> {
				}
			}
		}
		return pick(w, mix(daySeed + slot * 0x632BE59BD9B4E019L));
	}

	private static Leisure childPlay(long daySeed, int slot) {
		int[] w = new int[Leisure.values().length];
		w[Leisure.PLAY.ordinal()] = 7;
		w[Leisure.VISIT.ordinal()] = 1;
		w[Leisure.WANDER.ordinal()] = 1;
		w[Leisure.DOORSTEP.ordinal()] = 1;
		return pick(w, mix(daySeed + slot * 0x632BE59BD9B4E019L));
	}

	private static Leisure elderLeisure(Set<Trait> traits, long daySeed, int slot) {
		int[] w = new int[Leisure.values().length];
		w[Leisure.BENCH.ordinal()] = 5;
		w[Leisure.PLAZA.ordinal()] = 2;
		w[Leisure.DOORSTEP.ordinal()] = 3;
		w[Leisure.VISIT.ordinal()] = 1;
		w[Leisure.PRAY.ordinal()] = traits.contains(Trait.PIOUS) ? 5 : 1;
		w[Leisure.READ.ordinal()] = traits.contains(Trait.DREAMY) ? 3 : 0;
		return pick(w, mix(daySeed + slot * 0x632BE59BD9B4E019L));
	}

	private static Leisure pick(int[] weights, long roll) {
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int r = (int) Long.remainderUnsigned(roll, total);
		for (int i = 0; i < weights.length; i++) {
			r -= weights[i];
			if (r < 0) {
				return Leisure.values()[i];
			}
		}
		return Leisure.PLAZA;
	}

	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Block b : this.blocks) {
			sb.append(String.format("%02d:%02d %s%s; ", b.start / 60, b.start % 60, b.activity,
				b.leisure != null ? "(" + b.leisure + ")" : b.where != null && b.activity == Activity.EAT ? "@" + b.where : ""));
		}
		return sb.toString();
	}
}
