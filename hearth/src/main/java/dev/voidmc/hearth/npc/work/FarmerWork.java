package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.place.SettlementPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Farmers farm the real fields of their town: they harvest ripe crops and sow again at once, plant bare tilled earth
 * with what their land grows, till the soil at a field's edge, and weed between rows. Fields are the town's farm
 * plots (or, without plots, the tilled earth round their job site), found by scanning and kept for a few minutes.
 *
 * <p>A harvest puts food in the town's stores: the farmer is embodied, so the sim is not counting their labour.
 */
final class FarmerWork implements WorkBehaviour {
	private static final int RESCAN = 20 * 60 * 3;

	private final List<BlockPos> farmland = new ArrayList<>();
	private long scannedAt = -1_000_000_000L;
	private @Nullable BoundingBox field;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		long now = level.getGameTime();
		if (now - this.scannedAt > RESCAN || this.farmland.isEmpty() && now - this.scannedAt > 400) {
			if (!SettlementPlaces.takeScanBudget(level)) {
				return Tasks.Linger.at(brain.places(level).work, 60, "waiting at the fields");
			}
			scan(brain, level);
			this.scannedAt = now;
		}
		if (this.farmland.isEmpty()) {
			ResidentPlaces p = brain.places(level);
			return new Tasks.Linger(p.work, p.workSite != null ? p.workSite.pos().relative(p.workSite.facing()) : null, FolkPose.CRAFT, 400,
				new ItemStack(Items.IRON_HOE), ItemStack.EMPTY, null, "at the composter");
		}
		// Look at a handful of cells and do whatever is most useful: harvest, sow, till, else weed.
		BlockPos harvest = null;
		BlockPos sow = null;
		for (int i = 0; i < 12; i++) {
			BlockPos soil = this.farmland.get(brain.random.nextInt(this.farmland.size()));
			if (!level.isLoaded(soil)) {
				continue;
			}
			BlockState ground = level.getBlockState(soil);
			if (!ground.is(Blocks.FARMLAND)) {
				continue;
			}
			BlockState crop = level.getBlockState(soil.above());
			if (crop.getBlock() instanceof CropBlock c && c.isMaxAge(crop)) {
				harvest = soil.above();
				break;
			}
			if (crop.isAir() && sow == null) {
				sow = soil.above();
			}
		}
		if (harvest != null) {
			return new FieldJob(harvest, true, cropFor(brain.settlement.culture()));
		}
		if (sow != null) {
			return new FieldJob(sow, false, cropFor(brain.settlement.culture()));
		}
		BlockPos till = tillable(level, brain);
		if (till != null) {
			return new Till(till);
		}
		BlockPos row = this.farmland.get(brain.random.nextInt(this.farmland.size())).above();
		BlockPos stand = FolkSpots.standableNear(level, row, 2);
		return new Tasks.Linger(stand != null ? stand : row, row, FolkPose.TEND, 300, new ItemStack(Items.IRON_HOE), ItemStack.EMPTY, null,
			"weeding");
	}

	private void scan(FolkBrain brain, ServerLevel level) {
		this.farmland.clear();
		this.field = null;
		List<BoundingBox> fields = new ArrayList<>();
		for (PlotView plot : brain.settlement.plots()) {
			String path = plot.spec().archetype().getPath();
			if (path.startsWith("farm/") && plot.state() == PlotState.BUILT) {
				fields.add(SettlementPlaces.bounds(plot.spec()));
			}
		}
		PlotView work = brain.resident.work != Resident.NO_PLOT ? brain.settlement.plot(brain.resident.work) : null;
		if (work != null && work.spec().archetype().getPath().startsWith("farm/")) {
			fields.clear();
			fields.add(SettlementPlaces.bounds(work.spec()));
		}
		if (fields.isEmpty()) {
			BlockPos c = brain.places(level).work;
			// No farm plots (a player-built farm): tilled earth within a short walk of the job site.
			fields.add(new BoundingBox(c.getX() - 20, c.getY() - 3, c.getZ() - 20, c.getX() + 20, c.getY() + 3, c.getZ() + 20));
		}
		// The nearest field first; farmers keep to one at a time.
		BlockPos here = brain.folk.blockPosition();
		fields.sort((a, b) -> Double.compare(a.getCenter().distSqr(here), b.getCenter().distSqr(here)));
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (BoundingBox box : fields) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					for (int y = box.minY(); y <= box.maxY(); y++) {
						p.set(x, y, z);
						if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.FARMLAND)) {
							this.farmland.add(p.immutable());
						}
					}
				}
			}
			if (!this.farmland.isEmpty()) {
				this.field = box;
				break;
			}
		}
	}

	/** Grass or dirt right beside the field's tilled earth, inside a farm plot: the field grows by a row. */
	private @Nullable BlockPos tillable(ServerLevel level, FolkBrain brain) {
		if (this.field == null || brain.random.nextInt(3) != 0) {
			return null;
		}
		for (int i = 0; i < 6; i++) {
			BlockPos soil = this.farmland.get(brain.random.nextInt(this.farmland.size()));
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = soil.relative(d);
				if (!this.field.isInside(n) || !level.isLoaded(n)) {
					continue;
				}
				BlockState s = level.getBlockState(n);
				if ((s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK)) && level.getBlockState(n.above()).isAir()) {
					return n;
				}
			}
		}
		return null;
	}

	static Block cropFor(Culture culture) {
		return switch (culture) {
			case NORTHERN, TUNDRA, MOORLAND, ALPINE -> Blocks.POTATOES;
			case KARST, WISTERIA -> Blocks.BEETROOTS;
			case TROPICAL, BAYOU -> Blocks.CARROTS;
			default -> Blocks.WHEAT;
		};
	}

	/** Walks to a crop and harvests and re-sows it, or sows a bare cell. */
	private final class FieldJob implements Task {
		private final BlockPos at;
		private final boolean harvest;
		private final Block crop;
		private int work = 25;
		private int timeout = 20 * 30;

		FieldJob(BlockPos at, boolean harvest, Block crop) {
			this.at = at;
			this.harvest = harvest;
			this.crop = crop;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.timeout -= elapsed;
			if (this.timeout < 0) {
				return Status.FAILED;
			}
			if (!brain.walk(level, this.at, 1.8, 0.55)) {
				return brain.walkFailed() ? Status.FAILED : Status.RUNNING;
			}
			brain.faceTowards(this.at);
			brain.hold(this.harvest ? new ItemStack(Items.IRON_HOE) : new ItemStack(seedOf(this.crop)), ItemStack.EMPTY);
			brain.folk.setFolkPose(FolkPose.TEND);
			this.work -= elapsed;
			if (this.work > 0) {
				return Status.RUNNING;
			}
			BlockState here = level.getBlockState(this.at);
			BlockState soil = level.getBlockState(this.at.below());
			if (!soil.is(Blocks.FARMLAND)) {
				return Status.FAILED;
			}
			brain.swing();
			if (this.harvest && here.getBlock() instanceof CropBlock c && c.isMaxAge(here)) {
				level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, this.at, Block.getId(here));
				// Sown again straight away, with the seed saved from the harvest.
				level.setBlock(this.at, c.getStateForAge(0), Block.UPDATE_CLIENTS);
				brain.settlement.stockpile().add(Goods.FOOD, 1);
			} else if (!this.harvest && here.isAir() && this.crop instanceof CropBlock c) {
				level.setBlock(this.at, c.getStateForAge(0), Block.UPDATE_CLIENTS);
				level.playSound(null, this.at, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return Status.DONE;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return this.harvest ? "harvesting" : "sowing";
		}
	}

	/** Hoes a block of earth into farmland. */
	private final class Till implements Task {
		private final BlockPos at;
		private int work = 30;
		private int timeout = 20 * 30;

		Till(BlockPos at) {
			this.at = at;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.timeout -= elapsed;
			if (this.timeout < 0) {
				return Status.FAILED;
			}
			if (!brain.walk(level, this.at.above(), 1.8, 0.55)) {
				return brain.walkFailed() ? Status.FAILED : Status.RUNNING;
			}
			brain.faceTowards(this.at);
			brain.hold(new ItemStack(Items.IRON_HOE), ItemStack.EMPTY);
			brain.folk.setFolkPose(FolkPose.TEND);
			this.work -= elapsed;
			if (this.work > 0) {
				return Status.RUNNING;
			}
			BlockState s = level.getBlockState(this.at);
			if ((s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK)) && level.getBlockState(this.at.above()).isAir()) {
				brain.swing();
				level.setBlock(this.at, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.playSound(null, this.at, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
				FarmerWork.this.farmland.add(this.at);
			}
			return Status.DONE;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return "tilling";
		}
	}

	private static net.minecraft.world.item.Item seedOf(Block crop) {
		if (crop == Blocks.POTATOES) {
			return Items.POTATO;
		}
		if (crop == Blocks.CARROTS) {
			return Items.CARROT;
		}
		if (crop == Blocks.BEETROOTS) {
			return Items.BEETROOT_SEEDS;
		}
		return Items.WHEAT_SEEDS;
	}
}
