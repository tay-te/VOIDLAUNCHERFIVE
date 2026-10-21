package dev.voidmc.fray.combat;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.FrayAttributes;
import dev.voidmc.fray.api.InterruptReason;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.BalanceKey;
import dev.voidmc.fray.api.StatusService;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.action.Actions;
import dev.voidmc.fray.combat.math.MeterSet;
import dev.voidmc.fray.combat.net.CombatSync;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The shared statuses (CONTRACTS.md §6) and the per-tick upkeep of every entity with combat state: meters drain, timed
 * statuses expire, poise refills, threat decays, players' actions and stamina run, and what changed is synced.
 * Vanilla effects are reused where they exist (Poison, Wither, fire and freeze ticks).
 */
public final class Statuses implements StatusService {
	public static final Statuses INSTANCE = new Statuses();

	private static final Identifier ROOT = Fray.id("status/root");
	private static final Identifier CHILL_MOVE = Fray.id("status/chill");
	private static final Identifier CHILL_ATTACK = Fray.id("status/chill_attack");
	private static final Identifier EXPOSED = Fray.id("status/exposed");
	private static final Identifier BURNING = Fray.id("status/burning");

	private static final ReferenceOpenHashSet<CombatState> TICKING = new ReferenceOpenHashSet<>();

	/** Server cost probes (perf test): nanoseconds in {@link #tickLevel} and entity-ticks done. */
	public static long tickNanos;
	public static long entityTicks;

	private Statuses() {
	}

	static void track(CombatState s) {
		if (!s.ticking) {
			s.ticking = true;
			TICKING.add(s);
		}
	}

	public static void clearAll() {
		TICKING.clear();
	}

	public static int tracked() {
		return TICKING.size();
	}

	/** Build-up a hit of {@code amount} of {@code kind} adds to the kind's paired status (neutral defaults). */
	public static float buildFactor(DamageKind kind) {
		return switch (kind) {
			case SLASH -> 1.5F;
			case PIERCE -> 0.75F;
			case BLUNT, RADIANT -> 0;
			default -> 3.0F;
		};
	}

	public static boolean wet(LivingEntity e) {
		CombatState s = CombatState.peek(e);
		return e.isInWaterOrRain() || s != null && s.has(StatusType.WET, e.level().getGameTime());
	}

	static boolean metalArmoured(LivingEntity e) {
		ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
		return chest.is(Items.IRON_CHESTPLATE) || chest.is(Items.CHAINMAIL_CHESTPLATE) || chest.is(Items.GOLDEN_CHESTPLATE) || chest.is(Items.NETHERITE_CHESTPLATE)
			|| chest.is(Items.COPPER_CHESTPLATE);
	}

	// ------------------------------------------------------------------ service

	@Override
	public void addBuildUp(LivingEntity target, StatusType status, float amount, @Nullable Entity source) {
		if (!status.metered || amount <= 0 || !target.isAlive() || !(target.level() instanceof ServerLevel)) {
			return;
		}
		if (status == StatusType.POISON && target.is(EntityTypeTags.UNDEAD)) {
			return;
		}
		CombatState s = CombatState.of(target);
		long now = target.level().getGameTime();
		amount *= MeterSet.reactionScale(status, wet(target), s.has(StatusType.OILED, now));
		if (source instanceof LivingEntity src) {
			double m = MettleStats.mul(src, "status_power") * (1 + MettleStats.add(src, "status_chance." + status.id()) / 100.0);
			m = MettleStats.balance(src, BalanceKey.STATUS_BUILDUP, m, Fray.id(status.id()));
			amount *= (float) Math.min(2.0, m);
		}
		float threshold = this.threshold(target, status);
		CombatEvents.STATUS_BUILDUP.invoker().buildUp(target, status, amount, source);
		boolean proc = s.meters.add(status, amount, threshold, now);
		s.metersDirty = true;
		if (status == StatusType.CHILL) {
			this.updateChill(s);
		}
		if (proc) {
			this.proc(target, s, status, source, now);
		}
	}

	@Override
	public float meter(LivingEntity target, StatusType status) {
		CombatState s = CombatState.peek(target);
		return s == null ? 0 : s.meters.fraction(status);
	}

	@Override
	public float threshold(LivingEntity target, StatusType status) {
		Holder<Attribute> h = FrayAttributes.STATUS_RESIST.get(status);
		double r = h != null && target.getAttributes().hasAttribute(h) ? target.getAttributeValue(h) : 0;
		CombatState s = CombatState.peek(target);
		float boss = s != null && s.rank == Rank.BOSS ? 4 : 1;
		return MeterSet.threshold((float) r) * boss;
	}

	@Override
	public void apply(LivingEntity target, StatusType status, int ticks, @Nullable Entity source) {
		if (!target.isAlive() || !(target.level() instanceof ServerLevel)) {
			return;
		}
		CombatState s = CombatState.of(target);
		long now = target.level().getGameTime();
		int dur = ticks > 0 ? ticks : status.duration;
		if (status.harmful && target instanceof ServerPlayer p) {
			dur = (int) Math.max(1, Math.round(dur * Math.max(0.5, MettleStats.mul(p, "status_taken"))));
		}
		if (dur <= 0) {
			return;
		}
		int i = status.ordinal();
		s.until[i] = Math.max(s.until[i], now + dur);
		s.length[i] = (int) (s.until[i] - now);
		s.statusDirty = true;
		s.metersDirty = true;
		switch (status) {
			case STAGGER -> {
				Actions.INSTANCE.interrupt(target, InterruptReason.STAGGER);
				this.root(target, true);
				CombatEvents.STAGGERED.invoker().staggered(target, source, dur);
			}
			case DAZE -> Actions.INSTANCE.interrupt(target, InterruptReason.DAZE);
			case FROZEN -> {
				Actions.INSTANCE.interrupt(target, InterruptReason.FROZEN);
				this.root(target, true);
				target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() - 1));
			}
			case SHOCK -> {
				Actions.INSTANCE.interrupt(target, InterruptReason.STUN);
				this.root(target, true);
			}
			case ROOTED -> this.root(target, true);
			case EXPOSED -> modifier(target, Attributes.ARMOR, EXPOSED, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case BURN -> {
				Holder<Attribute> fire = FrayAttributes.RESIST.get(DamageKind.FIRE);
				modifier(target, fire, BURNING, -0.2, AttributeModifier.Operation.ADD_VALUE);
			}
			case MARKED -> s.markedBy = source == null ? -1 : source.getId();
			default -> {
			}
		}
	}

	@Override
	public boolean has(LivingEntity target, StatusType status) {
		CombatState s = CombatState.peek(target);
		return s != null && s.has(status, target.level().getGameTime());
	}

	@Override
	public int remaining(LivingEntity target, StatusType status) {
		CombatState s = CombatState.peek(target);
		return s == null ? 0 : s.left(status, target.level().getGameTime());
	}

	@Override
	public void clear(LivingEntity target, StatusType status) {
		CombatState s = CombatState.peek(target);
		if (s == null) {
			return;
		}
		s.until[status.ordinal()] = 0;
		s.statusDirty = true;
		s.metersDirty = true;
		this.expire(target, s, status, target.level().getGameTime());
	}

	@Override
	public boolean incapacitated(LivingEntity target) {
		CombatState s = CombatState.peek(target);
		if (s == null) {
			return false;
		}
		long now = target.level().getGameTime();
		return s.has(StatusType.STAGGER, now) || s.has(StatusType.DAZE, now) || s.has(StatusType.FROZEN, now) || s.has(StatusType.SHOCK, now);
	}

	// ------------------------------------------------------------------ procs

	private void proc(LivingEntity target, CombatState s, StatusType status, @Nullable Entity source, long now) {
		CombatEvents.STATUS_PROC.invoker().buildUp(target, status, 0, source);
		ServerLevel level = (ServerLevel) target.level();
		boolean boss = s.rank == Rank.BOSS;
		switch (status) {
			case BLEED -> statusDamage(level, target, target.getMaxHealth() * (boss ? 0.03F : 0.08F) + 4, source);
			case BURN -> {
				int dur = 80;
				if (s.has(StatusType.OILED, now)) {
					// Conflagration: a burst and double the burn.
					statusDamage(level, target, 6, source);
					dur = 160;
					this.clear(target, StatusType.OILED);
				}
				target.igniteForSeconds(dur / 20F);
				this.apply(target, StatusType.BURN, dur, source);
			}
			case CHILL -> {
				this.apply(target, StatusType.FROZEN, StatusType.FROZEN.duration, source);
				this.updateChill(s);
			}
			case SHOCK -> {
				this.apply(target, StatusType.SHOCK, StatusType.SHOCK.duration, source);
				float jump = s.lastHitDamage * 0.5F;
				if (jump > 0) {
					LivingEntity best = null;
					double bestD = 16;
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4), e -> e != target && e != source && e.isAlive())) {
						double d = e.distanceToSqr(target);
						if (d < bestD && (wet(e) || metalArmoured(e))) {
							best = e;
							bestD = d;
						}
					}
					if (best != null) {
						statusDamage(level, best, jump, source);
						this.apply(best, StatusType.SHOCK, StatusType.SHOCK.duration, source);
					}
				}
			}
			case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 120, boss ? 0 : 1), source);
			case HEX -> this.apply(target, StatusType.HEX, StatusType.HEX.duration, source);
			case BLIGHT -> {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 0), source);
				this.apply(target, StatusType.BLIGHT, 120, source);
			}
			default -> {
			}
		}
		s.statusDirty = true;
	}

	/** A status's own damage (Bleed burst, Shock jump, Conflagration): fray:status, never guarded, builds nothing. */
	public static void statusDamage(ServerLevel level, LivingEntity target, float amount, @Nullable Entity source) {
		target.hurtServer(level, FrayDamageSource.status(level, source), amount);
	}

	private void updateChill(CombatState s) {
		int stacks = s.has(StatusType.FROZEN, s.entity.level().getGameTime()) ? 0 : s.meters.chillStacks();
		if (stacks == s.chillStacks) {
			return;
		}
		s.chillStacks = stacks;
		if (stacks == 0) {
			removeModifier(s.entity, Attributes.MOVEMENT_SPEED, CHILL_MOVE);
			removeModifier(s.entity, Attributes.ATTACK_SPEED, CHILL_ATTACK);
		} else {
			modifier(s.entity, Attributes.MOVEMENT_SPEED, CHILL_MOVE, -0.1 * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			modifier(s.entity, Attributes.ATTACK_SPEED, CHILL_ATTACK, -0.1 * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	private void root(LivingEntity e, boolean on) {
		if (on) {
			modifier(e, Attributes.MOVEMENT_SPEED, ROOT, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			removeModifier(e, Attributes.MOVEMENT_SPEED, ROOT);
		}
	}

	static void modifier(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = attr == null ? null : e.getAttribute(attr);
		if (inst != null) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	static void removeModifier(LivingEntity e, Holder<Attribute> attr, Identifier id) {
		AttributeInstance inst = attr == null ? null : e.getAttribute(attr);
		if (inst != null && inst.hasModifier(id)) {
			inst.removeModifier(id);
		}
	}

	private void expire(LivingEntity e, CombatState s, StatusType status, long now) {
		switch (status) {
			case STAGGER, FROZEN, SHOCK, ROOTED -> {
				if (!s.has(StatusType.STAGGER, now) && !s.has(StatusType.FROZEN, now) && !s.has(StatusType.SHOCK, now) && !s.has(StatusType.ROOTED, now)) {
					this.root(e, false);
				}
				if (status == StatusType.FROZEN) {
					this.updateChill(s);
				}
			}
			case EXPOSED -> removeModifier(e, Attributes.ARMOR, EXPOSED);
			case BURN -> removeModifier(e, FrayAttributes.RESIST.get(DamageKind.FIRE), BURNING);
			case MARKED -> s.markedBy = -1;
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ tick

	public static void tickLevel(ServerLevel level) {
		if (TICKING.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		long now = level.getGameTime();
		List<CombatState> drop = null;
		for (CombatState s : TICKING) {
			LivingEntity e = s.entity;
			if (e.level() != level) {
				if (e.isRemoved()) {
					(drop == null ? drop = new ArrayList<>() : drop).add(s);
				}
				continue;
			}
			if (e.isRemoved()) {
				(drop == null ? drop = new ArrayList<>() : drop).add(s);
				continue;
			}
			INSTANCE.tickOne(level, s, e, now);
			entityTicks++;
			if (s.idle(now) && !s.metersDirty && !s.statusDirty) {
				(drop == null ? drop = new ArrayList<>() : drop).add(s);
			}
		}
		if (drop != null) {
			for (CombatState s : drop) {
				TICKING.remove(s);
				s.ticking = false;
			}
		}
		tickNanos += System.nanoTime() - t0;
	}

	private void tickOne(ServerLevel level, CombatState s, LivingEntity e, long now) {
		if (s.meters.tick(now)) {
			s.metersDirty = true;
			if (s.meters.value[StatusType.CHILL.meterIndex()] >= 0) {
				this.updateChill(s);
			}
		}
		for (StatusType st : StatusType.VALUES) {
			long u = s.until[st.ordinal()];
			if (u != 0 && u <= now) {
				s.until[st.ordinal()] = 0;
				s.statusDirty = true;
				s.metersDirty = true;
				this.expire(e, s, st, now);
			}
		}
		if (s.has(StatusType.FROZEN, now)) {
			e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() - 1));
		}
		if (s.player != null) {
			if (now % 10 == 0 && e.isInWaterOrRain()) {
				this.apply(e, StatusType.WET, StatusType.WET.duration, null);
			}
			s.player.tick(level, now);
		} else {
			s.poise.tick(now, s.rank.poiseRefill);
		}
		if (s.threat != null) {
			s.threat.tick(now);
		}
		CombatSync.meters(s, now);
	}
}
