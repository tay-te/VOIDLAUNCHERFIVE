package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.quest.service.LivingQuests;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.Notices;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Applies an event's {@link Outcome} to its town through the existing calls only (docs/LIVING_WORLD_2.md §3.2): the
 * treasury, the stores, plots (a burnt house goes in the book and the builders put it back), residents (death only where
 * the rule says), notices on the board, happenings for quests, and {@link SettlementEvents#TOWN_EVENT}. Called once per
 * event, abstract or embodied alike.
 */
public final class EventEffects {
	private EventEffects() {
	}

	public static void apply(HearthWorld w, Settlement s, Scheduled e, Outcome o) {
		EventBook book = s.data(EventBook.KEY);
		book.replace(e.withPhase(Scheduled.Phase.ENDED));
		book.remember(o);
		long day = e.day();
		switch (e.kind()) {
			case TRIAL -> trial(w, s, book, e, o);
			case EXECUTION -> {
				if (o.result().equals("hanged")) {
					for (UUID id : o.deaths()) {
						kill(s, id, "hanged");
					}
				} else {
					post(w, s, "news", day, "hearth.events.notice.mercy", List.of(name(s, e.principals())));
					if (!e.args().getBooleanOr("forced", false)) {
						for (UUID id : e.principals()) {
							kill(s, id, "left"); // spared the rope, and banished: they leave the town
						}
					}
				}
			}
			case RAID -> raid(w, s, book, e, o);
			case FIRE -> {
				if (o.plot() >= 0 && o.burned() >= 0.12) {
					burn(w, s, book, o.plot(), day, o.burned(), e.args().getBooleanOr("live", false));
				}
			}
			case TAX -> tax(w, s, e, o);
			case FAIR -> {
				int stalls = Math.max(3, e.args().getIntOr("stalls", 6));
				s.deposit((long) stalls * SocialModule.config().market().stallFee());
				if (!o.winner().isEmpty() && e.args().getBooleanOr("contest", true)) {
					post(w, s, "news", day, "hearth.events.notice.archery", List.of(o.winner()));
				}
			}
			case TOURNAMENT -> {
				if (!o.winner().isEmpty()) {
					post(w, s, "news", day, "hearth.events.notice.tournament", List.of(o.winner()));
				}
			}
			case SLAUGHTER_DAY, HORSE_FAIR, TROUPE -> {
				moveCoin(s, o.coin());
				takeGoods(s, o.goods());
			}
			default -> {
			}
		}
		s.markDirty();
		try {
			SettlementEvents.TOWN_EVENT.invoker().onTownEvent(w, s, e.kind().id(), SettlementEvents.TownEvent.Phase.ENDED, o.result());
		} catch (RuntimeException ex) {
			Hearth.LOG.warn("events: a listener of {} in {} failed", e.kind().id(), s.name(), ex);
		}
	}

	// ---------------------------------------------------------------- per kind

	private static void trial(HearthWorld w, Settlement s, EventBook book, Scheduled e, Outcome o) {
		String who = name(s, e.principals());
		String offence = "@hearth.events.offence." + e.args().getStringOr("offence", "theft");
		if (!o.result().equals("guilty")) {
			post(w, s, "news", e.day(), "hearth.events.notice.acquitted", List.of(who, offence));
			return;
		}
		post(w, s, "news", e.day(), "hearth.events.notice.guilty." + (o.sentence().isEmpty() ? "fine" : o.sentence()), List.of(who, offence));
		switch (o.sentence()) {
			case "fine" -> moveCoin(s, o.coin());
			case "pillory" -> {
				// two hours in the pillory, from the end of the court
				EventScheduler.Offer offer = new EventScheduler.Offer(EventKind.PILLORY, SocialTime.minute(e.end()), 120, e.principals(), List.of(), -1,
					e.args().copy());
				EventDirector.offerNow(w, s, offer, e.day());
			}
			case "gallows" -> {
				// at dawn the next day
				EventScheduler.Offer offer = new EventScheduler.Offer(EventKind.EXECUTION, SocialTime.DAY_START_MINUTE, 40, e.principals(), List.of(), -1,
					e.args().copy());
				EventDirector.later(s, e.day() + 1, offer);
			}
			case "cell" -> {
				for (UUID id : e.principals()) {
					s.setResidentAway(id, w.clock() + 3L * SocialTime.DAY_TICKS, "events:cell");
				}
			}
			default -> {
			}
		}
	}

	private static void raid(HearthWorld w, Settlement s, EventBook book, Scheduled e, Outcome o) {
		long band = e.args().getLongOr("band", 0);
		boolean sacked = o.result().equals("sacked");
		RoadBands.raided(w, band, sacked, (int) Math.round(e.args().getDoubleOr("raiders_lost", 0)));
		if (!sacked) {
			post(w, s, "news", e.day(), "hearth.events.notice.raid_repelled", List.of());
			return;
		}
		int taken = takeGoods(s, o.goods());
		long coin = moveCoin(s, o.coin());
		post(w, s, "news", e.day(), "hearth.events.notice.raid", List.of(Integer.toString(taken), Long.toString(coin)));
		if (o.plot() >= 0) {
			burn(w, s, book, o.plot(), e.day(), o.burned(), e.args().getBooleanOr("barn_live", false));
		}
		for (UUID id : o.deaths()) {
			kill(s, id, "raid");
		}
		happen(w, s, "raided", -1, RoadBands.camp(w, band), taken);
	}

	private static void tax(HearthWorld w, Settlement s, Scheduled e, Outcome o) {
		long owed = Math.min(o.coin(), Math.max(0, s.treasury()));
		if (owed > 0 && s.withdraw(owed)) {
			TaxRounds.carry(w, s, e, owed);
		} else {
			owed = 0;
		}
		takeGoods(s, o.goods());
		post(w, s, "news", e.day(), owed > 0 ? "hearth.events.notice.tax" : "hearth.events.notice.tax_excused", List.of(Long.toString(owed)));
		if (o.sentence().equals("pig")) {
			post(w, s, "news", e.day(), "hearth.events.notice.pig", List.of(e.args().getStringOr("poor_name", "")));
		}
	}

	/** A plot burns: its shell stands until the builders have put it back ({@code repair}), a few days at most abstractly. */
	public static void burn(HearthWorld w, Settlement s, EventBook book, int plotId, long day, double burned, boolean live) {
		PlotView p = s.plot(plotId);
		if (p == null || book.burnt(plotId) != null) {
			return;
		}
		long until = day + 3 + Math.round(burned * 6);
		// a live fire's damage is real already (the repairs have it): no shell is laid over it
		book.burnt.add(new EventBook.Burnt(plotId, day, until, live, 0));
		String whose = household(s, plotId);
		post(w, s, "news", day, whose.isEmpty() ? "hearth.events.notice.fire" : "hearth.events.notice.fire_home", List.of(whose));
		if (!s.embodied()) {
			happen(w, s, "fire", plotId, Venues.middle(p.spec()), (int) Math.round(burned * 100));
		}
	}

	// ---------------------------------------------------------------- the town's books

	/** Takes coin from the treasury (positive) or puts it in (negative); returns what moved out. */
	static long moveCoin(Settlement s, long coin) {
		if (coin < 0) {
			s.deposit(-coin);
			return 0;
		}
		long take = Math.min(coin, Math.max(0, s.treasury()));
		return take > 0 && s.withdraw(take) ? take : 0;
	}

	/** Takes goods lost from the stores (a negative count adds them); returns the units taken. */
	static int takeGoods(Settlement s, int[] goods) {
		int taken = 0;
		Goods[] all = Goods.values();
		for (int g = 0; g < goods.length && g < all.length; g++) {
			if (goods[g] > 0) {
				taken += s.stockpile().take(all[g], goods[g]);
			} else if (goods[g] < 0) {
				s.stockpile().add(all[g], -goods[g]);
			}
		}
		return taken;
	}

	/** A resident dies of an event: the town is told how (the board, a funeral). */
	static void kill(Settlement s, UUID id, String cause) {
		Resident r = s.resident(id);
		if (r == null) {
			return;
		}
		r.extra("social").putString("death", cause);
		s.removeResident(id);
	}

	static String name(Settlement s, List<UUID> ids) {
		if (ids.isEmpty()) {
			return "";
		}
		Resident r = s.resident(ids.get(0));
		return r == null ? "" : r.fullName();
	}

	/** The family name of a plot's household ("the Millers"), or "". */
	static String household(Settlement s, int plotId) {
		for (Resident r : s.residents()) {
			if (r.home == plotId) {
				return r.familyName;
			}
		}
		return "";
	}

	static void happen(HearthWorld w, Settlement s, String kind, int plot, @Nullable BlockPos at, int amount) {
		try {
			LivingQuests.happen(w.level(), s, kind, plot, at == null ? s.center() : at, -1, amount, "", "", kind.equals("fire"));
		} catch (RuntimeException | LinkageError ex) {
			Hearth.LOG.debug("events: no happening for {}", kind, ex);
		}
	}

	/** Puts a notice up (once) and tells the players in the town, as town life does. */
	public static void post(HearthWorld w, Settlement s, String kind, long eventDay, String key, List<String> args) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		if (Notices.has(ledger, key, args, eventDay)) {
			return;
		}
		long today = TownLife.today(w, s);
		SocialLedger.Notice n = Notices.post(ledger, kind, today, eventDay, key, new ArrayList<>(args), SocialModule.config().life().maxNotices());
		s.markDirty();
		if (SocialModule.config().life().announceInChat() && s.embodied() && today - eventDay <= 1) {
			Component text = Component.translatable("hearth.social.notice.posted", s.name(), Notices.text(n, today)).withStyle(ChatFormatting.YELLOW);
			for (ServerPlayer player : w.level().players()) {
				if (s == w.settlementAt(player.blockPosition())) {
					player.sendSystemMessage(text);
				}
			}
		}
	}
}
