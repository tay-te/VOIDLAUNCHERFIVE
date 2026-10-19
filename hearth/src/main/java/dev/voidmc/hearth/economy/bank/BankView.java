package dev.voidmc.hearth.economy.bank;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the bank counter (or the town hall's) shows: the player's account and the money changer, property and businesses
 * for sale, public works to fund, and supply contracts.
 *
 * @param settlement      the settlement's name
 * @param tab             {@link #TAB_BANK}, {@link #TAB_PROPERTY}, {@link #TAB_WORKS} or {@link #TAB_CONTRACTS}: which to show first
 * @param deposit         the player's deposit (honoured at every bank)
 * @param carried         coin the player carries
 * @param treasury        coin in the town's treasury
 * @param emeralds        emeralds the player carries
 * @param emeraldReserve  emeralds the changer has to sell
 * @param emeraldBuy      bits the changer pays for an emerald
 * @param emeraldSell     bits the changer asks for an emerald
 * @param reputation      the player's standing here
 * @param hasBank         whether this counter is a bank (deposits, changer) or only sells property (a town hall)
 * @param plots           property and businesses for sale (and the player's own here)
 * @param status          the result of the last action
 * @param hasWorks        whether this counter also takes funding and contracts (a bank or a town hall)
 * @param projects        public works the player may fund
 * @param contracts       supply contracts on offer, and the player's own
 * @param ledger          the player's last bank dealings, newest first ({@link BankLedger})
 */
public record BankView(String settlement, int tab, long deposit, long carried, long treasury, int emeralds, int emeraldReserve, int emeraldBuy,
	int emeraldSell, int reputation, boolean hasBank, List<PlotOffer> plots, Component status, boolean hasWorks, List<ProjectOffer> projects,
	List<ContractOffer> contracts, List<BankLedger.Line> ledger) {

	/** A view without the ledger (2026-10-19 addition). */
	public BankView(String settlement, int tab, long deposit, long carried, long treasury, int emeralds, int emeraldReserve, int emeraldBuy,
		int emeraldSell, int reputation, boolean hasBank, List<PlotOffer> plots, Component status, boolean hasWorks, List<ProjectOffer> projects,
		List<ContractOffer> contracts) {
		this(settlement, tab, deposit, carried, treasury, emeralds, emeraldReserve, emeraldBuy, emeraldSell, reputation, hasBank, plots, status,
			hasWorks, projects, contracts, List.of());
	}

	public static final int TAB_BANK = 0;
	public static final int TAB_PROPERTY = 1;
	public static final int TAB_WORKS = 2;
	public static final int TAB_CONTRACTS = 3;

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
	 * @param flags         {@link #YOURS}, {@link #LOW_STANDING}, {@link #CANNOT_AFFORD}, {@link #BUSINESS}, {@link #CAN_HIRE}
	 * @param income        a business's income a day: expected (for sale) or yesterday's (yours)
	 * @param workers       a business's workers
	 */
	public record PlotOffer(int plot, String building, boolean built, int sizeX, int sizeZ, int storeys, long price, int minReputation, int flags,
		long income, int workers) {
		public static final int YOURS = 1;
		public static final int LOW_STANDING = 2;
		public static final int CANNOT_AFFORD = 4;
		/** A business: its folk keep working and its income goes to the owner. For your own, {@code price} is the hiring cost. */
		public static final int BUSINESS = 8;
		/** Your business has room and there is someone to hire. */
		public static final int CAN_HIRE = 16;

		/** A house or plot. */
		public PlotOffer(int plot, String building, boolean built, int sizeX, int sizeZ, int storeys, long price, int minReputation, int flags) {
			this(plot, building, built, sizeX, sizeZ, storeys, price, minReputation, flags, 0, 0);
		}

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
			ByteBufCodecs.VAR_LONG.encode(buf, p.income);
			ByteBufCodecs.VAR_INT.encode(buf, p.workers);
		}, buf -> new PlotOffer(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
	}

	/**
	 * A public building the player may pay toward.
	 *
	 * @param plot      plot id
	 * @param building  archetype path
	 * @param rising    under construction (true) or only planned
	 * @param stage     the stage it is at
	 * @param stages    its stages in all
	 * @param cost      what its unfinished stages' materials are worth at the town's prices (0: unknown)
	 * @param given     what players have given toward it
	 * @param yours     what this player has given
	 * @param fund      coin given and not yet spent
	 * @param stalled   waiting for goods now
	 */
	public record ProjectOffer(int plot, String building, boolean rising, int stage, int stages, long cost, long given, long yours, long fund,
		boolean stalled) {
		static final StreamCodec<RegistryFriendlyByteBuf, ProjectOffer> STREAM_CODEC = StreamCodec.of((buf, p) -> {
			ByteBufCodecs.VAR_INT.encode(buf, p.plot);
			ByteBufCodecs.STRING_UTF8.encode(buf, p.building);
			ByteBufCodecs.BOOL.encode(buf, p.rising);
			ByteBufCodecs.VAR_INT.encode(buf, p.stage);
			ByteBufCodecs.VAR_INT.encode(buf, p.stages);
			ByteBufCodecs.VAR_LONG.encode(buf, p.cost);
			ByteBufCodecs.VAR_LONG.encode(buf, p.given);
			ByteBufCodecs.VAR_LONG.encode(buf, p.yours);
			ByteBufCodecs.VAR_LONG.encode(buf, p.fund);
			ByteBufCodecs.BOOL.encode(buf, p.stalled);
		}, buf -> new ProjectOffer(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.BOOL.decode(buf)));
	}

	/**
	 * A supply contract.
	 *
	 * @param id        the contract's id
	 * @param goods     the goods' serialized name
	 * @param units     units to deliver in all
	 * @param delivered units delivered so far
	 * @param total     what the whole delivery pays
	 * @param daysLeft  days until it is due (taken) or withdrawn (on offer)
	 * @param flags     {@link #TAKEN} (by this player), {@link #CARRYING} (the player carries some of the goods)
	 */
	public record ContractOffer(int id, String goods, int units, int delivered, long total, int daysLeft, int flags) {
		public static final int TAKEN = 1;
		public static final int CARRYING = 2;

		public boolean has(int flag) {
			return (this.flags & flag) != 0;
		}

		static final StreamCodec<RegistryFriendlyByteBuf, ContractOffer> STREAM_CODEC = StreamCodec.of((buf, c) -> {
			ByteBufCodecs.VAR_INT.encode(buf, c.id);
			ByteBufCodecs.STRING_UTF8.encode(buf, c.goods);
			ByteBufCodecs.VAR_INT.encode(buf, c.units);
			ByteBufCodecs.VAR_INT.encode(buf, c.delivered);
			ByteBufCodecs.VAR_LONG.encode(buf, c.total);
			ByteBufCodecs.VAR_INT.encode(buf, c.daysLeft);
			ByteBufCodecs.VAR_INT.encode(buf, c.flags);
		}, buf -> new ContractOffer(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
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
		ByteBufCodecs.BOOL.encode(buf, v.hasWorks);
		ProjectOffer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, v.projects);
		ContractOffer.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, v.contracts);
		BankLedger.Line.STREAM_CODEC.apply(ByteBufCodecs.list(BankLedger.MAX)).encode(buf, v.ledger.size() > BankLedger.MAX ? v.ledger.subList(0, BankLedger.MAX) : v.ledger);
	}

	private static BankView read(RegistryFriendlyByteBuf buf) {
		return new BankView(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf),
			PlotOffer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf), ByteBufCodecs.BOOL.decode(buf),
			ProjectOffer.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf), ContractOffer.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf),
			BankLedger.Line.STREAM_CODEC.apply(ByteBufCodecs.list(BankLedger.MAX)).decode(buf));
	}
}
