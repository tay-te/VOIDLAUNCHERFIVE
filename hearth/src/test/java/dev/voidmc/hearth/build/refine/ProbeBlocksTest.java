package dev.voidmc.hearth.build.refine;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Debugging (not a gate): the blocks one spec's draft uses, by count, and where a block matching a filter stands.
 * {@code HEARTH_PROBE=archetype,culture,seed,frontage,depth[,k=v;k=v] HEARTH_PROBE_FIND=hay ./gradlew test --rerun --tests '*ProbeBlocksTest*'}.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_PROBE", matches = ".+")
class ProbeBlocksTest {
	@Test
	void probe() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String[] a = System.getenv("HEARTH_PROBE").split(",");
		CompoundTag params = new CompoundTag();
		if (a.length > 5) {
			for (String kv : a[5].split(";")) {
				String[] p = kv.split("=");
				try {
					params.putInt(p[0], Integer.parseInt(p[1]));
				} catch (NumberFormatException e) {
					params.putString(p[0], p[1]);
				}
			}
		}
		int w = Integer.parseInt(a[3]);
		int dp = Integer.parseInt(a[4]);
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 42);
		PlotSpec spec = new PlotSpec(Hearth.id(a[0]), Hearth.id(a[1]), Long.parseLong(a[2]), new BlockPos(-w / 2, probe.groundY(0, dp), -dp / 2), w, dp,
			Direction.SOUTH, 40, params.getIntOr("tier", 2), params);
		Draft d = service.draw(spec, probe);
		String find = System.getenv("HEARTH_PROBE_FIND");
		TreeMap<String, Integer> counts = new TreeMap<>();
		StringBuilder where = new StringBuilder();
		d.forEach((x, y, z, c) -> {
			String id = BuiltInRegistries.BLOCK.getKey(c.state().getBlock()).getPath();
			counts.merge(id, 1, Integer::sum);
			if (find != null && id.contains(find) && where.length() < 2000) {
				where.append(" ").append(x).append(',').append(y).append(',').append(z).append('[').append(c.stage()).append(']');
			}
		});
		System.out.println("\nPROBE " + spec.archetype() + " " + d.dressing() + "\n" + counts + "\nAT" + where);
	}
}
