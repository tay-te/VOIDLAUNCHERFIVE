package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.check.PlayChecks;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Art review 2's floating-piece probe, kept (ART_REVIEW_2.md item 30): over a town grown by the planner, every built
 * plot's blueprint on the town's own land ({@link PlayChecks#floatingPieces}, the gate's {@code floating-piece} rule:
 * structure must rest on the ground or on structure), then the composed town as {@link TownScene} draws it, plot over
 * plot, where a neighbour's cut or a road can take the ground from under a piece. The unit tests build every archetype
 * on synthetic land with its floor at the street; the planner sets floors on real slopes and lays plots side by side,
 * which is where the walkway decks, the festival trees and the tilt-yard fences were found hanging.
 */
final class TownFloat {
	private TownFloat() {
	}

	/** One line per plot with a floating piece (its own, then in the composed town), and a summary line first. */
	static List<String> report(List<PlotView> plots, BlueprintServiceImpl real, BlueprintService service, TerrainProbe terrain, Culture culture, int x0,
		int z0, int x1, int z1) {
		List<String> own = new ArrayList<>();
		Map<Integer, PlotView> byId = new HashMap<>();
		for (PlotView p : plots) {
			byId.put(p.id() + 1, p);
			if (p.state() == PlotState.PLANNED || real.archetype(p.spec().archetype()) == null) {
				continue;
			}
			try {
				BlueprintImpl bp = real.compiled(p.spec(), terrain);
				PlayChecks.Pieces pc = PlayChecks.floatingPieces(bp, terrain);
				if (pc.count() > 0) {
					own.add(line("own", p, pc.count(), pc.cells(), pc.first(), null));
				}
			} catch (RuntimeException e) {
				own.add("own      plot #" + p.id() + " " + p.spec().archetype().getPath() + ": " + e);
			}
		}
		// the composed town: what the plots placed (each as far as it is built) over the land that is left
		Long2IntOpenHashMap owner = new Long2IntOpenHashMap();
		Voxels v = TownScene.build(plots, service, terrain, x0, z0, x1, z1, culture, owner);
		Long2ObjectMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
		for (Long2ObjectMap.Entry<BlockState> e : v.all().long2ObjectEntrySet()) {
			long k = e.getLongKey();
			if (owner.containsKey(k) && !e.getValue().isAir()) {
				placed.put(BlockPos.asLong(Voxels.kx(k), Voxels.ky(k), Voxels.kz(k)), e.getValue());
			}
		}
		PlayChecks.Pieces town = PlayChecks.floatingPieces(placed, k -> {
			long vk = Voxels.key(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
			BlockState s = v.get(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
			return s != null && !owner.containsKey(vk) && !s.isAir() && s.getFluidState().isEmpty();
		});
		List<String> composed = new ArrayList<>();
		Map<Integer, int[]> perPlot = new HashMap<>();
		Map<Integer, Long> firstOf = new HashMap<>();
		for (long[] piece : town.each()) {
			long k = piece[0];
			int id = owner.get(Voxels.key(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)));
			PlotView p = byId.get(id);
			if (p == null || !inside(p.spec(), x0, z0, x1, z1)) {
				// a plot cut by the window's edge loses its footing outside it: not a finding
				continue;
			}
			int[] n = perPlot.computeIfAbsent(id, i -> new int[2]);
			n[0]++;
			n[1] += (int) piece[1];
			firstOf.putIfAbsent(id, k);
		}
		for (var e : perPlot.entrySet()) {
			PlotView p = byId.get(e.getKey());
			composed.add(line("composed", p, e.getValue()[0], e.getValue()[1], firstOf.get(e.getKey()), placed.get(firstOf.get(e.getKey()))));
		}
		composed.sort(null);
		own.sort(null);
		List<String> out = new ArrayList<>();
		out.add(String.format(Locale.ROOT, "floating pieces: %d plots with pieces of their own on the town's land, %d in the composed town", own.size(),
			composed.size()));
		out.addAll(own);
		out.addAll(composed);
		return out;
	}

	private static boolean inside(PlotSpec s, int x0, int z0, int x1, int z1) {
		return s.origin().getX() > x0 && s.origin().getZ() > z0 && s.origin().getX() + s.sizeX() - 1 < x1 && s.origin().getZ() + s.sizeZ() - 1 < z1;
	}

	private static String line(String kind, PlotView p, int pieces, int cells, long first, BlockState s) {
		PlotSpec spec = p.spec();
		LocalFrame f = new LocalFrame(spec);
		int x = BlockPos.getX(first);
		int y = BlockPos.getY(first);
		int z = BlockPos.getZ(first);
		return String.format(Locale.ROOT, "%-8s plot #%d %s (%s): %d piece%s, %d blocks, first at %d,%d,%d (local %d/%d/%d)%s", kind, p.id(),
			spec.archetype().getPath(), spec.style().getPath(), pieces, pieces == 1 ? "" : "s", cells, x, y, z, f.localX(x, z), y - spec.floorY(), f.localZ(x, z),
			s == null ? "" : " " + BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
	}
}
