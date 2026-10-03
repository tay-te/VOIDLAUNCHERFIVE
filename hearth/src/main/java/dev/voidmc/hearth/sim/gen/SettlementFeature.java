package dev.voidmc.hearth.sim.gen;

import com.mojang.serialization.MapCodec;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.apply.AppliedLedger;
import dev.voidmc.hearth.sim.apply.ChunkLedgers;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code hearth:settlement}: world generation writes history. For the chunk being decorated it asks the atlas which
 * historic plots touch it and writes their completed stages ({@link Blueprint#forEachInChunk}), strictly inside this
 * chunk, so the order chunks generate in never matters and neighbours never need to exist. It records what it wrote in
 * the chunk's {@link AppliedLedger}, so the runtime applier later writes only what was built after day 0.
 *
 * <p>Placed in every overworld biome at {@code TOP_LAYER_MODIFICATION}, after trees (which the guard kept off planned
 * ground) and snow.
 */
public record SettlementFeature() implements Feature {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	public static final MapCodec<SettlementFeature> CODEC = MapCodec.unit(new SettlementFeature());
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	@Override
	public MapCodec<SettlementFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!(level instanceof WorldGenRegion region) || !HearthServices.hasBlueprints()) {
			return false;
		}
		SimWorld world = SimServer.world(region.getLevel());
		if (world == null || !world.atlas().enabled()) {
			return false;
		}
		long start = System.nanoTime();
		ChunkPos pos = region.getCenter();
		List<RegionPlan.HistoricPlot> plots = world.atlas().plotsInChunk(pos.x(), pos.z());
		if (plots.isEmpty()) {
			return false;
		}
		RegionPlan plan = world.atlas().plan(pos.x() >> 6, pos.z() >> 6);
		long settlement = plan.site() == null ? 0 : plan.site().settlementId();
		BlueprintService blueprints = HearthServices.blueprints();
		ChunkAccess chunk = level.getChunk(pos.x(), pos.z());
		AppliedLedger ledger = ChunkLedgers.of(chunk);
		int[] blocks = new int[1];
		for (RegionPlan.HistoricPlot hp : plots) {
			Blueprint bp;
			try {
				bp = blueprints.blueprint(hp.spec(), world.terrain());
			} catch (RuntimeException e) {
				LOG.warn("hearth: cannot write historic plot {} ({}) of {}: {}", hp.id(), hp.spec().archetype(), settlement, e.toString());
				continue;
			}
			bp.forEachInChunk(pos.x(), pos.z(), 0, hp.completed(), (x, y, z, state, tag) -> {
				BlockPos at = new BlockPos(x, y, z);
				if (y < level.getMinY() || y >= level.getMaxY()) {
					return;
				}
				level.setBlock(at, state, FLAGS);
				blocks[0]++;
				if (tag != null) {
					BlockEntity be = level.getBlockEntity(at);
					if (be != null) {
						be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
					}
				}
			});
			ledger = ledger.with(settlement, hp.id(), Cursor.pack(hp.completed(), 0));
		}
		ChunkLedgers.store(chunk, ledger);
		SimServer s = SimServer.current();
		if (s != null) {
			s.stats().featureChunks.increment();
			s.stats().featureBlocks.add(blocks[0]);
			s.stats().featureNanos.add(System.nanoTime() - start);
		}
		return blocks[0] > 0;
	}
}
