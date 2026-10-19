package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Tracked wanted posters (docs/HUB_UI.md: "wanted posters can be tracked"): a player follows someone the law wants, the
 * way a tracked quest is followed. The server checks the warrant is still there once a second and tells the tracker where
 * the quarry is, to the nearest {@link QuestPayloads.PosterTrack#GRID} blocks and only in the same dimension, sending only
 * what changed. One thing is tracked at a time: tracking a poster lets go of the tracked quest, and tracking a quest lets
 * go of the poster. Not saved: a poster is tracked for the session. Server thread.
 */
public final class PosterTracks {
	/** Who is tracked, from which town's posters. */
	record Track(UUID quarry, long settlementId) {
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static final Map<UUID, QuestPayloads.PosterTrack> SENT = new HashMap<>();
	private static int ticks;

	private PosterTracks() {
	}

	/** The player tracks {@code quarry} (a UUID string) from a settlement's posters, or stops ({@code quarry} empty). */
	public static void track(ServerPlayer player, long settlementId, String quarry) {
		if (quarry == null || quarry.isEmpty()) {
			untrack(player);
			return;
		}
		UUID who;
		try {
			who = UUID.fromString(quarry);
		} catch (IllegalArgumentException e) {
			return;
		}
		if (who.equals(player.getUUID()) || wanted(settlementId, who) == null) {
			return;
		}
		Track now = new Track(who, settlementId);
		if (now.equals(TRACKS.get(player.getUUID()))) {
			// the same poster again: a toggle, as a quest's track is
			untrack(player);
			return;
		}
		TRACKS.put(player.getUUID(), now);
		QuestStore.edit(player, pq -> pq.tracked = null);
		update(player);
	}

	public static void untrack(ServerPlayer player) {
		if (TRACKS.remove(player.getUUID()) != null) {
			send(player, QuestPayloads.PosterTrack.NONE);
		}
	}

	/** Who the player tracks, or null. */
	public static @Nullable UUID tracked(ServerPlayer player) {
		Track t = TRACKS.get(player.getUUID());
		return t == null ? null : t.quarry();
	}

	public static void tick(MinecraftServer server) {
		if (TRACKS.isEmpty() || ++ticks % 20 != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Track>> it = TRACKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Track> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				SENT.remove(e.getKey());
				continue;
			}
			if (wanted(e.getValue().settlementId(), e.getValue().quarry()) == null) {
				it.remove();
				send(player, QuestPayloads.PosterTrack.NONE);
				continue;
			}
			update(player);
		}
	}

	private static void update(ServerPlayer player) {
		Track t = TRACKS.get(player.getUUID());
		if (t == null) {
			return;
		}
		LawService.Wanted w = wanted(t.settlementId(), t.quarry());
		if (w == null) {
			untrack(player);
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerPlayer quarry = server.getPlayerList().getPlayer(t.quarry());
		boolean located = quarry != null && quarry.level() == player.level();
		BlockPos near = located ? snap(quarry.blockPosition()) : BlockPos.ZERO;
		HearthWorld world = HearthWorld.of(server.overworld());
		Settlement s = world == null ? null : world.settlement(t.settlementId());
		send(player, new QuestPayloads.PosterTrack(true, t.quarry().toString(), w.name(), Math.max(0, w.bounty()), w.outlaw(),
			w.worst().translationKey(), t.settlementId(), s == null ? "" : s.name(), quarry != null, located, near));
	}

	/** A position to the nearest grid square's centre (y kept to 8 blocks): where to look, not where they stand. */
	static BlockPos snap(BlockPos p) {
		int g = QuestPayloads.PosterTrack.GRID;
		return new BlockPos(Math.floorDiv(p.getX(), g) * g + g / 2, Math.floorDiv(p.getY(), 8) * 8, Math.floorDiv(p.getZ(), g) * g + g / 2);
	}

	private static LawService.@Nullable Wanted wanted(long settlementId, UUID quarry) {
		LawService law = HearthServices.law();
		if (law == null) {
			return null;
		}
		for (LawService.Wanted w : law.wanted(settlementId)) {
			if (w.player().equals(quarry)) {
				return w;
			}
		}
		return null;
	}

	private static void send(ServerPlayer player, QuestPayloads.PosterTrack track) {
		if (track.equals(SENT.get(player.getUUID()))) {
			return;
		}
		SENT.put(player.getUUID(), track);
		if (ServerPlayNetworking.canSend(player, QuestPayloads.PosterTrack.TYPE)) {
			ServerPlayNetworking.send(player, track);
		}
	}

	public static void forget(ServerPlayer player) {
		TRACKS.remove(player.getUUID());
		SENT.remove(player.getUUID());
	}

	public static void clear() {
		TRACKS.clear();
		SENT.clear();
		ticks = 0;
	}
}
