package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.embody.TownState;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.talk.TownNews;
import dev.voidmc.hearth.sim.roads.Journey;
import dev.voidmc.hearth.sim.roads.Roads;
import dev.voidmc.hearth.sim.roads.Traffic;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What travellers say (docs/LIVING_WORLD_2.md §1.1, "Talking to travellers"): where they are bound, what they carry,
 * news from where they came from, how the road ahead is — bandits past the ford, or quiet — and a pedlar's few wares
 * for coin. Their lines are in {@code lw_roads.json}.
 */
final class TravellerTalk implements StrangerTalk {
	private final TravelParty party;
	private final Folk folk;
	private final JobType role;
	private final boolean resident;
	private final RandomSource random;

	TravellerTalk(TravelParty party, Folk folk, JobType role, boolean resident) {
		this.party = party;
		this.folk = folk;
		this.role = role;
		this.resident = resident;
		this.random = RandomSource.create(folk.getId() * 31L + party.journeyId);
	}

	/** The word for who they are: "a pedlar", "a caravan guard", "a pilgrim". */
	private String who() {
		return switch (this.party.kind) {
			case PEDLAR -> "pedlar";
			case PILGRIMS -> "pilgrim";
			case MESSENGER -> "messenger";
			case CARTER -> "carter";
			case PATROL -> "patrol";
			case CARAVAN -> this.role == JobType.GUARD ? "guard" : "trader";
			case MARKET_GOERS -> "market";
			case SETTLERS -> "settler";
			default -> "traveller";
		};
	}

	@Override
	public String name() {
		if (this.resident) {
			return this.folk.folkName();
		}
		return Component.translatable("hearth.roads.who." + who(), this.party.fromName.isEmpty() ? "?" : this.party.fromName).getString();
	}

	@Override
	public com.google.gson.JsonObject card(ServerPlayer player) {
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("kind", who());
		o.addProperty("hooded", false);
		o.addProperty("resident", this.resident);
		o.addProperty("bound", this.party.toName);
		o.addProperty("partySize", this.party.size());
		return o;
	}

	@Override
	public String title() {
		return "hearth.npc.job." + this.folk.job().getSerializedName();
	}

	@Override
	public String from() {
		return this.party.fromName;
	}

	@Override
	public Component greeting(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Journey j = journey(level);
		if (j != null && j.resting(Roads.state(dev.voidmc.hearth.sim.SimServer.world(level)).world().clock())) {
			return Component.translatable("hearth.roads.say.camp." + this.random.nextInt(3));
		}
		return Component.translatable("hearth.roads.greet." + who() + "." + this.random.nextInt(3), this.party.toName);
	}

	private @Nullable Journey journey(ServerLevel level) {
		Roads.State st = Roads.state(dev.voidmc.hearth.sim.SimServer.world(level));
		return st == null ? null : st.traffic().journey(this.party.journeyId);
	}

	@Override
	public List<Option> options(ServerPlayer player) {
		List<Option> out = new ArrayList<>();
		out.add(Option.talk("bound", Component.translatable("hearth.roads.opt.bound")));
		out.add(Option.talk("carry", Component.translatable("hearth.roads.opt.carry")));
		if (!this.party.fromName.isEmpty()) {
			out.add(Option.talk("news", Component.translatable("hearth.roads.opt.news", this.party.fromName)));
		}
		out.add(Option.talk("ahead", Component.translatable("hearth.roads.opt.ahead")));
		if (this.party.kind == TravelKind.PEDLAR) {
			for (int i = 0; i < 3; i++) {
				ItemStack ware = ware(i);
				long price = price(player, ware);
				out.add(new Option("buy:" + i, Component.translatable("hearth.roads.opt.buy", ware.getHoverName(), coins(price)), false, null, "trade", -1, price));
			}
		}
		out.add(new Option("bye", Component.translatable("hearth.roads.opt.bye"), false, null, "leave", -1, -1));
		return out;
	}

	@Override
	public @Nullable Component choose(ServerPlayer player, String option) {
		ServerLevel level = (ServerLevel) player.level();
		Roads.State st = Roads.state(dev.voidmc.hearth.sim.SimServer.world(level));
		Journey j = st == null ? null : st.traffic().journey(this.party.journeyId);
		switch (option) {
			case "bound" -> {
				if (this.party.kind == TravelKind.PATROL) {
					return Component.translatable("hearth.roads.say.bound.patrol", this.party.fromName);
				}
				boolean home = j != null && j.returning(st.world().clock());
				return Component.translatable("hearth.roads.say.bound." + (home ? "home" : who()), home ? this.party.fromName : this.party.toName);
			}
			case "carry" -> {
				return carry(j);
			}
			case "news" -> {
				return news(level);
			}
			case "ahead" -> {
				return ahead(st, j);
			}
			case "bye" -> {
				return null;
			}
			default -> {
				if (option.startsWith("buy:")) {
					return buy(player, Integer.parseInt(option.substring(4)));
				}
				return Component.translatable("hearth.roads.say.what");
			}
		}
	}

	private Component carry(@Nullable Journey j) {
		switch (this.party.kind) {
			case PILGRIMS -> {
				return Component.translatable("hearth.roads.say.carry.pilgrim");
			}
			case MESSENGER -> {
				return Component.translatable("hearth.roads.say.carry.messenger", this.party.toName);
			}
			case PEDLAR -> {
				return Component.translatable("hearth.roads.say.carry.pedlar");
			}
			case PATROL -> {
				return Component.translatable("hearth.roads.say.carry.patrol");
			}
			default -> {
			}
		}
		int[] cargo = j == null ? new int[0] : j.details().getIntArray("cargo").orElse(new int[0]);
		int a = -1;
		int b = -1;
		for (int i = 0; i < cargo.length && i < Goods.VALUES.length; i++) {
			if (cargo[i] <= 0) {
				continue;
			}
			if (a < 0 || cargo[i] > cargo[a]) {
				b = a;
				a = i;
			} else if (b < 0 || cargo[i] > cargo[b]) {
				b = i;
			}
		}
		if (a < 0) {
			return Component.translatable(this.party.kind == TravelKind.MARKET_GOERS ? "hearth.roads.say.carry.market" : "hearth.roads.say.carry.none");
		}
		Component goods = Component.translatable(Goods.VALUES[a].translationKey());
		if (b >= 0) {
			goods = Component.translatable("hearth.npc.caravan.cargo.two", goods, Component.translatable(Goods.VALUES[b].translationKey()));
		}
		return Component.translatable("hearth.roads.say.carry.cargo", goods);
	}

	/** The latest news of the town they come from, as the npc module's own folk would tell it. */
	private Component news(ServerLevel level) {
		TownState town = NpcModule.embodiment().townIfKnown(this.party.from);
		long today = dev.voidmc.hearth.sim.SimServer.world(level) == null ? 0 : dev.voidmc.hearth.sim.SimServer.world(level).day();
		List<TownNews.Item> items = town == null ? List.of() : town.news.recent(today, 12);
		if (items.isEmpty()) {
			return Component.translatable("hearth.roads.say.news.none", this.party.fromName);
		}
		TownNews.Item it = items.get(this.random.nextInt(items.size()));
		String place = this.party.fromName;
		return switch (it.kind()) {
			case BUILT -> Component.translatable("hearth.roads.say.news.built", place, it.subject());
			case DIED -> Component.translatable("hearth.roads.say.news.died", it.subject(), place);
			case TIER -> Component.translatable("hearth.roads.say.news.tier", place,
				it.tier() == null ? "" : Component.translatable("hearth.sim.banner.tier." + it.tier().getSerializedName()));
			case STALLED -> Component.translatable("hearth.roads.say.news.short", place,
				it.goods() == null ? "" : Component.translatable(it.goods().translationKey()));
			case ARRIVED -> Component.translatable("hearth.roads.say.news.arrived", it.subject(), place);
			case LEFT -> Component.translatable("hearth.roads.say.news.left", place, it.subject());
			case FOUNDED -> Component.translatable("hearth.roads.say.news.founded", it.subject());
		};
	}

	/** How the road ahead is: bandits on it, a patrol lately, or quiet. */
	private Component ahead(Roads.@Nullable State st, @Nullable Journey j) {
		if (st == null || j == null) {
			return Component.translatable("hearth.roads.say.ahead.quiet");
		}
		Traffic t = st.traffic();
		long now = st.world().clock();
		double along = j.along(now);
		double before = 0;
		for (long id : j.path()) {
			var w = st.net().known(Math.abs(id));
			double len = w == null ? 0 : w.length();
			for (var band : t.bands()) {
				if (band.road() == Math.abs(id)) {
					double at = before + (id > 0 ? band.along() : len - band.along());
					if (at > along || j.returning(now)) {
						Component landmark = w == null ? Component.translatable("hearth.roads.landmark.road") : landmark(st, w, at - before, id > 0);
						return Component.translatable("hearth.roads.say.ahead.bandits." + this.random.nextInt(3), landmark);
					}
				}
			}
			before += len;
		}
		for (long id : j.path()) {
			if (t.danger(Math.abs(id), 0) < 0.05F && st.net().known(Math.abs(id)) != null) {
				return Component.translatable("hearth.roads.say.ahead.patrolled");
			}
		}
		return Component.translatable("hearth.roads.say.ahead.quiet." + this.random.nextInt(2));
	}

	/** Something to know a stretch of road by: the ford, the bridge, the old wood, the hill. */
	private static Component landmark(Roads.State st, dev.voidmc.hearth.sim.roads.Way w, double along, boolean forward) {
		var r = w.route();
		if (r == null) {
			return Component.translatable("hearth.roads.landmark.road");
		}
		// a ford or bridge before the spot, else the lie of the land there
		double acc = 0;
		String mark = "hearth.roads.landmark.road";
		for (int k = 1; k < r.size(); k++) {
			int i = forward ? k : r.size() - 1 - k;
			acc += 1;
			if (acc > along) {
				break;
			}
			if (r.types()[i] == dev.voidmc.hearth.sim.roads.Route.FORD) {
				mark = "hearth.roads.landmark.ford";
			} else if (r.types()[i] == dev.voidmc.hearth.sim.roads.Route.BRIDGE) {
				mark = "hearth.roads.landmark.bridge";
			}
		}
		return Component.translatable(mark);
	}

	// ---------------------------------------------------------------- a pedlar's wares

	private static final Goods[] WARES = {Goods.CLOTH, Goods.DYE, Goods.SALT, Goods.CANDLES, Goods.LUXURY, Goods.LEATHER, Goods.PAPER, Goods.TOOLS};

	private ItemStack ware(int i) {
		Goods g = WARES[(int) Math.floorMod(this.party.journeyId * 7 + i * 3L, WARES.length)];
		return HearthServices.goodsItems().sample(g, this.party.culture, this.party.journeyId * 13 + i);
	}

	private long price(ServerPlayer player, ItemStack ware) {
		EconomyService eco = HearthServices.economy();
		SimSettlement s = Parties.home((ServerLevel) player.level(), this.party);
		long base = eco == null || s == null ? 8 : eco.price(player, s, ware, true);
		// cheaper than in town, as the pedlar cries
		return Math.max(1, Math.round(base * 0.85));
	}

	private static Component coins(long bits) {
		EconomyService eco = HearthServices.economy();
		return eco == null ? Component.literal(bits + " bits") : eco.format(bits);
	}

	private Component buy(ServerPlayer player, int i) {
		EconomyService eco = HearthServices.economy();
		ItemStack ware = ware(i);
		long price = price(player, ware);
		if (eco == null || !eco.charge(player, price)) {
			return Component.translatable("hearth.roads.say.pedlar.poor");
		}
		ItemStack bought = ware.copy();
		if (!player.getInventory().add(bought)) {
			player.spawnAtLocation((ServerLevel) player.level(), bought);
		}
		return Component.translatable("hearth.roads.say.pedlar.sold." + this.random.nextInt(2), ware.getHoverName());
	}

	static String lower(Enum<?> e) {
		return e.name().toLowerCase(Locale.ROOT);
	}
}
