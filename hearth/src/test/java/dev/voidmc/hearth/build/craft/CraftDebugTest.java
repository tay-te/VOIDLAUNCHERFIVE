package dev.voidmc.hearth.build.craft;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * One craft spec in detail, for chasing a check failure (not a gate): set
 * {@code HEARTH_CRAFT_DEBUG=archetype,culture,terrain,terrainSeed,seed,facing,height,frontage,depth} and run
 * {@code ./gradlew test --rerun --tests '*CraftDebugTest*'}. Prints the checks, walk maps at the floor and above, and
 * the drafted plan at each requested level ({@code HEARTH_CRAFT_LEVELS=0,1,4}).
 */
class CraftDebugTest {
	@Test
	void debugOneSpec() {
		String env = System.getenv("HEARTH_CRAFT_DEBUG");
		if (env == null || env.isBlank()) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String[] a = env.split(",");
		ArchetypeInfo info = service.archetype(Hearth.id(a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
		SyntheticTerrain probe = SyntheticTerrain.named(a[2], Integer.parseInt(a[3]));
		long seed = Long.parseLong(a[4]);
		Direction facing = Direction.byName(a[5]);
		int height = Integer.parseInt(a[6]);
		int frontage = Integer.parseInt(a[7]);
		int depth = Integer.parseInt(a[8]);
		PlotSpec spec = CraftArchetypesTest.spec(info, c, seed, facing, probe, height, new CompoundTag(), frontage, depth);
		Draft d = service.draw(spec, probe);
		BlueprintImpl bp = dev.voidmc.hearth.build.blueprint.Compiler.compile(d);
		System.out.println("CHECKS " + BlueprintChecks.all(bp, probe));
		String levels = System.getenv().getOrDefault("HEARTH_CRAFT_LEVELS", "0");
		for (String l : levels.split(",")) {
			int y = Integer.parseInt(l.trim());
			System.out.println(BlueprintChecks.reachMap(bp, probe, spec.floorY() + y));
			StringBuilder sb = new StringBuilder("draft plan y=" + y + " (z down from the back, x across)\n");
			for (int z = depth - 1; z >= -1; z--) {
				sb.append(String.format("%3d ", z));
				for (int x = 0; x < frontage; x++) {
					var st = d.get(x, y, z);
					sb.append(glyph(st));
				}
				sb.append('\n');
			}
			System.out.println(sb);
		}
		String cols = System.getenv("HEARTH_CRAFT_COLUMNS");
		if (cols != null) {
			for (String cz : cols.split(";")) {
				String[] xz = cz.split(":");
				int x = Integer.parseInt(xz[0]);
				int z = Integer.parseInt(xz[1]);
				for (int y = 8; y >= -2; y--) {
					var cell = d.cell(x, y, z);
					System.out.println("column " + x + "," + z + " y" + y + ": " + (cell == null ? "-" : cell.stage() + " " + cell.state()));
				}
			}
		}
		for (var p : bp.pois()) {
			System.out.println("POI " + p.type() + " " + p.job() + " at " + p.pos());
		}
		for (var e : bp.entrances()) {
			System.out.println("ENTRANCE " + e);
		}
	}

	private static char glyph(net.minecraft.world.level.block.state.BlockState s) {
		if (s == null) {
			return ' ';
		}
		if (s.isAir()) {
			return '.';
		}
		String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		if (p.contains("trapdoor")) {
			return 't';
		}
		if (p.contains("door")) {
			return 'D';
		}
		if (p.contains("glass") || p.contains("pane")) {
			return '|';
		}
		if (p.contains("shelf")) {
			return 'h';
		}
		if (p.contains("stairs")) {
			return 'S';
		}
		if (p.contains("slab")) {
			return '_';
		}
		if (p.contains("fence")) {
			return 'f';
		}
		if (p.contains("log") || p.contains("wood")) {
			return '#';
		}
		if (p.contains("planks")) {
			return '=';
		}
		if (p.contains("trapdoor")) {
			return 't';
		}
		if (p.contains("water")) {
			return '~';
		}
		if (p.contains("chest") || p.contains("barrel")) {
			return 'C';
		}
		return '@';
	}
}
