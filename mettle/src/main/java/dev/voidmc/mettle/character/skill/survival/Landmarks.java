package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * Which structures are landmarks, what "kind" a structure is, and a cheap search for the nearest one of the kinds a
 * player hasn't found (Lay of the Land).
 *
 * <p>Landmarks are Expanse's eight ({@code #expanse:landmarks}) when Expanse ships that tag, otherwise the notable vanilla
 * structures in {@code #mettle:survival/notable}. Kinds fold variants together: every abandoned camp is one kind.
 *
 * <p>The search is vanilla's {@code /locate} on a budget: candidate chunks come from the placement maths alone (no
 * world access), nearest first, and only the closest few are confirmed against the world, so it never generates more
 * than a handful of chunks' structure starts.
 */
public final class Landmarks {
	/** Search radius in blocks. */
	public static final int RADIUS = 2048;
	private static final int MAX_PRESENCE_CHECKS = 48;
	private static final int MAX_CHUNK_LOADS = 4;
	/** Landmarks closer than this are where you already are. */
	private static final long NEAR = (long) SurvivalRules.MARKER_REACHED;

	public record Found(BlockPos pos, Identifier kind) {
	}

	private Landmarks() {
	}

	/** The structures that count as landmarks in this world. */
	public static Optional<HolderSet.Named<Structure>> landmarks(Registry<Structure> registry) {
		Optional<HolderSet.Named<Structure>> expanse = registry.get(SurvivalSkill.EXPANSE_LANDMARKS);
		if (expanse.isPresent() && expanse.get().size() > 0) {
			return expanse;
		}
		return registry.get(SurvivalSkill.NOTABLE);
	}

	public static boolean expanseLandmarks(Registry<Structure> registry) {
		return registry.get(SurvivalSkill.EXPANSE_LANDMARKS).map(s -> s.size() > 0).orElse(false);
	}

	/** A structure's kind: its id, with every abandoned camp folded into one. */
	public static Identifier kind(Identifier structure) {
		String path = structure.getPath();
		if (path.startsWith("abandoned_camp")) {
			return structure.withPath("abandoned_camp");
		}
		return structure;
	}

	/** The nearest landmark of a kind not in {@code known}, within {@link #RADIUS}, or null. */
	public static @Nullable Found nearest(ServerLevel level, BlockPos from, Set<Identifier> known) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Optional<HolderSet.Named<Structure>> set = landmarks(registry);
		if (set.isEmpty()) {
			return null;
		}
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		int cx = from.getX() >> 4;
		int cz = from.getZ() >> 4;
		record Candidate(ChunkPos chunk, Holder<Structure> structure, StructurePlacement placement, long distance) {
		}
		List<Candidate> candidates = new ArrayList<>();
		for (Holder<Structure> structure : set.get()) {
			Identifier id = structure.unwrapKey().map(k -> k.identifier()).orElse(null);
			if (id == null || known.contains(kind(id))) {
				continue;
			}
			for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
				if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
					continue;
				}
				int spacing = spread.spacing();
				int cells = (RADIUS / 16) / spacing + 1;
				int gx = Math.floorDiv(cx, spacing);
				int gz = Math.floorDiv(cz, spacing);
				for (int dx = -cells; dx <= cells; dx++) {
					for (int dz = -cells; dz <= cells; dz++) {
						ChunkPos chunk = spread.getPotentialStructureChunk(state.getLevelSeed(), (gx + dx) * spacing, (gz + dz) * spacing);
						long ddx = chunk.x() - cx;
						long ddz = chunk.z() - cz;
						long distance = ddx * ddx + ddz * ddz;
						// One you are standing at is reached, not still to find.
						if (distance * 256 <= (long) RADIUS * RADIUS && distance * 256 >= NEAR * NEAR
							&& spread.isStructureChunk(state, chunk.x(), chunk.z())) {
							candidates.add(new Candidate(chunk, structure, placement, distance));
						}
					}
				}
			}
		}
		candidates.sort(Comparator.comparingLong(Candidate::distance));
		StructureManager structures = level.structureManager();
		int checks = 0;
		int loads = 0;
		for (Candidate c : candidates) {
			if (checks++ >= MAX_PRESENCE_CHECKS) {
				break;
			}
			StructureCheckResult result = structures.checkStructurePresence(c.chunk, c.structure.value(), c.placement, false);
			if (result == StructureCheckResult.START_NOT_PRESENT) {
				continue;
			}
			if (result == StructureCheckResult.CHUNK_LOAD_NEEDED) {
				if (loads++ >= MAX_CHUNK_LOADS) {
					continue;
				}
				ChunkAccess chunk = level.getChunk(c.chunk.x(), c.chunk.z(), ChunkStatus.STRUCTURE_STARTS);
				StructureStart start = structures.getStartForStructure(c.structure.value(), chunk);
				if (start == null || !start.isValid()) {
					continue;
				}
			}
			Identifier id = c.structure.unwrapKey().orElseThrow().identifier();
			int x = c.chunk.getMiddleBlockX();
			int z = c.chunk.getMiddleBlockZ();
			// The terrain height from the noise alone, so marking a far landmark never generates its chunk.
			int y = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
			return new Found(new BlockPos(x, y, z), kind(id));
		}
		return null;
	}

	/** "village_plains" → "Village plains", for structures nobody wrote a name for. */
	public static String prettify(String path) {
		String s = path.replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
