package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.TravelService;
import dev.voidmc.hearth.social.life.TownLife;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/**
 * The lord's tax collector (docs/LIVING_WORLD_2.md §3.1, §3.2): every fourteen days a bailiff with two guards and a cart
 * rides from the seat to each town of the domain — a real journey on the roads (lane A1's {@code TAX_COLLECTOR}) — and
 * the tax day is held when it arrives. What the town hands over rides home with it: it reaches the seat's treasury when
 * the journey ends, and is lost if the journey is waylaid (the "tax caravan" a player may rob, or escort). With no
 * roads or travel the coin goes to the seat at once.
 */
final class TaxRounds {
	/** The bailiff sets out in the morning. */
	static final int DEPART_MINUTE = 6 * 60 + 30;

	private TaxRounds() {
	}

	/**
	 * Sends the bailiff on his round to this town and holds the tax day when he will be there; returns the event with its
	 * times moved to his arrival (or as it was, when nobody can ride).
	 */
	static Scheduled dispatch(HearthWorld w, Settlement s, Scheduled e) {
		TravelService travel = HearthServices.travel();
		Settlement seat = w.settlement(e.args().getLongOr("seat", 0));
		if (travel == null || seat == null) {
			return e;
		}
		long townNow = TownLife.now(w, s);
		long clockNow = w.clock();
		long stay = e.end() - e.start();
		// the town's ticks and the sim clock may differ (a town caught up ahead of the world): the journey keeps the clock
		long depart = Math.max(clockNow, clockNow + (dev.voidmc.hearth.social.calendar.SocialTime.at(e.day(), DEPART_MINUTE) - townNow));
		CompoundTag details = new CompoundTag();
		details.putBoolean("round_trip", true);
		details.putInt("stay", (int) Math.max(600, stay));
		details.putLong("town", s.id());
		details.putInt("event", e.id());
		long id;
		try {
			id = travel.dispatch(TravelService.TravelKind.TAX_COLLECTOR, seat, s, null, List.of(), depart, details);
		} catch (RuntimeException ex) {
			Hearth.LOG.debug("events: the bailiff could not set out for {}", s.name(), ex);
			id = 0;
		}
		if (id == 0) {
			return e;
		}
		e.args().putLong("journey", id);
		TravelService.TravellerView v = travel.traveller(id);
		if (v == null) {
			return e;
		}
		long outArrival = v.departs() + Math.max(0, (v.arrives() - v.departs() - Math.max(600, stay)) / 2);
		long start = outArrival - clockNow + townNow;
		long end = start + Math.max(600, stay);
		// a bailiff who would ride in after dark keeps the day's hours instead (his party is still on the road)
		if (start < dev.voidmc.hearth.social.calendar.SocialTime.at(e.day(), DEPART_MINUTE)
			|| start >= dev.voidmc.hearth.social.calendar.SocialTime.at(e.day(), 20 * 60)) {
			return e;
		}
		return e.withTimes(Math.max(start, townNow), Math.max(start, townNow) + (end - start));
	}

	/** The town handed over the tax: it rides home with the bailiff, or goes to the seat at once when he is not on the road. */
	static void carry(HearthWorld w, Settlement s, Scheduled e, long bits) {
		Settlement seat = w.settlement(e.args().getLongOr("seat", 0));
		if (seat == null || bits <= 0) {
			return;
		}
		long journey = e.args().getLongOr("journey", 0);
		TravelService travel = HearthServices.travel();
		if (journey != 0 && travel != null && travel.traveller(journey) != null) {
			EventBook book = seat.data(EventBook.KEY);
			book.takings.merge(Long.toString(journey), bits, Long::sum);
			seat.markDirty();
		} else {
			seat.deposit(bits);
			seat.markDirty();
		}
	}

	/** A journey ended: the bailiff's takings reach the seat, or are lost on the road. */
	static void traveller(HearthWorld w, TravelService.TravelKind kind, long id, long from, long to, SettlementEvents.Traveller.Phase phase) {
		if (kind != TravelService.TravelKind.TAX_COLLECTOR || phase == SettlementEvents.Traveller.Phase.DEPARTED) {
			return;
		}
		Settlement seat = w.settlement(from);
		if (seat == null) {
			return;
		}
		EventBook book = seat.data(EventBook.KEY);
		Long bits = book.takings.remove(Long.toString(id));
		if (bits == null) {
			return;
		}
		seat.markDirty();
		if (phase == SettlementEvents.Traveller.Phase.ARRIVED) {
			seat.deposit(bits);
		} else {
			EventEffects.post(w, seat, "news", TownLife.today(w, seat), "hearth.events.notice.tax_robbed", List.of(Long.toString(bits)));
		}
	}
}
