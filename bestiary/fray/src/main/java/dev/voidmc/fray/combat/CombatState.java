package dev.voidmc.fray.combat;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.action.PlayerCombat;
import dev.voidmc.fray.combat.math.MeterSet;
import dev.voidmc.fray.combat.math.PoiseMeter;
import dev.voidmc.fray.combat.math.ThreatTable;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Fray's server state on one living entity in combat (CONTRACTS.md §7.5): status meters and timers, poise, rank and
 * profile, threat (creatures), the player's actions and stamina. Not persistent: a fight doesn't survive a restart.
 * Created on first use; entities with live state are ticked by {@link Statuses}.
 */
public final class CombatState {
	public static final AttachmentType<CombatState> TYPE = AttachmentRegistry.create(Fray.id("combat"));

	public final LivingEntity entity;
	public final MeterSet meters = new MeterSet();
	/** Game tick each status ends (0: not active). */
	public final long[] until = new long[StatusType.VALUES.length];
	/** Each status's full duration when applied (HUD). */
	public final int[] length = new int[StatusType.VALUES.length];
	public PoiseMeter poise;
	public Rank rank = Rank.COMMON;
	public @Nullable Component subtitle;
	public List<Identifier> affixes = List.of();
	public @Nullable ThreatTable threat;
	public final @Nullable PlayerCombat player;
	/** Per-attacker floor for Fray hits (entity id → last hit tick). */
	public final Int2LongOpenHashMap lastHitBy = new Int2LongOpenHashMap();
	/** The last Fray hit's damage (Shock jumps half of it). */
	public float lastHitDamage;
	public int markedBy = -1;
	public int chillStacks;
	public boolean metersDirty;
	public boolean statusDirty;
	public long lastMetersSent = Long.MIN_VALUE / 2;
	public byte[] sentMeters = new byte[StatusType.METERED.length];
	public int sentMask;
	public boolean ticking;

	private CombatState(LivingEntity entity) {
		this.entity = entity;
		this.player = entity instanceof ServerPlayer p ? new PlayerCombat(p, this) : null;
		this.poise = new PoiseMeter(Poise.max(entity, this.rank));
	}

	public static CombatState of(LivingEntity e) {
		CombatState s = e.getAttached(TYPE);
		if (s == null) {
			s = new CombatState(e);
			e.setAttached(TYPE, s);
		}
		// Back into the ticking set whenever it is touched again (a creature drops out once idle).
		Statuses.track(s);
		return s;
	}

	public static @Nullable CombatState peek(LivingEntity e) {
		return e.getAttached(TYPE);
	}

	public boolean has(StatusType s, long now) {
		return this.until[s.ordinal()] > now;
	}

	public int left(StatusType s, long now) {
		return (int) Math.max(0, this.until[s.ordinal()] - now);
	}

	/** Bitmask of active statuses (bit = ordinal). */
	public int mask(long now) {
		int m = 0;
		for (StatusType s : StatusType.VALUES) {
			if (this.until[s.ordinal()] > now) {
				m |= 1 << s.ordinal();
			}
		}
		return m;
	}

	/** True when nothing needs ticking any more (creatures drop out of the ticking set). */
	public boolean idle(long now) {
		if (this.player != null) {
			return false;
		}
		if (!this.meters.empty() || this.poise.value < this.poise.max) {
			return false;
		}
		for (long u : this.until) {
			if (u > now) {
				return false;
			}
		}
		return this.threat == null || this.threat.size == 0;
	}
}
