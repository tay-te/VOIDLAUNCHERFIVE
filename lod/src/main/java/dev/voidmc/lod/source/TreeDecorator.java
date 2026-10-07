package dev.voidmc.lod.source;

import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.Decorator;
import dev.voidmc.lod.core.Voxels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.LoggerFactory;

/**
 * The world's own trees for the close tiles ({@link TreePlacer} places them): painted with this client's textures
 * (the leaves in their biome's tint, a log's rings and bark), and placed on threads of their own, nearest the player
 * first, so a tile is built at once with drawn crowns and again with its trees once they are placed.
 */
public final class TreeDecorator implements Decorator {
	private static final int REACH = TreePlacer.REACH;

	/** Where the trees come from: placed here, or, on a server, asked of it. */
	public interface Trees {
		/** The chunk's trees if known (kept or received), else null. */
		TreePlacer.Placed known(int cx, int cz);

		/** Works the chunk's trees out (or asks for them), on the calling thread; null if they come later. */
		TreePlacer.Placed place(int cx, int cz, Ground ground);

		/** Keeps the columns of a tile's samples, for the trees placed near it. */
		void keep(int minX, int minZ, int maxX, int maxZ, Ground ground);

		/** Column (x, z) as kept or asked of {@code ground}. */
		void column(int x, int z, Ground ground, ColumnSample out);

		Holder<Biome> biome(int id);

		String stats();
	}

	private final Trees trees;
	private final TreePlacer placer;
	private final BiomeLooks looks;
	private final Materials materials;
	private final AtomicLong failures = new AtomicLong();

	private TreeDecorator(Trees trees, TreePlacer placer, BiomeLooks looks, Materials materials) {
		this.trees = trees;
		this.placer = placer;
		this.looks = looks;
		this.materials = materials;
	}

	/** Trees this game places itself (singleplayer, a LAN host); null if the generator's decoration cannot be read. */
	public static TreeDecorator of(ServerLevel level, TreePlacer.Biomes biomes, BiomeLooks looks, Materials materials) {
		TreePlacer placer = TreePlacer.of(level, biomes, materials);
		if (placer == null) {
			return null;
		}
		return new TreeDecorator(new Trees() {
			@Override
			public TreePlacer.Placed known(int cx, int cz) {
				return placer.known(cx, cz);
			}

			@Override
			public TreePlacer.Placed place(int cx, int cz, Ground ground) {
				return placer.placed(cx, cz, ground);
			}

			@Override
			public void keep(int minX, int minZ, int maxX, int maxZ, Ground ground) {
				placer.keep(minX, minZ, maxX, maxZ, ground);
			}

			@Override
			public void column(int x, int z, Ground ground, ColumnSample out) {
				placer.column(x, z, ground, out);
			}

			@Override
			public Holder<Biome> biome(int id) {
				return placer.biome(id);
			}

			@Override
			public String stats() {
				return placer.stats();
			}
		}, placer, looks, materials);
	}

	/** Trees from elsewhere (a server that places them): painted here all the same. */
	public static TreeDecorator from(Trees trees, BiomeLooks looks, Materials materials) {
		return new TreeDecorator(trees, null, looks, materials);
	}

	/** Whether {@code level} is the stand-in world trees are placed in, not a real one. */
	public static boolean isStandIn(Object level) {
		return TreePlacer.isStandIn(level);
	}

	/** Keeps the trees placed here in {@code dir}, for the world and game of {@code identity}. */
	public void keepIn(java.nio.file.Path dir, long identity) {
		if (this.placer != null) {
			this.placer.keepIn(dir, identity);
		}
	}

	@Override
	public boolean decorate(int minX, int minZ, int maxX, int maxZ, Ground ground, Voxels out) {
		this.trees.keep(minX - REACH, minZ - REACH, maxX + REACH, maxZ + REACH, ground);
		int cx0 = Math.floorDiv(minX - REACH, 16), cx1 = Math.floorDiv(maxX - 1 + REACH, 16);
		int cz0 = Math.floorDiv(minZ - REACH, 16), cz1 = Math.floorDiv(maxZ - 1 + REACH, 16);
		TreePlacer.Placed[] chunks = new TreePlacer.Placed[(cx1 - cx0 + 1) * (cz1 - cz0 + 1)];
		List<Long> missing = null;
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				TreePlacer.Placed chunk = this.trees.known(cx, cz);
				if (chunk == null) {
					if (missing == null) {
						missing = new ArrayList<>();
					}
					missing.add(TreePlacer.chunkKey(cx, cz));
				}
				chunks[(cx - cx0) + (cz - cz0) * (cx1 - cx0 + 1)] = chunk;
			}
		}
		if (missing != null) {
			this.request(minX, minZ, maxX, maxZ, missing);
			return false;
		}
		ColumnSample sample = new ColumnSample();
		for (TreePlacer.Placed chunk : chunks) {
			for (int b = 0; b < chunk.positions().length; b++) {
				long p = chunk.positions()[b];
				int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
				if (x < minX || x >= maxX || z < minZ || z >= maxZ) {
					continue;
				}
				BlockState state = chunk.states()[b];
				boolean leaves = state.is(BlockTags.LEAVES);
				if (!leaves && !state.is(BlockTags.LOGS)) {
					continue;
				}
				sample.clear();
				this.trees.column(x, z, ground, sample);
				Holder<Biome> biome = this.trees.biome(sample.biome);
				Block block = state.getBlock();
				int[] colors = biome == null ? null : this.looks.treeColors(biome, block);
				if (leaves) {
					int c = colors != null ? colors[0] : biome != null ? this.looks.get(biome).leaves() : 0x48752C;
					int m = this.materials.id(block.defaultBlockState(), true);
					out.add(x, y, z, Voxels.LEAVES, (byte) 0, c, c, m, m);
				} else {
					byte axis = 0;
					if (state.hasProperty(BlockStateProperties.AXIS)) {
						Direction.Axis a = state.getValue(BlockStateProperties.AXIS);
						axis = (byte) (a == Direction.Axis.X ? 1 : a == Direction.Axis.Z ? 2 : 0);
					}
					BlockState upright = block.defaultBlockState();
					int top = colors != null ? colors[0] : 0x9A7B4F;
					int side = colors != null ? colors[1] : 0x6B5232;
					out.add(x, y, z, Voxels.LOG, axis, top, side, this.materials.id(upright, true), this.materials.id(upright, false));
				}
			}
		}
		return true;
	}

	// ---- placing in the background ---------------------------------------------------------------------------

	/** A tile's area waiting for its chunks' trees: told ready once the last of them is placed. */
	private record Pending(String area, int minX, int minZ, int maxX, int maxZ, java.util.concurrent.atomic.AtomicInteger missing) {
	}

	private record Job(long key, double distance) {
	}

	private final java.util.concurrent.PriorityBlockingQueue<Job> queue = new java.util.concurrent.PriorityBlockingQueue<>(256,
		java.util.Comparator.comparingDouble(Job::distance));
	private final java.util.Set<Long> queued = ConcurrentHashMap.newKeySet();
	private final Map<Long, List<Pending>> waiting = new HashMap<>();
	private final Map<String, Pending> pendingAreas = new HashMap<>();
	private volatile Ready ready;
	private volatile Ground background;
	private volatile double centreX;
	private volatile double centreZ;
	private volatile boolean started;
	private static final int THREADS = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 4));

	@Override
	public void onReady(Ready ready) {
		this.ready = ready;
	}

	/** The ground trees placed in the background stand on: the terrain source, column by column. */
	public void ground(Ground ground) {
		this.background = ground;
	}

	/** Where the player is: the trees nearest them are placed first. */
	public void centre(double x, double z) {
		this.centreX = x;
		this.centreZ = z;
	}

	/** Chunks waiting to have their trees placed. */
	public int queued() {
		return this.queued.size();
	}

	/** A chunk's trees came from elsewhere (a server): the tiles waiting for it hear of it. */
	public void arrived(long key) {
		this.done(key);
	}

	private void request(int minX, int minZ, int maxX, int maxZ, List<Long> missing) {
		String area = minX + "," + minZ + "," + maxX + "," + maxZ;
		synchronized (this.waiting) {
			if (this.pendingAreas.containsKey(area)) {
				return;
			}
			Pending p = new Pending(area, minX, minZ, maxX, maxZ, new java.util.concurrent.atomic.AtomicInteger(missing.size()));
			this.pendingAreas.put(area, p);
			for (long key : missing) {
				this.waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
			}
		}
		for (long key : missing) {
			if (this.queued.add(key)) {
				int cx = (int) (key >> 32), cz = (int) key;
				double dx = cx * 16 + 8 - this.centreX, dz = cz * 16 + 8 - this.centreZ;
				this.queue.add(new Job(key, dx * dx + dz * dz));
			}
		}
		this.start();
	}

	private void start() {
		if (this.started) {
			return;
		}
		synchronized (this) {
			if (this.started) {
				return;
			}
			this.started = true;
			for (int i = 0; i < THREADS; i++) {
				Thread t = new Thread(this::work, "VOID LOD trees " + (i + 1));
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				t.start();
			}
		}
	}

	private void work() {
		while (true) {
			Job job;
			try {
				job = this.queue.take();
			} catch (InterruptedException e) {
				return;
			}
			long key = job.key();
			int cx = (int) (key >> 32), cz = (int) key;
			boolean now = true;
			try {
				Ground ground = this.background;
				if (this.trees.known(cx, cz) == null && ground != null) {
					// placed here, or asked of a server: then it is done when the answer comes (arrived)
					now = this.trees.place(cx, cz, ground) != null;
				}
			} catch (RuntimeException | LinkageError e) {
				long failed = this.failures.incrementAndGet();
				if (failed <= 3 || Long.bitCount(failed) == 1) {
					LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: could not place the trees of chunk {}, {} ({} so far)", cx, cz, failed, e);
				}
			} finally {
				this.queued.remove(key);
			}
			if (now) {
				this.done(key);
			}
		}
	}

	private void done(long key) {
		List<Pending> done;
		synchronized (this.waiting) {
			done = this.waiting.remove(key);
		}
		if (done == null) {
			return;
		}
		for (Pending p : done) {
			if (p.missing().decrementAndGet() == 0) {
				synchronized (this.waiting) {
					this.pendingAreas.remove(p.area());
				}
				Ready r = this.ready;
				if (r != null) {
					r.ready(p.minX(), p.minZ(), p.maxX(), p.maxZ());
				}
			}
		}
	}

	public String stats() {
		return "trees: " + this.queued.size() + " queued, " + this.trees.stats();
	}
}
