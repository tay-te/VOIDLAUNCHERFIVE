package dev.voidmc.fray.combat.net;

import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.CombatState;
import dev.voidmc.fray.combat.Encounters;
import dev.voidmc.fray.combat.action.ActionKind;
import dev.voidmc.fray.combat.action.Arts;
import dev.voidmc.fray.combat.action.PlayerCombat;
import dev.voidmc.fray.api.DamageKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * What the server sends and when (CONTRACTS.md §15): on change only, rate limited (resources 10 a second, meters 4 a
 * second per entity), never per tick. Counts packets and bytes for the network budget (G-NET).
 */
public final class CombatSync {
	public static long packets;
	public static long bytes;

	private CombatSync() {
	}

	private static void send(ServerPlayer p, CustomPacketPayload payload, int size) {
		ServerPlayNetworking.send(p, payload);
		packets++;
		bytes += size;
	}

	public static void countDown(CustomPacketPayload payload) {
		packets++;
		bytes += 40;
	}

	public static void ack(ServerPlayer p, CombatPayloads.Ack ack) {
		send(p, ack, 8);
	}

	/** An action started (kind null: stopped) to everyone tracking the player, not the player (it predicted it). */
	public static void actionPlay(ServerPlayer p, @Nullable ActionKind kind, int variant, long start, float speed) {
		CombatPayloads.ActionPlay play = new CombatPayloads.ActionPlay(p.getId(), (byte) (kind == null ? -1 : kind.ordinal()), (byte) variant, start, speed);
		for (ServerPlayer o : PlayerLookup.tracking(p)) {
			if (o != p) {
				send(o, play, 12);
			}
		}
	}

	public static void self(PlayerCombat pc, long now) {
		int flags = (pc.guardHeld ? CombatPayloads.SELF_GUARDING : 0) | (pc.inCombat() ? CombatPayloads.SELF_IN_COMBAT : 0)
			| (pc.guardHeld && now - pc.guardPress >= pc.guard.blockFrom() ? CombatPayloads.SELF_BLOCK_UP : 0) | (pc.attackHeld ? CombatPayloads.SELF_CHARGING : 0)
			| (Encounters.INSTANCE.inBossFight(pc.player) ? CombatPayloads.SELF_BOSS_FIGHT : 0);
		int art = Arts.index(pc.art());
		int cd = (int) Math.max(0, pc.artReadyAt - now);
		CombatPayloads.Self s = new CombatPayloads.Self(Math.round(pc.stamina.value * 10) / 10F, pc.stamina.max, pc.stamina.windedLeft(now), pc.stamina.regenDelayLeft(now),
			flags, pc.state.poise.max <= 0 ? 1 : pc.state.poise.value / pc.state.poise.max, art, cd, pc.artTotal, pc.lockTarget, (int) Math.max(0, pc.riposteUntil - now),
			0);
		CombatPayloads.Self last = pc.lastSent;
		boolean important = last == null || last.flags() != s.flags() || last.art() != s.art() || last.lockTarget() != s.lockTarget() || (last.windedLeft() > 0) != (s.windedLeft() > 0)
			|| (last.riposteLeft() > 0) != (s.riposteLeft() > 0) || (last.artCooldown() > 0) != (s.artCooldown() > 0) || last.max() != s.max();
		boolean changed = important || last.stamina() != s.stamina() || last.regenDelayLeft() > 0 != s.regenDelayLeft() > 0 || Math.abs(last.poise() - s.poise()) > 0.01F
			|| last.artCooldown() / 20 != s.artCooldown() / 20;
		if (changed && (important || now - pc.lastSentTick >= 2)) {
			send(pc.player, s, 28);
			pc.lastSent = s;
			pc.lastSentTick = now;
		}
	}

	/** Meters and statuses of one entity, to trackers (and itself, with times, if a player). */
	public static void meters(CombatState s, long now) {
		if (!s.metersDirty && !s.statusDirty) {
			return;
		}
		if (now - s.lastMetersSent < 5 && !s.statusDirty) {
			return;
		}
		LivingEntity e = s.entity;
		byte[] m = s.meters.bytes();
		int mask = s.mask(now);
		if (s.statusDirty && e instanceof ServerPlayer self) {
			List<CombatPayloads.StatusLine> lines = new ArrayList<>();
			for (StatusType st : StatusType.VALUES) {
				int left = s.left(st, now);
				if (left > 0) {
					lines.add(new CombatPayloads.StatusLine((byte) st.ordinal(), left, s.length[st.ordinal()]));
				}
			}
			send(self, new CombatPayloads.SelfStatuses(lines), 4 + lines.size() * 5);
		}
		if (!Arrays.equals(m, s.sentMeters) || mask != s.sentMask) {
			CombatPayloads.Meters payload = new CombatPayloads.Meters(e.getId(), m, mask, s.left(StatusType.STAGGER, now));
			for (ServerPlayer o : PlayerLookup.tracking(e)) {
				send(o, payload, 14);
			}
			if (e instanceof ServerPlayer self) {
				send(self, payload, 14);
			}
			s.sentMeters = m;
			s.sentMask = mask;
		}
		s.lastMetersSent = now;
		s.metersDirty = false;
		s.statusDirty = false;
	}

	public static CombatPayloads.Profile profilePayload(CombatState s) {
		return new CombatPayloads.Profile(s.entity.getId(), (byte) s.rank.ordinal(), Optional.ofNullable(s.subtitle), s.affixes);
	}

	public static void profile(CombatState s) {
		CombatPayloads.Profile p = profilePayload(s);
		for (ServerPlayer o : PlayerLookup.tracking(s.entity)) {
			send(o, p, 24);
		}
	}

	/** A late joiner or someone coming into range: profile and meters as they are. */
	public static void startTracking(Entity e, ServerPlayer p) {
		if (!(e instanceof LivingEntity le)) {
			return;
		}
		CombatState s = CombatState.peek(le);
		if (s == null) {
			return;
		}
		if (s.rank != dev.voidmc.fray.api.Rank.COMMON || s.subtitle != null || !s.affixes.isEmpty()) {
			send(p, profilePayload(s), 24);
		}
		long now = le.level().getGameTime();
		send(p, new CombatPayloads.Meters(le.getId(), s.meters.bytes(), s.mask(now), s.left(StatusType.STAGGER, now)), 14);
	}

	/** A hit's outcome to the attacker and the target (players only). */
	/**
	 * A hit's feedback: to the attacker and the target (HUD), and to every other player tracking the target, who only
	 * plays the hit-stop and a boss slam's shake (COMBAT.md §5).
	 */
	public static void hit(@Nullable Entity attacker, LivingEntity target, GuardOutcome outcome, int flags, float amount, DamageKind kind, boolean killed) {
		CombatState as = attacker instanceof LivingEntity la ? CombatState.peek(la) : null;
		if (as != null && as.rank == dev.voidmc.fray.api.Rank.BOSS) {
			flags |= CombatPayloads.HIT_BOSS;
		}
		CombatPayloads.Hit h = new CombatPayloads.Hit(attacker == null ? -1 : attacker.getId(), target.getId(), (byte) outcome.ordinal(),
			flags | (killed ? CombatPayloads.HIT_KILL : 0), amount, (byte) kind.ordinal(), (float) target.getX(), (float) (target.getY() + target.getBbHeight() * 0.8),
			(float) target.getZ());
		if (attacker instanceof ServerPlayer ap) {
			send(ap, h, 26);
		}
		if (target instanceof ServerPlayer tp && tp != attacker) {
			send(tp, h, 26);
		}
		for (ServerPlayer o : PlayerLookup.tracking(target)) {
			if (o != attacker && o != target) {
				send(o, h, 26);
			}
		}
	}
}
