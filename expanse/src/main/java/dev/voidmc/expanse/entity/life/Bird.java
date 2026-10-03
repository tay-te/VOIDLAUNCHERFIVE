package dev.voidmc.expanse.entity.life;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What every bird here does: it stays put (on the ground, wading, or perched on a treetop, a fence, a
 * wall), now and then flies off to somewhere else nearby, and takes flight when someone comes too close,
 * flushing the rest of its flock with it.
 *
 * <p>Flight is steered directly, as vanilla's bat is, with no pathfinding: rise to a cruising height
 * above the trees ahead, glide in toward the landing spot, drop the last bit. Landing spots are found by
 * looking at a handful of columns near where it wants to go and reading the top of each (leaves, a fence,
 * the ground, the water), so a bird costs a few block lookups when it decides to move and nothing while
 * it sits.
 */
public abstract class Bird extends Critter {
	protected @Nullable Vec3 flightTarget;
	protected boolean targetIsPerch;
	private double cruiseY;
	private int flightTicks;
	private int stuckTicks;
	private boolean gaveUp;
	private @Nullable Player watching;
	protected int idle = 20;

	protected Bird(EntityType<? extends Bird> type, Level level) {
		super(type, level);
	}

	// ------------------------------------------------------------------ what kind of bird

	protected abstract double flightSpeed();

	/** How close a player may come (walking, sneaking) before it flies. */
	protected abstract double fearRadius();

	protected abstract double sneakingFearRadius();

	/** How far it goes when it moves on of its own accord. */
	protected abstract int wanderRadius();

	/** How much it prefers a perch (treetop, fence) to the ground, 0 to 1. */
	protected abstract float perchPreference();

	/** Ticks until it next does something where it sits. */
	protected abstract int idleTime();

	/** The chance that, when it next does something, it is to fly somewhere else. */
	protected abstract float wanderChance();

	/** Something to do where it sits: hop, peck, wade, look round. */
	protected abstract void sitAction(ServerLevel level);

	/** Called as it takes off; {@code alarmed} when something scared it. */
	protected void onTakeOff(ServerLevel level, boolean alarmed) {
	}

	/** Whether it rests (sits tight) on a perch it lands on. */
	protected boolean restsOnPerch() {
		return true;
	}

	/** Whether shallow water is somewhere to stand. */
	protected boolean wades() {
		return false;
	}

	// ------------------------------------------------------------------ ticking

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.isFlying()) {
			if ((this.tickCount + this.getId()) % 5 == 0) {
				// Keep an eye on anyone nearby; go if they come too close.
				Player near = level.getNearestPlayer(this, Math.max(8.0, this.fearRadius() * 1.5));
				if (near != null) {
					double r = near.isShiftKeyDown() ? this.sneakingFearRadius() : this.fearRadius();
					if (near.distanceToSqr(this) <= r * r) {
						this.watching = null;
						this.flee(level, near.position(), true);
						return;
					}
				}
				this.watching = near;
			}
			if (this.watching != null && !this.isSleeping()) {
				this.getLookControl().setLookAt(this.watching, 30.0F, 30.0F);
			}
		}
		if (this.isFlying()) {
			this.fly(level);
		} else {
			this.sit(level);
		}
	}

	private void sit(ServerLevel level) {
		if (this.isInWater() && !(this.wades() && this.shallow(level))) {
			this.wander(level);
			return;
		}
		if (this.isResting() && this.tickCount % 20 == 0 && !this.onGround() && this.getDeltaMovement().y < -0.1) {
			// Its perch went from under it.
			this.setResting(false);
		}
		if (--this.idle <= 0) {
			this.idle = this.idleTime();
			if (this.random.nextFloat() < this.wanderChance()) {
				this.wander(level);
			} else {
				this.sitAction(level);
			}
		}
	}

	private boolean shallow(ServerLevel level) {
		BlockPos feet = this.blockPosition();
		return level.getFluidState(feet.above()).isEmpty();
	}

	// ------------------------------------------------------------------ taking off

	/** Flies off somewhere nearby of its own accord, bringing some of the flock along. */
	protected void wander(ServerLevel level) {
		double angle = this.random.nextDouble() * Math.PI * 2;
		double dist = this.wanderRadius() * (0.5 + this.random.nextDouble() * 0.5);
		Vec3 center = this.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
		Spot spot = this.findSpot(level, center, 4, this.perchPreference());
		if (spot == null) {
			return;
		}
		this.takeOffTo(spot);
		this.onTakeOff(level, false);
		for (Bird other : this.flockmates(level, 6.0)) {
			if (this.random.nextFloat() < 0.6F) {
				other.followTo(level, spot);
			}
		}
	}

	/** Takes flight away from {@code from}, and flushes the flock. */
	public void flee(ServerLevel level, Vec3 from, boolean alarmOthers) {
		Vec3 away = this.position().subtract(from).multiply(1, 0, 1);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(this.random.nextDouble() - 0.5, 0, this.random.nextDouble() - 0.5);
		}
		double turn = (this.random.nextDouble() - 0.5) * Math.PI / 2;
		away = away.normalize().yRot((float) turn).scale(this.wanderRadius() * (0.8 + this.random.nextDouble() * 0.6));
		Vec3 center = this.position().add(away);
		Spot spot = this.findSpot(level, center, 5, this.perchPreference());
		if (spot == null) {
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(center.x), Mth.floor(center.z));
			spot = new Spot(new Vec3(center.x, ground, center.z), false);
		}
		this.takeOffTo(spot);
		this.onTakeOff(level, true);
		if (alarmOthers) {
			for (Bird other : this.flockmates(level, 10.0)) {
				other.followTo(level, spot);
			}
		}
	}

	private void followTo(ServerLevel level, Spot leader) {
		Spot spot = this.findSpot(level, leader.pos(), 3, leader.perch() ? 0.9F : 0.1F);
		this.takeOffTo(spot != null ? spot : leader);
		this.onTakeOff(level, false);
	}

	private List<? extends Bird> flockmates(ServerLevel level, double r) {
		return level.getEntitiesOfClass(this.getClass(), this.getBoundingBox().inflate(r, r / 2, r), b -> b != this && !b.isFlying());
	}

	protected void takeOffTo(Spot spot) {
		this.flightTarget = spot.pos();
		this.targetIsPerch = spot.perch();
		this.flightTicks = 0;
		this.stuckTicks = 0;
		this.gaveUp = false;
		this.cruiseY = Math.max(this.getY(), spot.pos().y) + 2.0 + this.random.nextDouble() * 2.0;
		this.setFlying(true);
		this.setResting(false);
		this.setSleeping(false);
		this.setNoGravity(true);
		this.getMoveControl().setWait();
		this.setDeltaMovement(this.getDeltaMovement().add(0, 0.3, 0));
	}

	// ------------------------------------------------------------------ flying

	private void fly(ServerLevel level) {
		this.flightTicks++;
		if (this.flightTarget == null) {
			this.land();
			return;
		}
		Vec3 pos = this.position();
		double dx = this.flightTarget.x - pos.x;
		double dz = this.flightTarget.z - pos.z;
		double hd = Math.sqrt(dx * dx + dz * dz);
		double nx = hd > 1.0E-3 ? dx / hd : 0;
		double nz = hd > 1.0E-3 ? dz / hd : 0;
		double wantY;
		if (hd > 3.0) {
			int ahead = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x + nx * 2.5), Mth.floor(pos.z + nz * 2.5));
			int here = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
			this.cruiseY = Math.max(this.cruiseY, Math.max(ahead, here) + 1.5);
			wantY = this.cruiseY;
		} else {
			wantY = this.flightTarget.y + 0.1 + hd * 0.25;
		}
		double speed = this.flightSpeed() * (hd < 3.0 ? Math.max(0.3, hd / 3.0) : 1.0);
		Vec3 want = new Vec3(nx * speed, Mth.clamp((wantY - pos.y) * 0.3, -speed, speed * 1.2), nz * speed);
		Vec3 v = this.getDeltaMovement();
		v = v.add(want.subtract(v).scale(0.2));
		this.setDeltaMovement(v);
		this.faceAlong(v, 18.0F);
		if (this.horizontalCollision) {
			this.cruiseY += 0.5;
			this.stuckTicks += 2;
		} else if (this.stuckTicks > 0) {
			this.stuckTicks--;
		}
		if (hd < 0.6 && Math.abs(pos.y - this.flightTarget.y) < 0.7) {
			this.land();
		} else if (!this.gaveUp && (this.flightTicks > 500 || this.stuckTicks > 60)) {
			// Give up on it: come down somewhere near.
			this.gaveUp = true;
			Spot spot = this.findSpot(level, pos, 3, 0.5F);
			if (spot != null) {
				this.flightTarget = spot.pos();
				this.targetIsPerch = spot.perch();
				this.stuckTicks = 0;
				this.cruiseY = Math.max(pos.y, spot.pos().y) + 1.0;
			} else {
				this.land();
			}
		} else if (this.gaveUp && (this.flightTicks > 800 || this.stuckTicks > 60)) {
			this.land();
		}
	}

	protected void land() {
		this.setFlying(false);
		this.setNoGravity(false);
		this.setResting(this.targetIsPerch && this.restsOnPerch());
		if (this.flightTarget != null && this.position().distanceToSqr(this.flightTarget) < 1.0) {
			this.setPos(this.flightTarget.x, this.flightTarget.y, this.flightTarget.z);
		}
		this.flightTarget = null;
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 0.0, 0.2));
		this.idle = this.idleTime();
	}

	// ------------------------------------------------------------------ where to land

	protected record Spot(Vec3 pos, boolean perch) {
	}

	/**
	 * A place to land near {@code center}, from a few looks at the tops of columns there. {@code perch}
	 * in the result means a treetop, fence or wall (for a wader, the shallows and the bank).
	 */
	protected @Nullable Spot findSpot(ServerLevel level, Vec3 center, int radius, float perchPreference) {
		Spot fallback = null;
		boolean wantPerch = this.random.nextFloat() < perchPreference;
		for (int i = 0; i < 10; i++) {
			int x = Mth.floor(center.x) + this.random.nextInt(2 * radius + 1) - radius;
			int z = Mth.floor(center.z) + this.random.nextInt(2 * radius + 1) - radius;
			BlockPos column = new BlockPos(x, Mth.floor(center.y), z);
			if (!level.hasChunkAt(column)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockPos below = new BlockPos(x, top - 1, z);
			if (!level.getBlockState(below.above()).getCollisionShape(level, below.above()).isEmpty()) {
				continue;
			}
			Spot spot = this.classify(level, below, level.getBlockState(below));
			if (spot == null) {
				continue;
			}
			if (spot.perch() == wantPerch) {
				return spot;
			}
			if (fallback == null) {
				fallback = spot;
			}
		}
		return fallback;
	}

	/** What the top of a column (the block {@code below} where it would stand) offers. */
	protected @Nullable Spot classify(ServerLevel level, BlockPos below, BlockState state) {
		if (!state.getFluidState().isEmpty()) {
			return null;
		}
		double h = state.getCollisionShape(level, below).max(Direction.Axis.Y);
		if (h <= 0.0 || state.is(BlockTags.FIRE) || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
			|| state.is(net.minecraft.world.level.block.Blocks.CACTUS)) {
			return null;
		}
		boolean perch = state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.LOGS);
		return new Spot(new Vec3(below.getX() + 0.5, below.getY() + h, below.getZ() + 0.5), perch);
	}

	// ------------------------------------------------------------------ the rest

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive() && !this.isFlying()) {
			Entity attacker = source.getEntity();
			this.flee(level, attacker != null ? attacker.position() : this.position(), true);
		}
		return hurt;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.isFlying()) {
			// Flight is steered on the server; let it carry, not sag.
			this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.9, 1.0));
		}
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return net.minecraft.sounds.SoundEvents.PARROT_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return net.minecraft.sounds.SoundEvents.PARROT_DEATH;
	}

	@Override
	protected boolean isFlapping() {
		return this.isFlying() && this.tickCount % 12 == 0;
	}
}
