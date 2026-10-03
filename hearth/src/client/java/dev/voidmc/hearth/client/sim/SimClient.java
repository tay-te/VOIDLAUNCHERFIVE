package dev.voidmc.hearth.client.sim;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.sim.net.ArrivalPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** Client side of the sim module: the arrival banner and a line on the debug screen (enable "hearth:sim" in its options). */
public final class SimClient {
	private SimClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ArrivalPayload.TYPE, (payload, context) -> ArrivalBanner.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ArrivalBanner.reset());
		HudElementRegistry.addLast(Hearth.id("sim/arrival"), ArrivalBanner::extract);
		DebugScreenEntries.register(Hearth.id("sim"), new DebugLine());
	}

	/** "Hearth: Ashbrook #12 (village, 34 souls, embodied)" or "Hearth: wilderness". */
	private static final class DebugLine implements DebugScreenEntry {
		@Override
		public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
			ArrivalPayload p = ArrivalBanner.current();
			displayer.addLine(p == null ? "Hearth: wilderness" : "Hearth: " + p.name() + " #" + p.id() + " (" + p.tier() + ", " + p.population() + " souls"
				+ (p.embodied() ? ", embodied" : "") + ", " + p.culture() + ")");
		}
	}
}
