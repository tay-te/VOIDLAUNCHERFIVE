package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
import dev.voidmc.hearth.economy.price.PriceData;
import dev.voidmc.hearth.economy.price.PriceTable;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.atlas.History;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Grows a town on its own the way a live world does (requests/w5-tour.md §4): the real planner over the build module's
 * real blueprints, hour steps, and the economy module's daily books on every day (wages from the treasury, purses, poll
 * tax, upkeep) with the shipped prices, so the clock leaves its own coin for work to the economy as it does live. No
 * patron, no player, no caravans.
 */
final class GrowthHarness {
	private static boolean prices;
	/** For tuning: another share of idle builder time fetched than the config's, or null. */
	static @Nullable Double fetchShare;
	/** A day at a time, as a fast-forward or a history steps a town, instead of hour by hour. */
	static boolean wholeDays;

	final SimSettlement town;
	private final boolean economy;
	private int day;

	private GrowthHarness(SimSettlement town, boolean economy) {
		this.town = town;
		this.economy = economy;
	}

	/** A temperate town founded on synthetic ground ({@code flat}, {@code hills}, {@code river}...) round the origin. */
	static GrowthHarness found(String terrain, long seed, boolean economy) {
		TestSupport.useRealCatalogue();
		if (economy) {
			loadPrices();
		}
		TerrainProbe t = SyntheticTerrain.named(terrain, seed);
		History.Context base = TestSupport.context(t);
		Double fetch = fetchShare;
		if (fetch != null) {
			// tuning: another share of idle builder time fetched
			SimConfig d = SimConfig.DEFAULTS;
			SimRules rules = new SimRules(new SimConfig(d.dimensions(), d.probeCorrection(), d.embody(), d.clock(), d.applier(), d.atlas(),
				d.labour().withFetchShare(fetch), d.production(), d.population(), d.storage(), d.founding(), d.daughters(),
				d.resources()));
			base = new History.Context(rules, t, base.blueprints(), base.planner(), base.residents(), base.land());
		}
		SimContext ctx = economy ? new Live(base) : base;
		SimSettlement s = new SimSettlement(41, "Growbury", Culture.TEMPERATE, Level.OVERWORLD, new BlockPos(0, t.groundY(0, 0), 0), 0,
			SimSettlement.NO_REGION, seed);
		s.bind(ctx);
		var f = ctx.rules().config.founding();
		SettlementClock.found(s, ctx, f.residents(), f.goodsMap(), f.treasury());
		return new GrowthHarness(s, economy);
	}

	/** One sim day, hour by hour as a live town nobody is near is stepped, then the economy's books. */
	void day() {
		this.day++;
		int slices = wholeDays ? 1 : 24;
		for (int h = 1; h <= slices; h++) {
			SettlementClock.advance(this.town, (this.day - 1) * SimRules.DAY + h * (SimRules.DAY / slices));
		}
		if (this.economy) {
			SettlementEconomy.dayPassed(this.town, this.day);
		}
	}

	int dayNumber() {
		return this.day;
	}

	/** A line for the growth table: souls, beds, treasury, plots, what the builders wait for. */
	String line() {
		SimSettlement s = this.town;
		Map<JobType, Integer> jobs = new EnumMap<>(JobType.class);
		for (Resident r : s.residents()) {
			if (r.job != JobType.NONE) {
				jobs.merge(r.job, 1, Integer::sum);
			}
		}
		StringBuilder stalls = new StringBuilder();
		for (SimPlot p : s.plots()) {
			if (p.isActive() && p.stalledOn() != null) {
				stalls.append(p.spec().archetype().getPath()).append('@').append(p.stage()).append('!').append(p.stalledOn().getSerializedName())
					.append(' ');
			}
		}
		return String.format(java.util.Locale.ROOT, "day %3d: %3d souls %3d beds %-7s treasury %6d prosp %.2f plots %s builders %d stalls [%s]",
			this.day, s.population(), s.beds(), s.tier().getSerializedName(), s.treasury(), s.land().prosperity(), s.plotCounts(),
			jobs.getOrDefault(JobType.BUILDER, 0), stalls.toString().trim());
	}

	/** What each stalled plot's stage still lacks: goods wanted / held / room for them. */
	String stalls() {
		StringBuilder out = new StringBuilder();
		for (SimPlot p : this.town.plots()) {
			if (!p.isActive() || p.stalledOn() == null || !this.town.factsReady(p)) {
				continue;
			}
			PlotFacts f = this.town.facts(p);
			if (p.stage() >= f.stageCount()) {
				continue;
			}
			out.append(p.spec().archetype().getPath()).append('@').append(p.stage()).append(p.heldBack ? " (held)" : "").append(':');
			int[] need = f.goods()[p.stage()];
			for (int g = 0; g < need.length; g++) {
				if (need[g] > 0) {
					Goods k = Goods.VALUES[g];
					out.append(' ').append(k.getSerializedName()).append('=').append(need[g]).append('/').append(this.town.stockpile().get(k)).append('/')
						.append(this.town.stockpile().capacity(k));
				}
			}
			out.append("; ");
		}
		return out.toString();
	}

	String stock() {
		StringBuilder out = new StringBuilder();
		for (Goods g : Goods.VALUES) {
			int n = this.town.stockpile().get(g);
			if (n > 0) {
				out.append(g.getSerializedName()).append('=').append(n).append(' ');
			}
		}
		return out.toString();
	}

	/** The shipped price data (data/hearth/hearth_prices), read off the classpath as a server's data pack reload would. */
	private static synchronized void loadPrices() {
		if (prices) {
			return;
		}
		try {
			Path goods = Path.of(GrowthHarness.class.getClassLoader().getResource("data/hearth/hearth_prices/goods.json").toURI());
			Path root = goods.getParent().getParent().getParent().getParent();
			PathPackResources pack = new PathPackResources(new PackLocationInfo("hearth", Component.literal("hearth"), PackSource.BUILT_IN,
				Optional.empty()), root);
			try (MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
				PriceTable.setData(PriceData.load(manager));
			}
			prices = true;
		} catch (java.net.URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A history context that leaves the treasury's income to the economy module, as the live world does. */
	private record Live(History.Context base) implements SimContext {
		@Override
		public boolean economyHandlesWages() {
			return true;
		}

		@Override
		public SimRules rules() {
			return this.base.rules();
		}

		@Override
		public TerrainProbe terrain() {
			return this.base.terrain();
		}

		@Override
		public BlueprintService blueprints() {
			return this.base.blueprints();
		}

		@Override
		public @Nullable SettlementPlanner planner() {
			return this.base.planner();
		}

		@Override
		public ResidentFactory residents() {
			return this.base.residents();
		}

		@Override
		public @Nullable HearthWorld eventWorld() {
			return null;
		}

		@Override
		public long gameTime() {
			return 0;
		}

		@Override
		public boolean allows(SimSettlement settlement, PlotSpec spec) {
			return this.base.allows(settlement, spec);
		}

		@Override
		public boolean tryPlannerCall() {
			return true;
		}

		@Override
		public void plotsChanged(SimSettlement settlement) {
		}

		@Override
		public void progressed(SimSettlement settlement, SimPlot plot) {
		}

		@Override
		public void placedByEntity(SimSettlement settlement, SimPlot plot, long from, long to) {
		}

		@Override
		public void dirty(SimSettlement settlement) {
		}

		@Override
		public int territoryLimit(SimSettlement settlement, int wanted) {
			return this.base.territoryLimit(settlement, wanted);
		}
	}
}
