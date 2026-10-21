package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Where a town washes (docs/LIVING_WORLD_2.md §2.3): open water within {@code reach} blocks of its bounds with a bank to
 * kneel on, found once by a scan of loaded columns while the town is embodied (spread over passes, a few hundred block
 * reads each) and cached; else a well's or fountain's meeting point. A river is told from a lake by its biome.
 */
public final class WashPlaces {
	/** Columns apart the scan samples. */
	static final int STEP = 4;
	static final int MOST = 12;

	/** A place to wash: the bank block a washer kneels on and the water before them. */
	public record Spot(BlockPos bank, BlockPos water, String kind) {
	}

	/** One town's scan, in progress or done. */
	static final class Scan {
		final int minX;
		final int minZ;
		final int width;
		final int depth;
		int next;
		final List<Spot> found = new ArrayList<>();
		@Nullable List<Spot> done;

		Scan(BoundingBox area) {
			this.minX = area.minX();
			this.minZ = area.minZ();
			this.width = (area.maxX() - area.minX()) / STEP + 1;
			this.depth = (area.maxZ() - area.minZ()) / STEP + 1;
		}

		int columns() {
			return this.width * this.depth;
		}
	}

	private static final Map<Long, Scan> SCANS = new HashMap<>();

	private WashPlaces() {
	}

	/** The town's wash places by the culture's preference, nearest first; empty until its scan is done (or if none). */
	public static List<Spot> of(Settlement s) {
		Scan scan = SCANS.get(s.id());
		if (scan == null || scan.done == null) {
			return List.of();
		}
		List<String> prefer = RhythmData.of(s.culture()).wash().places();
		for (String kind : prefer) {
			List<Spot> out = new ArrayList<>();
			for (Spot sp : scan.done) {
				if (sp.kind().equals(kind)) {
					out.add(sp);
				}
			}
			if (!out.isEmpty()) {
				return out;
			}
		}
		return List.of();
	}

	public static boolean scanned(Settlement s) {
		Scan scan = SCANS.get(s.id());
		return scan != null && scan.done != null;
	}

	/** Goes on with a town's scan for up to {@code reads} block reads; returns the reads made. */
	public static int step(ServerLevel level, Settlement s, int reach, int reads) {
		Scan scan = SCANS.get(s.id());
		if (scan == null) {
			BoundingBox b = s.bounds();
			scan = new Scan(new BoundingBox(b.minX() - reach, b.minY(), b.minZ() - reach, b.maxX() + reach, b.maxY(), b.maxZ() + reach));
			SCANS.put(s.id(), scan);
		}
		if (scan.done != null) {
			return 0;
		}
		int used = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		while (used < reads && scan.next < scan.columns()) {
			int i = scan.next++;
			int x = scan.minX + i % scan.width * STEP;
			int z = scan.minZ + i / scan.width * STEP;
			if (!level.hasChunk(x >> 4, z >> 4)) {
				used++;
				continue;
			}
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			m.set(x, top, z);
			BlockState surface = level.getBlockState(m);
			used++;
			if (!surface.getFluidState().is(FluidTags.WATER) || scan.found.size() >= MOST * 3) {
				continue;
			}
			BlockPos water = m.immutable();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				// walk toward the shore a few blocks for a bank to kneel on
				for (int k = 1; k <= 3; k++) {
					BlockPos q = water.relative(d, k);
					if (!level.hasChunk(q.getX() >> 4, q.getZ() >> 4)) {
						break;
					}
					used += 2;
					BlockPos bank = Venues.standable(level, q.above());
					if (bank != null && Math.abs(bank.getY() - (water.getY() + 1)) <= 1) {
						BlockPos edge = q.relative(d.getOpposite());
						if (level.getBlockState(edge).getFluidState().is(FluidTags.WATER) || k == 1) {
							String kind = level.getBiome(water).is(BiomeTags.IS_RIVER) ? "river" : "lake";
							add(scan.found, new Spot(bank, k == 1 ? water : edge, kind));
						}
						d = null;
						break;
					}
					if (!level.getBlockState(q).getFluidState().is(FluidTags.WATER)) {
						break;
					}
				}
				if (d == null) {
					break;
				}
			}
		}
		if (scan.next >= scan.columns()) {
			finish(s, scan);
		}
		return used;
	}

	private static void add(List<Spot> found, Spot spot) {
		for (Spot o : found) {
			if (o.bank().distSqr(spot.bank()) < 5 * 5) {
				return;
			}
		}
		found.add(spot);
	}

	private static void finish(Settlement s, Scan scan) {
		BlockPos c = s.center();
		List<Spot> all = new ArrayList<>(scan.found);
		// the wells and fountains of the town
		for (PlotView p : s.plots()) {
			String path = p.spec().archetype().getPath();
			if (p.state() != dev.voidmc.hearth.api.PlotState.BUILT || !(path.contains("well") || path.contains("fountain"))) {
				continue;
			}
			List<Poi> meets = Venues.inPlot(s.pois(PoiType.MEETING_POINT), p);
			BlockPos at = meets.isEmpty() ? Venues.middle(p.spec()) : meets.get(0).pos();
			all.add(new Spot(at, at, path.contains("fountain") ? "fountain" : "well"));
		}
		all.sort(Comparator.comparingDouble(sp -> sp.bank().distSqr(c)));
		scan.done = List.copyOf(all.size() > MOST * 2 ? all.subList(0, MOST * 2) : all);
	}

	/** Forget a town's scan (it changed, or for tests). */
	public static void forget(long settlement) {
		SCANS.remove(settlement);
	}

	static void clear() {
		SCANS.clear();
	}
}
