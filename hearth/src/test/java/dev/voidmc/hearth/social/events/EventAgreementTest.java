package dev.voidmc.hearth.social.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/**
 * Abstract and embodied agree (docs/LIVING_WORLD_2.md §3.8, {@code EventAgreementTest}; §12 risk 3): an event nobody
 * touched comes out the same whether the books decide it at its end (an abstract town, or a scene lost to a restart:
 * the event read back from the saved book) or a scene ends it (its interventions empty); and what players do moves the
 * outcome the way the design says.
 */
class EventAgreementTest {
	static final UUID PLAYER = new UUID(9, 9);

	static Scheduled event(EventKind kind, long seed, CompoundTag args, List<UUID> principals, int plot) {
		return new Scheduled(1, kind, 40, 40 * 24000L + 4000, 40 * 24000L + 6000, principals, List.of(), plot, seed, args, Scheduled.Phase.SCHEDULED);
	}

	static Scheduled trial(long seed) {
		CompoundTag a = new CompoundTag();
		a.putString("offence", "theft");
		a.putDouble("evidence", 0.6);
		a.putDouble("leniency", 0.02);
		a.putBoolean("poor", true);
		a.putLong("fine", 15);
		return event(EventKind.TRIAL, seed, a, List.of(new UUID(1, 1)), -1);
	}

	static Scheduled raid(long seed) {
		CompoundTag a = new CompoundTag();
		a.putInt("strength", 6);
		a.putInt("defence", 2);
		int[] stores = new int[Goods.values().length];
		stores[Goods.FOOD.ordinal()] = 400;
		stores[Goods.WOOD.ordinal()] = 200;
		a.putIntArray("stores", stores);
		a.putLong("treasury", 900);
		a.putInt("barn", 4);
		a.putBoolean("deaths_allowed", true);
		ListTag victims = new ListTag();
		victims.add(net.minecraft.nbt.StringTag.valueOf(new UUID(3, 3).toString()));
		a.put("victims", victims);
		return event(EventKind.RAID, seed, a, List.of(), 4);
	}

	static Scheduled tax(long seed) {
		CompoundTag a = new CompoundTag();
		a.putLong("treasury", 3000);
		a.putInt("population", 30);
		a.putString("poor_family", new UUID(5, 5).toString());
		return event(EventKind.TAX, seed, a, List.of(), -1);
	}

	static Scheduled fair(long seed) {
		CompoundTag a = new CompoundTag();
		ListTag comps = new ListTag();
		for (int i = 0; i < 3; i++) {
			CompoundTag c = new CompoundTag();
			c.putString("name", "Archer " + i);
			c.putDouble("skill", 0.3 + 0.2 * i);
			comps.add(c);
		}
		a.put("competitors", comps);
		return event(EventKind.FAIR, seed, a, List.of(), -1);
	}

	static Scheduled tourney(long seed) {
		CompoundTag a = new CompoundTag();
		ListTag knights = new ListTag();
		for (int i = 0; i < 4; i++) {
			CompoundTag c = new CompoundTag();
			c.putString("name", "Sir " + i);
			c.putDouble("skill", 0.4 + 0.1 * i);
			knights.add(c);
		}
		a.put("knights", knights);
		return event(EventKind.TOURNAMENT, seed, a, List.of(), -1);
	}

	static EventRule rule(EventKind k) {
		return EventRules.all().get(k);
	}

	@Test
	void theSameEventUntouchedComesOutTheSameAbstractAndEmbodied() {
		for (long seed = 1; seed < 400; seed++) {
			for (Scheduled e : List.of(trial(seed), raid(seed), tax(seed), fair(seed), tourney(seed), event(EventKind.FIRE, seed, new CompoundTag(), List.of(), 7),
				event(EventKind.EXECUTION, seed, new CompoundTag(), List.of(new UUID(2, 2)), -1), event(EventKind.PILLORY, seed, new CompoundTag(), List.of(), -1),
				event(EventKind.SLAUGHTER_DAY, seed, new CompoundTag(), List.of(), -1))) {
				EventRule r = rule(e.kind());
				Outcome books = Outcomes.resolve(e, r, Interventions.NONE);
				// the scene's own interventions, untouched
				Outcome scene = Outcomes.resolve(e, r, new Interventions());
				assertTrue(books.sameAs(scene), e.kind().id() + " seed " + seed + ": " + books + " vs " + scene);
				// the event as the saved book gives it back after a restart
				var saved = Scheduled.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, e).getOrThrow();
				Scheduled back = Scheduled.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
				assertTrue(books.sameAs(Outcomes.resolve(back, r, Interventions.NONE)), e.kind().id() + " after a restart, seed " + seed);
				assertFalse(books.intervened());
			}
		}
	}

	@Test
	void testimonyMovesAVerdict() {
		int guiltyPlain = 0;
		int guiltyAgainst = 0;
		int guiltyFor = 0;
		for (long seed = 1; seed < 2000; seed++) {
			Scheduled e = trial(seed);
			guiltyPlain += Outcomes.resolve(e, rule(EventKind.TRIAL), Interventions.NONE).result().equals("guilty") ? 1 : 0;
			Interventions against = new Interventions();
			against.add(PLAYER, "testimony", 1);
			guiltyAgainst += Outcomes.resolve(e, rule(EventKind.TRIAL), against).result().equals("guilty") ? 1 : 0;
			Interventions defence = new Interventions();
			defence.add(PLAYER, "testimony", -1);
			guiltyFor += Outcomes.resolve(e, rule(EventKind.TRIAL), defence).result().equals("guilty") ? 1 : 0;
		}
		assertTrue(guiltyAgainst > guiltyPlain + 200, guiltyAgainst + " vs " + guiltyPlain);
		assertTrue(guiltyFor < guiltyPlain - 200, guiltyFor + " vs " + guiltyPlain);
		// a poor thief goes to the pillory, unless a player pays the fine
		for (long seed = 1; seed < 200; seed++) {
			Outcome o = Outcomes.resolve(trial(seed), rule(EventKind.TRIAL), Interventions.NONE);
			if (o.result().equals("guilty")) {
				assertEquals("pillory", o.sentence());
				Interventions paid = new Interventions();
				paid.set(PLAYER, "fine_paid");
				assertEquals("fine", Outcomes.resolve(trial(seed), rule(EventKind.TRIAL), paid).sentence());
			}
		}
	}

	@Test
	void defendersSaveTheStores() {
		int lostPlain = 0;
		int lostDefended = 0;
		int repelledPlain = 0;
		int repelledDefended = 0;
		for (long seed = 1; seed < 1000; seed++) {
			Outcome plain = Outcomes.resolve(raid(seed), rule(EventKind.RAID), Interventions.NONE);
			Interventions iv = new Interventions();
			iv.add(PLAYER, "raiders_killed", 3);
			iv.add(PLAYER, "defenders", 1);
			Outcome defended = Outcomes.resolve(raid(seed), rule(EventKind.RAID), iv);
			lostPlain += plain.goodsLost();
			lostDefended += defended.goodsLost();
			repelledPlain += plain.result().equals("repelled") ? 1 : 0;
			repelledDefended += defended.result().equals("repelled") ? 1 : 0;
			// a sacked town loses at most a tenth of its stores, and a barn
			if (plain.result().equals("sacked")) {
				assertTrue(plain.goods()[Goods.FOOD.ordinal()] <= 40);
				assertEquals(4, plain.plot());
			} else {
				assertEquals(-1, plain.plot());
			}
		}
		assertTrue(lostDefended < lostPlain / 2, lostDefended + " vs " + lostPlain);
		assertTrue(repelledDefended > repelledPlain);
	}

	@Test
	void theFireFoughtBurnsLessAndATaxPaidKeepsThePig() {
		for (long seed = 1; seed < 300; seed++) {
			Scheduled fire = event(EventKind.FIRE, seed, new CompoundTag(), List.of(), 7);
			Interventions iv = new Interventions();
			iv.add(PLAYER, "fought", 2);
			assertTrue(Outcomes.resolve(fire, rule(EventKind.FIRE), iv).burned() < Outcomes.resolve(fire, rule(EventKind.FIRE), Interventions.NONE).burned());
			Outcome taxed = Outcomes.resolve(tax(seed), rule(EventKind.TAX), Interventions.NONE);
			assertEquals("pig", taxed.sentence());
			assertEquals(120, taxed.coin(), "a tenth of the treasury above 60 bits a head");
			Interventions paid = new Interventions();
			paid.set(PLAYER, "paid_for");
			assertEquals(0, Outcomes.resolve(tax(seed), rule(EventKind.TAX), paid).goodsLost());
		}
	}

	@Test
	void theArcheryTableIsTheBooks() {
		for (long seed = 1; seed < 100; seed++) {
			Scheduled e = fair(seed);
			int best = -1;
			String winner = "";
			for (Outcomes.Archer a : Outcomes.archers(e)) {
				assertTrue(a.score() >= 0 && a.score() <= 150);
				if (a.score() > best) {
					best = a.score();
					winner = a.name();
				}
			}
			assertEquals(winner, Outcomes.resolve(e, rule(EventKind.FAIR), Interventions.NONE).winner());
			Interventions iv = new Interventions();
			iv.best(PLAYER, "Jan", "archery", 151);
			assertEquals("Jan", Outcomes.resolve(e, rule(EventKind.FAIR), iv).winner(), "a player who outshoots the folk wins");
		}
		// a hit dead in the middle scores 15, at the rim 1
		assertEquals(15, Outcomes.strength(0));
		assertEquals(1, Outcomes.strength(0.49));
		assertEquals(15, FairScene.hit(new net.minecraft.core.BlockPos(0, 0, 0), new net.minecraft.world.phys.Vec3(1.0, 0.5, 0.5)));
		assertEquals(0, FairScene.hit(new net.minecraft.core.BlockPos(0, 0, 0), new net.minecraft.world.phys.Vec3(3, 0.5, 0.5)));
	}

	@Test
	void mercyAtTheGallows() {
		Scheduled e = event(EventKind.EXECUTION, 5, new CompoundTag(), List.of(new UUID(2, 2)), -1);
		assertEquals("hanged", Outcomes.resolve(e, rule(EventKind.EXECUTION), Interventions.NONE).result());
		assertEquals(List.of(new UUID(2, 2)), Outcomes.resolve(e, rule(EventKind.EXECUTION), Interventions.NONE).deaths());
		Interventions iv = new Interventions();
		iv.set(PLAYER, "mercy");
		Outcome spared = Outcomes.resolve(e, rule(EventKind.EXECUTION), iv);
		assertEquals("commuted", spared.result());
		assertTrue(spared.deaths().isEmpty());
		CompoundTag forced = new CompoundTag();
		forced.putBoolean("forced", true);
		assertTrue(Outcomes.resolve(event(EventKind.EXECUTION, 5, forced, List.of(new UUID(2, 2)), -1), rule(EventKind.EXECUTION), Interventions.NONE).deaths()
			.isEmpty(), "a forced (tour) execution kills nobody");
	}
}
