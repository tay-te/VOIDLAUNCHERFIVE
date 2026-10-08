package dev.voidmc.hearth.build;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A debugging probe, not a gate: one invariant-test case ({@code BuildTestSupport.spec}) compiled with each step of the
 * access pass left out in turn, and the invariant checks' problems printed for each, to find the step that made one.
 * {@code HEARTH_CASE=<archetype>,<culture>,<terrain>,<seed>,<facing>[,<variant>[,<decay>]]}, several separated by ';' (an empty
 * variant leaves it to the seed).
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_CASE", matches = ".+")
class AccessCaseProbeTest {
	static {
		BuildTestSupport.init();
	}

	private static java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> blocks(dev.voidmc.hearth.build.blueprint.BlueprintImpl bp) {
		var m = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				m.put(bp.pos(st, i), bp.state(st, i));
			}
		}
		return m;
	}

	@Test
	void probe() {
		for (String one : System.getenv("HEARTH_CASE").split(";")) {
			String[] a = one.split(",");
			ArchetypeInfo info = BuildTestSupport.SERVICE.archetypes().stream().filter(i -> i.id().getPath().equals(a[0])).findFirst().orElseThrow();
			Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
			SyntheticTerrain probe = SyntheticTerrain.named(a[2], 42);
			CompoundTag params = new CompoundTag();
			if (a.length > 5 && !a[5].isEmpty()) {
				params.putInt("variant", Integer.parseInt(a[5]));
			}
			if (a.length > 6) {
				params.putDouble(dev.voidmc.hearth.build.age.Decay.PARAM, Double.parseDouble(a[6]));
			}
			PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(c), Long.parseLong(a[3]), Direction.valueOf(a[4].toUpperCase(Locale.ROOT)), probe,
				params);
			for (String skip : new String[]{"", "doors", "stairs", "lofts", "guards", "doors,stairs,lofts,guards"}) {
				System.setProperty("hearth.access.skip", skip);
				var problems = BlueprintChecks.all(dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe)), probe);
				if (System.getenv("HEARTH_DARK") != null) {
					var bp = dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe));
					var r = dev.voidmc.hearth.build.check.LightCheck.evaluate(bp, probe);
					var all = blocks(bp);
					String at = System.getenv("HEARTH_DARK");
					if (at.contains(",")) {
						String[] q = at.split(",");
						int qx = Integer.parseInt(q[0]), qy = Integer.parseInt(q[1]), qz = Integer.parseInt(q[2]);
						for (var n : r.needs()) {
							if (n.x() == qx && n.y() == qy && n.z() == qz) {
								System.out.println("NEED " + n);
							}
						}
						for (int dir = 0; dir < 4; dir++) {
							int ddx = dir == 0 ? -1 : dir == 1 ? 1 : 0, ddz = dir == 2 ? -1 : dir == 3 ? 1 : 0;
							StringBuilder line = new StringBuilder("LINE " + ddx + "," + ddz + ":");
							for (int k = 1; k <= 8; k++) {
								int lx = qx + ddx * k, lz = qz + ddz * k;
								line.append(" ").append(k).append("=").append(r.grid().at(lx, qy + 1, lz).getBlock().getDescriptionId().replace("block.minecraft.", ""))
									.append(r.spaces().roofed(lx, qy, lz) ? "/R" : "/-").append(r.grid().contains(lx, qy + 1, lz) ? "" : "/OUT");
							}
							System.out.println(line);
						}
						System.out.println("AT light " + r.light().at(qx, qy, qz) + " reached " + r.spaces().reached(qx, qy, qz) + " enclosed "
							+ r.spaces().enclosed(qx, qy, qz) + " roofed " + r.spaces().roofed(qx, qy, qz));
					}
					for (var n : r.unmet()) {
						StringBuilder col = new StringBuilder();
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								for (int dy = -1; dy <= 3; dy++) {
									var st = all.get(new net.minecraft.core.BlockPos(n.x() + dx, n.y() + dy, n.z() + dz));
									if (st != null && !st.isAir()) {
										col.append(" [" + dx + "," + dy + "," + dz + " " + st.getBlock().getDescriptionId().replace("block.minecraft.", "") + "]");
									}
								}
							}
						}
						System.out.println("DARK " + n + " light " + r.light().at(n.x(), n.y(), n.z()) + " reached " + r.spaces().reached(n.x(), n.y(), n.z()) + col);
					}
				}
				System.out.println("CASE " + one + " skip [" + skip + "]: " + problems.size() + " " + problems);
			}
			System.clearProperty("hearth.access.skip");
			// HEARTH_REACH=y: the walk the loft step reads (doors and stairs done), at a local level: R reached, w walkable unreached
			String reach = System.getenv("HEARTH_REACH");
			if (reach != null && !reach.isBlank()) {
				var d = BuildTestSupport.SERVICE.draw(spec, probe);
				dev.voidmc.hearth.build.kit.Doors.finish(d);
				dev.voidmc.hearth.build.kit.Stairs.finish(d);
				var sp = dev.voidmc.hearth.build.kit.light.Spaces.of(d);
				for (int y = Integer.parseInt(reach) - 1; y <= Integer.parseInt(reach) + 1; y++) {
					System.out.println("REACH y" + y);
					for (int z = -1; z <= d.depth(); z++) {
						StringBuilder row = new StringBuilder(String.format(java.util.Locale.ROOT, "REACH %3d ", z));
						for (int x = -1; x <= d.frontage(); x++) {
							var st = d.get(x, y, z);
							String nm = st == null ? "-" : st.isAir() ? "." : st.getBlock().getDescriptionId().replace("block.minecraft.", "");
							row.append(sp.reached(x, y, z) ? "R" : sp.walkable(x, y, z) ? "w" : " ").append(String.format(java.util.Locale.ROOT, "%-6.6s", nm)).append(' ');
						}
						System.out.println(row);
					}
				}
				var w = d.frame().toWorld(0, 0, 0);
				System.out.println("REACH origin world " + w);
			}
			// HEARTH_SLICE=x0,x1,y0,y1,z0,z1: the blocks with the loft step left out, layer by layer
			String slice = System.getenv("HEARTH_SLICE");
			if (slice != null && !slice.isBlank()) {
				String[] n = slice.split(",");
				System.setProperty("hearth.access.skip", System.getenv().getOrDefault("HEARTH_NEAR_SKIP", "lofts"));
				var m = blocks(dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe)));
				System.clearProperty("hearth.access.skip");
				for (int y = Integer.parseInt(n[2]); y <= Integer.parseInt(n[3]); y++) {
					System.out.println("SLICE y" + y);
					for (int z = Integer.parseInt(n[4]); z <= Integer.parseInt(n[5]); z++) {
						StringBuilder row = new StringBuilder("SLICE " + z + " ");
						for (int x = Integer.parseInt(n[0]); x <= Integer.parseInt(n[1]); x++) {
							var st = m.get(new net.minecraft.core.BlockPos(x, y, z));
							String nm = st == null ? "-" : st.isAir() ? "." : st.getBlock().getDescriptionId().replace("block.minecraft.", "");
							row.append(String.format(java.util.Locale.ROOT, "%-9.9s", nm)).append(' ');
						}
						System.out.println(row);
					}
				}
			}
			// HEARTH_NEAR=x,y,z: the blocks the loft step changed within four of a world position
			String near = System.getenv("HEARTH_NEAR");
			if (near != null && !near.isBlank()) {
				String[] n = near.split(",");
				int nx = Integer.parseInt(n[0]), ny = Integer.parseInt(n[1]), nz = Integer.parseInt(n[2]);
				System.setProperty("hearth.access.skip", System.getenv().getOrDefault("HEARTH_NEAR_SKIP", "lofts"));
				var off = blocks(dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe)));
				System.clearProperty("hearth.access.skip");
				var on = blocks(dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe)));
				for (var e : off.entrySet()) {
					var p = e.getKey();
					if (Math.abs(p.getX() - nx) <= 40 && Math.abs(p.getY() - ny) <= 40 && Math.abs(p.getZ() - nz) <= 40 && !e.getValue().equals(on.get(p))) {
						var o = on.get(p);
						System.out.println("NEAR " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + e.getValue() + (e.getValue().equals(o) ? "" : " -> " + o));
					}
				}
			}
		}
	}
}
