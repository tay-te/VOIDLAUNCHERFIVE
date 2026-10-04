package dev.voidmc.hearth.plan.deep;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Stockpile;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.model.PlanState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * One level of a settlement that has a deep town (LIVING_WORLD.md §8.2): the surface, or the town in the realm below.
 * Plots on the two levels share columns (the deep town lies under the fields), so the planner plans each level on its
 * own — its own plan state, occupancy grid and roads — through a view that shows only that level's plots. The lift
 * down to the realm stands on both. Everything else is the settlement's own; the deep view's population is the share of
 * the town the deep is planned to house.
 */
public final class LevelView implements Settlement {
	private final Settlement root;
	private final boolean deep;
	private final PlanState state;
	private final int population;
	private @Nullable List<PlotView> plots;

	private LevelView(Settlement root, boolean deep, PlanState state, int population) {
		this.root = root;
		this.deep = deep;
		this.state = state;
		this.population = population;
	}

	/** The surface of a settlement with a deep town. */
	public static LevelView surface(Settlement root, PlanState state) {
		return new LevelView(root, false, state, root.population());
	}

	/** The deep town of a settlement, planned to house {@code population} folk. */
	public static LevelView deep(Settlement root, PlanState state, int population) {
		return new LevelView(root, true, state, population);
	}

	/** The settlement itself. */
	public Settlement root() {
		return this.root;
	}

	public boolean isDeep() {
		return this.deep;
	}

	/** The settlement under any view. */
	public static Settlement unwrap(Settlement s) {
		return s instanceof LevelView v ? v.root : s;
	}

	/**
	 * Which level a plot is on: 0 the surface, 1 the deep, 2 both (the lift). Deep plots are the {@code deep/*}
	 * archetypes and anything carrying a {@code realm} param (the deep town's roads); cached by plot id.
	 */
	static int level(PlanState state, PlotView p) {
		Integer cached = state.levels.get(p.id());
		if (cached != null && (cached >> 2) == (int) (p.spec().seed() & 0x3FFFFFFF)) {
			return cached & 3;
		}
		int level = level(p.spec());
		state.levels.put(p.id(), (int) ((p.spec().seed() & 0x3FFFFFFF) << 2) | level);
		return level;
	}

	public static int level(PlotSpec spec) {
		String path = spec.archetype().getPath();
		if (spec.archetype().equals(Archetypes.DEEP_LIFT)) {
			return 2;
		}
		if (path.startsWith("deep/")) {
			return 1;
		}
		if (path.startsWith("infra/") && spec.params().contains("realm")) {
			return 1;
		}
		return 0;
	}

	@Override
	public Collection<? extends PlotView> plots() {
		if (this.plots == null) {
			List<PlotView> out = new ArrayList<>();
			for (PlotView p : this.root.plots()) {
				int l = level(this.state, p);
				if (l == 2 || (l == 1) == this.deep) {
					out.add(p);
				}
			}
			this.plots = out;
		}
		return this.plots;
	}

	@Override
	public @Nullable PlotView plot(int id) {
		PlotView p = this.root.plot(id);
		if (p == null) {
			return null;
		}
		int l = level(this.state, p);
		return l == 2 || (l == 1) == this.deep ? p : null;
	}

	@Override
	public int population() {
		return this.population;
	}

	// ---- the rest is the settlement's own

	@Override
	public long id() {
		return this.root.id();
	}

	@Override
	public String name() {
		return this.root.name();
	}

	@Override
	public Culture culture() {
		return this.root.culture();
	}

	@Override
	public SettlementTier tier() {
		return this.root.tier();
	}

	@Override
	public ResourceKey<Level> dimension() {
		return this.root.dimension();
	}

	@Override
	public BlockPos center() {
		return this.root.center();
	}

	@Override
	public BoundingBox bounds() {
		return this.root.bounds();
	}

	@Override
	public long foundedDay() {
		return this.root.foundedDay();
	}

	@Override
	public boolean embodied() {
		return this.root.embodied();
	}

	@Override
	public Collection<Resident> residents() {
		return this.root.residents();
	}

	@Override
	public @Nullable Resident resident(UUID id) {
		return this.root.resident(id);
	}

	@Override
	public int beds() {
		return this.root.beds();
	}

	@Override
	public Stockpile stockpile() {
		return this.root.stockpile();
	}

	@Override
	public long treasury() {
		return this.root.treasury();
	}

	@Override
	public void deposit(long bits) {
		this.root.deposit(bits);
	}

	@Override
	public boolean withdraw(long bits) {
		return this.root.withdraw(bits);
	}

	@Override
	public int reputation(UUID player) {
		return this.root.reputation(player);
	}

	@Override
	public void addReputation(UUID player, int delta) {
		this.root.addReputation(player, delta);
	}

	@Override
	public void addResident(Resident resident) {
		this.root.addResident(resident);
	}

	@Override
	public boolean removeResident(UUID id) {
		return this.root.removeResident(id);
	}

	@Override
	public void setResidentEmbodied(UUID id, boolean embodied) {
		this.root.setResidentEmbodied(id, embodied);
	}

	@Override
	public boolean isResidentEmbodied(UUID id) {
		return this.root.isResidentEmbodied(id);
	}

	@Override
	public void creditLabour(UUID resident, JobType job, int ticks) {
		this.root.creditLabour(resident, job, ticks);
	}

	@Override
	public List<Poi> pois(PoiType type) {
		return this.root.pois(type);
	}

	@Override
	public void advanceConstruction(int plotId, int steps) {
		this.root.advanceConstruction(plotId, steps);
	}

	@Override
	public void setPlotOwner(int plotId, @Nullable UUID player) {
		this.root.setPlotOwner(plotId, player);
	}

	@Override
	public int territoryRadius() {
		return this.root.territoryRadius();
	}

	@Override
	public List<ResourceSite> sites() {
		// the deep town's land is its realm; the surface keeps the territory's sites
		return this.deep ? List.of() : this.root.sites();
	}

	@Override
	public <T> T data(HearthDataKey<T> key) {
		return this.root.data(key);
	}

	@Override
	public void markDirty() {
		this.root.markDirty();
	}
}
