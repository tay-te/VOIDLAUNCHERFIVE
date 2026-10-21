package dev.voidmc.hearth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.economy.broker.Broker;
import dev.voidmc.hearth.economy.broker.BrokerBoard;
import dev.voidmc.hearth.economy.broker.BrokerDialogue;
import dev.voidmc.hearth.economy.haggle.Haggle;
import dev.voidmc.hearth.economy.haggle.Temper;
import dev.voidmc.hearth.law.LawSkills;
import dev.voidmc.hearth.law.stealth.Detection;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import dev.voidmc.hearth.npc.talk.Legends;
import dev.voidmc.hearth.standing.StandingEngine;
import dev.voidmc.hearth.standing.speech.SpeechKind;
import dev.voidmc.hearth.standing.speech.SpeechOdds;
import dev.voidmc.hearth.standing.speech.SpeechRetries;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * VOID Mettle's requests 7–14 (docs/requests/mettle.md, "Queued 2026-10-21"): with {@link PlayerSkills#NONE} nothing
 * at any of Hearth's decision points changes; a fake skills mod's named bonuses move each one, within Hearth's clamps.
 */
class MettleBonusesTest {
	/** A skills mod that answers fixed bonuses. */
	private static class Fake implements PlayerSkills {
		final Map<String, Double> bonuses = new HashMap<>();
		String worn = "";
		List<Identifier> mythics = List.of();

		Fake with(String key, double v) {
			this.bonuses.put(key, v);
			return this;
		}

		@Override
		public int level(ServerPlayer player, String skill) {
			return 0;
		}

		@Override
		public int attribute(ServerPlayer player, String attribute) {
			return UNKNOWN_ATTRIBUTE;
		}

		@Override
		public boolean hasPerk(ServerPlayer player, Identifier perk) {
			return false;
		}

		@Override
		public Check check(ServerPlayer player, String skill, int difficulty) {
			return new Check(0.5, 0.5);
		}

		@Override
		public void award(ServerPlayer player, String skill, float xp, String reason) {
		}

		@Override
		public double bonus(ServerPlayer player, String key) {
			return this.bonuses.getOrDefault(key, 0.0);
		}

		@Override
		public List<Identifier> titles(ServerPlayer player) {
			return this.mythics;
		}

		@Override
		public Component title(ServerPlayer player) {
			return Component.literal(this.worn);
		}
	}

	@AfterEach
	void reset() {
		HearthServices.setPlayerSkills(null);
		HearthServices.clearVirtualGoods();
	}

	@Test
	void noneAnswersNothing() {
		assertEquals(0, PlayerSkills.NONE.bonus(null, PlayerSkills.BONUS_STEALTH_SIGHT), 0.0);
		assertTrue(PlayerSkills.NONE.titles(null).isEmpty());
		assertEquals("", PlayerSkills.NONE.title(null).getString());
		assertEquals(0, PlayerSkills.bonus(PlayerSkills.NONE, null, PlayerSkills.BONUS_HAGGLE_BAND, 0, 10), 0.0);
		// a broken partner never breaks a check: NaN and exceptions read as 0, the rest is clamped
		PlayerSkills broken = new Fake() {
			@Override
			public double bonus(ServerPlayer player, String key) {
				throw new IllegalStateException();
			}
		};
		assertEquals(0, PlayerSkills.bonus(broken, null, "x", 0, 1), 0.0);
		assertEquals(0, PlayerSkills.bonus(new Fake().with("x", Double.NaN), null, "x", 0, 1), 0.0);
		assertEquals(1, PlayerSkills.bonus(new Fake().with("x", 7), null, "x", 0, 1), 0.0);
		assertEquals(0, PlayerSkills.bonus(new Fake().with("x", -3), null, "x", 0, 1), 0.0);
	}

	// ---------------------------------------------------------------- haggling

	@Test
	void haggleBandPatienceAndNeverSour() {
		assertEquals(40.0, Temper.patience(40.0, 0), 0.0);
		assertEquals(5, Temper.bandSpeech(5, 0));
		assertFalse(Temper.neverSour(0));
		assertNull(Haggle.band(100, 80, Temper.bandSpeech(3, 0)), "Speech 3 alone reads no band");
		// Shrewd Eye 5: the band opens at Speech 3
		assertNotNull(Haggle.band(100, 80, Temper.bandSpeech(3, 5)));
		assertEquals(20, Temper.bandSpeech(18, 5));
		assertEquals(13, Temper.bandSpeech(3, 99), "clamped to 10 levels");
		assertEquals(46.0, Temper.patience(40.0, 0.15), 1e-9);
		assertEquals(60.0, Temper.patience(40.0, 3), 1e-9, "clamped to half again");
		assertTrue(Temper.neverSour(1));
	}

	// ---------------------------------------------------------------- speech: conversations and the law

	private static SpeechOdds.Inputs in(double bonus) {
		return new SpeechOdds.Inputs(10, 5, 0.2, 0.3, Set.of("cheerful"), 0, Station.ARTISAN, JobType.SMITH, 0.2, 1.0, 0, 0, 3, bonus);
	}

	@Test
	void speechOddsPerKind() {
		for (SpeechKind kind : SpeechKind.values()) {
			SpeechOdds.Inputs old = new SpeechOdds.Inputs(10, 5, 0.2, 0.3, Set.of("cheerful"), 0, Station.ARTISAN, JobType.SMITH, 0.2, 1.0, 0, 0, 3);
			assertEquals(SpeechOdds.of(kind, old, 0.05, 0.95), SpeechOdds.of(kind, in(0), 0.05, 0.95), kind.name());
			assertEquals(SpeechOdds.of(kind, old, 0, 1).chance() + 0.08, SpeechOdds.of(kind, in(0.08), 0, 1).chance(), 1e-9, kind.name());
			assertEquals(SpeechOdds.of(kind, old, 0, 1).chance() + SpeechOdds.MAX_BONUS, SpeechOdds.of(kind, in(5), 0, 1).chance(), 1e-9);
			assertTrue(SpeechOdds.of(kind, in(0.08), 0, 1).factors().stream().anyMatch(f -> f.key().equals("perk")));
			assertTrue(SpeechOdds.of(kind, in(0.9), 0.05, 0.95).chance() <= 0.95, "Hearth's clamp holds");
		}
	}

	@Test
	void lawSpeechPerKind() {
		assertEquals(0.4, LawSkills.speech((ServerPlayer) null, "persuade", 0.4), 0.0, "NONE: as before");
		assertEquals(LawSkills.speech((ServerPlayer) null, 0.4), LawSkills.speech((ServerPlayer) null, "bribe", 0.4), 0.0);
		HearthServices.setPlayerSkills(new Fake().with(PlayerSkills.BONUS_SPEECH + "intimidate", 0.06));
		assertEquals(0.46, LawSkills.speech((ServerPlayer) null, "intimidate", 0.4), 1e-9);
		assertEquals(0.4, LawSkills.speech((ServerPlayer) null, "persuade", 0.4), 0.0, "only the kind that has it");
		assertEquals(0.92, LawSkills.withBonus(0.9, 0.15), 1e-9);
		assertEquals(0.55, LawSkills.withBonus(0.4, 1), 1e-9, "clamped to 15 %");
		assertEquals("intimidate", LawSkills.kindOf("threaten_witness"));
		assertEquals("bribe", LawSkills.kindOf("bribe_search"));
		assertEquals("persuade", LawSkills.kindOf("persuade_guard"));
	}

	@Test
	void secondWordRetries() {
		assertEquals(0, SpeechRetries.budget(0));
		assertEquals(1, SpeechRetries.budget(1.7));
		assertEquals(SpeechRetries.MAX, SpeechRetries.budget(40));
		SpeechRetries.Ledger ledger = new SpeechRetries.Ledger();
		UUID p = UUID.randomUUID();
		assertFalse(ledger.take(p, 3, 0), "without the bonus nothing is taken back");
		assertTrue(ledger.take(p, 3, 1));
		assertFalse(ledger.take(p, 3, 1), "one a day");
		assertTrue(ledger.take(p, 4, 1), "a new day");
		assertFalse(LawSkills.retry(null, 1), "NONE: the law gives no retries");
	}

	@Test
	void checkDifficultyIsThePlainCheckScale() {
		for (int d = 1; d <= 11; d++) {
			assertEquals(d, StandingEvents.difficulty(PlayerSkills.odds(0, 3, d)), "difficulty " + d);
		}
		assertEquals(1, StandingEvents.difficulty(0.95));
		int[] seen = new int[1];
		StandingEvents.SpeechChecked l = (pl, s, kind, passed, diff) -> seen[0] = diff;
		l.onSpeechCheck(null, null, "persuade", true, 7);
		assertEquals(7, seen[0]);
	}

	// ---------------------------------------------------------------- standing, stealth, guards, pockets

	@Test
	void standingGainOnlyOnPositiveDeltas() {
		assertEquals(10, StandingEngine.gain(10, 0), 0.0);
		assertEquals(12.5, StandingEngine.gain(10, 0.25), 1e-9);
		assertEquals(15, StandingEngine.gain(10, 9), 1e-9, "clamped to half again");
		assertEquals(-10, StandingEngine.gain(-10, 0.5), 0.0, "losses untouched");
	}

	@Test
	void stealthSightAndUnseenBeyond() {
		Detection.Stance old = new Detection.Stance(5, true, false, true, 0, false, 8);
		Detection.Stance zero = new Detection.Stance(5, true, false, true, 0, false, 8, 0, 0);
		Detection.Observer o = Detection.Observer.ORDINARY;
		assertEquals(Detection.sightRange(16, 1, old, o), Detection.sightRange(16, 1, zero, o), 0.0);
		Detection.Stance cloaked = new Detection.Stance(5, true, false, true, 0, false, 8, 0.25, 0);
		assertEquals(0.75 * Detection.sightRange(16, 1, old, o), Detection.sightRange(16, 1, cloaked, o), 1e-9);
		Detection.Stance walking = new Detection.Stance(5, false, false, true, 0, false, 8, 0.25, 4);
		assertEquals(Detection.sightRange(16, 1, new Detection.Stance(5, false, false, true, 0, false, 8), o), Detection.sightRange(16, 1, walking, o), 0.0,
			"only a sneak");
		Detection.Stance ghost = new Detection.Stance(15, true, false, true, 0, false, 0, 0, 4);
		assertEquals(4, Detection.sightRange(32, 1, ghost, o), 1e-9);
		assertEquals(0, Detection.sight(5, true, 32, 1, ghost, o), 0.0, "beyond 4 blocks: unseen");
		assertTrue(Detection.sight(2, true, 32, 1, ghost, o) > 0);
		assertEquals(0, LawSkills.unseenBeyond(0), 0.0);
		assertEquals(LawSkills.MIN_UNSEEN, LawSkills.unseenBeyond(1), 0.0);
		assertEquals(LawSkills.MAX_UNSEEN, LawSkills.unseenBeyond(500), 0.0);
		assertEquals(0, LawSkills.sightBonus(null), 0.0);
		assertEquals(0, LawSkills.unseenBeyond((ServerPlayer) null), 0.0);
		HearthServices.setPlayerSkills(new Fake().with(PlayerSkills.BONUS_STEALTH_SIGHT, 0.9).with(PlayerSkills.BONUS_STEALTH_UNSEEN_BEYOND, 4));
		assertEquals(0.5, LawSkills.sightBonus(null), 0.0);
		assertEquals(4, LawSkills.unseenBeyond((ServerPlayer) null), 0.0);
	}

	@Test
	void guardsGiveUpAndMarksNotice() {
		assertEquals(240, LawSkills.searchTicks(240, 0));
		assertEquals(240, LawSkills.searchTicks(null, 240), "NONE");
		assertEquals(161, LawSkills.searchTicks(240, 0.33));
		assertEquals(60, LawSkills.searchTicks(240, 2), "clamped to three quarters");
		assertEquals(0.3, LawSkills.awareness(null, 0.3), 0.0, "NONE");
		HearthServices.setPlayerSkills(new Fake().with(PlayerSkills.BONUS_STEALTH_GIVE_UP, 0.33).with(PlayerSkills.BONUS_PICKPOCKET_AWARENESS, 0.1));
		assertEquals(161, LawSkills.searchTicks(null, 240));
		assertEquals(0.27, LawSkills.awareness(null, 0.3), 1e-9);
		assertEquals(0.15, LawSkills.awareness(0.3, 0.9), 1e-9, "clamped to half");
	}

	// ---------------------------------------------------------------- greetings

	private static DialogueTemplates.Context ctx() {
		return new DialogueTemplates.Context(600, "clear", 0, EnumSet.noneOf(Trait.class), JobType.NONE, Culture.TEMPERATE, AgeStage.ADULT, 0, 0);
	}

	@Test
	void legendsGreetOnlyWhatIsKnown() {
		DialogueTemplates.replace(DialogueTemplates.loadBuiltin());
		Map<String, String> v = Map.of("player", "Ada", "settlement", "Ashby");
		RandomSource a = RandomSource.create(9);
		RandomSource b = RandomSource.create(9);
		assertNull(Legends.line(Legends.Fame.NONE, ctx(), v, a));
		assertEquals(b.nextLong(), a.nextLong(), "nothing known: no random number drawn, so greetings are as before");
		assertFalse(Legends.of(null).any(), "NONE");
		Fake fake = new Fake();
		fake.worn = "the Deep Ember Warden";
		fake.mythics = List.of(Identifier.fromNamespaceAndPath("mettle", "mythic/dragon_fall"));
		fake.with(PlayerSkills.BONUS_GREET_HERO, 1);
		HearthServices.setPlayerSkills(fake);
		Legends.Fame fame = Legends.of(null);
		assertTrue(fame.hero() && fame.title().equals("the Deep Ember Warden") && fame.mythics().size() == 1);
		RandomSource r = RandomSource.create(3);
		boolean title = false;
		boolean mythic = false;
		int said = 0;
		for (int k = 0; k < 200; k++) {
			String line = Legends.line(fame, ctx(), v, r);
			if (line != null) {
				said++;
				assertFalse(line.contains("{"), line);
				title |= line.contains("the Deep Ember Warden");
				mythic |= line.contains("Dragon Fall");
			}
		}
		assertTrue(said > 50 && said < 150, "about one greeting in two: " + said);
		assertTrue(title && mythic);
		assertEquals("Dragon Fall", Legends.mythicName(Identifier.fromNamespaceAndPath("mettle", "mythic/dragon_fall")));
	}

	// ---------------------------------------------------------------- coin between players, the broker

	@Test
	void transferRefusesWithoutMovingAnything() {
		long[] coin = {100, 0};
		boolean[] room = {false};
		EconomyService eco = new EconomyService() {
			@Override
			public long balance(ServerPlayer player) {
				return coin[0];
			}

			@Override
			public boolean charge(ServerPlayer player, long bits) {
				if (coin[0] < bits) {
					return false;
				}
				coin[0] -= bits;
				return true;
			}

			@Override
			public void pay(ServerPlayer player, long bits) {
				coin[1] += bits;
			}

			@Override
			public boolean canReceive(ServerPlayer player, long bits) {
				return room[0];
			}

			@Override
			public Component format(long bits) {
				return Component.literal(Long.toString(bits));
			}

			@Override
			public long price(Settlement settlement, ItemStack stack, boolean playerBuys) {
				return 0;
			}
		};
		assertTrue(eco.transfer(null, null, 0), "nothing to move");
		assertFalse(eco.transfer(null, null, -5));
		// to oneself: nothing moves, and it "works" only if they could pay
		assertTrue(eco.transfer(null, null, 30));
		assertFalse(eco.transfer(null, null, 300));
		assertEquals(100, coin[0]);
		assertEquals(0, coin[1]);
		EconomyService plain = new EconomyService() {
			@Override
			public long balance(ServerPlayer player) {
				return 0;
			}

			@Override
			public boolean charge(ServerPlayer player, long bits) {
				return false;
			}

			@Override
			public void pay(ServerPlayer player, long bits) {
			}

			@Override
			public Component format(long bits) {
				return Component.empty();
			}

			@Override
			public long price(Settlement settlement, ItemStack stack, boolean playerBuys) {
				return 0;
			}
		};
		assertTrue(plain.canReceive(null, 1_000_000), "an economy that cannot tell assumes room");
	}

	@Test
	void brokerBoardFeesAndPrices() {
		assertEquals(1, Broker.fee(10));
		assertEquals(20, Broker.fee(1000));
		assertEquals(50, Broker.tax(1000));
		BrokerBoard board = new BrokerBoard();
		long a = board.nextId(7);
		long b = board.nextId(7);
		assertTrue(a != b && board.nextId(8) != a);
		UUID ada = UUID.randomUUID();
		UUID bo = UUID.randomUUID();
		board.add(new BrokerBoard.Listing(a, "mettle:card", 11, ada, "Ada", 300, 0, Broker.TERM_DAYS));
		board.add(new BrokerBoard.Listing(b, "mettle:card", 12, ada, "Ada", 120, 0, 2));
		assertTrue(board.listed("mettle:card", 11));
		assertEquals(List.of(), board.forBuyer(ada, 0), "not one's own");
		assertEquals(b, board.forBuyer(bo, 0).get(0).id(), "cheapest first");
		assertEquals(1, board.search("tunn", l -> l.goods() == 11 ? "Tunneler · Rare" : "Smith · Common", 0).size());
		assertEquals(300, BrokerDialogue.goingRate(board, "mettle:card", 2), "the lapsed one does not count");
		BrokerBoard back = BrokerBoard.CODEC.parse(JsonOps.INSTANCE, BrokerBoard.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow()).getOrThrow();
		assertEquals(board.all(), back.all());
		assertTrue(back.nextId(7) != b, "the count is saved");
		assertEquals(List.of(b), board.expire(2).stream().map(BrokerBoard.Listing::id).toList());
		assertEquals(1, board.size());
		assertEquals(125, BrokerDialogue.step(100, true));
		assertEquals(80, BrokerDialogue.step(100, false));
		assertEquals(2, BrokerDialogue.step(1, true));
		assertEquals(1, BrokerDialogue.step(1, false));
		assertEquals(100, BrokerDialogue.goingRate(new BrokerBoard(), "mettle:card", 0));
	}
}
