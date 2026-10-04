package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.History;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * A history context that fires events (into a stand-in {@link HearthWorld}) and records the phase 3 ones, for tests of
 * what a live settlement announces.
 */
public final class EventContext implements SimContext {
	private static final List<EventContext> LISTENING = new ArrayList<>();
	private static boolean registered;

	private final History.Context base;
	private final Stub world = new Stub();
	public final List<String> events = new ArrayList<>();
	public SimRules rules;

	public EventContext(History.Context base) {
		this.base = base;
		this.rules = base.rules();
		register();
		LISTENING.add(this);
	}

	private static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		SettlementEvents.SITE_FOUND.register((w, s, site) -> record(w, "found " + site.kind().getSerializedName()));
		SettlementEvents.SITE_DEPLETED.register((w, s, site) -> record(w, "depleted " + site.kind().getSerializedName()));
		SettlementEvents.PROSPERITY_CHANGED.register((w, s, rising, why) -> record(w, (rising ? "rising: " : "declining: ") + why));
		SettlementEvents.PLOT_ABANDONED.register((w, s, p, why) -> record(w, "abandoned: " + why));
		SettlementEvents.RESIDENT_REMOVED.register((w, s, r) -> record(w, "removed"));
	}

	private static void record(HearthWorld w, String what) {
		for (EventContext c : LISTENING) {
			if (c.world == w) {
				c.events.add(what);
			}
		}
	}

	public long count(String prefix) {
		return this.events.stream().filter(e -> e.startsWith(prefix)).count();
	}

	@Override
	public SimRules rules() {
		return this.rules;
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
		return this.world;
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

	@Override
	public boolean inWorld() {
		return true;
	}

	/** A world that answers nothing: only the identity events are fired with. */
	private static final class Stub implements HearthWorld {
		@Override
		public ServerLevel level() {
			throw new UnsupportedOperationException();
		}

		@Override
		public TerrainProbe terrain() {
			throw new UnsupportedOperationException();
		}

		@Override
		public long day() {
			return 0;
		}

		@Override
		public Collection<? extends Settlement> settlements() {
			return List.of();
		}

		@Override
		public @Nullable Settlement settlement(long id) {
			return null;
		}

		@Override
		public @Nullable Settlement settlementAt(BlockPos pos) {
			return null;
		}

		@Override
		public @Nullable Settlement nearest(BlockPos pos, int maxDistance) {
			return null;
		}

		@Override
		public <T> T data(HearthDataKey<T> key) {
			return key.factory().get();
		}

		@Override
		public void markDirty() {
		}

		@Override
		public @Nullable Settlement found(BlockPos at, @Nullable Culture culture, @Nullable Settlement parent, String reason) {
			return null;
		}

		@Override
		public @Nullable String whyNotFound(BlockPos at) {
			return "test";
		}

		@Override
		public List<SiteInfo> knownSites(BlockPos near, int radius) {
			return List.of();
		}
	}
}
