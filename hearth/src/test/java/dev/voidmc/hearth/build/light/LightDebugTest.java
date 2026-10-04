package dev.voidmc.hearth.build.light;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.AutoLighter;
import dev.voidmc.hearth.build.kit.light.Fixture;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Prints what the lighting pass placed in one spec, and the checks with and without it. HEARTH_LIGHT_DEBUG =
 * archetype,culture,terrain,seed,facing,height[,frontage,depth].
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_LIGHT_DEBUG", matches = ".+")
class LightDebugTest {
	static {
		LightSupport.init();
	}

	@Test
	void debug() {
		String[] a = System.getenv("HEARTH_LIGHT_DEBUG").split(",");
		ArchetypeInfo info = LightSupport.SERVICE.archetype(dev.voidmc.hearth.Hearth.id(a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase());
		SyntheticTerrain probe = SyntheticTerrain.named(a[2], Integer.parseInt(a.length > 8 ? a[8] : "42"));
		long seed = Long.parseLong(a[3]);
		Direction facing = Direction.byName(a[4]);
		int height = Integer.parseInt(a[5]);
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		if (a.length > 7) {
			frontage = Integer.parseInt(a[6]);
			depth = Integer.parseInt(a[7]);
		}
		PlotSpec base = LightSupport.spec(info, StyleRegistry.styleId(c), seed, facing, probe, new CompoundTag(), frontage, depth, 2);
		PlotSpec spec = new PlotSpec(base.archetype(), base.style(), seed, base.origin(), base.sizeX(), base.sizeZ(), facing, height, 2, base.params());
		Draft d = LightSupport.SERVICE.draw(spec, probe);
		StringBuilder sb = new StringBuilder("\nLIGHT DEBUG " + String.join(",", a) + "\n");
		for (Fixture f : AutoLighter.lightLogged(d)) {
			sb.append("  ").append(f.kind());
			for (Fixture.Part p : f.parts()) {
				BlockPos w = d.frame().toWorld(p.x(), p.y(), p.z());
				sb.append(" [").append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(" w").append(w.toShortString()).append(' ')
					.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).getPath()).append(']');
			}
			sb.append('\n');
		}
		BlueprintImpl bp = Compiler.compile(d);
		sb.append("with: ").append(BlueprintChecks.all(bp, probe)).append('\n');
		CompoundTag none = new CompoundTag();
		none.putString("lighting", "none");
		PlotSpec bare = new PlotSpec(base.archetype(), base.style(), seed, base.origin(), base.sizeX(), base.sizeZ(), facing, height, 2, none);
		sb.append("without: ").append(BlueprintChecks.all(Compiler.compile(LightSupport.SERVICE.draw(bare, probe)), probe)).append('\n');
		System.out.println(sb);
	}
}
