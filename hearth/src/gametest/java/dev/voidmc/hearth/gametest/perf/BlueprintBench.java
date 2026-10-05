package dev.voidmc.hearth.gametest.perf;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * What blueprints cost to make, archetype by archetype and family by family, without the game: for every catalogue
 * archetype in every culture that builds it, a spec of a random size and one of its largest size, drawn (with age) and
 * compiled on one thread, the compile split into the light pass, shape resolution and the campaign split. Prints a table
 * per family (median, p90, max of draw and compile, steps) and the slowest specs.
 *
 * <pre>
 *   ./gradlew blueprintBench [-Parchetype=all|house/cottage,...] [-Pstyle=all|temperate,...] [-Pterrain=flat|slope|...]
 * </pre>
 */
public final class BlueprintBench {
	private BlueprintBench() {
	}

	private record Sample(String id, String style, int size, int steps, double drawMs, double compileMs, double lightMs, double shapesMs, double campaignMs) {
		String family() {
			int slash = this.id.indexOf('/');
			return slash < 0 ? this.id : this.id.substring(0, slash);
		}
	}

	public static void main(String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetypeArg = args.length > 0 ? args[0] : "all";
		String styleArg = args.length > 1 ? args[1] : "all";
		String terrainArg = args.length > 2 ? args[2] : "slope";
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin(), 8);
		SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrainArg.toUpperCase(Locale.ROOT)), 42, 0.8F);

		List<Archetype> archetypes = new ArrayList<>();
		for (Archetype a : registry.all()) {
			String path = a.info().id().getPath();
			if (path.startsWith("debug/")) {
				continue;
			}
			if (archetypeArg.equals("all") || Arrays.stream(archetypeArg.split(",")).anyMatch(x -> path.equals(x) || path.startsWith(x + "/"))) {
				archetypes.add(a);
			}
		}
		// warm up: class loading and the JIT, so the first family is not charged for them
		warm(service, probe, archetypes);
		List<Sample> samples = new ArrayList<>();
		long t0 = System.nanoTime();
		for (Archetype a : archetypes) {
			ArchetypeInfo info = a.info();
			for (Culture c : Culture.values()) {
				if (!info.suits(c) || !(styleArg.equals("all") || Arrays.asList(styleArg.split(",")).contains(c.getSerializedName()))) {
					continue;
				}
				Style style = service.styles().get(StyleRegistry.styleId(c));
				if (style == null) {
					continue;
				}
				for (int k = 0; k < 2; k++) {
					CompoundTag extra = new CompoundTag();
					if (k == 1) {
						extra.putInt("frontage", info.maxFrontage());
						extra.putInt("depth", info.maxDepth());
					}
					extra.putInt("tier", 3);
					PlotSpec spec = BlueprintPreview.spec(info, style, 1000L * (k + 1) + 7, k, probe, extra);
					try {
						samples.add(measure(service, spec, probe));
					} catch (RuntimeException e) {
						System.out.println("FAIL " + info.id() + " " + style.id() + ": " + e);
					}
				}
			}
		}
		double seconds = (System.nanoTime() - t0) / 1e9;
		report(samples, seconds);
	}

	private static void warm(BlueprintServiceImpl service, SyntheticTerrain probe, List<Archetype> archetypes) {
		Style style = service.styles().get(StyleRegistry.styleId(Culture.TEMPERATE));
		int n = 0;
		for (Archetype a : archetypes) {
			if (n++ >= 30) {
				break;
			}
			try {
				Compiler.compile(service.draw(BlueprintPreview.spec(a.info(), style, 1, 0, probe, new CompoundTag()), probe));
			} catch (RuntimeException e) {
				// reported in the measured run
			}
		}
	}

	private static Sample measure(BlueprintServiceImpl service, PlotSpec spec, SyntheticTerrain probe) {
		long light = Compiler.Timings.LIGHT.sum();
		long shapes = Compiler.Timings.SHAPES.sum();
		long camp = Compiler.Timings.CAMPAIGNS.sum();
		long t0 = System.nanoTime();
		Draft d = service.draw(spec, probe);
		long t1 = System.nanoTime();
		BlueprintImpl bp = Compiler.compile(d);
		long t2 = System.nanoTime();
		return new Sample(spec.archetype().getPath(), spec.style().getPath(), spec.sizeX() * spec.sizeZ(), bp.totalSteps(), (t1 - t0) / 1e6, (t2 - t1) / 1e6,
			(Compiler.Timings.LIGHT.sum() - light) / 1e6, (Compiler.Timings.SHAPES.sum() - shapes) / 1e6, (Compiler.Timings.CAMPAIGNS.sum() - camp) / 1e6);
	}

	private static double pct(List<Double> sorted, double p) {
		if (sorted.isEmpty()) {
			return 0;
		}
		return sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(p * (sorted.size() - 1) + 0.5)));
	}

	private static List<Double> sorted(List<Sample> s, java.util.function.ToDoubleFunction<Sample> f) {
		List<Double> out = new ArrayList<>();
		for (Sample x : s) {
			out.add(f.applyAsDouble(x));
		}
		out.sort(null);
		return out;
	}

	private static void report(List<Sample> samples, double seconds) {
		Map<String, List<Sample>> byFamily = new TreeMap<>();
		for (Sample s : samples) {
			byFamily.computeIfAbsent(s.family(), k -> new ArrayList<>()).add(s);
		}
		System.out.printf(Locale.ROOT, "[bench] %d blueprints in %.1f s (one thread)%n", samples.size(), seconds);
		System.out.printf(Locale.ROOT, "[bench] %-12s %5s | %-21s | %-21s | %-21s | %8s %8s %8s%n", "family", "n", "draw ms med/p90/max", "compile ms med/p90/max",
			"light ms med/p90/max", "shapes", "campgn", "steps90");
		for (var e : byFamily.entrySet()) {
			row(e.getKey(), e.getValue());
		}
		row("ALL", samples);
		List<Sample> slow = new ArrayList<>(samples);
		slow.sort(Comparator.comparingDouble((Sample s) -> -(s.drawMs + s.compileMs)));
		System.out.println("[bench] slowest:");
		for (int i = 0; i < Math.min(25, slow.size()); i++) {
			Sample s = slow.get(i);
			System.out.printf(Locale.ROOT, "[bench]   %-34s %-12s %5d cols %7d steps: draw %7.1f, compile %7.1f (light %7.1f, shapes %6.1f, campaigns %6.1f) ms%n", s.id,
				s.style, s.size, s.steps, s.drawMs, s.compileMs, s.lightMs, s.shapesMs, s.campaignMs);
		}
	}

	private static void row(String name, List<Sample> s) {
		List<Double> draw = sorted(s, Sample::drawMs);
		List<Double> comp = sorted(s, Sample::compileMs);
		List<Double> light = sorted(s, Sample::lightMs);
		List<Double> shapes = sorted(s, Sample::shapesMs);
		List<Double> camp = sorted(s, Sample::campaignMs);
		List<Double> steps = sorted(s, x -> x.steps);
		System.out.printf(Locale.ROOT, "[bench] %-12s %5d | %6.1f %6.1f %7.1f | %6.1f %6.1f %7.1f | %6.1f %6.1f %7.1f | %8.1f %8.1f %8.0f%n", name, s.size(),
			pct(draw, 0.5), pct(draw, 0.9), pct(draw, 1), pct(comp, 0.5), pct(comp, 0.9), pct(comp, 1), pct(light, 0.5), pct(light, 0.9), pct(light, 1),
			pct(shapes, 0.9), pct(camp, 0.9), pct(steps, 0.9));
	}
}
