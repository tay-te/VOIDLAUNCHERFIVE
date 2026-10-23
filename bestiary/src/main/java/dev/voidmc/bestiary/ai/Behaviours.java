package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.bestiary.api.MoveBehaviour;
import dev.voidmc.bestiary.mixin.CreeperAccess;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.StatusType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The move behaviours (BESTIARY.md §9.1's stage 1 moves) by id: {@code melee} (the clip's capsules do everything),
 * {@code lunge} (a push toward the target as the strike begins), {@code grab} (a caught target is held and bitten until
 * it dodges free), {@code arrow} (vanilla arrows on the strike tick: quick and aimed shots, volleys, backstep shots),
 * {@code pounce} (a leap at the target; the fangs' capsule sweeps in flight), {@code web} (a spit that Roots what it
 * reaches unless dodged) and {@code fuse} (a creeper's fuse: the tell; a Stagger resets it).
 */
public final class Behaviours {
	private static final Map<String, MoveBehaviour> BY_ID = new ConcurrentHashMap<>();
	private static final MoveBehaviour MELEE = new MoveBehaviour() {
	};

	private Behaviours() {
	}

	public static void register(Identifier id, MoveBehaviour b) {
		BY_ID.put(id.getNamespace().equals(Bestiary.ID) ? id.getPath() : id.toString(), b);
	}

	public static MoveBehaviour get(String id) {
		return BY_ID.getOrDefault(id, MELEE);
	}

	public static void init() {
		BY_ID.put("melee", MELEE);
		BY_ID.put("lunge", new Lunge());
		BY_ID.put("grab", new Grab());
		BY_ID.put("arrow", new Arrow());
		BY_ID.put("pounce", new Pounce());
		BY_ID.put("web", new Web());
		BY_ID.put("fuse", new Fuse());
	}

	static Vec3 toward(Mob mob, LivingEntity t) {
		Vec3 d = t.position().subtract(mob.position()).multiply(1, 0, 1);
		return d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize();
	}

	/** A push toward the target a tick before the strike (the server moves the creature; the clip's root motion is a picture). */
	static final class Lunge implements MoveBehaviour {
		@Override
		public void tick(Context ctx, int tick) {
			if (tick == ctx.strikeTick() - 1 && ctx.mob().onGround()) {
				float power = ctx.move().param("power", 0.55F);
				Vec3 d = toward(ctx.mob(), ctx.target()).scale(power);
				ctx.mob().setDeltaMovement(ctx.mob().getDeltaMovement().add(d.x, 0.1, d.z));
				ctx.mob().needsSync = true;
			}
		}
	}

	/** The lunge-grab: a target the grab's capsule touched (and didn't dodge) is Rooted and bitten until it dodges free. */
	static final class Grab implements MoveBehaviour {
		@Override
		public void tick(Context ctx, int tick) {
			Mob mob = ctx.mob();
			LivingEntity t = ctx.target();
			if (tick == ctx.strikeTick() - 1 && mob.onGround()) {
				Vec3 d = toward(mob, t).scale(ctx.move().param("power", 0.6F));
				mob.setDeltaMovement(mob.getDeltaMovement().add(d.x, 0.1, d.z));
				mob.needsSync = true;
			}
			long since = ctx.level().getGameTime() - Grabs.since(t);
			boolean held = Grabs.by(t) == mob.getId();
			if (!held && tick > ctx.strikeTick() && Grabs.hitBy(mob, t, ctx.start())) {
				Grabs.hold(t, mob, ctx.level().getGameTime());
				FrayApi.statuses().apply(t, StatusType.ROOTED, 40, mob);
				ctx.level().playSound(null, t.blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 0.6F, 1.4F);
				return;
			}
			if (held) {
				var cur = FrayApi.actions().current(t);
				boolean free = cur != null && (cur.getPath().contains("dodge") || cur.getPath().contains("backstep"));
				if (free || since > 40 || mob.distanceTo(t) > 3) {
					Grabs.release(t);
					FrayApi.statuses().clear(t, StatusType.ROOTED);
				} else if (since > 0 && since % 10 == 0) {
					// A bite, through the pipeline (purple: dodge free; it can't be blocked).
					DamageSpec spec = DamageSpec.of(DamageKind.PIERCE, ctx.move().param("bite", 2), 2);
					FrayApi.damage().deal(t, new HitContext(mob, ItemStack.EMPTY, ctx.move().clip(), spec, HitFlags.MELEE | HitFlags.GRAB | HitFlags.UNBLOCKABLE
						| HitFlags.MULTI_HIT, 0, null));
				}
			}
		}

		@Override
		public void end(Context ctx, boolean interrupted) {
			if (Grabs.by(ctx.target()) == ctx.mob().getId()) {
				Grabs.release(ctx.target());
				FrayApi.statuses().clear(ctx.target(), StatusType.ROOTED);
			}
		}
	}

	/**
	 * Arrows on the strike tick: {@code count} arrows (a volley spreads them), {@code inaccuracy}, {@code damage} (×
	 * vanilla's arrow), {@code backstep} (a hop away from the target first). Needs a bow in hand and a clear line.
	 */
	static final class Arrow implements MoveBehaviour {
		@Override
		public boolean canStart(ServerLevel level, Mob mob, Brain brain, MoveDef move, LivingEntity target) {
			return mob.isHolding(Items.BOW) && mob.getSensing().hasLineOfSight(target) && clearOfAllies(level, mob, target);
		}

		@Override
		public void start(Context ctx) {
			if (ctx.move().param("backstep", 0) > 0 && ctx.mob().onGround()) {
				Vec3 d = toward(ctx.mob(), ctx.target()).scale(-ctx.move().param("backstep", 0.5F));
				ctx.mob().setDeltaMovement(ctx.mob().getDeltaMovement().add(d.x, 0.25, d.z));
				ctx.mob().needsSync = true;
			}
		}

		@Override
		public void tick(Context ctx, int tick) {
			Mob mob = ctx.mob();
			if (tick == ctx.strikeTick() - 3 && ctx.move().param("glint", 0) > 0) {
				// The aimed shot's glint: a spark on the arrow's tip, three ticks before the release.
				Vec3 at = mob.getEyePosition().add(mob.getLookAngle().scale(0.6));
				ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.01);
			}
			if (tick != ctx.strikeTick() || !mob.isHolding(Items.BOW)) {
				return;
			}
			int count = (int) ctx.move().param("count", 1);
			float spread = ctx.move().param("spread", 6);
			for (int i = 0; i < count; i++) {
				float yawOff = count == 1 ? 0 : (i - (count - 1) / 2F) * spread;
				shoot(ctx, yawOff);
			}
			mob.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
		}

		private static void shoot(Context ctx, float yawOff) {
			Mob mob = ctx.mob();
			LivingEntity t = ctx.target();
			ItemStack bow = mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(mob, Items.BOW));
			ItemStack ammo = mob.getProjectile(bow);
			AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, ammo, 1.0F, bow);
			arrow.setBaseDamage((2.0 + mob.level().getDifficulty().getId() * 0.11) * ctx.move().param("damage_scale", 1) * Scaling.damage(mob));
			double xd = t.getX() - mob.getX(), zd = t.getZ() - mob.getZ();
			if (yawOff != 0) {
				double r = Math.toRadians(yawOff), c = Math.cos(r), s = Math.sin(r);
				double nx = xd * c - zd * s, nz = xd * s + zd * c;
				xd = nx;
				zd = nz;
			}
			double yd = t.getY(1 / 3.0) - arrow.getY();
			double dist = Math.sqrt(xd * xd + zd * zd);
			Projectile.spawnProjectileUsingShoot(arrow, ctx.level(), ammo, xd, yd + dist * 0.2F, zd, ctx.move().param("power", 1.6F),
				ctx.move().param("inaccuracy", 6));
		}

		/** Ranged creatures never shoot through an ally (BESTIARY.md §5). */
		static boolean clearOfAllies(ServerLevel level, Mob mob, LivingEntity target) {
			Vec3 a = mob.getEyePosition(), b = target.getEyePosition();
			for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().minmax(target.getBoundingBox()).inflate(0.5), o -> o != mob && o != target
				&& Brain.get(o) != null)) {
				if (other.getBoundingBox().inflate(0.2).clip(a, b).isPresent()) {
					return false;
				}
			}
			return true;
		}
	}

	/** The pounce: a leap at the target a tick before the strike; the clip's fang capsule sweeps where the body flies. */
	static final class Pounce implements MoveBehaviour {
		@Override
		public boolean canStart(ServerLevel level, Mob mob, Brain brain, MoveDef move, LivingEntity target) {
			return mob.onGround() || mob.onClimbable();
		}

		@Override
		public void tick(Context ctx, int tick) {
			if (tick != ctx.strikeTick() - 1) {
				return;
			}
			Mob mob = ctx.mob();
			Vec3 to = ctx.target().position().subtract(mob.position());
			double h = Math.sqrt(to.x * to.x + to.z * to.z);
			double speed = Math.min(1.25, 0.18 * h + 0.25);
			Vec3 d = h < 1e-3 ? Vec3.ZERO : new Vec3(to.x / h * speed, Math.max(0.32, Math.min(0.6, 0.3 + to.y * 0.08)), to.z / h * speed);
			mob.setDeltaMovement(d);
			mob.needsSync = true;
			ctx.level().playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1, 0.7F);
		}
	}

	/** The web spit: on the strike tick a strand flies at the target; what it reaches (and didn't dodge) is Rooted. */
	static final class Web implements MoveBehaviour {
		@Override
		public boolean canStart(ServerLevel level, Mob mob, Brain brain, MoveDef move, LivingEntity target) {
			return mob.getSensing().hasLineOfSight(target);
		}

		@Override
		public void tick(Context ctx, int tick) {
			Mob mob = ctx.mob();
			LivingEntity t = ctx.target();
			int strike = ctx.strikeTick();
			double dist = mob.distanceTo(t);
			int travel = (int) Math.max(1, Math.ceil(dist / 1.6));
			if (tick == strike) {
				Vec3 a = mob.getEyePosition(), b = t.getEyePosition();
				ItemParticleOption web = new ItemParticleOption(ParticleTypes.ITEM, Items.COBWEB);
				for (int i = 0; i <= 8; i++) {
					Vec3 p = a.lerp(b, i / 8.0);
					ctx.level().sendParticles(web, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
				}
				ctx.level().playSound(null, mob.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 0.8F, 0.6F);
				ctx.brain().moveState = travel;
			}
			if (ctx.brain().moveState instanceof Integer tr && tick == strike + tr) {
				ctx.brain().moveState = null;
				boolean reaches = mob.distanceTo(t) <= ctx.move().maxRange() + 2 && mob.getSensing().hasLineOfSight(t);
				GuardOutcome g = t instanceof net.minecraft.server.level.ServerPlayer ? FrayApi.defense().preview(t, mob, HitFlags.GRAB | HitFlags.UNBLOCKABLE)
					: GuardOutcome.NONE;
				boolean dodged = g == GuardOutcome.DODGED || g == GuardOutcome.SLIP || FrayApi.actions().invulnerable(t);
				if (reaches && !dodged) {
					FrayApi.statuses().apply(t, StatusType.ROOTED, (int) ctx.move().param("root", 30), mob);
					ctx.level().sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.COBWEB), t.getX(), t.getY() + 0.3, t.getZ(), 12, 0.4, 0.2, 0.4, 0.02);
				}
			}
		}
	}

	/**
	 * A creeper's fuse (BESTIARY.md §9.1): the fuse is the tell (red, 30 ticks, the swell and hiss are vanilla's). It
	 * keeps burning while the target stays within 7 blocks; a Stagger (a heavy hit's poise damage) interrupts the clip,
	 * and an interrupted fuse resets: hit it hard to defuse it.
	 */
	static final class Fuse implements MoveBehaviour {
		@Override
		public boolean canStart(ServerLevel level, Mob mob, Brain brain, MoveDef move, LivingEntity target) {
			return mob instanceof Creeper;
		}

		@Override
		public void start(Context ctx) {
			if (ctx.mob() instanceof Creeper c) {
				c.setSwellDir(1);
			}
		}

		@Override
		public void tick(Context ctx, int tick) {
			if (ctx.mob() instanceof Creeper c && !c.isIgnited() && ctx.target().distanceToSqr(c) > 7 * 7) {
				defuse(c);
			}
		}

		@Override
		public void end(Context ctx, boolean interrupted) {
			if (ctx.mob() instanceof Creeper c && !c.isIgnited() && c.isAlive()) {
				defuse(c);
			}
		}

		static void defuse(Creeper c) {
			c.setSwellDir(-1);
			((CreeperAccess) c).bestiary$setSwell(0);
		}
	}
}
