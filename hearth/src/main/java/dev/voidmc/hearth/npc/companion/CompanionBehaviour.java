package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A companion at the player's side (docs/LIVING_WORLD_2.md §8.3; priority 280, the escort's band, held while hired): two
 * to four blocks behind (six to nine when told to keep their distance), standing where told to wait, catching up out of
 * sight when left more than {@code catch_up} blocks behind, fighting the monsters about and the player's own target
 * (never townsfolk, never another player) unless told to hold back, sitting by the player while they sleep, and passing a
 * remark now and then. The heavy looks (a scan for monsters) run every ten ticks; the rest is a few distance checks.
 */
public final class CompanionBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("npc/companion");
	public static final int PRIORITY = 280;
	private static final int SCAN_TICKS = 10;
	private static final double FIGHT_RANGE = 12.0;
	/** Server time spent in companions' ticks and the ticks counted, for the sim stats and the tour (§9.1: ~5 µs each). */
	public static final java.util.concurrent.atomic.LongAdder NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder TICKS = new java.util.concurrent.atomic.LongAdder();
	/** Where each companion last set off for (the player's place then): no new path while the player stays near it. */
	private static final java.util.Map<java.util.UUID, BlockPos> GOALS = new java.util.HashMap<>();
	private static final dev.voidmc.hearth.sim.LivingWorldStats.Timer FOLK_TIMER = dev.voidmc.hearth.sim.LivingWorldStats.timer("companion folk");

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		return CompanionState.hired(folk.resident());
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		if (folk.entity() instanceof Folk f && f.isSleeping()) {
			f.stopSleeping();
		}
		CompanionState st = CompanionState.of(folk.resident());
		if (st != null && folk.entity().level() instanceof ServerLevel level) {
			CompanionGear.dress(folk.entity(), st, level.registryAccess());
		}
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		long t0 = System.nanoTime();
		try {
			return step(folk);
		} finally {
			long dt = System.nanoTime() - t0;
			NANOS.add(dt);
			TICKS.increment();
			FOLK_TIMER.add(dt);
		}
	}

	/** The average cost of a companion's tick so far, in microseconds. */
	public static double averageMicros() {
		long n = TICKS.sum();
		return n == 0 ? 0 : NANOS.sum() / 1e3 / n;
	}

	private boolean step(NpcService.FolkHandle folk) {
		CompanionState st = CompanionState.of(folk.resident());
		if (st == null || !(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		Mob me = folk.entity();
		long now = level.getGameTime();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(st.player());
		if (player == null || player.level() != level || !player.isAlive() || player.isSpectator()) {
			// the player is gone from this world: they wait where they stand
			folk.stop();
			return true;
		}
		PlaceConfig.Companions cfg = Companions.config().companions();
		boolean scan = (me.tickCount + me.getId()) % SCAN_TICKS == 0;
		if (scan && me.tickCount % 200 == 0) {
			CompanionGear.dress(me, st, level.registryAccess());
		}
		// ---- fighting
		LivingEntity target = me.getTarget();
		if (target != null && (!target.isAlive() || me.distanceToSqr(target) > 24 * 24 || !st.fight())) {
			me.setTarget(null);
			target = null;
		}
		if (target == null && scan && st.fight()) {
			target = chooseTarget(level, me, player);
			if (target != null) {
				me.setTarget(target);
				CompanionBarks.say(folk, "companion_fight", player);
			}
		}
		if (target != null) {
			fight(folk, me, target, level);
			return true;
		}
		// ---- keeping to the player
		double d2 = me.distanceToSqr(player);
		if (scan && d2 > cfg.catchUp() * cfg.catchUp() && st.follow() != CompanionState.Follow.WAIT && !player.hasLineOfSight(me)) {
			BlockPos spot = FolkSpots.standableNear(level, player.blockPosition().relative(player.getDirection().getOpposite(), 2), 3);
			if (spot != null && level.isPositionEntityTicking(spot)) {
				me.getNavigation().stop();
				me.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				return true;
			}
		}
		switch (st.follow()) {
			case WAIT -> {
				BlockPos at = st.waitAt();
				if (at != null && me.blockPosition().distSqr(at) > 9) {
					if (scan) {
						folk.moveTo(at, 0.6);
					}
				} else {
					halt(folk, me);
					if (d2 < 16 * 16) {
						folk.lookAt(player);
					}
				}
			}
			case FOLLOW, DISTANCE -> {
				double near = st.follow() == CompanionState.Follow.DISTANCE ? 6 : 2.5;
				double far = st.follow() == CompanionState.Follow.DISTANCE ? 9 : 4;
				if (d2 > far * far) {
					// a path at most every ten ticks (the pathfinder is the costly part of following)
					// a new path only when the player has gone on from the last goal, or the old path ran out
					BlockPos goal = GOALS.get(folk.resident().id);
					boolean slow = (me.tickCount + me.getId()) % 20 == 0;
					if (scan && (goal == null || me.getNavigation().isDone()) || slow && goal.distSqr(player.blockPosition()) > 16) {
						GOALS.put(folk.resident().id, player.blockPosition());
						follow(me, player, d2 > 12 * 12 ? 0.9 : 0.65);
					}
				} else if (d2 < near * near * 0.5 && st.follow() == CompanionState.Follow.DISTANCE) {
					halt(folk, me);
				} else if (d2 <= far * far) {
					halt(folk, me);
					if (scan) {
						folk.lookAt(player);
					}
				}
				if (player.isSleeping() && d2 < 8 * 8) {
					// they sleep when the player sleeps: down by the bed
					folk.pose("sit", 60);
				}
			}
		}
		if (scan) {
			remark(folk, st, player, level, now);
		}
		return true;
	}

	/** A remark now and then (every two minutes at most): the road, a strange town, home, the night, their wounds. */
	private static void remark(NpcService.FolkHandle folk, CompanionState st, ServerPlayer player, ServerLevel level, long now) {
		if (now - st.lastRemark() < 20 * 120 || folk.entity().getRandom().nextInt(30) != 0) {
			return;
		}
		Mob me = folk.entity();
		String category;
		if (me.getHealth() < me.getMaxHealth() * 0.5F) {
			category = "companion_hurt";
		} else {
			HearthWorld w = HearthWorld.of(level);
			Settlement here = w == null ? null : w.settlementAt(me.blockPosition());
			if (here == null) {
				category = level.isDarkOutside() ? "companion_night" : "companion_road";
			} else if (here.id() == folk.settlement().id()) {
				category = "companion_home";
			} else {
				category = "companion_place";
			}
		}
		if (CompanionBarks.say(folk, category, player)) {
			st.setLastRemark(now);
			folk.settlement().markDirty();
		}
	}

	/**
	 * What to fight: whatever the player struck last (not townsfolk, villagers, animals or players), whatever is hurting
	 * the player or this companion, or a monster closing on either.
	 */
	static @Nullable LivingEntity chooseTarget(ServerLevel level, Mob me, ServerPlayer player) {
		LivingEntity struck = player.getLastHurtMob();
		if (struck != null && player.tickCount - player.getLastHurtMobTimestamp() < 100 && fair(struck) && me.distanceToSqr(struck) < FIGHT_RANGE * FIGHT_RANGE * 2) {
			return struck;
		}
		LivingEntity by = player.getLastHurtByMob();
		if (by != null && by.isAlive() && fair(by) && me.distanceToSqr(by) < FIGHT_RANGE * FIGHT_RANGE * 2) {
			return by;
		}
		LivingEntity mine = me.getLastHurtByMob();
		if (mine != null && mine.isAlive() && fair(mine)) {
			return mine;
		}
		AABB box = me.getBoundingBox().inflate(FIGHT_RANGE);
		// by class: the entity sections index monsters by class, so a crowded town costs nothing here
		List<Monster> near = level.getEntitiesOfClass(Monster.class, box, m -> m.isAlive() && (m.getTarget() == player || m.getTarget() == me
			|| m.distanceToSqr(player) < 6 * 6));
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : near) {
			double d = m.distanceToSqr(me);
			if (d < bestD) {
				best = m;
				bestD = d;
			}
		}
		return best;
	}

	/** A fair target: not a player, not townsfolk or villagers, not tame or farm animals. Bandits (folk on no town's books) are. */
	static boolean fair(LivingEntity e) {
		if (!e.isAlive() || e instanceof ServerPlayer || e instanceof AbstractVillager || e instanceof Animal) {
			return false;
		}
		if (e instanceof Folk f) {
			return f.brain() == null && f.controller() != null;
		}
		return true;
	}

	private static void fight(NpcService.FolkHandle folk, Mob me, LivingEntity target, ServerLevel level) {
		me.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double reach = me.getBbWidth() * 2.0F * me.getBbWidth() * 2.0F + target.getBbWidth() + 1.5;
		if (me.distanceToSqr(target) <= reach) {
			me.getNavigation().stop();
			if (me.tickCount % 20 == 0) {
				me.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				me.doHurtTarget(level, target);
			}
		} else if ((me.tickCount + me.getId()) % 20 == 0 || me.getNavigation().isDone() && me.tickCount % 5 == 0) {
			me.getNavigation().moveTo(target, 0.9);
		}
	}

	/**
	 * A path toward the player, its search bounded by the distance (the costly part of following): a straight vanilla
	 * path to the player's feet, without the routine's search for a door's approaches.
	 */
	private static void follow(Mob me, ServerPlayer player, double speed) {
		var nav = me.getNavigation();
		nav.setRequiredPathLength((float) Math.min(48.0, Math.sqrt(me.distanceToSqr(player)) * 1.5 + 8.0));
		nav.moveTo(player.getX(), player.getY(), player.getZ(), speed);
	}

	/** Stops walking, without asking the navigation again when it is idle already. */
	private static void halt(NpcService.FolkHandle folk, Mob me) {
		if (!me.getNavigation().isDone()) {
			folk.stop();
		}
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		GOALS.remove(folk.resident().id);
		folk.stop();
		folk.entity().setTarget(null);
	}
}
