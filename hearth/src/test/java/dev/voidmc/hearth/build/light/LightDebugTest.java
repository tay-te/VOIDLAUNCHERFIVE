package dev.voidmc.hearth.build.light;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.AutoLighter;
import dev.voidmc.hearth.build.kit.light.Fixture;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Prints what the lighting pass placed in one spec, and the checks with and without it. HEARTH_LIGHT_DEBUG =
 * archetype,culture,terrain,seed,facing,height[,frontage,depth[,terrainSeed]] (frontage "-" keeps the random size);
 * HEARTH_LIGHT_PARAMS = key=value;key=value for the spec's params (numbers as ints, true/false as booleans).
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_LIGHT_DEBUG", matches = ".+")
class LightDebugTest {
	static {
		LightSupport.init();
	}

	@Test
	void debug() {
		String[] a = System.getenv("HEARTH_LIGHT_DEBUG").split(",");
		ArchetypeInfo info = LightSupport.SERVICE.archetype(dev.voidmc.hearth.Hearth.id(a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase());
		SyntheticTerrain probe = SyntheticTerrain.named(a[2], Integer.parseInt(a.length > 8 ? a[8] : "42"));
		long seed = Long.parseLong(a[3]);
		Direction facing = Direction.byName(a[4]);
		int height = Integer.parseInt(a[5]) <= 0 ? Math.max(info.maxHeight(), 8) : Integer.parseInt(a[5]);
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		if (a.length > 7 && !a[6].equals("-")) {
			frontage = Integer.parseInt(a[6]);
			depth = Integer.parseInt(a[7]);
		}
		CompoundTag params = new CompoundTag();
		String ps = System.getenv("HEARTH_LIGHT_PARAMS");
		if (ps != null) {
			for (String kv : ps.split(";")) {
				String[] e = kv.split("=", 2);
				if (e.length != 2) {
					continue;
				}
				if (e[1].equals("true") || e[1].equals("false")) {
					params.putBoolean(e[0], Boolean.parseBoolean(e[1]));
				} else if (e[1].matches("-?\\d+")) {
					params.putInt(e[0], Integer.parseInt(e[1]));
				} else {
					params.putString(e[0], e[1]);
				}
			}
		}
		PlotSpec base = LightSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe, params, frontage, depth, 2);
		PlotSpec spec = new PlotSpec(base.archetype(), base.style(), seed, base.origin(), base.sizeX(), base.sizeZ(), facing, height, 2, base.params());
		Draft d = LightSupport.SERVICE.draw(spec, probe);
		String tc = System.getenv("HEARTH_LIGHT_TRACE");
		if (tc != null) {
			String[] t = tc.split(",");
			AutoLighter.trace = new int[]{Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2])};
		}
		StringBuilder sb = new StringBuilder("\nLIGHT DEBUG " + String.join(",", a) + "\n");
		java.util.Map<Long, String> preReached = System.getenv("HEARTH_LIGHT_REACH") != null ? AutoLighter.reached(LightSupport.SERVICE.draw(spec, probe)) : null;
		for (Fixture f : AutoLighter.lightLogged(d)) {
			sb.append("  ").append(f.kind());
			for (Fixture.Part p : f.parts()) {
				BlockPos w = d.frame().toWorld(p.x(), p.y(), p.z());
				sb.append(" [").append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(" w").append(w.toShortString()).append(' ')
					.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).getPath()).append(']');
			}
			sb.append('\n');
		}
		BlueprintImpl bp = Compiler.compile(d);
		sb.append("with: ").append(BlueprintChecks.all(bp, probe)).append('\n');
		var res = dev.voidmc.hearth.build.check.LightCheck.evaluate(bp, probe);
		sb.append("  needs ").append(res.needs().size()).append(", unmet ").append(res.unmet().size()).append('\n');
		for (var u : res.unmet()) {
			int lx = d.frame().localX(u.x(), u.z());
			int lz = d.frame().localZ(u.x(), u.z());
			sb.append("  unmet ").append(u.kind()).append(" local ").append(lx).append(',').append(u.y() - spec.floorY()).append(',').append(lz)
				.append(" light ").append(res.light().at(u.x(), u.y(), u.z())).append(" feet ").append(res.grid().at(u.x(), u.y(), u.z()))
				.append(" below ").append(res.grid().at(u.x(), u.y() - 1, u.z())).append('\n');
		}
		if (preReached != null) {
			// a map at the asked level: B reached in both, C in the check only, P in the pass only, # solid, . open
			int my = Integer.parseInt(System.getenv("HEARTH_LIGHT_REACH"));
			for (int dy = 0; dy <= 1; dy++) {
				sb.append("REACH MAP y").append(my + dy).append(" (feet/head blocks shown where not air)\n");
				for (int lz = -4; lz < d.depth() + 4; lz++) {
					StringBuilder row = new StringBuilder(String.format(java.util.Locale.ROOT, "%3d ", lz));
					for (int lx = -4; lx < d.frontage() + 4; lx++) {
						BlockPos w = d.frame().toWorld(lx, my + dy, lz);
						boolean post = res.spaces().reached(w.getX(), w.getY(), w.getZ());
						boolean pre = preReached.containsKey(BlockPos.asLong(lx, my + dy, lz));
						var st = res.grid().at(w.getX(), w.getY(), w.getZ());
						char ch = post && pre ? 'B' : post ? 'C' : pre ? 'P' : st.isAir() ? '.' : dev.voidmc.hearth.build.kit.light.Spaces.passable(st) ? ',' : '#';
						if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
							ch = 'D';
						} else if (st.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
							ch = 'G';
						}
						row.append(ch);
					}
					sb.append(row).append('\n');
				}
			}
			// blocks the pass and the check disagree on (the pass reads the draft and its site, the check the blueprint)
			Draft dPre = LightSupport.SERVICE.draw(spec, probe);
			dev.voidmc.hearth.build.blueprint.ShapeResolver.resolve(dPre, dPre.site());
			int diffs = 0;
			for (int ly = -3; ly < 12 && diffs < 30; ly++) {
				for (int lz = -4; lz < d.depth() + 4 && diffs < 30; lz++) {
					for (int lx = -4; lx < d.frontage() + 4 && diffs < 30; lx++) {
						BlockPos w = d.frame().toWorld(lx, ly, lz);
						var pc = dPre.cell(lx, ly, lz);
						var pst = pc != null ? pc.state()
							: ly < dPre.site().ground(lx, lz) ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
							: dPre.site().water(lx, lz) != dev.voidmc.hearth.build.terrain.Site.NONE && ly < dPre.site().water(lx, lz)
								? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
						var cs = res.grid().at(w.getX(), w.getY(), w.getZ());
						if (Spaces.passable(pst) != Spaces.passable(cs) || Spaces.standable(pst) != Spaces.standable(cs)) {
							diffs++;
							sb.append("  GRID differs at local ").append(lx).append(',').append(ly).append(',').append(lz).append(" pass ").append(pst).append(" check ").append(cs)
								.append(pc != null ? " stage " + pc.stage() : " natural").append('\n');
						}
					}
				}
			}
			int shown = 0;
			for (int y = res.grid().y0; y < res.grid().y0 + res.grid().sy && shown < 8; y++) {
				for (int z = res.grid().z0; z < res.grid().z0 + res.grid().sz && shown < 8; z++) {
					for (int x = res.grid().x0; x < res.grid().x0 + res.grid().sx && shown < 8; x++) {
						int lx = d.frame().localX(x, z);
						int lz = d.frame().localZ(x, z);
						int ly = y - spec.floorY();
						boolean post = res.spaces().reached(x, y, z);
						boolean pre = preReached.containsKey(BlockPos.asLong(lx, ly, lz));
						if (post != pre) {
							shown++;
							sb.append("  REACH differs at local ").append(lx).append(',').append(ly).append(',').append(lz).append(" check ").append(post)
								.append(" pass ").append(pre).append(" feet ").append(res.grid().at(x, y, z)).append(" below ").append(res.grid().at(x, y - 1, z)).append('\n');
						}
					}
				}
			}
		}
		for (int[] e : d.entrances()) {
			sb.append("  entrance local ").append(e[0]).append(',').append(e[1]).append(',').append(e[2]).append('\n');
		}
		if (!res.unmet().isEmpty()) {
			var u = res.unmet().get(0);
			int lx = d.frame().localX(u.x(), u.z());
			int lz = d.frame().localZ(u.x(), u.z());
			int ly = u.y() - spec.floorY();
			sb.append("LOCAL (pass):\n").append(AutoLighter.describe(d, lx, ly, lz));
			sb.append("WORLD (check), same local cells:\n");
			for (int yy = ly - 1; yy <= ly + 3; yy++) {
				sb.append("y").append(yy).append(":\n");
				for (int zz = lz - 2; zz <= lz + 2; zz++) {
					for (int xx = lx - 2; xx <= lx + 2; xx++) {
						BlockPos w = d.frame().toWorld(xx, yy, zz);
						var st = res.grid().at(w.getX(), w.getY(), w.getZ());
						String n = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
						String flag = (res.spaces().reached(w.getX(), w.getY(), w.getZ()) ? "R" : res.spaces().walkable(w.getX(), w.getY(), w.getZ()) ? "w" : " ") + " ";
						sb.append(String.format(java.util.Locale.ROOT, "%2d%s %-10.10s ", res.light().at(w.getX(), w.getY(), w.getZ()), flag, n));
					}
					sb.append('\n');
				}
			}
		}
		CompoundTag none = params.copy();
		none.putString("lighting", "none");
		PlotSpec bare = new PlotSpec(base.archetype(), base.style(), seed, base.origin(), base.sizeX(), base.sizeZ(), facing, height, 2, none);
		sb.append("without: ").append(BlueprintChecks.all(Compiler.compile(LightSupport.SERVICE.draw(bare, probe)), probe)).append('\n');
		System.out.println(sb);
	}
}
