package dev.voidmc.hearth.economy.net;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.economy.bank.BankView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The bank counter as it stands after an action, server to client. */
public record BankViewPayload(int containerId, BankView view) implements CustomPacketPayload {
	public static final Type<BankViewPayload> TYPE = new Type<>(Hearth.id("economy/bank_view"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BankViewPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BankViewPayload::containerId,
		BankView.STREAM_CODEC, BankViewPayload::view,
		BankViewPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
