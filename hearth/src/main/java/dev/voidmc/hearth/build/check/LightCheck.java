package dev.voidmc.hearth.build.check;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.LightField;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.build.kit.light.RoadLight;
import dev.voidmc.hearth.build.kit.light.Spaces;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The light check (docs/LIVING_WORLD.md §1): floods block light from every emitter the blueprint places, through the
 * blueprint over its natural terrain, by the game's own rules ({@link LightField}), and holds the result to the rule of
 * the archetype's family ({@link LightRules}): rooms at 8 or more, nowhere folk stand in a building's plot dark, a lamp
 * at every door, lit streets between their lamps.
 */
public final class LightCheck {
	/** Columns read round the plot (light reaching out and back in). */
	public static final int MARGIN = 8;

	private LightCheck() {
	}

	/** Everything the check reads, kept for previews and debugging. */
	public record Result(Grid grid, Spaces spaces, LightField light, List<LightRules.Need> needs, List<LightRules.Need> unmet) {
	}

	/** Reads the blueprint over its terrain and evaluates the rule. */
	public static Result evaluate(BlueprintImpl bp, TerrainProbe probe) {
		return evaluate(bp, new BlueprintChecks.World(bp, probe));
	}

	static Result evaluate(BlueprintImpl bp, BlueprintChecks.World w) {
		PlotSpec spec = bp.spec();
		LocalFrame f = new LocalFrame(spec);
		int[] c = {f.worldX(0, 0), f.worldZ(0, 0), f.worldX(f.frontage() - 1, f.depth() - 1), f.worldZ(f.frontage() - 1, f.depth() - 1)};
		int x0 = Math.min(c[0], c[2]) - MARGIN;
		int x1 = Math.max(c[0], c[2]) + MARGIN;
		int z0 = Math.min(c[1], c[3]) - MARGIN;
		int z1 = Math.max(c[1], c[3]) + MARGIN;
		var b = bp.bounds();
		int y0 = Math.min(b.minY(), spec.floorY()) - 2;
		int y1 = Math.max(b.maxY(), spec.floorY()) + 3;
		Grid g = new Grid(new Grid.Source() {
			@Override
			public BlockState at(int x, int y, int z) {
				return w.at(x, y, z);
			}

			@Override
			public boolean placed(int x, int y, int z) {
				return w.placed.containsKey(BlockPos.asLong(x, y, z));
			}

			@Override
			public boolean inPlot(int x, int z) {
				int lx = f.localX(x, z);
				int lz = f.localZ(x, z);
				return lx >= 0 && lz >= 0 && lx < f.frontage() && lz < f.depth();
			}

			@Override
			public Direction plusZ() {
				return Direction.SOUTH;
			}
		}, x0, y0, z0, x1, y1, z1);
		List<BlockPos> starts = new ArrayList<>(bp.entrances());
		for (int lx = 0; lx < f.frontage(); lx++) {
			int wx = f.worldX(lx, -1);
			int wz = f.worldZ(lx, -1);
			starts.add(new BlockPos(wx, Math.max(w.probe.groundY(wx, wz), spec.floorY()), wz));
		}
		Spaces sp = new Spaces(g, starts);
		LightField light = new LightField(g);
		LightRules.Family family = LightRules.family(spec.archetype());
		List<LightRules.Need> needs = family == LightRules.Family.ROAD ? RoadLight.needs(sp, light, spec)
			: LightRules.needs(sp, bp.entrances(), LightRules.Context.of(family, spec.tier()));
		return new Result(g, sp, light, needs, LightRules.unmet(light, needs));
	}

	/** Adds a line per kind of unmet need to {@code out}. */
	static void light(BlueprintImpl bp, BlueprintChecks.World w, List<String> out) {
		Result r = evaluate(bp, w);
		if (r.unmet().isEmpty()) {
			return;
		}
		Map<LightRules.Kind, List<LightRules.Need>> by = new EnumMap<>(LightRules.Kind.class);
		for (LightRules.Need n : r.unmet()) {
			by.computeIfAbsent(n.kind(), k -> new ArrayList<>()).add(n);
		}
		LocalFrame f = new LocalFrame(bp.spec());
		for (var e : by.entrySet()) {
			LightRules.Need n = e.getValue().get(0);
			int lx = f.localX(n.x(), n.z());
			int lz = f.localZ(n.x(), n.z());
			out.add(e.getValue().size() + " " + e.getKey().label() + " cells darker than " + n.min() + ", e.g. local " + lx + "," + (n.y() - bp.spec().floorY())
				+ "," + lz + " at " + r.light().at(n.x(), n.y(), n.z()));
		}
	}
}
