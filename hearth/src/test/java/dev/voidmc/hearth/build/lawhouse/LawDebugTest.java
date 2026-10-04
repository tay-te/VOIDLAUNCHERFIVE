package dev.voidmc.hearth.build.lawhouse;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * A debugging aid, not a gate: with {@code HEARTH_LAW_DEBUG=archetype,culture,terrain,seed,facing[,variant[,height]]} it
 * draws that spec as the family test does and prints the problems, the points of interest and walk maps at every
 * height from the floor up (run with {@code --rerun}: environment variables are not test inputs).
 */
class LawDebugTest {
	@Test
	void debug() {
		String arg = System.getenv("HEARTH_LAW_DEBUG");
		if (arg == null || arg.isBlank()) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String[] a = arg.split(",");
		ArchetypeInfo info = service.archetype(Hearth.id(a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
		SyntheticTerrain probe = SyntheticTerrain.named(a[2], a.length > 7 ? Long.parseLong(a[7]) : 42);
		long seed = Long.parseLong(a[3]);
		Direction facing = Direction.byName(a[4]);
		CompoundTag params = new CompoundTag();
		if (a.length > 5 && !a[5].equals("-")) {
			params.putInt("variant", Integer.parseInt(a[5]));
		}
		int height = a.length > 6 && !a[6].equals("-") ? Integer.parseInt(a[6]) : Math.max(info.maxHeight(), 8);
		PlotSpec spec = LawHouseArchetypesTest.spec(info, c, seed, facing, probe, height, 2, params);
		BlueprintImpl bp = service.compiled(spec, probe);
		System.out.println("LAWDEBUG spec " + spec);
		for (String p : BlueprintChecks.all(bp, probe)) {
			System.out.println("LAWDEBUG problem " + p);
		}
		for (Poi p : bp.pois()) {
			System.out.println("LAWDEBUG poi " + p.type() + " " + p.pos() + " " + p.facing() + " " + p.job());
		}
		for (int y = spec.floorY() - 1; y <= spec.floorY() + 12; y++) {
			System.out.println("LAWDEBUG " + BlueprintChecks.reachMap(bp, probe, y).replace("\n", "\nLAWDEBUG "));
		}
	}
}
