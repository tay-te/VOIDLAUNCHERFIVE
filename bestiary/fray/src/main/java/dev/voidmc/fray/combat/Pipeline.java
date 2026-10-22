package dev.voidmc.fray.combat;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.BalanceKey;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.DamageBreakdown;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DamageService;
import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.FrayAttributes;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.action.PlayerCombat;
import dev.voidmc.fray.combat.math.DamageMath;
import dev.voidmc.fray.combat.net.CombatSync;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The damage pipeline (CONTRACTS.md §4.2), stages 1-9 on the server thread. The maths lives in {@link DamageMath}; this
 * class reads the entities (attributes, Mettle's stats, statuses), asks the defender's guard, and hands the total to
 * vanilla's {@code hurtServer} with {@code fray:combat}.
 */
public final class Pipeline implements DamageService {
	public static final Pipeline INSTANCE = new Pipeline();
	/** No two Fray hits from the same attacker on the same target within this many ticks unless the action says multi_hit. */
	public static final int FLOOR_TICKS = 4;

	/** Cost probes. */
	public static long dealNanos;
	public static long deals;
	/** Time spent in vanilla's hurtServer (stage 8) inside {@link #dealNanos}. */
	public static long handoffNanos;
	/** The last breakdown (tests, the studio log). */
	public static volatile @Nullable DamageBreakdown last;
	public static final List<String> LOG = new java.util.concurrent.CopyOnWriteArrayList<>();
	public static boolean log;

	private Pipeline() {
	}

	/** Mutable pools for one stage (CombatEvents.DamageBuilder). */
	static final class Builder implements CombatEvents.DamageBuilder {
		final CombatEvents.Stage stage;
		final float[] add = new float[DamageKind.VALUES.length];
		final float[] inc = new float[DamageKind.VALUES.length];
		final float[] more = new float[DamageKind.VALUES.length];
		float incAll;
		float moreAll = 1;
		float reduce;

		Builder(CombatEvents.Stage stage) {
			this.stage = stage;
			java.util.Arrays.fill(this.more, 1);
		}

		@Override
		public CombatEvents.Stage stage() {
			return this.stage;
		}

		@Override
		public void add(DamageKind kind, float amount) {
			this.add[kind.ordinal()] += amount;
		}

		@Override
		public void increased(@Nullable DamageKind kind, float fraction) {
			if (kind == null) {
				this.incAll += fraction;
			} else {
				this.inc[kind.ordinal()] += fraction;
			}
		}

		@Override
		public void more(@Nullable DamageKind kind, float factor) {
			if (kind == null) {
				this.moreAll *= factor;
			} else {
				this.more[kind.ordinal()] *= factor;
			}
		}

		@Override
		public void reduce(float fraction) {
			this.reduce += fraction;
		}
	}

	// ------------------------------------------------------------------ service

	@Override
	public float resistance(LivingEntity target, DamageKind kind) {
		return resistance(target, kind, false);
	}

	/** Fray's resistance attribute plus (players) Mettle's {@code resist.<kind>}, as one 'less' fraction. */
	public static float resistance(LivingEntity target, DamageKind kind, boolean withMettle) {
		Holder<Attribute> h = FrayAttributes.RESIST.get(kind);
		float r = h != null && target.getAttributes().hasAttribute(h) ? (float) target.getAttributeValue(h) : 0;
		r = Math.max(-1, Math.min(0.9F, r));
		if (withMettle && target instanceof Player p) {
			float m = (float) Math.max(0, Math.min(0.9, MettleStats.add(p, "resist." + kind.id()) / 100.0));
			r = 1 - (1 - r) * (1 - m);
		}
		return Math.min(0.9F, r);
	}

	@Override
	public DamageSpec specFor(ItemStack weapon, float baseDamage, float poiseMultiplier) {
		DamageSplit split = Weapons.split(weapon);
		return DamageSpec.split(split.shares(), baseDamage, baseDamage * split.poiseFactor() * poiseMultiplier, split.buildUp());
	}

	@Override
	public DamageBreakdown deal(LivingEntity target, HitContext hit) {
		long t0 = System.nanoTime();
		try {
			return this.run(target, hit, false);
		} finally {
			dealNanos += System.nanoTime() - t0;
			deals++;
		}
	}

	/** Runs the pipeline; {@code replay} is set when the defender's grace releases a held hit. */
	DamageBreakdown run(LivingEntity target, HitContext hit, boolean replay) {
		if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) {
			return DamageBreakdown.avoided(0, GuardOutcome.NONE);
		}
		Entity atk = hit.attacker();
		LivingEntity attacker = atk instanceof LivingEntity l ? l : null;
		CombatState ts = CombatState.of(target);
		long now = level.getGameTime();
		int flags = hit.flags();
		if (atk instanceof Player && target instanceof Player) {
			flags |= HitFlags.PVP;
		}
		boolean status = HitFlags.has(flags, HitFlags.STATUS);

		// The cooldown rule: hit-once per action is the action's job; here a 4-tick floor per attacker.
		if (atk != null && !status && !HitFlags.has(flags, HitFlags.MULTI_HIT)) {
			long last = ts.lastHitBy.getOrDefault(atk.getId(), Long.MIN_VALUE / 2);
			if (now - last < FLOOR_TICKS) {
				return DamageBreakdown.avoided(0, GuardOutcome.NONE);
			}
		}
		// Defender's grace: a creature's hit on a player waits up to half the ping for a dodge or parry in flight.
		if (!replay && !status && target instanceof ServerPlayer tp && !(atk instanceof Player)) {
			int grace = Defense.INSTANCE.graceTicks(tp);
			if (grace > 0) {
				Defense.INSTANCE.hold(tp, hit, now, grace);
				return DamageBreakdown.avoided(hit.spec().total(), GuardOutcome.NONE);
			}
		}

		// Stage 1: build.
		EnumMap<DamageKind, Float> amounts = new EnumMap<>(DamageKind.class);
		amounts.putAll(hit.spec().amounts());
		float base = sum(amounts);

		// Stage 2: attacker pools.
		Builder b2 = new Builder(CombatEvents.Stage.ATTACKER);
		if (attacker instanceof ServerPlayer ap && !status) {
			for (DamageKind k : DamageKind.VALUES) {
				b2.inc[k.ordinal()] += (float) (MettleStats.add(ap, "damage." + k.id()) / 100.0);
			}
			String power = HitFlags.has(flags, HitFlags.SPELL) ? "spell_power" : HitFlags.has(flags, HitFlags.PROJECTILE) ? "ranged_power" : "melee_power";
			b2.moreAll *= (float) MettleStats.mul(ap, power);
		}
		if (atk != null && ts.markedBy >= 0 && ts.has(StatusType.MARKED, now) && (ts.markedBy == atk.getId() || atk.isAlliedTo(level.getEntity(ts.markedBy)))) {
			b2.moreAll *= 1.1F;
		}
		if (!status) {
			CombatEvents.MODIFY_DAMAGE.invoker().modify(target, hit, b2);
		}
		for (DamageKind k : DamageKind.VALUES) {
			int i = k.ordinal();
			float v = amounts.getOrDefault(k, 0F);
			if (v <= 0 && b2.add[i] <= 0) {
				continue;
			}
			amounts.put(k, DamageMath.pools(v, b2.add[i], b2.incAll + b2.inc[i], b2.moreAll * b2.more[i]));
		}
		float pooled = sum(amounts);
		float capped = DamageMath.capAttacker(base, pooled, DamageMath.ATTACKER_CAP);
		scale(amounts, pooled <= 0 ? 1 : capped / pooled);
		float stage2 = sum(amounts);

		// Stage 3: situational.
		float sit = 1;
		boolean rolled = false;
		if (!status) {
			if (attacker != null && !HitFlags.has(flags, HitFlags.PROJECTILE) && target instanceof Mob m && m.getTarget() != attacker && behind(target, attacker)) {
				flags |= HitFlags.BACKSTAB;
			}
			float critChance = 0, critDamage = 1.5F;
			if (attacker != null) {
				critChance = (float) ((attacker.getAttributes().hasAttribute(FrayAttributes.CRIT_CHANCE) ? attacker.getAttributeValue(FrayAttributes.CRIT_CHANCE) : 0)
					+ MettleStats.add(attacker, "crit_chance") / 100.0);
				critDamage = (float) ((attacker.getAttributes().hasAttribute(FrayAttributes.CRIT_DAMAGE) ? attacker.getAttributeValue(FrayAttributes.CRIT_DAMAGE) : 1.5)
					+ MettleStats.add(attacker, "crit_damage") / 100.0);
			}
			critChance = Math.min(0.4F, critChance);
			rolled = critChance > 0 && level.getRandom().nextFloat() < critChance;
			float plunge = (float) (attacker == null ? DamageMath.PLUNGE : MettleStats.balance(attacker, BalanceKey.of("plunge_multiplier"), DamageMath.PLUNGE, null));
			float cap = (float) (attacker == null ? DamageMath.SITUATIONAL_CAP : MettleStats.balance(attacker, BalanceKey.of("situational_cap"), DamageMath.SITUATIONAL_CAP, null));
			sit = DamageMath.situational(flags, Math.max(1, hit.situational()), critDamage, rolled, 1.5F, plunge, cap);
			if (rolled || HitFlags.has(flags, HitFlags.RIPOSTE | HitFlags.BACKSTAB | HitFlags.EXECUTE)) {
				flags |= HitFlags.CRIT;
			}
			Builder b3 = new Builder(CombatEvents.Stage.SITUATIONAL);
			CombatEvents.MODIFY_DAMAGE.invoker().modify(target, hit, b3);
			sit *= b3.moreAll;
			scale(amounts, sit);
		}
		float stage3 = sum(amounts);
		EnumMap<DamageKind, Float> preDefence = new EnumMap<>(amounts);

		// Stage 4: guard (players).
		GuardOutcome guard = GuardOutcome.NONE;
		if (target instanceof ServerPlayer tp && !status) {
			guard = Defense.INSTANCE.guardHit(tp, attacker, flags, amounts, now);
			if (guard == GuardOutcome.DODGED || guard == GuardOutcome.SLIP || guard == GuardOutcome.PARRIED) {
				DamageBreakdown r = DamageBreakdown.avoided(stage3, guard);
				last = r;
				CombatSync.hit(atk, target, guard, flags, 0, mainKind(preDefence), false);
				if (atk != null) {
					ts.lastHitBy.put(atk.getId(), now);
				}
				return r;
			}
		}

		// Stage 5: defence.
		FrayDamageSource source = FrayDamageSource.combat(level, hit);
		float raw = sum(amounts);
		long frozen = ts.until[StatusType.FROZEN.ordinal()];
		for (DamageKind k : DamageKind.VALUES) {
			Float v = amounts.get(k);
			if (v == null || v <= 0) {
				continue;
			}
			float x = DamageMath.resist(v, resistance(target, k, true));
			if (k == DamageKind.BLUNT && frozen > now) {
				x *= 1.3F;
			}
			amounts.put(k, x);
		}
		if (ts.has(StatusType.HEX, now)) {
			scale(amounts, 1.1F);
		}
		if (!status) {
			Builder b5 = new Builder(CombatEvents.Stage.DEFENCE);
			CombatEvents.MODIFY_DAMAGE.invoker().modify(target, hit, b5);
			if (b5.reduce > 0) {
				scale(amounts, 1 - Math.min(0.9F, b5.reduce));
			}
		}
		float reduced = sum(amounts);
		float floored = DamageMath.floor(raw, reduced);
		if (reduced > 0 && floored > reduced) {
			scale(amounts, floored / reduced);
		}
		float physical = 0, tWeighted = 0;
		for (Map.Entry<DamageKind, Float> e : amounts.entrySet()) {
			if (e.getKey().physical()) {
				physical += e.getValue();
				tWeighted += e.getValue() * e.getKey().toughnessFactor;
			}
		}
		if (physical > 0) {
			float armour = target.getArmorValue() * (HitFlags.has(flags, HitFlags.RIPOSTE) ? 0.5F : 1F);
			float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * (tWeighted / physical);
			float after = CombatRules.getDamageAfterAbsorb(target, physical, source, armour, toughness);
			target.hurtArmor(source, physical);
			float f = after / physical;
			for (Map.Entry<DamageKind, Float> e : amounts.entrySet()) {
				if (e.getKey().physical()) {
					e.setValue(e.getValue() * f);
				}
			}
		}
		if (HitFlags.has(flags, HitFlags.PVP)) {
			scale(amounts, DamageMath.PVP_SCALE);
		}
		float total = sum(amounts);

		// Stage 6: statuses and poise.
		List<StatusType> procsBefore = new ArrayList<>();
		boolean wasBroken = ts.rank == Rank.BOSS && Encounters.INSTANCE.broken(target);
		if (!status) {
			EnumMap<StatusType, Float> build = new EnumMap<>(StatusType.class);
			for (Map.Entry<DamageKind, Float> e : preDefence.entrySet()) {
				StatusType st = e.getKey().pairedStatus();
				float f = Statuses.buildFactor(e.getKey());
				if (st != null && f > 0) {
					build.merge(st, e.getValue() * f * (guard == GuardOutcome.BLOCKED ? 0.5F : 1F), Float::sum);
				}
			}
			if (HitFlags.has(flags, HitFlags.CRIT) && attacker != null && MettleStats.add(attacker, "crit_bleed") > 0) {
				build.computeIfPresent(StatusType.BLEED, (k, v) -> v * 2);
			}
			hit.spec().buildUp().forEach((k, v) -> build.merge(k, v, Float::sum));
			ts.lastHitDamage = total;
			for (Map.Entry<StatusType, Float> e : build.entrySet()) {
				boolean wasActive = ts.has(e.getKey(), now);
				float before = ts.meters.fraction(e.getKey());
				Statuses.INSTANCE.addBuildUp(target, e.getKey(), e.getValue(), atk);
				if (ts.meters.fraction(e.getKey()) < before || !wasActive && ts.has(e.getKey(), now)) {
					procsBefore.add(e.getKey());
				}
			}
			boolean blunt = preDefence.getOrDefault(DamageKind.BLUNT, 0F) >= stage3 * 0.5F;
			if (HitFlags.has(flags, HitFlags.PLUNGE) || HitFlags.has(flags, HitFlags.GUARD_COUNTER) || blunt && HitFlags.has(flags, HitFlags.CRIT)) {
				Statuses.INSTANCE.apply(target, StatusType.DAZE, 10, atk);
			}
			float poise = DamageMath.poise(stage3, hit.poiseFactor(), flags);
			if (attacker != null) {
				poise *= (float) (1 + MettleStats.add(attacker, "stagger_power") / 100.0);
				poise = (float) MettleStats.balance(attacker, BalanceKey.POISE_DAMAGE, poise, hit.action());
			}
			if (HitFlags.has(flags, HitFlags.WEAK_POINT)) {
				poise *= 1.5F;
			}
			if (guard == GuardOutcome.BLOCKED) {
				poise *= 0.5F;
			}
			Defense.INSTANCE.poiseHit(target, ts, poise, attacker, now);
		}

		// Stage 8: hand-off to vanilla (Resistance effect, absorption, death, totems, stats, advancements).
		if (atk != null) {
			ts.lastHitBy.put(atk.getId(), now);
		}
		long h0 = System.nanoTime();
		boolean hurt = total > 0 && target.hurtServer(level, source, total);
		handoffNanos += System.nanoTime() - h0;
		if (hurt && !status && !target.isDeadOrDying()) {
			// The hit flinch (additive, ANIM_EVENTS.md §1): player/hit_light or hit_heavy, zombie/hit_light, ...
			MotionBridge.flinch(target, target instanceof Player && HitFlags.has(flags, HitFlags.HEAVY) ? "hit_heavy" : "hit_light");
		}
		if (hurt && atk != null && !status) {
			float kb = HitFlags.has(flags, HitFlags.CHARGED) ? 0.8F : HitFlags.has(flags, HitFlags.HEAVY) ? 0.45F : 0.15F;
			if (target.isDeadOrDying()) {
				kb = 0.6F;
			}
			Vec3 d = atk.position().subtract(target.position());
			target.knockback(kb, d.x, d.z, source, total);
		}
		boolean killed = target.isDeadOrDying();

		// Stage 9: after.
		DamageBreakdown result = new DamageBreakdown(base, stage2, sit, guard, Map.copyOf(amounts), hurt ? total : 0, 0, List.copyOf(procsBefore),
			HitFlags.has(flags, HitFlags.CRIT), killed);
		last = result;
		if (log) {
			LOG.add(String.format(java.util.Locale.ROOT, "%s -> %s %s base %.2f atk %.2f sit %.2f guard %s total %.2f crit %s killed %s", atk == null ? "?" : atk.getName().getString(),
				target.getName().getString(), hit.action(), base, stage2, sit, guard, result.total(), result.crit(), killed));
		}
		if (!status) {
			CombatEvents.DAMAGE_DEALT.invoker().dealt(atk, target, hit, result);
			if (killed) {
				CombatEvents.KILLED.invoker().killed(atk, target, hit, ts.rank);
			}
			if (target instanceof Mob m && attacker != null) {
				Threat.INSTANCE.add(m, attacker, result.total(), Fray.id("damage"));
			}
			if (attacker instanceof ServerPlayer ap) {
				PlayerCombat pc = CombatState.of(ap).player;
				if (pc != null) {
					pc.tagCombat(100);
				}
			}
			if (target instanceof ServerPlayer tp2) {
				PlayerCombat pc = CombatState.of(tp2).player;
				if (pc != null) {
					pc.tagCombat(100);
				}
			}
			if (attacker instanceof ServerPlayer ap && target instanceof Mob m) {
				Encounters.INSTANCE.damage(m, ap, result.total());
			}
			boolean broke = ts.rank == Rank.BOSS && !wasBroken && Encounters.INSTANCE.broken(target);
			CombatSync.hit(atk, target, guard, flags | (killed ? dev.voidmc.fray.combat.net.CombatPayloads.HIT_KILL : 0)
				| (broke ? dev.voidmc.fray.combat.net.CombatPayloads.HIT_BREAK : 0), result.total(), mainKind(preDefence), killed);
		}
		return result;
	}

	static boolean behind(LivingEntity target, LivingEntity attacker) {
		Vec3 d = attacker.position().subtract(target.position());
		float to = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
		return Math.abs(Mth.wrapDegrees(to - target.yBodyRot)) >= 120;
	}

	static DamageKind mainKind(Map<DamageKind, Float> m) {
		DamageKind best = DamageKind.BLUNT;
		float v = -1;
		for (Map.Entry<DamageKind, Float> e : m.entrySet()) {
			if (e.getValue() > v) {
				v = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	static float sum(Map<DamageKind, Float> m) {
		float s = 0;
		for (float v : m.values()) {
			s += v;
		}
		return s;
	}

	static void scale(Map<DamageKind, Float> m, float f) {
		if (f == 1) {
			return;
		}
		for (Map.Entry<DamageKind, Float> e : m.entrySet()) {
			e.setValue(e.getValue() * f);
		}
	}
}
