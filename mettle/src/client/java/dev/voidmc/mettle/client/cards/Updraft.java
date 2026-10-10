package dev.voidmc.mettle.client.cards;

import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Updraft (Gale 6, docs/CARDS.md §6.3): press jump again in mid-air to jump once more before landing, every 8 s. The jump
 * is the client's own movement; the server is told so it can forgive the fall and keep the cooldown honest.
 */
final class Updraft {
	private static final Identifier GALE = Identifier.fromNamespaceAndPath("mettle", "gale");
	private static boolean wasDown;
	private static boolean leftGround;
	private static boolean used;
	private static long readyAt;

	private Updraft() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Updraft::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean down = mc.options.keyJump.isDown();
		if (player.onGround() || player.isInWater() || player.onClimbable()) {
			leftGround = false;
			used = false;
		} else if (!down) {
			leftGround = true;
		}
		long now = player.level().getGameTime();
		if (down && !wasDown && leftGround && !used && now >= readyAt && !player.getAbilities().flying
			&& CardStore.get(player).tier(GALE) >= 6) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, 0.42 * player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) / 0.42, v.z);
			player.resetFallDistance();
			used = true;
			readyAt = now + 160;
			ClientPlayNetworking.send(new MettlePayloads.Updraft());
		}
		wasDown = down;
	}
}
