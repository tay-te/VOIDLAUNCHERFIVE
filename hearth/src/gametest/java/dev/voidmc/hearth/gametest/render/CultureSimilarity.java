package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * The similarity check across cultures (ART_REVIEW.md item 30): one plan reused in many cultures. For every archetype,
 * each culture that builds it draws the same spec (same seed, size and facing, flat land); the blueprints' shapes are
 * compared, block for block and material aside (the Jaccard index of the cells each one fills), and cultures whose
 * shapes are at least {@code threshold} alike are grouped. An archetype is flagged when, on every seed, one group holds
 * at least half of the cultures (and four or more): its plan does not change with the culture, only its palette does,
 * and a contact sheet shows it as copies. Seeds of one culture are compared too, as the yardstick of how much a plan
 * moves at all.
 *
 * <pre>
 *   ./gradlew cultureSimilarity -Parchetype=all -Pseeds=2 -Pthreshold=0.8
 *   args: archetypes(all | id, path or name list)  seeds  threshold  outFile
 * </pre>
 * {@code townPreview} runs it over the archetypes its towns built. Output: a table on the console and in
 * build/previews/town/similarity.txt, flagged archetypes first.
 */
public final class CultureSimilarity {
	/** One archetype's result: the mean likeness across cultures, its largest group of look-alikes, and the seed yardstick. */
	public record Row(Identifier id, int cultures, double meanAcross, double meanSeeds, List<Culture> group, boolean flagged) {
	}

	private CultureSimilarity() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String which = args.length > 0 ? args[0] : "all";
		int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 2;
		double threshold = args.length > 2 ? Double.parseDouble(args[2]) : 0.8;
		Path out = Path.of(args.length > 3 ? args[3] : "build/previews/town/similarity.txt");
		BlueprintServiceImpl service = BuildModule.standalone();
		List<Identifier> ids = new ArrayList<>();
		for (ArchetypeInfo info : service.archetypes()) {
			if (which.equals("all") ? !info.id().getPath().startsWith("debug/") : matches(which, info.id())) {
				ids.add(info.id());
			}
		}
		ids.sort(java.util.Comparator.comparing(Identifier::toString));
		write(run(service, ids, seeds, threshold), threshold, out);
	}

	private static boolean matches(String arg, Identifier id) {
		for (String a : arg.split(",")) {
			if (a.equals(id.toString()) || a.equals(id.getPath()) || id.getPath().endsWith("/" + a)) {
				return true;
			}
		}
		return false;
	}

	/** Compares every archetype across the cultures that build it. Roads, fields and the like (no plan to speak of) are skipped. */
	public static List<Row> run(BlueprintServiceImpl service, Collection<Identifier> ids, int seeds, double threshold) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
		try {
			List<Future<Row>> rows = new ArrayList<>();
			for (Identifier id : ids) {
				ArchetypeInfo info = service.archetype(id);
				if (info == null || info.category() == ArchetypeCategory.INFRASTRUCTURE || info.category() == ArchetypeCategory.FARM) {
					continue;
				}
				rows.add(pool.submit(() -> compare(service, info, seeds, threshold)));
			}
			List<Row> out = new ArrayList<>();
			for (Future<Row> f : rows) {
				Row r = f.get();
				if (r != null) {
					out.add(r);
				}
			}
			out.sort(java.util.Comparator.comparing((Row r) -> !r.flagged()).thenComparing(r -> -r.meanAcross()));
			return out;
		} finally {
			pool.shutdown();
		}
	}

	private static Row compare(BlueprintServiceImpl service, ArchetypeInfo info, int seeds, double threshold) {
		List<Culture> cultures = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			Style s = service.styles().get(StyleRegistry.styleId(c));
			if (s != null && info.suits(c)) {
				cultures.add(c);
				styles.add(s);
			}
		}
		int n = cultures.size();
		if (n < 3) {
			return null;
		}
		double across = 0;
		int acrossN = 0;
		double seedSum = 0;
		int seedN = 0;
		int[] best = null;
		boolean flagged = true;
		LongOpenHashSet[] first = new LongOpenHashSet[n];
		for (int sd = 0; sd < seeds; sd++) {
			long seed = 1000L * (sd + 1) + 7;
			LongOpenHashSet[] shape = new LongOpenHashSet[n];
			for (int i = 0; i < n; i++) {
				shape[i] = shape(service, info, styles.get(i), seed);
			}
			// union of look-alikes
			int[] parent = new int[n];
			for (int i = 0; i < n; i++) {
				parent[i] = i;
			}
			for (int i = 0; i < n; i++) {
				for (int j = i + 1; j < n; j++) {
					double jac = jaccard(shape[i], shape[j]);
					across += jac;
					acrossN++;
					if (jac >= threshold) {
						parent[find(parent, i)] = find(parent, j);
					}
				}
				if (sd == 0) {
					first[i] = shape[i];
				} else {
					seedSum += jaccard(first[i], shape[i]);
					seedN++;
				}
			}
			int[] size = new int[n];
			int root = 0;
			for (int i = 0; i < n; i++) {
				int r = find(parent, i);
				if (++size[r] > size[root]) {
					root = r;
				}
			}
			int big = size[root];
			flagged &= big >= Math.max(4, (n + 1) / 2);
			if (best == null || big > best.length) {
				best = new int[big];
				int k = 0;
				for (int i = 0; i < n; i++) {
					if (find(parent, i) == root) {
						best[k++] = i;
					}
				}
			}
		}
		List<Culture> group = new ArrayList<>();
		for (int i : best) {
			group.add(cultures.get(i));
		}
		return new Row(info.id(), n, across / Math.max(1, acrossN), seedN == 0 ? Double.NaN : seedSum / seedN, group, flagged);
	}

	/** The cells a blueprint fills, in the plot's frame (air and fluids aside). */
	private static LongOpenHashSet shape(BlueprintServiceImpl service, ArchetypeInfo info, Style style, long seed) {
		SyntheticTerrain flat = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 42, 0.8F);
		CompoundTag extra = new CompoundTag();
		extra.putString("facing", Direction.SOUTH.getSerializedName());
		PlotSpec spec = BlueprintPreview.spec(info, style, seed, 0, flat, extra);
		LongOpenHashSet cells = new LongOpenHashSet();
		Blueprint bp;
		try {
			bp = service.blueprint(spec, flat);
		} catch (RuntimeException e) {
			return cells;
		}
		int ox = spec.origin().getX();
		int oy = spec.floorY();
		int oz = spec.origin().getZ();
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> {
					long k = Voxels.key(x - ox, y - oy, z - oz);
					if (state.isAir() || !state.getFluidState().isEmpty()) {
						cells.remove(k);
					} else {
						cells.add(k);
					}
				});
			}
		}
		return cells;
	}

	private static double jaccard(LongOpenHashSet a, LongOpenHashSet b) {
		if (a.isEmpty() && b.isEmpty()) {
			return 1;
		}
		LongOpenHashSet small = a.size() <= b.size() ? a : b;
		LongOpenHashSet large = small == a ? b : a;
		int both = 0;
		var it = small.iterator();
		while (it.hasNext()) {
			if (large.contains(it.nextLong())) {
				both++;
			}
		}
		return both / (double) (a.size() + b.size() - both);
	}

	private static int find(int[] p, int i) {
		while (p[i] != i) {
			p[i] = p[p[i]];
			i = p[i];
		}
		return i;
	}

	/** The table, flagged first, to the console and a file. */
	public static void write(List<Row> rows, double threshold, Path file) throws IOException {
		List<String> lines = new ArrayList<>();
		long flagged = rows.stream().filter(Row::flagged).count();
		lines.add(String.format(Locale.ROOT, "culture similarity: %d archetypes, %d flagged (one look-alike group holds half the cultures or more at "
			+ "likeness >= %.2f on every seed)", rows.size(), flagged, threshold));
		lines.add(String.format(Locale.ROOT, "%-4s %-34s %4s %8s %8s  %s", "", "archetype", "n", "across", "seeds", "largest look-alike group"));
		for (Row r : rows) {
			lines.add(String.format(Locale.ROOT, "%-4s %-34s %4d %8.2f %8.2f  %d: %s", r.flagged() ? "FLAG" : "", r.id().getPath(), r.cultures(), r.meanAcross(),
				r.meanSeeds(), r.group().size(), String.join(",", r.group().stream().map(Culture::getSerializedName).toList())));
		}
		lines.add("across: mean likeness of two cultures' blueprints of one spec; seeds: of one culture's two seeds (how far a plan moves at all).");
		lines.add("A flagged archetype: give each culture its own plan (docs/review/ART_REVIEW.md item 30); see its sheet with "
			+ "./gradlew blueprintPreview -Parchetype=<name> -Pstyle=all -Pseeds=1");
		lines.forEach(System.out::println);
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.write(file, lines);
		System.out.println("wrote " + file);
	}
}
