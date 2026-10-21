package dev.voidmc.fray.motion;

import dev.voidmc.fray.Fray;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fray Motion's payloads (CONTRACTS.md §15). One packet per action, never per tick. */
public final class MotionPayloads {
	private MotionPayloads() {
	}

	/**
	 * Server → trackers: entity {@code entity} plays clip {@code clip} (an index into the clip set both sides agreed on at
	 * login) on {@code layer}, started at server tick {@code startTick}.
	 */
	public record Play(int entity, int clip, long startTick, float speed, byte layer) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(Fray.id("motion/play"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Play::entity, ByteBufCodecs.VAR_INT, Play::clip, ByteBufCodecs.VAR_LONG, Play::startTick, ByteBufCodecs.FLOAT, Play::speed,
			ByteBufCodecs.BYTE, Play::layer, Play::new);

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	/** Server → trackers: stop a layer, blending out over {@code blendTicks}. */
	public record Stop(int entity, byte layer, byte blendTicks) implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(Fray.id("motion/stop"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Stop> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Stop::entity, ByteBufCodecs.BYTE, Stop::layer, ByteBufCodecs.BYTE, Stop::blendTicks, Stop::new);

		@Override
		public Type<Stop> type() {
			return TYPE;
		}
	}

	/** Client → server: the local player started an action (already playing it: prediction). */
	public record ActionRequest(int clip, int seq, long clientTick) implements CustomPacketPayload {
		public static final Type<ActionRequest> TYPE = new Type<>(Fray.id("action/request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionRequest> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ActionRequest::clip, ByteBufCodecs.VAR_INT, ActionRequest::seq, ByteBufCodecs.VAR_LONG, ActionRequest::clientTick,
			ActionRequest::new);

		@Override
		public Type<ActionRequest> type() {
			return TYPE;
		}
	}

	/** Server → the acting player: accepted or not, and the server tick it started on. */
	public record ActionAck(int seq, boolean accepted, long startTick) implements CustomPacketPayload {
		public static final Type<ActionAck> TYPE = new Type<>(Fray.id("action/ack"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionAck> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ActionAck::seq, ByteBufCodecs.BOOL, ActionAck::accepted, ByteBufCodecs.VAR_LONG, ActionAck::startTick, ActionAck::new);

		@Override
		public Type<ActionAck> type() {
			return TYPE;
		}
	}

	/** Configuration phase, both ways: protocol, the clip set's hash and size (the family handshake, CONTRACTS.md §16). */
	public record Hello(int protocol, String version, String clipSet, int clips) implements CustomPacketPayload {
		public static final Type<Hello> TYPE = new Type<>(Fray.id("hello"));
		public static final StreamCodec<ByteBuf, Hello> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Hello::protocol, ByteBufCodecs.stringUtf8(32), Hello::version, ByteBufCodecs.stringUtf8(64), Hello::clipSet,
			ByteBufCodecs.VAR_INT, Hello::clips, Hello::new);

		@Override
		public Type<Hello> type() {
			return TYPE;
		}
	}
}
