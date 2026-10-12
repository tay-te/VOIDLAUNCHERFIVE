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
		// The cooldown is the server's, saved with the player (CardCooldowns), so a relog never resets it.
		long saved = dev.voidmc.mettle.character.cards.CardCooldowns.readyAt(player, dev.voidmc.mettle.character.cards.CardCooldowns.UPDRAFT);
		if (down && !wasDown && leftGround && !used && now >= readyAt && now >= saved && !player.getAbilities().flying
			&& CardStore.get(player).tier(GALE) >= 6) {
			Vec3 v = player.getDeltaMovement();
			// As high as a jump, with the jump ceiling (Ceilings.JUMP_STRENGTH) that holds every jump under two blocks.
			double strength = Math.min(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH),
				dev.voidmc.mettle.character.cards.Ceilings.JUMP_STRENGTH);
			player.setDeltaMovement(v.x, strength, v.z);
			player.resetFallDistance();
			used = true;
			readyAt = now + dev.voidmc.mettle.character.cards.CardHooks.UPDRAFT_TICKS;
			ClientPlayNetworking.send(new MettlePayloads.Updraft());
		}
		wasDown = down;
	}
}
