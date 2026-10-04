package dev.voidmc.hearth.build.light;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.LightCheck;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A survey of the light rule over the catalogue (not a gate): per archetype, how many blueprints miss each kind of need
 * and by how many cells. Run with {@code HEARTH_LIGHT_SURVEY=1 ./gradlew test --tests '*LightSurveyTest*'}.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_LIGHT_SURVEY", matches = ".+")
class LightSurveyTest {
	static {
		LightSupport.init();
	}

	@Test
	void survey() {
		String only = System.getenv("HEARTH_LIGHT_SURVEY");
		StringBuilder sb = new StringBuilder("\nLIGHT SURVEY (blueprints failing / cells unmet, per kind)\n");
		long t0 = System.currentTimeMillis();
		int total = 0;
		int failing = 0;
		for (ArchetypeInfo info : LightSupport.SERVICE.archetypes()) {
			if (!only.equals("1") && !info.id().getPath().contains(only)) {
				continue;
			}
			Map<LightRules.Kind, int[]> by = new EnumMap<>(LightRules.Kind.class);
			int n = 0;
			int bad = 0;
			int[] kinds = new int[dev.voidmc.hearth.build.kit.light.Fixture.Kind.values().length];
			String example = null;
			for (String terrain : LightSupport.TERRAINS) {
				for (Culture c : Culture.values()) {
					for (int s = 0; s < 2; s++) {
						long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
						Direction facing = LightSupport.FACINGS[(s + c.ordinal()) % 4];
						SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
						PlotSpec spec = LightSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe, new CompoundTag());
						dev.voidmc.hearth.build.draft.Draft draft = LightSupport.SERVICE.draw(spec, probe);
						int[] placed = dev.voidmc.hearth.build.kit.light.AutoLighter.light(draft);
						for (int k = 0; k < placed.length; k++) {
							kinds[k] += placed[k];
						}
						BlueprintImpl bp = dev.voidmc.hearth.build.blueprint.Compiler.compile(draft);
						LightCheck.Result r = LightCheck.evaluate(bp, probe);
						n++;
						if (!r.unmet().isEmpty()) {
							bad++;
							EnumMap<LightRules.Kind, Integer> here = new EnumMap<>(LightRules.Kind.class);
							for (LightRules.Need u : r.unmet()) {
								here.merge(u.kind(), 1, Integer::sum);
							}
							for (var e : here.entrySet()) {
								int[] a = by.computeIfAbsent(e.getKey(), k -> new int[2]);
								a[0]++;
								a[1] += e.getValue();
							}
							if (example == null) {
								example = c.getSerializedName() + " " + terrain + " s" + s + " " + here;
							}
						}
					}
				}
			}
			total += n;
			failing += bad;
			sb.append(String.format(Locale.ROOT, "%-26s %3d/%3d", info.id().getPath(), bad, n));
			for (LightRules.Kind k : LightRules.Kind.values()) {
				int[] a = by.get(k);
				sb.append(String.format(Locale.ROOT, "  %s %3d/%5d", k.label(), a == null ? 0 : a[0], a == null ? 0 : a[1]));
			}
			sb.append("   fixtures/bp:");
			for (var k : dev.voidmc.hearth.build.kit.light.Fixture.Kind.values()) {
				if (kinds[k.ordinal()] > 0) {
					sb.append(String.format(Locale.ROOT, " %s %.1f", k.name().toLowerCase(Locale.ROOT), kinds[k.ordinal()] / (double) n));
				}
			}
			if (example != null) {
				sb.append("   e.g. ").append(example);
			}
			sb.append('\n');
		}
		sb.append(String.format(Locale.ROOT, "TOTAL %d/%d failing, %.1f s%n", failing, total, (System.currentTimeMillis() - t0) / 1000.0));
		System.out.println(sb);
	}
}
