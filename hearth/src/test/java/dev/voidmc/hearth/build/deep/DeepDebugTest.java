package dev.voidmc.hearth.build.deep;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.draft.Draft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Scratch debugging (run by hand with -Ddeep.debug). */
class DeepDebugTest {
	@Test
	void debug() {
		if (System.getenv("DEEP_DEBUG") == null) {
			return;
		}
		BlueprintServiceImpl service = BuildModule.standalone();
		var info = service.archetype(Hearth.id("deep/dwelling"));
		SyntheticTerrain probe = SyntheticTerrain.named("river", 42);
		PlotSpec spec = DeepArchetypesTest.surfaceSpec(info, Culture.TEMPERATE, 7919L, Direction.SOUTH, probe, new CompoundTag());
		Draft d = service.draw(spec, probe);
		StringBuilder sb = new StringBuilder("floor " + spec.floorY() + "\n");
		for (int z = -1; z <= d.depth(); z++) {
			for (int x = -1; x <= d.frontage(); x++) {
				sb.append(String.format("%3d/%-3s", d.site().ground(x, z), d.site().isWater(x, z) ? String.valueOf(d.site().water(x, z)) : "-"));
			}
			sb.append('\n');
		}
		System.out.println(sb);
	}
}
