package dev.voidmc.hearth.economy.household;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.sim.settlement.ResidentClaims;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * A player's servants (docs/LIVING_WORLD_2.md §8.1–§8.2), pure: who will keep house, how many a house takes, the claim that
 * keeps them at it through the sim's daily assignment, and the day's books (wages and the larder, from the owner's bank
 * account). The game supplies the bank and the prices; tests supply their own.
 */
public final class Servants {
	private Servants() {
	}

	/** The owner's bank (the economy's {@code chargeDeposit} in the game). */
	@FunctionalInterface
	public interface Bank {
		boolean charge(UUID player, long bits, String why);
	}

	/** What the day did to one house's servants, for the messages. */
	public record Left(UUID servant, String name, UUID player, int plot, String why) {
	}

	/** A house a player may keep servants in: theirs, built, a home ({@code house/...}). */
	public static boolean house(PlotView plot) {
		return plot.owner() != null && plot.state() == PlotState.BUILT && plot.spec().archetype().getPath().startsWith("house/");
	}

	/** The player's houses in a town. */
	public static List<PlotView> houses(Settlement s, UUID player) {
		List<PlotView> out = new ArrayList<>();
		for (PlotView p : s.plots()) {
			if (player.equals(p.owner()) && house(p)) {
				out.add(p);
			}
		}
		return out;
	}

	/** How many servants a house takes: the config's number, but no more than its beds (one at least). */
	public static int slots(Settlement s, PlotView plot, PlaceConfig cfg) {
		int beds = 0;
		for (Poi p : s.pois(PoiType.BED)) {
			if (inPlot(p.pos(), plot)) {
				beds++;
			}
		}
		return Math.min(cfg.household().servantsPerHouse(), Math.max(1, beds));
	}

	public static boolean inPlot(BlockPos pos, PlotView plot) {
		var spec = plot.spec();
		return pos.getX() >= spec.origin().getX() && pos.getX() < spec.origin().getX() + spec.sizeX() && pos.getZ() >= spec.origin().getZ()
			&& pos.getZ() < spec.origin().getZ() + spec.sizeZ();
	}

	/** The house's points of a kind (its chests, doors, hearth). */
	public static List<Poi> pois(Settlement s, PlotView plot, PoiType type) {
		List<Poi> out = new ArrayList<>();
		for (Poi p : s.pois(type)) {
			if (inPlot(p.pos(), plot)) {
				out.add(p);
			}
		}
		return out;
	}

	/** Poor adults willing to keep house: not in office, not serving anyone, not away. */
	public static boolean willing(Settlement s, Resident r) {
		if (r.age != AgeStage.ADULT || r.station != Station.POOR || ServantState.serving(r) || s.isResidentAway(r.id)
			|| r.extra.getCompoundOrEmpty("companion").getString("player").isPresent()) {
			return false;
		}
		String office = r.extra.getCompoundOrEmpty("social").getStringOr("office", "");
		return office.isEmpty() || office.equals("none");
	}

	/** A servant's daily wage (the poor station's, by the config's factor; a bit at least). */
	public static long wage(PlaceConfig cfg) {
		return Math.max(1, Math.round(cfg.wage(Station.POOR) * cfg.household().wageFactor()));
	}

	/** Takes a resident into a house's service: they live and work there from today. */
	public static void hire(Settlement s, HouseBook book, Resident r, PlotView plot, long day, PlaceConfig cfg) {
		UUID owner = plot.owner();
		ServantState.hire(r, owner, plot.id(), day, wage(cfg));
		HouseBook.House h = book.open(plot.id(), owner, day);
		if (!h.servants.contains(r.id)) {
			h.servants.add(r.id);
		}
		r.job = JobType.SERVANT;
		r.home = plot.id();
		r.work = plot.id();
	}

	/** Lets a servant go: they are a free hand again (the sim finds them a bed and work on its next day). */
	public static void release(Settlement s, HouseBook book, Resident r) {
		ServantState st = ServantState.of(r);
		if (st != null) {
			HouseBook.House h = book.house(st.plot());
			if (h != null) {
				h.servants.remove(r.id);
			}
		}
		ServantState.release(r);
		if (r.job == JobType.SERVANT) {
			r.job = JobType.NONE;
		}
		r.work = Resident.NO_PLOT;
		r.home = Resident.NO_PLOT;
	}

	/**
	 * The claim the sim's daily assignment consults (lane 0's {@link ResidentClaims}): a servant's job and home are the house
	 * they keep, as long as it stands and is someone's. The sim otherwise leaves player-owned plots empty.
	 */
	public static final ResidentClaims.Source CLAIMS = new ResidentClaims.Source() {
		@Override
		public ResidentClaims.@Nullable Job job(Settlement settlement, Resident resident) {
			int plot = keptHouse(settlement, resident);
			return plot == Resident.NO_PLOT ? null : new ResidentClaims.Job(JobType.SERVANT, plot);
		}

		@Override
		public @Nullable Integer home(Settlement settlement, Resident resident) {
			int plot = keptHouse(settlement, resident);
			return plot == Resident.NO_PLOT ? null : plot;
		}
	};

	/** The house a resident keeps, or {@link Resident#NO_PLOT}. */
	static int keptHouse(Settlement s, Resident r) {
		ServantState st = ServantState.of(r);
		if (st == null) {
			return Resident.NO_PLOT;
		}
		PlotView p = s.plot(st.plot());
		return p != null && p.owner() != null ? p.id() : Resident.NO_PLOT;
	}

	/**
	 * The day's books of a town's households: servants paid from the owner's bank (or not: unpaid {@code unpaid_days} days
	 * running, they leave), servants of a house nobody owns any more let go, a house sold to another player served for its
	 * new owner, and the larder topped up to the config's size from the town's food at the town's price (buying only what
	 * the town can spare).
	 *
	 * @param foodPrice the town's price for this many units of food
	 * @return who left today
	 */
	public static List<Left> daily(Settlement s, HouseBook book, long day, Bank bank, LongUnaryOperator foodPrice, PaySink pay, PlaceConfig cfg) {
		List<Left> left = new ArrayList<>();
		for (Iterator<HouseBook.House> it = book.houses.values().iterator(); it.hasNext(); ) {
			HouseBook.House h = it.next();
			PlotView plot = s.plot(h.plot);
			if (plot == null || plot.owner() == null) {
				for (UUID id : List.copyOf(h.servants)) {
					Resident r = s.resident(id);
					if (r != null) {
						left.add(new Left(id, r.fullName(), h.owner, h.plot, "sold"));
						release(s, book, r);
					}
				}
				it.remove();
				continue;
			}
			if (!plot.owner().equals(h.owner)) {
				// the deed changed hands: the house's servants serve whoever holds it
				h.owner = plot.owner();
			}
			for (UUID id : List.copyOf(h.servants)) {
				Resident r = s.resident(id);
				ServantState st = r == null ? null : ServantState.of(r);
				if (st == null) {
					h.servants.remove(id);
					continue;
				}
				st.setPlayer(h.owner);
				if (day <= st.paidDay()) {
					continue;
				}
				st.setPaidDay(day);
				if (bank.charge(h.owner, st.wage(), "servant wage")) {
					st.setUnpaid(0);
					h.spent += st.wage();
					pay.paid(r, st.wage());
				} else {
					st.setUnpaid(st.unpaid() + 1);
					if (st.unpaid() >= cfg.household().unpaidDays()) {
						left.add(new Left(id, r.fullName(), h.owner, h.plot, "unpaid"));
						release(s, book, r);
					}
				}
			}
			if (h.servants.isEmpty()) {
				continue;
			}
			int want = cfg.household().larder() - h.stocked - h.pending;
			if (want <= 0) {
				continue;
			}
			// the town keeps three days' food a head for itself
			int spare = Math.max(0, s.stockpile().get(Goods.FOOD) - 3 * s.population());
			int units = Math.min(want, spare);
			if (units <= 0) {
				continue;
			}
			long cost = Math.max(1, foodPrice.applyAsLong(units));
			if (bank.charge(h.owner, cost, "larder")) {
				int taken = s.stockpile().take(Goods.FOOD, units);
				s.deposit(cost);
				h.pending += taken;
				h.spent += cost;
				h.larderShort = false;
			} else {
				h.larderShort = true;
			}
		}
		return left;
	}

	/** Where a servant's wage goes (their purse, through the economy). */
	@FunctionalInterface
	public interface PaySink {
		void paid(Resident servant, long bits);
	}
}
