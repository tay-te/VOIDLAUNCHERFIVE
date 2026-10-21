package dev.voidmc.fray;

import dev.voidmc.fray.motion.FrayHandshake;
import dev.voidmc.fray.motion.MotionPayloads;
import dev.voidmc.fray.motion.MotionServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Fray: the shared combat and animation library (CONTRACTS.md §1, §3). Stage 0 carries only Fray Motion's spike:
 * clips, server timelines, hit volumes, sync and the login hash (ANIMATION.md, M0).
 */
public final class Fray implements ModInitializer {
	public static final String ID = "fray";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Fray");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(MotionPayloads.Play.TYPE, MotionPayloads.Play.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MotionPayloads.Stop.TYPE, MotionPayloads.Stop.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MotionPayloads.ActionAck.TYPE, MotionPayloads.ActionAck.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MotionPayloads.ActionRequest.TYPE, MotionPayloads.ActionRequest.STREAM_CODEC);
		MotionServer.init();
		FrayHandshake.init();
		// Combat systems (stage 1B): pipeline, statuses, stamina, actions, encounters, the combat handshake.
		dev.voidmc.fray.combat.FrayCombat.init();
	}
}
