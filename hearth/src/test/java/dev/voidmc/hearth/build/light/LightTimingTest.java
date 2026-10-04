package dev.voidmc.hearth.build.light;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.AutoLighter;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** What the lighting pass costs per archetype (not a gate). {@code HEARTH_LIGHT_TIMING=1 ./gradlew test --tests '*LightTimingTest*'}. */
@EnabledIfEnvironmentVariable(named = "HEARTH_LIGHT_TIMING", matches = ".+")
class LightTimingTest {
	static {
		LightSupport.init();
	}

	@Test
	void timing() {
		String only = System.getenv("HEARTH_LIGHT_TIMING");
		StringBuilder sb = new StringBuilder("\nLIGHT TIMING (ms per blueprint: draw+compile without the pass, the pass alone)\n");
		double totalBase = 0;
		double totalLight = 0;
		for (ArchetypeInfo info : LightSupport.SERVICE.archetypes()) {
			if (!only.equals("1") && !info.id().getPath().contains(only)) {
				continue;
			}
			long base = 0;
			long light = 0;
			int n = 0;
			for (String terrain : new String[]{"flat", "hills"}) {
				for (Culture c : Culture.values()) {
					long seed = 7919L + c.ordinal() * 104729L;
					SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
					PlotSpec spec = LightSupport.spec(info, StyleRegistry.styleId(c), seed, Direction.SOUTH, probe, new CompoundTag());
					long t0 = System.nanoTime();
					Draft d = LightSupport.SERVICE.draw(spec, probe);
					long t1 = System.nanoTime();
					AutoLighter.light(d);
					long t2 = System.nanoTime();
					CompoundTag none = new CompoundTag();
					none.putString("lighting", "none");
					PlotSpec bare = LightSupport.spec(info, StyleRegistry.styleId(c), seed, Direction.SOUTH, probe, none);
					long t3 = System.nanoTime();
					Compiler.compile(LightSupport.SERVICE.draw(bare, probe));
					long t4 = System.nanoTime();
					base += t4 - t3;
					light += t2 - t1;
					n++;
				}
			}
			totalBase += base / 1e6;
			totalLight += light / 1e6;
			sb.append(String.format(Locale.ROOT, "%-26s base %6.1f  light %6.1f%n", info.id().getPath(), base / 1e6 / n, light / 1e6 / n));
		}
		sb.append(String.format(Locale.ROOT, "TOTAL base %.0f ms, light %.0f ms%n", totalBase, totalLight));
		long[] t = AutoLighter.timings();
		sb.append(String.format(Locale.ROOT, "phases: setup %.0f doors %.0f rooms %.0f ground %.0f last %.0f ms%n", t[0] / 1e6, t[1] / 1e6, t[2] / 1e6, t[3] / 1e6,
			t[4] / 1e6));
		System.out.println(sb);
	}
}
