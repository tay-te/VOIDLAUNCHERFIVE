package dev.voidmc.expanse.command;

import com.mojang.datafixers.util.Pair;
import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.world.biome.ExpanseBiomes;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * Headless checks for development, off unless asked for on the command line:
 *
 * <pre>
 *   -Dexpanse.dev.atlas=RADIUS[:STEP]   render an atlas round the origin once the server is up
 *   -Dexpanse.dev.locate=RADIUS         log the distance from the origin to each Expanse biome
 *   -Dexpanse.dev.structures=CHUNKS     locate each Expanse structure (and, with .generate, build it)
 *   -Dexpanse.dev.generate=true         generate full chunks round each biome/structure found
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
		if (atlas == null && locate == null && System.getProperty("expanse.dev.structures") == null) {
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
		java.util.concurrent.CompletableFuture<String> done = new java.util.concurrent.CompletableFuture<>();
		server.execute(() -> {
			try {
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
						int h = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8);
						min = Math.min(min, h);
						max = Math.max(max, h);
					}
				}
				net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB((cx - 2) * 16, level.getMinY(), (cz - 2) * 16,
					(cx + 3) * 16, level.getMaxY(), (cz + 3) * 16);
				java.util.Map<String, Integer> mobs = new java.util.TreeMap<>();
				for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> true)) {
					mobs.merge(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), 1, Integer::sum);
				}
				done.complete("surface " + min + ".." + max + ", mobs " + mobs);
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
				for (Field f : ExpanseBiomes.class.getFields()) {
					if (!Modifier.isStatic(f.getModifiers())) {
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
					var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
						net.minecraft.core.HolderSet.direct(ref), BlockPos.ZERO, radius, false);
					Expanse.LOG.info("[dev] structure {} -> {}", ref.key().identifier(), found == null ? "NOT FOUND" : found.getFirst().toShortString());
					if (found != null && Boolean.getBoolean("expanse.dev.generate")) {
						generateAround(server, level, found.getFirst(), net.minecraft.resources.ResourceKey.create(
							net.minecraft.core.registries.Registries.BIOME, ref.key().identifier()));
					}
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
