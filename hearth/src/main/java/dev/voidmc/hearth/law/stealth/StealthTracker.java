package dev.voidmc.hearth.law.stealth;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.crime.Presence;
import dev.voidmc.hearth.law.crime.Witnesses;
import dev.voidmc.hearth.law.net.LawPayloads;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The sneaking eye (KCD's stealth indicator): while a player crouches in a settlement, how plainly the folk round them
 * can see them, sent a few times a second; and a flash when a witness saw a crime. Only for sneaking players in towns.
 */
public final class StealthTracker {
	private static final Map<UUID, Float> SENT = new HashMap<>();
	private static final Set<UUID> NOTICED = new HashSet<>();

	private StealthTracker() {
	}

	/** A witness just saw this player commit a crime: the eye flashes. */
	public static void noticed(ServerPlayer player) {
		NOTICED.add(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			boolean noticed = NOTICED.remove(id);
			Settlement s = Presence.of(player);
			boolean active = s != null && player.isShiftKeyDown() && !Law.exempt(player) && Law.config().enabled();
			if (!active) {
				if (SENT.remove(id) != null || noticed) {
					send(player, new LawPayloads.Stealth(false, 0, noticed));
				}
				continue;
			}
			float vis = (float) Witnesses.visibility(player.level(), player);
			Float before = SENT.get(id);
			if (noticed || before == null || Math.abs(before - vis) > 0.04F) {
				SENT.put(id, vis);
				send(player, new LawPayloads.Stealth(true, vis, noticed));
			}
		}
	}

	private static void send(ServerPlayer player, LawPayloads.Stealth payload) {
		if (ServerPlayNetworking.canSend(player, LawPayloads.Stealth.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void forget(UUID player) {
		SENT.remove(player);
		NOTICED.remove(player);
	}

	public static void clear() {
		SENT.clear();
		NOTICED.clear();
	}
}
