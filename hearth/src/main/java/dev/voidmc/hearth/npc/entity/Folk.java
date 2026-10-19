package dev.voidmc.hearth.npc.entity;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.QuestService;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.look.FolkLook;
import dev.voidmc.hearth.npc.person.Office;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code hearth:folk}: a resident made flesh. The {@link dev.voidmc.hearth.api.Resident} record is the person; this
 * entity is a view of them that exists only near players, is never saved with the chunk (its type is {@code noSave}),
 * and writes what happened back into the record when it goes away.
 *
 * <p>Everything the client needs to draw them is synced: name, appearance genome, culture, job, age, sex, the quest
 * marker above their head and the pose they are in. Behaviour lives in a server-side {@link FolkBrain}, attached by the
 * embodiment manager when it binds the entity to a resident.
 */
public class Folk extends PathfinderMob {
	private static final EntityDataAccessor<String> DATA_RESIDENT = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_APPEARANCE = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);
	/**
	 * Culture (bits 0-4), job (5-11), age stage (12-13), sex (14), station (15-18) and office (19-20) packed into one int:
	 * they change together and rarely.
	 */
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_MARKER = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.BYTE);

	private @Nullable FolkBrain brain;
	/** Drives folk who are not residents of a settlement (a caravan's traders and guards on the road). */
	private @Nullable Controller controller;
	private long settlementId = Long.MIN_VALUE;
	/** Set once the entity has been folded back into its record, so removal does not fold it twice. */
	private boolean folded;

	public Folk(EntityType<? extends Folk> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setCanPickUpLoot(false);
		if (this.getNavigation() instanceof FolkNavigation nav) {
			nav.setCanOpenDoors(true);
			nav.setCanFloat(true);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new FolkNavigation(this, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_RESIDENT, "");
		entityData.define(DATA_NAME, "");
		entityData.define(DATA_APPEARANCE, 0);
		entityData.define(DATA_LOOK, packLook(Culture.TEMPERATE, JobType.NONE, AgeStage.ADULT, false, Station.POOR, Office.NONE));
		entityData.define(DATA_MARKER, (byte) 0);
		entityData.define(DATA_POSE, (byte) 0);
	}

	@Override
	protected void registerGoals() {
		// Behaviour is the brain's (FolkBrain), driven from customServerAiStep; the goal selector only keeps the
		// vanilla door handling and swimming, which hook into navigation.
		this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
		this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
	}

	// ---------------------------------------------------------------- identity

	/** Binds this entity to a resident: what it shows and who it is. Server only. */
	public void setIdentity(UUID resident, String name, int appearance, Culture culture, JobType job, AgeStage age, boolean female) {
		setIdentity(resident, name, appearance, culture, job, age, female, Station.of(job), Office.NONE);
	}

	/** Binds this entity to a resident, with their station (dress and bearing) and any office they hold. Server only. */
	public void setIdentity(UUID resident, String name, int appearance, Culture culture, JobType job, AgeStage age, boolean female, Station station,
		Office office) {
		this.entityData.set(DATA_RESIDENT, resident.toString());
		this.entityData.set(DATA_NAME, name);
		this.entityData.set(DATA_APPEARANCE, appearance);
		this.entityData.set(DATA_LOOK, packLook(culture, job, age, female, station, office));
		this.refreshDimensions();
	}

	/** Changes station or office in place (a household rose or fell, the captain died and the next guard took over). */
	public void setStanding(Station station, Office office) {
		int look = this.entityData.get(DATA_LOOK);
		int packed = look & ~(0x3F << 15) | (station.ordinal() & 15) << 15 | (office.ordinal() & 3) << 19;
		if (packed != look) {
			this.entityData.set(DATA_LOOK, packed);
		}
	}

	public @Nullable UUID residentId() {
		String s = this.entityData.get(DATA_RESIDENT);
		if (s.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public String folkName() {
		return this.entityData.get(DATA_NAME);
	}

	public int appearance() {
		return this.entityData.get(DATA_APPEARANCE);
	}

	public Culture culture() {
		return look().culture();
	}

	public JobType job() {
		return look().job();
	}

	public AgeStage ageStage() {
		return look().age();
	}

	public boolean female() {
		return look().female();
	}

	public Station station() {
		return look().station();
	}

	public Office office() {
		return look().office();
	}

	public FolkLook look() {
		return unpackLook(appearance(), this.entityData.get(DATA_LOOK));
	}

	/** Culture, job, age, sex, station and office in one synced int (see {@code DATA_LOOK}). */
	public static int packLook(Culture culture, JobType job, AgeStage age, boolean female, Station station, Office office) {
		return culture.ordinal() & 31 | (job.ordinal() & 127) << 5 | (age.ordinal() & 3) << 12 | (female ? 1 : 0) << 14 | (station.ordinal() & 15) << 15
			| (office.ordinal() & 3) << 19;
	}

	/** The look a packed int and an appearance genome stand for; out-of-range fields fall back to plain defaults. */
	public static FolkLook unpackLook(int appearance, int packed) {
		Culture[] cultures = Culture.values();
		JobType[] jobs = JobType.values();
		AgeStage[] ages = AgeStage.values();
		Station[] stations = Station.values();
		int c = packed & 31;
		int j = packed >>> 5 & 127;
		int a = packed >>> 12 & 3;
		int st = packed >>> 15 & 15;
		return new FolkLook(appearance, c < cultures.length ? cultures[c] : Culture.TEMPERATE, j < jobs.length ? jobs[j] : JobType.NONE,
			a < ages.length ? ages[a] : AgeStage.ADULT, (packed >>> 14 & 1) != 0, st < stations.length ? stations[st] : Station.PEASANT,
			Office.byId(packed >>> 19 & 3));
	}

	public QuestService.Marker marker() {
		int v = this.entityData.get(DATA_MARKER);
		QuestService.Marker[] all = QuestService.Marker.values();
		return v >= 0 && v < all.length ? all[v] : QuestService.Marker.NONE;
	}

	public void setMarker(QuestService.Marker marker) {
		if (marker() != marker) {
			this.entityData.set(DATA_MARKER, (byte) marker.ordinal());
		}
	}

	public FolkPose folkPose() {
		return FolkPose.byId(this.entityData.get(DATA_POSE));
	}

	public void setFolkPose(FolkPose pose) {
		if (folkPose() != pose) {
			this.entityData.set(DATA_POSE, (byte) pose.ordinal());
		}
	}

	/** Set while a builder works on scaffolding: nobody shoves them off it (server side, not saved). */
	private boolean steady;

	public void setSteady(boolean steady) {
		this.steady = steady;
	}

	@Override
	public boolean isPushable() {
		return !this.steady && super.isPushable();
	}

	public long settlementId() {
		return this.settlementId;
	}

	public void setSettlementId(long id) {
		this.settlementId = id;
	}

	public @Nullable FolkBrain brain() {
		return this.brain;
	}

	public void setBrain(@Nullable FolkBrain brain) {
		this.brain = brain;
	}

	public @Nullable Controller controller() {
		return this.controller;
	}

	public void setController(@Nullable Controller controller) {
		this.controller = controller;
	}

	public boolean isFolded() {
		return this.folded;
	}

	public void markFolded() {
		this.folded = true;
	}

	@Override
	public boolean isBaby() {
		return ageStage() == AgeStage.CHILD;
	}

	@Override
	public float getAgeScale() {
		return isBaby() ? 0.62F : 1.0F;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_LOOK.equals(accessor)) {
			this.refreshDimensions();
		}
	}

	@Override
	protected Component getTypeName() {
		String name = folkName();
		return name.isEmpty() ? super.getTypeName() : Component.literal(name);
	}

	// ---------------------------------------------------------------- sleeping without touching the bed

	/**
	 * Lies down in a bed. Unlike {@link #startSleeping} this leaves the bed block alone (no occupied flag), so folding a
	 * sleeping folk away, or a chunk unloading under them, never leaves a bed marked as taken.
	 */
	public void sleepIn(BlockPos bed) {
		BlockState state = this.level().getBlockState(bed);
		if (this.isPassenger()) {
			this.stopRiding();
		}
		this.getNavigation().stop();
		double height = 0.5625;
		this.setPos(bed.getX() + 0.5, bed.getY() + height + 0.125, bed.getZ() + 0.5);
		this.setPose(Pose.SLEEPING);
		this.setSleepingPos(bed);
		this.setDeltaMovement(Vec3.ZERO);
		if (state.hasProperty(BedBlock.FACING)) {
			Direction facing = state.getValue(BedBlock.FACING);
			this.setYRot(facing.toYRot());
		}
		this.needsSync = true;
	}

	@Override
	public void stopSleeping() {
		Optional<BlockPos> bed = this.getSleepingPos();
		this.clearSleepingPos();
		this.setPose(Pose.STANDING);
		bed.ifPresent(pos -> {
			// Stand up beside the bed.
			BlockPos stand = FolkSpots.standableNear(this.level(), pos, 2);
			if (stand != null) {
				this.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
			} else {
				this.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
			}
		});
	}

	@Override
	public boolean startSleeping(BlockPos bedPosition) {
		sleepIn(bedPosition);
		return true;
	}

	// ---------------------------------------------------------------- behaviour

	/** Server time spent ticking folk entities (the whole entity tick: AI, pathing, movement), for the dev harness and tours. */
	public static final java.util.concurrent.atomic.LongAdder SERVER_TICK_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder SERVER_TICKS = new java.util.concurrent.atomic.LongAdder();

	@Override
	public void tick() {
		if (this.level().isClientSide()) {
			super.tick();
			return;
		}
		long t0 = System.nanoTime();
		super.tick();
		SERVER_TICK_NANOS.add(System.nanoTime() - t0);
		SERVER_TICKS.increment();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.brain != null) {
			this.brain.tick(level);
		} else if (this.controller != null) {
			this.controller.tick(level, this);
		} else if (this.tickCount > 40 && !this.folded) {
			// An entity nobody manages (summoned by command, loaded from an old save): adopt it or let it go.
			NpcModule.embodiment().adoptStray(level, this);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.isAlive()) {
			return InteractionResult.PASS;
		}
		if (this.isSleeping() && !player.isShiftKeyDown()) {
			// Asleep: they mumble and the player is told to sneak to wake them, never silence.
			if (player instanceof ServerPlayer sp && this.brain != null) {
				NpcModule.conversations().asleep(sp, this);
			}
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer sp) {
			if (this.brain == null && this.controller != null) {
				this.controller.interact(sp, this);
			} else {
				NpcModule.conversations().open(sp, this);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
			// A block put where they stood (a builder's step, the chunk applier, a player): they step out of it rather
			// than die in it, since a resident's death is for good.
			BlockPos out = FolkSpots.standableNear(level, this.blockPosition(), 3);
			if (out != null && !this.isSleeping()) {
				this.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
			}
			return false;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.brain != null) {
			this.brain.onHurt(level, source);
		} else if (hurt && this.controller != null) {
			this.controller.hurt(level, this, source);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			if (this.controller != null) {
				this.controller.died(level, this, source);
			} else {
				NpcModule.embodiment().onDeath(level, this, source);
			}
		}
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		super.onRemoval(reason);
		if (this.level() instanceof ServerLevel level && reason != Entity.RemovalReason.KILLED && !this.folded) {
			if (this.controller != null) {
				this.controller.removed(level, this);
			} else {
				NpcModule.embodiment().onRemoved(level, this);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
		// The embodiment manager decides when folk come and go.
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		// Their tools and bread are theirs; nothing drops.
	}

	@Override
	protected void dropEquipment(ServerLevel level) {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** Folk belong to their town: a portal would carry the entity off to where no settlement can take it back. */
	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.setLeftHanded(this.random.nextFloat() < 0.1F);
		return data;
	}

	/**
	 * What drives a folk who is not a settlement's resident made flesh: the people of a caravan on the road between
	 * towns. They have no record to fold back into; the controller decides everything.
	 */
	public interface Controller {
		void tick(ServerLevel level, Folk folk);

		/** A player right-clicked them (there is no conversation screen without a resident). */
		void interact(ServerPlayer player, Folk folk);

		default void hurt(ServerLevel level, Folk folk, DamageSource source) {
		}

		default void died(ServerLevel level, Folk folk, DamageSource source) {
		}

		/** Removed by the game (chunk unload, command) rather than killed. */
		default void removed(ServerLevel level, Folk folk) {
		}
	}

	// ---------------------------------------------------------------- sounds: people, not villagers

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		float base = female() ? 1.12F : 0.95F;
		if (isBaby()) {
			base += 0.25F;
		} else if (ageStage() == AgeStage.ELDER) {
			base -= 0.08F;
		}
		return base + (this.random.nextFloat() - 0.5F) * 0.08F;
	}
}
