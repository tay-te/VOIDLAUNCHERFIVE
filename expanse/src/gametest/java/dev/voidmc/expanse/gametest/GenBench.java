package dev.voidmc.expanse.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * A repeatable chunk-generation benchmark, the client game test's other mode ({@code -Dexpanse.genbench=true}):
 * a fresh world on the same seed every time, ticking frozen, and then
 *
 * <ul>
 *   <li>the timing: three fixed areas of 12 x 12 chunks (lowland, mountains, coast) generated to FULL, all of
 *   an area's chunks asked for at once so the worldgen workers are as busy as they are when a player flies,
 *   each area timed by the wall clock;</li>
 *   <li>the checks ({@link Check}), run on each area once all the timing is done. The first hashes the blocks
 *   of 8 x 8 chunks beside each area, brought only as far as {@link ChunkStatus#TERRAIN} (in 26.3 the one
 *   step that is noise, surface and carvers, and the Earth's rivers and rock after them): that step writes
 *   only its own chunk and reads only biomes and structure starts, so unlike FULL, where neighbouring
 *   features write into each other in whichever order the workers finish, it comes out the same every run.
 *   The hashes go to build/genbench/hashes.txt, one {@code chunkX chunkZ hash} line per chunk; a change
 *   meant only to speed generation up must leave the file as it was.</li>
 *   <li>the second check ({@link BaseHeights}) holds the Earth generator's fast base heights up against the noise
 *   generator's own, for every heightmap type, and times both.</li>
 * </ul>
 *
 * Every line it prints starts {@code [genbench]}. Run it through ./gradlew runClientGameTest with
 * {@code JAVA_TOOL_OPTIONS=-Dexpanse.genbench=true}.
 */
final class GenBench {
	private static final String SEED = "8675309";
	/** The timed areas' side, in chunks; even, so an area centred on a block is that block's chunk and the five round it. */
	private static final int SIDE = 12;
	/** The hashed areas' side, in chunks. */
	private static final int HASH_SIDE = 8;
	/**
	 * How far east of its timed area's centre a hashed area's centre lies, in chunks. A FULL chunk's
	 * neighbours run their features, which write a chunk further out again; so the hashed chunks keep three
	 * chunks clear of the timed ones, where nothing has gone past TERRAIN.
	 */
	private static final int HASH_OFFSET = SIDE / 2 + 3 + HASH_SIDE / 2;
	/** Generated first and not counted, so the JIT has warmed up before the first timed area. */
	private static final Area WARM_UP = new Area("warm-up", -3000, -3000);
	private static final List<Area> AREAS = List.of(
		new Area("lowland", 0, 0),
		new Area("mountains", 4197, 1299),
		new Area("coast", 1899, 930));

	/** One of the benchmark's areas: its name and the block its timed chunks centre on. */
	record Area(String name, int x, int z) {
		ChunkPos centre() {
			return new ChunkPos(this.x >> 4, this.z >> 4);
		}

		/** The square of {@code side} chunks round the centre, west to east within north to south. */
		List<ChunkPos> chunks(int side, int eastOffset) {
			ChunkPos c = this.centre();
			List<ChunkPos> out = new ArrayList<>(side * side);
			for (int dz = -side / 2; dz < side - side / 2; dz++) {
				for (int dx = -side / 2; dx < side - side / 2; dx++) {
					out.add(new ChunkPos(c.x() + eastOffset + dx, c.z() + dz));
				}
			}
			return out;
		}
	}

	/**
	 * Something the benchmark checks on each area after the timing (so its own generation is not timed): it
	 * runs on the server thread and prints its own [genbench] lines. A further check is one more entry in
	 * {@link #checks}; one that compares the generator's answers with the generated chunks can bring its own
	 * chunks to the status it needs with {@code level.getChunk(x, z, status, true)}.
	 */
	interface Check {
		void area(ServerLevel level, Area area);

		/** Once every area is done: where a check writes its file or prints its summary. */
		default void finish() {
		}
	}

	private static List<Check> checks() {
		return List.of(new TerrainHashes(Path.of(System.getProperty("expanse.genbench.dir", "../../genbench"))), new BaseHeights());
	}

	private GenBench() {
	}

	static boolean enabled() {
		return Boolean.getBoolean("expanse.genbench");
	}

	static void run(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			// the fewest chunks round the player, so the client's own loading and meshing hardly compete
			mc.options.renderDistance().set(2);
		});
		// the world made as the tour makes it, so the same world
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(SEED);
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamemode spectator @a");
			// no scheduled or random ticks from here on: nothing changes a block but generation
			world.getServer().runCommand("tick freeze");
			context.waitTicks(20);
			world.getServer().runOnServer(server -> System.out.println("[genbench] seed " + SEED + ", player at "
				+ server.getPlayerList().getPlayers().getFirst().blockPosition().toShortString() + ", "
				+ Runtime.getRuntime().availableProcessors() + " processors, generator "
				+ server.overworld().getChunkSource().getGenerator().getClass().getSimpleName()));

			time(world, WARM_UP, 8);
			long chunks = 0;
			long nanos = 0;
			for (Area area : AREAS) {
				long[] t = time(world, area, SIDE);
				chunks += t[0];
				nanos += t[1];
			}
			System.out.println(String.format(java.util.Locale.ROOT, "[genbench] total: %d chunks to FULL in %.2f s, %.1f chunks/s",
				chunks, nanos / 1e9, chunks / (nanos / 1e9)));

			List<Check> checks = checks();
			for (Area area : AREAS) {
				world.getServer().runOnServer(server -> {
					for (Check check : checks) {
						check.area(server.overworld(), area);
					}
				});
			}
			checks.forEach(Check::finish);
			System.out.println("[genbench] done");
		}
	}

	/**
	 * Generates an area to FULL and prints how long it took. Every chunk gets its ticket first (a loading
	 * ticket that never times out), so the workers have the whole area to share out; then asking for each
	 * chunk in turn only waits, while the server thread runs the tasks the workers hand back.
	 *
	 * @return the chunks generated and the nanoseconds it took
	 */
	private static long[] time(TestSingleplayerContext world, Area area, int side) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			var source = level.getChunkSource();
			List<ChunkPos> chunks = area.chunks(side, 0);
			int loaded = 0;
			for (ChunkPos c : chunks) {
				if (source.getChunkNow(c.x(), c.z()) != null) {
					loaded++;
				}
			}
			long start = System.nanoTime();
			List<CompletableFuture<?>> futures = new ArrayList<>(chunks.size());
			for (ChunkPos c : chunks) {
				futures.add(source.addTicketAndLoadWithRadius(TicketType.PLAYER_LOADING, c, 0));
			}
			for (ChunkPos c : chunks) {
				level.getChunk(c.x(), c.z());
			}
			long nanos = System.nanoTime() - start;
			if (!futures.stream().allMatch(CompletableFuture::isDone)) {
				System.out.println("[genbench] " + area.name() + ": some chunks not done after the wait");
			}
			System.out.println(String.format(java.util.Locale.ROOT, "[genbench] %s (%d, %d): %d chunks to FULL in %.2f s, %.1f chunks/s%s",
				area.name(), area.x(), area.z(), chunks.size(), nanos / 1e9, chunks.size() / (nanos / 1e9),
				loaded == 0 ? "" : " (" + loaded + " already loaded)"));
			return new long[]{chunks.size(), nanos};
		});
	}

	/**
	 * The block-state hash of 8 x 8 chunks beside each area, at TERRAIN: every block of every section, by its
	 * id in the block-state registry, bottom section first and then y, z, x within it, folded into one long.
	 */
	private static final class TerrainHashes implements Check {
		private final Path dir;
		private final List<String> lines = new ArrayList<>();

		TerrainHashes(Path dir) {
			this.dir = dir.toAbsolutePath().normalize();
		}

		@Override
		public void area(ServerLevel level, Area area) {
			long start = System.nanoTime();
			long all = 0;
			int beyond = 0;
			for (ChunkPos c : area.chunks(HASH_SIDE, HASH_OFFSET)) {
				ChunkAccess chunk = level.getChunk(c.x(), c.z(), ChunkStatus.TERRAIN, true);
				if (chunk.getPersistedStatus() != ChunkStatus.TERRAIN) {
					beyond++;   // something else generated it further: its hash may carry a neighbour's features
				}
				long h = hash(chunk);
				all = all * 31 + h;
				this.lines.add(c.x() + " " + c.z() + " " + String.format("%016x", h));
			}
			ChunkPos first = area.chunks(HASH_SIDE, HASH_OFFSET).getFirst();
			System.out.println(String.format(java.util.Locale.ROOT, "[genbench] %s terrain hashes: %d chunks from chunk %d, %d, combined %016x in %.2f s%s",
				area.name(), HASH_SIDE * HASH_SIDE, first.x(), first.z(), all, (System.nanoTime() - start) / 1e9,
				beyond == 0 ? "" : ", " + beyond + " chunks already past TERRAIN"));
		}

		private static long hash(ChunkAccess chunk) {
			long h = 0xcbf29ce484222325L;
			for (LevelChunkSection section : chunk.getSections()) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							h = (h ^ Block.getId(section.getBlockState(x, y, z))) * 0x100000001b3L;
						}
					}
				}
			}
			return h;
		}

		@Override
		public void finish() {
			Path file = this.dir.resolve("hashes.txt");
			try {
				Files.createDirectories(this.dir);
				Files.write(file, this.lines);
				System.out.println("[genbench] terrain hashes written to " + file);
			} catch (IOException e) {
				System.out.println("[genbench] terrain hashes not written to " + file + ": " + e);
			}
		}
	}

	/**
	 * The Earth generator's base heights ({@link EarthChunkGenerator#noiseHeight}: a short stretch of the column, every
	 * heightmap type at once, kept by column) against the noise generator's own ({@link EarthChunkGenerator#noiseHeightVanilla},
	 * the whole column from the top of the world), for every heightmap type: a column every 7 blocks over each timed area
	 * (7, so the columns fall at every place in the interpolation cells) and {@value #RANDOM} columns anywhere within
	 * {@value #REACH} blocks. Both are pure functions of the seed, so nothing is generated.
	 *
	 * <p>Then both are timed column by column, warm, so that what is timed is the base height and not the terrain model
	 * building the plates under a far column (which either way pays alike, and which the far columns, more than the
	 * model keeps, would otherwise make most of the time): each column is first asked once of vanilla, untimed; then
	 * the Earth's six answers are timed with its kept answers forgotten (so one scan of the column and five answers
	 * kept from it, as a caller asking every type meets it), then vanilla's six.
	 */
	private static final class BaseHeights implements Check {
		private static final int STEP = 7;
		private static final int RANDOM = 100;
		private static final int REACH = 40_000;
		/** Mismatches listed in full. */
		private static final int SHOWN = 10;
		private final List<String> mismatches = new ArrayList<>();
		private long checked;
		private long wrong;
		private long columnsTimed;
		private long earthNanos;
		private long vanillaNanos;

		@Override
		public void area(ServerLevel level, Area area) {
			if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator generator)) {
				System.out.println("[genbench] baseheight: not the Earth generator, skipped");
				return;
			}
			RandomState randomState = level.getChunkSource().randomState();
			Heightmap.Types[] types = Heightmap.Types.values();
			List<int[]> columns = new ArrayList<>();
			int minX = (area.centre().x() - SIDE / 2) * 16;
			int minZ = (area.centre().z() - SIDE / 2) * 16;
			for (int z = minZ; z < minZ + SIDE * 16; z += STEP) {
				for (int x = minX; x < minX + SIDE * 16; x += STEP) {
					columns.add(new int[]{x, z});
				}
			}
			java.util.Random random = new java.util.Random(area.name().hashCode());
			for (int i = 0; i < RANDOM; i++) {
				columns.add(new int[]{random.nextInt(2 * REACH) - REACH, random.nextInt(2 * REACH) - REACH});
			}

			long checked = 0;
			long wrong = 0;
			for (int[] c : columns) {
				for (Heightmap.Types type : types) {
					int earth = generator.noiseHeight(c[0], c[1], type, level, randomState);
					int vanilla = generator.noiseHeightVanilla(c[0], c[1], type, level, randomState);
					checked++;
					if (earth != vanilla) {
						wrong++;
						this.mismatch(String.format(java.util.Locale.ROOT, "%s at %d, %d: %d, vanilla %d", type, c[0], c[1], earth, vanilla));
					}
				}
			}

			long earthNanos = 0;
			long vanillaNanos = 0;
			for (int[] c : columns) {
				generator.noiseHeightVanilla(c[0], c[1], Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
				generator.forgetNoiseHeights();
				long start = System.nanoTime();
				for (Heightmap.Types type : types) {
					generator.noiseHeight(c[0], c[1], type, level, randomState);
				}
				long middle = System.nanoTime();
				for (Heightmap.Types type : types) {
					generator.noiseHeightVanilla(c[0], c[1], type, level, randomState);
				}
				long end = System.nanoTime();
				earthNanos += middle - start;
				vanillaNanos += end - middle;
			}

			System.out.println(String.format(java.util.Locale.ROOT,
				"[genbench] baseheight %s: %d columns x %d types, mismatches=%d checked=%d, earth %.1f us/call (%.1f us per column, every type), "
					+ "vanilla %.1f us/call (%.1fx)",
				area.name(), columns.size(), types.length, wrong, checked, earthNanos / 1e3 / checked, earthNanos / 1e3 / columns.size(),
				vanillaNanos / 1e3 / checked, (double) vanillaNanos / earthNanos));
			this.checked += checked;
			this.wrong += wrong;
			this.columnsTimed += columns.size();
			this.earthNanos += earthNanos;
			this.vanillaNanos += vanillaNanos;
		}

		private void mismatch(String line) {
			if (this.mismatches.size() < SHOWN) {
				this.mismatches.add(line);
			}
		}

		@Override
		public void finish() {
			if (this.checked == 0) {
				return;
			}
			System.out.println(String.format(java.util.Locale.ROOT,
				"[genbench] baseheight mismatches=%d checked=%d, earth %.1f us/call (%.1f us per column, every type), vanilla %.1f us/call (%.1fx)",
				this.wrong, this.checked, this.earthNanos / 1e3 / this.checked, this.earthNanos / 1e3 / this.columnsTimed,
				this.vanillaNanos / 1e3 / this.checked, (double) this.vanillaNanos / this.earthNanos));
			for (String line : this.mismatches) {
				System.out.println("[genbench] baseheight mismatch: " + line);
			}
		}
	}
}
