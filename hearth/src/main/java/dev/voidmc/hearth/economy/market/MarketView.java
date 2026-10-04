package dev.voidmc.hearth.economy.market;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the trade screen shows, decided on the server and sent whole when the market opens and after every trade. The
 * client only draws it and sends back which row was clicked.
 *
 * @param settlement the settlement's name
 * @param culture    its culture's serialized name (for the subtitle)
 * @param reputation the player's standing there
 * @param balance    coin the player carries
 * @param treasury   coin the counter can pay out: the shop's till, or the town's treasury at the town's market
 * @param note       what the merchant says (what the builders need, what is plentiful...)
 * @param status     the result of the last trade, or empty
 * @param sells      what the market sells (the player buys)
 * @param buys       what the market buys (the player sells): the player's own goods, and what the builders want
 * @param shop       whether the counter is a shop with its own till (true) or the town's market (false)
 * @param haggle     the haggle under way, or null
 */
public record MarketView(String settlement, String culture, int reputation, long balance, long treasury, Component note, Component status,
	List<Offer> sells, List<Offer> buys, boolean shop, @Nullable HaggleView haggle) {

	public static final StreamCodec<RegistryFriendlyByteBuf, MarketView> STREAM_CODEC = StreamCodec.of(MarketView::write, MarketView::read);

	public static final int NEEDED = 1;
	public static final int TOWN_CANNOT_PAY = 2;
	public static final int NO_ROOM = 4;
	public static final int CANNOT_AFFORD = 8;
	public static final int REFUSED = 16;
	/** Stolen in this town: the merchant will not touch it. */
	public static final int STOLEN = 32;
	/** Stolen, and this fence buys it, cheaply. */
	public static final int FENCED = 64;
	/** The merchant is sour with the player: worse prices and no haggling. */
	public static final int SOUR = 128;

	/**
	 * One row.
	 *
	 * @param id        the server's id for this row (sent back when clicked)
	 * @param stack     the item, with the lot size as its count
	 * @param price     price of one lot in bits
	 * @param available lots that can trade now (0: the row is shown but greyed, see flags)
	 * @param owned     for rows the market buys: how many of the item the player carries
	 * @param flags     {@link #NEEDED}, {@link #TOWN_CANNOT_PAY}, {@link #NO_ROOM}, {@link #CANNOT_AFFORD}, {@link #REFUSED},
	 *                  {@link #STOLEN}, {@link #FENCED}, {@link #SOUR}
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

	/**
	 * A haggle in progress.
	 *
	 * @param row        the row it is over
	 * @param stack      the goods, with the whole deal's count
	 * @param playerBuys whether the player is buying
	 * @param list       the posted price of the whole deal
	 * @param counter    the merchant's last word
	 * @param lastOffer  the player's last offer
	 * @param patience   the merchant's patience left, 0..100
	 * @param line       what the merchant just said
	 */
	public record HaggleView(int row, ItemStack stack, boolean playerBuys, long list, long counter, long lastOffer, int patience, Component line) {
		static final StreamCodec<RegistryFriendlyByteBuf, HaggleView> STREAM_CODEC = StreamCodec.of((buf, h) -> {
			ByteBufCodecs.VAR_INT.encode(buf, h.row);
			ItemStack.STREAM_CODEC.encode(buf, h.stack);
			ByteBufCodecs.BOOL.encode(buf, h.playerBuys);
			ByteBufCodecs.VAR_LONG.encode(buf, h.list);
			ByteBufCodecs.VAR_LONG.encode(buf, h.counter);
			ByteBufCodecs.VAR_LONG.encode(buf, h.lastOffer);
			ByteBufCodecs.VAR_INT.encode(buf, h.patience);
			ComponentSerialization.STREAM_CODEC.encode(buf, h.line);
		}, buf -> new HaggleView(ByteBufCodecs.VAR_INT.decode(buf), ItemStack.STREAM_CODEC.decode(buf), ByteBufCodecs.BOOL.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ComponentSerialization.STREAM_CODEC.decode(buf)));
	}

	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<HaggleView>> OPTIONAL_HAGGLE = ByteBufCodecs.optional(HaggleView.STREAM_CODEC);

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
		ByteBufCodecs.BOOL.encode(buf, v.shop);
		OPTIONAL_HAGGLE.encode(buf, Optional.ofNullable(v.haggle));
	}

	private static MarketView read(RegistryFriendlyByteBuf buf) {
		return new MarketView(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
			ComponentSerialization.STREAM_CODEC.decode(buf), Offer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
			Offer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf), ByteBufCodecs.BOOL.decode(buf), OPTIONAL_HAGGLE.decode(buf).orElse(null));
	}
}
