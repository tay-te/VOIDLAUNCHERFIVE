package dev.voidmc.mettle.client.cards;

import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;

/**
 * The Seven-League Stride (a Mythic, docs/CARDS.md §9.3): double-tap forward while sprinting to dash 6 blocks, every 8 s.
 * The dash is the client's own movement; the server is told so it forgives the fall that follows.
 */
final class Stride {
	private static final ResourceKey<dev.voidmc.mettle.api.Perk> KEY = ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.PERK,
		Identifier.fromNamespaceAndPath("mettle", "mythic/seven_league_stride"));
	private static boolean wasDown;
	private static long lastPress = -100;
	private static long readyAt;

	private Stride() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Stride::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean down = mc.options.keyUp.isDown();
		long now = player.level().getGameTime();
		if (down && !wasDown) {
			if (now - lastPress <= 7 && player.isSprinting() && now >= readyAt && CardStore.get(player).activeRank(KEY) > 0) {
				Vec3 look = Vec3.directionFromRotation(0, player.getYRot()).normalize();
				player.setDeltaMovement(look.x * 1.9, Math.max(player.getDeltaMovement().y, 0.15), look.z * 1.9);
				player.resetFallDistance();
				readyAt = now + 160;
				ClientPlayNetworking.send(new MettlePayloads.Dash());
			}
			lastPress = now;
		}
		wasDown = down;
	}
}
