package dev.voidmc.reel.client;

import dev.voidmc.reel.shot.CameraState;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Has the server load (generating if need be) every chunk the camera will see, before the first frame: generating
 * land is the slow part of loading it, and a running world cannot wait for it mid-shot. Afterwards the chunks only
 * have to be sent, which keeps up with any camera. Chunks are visited in the order the camera reaches them, in
 * small batches so the server goes on ticking between them.
 */
final class Prefetch {
	private static final int BATCH = 24;

	private final AtomicInteger remaining = new AtomicInteger();
	private final int total;

	private Prefetch(int total) {
		this.total = total;
	}

	int remaining() {
		return this.remaining.get();
	}

	int total() {
		return this.total;
	}

	static Prefetch start(Minecraft mc, RenderJob job) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.level == null) {
			return new Prefetch(0);
		}
		int radius = mc.options.getEffectiveRenderDistance() + Director.SEND_AHEAD;
		LongLinkedOpenHashSet chunks = new LongLinkedOpenHashSet();
		int lastX = Integer.MIN_VALUE;
		int lastZ = Integer.MIN_VALUE;
		// the camera every frame of the job: every chunk within the render distance of where it will be
		for (int i = 0; i < job.frames().length; i++) {
			CameraState state = job.shot().camera(job.time(i, 0));
			int cx = (int) Math.floor(state.pos().x()) >> 4;
			int cz = (int) Math.floor(state.pos().z()) >> 4;
			if (cx == lastX && cz == lastZ) {
				continue;
			}
			lastX = cx;
			lastZ = cz;
			for (int r = 0; r <= radius; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
							chunks.add(ChunkPos.pack(cx + dx, cz + dz));
						}
					}
				}
			}
		}
		Prefetch prefetch = new Prefetch(chunks.size());
		prefetch.remaining.set(chunks.size());
		ServerLevel level = server.getLevel(mc.level.dimension());
		if (level == null) {
			prefetch.remaining.set(0);
			return prefetch;
		}
		LongArrayList order = new LongArrayList(chunks);
		for (int from = 0; from < order.size(); from += BATCH) {
			long[] batch = order.subList(from, Math.min(order.size(), from + BATCH)).toLongArray();
			server.execute(() -> {
				for (long packed : batch) {
					level.getChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed));
				}
				prefetch.remaining.addAndGet(-batch.length);
			});
		}
		return prefetch;
	}
}
