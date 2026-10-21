package dev.voidmc.hearth.economy.works;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.Reputation;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.bank.DepositStore;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.property.PropertyNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Player-funded construction ("pay for the bridge", docs/LIVING_WORLD.md §5): a player gives coin toward a planned or
 * rising public building. The planner and the sim read the funded flag ({@link #funded}) and put it first; whenever its
 * construction stalls for want of goods, the fund buys them from passing traders at the town's price times
 * {@code importMarkup} and they go straight into the stores. When the building is finished the town keeps the change;
 * if it is abandoned the givers are repaid (into their bank accounts) what is left, in proportion to what they gave.
 */
public final class Projects {
	/** At most this many projects are listed at once (each costs a blueprint lookup to estimate). */
	static final int MAX_LISTED = 12;

	private Projects() {
	}

	public static Works works(Settlement settlement) {
		return settlement.data(EconomyModule.WORKS);
	}

	/** Plot ids of unfinished buildings players have paid toward: the planner and the sim put them first. */
	public static Set<Integer> funded(Settlement settlement) {
		Set<Integer> out = new HashSet<>();
		for (Works.Project p : works(settlement).projects.values()) {
			PlotView plot = settlement.plot(p.plot);
			if (plot != null && plot.state() != PlotState.BUILT) {
				out.add(p.plot);
			}
		}
		return out;
	}

	/** Whether a plot can be paid for: public construction not yet finished (homes and players' own plots are private). */
	public static boolean fundable(PlotView plot) {
		if (plot.state() == PlotState.BUILT || plot.owner() != null) {
			return false;
		}
		String path = plot.spec().archetype().getPath();
		return !path.startsWith("house/") && !path.startsWith("extract/");
	}

	/** What is listed at the counter: buildings under way first, then those planned, funded ones first. */
	public static List<BankView.ProjectOffer> listings(Settlement settlement, ServerPlayer player, @Nullable HearthWorld world) {
		Works works = works(settlement);
		MarketPricer pricer = new MarketPricer(settlement);
		List<PlotView> plots = new ArrayList<>();
		for (PlotView p : settlement.plots()) {
			if (fundable(p)) {
				plots.add(p);
			}
		}
		plots.sort(Comparator.comparing((PlotView p) -> !works.projects.containsKey(p.id())).thenComparing(p -> p.state() != PlotState.BUILDING)
			.thenComparingInt(PlotView::id));
		List<BankView.ProjectOffer> out = new ArrayList<>();
		for (PlotView p : plots) {
			if (out.size() >= MAX_LISTED) {
				break;
			}
			Works.Project project = works.projects.get(p.id());
			long given = project == null ? 0 : project.given();
			long yours = project == null ? 0 : project.contributions.getOrDefault(player.getUUID(), 0L);
			int[] stages = stages(p, world);
			long cost = estimate(p, world, pricer);
			out.add(new BankView.ProjectOffer(p.id(), p.spec().archetype().getPath(), p.state() == PlotState.BUILDING, p.stage(), stages[0],
				cost, given, yours, project == null ? 0 : project.fund, p.waitingForGoods()));
		}
		return out;
	}

	/** {stage count} of a plot's blueprint (1 if it cannot be read now). */
	private static int[] stages(PlotView plot, @Nullable HearthWorld world) {
		Blueprint bp = blueprint(plot.spec(), world);
		return new int[]{bp == null ? Math.max(1, plot.stage() + 1) : bp.stageCount()};
	}

	private static @Nullable Blueprint blueprint(PlotSpec spec, @Nullable HearthWorld world) {
		if (!HearthServices.hasBlueprints()) {
			return null;
		}
		try {
			// without the sim's world (a dev sandbox) the bill is read on flat ground: an estimate either way
			TerrainProbe terrain = world != null ? world.terrain() : new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, spec.seed());
			return HearthServices.blueprints().blueprint(spec, terrain);
		} catch (RuntimeException e) {
			return null; // an archetype this server does not have: no estimate
		}
	}

	/** The goods its unfinished stages still need, at the town's mid prices, in bits (0 if the blueprint cannot be read). */
	public static long estimate(PlotView plot, @Nullable HearthWorld world, MarketPricer pricer) {
		Blueprint bp = blueprint(plot.spec(), world);
		if (bp == null) {
			return 0;
		}
		double total = 0;
		for (int stage = Math.max(0, plot.stage()); stage < bp.stageCount(); stage++) {
			int[] goods = bp.goods(stage);
			double share = stage == plot.stage() && bp.stepCount(stage) > 0 ? 1.0 - (double) plot.step() / bp.stepCount(stage) : 1.0;
			for (int g = 0; g < Math.min(goods.length, Goods.VALUES.length); g++) {
				total += goods[g] * share * pricer.unitMid(Goods.VALUES[g]);
			}
		}
		return Math.round(total);
	}

	/** The player gives {@code bits} toward a plot; the status says what happened. */
	public static Component fund(Settlement settlement, ServerPlayer player, int plotId, long bits, long day) {
		PlotView plot = settlement.plot(plotId);
		if (plot == null || !fundable(plot)) {
			return Component.translatable("hearth.economy.works.gone").withStyle(ChatFormatting.DARK_RED);
		}
		if (bits <= 0 || !PlayerPurse.charge(player, bits)) {
			return Component.translatable("hearth.economy.market.cannot_afford", CoinFormat.format(Math.max(1, bits))).withStyle(ChatFormatting.DARK_RED);
		}
		Works works = works(settlement);
		Works.Project project = works.projects.computeIfAbsent(plotId, id -> new Works.Project(id, day));
		project.fund += bits;
		project.contributions.merge(player.getUUID(), bits, Long::sum);
		buyMissing(settlement, project, new MarketPricer(settlement));
		settlement.markDirty();
		EconomyConfig.Works cfg = EconomyModule.config().works();
		int weight = (int) Math.max(1, Math.min(25, Math.round(bits / 100.0 * cfg.fundWeightPerCrown())));
		Reputation.deed(settlement, player.getUUID(), Reputation.FUNDED_PROJECT, weight, null, player.blockPosition(), true);
		return Component.translatable("hearth.economy.works.funded", CoinFormat.format(bits), PropertyNames.lower(plot.spec().archetype().getPath()))
			.withStyle(ChatFormatting.DARK_GREEN);
	}

	/**
	 * The town council's choice (docs/LIVING_WORLD_2.md §8, the burghers' voice): the next public works the council may
	 * pick among — the planner's next {@code n} public buildings nobody has funded yet, those already rising first, then
	 * in the planner's order.
	 */
	public static List<PlotView> councilCandidates(Settlement settlement, int n) {
		Works works = works(settlement);
		List<PlotView> plots = new ArrayList<>();
		for (PlotView p : settlement.plots()) {
			if (fundable(p) && !works.projects.containsKey(p.id())) {
				plots.add(p);
			}
		}
		plots.sort(Comparator.comparing((PlotView p) -> p.state() != PlotState.BUILDING).thenComparingInt(PlotView::id));
		return plots.size() > n ? List.copyOf(plots.subList(0, n)) : plots;
	}

	/**
	 * The council chose a public building: it is put first as a funded one is (an entry with no coin yet, which players
	 * may still add to). Returns false if the plot cannot be chosen (built, private, gone).
	 */
	public static boolean councilChoice(Settlement settlement, int plotId, long day) {
		PlotView plot = settlement.plot(plotId);
		if (plot == null || !fundable(plot)) {
			return false;
		}
		works(settlement).projects.computeIfAbsent(plotId, id -> new Works.Project(id, day));
		settlement.markDirty();
		return true;
	}

	/** Construction of a plot stalled: if players have funded it, the fund buys what is missing now. */
	public static void stalled(Settlement settlement, PlotView plot, Goods goods, int missing) {
		Works.Project project = works(settlement).projects.get(plot.id());
		if (project == null) {
			return;
		}
		project.missing[goods.ordinal()] = Math.max(project.missing[goods.ordinal()], missing);
		buyMissing(settlement, project, new MarketPricer(settlement));
		settlement.markDirty();
	}

	/**
	 * Buys what the project's construction is short of from passing traders, as far as the fund goes. The coin leaves the
	 * town (the traders take it away); the goods go into the stores, where the stalled construction finds them.
	 *
	 * @return the coin spent
	 */
	static long buyMissing(Settlement settlement, Works.Project project, MarketPricer pricer) {
		double markup = EconomyModule.config().works().importMarkup();
		long spentNow = 0;
		for (Goods g : Goods.VALUES) {
			int i = g.ordinal();
			if (project.missing[i] <= 0 || project.fund <= 0) {
				continue;
			}
			double unit = Math.max(1e-6, pricer.unitMid(g) * markup);
			int units = (int) Math.min(project.missing[i], Math.floor(project.fund / unit));
			if (units <= 0) {
				continue;
			}
			int added = settlement.stockpile().add(g, units);
			long cost = Math.min(project.fund, (long) Math.ceil(added * unit - 1e-9));
			project.fund -= cost;
			project.spent += cost;
			spentNow += cost;
			project.missing[i] -= added;
			pricer.ledger().traded[i] += added; // bought in, not the town's own production
		}
		return spentNow;
	}

	/** The day's work: finished projects give their change to the town, abandoned ones repay their givers. */
	public static void daily(Settlement settlement, @Nullable MinecraftServer server) {
		Works works = works(settlement);
		if (works.projects.isEmpty()) {
			return;
		}
		MarketPricer pricer = new MarketPricer(settlement);
		for (Iterator<Works.Project> it = works.projects.values().iterator(); it.hasNext(); ) {
			Works.Project project = it.next();
			PlotView plot = settlement.plot(project.plot);
			if (plot == null) {
				refund(settlement, project, server);
				it.remove();
			} else if (plot.state() == PlotState.BUILT) {
				settlement.deposit(project.fund);
				project.fund = 0;
				it.remove();
			} else if (plot.waitingForGoods()) {
				buyMissing(settlement, project, pricer);
			}
		}
		settlement.markDirty();
	}

	/** A project given up (its plot abandoned): what is left goes back to its givers, by share. */
	public static void abandoned(Settlement settlement, int plotId, @Nullable MinecraftServer server) {
		Works.Project project = works(settlement).projects.remove(plotId);
		if (project != null) {
			refund(settlement, project, server);
			settlement.markDirty();
		}
	}

	private static void refund(Settlement settlement, Works.Project project, @Nullable MinecraftServer server) {
		long given = project.given();
		if (project.fund <= 0 || given <= 0) {
			return;
		}
		if (server == null) {
			settlement.deposit(project.fund); // nobody to repay through: the town keeps it
			project.fund = 0;
			return;
		}
		long left = project.fund;
		for (Map.Entry<UUID, Long> e : project.contributions.entrySet()) {
			long share = project.fund * e.getValue() / given;
			if (share > 0) {
				DepositStore.add(server, e.getKey(), share);
				left -= share;
			}
		}
		settlement.deposit(Math.max(0, left));
		project.fund = 0;
	}
}
