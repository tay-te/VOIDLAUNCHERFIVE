package dev.voidmc.hearth.standing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StandingService.Deed;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.standing.model.Companies;
import dev.voidmc.hearth.standing.model.Memories;
import dev.voidmc.hearth.standing.model.TownLedger;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandingEngineTest {
	private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");
	private static final UUID BEA = UUID.fromString("00000000-0000-0000-0000-0000000000be");

	private FakeHost host;
	private StandingEngine engine;
	private StandingConfig config;
	private SimpleSettlement ashbrook;

	@BeforeEach
	void setUp() {
		StandingTestKit.useShippedRules();
		this.config = StandingConfig.DEFAULTS;
		this.host = new FakeHost();
		this.engine = new StandingEngine(this.host, () -> this.config);
		this.ashbrook = this.host.town(1, "Ashbrook", 0, 0);
	}

	private Deed deed(String kind, int weight, Resident victim, boolean witnessed) {
		return new Deed(ALEX, this.ashbrook.id(), kind, weight, victim == null ? null : victim.id, new BlockPos(5, 64, 5), witnessed);
	}

	@Test
	void seedsFromTheSettlementsOldNumberAndMirrorsChangesBack() {
		// mirroring is off in the shipped config since the sim delegates to this service; a server may still turn it on
		StandingConfig.Town t = StandingConfig.Town.DEFAULTS;
		this.config = new StandingConfig(StandingConfig.Titles.DEFAULTS, new StandingConfig.Town(t.softCap(), t.negativeDriftPerDay(),
			t.positiveDriftPerDay(), t.unwitnessedBad(), t.unwitnessedGood(), t.seedFromSettlement(), true, t.newsPerTown(), t.newsMinWeight(),
			t.arrivalLine(), t.trackInterval()), StandingConfig.Opinion.DEFAULTS, StandingConfig.Gossip.DEFAULTS, StandingConfig.Fame.DEFAULTS,
			StandingConfig.Appearance.DEFAULTS, StandingConfig.Speech.DEFAULTS, StandingConfig.Companies.DEFAULTS);
		this.ashbrook.addReputation(ALEX, 30);
		assertEquals(30, this.engine.standing(1, ALEX), "a player with no entry reads the sim's number");
		this.engine.deed(deed("quest:completed", 10, null, true));
		int now = this.engine.standing(1, ALEX);
		assertTrue(now > 30 && now < 40, "softened above 0: " + now);
		assertEquals(now, this.ashbrook.reputation(ALEX), "the sim's number follows");
	}

	/** A sim that hands reputation to the standing service, as the integrated sim will. */
	private static final class DelegatingTown extends SimpleSettlement {
		StandingEngine engine;
		int raw;

		DelegatingTown(long id) {
			super(id, "Delegate", Culture.TEMPERATE, new BlockPos(500, 64, 0), 0);
		}

		@Override
		public int reputation(UUID player) {
			return this.engine.standing(id(), player);
		}

		@Override
		public void addReputation(UUID player, int delta) {
			this.raw += delta;
			this.engine.deed(new Deed(player, id(), Deeds.ADJUST, delta, null, center(), false));
		}
	}

	@Test
	void delegationNeitherRecursesNorCountsTwice() {
		DelegatingTown t = this.host.add(new DelegatingTown(7));
		t.engine = this.engine;
		assertEquals(0, t.reputation(ALEX));
		// Another module calling the old api lands as an exact adjustment.
		t.addReputation(ALEX, 12);
		assertEquals(12, this.engine.standing(7, ALEX));
		// A deed through the service mirrors into the sim, whose echo is ignored.
		this.engine.deed(new Deed(ALEX, 7, "economy:donation", 10, null, t.center(), true));
		int after = this.engine.standing(7, ALEX);
		assertTrue(after > 12 && after <= 22, "donation counted once: " + after);
		assertEquals(after, t.reputation(ALEX));
	}

	@Test
	void seedDeedHandsOverOnce() {
		this.engine.deed(new Deed(ALEX, 1, Deeds.SEED, 55, null, BlockPos.ZERO, false));
		this.engine.deed(new Deed(ALEX, 1, Deeds.SEED, -20, null, BlockPos.ZERO, false));
		assertEquals(55, this.engine.standing(1, ALEX));
	}

	@Test
	void theftMovesTownVictimFamilyAndWitnesses() {
		Resident edda = StandingTestKit.resident("Edda", JobType.BAKER);
		Resident tom = StandingTestKit.resident("Tom", JobType.FARMER);
		Resident watcher = StandingTestKit.resident("Ulf", JobType.GUARD);
		Resident stranger = StandingTestKit.resident("Ida", JobType.SMITH);
		StandingTestKit.marry(edda, tom);
		for (Resident r : new Resident[] {edda, tom, watcher, stranger}) {
			this.ashbrook.addResident(r);
		}
		this.host.witnesses.add(watcher.id);
		this.engine.deed(deed("law:theft", -30, edda, true));

		assertEquals(-30, this.engine.standing(1, ALEX));
		assertTrue(this.engine.opinion(edda.id, ALEX) <= -45 + -30 / 4 + 1, "victim: " + this.engine.opinion(edda.id, ALEX));
		assertTrue(this.engine.opinion(tom.id, ALEX) < this.engine.opinion(stranger.id, ALEX), "family remembers");
		assertTrue(this.engine.opinion(watcher.id, ALEX) < this.engine.opinion(stranger.id, ALEX), "witness remembers");
		assertEquals(-30 / 4 - 1, this.engine.opinion(stranger.id, ALEX), "others only feel the town's view");
		assertEquals(Title.DISTRUSTED, this.engine.title(1, ALEX));
		assertTrue(this.host.events.contains("Ashbrook:stranger->distrusted"));
		assertFalse(StandingEngine.ledger(this.ashbrook).newsOf(ALEX).isEmpty(), "folk talk about it");
	}

	@Test
	void unwitnessedCrimeTouchesOnlyTheVictim() {
		Resident edda = StandingTestKit.resident("Edda", JobType.BAKER);
		this.ashbrook.addResident(edda);
		this.engine.deed(deed("law:theft", -30, edda, false));
		assertEquals(0, this.engine.standing(1, ALEX));
		assertTrue(this.engine.opinion(edda.id, ALEX) < -30);
		assertTrue(this.host.region.road().isEmpty(), "no gossip about what nobody saw");
		assertEquals(0, this.host.region.fameAt(ALEX, 5, 5, this.host.day, 2000, 60));
		// Discovered later and reported again: the town learns, the victim does not count it twice.
		int victim = this.engine.opinion(edda.id, ALEX);
		this.engine.deed(deed("law:theft", -30, edda, true));
		assertEquals(-30, this.engine.standing(1, ALEX));
		assertEquals(victim - 30 / 4 - 1, this.engine.opinion(edda.id, ALEX), "same memory, only the town's colour changes");
	}

	@Test
	void gossipTravelsTheRoadsAndArrivesLaterAndFainter() {
		SimpleSettlement near = this.host.town(2, "Coldbrook", 900, 0);
		SimpleSettlement far = this.host.town(3, "Farwater", 1800, 0);
		this.engine.deed(deed("defense:raid_repelled", 25, null, true));
		assertEquals(2, this.host.region.road().size());
		assertEquals(0, this.engine.standing(2, ALEX), "news has not arrived yet");
		this.host.day += 1;
		this.engine.deliver();
		int coldbrook = this.engine.standing(2, ALEX);
		assertTrue(coldbrook > 0, "the neighbour hears first");
		assertEquals(0, this.engine.standing(3, ALEX), "the far town not yet");
		this.host.day += 1;
		this.engine.deliver();
		int farwater = this.engine.standing(3, ALEX);
		assertTrue(farwater > 0 && farwater < coldbrook, "fainter further away: " + farwater + " < " + coldbrook);
		assertTrue(this.host.region.road().isEmpty());
		assertTrue(this.host.told.stream().anyMatch(s -> s.startsWith("Coldbrook<-Ashbrook")));
		assertFalse(StandingEngine.ledger(near).news().getFirst().local());
		assertFalse(StandingEngine.ledger(far).news().isEmpty());
	}

	@Test
	void newsWaitsForATownNotYetSettled() {
		this.host.unsettled.add(new HearthWorld.SiteInfo(9, "Newfold", Culture.TEMPERATE, new BlockPos(800, 64, 0), SettlementTier.HAMLET, false));
		this.engine.deed(deed("law:murder", -100, null, true));
		this.host.day += 5;
		this.engine.deliver();
		assertEquals(1, this.host.region.held().size());
		SimpleSettlement newfold = this.host.town(9, "Newfold", 800, 0);
		this.engine.settled(newfold);
		assertTrue(this.engine.standing(9, ALEX) < -20, "the murder is known on arrival: " + this.engine.standing(9, ALEX));
		assertTrue(this.host.region.held().isEmpty());
	}

	@Test
	void companiesShareAFraction() {
		Companies.form(this.host.region, ALEX, "The Grey Company", 1, this.config.companies());
		Companies.invite(this.host.region, ALEX, BEA, this.config.companies());
		assertEquals(Companies.Result.JOINED, Companies.join(this.host.region, BEA, "the grey company", this.config.companies()));
		this.engine.deed(deed("economy:donation", 20, null, true));
		assertEquals(20, this.engine.standing(1, ALEX));
		assertEquals(5, this.engine.standing(1, BEA), "a quarter of it");
		assertEquals("The Grey Company", this.engine.company(BEA));
	}

	@Test
	void fameIsLocalAndFades() {
		this.engine.deed(deed("defense:raid_repelled", 40, null, true));
		int here = this.engine.fame(ALEX, new BlockPos(5, 64, 5));
		int away = this.engine.fame(ALEX, new BlockPos(1500, 64, 0));
		int gone = this.engine.fame(ALEX, new BlockPos(5000, 64, 0));
		assertTrue(here > away && away > 0, here + " > " + away);
		assertEquals(0, gone);
		this.host.day += 120;
		assertTrue(this.engine.fame(ALEX, new BlockPos(5, 64, 5)) < here / 3);
	}

	@Test
	void badNamesHealSlowly() {
		this.engine.set(this.ashbrook, ALEX, -10);
		for (int i = 0; i < 20; i++) {
			this.engine.dayPassed(this.ashbrook, this.host.day);
		}
		assertEquals(-5, this.engine.standing(1, ALEX));
		this.engine.set(this.ashbrook, ALEX, 30);
		this.engine.dayPassed(this.ashbrook, this.host.day);
		assertEquals(30, this.engine.standing(1, ALEX), "good names keep by default");
	}

	@Test
	void adjustIsExact() {
		this.engine.set(this.ashbrook, ALEX, 90);
		this.engine.deed(new Deed(ALEX, 1, Deeds.ADJUST, 5, null, BlockPos.ZERO, false));
		assertEquals(95, this.engine.standing(1, ALEX));
		assertTrue(this.host.region.road().isEmpty());
	}

	@Test
	void titlesFollowPropertyAndTheLaw() {
		this.engine.set(this.ashbrook, ALEX, 15);
		assertEquals(Title.KNOWN, this.engine.title(1, ALEX));
		SimpleSettlement.SimplePlot plot = this.ashbrook.addPlot(new PlotSpec(Hearth.id("house/cottage"), Hearth.id("temperate"), 1, BlockPos.ZERO, 9, 9,
			Direction.NORTH, 16, 1, new CompoundTag()));
		this.ashbrook.setPlotOwner(plot.id(), ALEX);
		assertEquals(Title.BURGHER, this.engine.title(1, ALEX));
		this.engine.refreshTitle(this.ashbrook, ALEX);
		assertEquals(Title.BURGHER, StandingEngine.ledger(this.ashbrook).get(ALEX).title);
		assertTrue(this.host.events.contains("Ashbrook:known->burgher"));
		this.host.outlaws.add(ALEX + "@1");
		assertEquals(Title.OUTLAW, this.engine.title(1, ALEX));
	}

	@Test
	void storiesSpreadBetweenFriendsWithoutEchoing() {
		Resident edda = StandingTestKit.resident("Edda", JobType.BAKER);
		Resident friend = StandingTestKit.resident("Hild", JobType.TAILOR);
		edda.extra("npc").store("friends", net.minecraft.core.UUIDUtil.CODEC.listOf(), java.util.List.of(friend.id));
		this.ashbrook.addResident(edda);
		this.ashbrook.addResident(friend);
		this.engine.deed(deed("law:assault", -40, edda, false));
		for (int d = 0; d < 7; d++) {
			this.engine.hearsay(this.ashbrook, this.host.day + d);
		}
		java.util.List<Memories.Memory> heard = Memories.of(friend).all();
		assertEquals(1, heard.size(), "the friend heard it: " + heard);
		assertEquals("hearsay:law:assault", heard.getFirst().kind());
		assertEquals(-24, heard.getFirst().weight(), "-40 x 2.0 (victim) x 0.3");
		for (int d = 7; d < 14; d++) {
			this.engine.hearsay(this.ashbrook, this.host.day + d);
		}
		assertTrue(Memories.of(edda).all().stream().noneMatch(m -> m.kind().startsWith("hearsay:")), "hearsay is not passed back");
	}

	@Test
	void bathDeedWashes() {
		this.engine.deed(new Deed(ALEX, 1, Deeds.BATHED, 0, null, BlockPos.ZERO, false));
		assertEquals("wash", this.host.actions.getFirst());
		assertEquals(0, this.engine.standing(1, ALEX));
	}

	@Test
	void hundredsOfDeedsStayBoundedAndCheap() {
		Resident edda = StandingTestKit.resident("Edda", JobType.BAKER);
		this.ashbrook.addResident(edda);
		for (int i = 0; i < 40; i++) {
			this.host.town(100 + i, "Town" + i, 300 * (i % 8) + 700, 300 * (i / 8));
		}
		long t0 = System.nanoTime();
		for (int i = 0; i < 500; i++) {
			UUID p = new UUID(1, i % 50);
			this.engine.deed(new Deed(p, 1, i % 2 == 0 ? "law:theft" : "quest:completed", i % 2 == 0 ? -30 : 25, edda.id, BlockPos.ZERO, true));
			this.host.day += i % 10 == 0 ? 1 : 0;
			this.engine.deliver();
		}
		long ms = (System.nanoTime() - t0) / 1_000_000;
		assertTrue(Memories.of(edda).size() <= this.config.opinion().memories());
		assertTrue(StandingEngine.ledger(this.ashbrook).news().size() <= this.config.town().newsPerTown());
		assertTrue(this.host.region.road().size() <= this.config.gossip().maxQueue());
		TownLedger l = StandingEngine.ledger(this.ashbrook);
		assertEquals(50, l.entries().size());
		assertTrue(ms < 5000, "500 deeds with gossip over 40 towns took " + ms + " ms");
	}
}
