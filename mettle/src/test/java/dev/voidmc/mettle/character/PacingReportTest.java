package dev.voidmc.mettle.character;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.character.skill.stealth.StealthRules;

import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.character.skill.CombatXp;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import dev.voidmc.mettle.character.skill.athletics.AthleticsRules;
import dev.voidmc.mettle.character.skill.heavy.HeavyRules;
import dev.voidmc.mettle.character.skill.survival.SurvivalRules;
import dev.voidmc.mettle.core.Curves;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The skill-pacing model behind BALANCE.md §1 and §7: a typical survival session, hour by hour, priced with the real
 * per-action XP constants. A report, not a check: it runs only with {@code -Dmettle.balance=true}
 * ({@code ./gradlew test --tests '*ReportTest' -Dmettle.balance=true}) and writes {@code build/reports/mettle-pacing.txt}.
 *
 * <p>The activity profile is an assumption, stated in {@link #PHASES}; change it and the tables follow.
 */
class PacingReportTest {
	/** One hour of play in a phase of the game. Counts are per hour. */
	record Hour(
		// Mining: blocks broken; ore counts are veins' blocks, deepslateShare of them deepslate variants
		double stone, double deepslate, double coal, double copper, double iron, double redstone, double lapis, double gold, double diamond,
		double deepslateShare,
		// Fighting: hostile kills in melee, animals killed in melee, kills with a bow; logs chopped with an axe
		double meleeKills, double animalKills, double bowKills, double logs,
		// Defense: raw damage coming at you (before shield and armour), the share a shield blocks, armour points worn
		double rawDamage, double shieldShare, double armour,
		// Athletics: metres sprinted, swum, climbed; share of the time carrying a Burdened load
		double sprintM, double swimM, double climbM, double burdenedShare,
		// Survival: animals hunted, wild plants picked, food cooked, new biomes, new structures
		double hunted, double plants, double cooked, double biomes, double structures,
		// Smithing: plain (wood/stone/leather) pieces, ingots smelted, iron-tier metal units crafted, diamond units, anvil jobs and
		// their level cost, netherite upgrades
		double plainPieces, double ingots, double ironUnits, double diamondUnits, double anvilJobs, double anvilLevels, double upgrades) {
	}

	/** Hostile mobs average about 19.5 max health (zombie, skeleton, creeper 20; spider 16); animals 10. */
	static final double HOSTILE_HP = 19.5;
	static final double ANIMAL_HP = 10;
	/** Bow kills: three hits from 15 blocks. */
	static final double BOW_HITS = 3;
	static final double BOW_DISTANCE = 15;
	/** A typical incoming hit (zombie 3, skeleton arrow 3–4). */
	static final double HIT = 3.5;

	/**
	 * Hour 1: wood and stone tools, the first night, no armour or shield. Hours 2–3: the iron age (iron armour and a shield
	 * from hour 2; the iron kit, 34 metal units, is crafted now). Hours 4–5: the same, a few replacement tools. Hour 6 on:
	 * the same pace, fewer new places, the diamond kit and repairs.
	 */
	static final Map<String, Hour> PHASES = new LinkedHashMap<>();
	/** Where each phase ends, in hours of play. */
	static final Map<String, Double> PHASE_END = new LinkedHashMap<>();

	static {
		PHASES.put("h1", new Hour(250, 0, 12, 6, 4, 0, 0, 0, 0, 0,
			12, 6, 3, 64,
			40, 0, 0,
			2000, 200, 30, 0,
			6, 10, 8, 4, 0.5,
			6, 8, 3, 0, 0, 0, 0));
		PHASE_END.put("h1", 1.0);
		PHASES.put("h2-3", new Hour(460, 70, 25, 12, 15, 4, 2, 2, 0.5, 0.3,
			30, 4, 10, 64,
			100, 0.4, 15,
			2000, 200, 30, 0.25,
			5, 10, 12, 2, 0.5,
			0.5, 25, 34 / 2.0, 0, 0, 0, 0));
		PHASE_END.put("h2-3", 3.0);
		PHASES.put("h4-5", new Hour(460, 70, 25, 12, 15, 4, 2, 2, 0.5, 0.3,
			30, 4, 10, 64,
			100, 0.4, 15,
			2000, 200, 30, 0.25,
			5, 10, 12, 2, 0.5,
			0, 25, 1.5, 0, 0.5, 3, 0));
		PHASE_END.put("h4-5", 5.0);
		PHASES.put("h6+", new Hour(460, 70, 25, 12, 15, 4, 2, 2, 0.5, 0.3,
			30, 4, 10, 64,
			100, 0.4, 15,
			2000, 200, 30, 0.25,
			5, 10, 12, 0.75, 0.4,
			0, 25, 1.5, 3, 0.5, 5, 0.1));
		PHASE_END.put("h6+", Double.POSITIVE_INFINITY);
	}

	/** XP per hour for each skill, from the real constants. Blades and Heavy are "if it is your melee weapon". */
	static final Map<String, ToDoubleFunction<Hour>> SKILLS = new LinkedHashMap<>();

	static {
		SKILLS.put("Mining", h -> {
			double ore = 1 + (MiningSkill.DEEPSLATE_ORE - 1) * h.deepslateShare();
			return h.stone() * MiningSkill.XP_STONE + h.deepslate() * MiningSkill.XP_DEEPSLATE
				+ ore * (h.coal() * MiningSkill.XP_COMMON_ORE + h.copper() * MiningSkill.XP_COMMON_ORE + h.iron() * MiningSkill.XP_IRON
				+ h.redstone() * MiningSkill.XP_REDSTONE + h.lapis() * MiningSkill.XP_IRON + h.gold() * MiningSkill.XP_GOLD)
				+ h.diamond() * MiningSkill.XP_DIAMOND * MiningSkill.DEEPSLATE_ORE;
		});
		SKILLS.put("Blades (your weapon)", PacingReportTest::melee);
		SKILLS.put("Heavy (your weapon)", h -> melee(h) + h.logs() * HeavyRules.XP_PER_LOG);
		SKILLS.put("Heavy (logs only)", h -> h.logs() * HeavyRules.XP_PER_LOG);
		SKILLS.put("Marksmanship", h -> h.bowKills() * (BOW_HITS * CombatXp.hit(true, HOSTILE_HP / BOW_HITS, BOW_DISTANCE) + CombatXp.kill(HOSTILE_HP)));
		SKILLS.put("Defense", h -> {
			double blocked = h.rawDamage() * h.shieldShare();
			double hit = h.rawDamage() - blocked;
			return CombatXp.defense(blocked, hit * CombatXp.armourFraction(h.armour(), 0, HIT));
		});
		SKILLS.put("Athletics", h -> {
			double light = h.sprintM() * 100 / AthleticsRules.SPRINT_CM_PER_XP + h.swimM() * 100 / AthleticsRules.SWIM_CM_PER_XP
				+ h.climbM() * 100 / AthleticsRules.CLIMB_CM_PER_XP;
			return light * ((1 - h.burdenedShare()) + h.burdenedShare() * AthleticsRules.loadMultiplier(LoadBand.BURDENED));
		});
		SKILLS.put("Survival", h -> h.hunted() * SurvivalRules.XP_ANIMAL + h.plants() * SurvivalRules.XP_PLANT + h.cooked() * SurvivalRules.XP_COOKED
			+ h.biomes() * SurvivalRules.XP_BIOME + h.structures() * SurvivalRules.XP_STRUCTURE);
		SKILLS.put("Smithing", h -> h.plainPieces() * SmithingSkill.XP_PLAIN_PIECE + h.ingots() * SmithingSkill.XP_INGOT
			+ h.ironUnits() * SmithingSkill.XP_PER_UNIT + h.diamondUnits() * SmithingSkill.XP_PER_DIAMOND_UNIT
			+ h.anvilJobs() * (SmithingSkill.XP_ANVIL + SmithingSkill.XP_ANVIL_PER_LEVEL * h.anvilLevels()) + h.upgrades() * SmithingSkill.XP_UPGRADE);
		// Stealth (docs/BUILD_FEEL.md §14.1): a player who hunts by night, every phase alike: 18 minutes of an hour sneaking near
		// hostiles that haven't seen them, 15 of the hour's kills made as sneak attacks on an average hostile.
		SKILLS.put("Stealth (night hunter)", h -> StealthRules.hourly(PacingReportTest.NIGHT_SNEAK_MINUTES, PacingReportTest.NIGHT_SNEAK_KILLS, HOSTILE_HP));
	}

	static final double NIGHT_SNEAK_MINUTES = 18;
	static final double NIGHT_SNEAK_KILLS = 15;

	@Test
	void stealthLandsInItsTarget() {
		double xp = rate("Stealth (night hunter)", "h2-3");
		assertTrue(xp >= 600 && xp <= 750, "BUILD_FEEL.md §14.1 aims Stealth at 600-750 XP an hour for a night hunter: " + xp);
	}

	/** The skills a sword-fighter levels in the mixed session (Heavy only from logs), for character XP. */
	static final String[] MIXED = {"Mining", "Blades (your weapon)", "Heavy (logs only)", "Marksmanship", "Defense", "Athletics", "Survival", "Smithing"};

	static double melee(Hour h) {
		double perHostile = CombatXp.hit(false, HOSTILE_HP, 0) + CombatXp.kill(HOSTILE_HP);
		double perAnimal = (CombatXp.hit(false, ANIMAL_HP, 0) + CombatXp.kill(ANIMAL_HP)) * 0.5;
		return h.meleeKills() * perHostile + h.animalKills() * perAnimal;
	}

	static double rate(String skill, String phase) {
		return SKILLS.get(skill).applyAsDouble(PHASES.get(phase));
	}

	/** Skill XP after {@code hours} of the session. */
	static double xpAt(String skill, double hours) {
		double xp = 0;
		double start = 0;
		for (Map.Entry<String, Double> phase : PHASE_END.entrySet()) {
			double end = Math.min(hours, phase.getValue());
			if (end > start) {
				xp += (end - start) * rate(skill, phase.getKey());
			}
			start = phase.getValue();
			if (start >= hours) {
				break;
			}
		}
		return xp;
	}

	static double hoursTo(String skill, int level, double multiplier) {
		double need = Curves.skillXpFor(level);
		double lo = 0;
		double hi = 5000;
		if (xpAt(skill, hi) * multiplier < need) {
			return Double.POSITIVE_INFINITY;
		}
		for (int i = 0; i < 60; i++) {
			double mid = (lo + hi) / 2;
			if (xpAt(skill, mid) * multiplier >= need) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return hi;
	}

	static String hours(double h) {
		return Double.isInfinite(h) ? "never" : h >= 100 ? String.format(Locale.ROOT, "%.0f", h) : String.format(Locale.ROOT, "%.1f", h);
	}

	@Test
	void report() throws IOException {
		Assumptions.assumeTrue(Boolean.getBoolean("mettle.balance"), "balance report: run with -Dmettle.balance=true");
		StringBuilder out = new StringBuilder();
		for (double m : new double[] {1.0, 1.0 + Progress.FAVOURED_BONUS}) {
			out.append(String.format(Locale.ROOT, "%n## Skill pacing, XP multiplier ×%.2f (%s)%n%n", m, m > 1 ? "favoured" : "not favoured, Wits 3"));
			out.append("| Skill | XP/h hour 1 | hours 2–3 | hours 4–5 | hour 6+ | Level at 1 h | at 3 h | at 5 h | Hours to 6 | to 12 | to 18 | to 20 |\n");
			out.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
			for (String skill : SKILLS.keySet()) {
				out.append(String.format(Locale.ROOT, "| %s | %.0f | %.0f | %.0f | %.0f | %d | %d | %d | %s | %s | %s | %s |%n", skill,
					rate(skill, "h1") * m, rate(skill, "h2-3") * m, rate(skill, "h4-5") * m, rate(skill, "h6+") * m,
					Curves.skillLevel(xpAt(skill, 1) * m), Curves.skillLevel(xpAt(skill, 3) * m), Curves.skillLevel(xpAt(skill, 5) * m),
					hours(hoursTo(skill, 6, m)), hours(hoursTo(skill, 12, m)), hours(hoursTo(skill, 18, m)), hours(hoursTo(skill, 20, m))));
			}
		}

		out.append("\n## Character level and points (mixed session, sword fighter, nothing favoured)\n\n");
		out.append("| Hours | Character XP | Character level | Attribute points to place (background 4 + levels) | Perk points (sum of skill levels) |\n");
		out.append("|---|---|---|---|---|\n");
		for (double t : new double[] {0.5, 1, 2, 3, 5, 10, 15, 20, 30, 40, 60}) {
			double total = 0;
			int perkPoints = 0;
			for (String s : MIXED) {
				total += xpAt(s, t);
				perkPoints += Curves.skillLevel(xpAt(s, t));
			}
			int level = Curves.characterLevel(total);
			out.append(String.format(Locale.ROOT, "| %s | %.0f | %d | %d | %d |%n", hours(t), total, level, 4 + level - 1, perkPoints));
		}

		out.append("\n## Tier gates: when the skill gets there, how many attribute points the character has\n\n");
		out.append("| Skill | Tier II (skill 6, attr 6) at | points then | Tier III (skill 12, attr 10) at | points then | Tier IV (skill 18, attr 14) at | points then |\n");
		out.append("|---|---|---|---|---|---|---|\n");
		for (String skill : SKILLS.keySet()) {
			StringBuilder row = new StringBuilder("| " + skill);
			for (int tier = 2; tier <= 4; tier++) {
				double t = hoursTo(skill, Curves.tierSkillGate(tier), 1.0);
				double total = 0;
				for (String s : MIXED) {
					total += Double.isInfinite(t) ? 0 : xpAt(s, t);
				}
				int level = Curves.characterLevel(total);
				row.append(" | ").append(hours(t)).append(" h | ").append(Double.isInfinite(t) ? "—" : String.valueOf(4 + level - 1));
			}
			out.append(row).append(" |\n");
		}
		out.append("\nAttribute points needed to reach a gate from the base 3: tier II 3, tier III 7, tier IV 11 (minus the background's bonus).\n");

		out.append("\n## Perk points against tree costs (points = skill level)\n\n");
		out.append("| Skill level | Points | Cheapest route to the next tier | Cost so far |\n|---|---|---|---|\n");
		out.append("| 2 | 2 | 2 root ranks (tier I opens) | 2 |\n");
		out.append("| 6 | 6 | roots 4 ranks + first tier II perk (2) | 6 |\n");
		out.append("| 12 | 12 | roots 6 + one tier II (2) + one tier III (3) | 11 |\n");
		out.append("| 18 | 18 | roots 6 + tier II (2) + tier III (3) + mastery (5) | 16 |\n");
		out.append("| 20 | 20 | a full tree costs 31: 20 points buy about two thirds | 20 |\n");

		String text = out.toString();
		System.out.println(text);
		Path file = Path.of("build", "reports", "mettle-pacing.md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		assertTrue(text.contains("Mining"));
	}
}
