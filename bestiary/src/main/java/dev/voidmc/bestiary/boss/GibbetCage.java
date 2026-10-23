package dev.voidmc.bestiary.boss;

import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.TellColour;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The gibbet cage the Gallows Knight tears off its back at its phase change (BESTIARY.md §8.3): it falls and rolls, and
 * stays a hazard for the rest of the fight. Every 12-16 s it rattles (an orange tell to the player it turns on, 20 ticks)
 * and rolls at them across the arena; whoever it runs over takes a heavy blow (block it, dodge it) and is thrown aside.
 * It can't be hurt or targeted; it goes when the fight ends (a reset puts the cage back on the knight's back). Drawn with
 * its own little rig ({@code bestiary:gibbet_cage}: the knight's cage cubes), rolling on its walk cycle.
 */
public class GibbetCage extends Mob {
	enum State {
		REST, WARN, ROLL
	}

	public static final float DAMAGE = 7;
	public static final int WARN_TICKS = 20;
	public static final double SPEED = 0.34;

	@Nullable UUID boss;
	State state = State.REST;
	int timer;
	int bumps;
	float heading;
	final Map<Integer, Long> struck = new HashMap<>();
	/** Rolls made (tests). */
	public int rolls;

	public GibbetCage(EntityType<? extends GibbetCage> type, Level level) {
		super(type, level);
		this.setPermanentlyInvulnerable(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1)
			.add(Attributes.STEP_HEIGHT, 0.6);
	}

	/** Thrown down by the knight: it falls and rolls away along {@code yaw} for a moment. */
	public void thrown(UUID boss, float yaw) {
		this.boss = boss;
		this.heading = yaw;
		this.state = State.ROLL;
		this.timer = 30;
		this.face(yaw);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean removeWhenFarAway(double dist) {
		return false;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	void face(float yaw) {
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	public @Nullable UUID bossId() {
		return this.boss;
	}

	/** Rattles and rolls at the nearest fighter next tick (tests, the studio). */
	public void provoke() {
		if (this.state == State.REST) {
			this.timer = 1;
		}
	}

	public String stateName() {
		return this.state.name() + " " + this.timer;
	}

	public boolean rolling() {
		return this.state == State.ROLL;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Entity b = this.boss == null ? null : level.getEntity(this.boss);
		BossData d = b instanceof Mob m ? BossData.get(m) : null;
		if (d == null || !b.isAlive() || !d.active && this.state != State.ROLL) {
			// The fight is over (or the knight is gone): the cage stays where it lies only while the fight lasts.
			if (this.tickCount > 40) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.4, this.getZ(), 10, 0.3, 0.2, 0.3, 0.01);
				this.discard();
			}
			return;
		}
		long now = level.getGameTime();
		switch (this.state) {
			case REST -> {
				this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 1, 0.5));
				if (--this.timer <= 0) {
					LivingEntity t = this.pick(d, level);
					if (t != null) {
						this.state = State.WARN;
						this.timer = WARN_TICKS;
						this.heading = (float) Math.toDegrees(Math.atan2(t.getZ() - this.getZ(), t.getX() - this.getX())) - 90;
						this.face(this.heading);
						FrayApi.telegraphs().tell(this, t, TellColour.ORANGE, WARN_TICKS, Component.literal("The gibbet cage rolls"));
						level.playSound(null, this.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
					} else {
						this.timer = 40;
					}
				}
			}
			case WARN -> {
				// The rattle: it rocks and sheds rust (the tell on the ground).
				if (this.timer % 4 == 0) {
					level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.5, this.getZ(), 6, 0.35, 0.3, 0.35, 0.05);
					level.playSound(null, this.blockPosition(), SoundEvents.CHAIN_STEP, SoundSource.HOSTILE, 1.0F, 0.7F);
				}
				if (--this.timer <= 0) {
					this.state = State.ROLL;
					this.timer = 50;
					this.bumps = 0;
					this.rolls++;
				}
			}
			case ROLL -> {
				double r = Math.toRadians(this.heading + 90);
				Vec3 v = new Vec3(Math.cos(r) * SPEED, this.getDeltaMovement().y, Math.sin(r) * SPEED);
				this.setDeltaMovement(v);
				this.face(this.heading);
				this.strike(d, level, now);
				boolean leaving = d.arena.distance(this.position()) > d.arena.radius() - 1.0 && this.outward(d.arena);
				if (this.horizontalCollision && this.bumps < 2 && this.onGround()) {
					// A step or a root in the way: it bounces over it once or twice before it stops.
					this.bumps++;
					this.setDeltaMovement(this.getDeltaMovement().x, 0.42, this.getDeltaMovement().z);
				} else if (--this.timer <= 0 || this.horizontalCollision || leaving) {
					if (this.horizontalCollision || leaving) {
						level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 0.7F);
					}
					this.state = State.REST;
					this.timer = 240 + this.getRandom().nextInt(80);
					this.setDeltaMovement(Vec3.ZERO.add(0, this.getDeltaMovement().y, 0));
				}
			}
		}
	}

	/** Rolling away from the arena's centre (it stops at the edge rather than leave). */
	private boolean outward(Arena a) {
		double r = Math.toRadians(this.heading + 90);
		double px = this.getX() - (a.centre().getX() + 0.5), pz = this.getZ() - (a.centre().getZ() + 0.5);
		return Math.cos(r) * px + Math.sin(r) * pz > 0;
	}

	/** A fighter in the arena at least 3 blocks off (the nearest such). */
	@Nullable LivingEntity pick(BossData d, ServerLevel level) {
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (LivingEntity e : BossEncounter.inside(d, level)) {
			double dist = e.distanceToSqr(this);
			if (dist >= 9 && dist < bd) {
				bd = dist;
				best = e;
			}
		}
		return best;
	}

	/** Whoever the rolling cage touches takes a heavy blow (guardable, dodgeable) and is thrown aside; once a second each. */
	void strike(BossData d, ServerLevel level, long now) {
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.25),
			e -> e != this && e.isAlive() && !(e instanceof GibbetCage) && BossEncounter.fighters(level).contains(e));
		for (LivingEntity e : hit) {
			if (now - this.struck.getOrDefault(e.getId(), Long.MIN_VALUE / 2) < 20) {
				continue;
			}
			this.struck.put(e.getId(), now);
			Entity owner = this.boss == null ? null : level.getEntity(this.boss);
			HitContext ctx = new HitContext(owner instanceof LivingEntity le ? le : this, ItemStack.EMPTY, dev.voidmc.bestiary.Bestiary.id("gibbet_cage/roll"),
				DamageSpec.of(DamageKind.BLUNT, DAMAGE, 3), HitFlags.MELEE | HitFlags.AOE | HitFlags.HEAVY_TELL, 0, null);
			var r = FrayApi.damage().deal(e, ctx);
			if (r.total() > 0) {
				double a = Math.toRadians(this.heading + 90);
				e.setDeltaMovement(e.getDeltaMovement().add(Math.cos(a) * 0.6, 0.35, Math.sin(a) * 0.6));
				e.needsSync = true;
			}
			level.playSound(null, e.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5F, 1.2F);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		if (this.boss != null) {
			out.putString("bestiary_boss", this.boss.toString());
		}
		out.putInt("bestiary_rolls", this.rolls);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		this.boss = in.getString("bestiary_boss").map(UUID::fromString).orElse(null);
		this.rolls = in.getIntOr("bestiary_rolls", 0);
		this.state = State.REST;
		this.timer = 100;
	}
}
