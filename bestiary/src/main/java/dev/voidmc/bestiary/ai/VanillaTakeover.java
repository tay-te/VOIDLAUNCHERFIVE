package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.CreatureProfiles;
import dev.voidmc.bestiary.danger.Danger;
import dev.voidmc.bestiary.elite.Elites;
import dev.voidmc.bestiary.mixin.MobAccess;
import dev.voidmc.bestiary.mixin.TargetGoalAccess;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * How Bestiary takes over a creature (BESTIARY.md §2.2), on {@code ENTITY_LOAD}: only exact entity types with a
 * creature profile (mobs from other mods, and vanilla's own variants such as husks and strays, are untouched until a
 * profile names them). Their melee, ranged, leap and swell goals go, and the target goal that hunts players makes way
 * for Bestiary's perception; one {@link BestiaryCombatGoal} goes in at the priority of the attack goals it replaced.
 * Every other goal (floating, fleeing the sun, avoiding cats, breaking doors, the villager and golem targets, spear
 * use) stays. Danger is computed once and saved; elites are rolled once, for creatures spawned with Bestiary present.
 * NoAI mobs (props, dummies) are left alone.
 */
public final class VanillaTakeover {
	public static long takenOver;

	private VanillaTakeover() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register(VanillaTakeover::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob m && Brain.get(m) != null) {
				Tokens.release(Brain.get(m));
				Squads.leave(Brain.get(m));
				Grabs.releaseBy(m);
			}
		});
	}

	static void onLoad(Entity entity, ServerLevel level) {
		// NoAI mobs are props (map makers' statues, showcase dummies): Bestiary leaves them exactly as vanilla has them.
		if (!(entity instanceof Mob mob) || Brain.get(mob) != null || mob.isNoAi()) {
			return;
		}
		CreatureProfile p = CreatureProfiles.forType(mob.getType());
		if (p == null) {
			return;
		}
		attach(mob, p, level);
	}

	/** Gives a mob the Bestiary loop with this profile (also {@code BestiaryApi.attach}, for Hearth folk and bosses). */
	public static Brain attach(Mob mob, CreatureProfile p, ServerLevel level) {
		Brain b = new Brain(mob, p);
		mob.setAttached(Brain.TYPE, b);
		GoalSelector goals = ((MobAccess) mob).bestiary$goals();
		GoalSelector targets = ((MobAccess) mob).bestiary$targets();
		int prio = Integer.MAX_VALUE;
		List<Goal> drop = new ArrayList<>();
		for (WrappedGoal w : goals.getAvailableGoals()) {
			Goal g = w.getGoal();
			if (g instanceof MeleeAttackGoal || g instanceof RangedBowAttackGoal<?> || g instanceof RangedAttackGoal || g instanceof RangedCrossbowAttackGoal<?>
				|| g instanceof LeapAtTargetGoal || g instanceof SwellGoal) {
				prio = Math.min(prio, w.getPriority());
				drop.add(g);
			}
		}
		drop.forEach(goals::removeGoal);
		List<Goal> dropTargets = new ArrayList<>();
		for (WrappedGoal w : targets.getAvailableGoals()) {
			if (w.getGoal() instanceof NearestAttackableTargetGoal<?> n && Player.class.isAssignableFrom(((TargetGoalAccess) n).bestiary$targetType())) {
				dropTargets.add(n);
			}
		}
		dropTargets.forEach(targets::removeGoal);
		goals.addGoal(prio == Integer.MAX_VALUE ? 2 : prio, new BestiaryCombatGoal(b));
		b.takenOver = true;
		takenOver++;

		CreatureData d = CreatureData.get(mob);
		boolean fresh = false;
		if (d == null) {
			// Older than Bestiary (or spawned without finalizeSpawn): danger from where it stands, never elite.
			d = CreatureData.FRESH;
		}
		if (d.danger() < 1) {
			d = d.withDanger(Danger.at(level, mob.blockPosition()));
			fresh = !d.reason().equals("legacy");
		}
		if (!d.rolled()) {
			d = Elites.roll(mob, p, d, level);
		}
		CreatureData.set(mob, d);
		Scaling.apply(mob, p, d, fresh);
		b.role = Squads.role(mob, p, d);
		Elites.present(mob, p, d, level);
		if (d.elite() && d.packSize() > 0 && !d.packSpawned()) {
			CreatureData leaderData = d;
			level.getServer().execute(() -> spawnPack(mob, p, leaderData, level));
		}
		return b;
	}

	/** A champion pack (D3+): 3-5 commons of the leader's kind around it, sharing one of its affixes. */
	static void spawnPack(Mob leader, CreatureProfile p, CreatureData d, ServerLevel level) {
		if (!leader.isAlive()) {
			return;
		}
		CreatureData.set(leader, d.markPackSpawned());
		List<String> shared = d.affixes().isEmpty() ? List.of() : List.of(d.affixes().get(leader.getRandom().nextInt(d.affixes().size())));
		for (int i = 0; i < d.packSize(); i++) {
			Entity e = leader.getType().create(level, EntitySpawnReason.REINFORCEMENT);
			if (!(e instanceof Mob m)) {
				continue;
			}
			double a = i * (Math.PI * 2 / d.packSize());
			m.snapTo(leader.getX() + Math.cos(a) * 2, leader.getY(), leader.getZ() + Math.sin(a) * 2, leader.getYRot(), 0);
			CreatureData.set(m, CreatureData.FRESH.withReason("pack").withDanger(d.danger()).withLeader(leader.getUUID(), shared));
			if (m instanceof PathfinderMob) {
				m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), EntitySpawnReason.REINFORCEMENT, null);
			}
			level.addFreshEntity(m);
		}
		Bestiary.LOGGER.debug("champion pack of {} around {}", d.packSize(), leader.getName().getString());
	}
}
