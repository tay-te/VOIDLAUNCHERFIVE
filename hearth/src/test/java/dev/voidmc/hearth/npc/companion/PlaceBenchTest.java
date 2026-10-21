package dev.voidmc.hearth.npc.companion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.household.HouseBook;
import dev.voidmc.hearth.economy.household.Servants;
import dev.voidmc.hearth.social.burgher.BurgherBook;
import dev.voidmc.hearth.social.burgher.BurgherRules;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The player's place's books against the budget (docs/LIVING_WORLD_2.md §8.4, §9.1: daily books ≤ 10 µs a player): a
 * town's household day (three servants paid, the larder topped up), a companion's payday, the burghers' daily owner check
 * on a 120-plot town. Prints the costs; the bounds are loose (a slow CI box must not fail it), the numbers go in the docs.
 */
class PlaceBenchTest {
	private static final UUID OWNER = new UUID(1, 2);

	@Test
	void theDailyBooksAreCheap() {
		SimpleSettlement s = new SimpleSettlement(1, "Bench", Culture.TEMPERATE, BlockPos.ZERO, -100);
		for (int i = 0; i < 120; i++) {
			SimpleSettlement.SimplePlot p = s.addPlot(new PlotSpec(Hearth.id(i == 0 ? "house/townhouse" : "house/cottage"), Hearth.id("temperate"), i,
				new BlockPos(i * 12, 64, 0), 9, 9, Direction.NORTH, 12, 1, new CompoundTag()));
			p.state = PlotState.BUILT;
			if (i == 0) {
				p.owner = OWNER;
			}
		}
		HouseBook book = new HouseBook();
		for (int i = 0; i < 150; i++) {
			Resident r = new Resident(new UUID(9, i), "R" + i, "Bench", false, AgeStage.ADULT, -9000, JobType.FARMER, -1, -1, 1, new CompoundTag());
			s.addResident(r);
			if (i < 3) {
				Servants.hire(s, book, r, s.plot(0), 0, PlaceConfig.DEFAULTS);
			}
		}
		s.stockpile().add(Goods.FOOD, 9000);
		int days = 20_000;
		long t0 = System.nanoTime();
		for (int d = 1; d <= days; d++) {
			// the larder emptied each day, so every day buys
			book.house(0).pending = 0;
			Servants.daily(s, book, d, (p, bits, why) -> true, units -> units * 2L, (r, bits) -> {
			}, PlaceConfig.DEFAULTS);
			if (s.stockpile().get(Goods.FOOD) < 1000) {
				s.stockpile().add(Goods.FOOD, 9000);
			}
		}
		double household = (System.nanoTime() - t0) / 1e3 / days;

		CompanionState st = CompanionState.hire(s.residents().iterator().next(), OWNER, 0, 8);
		t0 = System.nanoTime();
		for (int d = 1; d <= days; d++) {
			CompanionRules.payday(st, d, true, bits -> true, PlaceConfig.DEFAULTS);
		}
		double payday = (System.nanoTime() - t0) / 1e3 / days;

		BurgherBook burghers = new BurgherBook();
		t0 = System.nanoTime();
		for (int d = 1; d <= days; d++) {
			BurgherRules.owners(burghers, s, d);
		}
		double owners = (System.nanoTime() - t0) / 1e3 / days;
		System.out.println(String.format(Locale.ROOT, "[place bench] household day %.2f µs (3 servants, larder), companion payday %.3f µs, burghers' owners "
			+ "%.2f µs (120 plots)", household, payday, owners));
		assertTrue(household < 500 && payday < 50 && owners < 500, "the daily books stay small");
	}
}
