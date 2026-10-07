package dev.voidmc.atlas.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small ids for names (block ids like {@code minecraft:stone}, biome ids), stable for one world's map: region files
 * store the ids, this file the names, so a map survives mods being added or removed (an unknown name keeps its id
 * and simply has no colour). Id 0 is reserved for "none". Thread-safe; ids only ever grow.
 */
public final class NamePalette {
	public static final int NONE = 0;
	public static final int MAX = Character.MAX_VALUE;

	private final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();
	private final List<String> names = new ArrayList<>();
	private volatile boolean dirty;

	public NamePalette() {
		this.names.add("");
	}

	/** The id of a name, made if it is new (NONE once the palette is full). */
	public int id(String name) {
		Integer id = this.ids.get(name);
		if (id != null) {
			return id;
		}
		synchronized (this.names) {
			id = this.ids.get(name);
			if (id != null) {
				return id;
			}
			if (this.names.size() > MAX) {
				return NONE;
			}
			int next = this.names.size();
			this.names.add(name);
			this.ids.put(name, next);
			this.dirty = true;
			return next;
		}
	}

	/** The name of an id, or null. */
	public String name(int id) {
		synchronized (this.names) {
			return id > 0 && id < this.names.size() ? this.names.get(id) : null;
		}
	}

	public int size() {
		synchronized (this.names) {
			return this.names.size();
		}
	}

	public boolean dirty() {
		return this.dirty;
	}

	/**
	 * One name per line, in id order (line n is id n + 1). Lines end in {@code \n} on every system, so a map folder
	 * reads the same wherever it was written ({@link #load} accepts {@code \r\n} and {@code \r} as well).
	 */
	public void save(Path file) throws IOException {
		List<String> copy;
		synchronized (this.names) {
			copy = new ArrayList<>(this.names.subList(1, this.names.size()));
			this.dirty = false;
		}
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		StringBuilder text = new StringBuilder();
		for (String name : copy) {
			text.append(name).append('\n');
		}
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		dev.voidmc.atlas.core.store.FileMoves.replace(tmp, file);
	}

	public static NamePalette load(Path file) throws IOException {
		NamePalette palette = new NamePalette();
		if (Files.isRegularFile(file)) {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				// a blank line keeps its place, so later ids keep theirs
				palette.names.add(line);
				if (!line.isEmpty()) {
					palette.ids.putIfAbsent(line, palette.names.size() - 1);
				}
			}
		}
		return palette;
	}
}
