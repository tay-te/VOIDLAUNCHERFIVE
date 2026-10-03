package dev.voidmc.hearth.npc.place;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The places of one settlement that folk use, per plot: from each built plot's blueprint POIs, or by scanning the
 * plot's blocks when there are none (a player-built house, the sandbox). Kept until the plot changes (its state or
 * stage), refreshed a few plots at a time, and gathered by type for the town-wide places (squares, inns, altars,
 * guard posts, viewpoints, benches).
 *
 * <p>Server thread only.
 */
public final class SettlementPlaces {
	/** Scans allowed per server tick across all settlements: they read blocks, so they are rationed. */
	private static final int SCANS_PER_TICK = 1;
	private static long budgetTick = Long.MIN_VALUE;
	private static int budgetLeft;

	private final Map<Integer, PlotPois> byPlot = new HashMap<>();
	private final EnumMap<PoiType, List<Poi>> publicPlaces = new EnumMap<>(PoiType.class);
	private final List<Poi> innPlaces = new ArrayList<>();
	private boolean dirty = true;

	private record PlotPois(int stamp, List<Poi> pois, boolean scanned) {
	}

	/** The POIs of a plot, or an empty list if they are not known yet (they will be after a later refresh). */
	public List<Poi> plot(ServerLevel level, Settlement settlement, @Nullable PlotView plot) {
		if (plot == null) {
			return List.of();
		}
		PlotPois known = this.byPlot.get(plot.id());
		int stamp = stamp(plot);
		if (known != null && known.stamp == stamp) {
			return known.pois;
		}
		PlotPois fresh = compute(level, settlement, plot, stamp);
		if (fresh != null) {
			this.byPlot.put(plot.id(), fresh);
			this.dirty = true;
			return fresh.pois;
		}
		return known != null ? known.pois : List.of();
	}

	/** Brings a few out-of-date plots up to date; the manager calls this while the settlement is embodied. */
	public void refresh(ServerLevel level, Settlement settlement, int maxPlots) {
		int done = 0;
		for (PlotView plot : settlement.plots()) {
			PlotPois known = this.byPlot.get(plot.id());
			if (known == null || known.stamp != stamp(plot)) {
				plot(level, settlement, plot);
				if (++done >= maxPlots) {
					break;
				}
			}
		}
		// Forget plots that no longer exist.
		if (this.byPlot.size() > settlement.plots().size()) {
			this.byPlot.keySet().removeIf(id -> settlement.plot(id) == null);
			this.dirty = true;
		}
	}

	/** Every known POI of a type in the town's public buildings and grounds (not inside people's homes). */
	public List<Poi> all(ServerLevel level, Settlement settlement, PoiType type) {
		if (this.dirty) {
			rebuild(settlement);
		}
		return this.publicPlaces.getOrDefault(type, List.of());
	}

	/** Seats, tables and hearths in the settlement's inns. */
	public List<Poi> inn(Settlement settlement) {
		if (this.dirty) {
			rebuild(settlement);
		}
		return this.innPlaces;
	}

	/** Every known POI within {@code radius} blocks of a spot, whatever plot it is in (for folk without plots). */
	public List<Poi> near(BlockPos at, int radius) {
		List<Poi> out = new ArrayList<>();
		double r2 = (double) radius * radius;
		for (PlotPois pp : this.byPlot.values()) {
			for (Poi p : pp.pois) {
				if (p.pos().distSqr(at) <= r2) {
					out.add(p);
				}
			}
		}
		return out;
	}

	public void invalidate(int plotId) {
		this.byPlot.remove(plotId);
		this.dirty = true;
	}

	private void rebuild(Settlement settlement) {
		this.publicPlaces.clear();
		this.innPlaces.clear();
		for (Map.Entry<Integer, PlotPois> e : this.byPlot.entrySet()) {
			PlotView plot = settlement.plot(e.getKey());
			if (plot == null) {
				continue;
			}
			String path = plot.spec().archetype().getPath();
			boolean home = path.startsWith("house/") || path.startsWith("dev/home");
			boolean inn = path.equals("commerce/inn");
			for (Poi poi : e.getValue().pois) {
				if (inn && (poi.type() == PoiType.SEAT || poi.type() == PoiType.TABLE || poi.type() == PoiType.HEARTH || poi.type() == PoiType.COUNTER)) {
					this.innPlaces.add(poi);
				}
				if (!home || poi.type() == PoiType.DOOR || poi.type() == PoiType.VIEWPOINT) {
					this.publicPlaces.computeIfAbsent(poi.type(), t -> new ArrayList<>()).add(poi);
				}
			}
		}
		this.publicPlaces.replaceAll((t, l) -> Collections.unmodifiableList(l));
		this.dirty = false;
	}

	private static @Nullable PlotPois compute(ServerLevel level, Settlement settlement, PlotView plot, int stamp) {
		if (plot.state() != PlotState.BUILT && plot.state() != PlotState.ABANDONED) {
			return new PlotPois(stamp, List.of(), false);
		}
		Blueprint blueprint = BlueprintSource.of(level, settlement, plot);
		if (blueprint != null && !blueprint.pois().isEmpty()) {
			return new PlotPois(stamp, List.copyOf(blueprint.pois()), false);
		}
		if (!takeScanBudget(level)) {
			return null;
		}
		BoundingBox box = blueprint != null ? blueprint.bounds() : bounds(plot.spec());
		return new PlotPois(stamp, List.copyOf(PoiScanner.scan(level, box)), true);
	}

	public static BoundingBox bounds(PlotSpec spec) {
		BlockPos o = spec.origin();
		return new BoundingBox(o.getX(), o.getY() - 2, o.getZ(), o.getX() + spec.sizeX() - 1, o.getY() + Math.max(4, spec.maxHeight()),
			o.getZ() + spec.sizeZ() - 1);
	}

	private static int stamp(PlotView plot) {
		return plot.state().ordinal() * 31 + plot.stage();
	}

	/** Takes one scan from this tick's ration; false if it is spent. */
	public static boolean takeScanBudget(ServerLevel level) {
		long tick = level.getServer().getTickCount();
		if (tick != budgetTick) {
			budgetTick = tick;
			budgetLeft = SCANS_PER_TICK;
		}
		if (budgetLeft <= 0) {
			return false;
		}
		budgetLeft--;
		return true;
	}
}
