package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DefenseHooks;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.InterruptReason;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.action.Actions;
import dev.voidmc.fray.combat.action.ActionKind;
import dev.voidmc.fray.combat.action.Movesets;
import dev.voidmc.fray.combat.action.PlayerCombat;
import dev.voidmc.fray.combat.math.GuardMath;
import dev.voidmc.fray.combat.net.CombatSync;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The defender's side of every hit on a player (COMBAT.md §6, §7, §10.3): dodge i-frames and Slip, parry and block,
 * guard break, flinch, and the defender's grace that holds a creature's hit for up to half the player's ping so a
 * dodge or parry already pressed (but still in flight) wins. Works the same for Fray hits (pipeline stage 4) and
 * foreign hits (vanilla zombies, Fray Motion's creature sweeps), which reach it through {@code hurtServer}.
 */
public final class Defense implements DefenseHooks {
	public static final Defense INSTANCE = new Defense();

	/** A hit held for the defender's grace: either a Fray hit or a foreign one. */
	private record Pending(ServerPlayer target, @Nullable HitContext hit, @Nullable DamageSource source, float amount, long land, long release) {
	}

	private final List<Pending> pending = new ArrayList<>();
	/** Set while a held or reduced foreign hit is replayed (the mixin lets it through untouched). */
	public static boolean replaying;
	/** Override for tests: a fixed grace in ticks (-1: Fray Motion's {@code DefenderTiming}). */
	public static int graceOverride = -1;

	private Defense() {
	}

	/**
	 * How long a creature's hit on this player is held for a dodge or parry still in flight: {@code DefenderTiming}'s
	 * grace (the measured round trip + 1 tick, capped), the same number timeline hits are held for.
	 */
	@Override
	public int graceTicks(ServerPlayer player) {
		if (graceOverride >= 0) {
			return graceOverride;
		}
		return dev.voidmc.fray.motion.DefenderTiming.graceTicks(player.getUUID(), false);
	}

	/**
	 * A creature's timeline hit (Fray Motion found the capsule touching and held it for the defender's grace): judged
	 * like every other hit on a player, by what the player's own action covered at {@code serverTick} on their clock
	 * ({@link PlayerCombat#window}, i.e. {@code DefenderTiming}), with the tell rules, then the pipeline. A player's own
	 * clip volumes are ignored: the player's melee is the combat side's arc sweep.
	 */
	public void timelineHit(LivingEntity attacker, LivingEntity target, net.minecraft.resources.Identifier clip, @Nullable String tell, float damage, long serverTick) {
		if (attacker instanceof Player || !target.isAlive() || !(target.level() instanceof ServerLevel)) {
			return;
		}
		dev.voidmc.fray.api.TellColour colour = dev.voidmc.fray.api.TellColour.byId(tell == null ? "white" : tell);
		int flags = HitFlags.MELEE | colour.hitFlags();
		float dmg = damage > 0 ? damage : (float) (attacker.getAttributes().hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
			? attacker.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) : 1);
		HitContext hit = new HitContext(attacker, attacker.getMainHandItem(), clip, Pipeline.INSTANCE.specFor(attacker.getMainHandItem(), dmg, 1), flags, 0, null);
		if (!(target instanceof ServerPlayer p)) {
			Pipeline.INSTANCE.deal(target, hit);
			return;
		}
		PlayerCombat pc = CombatState.of(p).player;
		long before = pc == null ? Long.MIN_VALUE : pc.graceLand;
		if (pc != null) {
			pc.graceLand = serverTick;
		}
		try {
			Pipeline.INSTANCE.run(p, hit, true);
		} finally {
			if (pc != null) {
				pc.graceLand = before;
			}
		}
	}

	@Override
	public GuardOutcome preview(LivingEntity defender, LivingEntity attacker, int hitFlags) {
		if (!(defender instanceof ServerPlayer p)) {
			return GuardOutcome.NONE;
		}
		PlayerCombat pc = CombatState.of(p).player;
		return pc == null ? GuardOutcome.NONE : this.resolve(pc, attacker, null, hitFlags, pc.now());
	}

	void hold(ServerPlayer target, HitContext hit, long now, int grace) {
		this.pending.add(new Pending(target, hit, null, 0, now, now + grace));
	}

	public void holdForeign(ServerPlayer target, DamageSource source, float amount, long now, int grace) {
		this.pending.add(new Pending(target, null, source, amount, now, now + grace));
	}

	public void clear() {
		this.pending.clear();
	}

	public void tick(ServerLevel level) {
		if (this.pending.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		List<Pending> due = null;
		for (Iterator<Pending> it = this.pending.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.target.level() != level) {
				if (p.target.isRemoved()) {
					it.remove();
				}
				continue;
			}
			if (now >= p.release) {
				it.remove();
				(due == null ? due = new ArrayList<>() : due).add(p);
			}
		}
		if (due == null) {
			return;
		}
		for (Pending p : due) {
			if (!p.target.isAlive()) {
				continue;
			}
			if (p.hit != null) {
				// Resolved as of the tick it landed: windows were back-dated by the lag allowance when they were pressed.
				CombatState.of(p.target).player.graceLand = p.land;
				try {
					Pipeline.INSTANCE.run(p.target, p.hit, true);
				} finally {
					CombatState.of(p.target).player.graceLand = Long.MIN_VALUE;
				}
			} else {
				this.foreign(p.target, p.source, p.amount, p.land, level);
			}
		}
	}

	/** Who struck: the living attacker of a melee hit or a projectile's owner. */
	static @Nullable LivingEntity attacker(DamageSource source) {
		return source.getEntity() instanceof LivingEntity le ? le : null;
	}

	/**
	 * A foreign hit on a player: returns the damage to pass on (0 = cancel). Holds it for the grace when the attacker is a
	 * creature, resolves dodge, parry and block otherwise.
	 */
	public float foreignHit(ServerPlayer p, DamageSource source, float amount, ServerLevel level) {
		if (replaying || source instanceof FrayDamageSource) {
			return amount;
		}
		LivingEntity attacker = attacker(source);
		if (attacker == null || attacker == p) {
			return amount;
		}
		boolean melee = source.getDirectEntity() == attacker;
		boolean projectile = source.getDirectEntity() instanceof Projectile;
		if (!melee && !projectile) {
			return amount;
		}
		long now = level.getGameTime();
		if (!(attacker instanceof Player)) {
			int grace = this.graceTicks(p);
			if (grace > 0) {
				this.holdForeign(p, source, amount, now, grace);
				return 0;
			}
		}
		return this.foreign(p, source, amount, now, level);
	}

	/** Resolves a foreign hit as of tick {@code at}; returns the damage (and applies it itself when replaying a held hit). */
	private float foreign(ServerPlayer p, DamageSource source, float amount, long at, ServerLevel level) {
		LivingEntity attacker = attacker(source);
		PlayerCombat pc = CombatState.of(p).player;
		boolean projectile = source.getDirectEntity() instanceof Projectile;
		int flags = (projectile ? HitFlags.PROJECTILE : HitFlags.MELEE) | (attacker == null ? 0 : Telegraphs.INSTANCE.flags(attacker));
		boolean held = at < level.getGameTime();
		DamageKind kind = DamageProfiles.mainKind(source);
		Map<DamageKind, Float> amounts = new java.util.EnumMap<>(DamageKind.class);
		amounts.put(kind, amount);
		GuardOutcome g = pc == null ? GuardOutcome.NONE : this.guardAt(pc, attacker, source.getDirectEntity(), flags, amounts, at, level.getGameTime());
		float out = g == GuardOutcome.DODGED || g == GuardOutcome.SLIP || g == GuardOutcome.PARRIED ? 0 : Pipeline.sum(amounts);
		if (g != GuardOutcome.NONE) {
			CombatSync.hit(attacker, p, g, flags, out, kind, false);
		}
		if (out > 0 && pc != null) {
			this.poiseHit(p, CombatState.of(p), amount, attacker, level.getGameTime());
			pc.tagCombat(100);
		}
		if (held && out > 0) {
			replaying = true;
			try {
				p.hurtServer(level, source, out);
			} finally {
				replaying = false;
			}
			return 0;
		}
		return out;
	}

	/** Stage 4 for a Fray hit on a player; mutates {@code amounts} for a block. */
	GuardOutcome guardHit(ServerPlayer p, @Nullable LivingEntity attacker, int flags, Map<DamageKind, Float> amounts, long now) {
		PlayerCombat pc = CombatState.of(p).player;
		if (pc == null) {
			return GuardOutcome.NONE;
		}
		long at = pc.graceLand != Long.MIN_VALUE ? pc.graceLand : now;
		return this.guardAt(pc, attacker, attacker, flags, amounts, at, now);
	}

	private GuardOutcome resolve(PlayerCombat pc, @Nullable LivingEntity attacker, @Nullable Entity direct, int flags, long at) {
		if (HitFlags.has(flags, HitFlags.STATUS)) {
			return GuardOutcome.NONE;
		}
		dev.voidmc.fray.motion.DefenderTiming.Window w = pc.window(at);
		if (w == dev.voidmc.fray.motion.DefenderTiming.Window.PERFECT) {
			return pc.slipped ? GuardOutcome.DODGED : GuardOutcome.SLIP;
		}
		if (w == dev.voidmc.fray.motion.DefenderTiming.Window.IFRAMES) {
			return GuardOutcome.DODGED;
		}
		boolean parry = w == dev.voidmc.fray.motion.DefenderTiming.Window.PARRY && !pc.winded();
		if (!parry && !pc.guardHeld) {
			return GuardOutcome.NONE;
		}
		Entity from = direct != null ? direct : attacker;
		float off = 0;
		if (from != null) {
			Vec3 d = from.position().subtract(pc.player.position());
			float to = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
			off = Mth.wrapDegrees(to - pc.player.getYHeadRot());
		}
		boolean covered = GuardMath.covers(pc.guard, off);
		// The parry is DefenderTiming's window (above); the block needs the guard held since blockFrom ticks.
		int since = (int) Math.max(0, Math.min(Integer.MAX_VALUE - 1, at - pc.guardPress));
		return GuardMath.resolve(pc.guard, since, parry ? since + 1 : 0, pc.guardHeld, covered, flags);
	}

	private GuardOutcome guardAt(PlayerCombat pc, @Nullable LivingEntity attacker, @Nullable Entity direct, int flags, Map<DamageKind, Float> amounts, long at,
		long now) {
		GuardOutcome g = this.resolve(pc, attacker, direct, flags, at);
		ServerPlayer p = pc.player;
		switch (g) {
			case SLIP -> {
				pc.slipped = true;
				pc.stamina.restore(Movesets.SLIP_REFUND);
				pc.slipBonusUntil = now + Movesets.SLIP_BONUS_TICKS;
				if (attacker instanceof Mob m) {
					Statuses.INSTANCE.apply(m, StatusType.DAZE, 6, p);
				}
				CombatEvents.DODGED.invoker().dodged(p, ActionKind.DODGE.id(), true);
			}
			case DODGED -> CombatEvents.DODGED.invoker().dodged(p, ActionKind.DODGE.id(), false);
			case PARRIED -> this.parried(pc, attacker, now);
			case BLOCKED -> g = this.block(pc, attacker, flags, amounts, now);
			default -> {
			}
		}
		return g;
	}

	private void parried(PlayerCombat pc, @Nullable LivingEntity attacker, long now) {
		ServerPlayer p = pc.player;
		pc.parriedThisPress = true;
		float refund = GuardMath.PARRY_REFUND;
		refund += (float) (MettleStats.add(p, "stamina_refund") * dev.voidmc.fray.combat.math.GuardMath.PARRY_COST);
		pc.stamina.restore(refund);
		Rank rank = attacker == null ? Rank.COMMON : Profiles.INSTANCE.rank(attacker);
		int window = attacker instanceof Player ? 10 : rank.riposteWindow;
		if (attacker instanceof Mob m && Encounters.INSTANCE.of(m) != null) {
			Encounters.INSTANCE.addBreak(m, 0.08F);
			window = 14;
		} else if (attacker != null) {
			int stagger = attacker instanceof Player ? 10 : rank.staggerTicks;
			Statuses.INSTANCE.apply(attacker, StatusType.STAGGER, stagger, p);
			Actions.INSTANCE.interrupt(attacker, InterruptReason.PARRIED);
			Vec3 d = p.position().subtract(attacker.position());
			attacker.knockback(0.3, d.x, d.z, p.damageSources().playerAttack(p), 0);
		}
		pc.riposteUntil = now + window;
		pc.riposteTotal = window;
		CombatEvents.GUARDED.invoker().guarded(p, attacker, GuardOutcome.PARRIED, 0);
	}

	private GuardOutcome block(PlayerCombat pc, @Nullable LivingEntity attacker, int flags, Map<DamageKind, Float> amounts, long now) {
		ServerPlayer p = pc.player;
		float blocked = 0;
		float[] parts = new float[DamageKind.VALUES.length];
		for (Map.Entry<DamageKind, Float> e : amounts.entrySet()) {
			float b = e.getValue() * pc.guard.blocked(e.getKey());
			parts[e.getKey().ordinal()] = b;
			blocked += b;
		}
		if (blocked <= 0) {
			return GuardOutcome.NONE;
		}
		float scale = (float) MettleStats.mul(p, "stamina_cost.block");
		float cost = GuardMath.blockCost(pc.guard, blocked, flags, scale);
		cost = (float) MettleStats.balance(p, dev.voidmc.fray.api.BalanceKey.GUARD_EFFICIENCY, cost, null);
		boolean breaks = pc.winded() || pc.stamina.value < cost;
		float paid = breaks ? (pc.winded() ? 0 : pc.stamina.value / cost) : 1;
		pc.spend(Math.min(cost, Math.max(pc.stamina.value, 0.01F)), ActionKind.PARRY.id());
		for (Map.Entry<DamageKind, Float> e : amounts.entrySet()) {
			e.setValue(e.getValue() - parts[e.getKey().ordinal()] * paid);
		}
		pc.lastBlockedHit = now;
		// The axe's heavy breaks a raised shield for 5 s (vanilla's rule, kept).
		if (pc.guard.shield() && attacker != null && attacker.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES) && HitFlags.has(flags, HitFlags.HEAVY)) {
			ItemStack shield = Weapons.hasShield(p) ? (p.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS) ? p.getOffhandItem() : p.getMainHandItem()) : ItemStack.EMPTY;
			if (!shield.isEmpty()) {
				p.getCooldowns().addCooldown(shield, 100);
				p.stopUsingItem();
			}
		}
		if (breaks) {
			pc.guardHeld = false;
			if (!pc.winded()) {
				pc.stamina.spend(pc.stamina.value + 1, now);
			}
			Statuses.INSTANCE.apply(p, StatusType.STAGGER, 25, attacker);
			p.stopUsingItem();
			CombatEvents.GUARDED.invoker().guarded(p, attacker, GuardOutcome.GUARD_BREAK, blocked * paid);
			return GuardOutcome.GUARD_BREAK;
		}
		CombatEvents.GUARDED.invoker().guarded(p, attacker, GuardOutcome.BLOCKED, blocked);
		MotionBridge.flinch(p, "block_hit");
		return GuardOutcome.BLOCKED;
	}

	/** Poise damage: creatures stagger, players flinch, bosses fill the Break meter. */
	void poiseHit(LivingEntity target, CombatState s, float amount, @Nullable LivingEntity by, long now) {
		if (amount <= 0) {
			return;
		}
		if (target instanceof Mob m && Encounters.INSTANCE.of(m) != null) {
			Encounters.INSTANCE.addBreak(m, amount / Math.max(100, s.poise.max * 3));
			return;
		}
		if (s.player != null) {
			PlayerCombat pc = s.player;
			// Hyper-armour: the clip's hyper_armour window (or windup and ACTIVE of a heavy, charged or Art without one).
			float hyper = pc.run != null && pc.run.timing.hyperArmour((int) (now - pc.run.start)) ? Poise.HYPER_ARMOUR : 0;
			if (s.poise.flinches(amount, hyper)) {
				pc.interrupt(InterruptReason.FLINCH);
			}
			s.poise.hit(amount, hyper, now);
			return;
		}
		if (s.has(StatusType.STAGGER, now)) {
			return;
		}
		if (s.poise.hit(amount, 0, now)) {
			CombatEvents.POISE_BROKEN.invoker().staggered(target, by, s.rank.staggerTicks);
			Statuses.INSTANCE.apply(target, StatusType.STAGGER, s.rank.staggerTicks, by);
		}
	}
}
