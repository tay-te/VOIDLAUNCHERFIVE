package dev.voidmc.hearth.economy.sandbox;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
import dev.voidmc.hearth.economy.price.PriceTable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Dev tooling: an in-memory {@link SimpleSettlement} per player, so the market, bank and property screens and every
 * transaction can be tried without the sim module ({@code /hearth economy sandbox}). Its stockpile follows the culture
 * (plentiful in its specialities, short of what it imports), it has a merchant at the market stall, a baker, a tailor,
 * an innkeeper, a smith (whose smithy is for sale as a business), a fence, a minter, a reeve at the town hall, builders,
 * households with purses, a few houses for sale, a chapel planned (to fund) and coin in its treasury. Forgotten when the
 * server stops; never saved.
 */
public final class Sandbox {
	private static final Map<Long, SimpleSettlement> BY_ID = new HashMap<>();
	private static final Map<UUID, Long> BY_PLAYER = new HashMap<>();
	/** Negative ids, so they never collide with the sim's. */
	private static long nextId = -1;

	private Sandbox() {
	}

	/** The player's sandbox settlement, made fresh if they have none or ask for another culture. */
	public static SimpleSettlement forPlayer(ServerPlayer player, @Nullable Culture culture) {
		Long id = BY_PLAYER.get(player.getUUID());
		SimpleSettlement s = id == null ? null : BY_ID.get(id);
		if (s != null && (culture == null || s.culture() == culture)) {
			return s;
		}
		if (s != null) {
			BY_ID.remove(s.id());
		}
		s = create(player, culture == null ? Culture.TEMPERATE : culture);
		BY_ID.put(s.id(), s);
		BY_PLAYER.put(player.getUUID(), s.id());
		return s;
	}

	public static @Nullable SimpleSettlement existing(ServerPlayer player) {
		Long id = BY_PLAYER.get(player.getUUID());
		return id == null ? null : BY_ID.get(id);
	}

	public static @Nullable Settlement find(long id) {
		return BY_ID.get(id);
	}

	public static Collection<SimpleSettlement> all() {
		return List.copyOf(BY_ID.values());
	}

	public static void clear() {
		BY_ID.clear();
		BY_PLAYER.clear();
	}

	private static SimpleSettlement create(ServerPlayer player, Culture culture) {
		RandomSource random = RandomSource.create(player.getUUID().getLeastSignificantBits() ^ culture.ordinal());
		BlockPos center = player.blockPosition();
		String name = "Sandbox " + Character.toUpperCase(culture.getSerializedName().charAt(0)) + culture.getSerializedName().substring(1);
		SimpleSettlement s = new SimpleSettlement(nextId--, name, culture, center, 0);

		// Stores that follow the culture: plenty of what it is known for, short of what it imports.
		for (Goods g : Goods.VALUES) {
			double spec = PriceTable.speciality(culture, g);
			int amount = spec < 0.95 ? 5500 + random.nextInt(2500) : spec > 1.05 ? 300 + random.nextInt(900) : 2600 + random.nextInt(2400);
			s.stockpile().add(g, amount);
		}
		s.deposit(4000 + random.nextInt(4000));

		// A few houses: two built and empty (for sale), one lived in, two staked-out plots.
		List<Integer> houses = new ArrayList<>();
		String[] kinds = {"house/cottage", "house/townhouse", "house/rowhouse", "house/cottage", "house/farmhouse"};
		for (int i = 0; i < kinds.length; i++) {
			int w = 7 + random.nextInt(5);
			int d = 8 + random.nextInt(5);
			PlotSpec spec = new PlotSpec(Hearth.id(kinds[i]), Hearth.id(culture.getSerializedName()), random.nextLong(),
				center.offset(12 + i * 14, 0, 10), w, d, Direction.NORTH, kinds[i].endsWith("townhouse") ? 18 : 10, Math.min(4, i), new CompoundTag());
			SimpleSettlement.SimplePlot plot = s.addPlot(spec);
			plot.state = i < 3 ? PlotState.BUILT : PlotState.PLANNED;
			houses.add(plot.id());
		}
		PlotSpec bankSpec = new PlotSpec(Hearth.id("commerce/bank"), Hearth.id(culture.getSerializedName()), 1L, center.offset(-16, 0, 0), 11, 9,
			Direction.EAST, 12, 2, new CompoundTag());
		SimpleSettlement.SimplePlot bank = s.addPlot(bankSpec);
		bank.state = PlotState.BUILT;
		SimpleSettlement.SimplePlot stall = built(s, culture, "commerce/market_stall", center.offset(0, 0, -8), 5, 4, 2L);
		SimpleSettlement.SimplePlot bakery = built(s, culture, "work/bakery", center.offset(-14, 0, -14), 9, 9, 3L);
		SimpleSettlement.SimplePlot tailor = built(s, culture, "work/tailor", center.offset(-28, 0, -14), 7, 9, 4L);
		SimpleSettlement.SimplePlot inn = built(s, culture, "commerce/inn", center.offset(0, 0, -26), 13, 11, 5L);
		SimpleSettlement.SimplePlot smithy = built(s, culture, "work/smithy", center.offset(16, 0, -14), 9, 9, 6L);
		SimpleSettlement.SimplePlot hall = built(s, culture, "civic/town_hall", center.offset(-16, 0, 16), 13, 13, 7L);
		PlotSpec chapelSpec = new PlotSpec(Hearth.id("civic/chapel"), Hearth.id(culture.getSerializedName()), 8L, center.offset(30, 0, 20), 9, 15,
			Direction.WEST, 20, 2, new CompoundTag());
		s.addPlot(chapelSpec); // planned: a project to fund
		s.setBeds(8);

		addResident(s, random, "Wenna", "Marrow", JobType.MERCHANT, houses.get(2), stall.id());
		addResident(s, random, "Osric", "Penhallow", JobType.MINTER, houses.get(2), bank.id());
		addResident(s, random, "Hob", "Miller", JobType.BAKER, houses.get(1), bakery.id());
		addResident(s, random, "Tamsin", "Weaver", JobType.TAILOR, houses.get(1), tailor.id());
		addResident(s, random, "Jory", "Alewife", JobType.INNKEEPER, houses.get(0), inn.id());
		addResident(s, random, "Bran", "Smith", JobType.SMITH, houses.get(0), smithy.id());
		addResident(s, random, "Ezra", "Cole", JobType.FENCE, Resident.NO_PLOT, Resident.NO_PLOT);
		addResident(s, random, "Mabyn", "Trevail", JobType.REEVE, houses.get(2), hall.id());
		for (int i = 0; i < 4; i++) {
			addResident(s, random, "Builder", String.valueOf(i + 1), JobType.BUILDER, houses.get(i % 3), Resident.NO_PLOT);
		}
		for (int i = 0; i < 10; i++) {
			addResident(s, random, "Folk", String.valueOf(i + 1), i % 3 == 0 ? JobType.FARMER : i % 3 == 1 ? JobType.FORESTER : JobType.NONE,
				i < 6 ? houses.get(i % 3) : Resident.NO_PLOT, Resident.NO_PLOT);
		}
		s.addReputation(player.getUUID(), 40);
		// a day of its economy: purses seeded, shops opened and stocked, contracts offered
		SettlementEconomy.dayPassed(s, EconomyModule.day(player.level()));
		return s;
	}

	private static SimpleSettlement.SimplePlot built(SimpleSettlement s, Culture culture, String archetype, BlockPos origin, int w, int d, long seed) {
		SimpleSettlement.SimplePlot plot = s.addPlot(new PlotSpec(Hearth.id(archetype), Hearth.id(culture.getSerializedName()), seed, origin, w, d,
			Direction.SOUTH, 10, 1, new CompoundTag()));
		plot.state = PlotState.BUILT;
		return plot;
	}

	private static void addResident(SimpleSettlement s, RandomSource random, String given, String family, JobType job, int home, int work) {
		s.addResident(new Resident(new UUID(random.nextLong(), random.nextLong()), given, family, random.nextBoolean(), AgeStage.ADULT, -random.nextInt(9000),
			job, home, work, random.nextInt(), new CompoundTag()));
	}

	/** The sandbox's merchant (for testing the dialogue provider). */
	public static @Nullable Resident merchant(SimpleSettlement s) {
		for (Resident r : s.residents()) {
			if (r.job == JobType.MERCHANT) {
				return r;
			}
		}
		return null;
	}
}
