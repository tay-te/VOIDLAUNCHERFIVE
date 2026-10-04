package dev.voidmc.hearth.social.dice;

import dev.voidmc.hearth.Hearth;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The dice table's payloads: clicks up ({@link Action}), the table as it stands down ({@link View}). */
public final class DicePayloads {
	private DicePayloads() {
	}

	/**
	 * A click at the table.
	 *
	 * @param action {@link #STAKE} (arg: index into the offered stakes), {@link #TOGGLE} (arg: die), {@link #ROLL},
	 *               {@link #BANK}, {@link #PASS}, {@link #LOADED} (arg: 1 to play them, 0 to put them away), {@link #AGAIN}
	 */
	public record Action(int containerId, int action, int arg) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Hearth.id("social/dice_action"));
		public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Action::containerId,
			ByteBufCodecs.VAR_INT, Action::action,
			ByteBufCodecs.VAR_INT, Action::arg,
			Action::new);

		public static final int STAKE = 1;
		public static final int TOGGLE = 2;
		public static final int ROLL = 3;
		public static final int BANK = 4;
		public static final int PASS = 5;
		public static final int LOADED = 6;
		public static final int AGAIN = 7;

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record View(int containerId, DiceView view) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(Hearth.id("social/dice_view"));
		public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, View::containerId,
			DiceView.STREAM_CODEC, View::view,
			View::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
