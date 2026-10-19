package dev.voidmc.hearth.gametest.audit;

import dev.voidmc.hearth.build.check.Gate;
import dev.voidmc.hearth.build.check.PlayChecks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The audit gate ({@code structureAudit -Pmode=gate}): every archetype measured, then judged against BUILDING_GUIDE.md
 * §8's thresholds. <b>Hard</b> columns must be 0 in every suited case (a broken building): one case over is a FAIL, and
 * the run exits 1. <b>Soft</b> targets are means over the suited cases (the habits §8 names): a miss is a reason to look
 * at the sheet again, listed but not failing. Output {@code gate.md} and {@code gate.csv} next to {@code metrics.csv},
 * and one {@code GATE PASS|FAIL} line per archetype on the console.
 *
 * <p>An edge that is the point (a quarry face, a quay, the town side of a wall walk), a smoke hole or a light well a
 * family page names, is allowed per archetype and column in {@code hearth/gate/allow.txt}. The play rules' known
 * failures ({@code hearth/gate/known/}) are the unit tests' business; here they are only marked, never excused.
 */
final class GateTable {
	private GateTable() {
	}

	/** A hard column: 0 (or empty) in every suited case. */
	static final String[] HARD = {"error", "check_problems", "floating", "unsupported", "main_entrance_unreached", "entrances_unreached",
		"unreached_furnished_cells", "blocked_doors", "unreached_doors", "bad_flights", "dead_ladders", "beds_no_free_side", "workstations_unusable",
		"chest_lids_blocked", "dark_room_cells", "fall_edge_cells", "lava", "water_roofed", "strict_reach", "floating_pieces", "roof_gap_cells"};

	/** The play rule each hard column is (for marking known failures). */
	static final Map<String, PlayChecks.Rule> RULE_OF = Map.of("strict_reach", PlayChecks.Rule.WALK, "blocked_doors", PlayChecks.Rule.DOOR_SIDES,
		"dead_ladders", PlayChecks.Rule.LADDER_TOP, "unreached_furnished_cells", PlayChecks.Rule.FURNISHED_REACHED, "floating_pieces",
		PlayChecks.Rule.FLOATING_PIECE, "roof_gap_cells", PlayChecks.Rule.ROOF_HOLE);

	/** A soft target on an archetype's mean over its suited cases. */
	record Soft(String label, String col, boolean max, double line) {
	}

	static final List<Soft> SOFT = List.of(
		new Soft("sealed voids per case", "_sealed", true, 8),
		new Soft("rooms with no 3x3", "rooms_no3x3", true, 0),
		new Soft("cramped rooms", "cramped_rooms", true, 0),
		new Soft("purposeless rooms", "purposeless_rooms", true, 0),
		new Soft("empty rooms", "empty_room_ratio", true, 0),
		new Soft("furniture per cell", "furniture_per_cell", false, 0.25),
		new Soft("tight headroom", "tight_headroom_share", true, 0.35),
		new Soft("walkable roof holes", "roof_hole_cells", true, 0),
		new Soft("relief", "relief_share", false, 0.10),
		new Soft("flat patch", "flat_patch_max", true, 24),
		new Soft("blind sides", "blind_sides", true, 1),
		new Soft("dominant block", "dominant_share", true, 0.8),
		new Soft("facade blocks", "facade_blocks", false, 2),
		new Soft("overhang (pitched roofs)", "_overhang", false, 0.25),
		new Soft("roof complexity", "roof_complexity", false, 3),
		new Soft("mirror symmetry", "mirror_sym", true, 0.95),
		new Soft("repeated bays", "repeat_cols", true, 0.75),
		new Soft("culture likes", "culture_likes", true, 4),
		new Soft("play mean", "play_score", false, 90),
		new Soft("detail mean", "detail_score", false, 75));

	/** Per-case extremes §8 also names: no case's relief under 0.06, no flat patch over 60, no case's play under 75. */
	record Floor(String label, String col, boolean max, double line) {
	}

	static final List<Floor> FLOORS = List.of(new Floor("relief in a case", "relief_share", false, 0.06), new Floor("flat patch in a case",
		"flat_patch_max", true, 60), new Floor("play in a case", "play_score", false, 75));

	/** The verdict of one archetype. */
	record Verdict(String archetype, boolean pass, List<String> hard, List<String> soft, int cases) {
	}

	/** Judges every archetype, writes gate.md and gate.csv, prints a line each; true when every archetype passes. */
	static boolean write(List<Map<String, Object>> rows, List<StructureAudit.Entry> entries, Path out) throws IOException {
		Map<String, Set<String>> allow = allowed(Path.of(System.getProperty("hearth.gate.allow", "gate/allow.txt")));
		Map<String, List<Map<String, Object>>> by = new LinkedHashMap<>();
		for (Map<String, Object> r : rows) {
			by.computeIfAbsent((String) r.get("archetype"), k -> new ArrayList<>()).add(r);
		}
		List<Verdict> verdicts = new ArrayList<>();
		for (StructureAudit.Entry e : entries) {
			String a = e.info().id().getPath();
			verdicts.add(judge(a, by.getOrDefault(a, List.of()), allow.getOrDefault(a, Set.of())));
		}
		int fails = 0;
		List<String> md = new ArrayList<>();
		List<String> csv = new ArrayList<>();
		csv.add("archetype,verdict,cases,hard,soft");
		md.add("# Hearth audit gate");
		md.add("");
		md.add("`structureAudit -Pmode=gate` against BUILDING_GUIDE.md §8. **Hard**: 0 in every suited case (14 cultures, flat, slope and "
			+ "the archetype's own land, 2 seeds); a column is shown as `cases over / suited cases, worst value (where)`. **Soft**: the mean "
			+ "over suited cases misses its target. `known` marks a play rule listed for the archetype in `hearth/gate/known/` (still a "
			+ "FAIL here); `allowed` a column excused in `hearth/gate/allow.txt`.");
		md.add("");
		md.add("| archetype | gate | hard | soft |");
		md.add("|---|---|---|---|");
		for (Verdict v : verdicts) {
			if (!v.pass()) {
				fails++;
			}
			md.add("| " + v.archetype() + " | " + (v.pass() ? "PASS" : "**FAIL**") + " | " + String.join("; ", v.hard()).replace("|", "/") + " | "
				+ String.join("; ", v.soft()).replace("|", "/") + " |");
			csv.add(StructureAudit.cell(v.archetype()) + "," + (v.pass() ? "PASS" : "FAIL") + "," + v.cases() + "," + StructureAudit.cell(String.join("; ",
				v.hard())) + "," + StructureAudit.cell(String.join("; ", v.soft())));
			System.out.println("GATE " + (v.pass() ? "PASS " : "FAIL ") + v.archetype() + (v.hard().isEmpty() ? "" : ": " + String.join("; ", v.hard()))
				+ (v.soft().isEmpty() ? "" : "  [soft: " + v.soft().size() + "]"));
		}
		md.add("");
		md.add(String.format(Locale.ROOT, "%d of %d archetypes pass the hard columns.", verdicts.size() - fails, verdicts.size()));
		StructureAudit.writeLines(out.resolve("gate.md"), md);
		StructureAudit.writeLines(out.resolve("gate.csv"), csv);
		System.out.printf(Locale.ROOT, "audit gate: %d of %d archetypes PASS, %d FAIL -> %s%n", verdicts.size() - fails, verdicts.size(), fails,
			out.resolve("gate.md").toAbsolutePath());
		return fails == 0;
	}

	static Verdict judge(String archetype, List<Map<String, Object>> rows, Set<String> allow) {
		List<Map<String, Object>> suited = new ArrayList<>();
		for (Map<String, Object> r : rows) {
			if (StructureAudit.num(r, "suits") > 0) {
				suited.add(r);
			}
		}
		List<String> hard = new ArrayList<>();
		for (String col : HARD) {
			int over = 0;
			double worst = 0;
			Map<String, Object> worstRow = null;
			for (Map<String, Object> r : suited) {
				double v;
				if (col.equals("error")) {
					v = r.get("error") == null ? 0 : 1;
				} else {
					v = StructureAudit.num(r, col);
					if (Double.isNaN(v) || r.get("error") != null) {
						continue;
					}
				}
				if (v > 0) {
					over++;
					if (v > worst || worstRow == null) {
						worst = v;
						worstRow = r;
					}
				}
			}
			if (over == 0) {
				continue;
			}
			String note = allow.contains(col) ? " allowed" : RULE_OF.containsKey(col) && Gate.knownFailure(archetype, RULE_OF.get(col)) ? " known" : "";
			String at = worstRow == null ? "" : " (" + worstRow.get("culture") + " " + worstRow.get("terrain") + " s" + worstRow.get("seed_index")
				+ where(worstRow, col) + ")";
			String text = col + " " + over + "/" + suited.size() + (col.equals("error") ? "" : ", worst " + StructureAudit.cell(worst)) + at + note;
			hard.add(text);
		}
		boolean pass = hard.stream().allMatch(h -> h.endsWith(" allowed"));
		List<String> soft = new ArrayList<>();
		for (Soft s : SOFT) {
			double sum = 0;
			int n = 0;
			for (Map<String, Object> r : suited) {
				if (r.get("error") != null) {
					continue;
				}
				double v = softValue(r, s.col());
				if (!Double.isNaN(v)) {
					sum += v;
					n++;
				}
			}
			if (n == 0) {
				continue;
			}
			double mean = sum / n;
			if (s.max() ? mean > s.line() + 1e-9 : mean < s.line() - 1e-9) {
				soft.add(String.format(Locale.ROOT, "%s %s (%s %s)", s.label(), StructureAudit.cell(mean), s.max() ? "<=" : ">=", fmt(s.line())));
			}
		}
		for (Floor fl : FLOORS) {
			double worst = fl.max() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
			for (Map<String, Object> r : suited) {
				double v = StructureAudit.num(r, fl.col());
				if (!Double.isNaN(v) && r.get("error") == null) {
					worst = fl.max() ? Math.max(worst, v) : Math.min(worst, v);
				}
			}
			if (Double.isFinite(worst) && (fl.max() ? worst > fl.line() : worst < fl.line())) {
				soft.add(String.format(Locale.ROOT, "%s %s (%s %s)", fl.label(), StructureAudit.cell(worst), fl.max() ? "<=" : ">=", fmt(fl.line())));
			}
		}
		return new Verdict(archetype, pass, hard, soft, suited.size());
	}

	private static String fmt(double d) {
		return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
	}

	/** A soft column's value in a case; the derived ones: sealed voids, and the overhang only on pitched roofs. */
	private static double softValue(Map<String, Object> r, String col) {
		return switch (col) {
			case "_sealed" -> StructureAudit.num(r, "unreached_room_cells") - StructureAudit.num(r, "unreached_furnished_cells");
			case "_overhang" -> StructureAudit.num(r, "roof_facings") >= 2 ? StructureAudit.num(r, "overhang_mean") : Double.NaN;
			default -> StructureAudit.num(r, col);
		};
	}

	/** The where-entry of a case that fits a column, if any. */
	private static String where(Map<String, Object> r, String col) {
		String key = switch (col) {
			case "blocked_doors" -> "blocked_door@";
			case "unreached_doors" -> "unreached_door@";
			case "unreached_furnished_cells" -> "unreached_furnished_room@";
			case "bad_flights" -> "bad_flight@";
			case "dead_ladders" -> "dead_ladder@";
			case "beds_no_free_side" -> "bed_blocked@";
			case "workstations_unusable" -> "station_unusable";
			case "chest_lids_blocked" -> "chest_lid@";
			case "fall_edge_cells" -> "fall_edge@";
			case "entrances_unreached" -> "entrance_unreached@";
			default -> null;
		};
		if (key == null) {
			return "";
		}
		for (String w : String.valueOf(r.getOrDefault("where", "")).split(" ")) {
			if (w.startsWith(key)) {
				return " " + w;
			}
		}
		return "";
	}

	/** {@code <archetype path> <column> [<column>...] [# why]} per line. */
	static Map<String, Set<String>> allowed(Path file) throws IOException {
		Map<String, Set<String>> out = new HashMap<>();
		if (!Files.exists(file)) {
			return out;
		}
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			int hash = line.indexOf('#');
			String body = (hash >= 0 ? line.substring(0, hash) : line).trim();
			if (body.isEmpty()) {
				continue;
			}
			String[] p = body.split("\\s+");
			Set<String> cols = out.computeIfAbsent(p[0], k -> new HashSet<>());
			for (int i = 1; i < p.length; i++) {
				cols.add(p[i]);
			}
		}
		return out;
	}
}
