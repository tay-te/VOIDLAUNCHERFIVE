package dev.voidmc.bestiary.anim;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.boss.Boss;
import dev.voidmc.bestiary.boss.BossData;
import dev.voidmc.bestiary.boss.BossEncounter;
import dev.voidmc.bestiary.boss.GibbetCage;
import dev.voidmc.bestiary.boss.KnightScript;
import dev.voidmc.fray.motion.MotionServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Gallows Knight (BESTIARY.md §8.3), stage 1.5's boss: the custom rig (real elbows, knees, spine, a jointed chain
 * with a hook, the gibbet cage on its back) drawn by Fray's {@code RigModel}, fought through Bestiary's AI framework
 * (profile {@code bestiary:gallows_knight}: every move a clip with its tell, chosen by the utility layer, tokens ignored)
 * and the encounter framework ({@link BossEncounter}: arena, start, reset, bar, party health, personal loot). Its phase
 * script ({@link KnightScript}) tears the cage off at 55 % and opens phase 2's moves; the Break kneels it for 60 ticks.
 */
public class GallowsKnight extends Monster implements Boss {
	public static final Identifier PROFILE = Bestiary.id("gallows_knight");
	public static final Identifier CHAIN_SWEEP = Bestiary.id("gallows_knight/chain_sweep");
	public static final Identifier STAGGER = Bestiary.id("gallows_knight/stagger");
	public static final Identifier BROKEN = Bestiary.id("gallows_knight/broken");
	public static final Identifier LOOT = Bestiary.id("boss/gallows_knight");
	/** BESTIARY §8.3: phase 2 from 55 % health. */
	public static final float PHASE_2 = 0.55F;
	private static final EntityDataAccessor<Boolean> DATA_CAGED = SynchedEntityData.defineId(GallowsKnight.class, EntityDataSerializers.BOOLEAN);

	/** The phase script's state (not saved: it re-derives from {@link BossData} after a load). */
	public final KnightScript script = new KnightScript(this);

	public GallowsKnight(EntityType<? extends GallowsKnight> type, Level level) {
		super(type, level);
		this.xpReward = 50;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 900)
			.add(Attributes.ARMOR, 12)
			.add(Attributes.ATTACK_DAMAGE, 10)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
			.add(Attributes.FOLLOW_RANGE, 32)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CAGED, true);
	}

	/** Whether the gibbet cage is still on its back (the client hides the cage bone when it isn't). */
	public boolean caged() {
		return this.entityData.get(DATA_CAGED);
	}

	public void setCaged(boolean caged) {
		this.entityData.set(DATA_CAGED, caged);
	}

	@Override
	protected void registerGoals() {
		// Bestiary's combat goal goes in when the AI takes it over (its profile); no vanilla attack or target goals.
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (BossData.get(this) == null) {
			// Summoned or spawned some other way: its arena is where it stands.
			BossEncounter.setup(this, dev.voidmc.bestiary.boss.Arena.around(this.blockPosition(), 14, this.getYRot()));
		}
		BossEncounter.tick(this, level);
		this.script.tick(level);
	}

	/** Busy while a Fray action runs: no turning, no walking (the swing keeps the facing it started with). */
	public boolean acting() {
		return MotionServer.running(this) != null;
	}

	@Override
	public boolean removeWhenFarAway(double dist) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CHAIN_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	// ------------------------------------------------------------------ Boss

	@Override
	public Component bossName() {
		return Component.literal("The Gallows Knight");
	}

	@Override
	public @Nullable Component bossSubtitle() {
		BossData d = BossData.get(this);
		return Component.literal(d != null && d.arena.mound().isPresent() ? "Gallows Hill" : "The hanged man's ground");
	}

	@Override
	public float[] phaseThresholds() {
		return new float[] {PHASE_2};
	}

	@Override
	public Identifier personalLoot() {
		return LOOT;
	}

	@Override
	public void onFightStart(ServerLevel level, LivingEntity first) {
		this.script.fightStarted(level);
	}

	@Override
	public void onPhase(ServerLevel level, int phase) {
		this.script.phase(level, phase);
	}

	@Override
	public void onReset(ServerLevel level) {
		this.script.reset(level);
	}

	@Override
	public boolean busyScripted() {
		return this.script.transitioning();
	}

	/** The brain Bestiary runs this knight with (null before the AI took it over). */
	public @Nullable Brain brain() {
		return Brain.get(this);
	}

	/** Cages thrown by this knight, in the level. */
	public java.util.List<GibbetCage> cages(ServerLevel level) {
		return level.getEntitiesOfClass(GibbetCage.class, this.getBoundingBox().inflate(64), c -> this.getUUID().equals(c.bossId()));
	}
}
