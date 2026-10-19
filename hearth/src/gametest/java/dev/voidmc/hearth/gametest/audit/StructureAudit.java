package dev.voidmc.hearth.gametest.audit;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * The structure audit: every registered archetype x every culture x three terrains (flat, slope and the archetype's own:
 * river, coast, cliff or hills) x two seeds, measured offline for playability and detail ({@link Measure}), ranked, and
 * (mode {@code sheets}) drawn as contact sheets for the visual review ({@link AuditSheets}).
 *
 * <pre>
 *   ./gradlew structureAudit -Pmode=metrics -Pout=build/audit
 *   ./gradlew structureAudit -Pmode=sheets -Parchetype=house/cottage,inn
 *   ./gradlew structureAudit -Pmode=gate -Parchetype=house/       (metrics, then PASS/FAIL per archetype; exit 1 on a FAIL)
 *   args: mode(metrics|sheets|all|gate)  archetypes(all | ids, paths or names)  seeds  outDir  threads
 * </pre>
 * Output: {@code metrics.csv} (one row per case), {@code archetypes.csv} (scores per archetype), {@code worst.md}
 * (worst offenders per metric) and {@code INDEX.md}; sheets in {@code sheets/<category>/<name>.webp}; in mode {@code gate}
 * also {@code gate.md} and {@code gate.csv}, the verdict per archetype against BUILDING_GUIDE.md §8 ({@link GateTable}).
 * The walk is the invariant checks' own (BlueprintChecks / Spaces): a 1 x 2 mover that steps up a block (stairs, slabs,
 * a jump onto a full block with three clear above), drops at most three, climbs ladders and passes doors: a player's box.
 */
public final class StructureAudit {
	/** The six groups the visual auditors split the catalogue into, by the archetype's package. */
	static final Map<String, String> GROUPS = new LinkedHashMap<>();

	static {
		GROUPS.put("house", "G1 homes");
		GROUPS.put("work", "G2 work-craft-industry-storage");
		GROUPS.put("storage", "G2 work-craft-industry-storage");
		GROUPS.put("civic", "G3 civic-learning-law");
		GROUPS.put("law", "G3 civic-learning-law");
		GROUPS.put("commerce", "G4 commerce-social-landscape");
		GROUPS.put("landscape", "G4 commerce-social-landscape");
		GROUPS.put("landmark", "G5 landmark-noble-defense");
		GROUPS.put("noble", "G5 landmark-noble-defense");
		GROUPS.put("defense", "G5 landmark-noble-defense");
		GROUPS.put("farm", "G6 farm-infra-extract-deep");
		GROUPS.put("infra", "G6 farm-infra-extract-deep");
		GROUPS.put("extract", "G6 farm-infra-extract-deep");
		GROUPS.put("deep", "G6 farm-infra-extract-deep");
	}

	/** The columns of metrics.csv, in order. */
	static final String[] COLUMNS = {"group", "category", "archetype", "culture", "suits", "terrain", "seed_index", "seed", "facing", "frontage", "depth",
		"steps", "beds", "entrances", "error",
		// playability
		"check_problems", "floating", "unsupported", "main_entrance_unreached", "beds_unreached_check", "jobs_unreached_check", "walk_cells",
		"reached_cells", "room_cells", "unreached_room_cells", "unreached_furnished_cells", "unreached_pockets", "unreached_levels", "rooms", "rooms_no3x3", "cramped_rooms",
		"empty_rooms", "purposeless_rooms", "tight_headroom_share", "open_roofed_share", "doors", "blocked_doors", "doors_facing_wall",
		"doors_facing_drop", "unreached_doors", "entrances_unreached", "flights", "bad_flights", "ladders", "dead_ladders", "beds_no_free_side",
		"workstations", "workstations_unusable", "chest_lids_blocked", "light_min_room", "light_mean_room", "dark_room_cells", "light_unmet",
		"roof_hole_cells", "fall_edge_cells", "lava", "water_roofed",
		// the gate's play rules (PlayChecks): the strict walk, pieces standing on nothing, roof gaps walkable or not, the rules broken
		"strict_reach", "floating_pieces", "roof_gap_cells", "gate",
		// detail
		"relief_share", "flat_patch_max", "facade_blocks", "dominant_share", "windows_per_side", "blind_sides", "overhang_mean", "roof_facings",
		"roof_corners", "roof_levels", "roof_complexity", "height", "height_ratio", "mirror_sym", "repeat_cols", "furniture_per_cell",
		"empty_room_ratio", "culture_twin", "twin_culture", "culture_likes", "shape_hash",
		// scores
		"play_score", "detail_score", "where", "check_text", "gate_text"};

	private StructureAudit() {
	}

	/** An archetype as the audit sees it: its info, package category and group. */
	record Entry(ArchetypeInfo info, String category, String group, String terrain) {
		String name() {
			return this.info.id().getPath().replace('/', '_');
		}
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String mode = args.length > 0 ? args[0] : "metrics";
		String which = args.length > 1 ? args[1] : "all";
		int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 2;
		Path out = Path.of(args.length > 3 ? args[3] : "build/audit");
		int threads = args.length > 4 ? Integer.parseInt(args[4]) : Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
		Files.createDirectories(out);

		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		List<Entry> entries = new ArrayList<>();
		for (Archetype a : registry.all()) {
			ArchetypeInfo info = a.info();
			if (info.id().getPath().startsWith("debug/") || !(which.equals("all") || matches(which, info))) {
				continue;
			}
			String pkg = a.getClass().getPackageName();
			String cat = pkg.contains(".archetype.") ? pkg.substring(pkg.indexOf(".archetype.") + 11).split("\\.")[0] : info.id().getPath().split("/")[0];
			if (!GROUPS.containsKey(cat)) {
				cat = info.id().getPath().split("/")[0];
			}
			entries.add(new Entry(info, cat, GROUPS.getOrDefault(cat, "G? other"), naturalTerrain(info, cat)));
		}
		entries.sort(Comparator.comparing((Entry e) -> e.group()).thenComparing(e -> e.info().id().toString()));
		System.out.println("structure audit: " + entries.size() + " archetypes, mode " + mode + ", " + threads + " threads, out " + out.toAbsolutePath());

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		boolean gatePassed = true;
		try {
			if (mode.equals("metrics") || mode.equals("all") || mode.equals("gate")) {
				List<Map<String, Object>> rows = metrics(service, entries, seeds, out, pool);
				if (mode.equals("gate")) {
					gatePassed = GateTable.write(rows, entries, out);
				}
			}
			if (mode.equals("sheets") || mode.equals("all")) {
				AuditSheets.run(service, entries, out.resolve("sheets"), pool);
			}
		} finally {
			pool.shutdownNow();
		}
		Index.write(entries, out);
		System.out.println("wrote " + out.resolve("INDEX.md").toAbsolutePath());
		if (!gatePassed) {
			// a hard column over 0 somewhere: the gate fails the build step that ran it
			System.exit(1);
		}
		System.exit(0);
	}

	private static boolean matches(String arg, ArchetypeInfo info) {
		for (String a : arg.split(",")) {
			String p = info.id().getPath();
			if (a.equals(info.id().toString()) || a.equals(p) || p.endsWith("/" + a) || a.endsWith("/") && p.startsWith(a)) {
				return true;
			}
		}
		return false;
	}

	/** The land an archetype is made for, beside flat and slope: water for waterside works, a cliff for workings, hills else. */
	static String naturalTerrain(ArchetypeInfo info, String cat) {
		String p = info.id().getPath();
		boolean water = info.flags().contains(ArchetypeInfo.SiteFlag.WATERFRONT) || info.flags().contains(ArchetypeInfo.SiteFlag.ON_WATER);
		if (water) {
			return p.matches(".*(dock|harbo|pier|wharf|light|sea|boat|fish|salt|quay|ship|net).*") ? "coast" : "river";
		}
		if (cat.equals("extract") || cat.equals("deep")) {
			return "cliff";
		}
		return "hills";
	}

	/** The terrain probe the previews use for a culture (cold cultures get snow). */
	static SyntheticTerrain probe(String terrain, Culture c) {
		float temperature = c == Culture.TUNDRA ? -0.3F : c == Culture.ALPINE || c == Culture.NORTHERN ? 0.3F : 0.8F;
		return new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
	}

	static long seed(int index) {
		return 1000L * (index + 1) + 7;
	}

	// =========================================================================================== metrics

	private static List<Map<String, Object>> metrics(BlueprintServiceImpl service, List<Entry> entries, int seeds, Path out, ExecutorService pool)
		throws Exception {
		List<Map<String, Object>> all = new ArrayList<>();
		long t0 = System.currentTimeMillis();
		try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out.resolve("metrics.csv"), StandardCharsets.UTF_8))) {
			csv.println(String.join(",", COLUMNS));
			int done = 0;
			for (Entry e : entries) {
				List<Future<Map<String, Object>>> fs = new ArrayList<>();
				for (Culture c : Culture.values()) {
					Style style = service.styles().get(StyleRegistry.styleId(c));
					if (style == null) {
						continue;
					}
					for (String terrain : new String[]{"flat", "slope", e.terrain()}) {
						for (int s = 0; s < seeds; s++) {
							int index = s;
							fs.add(pool.submit(() -> one(service, e, c, style, terrain, index)));
						}
					}
				}
				List<Map<String, Object>> rows = new ArrayList<>();
				for (Future<Map<String, Object>> f : fs) {
					rows.add(f.get());
				}
				twins(rows);
				for (Map<String, Object> r : rows) {
					Scores.score(r);
					csv.println(line(r));
				}
				csv.flush();
				all.addAll(rows);
				done++;
				System.out.printf(Locale.ROOT, "[%d/%d] %s: %d cases, %.0f s%n", done, entries.size(), e.info().id().getPath(), rows.size(),
					(System.currentTimeMillis() - t0) / 1000.0);
			}
		}
		Scores.write(all, entries, out);
		System.out.printf(Locale.ROOT, "audit metrics: %d cases in %.0f s -> %s%n", all.size(), (System.currentTimeMillis() - t0) / 1000.0,
			out.resolve("metrics.csv").toAbsolutePath());
		return all;
	}

	private static Map<String, Object> one(BlueprintServiceImpl service, Entry e, Culture c, Style style, String terrain, int index) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("group", e.group());
		m.put("category", e.category());
		m.put("archetype", e.info().id().getPath());
		m.put("culture", c.getSerializedName());
		m.put("suits", e.info().suits(c) ? 1 : 0);
		m.put("terrain", terrain);
		m.put("seed_index", index);
		m.put("seed", seed(index));
		try {
			// blueprintPreview's case; the dock set on its shore and the walkway between two house fronts (Demos)
			Demos.Case cs = Demos.of(e.info(), style, seed(index), index, probe(terrain, c), terrain);
			dev.voidmc.hearth.api.TerrainProbe probe = cs.probe();
			PlotSpec spec = cs.spec();
			// -Daudit.params=k=v;k=v: params every case is drawn with (storeys=2: the planner's village height)
			CompoundTag params = spec.params().copy();
			boolean extra = false;
			for (String kv : System.getProperty("audit.params", "").split(";")) {
				if (kv.contains("=")) {
					String[] p = kv.split("=", 2);
					extra = true;
					try {
						params.putInt(p[0], Integer.parseInt(p[1]));
					} catch (NumberFormatException ex) {
						params.putString(p[0], p[1]);
					}
				}
			}
			if (extra) {
				spec = new PlotSpec(spec.archetype(), spec.style(), spec.seed(), spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing(), spec.maxHeight(),
					spec.tier(), params);
			}
			m.put("facing", spec.facing().getSerializedName());
			m.put("frontage", spec.frontage());
			m.put("depth", spec.depth());
			BlueprintImpl bp = service.compiled(spec, probe);
			m.put("steps", bp.totalSteps());
			m.put("beds", bp.beds());
			m.put("entrances", bp.entrances().size());
			Measure.playability(bp, probe, m);
			Measure.detail(bp, m);
			if (terrain.equals("flat") && index == 0) {
				m.put("_shape", Measure.shape(bp));
			}
		} catch (Throwable t) {
			// where it broke: the first frames in Hearth's own code
			StringBuilder at = new StringBuilder();
			for (StackTraceElement el : t.getStackTrace()) {
				if (el.getClassName().startsWith("dev.voidmc") && at.length() < 200) {
					at.append(at.isEmpty() ? " at " : " < ").append(el.getClassName().substring(el.getClassName().lastIndexOf('.') + 1)).append(':')
						.append(el.getLineNumber());
				}
			}
			m.put("error", (t.getClass().getSimpleName() + ": " + t.getMessage() + at).replace('\n', ' '));
		}
		return m;
	}

	/** Likeness across cultures of one archetype's flat seed-0 blueprint (its plan, materials aside): the closest twin. */
	private static void twins(List<Map<String, Object>> rows) {
		List<Map<String, Object>> flat = new ArrayList<>();
		for (Map<String, Object> r : rows) {
			if (r.get("_shape") != null) {
				flat.add(r);
			}
		}
		for (Map<String, Object> a : flat) {
			LongOpenHashSet sa = (LongOpenHashSet) a.get("_shape");
			double best = -1;
			String who = "";
			for (Map<String, Object> b : flat) {
				if (a == b || ((Number) b.get("suits")).intValue() == 0) {
					continue;
				}
				double j = Measure.jaccard(sa, (LongOpenHashSet) b.get("_shape"));
				if (j > best) {
					best = j;
					who = (String) b.get("culture");
				}
			}
			int likes = 0;
			for (Map<String, Object> b : flat) {
				if (a != b && ((Number) b.get("suits")).intValue() != 0 && Measure.jaccard(sa, (LongOpenHashSet) b.get("_shape")) >= 0.8) {
					likes++;
				}
			}
			a.put("culture_likes", likes);
			if (best >= 0) {
				a.put("culture_twin", best);
				a.put("twin_culture", who);
			}
			long h = 1125899906842597L;
			long[] keys = sa.toLongArray();
			java.util.Arrays.sort(keys);
			for (long k : keys) {
				h = 31 * h + k;
			}
			a.put("shape_hash", Long.toHexString(h));
		}
		for (Map<String, Object> a : flat) {
			a.remove("_shape");
		}
	}

	static String line(Map<String, Object> r) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < COLUMNS.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(cell(r.get(COLUMNS[i])));
		}
		return sb.toString();
	}

	static String cell(Object v) {
		if (v == null) {
			return "";
		}
		if (v instanceof Double d) {
			return d.isNaN() ? "" : String.format(Locale.ROOT, "%.3f", d);
		}
		String s = v.toString();
		if (s.contains(",") || s.contains("\"")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	static double num(Map<String, Object> r, String k) {
		Object v = r.get(k);
		return v instanceof Number n ? n.doubleValue() : Double.NaN;
	}

	static Map<String, List<Entry>> byGroup(List<Entry> entries) {
		Map<String, List<Entry>> m = new TreeMap<>();
		for (Entry e : entries) {
			m.computeIfAbsent(e.group(), k -> new ArrayList<>()).add(e);
		}
		return m;
	}

	static void writeLines(Path file, List<String> lines) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.write(file, lines, StandardCharsets.UTF_8);
	}

	static final Map<Culture, String> CULTURE_NAMES = new EnumMap<>(Culture.class);

	static {
		for (Culture c : Culture.values()) {
			CULTURE_NAMES.put(c, c.getSerializedName());
		}
	}
}
