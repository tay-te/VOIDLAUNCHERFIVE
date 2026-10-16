package dev.voidmc.hearth.build.access;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Locale;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A debugging probe, not a gate: draws one audit case (as {@code structureAudit} does: its plot, terrain and seed),
 * compiles it, and prints the blocks round a local cell (x across, y above the floor, z back from the front) layer by
 * layer, with each cell's stage. {@code HEARTH_PROBE=<archetype>,<culture>,<terrain>,<seed index>,<x>,<y>,<z>}.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_PROBE", matches = ".+")
class AccessProbeTest {
	@Test
	void probe() {
		for (String one : System.getenv("HEARTH_PROBE").split(";")) {
			String[] a = one.split(",");
			ArchetypeInfo info = AccessKitTest.SERVICE.archetypes().stream().filter(i -> i.id().getPath().equals(a[0])).findFirst().orElseThrow();
			Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
			// a seed index as the audit numbers them, or s<seed> for a literal seed facing south (the age tests' plots)
			int index = a[3].startsWith("s") ? 0 : Integer.parseInt(a[3]);
			long seed = a[3].startsWith("s") ? Long.parseLong(a[3].substring(1)) : 1000L * (index + 1) + 7;
			float temperature = c == Culture.TUNDRA ? -0.3F : c == Culture.ALPINE || c == Culture.NORTHERN ? 0.3F : 0.8F;
			SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(a[2].toUpperCase(Locale.ROOT)), 42, temperature);
			PlotSpec spec = spec(info, StyleRegistry.styleId(c), seed, index, probe);
			Draft d = AccessKitTest.SERVICE.draw(spec, probe);
			System.setProperty("hearth.access.debug", "true");
			var bp = Compiler.compile(d);
			System.clearProperty("hearth.access.debug");
			for (var kind : dev.voidmc.hearth.build.age.Dressings.Kind.values()) {
				if (dev.voidmc.hearth.build.age.Dressings.applies(spec, kind)) {
					System.out.println("dressing " + kind + ": " + dev.voidmc.hearth.build.age.Dressings.steps(spec, bp, kind, 0, probe).size() + " steps");
				}
			}
			int x = Integer.parseInt(a[4]);
			int y = Integer.parseInt(a[5]);
			int z = Integer.parseInt(a[6]);
			StringBuilder sb = new StringBuilder("PROBE " + one + " plot " + d.frontage() + "x" + d.depth() + System.lineSeparator());
			for (int yy = y + 2; yy >= y - 2; yy--) {
				sb.append("y").append(yy).append(System.lineSeparator());
				for (int zz = z + 2; zz >= z - 2; zz--) {
					for (int xx = x - 2; xx <= x + 2; xx++) {
						Draft.Cell cell = d.cell(xx, yy, zz);
						String n = cell == null ? "-" : name(cell.state()) + "/" + cell.stage().name().substring(0, 3);
						sb.append(String.format(Locale.ROOT, "%-30.30s", (xx == x && zz == z ? "*" : "") + n));
					}
					sb.append("  z").append(zz).append(System.lineSeparator());
				}
			}
			System.out.println(sb);
		}
	}

	private static String name(BlockState s) {
		String n = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
			n += ":" + s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getSerializedName().charAt(0);
		}
		if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
			&& s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
			n += ":o";
		}
		return n;
	}

	/** The audit's plot for a case (BlueprintPreview.spec). */
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
		int fx;
		int fz;
		switch (facing) {
			case SOUTH -> {
				fx = ox + sizeX / 2;
				fz = oz + sizeZ;
			}
			case NORTH -> {
				fx = ox + sizeX / 2;
				fz = oz - 1;
			}
			case EAST -> {
				fx = ox + sizeX;
				fz = oz + sizeZ / 2;
			}
			default -> {
				fx = ox - 1;
				fz = oz + sizeZ / 2;
			}
		}
		int floor = probe.groundY(fx, fz);
		if (probe.waterY(fx, fz) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(fx, fz) + 1);
		}
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2, new CompoundTag());
	}
}
