package dev.voidmc.mettle.client.athletics;

import dev.voidmc.mettle.character.skill.athletics.Movement;
import dev.voidmc.mettle.character.skill.athletics.Scramble;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/**
 * Athletics on the client (called from MettleClient). The player's own movement is the client's to simulate, so the
 * sprint modifiers (Runner, Courier's Stride, Hauler's Stride, Steady Pace, Vaulter, Spring) are kept in step here as
 * well as on the server, and the Freerunner scramble climbs here and tells the server it began.
 */
public final class AthleticsClient {
	private AthleticsClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			LocalPlayer player = mc.player;
			if (player == null || mc.level == null || mc.isPaused()) {
				return;
			}
			Movement.tick(player);
			if (Scramble.climb(player, player.input.keyPresses.jump()) && ClientPlayNetworking.canSend(Scramble.Start.TYPE)) {
				ClientPlayNetworking.send(new Scramble.Start());
			}
		});
	}
}
