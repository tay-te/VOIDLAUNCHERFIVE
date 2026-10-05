package dev.voidmc.atlas.core.waypoints;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** File helpers shared by the importers: careful listing and reading that skip what they cannot read. */
final class ImportFiles {
	/** Bigger files than this are not waypoint files, or not ones worth reading. */
	static final long MAX_FILE_BYTES = 8L << 20;

	private ImportFiles() {
	}

	/**
	 * The game directory to look in. Launchers lay instances out differently; when {@code dir} holds none of the
	 * folders the importers look for but has a {@code .minecraft} or {@code minecraft} folder inside (as MultiMC and
	 * Prism instances do), that inner folder is used.
	 */
	static Path gameDir(Path dir) {
		if (dir == null) {
			return null;
		}
		for (String known : new String[]{"xaero", "XaeroWaypoints", "journeymap"}) {
			if (Files.isDirectory(dir.resolve(known))) {
				return dir;
			}
		}
		for (String inner : new String[]{".minecraft", "minecraft"}) {
			Path p = dir.resolve(inner);
			if (Files.isDirectory(p)) {
				return p;
			}
		}
		return dir;
	}

	/** The entries of a directory matching {@code filter}, sorted by name; empty if it cannot be listed. */
	static List<Path> list(Path dir, Predicate<Path> filter, Consumer<String> warn) {
		if (dir == null || !Files.isDirectory(dir)) {
			return List.of();
		}
		List<Path> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir)) {
			s.filter(filter).forEach(out::add);
		} catch (IOException | RuntimeException e) {
			warn.accept("Could not list " + dir + ": " + e);
			return List.of();
		}
		Collections.sort(out);
		return out;
	}

	/** A file's text (bad UTF-8 replaced, not fatal), or null when it is missing, too big or unreadable. */
	static String read(Path file, Consumer<String> warn) {
		try {
			long size = Files.size(file);
			if (size > MAX_FILE_BYTES) {
				warn.accept("Skipped " + file + ": too big (" + size + " bytes)");
				return null;
			}
			String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			return text.startsWith("﻿") ? text.substring(1) : text;
		} catch (IOException | RuntimeException e) {
			warn.accept("Could not read " + file + ": " + e);
			return null;
		}
	}

	/** The list without repeats of the same waypoint, first copy kept. */
	static List<ImportedWaypoint> dedupe(List<ImportedWaypoint> all) {
		Map<String, ImportedWaypoint> seen = new LinkedHashMap<>();
		for (ImportedWaypoint w : all) {
			seen.putIfAbsent(w.dedupeKey(), w);
		}
		return new ArrayList<>(seen.values());
	}

	static boolean inWorld(long x, long z) {
		return Math.abs(x) <= CoordParser.XZ_LIMIT && Math.abs(z) <= CoordParser.XZ_LIMIT;
	}
}
