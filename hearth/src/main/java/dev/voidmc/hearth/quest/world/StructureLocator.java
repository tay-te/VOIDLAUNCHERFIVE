package dev.voidmc.hearth.quest.world;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.model.SettlementQuests.LocatedTarget;
import dev.voidmc.hearth.quest.service.QuestStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Finds landmarks for exploration quests without stalling the server. Vanilla's {@code /locate} walks rings of
 * candidate chunks on the server thread, generating structure starts as it goes; a quest board cannot afford that. Here,
 * on a worker thread, the candidates come from the structure set's placement maths and a biome check against the biome
 * source (no chunks); then only the nearest few candidates are confirmed by asking for their chunk at
 * {@code STRUCTURE_STARTS} asynchronously (the chunk system generates it on its own workers). The result — found with
 * its bounds, or not found within the radius — is stored in the settlement's quest data, so each structure is searched
 * once per settlement (a miss is retried after a month).
 *
 * <p>Only random-spread placements are searched (every Expanse landmark and building, and vanilla's map structures);
 * Expanse's underground halls are placed by the realms themselves, and quests reach the realms by biome instead.
 */
public final class StructureLocator {
	/** Candidates confirmed per search, nearest first. */
	private static final int CONFIRM = 4;
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-quest-locate");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private StructureLocator() {
	}

	/**
	 * Queues searches round a settlement for structures it has no answer for yet. {@code onDone} runs on the server
	 * thread with the settlement id when a search finishes (the board tops up with the new target).
	 */
	public static void request(ServerLevel level, Settlement settlement, List<Identifier> structures, int radius, long day, LongConsumer onDone) {
		for (Identifier id : structures) {
			String key = settlement.id() + "|" + id;
			if (!PENDING.add(key)) {
				continue;
			}
			long settlementId = settlement.id();
			BlockPos center = settlement.center();
			WORKER.execute(() -> {
				LocatedTarget result;
				try {
					result = search(level, center, id, radius, day);
				} catch (RuntimeException e) {
					Hearth.LOG.warn("Quest landmark search for {} near settlement {} failed", id, settlementId, e);
					result = new LocatedTarget(id, false, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, day);
				}
				LocatedTarget found = result;
				MinecraftServer server = level.getServer();
				if (!server.isRunning()) {
					PENDING.remove(key);
					return;
				}
				server.execute(() -> {
					PENDING.remove(key);
					Settlement s = QuestWorlds.byId(server, settlementId);
					if (s == null) {
						return;
					}
					SettlementQuests data = QuestStore.board(s);
					data.putTarget(found);
					s.markDirty();
					if (found.found()) {
						onDone.accept(settlementId);
					}
				});
			});
		}
	}

	/** Searches a settlement's surroundings for one structure. Worker thread. */
	private static LocatedTarget search(ServerLevel level, BlockPos center, Identifier id, int radius, long day) {
		LocatedTarget none = new LocatedTarget(id, false, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, day);
		Optional<Holder.Reference<Structure>> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(ResourceKey.create(Registries.STRUCTURE, id));
		if (holder.isEmpty()) {
			return none;
		}
		Structure structure = holder.get().value();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		BiomeResolver biomes = generator.getBiomeSource().createUncachedResolver(level.getChunkSource().randomState());
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		int radiusChunks = Math.max(1, radius >> 4);

		List<ChunkPos> spots = new ArrayList<>();
		for (StructurePlacement placement : state.getPlacementsForStructure(holder.get())) {
			if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
				continue;
			}
			int spacing = spread.spacing();
			int regions = radiusChunks / spacing + 1;
			int rx0 = Math.floorDiv(cx, spacing);
			int rz0 = Math.floorDiv(cz, spacing);
			for (int rx = rx0 - regions; rx <= rx0 + regions; rx++) {
				for (int rz = rz0 - regions; rz <= rz0 + regions; rz++) {
					// The placement takes any chunk of a region and finds that region's candidate.
					ChunkPos chunk = spread.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
					long dx = chunk.x() - cx;
					long dz = chunk.z() - cz;
					if (dx * dx + dz * dz <= (long) radiusChunks * radiusChunks) {
						spots.add(chunk);
					}
				}
			}
		}
		spots.sort(Comparator.comparingLong(c -> {
			long dx = c.x() - cx;
			long dz = c.z() - cz;
			return dx * dx + dz * dz;
		}));

		// Cheap filters first (placement rules, the biome at the surface), then confirm the nearest few for real.
		List<ChunkPos> candidates = new ArrayList<>();
		for (ChunkPos chunk : spots) {
			if (candidates.size() >= CONFIRM) {
				break;
			}
			boolean placed = false;
			for (StructurePlacement placement : state.getPlacementsForStructure(holder.get())) {
				if (placement.isStructureChunk(state, chunk.x(), chunk.z())) {
					placed = true;
					break;
				}
			}
			if (!placed) {
				continue;
			}
			int x = chunk.getMiddleBlockX();
			int z = chunk.getMiddleBlockZ();
			int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
			Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
			if (structure.biomes().contains(biome)) {
				candidates.add(chunk);
			}
		}
		for (ChunkPos chunk : candidates) {
			if (!level.getServer().isRunning()) {
				return none;
			}
			// Off the server thread this schedules the request on it and returns at once; join waits on this worker only.
			ChunkResult<ChunkAccess> result = level.getChunkSource().getChunkFuture(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS, true).join();
			ChunkAccess access = result.orElse(null);
			if (access == null) {
				continue;
			}
			StructureStart start = access.getStartForStructure(structure);
			if (start != null && start.isValid()) {
				BoundingBox box = start.getBoundingBox();
				BlockPos mid = new BlockPos((box.minX() + box.maxX()) / 2, (box.minY() + box.maxY()) / 2, (box.minZ() + box.maxZ()) / 2);
				return new LocatedTarget(id, true, mid, new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()),
					day);
			}
		}
		return none;
	}

	/** Forgets searches in flight (server stopping). */
	public static void clear() {
		PENDING.clear();
	}
}
