package dev.voidmc.fray.combat.action;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.BalanceKey;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.FrayAttributes;
import dev.voidmc.fray.api.InterruptReason;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.CombatState;
import dev.voidmc.fray.combat.Encounters;
import dev.voidmc.fray.combat.MettleStats;
import dev.voidmc.fray.combat.Statuses;
import dev.voidmc.fray.combat.Weapons;
import dev.voidmc.fray.combat.math.GuardMath;
import dev.voidmc.fray.combat.math.HoldMath;
import dev.voidmc.fray.combat.math.Phases;
import dev.voidmc.fray.combat.math.PoiseMeter;
import dev.voidmc.fray.combat.math.StaminaPool;
import dev.voidmc.fray.combat.net.CombatPayloads;
import dev.voidmc.fray.combat.net.CombatSync;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One player's combat on the server (COMBAT.md §3-§11): stamina, the running action and its phases, the input buffer,
 * the attack button's hold, guard (parry and block), dodge i-frames, the specials' windows, lock-on and the Art
 * cooldown. Inputs arrive as {@link CombatPayloads.Input} (already predicted by the client); the server validates and
 * runs everything here, and its phases decide every hit.
 */
public final class PlayerCombat {
	/** Reasons in an Ack. */
	public static final byte OK = 0, BUSY = 1, WINDED = 2, NO_STAMINA = 3, CANT_ACT = 4, NO_ART = 5, COOLDOWN = 6, NO_TECHNIQUE = 7, AIRBORNE = 8,
		BUFFERED = 9, BAD_TARGET = 10;

	private static final Identifier SLOW = Fray.id("action_slow");
	/** Every action started ("tick player KIND"), for tests and the studio log. */
	public static final java.util.List<String> ACTIONS = new java.util.concurrent.CopyOnWriteArrayList<>();

	public final ServerPlayer player;
	public final CombatState state;
	public final StaminaPool stamina = new StaminaPool(100);

	public static final class Run {
		public ActionKind kind;
		public ActionDef def;
		public Phases phases;
		public long start;
		public int combo;
		public float charge;
		public float yaw;
		public boolean aimed;
		public int declared = -1;
		/** The light's combo index or the dodge's compass direction (which clip plays). */
		public int variant;
		public boolean slipBonus;
		public final IntOpenHashSet hit = new IntOpenHashSet();
	}

	public @Nullable Run run;
	private int lastCombo = -1;
	private long comboOpenUntil = Long.MIN_VALUE / 2;

	// The attack button.
	public boolean attackHeld;
	public long attackDown;
	private int attackSeq;
	private int declaredOnDown = -1;

	// Guard.
	public boolean guardHeld;
	public long guardPress = Long.MIN_VALUE / 2;
	public int parryWindow;
	private long lastParryPress = Long.MIN_VALUE / 2;
	public GuardMath.Spec guard = GuardMath.weapon();
	public boolean parriedThisPress;
	public long whiffUntil = Long.MIN_VALUE / 2;
	public long lastBlockedHit = Long.MIN_VALUE / 2;

	// Dodge.
	public long dodgeStart = Long.MIN_VALUE / 2;
	public int iFrom, iTo;
	public long slipBonusUntil = Long.MIN_VALUE / 2;
	public boolean slipped;

	// Windows.
	public long flinchUntil = Long.MIN_VALUE / 2;
	public long riposteUntil = Long.MIN_VALUE / 2;
	public int riposteTotal;

	// Lock-on.
	public int lockTarget = -1;
	private long lockUnseenSince = Long.MIN_VALUE / 2;

	// Buffer.
	private CombatPayloads.@Nullable Input buffered;
	private long bufferedAt;

	// Art.
	public @Nullable Identifier artOverride;
	public long artReadyAt = Long.MIN_VALUE / 2;
	public int artTotal;

	public long inCombatUntil = Long.MIN_VALUE / 2;
	/** While the defender's grace replays a held hit: the tick it landed (windows are judged then). */
	public long graceLand = Long.MIN_VALUE;

	// Sync.
	public CombatPayloads.@Nullable Self lastSent;
	public long lastSentTick = Long.MIN_VALUE / 2;

	public PlayerCombat(ServerPlayer player, CombatState state) {
		this.player = player;
		this.state = state;
		this.stamina.max = this.maxStamina();
		this.stamina.value = this.stamina.max;
	}

	// ------------------------------------------------------------------ numbers (Fray's attribute, Mettle's stat, the provider)

	public float maxStamina() {
		double base = this.player.getAttributeValue(FrayAttributes.MAX_STAMINA) + MettleStats.add(this.player, "stamina_max");
		return (float) Math.min(220, Math.max(0, base));
	}

	public float regen() {
		return (float) (this.player.getAttributeValue(FrayAttributes.STAMINA_REGEN) * MettleStats.mul(this.player, "stamina_regen"));
	}

	/** An action's stamina after Mettle's cost multiplier for its group and the provider (floor: the 35 % reduction ceiling). */
	public float cost(float base, String group, Identifier action) {
		if (base <= 0) {
			return 0;
		}
		double m = group == null ? 1 : MettleStats.mul(this.player, "stamina_cost." + group);
		double v = MettleStats.balance(this.player, BalanceKey.STAMINA_COST, base * m, action);
		return (float) Math.max(base * 0.65, v);
	}

	private long lastStamp = Long.MIN_VALUE;

	/** The defender stamp of an input (ANIM_EVENTS.md §4): the client's tick, clamped by the grace and monotonic. */
	private long stamp(CombatPayloads.Input in, long now) {
		long s = dev.voidmc.fray.combat.Defense.clampStamp(in.clientTick(), now, dev.voidmc.fray.combat.Defense.rtt(this.player), this.lastStamp);
		this.lastStamp = s;
		return s;
	}

	public long now() {
		return this.player.level().getGameTime();
	}

	public boolean winded() {
		return this.stamina.winded(this.now());
	}

	public boolean inCombat() {
		return this.now() < this.inCombatUntil;
	}

	public void tagCombat(int ticks) {
		this.inCombatUntil = Math.max(this.inCombatUntil, this.now() + ticks);
	}

	/** False while staggered, dazed, frozen, stunned, flinching or in a whiff. */
	public boolean canAct(long now) {
		return this.player.isAlive() && !Statuses.INSTANCE.incapacitated(this.player) && now >= this.flinchUntil && now >= this.whiffUntil;
	}

	public boolean spend(float amount, Identifier action) {
		if (amount <= 0) {
			return true;
		}
		if (!this.stamina.canAfford(amount)) {
			return false;
		}
		boolean winded = this.stamina.spend(amount, this.now());
		this.tagCombat(100);
		CombatEvents.RESOURCE_SPENT.invoker().spent(this.player, "stamina", amount, action);
		if (winded) {
			CombatEvents.WINDED.invoker().winded(this.player);
		}
		return true;
	}

	// ------------------------------------------------------------------ input

	public void onInput(CombatPayloads.Input in) {
		long now = this.now();
		switch (in.kind()) {
			case CombatPayloads.ATTACK_DOWN -> {
				this.attackHeld = true;
				this.attackDown = this.stamp(in, now);
				this.attackSeq = in.seq();
				this.declaredOnDown = in.target();
				this.ack(in.seq(), true, (byte) -1, now, OK);
			}
			case CombatPayloads.ATTACK_UP -> {
				if (!this.attackHeld) {
					// The server already released a full charge itself (or never saw the press).
					this.ack(in.seq(), false, (byte) -1, now, BUSY);
					return;
				}
				int serverHeld = (int) (now - this.attackDown);
				int held = Mth.clamp(in.param(), serverHeld - 2, serverHeld + 2);
				this.attackHeld = false;
				this.attack(in, Math.max(0, held), now);
			}
			case CombatPayloads.GUARD_DOWN -> this.guardDown(in, now);
			case CombatPayloads.GUARD_UP -> this.guardUp(now);
			case CombatPayloads.DODGE -> this.dodge(in, now);
			case CombatPayloads.TECHNIQUE -> this.ack(in.seq(), false, (byte) -1, now, NO_TECHNIQUE);
			case CombatPayloads.ART -> this.art(in, now);
			case CombatPayloads.LOCK -> this.lock(in, now);
			default -> this.ack(in.seq(), false, (byte) -1, now, BUSY);
		}
	}

	private void ack(int seq, boolean ok, byte kind, long start, byte reason) {
		if (seq >= 0) {
			CombatSync.ack(this.player, new CombatPayloads.Ack(seq, ok, kind, start, reason));
		}
	}

	private void buffer(CombatPayloads.Input in, long now) {
		this.buffered = in;
		this.bufferedAt = now;
	}

	// ------------------------------------------------------------------ attacks

	private void attack(CombatPayloads.Input in, int held, long now) {
		Moveset ms = Movesets.of(Weapons.family(this.player.getMainHandItem()));
		HoldMath.Kind kind = HoldMath.classify(held);
		if (kind != HoldMath.Kind.LIGHT && this.winded()) {
			kind = HoldMath.Kind.LIGHT;
		}
		Run cur = this.run;
		if (cur != null) {
			int k = (int) (now - cur.start);
			boolean chain = cur.kind.attack() && cur.phases.chainOpen(k);
			boolean rollAttack = (cur.kind == ActionKind.DODGE || cur.kind == ActionKind.BACKSTEP) && k >= cur.phases.total() - 3;
			if (!chain && !rollAttack) {
				this.buffer(new CombatPayloads.Input(CombatPayloads.ATTACK_UP, in.seq(), in.clientTick(), in.yaw(), in.pitch(), in.target(), held), now);
				this.ack(in.seq(), true, (byte) -1, now, BUFFERED);
				return;
			}
			if (rollAttack && kind == HoldMath.Kind.LIGHT) {
				this.end(CombatEvents.ActionOutcome.CANCELLED);
				this.startAttack(in.seq(), ActionKind.ROLL_ATTACK, Movesets.ROLL_ATTACK, 0, 0, in.target(), now);
				return;
			}
			this.end(CombatEvents.ActionOutcome.CANCELLED);
		}
		if (!this.canAct(now)) {
			this.ack(in.seq(), false, (byte) -1, now, CANT_ACT);
			return;
		}
		int declared = in.target() >= 0 ? in.target() : this.declaredOnDown;
		switch (kind) {
			case LIGHT -> this.light(in.seq(), ms, declared, now);
			case HEAVY -> this.startAttack(in.seq(), ActionKind.HEAVY, ms.heavy(), 0, 0, declared, now);
			case CHARGED -> {
				float charge = HoldMath.charge(held, ms.chargeTicks(), MettleStats.mul(this.player, "charge_speed"));
				ActionDef d = ms.heavy();
				float cost = HoldMath.chargedCost(d.stamina(), ms.chargedStaminaMax(), charge);
				this.startAttack(in.seq(), ActionKind.CHARGED, new ActionDef(d.phases(), 1.0F, cost, d.reach(), d.arc(), d.slow(), d.step() + 0.2F), 0, charge,
					declared, now);
			}
		}
	}

	private void light(int seq, Moveset ms, int declared, long now) {
		ActionDef base = ms.light(0);
		if (now < this.riposteUntil) {
			this.riposteUntil = Long.MIN_VALUE / 2;
			this.startAttack(seq, ActionKind.RIPOSTE, base, 0, 0, declared, now);
			return;
		}
		if (!this.player.onGround() && this.player.fallDistance >= 1.5 && !this.player.isInWater()) {
			this.startAttack(seq, ActionKind.PLUNGE, base, 0, 0, declared, now);
			return;
		}
		if (now - this.lastBlockedHit <= GuardMath.COUNTER_TICKS) {
			this.lastBlockedHit = Long.MIN_VALUE / 2;
			this.startAttack(seq, ActionKind.GUARD_COUNTER, Movesets.GUARD_COUNTER, 0, 0, declared, now);
			return;
		}
		LivingEntity exec = this.executeTarget(declared, base.reach());
		if (exec != null) {
			this.startAttack(seq, ActionKind.EXECUTE, Movesets.EXECUTE, 0, 0, exec.getId(), now);
			return;
		}
		if (this.player.isSprinting()) {
			this.startAttack(seq, ActionKind.RUNNING, new ActionDef(base.phases(), 1.0F, base.stamina(), base.reach(), base.arc(), 1.0F, 1.5F), 0, 0, declared, now);
			return;
		}
		int combo = now <= this.comboOpenUntil ? this.lastCombo + 1 : 0;
		if (combo >= ms.lights().size()) {
			combo = 0;
		}
		this.startAttack(seq, ActionKind.LIGHT, ms.light(combo), combo, 0, declared, now);
	}

	/** A Staggered elite (or a Broken boss) at melee range: the light becomes an Execute. */
	private @Nullable LivingEntity executeTarget(int declared, float reach) {
		Entity e = declared >= 0 ? this.player.level().getEntity(declared) : this.lockTarget >= 0 ? this.player.level().getEntity(this.lockTarget) : null;
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.distanceTo(this.player) > reach + 1) {
			return null;
		}
		CombatState s = CombatState.peek(le);
		if (s == null) {
			return null;
		}
		boolean staggeredElite = s.rank == Rank.ELITE && s.has(StatusType.STAGGER, this.now());
		boolean brokenBoss = le instanceof Mob m && Encounters.INSTANCE.of(m) != null && Encounters.INSTANCE.of(m).broken();
		return staggeredElite || brokenBoss ? le : null;
	}

	private void startAttack(int seq, ActionKind kind, ActionDef def, int combo, float charge, int declared, long now) {
		String group = switch (kind) {
			case HEAVY, EXECUTE -> "heavy";
			case CHARGED -> "charged";
			default -> null;
		};
		float cost = this.cost(def.stamina(), group, kind.id());
		if (kind != ActionKind.LIGHT && kind != ActionKind.RUNNING && kind != ActionKind.RIPOSTE && this.winded() && kind != ActionKind.PLUNGE) {
			this.ack(seq, false, (byte) kind.ordinal(), now, WINDED);
			return;
		}
		if (!this.stamina.canAfford(cost)) {
			this.ack(seq, false, (byte) kind.ordinal(), now, NO_STAMINA);
			return;
		}
		if (!CombatEvents.BEFORE_ACTION.invoker().allow(this.player, kind.id())) {
			this.ack(seq, false, (byte) kind.ordinal(), now, BUSY);
			return;
		}
		this.spend(cost, kind.id());
		Run r = new Run();
		r.kind = kind;
		r.def = def;
		double speed = MettleStats.balance(this.player, BalanceKey.ACTION_SPEED, 1.0, kind.id());
		r.phases = def.phases().speed(speed);
		r.start = now;
		r.combo = combo;
		r.variant = combo;
		r.charge = charge;
		r.declared = declared;
		r.slipBonus = now < this.slipBonusUntil;
		if (r.slipBonus) {
			this.slipBonusUntil = Long.MIN_VALUE / 2;
		}
		if (kind == ActionKind.LIGHT) {
			this.lastCombo = combo;
		} else {
			this.lastCombo = -1;
		}
		this.begin(r, seq);
	}

	private void begin(Run r, int seq) {
		this.run = r;
		ACTIONS.add(r.start + " " + this.player.getName().getString() + " " + r.kind.name() + (r.kind == ActionKind.LIGHT ? " " + r.combo : ""));
		this.ack(seq, true, (byte) r.kind.ordinal(), r.start, OK);
		// With the stage 1 motion runtime the clip runs on Fray Motion (defender windows, trackers); else our own packet.
		Identifier clip = ActionClips.pick(r.kind, r.variant, id -> dev.voidmc.fray.motion.MotionServer.timeline(id) != null);
		long defender = r.kind == ActionKind.DODGE || r.kind == ActionKind.BACKSTEP ? this.dodgeStart : r.start;
		if (clip == null || !dev.voidmc.fray.combat.MotionBridge.startPlayerAction(this.player, clip, defender)) {
			CombatSync.actionPlay(this.player, r.kind, r.variant, r.start, ActionKind.clipSpeed(r.kind, r.phases.windup()));
		}
		CombatEvents.ACTION_STARTED.invoker().on(this.player, r.kind.id(), CombatEvents.ActionOutcome.DONE);
		this.applySlow(r, 0);
	}

	public void end(CombatEvents.ActionOutcome outcome) {
		Run r = this.run;
		if (r == null) {
			return;
		}
		this.run = null;
		if (r.kind == ActionKind.LIGHT) {
			this.comboOpenUntil = this.now() + 2;
		}
		this.removeSlow();
		CombatEvents.ACTION_ENDED.invoker().on(this.player, r.kind.id(), outcome);
		if (outcome == CombatEvents.ActionOutcome.INTERRUPTED) {
			CombatSync.actionPlay(this.player, null, 0, this.now(), 1);
		}
	}

	public boolean interrupt(InterruptReason reason) {
		boolean had = this.run != null;
		this.end(CombatEvents.ActionOutcome.INTERRUPTED);
		this.attackHeld = false;
		if (reason == InterruptReason.FLINCH) {
			this.flinchUntil = this.now() + dev.voidmc.fray.combat.Poise.FLINCH_TICKS;
		}
		return had;
	}

	private void applySlow(Run r, int k) {
		AttributeInstance speed = this.player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		Phases.Phase phase = r.phases.at(k);
		float slow = r.def.slow();
		if ((phase == Phases.Phase.WINDUP || phase == Phases.Phase.ACTIVE) && slow < 1) {
			AttributeModifier m = speed.getModifier(SLOW);
			if (m == null || m.amount() != slow - 1) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW, slow - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			this.removeSlow();
		}
	}

	private void removeSlow() {
		AttributeInstance speed = this.player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.hasModifier(SLOW)) {
			speed.removeModifier(SLOW);
		}
	}

	// ------------------------------------------------------------------ guard

	private void guardDown(CombatPayloads.Input in, long now) {
		Run cur = this.run;
		if (cur != null) {
			int k = (int) (now - cur.start);
			if ((cur.kind == ActionKind.HEAVY || cur.kind == ActionKind.CHARGED) && cur.phases.at(k) == Phases.Phase.WINDUP && cur.phases.feint(k)) {
				this.spend(this.cost(GuardMath.FEINT_COST, null, ActionKind.PARRY.id()), Fray.id("player/feint"));
				this.end(CombatEvents.ActionOutcome.CANCELLED);
			} else if (cur.phases.guardCancel(k) || cur.kind == ActionKind.DODGE || cur.kind == ActionKind.BACKSTEP && k >= cur.phases.total() - 2) {
				this.end(CombatEvents.ActionOutcome.CANCELLED);
			} else {
				this.buffer(in, now);
				this.ack(in.seq(), true, (byte) -1, now, BUFFERED);
				return;
			}
		}
		if (!this.canAct(now)) {
			this.ack(in.seq(), false, (byte) ActionKind.PARRY.ordinal(), now, CANT_ACT);
			return;
		}
		boolean shield = Weapons.hasShield(this.player);
		this.guard = shield ? GuardMath.shield(1.3F) : GuardMath.weapon();
		boolean spammed = now - this.lastParryPress < GuardMath.SPAM_TICKS;
		this.lastParryPress = now;
		this.guardPress = this.stamp(in, now);
		this.guardHeld = true;
		this.parriedThisPress = false;
		float cost = this.cost(GuardMath.PARRY_COST, "parry", ActionKind.PARRY.id());
		if (this.winded() || !this.stamina.canAfford(cost)) {
			this.parryWindow = 0;
		} else {
			this.spend(cost, ActionKind.PARRY.id());
			int bonus = (int) Math.round(MettleStats.add(this.player, "parry_window") / 50.0);
			int base = (int) Math.round(MettleStats.balance(this.player, BalanceKey.PARRY_WINDOW, this.guard.parryWindow(), null));
			this.parryWindow = GuardMath.window(base, bonus, spammed);
		}
		this.ack(in.seq(), true, (byte) ActionKind.PARRY.ordinal(), now, OK);
		CombatSync.actionPlay(this.player, ActionKind.PARRY, 0, now, 1);
	}

	private void guardUp(long now) {
		if (!this.guardHeld) {
			return;
		}
		this.guardHeld = false;
		long since = now - this.guardPress;
		if (!this.parriedThisPress && this.parryWindow > 0 && since <= this.parryWindow + 2) {
			this.whiffUntil = Math.max(now, this.guardPress + this.parryWindow) + GuardMath.WHIFF_TICKS;
		}
	}

	/** True while a press's parry window is open or the block is held. */
	public boolean guarding(long at) {
		return this.guardHeld || GuardMath.inParry((int) (at - this.guardPress), this.parryWindow);
	}

	// ------------------------------------------------------------------ dodge

	private void dodge(CombatPayloads.Input in, long now) {
		Run cur = this.run;
		boolean cancel = false;
		if (cur != null) {
			int k = (int) (now - cur.start);
			if (cur.kind.attack() && cur.phases.dodgeCancel(k)) {
				cancel = true;
			} else {
				this.buffer(in, now);
				this.ack(in.seq(), true, (byte) -1, now, BUFFERED);
				return;
			}
		}
		if (!this.canAct(now) || Statuses.INSTANCE.has(this.player, StatusType.ROOTED) || Statuses.INSTANCE.has(this.player, StatusType.FROZEN)) {
			this.ack(in.seq(), false, (byte) ActionKind.DODGE.ordinal(), now, CANT_ACT);
			return;
		}
		if (this.winded()) {
			this.ack(in.seq(), false, (byte) ActionKind.DODGE.ordinal(), now, WINDED);
			return;
		}
		if (!this.player.onGround() || this.player.isSwimming() || this.player.onClimbable() || this.player.isFallFlying()) {
			this.ack(in.seq(), false, (byte) ActionKind.DODGE.ordinal(), now, AIRBORNE);
			return;
		}
		boolean roll = in.param() != 0;
		ActionKind kind = roll ? ActionKind.DODGE : ActionKind.BACKSTEP;
		ActionDef def = roll ? Movesets.ROLL : Movesets.BACKSTEP;
		float base = roll ? Movesets.DODGE_COST[Weapons.armourClass(this.player)] : def.stamina();
		float cost = this.cost(base, "dodge", kind.id()) + (cancel ? Movesets.DODGE_CANCEL_COST : 0);
		if (!this.stamina.canAfford(cost)) {
			this.ack(in.seq(), false, (byte) kind.ordinal(), now, NO_STAMINA);
			return;
		}
		if (cancel) {
			this.end(CombatEvents.ActionOutcome.CANCELLED);
		}
		if (!CombatEvents.BEFORE_ACTION.invoker().allow(this.player, kind.id())) {
			this.ack(in.seq(), false, (byte) kind.ordinal(), now, BUSY);
			return;
		}
		this.spend(cost, kind.id());
		this.player.stopUsingItem();
		this.guardHeld = false;
		this.attackHeld = false;
		Run r = new Run();
		r.kind = kind;
		r.def = def;
		r.phases = def.phases();
		r.start = now;
		r.variant = in.param();
		this.dodgeStart = this.stamp(in, now);
		int bonus = (int) Math.round(MettleStats.add(this.player, "dodge_iframes") / 50.0);
		int to = roll ? 7 : 4;
		int extra = (int) Math.round(MettleStats.balance(this.player, BalanceKey.IFRAME_TICKS, to, kind.id())) - to + bonus;
		this.iFrom = 1;
		this.iTo = Math.min(10, to + Math.max(0, extra));
		this.slipped = false;
		this.begin(r, in.seq());
	}

	/** Inside the dodge's i-frames at tick {@code at} (lag allowance included). */
	public boolean invulnerable(long at) {
		long k = at - this.dodgeStart;
		return k >= this.iFrom && k <= this.iTo;
	}

	/** Inside the first three i-frame ticks: a hit here is a Slip. */
	public boolean perfect(long at) {
		long k = at - this.dodgeStart;
		return k >= this.iFrom && k < this.iFrom + Movesets.SLIP_TICKS;
	}

	// ------------------------------------------------------------------ Arts

	public @Nullable Identifier art() {
		return this.artOverride != null ? this.artOverride : Actions.INSTANCE.provider().art(this.player);
	}

	private void art(CombatPayloads.Input in, long now) {
		Identifier art = this.art();
		ActionKind kind = art == null ? null : Arts.kind(art);
		if (kind == null) {
			this.ack(in.seq(), false, (byte) -1, now, NO_ART);
			return;
		}
		if (now < this.artReadyAt) {
			this.ack(in.seq(), false, (byte) kind.ordinal(), now, COOLDOWN);
			return;
		}
		if (this.run != null) {
			int k = (int) (now - this.run.start);
			if (this.run.kind.attack() && this.run.phases.chainOpen(k)) {
				this.end(CombatEvents.ActionOutcome.CANCELLED);
			} else {
				this.buffer(in, now);
				this.ack(in.seq(), true, (byte) -1, now, BUFFERED);
				return;
			}
		}
		if (!this.canAct(now)) {
			this.ack(in.seq(), false, (byte) kind.ordinal(), now, CANT_ACT);
			return;
		}
		ActionDef def = Arts.def(kind);
		int before = this.run == null ? 0 : 1;
		this.startAttack(in.seq(), kind, def, 0, 0, in.target(), now);
		if (this.run != null && this.run.kind == kind && before == 0) {
			int cd = Arts.cooldown(kind);
			double scale = MettleStats.mul(this.player, "cooldown_scale");
			double v = MettleStats.balance(this.player, BalanceKey.TECHNIQUE_COOLDOWN, cd * scale, art);
			this.artTotal = (int) Math.max(cd * 0.6, v);
			this.artReadyAt = now + this.artTotal;
		}
	}

	// ------------------------------------------------------------------ lock-on

	private void lock(CombatPayloads.Input in, long now) {
		if (in.target() < 0) {
			this.lockTarget = -1;
			this.ack(in.seq(), true, (byte) -1, now, OK);
			return;
		}
		Entity e = this.player.level().getEntity(in.target());
		boolean ranged = Weapons.family(this.player.getMainHandItem()) == WeaponFamily.BOW;
		double range = (ranged ? 48 : 20) + 2;
		if (!(e instanceof LivingEntity le) || e == this.player || !le.isAlive() || le.distanceTo(this.player) > range || !this.player.hasLineOfSight(le)) {
			this.ack(in.seq(), false, (byte) -1, now, BAD_TARGET);
			return;
		}
		this.lockTarget = le.getId();
		this.lockUnseenSince = Long.MIN_VALUE / 2;
		this.tagCombat(100);
		this.ack(in.seq(), true, (byte) -1, now, OK);
	}

	public @Nullable LivingEntity lockEntity() {
		return this.lockTarget >= 0 && this.player.level().getEntity(this.lockTarget) instanceof LivingEntity le ? le : null;
	}

	// ------------------------------------------------------------------ tick

	public void tick(ServerLevel level, long now) {
		this.stamina.max = this.maxStamina();
		boolean wasWinded = this.stamina.winded(now - 1);
		this.stamina.tick(now, this.regen(), this.guardHeld);
		if (wasWinded && !this.stamina.winded(now)) {
			this.lastSentTick = Long.MIN_VALUE / 2;
		}
		PoiseMeter poise = this.state.poise;
		poise.max = dev.voidmc.fray.combat.Poise.max(this.player, Rank.COMMON);
		poise.tick(now, dev.voidmc.fray.combat.Poise.PLAYER_REFILL);

		// A held attack charging: full charge drains stamina and releases itself after 40 ticks.
		if (this.attackHeld && this.run == null) {
			Moveset ms = Movesets.of(Weapons.family(this.player.getMainHandItem()));
			int held = (int) (now - this.attackDown);
			double cs = MettleStats.mul(this.player, "charge_speed");
			if (held >= HoldMath.CHARGE_HOLD && HoldMath.charge(held, ms.chargeTicks(), cs) >= 1) {
				float drain = this.cost(Movesets.CHARGE_HOLD_DRAIN, "charged", ActionKind.CHARGED.id()) / 20F;
				this.stamina.spend(drain, now);
				if (HoldMath.autoRelease(held, ms.chargeTicks(), cs) || this.stamina.value <= 0) {
					this.attackHeld = false;
					this.attack(new CombatPayloads.Input(CombatPayloads.ATTACK_UP, -1, now, this.player.getYRot(), this.player.getXRot(), this.declaredOnDown, held),
						held, now);
				}
			}
		}

		Run r = this.run;
		if (r != null) {
			int k = (int) (now - r.start);
			this.applySlow(r, k);
			if (r.phases.active(k) && r.kind.attack()) {
				if (!r.aimed) {
					r.aimed = true;
					r.yaw = Sweep.aimYaw(this, r);
				}
				Sweep.sweep(this, r, k, level);
			}
			if (this.run == r && k >= r.phases.total() - 1) {
				this.end(CombatEvents.ActionOutcome.DONE);
			}
		}

		if (this.buffered != null) {
			CombatPayloads.Input b = this.buffered;
			if (now - this.bufferedAt > HoldMath.BUFFER) {
				this.buffered = null;
			} else if (this.run == null || this.windowOpenFor(b, now)) {
				this.buffered = null;
				if (b.kind() == CombatPayloads.ATTACK_UP) {
					this.attack(b, b.param(), now);
				} else {
					this.onInput(b);
				}
			}
		}

		if (this.lockTarget >= 0) {
			LivingEntity t = this.lockEntity();
			boolean ranged = Weapons.family(this.player.getMainHandItem()) == WeaponFamily.BOW;
			if (t == null || !t.isAlive() || t.distanceTo(this.player) > (ranged ? 60 : 30)) {
				this.lockTarget = -1;
			} else if (!this.player.hasLineOfSight(t)) {
				if (this.lockUnseenSince < 0) {
					this.lockUnseenSince = now;
				} else if (now - this.lockUnseenSince > 40) {
					this.lockTarget = -1;
				}
			} else {
				this.lockUnseenSince = Long.MIN_VALUE / 2;
			}
		}

		if (now % 20 == 0) {
			AABB box = this.player.getBoundingBox().inflate(24);
			for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == this.player)) {
				this.tagCombat(100);
				break;
			}
		}
		CombatSync.self(this, now);
	}

	private boolean windowOpenFor(CombatPayloads.Input b, long now) {
		Run r = this.run;
		if (r == null) {
			return true;
		}
		int k = (int) (now - r.start);
		return switch (b.kind()) {
			case CombatPayloads.ATTACK_UP, CombatPayloads.ART -> r.kind.attack() && r.phases.chainOpen(k)
				|| (r.kind == ActionKind.DODGE || r.kind == ActionKind.BACKSTEP) && k >= r.phases.total() - 3;
			case CombatPayloads.DODGE -> r.kind.attack() && r.phases.dodgeCancel(k);
			case CombatPayloads.GUARD_DOWN -> r.phases.guardCancel(k);
			default -> false;
		};
	}
}
