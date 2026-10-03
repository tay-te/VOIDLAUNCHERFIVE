package dev.voidmc.expanse.command;

import com.mojang.datafixers.util.Pair;
import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.world.biome.ExpanseBiomes;
import dev.voidmc.expanse.world.biome.UndergroundBiomes;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Headless checks for development, off unless asked for on the command line:
 *
 * <pre>
 *   -Dexpanse.dev.atlas=RADIUS[:STEP]   render an atlas round the origin once the server is up
 *   -Dexpanse.dev.locate=RADIUS         log the distance from the origin to each Expanse biome
 *   -Dexpanse.dev.structures=CHUNKS     locate each Expanse structure (and, with .generate, build it)
 *   -Dexpanse.dev.generate=true         generate full chunks round each biome/structure found
 *   -Dexpanse.dev.visit=X,Z[;X,Z...]     generate full chunks round each of these places
 *   -Dexpanse.dev.stop=true             then shut the server down
 * </pre>
 *
 * This is how the biome placement was checked without a game client: run the dedicated server with
 * these flags, read the log, look at the PNG.
 */
public final class DevHarness {
	private DevHarness() {
	}

	public static void install() {
		String atlas = System.getProperty("expanse.dev.atlas");
		String locate = System.getProperty("expanse.dev.locate");
		if (atlas == null && locate == null && System.getProperty("expanse.dev.structures") == null && System.getProperty("expanse.dev.visit") == null) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Thread t = new Thread(() -> run(server, atlas, locate), "expanse-dev");
			t.setDaemon(true);
			t.start();
		});
	}

	/**
	 * Generates the chunks round a biome sample to full status — features, trees, structures, initial
	 * mob spawns — so anything that fails while decorating that biome shows up in the log, and reports
	 * the surface height range and the mobs that spawned.
	 */
	private static void generateAround(MinecraftServer server, ServerLevel level, BlockPos at, ResourceKey<Biome> key) throws Exception {
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		// Ask for the chunks without blocking the server thread, and wait for them here: a server thread that
		// waits in getChunk runs other queued tasks meanwhile (the next round's among them), so rounds would
		// nest and the server stop ticking until the watchdog took it for hung.
		java.util.List<java.util.concurrent.CompletableFuture<?>> loading = server.submit(() -> {
			java.util.List<java.util.concurrent.CompletableFuture<?>> futures = new java.util.ArrayList<>();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					futures.add(level.getChunkSource().getChunkFuture(cx + dx, cz + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true));
				}
			}
			return futures;
		}).join();
		java.util.concurrent.CompletableFuture.allOf(loading.toArray(java.util.concurrent.CompletableFuture[]::new)).get();
		java.util.concurrent.CompletableFuture<String> done = new java.util.concurrent.CompletableFuture<>();
		server.execute(() -> {
			try {
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				java.util.Map<String, Integer> tops = new java.util.TreeMap<>();
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
						for (int lx = 0; lx < 16; lx += 4) {
							for (int lz = 0; lz < 16; lz += 4) {
								int h = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, lx, lz);
								min = Math.min(min, h);
								max = Math.max(max, h);
								BlockPos top = new BlockPos(chunk.getPos().getMinBlockX() + lx, h, chunk.getPos().getMinBlockZ() + lz);
								tops.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(top).getBlock()).getPath(), 1, Integer::sum);
							}
						}
					}
				}
				net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB((cx - 2) * 16, level.getMinY(), (cz - 2) * 16,
					(cx + 3) * 16, level.getMaxY(), (cz + 3) * 16);
				java.util.Map<String, Integer> mobs = new java.util.TreeMap<>();
				for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> true)) {
					mobs.merge(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), 1, Integer::sum);
				}
				done.complete("surface " + min + ".." + max + ", mobs " + mobs + ", top blocks " + tops);
			} catch (Throwable t) {
				done.completeExceptionally(t);
			}
		});
		Expanse.LOG.info("[dev]   generated round {}: {}", key.identifier(), done.get());
	}

	private static void run(MinecraftServer server, String atlas, String locate) {
		ServerLevel level = server.overworld();
		try {
			if (locate != null) {
				int radius = Integer.parseInt(locate);
				// Whose caves this world has: the Expanse's under the Earth terrain, vanilla's anywhere else.
				for (ResourceKey<Biome> key : java.util.List.of(Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES, Biomes.DEEP_DARK, Biomes.SULFUR_CAVES,
					UndergroundBiomes.MOSSY_CAVES, UndergroundBiomes.DRIPSTONE_GROTTOS, UndergroundBiomes.ECHOING_DEPTHS, UndergroundBiomes.SULFUR_SEEPS)) {
					Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), BlockPos.ZERO, 2000, 32, 32);
					Expanse.LOG.info("[dev] cave biome {} -> {}", key.identifier(), found == null ? "none within 2000" : found.getFirst().toShortString());
				}
				for (Field f : ExpanseBiomes.class.getFields()) {
					if (!Modifier.isStatic(f.getModifiers()) || !(f.get(null) instanceof ResourceKey<?>)) {
						continue;
					}
					@SuppressWarnings("unchecked")
					ResourceKey<Biome> key = (ResourceKey<Biome>) f.get(null);
					long start = System.nanoTime();
					Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), BlockPos.ZERO, radius, 32, 64);
					long ms = (System.nanoTime() - start) / 1_000_000;
					Expanse.LOG.info("[dev] {} -> {} ({} ms)", key.identifier(),
						found == null ? "NOT FOUND within " + radius : found.getFirst().toShortString() + " d=" + (int) Math.sqrt(found.getFirst().distSqr(BlockPos.ZERO)), ms);
					if (found != null && Boolean.getBoolean("expanse.dev.generate")) {
						generateAround(server, level, found.getFirst(), key);
					}
				}
			}
			String structures = System.getProperty("expanse.dev.structures");
			if (structures != null) {
				int radius = Integer.parseInt(structures);
				var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
				for (var ref : registry.listElements().filter(r -> r.key().identifier().getNamespace().equals(Expanse.MOD_ID)).toList()) {
					// On the server thread: the structure check cache that locating fills is not thread-safe.
					var found = server.submit(() -> level.getChunkSource().getGenerator().findNearestMapStructure(level,
						net.minecraft.core.HolderSet.direct(ref), BlockPos.ZERO, radius, false)).join();
					Expanse.LOG.info("[dev] structure {} -> {}", ref.key().identifier(), found == null ? "NOT FOUND" : found.getFirst().toShortString());
					if (found != null && Boolean.getBoolean("expanse.dev.generate")) {
						generateAround(server, level, found.getFirst(), net.minecraft.resources.ResourceKey.create(
							net.minecraft.core.registries.Registries.BIOME, ref.key().identifier()));
						BlockPos at = found.getFirst();
						Expanse.LOG.info("[dev]   placed {}", server.submit(() -> {
							var start = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(ref.value());
							if (start == null || !start.isValid()) {
								return "NOTHING";
							}
							var box = start.getBoundingBox();
							return start.getPieces().size() + " pieces, " + box.getXSpan() + "x" + box.getYSpan() + "x" + box.getZSpan();
						}).join());
					}
				}
			}
			String visit = System.getProperty("expanse.dev.visit");
			if (visit != null) {
				for (String place : visit.split(";")) {
					String[] xz = place.split(",");
					int x = Integer.parseInt(xz[0].trim());
					int z = Integer.parseInt(xz[1].trim());
					int y = level.getChunkSource().getGenerator().getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
						level, level.getChunkSource().randomState());
					ResourceKey<Biome> key = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().orElseThrow();
					Expanse.LOG.info("[dev] visit {}, {}, {} ({})", x, y, z, key.identifier());
					generateAround(server, level, new BlockPos(x, y, z), key);
				}
			}
			if (atlas != null) {
				String[] parts = atlas.split(":");
				int radius = Integer.parseInt(parts[0]);
				int step = parts.length > 1 ? Integer.parseInt(parts[1]) : Math.max(4, radius / 256);
				long start = System.nanoTime();
				Atlas.Result r = Atlas.render(level, 0, 0, radius, step);
				Expanse.LOG.info("[dev] atlas {} in {} ms, surface y {}..{}", r.image(), (System.nanoTime() - start) / 1_000_000, r.minHeight(), r.maxHeight());
				Atlas.summarise(r).forEach((k, v) -> Expanse.LOG.info("[dev]   {} {}", v, k));
			}
		} catch (Exception e) {
			Expanse.LOG.error("[dev] harness failed", e);
		}
		if (Boolean.getBoolean("expanse.dev.stop")) {
			server.execute(() -> server.halt(false));
		}
	}
}
