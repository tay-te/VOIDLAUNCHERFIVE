package dev.voidmc.hearth.standing;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.standing.appearance.Appearances;
import dev.voidmc.hearth.standing.net.StandingNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The standing module's only regular work: each player is looked at once every {@code trackInterval} ticks (staggered,
 * so a full server spreads them over the interval) — which town they are in (to send their standing when they walk in),
 * whether they washed, whether their title there changed — and once every 100 ticks the gossip queue is checked for news
 * that has arrived. A few map lookups per player per second.
 */
final class PlayerTracker {
	private final Map<UUID, Long> presence = new HashMap<>();
	private int tick;

	void reset() {
		this.presence.clear();
		this.tick = 0;
	}

	void forget(UUID player) {
		this.presence.remove(player);
	}

	/** The town the player was last seen in, or {@link Long#MIN_VALUE}. */
	long presence(UUID player) {
		return this.presence.getOrDefault(player, Long.MIN_VALUE);
	}

	void tick(MinecraftServer server, StandingEngine engine) {
		this.tick++;
		int interval = engine.config().town().trackInterval();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Math.floorMod(p.getId() + this.tick, interval) != 0) {
				continue;
			}
			look(p, engine);
		}
		if (this.tick % 100 == 0) {
			engine.deliver();
		}
	}

	private void look(ServerPlayer p, StandingEngine engine) {
		HearthWorld w = HearthWorld.of(p.level());
		Settlement in = w == null || p.isSpectator() ? null : w.settlementAt(p.blockPosition());
		long id = in == null ? Long.MIN_VALUE : in.id();
		Long prev = this.presence.put(p.getUUID(), id);
		if (prev == null || prev != id) {
			StandingNetwork.sendHere(p, in, true);
		}
		if (in != null) {
			engine.refreshTitle(in, p.getUUID());
		}
		Appearances.checkWater(p, in);
	}

	/** After damage: blood on whoever fought hand to hand (the one hurt, and a player who struck the blow). */
	static void afterDamage(LivingEntity target, DamageSource source, float taken) {
		if (taken < 2 || target instanceof ArmorStand) {
			return;
		}
		if (target instanceof ServerPlayer hurt && source.getEntity() instanceof LivingEntity) {
			Appearances.bloody(hurt);
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof ServerPlayer attacker && attacker != target) {
			Appearances.bloody(attacker);
		}
	}
}
