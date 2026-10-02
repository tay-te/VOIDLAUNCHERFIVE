package dev.voidmc.expanse.entity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The small wild things that make the land feel lived in: butterflies, dragonflies, songbirds, herons,
 * owls. They are scenery rather than livestock, so they behave like vanilla's bat: ambient creatures
 * that live near whoever is there to see them and quietly leave once nobody is.
 *
 * <ul>
 *   <li>A colour variant (species, really), chosen at spawn from the climate and the hour, and shared by
 *       a flock or a cloud of butterflies.</li>
 *   <li>Two synced flags the models read: flying, and resting (perched, settled on a flower, asleep).</li>
 *   <li>They leave when no player is within {@link #LEAVE_DISTANCE}, and slip away out of their hours
 *       (an owl by day, a butterfly by night) once nobody is close enough to watch them go.</li>
 *   <li>No fall damage, no pushing, no tripwires: they are too light for any of it.</li>
 * </ul>
 */
public abstract class Critter extends AmbientCreature {
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Critter.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Critter.class, EntityDataSerializers.BYTE);
	private static final int FLYING = 1;
	private static final int RESTING = 2;
	private static final int SLEEPING = 4;
	/** Beyond this, with nobody nearer, a critter has no audience left and goes. */
	public static final double LEAVE_DISTANCE = 72.0;
	/** Natural spawns need a player at least this close: they are made for someone to see. */
	public static final double SPAWN_RADIUS = 56.0;

	protected Critter(EntityType<? extends Critter> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_FLAGS, (byte) 0);
	}

	// ------------------------------------------------------------------ variant and flags

	public int getVariant() {
		return this.entityData.get(DATA_VARIANT);
	}

	public void setVariant(int variant) {
		this.entityData.set(DATA_VARIANT, Mth.clamp(variant, 0, this.variantCount() - 1));
	}

	protected abstract int variantCount();

	/** The variant for a newcomer here, by climate and hour. */
	protected abstract int pickVariant(ServerLevelAccessor level, BlockPos pos, RandomSource random);

	private boolean flag(int bit) {
		return (this.entityData.get(DATA_FLAGS) & bit) != 0;
	}

	private void setFlag(int bit, boolean on) {
		byte flags = this.entityData.get(DATA_FLAGS);
		this.entityData.set(DATA_FLAGS, (byte) (on ? flags | bit : flags & ~bit));
	}

	public boolean isFlying() {
		return this.flag(FLYING);
	}

	public void setFlying(boolean flying) {
		this.setFlag(FLYING, flying);
	}

	public boolean isResting() {
		return this.flag(RESTING);
	}

	public void setResting(boolean resting) {
		this.setFlag(RESTING, resting);
	}

	public boolean isSleeping() {
		return this.flag(SLEEPING);
	}

	public void setSleeping(boolean sleeping) {
		this.setFlag(SLEEPING, sleeping);
	}

	/** An owl by day, a butterfly by night, a heron after dark. */
	protected abstract boolean isOutOfHours();

	// ------------------------------------------------------------------ spawning and leaving

	private record Kin(int variant) implements SpawnGroupData {
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData group) {
		if (group instanceof Kin kin) {
			this.setVariant(kin.variant());
		} else {
			int variant = this.pickVariant(level, this.blockPosition(), level.getRandom());
			this.setVariant(variant);
			group = new Kin(variant);
		}
		return super.finalizeSpawn(level, difficulty, reason, group);
	}

	@Override
	public void checkDespawn() {
		super.checkDespawn();
		if (this.isRemoved() || this.isPersistenceRequired() || this.requiresCustomPersistence()) {
			return;
		}
		Player player = this.level().getNearestPlayer(this, -1.0);
		if (player == null) {
			return;
		}
		double distSqr = player.distanceToSqr(this);
		if (distSqr > LEAVE_DISTANCE * LEAVE_DISTANCE) {
			this.discard();
		} else if (this.isOutOfHours() && distSqr > 24.0 * 24.0 && this.random.nextInt(400) == 0) {
			this.discard();
		}
	}

	/** Natural spawns only within {@link #SPAWN_RADIUS} of a player; any other way (eggs, commands) anywhere. */
	protected static boolean nearAPlayer(LevelAccessor level, EntitySpawnReason reason, BlockPos pos) {
		return reason != EntitySpawnReason.NATURAL
			|| level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPAWN_RADIUS, false) != null;
	}

	/** Under the open sky or a canopy, not in a cave. */
	protected static boolean outdoors(LevelAccessor level, BlockPos pos) {
		return level.getBrightness(LightLayer.SKY, pos) >= 8;
	}

	/** Air, or a plant a creature can sit in: nothing solid, no water. */
	protected static boolean open(LevelAccessor level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	protected static float temperature(LevelAccessor level, BlockPos pos) {
		return level.getBiome(pos).value().getBaseTemperature();
	}

	protected static int pick(RandomSource random, int... weightsByVariant) {
		int total = 0;
		for (int w : weightsByVariant) {
			total += w;
		}
		int roll = random.nextInt(Math.max(1, total));
		for (int i = 0; i < weightsByVariant.length; i++) {
			roll -= weightsByVariant[i];
			if (roll < 0) {
				return i;
			}
		}
		return 0;
	}

	// ------------------------------------------------------------------ moving

	/** Eases the velocity toward {@code speed} along the line to {@code to}, and turns to face the way it goes. */
	protected void steerTowards(Vec3 to, double speed, double agility) {
		Vec3 d = to.subtract(this.position());
		double len = d.length();
		Vec3 want = len < 1.0E-4 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.5) / len);
		Vec3 v = this.getDeltaMovement();
		v = v.add(want.subtract(v).scale(agility));
		this.setDeltaMovement(v);
		this.faceAlong(v, 25.0F);
	}

	protected void faceAlong(Vec3 v, float maxTurn) {
		if (v.horizontalDistanceSqr() > 1.0E-5) {
			float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, maxTurn));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
	}

	protected @Nullable Player nearestUnsneaking(double radius, double sneakingRadius) {
		Player player = this.level().getNearestPlayer(this, radius);
		if (player == null) {
			return null;
		}
		double r = player.isShiftKeyDown() ? sneakingRadius : radius;
		return player.distanceToSqr(this) <= r * r ? player : null;
	}

	// ------------------------------------------------------------------ too light for the world's rough edges

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.EVENTS;
	}

	// ------------------------------------------------------------------ saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Variant", this.getVariant());
		output.putBoolean("Resting", this.isResting());
		output.putBoolean("Flying", this.isFlying());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setVariant(input.getIntOr("Variant", 0));
		this.setResting(input.getBooleanOr("Resting", false));
		this.setFlying(input.getBooleanOr("Flying", false));
	}
}
