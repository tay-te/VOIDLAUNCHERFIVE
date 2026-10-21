package dev.voidmc.hearth.social.burgher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.PriceModifier;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.price.PriceModifiers;
import dev.voidmc.hearth.economy.works.Projects;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.standing.StandingConfig;
import dev.voidmc.hearth.standing.logic.Titles;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The burgher (docs/LIVING_WORLD_2.md §8.8): the oath's requirements; the price factor inside 0.5–1.5 with every
 * modifier; owners are Householders and the sworn are Burghers; the council's choice is funded first.
 */
class BurgherTest {
	private static final PlaceConfig.Burghers CFG = PlaceConfig.Burghers.DEFAULTS;
	private static final UUID ALEX = new UUID(5, 1);
	private static final UUID BEA = new UUID(5, 2);

	@org.junit.jupiter.api.BeforeAll
	static void boot() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@AfterEach
	void reset() {
		Titles.setSworn(null);
	}

	@Test
	void theOathAsksStandingThirtyDaysOfPropertyAndAMarketDay() {
		BurgherBook book = new BurgherBook();
		assertEquals(BurgherRules.Oath.NOT_OWNER, BurgherRules.check(book, ALEX, 60, null, 100, true, true, CFG));
		assertEquals(BurgherRules.Oath.STANDING, BurgherRules.check(book, ALEX, 39, 10L, 100, true, true, CFG));
		assertEquals(BurgherRules.Oath.NOT_LONG_ENOUGH, BurgherRules.check(book, ALEX, 60, 80L, 100, true, true, CFG));
		assertEquals(BurgherRules.Oath.NOT_MARKET_DAY, BurgherRules.check(book, ALEX, 60, 10L, 100, false, true, CFG));
		assertEquals(BurgherRules.Oath.OK, BurgherRules.check(book, ALEX, 60, 70L, 100, true, true, CFG), "held exactly thirty days");
		assertEquals(BurgherRules.Oath.OK, BurgherRules.check(book, ALEX, 40, 10L, 100, false, false, CFG), "a town with no market swears any day");
		book.sworn.put(ALEX, 100L);
		assertEquals(BurgherRules.Oath.ALREADY, BurgherRules.check(book, ALEX, 60, 10L, 100, true, true, CFG));
		// the fee: a crown a tier
		assertEquals(100, BurgherRules.fee(SettlementTier.CAMP, CFG));
		assertEquals(400, BurgherRules.fee(SettlementTier.TOWN, CFG));
		assertEquals(500, BurgherRules.fee(SettlementTier.CITY, CFG));
	}

	@Test
	void ownersAreHouseholdersAndTheSwornAreBurghers() {
		StandingConfig.Titles t = StandingConfig.DEFAULTS.titles();
		// without the burgher module, every owner is a burgher as before
		assertEquals(Title.BURGHER, Titles.of(10, true, Titles.sworn(1, ALEX), false, t));
		Titles.setSworn((id, p) -> p.equals(ALEX));
		assertEquals(Title.BURGHER, Titles.of(45, true, Titles.sworn(1, ALEX), false, t));
		assertEquals(Title.HOUSEHOLDER, Titles.of(45, true, Titles.sworn(1, BEA), false, t));
		assertEquals(Title.FRIEND, Titles.of(45, false, Titles.sworn(1, BEA), false, t), "no property, no householder");
		assertEquals(Title.HERO, Titles.of(90, true, false, false, t), "a hero is a hero");
		assertEquals(Title.OUTLAW, Titles.of(45, true, true, true, t), "the law's verdict wins");
	}

	@Test
	void theBurghersPriceStaysInsideTheBoundsWithEveryModifier() {
		assertEquals(1.0, BurgherRules.priceFactor(false, true, CFG), 0.0);
		assertTrue(BurgherRules.priceFactor(true, true, CFG) < 1.0, "a burgher pays less");
		assertTrue(BurgherRules.priceFactor(true, false, CFG) > 1.0, "and is paid more");
		for (double other : new double[]{0.3, 0.5, 0.8, 1.0, 1.3, 1.5, 2.0}) {
			for (boolean buys : new boolean[]{true, false}) {
				double f = PriceModifiers.combine(other * BurgherRules.priceFactor(true, buys, CFG));
				assertTrue(f >= PriceModifier.MIN && f <= PriceModifier.MAX, other + " " + buys + " -> " + f);
			}
		}
		// even at the config's widest dues
		PlaceConfig.Burghers widest = new PlaceConfig.Burghers(40, 30, 1, true, 0.25, 28, 3);
		assertTrue(BurgherRules.priceFactor(true, true, widest) >= PriceModifier.MIN);
		assertTrue(BurgherRules.priceFactor(true, false, widest) <= PriceModifier.MAX);
	}

	private static SimpleSettlement.SimplePlot plot(SimpleSettlement s, String archetype, int x, PlotState state, UUID owner) {
		SimpleSettlement.SimplePlot p = s.addPlot(new PlotSpec(Hearth.id(archetype), Hearth.id("temperate"), x, new BlockPos(x * 20, 64, 0), 9, 11,
			Direction.NORTH, 20, 1, new CompoundTag()));
		p.state = state;
		p.owner = owner;
		return p;
	}

	@Test
	void theCouncilsChoiceIsFundedFirst() {
		SimpleSettlement s = new SimpleSettlement(8, "Kutna", Culture.TEMPERATE, BlockPos.ZERO, -100);
		plot(s, "house/cottage", 1, PlotState.BUILT, ALEX);
		PlotView chapel = plot(s, "civic/chapel", 2, PlotState.PLANNED, null);
		PlotView well = plot(s, "landscape/well", 3, PlotState.PLANNED, null);
		PlotView hall = plot(s, "civic/town_hall", 4, PlotState.BUILDING, null);
		plot(s, "civic/bathhouse", 5, PlotState.PLANNED, null);
		plot(s, "civic/guardhouse", 6, PlotState.BUILT, null);
		BurgherBook book = new BurgherBook();
		// owners are tracked from the day they are seen
		assertTrue(BurgherRules.owners(book, s, 40));
		assertEquals(40L, book.owners.get(ALEX));
		assertFalse(BurgherRules.owners(book, s, 41), "nothing changed");
		// no sworn burghers: no council
		assertFalse(BurgherRules.councilDue(book, 42, true, CFG));
		book.sworn.put(ALEX, 41L);
		book.sworn.put(BEA, 41L);
		assertFalse(BurgherRules.councilDue(book, 42, false, CFG), "it sits on a rest day");
		assertTrue(BurgherRules.councilDue(book, 42, true, CFG));
		List<PlotView> table = Projects.councilCandidates(s, CFG.choices());
		assertEquals(List.of(hall.id(), chapel.id(), well.id()), table.stream().map(PlotView::id).toList(),
			"the planner's next three public works, those rising first; homes and built ones are not on the table");
		BurgherRules.sit(book, 42, table.stream().map(PlotView::id).toList());
		assertFalse(BurgherRules.vote(book, new UUID(1, 1), well.id(), 42), "only the sworn vote");
		assertFalse(BurgherRules.vote(book, ALEX, 999, 42), "only what is on the table");
		assertTrue(BurgherRules.vote(book, ALEX, well.id(), 42));
		assertTrue(BurgherRules.vote(book, BEA, well.id(), 42));
		assertFalse(BurgherRules.vote(book, BEA, chapel.id(), 43), "the sitting is over");
		int chosen = BurgherRules.close(book);
		assertEquals(well.id(), chosen);
		assertTrue(Projects.councilChoice(s, chosen, 43));
		assertTrue(Projects.funded(s).contains(well.id()), "funded first, as players' gifts are");
		assertFalse(BurgherRules.councilDue(book, 49, true, CFG), "once a festival month");
		assertTrue(BurgherRules.councilDue(book, 42 + CFG.councilDays(), true, CFG));
		// a sitting nobody spoke at chooses nothing
		BurgherRules.sit(book, 70, List.of(chapel.id()));
		assertEquals(-1, BurgherRules.close(book));
		// and the book saves
		var json = BurgherBook.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow();
		BurgherBook back = BurgherBook.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertTrue(back.isSworn(ALEX));
		assertEquals(well.id(), back.chosen);
	}
}
