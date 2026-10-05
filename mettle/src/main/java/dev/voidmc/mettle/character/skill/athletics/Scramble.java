package dev.voidmc.mettle.character.skill.athletics;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.AthleticsSkill;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Freerunner mastery: hold jump against a wall to scramble up it, up to {@link AthleticsRules#SCRAMBLE_HEIGHT}
 * blocks above your jump, once before you land again. The climb itself is the client's movement (players move
 * themselves; the server checks the result), so {@link #climb} runs there; the client tells the server when a scramble
 * starts, and the server checks it's allowed, spends it and forgives the fall it ends.
 */
public final class Scramble {
	private Scramble() {
	}

	/** Client → server: a scramble began. */
	public record Start() implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(Mettle.id("athletics/scramble"));
		public static final StreamCodec<ByteBuf, Start> STREAM_CODEC = StreamCodec.unit(new Start());

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Start.TYPE, (payload, context) -> started(context.player()));
	}

	private static void started(ServerPlayer player) {
		AthleticsState state = AthleticsState.of(player);
		if (!Perks.has(player, AthleticsSkill.FREERUNNER) || state.scrambleSpent || player.onGround()) {
			return;
		}
		state.scrambleSpent = true;
		state.scrambles++;
		player.resetFallDistance();
		long now = player.level().getGameTime();
		if (now - state.scrambleChipAt >= 100) {
			state.scrambleChipAt = now;
			AthleticsSkill.chip(player, AthleticsSkill.FREERUNNER, "freerunner");
		}
	}

	/** Server tick: landing gives the scramble back. */
	public static void tick(ServerPlayer player) {
		if (player.onGround()) {
			AthleticsState.of(player).scrambleSpent = false;
		}
	}

	/**
	 * Client tick for the local player. Returns true on the tick a scramble starts, so the caller can tell the server.
	 * {@code jumpHeld} is the jump key.
	 */
	public static boolean climb(Player player, boolean jumpHeld) {
		AthleticsState state = AthleticsState.of(player);
		if (player.onGround() || player.onClimbable() || player.isInWater() || player.getAbilities().flying || player.isPassenger()
			|| player.isFallFlying()) {
			state.jumpStartY = player.getY();
			if (player.onGround()) {
				state.scrambleSpent = false;
			}
			state.scrambling = false;
			return false;
		}
		if (!Perks.has(player, AthleticsSkill.FREERUNNER)) {
			return false;
		}
		double top = AthleticsRules.scrambleTop(state.jumpStartY);
		boolean holding = jumpHeld && player.horizontalCollision && player.getY() < top;
		if (state.scrambling && !holding) {
			state.scrambling = false;
			state.scrambleSpent = true;
			return false;
		}
		boolean starting = !state.scrambling && holding && !state.scrambleSpent;
		if (starting) {
			state.scrambling = true;
		}
		if (state.scrambling) {
			// Never past the top: the last step lands exactly there and the scramble ends.
			double up = Math.min(AthleticsRules.SCRAMBLE_SPEED, top - player.getY());
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, Math.max(v.y, up), v.z);
			player.resetFallDistance();
		}
		return starting;
	}
}
