package dev.voidmc.mettle.character.skill.stealth;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.StealthSkill;
import dev.voidmc.mettle.character.skill.athletics.AthleticsRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A player's sneaking, tracked on the server each tick: how long they have sneaked (Vanish), stood still sneaking
 * (Patient), when they last hit something (Ghost's "not attacking"); the XP for sneaking near unaware hostiles and past a
 * Warden; Vanish; Nightstalker's speed in the dark; and Under Its Nose. Cheap: the scans run once a second.
 */
public final class Sneaking {
	private static final Map<UUID, State> STATES = new HashMap<>();
	static final Identifier NIGHTSTALKER = Mettle.id("stealth/nightstalker");

	static final class State {
		long sneakSince = -1;
		long stillSince = -1;
		Vec3 stillAt = Vec3.ZERO;
		long lastAttack = Long.MIN_VALUE / 2;
		long vanishReady;
		long shadowstepReady;
		int nearTicks;
		boolean patientSpent;
		final AthleticsRules.Meter near = new AthleticsRules.Meter(StealthRules.NEAR_CAP_PER_MINUTE);
		final AthleticsRules.Meter warden = new AthleticsRules.Meter(StealthRules.WARDEN_CAP_PER_MINUTE);
		/** Under Its Nose: a Warden this player came within 8 blocks of without it noticing them. */
		UUID passing;
	}

	private Sneaking() {
	}

	static State state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new State());
	}

	public static void forget(UUID player) {
		STATES.remove(player);
	}

	public static void clear() {
		STATES.clear();
	}

	/** Sneaking now (crouched, not flying or a spectator). */
	public static boolean sneaking(Player player) {
		return player.isShiftKeyDown() && !player.isSpectator() && !player.getAbilities().flying;
	}

	/** Light 7 or less where the player stands (the sky's darkening included). */
	public static boolean dark(Player player) {
		return light(player) <= StealthRules.DARK;
	}

	public static int light(Player player) {
		return player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition()));
	}

	/** A hit within the last 3 s. */
	public static boolean attacking(Player player) {
		return player.level().getGameTime() - state(player).lastAttack < StealthRules.ATTACK_GRACE;
	}

	/** Ticks of unbroken sneaking (0 when not sneaking). */
	public static long sneakTicks(Player player) {
		State s = state(player);
		return s.sneakSince < 0 ? 0 : player.level().getGameTime() - s.sneakSince;
	}

	/** Patient: sneaking still for 2 s, and the readied hit not yet spent. */
	public static boolean patientReady(Player player) {
		State s = state(player);
		return !s.patientSpent && s.stillSince >= 0 && player.level().getGameTime() - s.stillSince >= StealthRules.PATIENT_TICKS;
	}

	static void attacked(Player player) {
		State s = state(player);
		s.lastAttack = player.level().getGameTime();
		if (patientReady(player)) {
			s.patientSpent = true;
		}
	}

	/** Footpad's cover: climbing or in water, with the synergy. */
	public static boolean footpadCover(Player player) {
		return (player.onClimbable() || player.isInWater()) && Perks.has(player, StealthSkill.FOOTPAD);
	}

	public static void tick(ServerPlayer player, int tick) {
		State s = state(player);
		long now = player.level().getGameTime();
		boolean sneaking = sneaking(player);
		if (sneaking) {
			if (s.sneakSince < 0) {
				s.sneakSince = now;
			}
			if (s.stillSince < 0 || player.position().distanceToSqr(s.stillAt) > 0.01) {
				s.stillSince = now;
				s.stillAt = player.position();
				s.patientSpent = false;
			}
		} else {
			s.sneakSince = -1;
			s.stillSince = -1;
			s.nearTicks = 0;
		}
		if (sneaking && Perks.has(player, StealthSkill.VANISH) && now >= s.vanishReady && now - s.sneakSince >= StealthRules.VANISH_SNEAK_TICKS
			&& (tick + player.getId()) % 5 == 0) {
			vanish(player, s, now);
		}
		if ((tick + player.getId()) % 10 == 0) {
			nightstalker(player, sneaking);
		}
		if ((tick + player.getId()) % 20 != 0 || player.isCreative() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		if (sneaking) {
			// One XP every 4 s near a hostile that hasn't seen you (at most 15 a minute).
			if (unawareNear(player, level)) {
				s.nearTicks += 20;
				if (s.nearTicks >= StealthRules.NEAR_TICKS) {
					s.nearTicks -= StealthRules.NEAR_TICKS;
					double paid = s.near.take(StealthRules.NEAR_XP, now);
					StealthSkill.award(player, "near", paid);
				}
			}
		}
		warden(player, level, s, sneaking, now);
	}

	/** A hostile within 12 blocks that isn't targeting the player, can reach them, isn't caged, leashed or from a spawner. */
	private static boolean unawareNear(ServerPlayer player, ServerLevel level) {
		AABB box = player.getBoundingBox().inflate(StealthRules.NEAR_RANGE);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive()
			&& m.distanceToSqr(player) <= StealthRules.NEAR_RANGE * StealthRules.NEAR_RANGE);
		mobs.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		int tried = 0;
		for (Mob m : mobs) {
			boolean hostile = true;
			boolean targeting = m.getTarget() == player;
			boolean spawner = m.entityTags().contains(Combat.SPAWNER_TAG);
			if (!StealthRules.teaches(hostile, targeting, spawner, m.isLeashed(), m.isPassenger(), true)) {
				continue;
			}
			// Reachable: a path to the player (the costly part, at most three a second).
			if (tried++ >= 3) {
				break;
			}
			var path = m.getNavigation().createPath(player, 1);
			if (path != null && path.canReach()) {
				return true;
			}
		}
		return false;
	}

	/** Vanish: every hostile hunting the player that can't see them now loses them at once. */
	private static void vanish(ServerPlayer player, State s, long now) {
		List<Mob> hunting = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(StealthRules.VANISH_RANGE),
			m -> m.getTarget() == player && !m.hasLineOfSight(player));
		if (hunting.isEmpty()) {
			return;
		}
		for (Mob m : hunting) {
			lose(m, player);
		}
		s.vanishReady = now + Math.round(StealthRules.VANISH_COOLDOWN * CardHooks.cooldownScale(player));
		StealthSkill.chip(player, StealthSkill.VANISH, "vanish", hunting.size());
	}

	/** A mob forgets the player: its target, and its brain's attack target where it has one. */
	static void lose(Mob mob, Player player) {
		if (mob.getTarget() == player) {
			mob.setTarget(null);
		}
		if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
			&& mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).map(t -> t == player).orElse(false)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
		if (mob instanceof Warden warden) {
			warden.getAngerManagement().clearAnger(player);
		}
	}

	/** Nightstalker: sneaking faster in the dark, a modifier kept in step every half second. */
	private static void nightstalker(ServerPlayer player, boolean sneaking) {
		double pct = StealthHooks.pct(player, "stealth/nightstalker");
		double amount = pct > 0 && dark(player) ? pct : 0;
		AttributeInstance speed = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (speed == null) {
			return;
		}
		AttributeModifier current = speed.getModifier(NIGHTSTALKER);
		if (amount <= 0) {
			if (current != null) {
				speed.removeModifier(NIGHTSTALKER);
			}
			return;
		}
		if (current != null && current.amount() == amount) {
			return;
		}
		speed.removeModifier(NIGHTSTALKER);
		speed.addTransientModifier(new AttributeModifier(NIGHTSTALKER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	/** Near a Warden: XP while sneaking past it unnoticed, and Under Its Nose once the player is through. */
	private static void warden(ServerPlayer player, ServerLevel level, State s, boolean sneaking, long now) {
		List<Warden> wardens = level.getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(StealthRules.WARDEN_RANGE),
			w -> w.isAlive() && w.distanceToSqr(player) <= StealthRules.WARDEN_RANGE * StealthRules.WARDEN_RANGE);
		Warden passing = null;
		for (Warden w : wardens) {
			boolean unnoticed = w.getAngerManagement().getActiveAnger(player) == 0 && w.getTarget() != player;
			if (s.passing != null && s.passing.equals(w.getUUID())) {
				passing = w;
				if (!unnoticed) {
					// It noticed: the pass doesn't count.
					s.passing = null;
				}
			}
			if (!unnoticed) {
				continue;
			}
			if (sneaking) {
				double paid = s.warden.take(StealthRules.WARDEN_XP_PER_SECOND, now);
				StealthSkill.award(player, "warden", paid);
			}
			if (s.passing == null && w.distanceToSqr(player) <= StealthRules.WARDEN_PASS * StealthRules.WARDEN_PASS) {
				s.passing = w.getUUID();
				passing = w;
			}
		}
		if (s.passing != null && passing == null) {
			// Through: the Warden it passed is now beyond 16 blocks (or gone) and never noticed them.
			s.passing = null;
			FeatCounters.add(player, StealthSkill.WARDEN_PASS_COUNTER, 1);
		}
	}

	static long shadowstepReady(Player player) {
		return state(player).shadowstepReady;
	}

	static void shadowstepUsed(Player player, long until) {
		state(player).shadowstepReady = until;
	}
}
