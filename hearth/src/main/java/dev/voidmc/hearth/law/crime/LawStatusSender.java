package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.justice.PlayerLaw;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.net.LawPayloads;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Keeps each player's law status on their HUD (wanted here, how much, suspected, jailed), sending only changes. */
public final class LawStatusSender {
	private static final Map<UUID, LawPayloads.Status> SENT = new HashMap<>();

	private LawStatusSender() {
	}

	public static void refresh(ServerPlayer player) {
		LawPayloads.Status status = status(player);
		if (!status.equals(SENT.get(player.getUUID())) && ServerPlayNetworking.canSend(player, LawPayloads.Status.TYPE)) {
			SENT.put(player.getUUID(), status);
			ServerPlayNetworking.send(player, status);
		}
	}

	static LawPayloads.Status status(ServerPlayer player) {
		PlayerLaw law = PlayerLaw.of(player);
		HearthWorld world = HearthWorld.of(player.level());
		if (law.sentence().isPresent()) {
			PlayerLaw.Sentence s = law.sentence().get();
			Settlement where = world == null ? null : world.settlement(s.settlement());
			return new LawPayloads.Status(where == null ? "" : where.name(), 0, false, 0, Math.max(1, s.ticksLeft()), s.pillory(), false);
		}
		Settlement here = Presence.of(player);
		boolean elsewhere = false;
		if (world != null) {
			for (Settlement s : world.settlements()) {
				if (here != null && s.id() == here.id()) {
					continue;
				}
				Warrant w = Law.state(s).warrant(player.getUUID());
				if (w != null && w.active()) {
					elsewhere = true;
					break;
				}
			}
		}
		if (here == null) {
			return new LawPayloads.Status("", 0, false, 0, 0, false, elsewhere);
		}
		Warrant w = Law.state(here).warrant(player.getUUID());
		int suspicion = Law.state(here).suspicion(player.getUUID());
		if (w == null || !w.active()) {
			return new LawPayloads.Status(here.name(), 0, false, suspicion, 0, false, elsewhere);
		}
		return new LawPayloads.Status(here.name(), w.bounty(), w.outlaw(), suspicion, 0, false, elsewhere);
	}

	public static void forget(UUID player) {
		SENT.remove(player);
	}

	public static void clear() {
		SENT.clear();
	}
}
