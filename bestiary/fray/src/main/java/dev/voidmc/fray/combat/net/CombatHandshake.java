package dev.voidmc.fray.combat.net;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.combat.action.Movesets;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

/**
 * Fray combat's login check (CONTRACTS.md §16), beside Fray Motion's clip check: the server sends its combat protocol
 * and the hash of its movesets and combat rules; the client answers with its own. A mismatch refuses the login with a
 * plain message, so a client never predicts a swing whose phases differ from the server's.
 */
public final class CombatHandshake {
	private static final ConfigurationTask.Type KEY = new ConfigurationTask.Type(Fray.id("combat/hello").toString());
	public static volatile String lastRefusal = "";
	public static volatile int accepted;

	private CombatHandshake() {
	}

	/** SHA-256 over the movesets' canonical text and the rule constants, first 16 hex digits. */
	public static String contentHash() {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(Movesets.canonical().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(md.digest()).substring(0, 16);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static CombatPayloads.Hello hello() {
		return new CombatPayloads.Hello(FrayApi.PROTOCOL, Fray.version(), contentHash());
	}

	public static Component refusal(CombatPayloads.Hello server, CombatPayloads.Hello client) {
		if (server.protocol() != client.protocol()) {
			return Component.literal("VOID Fray " + server.version() + " required (you have " + client.version() + "): the combat protocol differs");
		}
		return Component.literal("Your VOID combat rules don't match this server's (movesets " + server.content().substring(0, 8) + " here, "
			+ client.content().substring(0, Math.min(8, client.content().length())) + " on your side). Install the same VOID Fray version as the server (" + server.version() + ").");
	}

	public static void init() {
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (ServerConfigurationNetworking.canSend(handler, CombatPayloads.Hello.TYPE)) {
				handler.addTask(new Task());
			}
			// A client without Fray at all is refused by Fray Motion's handshake.
		});
		ServerConfigurationNetworking.registerGlobalReceiver(CombatPayloads.Hello.TYPE, (payload, context) -> {
			CombatPayloads.Hello mine = hello();
			if (payload.protocol() != mine.protocol() || !payload.content().equals(mine.content())) {
				Component reason = refusal(mine, payload);
				lastRefusal = reason.getString();
				Fray.LOGGER.info("refused a client: {}", lastRefusal);
				context.packetListener().disconnect(reason);
				return;
			}
			accepted++;
			context.packetListener().completeTask(KEY);
		});
	}

	private record Task() implements ConfigurationTask {
		@Override
		public void start(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createClientboundPacket(hello()));
		}

		@Override
		public Type type() {
			return KEY;
		}
	}
}
