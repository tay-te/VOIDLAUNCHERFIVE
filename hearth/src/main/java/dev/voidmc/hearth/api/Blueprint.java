package dev.voidmc.hearth.api;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A generated building: every block it places, grouped into stages in build order, plus what folk need to know about
 * it (beds, job sites, doors). Immutable and thread-safe; produced by {@link BlueprintService#blueprint}.
 *
 * <p>Steps within a stage are already in a sensible build order: bottom-up, supports before what they support
 * (a torch after its wall, a door after its frame), and from the entrance outward, so an NPC placing them one by one
 * looks like a person building. Indexing is stable: {@code (stage, index)} names the same block every time the
 * blueprint is regenerated from the same spec and terrain.
 *
 * <p>Air steps are real steps (carving the interior, clearing a doorway). Positions not covered by any step are left
 * as the world has them.
 */
public interface Blueprint {
	PlotSpec spec();

	/** Every position any step touches, in world coordinates. */
	BoundingBox bounds();

	int stageCount();

	StageKind stageKind(int stage);

	/**
	 * The campaign a stage belongs to: a building raised over months (a castle) or a working dug over weeks (a quarry's
	 * benches, a mine's galleries, a woodlot's coupes) is built in campaigns, each a whole run of stages in
	 * {@link StageKind} order; the next campaign starts the kinds again. Never decreases with the stage index; 0 for a
	 * building raised in one (the default).
	 */
	default int campaign(int stage) {
		return 0;
	}

	int stepCount(int stage);

	/** Emits step {@code index} of {@code stage}. */
	void step(int stage, int index, StepSink sink);

	/**
	 * Emits, in build order, every step of stages {@code [fromStage, toStage)} whose position lies in the given chunk.
	 * This is how blueprints are written into chunks as they generate or load, one chunk at a time, so it must be cheap:
	 * implementations index steps by chunk rather than filtering all of them.
	 */
	void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, StepSink sink);

	/** Labour to complete a stage, in work units: one unit is one builder placing one ordinary block. */
	int work(int stage);

	/** Goods a stage consumes ({@link Goods} units), indexed by {@link Goods#ordinal()}. */
	int[] goods(int stage);

	List<Poi> pois();

	/** The outside block in front of each entrance, main entrance first. */
	List<BlockPos> entrances();

	/** How many people can sleep here (beds). */
	int beds();

	Silhouette silhouette();
}
