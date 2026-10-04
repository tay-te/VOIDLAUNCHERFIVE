package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Foresters (docs/LIVING_WORLD.md §7.4): they walk out to the woods of the town's territory, fell a real tree whole —
 * the axe at the stump a while, then the tree coming down top to bottom, crown and all — replant saplings of its kind
 * (two for every one felled, by default), and carry the logs back to the yard. Where the town has a plantation
 * excavation open they also work its steps (rows of saplings, paths) under the builders' contract.
 *
 * <p>Which trees may be felled is {@link Trees}' business (natural crowns only, never giants, never a treehouse, never
 * near a deed or inside the town). The sim credits the wood from the forester's working time, as it does while the
 * town is abstract, so the amounts agree; with {@code gathering.terrain_changes} off the felling is mimed.
 */
final class ForesterWork implements WorkBehaviour {
	private final ExcavationWork plantation = new ExcavationWork(JobType.FORESTER, (b, l) -> null);
	private final FieldWork mime = new FieldWork(JobType.FORESTER);
	private int bouts;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		this.bouts++;
		NpcConfig.Gathering cfg = NpcModule.config().gathering();
		if (this.bouts % 3 == 0 && ExcavationWork.choose(brain, JobType.FORESTER) != null) {
			Task t = this.plantation.next(brain, level);
			if (t != null) {
				return t;
			}
		}
		if (!cfg.terrainChanges()) {
			return this.mime.next(brain, level);
		}
		Trees.Tree tree = Trees.find(level, brain.settlement, brain.random, cfg.maxTreeLogs(), cfg.keepClearOfDeeds(), brain.town.treesClaimed);
		if (tree == null) {
			return this.mime.next(brain, level);
		}
		brain.town.treesClaimed.add(tree.base().asLong());
		return new Fell(tree);
	}

	@Override
	public void onFold(FolkBrain brain, ServerLevel level) {
		this.plantation.onFold(brain, level);
	}

	/** Where the logs go: the workplace's store (the logging camp, the sawmill), else the town's nearest store. */
	static @Nullable BlockPos yard(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		if (p.workStorage != null) {
			return p.workStorage.pos();
		}
		List<Poi> stores = brain.town.places.all(level, brain.settlement, PoiType.STORAGE);
		BlockPos here = brain.folk.blockPosition();
		Poi best = null;
		for (Poi s : stores) {
			if (best == null || s.pos().distSqr(here) < best.pos().distSqr(here)) {
				best = s;
			}
		}
		if (best != null) {
			return best.pos();
		}
		return p.work;
	}

	/** One tree, from the walk out to the logs set down at the yard. */
	static final class Fell implements Task {
		private enum Phase { GO, CHOP, FALL, REPLANT, CARRY, DONE }

		private final Trees.Tree tree;
		private Phase phase = Phase.GO;
		private int timer;
		private int fallIndex;
		private int leafIndex;
		private int planted;
		private @Nullable BlockPos stand;
		private @Nullable BlockPos yard;
		private int felled;

		Fell(Trees.Tree tree) {
			this.tree = tree;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			BlockPos base = this.tree.base();
			switch (this.phase) {
				case GO -> {
					brain.hold(new ItemStack(Items.IRON_AXE), ItemStack.EMPTY);
					if (this.stand == null) {
						this.stand = FolkSpots.standableNear(level, base.relative(net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(brain.random)), 2);
						if (this.stand == null) {
							return Status.FAILED;
						}
					}
					this.timer += elapsed;
					if (brain.walk(level, this.stand, 1.2, 0.6) || (brain.walkFailed() && this.timer > 200)) {
						brain.resetWalk();
						if (!level.getBlockState(base).is(BlockTags.LOGS)) {
							// someone else got to it
							return Status.FAILED;
						}
						this.phase = Phase.CHOP;
						// a fair stint at the stump: bigger trees take longer
						this.timer = Math.min(400, 60 + this.tree.logs().size() * 5);
					} else if (this.timer > 20 * 60) {
						return Status.FAILED;
					}
				}
				case CHOP -> {
					brain.faceTowards(base);
					brain.folk.setFolkPose(FolkPose.HAMMER);
					if ((brain.folk.tickCount + elapsed) % 16 < elapsed) {
						brain.swing();
						BlockState wood = level.getBlockState(base);
						level.playSound(null, base, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.9F, 0.85F + brain.random.nextFloat() * 0.2F);
						Vec3 c = Vec3.atCenterOf(base);
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wood.isAir() ? this.tree.log().defaultBlockState() : wood), c.x, c.y,
							c.z, 6, 0.3, 0.3, 0.3, 0.05);
					}
					this.timer -= elapsed;
					if (this.timer <= 0) {
						this.phase = Phase.FALL;
						brain.folk.setFolkPose(FolkPose.NONE);
						// the crack of the trunk giving way
						level.playSound(null, base, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7F, 0.55F);
					}
				}
				case FALL -> {
					// Top to bottom: a few logs a tick, the crown with them, so it comes down in a second or two.
					int budget = 3 * Math.max(1, elapsed);
					while (budget-- > 0 && this.fallIndex < this.tree.logs().size()) {
						BlockPos p = this.tree.logs().get(this.fallIndex++);
						BlockState st = level.getBlockState(p);
						if (level.isLoaded(p) && st.is(BlockTags.LOGS)) {
							if (this.fallIndex % 3 == 1) {
								level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, p, Block.getId(st));
							}
							level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
							this.felled++;
						}
						// the leaves at and above this log's height go as it passes
						int y = p.getY();
						while (this.leafIndex < this.tree.leaves().size() && this.tree.leaves().get(this.leafIndex).getY() >= y) {
							removeLeaf(level, this.tree.leaves().get(this.leafIndex++));
						}
					}
					if (this.fallIndex >= this.tree.logs().size()) {
						while (this.leafIndex < this.tree.leaves().size()) {
							removeLeaf(level, this.tree.leaves().get(this.leafIndex++));
						}
						for (BlockPos e : this.tree.extras()) {
							if (level.isLoaded(e) && !level.getBlockState(e).isAir() && !Trees.built(level.getBlockState(e))) {
								level.setBlock(e, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
							}
						}
						this.phase = Phase.REPLANT;
						this.timer = 0;
					}
				}
				case REPLANT -> {
					brain.hold(new ItemStack(Trees.saplingFor(this.tree.log()).map(Block::asItem).orElse(Items.OAK_SAPLING)), ItemStack.EMPTY);
					brain.folk.setFolkPose(FolkPose.TEND);
					this.timer -= elapsed;
					if (this.timer > 0) {
						break;
					}
					this.timer = 20;
					if (!plantNext(brain, level)) {
						brain.folk.setFolkPose(FolkPose.NONE);
						this.phase = Phase.CARRY;
						this.timer = 0;
						this.yard = yard(brain, level);
					}
				}
				case CARRY -> {
					BlockPos to = this.yard;
					if (to == null || this.felled == 0) {
						this.phase = Phase.DONE;
						break;
					}
					brain.hold(new ItemStack(this.tree.log().asItem()), ItemStack.EMPTY);
					brain.folk.setFolkPose(FolkPose.CRAFT);
					this.timer += elapsed;
					if (brain.walk(level, to, 1.8, 0.55) || brain.walkFailed() || this.timer > 20 * 90) {
						brain.resetWalk();
						brain.faceTowards(to);
						level.playSound(null, to, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
						brain.swing();
						brain.folk.setFolkPose(FolkPose.NONE);
						this.phase = Phase.DONE;
					}
				}
				case DONE -> {
					return Status.DONE;
				}
			}
			return Status.RUNNING;
		}

		private static void removeLeaf(ServerLevel level, BlockPos p) {
			if (level.isLoaded(p) && Trees.isNaturalLeaves(level.getBlockState(p))) {
				level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}

		/**
		 * Plants the next sapling: first on the stumps (all four of a 2×2 trunk, which the big species need), then the
		 * rest of the forester's quota on open soil a few blocks off. False when done.
		 */
		private boolean plantNext(FolkBrain brain, ServerLevel level) {
			Optional<Block> sapling = Trees.saplingFor(this.tree.log());
			if (sapling.isEmpty()) {
				return false;
			}
			BlockState state = sapling.get().defaultBlockState();
			int quota = Math.max(this.tree.stumps().size(), NpcModule.config().gathering().saplingsPerTree());
			if (this.planted >= quota) {
				return false;
			}
			BlockPos at = null;
			if (this.planted < this.tree.stumps().size()) {
				at = this.tree.stumps().get(this.planted);
			} else {
				for (int tries = 0; tries < 12 && at == null; tries++) {
					BlockPos c = this.tree.base().offset(brain.random.nextInt(9) - 4, 0, brain.random.nextInt(9) - 4);
					for (int dy = 2; dy >= -2 && at == null; dy--) {
						BlockPos p = c.above(dy);
						if (level.isLoaded(p) && level.getBlockState(p).isAir() && state.canSurvive(level, p) && roomy(level, p)) {
							at = p;
						}
					}
				}
			}
			this.planted++;
			if (at == null || !level.getBlockState(at).isAir() || !state.canSurvive(level, at)) {
				return true;
			}
			brain.faceTowards(at);
			brain.swing();
			level.setBlockAndUpdate(at, state);
			level.playSound(null, at, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
			return true;
		}

		/** Room to grow: no log, sapling or building within two blocks. */
		private static boolean roomy(ServerLevel level, BlockPos p) {
			for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, 0, -2), p.offset(2, 3, 2))) {
				BlockState s = level.getBlockState(q);
				if (s.is(BlockTags.LOGS) || s.is(BlockTags.SAPLINGS) || Trees.built(s)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
			// A tree left half felled is finished off at once rather than left floating: the rest comes down, and the
			// stump is replanted.
			if (this.phase == Phase.FALL || this.phase == Phase.REPLANT) {
				while (this.phase == Phase.FALL) {
					tick(brain, level, 64);
				}
				while (plantNext(brain, level)) {
					// plant the remaining quota
				}
			}
			brain.town.treesClaimed.remove(this.tree.base().asLong());
		}

		@Override
		public String describe() {
			return switch (this.phase) {
				case GO -> "walking out to a tree";
				case CHOP -> "felling a tree (" + this.tree.logs().size() + " logs)";
				case FALL -> "timber!";
				case REPLANT -> "replanting";
				case CARRY -> "carrying logs to the yard";
				case DONE -> "done felling";
			};
		}
	}
}
