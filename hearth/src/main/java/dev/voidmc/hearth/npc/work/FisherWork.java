package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fishers (docs/LIVING_WORLD.md §7.4): out on the dock (any {@code DOCK} of the town, or a bank beside open water near
 * their workplace), casting, waiting for the bite, landing the catch — a fish held up a moment, the culture's kind — and
 * after a few, carrying the basket to the fishery's store. The catch is the sim's to credit from their working time.
 */
final class FisherWork implements WorkBehaviour {
	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		// out in one of the dock's boats while one lies free and the stretch is long enough (docs/LIVING_WORLD_2.md §6.1)
		Task row = dev.voidmc.hearth.npc.work.field.Boats.row(brain, level, catchOf(brain.settlement.culture()));
		if (row != null) {
			return row;
		}
		ResidentPlaces p = brain.places(level);
		BlockPos spot = null;
		BlockPos water = null;
		List<Poi> docks = brain.town.places.all(level, brain.settlement, PoiType.DOCK);
		if (!docks.isEmpty()) {
			// the dock nearest their workplace, mostly; another now and then
			Poi d = docks.get(0);
			for (Poi o : docks) {
				if (o.pos().distSqr(p.work) < d.pos().distSqr(p.work) && brain.random.nextInt(4) != 0) {
					d = o;
				}
			}
			spot = d.pos();
			water = d.pos().relative(d.facing(), 3);
		} else {
			BlockPos w = findWater(level, p.work, 20);
			if (w != null) {
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					BlockPos bank = w.relative(dir).above();
					if (FolkSpots.standable(level, bank)) {
						spot = bank;
						water = w.relative(dir.getOpposite(), 2);
						break;
					}
				}
			}
		}
		if (spot == null) {
			return new FieldWork.WanderHolding(p.work, 8, new ItemStack(Items.FISHING_ROD), "mending nets");
		}
		return new Fish(spot, water, catchOf(brain.settlement.culture()), p.workStorage != null ? p.workStorage.pos() : null);
	}

	static Item catchOf(Culture culture) {
		return switch (culture) {
			case TROPICAL -> Items.TROPICAL_FISH;
			case MARITIME, NORTHERN, TUNDRA, ALPINE -> Items.SALMON;
			default -> Items.COD;
		};
	}

	private static @Nullable BlockPos findWater(ServerLevel level, BlockPos at, int r) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				for (int dy = -4; dy <= 2; dy++) {
					m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (!level.isLoaded(m)) {
						continue;
					}
					BlockState s = level.getBlockState(m);
					if (s.getFluidState().isSource() && s.getFluidState().is(FluidTags.WATER) && level.getBlockState(m.above()).isAir()) {
						double d = m.distSqr(at);
						if (d < bestD) {
							bestD = d;
							best = m.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	/** A stint of fishing: cast, wait, land, a few times; then the basket to the store. */
	static final class Fish implements Task {
		private final BlockPos spot;
		private final BlockPos water;
		private final Item fish;
		private final @Nullable BlockPos store;
		private boolean arrived;
		private int walk;
		private int wait;
		private int landed;
		private int showing;
		private boolean carrying;
		private int casts;

		Fish(BlockPos spot, BlockPos water, Item fish, @Nullable BlockPos store) {
			this.spot = spot;
			this.water = water;
			this.fish = fish;
			this.store = store;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			if (this.carrying) {
				brain.hold(new ItemStack(this.fish), new ItemStack(this.fish));
				brain.folk.setFolkPose(FolkPose.CRAFT);
				this.walk += elapsed;
				if (this.store == null || brain.walk(level, this.store, 1.8, 0.55) || brain.walkFailed() || this.walk > 20 * 60) {
					brain.resetWalk();
					if (this.store != null) {
						level.playSound(null, this.store, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.6F, 1.0F);
					}
					return Status.DONE;
				}
				return Status.RUNNING;
			}
			if (!this.arrived) {
				this.walk += elapsed;
				brain.hold(new ItemStack(Items.FISHING_ROD), ItemStack.EMPTY);
				if (brain.walk(level, this.spot, 1.0, 0.6) || brain.walkFailed() || this.walk > 20 * 60) {
					brain.resetWalk();
					this.arrived = true;
					this.wait = 0;
				}
				return Status.RUNNING;
			}
			if ((brain.folk.tickCount & 15) == 0) {
				brain.faceTowards(this.water);
			}
			if (this.showing > 0) {
				// the catch held up, then into the basket
				this.showing -= elapsed;
				if (this.showing <= 0) {
					brain.hold(new ItemStack(Items.FISHING_ROD), ItemStack.EMPTY);
					brain.folk.setFolkPose(FolkPose.NONE);
				}
				return Status.RUNNING;
			}
			this.wait -= elapsed;
			if (this.wait > 0) {
				return Status.RUNNING;
			}
			if (this.casts == this.landed) {
				// cast
				this.casts++;
				brain.swing();
				level.playSound(null, brain.folk.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (brain.random.nextFloat() * 0.4F + 0.8F));
				this.wait = 160 + brain.random.nextInt(300);
				return Status.RUNNING;
			}
			// the bite, and the catch landed (about one cast in three comes up empty)
			Vec3 c = Vec3.atCenterOf(this.water).add(0, 0.5, 0);
			level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 8, 0.2, 0.0, 0.2, 0.1);
			level.playSound(null, this.water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
			brain.swing();
			this.landed++;
			if (brain.random.nextInt(3) != 0) {
				brain.hold(new ItemStack(Items.FISHING_ROD), new ItemStack(this.fish));
				brain.folk.setFolkPose(FolkPose.CHEER);
				this.showing = 30;
			}
			this.wait = 20;
			if (this.landed >= 4) {
				this.carrying = this.store != null;
				this.walk = 0;
				return this.carrying ? Status.RUNNING : Status.DONE;
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return this.carrying ? "carrying the catch" : this.arrived ? "fishing (" + this.landed + " landed)" : "going to the water";
		}
	}
}
