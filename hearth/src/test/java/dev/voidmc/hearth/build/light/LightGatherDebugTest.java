package dev.voidmc.hearth.build.light;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.AutoLighter;
import dev.voidmc.hearth.build.kit.light.Fixture;
import dev.voidmc.hearth.build.style.StyleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The landscape test's gathering specs, with what the lighting pass placed. HEARTH_LIGHT_GATHER = id,culture,terrain. */
@EnabledIfEnvironmentVariable(named = "HEARTH_LIGHT_GATHER", matches = ".+")
class LightGatherDebugTest {
	static {
		LightSupport.init();
	}

	@Test
	void debug() {
		String[] a = System.getenv("HEARTH_LIGHT_GATHER").split(",");
		var info = LightSupport.SERVICE.archetype(Hearth.id(a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase());
		SyntheticTerrain t = SyntheticTerrain.named(a[2], 11);
		int f = (info.minFrontage() + info.maxFrontage()) / 2 | 1;
		int dpt = (info.minDepth() + info.maxDepth()) / 2 | 1;
		int floor = t.groundY(0, -dpt / 2 - 1);
		PlotSpec spec = new PlotSpec(info.id(), StyleRegistry.styleId(c), 1234L + c.ordinal() * 7L, new BlockPos(-f / 2, floor, -dpt / 2), f, dpt,
			Direction.NORTH, Math.max(info.maxHeight(), 8), 3, new CompoundTag());
		Draft d = LightSupport.SERVICE.draw(spec, t);
		StringBuilder sb = new StringBuilder("\nLIGHT GATHER " + String.join(",", a) + "\n");
		for (Fixture fx : AutoLighter.lightLogged(d)) {
			sb.append("  ").append(fx.kind());
			for (Fixture.Part p : fx.parts()) {
				BlockPos w = d.frame().toWorld(p.x(), p.y(), p.z());
				sb.append(" [").append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(" w").append(w.toShortString()).append(' ')
					.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).getPath()).append(']');
			}
			sb.append('\n');
		}
		var bp = Compiler.compile(d);
		for (var poi : bp.pois()) {
			sb.append("  poi ").append(poi.type()).append(' ').append(poi.pos().toShortString()).append('\n');
		}
		sb.append("with: ").append(BlueprintChecks.all(bp, t)).append('\n');
		System.out.println(sb);
	}
}
