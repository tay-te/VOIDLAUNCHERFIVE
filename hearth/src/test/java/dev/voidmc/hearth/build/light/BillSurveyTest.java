package dev.voidmc.hearth.build.light;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The largest stage bills per archetype (not a gate). {@code HEARTH_BILL_SURVEY=1}. */
@EnabledIfEnvironmentVariable(named = "HEARTH_BILL_SURVEY", matches = ".+")
class BillSurveyTest {
	static {
		LightSupport.init();
	}

	@Test
	void survey() {
		StringBuilder sb = new StringBuilder("\nBILL SURVEY (largest stage: units, its stage, its top goods; whole building)\n");
		int worst = 0;
		for (ArchetypeInfo info : LightSupport.SERVICE.archetypes()) {
			int best = 0;
			String where = "";
			int[] whole = new int[Goods.VALUES.length];
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.KARST, Culture.NORTHERN, Culture.MEDITERRANEAN}) {
				SyntheticTerrain probe = SyntheticTerrain.named("hills", 42);
				CompoundTag p = new CompoundTag();
				PlotSpec spec = LightSupport.spec(info, StyleRegistry.styleId(c), 99L + c.ordinal(), Direction.SOUTH, probe, p,
					info.maxFrontage(), info.maxDepth(), 4);
				BlueprintImpl bp = LightSupport.SERVICE.compiled(spec, probe);
				for (int s = 0; s < bp.stageCount(); s++) {
					int[] g = bp.goods(s);
					int sum = 0;
					for (int v : g) {
						sum += v;
					}
					if (sum > best) {
						best = sum;
						StringBuilder top = new StringBuilder();
						java.util.List<Goods> order = new java.util.ArrayList<>(java.util.List.of(Goods.VALUES));
						order.sort((a, b) -> Integer.compare(g[b.ordinal()], g[a.ordinal()]));
						for (int k = 0; k < 5 && g[order.get(k).ordinal()] > 0; k++) {
							top.append(order.get(k).getSerializedName()).append(' ').append(g[order.get(k).ordinal()]).append(' ');
						}
						where = c.getSerializedName() + " " + bp.stageKind(s) + ": " + top;
					}
					if (c == Culture.TEMPERATE) {
						for (int k = 0; k < g.length; k++) {
							whole[k] += g[k];
						}
					}
				}
			}
			worst = Math.max(worst, best);
			StringBuilder all = new StringBuilder();
			for (Goods g : Goods.VALUES) {
				if (whole[g.ordinal()] > 0) {
					all.append(g.getSerializedName()).append(' ').append(whole[g.ordinal()]).append(' ');
				}
			}
			sb.append(String.format(Locale.ROOT, "%-26s %6d  %s  | temperate whole: %s%n", info.id().getPath(), best, where, all));
		}
		sb.append("WORST stage ").append(worst).append('\n');
		System.out.println(sb);
	}
}
