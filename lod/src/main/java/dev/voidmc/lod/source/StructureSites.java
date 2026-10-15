package dev.voidmc.lod.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.LoggerFactory;

/**
 * Where the world's structures will stand on land nobody has loaded, worked out the way the game does it, without a
 * chunk: each structure set's own placement grid gives the candidate chunks, the set's weighted pick and the structure's
 * own site checks decide, and the structure is assembled to know its pieces (a village's houses, a lighthouse's
 * tower). Only their bounding boxes are kept: what the far view needs to stand them on the land, a box each.
 *
 * <p>Singleplayer and a LAN host only: it needs the server's generator. Worked out per {@value #REGION}-block region,
 * on a thread of its own at minimum priority, the first time a tile asks there; tiles over a region are built again
 * once its structures are known ({@link #onKnown}).
 */
public final class StructureSites {
	/** Blocks a region side: 32 chunks. */
	static final int REGION = 512;
	/** Past this far from where the player joined, only sets of big structures are worked out: a hut is nothing at 4 km. */
	private static final int SMALL_REACH = 1536;

	/** A piece of a structure: a box of the world, and the structure it belongs to (for its look). */
	public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int look) {
	}

	/** Regions asked for and not yet worked out, by key: the thread takes the one nearest the player first. */
	private final java.util.HashMap<Long, CompletableFuture<List<Box>>> pending = new java.util.HashMap<>();
	private Thread worker;

	private final ServerLevel level;
	private final ChunkGeneratorStructureState state;
	private final ChunkGenerator generator;
	private final BiomeSource biomes;
	private final RandomState random;
	private final StructureTemplateManager templates;
	private final RegistryAccess registries;
	private final long seed;
	private final ConcurrentHashMap<Long, CompletableFuture<List<Box>>> regions = new ConcurrentHashMap<>();
	private volatile boolean closed;
	/** The climate sampler of the region being worked out (the structure thread's own). */
	private net.minecraft.world.level.biome.Climate.Sampler climate;
	private volatile double centreX;
	private volatile double centreZ;
	public final AtomicLong starts = new AtomicLong();
	public final AtomicLong regionNanos = new AtomicLong();
	public final AtomicLong regionsDone = new AtomicLong();

	public StructureSites(ServerLevel level) {
		this.level = level;
		this.state = level.getChunkSource().getGeneratorState();
		this.generator = level.getChunkSource().getGenerator();
		this.biomes = this.generator.getBiomeSource();
		this.random = level.getChunkSource().randomState();
		this.templates = level.getServer().getStructureTemplateManager();
		this.registries = level.registryAccess();
		this.seed = level.getSeed();
	}

	/** The world's sea level: pieces far under it are never seen from above. */
	public int seaLevel() {
		return this.generator.getSeaLevel();
	}

	/** Where the player is, about: small structures are worked out only near it. */
	public void centre(double x, double z) {
		this.centreX = x;
		this.centreZ = z;
	}

	public void close() {
		this.closed = true;
		synchronized (this.pending) {
			for (CompletableFuture<List<Box>> f : this.pending.values()) {
				f.complete(List.of());
			}
			this.pending.clear();
			this.pending.notifyAll();
		}
	}

	static long key(int rx, int rz) {
		return (long) rx << 32 | rz & 0xFFFFFFFFL;
	}

	/** The region holding block x (or z). */
	static int region(int block) {
		return Math.floorDiv(block, REGION);
	}

	/**
	 * The boxes of the structures in region (rx, rz) if they are known, else null, and asks for them: a tile built before
	 * then goes without, and is built again when they are ({@link #onKnown}).
	 */
	public List<Box> boxes(int rx, int rz) {
		if (this.closed) {
			return List.of();
		}
		CompletableFuture<List<Box>> f = this.regions.get(key(rx, rz));
		if (f == null) {
			CompletableFuture<List<Box>> mine = new CompletableFuture<>();
			f = this.regions.putIfAbsent(key(rx, rz), mine);
			if (f == null) {
				f = mine;
				synchronized (this.pending) {
					this.pending.put(key(rx, rz), mine);
					if (this.worker == null) {
						this.worker = new Thread(this::work, "VOID LOD structures");
						this.worker.setDaemon(true);
						this.worker.setPriority(Thread.MIN_PRIORITY);
						this.worker.start();
					}
					this.pending.notifyAll();
				}
			}
		}
		return f.getNow(null);
	}

	/** Works out the pending region nearest the player, one after another, until closed. */
	private void work() {
		while (!this.closed) {
			long key;
			CompletableFuture<List<Box>> future;
			synchronized (this.pending) {
				while (this.pending.isEmpty() && !this.closed) {
					try {
						this.pending.wait(1000);
					} catch (InterruptedException e) {
						return;
					}
				}
				if (this.closed) {
					return;
				}
				long best = 0;
				double bestD = Double.MAX_VALUE;
				for (long k : this.pending.keySet()) {
					double dx = ((int) (k >> 32) + 0.5) * REGION - this.centreX;
					double dz = ((int) k + 0.5) * REGION - this.centreZ;
					double d = dx * dx + dz * dz;
					if (d < bestD) {
						bestD = d;
						best = k;
					}
				}
				key = best;
				future = this.pending.remove(best);
			}
			int rx = (int) (key >> 32), rz = (int) key;
			List<Box> found;
			try {
				found = this.find(rx, rz);
			} catch (RuntimeException e) {
				LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: could not work out the structures of region {}, {}", rx, rz, e);
				found = List.of();
			}
			future.complete(found);
			if (!found.isEmpty() && !this.closed) {
				this.notify(rx, rz);
			}
		}
	}

	/** Whether the structures of the region holding (x, z) are known. */
	public boolean known(int x, int z) {
		CompletableFuture<List<Box>> f = this.regions.get(key(region(x), region(z)));
		return f != null && f.isDone();
	}

	/** Regions waiting to be worked out. */
	public int queued() {
		synchronized (this.pending) {
			return this.pending.size();
		}
	}

	/** The listener for regions whose structures came in: the engine's invalidation, given block bounds. */
	public interface Invalidate {
		void invalidate(int minX, int minZ, int maxX, int maxZ);
	}

	private volatile Invalidate invalidate;

	public void onKnown(Invalidate invalidate) {
		this.invalidate = invalidate;
	}

	private void notify(int rx, int rz) {
		Invalidate i = this.invalidate;
		if (i != null) {
			i.invalidate(rx * REGION, rz * REGION, (rx + 1) * REGION, (rz + 1) * REGION);
		}
	}

	/** The structures that would start in region (rx, rz), as the game would place them. */
	private List<Box> find(int rx, int rz) {
		long t0 = System.nanoTime();
		// as the game's own structure step makes one: per batch, on this one thread
		this.climate = this.random.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build());
		List<Box> out = new ArrayList<>();
		int cx0 = rx * (REGION >> 4), cz0 = rz * (REGION >> 4), cx1 = cx0 + (REGION >> 4), cz1 = cz0 + (REGION >> 4);
		double dx = (rx + 0.5) * REGION - this.centreX, dz = (rz + 0.5) * REGION - this.centreZ;
		boolean near = dx * dx + dz * dz < (double) SMALL_REACH * SMALL_REACH;
		for (Holder<StructureSet> holder : this.state.possibleStructureSets()) {
			StructureSet set = holder.value();
			if (!(set.placement() instanceof RandomSpreadStructurePlacement grid)) {
				continue;
			}
			if (!near && grid.spacing() < 20) {
				// a set of small things, densely spread: only near the player
				continue;
			}
			int spacing = grid.spacing();
			for (int gx = Math.floorDiv(cx0, spacing); gx <= Math.floorDiv(cx1 - 1, spacing); gx++) {
				for (int gz = Math.floorDiv(cz0, spacing); gz <= Math.floorDiv(cz1 - 1, spacing); gz++) {
					ChunkPos c = grid.getPotentialStructureChunk(this.seed, gx * spacing, gz * spacing);
					if (c.x() < cx0 || c.x() >= cx1 || c.z() < cz0 || c.z() >= cz1 || !grid.isStructureChunk(this.state, c.x(), c.z())) {
						continue;
					}
					StructureStart start = this.choose(set, c);
					if (start != null && !settlesOnPlacement(start.getStructure())) {
						this.starts.incrementAndGet();
						var name = this.registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(start.getStructure());
						int look = name == null ? 0 : Looks.of(name.getPath());
						for (StructurePiece piece : start.getPieces()) {
							BoundingBox b = piece.getBoundingBox();
							out.add(new Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), look));
						}
					}
				}
			}
		}
		this.regionNanos.addAndGet(System.nanoTime() - t0);
		this.regionsDone.incrementAndGet();
		return out;
	}

	/** The set's structure at chunk {@code c}, picked and tried as the game does (ChunkGenerator.createStructures). */
	private StructureStart choose(StructureSet set, ChunkPos c) {
		List<StructureSet.StructureSelectionEntry> entries = set.structures();
		if (entries.size() == 1) {
			return this.tryStart(entries.get(0), c);
		}
		List<StructureSet.StructureSelectionEntry> left = new ArrayList<>(entries);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(this.state.getLevelSeed(), c.x(), c.z());
		int total = 0;
		for (StructureSet.StructureSelectionEntry e : left) {
			total += e.weight();
		}
		while (!left.isEmpty()) {
			int pick = random.nextInt(total);
			int at = 0;
			for (StructureSet.StructureSelectionEntry e : left) {
				pick -= e.weight();
				if (pick < 0) {
					break;
				}
				at++;
			}
			StructureSet.StructureSelectionEntry entry = left.get(at);
			StructureStart start = this.tryStart(entry, c);
			if (start != null) {
				return start;
			}
			left.remove(at);
			total -= entry.weight();
		}
		return null;
	}

	/**
	 * Pieces whose height is only found when the chunk is decorated (ocean ruins and shipwrecks on the seabed, buried
	 * treasure, ruined portals sunk or raised to the ground): at generation they float at a placeholder height, so they
	 * are left out. They are small, and mostly hidden under water or ground anyway.
	 */
	private static boolean settlesOnPlacement(net.minecraft.world.level.levelgen.structure.Structure structure) {
		var type = structure.type();
		return type == net.minecraft.world.level.levelgen.structure.StructureType.OCEAN_RUIN
			|| type == net.minecraft.world.level.levelgen.structure.StructureType.SHIPWRECK
			|| type == net.minecraft.world.level.levelgen.structure.StructureType.BURIED_TREASURE
			|| type == net.minecraft.world.level.levelgen.structure.StructureType.RUINED_PORTAL;
	}

	private StructureStart tryStart(StructureSet.StructureSelectionEntry entry, ChunkPos c) {
		Holder<Structure> structure = entry.structure();
		StructureStart start = structure.value().generate(structure, this.level.dimension(), this.registries, this.generator, this.biomes,
			this.climate, this.random, this.templates, this.seed, c, 0, this.level, structure.value().biomes()::contains);
		return start.isValid() ? start : null;
	}

	public String stats() {
		long n = this.regionsDone.get();
		return String.format(Locale.ROOT, "structures: %d regions (%d queued), %d starts, %.0f ms a region", n, this.queued(), this.starts.get(),
			n == 0 ? 0 : this.regionNanos.get() / 1e6 / n);
	}

	/** What a structure looks like from afar, by its name: walls, roof and whether it is lived in (lit at night). */
	public static final class Looks {
		public static final int[] WALL = {0xB08A5A, 0x9A9A9A, 0xE8E2D4, 0x7A5A3A, 0xC9A66B, 0x6E6E6E};
		public static final int[] ROOF = {0x5A3A22, 0x6B6B6B, 0xA03A2A, 0x3A2A1A, 0x8A5A3A, 0x4A4A4A};
		public static final boolean[] LIT = {true, true, true, true, true, false};

		private Looks() {
		}

		/** 0 timber village, 1 stone (monastery, pagoda), 2 white (lighthouse, manor), 3 dark timber (cold), 4 sandstone, 5 ruin. */
		static int of(String name) {
			if (name.contains("ruin") || name.contains("watchtower") || name.contains("circle") || name.contains("cairn")
				|| name.contains("colossus") || name.contains("graveyard")) {
				return 5;
			}
			if (name.contains("lighthouse") || name.contains("manor") || name.contains("wisteria")) {
				return 2;
			}
			if (name.contains("monastery") || name.contains("pagoda") || name.contains("karst") || name.contains("outpost")) {
				return 1;
			}
			if (name.contains("tundra") || name.contains("snow") || name.contains("frost") || name.contains("redwood") || name.contains("taiga")) {
				return 3;
			}
			if (name.contains("desert") || name.contains("steppe") || name.contains("caravanserai") || name.contains("sand")
				|| name.contains("nomad") || name.contains("sun_")) {
				return 4;
			}
			return 0;
		}
	}
}
