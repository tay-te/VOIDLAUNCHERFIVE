package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.price.GoodsMask;
import dev.voidmc.hearth.economy.price.PriceData;
import dev.voidmc.hearth.economy.price.PriceTable;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.ShopBook;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Rarity;

/** A small test town for the economy's tests: a market stall, a bakery, a tailor, an inn, homes and folk. */
final class Towns {
	private Towns() {
	}

	/** Price data with unit values of 1 bit for every goods and the shop kinds the town uses. */
	static PriceData data() {
		double[] ones = new double[Goods.VALUES.length];
		Arrays.fill(ones, 1.0);
		return new PriceData(Map.of(), List.of(), ones, ones, 0.25, Map.of(Rarity.EPIC, 40.0), Map.of(), Map.of(),
			Map.of("commerce/market_stall", GoodsMask.ALL, "work/bakery", GoodsMask.of(Goods.FOOD, Goods.FLOUR),
				"work/tailor", GoodsMask.of(Goods.CLOTH, Goods.DYE), "commerce/inn", GoodsMask.of(Goods.FOOD),
				"work/chandler", GoodsMask.of(Goods.CANDLES, Goods.WAX)),
			Map.of("merchant", GoodsMask.ALL, "fence", GoodsMask.ALL, "baker", GoodsMask.of(Goods.FOOD, Goods.FLOUR),
				"tailor", GoodsMask.of(Goods.CLOTH, Goods.DYE), "innkeeper", GoodsMask.of(Goods.FOOD)));
	}

	static void install() {
		PriceTable.setData(data());
	}

	static void reset() {
		PriceTable.setData(PriceData.EMPTY);
	}

	static SimpleSettlement.SimplePlot built(SimpleSettlement s, String archetype, int x) {
		SimpleSettlement.SimplePlot p = s.addPlot(new PlotSpec(Hearth.id(archetype), Hearth.id("temperate"), x, new BlockPos(x, 64, 0), 9, 9,
			Direction.NORTH, 10, 1, new CompoundTag()));
		p.state = PlotState.BUILT;
		return p;
	}

	static Resident resident(SimpleSettlement s, int n, JobType job, AgeStage age, int home, int work) {
		Resident r = new Resident(new UUID(n, 77), "R" + n, "Test", n % 2 == 0, age, -5000, job, home, work, n, new CompoundTag());
		s.addResident(r);
		return r;
	}

	/**
	 * A village of 30: four shops (market stall, bakery, tailor, inn) with their keepers, eight homes, farmers, foresters,
	 * builders and children; full stores and a treasury.
	 */
	static SimpleSettlement village() {
		SimpleSettlement s = new SimpleSettlement(11, "Testwick", Culture.TEMPERATE, BlockPos.ZERO, 0);
		int[] homes = new int[8];
		for (int i = 0; i < homes.length; i++) {
			homes[i] = built(s, "house/cottage", 100 + i * 12).id();
		}
		int stall = built(s, "commerce/market_stall", 0).id();
		int bakery = built(s, "work/bakery", 12).id();
		int tailor = built(s, "work/tailor", 24).id();
		int inn = built(s, "commerce/inn", 36).id();
		int n = 0;
		resident(s, n++, JobType.MERCHANT, AgeStage.ADULT, homes[0], stall);
		resident(s, n++, JobType.BAKER, AgeStage.ADULT, homes[1], bakery);
		resident(s, n++, JobType.TAILOR, AgeStage.ADULT, homes[2], tailor);
		resident(s, n++, JobType.INNKEEPER, AgeStage.ADULT, homes[3], inn);
		for (int i = 0; i < 20; i++) {
			JobType job = i % 4 == 0 ? JobType.BUILDER : i % 4 == 1 ? JobType.FORESTER : JobType.FARMER;
			resident(s, n++, job, AgeStage.ADULT, homes[i % homes.length], Resident.NO_PLOT);
		}
		for (int i = 0; i < 6; i++) {
			resident(s, n++, JobType.NONE, AgeStage.CHILD, homes[i % homes.length], Resident.NO_PLOT);
		}
		for (Goods g : Goods.VALUES) {
			s.stockpile().add(g, 6000);
		}
		s.deposit(5000);
		s.setBeds(40);
		return s;
	}

	/** All the coin in the town: treasury, purses and shop tills. */
	static long coin(SimpleSettlement s) {
		long total = s.treasury();
		for (Resident r : s.residents()) {
			total += dev.voidmc.hearth.economy.purse.Purses.get(r);
		}
		ShopBook book = s.data(EconomyModule.SHOPS);
		for (Shop shop : book.shops.values()) {
			total += shop.till;
		}
		return total;
	}
}
