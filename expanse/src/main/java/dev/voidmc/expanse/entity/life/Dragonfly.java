package dev.voidmc.expanse.entity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dragonflies patrol a stretch of open water on sunny days: a quick dart, a dead-still hover, another
 * dart, never far from the spot they claimed, and never more than a couple of blocks above the water.
 * Now and then one settles on the bank or a reed for a while. They sit out the night and the rain.
 *
 * <p>Azure, emerald and scarlet; glassy wings.
 */
public class Dragonfly extends Critter {
	public static final String[] VARIANTS = {"azure", "emerald", "scarlet"};

	private @Nullable Vec3 target;
	private @Nullable BlockPos home;
	private boolean landing;
	private int hover;
	private int restTicks;
	private int retarget;

	public Dragonfly(EntityType<? extends Dragonfly> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0).add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected int variantCount() {
		return VARIANTS.length;
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		return temperature(level, pos) >= 0.9F ? pick(random, 2, 2, 3) : pick(random, 3, 3, 1);
	}

	@Override
	protected boolean isOutOfHours() {
		return this.level().isDarkOutside();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.isResting()) {
			this.setDeltaMovement(Vec3.ZERO);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null) {
			this.home = this.blockPosition();
		}
		boolean sleepy = this.isOutOfHours() || level.isRainingAt(this.blockPosition());
		Player near = (this.tickCount + this.getId()) % 4 == 0 ? this.nearestUnsneaking(2.5, 1.2) : null;
		if (this.isResting()) {
			if (near != null || --this.restTicks <= 0 && !sleepy) {
				this.takeOff();
			}
			return;
		}
		this.setFlying(true);
		if (near != null) {
			// A dart straight away from whoever came close.
			Vec3 away = this.position().subtract(near.position()).multiply(1, 0, 1).normalize().scale(3.5);
			this.target = this.position().add(away).add(0, 0.6, 0);
			this.hover = 0;
			this.landing = false;
			this.retarget = 25;
		}
		if (this.hover > 0) {
			this.hover--;
			// Dead still but for a shiver.
			this.setDeltaMovement(this.getDeltaMovement().scale(0.5).add((this.random.nextDouble() - 0.5) * 0.01,
				(this.random.nextDouble() - 0.5) * 0.01, (this.random.nextDouble() - 0.5) * 0.01));
			if (this.hover == 0) {
				this.pickTarget(level, sleepy);
			}
			return;
		}
		if (this.target == null || --this.retarget <= 0) {
			this.pickTarget(level, sleepy);
		}
		if (this.target == null) {
			return;
		}
		this.steerTowards(this.target, this.landing ? 0.12 : 0.32, this.landing ? 0.2 : 0.35);
		if (this.position().distanceToSqr(this.target) < (this.landing ? 0.02 : 0.25)) {
			if (this.landing) {
				this.setPos(this.target.x, this.target.y, this.target.z);
				this.setDeltaMovement(Vec3.ZERO);
				this.setResting(true);
				this.setFlying(false);
				this.restTicks = sleepy ? 600 : 100 + this.random.nextInt(300);
				this.landing = false;
				this.target = null;
			} else {
				this.hover = 8 + this.random.nextInt(40);
			}
		}
	}

	private void pickTarget(ServerLevel level, boolean sleepy) {
		this.retarget = 50;
		this.landing = false;
		BlockPos h = this.home != null && this.home.closerToCenterThan(this.position(), 16.0) ? this.home : this.blockPosition();
		if (sleepy || this.random.nextInt(14) == 0) {
			Vec3 perch = this.findPerch(level, h);
			if (perch != null) {
				this.target = perch;
				this.landing = true;
				this.retarget = 120;
				return;
			}
		}
		// Over the water if a few looks find some; the bank will do.
		Vec3 best = null;
		for (int i = 0; i < 4; i++) {
			int x = h.getX() + this.random.nextInt(11) - 5;
			int z = h.getZ() + this.random.nextInt(11) - 5;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			best = new Vec3(x + 0.5, top + 0.4 + this.random.nextDouble() * 1.6, z + 0.5);
			if (!level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
				break;
			}
		}
		this.target = best;
	}

	/** The top of a plant or the bank beside the water, to rest on. */
	private @Nullable Vec3 findPerch(ServerLevel level, BlockPos h) {
		for (int i = 0; i < 8; i++) {
			int x = h.getX() + this.random.nextInt(9) - 4;
			int z = h.getZ() + this.random.nextInt(9) - 4;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos below = new BlockPos(x, top - 1, z);
			if (!level.getFluidState(below).isEmpty()) {
				continue;
			}
			BlockPos at = below.above();
			if (!level.getBlockState(at).isAir() && level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				// A reed, a cattail, tall grass: sit on its tip.
				return new Vec3(x + 0.5, at.getY() + 0.8, z + 0.5);
			}
			return new Vec3(x + 0.5, top + 0.02, z + 0.5);
		}
		return null;
	}

	private void takeOff() {
		this.setResting(false);
		this.setFlying(true);
		this.target = null;
		this.hover = 0;
		this.setDeltaMovement(0.0, 0.15, 0.0);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.isResting()) {
			this.takeOff();
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected float getSoundVolume() {
		return 0.0F;
	}

	/** Over open water, by day, in fair weather. */
	public static boolean checkSpawn(EntityType<Dragonfly> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return nearAPlayer(level, reason, pos) && outdoors(level, pos) && open(level, pos)
			&& !level.getFluidState(pos.below()).isEmpty() && level.getBlockState(pos).isAir()
			&& level.getLevel().isBrightOutside() && !level.getLevel().isRaining();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.store("Home", BlockPos.CODEC, this.home);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.home = input.read("Home", BlockPos.CODEC).orElse(null);
		this.restTicks = 40;
	}
}
