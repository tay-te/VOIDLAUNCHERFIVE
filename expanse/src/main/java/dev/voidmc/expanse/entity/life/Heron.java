package dev.voidmc.expanse.entity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Herons and egrets: tall, patient waders of the shallows. One stands in the water at the edge of a
 * river or lake, stalks a step or two now and then, and strikes at fish; long after you have noticed it
 * it lifts off with a harsh croak and slow wingbeats, neck folded, legs trailing, and comes down again
 * further along the bank. They are wary: walk within a dozen blocks and they go.
 *
 * <p>Grey heron, and the white great egret, which is commoner in warm country.
 */
public class Heron extends Bird {
	public static final int GREY_HERON = 0;
	public static final int GREAT_EGRET = 1;
	public static final String[] VARIANTS = {"grey_heron", "great_egret"};

	public Heron(EntityType<? extends Heron> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected int variantCount() {
		return VARIANTS.length;
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		return temperature(level, pos) >= 0.85F ? pick(random, 3, 7) : pick(random, 6, 1);
	}

	@Override
	protected boolean isOutOfHours() {
		return this.level().isDarkOutside();
	}

	@Override
	protected double flightSpeed() {
		return 0.32;
	}

	@Override
	protected double fearRadius() {
		return 12.0;
	}

	@Override
	protected double sneakingFearRadius() {
		return 5.0;
	}

	@Override
	protected int wanderRadius() {
		return 24;
	}

	/** For a heron a "perch" is the shallows or the bank (see {@link #classify}). */
	@Override
	protected float perchPreference() {
		return 0.97F;
	}

	@Override
	protected boolean restsOnPerch() {
		return false;
	}

	@Override
	protected boolean wades() {
		return true;
	}

	@Override
	protected int idleTime() {
		return 60 + this.random.nextInt(140);
	}

	@Override
	protected float wanderChance() {
		return this.level().isDarkOutside() ? 0.0F : 0.05F;
	}

	@Override
	protected void sitAction(ServerLevel level) {
		if (this.random.nextFloat() < 0.5F) {
			// A slow step or two through the shallows.
			Spot spot = this.findSpot(level, this.position(), 4, 1.0F);
			if (spot != null && spot.perch()) {
				this.getMoveControl().setWantedPosition(spot.pos().x, spot.pos().y, spot.pos().z, 0.55);
			}
		} else {
			this.setYRot(this.getYRot() + (this.random.nextFloat() - 0.5F) * 70.0F);
			this.yBodyRot = this.getYRot();
		}
	}

	/** The shallows (water one deep over a firm bed) and banks (ground beside water) are where it wants to be. */
	@Override
	protected @Nullable Spot classify(ServerLevel level, BlockPos below, BlockState state) {
		if (state.getFluidState().is(FluidTags.WATER)) {
			BlockPos bed = below.below();
			BlockState bedState = level.getBlockState(bed);
			if (bedState.getFluidState().isEmpty() && !bedState.getCollisionShape(level, bed).isEmpty()) {
				return new Spot(new Vec3(below.getX() + 0.5, below.getY(), below.getZ() + 0.5), true);
			}
			return null;
		}
		Spot spot = super.classify(level, below, state);
		if (spot == null || state.is(net.minecraft.tags.BlockTags.LEAVES)) {
			return null;
		}
		return new Spot(spot.pos(), besideWater(level, below));
	}

	/** Water within two blocks of this ground block, level with it or a step below (a river's surface often is). */
	private static boolean besideWater(LevelAccessor level, BlockPos ground) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int step = 1; step <= 2; step++) {
				BlockPos side = ground.relative(d, step);
				if (level.getFluidState(side).is(FluidTags.WATER) || level.getFluidState(side.below()).is(FluidTags.WATER)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void onTakeOff(ServerLevel level, boolean alarmed) {
		this.playSound(LifeSounds.WINGS, 0.5F, 0.55F + this.random.nextFloat() * 0.1F);
		if (alarmed || this.random.nextInt(3) == 0) {
			this.playSound(LifeSounds.HERON_CROAK, this.getSoundVolume(), this.getVoicePitch());
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.random.nextInt(6) == 0 ? LifeSounds.HERON_CROAK : null;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 600;
	}

	@Override
	protected float getSoundVolume() {
		return 1.5F;
	}

	@Override
	public float getVoicePitch() {
		return (this.getVariant() == GREAT_EGRET ? 1.1F : 1.0F) * (0.9F + this.random.nextFloat() * 0.2F);
	}

	/** By day, standing in the shallows or on a bank right beside the water. */
	public static boolean checkSpawn(EntityType<Heron> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!nearAPlayer(level, reason, pos) || !outdoors(level, pos) || !level.getLevel().isBrightOutside() || !level.getBlockState(pos).isAir()) {
			return false;
		}
		BlockPos below = pos.below();
		BlockState state = level.getBlockState(below);
		if (state.getFluidState().is(FluidTags.WATER)) {
			BlockState bed = level.getBlockState(below.below());
			return bed.getFluidState().isEmpty() && !bed.getCollisionShape(level, below.below()).isEmpty();
		}
		return !state.getCollisionShape(level, below).isEmpty() && besideWater(level, below);
	}
}
