package dev.voidmc.hearth.sim.atlas;

import dev.voidmc.hearth.sim.SimConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The structures a settlement keeps clear of: every structure set holding a {@code #minecraft:village} structure (vanilla,
 * Expanse or modded villages) and the configured extras (Expanse's landmarks and buildings, pillager outposts). Where they
 * are is pure arithmetic on the seed ({@link RandomSpreadStructurePlacement}), so this works for land nobody has
 * generated and on any thread.
 */
public final class StructureAvoidance {
	/** One set to avoid, and by how many chunks. */
	private record Avoid(Holder<StructureSet> set, RandomSpreadStructurePlacement placement, int clearance) {
	}

	private final List<Avoid> sets = new ArrayList<>();
	private final ChunkGeneratorStructureState state;

	public StructureAvoidance(ServerLevel level, SimConfig.Atlas config) {
		this.state = level.getChunkSource().getGeneratorState();
		for (Holder<StructureSet> set : this.state.possibleStructureSets()) {
			if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
				// strongholds and the like: ring placements are computed asynchronously and are underground anyway
				continue;
			}
			Identifier id = set.unwrapKey().map(k -> k.identifier()).orElse(null);
			int clearance = -1;
			for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
				if (e.structure().is(StructureTags.VILLAGE)) {
					clearance = config.villageClearance();
				}
			}
			if (id != null && config.structureClearance().containsKey(id.toString())) {
				clearance = Math.max(clearance, config.structureClearance().get(id.toString()));
			}
			if (clearance > 0) {
				this.sets.add(new Avoid(set, spread, clearance));
			}
		}
	}

	public int setCount() {
		return this.sets.size();
	}

	/** A structure chunk to keep clear of: where it starts and how far to keep. */
	public record Hazard(int chunkX, int chunkZ, int clearance) {
	}

	/**
	 * Every structure start of the avoided sets that could reach into the given block rectangle, checked against the
	 * biome there (a village chunk whose biome takes no village never gets one).
	 *
	 * @param biomeAt the surface biome at a block (the probe's)
	 */
	public List<Hazard> hazards(int minX, int minZ, int maxX, int maxZ, java.util.function.BiFunction<Integer, Integer, Holder<Biome>> biomeAt) {
		List<Hazard> out = new ArrayList<>();
		long seed = this.state.getLevelSeed();
		for (Avoid a : this.sets) {
			int spacing = a.placement.spacing();
			int c0x = (minX >> 4) - a.clearance;
			int c0z = (minZ >> 4) - a.clearance;
			int c1x = (maxX >> 4) + a.clearance;
			int c1z = (maxZ >> 4) + a.clearance;
			for (int gx = Math.floorDiv(c0x, spacing); gx <= Math.floorDiv(c1x, spacing); gx++) {
				for (int gz = Math.floorDiv(c0z, spacing); gz <= Math.floorDiv(c1z, spacing); gz++) {
					ChunkPos p = a.placement.getPotentialStructureChunk(seed, gx * spacing, gz * spacing);
					if (p.x() < c0x || p.x() > c1x || p.z() < c0z || p.z() > c1z) {
						continue;
					}
					if (!a.placement.isStructureChunk(this.state, p.x(), p.z())) {
						continue;
					}
					Holder<Biome> biome = biomeAt.apply(p.getMiddleBlockX(), p.getMiddleBlockZ());
					boolean fits = false;
					for (StructureSet.StructureSelectionEntry e : a.set.value().structures()) {
						Structure s = e.structure().value();
						if (s.biomes().contains(biome)) {
							fits = true;
							break;
						}
					}
					if (fits) {
						out.add(new Hazard(p.x(), p.z(), a.clearance));
					}
				}
			}
		}
		return out;
	}

	/** Whether a block column is clear of every hazard. */
	public static boolean clear(List<Hazard> hazards, int x, int z) {
		int cx = x >> 4;
		int cz = z >> 4;
		for (Hazard h : hazards) {
			if (Math.max(Math.abs(h.chunkX - cx), Math.abs(h.chunkZ - cz)) <= h.clearance) {
				return false;
			}
		}
		return true;
	}
}
