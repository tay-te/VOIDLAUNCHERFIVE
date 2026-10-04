package dev.voidmc.hearth.economy.net;

import dev.voidmc.hearth.Hearth;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A click in a market or bank screen, client to server. The server checks it against the player's open menu and decides
 * everything; the client never says what anything costs.
 *
 * @param containerId the menu it was clicked in
 * @param action      {@link #TRADE}, {@link #DEPOSIT}, ...
 * @param index       a row id (market) or plot id (property)
 * @param amount      bits or a count, by action ({@link #ALL} for everything)
 */
public record EconomyActionPayload(int containerId, int action, int index, long amount) implements CustomPacketPayload {
	public static final Type<EconomyActionPayload> TYPE = new Type<>(Hearth.id("economy/action"));
	public static final StreamCodec<ByteBuf, EconomyActionPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, EconomyActionPayload::containerId,
		ByteBufCodecs.VAR_INT, EconomyActionPayload::action,
		ByteBufCodecs.VAR_INT, EconomyActionPayload::index,
		ByteBufCodecs.VAR_LONG, EconomyActionPayload::amount,
		EconomyActionPayload::new);

	/** Market row clicked: {@code index} the row id, {@code amount} 1 for a bulk (shift) click. */
	public static final int TRADE = 1;
	/** Market: start haggling over row {@code index}, {@code amount} 1 for a bulk deal. */
	public static final int HAGGLE_START = 2;
	/** Haggling: the player offers {@code amount} bits for the whole deal. */
	public static final int HAGGLE_OFFER = 3;
	/** Haggling: the player takes the merchant's last word. */
	public static final int HAGGLE_ACCEPT = 4;
	/** Haggling: the player walks away (no harm done). */
	public static final int HAGGLE_CANCEL = 5;
	/** Bank: put {@code amount} bits of carried coin on deposit. */
	public static final int DEPOSIT = 10;
	/** Bank: take {@code amount} bits off deposit as coin. */
	public static final int WITHDRAW = 11;
	/** Money changer: sell {@code amount} emeralds for coin. */
	public static final int SELL_EMERALDS = 12;
	/** Money changer: buy {@code amount} emeralds with coin. */
	public static final int BUY_EMERALDS = 13;
	/** Property: buy plot {@code index} (a house, land, or a business). */
	public static final int BUY_PLOT = 14;
	/** Works: give {@code amount} bits toward plot {@code index}. */
	public static final int FUND = 15;
	/** Contracts: take contract {@code index}. */
	public static final int ACCEPT_CONTRACT = 16;
	/** Contracts: deliver what you carry toward contract {@code index}. */
	public static final int DELIVER = 17;
	/** Property: hire an idle resident into your business at plot {@code index}. */
	public static final int HIRE = 18;
	public static final long ALL = -1;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
