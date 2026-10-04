package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.state.LawState;
import dev.voidmc.hearth.law.stealth.Visibility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Which settlement each player is in, refreshed every half second. Everything the law does continuously (the sneaking
 * eye, ownership hints, trespass, guards looking for wanted faces) runs only for players found here, so its cost
 * follows the players in towns, not the number of towns. Also notes the day each player was last seen in each town,
 * which is how a crime discovered later finds its suspects.
 */
public final class Presence {
	private static final Map<UUID, Long> WHERE = new HashMap<>();

	private Presence() {
	}

	public static void update(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			HearthWorld world = HearthWorld.of(level);
			Settlement s = world == null ? null : world.settlementAt(player.blockPosition());
			Long before = WHERE.get(player.getUUID());
			if (s == null) {
				if (before != null) {
					WHERE.remove(player.getUUID());
					LawStatusSender.refresh(player);
				}
				continue;
			}
			Visibility.sample(player);
			WHERE.put(player.getUUID(), s.id());
			LawState state = Law.state(s);
			long day = Law.day(level);
			if (state.seenDay(player.getUUID()) != day && !Law.exempt(player)) {
				state.markSeen(player.getUUID(), day);
				s.markDirty();
			}
			if (before == null || before != s.id()) {
				LawStatusSender.refresh(player);
			}
		}
	}

	/** The settlement the player is in, or null. */
	public static @Nullable Settlement of(ServerPlayer player) {
		Long id = WHERE.get(player.getUUID());
		if (id == null) {
			return null;
		}
		HearthWorld world = HearthWorld.of(player.level());
		return world == null ? null : world.settlement(id);
	}

	public static @Nullable Long settlementId(UUID player) {
		return WHERE.get(player);
	}

	/** The players in a settlement now. */
	public static List<ServerPlayer> playersIn(MinecraftServer server, long settlementId) {
		List<ServerPlayer> out = new ArrayList<>(2);
		for (Map.Entry<UUID, Long> e : WHERE.entrySet()) {
			if (e.getValue() == settlementId) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					out.add(p);
				}
			}
		}
		return out;
	}

	public static boolean anyone() {
		return !WHERE.isEmpty();
	}

	public static void forget(UUID player) {
		WHERE.remove(player);
		Visibility.forget(player);
	}

	public static void clear() {
		WHERE.clear();
	}
}
