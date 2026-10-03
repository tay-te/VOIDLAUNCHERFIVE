package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Trades that work outside the walls. Foresters walk out to the woods and fell (the felling itself is abstract: the
 * sim credits the wood; here the axe rings on a real trunk), quarriers work the stone, hunters and herders range the
 * edges and pastures, fishers sit at the water's edge, couriers go to and from the gate with letters. Beyond what a
 * player can see it is all the sim's numbers; what is near is people at work.
 */
final class FieldWork implements WorkBehaviour {
	private final JobType job;
	private int trips;

	FieldWork(JobType job) {
		this.job = job;
	}

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		this.trips++;
		return switch (this.job) {
			case FORESTER -> forester(brain, level);
			case QUARRIER -> quarrier(brain, level, p);
			case FISHER -> fisher(brain, level, p);
			case HERDER -> {
				BlockPos centre = pasture(brain).orElse(p.work);
				yield this.trips % 3 == 0
					? new Tasks.Linger(centre, null, FolkPose.TEND, 200, new ItemStack(Items.WHEAT), ItemStack.EMPTY, null, "with the herd")
					: new WanderHolding(centre, 10, new ItemStack(Items.WHEAT), "minding the herd");
			}
			case HUNTER -> new WanderHolding(edge(brain, level, 20), 16, new ItemStack(Items.BOW), "out hunting");
			case COURIER -> {
				List<Poi> gates = brain.town.places.all(level, brain.settlement, PoiType.GATE);
				BlockPos gate = gates.isEmpty() ? edge(brain, level, 4) : gates.get(brain.random.nextInt(gates.size())).pos();
				yield new Tasks.Sequence("carrying letters",
					new Tasks.Linger(gate, null, FolkPose.NONE, 200, new ItemStack(Items.PAPER), ItemStack.EMPTY, null, "at the gate"),
					new Tasks.Linger(brain.settlement.center(), null, FolkPose.NONE, 200, new ItemStack(Items.PAPER), ItemStack.EMPTY, null,
						"delivering letters"));
			}
			default -> null;
		};
	}

	private Task forester(FolkBrain brain, ServerLevel level) {
		BlockPos out = edge(brain, level, 14);
		BlockPos log = findNear(level, out, 8, s -> s.is(BlockTags.LOGS));
		if (log == null) {
			return new WanderHolding(out, 10, new ItemStack(Items.IRON_AXE), "looking for timber");
		}
		BlockPos stand = FolkSpots.standableNear(level, log, 2);
		BlockState wood = level.getBlockState(log);
		Tasks.Effect chop = Tasks.every(16, SoundEvents.WOOD_HIT, 0.8F, (b, lvl, e, at) -> {
			Vec3 c = Vec3.atCenterOf(log);
			lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wood), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.05);
		}, true);
		return new Tasks.Linger(stand != null ? stand : out, log, FolkPose.HAMMER, 600, new ItemStack(Items.IRON_AXE), ItemStack.EMPTY, chop, "felling timber");
	}

	private Task quarrier(FolkBrain brain, ServerLevel level, ResidentPlaces p) {
		BlockPos at = p.workSite != null ? p.workSite.pos() : edge(brain, level, 10);
		BlockPos rock = findNear(level, at, 6, s -> s.is(BlockTags.BASE_STONE_OVERWORLD));
		BlockPos face = rock != null ? rock : at.below();
		BlockState stone = level.getBlockState(face);
		Tasks.Effect pick = Tasks.every(16, SoundEvents.STONE_HIT, 0.8F, (b, lvl, e, x) -> {
			Vec3 c = Vec3.atCenterOf(face);
			lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, stone), c.x, c.y + 0.5, c.z, 6, 0.3, 0.2, 0.3, 0.05);
		}, true);
		BlockPos stand = rock != null ? FolkSpots.standableNear(level, rock, 2) : at;
		return new Tasks.Linger(stand != null ? stand : at, face, FolkPose.HAMMER, 600, new ItemStack(Items.IRON_PICKAXE), ItemStack.EMPTY, pick,
			"quarrying");
	}

	private Task fisher(FolkBrain brain, ServerLevel level, ResidentPlaces p) {
		List<Poi> docks = brain.town.places.all(level, brain.settlement, PoiType.DOCK);
		BlockPos spot = null;
		BlockPos water = null;
		if (!docks.isEmpty()) {
			Poi d = docks.get(brain.random.nextInt(docks.size()));
			spot = d.pos();
			water = d.pos().relative(d.facing(), 2);
		} else {
			// The nearest bank: a standable block beside water.
			BlockPos w = findNear(level, p.work, 20, s -> s.getFluidState().isSource() && s.getFluidState().is(net.minecraft.tags.FluidTags.WATER));
			if (w != null) {
				water = w;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos bank = w.relative(d).above();
					if (FolkSpots.standable(level, bank)) {
						spot = bank;
						break;
					}
				}
			}
		}
		if (spot == null || water == null) {
			return new WanderHolding(p.work, 8, new ItemStack(Items.FISHING_ROD), "mending nets");
		}
		BlockPos wet = water;
		Tasks.Effect bite = Tasks.every(160, SoundEvents.FISHING_BOBBER_SPLASH, 0.4F, (b, lvl, e, at) -> {
			Vec3 c = Vec3.atCenterOf(wet).add(0, 0.5, 0);
			lvl.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 6, 0.2, 0.0, 0.2, 0.1);
		}, true);
		return new Tasks.Linger(spot, water, FolkPose.NONE, 900, new ItemStack(Items.FISHING_ROD), ItemStack.EMPTY, bite, "fishing");
	}

	/** A point a little beyond the town's edge, in a direction that changes with each trip. */
	private BlockPos edge(FolkBrain brain, ServerLevel level, int beyond) {
		BoundingBox b = brain.settlement.bounds();
		BlockPos c = brain.settlement.center();
		double angle = ((brain.seed() >>> 3) % 360 + this.trips * 47) * Math.PI / 180.0;
		double r = Math.max(b.getXSpan(), b.getZSpan()) / 2.0 + beyond;
		r = Math.min(r, 72);
		int x = c.getX() + (int) Math.round(Math.cos(angle) * r);
		int z = c.getZ() + (int) Math.round(Math.sin(angle) * r);
		BlockPos column = new BlockPos(x, c.getY(), z);
		if (!level.isLoaded(column)) {
			return c;
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
	}

	private java.util.Optional<BlockPos> pasture(FolkBrain brain) {
		PlotView plot = brain.resident.work != Resident.NO_PLOT ? brain.settlement.plot(brain.resident.work) : null;
		return plot == null ? java.util.Optional.empty() : java.util.Optional.of(ResidentPlaces.centre(plot.spec()));
	}

	private static @Nullable BlockPos findNear(ServerLevel level, BlockPos at, int r, java.util.function.Predicate<BlockState> test) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -3; dy <= 6; dy++) {
					p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (!level.isLoaded(p)) {
						continue;
					}
					if (test.test(level.getBlockState(p))) {
						double d = p.distSqr(at);
						if (d < bestD) {
							bestD = d;
							best = p.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	/** Wandering with something in hand. */
	static final class WanderHolding implements Task {
		private final Tasks.Wander wander;
		private final ItemStack held;
		private final String what;

		WanderHolding(BlockPos centre, int radius, ItemStack held, String what) {
			this.wander = new Tasks.Wander(centre, radius, 4);
			this.held = held;
			this.what = what;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			brain.hold(this.held, ItemStack.EMPTY);
			return this.wander.tick(brain, level, elapsed);
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			this.wander.stop(brain, level);
		}

		@Override
		public String describe() {
			return this.what;
		}
	}
}
