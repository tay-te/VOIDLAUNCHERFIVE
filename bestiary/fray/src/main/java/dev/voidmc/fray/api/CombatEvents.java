package dev.voidmc.fray.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Fray's combat events (CONTRACTS.md §10), fired on the server thread. Order: {@link #DAMAGE_DEALT} after vanilla's
 * AFTER_DAMAGE, {@link #KILLED} after AFTER_DEATH.
 *
 * @since 0.1.0 (2026-10-21)
 */
public final class CombatEvents {
	private CombatEvents() {
	}

	public enum ActionOutcome {
		DONE, INTERRUPTED, CANCELLED
	}

	/** The pipeline stage a {@link #MODIFY_DAMAGE} listener is called for. */
	public enum Stage {
		ATTACKER, SITUATIONAL, DEFENCE
	}

	/** Mutable view a MODIFY_DAMAGE listener adds to (pools per stage, CONTRACTS.md §4.2). */
	public interface DamageBuilder {
		Stage stage();

		/** Flat damage added to a kind before the multipliers (ATTACKER). */
		void add(DamageKind kind, float amount);

		/** Summed percentage pool (0.15 = +15 %), all kinds when {@code kind} is null. */
		void increased(@Nullable DamageKind kind, float fraction);

		/** A multiplier of its own (each one multiplies). */
		void more(@Nullable DamageKind kind, float factor);

		/** A stage-5 reduction (Iron, Warden): 0.2 = 20 % less, after armour, inside the incoming ceiling. */
		void reduce(float fraction);
	}

	public interface BeforeAction {
		/** False cancels the action. */
		boolean allow(LivingEntity entity, Identifier action);
	}

	public interface ActionEvent {
		void on(LivingEntity entity, Identifier action, ActionOutcome outcome);
	}

	public interface ModifyDamage {
		void modify(LivingEntity target, HitContext hit, DamageBuilder builder);
	}

	public interface DamageDealt {
		void dealt(@Nullable Entity attacker, LivingEntity target, HitContext hit, DamageBreakdown result);
	}

	public interface Guarded {
		void guarded(LivingEntity defender, @Nullable Entity attacker, GuardOutcome kind, float amount);
	}

	public interface Dodged {
		void dodged(LivingEntity entity, Identifier action, boolean perfect);
	}

	public interface Staggered {
		void staggered(LivingEntity entity, @Nullable Entity by, int ticks);
	}

	public interface StatusBuildUp {
		void buildUp(LivingEntity target, StatusType status, float amount, @Nullable Entity source);
	}

	public interface ResourceSpent {
		void spent(ServerPlayer player, String resource, float amount, Identifier action);
	}

	public interface Winded {
		void winded(ServerPlayer player);
	}

	public interface Killed {
		void killed(@Nullable Entity killer, LivingEntity victim, @Nullable HitContext hit, Rank rank);
	}

	/** @since 0.2.0 (2026-10-22) */
	public interface AllowStatus {
		/** False refuses the status (an Ironbound plate, an immunity); checked before it is applied. */
		boolean allow(LivingEntity target, StatusType status, int ticks, @Nullable Entity source);
	}

	public interface EncounterEvent {
		void on(Encounter encounter, Encounter.@Nullable Outcome outcome);
	}

	public static final Event<BeforeAction> BEFORE_ACTION = EventFactory.createArrayBacked(BeforeAction.class, ls -> (e, a) -> {
		for (BeforeAction l : ls) {
			if (!l.allow(e, a)) {
				return false;
			}
		}
		return true;
	});
	public static final Event<ActionEvent> ACTION_STARTED = EventFactory.createArrayBacked(ActionEvent.class, ls -> (e, a, o) -> {
		for (ActionEvent l : ls) {
			l.on(e, a, o);
		}
	});
	public static final Event<ActionEvent> ACTION_ENDED = EventFactory.createArrayBacked(ActionEvent.class, ls -> (e, a, o) -> {
		for (ActionEvent l : ls) {
			l.on(e, a, o);
		}
	});
	public static final Event<ModifyDamage> MODIFY_DAMAGE = EventFactory.createArrayBacked(ModifyDamage.class, ls -> (t, h, b) -> {
		for (ModifyDamage l : ls) {
			l.modify(t, h, b);
		}
	});
	public static final Event<DamageDealt> DAMAGE_DEALT = EventFactory.createArrayBacked(DamageDealt.class, ls -> (a, t, h, r) -> {
		for (DamageDealt l : ls) {
			l.dealt(a, t, h, r);
		}
	});
	public static final Event<Guarded> GUARDED = EventFactory.createArrayBacked(Guarded.class, ls -> (d, a, k, n) -> {
		for (Guarded l : ls) {
			l.guarded(d, a, k, n);
		}
	});
	public static final Event<Dodged> DODGED = EventFactory.createArrayBacked(Dodged.class, ls -> (e, a, p) -> {
		for (Dodged l : ls) {
			l.dodged(e, a, p);
		}
	});
	public static final Event<Staggered> STAGGERED = EventFactory.createArrayBacked(Staggered.class, ls -> (e, b, t) -> {
		for (Staggered l : ls) {
			l.staggered(e, b, t);
		}
	});
	public static final Event<Staggered> POISE_BROKEN = EventFactory.createArrayBacked(Staggered.class, ls -> (e, b, t) -> {
		for (Staggered l : ls) {
			l.staggered(e, b, t);
		}
	});
	public static final Event<StatusBuildUp> STATUS_BUILDUP = EventFactory.createArrayBacked(StatusBuildUp.class, ls -> (t, s, a, src) -> {
		for (StatusBuildUp l : ls) {
			l.buildUp(t, s, a, src);
		}
	});
	public static final Event<StatusBuildUp> STATUS_PROC = EventFactory.createArrayBacked(StatusBuildUp.class, ls -> (t, s, a, src) -> {
		for (StatusBuildUp l : ls) {
			l.buildUp(t, s, a, src);
		}
	});
	public static final Event<ResourceSpent> RESOURCE_SPENT = EventFactory.createArrayBacked(ResourceSpent.class, ls -> (p, r, a, act) -> {
		for (ResourceSpent l : ls) {
			l.spent(p, r, a, act);
		}
	});
	public static final Event<Winded> WINDED = EventFactory.createArrayBacked(Winded.class, ls -> p -> {
		for (Winded l : ls) {
			l.winded(p);
		}
	});
	public static final Event<Killed> KILLED = EventFactory.createArrayBacked(Killed.class, ls -> (k, v, h, r) -> {
		for (Killed l : ls) {
			l.killed(k, v, h, r);
		}
	});
	/**
	 * A creature's guard (stage 4 for non-players): see {@link CreatureGuard}.
	 *
	 * @since 0.2.0 (2026-10-22)
	 */
	public static final Event<CreatureGuard> CREATURE_GUARD = EventFactory.createArrayBacked(CreatureGuard.class, ls -> (d, a, h, f) -> {
		for (CreatureGuard l : ls) {
			CreatureGuard.Result r = l.guard(d, a, h, f);
			if (r.outcome() != GuardOutcome.NONE) {
				return r;
			}
		}
		return CreatureGuard.Result.NONE;
	});
	/**
	 * Before a status is applied (STAGGER, ROOTED, ...); any listener may refuse it.
	 *
	 * @since 0.2.0 (2026-10-22)
	 */
	public static final Event<AllowStatus> ALLOW_STATUS = EventFactory.createArrayBacked(AllowStatus.class, ls -> (t, s, k, src) -> {
		for (AllowStatus l : ls) {
			if (!l.allow(t, s, k, src)) {
				return false;
			}
		}
		return true;
	});
	public static final Event<EncounterEvent> ENCOUNTER_STARTED = EventFactory.createArrayBacked(EncounterEvent.class, ls -> (e, o) -> {
		for (EncounterEvent l : ls) {
			l.on(e, o);
		}
	});
	public static final Event<EncounterEvent> ENCOUNTER_PHASE = EventFactory.createArrayBacked(EncounterEvent.class, ls -> (e, o) -> {
		for (EncounterEvent l : ls) {
			l.on(e, o);
		}
	});
	public static final Event<EncounterEvent> ENCOUNTER_ENDED = EventFactory.createArrayBacked(EncounterEvent.class, ls -> (e, o) -> {
		for (EncounterEvent l : ls) {
			l.on(e, o);
		}
	});
}
