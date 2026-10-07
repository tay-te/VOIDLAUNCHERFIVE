package dev.voidmc.hearth.gametest.audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scores per case (playability and detail, 0 to 100), the scores per archetype, and the worst offenders per metric. The
 * weights are a reviewer's, not a law: they rank what to look at first.
 */
final class Scores {
	private Scores() {
	}

	/** A metric ranked for the worst-offenders list: its column, whether higher is worse, and the line a value must pass to count. */
	record Metric(String col, String what, boolean higherWorse, double bad, boolean play) {
	}

	static final List<Metric> METRICS = List.of(
		new Metric("check_problems", "invariant check failures (BlueprintChecks.all: bounds, floating, reach, order, light)", true, 0, true),
		new Metric("floating", "floating blocks (not joined to the ground)", true, 0, true),
		new Metric("unsupported", "blocks lacking support (plants, carpets, lanterns)", true, 0, true),
		new Metric("main_entrance_unreached", "main entrance not walkable from the street", true, 0, true),
		new Metric("unreached_room_cells", "room cells (enclosed, walkable) a player cannot reach from the street door", true, 0, true),
		new Metric("unreached_levels", "whole storeys with rooms that no stair or ladder reaches", true, 0, true),
		new Metric("blocked_doors", "doors with a wall or a drop on one side", true, 0, true),
		new Metric("unreached_doors", "doors nobody can walk to", true, 0, true),
		new Metric("entrances_unreached", "registered entrances not reached", true, 0, true),
		new Metric("bad_flights", "stair flights that end at a wall/ceiling or lack headroom", true, 0, true),
		new Metric("dead_ladders", "ladders with no floor to step off at the top", true, 0, true),
		new Metric("beds_no_free_side", "beds with no reachable free side", true, 0, true),
		new Metric("workstations_unusable", "workstations, chests and barrels with no reachable face", true, 0, true),
		new Metric("chest_lids_blocked", "chests under a solid block (cannot open)", true, 0, true),
		new Metric("dark_room_cells", "room cells under light 8", true, 0, true),
		new Metric("light_min_room", "darkest room cell (block light)", false, 8, true),
		new Metric("roof_hole_cells", "open-sky cells inside the roof (rain gets in)", true, 0, true),
		new Metric("open_roofed_share", "share of roofed floor that is not enclosed (porches, or walls with gaps)", true, 0.6, true),
		new Metric("rooms_no3x3", "rooms of 6+ cells with no 3x3 clear floor", true, 0, true),
		new Metric("cramped_rooms", "furnished rooms with fewer than 6 free cells", true, 0, true),
		new Metric("purposeless_rooms", "dead-end rooms with no POI and at most one piece of furniture", true, 0, true),
		new Metric("tight_headroom_share", "share of room floor with only 2 blocks of headroom", true, 0.35, true),
		new Metric("fall_edge_cells", "walked cells beside an unguarded drop of 4+", true, 0, true),
		new Metric("lava", "lava placed", true, 0, true),
		new Metric("water_roofed", "water placed under a roof", true, 0, true),
		new Metric("relief_share", "facade relief (share of wall area with depth or shaped blocks)", false, 0.15, false),
		new Metric("flat_patch_max", "largest flat patch of one block on a facade (cells)", true, 24, false),
		new Metric("facade_blocks", "distinct blocks per facade", false, 2.0, false),
		new Metric("dominant_share", "share of the commonest block on a facade", true, 0.8, false),
		new Metric("windows_per_side", "windows per facade (sides 5+ wide)", false, 1.0, false),
		new Metric("blind_sides", "facades 5+ wide with no window", true, 1, false),
		new Metric("overhang_mean", "roof overhang beyond the wall (blocks, median per side)", false, 0.5, false),
		new Metric("roof_complexity", "roof form complexity (slope facings + hips/valleys + levels)", false, 3.0, false),
		new Metric("mirror_sym", "left-right mirror likeness of the body", true, 0.95, false),
		new Metric("repeat_cols", "facade columns repeating another exactly", true, 0.75, false),
		new Metric("empty_room_ratio", "share of rooms (6+ cells) with no furniture", true, 0.0, false),
		new Metric("furniture_per_cell", "furniture blocks per room floor cell", false, 0.25, false),
		new Metric("culture_twin", "plan likeness to the nearest other culture (flat, seed 0)", true, 0.85, false),
		new Metric("height_ratio", "height over the longer side", true, 2.5, false));

	private static double clamp(double v) {
		return Math.max(0, Math.min(1, v));
	}

	/** Puts play_score and detail_score on a row. */
	static void score(Map<String, Object> r) {
		if (r.get("error") != null) {
			r.put("play_score", 0.0);
			r.put("detail_score", Double.NaN);
			return;
		}
		double p = 100;
		p -= Math.min(45, 15 * StructureAudit.num(r, "check_problems"));
		double room = StructureAudit.num(r, "room_cells");
		if (room > 0) {
			p -= 40 * StructureAudit.num(r, "unreached_room_cells") / room;
			p -= 30 * StructureAudit.num(r, "dark_room_cells") / room;
		}
		p -= Math.min(15, 5 * StructureAudit.num(r, "unreached_levels"));
		p -= Math.min(24, 6 * StructureAudit.num(r, "blocked_doors"));
		p -= Math.min(12, 3 * StructureAudit.num(r, "unreached_doors"));
		p -= Math.min(10, 10 * StructureAudit.num(r, "entrances_unreached"));
		p -= Math.min(24, 8 * StructureAudit.num(r, "bad_flights"));
		p -= Math.min(12, 6 * StructureAudit.num(r, "dead_ladders"));
		p -= Math.min(30, 10 * StructureAudit.num(r, "beds_no_free_side"));
		p -= Math.min(20, 5 * StructureAudit.num(r, "workstations_unusable"));
		p -= Math.min(9, 3 * StructureAudit.num(r, "chest_lids_blocked"));
		p -= Math.min(16, 2 * StructureAudit.num(r, "roof_hole_cells"));
		p -= Math.min(12, 3 * StructureAudit.num(r, "rooms_no3x3"));
		p -= Math.min(12, 4 * StructureAudit.num(r, "cramped_rooms"));
		p -= Math.min(10, 2 * StructureAudit.num(r, "purposeless_rooms"));
		p -= Math.min(15, StructureAudit.num(r, "fall_edge_cells"));
		p -= Math.min(20, 10 * StructureAudit.num(r, "lava"));
		r.put("play_score", Math.max(0, p));

		List<Double> sub = new ArrayList<>();
		add(sub, clamp(StructureAudit.num(r, "relief_share") / 0.30));
		double patch = StructureAudit.num(r, "flat_patch_max");
		add(sub, Double.isNaN(StructureAudit.num(r, "relief_share")) ? Double.NaN : clamp(1 - (patch - 9) / 31.0));
		add(sub, clamp((StructureAudit.num(r, "facade_blocks") - 1) / 2.0) * (StructureAudit.num(r, "dominant_share") > 0.85 ? 0.6 : 1));
		add(sub, clamp(StructureAudit.num(r, "windows_per_side") / 2.0));
		double over = StructureAudit.num(r, "overhang_mean");
		add(sub, Double.isNaN(over) ? Double.NaN : 0.3 + 0.7 * clamp(over));
		add(sub, clamp(StructureAudit.num(r, "roof_complexity") / 6.0));
		double sym = StructureAudit.num(r, "mirror_sym");
		add(sub, Double.isNaN(sym) ? Double.NaN : sym >= 0.97 ? 0.3 : clamp(1 - (sym - 0.9) / 0.1 * 0.7));
		double rep = StructureAudit.num(r, "repeat_cols");
		add(sub, Double.isNaN(rep) ? Double.NaN : rep <= 0.5 ? 1 : clamp(1 - (rep - 0.5) / 0.4 * 0.8));
		double empty = StructureAudit.num(r, "empty_room_ratio");
		add(sub, Double.isNaN(empty) ? Double.NaN : 1 - empty);
		double twin = StructureAudit.num(r, "culture_twin");
		add(sub, Double.isNaN(twin) ? Double.NaN : clamp(1 - (twin - 0.6) / 0.3));
		double s = 0;
		int n = 0;
		for (double d : sub) {
			s += d;
			n++;
		}
		r.put("detail_score", n == 0 ? Double.NaN : 100 * s / n);
	}

	private static void add(List<Double> l, double v) {
		if (!Double.isNaN(v)) {
			l.add(v);
		}
	}

	static boolean counts(Map<String, Object> r) {
		return r.get("error") == null && StructureAudit.num(r, "suits") > 0;
	}

	/** archetypes.csv, worst.md and top.md. */
	static void write(List<Map<String, Object>> rows, List<StructureAudit.Entry> entries, Path out) throws IOException {
		// per archetype
		Map<String, List<Map<String, Object>>> by = new LinkedHashMap<>();
		for (Map<String, Object> r : rows) {
			by.computeIfAbsent((String) r.get("archetype"), k -> new ArrayList<>()).add(r);
		}
		String[] keys = {"unreached_room_cells", "blocked_doors", "bad_flights", "beds_no_free_side", "workstations_unusable", "dark_room_cells",
			"roof_hole_cells", "rooms_no3x3", "relief_share", "flat_patch_max", "windows_per_side", "overhang_mean", "roof_complexity", "mirror_sym",
			"empty_room_ratio", "culture_twin"};
		List<String> lines = new ArrayList<>();
		lines.add("group,category,archetype,natural_terrain,cases,errors,cases_with_check_failures,play_score,play_min,detail_score,overall,"
			+ String.join(",", keys));
		List<String[]> ranked = new ArrayList<>();
		for (StructureAudit.Entry e : entries) {
			List<Map<String, Object>> rs = by.getOrDefault(e.info().id().getPath(), List.of());
			int cases = 0;
			int errors = 0;
			int failing = 0;
			double ps = 0;
			double pmin = 100;
			double ds = 0;
			int dn = 0;
			double[] sums = new double[keys.length];
			int[] ns = new int[keys.length];
			for (Map<String, Object> r : rs) {
				if (StructureAudit.num(r, "suits") <= 0) {
					continue;
				}
				if (r.get("error") != null) {
					errors++;
					continue;
				}
				cases++;
				if (StructureAudit.num(r, "check_problems") > 0) {
					failing++;
				}
				double p = StructureAudit.num(r, "play_score");
				ps += p;
				pmin = Math.min(pmin, p);
				double d = StructureAudit.num(r, "detail_score");
				if (!Double.isNaN(d)) {
					ds += d;
					dn++;
				}
				for (int i = 0; i < keys.length; i++) {
					double v = StructureAudit.num(r, keys[i]);
					if (!Double.isNaN(v)) {
						sums[i] += v;
						ns[i]++;
					}
				}
			}
			double play = cases == 0 ? Double.NaN : ps / cases;
			double detail = dn == 0 ? Double.NaN : ds / dn;
			double overall = Double.isNaN(detail) ? play : (play + detail) / 2;
			StringBuilder sb = new StringBuilder();
			sb.append(e.group()).append(',').append(e.category()).append(',').append(e.info().id().getPath()).append(',').append(e.terrain()).append(',')
				.append(cases).append(',').append(errors).append(',').append(failing).append(',').append(StructureAudit.cell(play)).append(',')
				.append(StructureAudit.cell(pmin)).append(',').append(StructureAudit.cell(detail)).append(',').append(StructureAudit.cell(overall));
			for (int i = 0; i < keys.length; i++) {
				sb.append(',').append(StructureAudit.cell(ns[i] == 0 ? Double.NaN : sums[i] / ns[i]));
			}
			ranked.add(new String[]{String.format(Locale.ROOT, "%09.3f", Double.isNaN(overall) ? 0 : overall), sb.toString()});
		}
		ranked.sort(Comparator.comparing(a -> a[0]));
		for (String[] r : ranked) {
			lines.add(r[1]);
		}
		StructureAudit.writeLines(out.resolve("archetypes.csv"), lines);

		// worst offenders per metric: the worst case of each archetype, the 15 worst archetypes
		List<String> md = new ArrayList<>();
		List<String> top = new ArrayList<>();
		md.add("# Worst offenders per metric");
		md.add("");
		md.add("Suited cultures only (an archetype is never built in a culture it does not suit). One line per archetype: its worst case.");
		md.add("Case = culture / terrain / seed index (seed 1007 + 1000 x index; index 0 faces south, 1 east). 15 worst archetypes per metric.");
		for (Metric mt : METRICS) {
			Map<String, Map<String, Object>> worst = new HashMap<>();
			Map<String, Integer> badCases = new HashMap<>();
			int bad = 0;
			int total = 0;
			for (Map<String, Object> r : rows) {
				if (!counts(r)) {
					continue;
				}
				double v = StructureAudit.num(r, mt.col());
				if (Double.isNaN(v)) {
					continue;
				}
				total++;
				boolean isBad = mt.higherWorse() ? v > mt.bad() : v < mt.bad();
				if (!isBad) {
					continue;
				}
				bad++;
				String a = (String) r.get("archetype");
				badCases.merge(a, 1, Integer::sum);
				Map<String, Object> w = worst.get(a);
				if (w == null || (mt.higherWorse() ? v > StructureAudit.num(w, mt.col()) : v < StructureAudit.num(w, mt.col()))) {
					worst.put(a, r);
				}
			}
			List<Map<String, Object>> l = new ArrayList<>(worst.values());
			l.sort(Comparator.comparingDouble((Map<String, Object> r) -> (mt.higherWorse() ? -1 : 1) * StructureAudit.num(r, mt.col()))
				.thenComparing(r -> -badCases.get((String) r.get("archetype"))));
			md.add("");
			md.add("## " + mt.col() + (mt.play() ? " (playability)" : " (detail)"));
			md.add("");
			md.add(mt.what() + ". Bad when " + (mt.higherWorse() ? "> " : "< ") + mt.bad() + ": " + bad + " of " + total + " cases, " + worst.size()
				+ " archetypes.");
			md.add("");
			if (l.isEmpty()) {
				md.add("(none)");
				continue;
			}
			md.add("| archetype | value | case | bad cases | note |");
			md.add("|---|---|---|---|---|");
			Set<String> shown = new HashSet<>();
			StringBuilder t = new StringBuilder();
			for (Map<String, Object> r : l) {
				if (shown.size() >= 15) {
					break;
				}
				String a = (String) r.get("archetype");
				shown.add(a);
				String note = mt.col().equals("check_problems") || mt.col().equals("floating") || mt.col().equals("unsupported")
					|| mt.col().equals("main_entrance_unreached") ? String.valueOf(r.getOrDefault("check_text", "")).replace("|", "/")
					: mt.play() ? String.valueOf(r.getOrDefault("where", "")) : "";
				if (note.length() > 160) {
					note = note.substring(0, 160) + "...";
				}
				md.add("| " + a + " | " + StructureAudit.cell(r.get(mt.col())) + " | " + r.get("culture") + " / " + r.get("terrain") + " / s"
					+ r.get("seed_index") + " | " + badCases.get(a) + " | " + note + " |");
				if (shown.size() <= 5) {
					t.append(t.isEmpty() ? "" : "; ").append(a).append(" ").append(StructureAudit.cell(r.get(mt.col()))).append(" (").append(r.get("culture"))
						.append(" ").append(r.get("terrain")).append(" s").append(r.get("seed_index")).append(")");
				}
			}
			top.add((mt.play() ? "P" : "D") + "|" + mt.col() + "|" + bad + "|" + total + "|" + worst.size() + "|" + t);
		}
		StructureAudit.writeLines(out.resolve("worst.md"), md);
		StructureAudit.writeLines(out.resolve("top.txt"), top);
	}
}
