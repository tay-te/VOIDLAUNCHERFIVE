package dev.voidmc.atlas.core.under;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cave biomes the player has seen underground ("Dripstone Caves", "Lush Caves", "Deep Dark"), by 16-block cell and
 * 8-block band, joined into areas per band so each can carry its name on the underground map. Only what the
 * underground reveal saw is ever put here: no x-ray. Thread-safe.
 */
public final class CaveAreas {
	public static final int CELL = 16;
	/** The fewest cells an area needs to be named (three 16-block cells: a real cave, not a corner of one). */
	public static final int MIN_CELLS = 3;

	/** A named cave area in one band: its biome, size in cells, and the label point (the member cell nearest its middle). */
	public record Area(int band, String biome, int cells, int x, int z) {
		/** A stable id: the band, the biome and the label's cell. */
		public String id() {
			return "atlas:cave@" + this.band + "@" + this.biome + "@" + Math.floorDiv(this.x, CELL) + "," + Math.floorDiv(this.z, CELL);
		}
	}

	private final Map<Long, String> cells = new HashMap<>();

	static long key(int band, int cx, int cz) {
		return (long) (band + 128 & 0xFF) << 48 | (long) (cx & 0xFFFFFF) << 24 | cz & 0xFFFFFF;
	}

	private static int band(long key) {
		return (int) (key >>> 48 & 0xFF) - 128;
	}

	private static int cx(long key) {
		return (int) (key >>> 24 & 0xFFFFFF) << 8 >> 8;
	}

	private static int cz(long key) {
		return (int) (key & 0xFFFFFF) << 8 >> 8;
	}

	/** Notes a cave biome seen at a block in a band; true if that cell was not known as that biome yet. */
	public synchronized boolean put(int band, int x, int z, String biome) {
		long k = key(band, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		return !biome.equals(this.cells.put(k, biome));
	}

	public synchronized int size() {
		return this.cells.size();
	}

	/** The areas: cells of one biome in one band joined with their four neighbours, the biggest first. */
	public synchronized List<Area> areas() {
		List<Area> out = new ArrayList<>();
		Map<Long, Boolean> seen = new HashMap<>();
		List<Long> keys = new ArrayList<>(this.cells.keySet());
		keys.sort(null);
		for (long start : keys) {
			if (seen.containsKey(start)) {
				continue;
			}
			String biome = this.cells.get(start);
			int band = band(start);
			List<Long> members = new ArrayList<>();
			ArrayList<Long> stack = new ArrayList<>();
			stack.add(start);
			seen.put(start, true);
			while (!stack.isEmpty()) {
				long k = stack.remove(stack.size() - 1);
				members.add(k);
				int cx = cx(k);
				int cz = cz(k);
				for (long n : new long[]{key(band, cx + 1, cz), key(band, cx - 1, cz), key(band, cx, cz + 1), key(band, cx, cz - 1)}) {
					if (!seen.containsKey(n) && biome.equals(this.cells.get(n))) {
						seen.put(n, true);
						stack.add(n);
					}
				}
			}
			if (members.size() < MIN_CELLS) {
				continue;
			}
			double mx = 0;
			double mz = 0;
			for (long k : members) {
				mx += cx(k);
				mz += cz(k);
			}
			mx /= members.size();
			mz /= members.size();
			long best = members.get(0);
			double bestD = Double.MAX_VALUE;
			for (long k : members) {
				double d = Math.hypot(cx(k) - mx, cz(k) - mz);
				if (d < bestD || d == bestD && k < best) {
					bestD = d;
					best = k;
				}
			}
			out.add(new Area(band, biome, members.size(), cx(best) * CELL + CELL / 2, cz(best) * CELL + CELL / 2));
		}
		out.sort(Comparator.comparingInt((Area a) -> -a.cells()).thenComparing(Area::id));
		return out;
	}

	/** One line per cell: band, cell x, cell z, biome. */
	public synchronized String save() {
		StringBuilder b = new StringBuilder();
		List<Long> keys = new ArrayList<>(this.cells.keySet());
		keys.sort(null);
		for (long k : keys) {
			b.append(band(k)).append(' ').append(cx(k)).append(' ').append(cz(k)).append(' ').append(this.cells.get(k)).append('\n');
		}
		return b.toString();
	}

	/** Reads what {@link #save} wrote; lines it cannot read are skipped. */
	public synchronized void load(String text) {
		for (String line : text.split("\n")) {
			String[] p = line.strip().split(" ");
			if (p.length != 4) {
				continue;
			}
			try {
				this.cells.put(key(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), p[3]);
			} catch (NumberFormatException ignored) {
				// a damaged line
			}
		}
	}
}
