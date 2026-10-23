package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.ai.math.Curves;
import dev.voidmc.bestiary.ai.math.Utility;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.bestiary.api.MoveBehaviour;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.motion.MotionServer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tactics (BESTIARY.md §2.1), every 5 ticks while engaged: every move that may start (cooldown, danger, range, token,
 * its behaviour's conditions) and every positioning the profile weighs is scored, weight × Π considerations ×
 * repetition × adaptation × noise, and the best (or one of the top two within 10 %) is chosen. The situation is only
 * what the creature can see: distances, where it stands relative to the target's facing, whether the target is guarding,
 * attacking, drinking, drawing a bow, Staggered or Winded, its own health, the token, its allies.
 */
public final class Tactics {
	public static final int PERIOD = 5;

	/** The inputs considerations read (names in profiles). */
	public static final class Situation {
		public float distance, height, behind, facing, guarding, attacking, staggered, winded, drawing, drinking, heavy, health, token, others, los, cornered,
			danger, enraged, kite, seenGuard, seenAttack, random;

		public float get(String input) {
			return switch (input) {
				case "distance" -> this.distance;
				case "height" -> this.height;
				case "behind" -> this.behind;
				case "facing" -> this.facing;
				case "target_guarding" -> this.guarding;
				case "target_attacking" -> this.attacking;
				case "target_staggered" -> this.staggered;
				case "target_winded" -> this.winded;
				case "target_drawing" -> this.drawing;
				case "target_drinking" -> this.drinking;
				case "target_heavy" -> this.heavy;
				case "health" -> this.health;
				case "token" -> this.token;
				case "others" -> this.others;
				case "los" -> this.los;
				case "cornered" -> this.cornered;
				case "danger" -> this.danger;
				case "enraged" -> this.enraged;
				case "kiting" -> this.kite;
				case "seen_guard" -> this.seenGuard;
				case "seen_attack" -> this.seenAttack;
				default -> this.random;
			};
		}
	}

	/** A scored option: a move or a positioning. */
	public record Choice(@Nullable MoveDef move, Brain.@Nullable Position position, float score) {
	}

	/** The last choices made (studio and test log). */
	public static final List<String> LOG = new java.util.concurrent.CopyOnWriteArrayList<>();
	public static boolean log;

	private Tactics() {
	}

	public static Situation situation(Brain b, LivingEntity t, ServerLevel level, long now) {
		Mob mob = b.mob;
		Situation s = new Situation();
		s.distance = mob.distanceTo(t);
		s.height = (float) (t.getY() - mob.getY());
		Vec3 d = mob.position().subtract(t.position());
		float to = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
		s.behind = Math.abs(Mth.wrapDegrees(to - t.getYHeadRot())) / 180F;
		Vec3 d2 = t.position().subtract(mob.position());
		float to2 = (float) (Mth.atan2(d2.z, d2.x) * Mth.RAD_TO_DEG) - 90F;
		s.facing = Math.abs(Mth.wrapDegrees(to2 - mob.yBodyRot)) / 180F;
		Identifier cur = FrayApi.actions() == null ? null : FrayApi.actions().current(t);
		String path = cur == null ? "" : cur.getPath();
		boolean using = t.isUsingItem();
		boolean consumable = using && t.getUseItem().has(net.minecraft.core.component.DataComponents.CONSUMABLE);
		s.guarding = path.contains("parry") || using && !consumable && !t.getUseItem().is(Items.BOW) && !t.getUseItem().is(Items.CROSSBOW) ? 1 : 0;
		s.attacking = !path.isEmpty() && !path.contains("parry") && !path.contains("dodge") && !path.contains("backstep") || MotionServer.running(t) != null && !(t instanceof ServerPlayer) ? 1 : 0;
		s.heavy = path.endsWith("heavy") || path.endsWith("charged") ? 1 : 0;
		s.staggered = FrayApi.statuses() != null && FrayApi.statuses().has(t, StatusType.STAGGER) ? 1 : 0;
		s.winded = t instanceof ServerPlayer sp && FrayApi.resources() != null && FrayApi.resources().winded(sp) ? 1 : 0;
		s.drawing = using && (t.getUseItem().is(Items.BOW) || t.getUseItem().is(Items.CROSSBOW)) ? 1 : 0;
		s.drinking = consumable ? 1 : 0;
		s.health = mob.getHealth() / Math.max(1, mob.getMaxHealth());
		s.token = Tokens.available(b, t, MoveDef.Tokens.MELEE, now) ? 1 : 0;
		s.others = Math.max(0, Tokens.holders(t, MoveDef.Tokens.MELEE, now) - (b.tokenTarget == t.getId() ? 1 : 0));
		s.los = mob.getSensing().hasLineOfSight(t) ? 1 : 0;
		s.cornered = s.distance < 2.6F && cornered(mob, t, level) ? 1 : 0;
		s.danger = CreatureData.of(mob).danger();
		s.enraged = b.enraged ? 1 : 0;
		s.kite = Math.min(1, b.seenKite / 5F);
		s.seenGuard = Math.min(1, b.seenGuard / 5F);
		s.seenAttack = Math.min(1, b.seenAttack / 5F);
		s.random = mob.getRandom().nextFloat();
		return s;
	}

	/** Whether the block behind the creature (away from its target) is solid: it can't back off. */
	static boolean cornered(Mob mob, LivingEntity t, ServerLevel level) {
		Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
		if (away.lengthSqr() < 1e-4) {
			return false;
		}
		Vec3 p = mob.position().add(away.normalize().scale(1.2));
		BlockPos at = BlockPos.containing(p.x, mob.getY() + 0.5, p.z);
		return !level.getBlockState(at).getCollisionShape(level, at).isEmpty();
	}

	/** Scores every option and returns the choice (never null: holding position is always possible). */
	public static Choice choose(Brain b, LivingEntity t, ServerLevel level, long now) {
		long t0 = System.nanoTime();
		try {
			return run(b, t, level, now);
		} finally {
			Brain.tacticsNanos += System.nanoTime() - t0;
			Brain.tacticsRuns++;
		}
	}

	private static Choice run(Brain b, LivingEntity t, ServerLevel level, long now) {
		CreatureProfile p = b.profile;
		Situation s = situation(b, t, level, now);
		List<Choice> opts = new ArrayList<>(p.moves().size() + 7);
		float[] vals = new float[16];
		CreatureData data = CreatureData.of(b.mob);
		if (b.forced != null) {
			MoveDef f = p.move(b.forced);
			b.forced = null;
			if (f != null && MotionServer.timeline(f.clip()) != null) {
				return new Choice(f, null, 1);
			}
		}
		for (MoveDef m : p.moves()) {
			if (!b.ready(m.id(), now) || m.minDanger() > data.danger() + 1e-3F || MotionServer.timeline(m.clip()) == null) {
				continue;
			}
			if (s.distance < m.minRange() - 0.25F || s.distance > m.maxRange() + 0.25F) {
				continue;
			}
			if (m.tokens() != MoveDef.Tokens.NONE && !Tokens.available(b, t, m.tokens(), now)) {
				continue;
			}
			MoveBehaviour beh = Behaviours.get(m.behaviour());
			if (!beh.canStart(level, b.mob, b, m, t)) {
				continue;
			}
			int n = 0;
			vals[n++] = Curves.eval(Curves.Curve.BAND, s.distance, m.minRange(), m.maxRange());
			for (MoveDef.Consideration c : m.considerations()) {
				if (n < vals.length) {
					vals[n++] = c.eval(s.get(c.input()));
				}
			}
			float w = m.weight();
			if (m.tagged("guard_break")) {
				w *= 1 + s.seenGuard + s.guarding;
			}
			if (m.tagged("closer")) {
				w *= 1 + 2 * s.kite;
			}
			if (m.tagged("punish")) {
				w *= 1 + 3 * (s.drinking + s.staggered + s.winded);
			}
			if (m.tagged("ranged_support")) {
				w *= 1 + s.kite;
			}
			if (m.id().equals(b.lastMove)) {
				w *= Utility.repetition(b.repeats);
			}
			opts.add(new Choice(m, null, Utility.score(w, vals, n, b.mob.getRandom().nextFloat())));
		}
		positions(b, s, opts, vals);
		float[] scores = new float[opts.size()];
		for (int i = 0; i < scores.length; i++) {
			scores[i] = opts.get(i).score();
		}
		int pick = Utility.pick(scores, scores.length, b.mob.getRandom().nextFloat());
		Choice c = pick < 0 ? new Choice(null, Brain.Position.HOLD, 0) : opts.get(pick);
		if (log) {
			StringBuilder sb = new StringBuilder();
			sb.append(b.mob.getId()).append(' ').append(b.profile.id().getPath()).append(" d=").append(String.format(java.util.Locale.ROOT, "%.1f", s.distance))
				.append(" -> ").append(c.move() != null ? c.move().id() : String.valueOf(c.position()));
			LOG.add(sb.toString());
		}
		return c;
	}

	private static void positions(Brain b, Situation s, List<Choice> opts, float[] vals) {
		CreatureProfile p = b.profile;
		float roleFlank = b.role.equals("flanker") ? 2 : 1;
		float roleRun = b.role.equals("skirmisher") ? 2 : 1;
		for (Brain.Position pos : Brain.Position.values()) {
			float w = p.tactic(pos.name().toLowerCase(java.util.Locale.ROOT));
			if (w <= 0 && pos != Brain.Position.HOLD) {
				continue;
			}
			int n = 0;
			switch (pos) {
				case CLOSE -> {
					vals[n++] = s.token > 0 ? 1 : 0.15F;
					vals[n++] = Curves.eval(Curves.Curve.LINEAR, s.distance, p.keepMax(), p.keepMax() + 3);
				}
				case CIRCLE -> {
					vals[n++] = s.token > 0 ? 0.25F : 1;
					vals[n++] = Curves.eval(Curves.Curve.INVERSE, s.distance, p.ringMax() + 2, p.ringMax() + 10);
				}
				case FLANK -> {
					w *= roleFlank;
					vals[n++] = s.others > 0 ? 1 : 0.2F;
					vals[n++] = 1 - s.behind * 0.8F;
				}
				case RETREAT -> {
					w *= roleRun;
					vals[n++] = Curves.eval(Curves.Curve.INVERSE, s.distance, p.keepMin(), p.keepMin() + 4);
					vals[n++] = Math.max(s.attacking, 0.3F);
				}
				case KITE -> {
					vals[n++] = Curves.eval(Curves.Curve.INVERSE, s.distance, p.keepMin() - 1, p.keepMin() + 2);
				}
				case GUARD -> {
					vals[n++] = Math.max(s.attacking, 0.5F);
					vals[n++] = s.token > 0 ? 0.6F : 1;
					vals[n++] = Curves.eval(Curves.Curve.INVERSE, s.distance, p.ringMax(), p.ringMax() + 6);
				}
				case STRAFE -> {
					vals[n++] = 0.6F;
					vals[n++] = Curves.eval(Curves.Curve.BAND, s.distance, p.keepMin(), p.keepMax() + 4);
				}
				case HOLD -> {
					w = Math.max(w, 0.05F);
					vals[n++] = 1;
				}
			}
			if (b.position == pos) {
				// A little stickiness: positionings run for a while rather than jitter every think.
				w *= 1.2F;
			}
			opts.add(new Choice(null, pos, Utility.score(w, vals, n, b.mob.getRandom().nextFloat())));
		}
	}
}
