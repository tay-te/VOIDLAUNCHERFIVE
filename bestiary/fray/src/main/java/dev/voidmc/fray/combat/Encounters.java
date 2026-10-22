package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.Encounter;
import dev.voidmc.fray.api.EncounterService;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.net.CombatPayloads;
import dev.voidmc.fray.combat.net.CombatSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * {@link EncounterService}: boss fights with Fray's own bar (H6) and banners (H7). Participants are the players within
 * 48 blocks of the boss; the bar goes to them on change, at most 4 times a second. The Break meter fills from poise
 * damage, parries (+8 %) and weak points; full = Broken for 60 ticks (the boss can't act, Executes land).
 */
public final class Encounters implements EncounterService {
	public static final Encounters INSTANCE = new Encounters();
	public static final double RANGE = 48;
	public static final int BROKEN_TICKS = 60;

	private final Map<Mob, Impl> byBoss = new IdentityHashMap<>();
	private int nextId = 1;

	private Encounters() {
	}

	final class Impl implements Encounter {
		final int id;
		final Mob boss;
		final Component name;
		final @Nullable Component subtitle;
		final float[] thresholds;
		int phase;
		float breakMeter;
		long brokenUntil = Long.MIN_VALUE / 2;
		final Map<UUID, Float> damage = new HashMap<>();
		final Set<ServerPlayer> viewers = new HashSet<>();
		long lastSent = Long.MIN_VALUE / 2;
		boolean dirty = true;
		float sentHealth = -1;
		@Nullable String firstToFell;

		Impl(int id, Mob boss, Component name, @Nullable Component subtitle, float[] thresholds) {
			this.id = id;
			this.boss = boss;
			this.name = name;
			this.subtitle = subtitle;
			this.thresholds = thresholds.clone();
		}

		@Override
		public int id() {
			return this.id;
		}

		@Override
		public Mob boss() {
			return this.boss;
		}

		@Override
		public Component name() {
			return this.name;
		}

		@Override
		public int phase() {
			return this.phase;
		}

		@Override
		public float[] phaseThresholds() {
			return this.thresholds.clone();
		}

		@Override
		public float breakMeter() {
			return this.breakMeter;
		}

		@Override
		public boolean broken() {
			return this.boss.level().getGameTime() < this.brokenUntil;
		}

		@Override
		public Map<UUID, Float> participants() {
			return Map.copyOf(this.damage);
		}

		CombatPayloads.EncounterBar bar(long now) {
			List<Float> notches = new ArrayList<>();
			for (float t : this.thresholds) {
				notches.add(t);
			}
			CombatState s = CombatState.of(this.boss);
			return new CombatPayloads.EncounterBar(this.id, this.boss.getId(), this.name, Optional.ofNullable(this.subtitle),
				this.boss.getHealth() / Math.max(1, this.boss.getMaxHealth()), this.phase, notches, this.breakMeter, (int) Math.max(0, this.brokenUntil - now),
				s.meters.bytes());
		}
	}

	@Override
	public Encounter start(Mob boss, Component name, @Nullable Component subtitle, float[] phaseThresholds) {
		Impl old = this.byBoss.get(boss);
		if (old != null) {
			return old;
		}
		Impl e = new Impl(this.nextId++, boss, name, subtitle, phaseThresholds);
		this.byBoss.put(boss, e);
		Profiles.INSTANCE.set(boss, Rank.BOSS, subtitle, List.of());
		CombatEvents.ENCOUNTER_STARTED.invoker().on(e, null);
		if (boss.level() instanceof ServerLevel level) {
			CombatPayloads.Banner banner = new CombatPayloads.Banner(CombatPayloads.BANNER_START, name,
				subtitle == null ? Component.empty() : Component.literal(subtitle.getString().toUpperCase(Locale.ROOT)));
			for (ServerPlayer p : level.players()) {
				if (p.distanceTo(boss) <= RANGE) {
					ServerPlayNetworking.send(p, banner);
				}
			}
		}
		return e;
	}

	@Override
	public @Nullable Encounter of(Mob boss) {
		return this.byBoss.get(boss);
	}

	/** True while this boss is Broken. */
	public boolean broken(LivingEntity boss) {
		Impl e = boss instanceof Mob m ? this.byBoss.get(m) : null;
		return e != null && e.broken();
	}

	@Override
	public void addBreak(Mob boss, float fraction) {
		Impl e = this.byBoss.get(boss);
		if (e == null || e.broken() || fraction <= 0) {
			return;
		}
		e.breakMeter = Math.min(1, e.breakMeter + fraction);
		e.dirty = true;
		if (e.breakMeter >= 1) {
			long now = boss.level().getGameTime();
			e.brokenUntil = now + BROKEN_TICKS;
			Statuses.INSTANCE.apply(boss, StatusType.STAGGER, BROKEN_TICKS, null);
			CombatEvents.POISE_BROKEN.invoker().staggered(boss, null, BROKEN_TICKS);
		}
	}

	void damage(Mob boss, ServerPlayer p, float amount) {
		Impl e = this.byBoss.get(boss);
		if (e != null && amount > 0) {
			e.damage.merge(p.getUUID(), amount, Float::sum);
			e.dirty = true;
		}
	}

	@Override
	public void end(Mob boss, Encounter.Outcome outcome) {
		Impl e = this.byBoss.remove(boss);
		if (e == null) {
			return;
		}
		CombatEvents.ENCOUNTER_ENDED.invoker().on(e, outcome);
		for (ServerPlayer p : e.viewers) {
			ServerPlayNetworking.send(p, removal(e));
			if (outcome == Encounter.Outcome.VICTORY) {
				String first = e.firstToFell == null ? "" : "  ·  FIRST TO FELL IT: " + e.firstToFell.toUpperCase(Locale.ROOT);
				ServerPlayNetworking.send(p, new CombatPayloads.Banner(CombatPayloads.BANNER_FELLED, Component.translatable("fray.encounter.felled"),
					Component.literal(e.name.getString().toUpperCase(Locale.ROOT) + first)));
			}
		}
		e.viewers.clear();
	}

	private static CombatPayloads.EncounterBar removal(Impl e) {
		return new CombatPayloads.EncounterBar(e.id, -1, e.name, Optional.empty(), 0, 0, List.of(), 0, 0, new byte[0]);
	}

	@Override
	public List<Encounter> active() {
		return List.copyOf(this.byBoss.values());
	}

	@Override
	public boolean inBossFight(ServerPlayer player) {
		for (Impl e : this.byBoss.values()) {
			if (e.viewers.contains(player)) {
				return true;
			}
		}
		return false;
	}

	/** The killer's name, for the Felled banner ("first to fell it"). */
	public void killed(Mob boss, @Nullable ServerPlayer killer) {
		Impl e = this.byBoss.get(boss);
		if (e != null) {
			if (killer != null) {
				e.firstToFell = killer.getName().getString();
			}
			this.end(boss, Encounter.Outcome.VICTORY);
		}
	}

	public void clear() {
		this.byBoss.clear();
	}

	public void tick(ServerLevel level) {
		if (this.byBoss.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Impl e : List.copyOf(this.byBoss.values())) {
			if (e.boss.level() != level) {
				continue;
			}
			if (e.boss.isRemoved() || !e.boss.isAlive()) {
				this.end(e.boss, e.boss.isAlive() ? Encounter.Outcome.RESET : Encounter.Outcome.VICTORY);
				continue;
			}
			float health = e.boss.getHealth() / Math.max(1, e.boss.getMaxHealth());
			int phase = 0;
			for (float t : e.thresholds) {
				if (health <= t) {
					phase++;
				}
			}
			if (phase != e.phase) {
				e.phase = phase;
				e.dirty = true;
				CombatEvents.ENCOUNTER_PHASE.invoker().on(e, null);
			}
			if (e.brokenUntil != Long.MIN_VALUE / 2 && now >= e.brokenUntil) {
				e.brokenUntil = Long.MIN_VALUE / 2;
				e.breakMeter = 0;
				e.dirty = true;
			}
			if (Math.abs(health - e.sentHealth) > 0.001F) {
				e.dirty = true;
			}
			CombatState s = CombatState.peek(e.boss);
			if (s != null && s.metersDirty) {
				e.dirty = true;
			}
			Set<ServerPlayer> now48 = new HashSet<>();
			for (ServerPlayer p : level.players()) {
				if (p.isAlive() && p.distanceTo(e.boss) <= RANGE) {
					now48.add(p);
				}
			}
			for (ServerPlayer gone : e.viewers) {
				if (!now48.contains(gone)) {
					ServerPlayNetworking.send(gone, removal(e));
				}
			}
			boolean joined = !e.viewers.containsAll(now48);
			e.viewers.retainAll(now48);
			if (joined || e.dirty && now - e.lastSent >= 5) {
				CombatPayloads.EncounterBar bar = e.bar(now);
				for (ServerPlayer p : now48) {
					if (joined && e.viewers.contains(p) && !e.dirty) {
						continue;
					}
					ServerPlayNetworking.send(p, bar);
					CombatSync.countDown(bar);
				}
				e.viewers.addAll(now48);
				e.lastSent = now;
				e.dirty = false;
				e.sentHealth = health;
			}
		}
	}
}
