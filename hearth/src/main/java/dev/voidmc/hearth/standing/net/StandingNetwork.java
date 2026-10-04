package dev.voidmc.hearth.standing.net;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.standing.StandingEngine;
import dev.voidmc.hearth.standing.StandingModule;
import dev.voidmc.hearth.standing.appearance.Look;
import dev.voidmc.hearth.standing.model.Company;
import dev.voidmc.hearth.standing.model.TownLedger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Registers the standing packets, answers page requests, and sends a player their standing where they are. */
public final class StandingNetwork {
	private StandingNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(StandingPayloads.Here.TYPE, StandingPayloads.Here.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StandingPayloads.Talk.TYPE, StandingPayloads.Talk.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StandingPayloads.Page.TYPE, StandingPayloads.Page.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StandingPayloads.RequestPage.TYPE, StandingPayloads.RequestPage.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StandingPayloads.RequestPage.TYPE, (payload, context) -> sendPage(context.player()));
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** The player's standing in a town (null: in none), for the arrival line and the screens. */
	public static void sendHere(ServerPlayer player, @Nullable Settlement town, boolean arrived) {
		StandingEngine engine = StandingModule.engine();
		if (engine == null) {
			return;
		}
		if (town == null) {
			send(player, StandingPayloads.Here.LEFT);
			return;
		}
		UUID id = player.getUUID();
		double renown = engine.renown(id, town.center());
		int fame = engine.fame(id, town.center());
		send(player, new StandingPayloads.Here(town.id(), town.name(), engine.standing(town, id), engine.title(town, id).getSerializedName(), fame,
			fame < 5 ? 0 : renown >= 0 ? 1 : -1, arrived && StandingModule.config().town().arrivalLine()));
	}

	public static void sendTalk(ServerPlayer player, int entity, String resident, int opinion, Component result) {
		send(player, new StandingPayloads.Talk(entity, resident, opinion, result));
	}

	/** Builds and sends the reputation page: every town the player has dealings with, their fame here, their looks, their company. */
	public static void sendPage(ServerPlayer player) {
		StandingEngine engine = StandingModule.engine();
		if (engine == null) {
			return;
		}
		send(player, page(engine, player));
	}

	public static StandingPayloads.Page page(StandingEngine engine, ServerPlayer player) {
		UUID id = player.getUUID();
		HearthWorld w = HearthWorld.of(player.level());
		Settlement here = w == null ? null : w.settlementAt(player.blockPosition());
		List<StandingPayloads.TownRow> rows = new ArrayList<>();
		for (Settlement s : engine.host().settlements()) {
			TownLedger.Entry e = StandingEngine.ledger(s).get(id);
			boolean isHere = here != null && here.id() == s.id();
			if (e == null && !isHere) {
				continue;
			}
			int distance = s.dimension() == player.level().dimension() ? (int) Math.sqrt(s.center().distSqr(player.blockPosition())) : -1;
			rows.add(new StandingPayloads.TownRow(s.id(), s.name(), s.culture().getSerializedName(), s.tier().getSerializedName(), engine.standing(s, id),
				engine.title(s, id).getSerializedName(), StandingEngine.owns(s, id), distance, isHere));
		}
		rows.sort(Comparator.comparing((StandingPayloads.TownRow r) -> !r.here()).thenComparingInt(r -> r.distance() < 0 ? Integer.MAX_VALUE : r.distance()));
		if (rows.size() > 200) {
			rows = new ArrayList<>(rows.subList(0, 200));
		}
		Look look = engine.look(player);
		int fame = engine.fame(id, player.blockPosition());
		double renown = engine.renown(id, player.blockPosition());
		Company c = StandingModule.config().companies().enabled() ? engine.host().region().companyOf(id) : null;
		List<String> members = new ArrayList<>();
		if (c != null) {
			for (UUID m : c.members()) {
				String n = engine.host().playerName(m);
				members.add(n.isEmpty() ? m.toString().substring(0, 8) : n);
			}
		}
		return new StandingPayloads.Page(rows, fame, fame < 5 ? 0 : renown >= 0 ? 1 : -1, (float) look.charisma(), (float) look.menace(), look.notes(),
			c == null ? "" : c.name(), members, StandingModule.config().companies().enabled());
	}
}
