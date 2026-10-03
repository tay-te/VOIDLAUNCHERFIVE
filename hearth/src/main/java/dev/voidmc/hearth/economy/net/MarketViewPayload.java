package dev.voidmc.hearth.economy.net;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.economy.market.MarketView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The market as it stands after a trade, server to client. */
public record MarketViewPayload(int containerId, MarketView view) implements CustomPacketPayload {
	public static final Type<MarketViewPayload> TYPE = new Type<>(Hearth.id("economy/market_view"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MarketViewPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, MarketViewPayload::containerId,
		MarketView.STREAM_CODEC, MarketViewPayload::view,
		MarketViewPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
