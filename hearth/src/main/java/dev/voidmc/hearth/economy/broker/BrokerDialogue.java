package dev.voidmc.hearth.economy.broker;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.VirtualGoods;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The broker's table in conversation (docs/requests/mettle-cards.md §7), until the broker's screen is designed in Figma
 * (docs/HUB_UI.md): a merchant in a town or city's market offers "The card table"; at the table the player sees the
 * cheapest listings to buy, what they may list, and their own listings to take back. Listing walks through a price
 * (start at the going rate of that kind, ask more or less, then list at that price with the fee shown). Shown only when
 * a mod has registered {@link VirtualGoods}.
 */
public final class BrokerDialogue implements DialogueProvider {
	public static final String PREFIX = "economy:broker";
	static final String TABLE = PREFIX;
	static final String BUY = PREFIX + "/buy/";
	static final String SELL = PREFIX + "/sell/";
	static final String MINE = PREFIX + "/mine/";
	static final String UP = PREFIX + "/up";
	static final String DOWN = PREFIX + "/down";
	static final String CONFIRM = PREFIX + "/confirm";
	static final String CANCEL = PREFIX + "/cancel";
	static final String LEAVE = PREFIX + "/leave";
	/** Listings to buy shown at once (cheapest first), and goods of the player's offered to list. */
	static final int SHOWN = 6;
	static final int SHOWN_SELL = 4;
	/** A starting price when nothing of the kind is on the board: one crown. */
	static final long DEFAULT_PRICE = 100;

	/** Who sits at whose table (player → resident). */
	private final Map<UUID, UUID> seated = new HashMap<>();
	/** A listing being priced (player → it). */
	private final Map<UUID, Pending> pricing = new HashMap<>();
	private final Map<UUID, Component> answers = new HashMap<>();

	private record Pending(UUID resident, String kind, long goods, long price) {
	}

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		if (HearthServices.virtualGoods().isEmpty() || !Broker.keeps(settlement) || !isBroker(settlement, resident)) {
			return;
		}
		UUID id = player.getUUID();
		long today = EconomyModule.day(player.level());
		Pending p = this.pricing.get(id);
		if (p != null && p.resident.equals(resident.id)) {
			VirtualGoods goods = HearthServices.virtualGoods(p.kind);
			Component name = goods == null ? Component.literal(p.kind) : goods.name(p.goods);
			add(out, UP, Component.translatable("hearth.economy.broker.up", name), 141, null, null, -1);
			add(out, DOWN, Component.translatable("hearth.economy.broker.down", name), 142, null, null, -1);
			long fee = Broker.fee(p.price);
			EconomyService economy = HearthServices.economy();
			boolean canPay = economy != null && economy.balance(player) >= fee;
			add(out, CONFIRM, Component.translatable("hearth.economy.broker.confirm", name, CoinFormat.format(p.price)), 143,
				Component.translatable("hearth.economy.broker.fee", CoinFormat.format(fee)), canPay ? null
					: Component.translatable("hearth.economy.broker.result.cannot_pay_fee"), fee, !canPay);
			add(out, CANCEL, Component.translatable("hearth.economy.broker.cancel"), 144, null, null, -1);
			return;
		}
		if (!resident.id.equals(this.seated.get(id))) {
			add(out, TABLE, Component.translatable("hearth.economy.broker.table"), 140, null, null, -1);
			return;
		}
		Broker.expire(settlement, today);
		BrokerBoard board = Broker.board(settlement);
		EconomyService economy = HearthServices.economy();
		long balance = economy == null ? 0 : economy.balance(player);
		int n = 0;
		for (BrokerBoard.Listing l : board.forBuyer(id, today)) {
			VirtualGoods goods = HearthServices.virtualGoods(l.kind());
			if (goods == null) {
				continue;
			}
			if (n++ >= SHOWN) {
				break;
			}
			boolean afford = balance >= l.price();
			add(out, BUY + l.id(), Component.translatable("hearth.economy.broker.buy", goods.name(l.goods())), 141, CoinFormat.format(l.price()),
				tooltip(goods, l), l.price(), !afford);
		}
		n = 0;
		for (VirtualGoods goods : HearthServices.virtualGoods()) {
			for (long g : goods.listable(player)) {
				if (board.listed(goods.kind(), g)) {
					continue;
				}
				if (n++ >= SHOWN_SELL) {
					break;
				}
				add(out, SELL + goods.kind() + "/" + g, Component.translatable("hearth.economy.broker.sell", goods.name(g)), 146, null,
					lines(goods.describe(g)), -1);
			}
		}
		for (BrokerBoard.Listing l : board.bySeller(id)) {
			add(out, MINE + l.id(), Component.translatable("hearth.economy.broker.mine", Broker.name(l)), 147, CoinFormat.format(l.price()),
				Component.translatable("hearth.economy.broker.until", Math.max(0, l.expires() - today)), -1);
		}
		add(out, LEAVE, Component.translatable("hearth.economy.broker.leave"), 149, null, null, -1);
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		if (!optionId.startsWith(PREFIX) || !Broker.keeps(settlement) || !isBroker(settlement, resident)) {
			return false;
		}
		UUID id = player.getUUID();
		long today = EconomyModule.day(player.level());
		this.answers.remove(id);
		if (optionId.equals(TABLE)) {
			this.seated.put(id, resident.id);
			this.pricing.remove(id);
			answer(id, Component.translatable("hearth.economy.broker.welcome"));
			return true;
		}
		if (optionId.equals(LEAVE)) {
			this.seated.remove(id);
			this.pricing.remove(id);
			return true;
		}
		Pending p = this.pricing.get(id);
		switch (optionId) {
			case UP, DOWN -> {
				if (p == null) {
					return false;
				}
				this.pricing.put(id, new Pending(p.resident, p.kind, p.goods, step(p.price, optionId.equals(UP))));
				return true;
			}
			case CONFIRM -> {
				if (p == null) {
					return false;
				}
				this.pricing.remove(id);
				Broker.Result r = Broker.list(player, settlement, p.kind, p.goods, p.price, today);
				answer(id, Component.translatable(r.translationKey()));
				return true;
			}
			case CANCEL -> {
				this.pricing.remove(id);
				return true;
			}
			default -> {
			}
		}
		if (!resident.id.equals(this.seated.get(id))) {
			return false;
		}
		try {
			if (optionId.startsWith(BUY)) {
				Broker.Result r = Broker.buy(player, settlement, Long.parseLong(optionId.substring(BUY.length())), today);
				answer(id, Component.translatable(r.translationKey()));
				return true;
			}
			if (optionId.startsWith(MINE)) {
				Broker.Result r = Broker.withdraw(player, settlement, Long.parseLong(optionId.substring(MINE.length())));
				answer(id, Component.translatable(r.translationKey()));
				return true;
			}
			if (optionId.startsWith(SELL)) {
				String rest = optionId.substring(SELL.length());
				int slash = rest.lastIndexOf('/');
				String kind = rest.substring(0, slash);
				long goods = Long.parseLong(rest.substring(slash + 1));
				this.pricing.put(id, new Pending(resident.id, kind, goods, goingRate(Broker.board(settlement), kind, today)));
				answer(id, Component.translatable("hearth.economy.broker.ask"));
				return true;
			}
		} catch (RuntimeException e) {
			return false; // a malformed id
		}
		return false;
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return this.answers.remove(player.getUUID());
	}

	/** Forgets a player (logged out). */
	public void forget(UUID player) {
		this.seated.remove(player);
		this.pricing.remove(player);
		this.answers.remove(player);
	}

	/** The next asking price: a quarter more, or a fifth less (at least a bit either way), within 1 and {@link Broker#MAX_PRICE}. */
	public static long step(long price, boolean up) {
		long p = Math.max(1, price);
		long next = up ? Math.max(p + 1, Math.round(p * 1.25)) : Math.min(p - 1, Math.round(p * 0.8));
		return Math.clamp(next, 1, Broker.MAX_PRICE);
	}

	/** Where a new listing's price starts: the median asking price of that kind on the board, else a crown. */
	public static long goingRate(BrokerBoard board, String kind, long today) {
		List<Long> prices = new ArrayList<>();
		for (BrokerBoard.Listing l : board.all()) {
			if (l.kind().equals(kind) && l.expires() > today) {
				prices.add(l.price());
			}
		}
		if (prices.isEmpty()) {
			return DEFAULT_PRICE;
		}
		prices.sort(Long::compare);
		return prices.get(prices.size() / 2);
	}

	/** Merchants and traders, and anyone working at a market stall or hall. */
	static boolean isBroker(Settlement s, Resident r) {
		if (r.job == JobType.MERCHANT || r.job == JobType.TRADER) {
			return true;
		}
		if (r.work == Resident.NO_PLOT) {
			return false;
		}
		PlotView plot = s.plot(r.work);
		return plot != null && plot.spec().archetype().getPath().startsWith("commerce/market");
	}

	private void answer(UUID player, Component line) {
		this.answers.put(player, line);
	}

	private static Component tooltip(VirtualGoods goods, BrokerBoard.Listing l) {
		MutableComponent t = Component.translatable("hearth.economy.broker.seller", l.name()).copy();
		for (Component c : goods.describe(l.goods())) {
			t.append("\n").append(c);
		}
		return t;
	}

	private static @Nullable Component lines(List<Component> lines) {
		if (lines.isEmpty()) {
			return null;
		}
		MutableComponent t = Component.empty();
		for (int k = 0; k < lines.size(); k++) {
			if (k > 0) {
				t.append("\n");
			}
			t.append(lines.get(k));
		}
		return t;
	}

	private static void add(List<Option> out, String id, Component label, int order, @Nullable Component hint, @Nullable Component tooltip, long cost) {
		add(out, id, label, order, hint, tooltip, cost, false);
	}

	private static void add(List<Option> out, String id, Component label, int order, @Nullable Component hint, @Nullable Component tooltip, long cost,
		boolean locked) {
		out.add(new Option(id, label, order, locked, hint, tooltip));
		DialogueProvider.describe(id, new Info("trade", -1, cost, null));
	}
}
