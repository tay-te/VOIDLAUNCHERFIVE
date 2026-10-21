package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/**
 * The outcome rule of every kind of event (docs/LIVING_WORLD_2.md §3.2): what happens if nobody intervenes, decided from
 * the facts taken when the event was scheduled ({@link Scheduled#args()}) and its seed — and what happens when players
 * did (testimony, raiders killed, the fire fought, a contest won), the same roll weighed by what they did. Pure: an
 * abstract town and an embodied one call the same {@link #resolve} with the same event, so they agree.
 */
public final class Outcomes {
	private Outcomes() {
	}

	public static Outcome resolve(Scheduled e, EventRule rule, Interventions iv) {
		SplittableRandom r = new SplittableRandom(e.seed() ^ 0x5DEECE66DL);
		boolean touched = iv.any();
		return switch (e.kind()) {
			case TRIAL -> trial(e, iv, r, touched);
			case PILLORY -> pillory(e, iv, touched);
			case EXECUTION -> execution(e, iv, touched);
			case RAID -> raid(e, rule, iv, r, touched);
			case FIRE -> fire(e, rule, iv, r, touched);
			case TAX -> tax(e, rule, iv, touched);
			case FAIR -> fair(e, iv, r, touched);
			case TOURNAMENT -> tournament(e, iv, r, touched);
			case SLAUGHTER_DAY -> new Outcome(e.id(), e.kind(), e.day(), "held", 0, gained(Goods.MEAT, (int) rule.param("meat", 6)), List.of(), -1, 0, "", "",
				false);
			case TROUPE -> new Outcome(e.id(), e.kind(), e.day(), "held", (long) rule.param("purse", 6), new int[0], List.of(), -1, 0, "", "", false);
			case HORSE_FAIR -> new Outcome(e.id(), e.kind(), e.day(), "held", -(long) rule.param("fees", 8), new int[0], List.of(), -1, 0, "", "", false);
			case LOST_CHILD -> new Outcome(e.id(), e.kind(), e.day(), touched ? "found_by_player" : "found", 0, new int[0], List.of(), -1, 0, "", "", touched);
			default -> new Outcome(e.id(), e.kind(), e.day(), "held", 0, new int[0], List.of(), e.plot(), 0, "", "", touched);
		};
	}

	// ---------------------------------------------------------------- the law

	/** The chance a trial finds the accused guilty, before any testimony: the evidence, less the judge's mercy. */
	public static double guiltOdds(CompoundTag a) {
		return Mth.clamp(0.35 + 0.55 * a.getDoubleOr("evidence", 0.5) - a.getDoubleOr("leniency", 0), 0.05, 0.95);
	}

	static Outcome trial(Scheduled e, Interventions iv, SplittableRandom r, boolean touched) {
		CompoundTag a = e.args();
		double roll = r.nextDouble();
		// testimony moves the odds: positive against the accused, negative for them (each witness a speech check that worked)
		double p = Mth.clamp(guiltOdds(a) + 0.25 * iv.get("testimony"), 0.02, 0.98);
		boolean guilty = roll < p;
		String offence = a.getStringOr("offence", "theft");
		boolean poor = a.getBooleanOr("poor", false);
		long fine = a.getLongOr("fine", 10);
		String sentence;
		long coin = 0;
		if (!guilty) {
			sentence = "";
		} else if (offence.equals("murder")) {
			sentence = a.getStringOr("executions", "staged").equals("never") ? "cell" : "gallows";
		} else if (iv.flag("fine_paid")) {
			sentence = "fine"; // a player paid it for them: no pillory
		} else if (poor && !offence.equals("poaching")) {
			sentence = "pillory";
		} else {
			sentence = "fine";
			coin = -fine; // paid into the treasury by the household
		}
		return new Outcome(e.id(), e.kind(), e.day(), guilty ? "guilty" : "acquitted", coin, new int[0], List.of(), -1, 0, "", sentence, touched);
	}

	static Outcome pillory(Scheduled e, Interventions iv, boolean touched) {
		String result = iv.flag("freed") ? "freed" : iv.flag("fine_paid") ? "released" : "served";
		return new Outcome(e.id(), e.kind(), e.day(), result, 0, new int[0], List.of(), -1, 0, "", "", touched);
	}

	static Outcome execution(Scheduled e, Interventions iv, boolean touched) {
		if (iv.flag("mercy") || e.principals().isEmpty() || e.args().getBooleanOr("forced", false)) {
			return new Outcome(e.id(), e.kind(), e.day(), "commuted", 0, new int[0], List.of(), -1, 0, "", "exile", touched);
		}
		return new Outcome(e.id(), e.kind(), e.day(), "hanged", 0, new int[0], List.of(e.principals().get(0)), -1, 0, "", "gallows", touched);
	}

	// ---------------------------------------------------------------- raids and fires

	/** The odds a town sees a raid off: its defence (and the defenders who came) against the band's strength left. */
	public static double repelOdds(int strength, int defence, double killed, double defenders) {
		double s = Math.max(0, strength - killed);
		double d = defence + 3 * defenders;
		if (s <= 0) {
			return 1;
		}
		return Mth.clamp(d / (s + d + 1.0), 0.02, 0.98);
	}

	static Outcome raid(Scheduled e, EventRule rule, Interventions iv, SplittableRandom r, boolean touched) {
		CompoundTag a = e.args();
		int strength = a.getIntOr("strength", 3);
		int defence = a.getIntOr("defence", 0);
		double killed = iv.get("raiders_killed");
		double repelRoll = r.nextDouble();
		double shareRoll = r.nextDouble();
		double coinRoll = r.nextDouble();
		double burnRoll = r.nextDouble();
		double deathRoll = r.nextDouble();
		int victimIndex = r.nextInt(1 << 20);
		boolean repelled = repelRoll < repelOdds(strength, defence, killed, iv.get("defenders"));
		int[] stores = a.getIntArray("stores").orElse(new int[0]);
		double left = Math.max(0, strength - killed) / (double) Math.max(1, strength);
		double share = repelled ? 0.01 * left : rule.param("store_share", 0.10) * (0.5 + 0.5 * shareRoll) * Math.max(0.3, left);
		int[] lost = new int[stores.length];
		for (int g = 0; g < stores.length; g++) {
			lost[g] = (int) Math.floor(stores[g] * share);
		}
		long treasury = a.getLongOr("treasury", 0);
		long coin = repelled ? 0 : Math.round(treasury * rule.param("coin_share", 0.10) * (0.5 + coinRoll) * Math.max(0.3, left));
		int barn = repelled ? -1 : a.getIntOr("barn", -1);
		double burned = barn < 0 ? 0 : 0.3 + 0.3 * burnRoll;
		List<UUID> deaths = new ArrayList<>();
		if (!repelled && a.getBooleanOr("deaths_allowed", true) && deathRoll < rule.param("death_chance", 0.08)) {
			List<UUID> victims = uuids(a, "victims");
			if (!victims.isEmpty()) {
				deaths.add(victims.get(victimIndex % victims.size()));
			}
		}
		return new Outcome(e.id(), e.kind(), e.day(), repelled ? "repelled" : "sacked", coin, lost, deaths, barn, burned, "", "", touched);
	}

	static Outcome fire(Scheduled e, EventRule rule, Interventions iv, SplittableRandom r, boolean touched) {
		double base = rule.param("burn_min", 0.25) + (rule.param("burn_max", 0.65) - rule.param("burn_min", 0.25)) * r.nextDouble();
		// each player at the buckets saves a share; the brigade alone is already in the base
		double burned = Mth.clamp(base - 0.15 * iv.get("fought"), 0.05, 1);
		return new Outcome(e.id(), e.kind(), e.day(), burned < 0.12 ? "saved" : "burnt", 0, new int[0], List.of(), e.plot(), burned, "", "", touched);
	}

	// ---------------------------------------------------------------- the lord's coin

	/** The tax a town owes: its share of the treasury above the floor (§3.2: 10 % above 60 bits a head). */
	public static long taxOwed(long treasury, int population, double share, long floorPerHead) {
		long above = treasury - floorPerHead * Math.max(0, population);
		return above <= 0 ? 0 : (long) Math.floor(above * share);
	}

	static Outcome tax(Scheduled e, EventRule rule, Interventions iv, boolean touched) {
		CompoundTag a = e.args();
		long owed = taxOwed(a.getLongOr("treasury", 0), a.getIntOr("population", 0), rule.param("share", 0.10), (long) rule.param("floor", 60));
		boolean poor = a.contains("poor_family");
		int[] goods = new int[0];
		String sentence = "";
		if (poor && !iv.flag("paid_for")) {
			goods = lost(Goods.MEAT, (int) rule.param("pig", 4)); // the poor household's pig is led away
			sentence = "pig";
		} else if (poor) {
			sentence = "paid_for";
		}
		return new Outcome(e.id(), e.kind(), e.day(), owed > 0 ? "paid" : "excused", owed, goods, List.of(), -1, 0, "", sentence, touched);
	}

	// ---------------------------------------------------------------- contests

	/** A folk archer's score over ten arrows: hits by their skill, each worth 1–15 (the target's own strength). */
	public static int archerScore(double skill, SplittableRandom r) {
		int score = 0;
		for (int i = 0; i < 10; i++) {
			if (r.nextDouble() < 0.35 + 0.5 * skill) {
				// a hit lands nearer the middle the better the archer
				double off = Math.abs(r.nextGaussian()) * (0.32 - 0.22 * skill);
				score += strength(off);
			}
		}
		return score;
	}

	/** The target block's redstone strength for a hit {@code off} blocks from the middle of its face (as vanilla's target). */
	public static int strength(double off) {
		double d = Math.min(0.5, Math.abs(off));
		return Math.max(1, (int) Math.ceil(15 * Mth.clamp((0.5 - d) / 0.5, 0, 1)));
	}

	/** A folk archer's name and score. */
	public record Archer(String name, int score) {
	}

	/** The folk archers' scores at a fair: the same draw the outcome makes, so the table a player sees is the books'. */
	public static List<Archer> archers(Scheduled e) {
		SplittableRandom r = new SplittableRandom(e.seed() ^ 0x5DEECE66DL);
		List<Archer> out = new ArrayList<>();
		for (Tag t : e.args().getListOrEmpty("competitors")) {
			if (t instanceof CompoundTag c) {
				out.add(new Archer(c.getStringOr("name", ""), archerScore(c.getDoubleOr("skill", 0.4), r)));
			}
		}
		return out;
	}

	static Outcome fair(Scheduled e, Interventions iv, SplittableRandom r, boolean touched) {
		String winner = "";
		int best = -1;
		for (Archer a : archers(e)) {
			if (a.score() > best) {
				best = a.score();
				winner = a.name();
			}
		}
		if (touched && iv.get("archery") > best && iv.bestName() != null) {
			winner = iv.bestName();
		}
		return new Outcome(e.id(), e.kind(), e.day(), winner.isEmpty() ? "held" : "won", 0, new int[0], List.of(), -1, 0, winner, "", touched);
	}

	static Outcome tournament(Scheduled e, Interventions iv, SplittableRandom r, boolean touched) {
		ListTag knights = e.args().getListOrEmpty("knights");
		String winner = "";
		double best = -1;
		for (Tag t : knights) {
			if (t instanceof CompoundTag c) {
				// three passes: lances broken by skill and luck
				double s = 0;
				for (int pass = 0; pass < 3; pass++) {
					s += c.getDoubleOr("skill", 0.5) + r.nextDouble();
				}
				if (s > best) {
					best = s;
					winner = c.getStringOr("name", "");
				}
			}
		}
		if (iv.get("melee") >= 2 && iv.bestName() != null) {
			winner = iv.bestName(); // two of the melee's three rounds won: the purse and the lord's favour
		}
		return new Outcome(e.id(), e.kind(), e.day(), winner.isEmpty() ? "held" : "won", 0, new int[0], List.of(), -1, 0, winner, "", touched);
	}

	// ---------------------------------------------------------------- small helpers

	static int[] lost(Goods g, int units) {
		int[] out = new int[Goods.values().length];
		out[g.ordinal()] = units;
		return out;
	}

	/** Goods gained come as a negative loss. */
	static int[] gained(Goods g, int units) {
		return lost(g, -units);
	}

	public static List<UUID> uuids(CompoundTag a, String key) {
		List<UUID> out = new ArrayList<>();
		for (Tag t : a.getListOrEmpty(key)) {
			try {
				out.add(UUID.fromString(t.asString().orElse("")));
			} catch (IllegalArgumentException ignored) {
				// skip
			}
		}
		return out;
	}

	public static ListTag uuidList(List<UUID> ids) {
		ListTag l = new ListTag();
		for (UUID u : ids) {
			l.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
		}
		return l;
	}
}
