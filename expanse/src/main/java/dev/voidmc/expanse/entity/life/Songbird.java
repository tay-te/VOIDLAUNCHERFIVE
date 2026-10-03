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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Small birds in little flocks: they hop and peck about on the grass, sit on the treetops, fences and
 * walls and sing, and every so often flit off a few blocks to somewhere else, the flock straggling
 * after. Walk up on one and the whole flock bursts up and away with an alarm call; sneak, and you can
 * get close. They roost quietly at night.
 *
 * <p>Robin, bluebird, goldfinch, cardinal, sparrow, and in the snow the snow bunting.
 */
public class Songbird extends Bird {
	public static final int ROBIN = 0;
	public static final int BLUEBIRD = 1;
	public static final int GOLDFINCH = 2;
	public static final int CARDINAL = 3;
	public static final int SPARROW = 4;
	public static final int SNOW_BUNTING = 5;
	public static final String[] VARIANTS = {"robin", "bluebird", "goldfinch", "cardinal", "sparrow", "snow_bunting"};

	public Songbird(EntityType<? extends Songbird> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 3.0).add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected int variantCount() {
		return VARIANTS.length;
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		float t = temperature(level, pos);
		if (t < 0.2F) {
			// robin, bluebird, goldfinch, cardinal, sparrow, snow bunting
			return pick(random, 2, 0, 0, 0, 3, 6);
		}
		if (t >= 0.9F) {
			return pick(random, 0, 3, 3, 4, 2, 0);
		}
		return pick(random, 5, 3, 3, 2, 4, 0);
	}

	@Override
	protected boolean isOutOfHours() {
		return this.level().isDarkOutside();
	}

	@Override
	protected double flightSpeed() {
		return 0.42;
	}

	@Override
	protected double fearRadius() {
		return 5.0;
	}

	@Override
	protected double sneakingFearRadius() {
		return 2.0;
	}

	@Override
	protected int wanderRadius() {
		return 12;
	}

	@Override
	protected float perchPreference() {
		return 0.55F;
	}

	@Override
	protected int idleTime() {
		return 15 + this.random.nextInt(45);
	}

	@Override
	protected float wanderChance() {
		// Roosting at night: they stay put.
		if (this.level().isDarkOutside()) {
			return 0.0F;
		}
		return this.isResting() ? 0.08F : 0.06F;
	}

	@Override
	protected void sitAction(ServerLevel level) {
		if (level.isDarkOutside()) {
			// Roosting.
			return;
		}
		if (this.isResting() || !this.onGround()) {
			// On a perch: look about.
			this.setYRot(this.getYRot() + (this.random.nextFloat() - 0.5F) * 120.0F);
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
			return;
		}
		if (this.random.nextFloat() < 0.6F) {
			// A hop, if there is ground to hop to and no water.
			float yaw = this.random.nextFloat() * 360.0F;
			Vec3 dir = Vec3.directionFromRotation(0.0F, yaw);
			BlockPos to = BlockPos.containing(this.getX() + dir.x, this.getY() - 0.5, this.getZ() + dir.z);
			BlockState ground = level.getBlockState(to);
			if (!ground.getCollisionShape(level, to).isEmpty() && level.getFluidState(to.above()).isEmpty()
				&& level.getBlockState(to.above()).getCollisionShape(level, to.above()).isEmpty()) {
				this.setYRot(yaw);
				this.yBodyRot = yaw;
				this.yHeadRot = yaw;
				this.setDeltaMovement(dir.x * 0.14, 0.3, dir.z * 0.14);
				this.needsSync = true;
			}
		}
	}

	@Override
	protected void onTakeOff(ServerLevel level, boolean alarmed) {
		this.playSound(LifeSounds.WINGS, 0.25F, 1.3F + this.random.nextFloat() * 0.3F);
		if (alarmed) {
			this.playSound(LifeSounds.SONGBIRD_CALL, 0.6F, this.getVoicePitch());
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.tickCount % 40 == 0) {
			this.setSleeping(this.isResting() && this.level().isDarkOutside());
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		// They sing from a perch; on the ground, busy feeding, only now and then.
		return this.level().isBrightOutside() && !this.isFlying() && (this.isResting() || this.random.nextInt(3) == 0)
			? LifeSounds.SONGBIRD_SONG : null;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 260;
	}

	@Override
	protected float getSoundVolume() {
		return 0.55F;
	}

	@Override
	public float getVoicePitch() {
		// Each species a little higher or lower; each bird its own.
		float species = switch (this.getVariant()) {
			case CARDINAL -> 0.85F;
			case GOLDFINCH -> 1.15F;
			case SPARROW -> 1.05F;
			case SNOW_BUNTING -> 1.1F;
			default -> 1.0F;
		};
		return species * (0.92F + (this.getId() % 7) * 0.025F) * (0.95F + this.random.nextFloat() * 0.1F);
	}

	/** By day, on grass, dirt, sand or leaves under the open sky or a canopy. */
	public static boolean checkSpawn(EntityType<Songbird> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!nearAPlayer(level, reason, pos) || !outdoors(level, pos) || !open(level, pos) || !level.getLevel().isBrightOutside()) {
			return false;
		}
		BlockState below = level.getBlockState(pos.below());
		return below.is(BlockTags.LEAVES) || below.is(BlockTags.ANIMALS_SPAWNABLE_ON) || below.is(BlockTags.DIRT) || below.is(BlockTags.SAND)
			|| below.is(BlockTags.FENCES) || below.is(BlockTags.SNOW);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData group) {
		BlockState below = level.getBlockState(this.blockPosition().below());
		this.setResting(below.is(BlockTags.LEAVES) || below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS));
		return super.finalizeSpawn(level, difficulty, reason, group);
	}
}
