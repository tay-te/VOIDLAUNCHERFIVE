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
 * {@code HEARTH_CASE=<archetype>,<culture>,<terrain>,<seed>,<facing>[,<variant>]}, several separated by ';'.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_CASE", matches = ".+")
class AccessCaseProbeTest {
	static {
		BuildTestSupport.init();
	}

	@Test
	void probe() {
		for (String one : System.getenv("HEARTH_CASE").split(";")) {
			String[] a = one.split(",");
			ArchetypeInfo info = BuildTestSupport.SERVICE.archetypes().stream().filter(i -> i.id().getPath().equals(a[0])).findFirst().orElseThrow();
			Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
			SyntheticTerrain probe = SyntheticTerrain.named(a[2], 42);
			CompoundTag params = new CompoundTag();
			if (a.length > 5) {
				params.putInt("variant", Integer.parseInt(a[5]));
			}
			PlotSpec spec = BuildTestSupport.spec(info, StyleRegistry.styleId(c), Long.parseLong(a[3]), Direction.valueOf(a[4].toUpperCase(Locale.ROOT)), probe,
				params);
			for (String skip : new String[]{"", "doors", "stairs", "lofts", "guards", "doors,stairs,lofts,guards"}) {
				System.setProperty("hearth.access.skip", skip);
				var problems = BlueprintChecks.all(dev.voidmc.hearth.build.blueprint.Compiler.compile(BuildTestSupport.SERVICE.draw(spec, probe)), probe);
				System.out.println("CASE " + one + " skip [" + skip + "]: " + problems.size() + " " + problems);
			}
			System.clearProperty("hearth.access.skip");
		}
	}
}
