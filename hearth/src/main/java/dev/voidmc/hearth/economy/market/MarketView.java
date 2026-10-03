package dev.voidmc.hearth.economy.market;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What the trade screen shows, decided on the server and sent whole when the market opens and after every trade. The
 * client only draws it and sends back which row was clicked.
 *
 * @param settlement the settlement's name
 * @param culture    its culture's serialized name (for the subtitle)
 * @param reputation the player's standing there
 * @param balance    coin the player carries
 * @param treasury   coin in the town's treasury
 * @param note       what the merchant says (what the builders need, what is plentiful...)
 * @param status     the result of the last trade, or empty
 * @param sells      what the market sells (the player buys)
 * @param buys       what the market buys (the player sells): the player's own goods, and what the builders want
 */
public record MarketView(String settlement, String culture, int reputation, long balance, long treasury, Component note, Component status,
	List<Offer> sells, List<Offer> buys) {

	public static final StreamCodec<RegistryFriendlyByteBuf, MarketView> STREAM_CODEC = StreamCodec.of(MarketView::write, MarketView::read);

	public static final int NEEDED = 1;
	public static final int TOWN_CANNOT_PAY = 2;
	public static final int NO_ROOM = 4;
	public static final int CANNOT_AFFORD = 8;
	public static final int REFUSED = 16;

	/**
	 * One row.
	 *
	 * @param id        the server's id for this row (sent back when clicked)
	 * @param stack     the item, with the lot size as its count
	 * @param price     price of one lot in bits
	 * @param available lots that can trade now (0: the row is shown but greyed, see flags)
	 * @param owned     for rows the market buys: how many of the item the player carries
	 * @param flags     {@link #NEEDED}, {@link #TOWN_CANNOT_PAY}, {@link #NO_ROOM}, {@link #CANNOT_AFFORD}, {@link #REFUSED}
	 * @param goods     the goods' serialized name
	 */
	public record Offer(int id, ItemStack stack, long price, int available, int owned, int flags, String goods) {
		public boolean has(int flag) {
			return (this.flags & flag) != 0;
		}

		static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.of((buf, o) -> {
			ByteBufCodecs.VAR_INT.encode(buf, o.id);
			ItemStack.STREAM_CODEC.encode(buf, o.stack);
			ByteBufCodecs.VAR_LONG.encode(buf, o.price);
			ByteBufCodecs.VAR_INT.encode(buf, o.available);
			ByteBufCodecs.VAR_INT.encode(buf, o.owned);
			ByteBufCodecs.VAR_INT.encode(buf, o.flags);
			ByteBufCodecs.STRING_UTF8.encode(buf, o.goods);
		}, buf -> new Offer(ByteBufCodecs.VAR_INT.decode(buf), ItemStack.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.STRING_UTF8.decode(buf)));
	}

	private static void write(RegistryFriendlyByteBuf buf, MarketView v) {
		ByteBufCodecs.STRING_UTF8.encode(buf, v.settlement);
		ByteBufCodecs.STRING_UTF8.encode(buf, v.culture);
		ByteBufCodecs.VAR_INT.encode(buf, v.reputation);
		ByteBufCodecs.VAR_LONG.encode(buf, v.balance);
		ByteBufCodecs.VAR_LONG.encode(buf, v.treasury);
		ComponentSerialization.STREAM_CODEC.encode(buf, v.note);
		ComponentSerialization.STREAM_CODEC.encode(buf, v.status);
		Offer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, v.sells);
		Offer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, v.buys);
	}

	private static MarketView read(RegistryFriendlyByteBuf buf) {
		return new MarketView(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
			ComponentSerialization.STREAM_CODEC.decode(buf), Offer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
			Offer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf));
	}
}
