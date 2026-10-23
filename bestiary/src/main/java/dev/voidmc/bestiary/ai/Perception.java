package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.ai.math.SenseMath;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.api.BestiaryApi;
import dev.voidmc.bestiary.api.StealthModifier;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.PerceptionService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Perception (BESTIARY.md §3), run every 4 ticks per creature (staggered by entity id): sight in a cone (close sense
 * whatever the cone), shortened by darkness, night, sneaking, invisibility and Mettle's stealth modifiers; hearing of
 * Fray's noises and of footsteps; memory of the last known position for 30 s; alerting its kind. It moves the
 * awareness state and picks the target (Fray's threat first among what it perceives).
 */
public final class Perception {
	public static final int PERIOD = 4;
	private static final List<StealthModifier> STEALTH = new CopyOnWriteArrayList<>();
	/** How far a candidate is looked for at all. */
	private static final double SEARCH = 32;

	private Perception() {
	}

	public static void addStealth(StealthModifier m) {
		STEALTH.add(m);
	}

	static float stealth(Mob observer, LivingEntity target, StealthModifier.Sense sense) {
		float v = 1;
		for (StealthModifier m : STEALTH) {
			v = m.modify(observer, target, sense, v);
		}
		return v;
	}

	/** Players and registered bots (test mannequins) near the creature that it could fight. */
	static List<LivingEntity> candidates(Mob mob, ServerLevel level) {
		List<LivingEntity> out = new ArrayList<>(4);
		Brain b = Brain.get(mob);
		java.util.function.Predicate<LivingEntity> leash = b == null ? null : b.leash;
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(mob) <= SEARCH * SEARCH && (leash == null || leash.test(p))) {
				out.add(p);
			}
		}
		for (LivingEntity bot : BestiaryApi.bots()) {
			if (bot.level() == level && bot.isAlive() && bot.distanceToSqr(mob) <= SEARCH * SEARCH && (leash == null || leash.test(bot))) {
				out.add(bot);
			}
		}
		return out;
	}

	/** Whether the creature sees {@code t} right now. */
	public static boolean sees(Brain b, LivingEntity t, ServerLevel level) {
		Mob mob = b.mob;
		CreatureProfile.Senses s = b.profile.senses();
		double dx = t.getX() - mob.getX(), dz = t.getZ() - mob.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz + (t.getY() - mob.getY()) * (t.getY() - mob.getY()));
		if (dist <= SenseMath.CLOSE_SENSE) {
			return true;
		}
		if (!SenseMath.inCone(mob.getYHeadRot(), dx, dz, s.fov())) {
			return false;
		}
		BlockPos at = t.blockPosition();
		int light = level.getMaxLocalRawBrightness(at);
		boolean night = level.isDarkOutside();
		boolean sneaking = t.isDiscrete();
		float range = SenseMath.sightRange(s.sight(), s.nightSight(), night, s.seesInDark(), light, sneaking, t.isInvisible(),
			stealth(mob, t, StealthModifier.Sense.SIGHT));
		return dist <= range && mob.getSensing().hasLineOfSight(t);
	}

	public static void sense(Brain b, ServerLevel level, long now) {
		long t0 = System.nanoTime();
		try {
			run(b, level, now);
		} finally {
			Brain.perceptionNanos += System.nanoTime() - t0;
			Brain.perceptionRuns++;
		}
	}

	private static void run(Brain b, ServerLevel level, long now) {
		Mob mob = b.mob;
		CreatureProfile p = b.profile;
		if (p.dayNeutral() && mob.getLightLevelDependentMagicValue() >= 0.5F && mob.getLastHurtByMob() == null) {
			// Spiders keep vanilla's rule: neutral in daylight unless hurt.
			if (b.awareness != Brain.Awareness.ENGAGED || b.target == null || b.target.distanceToSqr(mob) > 16 * 16) {
				b.setAwareness(Brain.Awareness.UNAWARE, now);
				forget(b);
				return;
			}
		}
		// The target vanilla gave it (hurt by, villagers, golems) counts as engaged.
		LivingEntity vanillaTarget = mob.getTarget();
		List<LivingEntity> cands = candidates(mob, level);
		LivingEntity seen = null;
		double best = Double.MAX_VALUE;
		LivingEntity top = FrayApi.threat() == null ? null : FrayApi.threat().top(mob);
		for (LivingEntity c : cands) {
			if (!sees(b, c, level)) {
				continue;
			}
			double d = c.distanceToSqr(mob) * (c == top ? 0.25 : 1) * (c == b.target ? 0.6 : 1);
			if (d < best) {
				best = d;
				seen = c;
			}
		}
		// Hearing: Fray's noises, and footsteps of candidates nearby.
		Vec3 heard = null;
		float hearing = p.senses().hearing();
		for (PerceptionService.Noise n : FrayApi.perception().heard(level, mob.position(), now - PERIOD)) {
			if (n.sourceId() != mob.getId() && SenseMath.hears(n.radius(), n.pos().distanceTo(mob.position()), hearing)) {
				heard = n.pos();
				break;
			}
		}
		if (heard == null) {
			for (LivingEntity c : cands) {
				boolean moving = c.getDeltaMovement().horizontalDistance() > 0.03 || Math.hypot(c.getX() - c.xo, c.getZ() - c.zo) > 0.02;
				float r = SenseMath.footsteps(moving, c.isSprinting(), c.isDiscrete(), stealth(mob, c, StealthModifier.Sense.STEPS));
				if (r > 0 && SenseMath.hears(r, c.distanceTo(mob), hearing)) {
					heard = c.position();
					break;
				}
			}
		}
		if (b.awareness == Brain.Awareness.FLEEING) {
			// Morale decides when it stops running (Squads); it still keeps an eye on what it runs from.
			if (seen != null) {
				b.target = seen;
				b.lastSeen = now;
			}
			return;
		}
		if (seen != null) {
			boolean wasEngaged = b.awareness == Brain.Awareness.ENGAGED;
			b.target = seen;
			b.lastKnown = seen.position();
			b.lastSeen = now;
			b.setAwareness(Brain.Awareness.ENGAGED, now);
			if (!wasEngaged) {
				onEngage(b, seen, level);
			}
		} else if (vanillaTarget != null && vanillaTarget != b.target && vanillaTarget.isAlive() && (b.leash == null || b.leash.test(vanillaTarget))) {
			// Someone else's targeting (hurt by, villagers, golems, other mods' goals): being hit reveals the attacker.
			b.target = vanillaTarget;
			b.lastKnown = vanillaTarget.position();
			b.lastSeen = now;
			b.setAwareness(Brain.Awareness.ENGAGED, now);
		} else if (b.awareness == Brain.Awareness.ENGAGED && b.target != null && b.target.isAlive() && now - b.lastSeen < p.senses().memory()) {
			// Lost from sight: remembered. Searching walks to where it was last seen.
			if (now - b.lastSeen > 40) {
				b.setAwareness(Brain.Awareness.SEARCHING, now);
			}
		} else if (heard != null && b.awareness != Brain.Awareness.ENGAGED) {
			b.heardAt = heard;
			b.lastHeard = now;
			if (b.awareness == Brain.Awareness.UNAWARE) {
				b.setAwareness(Brain.Awareness.SUSPICIOUS, now);
				mob.playAmbientSound();
			} else if (b.awareness == Brain.Awareness.SUSPICIOUS && now - b.awarenessSince > 30) {
				b.lastKnown = heard;
				b.setAwareness(Brain.Awareness.SEARCHING, now);
			}
		} else if (b.awareness == Brain.Awareness.SEARCHING && now - Math.max(b.lastSeen, b.lastHeard) > p.senses().memory()) {
			b.setAwareness(Brain.Awareness.UNAWARE, now);
			forget(b);
			b.lastKnown = null;
		} else if (b.awareness == Brain.Awareness.SUSPICIOUS && now - b.lastHeard > 100) {
			b.setAwareness(Brain.Awareness.UNAWARE, now);
		} else if (b.awareness == Brain.Awareness.ENGAGED && (b.target == null || !b.target.isAlive())) {
			b.setAwareness(Brain.Awareness.UNAWARE, now);
			forget(b);
		}
		if (b.target != null && (!b.target.isAlive() || b.target.isRemoved())) {
			b.target = null;
		}
		watch(b, now);
	}

	/** Drops the target (and vanilla's, when it was the one Bestiary gave it). */
	static void forget(Brain b) {
		if (b.target != null && b.mob.getTarget() == b.target) {
			b.mob.setTarget(null);
		}
		b.target = null;
	}

	/** The battle cue, alerting its kind (unless it doesn't call: the undead), the codex's "seen". */
	static void onEngage(Brain b, LivingEntity target, ServerLevel level) {
		Mob mob = b.mob;
		mob.playAmbientSound();
		if (target instanceof ServerPlayer sp) {
			Codex.seen(sp, b.profile);
		}
		CreatureProfile.Senses s = b.profile.senses();
		if (!s.calls()) {
			return;
		}
		for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(s.alert()), o -> o != mob && o.getType() == mob.getType())) {
			Brain ob = Brain.get(other);
			if (ob != null && ob.awareness != Brain.Awareness.ENGAGED) {
				ob.target = target;
				ob.lastKnown = target.position();
				ob.lastSeen = level.getGameTime();
				ob.setAwareness(Brain.Awareness.ENGAGED, level.getGameTime());
			}
		}
	}

	/** Adaptation: what the creature sees its target doing, decaying over a few seconds. Never the player's inputs. */
	static void watch(Brain b, long now) {
		b.seenGuard *= 0.9F;
		b.seenKite *= 0.9F;
		b.seenDrink *= 0.9F;
		b.seenAttack *= 0.9F;
		LivingEntity t = b.target;
		if (t == null) {
			b.lastTargetDistance = -1;
			return;
		}
		double d = t.distanceTo(b.mob);
		if (b.lastTargetDistance >= 0 && d > b.lastTargetDistance + 0.3 && d > 3) {
			b.seenKite += 1;
		}
		b.lastTargetDistance = d;
		if (t.isUsingItem()) {
			if (t.getUseItem().has(net.minecraft.core.component.DataComponents.CONSUMABLE)) {
				b.seenDrink += 1;
			} else {
				b.seenGuard += 1;
			}
		}
		if (FrayApi.actions() != null) {
			var cur = FrayApi.actions().current(t);
			if (cur != null) {
				String path = cur.getPath();
				if (path.contains("parry")) {
					b.seenGuard += 1;
				} else if (!path.contains("dodge") && !path.contains("backstep")) {
					b.seenAttack += 1;
				}
			}
		}
	}

	public static @Nullable LivingEntity targetOf(Brain b) {
		return b.target;
	}
}
