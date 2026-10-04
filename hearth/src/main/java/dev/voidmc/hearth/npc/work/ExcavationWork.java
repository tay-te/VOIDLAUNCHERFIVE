package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.place.BlueprintSource;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Miners, quarriers and clay diggers working an excavation (docs/LIVING_WORLD.md §7.3–7.4). The same contract the
 * builders keep (ARCHITECTURE.md §5.3), run backwards:
 *
 * <ol>
 *   <li>Pick an open excavation whose kind fits the trade ({@link Extraction#fit}), spreading the crew over plots.</li>
 *   <li>The next step is {@code (plot.stage(), plot.step())} of its blueprint, <b>read again at the moment of
 *   cutting</b>.</li>
 *   <li>Walk to a {@code WORK_FACE} of the plot within reach of the step (or any place within reach); an air step is cut
 *   (the block broken with its sound and dust), a solid step placed (a prop, a rail, a ladder, a lantern going in behind
 *   the face).</li>
 *   <li>Then {@code settlement.advanceConstruction(plot.id(), 1)}: the sim credits what the cut removed as yield.</li>
 * </ol>
 *
 * Nothing outside the blueprint is ever touched. About one step per {@code gathering.cut_step_ticks}, the pace the
 * abstract sim assumes, so a pit near a player and one nobody watches go down at the same rate. Every few cuts the
 * worker carries a load to the pit's store. Time spent cutting is not reported as labour (the steps carry it); with no
 * excavation open, or with {@code gathering.terrain_changes} off, the trade falls back to its old work, whose time is.
 */
final class ExcavationWork implements WorkBehaviour {
	/** Natural cuts between trips to the store with a load. */
	private static final int CUTS_PER_LOAD = 8;

	private final JobType job;
	private final WorkBehaviour fallback;

	ExcavationWork(JobType job, WorkBehaviour fallback) {
		this.job = job;
		this.fallback = fallback;
	}

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		PlotView plot = choose(brain, this.job);
		if (plot == null) {
			brain.town.gatherClaims.remove(brain.resident.id);
			return this.fallback.next(brain, level);
		}
		brain.town.gatherClaims.put(brain.resident.id, plot.id());
		if (!NpcModule.config().gathering().terrainChanges()) {
			return mime(brain, level, plot);
		}
		return new Excavate(plot.id());
	}

	@Override
	public void onFold(FolkBrain brain, ServerLevel level) {
		brain.town.gatherClaims.remove(brain.resident.id);
		this.fallback.onFold(brain, level);
	}

	/** The excavation this worker should be at: the one already claimed while it stays open, else the best fit nearby. */
	static @Nullable PlotView choose(FolkBrain brain, JobType job) {
		Integer claimed = brain.town.gatherClaims.get(brain.resident.id);
		if (claimed != null) {
			PlotView p = brain.settlement.plot(claimed);
			if (p != null && Extraction.workable(p) && Extraction.fit(job, Extraction.kind(p)) > 0) {
				return p;
			}
		}
		BlockPos here = brain.folk.blockPosition();
		PlotView best = null;
		double bestScore = Double.MAX_VALUE;
		for (PlotView p : brain.settlement.plots()) {
			if (!Extraction.workable(p)) {
				continue;
			}
			int fit = Extraction.fit(job, Extraction.kind(p));
			if (fit == 0) {
				continue;
			}
			double score = (2 - fit) * 1000.0 + Math.sqrt(ResidentPlaces.centre(p.spec()).distSqr(here)) + 48.0 * brain.town.gatherClaimsOn(p.id());
			if (score < bestScore) {
				best = p;
				bestScore = score;
			}
		}
		return best;
	}

	/** With terrain changes switched off: work the face in pantomime, the loop running as numbers. */
	private static Task mime(FolkBrain brain, ServerLevel level, PlotView plot) {
		Blueprint bp = BlueprintSource.of(level, brain.settlement, plot);
		List<Poi> faces = bp == null ? List.of() : Extraction.workFaces(bp);
		Poi face = faces.isEmpty() ? null : faces.get(brain.random.nextInt(faces.size()));
		BlockPos stand = face != null ? face.pos() : ResidentPlaces.centre(plot.spec());
		BlockPos look = face != null ? face.pos().relative(face.facing()) : stand.below();
		BlockState rock = level.isLoaded(look) ? level.getBlockState(look) : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
		Tasks.Effect pick = Tasks.every(18, SoundEvents.STONE_HIT, 0.7F, (b, lvl, e, at) -> {
			Vec3 c = Vec3.atCenterOf(look);
			lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock.isAir() ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : rock),
				c.x, c.y, c.z, 5, 0.3, 0.3, 0.3, 0.05);
		}, true);
		return new Tasks.Linger(stand, look, FolkPose.HAMMER, 600, new ItemStack(toolFor(rock)), ItemStack.EMPTY, pick, "working the face");
	}

	static Item toolFor(BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return Items.IRON_SHOVEL;
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return Items.IRON_AXE;
		}
		return Items.IRON_PICKAXE;
	}

	static int cutTicks(FolkBrain brain) {
		int base = NpcModule.config().gathering().cutStepTicks();
		return Math.max(4, base - base / 4 + brain.random.nextInt(Math.max(1, base / 2)));
	}

	/** Working one excavation until it closes, its stage waits for goods, or the day moves on. */
	static final class Excavate implements Task {
		private final int plotId;
		private BuilderWork.@Nullable Step current;
		private @Nullable BlockPos stand;
		private int stepTicks;
		private int cooldown;
		private int waited;
		private int naturalCuts;
		private @Nullable BlockPos carryTo;
		private ItemStack load = ItemStack.EMPTY;
		private int carryTicks;
		private int carryWalk;
		private String doing = "at the face";

		Excavate(int plotId) {
			this.plotId = plotId;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			PlotView plot = brain.settlement.plot(this.plotId);
			Blueprint bp = plot == null ? null : BlueprintSource.of(level, brain.settlement, plot);
			if (plot == null || bp == null || !Extraction.workable(plot) || plot.stage() >= bp.stageCount()) {
				brain.town.gatherClaims.remove(brain.resident.id);
				return Status.DONE;
			}
			this.cooldown -= elapsed;
			this.stepTicks += elapsed;

			if (this.carryTo != null) {
				return carry(brain, level, elapsed);
			}

			int stage = plot.stage();
			int index = plot.step();
			if (index >= bp.stepCount(stage)) {
				// the bench or gallery is done; the sim opens the next when its goods are in hand
				this.waited += elapsed;
				this.doing = "waiting for the next bench";
				return this.waited > 400 ? Status.DONE : Status.RUNNING;
			}
			this.waited = 0;

			BuilderWork.Step step = this.current;
			if (step == null || step.stage() != stage || step.index() != index) {
				step = BuilderWork.read(bp, stage, index);
				if (step == null) {
					return Status.FAILED;
				}
				this.current = step;
				this.stand = null;
				this.stepTicks = 0;
			}
			if (!level.isLoaded(step.pos())) {
				return Status.FAILED;
			}
			// Steps that change nothing (a cut where the ground is already open) are passed over quickly.
			if (BuilderWork.sameAs(level.getBlockState(step.pos()), step.state())) {
				int skipped = 0;
				BuilderWork.Step s = step;
				while (skipped < 24 && s != null && BuilderWork.sameAs(level.getBlockState(s.pos()), s.state()) && plot.stage() == stage
					&& plot.step() < bp.stepCount(stage)) {
					brain.settlement.advanceConstruction(plot.id(), 1);
					skipped++;
					s = plot.stage() == stage && plot.step() < bp.stepCount(stage) ? BuilderWork.read(bp, stage, plot.step()) : null;
				}
				this.current = null;
				return Status.RUNNING;
			}
			BlockState there = level.getBlockState(step.pos());
			brain.hold(step.state().isAir() ? new ItemStack(toolFor(there)) : new ItemStack(step.state().getBlock().asItem()), ItemStack.EMPTY);

			Folk folk = brain.folk;
			if (BuilderWork.canReach(folk, step.pos()) && !standingOn(folk, step.pos()) && !inside(folk, step)) {
				brain.resetWalk();
				brain.faceTowards(step.pos());
				folk.setFolkPose(FolkPose.HAMMER);
				this.doing = step.state().isAir() ? "cutting " + there.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT)
					: "setting " + step.state().getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
				if (this.cooldown <= 0) {
					if (cut(brain, level, plot, bp, stage, index)) {
						this.cooldown = cutTicks(brain);
					}
					this.current = null;
				}
				return Status.RUNNING;
			}
			folk.setFolkPose(FolkPose.NONE);
			if (this.stand == null) {
				this.stand = standFor(level, bp, step.pos(), folk);
			}
			this.doing = "walking to the face";
			BlockPos goal = this.stand != null ? this.stand : step.pos();
			boolean arrived = brain.walk(level, goal, 1.0, 0.65);
			if ((arrived && (!BuilderWork.canReach(folk, step.pos()) || standingOn(folk, step.pos()))) || brain.walkFailed() || this.stepTicks > 400) {
				brain.resetWalk();
				// After a fair try, cut it from where they are (never from far away): a pit never stalls on one awkward block.
				if (!standingOn(folk, step.pos()) && (folk.distanceToSqr(Vec3.atCenterOf(step.pos())) < 10 * 10 || this.stepTicks > 900)) {
					brain.faceTowards(step.pos());
					if (this.cooldown <= 0 && cut(brain, level, plot, bp, stage, index)) {
						this.cooldown = cutTicks(brain);
					}
					this.current = null;
				} else {
					this.stand = null;
				}
			}
			return Status.RUNNING;
		}

		/** Cuts or sets the plot's next step if it is still the one we think. */
		private boolean cut(FolkBrain brain, ServerLevel level, PlotView plot, Blueprint bp, int stage, int index) {
			if (plot.stage() != stage || plot.step() != index || index >= bp.stepCount(stage)) {
				return false;
			}
			BuilderWork.Step step = BuilderWork.read(bp, stage, index);
			if (step == null) {
				return false;
			}
			BlockPos pos = step.pos();
			BlockState old = level.getBlockState(pos);
			if (old.hasBlockEntity() && old.getBlock() != step.state().getBlock()) {
				// somebody's chest or sign: never dug out (the sim's applier refuses the same); the step is passed
				brain.settlement.advanceConstruction(plot.id(), 1);
				return true;
			}
			if (step.state().isAir()) {
				// Never pull the ground from under a player (or another folk) standing on it.
				if (supportsSomeone(level, pos, brain.folk)) {
					return false;
				}
				if (!old.isAir()) {
					brain.swing();
					level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(old));
					level.setBlock(pos, step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					if (old.getFluidState().isEmpty() && !old.canBeReplaced()) {
						this.naturalCuts++;
						if (this.naturalCuts % CUTS_PER_LOAD == 0) {
							startCarry(brain, level, plot, bp, old);
						}
					}
				}
			} else {
				if (!step.state().getCollisionShape(level, pos).isEmpty()
					&& !level.getEntitiesOfClass(Player.class, new AABB(pos), p -> !p.isSpectator()).isEmpty()) {
					return false;
				}
				brain.swing();
				level.setBlock(pos, step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (step.tag() != null) {
					BuilderWork.loadBlockEntity(level, pos, step.state(), step.tag());
				}
				SoundType sound = step.state().getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			}
			brain.settlement.advanceConstruction(plot.id(), 1);
			return true;
		}

		/** A load of what came out of the face, carried to the pit's store and set down. */
		private void startCarry(FolkBrain brain, ServerLevel level, PlotView plot, Blueprint bp, BlockState cut) {
			Poi store = Extraction.storage(bp);
			BlockPos to = store != null ? store.pos() : !bp.entrances().isEmpty() ? bp.entrances().get(0) : null;
			if (to == null || to.distSqr(brain.folk.blockPosition()) < 9) {
				return;
			}
			Item item = cut.getBlock().asItem();
			this.load = new ItemStack(item == Items.AIR ? Items.COBBLESTONE : item);
			this.carryTo = to;
			this.carryTicks = 40;
			this.carryWalk = 0;
		}

		private Status carry(FolkBrain brain, ServerLevel level, int elapsed) {
			BlockPos to = this.carryTo;
			this.doing = "carrying a load to the store";
			brain.hold(this.load, ItemStack.EMPTY);
			brain.folk.setFolkPose(FolkPose.CRAFT);
			this.carryWalk += elapsed;
			if (brain.walk(level, to, 1.8, 0.6) || brain.walkFailed() || this.carryWalk > 20 * 40) {
				brain.resetWalk();
				brain.faceTowards(to);
				if (this.carryTicks == 40) {
					level.playSound(null, to, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F);
					brain.swing();
				}
				this.carryTicks -= elapsed;
				if (this.carryTicks <= 0) {
					this.carryTo = null;
					this.load = ItemStack.EMPTY;
					brain.folk.setFolkPose(FolkPose.NONE);
					this.stand = null;
				}
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public boolean creditedBySteps() {
			return true;
		}

		@Override
		public String describe() {
			return this.doing + " (excavation " + this.plotId + ")";
		}
	}

	/** Standing on top of the block to be cut: they would fall into their own cut. */
	static boolean standingOn(Folk folk, BlockPos pos) {
		AABB feet = folk.getBoundingBox().move(0, -0.2, 0);
		return feet.intersects(new AABB(pos)) && !folk.getBoundingBox().intersects(new AABB(pos));
	}

	private static boolean inside(Folk folk, BuilderWork.Step step) {
		return !step.state().isAir() && folk.getBoundingBox().intersects(new AABB(step.pos()))
			&& !step.state().getCollisionShape(folk.level(), step.pos()).isEmpty();
	}

	/** Someone (a player, a folk other than the cutter, an animal) stands on this block. */
	static boolean supportsSomeone(ServerLevel level, BlockPos pos, Entity cutter) {
		AABB above = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 1.3, pos.getZ() + 1);
		return !level.getEntities(cutter, above, e -> e.isAlive() && !e.isSpectator() && e.onGround()).isEmpty();
	}

	/** A work face of the excavation within reach of the step (and not on top of it), else any place within reach. */
	static @Nullable BlockPos standFor(ServerLevel level, Blueprint bp, BlockPos target, Folk folk) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (Poi face : Extraction.workFaces(bp)) {
			BlockPos p = face.pos();
			if (p.equals(target.above()) || !level.isLoaded(p) || !FolkSpots.standable(level, p)) {
				continue;
			}
			Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + folk.getEyeHeight(), p.getZ() + 0.5);
			if (eye.distanceToSqr(Vec3.atCenterOf(target)) > 4.6 * 4.6) {
				continue;
			}
			double d = p.distSqr(folk.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		if (best != null) {
			return best;
		}
		BlockPos any = BuilderWork.standFor(level, target, folk);
		return any != null && any.equals(target.above()) ? null : any;
	}

	static NpcConfig.Gathering config() {
		return NpcModule.config().gathering();
	}
}
