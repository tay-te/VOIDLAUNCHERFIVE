package dev.voidmc.mettle.character.cards.legend;

import dev.voidmc.mettle.Mettle;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The packets of the sixteen drawn Mythics, registered on both sides from common init. */
public final class LegendPayloads {
	private LegendPayloads() {
	}

	/** Server → client: the Long Watch, outline these entities for {@code ticks} for this player alone. */
	public record Watch(List<Integer> entities, int ticks) implements CustomPacketPayload {
		public static final Type<Watch> TYPE = new Type<>(Mettle.id("legend/watch"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Watch> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), Watch::entities,
			ByteBufCodecs.VAR_INT, Watch::ticks,
			Watch::new);

		@Override
		public Type<Watch> type() {
			return TYPE;
		}
	}

	/** Client → server: the Cat's Way, the client began running along a wall (its own movement). */
	public record WallRun() implements CustomPacketPayload {
		public static final Type<WallRun> TYPE = new Type<>(Mettle.id("legend/wall_run"));
		public static final StreamCodec<RegistryFriendlyByteBuf, WallRun> STREAM_CODEC = StreamCodec.unit(new WallRun());

		@Override
		public Type<WallRun> type() {
			return TYPE;
		}
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Watch.TYPE, Watch.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WallRun.TYPE, WallRun.STREAM_CODEC);
	}
}
