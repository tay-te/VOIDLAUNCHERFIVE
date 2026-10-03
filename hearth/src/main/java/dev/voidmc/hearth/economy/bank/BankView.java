package dev.voidmc.hearth.economy.bank;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the bank counter shows: the player's account and the money changer on one tab, property for sale on the other.
 *
 * @param settlement      the settlement's name
 * @param tab             {@link #TAB_BANK} or {@link #TAB_PROPERTY}: which one to show first
 * @param deposit         the player's deposit (honoured at every bank)
 * @param carried         coin the player carries
 * @param treasury        coin in the town's treasury
 * @param emeralds        emeralds the player carries
 * @param emeraldReserve  emeralds the changer has to sell
 * @param emeraldBuy      bits the changer pays for an emerald
 * @param emeraldSell     bits the changer asks for an emerald
 * @param reputation      the player's standing here
 * @param hasBank         whether this counter is a bank (deposits, changer) or only sells property (a town hall)
 * @param plots           property for sale (and the player's own here)
 * @param status          the result of the last action
 */
public record BankView(String settlement, int tab, long deposit, long carried, long treasury, int emeralds, int emeraldReserve, int emeraldBuy,
	int emeraldSell, int reputation, boolean hasBank, List<PlotOffer> plots, Component status) {

	public static final int TAB_BANK = 0;
	public static final int TAB_PROPERTY = 1;

	public static final StreamCodec<RegistryFriendlyByteBuf, BankView> STREAM_CODEC = StreamCodec.of(BankView::write, BankView::read);

	/**
	 * A house or plot for sale.
	 *
	 * @param plot          plot id
	 * @param building      archetype path
	 * @param built         a house (true) or empty land (false)
	 * @param sizeX         footprint
	 * @param sizeZ         footprint
	 * @param storeys       storeys (houses)
	 * @param price         asking price in bits (with the player's reputation discount)
	 * @param minReputation standing needed to buy
	 * @param flags         {@link #YOURS}, {@link #LOW_STANDING}, {@link #CANNOT_AFFORD}
	 */
	public record PlotOffer(int plot, String building, boolean built, int sizeX, int sizeZ, int storeys, long price, int minReputation, int flags) {
		public static final int YOURS = 1;
		public static final int LOW_STANDING = 2;
		public static final int CANNOT_AFFORD = 4;

		public boolean has(int flag) {
			return (this.flags & flag) != 0;
		}

		static final StreamCodec<RegistryFriendlyByteBuf, PlotOffer> STREAM_CODEC = StreamCodec.of((buf, p) -> {
			ByteBufCodecs.VAR_INT.encode(buf, p.plot);
			ByteBufCodecs.STRING_UTF8.encode(buf, p.building);
			ByteBufCodecs.BOOL.encode(buf, p.built);
			ByteBufCodecs.VAR_INT.encode(buf, p.sizeX);
			ByteBufCodecs.VAR_INT.encode(buf, p.sizeZ);
			ByteBufCodecs.VAR_INT.encode(buf, p.storeys);
			ByteBufCodecs.VAR_LONG.encode(buf, p.price);
			ByteBufCodecs.VAR_INT.encode(buf, p.minReputation);
			ByteBufCodecs.VAR_INT.encode(buf, p.flags);
		}, buf -> new PlotOffer(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
	}

	private static void write(RegistryFriendlyByteBuf buf, BankView v) {
		ByteBufCodecs.STRING_UTF8.encode(buf, v.settlement);
		ByteBufCodecs.VAR_INT.encode(buf, v.tab);
		ByteBufCodecs.VAR_LONG.encode(buf, v.deposit);
		ByteBufCodecs.VAR_LONG.encode(buf, v.carried);
		ByteBufCodecs.VAR_LONG.encode(buf, v.treasury);
		ByteBufCodecs.VAR_INT.encode(buf, v.emeralds);
		ByteBufCodecs.VAR_INT.encode(buf, v.emeraldReserve);
		ByteBufCodecs.VAR_INT.encode(buf, v.emeraldBuy);
		ByteBufCodecs.VAR_INT.encode(buf, v.emeraldSell);
		ByteBufCodecs.VAR_INT.encode(buf, v.reputation);
		ByteBufCodecs.BOOL.encode(buf, v.hasBank);
		PlotOffer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, v.plots);
		ComponentSerialization.STREAM_CODEC.encode(buf, v.status);
	}

	private static BankView read(RegistryFriendlyByteBuf buf) {
		return new BankView(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf),
			PlotOffer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf));
	}
}
