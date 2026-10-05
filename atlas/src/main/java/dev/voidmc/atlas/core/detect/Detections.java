package dev.voidmc.atlas.core.detect;

import dev.voidmc.atlas.core.Coords;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Weighs what several chunks hold together, for the structures one chunk cannot show alone. A village: a bell with at
 * least two beds (four bed blocks) and a job site within {@value #VILLAGE_REACH} blocks of it. A bell whose neighbours have not arrived
 * yet waits, and is judged again as they do. One per dimension; thread-safe.
 */
public final class Detections {
	/** How far round a bell its village's beds and job sites are looked for. */
	public static final int VILLAGE_REACH = 48;
	private static final int WINDOW = (VILLAGE_REACH >> 4) + 1;
	private static final int MAX_TALLIES = 1 << 15;
	private static final int MAX_PENDING = 256;

	private final Map<Long, StructureScan.Tally> tallies = new HashMap<>();
	/** Bells not yet part of a village: x, y, z, underground (0/1). */
	private final List<int[]> pending = new ArrayList<>();

	/** A chunk was scanned: records its tally and hands on any village it completes. */
	public synchronized void chunk(int cx, int cz, StructureScan.Tally tally, Consumer<Sighting> out) {
		long key = Coords.pack(cx, cz);
		if (tally.isEmpty()) {
			this.tallies.remove(key);
		} else {
			if (this.tallies.size() >= MAX_TALLIES) {
				this.tallies.clear();
			}
			this.tallies.put(key, tally);
			int[] b = tally.bells();
			for (int i = 0; i + 3 < b.length; i += 4) {
				this.addPending(b[i], b[i + 1], b[i + 2], b[i + 3]);
			}
		}
		// judge every waiting bell this chunk is in reach of
		List<int[]> villages = new ArrayList<>();
		for (int[] bell : this.pending) {
			int bcx = bell[0] >> 4;
			int bcz = bell[2] >> 4;
			if (Math.abs(bcx - cx) > WINDOW || Math.abs(bcz - cz) > WINDOW) {
				continue;
			}
			boolean near = false;
			for (int[] v : villages) {
				near |= Math.hypot(v[0] - bell[0], v[2] - bell[2]) <= StructureKind.VILLAGE.mergeRadius;
			}
			if (!near && this.isVillage(bell[0], bell[2])) {
				villages.add(bell);
				out.accept(new Sighting(StructureKind.VILLAGE, bell[0], bell[1], bell[2], bell[3] == 1));
			}
		}
		// a village's other bells are part of it
		for (int[] v : villages) {
			this.pending.removeIf(o -> Math.hypot(o[0] - v[0], o[2] - v[2]) <= StructureKind.VILLAGE.mergeRadius);
		}
	}

	private void addPending(int x, int y, int z, int under) {
		for (int[] p : this.pending) {
			if (p[0] == x && p[1] == y && p[2] == z) {
				return;
			}
		}
		if (this.pending.size() >= MAX_PENDING) {
			this.pending.remove(0);
		}
		this.pending.add(new int[]{x, y, z, under});
	}

	/** Whether the chunks round a bell hold a village's beds and work. */
	boolean isVillage(int x, int z) {
		int beds = 0;
		int jobs = 0;
		int bcx = x >> 4;
		int bcz = z >> 4;
		for (int dz = -WINDOW; dz <= WINDOW; dz++) {
			for (int dx = -WINDOW; dx <= WINDOW; dx++) {
				StructureScan.Tally t = this.tallies.get(Coords.pack(bcx + dx, bcz + dz));
				if (t == null) {
					continue;
				}
				// a chunk counts if any of it is within reach
				int nearestX = Math.max((bcx + dx) << 4, Math.min(x, ((bcx + dx) << 4) + 15));
				int nearestZ = Math.max((bcz + dz) << 4, Math.min(z, ((bcz + dz) << 4) + 15));
				if (Math.hypot(nearestX - x, nearestZ - z) > VILLAGE_REACH) {
					continue;
				}
				beds += t.beds();
				jobs += t.jobs();
			}
		}
		// a bed is two blocks: two beds (and somewhere to work) make a village, one is a player's camp
		return beds >= 4 && jobs >= 1;
	}

	public synchronized int pendingBells() {
		return this.pending.size();
	}

	public synchronized void clear() {
		this.tallies.clear();
		this.pending.clear();
	}
}
