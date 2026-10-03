package dev.voidmc.expanse.entity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Owls: the night's watchers in the woods. One sits on a treetop and hoots, swivels its head to follow
 * you, and drifts silently off to another tree if you come too close. By day it dozes where it sat,
 * eyes shut, and lets you come quite near before it bothers to move.
 *
 * <p>Tawny and barn owls in the woods, the snowy owl in the cold.
 */
public class Owl extends Bird {
	public static final int TAWNY = 0;
	public static final int BARN = 1;
	public static final int SNOWY = 2;
	public static final String[] VARIANTS = {"tawny", "barn", "snowy"};

	public Owl(EntityType<? extends Owl> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.2);
	}

	@Override
	protected int variantCount() {
		return VARIANTS.length;
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		return temperature(level, pos) < 0.2F ? SNOWY : pick(random, 3, 2, 0);
	}

	@Override
	protected boolean isOutOfHours() {
		return this.level().isBrightOutside();
	}

	@Override
	protected double flightSpeed() {
		return 0.34;
	}

	@Override
	protected double fearRadius() {
		return this.isSleeping() ? 2.5 : 4.5;
	}

	@Override
	protected double sneakingFearRadius() {
		return this.isSleeping() ? 1.2 : 2.0;
	}

	@Override
	protected int wanderRadius() {
		return 16;
	}

	@Override
	protected float perchPreference() {
		return 0.95F;
	}

	@Override
	protected int idleTime() {
		return 80 + this.random.nextInt(200);
	}

	@Override
	protected float wanderChance() {
		return this.level().isDarkOutside() ? 0.12F : 0.0F;
	}

	@Override
	protected void sitAction(ServerLevel level) {
		if (this.isSleeping()) {
			return;
		}
		// The famous head-turn is the model's; the body shifts a little now and then.
		this.setYRot(this.getYRot() + (this.random.nextFloat() - 0.5F) * 90.0F);
		this.yBodyRot = this.getYRot();
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.tickCount % 20 == 0) {
			this.setSleeping(!this.isFlying() && this.level().isBrightOutside());
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.level().isDarkOutside() && !this.isFlying() ? LifeSounds.OWL_HOOT : null;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 320;
	}

	@Override
	protected float getSoundVolume() {
		// Carries: heard across a wood, to 32 blocks.
		return 2.0F;
	}

	@Override
	public float getVoicePitch() {
		float species = switch (this.getVariant()) {
			case BARN -> 1.2F;
			case SNOWY -> 0.85F;
			default -> 1.0F;
		};
		return species * (0.95F + this.random.nextFloat() * 0.1F);
	}

	@Override
	public int getMaxHeadYRot() {
		return 150;
	}

	/** At night, on a treetop (or a log) in the woods. */
	public static boolean checkSpawn(EntityType<Owl> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!nearAPlayer(level, reason, pos) || !outdoors(level, pos) || !open(level, pos) || !level.getLevel().isDarkOutside()) {
			return false;
		}
		BlockState below = level.getBlockState(pos.below());
		return below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS)
			|| reason != EntitySpawnReason.NATURAL && !below.getCollisionShape(level, pos.below()).isEmpty();
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData group) {
		BlockState below = level.getBlockState(this.blockPosition().below());
		this.setResting(below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS) || below.is(BlockTags.FENCES));
		return super.finalizeSpawn(level, difficulty, reason, group);
	}
}
