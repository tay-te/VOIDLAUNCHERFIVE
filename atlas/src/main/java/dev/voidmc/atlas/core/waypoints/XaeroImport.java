package dev.voidmc.atlas.core.waypoints;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Reads Xaero's Minimap waypoint files so players can bring their waypoints into Atlas.
 *
 * <p>Where they live. Current versions keep them under {@code <game dir>/xaero/minimap/}, older ones under
 * {@code <game dir>/XaeroWaypoints/}. Both are laid out as {@code <world folder>/<dimension folder>/<file>.txt}: the
 * world folder names the save or server ({@code Multiplayer_mc.example.net}), the dimension folder is
 * {@code dim%0}, {@code dim%-1} or {@code dim%1} for the vanilla three (old dimension numbers) or
 * {@code dim%<namespace>$<path>} for others, and the files are named {@code mw<sub-world>_<n>.txt}
 * ({@code mw$default_1.txt} usually). Backup folders Xaero makes next to these are not read.
 *
 * <p>The file format. Lines starting with {@code #} are comments; {@code sets:} lines list the waypoint sets and are
 * skipped; waypoint lines are
 * {@code waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination}, with
 * fewer trailing fields in older versions (only up to {@code z} is required here). {@code y} may be {@code ~} for
 * unknown, {@code §§} in a name stands for {@code :}, the colour is an index into Minecraft's sixteen chat colours,
 * and type 1 is a death point (as are the names {@code gui.xaero_deathpoint} and {@code gui.xaero_deathpoint_old},
 * which are translation keys, shown here as "Death" and "Old death"). Lines that do not read, or whose position is
 * outside the world, are skipped; a file that cannot be read is skipped. Nothing here throws on bad input.
 *
 * <p>Not read: the single-file {@code xaerowaypoints.txt} from very old (2015-era) versions, and Xaero's World Map
 * data, which holds no waypoints.
 */
public final class XaeroImport {
	private static final Consumer<String> QUIET = s -> {
	};

	private XaeroImport() {
	}

	/** Every Xaero waypoint under the game directory, grouped by world folder (alphabetically) with repeats removed. */
	public static List<ImportedWaypoint> scan(Path gameDir) {
		return scan(gameDir, QUIET);
	}

	/** As {@link #scan(Path)}, telling {@code warn} about each folder, file or line it had to skip. */
	public static List<ImportedWaypoint> scan(Path gameDir, Consumer<String> warn) {
		Path dir = ImportFiles.gameDir(gameDir);
		List<ImportedWaypoint> all = new ArrayList<>();
		if (dir == null) {
			return all;
		}
		for (Path root : new Path[]{dir.resolve("xaero").resolve("minimap"), dir.resolve("XaeroWaypoints")}) {
			for (Path world : ImportFiles.list(root, Files::isDirectory, warn)) {
				for (Path dim : ImportFiles.list(world, Files::isDirectory, warn)) {
					String dimension = dimensionOf(dim.getFileName().toString());
					if (dimension == null) {
						if (dim.getFileName().toString().startsWith("dim%")) {
							warn.accept("Skipped " + dim + ": unknown dimension");
						}
						continue;
					}
					for (Path file : ImportFiles.list(dim, XaeroImport::isWaypointFile, warn)) {
						all.addAll(parseFile(file, dimension, warn));
					}
				}
			}
		}
		List<ImportedWaypoint> out = ImportFiles.dedupe(all);
		List<ImportedWaypoint> grouped = new ArrayList<>(out.size());
		ImportedWaypoint.byWorld(out).values().forEach(grouped::addAll);
		return grouped;
	}

	/**
	 * The waypoints in one Xaero file. The world is the name of the folder two levels up (the world folder), or empty
	 * when there is none. When {@code dimensionId} is null the dimension comes from the file's folder name.
	 */
	public static List<ImportedWaypoint> parseFile(Path file, String dimensionId) {
		return parseFile(file, dimensionId, QUIET);
	}

	/** As {@link #parseFile(Path, String)}, telling {@code warn} about what it skipped. */
	public static List<ImportedWaypoint> parseFile(Path file, String dimensionId, Consumer<String> warn) {
		Path dimFolder = file.getParent();
		Path worldFolder = dimFolder == null ? null : dimFolder.getParent();
		String world = worldFolder == null || worldFolder.getFileName() == null ? "" : worldFolder.getFileName().toString();
		String dimension = dimensionId;
		if (dimension == null && dimFolder != null && dimFolder.getFileName() != null) {
			dimension = dimensionOf(dimFolder.getFileName().toString());
		}
		if (dimension == null) {
			dimension = DimensionIds.OVERWORLD;
		}
		String text = ImportFiles.read(file, warn);
		if (text == null) {
			return new ArrayList<>();
		}
		return parse(text, world, dimension, file.toString(), warn);
	}

	/** The waypoints in the text of one Xaero file. {@code where} names the file in warnings. */
	public static List<ImportedWaypoint> parse(String text, String world, String dimensionId, String where, Consumer<String> warn) {
		List<ImportedWaypoint> out = new ArrayList<>();
		String[] lines = text.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].strip();
			if (line.isEmpty() || line.startsWith("#") || !line.startsWith("waypoint:")) {
				continue;
			}
			ImportedWaypoint w = parseLine(line, world, dimensionId);
			if (w == null) {
				warn.accept("Skipped " + where + " line " + (i + 1) + ": not a waypoint Atlas can read");
			} else {
				out.add(w);
			}
		}
		return out;
	}

	/** One {@code waypoint:} line, or null when it does not read. */
	static ImportedWaypoint parseLine(String line, String world, String dimensionId) {
		String[] f = line.split(":", -1);
		if (f.length < 6 || !f[0].equals("waypoint")) {
			return null;
		}
		Long x = whole(f[3]);
		Long z = whole(f[5]);
		if (x == null || z == null || !ImportFiles.inWorld(x, z)) {
			return null;
		}
		Integer y = null;
		String ys = f[4].strip();
		if (!ys.equals("~") && !ys.isEmpty()) {
			Long yl = whole(ys);
			if (yl == null) {
				return null;
			}
			if (Math.abs(yl) <= CoordParser.Y_LIMIT) {
				y = yl.intValue();
			}
		}
		String rawName = f[1].replace("§§", ":").strip();
		boolean death = rawName.startsWith("gui.xaero_deathpoint");
		String name = switch (rawName) {
			case "gui.xaero_deathpoint" -> "Death";
			case "gui.xaero_deathpoint_old" -> "Old death";
			default -> rawName;
		};
		int colour = AtlasColours.INK;
		if (f.length > 6) {
			Long c = whole(f[6]);
			if (c != null) {
				colour = AtlasColours.fromChatColour((int) Math.floorMod(c, 16L));
			}
		}
		boolean disabled = f.length > 7 && f[7].strip().equalsIgnoreCase("true");
		if (f.length > 8) {
			Long type = whole(f[8]);
			death |= type != null && type == 1;
		}
		return new ImportedWaypoint(ImportedWaypoint.Source.XAERO, world, name, x.intValue(), y, z.intValue(), dimensionId, colour, death, disabled);
	}

	/**
	 * The dimension id for one of Xaero's dimension folder names: {@code dim%0}, {@code dim%-1} and {@code dim%1} are
	 * the overworld, nether and end, and {@code dim%namespace$path} is {@code namespace:path}. Null for anything else.
	 */
	public static String dimensionOf(String folder) {
		if (folder == null || !folder.toLowerCase(Locale.ROOT).startsWith("dim%") || folder.length() <= 4) {
			return null;
		}
		String rest = folder.substring(4);
		if (rest.matches("-?\\d+")) {
			return switch (rest) {
				case "0" -> DimensionIds.OVERWORLD;
				case "-1" -> DimensionIds.NETHER;
				case "1" -> DimensionIds.END;
				default -> null;
			};
		}
		int dollar = rest.indexOf('$');
		if (dollar <= 0 || dollar == rest.length() - 1) {
			return null;
		}
		String id = rest.substring(0, dollar) + ":" + rest.substring(dollar + 1).replace('$', '/');
		return DimensionIds.normalise(id);
	}

	private static boolean isWaypointFile(Path p) {
		String name = p.getFileName().toString();
		return Files.isRegularFile(p) && name.startsWith("mw") && name.endsWith(".txt");
	}

	private static Long whole(String s) {
		try {
			return Long.parseLong(s.strip());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
