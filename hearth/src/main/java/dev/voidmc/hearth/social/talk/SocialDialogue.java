package dev.voidmc.hearth.social.talk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.dice.DiceTables;
import dev.voidmc.hearth.social.folk.StallKeeperBehaviour;
import dev.voidmc.hearth.social.life.Notices;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import dev.voidmc.hearth.social.service.Bathhouse;
import dev.voidmc.hearth.social.service.Inn;
import dev.voidmc.hearth.social.service.PlayerLife;
import dev.voidmc.hearth.social.service.ServiceSites;
import dev.voidmc.hearth.social.service.Stables;
import dev.voidmc.hearth.social.service.VisitingWares;
import dev.voidmc.hearth.social.service.WageTask;
import dev.voidmc.hearth.social.service.WageTasks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The social module's lines in conversation (docs/SOCIAL.md §6): what is on in town, for everyone; a bed, a room, a
 * meal and news from the road at the inn; a bath; a mount at the stable; paid work from farmers and quarriers and its
 * hand-in to farmers and builders; dice; the visiting trader's wares at the market stalls; and each official's business.
 */
public final class SocialDialogue implements DialogueProvider {
	static final String WHATS_ON = "social:whats_on";
	static final String ROAD = "social:road_news";
	static final String BED = "social:bed";
	static final String ROOM = "social:room";
	static final String MEAL = "social:meal";
	static final String BATH = "social:bath";
	static final String HIRE = "social:hire";
	static final String RETURN = "social:return";
	static final String BUY = "social:buy_mount";
	static final String HARVEST = "social:harvest";
	static final String HARVEST_IN = "social:harvest_in";
	static final String HAUL = "social:haul";
	static final String HAUL_IN = "social:haul_in";
	static final String QUIT = "social:quit_job";
	static final String DICE = "social:dice";
	static final String WARE = "social:ware/";
	static final String TOWN = "social:town_report";
	static final String OFFICES = "social:offices";
	static final String WORK = "social:work";
	static final String TROUBLE = "social:trouble";
	static final String ALMS = "social:alms";
	static final String RITES = "social:rites";
	static final String BUILDING = "social:building";
	static final String PLEDGE = "social:pledge";
	static final String REALM = "social:realm";
	static final String DONATE = "social:donate";
	static final String COFFERS = "social:coffers";
	static final String LAW = "social:law";

	static final int ALMS_BITS = 5;
	static final int DONATION_BITS = 100;

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		SocialConfig cfg = SocialModule.config();
		HearthWorld w = HearthWorld.of((ServerLevel) player.level());
		if (r.age == AgeStage.CHILD) {
			return;
		}
		if (cfg.life().enabled()) {
			out.add(opt(WHATS_ON, "whats_on", 15));
		}
		boolean money = Hooks.economy() != null;
		if (cfg.inn().enabled() && ServiceSites.innkeeper(s, r)) {
			out.add(opt(ROAD, "road_news", 20));
			out.add(new Option(BED, label("bed", Hooks.coins(cfg.inn().bed())), 130));
			boolean hasRoom = false;
			for (SocialLedger.Rental rent : s.data(SocialModule.LEDGER).rooms) {
				hasRoom |= rent.player().equals(player.getUUID());
			}
			out.add(new Option(ROOM, label(hasRoom ? "room_more" : "room", Hooks.coins(cfg.inn().room())), 131));
			out.add(new Option(MEAL, label("meal", Hooks.coins(cfg.inn().meal())), 132));
		}
		if (cfg.bath().enabled() && ServiceSites.bathKeeper(s, r)) {
			out.add(new Option(BATH, label("bath", Hooks.coins(cfg.bath().price())), 135));
		}
		if (cfg.stables().enabled() && ServiceSites.stablemaster(s, r, cfg.stables().innsHire())) {
			PlayerLife life = PlayerLife.of(player);
			if (life.hire().isPresent()) {
				out.add(opt(RETURN, "return_mount", 141));
			} else {
				out.add(new Option(HIRE, label("hire", Hooks.coins(cfg.stables().hirePerDay()), Hooks.coins(cfg.stables().deposit())), 140));
			}
			out.add(new Option(BUY, label("buy_mount", Hooks.coins(cfg.stables().buy())), 142));
		}
		if (cfg.wages().enabled() && w != null && money) {
			WageTask task = WageTasks.task(player);
			int minute = SocialTime.minute(TownLife.now(w, s));
			boolean daytime = minute >= 7 * 60 && minute < 18 * 60;
			if (task == null && daytime) {
				if (ServiceSites.farmhand(s, r)) {
					out.add(new Option(HARVEST, label("harvest", Hooks.coins((long) cfg.wages().harvestWage() * cfg.wages().harvestCrops())), 150));
				}
				if (ServiceSites.quarrier(s, r)) {
					out.add(new Option(HAUL, label("haul", Hooks.coins(cfg.wages().haulWage())), 152));
				}
			} else if (task != null && task.settlementId == s.id()) {
				if (task.kind == WageTask.Kind.HARVEST && task.progress > 0 && ServiceSites.farmhand(s, r)) {
					out.add(opt(HARVEST_IN, "harvest_in", 151));
				}
				if (task.kind == WageTask.Kind.HAUL && ServiceSites.builder(s, r)) {
					out.add(opt(HAUL_IN, "haul_in", 153));
				}
				if (task.giver.equals(r.id)) {
					out.add(opt(QUIT, "quit_job", 159));
				}
			}
		}
		if (cfg.market().enabled() && money && w != null && VisitingWares.enabled()) {
			TownEvent market = TownLife.current(s, TownEvent.Kind.MARKET);
			if (market != null && (StallKeeperBehaviour.stallOf(s, market, r) >= 0 || ServiceSites.workPath(s, r).startsWith("commerce/market")
				|| r.job == dev.voidmc.hearth.api.JobType.MERCHANT)) {
				VisitingWares.Visit visit = VisitingWares.visit(w, s, TownLife.today(w, s));
				if (visit != null) {
					SocialLedger ledger = s.data(SocialModule.LEDGER);
					for (int i = 0; i < visit.wares().size(); i++) {
						VisitingWares.Ware ware = visit.wares().get(i);
						long price = VisitingWares.price(s, ware, cfg.market());
						if (price > 0 && VisitingWares.stock(ledger, TownLife.today(w, s), ware, cfg.market()) > 0) {
							out.add(new Option(WARE + i, Component.translatable("hearth.social.talk.ware", ware.count(), new ItemStack(ware.item()).getHoverName(),
								Hooks.coins(price)), 120 + i));
						}
					}
				}
			}
		}
		if (cfg.dice().enabled() && DiceTables.atTable(player, s, r) && DiceTables.refusal(player, s, r) == null) {
			out.add(opt(DICE, "dice", 170));
		}
		officials(player, s, r, out, cfg);
	}

	private static void officials(ServerPlayer player, Settlement s, Resident r, List<Option> out, SocialConfig cfg) {
		Office office = Officials.officeOf(s.data(SocialModule.LEDGER), r.id);
		if (office == null) {
			return;
		}
		switch (office) {
			case REEVE -> {
				out.add(opt(TOWN, "town_report", 180));
				out.add(opt(OFFICES, "offices", 181));
				out.add(opt(WORK, "work", 182));
			}
			case GUARD_CAPTAIN -> out.add(opt(TROUBLE, "trouble", 183));
			case PRIEST -> {
				out.add(new Option(ALMS, label("alms", Hooks.coins(ALMS_BITS)), 184));
				out.add(opt(RITES, "rites", 185));
			}
			case MASTER_BUILDER -> out.add(opt(BUILDING, "building", 186));
			case LORD -> {
				if (!pledged(r, player)) {
					out.add(opt(PLEDGE, "pledge", 187));
				}
				out.add(opt(REALM, "realm", 188));
			}
			case STEWARD -> {
				out.add(new Option(DONATE, label("donate", Hooks.coins(DONATION_BITS)), 189));
				out.add(opt(COFFERS, "coffers", 190));
			}
			case JUDGE, JAILER -> out.add(opt(LAW, "law", 191));
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String id) {
		SocialConfig cfg = SocialModule.config();
		HearthWorld w = HearthWorld.of((ServerLevel) player.level());
		if (w == null) {
			return false;
		}
		if (id.startsWith(WARE)) {
			return buyWare(player, w, s, r, id.substring(WARE.length()));
		}
		switch (id) {
			case WHATS_ON -> say(player, r, s, whatsOn(w, s));
			case ROAD -> say(player, r, s, roadNews(w, s));
			case BED -> {
				if (!ServiceSites.innkeeper(s, r)) {
					return false;
				}
				Inn.Result res = Inn.rentBed(player, w, s, r);
				switch (res) {
					case OK -> say(player, r, s, Component.translatable("hearth.social.inn.bed_ok", Inn.whereIs(player, PlayerLife.of(player).bed().get().pos())));
					case ALREADY -> say(player, r, s, Component.translatable("hearth.social.inn.bed_already"));
					case FULL -> say(player, r, s, Component.translatable("hearth.social.inn.full"));
					case NO_INN -> say(player, r, s, Component.translatable("hearth.social.inn.no_inn"));
					case CANNOT_PAY -> {
					}
				}
			}
			case ROOM -> {
				if (!ServiceSites.innkeeper(s, r)) {
					return false;
				}
				BlockPos[] chest = new BlockPos[1];
				Inn.Result res = Inn.rentRoom(player, w, s, r, chest);
				switch (res) {
					case OK -> say(player, r, s, Component.translatable("hearth.social.inn.room_ok", Inn.whereIs(player, chest[0]),
						cfg.inn().roomGraceDays()));
					case ALREADY -> say(player, r, s, Component.translatable("hearth.social.inn.room_more"));
					case FULL -> say(player, r, s, Component.translatable("hearth.social.inn.rooms_full"));
					case NO_INN -> say(player, r, s, Component.translatable("hearth.social.inn.no_inn"));
					case CANNOT_PAY -> {
					}
				}
			}
			case MEAL -> {
				if (ServiceSites.innkeeper(s, r) && Inn.meal(player, s, r)) {
					say(player, r, s, Component.translatable("hearth.social.inn.meal_" + (1 + Math.floorMod(player.level().getGameTime() / 20, 3L))));
				}
			}
			case BATH -> {
				if (ServiceSites.bathKeeper(s, r) && Bathhouse.bathe(player, s, r)) {
					say(player, r, s, Component.translatable("hearth.social.bath.done"));
				}
			}
			case HIRE -> stable(player, s, r, Stables.hire(player, w, s, r), "hired");
			case BUY -> stable(player, s, r, Stables.buy(player, s, r), "bought");
			case RETURN -> {
				long[] refund = new long[1];
				Stables.Result res = Stables.giveBack(player, w, s, refund);
				switch (res) {
					case OK -> say(player, r, s, Component.translatable("hearth.social.stable.returned", Hooks.coins(refund[0])));
					case TOO_FAR -> say(player, r, s, Component.translatable("hearth.social.stable.too_far"));
					default -> say(player, r, s, Component.translatable("hearth.social.stable.lost"));
				}
			}
			case HARVEST -> wages(player, s, r, WageTasks.takeHarvest(player, w, s, r), "harvest");
			case HAUL -> wages(player, s, r, WageTasks.takeHaul(player, w, s, r), "haul");
			case HARVEST_IN -> {
				long paid = WageTasks.handInHarvest(player, s);
				say(player, r, s, paid < 0 ? Component.translatable("hearth.social.wages.nothing_brought")
					: Component.translatable("hearth.social.wages.paid", Hooks.coins(paid)));
			}
			case HAUL_IN -> {
				long paid = WageTasks.handInHaul(player, s);
				say(player, r, s, paid < 0 ? Component.translatable("hearth.social.wages.short_load")
					: Component.translatable("hearth.social.wages.paid", Hooks.coins(paid)));
			}
			case QUIT -> {
				if (WageTasks.quit(player, s)) {
					say(player, r, s, Component.translatable("hearth.social.wages.quit"));
				}
			}
			case DICE -> {
				return DiceTables.open(player, s, r);
			}
			case TOWN -> say(player, r, s, townReport(player, s));
			case OFFICES -> say(player, r, s, officesReport(s));
			case WORK -> say(player, r, s, workReport(s));
			case TROUBLE -> say(player, r, s, troubleReport(player, s));
			case ALMS -> {
				if (Hooks.charge(player, ALMS_BITS, r, s)) {
					s.deposit(ALMS_BITS);
					s.markDirty();
					Hooks.deed(player, s, "social:alms", 1, r.id, player.blockPosition(), true);
					say(player, r, s, Component.translatable("hearth.social.office.priest.alms"));
				}
			}
			case RITES -> say(player, r, s, ritesReport(w, s));
			case BUILDING -> say(player, r, s, buildingReport(s));
			case PLEDGE -> {
				if (!pledged(r, player)) {
					var list = r.extra("social").getListOrEmpty("pledged").copy();
					list.add(net.minecraft.nbt.StringTag.valueOf(player.getUUID().toString()));
					r.extra("social").put("pledged", list);
					s.markDirty();
					Hooks.deed(player, s, "social:fealty", 3, r.id, player.blockPosition(), true);
					say(player, r, s, Component.translatable("hearth.social.office.lord.pledged", player.getName()));
				}
			}
			case REALM -> say(player, r, s, realmReport(player, w, s));
			case DONATE -> {
				if (Hooks.charge(player, DONATION_BITS, r, s)) {
					s.deposit(DONATION_BITS);
					s.markDirty();
					Hooks.deed(player, s, "social:donation", 5, r.id, player.blockPosition(), true);
					say(player, r, s, Component.translatable("hearth.social.office.steward.thanks"));
				}
			}
			case COFFERS -> say(player, r, s, Component.translatable("hearth.social.office.steward.coffers", Hooks.coins(s.treasury()), s.population()));
			case LAW -> say(player, r, s, lawReport(player, s));
			default -> {
				return false;
			}
		}
		return true;
	}

	// ---- the pieces

	private static Option opt(String id, String key, int order) {
		return new Option(id, Component.translatable("hearth.social.talk." + key), order);
	}

	private static Component label(String key, Object... args) {
		return Component.translatable("hearth.social.talk." + key, args);
	}

	private static void say(ServerPlayer player, Resident r, Settlement s, Component line) {
		Hooks.reply(player, r, s, line);
	}

	static boolean pledged(Resident lord, ServerPlayer player) {
		var list = lord.extra.getCompoundOrEmpty("social").getListOrEmpty("pledged");
		for (int i = 0; i < list.size(); i++) {
			if (list.getStringOr(i, "").equals(player.getUUID().toString())) {
				return true;
			}
		}
		return false;
	}

	private static void stable(ServerPlayer player, Settlement s, Resident r, Stables.Result res, String what) {
		switch (res) {
			case OK -> say(player, r, s, Component.translatable("hearth.social.stable." + what));
			case ALREADY_HIRED -> say(player, r, s, Component.translatable("hearth.social.stable.already"));
			case NO_ROOM -> say(player, r, s, Component.translatable("hearth.social.stable.no_room"));
			default -> {
			}
		}
	}

	private static void wages(ServerPlayer player, Settlement s, Resident r, WageTasks.Result res, String what) {
		SocialConfig.Wages cfg = SocialModule.config().wages();
		Component line = switch (res) {
			case OK -> what.equals("harvest") ? Component.translatable("hearth.social.wages.harvest_ok", cfg.harvestCrops(), cfg.timeLimitTicks() / 1200)
				: Component.translatable("hearth.social.wages.haul_ok", cfg.haulLoad(), cfg.timeLimitTicks() / 1200);
			case BUSY -> Component.translatable("hearth.social.wages.busy");
			case LIMIT -> Component.translatable("hearth.social.wages.limit");
			case NOTHING -> Component.translatable("hearth.social.wages.nothing");
			case NO_ROOM -> Component.translatable("hearth.social.wages.no_room");
			case TOWN_CANNOT_PAY -> Component.translatable("hearth.social.wages.town_poor");
		};
		say(player, r, s, line);
	}

	private static boolean buyWare(ServerPlayer player, HearthWorld w, Settlement s, Resident r, String index) {
		SocialConfig cfg = SocialModule.config();
		VisitingWares.Visit visit = VisitingWares.visit(w, s, TownLife.today(w, s));
		int i;
		try {
			i = Integer.parseInt(index);
		} catch (NumberFormatException e) {
			return false;
		}
		if (visit == null || i < 0 || i >= visit.wares().size() || TownLife.current(s, TownEvent.Kind.MARKET) == null) {
			return false;
		}
		VisitingWares.Ware ware = visit.wares().get(i);
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		long price = VisitingWares.price(s, ware, cfg.market());
		if (price <= 0 || VisitingWares.stock(ledger, TownLife.today(w, s), ware, cfg.market()) <= 0) {
			return false;
		}
		if (!Hooks.charge(player, price, r, s)) {
			return true;
		}
		VisitingWares.sold(ledger, ware);
		// the trader's coin goes home with them; the stallholder keeps a cut for the town
		long cut = price / 5;
		s.deposit(cut);
		if (visit.fromSettlement() != null) {
			Settlement home = w.settlement(visit.fromSettlement());
			if (home != null) {
				home.deposit(price - cut);
				home.markDirty();
			}
		}
		s.markDirty();
		ItemStack stack = new ItemStack(ware.item(), ware.count());
		if (!player.getInventory().add(stack)) {
			net.minecraft.world.Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		}
		say(player, r, s, visit.from().isEmpty()
			? Component.translatable("hearth.social.market.sold_far", Component.translatable("hearth.culture." + visit.culture().getSerializedName()))
			: Component.translatable("hearth.social.market.sold", visit.from()));
		return true;
	}

	/** Today's doings, the next market and feast, and the latest notices. */
	static Component whatsOn(HearthWorld w, Settlement s) {
		SocialConfig cfg = SocialModule.config();
		long today = TownLife.today(w, s);
		long now = TownLife.now(w, s);
		MutableComponent out = Component.empty();
		List<Component> parts = new ArrayList<>();
		for (TownEvent e : TownLife.events(s)) {
			if (e.end() <= now) {
				continue;
			}
			parts.add(eventLine(e, now));
		}
		if (parts.isEmpty()) {
			parts.add(Component.translatable("hearth.social.whats_on.quiet"));
		}
		if (cfg.market().enabled() && s.tier().ordinal() >= cfg.market().minTier().ordinal()) {
			long next = TownCalendar.nextMarketDay(s.id(), today + 1, cfg.market());
			parts.add(Component.translatable("hearth.social.whats_on.next_market", days(next - today)));
		}
		TownCalendar.Upcoming f = TownCalendar.nextFestival(today + 1, s.culture(), s.foundedDay(), s.tier(), cfg.festivals());
		if (f != null) {
			parts.add(Component.translatable("hearth.social.whats_on.next_festival", Component.translatable(f.festival().translationKey()), days(f.day() - today)));
		}
		List<SocialLedger.Notice> notices = Notices.current(s.data(SocialModule.LEDGER), today);
		for (int i = 0; i < Math.min(2, notices.size()); i++) {
			parts.add(Notices.text(notices.get(i), today));
		}
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(" ");
			}
			out.append(parts.get(i));
		}
		return out;
	}

	static Component eventLine(TownEvent e, long now) {
		boolean on = e.activeAt(now);
		String when = SocialConfig.formatClock(SocialTime.minute(e.start()));
		return switch (e.kind()) {
			case MARKET -> Component.translatable(on ? "hearth.social.whats_on.market_now" : "hearth.social.whats_on.market_later", when);
			case FESTIVAL -> Component.translatable(on ? "hearth.social.whats_on.festival_now" : "hearth.social.whats_on.festival_later",
				Component.translatable(e.festival() == null ? "hearth.social.festival.founders" : e.festival().translationKey()), when);
			case WEDDING -> Component.translatable("hearth.social.whats_on.wedding", name(e, 0), name(e, 1), when);
			case FEAST -> Component.translatable("hearth.social.whats_on.feast", name(e, 0), name(e, 1));
			case FUNERAL -> Component.translatable("hearth.social.whats_on.funeral", name(e, 0), when);
			case COURT -> Component.translatable(on ? "hearth.social.whats_on.court_now" : "hearth.social.whats_on.court_later", when);
		};
	}

	private static String name(TownEvent e, int i) {
		return i < e.names().size() ? e.names().get(i) : "?";
	}

	static Component days(long d) {
		return d <= 0 ? Component.translatable("hearth.social.when.today") : d == 1 ? Component.translatable("hearth.social.when.tomorrow")
			: Component.translatable("hearth.social.when.days", d);
	}

	/** What neighbouring towns have on this week: markets and feasts. */
	static Component roadNews(HearthWorld w, Settlement s) {
		SocialConfig cfg = SocialModule.config();
		long today = TownLife.today(w, s);
		MutableComponent out = Component.empty();
		int told = 0;
		for (HearthWorld.SiteInfo site : w.knownSites(s.center(), 1500)) {
			if (told >= 3 || site.settlementId() == s.id() || !site.live()) {
				continue;
			}
			Settlement other = w.settlement(site.settlementId());
			if (other == null) {
				continue;
			}
			Component dir = direction(s.center(), other.center());
			TownCalendar.Upcoming f = TownCalendar.nextFestival(today, other.culture(), other.foundedDay(), other.tier(), cfg.festivals());
			long market = TownCalendar.nextMarketDay(other.id(), today, cfg.market());
			if (told > 0) {
				out.append(" ");
			}
			if (f != null && f.day() - today <= 7) {
				out.append(Component.translatable("hearth.social.road.festival", other.name(), dir, Component.translatable(f.festival().translationKey()),
					days(f.day() - today)));
			} else if (other.tier().ordinal() >= cfg.market().minTier().ordinal()) {
				out.append(Component.translatable("hearth.social.road.market", other.name(), dir, days(market - today)));
			} else {
				out.append(Component.translatable("hearth.social.road.quiet_town", other.name(), dir));
			}
			told++;
		}
		if (told == 0) {
			return Component.translatable("hearth.social.road.none");
		}
		return out;
	}

	static Component direction(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
		String name = names[(int) Math.floorMod(Math.round(angle / 45.0), 8L)];
		long dist = Math.round(Math.sqrt(dx * dx + dz * dz) / 100.0) * 100;
		return Component.translatable("hearth.social.road.where", Component.translatable("hearth.social.compass." + name), dist);
	}

	static Component townReport(ServerPlayer player, Settlement s) {
		int building = 0;
		int waiting = 0;
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.BUILDING) {
				building++;
				if (p.waitingForGoods()) {
					waiting++;
				}
			}
		}
		var title = Hooks.title(player, s);
		Component standing = title == null ? Component.literal(Integer.toString(Hooks.standing(player, s))) : Component.translatable(title.translationKey());
		return Component.translatable("hearth.social.office.reeve.report", s.name(), Component.translatable(s.tier().translationKey()), s.population(),
			building, waiting, standing);
	}

	static Component officesReport(Settlement s) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		MutableComponent out = Component.translatable("hearth.social.office.reeve.offices");
		for (Office o : Office.values()) {
			Resident h = Officials.holder(s, ledger, o);
			if (h != null) {
				out.append(" ").append(Component.translatable("hearth.social.office.entry", Component.translatable(o.translationKey()), h.fullName()));
			}
		}
		return out;
	}

	static Component workReport(Settlement s) {
		List<String> farmers = new ArrayList<>();
		List<String> quarriers = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.ADULT) {
				continue;
			}
			if (ServiceSites.farmhand(s, r) && farmers.size() < 2) {
				farmers.add(r.givenName);
			} else if (ServiceSites.quarrier(s, r) && quarriers.size() < 2) {
				quarriers.add(r.givenName);
			}
		}
		if (farmers.isEmpty() && quarriers.isEmpty()) {
			return Component.translatable("hearth.social.office.reeve.no_work");
		}
		return Component.translatable("hearth.social.office.reeve.work", farmers.isEmpty() ? "-" : String.join(", ", farmers),
			quarriers.isEmpty() ? "-" : String.join(", ", quarriers));
	}

	static Component troubleReport(ServerPlayer player, Settlement s) {
		long bounty = Hooks.bounty(player, s);
		if (Hooks.wanted(player, s)) {
			return Component.translatable("hearth.social.office.captain.wanted", Hooks.coins(bounty));
		}
		return Component.translatable("hearth.social.office.captain.quiet_" + (1 + Math.floorMod(player.level().getGameTime() / 40, 3L)));
	}

	static Component lawReport(ServerPlayer player, Settlement s) {
		if (Hooks.wanted(player, s)) {
			return Component.translatable("hearth.social.office.judge.wanted", Hooks.coins(Hooks.bounty(player, s)));
		}
		return Component.translatable("hearth.social.office.judge.clean");
	}

	static Component ritesReport(HearthWorld w, Settlement s) {
		long today = TownLife.today(w, s);
		MutableComponent out = Component.empty();
		int n = 0;
		for (SocialLedger.FamilyEvent e : s.data(SocialModule.LEDGER).family) {
			if (e.day() < today) {
				continue;
			}
			if (n++ > 0) {
				out.append(" ");
			}
			if (e.kind() == SocialLedger.FamilyEvent.Kind.WEDDING && e.names().size() >= 2) {
				out.append(Component.translatable("hearth.social.office.priest.wedding", e.names().get(0), e.names().get(1), days(e.day() - today)));
			} else if (!e.names().isEmpty()) {
				out.append(Component.translatable("hearth.social.office.priest.funeral", e.names().get(0), days(e.day() - today)));
			}
		}
		return n == 0 ? Component.translatable("hearth.social.office.priest.none") : out;
	}

	static Component buildingReport(Settlement s) {
		MutableComponent out = Component.empty();
		int n = 0;
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.BUILDING || n >= 3) {
				continue;
			}
			String path = p.spec().archetype().getPath();
			if (path.startsWith("infra/")) {
				continue;
			}
			Component what = Component.translatableWithFallback("hearth.social.building." + path.replace('/', '.'), path.substring(path.indexOf('/') + 1)
				.replace('_', ' '));
			if (n++ > 0) {
				out.append(" ");
			}
			out.append(Component.translatable(p.waitingForGoods() ? "hearth.social.office.builder.waiting" : "hearth.social.office.builder.raising", what));
		}
		return n == 0 ? Component.translatable("hearth.social.office.builder.idle") : out;
	}

	static Component realmReport(ServerPlayer player, HearthWorld w, Settlement s) {
		int towns = 0;
		for (HearthWorld.SiteInfo site : w.knownSites(s.center(), 2500)) {
			if (site.settlementId() != s.id()) {
				towns++;
			}
		}
		var title = Hooks.title(player, s);
		Component standing = title == null ? Component.translatable("hearth.social.office.lord.unknown") : Component.translatable(title.translationKey());
		return Component.translatable("hearth.social.office.lord.realm", s.name(), towns, standing);
	}
}
