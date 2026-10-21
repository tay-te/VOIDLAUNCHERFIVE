package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementKind;
import dev.voidmc.hearth.plan.wild.PostLayouts;
import dev.voidmc.hearth.sim.settlement.ResidentClaims;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a post does in the books (docs/LIVING_WORLD_2.md §7.2–7.3), once a sim day:
 * <ul>
 *   <li><b>Board</b>: travellers put up and pay in kind, so a post that cannot feed itself from its land (an inn, a toll
 *   gate, a hermit) keeps a fortnight's food in store; what it is short of, guests and passing carts bring.</li>
 *   <li><b>The nomads move</b>: every 14 days the camp packs up (its yurts, pen and paths come down) and pitches on its
 *   other ground, felt and poles quick to raise; a ground used before is taken up again where it was.</li>
 *   <li><b>Tolls</b> paid on earlier days are forgotten (a paid player passes freely for the rest of the sim day).</li>
 * </ul>
 * And the trades only posts have: the toll gate's keeper ({@code TOLLKEEPER}, at the gate), the hermit
 * ({@code HERMIT}, at his hut), through {@link ResidentClaims}.
 */
public final class PostRules {
	/** Days a nomad camp stays on one ground. */
	public static final int CAMP_DAYS = 14;
	/** Food kept in store per head (days of eating, roughly) before guests' board tops it up. */
	static final int BOARD_DAYS = 14;

	private PostRules() {
	}

	/** The sim day's business of a post (from {@code DAY_PASSED}; the tests call it directly). */
	public static void daily(SimSettlement s, long day) {
		PostData d = PostData.of(s);
		if (d == null) {
			return;
		}
		board(s, d);
		d.forgetOld(day);
		if (d.site.type() == PostType.NOMAD_CAMP) {
			if (d.movedDay == Long.MIN_VALUE) {
				d.movedDay = day;
			} else if (day - d.movedDay >= CAMP_DAYS) {
				move(s, d, day);
			}
		}
	}

	/** Guests' board: the store kept at a fortnight's food a head. */
	static void board(SimSettlement s, PostData d) {
		int heads = Math.max(1, s.population());
		int want = BOARD_DAYS * heads;
		int have = s.stockpile().get(Goods.FOOD);
		if (have < want) {
			int bring = Math.min(want - have, 2 * heads + 2);
			s.stockpile().add(Goods.FOOD, bring);
			d.guests++;
			s.markDirty();
		}
	}

	/** The camp packs up and pitches on its other ground (in the books; the applier clears and writes the land). */
	public static void move(SimSettlement s, PostData d, long day) {
		int to = 1 - d.ground;
		List<SimPlot> old = new ArrayList<>();
		for (SimPlot p : s.plots()) {
			if (p.state() != PlotState.DEMOLISHING && p.state() != PlotState.ABANDONED) {
				old.add(p);
			}
		}
		var context = s.context();
		if (context == null) {
			return;
		}
		List<PlotSpec> pitch = PostLayouts.camp(d.site, s.culture(), context.terrain(), to);
		if (pitch.isEmpty()) {
			return;
		}
		for (SimPlot p : old) {
			s.demolishPlot(p.id());
		}
		d.ground = to;
		d.movedDay = day;
		for (PlotSpec spec : pitch) {
			// the same tent on the same ground as last time takes over what is left of it there
			int replaces = -1;
			for (SimPlot p : s.plots()) {
				if (p.state() == PlotState.DEMOLISHING && p.spec().archetype().equals(spec.archetype()) && p.spec().origin().equals(spec.origin())) {
					replaces = p.id();
					break;
				}
			}
			SimPlot p = s.addPlot(spec, replaces, SimSettlement.FOUNDING);
			s.completePlot(p.id());
		}
		s.recomputeDerived();
		s.markDirty();
	}

	// ---------------------------------------------------------------- trades only posts have

	/** The toll keeper and the hermit, settled before the daily assignment. */
	static final ResidentClaims.Source JOBS = new ResidentClaims.Source() {
		@Override
		public ResidentClaims.@Nullable Job job(Settlement settlement, Resident resident) {
			if (settlement.kind() != SettlementKind.POST || resident.age != AgeStage.ADULT) {
				return null;
			}
			String type = settlement.postType();
			if (type.equals(PostType.TOLL_GATE.getSerializedName())) {
				return keeper(settlement, resident, JobType.TOLLKEEPER, "law/toll_gate");
			}
			if (type.equals(PostType.HERMITAGE.getSerializedName())) {
				return keeper(settlement, resident, JobType.HERMIT, "house/hermit_hut");
			}
			return null;
		}
	};

	/** The first adult (by id) keeps the place; everyone else is left to the daily assignment. */
	private static ResidentClaims.@Nullable Job keeper(Settlement s, Resident resident, JobType job, String archetype) {
		UUID first = null;
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && (first == null || r.id.compareTo(first) < 0)) {
				first = r.id;
			}
		}
		if (!resident.id.equals(first)) {
			return null;
		}
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals(archetype)) {
				return new ResidentClaims.Job(job, p.id());
			}
		}
		return new ResidentClaims.Job(job, Resident.NO_PLOT);
	}

	/** The plot of a post's main building (the gate, the hospice, the inn...), or null. */
	public static @Nullable PlotView main(Settlement s, String archetypePath) {
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals(archetypePath)) {
				return p;
			}
		}
		return null;
	}
}
