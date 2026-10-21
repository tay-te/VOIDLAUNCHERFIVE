package dev.voidmc.fray.motion;

import dev.voidmc.fray.Fray;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

/**
 * The login check (configuration phase, CONTRACTS.md §16): the server sends its Fray protocol and the hash of the clip
 * set it baked its timelines from; the client answers with the hash of the clips it will draw. Any difference refuses the
 * login with a plain reason, so a client never plays a swing whose timing differs from the server's hit.
 */
public final class FrayHandshake {
	/** Bumped whenever server and client must match beyond the clips themselves (payload formats). */
	public static final int PROTOCOL = 1;

	/** The last refusal's reason (tests and the studio log). */
	public static volatile String lastRefusal = "";
	/** Logins that passed the check. */
	public static volatile int accepted;

	private static final ConfigurationTask.Type KEY = new ConfigurationTask.Type(Fray.id("hello").toString());

	private FrayHandshake() {
	}

	public static Component refusal(MotionPayloads.Hello server, MotionPayloads.Hello client) {
		if (client.protocol() != server.protocol()) {
			return Component.literal("VOID Fray " + server.version() + " required (you have " + client.version() + ")");
		}
		return Component.literal("Your VOID animations don't match this server's: clip set " + shortHash(server.clipSet()) + " (" + server.clips()
			+ " clips) here, " + shortHash(client.clipSet()) + " (" + client.clips() + " clips) on your side. Install the same VOID Bestiary version as the server.");
	}

	public static String shortHash(String h) {
		return h.length() > 8 ? h.substring(0, 8) : h;
	}

	public static MotionPayloads.Hello serverHello() {
		return new MotionPayloads.Hello(PROTOCOL, Fray.version(), MotionServer.clipSetHash(), MotionServer.clipCount());
	}

	public static void init() {
		PayloadTypeRegistry.clientboundConfiguration().register(MotionPayloads.Hello.TYPE, MotionPayloads.Hello.STREAM_CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(MotionPayloads.Hello.TYPE, MotionPayloads.Hello.STREAM_CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (ServerConfigurationNetworking.canSend(handler, MotionPayloads.Hello.TYPE)) {
				handler.addTask(new Task());
			} else {
				Fray.LOGGER.info("refused a client without VOID Fray (it can't take the motion handshake)");
				handler.disconnect(Component.literal("VOID Bestiary " + Fray.version() + " required (you have none)"));
			}
		});
		ServerConfigurationNetworking.registerGlobalReceiver(MotionPayloads.Hello.TYPE, (payload, context) -> {
			MotionPayloads.Hello mine = serverHello();
			if (payload.protocol() != mine.protocol() || !payload.clipSet().equals(mine.clipSet())) {
				Component reason = refusal(mine, payload);
				lastRefusal = reason.getString();
				Fray.LOGGER.info("refused a client: {}", reason.getString());
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
			sender.accept(ServerConfigurationNetworking.createClientboundPacket(serverHello()));
		}

		@Override
		public Type type() {
			return KEY;
		}
	}
}
