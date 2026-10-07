package dev.voidmc.gloam.season;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Rebuilds the chunk meshes around the camera so they take new tints, a few hundred sections a tick, nearest first:
 * only the sections around each column's surface (from the lowest open ground to the treetops), where all the grass
 * and leaves are, so a season step costs a fraction of what reloading the chunks does and never stalls a frame.
 * Marking a section only tells vanilla (or Sodium) it is out of date; they rebuild it on their own workers.
 */
final class Remesh {
	/** The marks are spread over about this many ticks. */
	private static final int SPREAD_TICKS = 10;

	private final LongArrayList queue = new LongArrayList();
	private int next;
	private int perTick;
	private long startedNanos;
	/** The last pass: how many sections it marked and how long it took to mark them all. */
	private int lastSections;
	private long lastNanos;
	/** The time spent working out which sections to mark, last pass. */
	private long lastPlanNanos;
	/** From the start of the last pass until vanilla's meshers had rebuilt everything marked. */
	private long lastBuiltNanos;
	private boolean waiting;

	/** Plans a pass over the loaded chunks around the camera (replacing any pass still going). */
	void start(Minecraft mc) {
		ClientLevel level = mc.level;
		this.queue.clear();
		this.next = 0;
		if (level == null) {
			return;
		}
		long t0 = System.nanoTime();
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		int cx = SectionPos.blockToSectionCoord(eye.x), cz = SectionPos.blockToSectionCoord(eye.z);
		int r = mc.options.getEffectiveRenderDistance() + 1;
		int minSection = level.getMinSectionY(), maxSection = level.getMaxSectionY();
		// ring by ring outward, so the nearest land turns first
		for (int ring = 0; ring <= r; ring++) {
			for (int dz = -ring; dz <= ring; dz++) {
				for (int dx = -ring; dx <= ring; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int x = cx + dx, z = cz + dz;
					if (!level.hasChunk(x, z)) {
						continue;
					}
					int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
					for (int s = 0; s < 5; s++) {
						int bx = (x << 4) + (s == 0 ? 8 : s < 3 ? 1 : 14), bz = (z << 4) + (s == 0 ? 8 : s % 2 == 1 ? 1 : 14);
						low = Math.min(low, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz));
						high = Math.max(high, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz));
					}
					// a little below the lowest ground (grass in a hollow between the samples) up past the crowns
					int from = Math.max(minSection, SectionPos.blockToSectionCoord(low - 6));
					int to = Math.min(maxSection, SectionPos.blockToSectionCoord(high + 2));
					for (int y = from; y <= to; y++) {
						this.queue.add(SectionPos.asLong(x, y, z));
					}
				}
			}
		}
		this.perTick = Math.max(64, (this.queue.size() + SPREAD_TICKS - 1) / SPREAD_TICKS);
		this.startedNanos = System.nanoTime();
		this.lastPlanNanos = this.startedNanos - t0;
	}

	/** Marks the next sections of the pass. */
	void tick(Minecraft mc) {
		if (this.next >= this.queue.size()) {
			return;
		}
		if (mc.level == null) {
			this.queue.clear();
			this.next = 0;
			return;
		}
		int end = Math.min(this.queue.size(), this.next + this.perTick);
		for (; this.next < end; this.next++) {
			long s = this.queue.getLong(this.next);
			mc.levelExtractor.setSectionDirty(SectionPos.x(s), SectionPos.y(s), SectionPos.z(s));
		}
		if (this.next >= this.queue.size()) {
			this.lastSections = this.queue.size();
			this.lastNanos = System.nanoTime() - this.startedNanos;
			this.waiting = true;
		}
	}

	/** Once the marks are done: notes when vanilla's meshers have caught up (Sodium keeps its own queue, not seen here). */
	void watch(Minecraft mc) {
		if (this.waiting && !this.busy() && mc.levelRenderer != null && mc.levelRenderer.hasRenderedAllSections()) {
			this.waiting = false;
			this.lastBuiltNanos = System.nanoTime() - this.startedNanos;
		}
	}

	/** Whether a pass is still marking sections. */
	boolean busy() {
		return this.next < this.queue.size();
	}

	String stats() {
		return String.format(java.util.Locale.ROOT, "last re-mesh %d sections, planned in %.2f ms, marked over %.0f ms, rebuilt by %.0f ms",
			this.lastSections, this.lastPlanNanos / 1e6, this.lastNanos / 1e6, this.lastBuiltNanos / 1e6);
	}
}
