package dev.voidmc.hearth.npc.work;

import com.mojang.logging.LogUtils;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.place.BlueprintSource;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Builders raise their town block by block. The contract with the sim (ARCHITECTURE.md §5.3, docs/FOLK.md):
 *
 * <ol>
 *   <li>Pick a plot under construction ({@link PlotState#BUILDING}) in the settlement, spreading builders over plots.</li>
 *   <li>The next step is {@code (plot.stage(), plot.step())} of the plot's blueprint, <b>read again at the moment of
 *   placing</b> (another builder, or the sim, may have moved it on).</li>
 *   <li>Walk within reach; place it with no neighbour updates (shapes are pre-resolved), with a swing, the block's
 *   sound and particles — or, for an air step, break what is there with particles.</li>
 *   <li>Then {@code settlement.advanceConstruction(plot.id(), 1)}.</li>
 * </ol>
 *
 * High work is reached the way a player reaches it: the builder towers up on vanilla scaffolding (jumping and placing
 * a scaffold underfoot), works from the top, drops back down through it and takes it down. Every scaffold is entered in
 * the settlement's {@link ScaffoldLedger} and removed when done, when the builder is interrupted, or when they are
 * folded away; scaffolds are never blueprint steps. Between stages the builder fetches materials from the store.
 */
final class BuilderWork implements WorkBehaviour {
	private static final Logger LOG = LogUtils.getLogger();
	/** Eye-to-block-centre distance a builder can place at. */
	private static final double REACH = 4.6;

	private int idleRounds;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		PlotView plot = choosePlot(brain, level);
		if (plot == null) {
			brain.town.builderClaims.remove(brain.resident.id);
			// Nothing to build: work at the yard, sawing and squaring timber for the next job.
			this.idleRounds++;
			ResidentPlaces p = brain.places(level);
			BlockPos look = p.workSite != null ? p.workSite.pos().relative(p.workSite.facing()) : null;
			return new Tasks.Linger(p.work, look, this.idleRounds % 2 == 0 ? FolkPose.HAMMER : FolkPose.CRAFT, 300, new ItemStack(Items.IRON_AXE),
				ItemStack.EMPTY, Tasks.every(20, SoundEvents.WOOD_HIT, 0.5F, null, true), "at the builders' yard");
		}
		brain.town.builderClaims.put(brain.resident.id, plot.id());
		return new Build(plot.id());
	}

	@Override
	public void onFold(FolkBrain brain, ServerLevel level) {
		brain.town.builderClaims.remove(brain.resident.id);
		brain.settlement.data(ScaffoldLedger.KEY).clear(level, brain.resident.id);
		brain.settlement.markDirty();
	}

	private static @Nullable PlotView choosePlot(FolkBrain brain, ServerLevel level) {
		Integer claimed = brain.town.builderClaims.get(brain.resident.id);
		if (claimed != null) {
			PlotView p = brain.settlement.plot(claimed);
			if (p != null && p.state() == PlotState.BUILDING && !brain.town.stalled(claimed, level.getGameTime())) {
				return p;
			}
		}
		BlockPos here = brain.folk.blockPosition();
		PlotView best = null;
		double bestScore = Double.MAX_VALUE;
		for (PlotView p : brain.settlement.plots()) {
			if (p.state() != PlotState.BUILDING || brain.town.stalled(p.id(), level.getGameTime())) {
				continue;
			}
			PlotSpec s = p.spec();
			double d = Math.sqrt(s.origin().distSqr(here));
			// Spread out: another builder on a plot counts as a long walk.
			double score = d + 64.0 * brain.town.claimsOn(p.id());
			if (score < bestScore) {
				best = p;
				bestScore = score;
			}
		}
		return best;
	}

	/** One step of a blueprint, as read. */
	private record Step(int stage, int index, BlockPos pos, BlockState state, @Nullable CompoundTag tag) {
	}

	/** Where the rest of this stage and the next two will put solid blocks. */
	private static java.util.Set<Long> upcoming(Blueprint blueprint, int stage, int index) {
		java.util.Set<Long> out = new java.util.HashSet<>();
		for (int st = stage; st < Math.min(blueprint.stageCount(), stage + 3); st++) {
			int from = st == stage ? index : 0;
			for (int i = from; i < blueprint.stepCount(st); i++) {
				blueprint.step(st, i, (x, y, z, state, tag) -> {
					if (!state.isAir()) {
						out.add(BlockPos.asLong(x, y, z));
					}
				});
			}
		}
		return out;
	}

	private static @Nullable Step read(Blueprint blueprint, int stage, int index) {
		Step[] out = new Step[1];
		blueprint.step(stage, index, (x, y, z, state, tag) -> out[0] = new Step(stage, index, new BlockPos(x, y, z), state, tag));
		return out[0];
	}

	/** Building one plot until it is done or the day moves on. */
	private static final class Build implements Task {
		private final int plotId;
		private int lastStage = -1;
		private @Nullable BlockPos fetchFrom;
		private int fetchTicks;
		private @Nullable Step current;
		private @Nullable BlockPos stand;
		private int stepTicks;
		private int cooldown;
		private int waitedForStage;
		private @Nullable Scaffold scaffold;
		private String doing = "building";

		Build(int plotId) {
			this.plotId = plotId;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			PlotView plot = brain.settlement.plot(this.plotId);
			Blueprint blueprint = plot == null ? null : BlueprintSource.of(level, brain.settlement, plot);
			if (plot == null || blueprint == null || plot.state() != PlotState.BUILDING || plot.stage() >= blueprint.stageCount()) {
				return finish(brain, level);
			}
			this.cooldown -= elapsed;
			this.stepTicks += elapsed;

			// Scaffolding: put up, climbed, come down, taken down.
			if (this.scaffold != null && this.scaffold.done()) {
				this.scaffold = null;
			}
			if (this.scaffold != null && this.scaffold.busy()) {
				this.doing = this.scaffold.describe();
				this.scaffold.tick(brain, level, elapsed);
				return Status.RUNNING;
			}

			int stage = plot.stage();
			int index = plot.step();
			if (index >= blueprint.stepCount(stage)) {
				// The stage is laid; the sim starts the next when its goods are in hand.
				this.waitedForStage += elapsed;
				this.doing = "waiting for materials";
				brain.folk.setFolkPose(FolkPose.NONE);
				return this.waitedForStage > 400 ? finish(brain, level) : Status.RUNNING;
			}
			this.waitedForStage = 0;

			// A new stage: fetch materials from the store first.
			if (stage != this.lastStage) {
				this.lastStage = stage;
				this.fetchFrom = store(brain, level);
				this.fetchTicks = 50;
			}
			if (this.fetchFrom != null) {
				this.doing = "fetching materials";
				if (brain.walk(level, this.fetchFrom, 1.8, 0.6) || brain.walkFailed() || this.stepTicks > 900) {
					brain.resetWalk();
					brain.faceTowards(this.fetchFrom);
					if (this.fetchTicks == 50) {
						level.playSound(null, this.fetchFrom, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
						brain.swing();
					}
					this.fetchTicks -= elapsed;
					if (this.fetchTicks <= 0) {
						level.playSound(null, this.fetchFrom, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
						this.fetchFrom = null;
						this.stepTicks = 0;
					}
				}
				return Status.RUNNING;
			}

			Step step = this.current;
			if (step == null || step.stage != stage || step.index != index) {
				step = read(blueprint, stage, index);
				if (step == null) {
					return Status.FAILED;
				}
				this.current = step;
				this.stand = null;
				this.stepTicks = 0;
			}
			BlockState there = level.getBlockState(step.pos);
			if (!level.isLoaded(step.pos)) {
				return Status.FAILED;
			}
			// Steps that change nothing (air over air, a block already in place) are passed over quickly.
			if (sameAs(there, step.state)) {
				int skipped = 0;
				while (skipped < 24 && step != null && sameAs(level.getBlockState(step.pos), step.state) && plot.step() < blueprint.stepCount(stage)
					&& plot.stage() == stage) {
					brain.settlement.advanceConstruction(plot.id(), 1);
					skipped++;
					step = plot.stage() == stage && plot.step() < blueprint.stepCount(stage) ? read(blueprint, stage, plot.step()) : null;
				}
				this.current = null;
				return Status.RUNNING;
			}
			brain.hold(step.state.isAir() ? new ItemStack(Items.IRON_PICKAXE) : new ItemStack(step.state.getBlock().asItem()), ItemStack.EMPTY);

			Folk folk = brain.folk;
			if (canReach(folk, step.pos) && !insideTarget(folk, step)) {
				brain.resetWalk();
				brain.faceTowards(step.pos);
				this.doing = step.state.isAir() ? "clearing" : "placing " + step.state.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
				if (this.cooldown <= 0) {
					if (place(brain, level, plot, blueprint, stage, index)) {
						this.cooldown = BuilderStep.ticks(brain);
					}
					this.current = null;
				}
				return Status.RUNNING;
			}
			// On top of a scaffold that no longer reaches: down and take it away first.
			if (this.scaffold != null) {
				this.scaffold.dismantle();
				return Status.RUNNING;
			}
			double above = step.pos.getY() - folk.getY();
			if (above > 3.0 && FolkBrain.config().scaffolding()) {
				Scaffold s = Scaffold.plan(level, brain, plot.spec(), step.pos, upcoming(blueprint, stage, index));
				if (s != null) {
					this.scaffold = s;
					return Status.RUNNING;
				}
			}
			if (this.stand == null) {
				this.stand = standFor(level, step.pos, folk);
			}
			this.doing = "walking to the work";
			BlockPos goal = this.stand != null ? this.stand : step.pos;
			boolean there2 = brain.walk(level, goal, 1.0, 0.65);
			if ((there2 && !canReach(folk, step.pos)) || brain.walkFailed() || this.stepTicks > 400) {
				// Out of reach from anywhere we can stand: after a fair try, place it anyway (the builder stretches;
				// never stall a town over one awkward block), but only from close by.
				brain.resetWalk();
				if (folk.distanceToSqr(Vec3.atCenterOf(step.pos)) < 10 * 10 || this.stepTicks > 900) {
					brain.faceTowards(step.pos);
					if (this.cooldown <= 0 && place(brain, level, plot, blueprint, stage, index)) {
						this.cooldown = BuilderStep.ticks(brain);
					}
					this.current = null;
				} else {
					this.stand = null;
				}
			}
			return Status.RUNNING;
		}

		private Status finish(FolkBrain brain, ServerLevel level) {
			if (this.scaffold != null && this.scaffold.hasBlocks()) {
				this.scaffold.dismantle();
				this.scaffold.tick(brain, level, 2);
				return Status.RUNNING;
			}
			brain.town.builderClaims.remove(brain.resident.id);
			return Status.DONE;
		}

		/**
		 * Places the plot's next step if it is still the one we think: the contract reads {@code (stage, step)} at the
		 * moment of placing.
		 */
		private boolean place(FolkBrain brain, ServerLevel level, PlotView plot, Blueprint blueprint, int stage, int index) {
			if (plot.stage() != stage || plot.step() != index || index >= blueprint.stepCount(stage)) {
				return false;
			}
			Step step = read(blueprint, stage, index);
			if (step == null) {
				return false;
			}
			BlockPos pos = step.pos;
			// Never wall a player in.
			if (!step.state.getCollisionShape(level, pos).isEmpty()
				&& !level.getEntitiesOfClass(Player.class, new AABB(pos), p -> !p.isSpectator()).isEmpty()) {
				return false;
			}
			BlockState old = level.getBlockState(pos);
			ScaffoldLedger ledger = brain.settlement.data(ScaffoldLedger.KEY);
			if (old.is(Blocks.SCAFFOLDING)) {
				ledger.remove(brain.resident.id, pos);
			}
			brain.swing();
			if (step.state.isAir()) {
				if (!old.isAir()) {
					level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(old));
					level.setBlock(pos, step.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			} else {
				level.setBlock(pos, step.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (step.tag != null) {
					loadBlockEntity(level, pos, step.state, step.tag);
				}
				SoundType sound = step.state.getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
				Vec3 c = Vec3.atCenterOf(pos);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, step.state), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.02);
			}
			brain.settlement.advanceConstruction(plot.id(), 1);
			return true;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
			if (this.scaffold != null) {
				this.scaffold.removeAll(brain, level);
				this.scaffold = null;
			}
		}

		@Override
		public String describe() {
			return this.doing + " (plot " + this.plotId + ")";
		}
	}

	private static boolean sameAs(BlockState there, BlockState wanted) {
		return there == wanted || wanted.isAir() && there.isAir();
	}

	static boolean canReach(Folk folk, BlockPos pos) {
		return folk.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
	}

	/** Placing a solid block where the builder stands would bury them: step aside first. */
	private static boolean insideTarget(Folk folk, Step step) {
		if (step.state.isAir()) {
			return false;
		}
		return folk.getBoundingBox().intersects(new AABB(step.pos)) && !step.state.getCollisionShape(folk.level(), step.pos).isEmpty();
	}

	/** Somewhere to stand within reach of a block, not in its way. */
	private static @Nullable BlockPos standFor(ServerLevel level, BlockPos target, Folk folk) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -3; dy <= 1; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dx == 0 && dz == 0 && dy >= -1) {
						continue;
					}
					p.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (!level.isLoaded(p) || !FolkSpots.standable(level, p)) {
						continue;
					}
					Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + folk.getEyeHeight(), p.getZ() + 0.5);
					if (eye.distanceToSqr(Vec3.atCenterOf(target)) > REACH * REACH) {
						continue;
					}
					double d = p.distSqr(folk.blockPosition());
					if (d < bestD) {
						bestD = d;
						best = p.immutable();
					}
				}
			}
		}
		return best;
	}

	private static @Nullable BlockPos store(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		if (p.workStorage != null) {
			return p.workStorage.pos();
		}
		List<Poi> stores = brain.town.places.all(level, brain.settlement, PoiType.STORAGE);
		Poi best = null;
		for (Poi s : stores) {
			if (best == null || s.pos().distSqr(brain.folk.blockPosition()) < best.pos().distSqr(brain.folk.blockPosition())) {
				best = s;
			}
		}
		return best != null && best.pos().distSqr(brain.folk.blockPosition()) < 64 * 64 ? best.pos() : null;
	}

	private static void loadBlockEntity(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return;
		}
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(be.problemPath(), LOG)) {
			be.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), tag));
			be.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
	}

	/** About one step per {@code builder_step_ticks}, a little irregular so a crew does not move in time. */
	static final class BuilderStep {
		static int ticks(FolkBrain brain) {
			int base = FolkBrain.config().builderStepTicks();
			return Math.max(4, base - base / 4 + brain.random.nextInt(Math.max(1, base / 2)));
		}
	}

	/**
	 * A column of vanilla scaffolding beside high work. Put up the way a player does it: stand on the spot, jump, and
	 * place a scaffold underfoot at the top of the jump, again and again. Worked from the top; dropped through
	 * (sneaking, as players do) and taken down top first. Every block goes in the ledger as it is placed.
	 */
	static final class Scaffold {
		private enum Phase { GO, POLE, TOP, DOWN, TEARDOWN, DONE }

		private final BlockPos base;
		private final int height;
		private final List<BlockPos> placed = new ArrayList<>();
		private Phase phase = Phase.GO;
		private int timer;
		private int jumpWait;

		private Scaffold(BlockPos base, int height) {
			this.base = base;
			this.height = height;
		}

		/**
		 * Finds a clear column near the target: not on any block the next stages will place (so the scaffold is never in
		 * the building's way), outside the plot's footprint if one will do.
		 */
		static @Nullable Scaffold plan(ServerLevel level, FolkBrain brain, PlotSpec spec, BlockPos target, java.util.Set<Long> upcoming) {
			BlockPos best = null;
			int bestHeight = 0;
			double bestScore = Double.MAX_VALUE;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int ring = Math.max(Math.abs(dx), Math.abs(dz));
					if (ring == 0) {
						continue;
					}
					int x = target.getX() + dx;
					int z = target.getZ() + dz;
					BlockPos ground = groundBelow(level, new BlockPos(x, target.getY(), z), 24);
					if (ground == null) {
						continue;
					}
					// Feet one below the target: the column fills from the ground up to there.
					int feet = target.getY() - 1;
					int h = feet - ground.getY();
					if (h < 2 || h > 24 || !clearColumn(level, ground, h + 2) || inTheWay(ground, h + 2, upcoming)) {
						continue;
					}
					boolean inside = x >= spec.origin().getX() && x < spec.origin().getX() + spec.sizeX() && z >= spec.origin().getZ()
						&& z < spec.origin().getZ() + spec.sizeZ();
					double score = ring + (inside ? 3 : 0) + ground.distSqr(brain.folk.blockPosition()) * 0.001;
					if (score < bestScore) {
						best = ground;
						bestHeight = h;
						bestScore = score;
					}
				}
			}
			return best == null ? null : new Scaffold(best, bestHeight);
		}

		private static @Nullable BlockPos groundBelow(ServerLevel level, BlockPos from, int depth) {
			BlockPos.MutableBlockPos p = from.mutable();
			for (int i = 0; i < depth; i++) {
				if (!level.isLoaded(p)) {
					return null;
				}
				if (FolkSpots.standable(level, p)) {
					return p.immutable();
				}
				p.move(0, -1, 0);
			}
			return null;
		}

		private static boolean inTheWay(BlockPos ground, int h, java.util.Set<Long> upcoming) {
			for (int i = 0; i < h; i++) {
				if (upcoming.contains(ground.above(i).asLong())) {
					return true;
				}
			}
			return false;
		}

		private static boolean clearColumn(ServerLevel level, BlockPos ground, int h) {
			for (int i = 0; i < h; i++) {
				BlockPos p = ground.above(i);
				BlockState s = level.getBlockState(p);
				if (!s.isAir() && !s.canBeReplaced()) {
					return false;
				}
			}
			return true;
		}

		boolean busy() {
			return this.phase == Phase.GO || this.phase == Phase.POLE || this.phase == Phase.DOWN || this.phase == Phase.TEARDOWN;
		}

		boolean done() {
			return this.phase == Phase.DONE;
		}

		boolean hasBlocks() {
			return !this.placed.isEmpty();
		}

		void dismantle() {
			if (this.phase == Phase.TOP || this.phase == Phase.POLE || this.phase == Phase.GO) {
				this.phase = this.placed.isEmpty() ? Phase.DONE : Phase.DOWN;
			}
		}

		String describe() {
			return switch (this.phase) {
				case GO -> "carrying scaffolding";
				case POLE -> "putting up scaffolding";
				case TOP -> "working from the scaffold";
				case DOWN -> "climbing down";
				case TEARDOWN -> "taking down scaffolding";
				case DONE -> "building";
			};
		}

		void tick(FolkBrain brain, ServerLevel level, int elapsed) {
			Folk folk = brain.folk;
			ScaffoldLedger ledger = brain.settlement.data(ScaffoldLedger.KEY);
			switch (this.phase) {
				case GO -> {
					brain.hold(new ItemStack(Items.SCAFFOLDING), ItemStack.EMPTY);
					if (brain.walk(level, this.base, 0.4, 0.65)) {
						brain.resetWalk();
						this.phase = Phase.POLE;
					} else if (brain.walkFailed()) {
						brain.resetWalk();
						this.phase = Phase.DONE;
					}
				}
				case POLE -> {
					folk.getNavigation().stop();
					centre(folk);
					int next = this.base.getY() + this.placed.size();
					if (this.placed.size() >= this.height) {
						this.phase = Phase.TOP;
						break;
					}
					if (folk.getY() >= next + 1.0) {
						// At the top of the jump: a scaffold underfoot.
						BlockPos p = new BlockPos(this.base.getX(), next, this.base.getZ());
						BlockState s = level.getBlockState(p);
						if (s.isAir() || s.canBeReplaced()) {
							level.setBlock(p, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
							level.playSound(null, p, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F + brain.random.nextFloat() * 0.2F);
							brain.swing();
							ledger.add(brain.resident.id, p);
							brain.settlement.markDirty();
							this.placed.add(p);
						} else {
							// Something grew or was built in the way: stop here.
							this.phase = this.placed.isEmpty() ? Phase.DONE : Phase.TOP;
						}
					} else if (folk.onGround() && --this.jumpWait <= 0) {
						folk.getJumpControl().jump();
						this.jumpWait = 3;
					}
					this.timer += elapsed;
					if (this.timer > 20 * 40) {
						// Could not get up: take down what there is.
						this.phase = this.placed.isEmpty() ? Phase.DONE : Phase.DOWN;
					}
				}
				case TOP -> {
					centre(folk);
					folk.setDeltaMovement(folk.getDeltaMovement().multiply(0, 1, 0));
				}
				case DOWN -> {
					folk.getNavigation().stop();
					centre(folk);
					folk.setShiftKeyDown(true);
					if (folk.onGround() && folk.getBlockY() <= this.base.getY()) {
						folk.setShiftKeyDown(false);
						this.phase = Phase.TEARDOWN;
						this.timer = 0;
					}
					this.timer += elapsed;
					if (this.timer > 20 * 30) {
						// Stuck on the way down: hop off at the bottom.
						folk.setShiftKeyDown(false);
						folk.teleportTo(this.base.getX() + 0.5, this.base.getY(), this.base.getZ() + 0.5);
						this.phase = Phase.TEARDOWN;
					}
				}
				case TEARDOWN -> {
					// Step off the column and take it down, top first.
					this.timer -= elapsed;
					if (this.timer > 0) {
						break;
					}
					this.timer = 3;
					if (this.placed.isEmpty()) {
						this.phase = Phase.DONE;
						break;
					}
					BlockPos top = this.placed.remove(this.placed.size() - 1);
					if (level.getBlockState(top).is(Blocks.SCAFFOLDING)) {
						level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, top, Block.getId(Blocks.SCAFFOLDING.defaultBlockState()));
						level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						level.playSound(null, top, SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
					}
					ledger.remove(brain.resident.id, top);
					brain.settlement.markDirty();
					brain.swing();
				}
				case DONE -> {
				}
			}
		}

		/** Interrupted: everything comes down at once, and the builder with it (safely). */
		void removeAll(FolkBrain brain, ServerLevel level) {
			Folk folk = brain.folk;
			folk.setShiftKeyDown(false);
			if (!this.placed.isEmpty() && folk.getY() > this.base.getY() + 0.5) {
				folk.teleportTo(this.base.getX() + 0.5, this.base.getY(), this.base.getZ() + 0.5);
			}
			ScaffoldLedger ledger = brain.settlement.data(ScaffoldLedger.KEY);
			for (int i = this.placed.size() - 1; i >= 0; i--) {
				BlockPos p = this.placed.get(i);
				if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
					level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(Blocks.SCAFFOLDING.defaultBlockState()));
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					ledger.remove(brain.resident.id, p);
				}
			}
			this.placed.clear();
			this.phase = Phase.DONE;
			brain.settlement.markDirty();
		}

		private void centre(Folk folk) {
			double cx = this.base.getX() + 0.5;
			double cz = this.base.getZ() + 0.5;
			if (Math.abs(folk.getX() - cx) > 0.05 || Math.abs(folk.getZ() - cz) > 0.05) {
				folk.setPos(cx, folk.getY(), cz);
			}
		}
	}
}
