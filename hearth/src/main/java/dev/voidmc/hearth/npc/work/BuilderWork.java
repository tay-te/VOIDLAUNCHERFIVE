package dev.voidmc.hearth.npc.work;

import com.mojang.logging.LogUtils;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.person.Office;
import dev.voidmc.hearth.npc.place.BlueprintSource;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * Builders raise their town block by block, as a crew on a building site (ARCHITECTURE.md §5.3, docs/FOLK.md §6):
 *
 * <ol>
 *   <li>Pick a plot under construction ({@link PlotState#BUILDING}) with room in its crew ({@link #crewCap}: two or three
 *   on a cottage, more on a hall); a builder with no room anywhere carries materials for the nearest site.</li>
 *   <li>Go to a place to work from — the ground, the floor inside, or the site's scaffolding — chosen so that the most
 *   steps that may be placed now ({@link StepPlan}) are within reach, and not where another builder works.</li>
 *   <li>Place every such step within reach, in the blueprint's order (supports before what rests on them), with no
 *   neighbour updates (shapes are pre-resolved), a swing, the block's sound and particles; an air step breaks what is
 *   there. Then move along.</li>
 *   <li>The site moves the plot's counter on over the done steps from the cursor with
 *   {@code settlement.advanceConstruction(plot.id(), n)} ({@link BuildSite#refresh}).</li>
 * </ol>
 *
 * Work high on the shell is reached from a ring of vanilla scaffolding round the building ({@link Staging}), put up by
 * the crew lift by lift when the work first needs it, climbed inside and walked round, and taken down once the last
 * stage with high work is done. Every scaffold block is in the settlement's {@link ScaffoldLedger}; scaffolds are never
 * blueprint steps. Between stages the builders fetch materials from the store (unless someone carries for the site).
 */
final class BuilderWork implements WorkBehaviour {
	private static final Logger LOG = LogUtils.getLogger();
	/** Eye-to-block-centre distance a builder can place at (a player's reach). */
	static final double REACH = 4.6;
	/** A step out of reach from anywhere one can stand is placed leaning out from this close (never stall a town). */
	static final double LONG_REACH = 7.0;

	private static final boolean DEBUG = Boolean.getBoolean("hearth.debug.builders");

	private int idleRounds;
	private int rounds;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		PlotView plot = pick(brain.settlement, brain.town.builderClaims, brain.resident.id, brain.folk.blockPosition());
		if (plot == null) {
			brain.town.builderClaims.remove(brain.resident.id);
			// Every site has its crew: carry materials for the nearest one, else work at the yard.
			PlotView help = nearestSite(brain.settlement, brain.folk.blockPosition());
			BlockPos from = help == null ? null : store(brain, level);
			if (help != null && from != null) {
				return new Carry(help.id(), from);
			}
			this.idleRounds++;
			ResidentPlaces p = brain.places(level);
			BlockPos look = p.workSite != null ? p.workSite.pos().relative(p.workSite.facing()) : null;
			return new Tasks.Linger(p.work, look, this.idleRounds % 2 == 0 ? FolkPose.HAMMER : FolkPose.CRAFT, 300, new ItemStack(Items.IRON_AXE),
				ItemStack.EMPTY, Tasks.every(20, SoundEvents.WOOD_HIT, 0.5F, null, true), "at the builders' yard");
		}
		brain.town.builderClaims.put(brain.resident.id, plot.id());
		// The master builder walks the other sites with the plans between stints of building.
		if (++this.rounds % 3 == 0 && brain.town.office(brain.settlement, brain.resident) == Office.MASTER_BUILDER) {
			PlotView other = inspect(brain, plot);
			if (other != null) {
				BlockPos at = ResidentPlaces.centre(other.spec()).relative(other.spec().facing(), other.spec().depth() / 2 + 1);
				return new Tasks.Linger(at, ResidentPlaces.centre(other.spec()), FolkPose.TALK, 240, new ItemStack(Items.PAPER), ItemStack.EMPTY, null,
					"directing the crew (plot " + other.id() + ")");
			}
		}
		return new Build(plot.id());
	}

	/** Another site under construction where builders are at work: where the master builder goes to look in. */
	private static @Nullable PlotView inspect(FolkBrain brain, PlotView mine) {
		PlotView best = null;
		for (PlotView p : brain.settlement.plots()) {
			if (p.id() != mine.id() && buildable(p) && brain.town.claimsOn(p.id()) > 0 && (best == null || brain.random.nextBoolean())) {
				best = p;
			}
		}
		return best;
	}

	@Override
	public void onFold(FolkBrain brain, ServerLevel level) {
		UUID me = brain.resident.id;
		brain.town.builderClaims.remove(me);
		ScaffoldLedger ledger = brain.settlement.data(ScaffoldLedger.KEY);
		for (BuildSite site : brain.town.sites.values()) {
			site.leave(me);
			site.carriers.remove(me);
			// the last of a crew folded away: the town is not left wearing its scaffolding
			if (site.hasScaffolding() && brain.town.claimsOn(site.plotId) == 0) {
				site.clearScaffolding(level, ledger);
			}
		}
		// scaffolding put up by a builder under the old rules (one tower each), if any is left
		ledger.clear(level, me);
		Walker.release(brain.folk);
		brain.settlement.markDirty();
	}

	// ---------------------------------------------------------------- crews

	/**
	 * How many builders a site takes: about one for every seven blocks of its sides (two on a 7×7 cottage, three on an
	 * 11×9 house, five on a hall), at most ten. More only stand in each other's way on the scaffolding.
	 */
	static int crewCap(PlotSpec spec) {
		return Math.clamp((spec.sizeX() + spec.sizeZ() + 3) / 7, 1, 10);
	}

	/**
	 * The plot a builder should work: the one already claimed while it is buildable and the builder is among the first
	 * {@link #crewCap} of its claimants (by id), else the nearest buildable plot with room in its crew (another builder
	 * on a plot counts as a long walk, so crews spread). Null when every site has its crew.
	 */
	static @Nullable PlotView pick(Settlement settlement, Map<UUID, Integer> claims, UUID me, BlockPos here) {
		Integer claimed = claims.get(me);
		if (claimed != null) {
			PlotView p = settlement.plot(claimed);
			if (p != null && buildable(p) && rank(claims, me, claimed) < crewCap(p.spec())) {
				return p;
			}
		}
		PlotView best = null;
		double bestScore = Double.MAX_VALUE;
		for (PlotView p : settlement.plots()) {
			if (!buildable(p)) {
				continue;
			}
			int on = claimsOn(claims, p.id(), me);
			if (on >= crewCap(p.spec())) {
				continue;
			}
			double score = Math.sqrt(p.spec().origin().distSqr(here)) + 64.0 * on;
			if (score < bestScore) {
				best = p;
				bestScore = score;
			}
		}
		return best;
	}

	/** Claimants of the plot other than {@code me}. */
	static int claimsOn(Map<UUID, Integer> claims, int plot, UUID me) {
		int n = 0;
		for (Map.Entry<UUID, Integer> e : claims.entrySet()) {
			if (e.getValue() == plot && !e.getKey().equals(me)) {
				n++;
			}
		}
		return n;
	}

	/** Claimants of the plot that come before {@code me} by id: the first {@link #crewCap} keep their place. */
	static int rank(Map<UUID, Integer> claims, UUID me, int plot) {
		int n = 0;
		for (Map.Entry<UUID, Integer> e : claims.entrySet()) {
			if (e.getValue() == plot && e.getKey().compareTo(me) < 0) {
				n++;
			}
		}
		return n;
	}

	/** The nearest buildable site (for a builder with no room in any crew to carry for). */
	static @Nullable PlotView nearestSite(Settlement settlement, BlockPos here) {
		PlotView best = null;
		double bestD = Double.MAX_VALUE;
		for (PlotView p : settlement.plots()) {
			if (buildable(p)) {
				double d = p.spec().origin().distSqr(here);
				if (d < bestD) {
					best = p;
					bestD = d;
				}
			}
		}
		return best;
	}

	static long cursor(int stage, int step) {
		return (long) stage << 32 | step & 0xFFFF_FFFFL;
	}

	/** Under construction with its current stage's goods in hand: a plot waiting for goods is left until they come. */
	static boolean buildable(PlotView p) {
		// excavations (quarry pits, mines, clay pits, plantations) are the gatherers' to work, not the builders'
		return p.state() == PlotState.BUILDING && !p.waitingForGoods() && !Extraction.is(p);
	}

	/** One step of a blueprint, as read. */
	record Step(int stage, int index, BlockPos pos, BlockState state, @Nullable CompoundTag tag) {
	}

	static @Nullable Step read(Blueprint blueprint, int stage, int index) {
		Step[] out = new Step[1];
		blueprint.step(stage, index, (x, y, z, state, tag) -> out[0] = new Step(stage, index, new BlockPos(x, y, z), state, tag));
		return out[0];
	}

	static boolean sameAs(BlockState there, BlockState wanted) {
		return StepPlan.sameAs(there, wanted);
	}

	static boolean canReach(Folk folk, BlockPos pos) {
		return folk.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
	}

	private static boolean reaches(Vec3 eye, long pos, double reach) {
		double dx = BlockPos.getX(pos) + 0.5 - eye.x;
		double dy = BlockPos.getY(pos) + 0.5 - eye.y;
		double dz = BlockPos.getZ(pos) + 0.5 - eye.z;
		return dx * dx + dy * dy + dz * dz <= reach * reach;
	}

	private static Vec3 eyeAt(BlockPos feet, double eyeHeight) {
		return new Vec3(feet.getX() + 0.5, feet.getY() + eyeHeight, feet.getZ() + 0.5);
	}

	/**
	 * A wall top or a ledge with a drop on two sides: no place to work from (builders work from the ground, a floor or
	 * the scaffolding, not balanced on the wall they are building), nor one the path can be trusted to reach.
	 */
	static boolean perch(ServerLevel level, BlockPos feet) {
		int drops = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			m.set(feet.getX() + d.getStepX(), feet.getY() - 1, feet.getZ() + d.getStepZ());
			if (FolkSpots.passable(level, m) && FolkSpots.passable(level, m.move(0, -1, 0)) && ++drops >= 2) {
				return true;
			}
		}
		return false;
	}

	/** Placing a solid block where the builder stands would bury them. */
	private static boolean inside(Folk folk, BlockPos pos, BlockState state) {
		return !state.isAir() && folk.getBoundingBox().intersects(new AABB(pos)) && !state.getCollisionShape(folk.level(), pos).isEmpty();
	}

	/** Somewhere to stand within reach of a block, not in its way (the nearest to the folk). */
	static @Nullable BlockPos standFor(ServerLevel level, BlockPos target, Folk folk) {
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

	static @Nullable BlockPos store(FolkBrain brain, ServerLevel level) {
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

	static void loadBlockEntity(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return;
		}
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(be.problemPath(), LOG)) {
			be.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), tag));
			// (not be.setChanged(): its comparator update reads the neighbours, which may be in a chunk not loaded)
			level.blockEntityChanged(pos);
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
	}

	/** About one step per {@code builder_step_ticks}, a little irregular so a crew does not move in time. */
	static final class BuilderStep {
		static int ticks(FolkBrain brain) {
			int base = FolkBrain.config().builderStepTicks();
			return Math.max(4, base - base / 4 + brain.random.nextInt(Math.max(1, base / 2)));
		}

		/** Scaffolding goes up and comes down quicker than walls. */
		static int scaffold(FolkBrain brain) {
			return Math.max(3, ticks(brain) / 3);
		}
	}

	/** In front of a site, by the street. */
	static BlockPos frontOf(PlotSpec spec) {
		return ResidentPlaces.centre(spec).relative(spec.facing(), spec.depth() / 2 + 2);
	}

	/** A place to work from: feet, and the ring column stood in ({@code -1} on the ground or a floor). */
	record Spot(BlockPos feet, int cell) {
	}

	// ---------------------------------------------------------------- building

	/** Building one plot until it is done or the day moves on. */
	private static final class Build implements Task {
		private final int plotId;
		private int lastStage = -1;
		private @Nullable BlockPos fetchFrom;
		private int fetchTicks;
		private int stepTicks;
		private int cooldown;
		private int waitedForStage;
		private String doing = "building";
		private final Walker walker = new Walker();
		private @Nullable Spot spot;
		private boolean arrived;
		private int spotTicks;
		private int spotAllowance = 600;
		private int idleAtSpot;
		private int waitTicks;
		private int placedHere;
		/** The last few decisions, for {@code -Dhearth.debug.builders=true} (shown in describe). */
		private final java.util.ArrayDeque<String> trace = new java.util.ArrayDeque<>();

		private void trace(String event) {
			if (DEBUG) {
				if (this.trace.size() >= 6) {
					this.trace.removeFirst();
				}
				this.trace.addLast(event);
			}
		}
		private long placedAt = Long.MIN_VALUE;
		/** A step no place to stand reaches: placed from {@link #LONG_REACH}. */
		private int stretch = -1;
		private int stretchStage = -1;
		private int stretchSince;
		/** The column taken for raising or taking down, and the work mode the spot was chosen for. */
		private int column = -1;
		private int spotMode;
		private final LongOpenHashSet badSpots = new LongOpenHashSet();
		/** Walks failed in a row from about the same place: a builder shut in somewhere. */
		private @Nullable BlockPos trappedAt;
		/** The plot's spec, as last read. */
		private @Nullable PlotSpec spec;
		private int trappedFails;
		/** Steps of this stage no place one could get to reached lately: passed for the next ready ones a while. */
		private final it.unimi.dsi.fastutil.ints.IntOpenHashSet badTargets = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
		/** Paths asked for in this choice of where to work (they cost: a few at most). */
		private int pathTries;
		private int badAge;
		/** Climbing down off the building: the column beside them and the ground's height there. */
		private @Nullable BlockPos descend;
		private int descendTo;

		private static final int PLACE = 0;
		private static final int RAISE = 1;
		private static final int DOWN = 2;

		Build(int plotId) {
			this.plotId = plotId;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			long now = level.getGameTime();
			Settlement settlement = brain.settlement;
			PlotView plot = settlement.plot(this.plotId);
			Blueprint bp = plot == null ? null : BlueprintSource.of(level, settlement, plot);
			BuildSite site = brain.town.site(this.plotId);
			this.cooldown -= elapsed;
			this.stepTicks += elapsed;
			if (plot != null && bp != null && buildable(plot) && plot.stage() < bp.stageCount()) {
				site.refresh(level, settlement, plot, bp, now);
			}
			if (plot != null) {
				this.spec = plot.spec();
			}
			boolean open = plot != null && bp != null && plot.state() == PlotState.BUILDING && plot.stage() < bp.stageCount();
			if (this.descend != null) {
				this.doing = "climbing down";
				if (this.walker.climbDown(brain.folk, this.descend, this.descendTo, elapsed)) {
					this.descend = null;
					this.badSpots.clear();
				}
				return Status.RUNNING;
			}
			// Scaffolding that has done its work comes down before anything else.
			if (site.staging != null && site.scaffoldDone(open ? plot : null)) {
				return teardown(brain, level, site, elapsed);
			}
			if (!open || !buildable(plot)) {
				return finish(brain, level, site);
			}
			int stage = plot.stage();
			int index = plot.step();
			if (index >= bp.stepCount(stage)) {
				// The stage is laid; the sim starts the next when its goods are in hand.
				this.waitedForStage += elapsed;
				this.doing = "waiting for materials";
				brain.folk.setFolkPose(FolkPose.NONE);
				return this.waitedForStage > 400 ? finish(brain, level, site) : Status.RUNNING;
			}
			this.waitedForStage = 0;

			// A new stage: fetch materials from the store first (unless someone carries them to the site).
			this.badAge += elapsed;
			if (this.badAge > 600) {
				// places no way was found to may be reachable now (the work moved on, a door was opened)
				this.badSpots.clear();
				this.badTargets.clear();
				this.badAge = 0;
			}
			if (stage != this.lastStage) {
				this.lastStage = stage;
				this.badSpots.clear();
				this.badTargets.clear();
				// one of the crew goes to the store for it (unless someone carries for the site); the rest work on
				this.fetchFrom = site.hasCarrier(now) || this.walker.onRing(site.staging, brain.folk) || !site.fetchFor(stage) ? null : store(brain, level);
				this.fetchTicks = 50;
				if (this.fetchFrom != null) {
					leaveSpot(brain, site);
				}
			}
			if (this.fetchFrom != null) {
				return fetch(brain, level, elapsed);
			}
			Staging st = site.staging;
			if (st != null && st.raising()) {
				return raise(brain, level, site, st, plot, bp, elapsed, now);
			}
			if (this.column >= 0) {
				site.columns.remove(brain.resident.id);
				this.column = -1;
			}
			return work(brain, level, site, plot, bp, elapsed, now);
		}

		private Status fetch(FolkBrain brain, ServerLevel level, int elapsed) {
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

		// ------------------------------------------------------------ placing the steps

		private Status work(FolkBrain brain, ServerLevel level, BuildSite site, PlotView plot, Blueprint bp, int elapsed, long now) {
			StepPlan.Scan scan = site.scan();
			if (scan == null || scan.stage() != plot.stage()) {
				this.doing = "building";
				return Status.RUNNING;
			}
			Folk folk = brain.folk;
			if (this.spot != null && this.spotMode != PLACE) {
				leaveSpot(brain, site);
			}
			if (this.spot != null && this.arrived) {
				this.walker.hold(brain, site.staging, this.spot);
				if (this.cooldown > 0) {
					return Status.RUNNING;
				}
				StepPlan.Pending p = inReach(site, scan, plot, folk);
				if (p != null) {
					BlockPos pos = p.blockPos();
					brain.hold(p.state().isAir() ? new ItemStack(Items.IRON_PICKAXE) : new ItemStack(p.state().getBlock().asItem()), ItemStack.EMPTY);
					brain.faceTowards(pos);
					this.doing = p.state().isAir() ? "clearing" : "placing " + p.state().getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
					if (place(brain, level, site, plot, bp, scan.stage(), p)) {
						site.active(now);
						this.cooldown = BuilderStep.ticks(brain);
						this.placedAt = now;
						this.placedHere++;
					} else {
						trace("X" + p.index());
						this.cooldown = 10;
					}
					this.idleAtSpot = 0;
					return Status.RUNNING;
				}
				// Nothing more within reach: once the site has read the stage again since the last block, move along.
				this.idleAtSpot += elapsed;
				if (site.scannedAt() > this.placedAt || this.idleAtSpot > 40) {
					// came and placed nothing (the work moved on, or it was out of reach after all): a pause before choosing again
					if (this.placedHere == 0) {
						this.waitTicks = 20;
						trace("I0");
					}
					leaveSpot(brain, site);
				} else {
					this.doing = this.spot.cell() >= 0 ? "working from the scaffold" : "building";
					return Status.RUNNING;
				}
			}
			if (this.spot == null) {
				if (this.waitTicks > 0) {
					this.waitTicks -= elapsed;
					return Status.RUNNING;
				}
				long t0 = System.nanoTime();
				boolean chosen = chooseSpot(brain, level, site, plot, bp, scan);
				BuildSite.CHOOSE_NANOS.add(System.nanoTime() - t0);
				if (!chosen) {
					return Status.RUNNING;
				}
			}
			return walk(brain, level, site, elapsed);
		}

		/** Walking (or climbing) to the chosen spot; there, it is held. */
		private Status walk(FolkBrain brain, ServerLevel level, BuildSite site, int elapsed) {
			this.spotTicks += elapsed;
			boolean there = this.walker.tick(brain, level, site, this.spot, elapsed);
			this.doing = this.walker.doing();
			if (there) {
				trace("A" + this.spot.feet().getY());
				this.arrived = true;
				this.idleAtSpot = 0;
				this.placedHere = 0;
				this.doing = this.spot.cell() >= 0 ? "working from the scaffold" : "building";
				site.arrived.add(brain.resident.id);
				return Status.RUNNING;
			}
			if (this.walker.failed() || this.spotTicks > this.spotAllowance) {
				trace(this.walker.failed() ? "F" + this.walker.why() : "T");
				this.badSpots.add(this.spot.feet().asLong());
				leaveSpot(brain, site);
				BlockPos now = brain.folk.blockPosition();
				this.trappedFails = this.trappedAt != null && this.trappedAt.closerThan(now, 1.5) ? this.trappedFails + 1 : 1;
				this.trappedAt = now;
				if (this.trappedFails >= 6) {
					// shut in (a room whose way out was walled up, a loft whose hatch is under a block): out by the front
					// rather than stand there for good. A last resort: half a minute of getting nowhere first
					BlockPos front = this.spec == null ? null : FolkSpots.standableNear(level, frontOf(this.spec), 4);
					if (front != null) {
						trace("O");
						brain.folk.getNavigation().stop();
						Walker.release(brain.folk);
						brain.folk.teleportTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
					}
					this.trappedFails = 0;
					this.badSpots.clear();
					return Status.RUNNING;
				}
				if (!this.walker.onRing(site.staging, brain.folk) && startDescent(level, brain.folk)) {
					trace("D");
				}
			}
			return Status.RUNNING;
		}

		/** The first step that may be placed now and is within reach of where the builder stands. */
		private StepPlan.@Nullable Pending inReach(BuildSite site, StepPlan.Scan scan, PlotView plot, Folk folk) {
			Vec3 eye = folk.getEyePosition();
			int cursor = plot.step();
			for (StepPlan.Pending p : scan.pending()) {
				if (!p.ready() || p.index() < cursor) {
					continue;
				}
				// a step nothing reaches: from close by, and after a fair try from wherever they are (never stall a town)
				double reach = p.index() != this.stretch || scan.stage() != this.stretchStage ? REACH + 0.3
					: this.stepTicks - this.stretchSince > 200 ? 12 : LONG_REACH;
				if (!reaches(eye, p.pos(), reach)) {
					continue;
				}
				BlockPos pos = p.blockPos();
				if (!folk.level().isLoaded(pos) || StepPlan.done(folk.level().getBlockState(pos), p.state(), site.isPlaced(scan.stage(), p.index()))
					|| inside(folk, pos, p.state())) {
					continue;
				}
				return p;
			}
			return null;
		}

		/**
		 * Chooses where to work from: for the first step that may be placed now and that no other builder has within
		 * reach, the place (on the ground, a floor, or the scaffolding as it stands) from which the most such steps are
		 * in reach. If only scaffolding not yet up would reach it, the ring is asked to go higher (and the crew raises it);
		 * if nothing would, it is placed from as close as one can stand. False while there is nothing to go to.
		 */
		private boolean chooseSpot(FolkBrain brain, ServerLevel level, BuildSite site, PlotView plot, Blueprint bp, StepPlan.Scan scan) {
			Folk folk = brain.folk;
			UUID me = brain.resident.id;
			double eyeH = folk.getEyeHeight();
			List<StepPlan.Pending> ready = new ArrayList<>();
			for (StepPlan.Pending p : scan.pending()) {
				if (p.ready() && p.index() >= plot.step() && !site.takenByOther(me, p.pos(), eyeH)) {
					ready.add(p);
					if (ready.size() >= 256) {
						break;
					}
				}
			}
			if (ready.isEmpty()) {
				trace("W" + scan.pending().size());
				this.doing = "waiting for the crew";
				folk.setFolkPose(FolkPose.NONE);
				this.waitTicks = 10;
				return false;
			}
			// the first ready steps in turn (a step no place one can get to reaches is passed for the next: steps may be
			// placed ahead, so one awkward block never holds the crew up)
			this.pathTries = 0;
			int tried = 0;
			StepPlan.Pending target = null;
			for (StepPlan.Pending p : ready) {
				if (this.badTargets.contains(p.index())) {
					continue;
				}
				if (target == null) {
					target = p;
				}
				Spot best = spotFor(brain, level, site, p, ready);
				if (best != null) {
					setSpot(brain, site, best, PLACE);
					return true;
				}
				this.badTargets.add(p.index());
				if (++tried >= 4 || this.pathTries >= 8) {
					break;
				}
			}
			if (target == null) {
				// every ready step has been tried and passed: the first of them again, by the ring or a long reach
				target = ready.get(0);
				this.badTargets.clear();
			}
			BlockPos t = target.blockPos();
			Staging st = site.staging;
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			// Out of reach from anywhere one can stand now: the scaffolding, if a ring can serve it.
			Staging ring = st != null ? st : site.staging(level, bp, plot.spec());
			if (ring == null) {
				trace("N");
			} else {
				int need = feetFor(ring, t, eyeH);
				if (need != Staging.NONE && ring.require(need) && ring.raising()) {
					trace("R" + need);
					this.doing = "putting up scaffolding";
					return false;
				}
				trace("r" + (need == Staging.NONE ? "-" : need - ring.minBase));
			}
			// Nowhere: placed leaning out from the nearest place one can stand (never stall a town over one block).
			if (this.stretch != target.index() || this.stretchStage != scan.stage()) {
				this.stretch = target.index();
				this.stretchStage = scan.stage();
				this.stretchSince = this.stepTicks;
			}
			trace("S" + target.index() + "@" + t.getX() % 100 + "," + t.getY() + "," + t.getZ() % 100);
			BlockPos feetNow = folk.blockPosition();
			boolean onIt = inside(folk, t, target.state()) || t.equals(feetNow) || t.equals(feetNow.above());
			if (!onIt && reaches(folk.getEyePosition(), target.pos(), LONG_REACH - 0.3) && !this.walker.onRing(site.staging, folk)) {
				// within a long reach of where they stand: no need to go anywhere
				setSpot(brain, site, new Spot(folk.blockPosition(), -1), PLACE);
				return true;
			}
			// the nearest places one can stand within a long reach, crowded or not, the first that can be got to
			List<Spot> near = new ArrayList<>();
			List<Double> closeness = new ArrayList<>();
			for (int dy = -6; dy <= 2; dy++) {
				for (int dx = -4; dx <= 4; dx++) {
					for (int dz = -4; dz <= 4; dz++) {
						m.set(t.getX() + dx, t.getY() + dy, t.getZ() + dz);
						if (!reaches(eyeAt(m, eyeH), target.pos(), LONG_REACH) || !level.isLoaded(m) || this.badSpots.contains(m.asLong())
							|| site.pendingAt(m.asLong()) || site.pendingAt(BlockPos.asLong(m.getX(), m.getY() + 1, m.getZ())) || !FolkSpots.standable(level, m)) {
							continue;
						}
						near.add(new Spot(m.immutable(), -1));
						closeness.add(-m.distSqr(t));
					}
				}
			}
			Staging ringNow = site.staging;
			if (ringNow != null) {
				// or from the scaffolding (the ridge of a roof, leaning in from the ring's top)
				for (int c = 0; c < ringNow.n; c++) {
					if (!ringNow.valid(c)) {
						continue;
					}
					for (int f = t.getY() - 6; f <= t.getY() + 2; f++) {
						BlockPos feet = new BlockPos(ringNow.xs[c], f, ringNow.zs[c]);
						if (ringNow.supports(c, f) && reaches(eyeAt(feet, eyeH), target.pos(), LONG_REACH) && !this.badSpots.contains(feet.asLong())) {
							near.add(new Spot(feet, c));
							closeness.add(-feet.distSqr(t) + 2.0);
						}
					}
				}
			}
			Spot lean = reachable(brain, site, near, closeness);
			if (lean == null && !this.walker.onRing(site.staging, folk) && startDescent(level, folk)) {
				// up on the building with no way on from here: down the wall first, then choose again
				trace("D");
				return false;
			}
			if (lean == null && folk.getEyePosition().distanceToSqr(Vec3.atCenterOf(t)) > 10 * 10) {
				// far from it (back from an errand, or the way in is shut): to the front of the site first, then choose again
				BlockPos front = FolkSpots.standableNear(level, frontOf(plot.spec()), 4);
				if (front != null && !this.badSpots.contains(front.asLong())) {
					trace("G");
					setSpot(brain, site, new Spot(front, -1), PLACE);
					return true;
				}
			}
			if (lean == null && onIt) {
				// standing where it goes: a step aside
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					BlockPos aside = feetNow.relative(d);
					if (!aside.equals(t) && !aside.above().equals(t) && FolkSpots.standable(level, aside)) {
						lean = new Spot(aside, -1);
						break;
					}
				}
			}
			setSpot(brain, site, lean != null ? lean : new Spot(feetNow, -1), PLACE);
			return true;
		}

		/**
		 * The place to work a step from: on the ground, a floor, or the scaffolding as it stands, whichever reaches the most
		 * ready steps (a little for staying up on the scaffolding once there), not where another builder works, and that
		 * can be got to. Null if none.
		 */
		private @Nullable Spot spotFor(FolkBrain brain, ServerLevel level, BuildSite site, StepPlan.Pending target, List<StepPlan.Pending> ready) {
			Folk folk = brain.folk;
			UUID me = brain.resident.id;
			double eyeH = folk.getEyeHeight();
			BlockPos t = target.blockPos();
			List<Spot> spots = new ArrayList<>();
			List<Double> scores = new ArrayList<>();
			// on the ground, a floor, a wall top: anywhere one can stand within reach
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int dy = -5; dy <= 1; dy++) {
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						m.set(t.getX() + dx, t.getY() + dy, t.getZ() + dz);
						Vec3 eye = eyeAt(m, eyeH);
						if (!reaches(eye, target.pos(), REACH) || !level.isLoaded(m) || this.badSpots.contains(m.asLong())) {
							continue;
						}
						if (site.pendingAt(m.asLong()) || site.pendingAt(BlockPos.asLong(m.getX(), m.getY() + 1, m.getZ())) || !FolkSpots.standable(level, m)
							|| site.crowded(me, m, 2.5) || perch(level, m)) {
							continue;
						}
						spots.add(new Spot(m.immutable(), -1));
						scores.add(served(eye, ready) - 0.06 * Math.sqrt(m.distSqr(folk.blockPosition())));
					}
				}
			}
			// on the scaffolding, at any height it stands to
			Staging st = site.staging;
			if (st != null) {
				for (int c = 0; c < st.n; c++) {
					if (!st.valid(c) || Mth.square(st.xs[c] - t.getX()) + Mth.square(st.zs[c] - t.getZ()) > REACH * REACH) {
						continue;
					}
					for (int f = t.getY() - 5; f <= t.getY() + 2; f++) {
						BlockPos feet = new BlockPos(st.xs[c], f, st.zs[c]);
						Vec3 eye = eyeAt(feet, eyeH);
						if (!st.supports(c, f) || !reaches(eye, target.pos(), REACH) || site.crowded(me, feet, 2.5) || this.badSpots.contains(feet.asLong())) {
							continue;
						}
						// a little for the scaffolding: once up there, staying up beats coming down
						spots.add(new Spot(feet, c));
						scores.add(served(eye, ready) - 0.06 * Math.sqrt(feet.distSqr(folk.blockPosition())) + (this.walker.onRing(st, folk) ? 0.5 : -0.5));
					}
				}
			}
			if (spots.isEmpty()) {
				// every place by it is near another builder: better to work side by side than not at all
				for (int dy = -5; dy <= 1; dy++) {
					for (int dx = -3; dx <= 3; dx++) {
						for (int dz = -3; dz <= 3; dz++) {
							m.set(t.getX() + dx, t.getY() + dy, t.getZ() + dz);
							Vec3 eye = eyeAt(m, eyeH);
							if (!reaches(eye, target.pos(), REACH) || !level.isLoaded(m) || this.badSpots.contains(m.asLong()) || site.pendingAt(m.asLong())
								|| site.pendingAt(BlockPos.asLong(m.getX(), m.getY() + 1, m.getZ())) || !FolkSpots.standable(level, m) || site.crowded(me, m, 0.9) || perch(level, m)) {
								continue;
							}
							spots.add(new Spot(m.immutable(), -1));
							scores.add(served(eye, ready) - 0.06 * Math.sqrt(m.distSqr(folk.blockPosition())));
						}
					}
				}
			}
			Spot best = reachable(brain, site, spots, scores);
			trace("C" + spots.size() + (best == null ? "x" : best.cell() >= 0 ? "r" : "g"));
			return best;
		}

		/**
		 * The best of the candidate spots that can be got to: a floor or wall top the path does not climb to is no use. The
		 * best three are tried with the pathfinder (to the spot, or to the foot of its scaffold column); the path found is
		 * the one walked. Up on the scaffolding (or in the air) the walker gets about the ring by hand, so the best is taken.
		 */
		private @Nullable Spot reachable(FolkBrain brain, BuildSite site, List<Spot> spots, List<Double> scores) {
			Folk folk = brain.folk;
			Integer[] order = new Integer[spots.size()];
			for (int i = 0; i < order.length; i++) {
				order[i] = i;
			}
			java.util.Arrays.sort(order, (a, b) -> Double.compare(scores.get(b), scores.get(a)));
			Staging st = site.staging;
			boolean byHand = this.walker.onRing(st, folk) || !folk.onGround();
			int tries = 0;
			for (int i : order) {
				Spot s = spots.get(i);
				if (byHand) {
					return s;
				}
				BlockPos to = s.cell() >= 0 && st != null ? new BlockPos(st.xs[s.cell()], st.base[s.cell()], st.zs[s.cell()]) : s.feet();
				if (to.distSqr(folk.blockPosition()) <= 1) {
					// already there
					return s;
				}
				if (to.distSqr(folk.blockPosition()) > 24 * 24) {
					// too far to test (paths are short): walk toward it; the walk finds out on the way
					return s;
				}
				// (into a scaffold column's foot from beside it is enough: the walker steps in under it)
				if (this.pathTries >= 8) {
					break;
				}
				this.pathTries++;
				net.minecraft.world.level.pathfinder.Path path = folk.getNavigation().createPath(to, s.cell() >= 0 ? 1 : 0);
				if (path != null && path.canReach()) {
					folk.getNavigation().moveTo(path, 0.65);
					return s;
				}
				this.badSpots.add(s.feet().asLong());
				trace("P!");
				if (++tries >= 4) {
					break;
				}
			}
			return null;
		}

		/**
		 * Up on a wall top or a roof (the ground more than two blocks down): picks the side to climb down, an open drop
		 * to standing ground beside them. False on the ground or with nowhere to climb down.
		 */
		private boolean startDescent(ServerLevel level, Folk folk) {
			BlockPos feet = folk.blockPosition();
			BlockPos best = null;
			int bestY = Integer.MIN_VALUE;
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos side = feet.relative(d);
				if (!level.isLoaded(side) || !FolkSpots.passable(level, side) || !FolkSpots.passable(level, side.above())) {
					continue;
				}
				for (int dy = 1; dy <= 20; dy++) {
					BlockPos p = side.below(dy);
					if (!level.isLoaded(p)) {
						break;
					}
					if (FolkSpots.standable(level, p)) {
						if (dy > 2 && p.getY() > bestY) {
							best = side;
							bestY = p.getY();
						}
						break;
					}
					if (!FolkSpots.passable(level, p)) {
						break;
					}
				}
			}
			if (best == null) {
				return false;
			}
			this.descend = best;
			this.descendTo = bestY;
			return true;
		}

		/** The lowest feet height on the ring from which a block is within reach, or NONE. */
		private static int feetFor(Staging st, BlockPos t, double eyeH) {
			int best = Staging.NONE;
			for (int c = 0; c < st.n; c++) {
				if (!st.valid(c)) {
					continue;
				}
				double h2 = Mth.square(st.xs[c] - t.getX()) + Mth.square(st.zs[c] - t.getZ());
				if (h2 > (REACH - 0.2) * (REACH - 0.2)) {
					continue;
				}
				double v = Math.sqrt(REACH * REACH - h2) - 0.2;
				int f = Math.max(st.base[c], Mth.ceil(t.getY() + 0.5 - eyeH - v));
				if (f <= st.cap[c] + 1 && (best == Staging.NONE || f < best)) {
					best = f;
				}
			}
			return best;
		}

		private static int served(Vec3 eye, List<StepPlan.Pending> ready) {
			int n = 0;
			for (StepPlan.Pending p : ready) {
				if (reaches(eye, p.pos(), REACH)) {
					n++;
				}
			}
			return n;
		}

		private void setSpot(FolkBrain brain, BuildSite site, Spot s, int mode) {
			this.spot = s;
			this.spotMode = mode;
			this.arrived = false;
			this.spotTicks = 0;
			// time to get there: a walk across town takes a while, a step along the scaffold does not
			this.spotAllowance = 300 + (int) (12 * Math.sqrt(s.feet().distSqr(brain.folk.blockPosition())));
			this.walker.reset();
			// (a path that failed to an earlier spot must not count against this one)
			brain.resetWalk();
			site.arrived.remove(brain.resident.id);
			site.spots.put(brain.resident.id, s.feet().asLong());
		}

		private void leaveSpot(FolkBrain brain, BuildSite site) {
			this.spot = null;
			this.arrived = false;
			site.spots.remove(brain.resident.id);
			site.arrived.remove(brain.resident.id);
			if (this.column >= 0) {
				site.columns.remove(brain.resident.id);
				this.column = -1;
			}
		}

		/**
		 * Places a pending step if it is still to do and still ahead of (or at) the counter: the contract reads the plot's
		 * {@code (stage, step)} at the moment of placing. The site moves the counter on.
		 */
		private boolean place(FolkBrain brain, ServerLevel level, BuildSite site, PlotView plot, Blueprint bp, int stage, StepPlan.Pending p) {
			if (plot.stage() != stage || p.index() < plot.step() || p.index() >= bp.stepCount(stage)) {
				return false;
			}
			BlockPos pos = p.blockPos();
			BlockState state = p.state();
			BlockState old = level.getBlockState(pos);
			if (StepPlan.done(old, state, site.isPlaced(stage, p.index()))) {
				site.changed();
				return false;
			}
			// Never wall a player in.
			if (!state.getCollisionShape(level, pos).isEmpty() && !level.getEntitiesOfClass(Player.class, new AABB(pos), pl -> !pl.isSpectator()).isEmpty()) {
				return false;
			}
			if (old.is(Blocks.SCAFFOLDING)) {
				ScaffoldLedger ledger = brain.settlement.data(ScaffoldLedger.KEY);
				ledger.remove(site.ledgerId, pos);
				ledger.remove(brain.resident.id, pos);
				brain.settlement.markDirty();
			}
			brain.swing();
			if (state.isAir()) {
				if (!old.isAir()) {
					level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(old));
					level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
				}
			} else {
				level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
				if (p.tag() != null) {
					loadBlockEntity(level, pos, state, p.tag());
				}
				SoundType sound = state.getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
				Vec3 c = Vec3.atCenterOf(pos);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.02);
			}
			site.placed(stage, p.index());
			return true;
		}

		// ------------------------------------------------------------ scaffolding

		/**
		 * Putting the ring up to the height asked for, a column at a time. From the ground a builder takes the nearest column
		 * still short that nobody else has, stands beside its foot and runs it up to the top (as clicking the foot of a
		 * vanilla scaffold column extends it at the top); one already up on the ring runs up the columns in reach of where
		 * they stand. With every short column in hand, the rest get on with the walls.
		 */
		private Status raise(FolkBrain brain, ServerLevel level, BuildSite site, Staging st, PlotView plot, Blueprint bp, int elapsed, long now) {
			Folk folk = brain.folk;
			UUID me = brain.resident.id;
			if (this.column >= 0 && !st.isShort(this.column)) {
				site.columns.remove(me);
				this.column = -1;
				if (this.spotMode == RAISE) {
					leaveSpot(brain, site);
				}
			}
			if (this.column < 0 && this.walker.onRing(st, folk)) {
				// up on the ring: the columns within reach of here, from here
				int c = reachableColumn(st, folk);
				if (c < 0) {
					return work(brain, level, site, plot, bp, elapsed, now);
				}
				if (this.spot != null && this.arrived) {
					this.walker.hold(brain, st, this.spot);
				}
				if (this.cooldown <= 0) {
					brain.hold(new ItemStack(Items.SCAFFOLDING), ItemStack.EMPTY);
					raiseOne(brain, level, site, st, c, now);
				}
				return Status.RUNNING;
			}
			if (this.column < 0) {
				int best = -1;
				double bestD = Double.MAX_VALUE;
				for (int c = 0; c < st.n; c++) {
					if (!st.isShort(c) || site.columns.containsValue(c)) {
						continue;
					}
					double d = Mth.square(st.xs[c] + 0.5 - folk.getX()) + Mth.square(st.zs[c] + 0.5 - folk.getZ());
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
				if (best < 0) {
					// the rest of the ring is in hand
					return work(brain, level, site, plot, bp, elapsed, now);
				}
				if (this.spot != null) {
					leaveSpot(brain, site);
				}
				List<BlockPos> stand = beside(level, st, best);
				BlockPos at = stand.isEmpty() ? FolkSpots.standableNear(level, new BlockPos(st.xs[best], st.base[best], st.zs[best]), 2) : stand.get(0);
				if (at == null) {
					st.skipped[best] = true;
					return Status.RUNNING;
				}
				this.column = best;
				site.columns.put(me, best);
				setSpot(brain, site, new Spot(at, -1), RAISE);
			}
			brain.hold(new ItemStack(Items.SCAFFOLDING), ItemStack.EMPTY);
			int c = this.column;
			if (!this.arrived) {
				this.spotTicks += elapsed;
				if (this.walker.tick(brain, level, site, this.spot, elapsed)) {
					this.arrived = true;
					site.arrived.add(me);
				} else if (this.walker.failed() || this.spotTicks > this.spotAllowance) {
					BlockPos foot = new BlockPos(st.xs[c], st.base[c], st.zs[c]);
					if (foot.distSqr(folk.blockPosition()) <= 6 * 6 && !this.walker.onRing(st, folk)) {
						// close enough: run it up from here
						this.arrived = true;
					} else {
						trace("K" + c);
						st.skipped[c] = true;
						site.columns.remove(me);
						this.column = -1;
						leaveSpot(brain, site);
					}
				}
				this.doing = this.walker.onRing(st, folk) ? this.walker.doing() : "carrying scaffolding";
				return Status.RUNNING;
			}
			this.doing = "putting up scaffolding";
			if (this.cooldown > 0) {
				return Status.RUNNING;
			}
			if (!raiseOne(brain, level, site, st, c, now) && st.isShort(c)) {
				// not free there after all (or its chunk is gone): left short
				st.skipped[c] = true;
			}
			return Status.RUNNING;
		}

		/** One scaffold block on top of column {@code c}. */
		private boolean raiseOne(FolkBrain brain, ServerLevel level, BuildSite site, Staging st, int c, long now) {
			BlockPos at = new BlockPos(st.xs[c], st.height[c] + 1, st.zs[c]);
			brain.faceTowards(at);
			this.doing = "putting up scaffolding";
			this.cooldown = BuilderStep.scaffold(brain);
			if (!st.raise(level, brain.settlement.data(ScaffoldLedger.KEY), site.ledgerId, c)) {
				return false;
			}
			brain.swing();
			level.playSound(null, at, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F + brain.random.nextFloat() * 0.2F);
			brain.settlement.markDirty();
			site.active(now);
			return true;
		}

		/** The short column whose next block is lowest within reach of where the builder stands (never over their own head). */
		private static int reachableColumn(Staging st, Folk folk) {
			Vec3 eye = folk.getEyePosition();
			int here = Walker.ringCell(st, folk);
			int best = -1;
			int bestY = Integer.MAX_VALUE;
			for (int c = 0; c < st.n; c++) {
				if (!st.isShort(c)) {
					continue;
				}
				int y = st.height[c] + 1;
				if (c == here || !reaches(eye, BlockPos.asLong(st.xs[c], y, st.zs[c]), REACH)) {
					continue;
				}
				if (folk.getBoundingBox().intersects(new AABB(st.xs[c], y, st.zs[c], st.xs[c] + 1, y + 1, st.zs[c] + 1))) {
					continue;
				}
				if (y < bestY) {
					bestY = y;
					best = c;
				}
			}
			return best;
		}

		/** Standing places on the ground right beside a column, outside the ring first. */
		private static List<BlockPos> beside(ServerLevel level, Staging st, int c) {
			List<BlockPos> out = new ArrayList<>(2);
			int x = st.xs[c];
			int z = st.zs[c];
			int ox = x < st.shellMinX ? -1 : x > st.shellMaxX ? 1 : 0;
			int oz = z < st.shellMinZ ? -1 : z > st.shellMaxZ ? 1 : 0;
			for (int sign = 1; sign >= -1; sign -= 2) {
				for (int dy = 0; dy >= -1 && out.size() < 2; dy--) {
					BlockPos p = new BlockPos(x + ox * sign, st.base[c] + dy, z + oz * sign);
					if (level.isLoaded(p) && st.cellAt(p.getX(), p.getZ()) < 0 && FolkSpots.standable(level, p)) {
						out.add(p);
						break;
					}
				}
				if (sign == 1 && out.isEmpty()) {
					// up a step outside
					BlockPos p = new BlockPos(x + ox, st.base[c] + 1, z + oz);
					if (level.isLoaded(p) && FolkSpots.standable(level, p)) {
						out.add(p);
					}
				}
			}
			return out;
		}

		/** Taking the ring down, column by column from the ground (pulling out the foot brings a column down). */
		private Status teardown(FolkBrain brain, ServerLevel level, BuildSite site, int elapsed) {
			Staging st = site.staging;
			Folk folk = brain.folk;
			if (st == null || !st.standing()) {
				site.scaffoldGone();
				leaveSpot(brain, site);
				return Status.RUNNING;
			}
			if (this.spot != null && this.spotMode != DOWN) {
				leaveSpot(brain, site);
			}
			brain.hold(ItemStack.EMPTY, ItemStack.EMPTY);
			this.doing = "taking down scaffolding";
			UUID me = brain.resident.id;
			if (this.spot != null && this.arrived) {
				if (this.cooldown > 0) {
					return Status.RUNNING;
				}
				if (this.column >= 0 && st.height[this.column] >= st.base[this.column]) {
					brain.faceTowards(new BlockPos(st.xs[this.column], st.base[this.column], st.zs[this.column]));
					brain.swing();
					st.takeDown(level, brain.settlement.data(ScaffoldLedger.KEY), site.ledgerId, this.column);
					brain.settlement.markDirty();
					this.cooldown = BuilderStep.scaffold(brain) * 3;
				}
				leaveSpot(brain, site);
				return Status.RUNNING;
			}
			if (this.spot == null) {
				// the nearest column still standing that nobody has taken
				int best = -1;
				double bestD = Double.MAX_VALUE;
				for (int c = 0; c < st.n; c++) {
					if (!st.valid(c) || st.height[c] < st.base[c] || site.columns.containsValue(c)) {
						continue;
					}
					double d = Mth.square(st.xs[c] + 0.5 - folk.getX()) + Mth.square(st.zs[c] + 0.5 - folk.getZ());
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
				if (best < 0) {
					this.doing = "taking down scaffolding";
					return Status.RUNNING;
				}
				List<BlockPos> stand = beside(level, st, best);
				BlockPos at = stand.isEmpty() ? null : stand.get(0);
				if (at == null || this.badSpots.contains(at.asLong())) {
					// nowhere to stand by it: brought down from where they are
					st.takeDown(level, brain.settlement.data(ScaffoldLedger.KEY), site.ledgerId, best);
					brain.swing();
					brain.settlement.markDirty();
					return Status.RUNNING;
				}
				setSpot(brain, site, new Spot(at, -1), DOWN);
				this.column = best;
				site.columns.put(me, best);
			}
			Status r = walk(brain, level, site, elapsed);
			if (this.walker.onRing(st, folk)) {
				this.doing = "climbing down the scaffold";
			} else if (!this.arrived) {
				this.doing = "taking down scaffolding";
			}
			return r;
		}

		private Status finish(FolkBrain brain, ServerLevel level, BuildSite site) {
			leaveSpot(brain, site);
			this.walker.leave(brain, site.staging);
			brain.town.builderClaims.remove(brain.resident.id);
			return Status.DONE;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
			if (this.descend != null) {
				// stopped half way down the wall: at the foot of it
				brain.folk.teleportTo(this.descend.getX() + 0.5, this.descendTo, this.descend.getZ() + 0.5);
				this.descend = null;
				Walker.release(brain.folk);
			}
			BuildSite site = brain.town.sites.get(this.plotId);
			if (site != null) {
				leaveSpot(brain, site);
				this.walker.leave(brain, site.staging);
			} else {
				Walker.release(brain.folk);
			}
		}

		@Override
		public boolean creditedBySteps() {
			return true;
		}

		@Override
		public String describe() {
			Spot s = this.spot;
			return this.doing + " (plot " + this.plotId + ")" + (s == null || this.arrived ? "" : " to " + s.feet().toShortString() + (s.cell() >= 0 ? " on the ring" : ""))
				+ (DEBUG ? " {" + String.join(" ", this.trace) + (s == null ? " nospot" : this.arrived ? " at" : " going") + " w" + this.waitTicks + "}" : "");
		}
	}

	// ---------------------------------------------------------------- carrying

	/**
	 * A builder with no room in any crew carries materials for the nearest site: from the store to the site's front and
	 * back, three loads, while the crew there need not fetch their own.
	 */
	private static final class Carry implements Task {
		private final int plotId;
		private final BlockPos store;
		private int phase;
		private int ticks;
		private int walked;
		private int trips;
		private ItemStack load = new ItemStack(Items.OAK_PLANKS);

		Carry(int plotId, BlockPos store) {
			this.plotId = plotId;
			this.store = store;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			PlotView plot = brain.settlement.plot(this.plotId);
			if (plot == null || !buildable(plot)) {
				return Status.DONE;
			}
			BuildSite site = brain.town.site(this.plotId);
			site.carriers.put(brain.resident.id, level.getGameTime() + 400);
			this.walked += elapsed;
			switch (this.phase) {
				case 0 -> {
					brain.hold(ItemStack.EMPTY, ItemStack.EMPTY);
					if (brain.walk(level, this.store, 1.8, 0.6) || brain.walkFailed() || this.walked > 900) {
						brain.resetWalk();
						brain.faceTowards(this.store);
						level.playSound(null, this.store, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
						brain.swing();
						this.phase = 1;
						this.ticks = 40;
					}
				}
				case 1 -> {
					this.ticks -= elapsed;
					if (this.ticks <= 0) {
						level.playSound(null, this.store, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
						this.load = loadFor(site);
						brain.hold(this.load, ItemStack.EMPTY);
						this.phase = 2;
						this.walked = 0;
					}
				}
				case 2 -> {
					brain.hold(this.load, ItemStack.EMPTY);
					BlockPos drop = dropAt(plot.spec());
					if (brain.walk(level, drop, 2.0, 0.6) || brain.walkFailed() || this.walked > 900) {
						brain.resetWalk();
						brain.faceTowards(ResidentPlaces.centre(plot.spec()));
						brain.folk.setFolkPose(FolkPose.CRAFT);
						this.phase = 3;
						this.ticks = 30;
					}
				}
				default -> {
					this.ticks -= elapsed;
					if (this.ticks <= 0) {
						brain.swing();
						level.playSound(null, brain.folk.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F);
						brain.folk.setFolkPose(FolkPose.NONE);
						brain.hold(ItemStack.EMPTY, ItemStack.EMPTY);
						this.phase = 0;
						this.walked = 0;
						if (++this.trips >= 3) {
							return Status.DONE;
						}
					}
				}
			}
			return Status.RUNNING;
		}

		/** What the crew will lay next. */
		private static ItemStack loadFor(BuildSite site) {
			StepPlan.Scan scan = site.scan();
			if (scan != null) {
				for (StepPlan.Pending p : scan.pending()) {
					if (!p.state().isAir() && p.state().getBlock().asItem() != Items.AIR) {
						return new ItemStack(p.state().getBlock().asItem());
					}
				}
			}
			return new ItemStack(Items.OAK_PLANKS);
		}

		/** In front of the site, by the street. */
		private static BlockPos dropAt(PlotSpec spec) {
			return ResidentPlaces.centre(spec).relative(spec.facing(), spec.depth() / 2 + 2);
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
			BuildSite site = brain.town.sites.get(this.plotId);
			if (site != null) {
				site.carriers.remove(brain.resident.id);
			}
		}

		@Override
		public boolean creditedBySteps() {
			return true;
		}

		@Override
		public String describe() {
			return "carrying materials (plot " + this.plotId + ")";
		}
	}
}
