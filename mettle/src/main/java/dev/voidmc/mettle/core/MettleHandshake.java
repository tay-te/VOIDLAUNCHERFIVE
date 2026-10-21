package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

/**
 * The version check at login (configuration phase): the server sends {@link MettlePayloads.Hello} with its protocol, the
 * client answers with its own, and a client on another protocol is refused with a plain reason rather than failing later
 * on a packet or a registry it doesn't know. A client that can't answer (no Mettle, or a Mettle from before this check)
 * is refused the same way.
 */
public final class MettleHandshake {
	/**
	 * Bumped whenever the server and client must match: payloads, synced attachments, synced registries. 1 was 0.1.0
	 * (no handshake); 2 is 0.2.0 (the cards view attachment, the card config payload, the hidden draft seed); 3 is 0.3.0
	 * (build feel: the byname registry, keyword momentum and Mythic epithets, the build action payload, words on moments,
	 * the public renown attachment, boons in the draft record).
	 */
	public static final int PROTOCOL = 3;

	private static final ConfigurationTask.Type KEY = new ConfigurationTask.Type(Mettle.id("hello").toString());

	private MettleHandshake() {
	}

	/** This side's Mettle version ("0.2.0"). */
	public static String version() {
		return FabricLoader.getInstance().getModContainer(Mettle.ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	/** The reason a client is refused. */
	public static Component refusal(String clientVersion) {
		return Component.literal("VOID Mettle " + version() + " required (you have " + clientVersion + ")");
	}

	public static void init() {
		PayloadTypeRegistry.clientboundConfiguration().register(MettlePayloads.Hello.TYPE, MettlePayloads.Hello.STREAM_CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(MettlePayloads.Hello.TYPE, MettlePayloads.Hello.STREAM_CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (ServerConfigurationNetworking.canSend(handler, MettlePayloads.Hello.TYPE)) {
				handler.addTask(new Task());
			} else {
				Mettle.LOGGER.info("refused a client without VOID Mettle {} (it can't take Mettle's handshake)", version());
				handler.disconnect(refusal("none, or one older than 0.2"));
			}
		});
		ServerConfigurationNetworking.registerGlobalReceiver(MettlePayloads.Hello.TYPE, (payload, context) -> {
			if (payload.protocol() != PROTOCOL) {
				Mettle.LOGGER.info("refused a client with VOID Mettle {} (protocol {}, this server speaks {})", payload.version(), payload.protocol(), PROTOCOL);
				context.packetListener().disconnect(refusal(payload.version()));
				return;
			}
			context.packetListener().completeTask(KEY);
		});
	}

	private record Task() implements ConfigurationTask {
		@Override
		public void start(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createClientboundPacket(new MettlePayloads.Hello(PROTOCOL, version())));
		}

		@Override
		public Type type() {
			return KEY;
		}
	}
}
