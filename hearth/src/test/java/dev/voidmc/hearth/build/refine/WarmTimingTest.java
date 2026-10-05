package dev.voidmc.hearth.build.refine;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.AutoLighter;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.Locale;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What drawing, lighting and compiling cost per archetype once the JIT has warmed up (the server's steady state), not a
 * gate: {@code HEARTH_WARM_TIMING=cathedral ./gradlew test --rerun --tests '*WarmTimingTest*'}. Each blueprint is drawn
 * twice, once to time the draw and the light pass, once with {@code lighting=none} to time the compile alone.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_WARM_TIMING", matches = ".+")
class WarmTimingTest {
	@Test
	void timing() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String only = System.getenv("HEARTH_WARM_TIMING");
		StringBuilder sb = new StringBuilder("\nWARM TIMING (CPU ms per blueprint, after warm-up: draw, light pass, compile without it, whole compile)\n");
		for (ArchetypeInfo info : service.archetypes()) {
			if (!only.equals("1") && !info.id().getPath().contains(only)) {
				continue;
			}
			double[] best = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
			long[] phases0 = AutoLighter.timings();
			long resolveT = 0;
			int resolveN = 0;
			for (int round = 0; round < 6; round++) {
				if (round == 1) {
					phases0 = AutoLighter.timings();
					resolveT = 0;
					resolveN = 0;
				}
				long draw = 0;
				long light = 0;
				long compile = 0;
				long whole = 0;
				int n = 0;
				for (String terrain : new String[]{"flat", "hills"}) {
					for (Culture c : Culture.values()) {
						long seed = 7919L + c.ordinal() * 104729L;
						SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
						PlotSpec spec = spec(info, c, seed, probe, new CompoundTag());
						long t0 = cpu();
						Draft d = service.draw(spec, probe);
						long t1 = cpu();
						AutoLighter.light(d);
						long t2 = cpu();
						CompoundTag none = new CompoundTag();
						none.putString("lighting", "none");
						Draft bare = service.draw(spec(info, c, seed, probe, none), probe);
						long t3 = cpu();
						Compiler.compile(bare);
						long t4 = cpu();
						Draft again = service.draw(spec, probe);
						long r0 = cpu();
						dev.voidmc.hearth.build.blueprint.ShapeResolver.resolve(again, again.site());
						resolveT += cpu() - r0;
						resolveN++;
						Draft full = service.draw(spec, probe);
						long t5 = cpu();
						Compiler.compile(full);
						long t6 = cpu();
						draw += t1 - t0;
						light += t2 - t1;
						compile += t4 - t3;
						whole += t6 - t5;
						n++;
					}
				}
				if (round >= 1) {
					best[0] = Math.min(best[0], draw / 1e6 / n);
					best[1] = Math.min(best[1], light / 1e6 / n);
					best[2] = Math.min(best[2], compile / 1e6 / n);
					best[3] = Math.min(best[3], whole / 1e6 / n);
				}
			}
			sb.append(String.format(Locale.ROOT, "%-26s draw %6.1f  light %6.1f  compile %6.1f  whole %6.1f   (best of 5 rounds)%n", info.id().getPath(), best[0],
				best[1], best[2], best[3]));
			long[] phases1 = AutoLighter.timings();
			// two passes per blueprint: the one timed alone and the one inside the whole compile
			int passes = 5 * 28 * 2;
			sb.append(String.format(Locale.ROOT, "    per pass: shape resolve %.1f, setup %.1f, doors %.1f, rooms %.1f, ground %.1f, last %.1f ms%n", resolveT / 1e6 / resolveN,
				(phases1[0] - phases0[0]) / 1e6 / passes, (phases1[1] - phases0[1]) / 1e6 / passes, (phases1[2] - phases0[2]) / 1e6 / passes,
				(phases1[3] - phases0[3]) / 1e6 / passes, (phases1[4] - phases0[4]) / 1e6 / passes));
		}
		System.out.println(sb);
	}

	private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();

	/** CPU time of this thread (the machine is shared: wall time swings with the other builds). */
	private static long cpu() {
		return THREADS.getCurrentThreadCpuTime();
	}

	private static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, SyntheticTerrain probe, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		int ox = -frontage / 2;
		int oz = -depth / 2;
		int floor = probe.groundY(ox + frontage / 2, oz + depth);
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), frontage, depth, Direction.SOUTH,
			Math.max(info.maxHeight(), 8), 2, params);
	}
}
