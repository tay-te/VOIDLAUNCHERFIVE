package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.bestiary.api.MoveBehaviour;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.bestiary.elite.Affix;
import dev.voidmc.bestiary.danger.DangerMath;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.TellColour;
import dev.voidmc.fray.motion.MotionServer;
import dev.voidmc.fray.motion.Timeline;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The executor (BESTIARY.md §2.1): every tick it runs the chosen move (its clip on Fray Motion, the tell sent to the
 * target, the behaviour on the clip's ticks, the token held until the clip ends) or the chosen positioning (close,
 * circle the waiting ring, flank, retreat, kite, guard, strafe) through vanilla navigation, repathing at most every 10
 * ticks.
 */
public final class Executor {
	public static final int REPATH = 10;
	/** Moves started (tests and the perf report). */
	public static long movesStarted;

	private Executor() {
	}

	public static void tick(Brain b, ServerLevel level, long now) {
		long t0 = System.nanoTime();
		try {
			run(b, level, now);
		} finally {
			Brain.executorNanos += System.nanoTime() - t0;
			Brain.executorRuns++;
		}
	}

	private static void run(Brain b, ServerLevel level, long now) {
		Mob mob = b.mob;
		if (FrayApi.statuses().incapacitated(mob)) {
			if (b.move != null) {
				end(b, level, now, true);
			}
			mob.getNavigation().stop();
			setGuard(b, false);
			return;
		}
		if (b.move != null) {
			MotionServer.Running r = MotionServer.running(mob);
			if (r == null || !r.clip().equals(b.move.clip())) {
				end(b, level, now, r == null && now - b.moveStart < clipTicks(b.move) - 1 || r != null);
			} else {
				LivingEntity t = b.target;
				if (t != null) {
					MoveBehaviour beh = Behaviours.get(b.move.behaviour());
					beh.tick(context(b, level, t), (int) (now - b.moveStart));
				}
				mob.getNavigation().stop();
				return;
			}
		}
		LivingEntity t = b.target;
		if (b.awareness == Brain.Awareness.FLEEING && t != null) {
			setGuard(b, false);
			Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
			Vec3 goal = mob.position().add(away.lengthSqr() < 1e-4 ? new Vec3(8, 0, 0) : away.normalize().scale(12));
			path(b, goal, 1.3, now);
			return;
		}
		if (b.awareness != Brain.Awareness.ENGAGED || t == null) {
			setGuard(b, false);
			if (b.awareness == Brain.Awareness.SEARCHING && b.lastKnown != null) {
				path(b, b.lastKnown, 0.9, now);
				if (mob.distanceToSqr(b.lastKnown) < 2.5) {
					mob.getLookControl().setLookAt(b.lastKnown.x + Mth.sin(now * 0.1F) * 4, b.lastKnown.y + 1, b.lastKnown.z + Mth.cos(now * 0.1F) * 4);
				}
			} else if (b.awareness == Brain.Awareness.SUSPICIOUS && b.heardAt != null) {
				mob.getNavigation().stop();
				mob.getLookControl().setLookAt(b.heardAt.x, b.heardAt.y + 1, b.heardAt.z);
			}
			return;
		}
		mob.getLookControl().setLookAt(t, 30, 30);
		if (now >= b.nextThink) {
			b.nextThink = now + Tactics.PERIOD;
			Tactics.Choice c = Tactics.choose(b, t, level, now);
			if (c.move() != null) {
				if (start(b, level, c.move(), t, now)) {
					return;
				}
			} else if (c.position() != null) {
				b.position = c.position();
			}
		}
		position(b, level, t, now);
	}

	static int clipTicks(MoveDef m) {
		Timeline tl = MotionServer.timeline(m.clip());
		return tl == null ? 20 : tl.ticks;
	}

	static MoveBehaviour.Context context(Brain b, ServerLevel level, LivingEntity t) {
		return new MoveBehaviour.Context(level, b.mob, b, b.move, t, MotionServer.timeline(b.move.clip()), b.moveStart);
	}

	/** Starts a move: faces the target, takes the token, plays the clip, sends the tell, runs the behaviour. */
	public static boolean start(Brain b, ServerLevel level, MoveDef m, LivingEntity t, long now) {
		Mob mob = b.mob;
		Timeline tl = MotionServer.timeline(m.clip());
		if (tl == null || !Tokens.acquire(b, t, m.tokens(), now)) {
			return false;
		}
		float yaw = (float) Math.toDegrees(Math.atan2(t.getZ() - mob.getZ(), t.getX() - mob.getX())) - 90;
		mob.setYRot(yaw);
		mob.yBodyRot = yaw;
		mob.yHeadRot = yaw;
		mob.getNavigation().stop();
		setGuard(b, false);
		if (!MotionServer.play(mob, m.clip(), 1)) {
			Tokens.release(b);
			return false;
		}
		b.move = m;
		b.moveStart = now;
		b.moveState = null;
		b.position = null;
		movesStarted++;
		TellColour colour = TellColour.byId(tl.tellColour.isEmpty() ? "white" : tl.tellColour);
		int windup = Math.max(0, tl.activeStart - Math.max(0, tl.tellFrom));
		FrayApi.telegraphs().tell(mob, t, colour, windup, Component.literal(b.profile.codexName() + " " + verb(colour)));
		if (t instanceof ServerPlayer sp) {
			Codex.moveSeen(sp, b.profile, m);
		}
		// Cooldown from the end of the clip: danger shortens recovery (§6.2), Frenzied the gaps (+25 %, +50 % enraged).
		CreatureData d = CreatureData.of(mob);
		float gap = m.cooldown() * DangerMath.recovery(d.danger());
		if (d.has(Affix.FRENZIED.id)) {
			gap *= b.enraged ? 0.55F : 0.75F;
		}
		b.ready.put(m.id(), now + tl.ticks + (long) gap);
		if (m.id().equals(b.lastMove)) {
			b.repeats++;
		} else {
			b.repeats = 0;
			b.lastMove = m.id();
		}
		Behaviours.get(m.behaviour()).start(context(b, level, t));
		return true;
	}

	static String verb(TellColour c) {
		return switch (c) {
			case WHITE -> "strikes";
			case ORANGE -> "winds up a heavy blow";
			case RED -> "readies something unblockable";
			case PURPLE -> "reaches for you";
		};
	}

	static void end(Brain b, ServerLevel level, long now, boolean interrupted) {
		MoveDef m = b.move;
		LivingEntity t = b.target;
		if (m != null && t != null) {
			Behaviours.get(m.behaviour()).end(context(b, level, t), interrupted);
		}
		Tokens.release(b);
		b.move = null;
		b.moveState = null;
		// After a hit, skirmishers back off (hit and run); others take a breath before choosing again.
		b.nextThink = now + 4;
		if (b.role.equals("skirmisher") && !interrupted) {
			b.position = Brain.Position.RETREAT;
			b.positionUntil = now + 20;
		}
	}

	/** Holds the guard up (a shield in the off hand) or lowers it. */
	static void setGuard(Brain b, boolean up) {
		Mob mob = b.mob;
		boolean shield = mob.getOffhandItem().is(Items.SHIELD);
		if (up && shield) {
			if (!mob.isUsingItem()) {
				mob.startUsingItem(InteractionHand.OFF_HAND);
			}
			b.guarding = true;
		} else {
			if (b.guarding && mob.isUsingItem()) {
				mob.stopUsingItem();
			}
			b.guarding = false;
		}
	}

	static void position(Brain b, ServerLevel level, LivingEntity t, long now) {
		Mob mob = b.mob;
		Brain.Position pos = b.position == null ? Brain.Position.CLOSE : b.position;
		double dist = mob.distanceTo(t);
		float speed = 1.0F;
		if (b.profile.tactic("rush_from") > 0 && CreatureData.of(mob).danger() >= b.profile.tactic("rush_from")) {
			speed = 1.4F;
		}
		boolean guards = b.profile.guards() || CreatureData.of(mob).has(Affix.SHIELDED.id);
		switch (pos) {
			case CLOSE -> {
				setGuard(b, guards && dist < 6);
				if (dist > b.profile.keepMax()) {
					path(b, t.position(), speed, now);
				} else {
					mob.getNavigation().stop();
				}
			}
			case CIRCLE, GUARD -> {
				setGuard(b, guards);
				float r = (b.profile.ringMin() + b.profile.ringMax()) / 2;
				double a = Math.toRadians(b.ringSlot * 30 + now * (pos == Brain.Position.GUARD ? 0.6 : 1.5));
				Vec3 goal = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
				// Feint steps in now and then (never stand still in a line).
				if ((now + b.mob.getId() * 7) % 60 < 8) {
					goal = t.position().add(Math.cos(a) * b.profile.ringMin() * 0.8, 0, Math.sin(a) * b.profile.ringMin() * 0.8);
				}
				path(b, goal, 0.8, now);
			}
			case FLANK -> {
				setGuard(b, false);
				float back = (float) Math.toRadians(t.getYHeadRot() + 90 + 180);
				Vec3 behind = t.position().add(Math.cos(back) * 2.5, 0, Math.sin(back) * 2.5);
				path(b, behind, 1.1, now);
			}
			case RETREAT, KITE -> {
				setGuard(b, guards);
				Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
				double want = pos == Brain.Position.KITE ? (b.profile.keepMin() + b.profile.keepMax()) / 2 : b.profile.ringMax();
				if (dist < want) {
					Vec3 goal = t.position().add(away.lengthSqr() < 1e-4 ? new Vec3(want, 0, 0) : away.normalize().scale(want));
					path(b, goal, 1.1, now);
				} else {
					mob.getNavigation().stop();
				}
			}
			case STRAFE -> {
				setGuard(b, guards);
				Vec3 to = t.position().subtract(mob.position()).multiply(1, 0, 1);
				if (to.lengthSqr() > 1e-4) {
					Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(((now / 40) % 2 == 0 ? 1 : -1) * 2.5);
					path(b, mob.position().add(side), 0.8, now);
				}
			}
			case HOLD -> {
				setGuard(b, guards);
				mob.getNavigation().stop();
			}
		}
	}

	/** Vanilla navigation, repathing at most every {@link #REPATH} ticks (or when the goal moved a lot). */
	static void path(Brain b, Vec3 goal, double speed, long now) {
		Mob mob = b.mob;
		boolean moved = b.pathGoal == null || b.pathGoal.distanceToSqr(goal) > 4;
		if (now - b.lastPath < REPATH && !(moved && mob.getNavigation().isDone())) {
			return;
		}
		if (now - b.lastPath < REPATH && !mob.getNavigation().isDone()) {
			return;
		}
		b.lastPath = now;
		b.pathGoal = goal;
		mob.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
	}
}
