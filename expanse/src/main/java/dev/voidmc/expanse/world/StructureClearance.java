package dev.voidmc.expanse.world;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Where the buildings are, while features are being placed — so a karst tower, an ice spire, a dune or
 * a tree does not land on a pagoda, an outpost or a village house.
 *
 * <p>Structures are placed before vegetation and most terrain features, and nothing in vanilla stops a
 * later feature from growing straight through them. Feature code is not handed the structure manager,
 * but the world-generation region it writes to can see what it needs: structure references in the 3x3
 * chunks round the chunk being decorated, and structure starts up to eight chunks away. Asking it for
 * anything further is a crash, not an empty answer, so every lookup here checks the region first.
 */
public final class StructureClearance {
	private StructureClearance() {
	}

	/** Bounding boxes of surface structures with a piece in, or near, the chunk being decorated. */
	public static List<BoundingBox> nearbyBoxes(WorldGenLevel level) {
		List<BoundingBox> boxes = new ArrayList<>();
		if (!(level instanceof WorldGenRegion region)) {
			return boxes;
		}
		ChunkPos centre = region.getCenter();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkAccess chunk = region.getChunk(centre.x() + dx, centre.z() + dz, ChunkStatus.STRUCTURE_REFERENCES, false);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<Structure, LongSet> refs : chunk.getAllReferences().entrySet()) {
					if (refs.getKey().step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
						continue;
					}
					for (long packed : refs.getValue()) {
						ChunkPos at = ChunkPos.unpack(packed);
						if (!region.hasChunk(at.x(), at.z())) {
							continue;
						}
						ChunkAccess startChunk = region.getChunk(at.x(), at.z(), ChunkStatus.STRUCTURE_STARTS, false);
						StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(refs.getKey());
						if (start != null && start.isValid() && !boxes.contains(start.getBoundingBox())) {
							boxes.add(start.getBoundingBox());
						}
					}
				}
			}
		}
		return boxes;
	}

	/** Horizontal distance from (x, z) to the nearest box, 0 inside one; {@code Integer.MAX_VALUE} if there are none. */
	public static int distance(List<BoundingBox> boxes, int x, int z) {
		int best = Integer.MAX_VALUE;
		for (BoundingBox b : boxes) {
			int dx = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
			int dz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
			best = Math.min(best, Math.max(dx, dz));
		}
		return best;
	}
}
