package dev.voidmc.hearth.api.testing;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Stockpile;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A plain in-memory {@link Settlement} for tests and the offline previews, so modules can exercise their logic without
 * the sim module. Not the real thing: it knows nothing of blueprints (a plot's progress is just counters), never fires
 * events, and saves nothing. The sim module's own implementation is what runs in game.
 */
public class SimpleSettlement implements Settlement {
	private final long id;
	private final String name;
	private final Culture culture;
	private final BlockPos center;
	private final long foundedDay;
	private final Map<Integer, SimplePlot> plots = new LinkedHashMap<>();
	private final Map<UUID, Resident> residents = new LinkedHashMap<>();
	private final Set<UUID> embodiedResidents = new HashSet<>();
	private final SimpleStockpile stockpile = new SimpleStockpile(10_000);
	private final Map<UUID, Integer> reputation = new HashMap<>();
	private final Map<HearthDataKey<?>, Object> data = new HashMap<>();
	private int nextPlotId;
	private long treasury;
	private boolean embodied;
	private int beds;
	private int dirtyCount;

	public SimpleSettlement(long id, String name, Culture culture, BlockPos center, long foundedDay) {
		this.id = id;
		this.name = name;
		this.culture = culture;
		this.center = center;
		this.foundedDay = foundedDay;
	}

	/** Adds a plot in the {@link PlotState#PLANNED} state and returns it. */
	public SimplePlot addPlot(PlotSpec spec) {
		SimplePlot plot = new SimplePlot(this.nextPlotId++, spec);
		this.plots.put(plot.id, plot);
		return plot;
	}

	public void removePlot(int id) {
		this.plots.remove(id);
	}

	public void setBeds(int beds) {
		this.beds = beds;
	}

	public void setEmbodied(boolean embodied) {
		this.embodied = embodied;
	}

	/** How many times {@link #markDirty} was called (tests can assert a module marks its changes). */
	public int dirtyCount() {
		return this.dirtyCount;
	}

	@Override
	public long id() {
		return this.id;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public Culture culture() {
		return this.culture;
	}

	@Override
	public SettlementTier tier() {
		return SettlementTier.forPopulation(population());
	}

	@Override
	public ResourceKey<Level> dimension() {
		return Level.OVERWORLD;
	}

	@Override
	public BlockPos center() {
		return this.center;
	}

	@Override
	public BoundingBox bounds() {
		BoundingBox box = new BoundingBox(this.center);
		for (SimplePlot p : this.plots.values()) {
			PlotSpec s = p.spec;
			box.encapsulate(s.origin());
			box.encapsulate(s.origin().offset(s.sizeX() - 1, s.maxHeight(), s.sizeZ() - 1));
		}
		return box;
	}

	@Override
	public long foundedDay() {
		return this.foundedDay;
	}

	@Override
	public boolean embodied() {
		return this.embodied;
	}

	@Override
	public Collection<SimplePlot> plots() {
		return Collections.unmodifiableCollection(this.plots.values());
	}

	@Override
	public @Nullable SimplePlot plot(int id) {
		return this.plots.get(id);
	}

	@Override
	public Collection<Resident> residents() {
		return Collections.unmodifiableCollection(this.residents.values());
	}

	@Override
	public @Nullable Resident resident(UUID id) {
		return this.residents.get(id);
	}

	@Override
	public int population() {
		return this.residents.size();
	}

	@Override
	public int beds() {
		return this.beds;
	}

	@Override
	public SimpleStockpile stockpile() {
		return this.stockpile;
	}

	@Override
	public long treasury() {
		return this.treasury;
	}

	@Override
	public void deposit(long bits) {
		this.treasury += bits;
	}

	@Override
	public boolean withdraw(long bits) {
		if (bits > this.treasury) {
			return false;
		}
		this.treasury -= bits;
		return true;
	}

	@Override
	public int reputation(UUID player) {
		return this.reputation.getOrDefault(player, 0);
	}

	@Override
	public void addReputation(UUID player, int delta) {
		this.reputation.merge(player, delta, Integer::sum);
	}

	@Override
	public void addResident(Resident resident) {
		this.residents.put(resident.id, resident);
	}

	@Override
	public boolean removeResident(UUID id) {
		this.embodiedResidents.remove(id);
		return this.residents.remove(id) != null;
	}

	@Override
	public void setResidentEmbodied(UUID id, boolean embodied) {
		if (embodied) {
			this.embodiedResidents.add(id);
		} else {
			this.embodiedResidents.remove(id);
		}
	}

	@Override
	public boolean isResidentEmbodied(UUID id) {
		return this.embodiedResidents.contains(id);
	}

	@Override
	public void advanceConstruction(int plotId, int steps) {
		SimplePlot plot = this.plots.get(plotId);
		if (plot != null) {
			plot.step += steps;
			if (plot.state == PlotState.PLANNED) {
				plot.state = PlotState.BUILDING;
			}
		}
	}

	@Override
	public void setPlotOwner(int plotId, @Nullable UUID player) {
		SimplePlot plot = this.plots.get(plotId);
		if (plot != null) {
			plot.owner = player;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T data(HearthDataKey<T> key) {
		return (T) this.data.computeIfAbsent(key, k -> k.factory().get());
	}

	@Override
	public void markDirty() {
		this.dirtyCount++;
	}

	/** A plot whose progress tests set directly. */
	public static final class SimplePlot implements PlotView {
		private final int id;
		private final PlotSpec spec;
		public PlotState state = PlotState.PLANNED;
		public int stage;
		public int step;
		public @Nullable UUID owner;

		SimplePlot(int id, PlotSpec spec) {
			this.id = id;
			this.spec = spec;
		}

		@Override
		public int id() {
			return this.id;
		}

		@Override
		public PlotSpec spec() {
			return this.spec;
		}

		@Override
		public PlotState state() {
			return this.state;
		}

		@Override
		public int stage() {
			return this.stage;
		}

		@Override
		public int step() {
			return this.step;
		}

		@Override
		public @Nullable UUID owner() {
			return this.owner;
		}
	}

	/** Every kind of goods with the same capacity. */
	public static final class SimpleStockpile implements Stockpile {
		private final int[] amounts = new int[Goods.VALUES.length];
		private final int capacity;

		public SimpleStockpile(int capacity) {
			this.capacity = capacity;
		}

		@Override
		public int get(Goods goods) {
			return this.amounts[goods.ordinal()];
		}

		@Override
		public int add(Goods goods, int amount) {
			int added = Math.max(0, Math.min(amount, this.capacity - this.amounts[goods.ordinal()]));
			this.amounts[goods.ordinal()] += added;
			return added;
		}

		@Override
		public int take(Goods goods, int amount) {
			int taken = Math.max(0, Math.min(amount, this.amounts[goods.ordinal()]));
			this.amounts[goods.ordinal()] -= taken;
			return taken;
		}

		@Override
		public int capacity(Goods goods) {
			return this.capacity;
		}
	}
}
