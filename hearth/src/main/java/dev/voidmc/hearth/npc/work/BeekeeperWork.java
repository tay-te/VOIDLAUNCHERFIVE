package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.npc.NpcModule;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Beekeepers at the apiary (docs/LIVING_WORLD.md §7.4–7.5): they smoke the hives, take the honeycomb from hives that are
 * full (the hive's honey level drops to empty, as when a player shears it beside a campfire; the bees stay calm), and
 * carry the comb to the store, where the chandler's candles will come from. The hives are found by a scan of the
 * workplace, repeated every five minutes; the comb itself is the sim's to credit from their working time.
 */
final class BeekeeperWork implements WorkBehaviour {
	private static final int RESCAN = 20 * 60 * 5;
	private final List<BlockPos> hives = new ArrayList<>();
	private long scannedAt = Long.MIN_VALUE;
	private int combs;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		long now = level.getGameTime();
		if (now - this.scannedAt > RESCAN) {
			scan(brain, level);
			this.scannedAt = now;
		}
		ResidentPlaces p = brain.places(level);
		if (this.hives.isEmpty()) {
			return new Tasks.Linger(p.work, null, FolkPose.CRAFT, 400, new ItemStack(Items.HONEY_BOTTLE), ItemStack.EMPTY,
				Tasks.every(90, SoundEvents.BOTTLE_FILL, 0.4F, null, false), "bottling honey");
		}
		if (this.combs >= 3 && p.workStorage != null) {
			this.combs = 0;
			return new Tasks.Linger(p.workStorage.pos(), p.workStorage.pos().relative(p.workStorage.facing()), FolkPose.CRAFT, 60,
				new ItemStack(Items.HONEYCOMB), ItemStack.EMPTY, Tasks.every(40, SoundEvents.CHEST_OPEN, 0.4F, null, true), "storing honeycomb");
		}
		BlockPos full = null;
		for (BlockPos h : this.hives) {
			BlockState st = level.isLoaded(h) ? level.getBlockState(h) : null;
			if (st != null && st.hasProperty(BeehiveBlock.HONEY_LEVEL) && st.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS) {
				full = h;
				break;
			}
		}
		if (full != null && NpcModule.config().gathering().harvestHives()) {
			return new Harvest(full);
		}
		BlockPos hive = this.hives.get(brain.random.nextInt(this.hives.size()));
		BlockPos stand = FolkSpots.standableNear(level, hive, 2);
		Tasks.Effect smoke = Tasks.every(30, SoundEvents.CAMPFIRE_CRACKLE, 0.4F, (b, lvl, e, at) -> {
			Vec3 c = Vec3.atCenterOf(hive);
			lvl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.3, c.z, 1, 0.2, 0.1, 0.2, 0.005);
		}, true);
		return new Tasks.Linger(stand != null ? stand : hive, hive, FolkPose.TEND, 300, new ItemStack(Items.CAMPFIRE), ItemStack.EMPTY, smoke,
			"smoking the hives");
	}

	private void scan(FolkBrain brain, ServerLevel level) {
		this.hives.clear();
		PlotView plot = brain.resident.work != Resident.NO_PLOT ? brain.settlement.plot(brain.resident.work) : null;
		BoundingBox box;
		if (plot != null) {
			box = SettlementPlaces.bounds(plot.spec());
		} else {
			BlockPos w = brain.places(level).work;
			box = new BoundingBox(w.getX() - 12, w.getY() - 4, w.getZ() - 12, w.getX() + 12, w.getY() + 8, w.getZ() + 12);
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= Math.min(box.maxY(), box.minY() + 14); y++) {
					m.set(x, y, z);
					if (level.isLoaded(m) && level.getBlockState(m).is(BlockTags.BEEHIVES)) {
						this.hives.add(m.immutable());
						if (this.hives.size() >= 24) {
							return;
						}
					}
				}
			}
		}
	}

	/** Taking the comb from a full hive. */
	final class Harvest implements Task {
		private final BlockPos hive;
		private int timeout = 20 * 40;
		private int work = -1;

		Harvest(BlockPos hive) {
			this.hive = hive;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.timeout -= elapsed;
			if (this.timeout < 0) {
				return Status.FAILED;
			}
			brain.hold(new ItemStack(Items.SHEARS), new ItemStack(Items.CAMPFIRE));
			if (this.work < 0) {
				if (brain.walk(level, this.hive, 2.2, 0.6) || brain.walkFailed()) {
					brain.resetWalk();
					this.work = 40;
				}
				return Status.RUNNING;
			}
			brain.faceTowards(this.hive);
			brain.folk.setFolkPose(FolkPose.TEND);
			this.work -= elapsed;
			if (this.work > 0) {
				return Status.RUNNING;
			}
			BlockState st = level.getBlockState(this.hive);
			if (st.hasProperty(BeehiveBlock.HONEY_LEVEL) && st.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS) {
				brain.swing();
				level.setBlock(this.hive, st.setValue(BeehiveBlock.HONEY_LEVEL, 0), Block.UPDATE_CLIENTS);
				level.playSound(null, this.hive, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
				Vec3 c = Vec3.atCenterOf(this.hive);
				level.sendParticles(ParticleTypes.FALLING_HONEY, c.x, c.y, c.z, 6, 0.3, 0.2, 0.3, 0.0);
				brain.hold(new ItemStack(Items.HONEYCOMB), ItemStack.EMPTY);
				BeekeeperWork.this.combs++;
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
			return "taking honeycomb";
		}
	}
}
