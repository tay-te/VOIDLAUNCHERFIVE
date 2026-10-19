package dev.voidmc.hearth.build.townhome;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Debugging (not a gate): one audit case's compiled blueprint printed as plan slices in the audit's local frame (x
 * across the front from the left, y above the floor, back at the top), one symbol per block with a legend.
 * {@code HEARTH_SLICE=<archetype>,<culture>,<terrain>,<seed>[;...] HEARTH_SLICE_Y=lo,hi ./gradlew test --tests '*HomeSliceDebugTest*'}
 * (seed 1007 is the audit's first case, facing south; 2007 its second, facing east). Facings are the world's: in a south
 * facing case the back (north) is at the top.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_SLICE", matches = ".+")
class HomeSliceDebugTest {
	private static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$%&*=?!<>^~";

	@Test
	void slices() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String[] ys = System.getenv().getOrDefault("HEARTH_SLICE_Y", "-1,12").split(",");
		for (String one : System.getenv("HEARTH_SLICE").split(";")) {
			String[] a = one.split(",");
			ArchetypeInfo info = service.archetypes().stream().filter(i -> i.id().getPath().equals(a[0])).findFirst().orElseThrow();
			Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
			float temperature = c == Culture.TUNDRA ? -0.3F : c == Culture.ALPINE || c == Culture.NORTHERN ? 0.3F : 0.8F;
			TerrainProbe probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(a[2].toUpperCase(Locale.ROOT)), 42, temperature);
			long seed = Long.parseLong(a[3]);
			int index = (int) ((seed - 7) / 1000 - 1);
			PlotSpec spec = spec(info, StyleRegistry.styleId(c), seed, index, probe);
			if (a.length == 7 && (a[4].equals("age") || a[4].equals("inv"))) {
				// <archetype>,<culture>,<terrain>,<seed>,age,<facing>,<additions>: an AccretionTest case
				probe = SyntheticTerrain.named(a[2], 42);
				Random r = new Random(seed * 31 + info.id().hashCode());
				int fr = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
				int dp = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
				PlotSpec p0 = plot(info, StyleRegistry.styleId(c), seed, Direction.valueOf(a[5].toUpperCase(Locale.ROOT)), probe, fr, dp,
					Math.max(info.maxHeight(), 8));
				CompoundTag add = new CompoundTag();
				if (a[4].equals("age")) {
					// (inv: an invariants test's case, the same plot without params)
					add.putInt("additions", Integer.parseInt(a[6]));
				}
				spec = new PlotSpec(p0.archetype(), p0.style(), p0.seed(), p0.origin(), p0.sizeX(), p0.sizeZ(), p0.facing(), p0.maxHeight(), 2, add);
			} else if (a.length >= 7) {
				// <archetype>,<culture>,<terrain>,<seed>,<frontage>,<depth>,<height>[,<facing>]: a station test's plot, south
				// facing unless named, on the named terrain (seed 42)
				probe = SyntheticTerrain.named(a[2], 42);
				spec = plot(info, StyleRegistry.styleId(c), seed, a.length > 7 ? Direction.valueOf(a[7].toUpperCase(Locale.ROOT)) : Direction.SOUTH, probe,
					Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6]));
				if (a.length > 8) {
					// ,k=v:k=v: params (station=peasant)
					CompoundTag ps = new CompoundTag();
					for (String kv : a[8].split(":")) {
						String[] p = kv.split("=", 2);
						ps.putString(p[0], p[1]);
					}
					spec = new PlotSpec(spec.archetype(), spec.style(), spec.seed(), spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing(), spec.maxHeight(),
						spec.tier(), ps);
				}
			}
			BlueprintImpl bp = service.compiled(spec, probe);
			for (String pr : dev.voidmc.hearth.build.check.BlueprintChecks.all(bp, probe)) {
				System.out.println("CHECK " + pr);
			}
			Map<BlockPos, BlockState> last = new HashMap<>();
			for (int st = 0; st < bp.stageCount(); st++) {
				for (int i = 0; i < bp.stepCount(st); i++) {
					last.put(bp.pos(st, i), bp.state(st, i));
				}
			}
			LocalFrame f = new LocalFrame(spec);
			int floor = spec.floorY();
			Map<String, Character> legend = new LinkedHashMap<>();
			StringBuilder out = new StringBuilder("\nSLICE " + one + " " + spec + "\n");
			for (int ly = Integer.parseInt(ys[0]); ly <= Integer.parseInt(ys[1]); ly++) {
				out.append("y=").append(ly).append('\n');
				for (int lz = f.depth(); lz >= -2; lz--) {
					out.append(String.format(Locale.ROOT, "%3d ", lz));
					for (int lx = -1; lx <= f.frontage(); lx++) {
						BlockState s = last.get(new BlockPos(f.worldX(lx, lz), floor + ly, f.worldZ(lx, lz)));
						if (s == null) {
							out.append(' ');
						} else if (s.isAir()) {
							out.append('.');
						} else {
							String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
							String k = id + (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
								? "[" + s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
								+ "]" : "");
							Character ch = legend.computeIfAbsent(k, x -> SYMBOLS.charAt(Math.min(legend.size(), SYMBOLS.length() - 1)));
							out.append(ch);
						}
					}
					out.append('\n');
				}
			}
			legend.forEach((k, v) -> out.append(v).append('=').append(k).append("  "));
			System.out.println(out);
		}
	}

	private static PlotSpec plot(ArchetypeInfo info, net.minecraft.resources.Identifier style, long seed, Direction facing, TerrainProbe probe, int frontage,
		int depth, int height) {
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		int fx = switch (facing) {
			case EAST -> ox + sizeX;
			case WEST -> ox - 1;
			default -> ox + sizeX / 2;
		};
		int fz = switch (facing) {
			case SOUTH -> oz + sizeZ;
			case NORTH -> oz - 1;
			default -> oz + sizeZ / 2;
		};
		int floor = probe.groundY(fx, fz);
		if (probe.waterY(fx, fz) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(fx, fz) + 1);
		}
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, height, 2, new CompoundTag());
	}

	private static PlotSpec spec(ArchetypeInfo info, net.minecraft.resources.Identifier style, long seed, int index, TerrainProbe probe) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		Direction facing = new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}[index % 4];
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		int fx = switch (facing) {
			case SOUTH, NORTH -> ox + sizeX / 2;
			case EAST -> ox + sizeX;
			default -> ox - 1;
		};
		int fz = switch (facing) {
			case SOUTH -> oz + sizeZ;
			case NORTH -> oz - 1;
			default -> oz + sizeZ / 2;
		};
		int floor = probe.groundY(fx, fz);
		if (probe.waterY(fx, fz) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(fx, fz) + 1);
		}
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2, new CompoundTag());
	}
}
