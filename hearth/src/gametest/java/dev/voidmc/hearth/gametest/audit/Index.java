package dev.voidmc.hearth.gametest.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** INDEX.md: how to read the outputs, the archetypes by audit group with their sheets and scores, and the top problems. */
final class Index {
	private Index() {
	}

	static void write(List<StructureAudit.Entry> entries, Path out) throws IOException {
		Map<String, String[]> scores = new HashMap<>();
		Path arch = out.resolve("archetypes.csv");
		if (Files.exists(arch)) {
			List<String> lines = Files.readAllLines(arch, StandardCharsets.UTF_8);
			for (int i = 1; i < lines.size(); i++) {
				String[] c = lines.get(i).split(",", -1);
				scores.put(c[2], c);
			}
		}
		List<String> md = new ArrayList<>();
		md.add("# Hearth structure audit");
		md.add("");
		md.add("Made by `./gradlew structureAudit` (hearth/src/gametest/java/dev/voidmc/hearth/gametest/audit/). " + entries.size()
			+ " registered archetypes x 14 cultures x 3 terrains (flat, slope, and the archetype's own: river or coast for waterside works, cliff "
			+ "for workings and the deep, hills for the rest) x 2 seeds (1007 facing south, 2007 facing east), every blueprint compiled by the "
			+ "real build module offline, as `blueprintPreview` does.");
		md.add("");
		md.add("## Files");
		md.add("");
		md.add("- `metrics.csv`: one row per case (archetype, culture, terrain, seed). `suits=0` marks a culture that never builds the archetype "
			+ "(measured, but left out of every ranking). `error` is set when the blueprint failed to draw or compile.");
		md.add("- `archetypes.csv`: per archetype over its suited cases, sorted worst first: `play_score` (mean, 0-100), `play_min` (its worst "
			+ "case), `detail_score`, `overall` (mean of the two) and the means of the key metrics.");
		md.add("- `worst.md`: per metric the 15 worst archetypes (each by its worst case) with the case to reproduce.");
		md.add("- `sheets/<category>/<name>.webp`: every culture from the front-right corner, seed 1007, flat land (river, coast or cliff for "
			+ "waterside works, workings and the deep). `<name>_cut.webp` for buildings with rooms: per culture its storey cutaways by day "
			+ "and the main floor by night with its own lamps; red floor markers are room cells under light 8, orange a dark door, magenta "
			+ "dark ground. Grey labels: a culture that does not build the archetype.");
		md.add("");
		md.add("Reproduce one case: `./gradlew blueprintPreview -Parchetype=<path> -Pstyle=<culture> -Pseeds=1 -Pterrain=\"<terrain>;seed=<seed>;"
			+ "facing=<facing>\"` (add `night=true` for the night panels, `reach=0/4` for walk maps).");
		md.add("");
		md.add("## How to read metrics.csv");
		md.add("");
		md.add("The walk is the invariant checks' own (BlueprintChecks and the light check's Spaces): a mover one block wide and two high (a "
			+ "player's 0.6 x 1.8), stepping up a stair, slab or one full block with three clear above, dropping at most three, climbing "
			+ "ladders, passing doors and gates, starting from the street row and the registered entrances. A *room cell* is a walkable cell "
			+ "in the plot with a roof above and a wall before the roof ends in all four directions (Spaces.enclosed). The light check's walk "
			+ "does not stand on candles, lanterns, flower pots, cakes or skulls (Spaces.standable); the BlueprintChecks gate does, so a "
			+ "room reached only by clambering over pots passes the tests but shows here as unreached (desert courtyard house).");
		md.add("");
		md.add("| column | meaning |");
		md.add("|---|---|");
		String[][] cols = {
			{"check_problems, floating, unsupported, main_entrance_unreached, beds/jobs_unreached_check, check_text",
				"BlueprintChecks.all as the tests run it (bounds, floating, support, reach, chunks, order, light), parsed"},
			{"walk_cells, reached_cells, room_cells", "walkable cells in the plot; of them reached from the street; of them room cells"},
			{"unreached_room_cells, unreached_furnished_cells, unreached_pockets, unreached_levels", "room cells no one can walk to; of them "
				+ "those in rooms with furniture, a POI or a door (the rest are sealed voids under roofs: wasted space that needs a hidden "
				+ "lamp); unreached pockets of 4+ cells; whole storeys (6+ room cells) with nothing reached"},
			{"rooms, rooms_no3x3, cramped_rooms, empty_rooms, purposeless_rooms", "reached rooms of 4+ cells (4-connected on one level, doors "
				+ "split them); rooms of 6+ cells with no 3x3 of clear floor; furnished rooms with < 6 free cells; rooms of 6+ cells with no "
				+ "furniture; rooms with <= 2 ways out, no POI and <= 1 piece of furniture"},
			{"tight_headroom_share", "share of room floor with exactly two blocks clear above (heads at the ceiling)"},
			{"open_roofed_share", "share of roofed reached floor that is not enclosed (porches and arcades, or rooms whose walls have gaps)"},
			{"doors, blocked_doors, doors_facing_wall, doors_facing_drop, unreached_doors", "door blocks; doors with a side nobody can stand on "
				+ "(level, a step down, or a stair up); sides against a wall; sides over a drop; doors nobody walks to"},
			{"entrances_unreached", "registered entrances with no reached cell within two below or one above"},
			{"flights, bad_flights", "walked flights (2+ stairs climbing one way); flights that end at a wall or ceiling or have a step without "
				+ "two clear above"},
			{"ladders, dead_ladders", "ladder columns; ladders with nowhere to step off at the top"},
			{"beds_no_free_side", "beds with no reached cell beside either half"},
			{"workstations, workstations_unusable, chest_lids_blocked", "job blocks, chests, barrels; those with no reached face (beside, a "
				+ "level up or down); chests under a solid block"},
			{"light_min_room, light_mean_room, dark_room_cells, light_unmet", "block light on reached room cells (the light check's flood); cells "
				+ "under 8; the light check's unmet needs"},
			{"roof_hole_cells", "walkable open-sky cells in patches of <= 6 with roof all round (rain gets in; smoke holes and light wells show "
				+ "here too)"},
			{"fall_edge_cells", "reached cells beside a drop of 4+ with no rail and no water below"},
			{"lava, water_roofed", "lava placed; water placed under a roof"},
			{"relief_share", "per facade (front, back, sides; the body's stages only: foundation, frame, walls, floors, roof, openings) the share "
				+ "of wall cells standing proud or recessed from the wall plane or made of shaped blocks (stairs, slabs, trapdoors, fences): "
				+ "beams, sills, frames, shutters, pilasters"},
			{"flat_patch_max", "largest 4-connected patch of one block on the wall plane, over all facades (cells)"},
			{"facade_blocks, dominant_share", "distinct blocks per facade; the commonest block's share"},
			{"windows_per_side, blind_sides", "glass groups and wall openings per facade 5+ wide; such facades without one"},
			{"overhang_mean", "median over a facade's columns of how far the roof above the wall stands out from the wall plane"},
			{"roof_facings, roof_corners, roof_levels, roof_complexity", "top surface: distinct stair facings; inner/outer stair corners "
				+ "(hips, valleys); distinct heights; facings + corners/2 (<= 4) + levels/3 (<= 3)"},
			{"height, height_ratio", "blocks above the floor; over the body's longer side"},
			{"mirror_sym", "share of body blocks whose left-right mirror is the same block (1 = perfectly symmetric)"},
			{"repeat_cols", "share of facade columns repeating another column exactly (bays copied)"},
			{"furniture_per_cell, empty_room_ratio", "furniture blocks round room cells (feet and head height) per room cell; share of rooms "
				+ "with none"},
			{"culture_twin, twin_culture, culture_likes, shape_hash", "flat seed-0 rows only: the Jaccard likeness of the blueprint's filled "
				+ "cells to the nearest suited culture's (as cultureSimilarity), which culture, how many suited cultures are >= 0.8 alike "
				+ "(cultureSimilarity's threshold), and a hash of the shape"},
			{"where", "the first location of each kind of playability finding, in the plot's frame: x across the front from the left as "
				+ "seen from the street, y above the floor, z back from the front row (`-Preach=<path>,<culture>,<seed>,<facing>,<y>` prints "
				+ "that case's walk map)"},
			{"play_score, detail_score", "reviewer weights (Scores.java): playability starts at 100 and loses points per finding; detail "
				+ "averages ten 0-1 sub-scores (relief, flat patch, palette, windows, overhang, roof, symmetry, repetition, furniture, "
				+ "culture likes)"}};
		for (String[] c : cols) {
			md.add("| " + c[0] + " | " + c[1] + " |");
		}
		md.add("");
		md.add("Caveats: infrastructure archetypes (roads, bridges, docks, walls) need the planner's polylines and draw a stub on a bare plot; "
			+ "deep archetypes are drawn on the synthetic surface here, not in a cavern; Expanse blocks render as their vanilla stand-ins; "
			+ "castles are being fixed in parallel on branch hearth-w6-fix, so their rows describe the hearth branch as it is.");
		md.add("");
		Path top = out.resolve("top.txt");
		if (Files.exists(top)) {
			md.add("## Top problems found automatically");
			md.add("");
			md.add("Per metric: cases over the line (suited cultures), archetypes affected, and the five worst (archetype value (case)).");
			md.add("");
			for (String section : new String[]{"P", "D"}) {
				md.add(section.equals("P") ? "### Playability" : "### Detail and hand-built look");
				md.add("");
				for (String l : Files.readAllLines(top, StandardCharsets.UTF_8)) {
					String[] c = l.split("\\|", 6);
					if (c.length < 6 || !c[0].equals(section) || c[2].equals("0")) {
						continue;
					}
					md.add("- **" + c[1] + "**: " + c[2] + " of " + c[3] + " cases, " + c[4] + " archetypes. " + c[5]);
				}
				md.add("");
			}
		}
		md.add("## Archetypes by audit group");
		md.add("");
		md.add("Scores: play / detail / overall (means over suited cases). Sheets are relative to this file.");
		String group = null;
		for (StructureAudit.Entry e : entries) {
			if (!e.group().equals(group)) {
				group = e.group();
				md.add("");
				md.add("### " + group);
				md.add("");
				md.add("| archetype | own terrain | play | detail | overall | sheets |");
				md.add("|---|---|---|---|---|---|");
			}
			String[] s = scores.get(e.info().id().getPath());
			String sheet = sheet(out, e.category(), e.name());
			String cut = sheet(out, e.category(), e.name() + "_cut");
			String castle = e.info().id().getPath().contains("castle") ? " (castle: fixed on hearth-w6-fix)" : "";
			md.add("| " + e.info().id().getPath() + castle + " | " + e.terrain() + " | " + (s == null ? "" : s[7]) + " | " + (s == null ? "" : s[9])
				+ " | " + (s == null ? "" : s[10]) + " | " + (sheet == null ? "" : "[sheet](" + sheet + ")") + (cut == null ? "" : " [cut](" + cut + ")")
				+ " |");
		}
		md.add("");
		md.add(String.format(Locale.ROOT, "Written %s.", java.time.LocalDate.now()));
		StructureAudit.writeLines(out.resolve("INDEX.md"), md);
	}

	private static String sheet(Path out, String cat, String name) {
		for (String ext : new String[]{".webp", ".jpg"}) {
			if (Files.exists(out.resolve("sheets").resolve(cat).resolve(name + ext))) {
				return "sheets/" + cat + "/" + name + ext;
			}
		}
		return null;
	}
}
