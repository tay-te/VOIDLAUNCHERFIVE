package dev.voidmc.fray.client.combat;

import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.net.CombatPayloads;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Everything the combat HUD knows (client side): the player's own resources and statuses, other entities' meters and
 * profiles, the boss bar, banners, tells, hit feedback and damage numbers. Filled by Fray's payloads; read every frame.
 */
public final class ClientCombat {
	public static CombatPayloads.@Nullable Self self;
	public static long selfAt;
	public static List<CombatPayloads.StatusLine> statuses = List.of();
	public static long statusesAt;
	public static final Int2ObjectMap<CombatPayloads.Meters> METERS = new Int2ObjectOpenHashMap<>();
	public static final Int2ObjectMap<Long> METERS_AT = new Int2ObjectOpenHashMap<>();
	public static final Int2ObjectMap<CombatPayloads.Profile> PROFILES = new Int2ObjectOpenHashMap<>();
	public static CombatPayloads.@Nullable EncounterBar bar;
	public static long barAt;
	public static CombatPayloads.@Nullable Banner banner;
	public static long bannerAt;

	/** A tell on screen. */
	public record Tell(int attacker, byte colour, int windup, String subtitle, long at) {
	}

	public static final List<Tell> TELLS = new ArrayList<>();

	/** One piece of hit feedback (H8). */
	public record Fx(GuardOutcome outcome, int flags, float amount, byte kind, boolean mine, boolean onMe, double x, double y, double z, long at) {
	}

	public static final List<Fx> FX = new ArrayList<>();
	/** Stamina predicted spent since the last server value (shown at once, replaced by the server's next number). */
	public static float predictedSpend;
	public static long predictedAt;
	/** The last stamina refund seen (parry +15 flash). */
	public static float refund;
	public static long refundAt;
	/** Proc flashes: status → time. */
	public static final long[] PROC_AT = new long[StatusType.VALUES.length];
	public static int lastPredictedKind = -1;

	private ClientCombat() {
	}

	/** Milliseconds now (HUD animations run on wall time so they are smooth between ticks). */
	public static long ms() {
		return System.nanoTime() / 1_000_000L;
	}

	public static void clear() {
		self = null;
		statuses = List.of();
		METERS.clear();
		METERS_AT.clear();
		PROFILES.clear();
		bar = null;
		banner = null;
		TELLS.clear();
		FX.clear();
		predictedSpend = 0;
		LockOn.clear();
	}

	public static void onSelf(CombatPayloads.Self s) {
		CombatPayloads.Self old = self;
		if (old != null && s.stamina() - old.stamina() >= 8 && s.windedLeft() == 0) {
			refund = s.stamina() - old.stamina();
			refundAt = ms();
		}
		self = s;
		selfAt = ms();
		predictedSpend = 0;
		if (s.lockTarget() < 0 && LockOn.target >= 0 && LockOn.confirmed) {
			LockOn.lost();
		}
		if (s.lockTarget() >= 0) {
			LockOn.target = s.lockTarget();
			LockOn.confirmed = true;
		}
	}

	public static void onMeters(CombatPayloads.Meters m) {
		CombatPayloads.Meters old = METERS.get(m.entity());
		Minecraft mc = Minecraft.getInstance();
		if (old != null && mc.player != null && m.entity() == mc.player.getId()) {
			for (StatusType s : StatusType.METERED) {
				int i = s.meterIndex();
				boolean nowActive = (m.mask() & 1 << s.ordinal()) != 0;
				if ((old.meters()[i] & 0xFF) > 140 && (m.meters()[i] & 0xFF) < 20 && (nowActive || s == StatusType.BLEED || s == StatusType.CHILL)) {
					PROC_AT[s.ordinal()] = ms();
				}
			}
		}
		METERS.put(m.entity(), m);
		METERS_AT.put(m.entity(), Long.valueOf(ms()));
	}

	public static float meter(int entity, StatusType s) {
		CombatPayloads.Meters m = METERS.get(entity);
		return m == null || s.meterIndex() < 0 ? 0 : (m.meters()[s.meterIndex()] & 0xFF) / 255F;
	}

	public static boolean active(int entity, StatusType s) {
		CombatPayloads.Meters m = METERS.get(entity);
		return m != null && (m.mask() & 1 << s.ordinal()) != 0;
	}

	public static void onHit(CombatPayloads.Hit h) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		boolean mine = h.attacker() == mc.player.getId();
		boolean onMe = h.target() == mc.player.getId();
		GuardOutcome o = GuardOutcome.values()[Math.max(0, Math.min(GuardOutcome.values().length - 1, h.outcome()))];
		FX.add(new Fx(o, h.flags(), h.amount(), h.kind(), mine, onMe, h.x(), h.y(), h.z(), ms()));
		if (FX.size() > 48) {
			FX.removeFirst();
		}
	}

	public static void onTell(CombatPayloads.Tell t) {
		TELLS.removeIf(x -> x.attacker() == t.attacker());
		TELLS.add(new Tell(t.attacker(), t.colour(), t.windup(), t.subtitle().map(c -> c.getString()).orElse(""), ms()));
	}

	public static void onBar(CombatPayloads.EncounterBar b) {
		if (b.boss() < 0) {
			if (bar != null && bar.id() == b.id()) {
				bar = null;
			}
			return;
		}
		bar = b;
		barAt = ms();
	}

	public static void onBanner(CombatPayloads.Banner b) {
		banner = b;
		bannerAt = ms();
	}

	/** The player's stamina as shown: the server's value minus what the client has predicted since. */
	public static float stamina() {
		return self == null ? 100 : Math.max(0, self.stamina() - predictedSpend);
	}

	public static void prune() {
		long now = ms();
		FX.removeIf(f -> now - f.at() > 1500);
		TELLS.removeIf(t -> now - t.at() > t.windup() * 50L + 900);
		if (banner != null && now - bannerAt > 4000) {
			banner = null;
		}
		if (predictedSpend > 0 && now - predictedAt > 1000) {
			predictedSpend = 0;
		}
	}
}
