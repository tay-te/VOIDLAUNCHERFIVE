package dev.voidmc.mettle.compat.hearth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.api.PriceModifier;
import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.mettle.core.Curves;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * The numbers Mettle hands Hearth (BALANCE.md §9): check odds, market factors, lesson XP, fees and caps, quest and trade
 * XP. The Hearth enums used here are the api copy in src/hearthApi, which is what keeps the tables in step with Hearth.
 */
class HearthRulesTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final double EPS = 1e-9;

	// ------------------------------------------------------------------ checks

	@Test
	void checksAreEvenWhenTheDifficultyMatches() {
		// Speech 5, Presence 10: level + attribute ÷ 2 = 10 meets a fair (10) check at exactly half.
		assertEquals(0.5, HearthRules.checkOdds(5, 10, 10), EPS);
		// Each level is 5 %, each attribute point 2.5 %.
		assertEquals(0.55, HearthRules.checkOdds(6, 10, 10), EPS);
		assertEquals(0.525, HearthRules.checkOdds(5, 11, 10), EPS);
		// A fresh character (level 0, attribute 3) against an easy check.
		assertEquals(0.325, HearthRules.checkOdds(0, 3, 5), EPS);
	}

	@Test
	void checksStayBetweenFiveAndNinetyFive() {
		assertEquals(0.05, HearthRules.checkOdds(0, 1, 25), EPS);
		assertEquals(0.95, HearthRules.checkOdds(20, 20, 1), EPS);
		for (int level = 0; level <= 20; level++) {
			for (int attribute = 1; attribute <= 20; attribute++) {
				for (int difficulty = 1; difficulty <= 25; difficulty++) {
					double odds = HearthRules.checkOdds(level, attribute, difficulty);
					assertTrue(odds >= 0.05 && odds <= 0.95);
					assertTrue(HearthRules.checkOdds(Math.min(20, level + 1), attribute, difficulty) >= odds, "a level never hurts");
				}
			}
		}
	}

	@Test
	void checksFollowTheRuleHearthDocuments() {
		// Hearth's PlayerSkills.odds is "the skills mod's own rule"; NONE uses it with neutral numbers.
		for (int level = 0; level <= 20; level++) {
			for (int attribute = 1; attribute <= 20; attribute++) {
				for (int difficulty = 1; difficulty <= 25; difficulty += 3) {
					assertEquals(PlayerSkills.odds(level, attribute, difficulty), HearthRules.checkOdds(level, attribute, difficulty), EPS);
				}
			}
		}
	}

	// ------------------------------------------------------------------ prices

	@Test
	void anOrdinaryCharacterLeavesPricesAlone() {
		double edge = HearthRules.priceEdge(3, 0, false);
		assertEquals(0, edge, EPS);
		assertEquals(1, HearthRules.priceFactor(edge, true), EPS);
		assertEquals(1, HearthRules.priceFactor(edge, false), EPS);
	}

	@Test
	void presenceSpeechAndCoinSenseMovePricesALittle() {
		assertEquals(0.035, HearthRules.priceEdge(10, 0, false), EPS);   // Presence 10: 3.5 %
		assertEquals(0.05, HearthRules.priceEdge(3, 10, false), EPS);    // Speech 10: 5 %
		assertEquals(0.05, HearthRules.priceEdge(3, 0, true), EPS);      // Coin Sense: 5 %
		assertEquals(0.085, HearthRules.priceEdge(20, 0, false), EPS);
		assertEquals(0.10, HearthRules.priceEdge(3, 20, false), EPS);
		// Everything at once: 23.5 %, under the 25 % ceiling.
		double best = HearthRules.priceEdge(20, 20, true);
		assertEquals(0.235, best, EPS);
		assertEquals(0.765, HearthRules.priceFactor(best, true), EPS);
		assertEquals(1.235, HearthRules.priceFactor(best, false), EPS);
		// A Presence dumped to 1 costs a little.
		assertEquals(-0.01, HearthRules.priceEdge(1, 0, false), EPS);
		assertTrue(HearthRules.priceFactor(HearthRules.priceEdge(1, 0, false), true) > 1, "worse buying");
	}

	@Test
	void factorsStayInsideHearthsClamp() {
		for (int presence = 1; presence <= 20; presence++) {
			for (int speech = 0; speech <= 20; speech++) {
				for (boolean coin : new boolean[] {false, true}) {
					double edge = HearthRules.priceEdge(presence, speech, coin);
					for (boolean buys : new boolean[] {false, true}) {
						double f = HearthRules.priceFactor(edge, buys);
						assertTrue(f >= PriceModifier.MIN && f <= PriceModifier.MAX, "factor " + f);
					}
					// Symmetric: the edge a player gains buying is the edge they gain selling (Hearth keeps the spread).
					assertEquals(2, HearthRules.priceFactor(edge, true) + HearthRules.priceFactor(edge, false), EPS);
				}
			}
		}
	}

	// ------------------------------------------------------------------ trainers

	@Test
	void trainerCapsFollowTheSettlementsTier() {
		assertEquals(List.of(4, 6, 10, 15, 20), Stream.of(SettlementTier.values()).map(t -> HearthRules.trainerCap(t.getSerializedName())).toList());
		assertTrue(HearthRules.canTeach("camp", 3));
		assertFalse(HearthRules.canTeach("camp", 4), "a camp teaches up to 4");
		assertTrue(HearthRules.canTeach("village", 9));
		assertFalse(HearthRules.canTeach("village", 10));
		assertTrue(HearthRules.canTeach("city", 19));
		assertFalse(HearthRules.canTeach("city", 20), "nothing past mastery");
	}

	@Test
	void aLessonIsFortyPercentOfTheNextLevel() {
		for (int level = 0; level < Curves.SKILL_CAP; level++) {
			double gap = Curves.skillXpFor(level + 1) - Curves.skillXpFor(level);
			assertEquals(Math.round(0.4 * gap), HearthRules.lessonXp(level), EPS, "level " + level);
			assertTrue(HearthRules.lessonXp(level) < gap, "one lesson never makes a level");
		}
		assertEquals(0, HearthRules.lessonXp(Curves.SKILL_CAP), EPS);
		assertEquals(4, HearthRules.lessonXp(0), EPS);        // 0 → 1: 10 XP
		assertEquals(447, HearthRules.lessonXp(9), EPS);      // 9 → 10: 1,117 XP
		assertEquals(1474, HearthRules.lessonXp(19), EPS);    // 19 → 20: 3,684 XP
	}

	@Test
	void feesRiseWithTheLevel() {
		assertEquals(20, HearthRules.lessonFee(0));
		assertEquals(40, HearthRules.lessonFee(2), "Train Blades · 40 bits");
		assertEquals(265, HearthRules.lessonFee(9));
		assertEquals(1015, HearthRules.lessonFee(19));
		for (int level = 0; level < 19; level++) {
			assertTrue(HearthRules.lessonFee(level + 1) > HearthRules.lessonFee(level));
			assertEquals(0, HearthRules.lessonFee(level) % 5);
		}
	}

	@Test
	void trainersAndMastersMatchTreesAndHearthsJobs() throws IOException {
		Set<String> jobs = new HashSet<>();
		for (JobType j : JobType.values()) {
			jobs.add(j.getSerializedName());
		}
		for (String job : HearthRules.TRAINERS.keySet()) {
			assertTrue(jobs.contains(job), job + " is a Hearth job");
			for (String skill : HearthRules.trains(job)) {
				assertTrue(Files.isRegularFile(skillFile(skill)), skill + " is a Mettle skill");
			}
		}
		// Every Teaching in the data has a city master, and that master trains the skill too.
		Set<String> taught = new HashSet<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/resources/data/mettle/mettle/perk"))) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject perk = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
				if (perk.has("kind") && perk.get("kind").getAsString().equals("teaching")) {
					taught.add(perk.get("skill").getAsString().replace("mettle:", ""));
				}
			}
		}
		assertEquals(taught, HearthRules.TEACHERS.keySet(), "a master for every Teaching");
		HearthRules.TEACHERS.forEach((skill, job) -> {
			assertTrue(HearthRules.trains(job).contains(skill), job + " trains " + skill);
			assertTrue(HearthRules.teaches(job, "city", skill));
			assertFalse(HearthRules.teaches(job, "town", skill), "only a city's master");
		});
		assertTrue(HearthRules.trains("guard").containsAll(List.of("blades", "defense")));
		assertTrue(HearthRules.trains("farmer").isEmpty());
		for (String s : HearthRules.HEARTH_SKILLS) {
			assertTrue(Files.isRegularFile(skillFile(s)), s + " is registered for Hearth to read");
		}
	}

	private static Path skillFile(String skill) {
		return Path.of("src/main/resources/data/mettle/mettle/skill/" + skill + ".json");
	}

	// ------------------------------------------------------------------ quests and trade

	@Test
	void questsPayByType() {
		assertEquals(new HearthRules.QuestXp(HearthRules.COMBAT, 120), HearthRules.questXp("bounty", "bounty/spiders"));
		assertEquals(new HearthRules.QuestXp("athletics", 100), HearthRules.questXp("courier", "courier/parcel"));
		assertEquals(new HearthRules.QuestXp("athletics", 100), HearthRules.questXp("escort", "escort/pack_mule"));
		assertEquals(new HearthRules.QuestXp("mining", 80), HearthRules.questXp("supply", "supply/ore"));
		assertEquals(new HearthRules.QuestXp("mining", 80), HearthRules.questXp("supply", "supply/dressed_stone"));
		assertEquals(new HearthRules.QuestXp("smithing", 80), HearthRules.questXp("supply", "supply/tools"));
		assertEquals(new HearthRules.QuestXp("survival", 80), HearthRules.questXp("supply", "supply/grain"));
		assertEquals(new HearthRules.QuestXp("survival", 80), HearthRules.questXp("contract", "contract/standing_order"));
		assertEquals(new HearthRules.QuestXp("mining", 80), HearthRules.questXp("contract", "contract/smelter"));
		assertEquals(new HearthRules.QuestXp("survival", 150), HearthRules.questXp("exploration", "exploration/frostwatch"));
		assertEquals(new HearthRules.QuestXp("survival", 150), HearthRules.questXp("scouting", "scouting/new_hamlet"));
		assertEquals(new HearthRules.QuestXp("survival", 60), HearthRules.questXp("gathering", "gathering/honey"));
		assertEquals(new HearthRules.QuestXp("speech", 80), HearthRules.questXp("personal", "personal/letter"));
		assertEquals(new HearthRules.QuestXp(HearthRules.COMBAT, 120), HearthRules.questXp("law", "law/outlaw_warrant"));
		assertEquals(new HearthRules.QuestXp(HearthRules.COMBAT, 120), HearthRules.questXp("caravan", "caravan/bandit_hunt"));
		assertEquals(new HearthRules.QuestXp(HearthRules.COMBAT, 100), HearthRules.questXp("disaster", "disaster/wolves_at_the_pasture"));
		assertEquals(new HearthRules.QuestXp("mining", 100), HearthRules.questXp("disaster", "disaster/mine_rescue"));
		assertEquals(new HearthRules.QuestXp("survival", 100), HearthRules.questXp("disaster", "disaster/bucket_brigade"));
		assertEquals(new HearthRules.QuestXp(HearthRules.COMBAT, 80), HearthRules.questXp("official", "official/guard/night_patrol"));
		assertEquals(new HearthRules.QuestXp("mining", 80), HearthRules.questXp("official", "official/builder/haul_stone"));
		assertEquals(new HearthRules.QuestXp("speech", 80), HearthRules.questXp("official", "official/priest/alms"));
		assertEquals(new HearthRules.QuestXp("stealth", 100), HearthRules.questXp("crime", "crime/rob_caravan"));
	}

	@Test
	void everyQuestTypeHasARewardInTheDocumentedRange() {
		for (QuestType type : QuestType.values()) {
			HearthRules.QuestXp xp = HearthRules.questXp(type.getSerializedName(), type.getSerializedName() + "/any");
			assertTrue(xp.xp() >= 60 && xp.xp() <= 200, type + " pays " + xp.xp());
			assertTrue(xp.skill().equals(HearthRules.COMBAT) || Files.isRegularFile(skillFile(xp.skill())), type + " → " + xp.skill());
		}
	}

	@Test
	void businessPaysSpeechAndTapersAfterTwentyCrowns() {
		assertEquals(5, HearthRules.tradeXp(0, 1), EPS);            // a crown of business
		assertEquals(0.5, HearthRules.tradeXp(0, 0.1), EPS);        // ten bits
		assertEquals(100, HearthRules.tradeXp(0, 20), EPS);
		assertEquals(100 + 10, HearthRules.tradeXp(0, 30), EPS);    // ten crowns past the line at 1 a crown
		assertEquals(5 * 5 + 5, HearthRules.tradeXp(15, 10), EPS);  // straddles the line
		assertEquals(3, HearthRules.tradeXp(40, 3), EPS);
		assertEquals(0, HearthRules.tradeXp(0, 0), EPS);
	}
}
