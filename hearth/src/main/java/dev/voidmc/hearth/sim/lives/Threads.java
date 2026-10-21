package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.sim.lives.Life.Bond;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * The threads of the named characters' stories (docs/LIVING_WORLD_2.md §4.2): each a small state machine from
 * {@link LivesBook}. Time-only beats move on abstractly on the day their conditions hold (so a town the player left has
 * moved on); a beat with choices waits for the first player who takes one, or moves on by itself when its time runs out.
 * Effects that touch the town are applied here; those for the player who moved the thread (a gift, coin, a memory, a
 * deed) are handed back to the caller, or kept on the character for that player's next visit.
 */
public final class Threads {
	/** Pending effects for the player who moved the thread, kept as flags {@code pending:<type>|<item>|<amount>|<kind>}. */
	static final String PENDING = "pending:";

	private Threads() {
	}

	/** An effect for a player, to be given by the caller (social module) who has the player at hand. */
	public record PlayerEffect(String type, String kind, int amount, String item) {
	}

	/** The thread's first beat begins (its effects apply). */
	static void enter(Settlement s, TownLives town, TownLives.Notable n, LivesBook.Thread thread, long day, LivesConfig cfg, SplittableRandom random,
		BiConsumer<Resident, String> event) {
		LivesBook.Beat b = thread.beat(n.beat);
		if (b != null) {
			apply(s, town, n, b.effects(), day, cfg, random, event, null);
		}
	}

	/** Moves each character's thread on if a transition's conditions hold today (one step a day at most). */
	static void day(Settlement s, TownLives town, LivesBook book, long day, LivesConfig cfg, SplittableRandom random, BiConsumer<Resident, String> event) {
		SettlementCalendar cal = HearthServices.calendar();
		List<TownLives.Notable> all = new ArrayList<>(town.notables);
		for (TownLives.Notable n : all) {
			if (!town.notables.contains(n)) {
				continue; // gone with an earlier one's beat (lovers who left together)
			}
			LivesBook.Thread t = book.thread(n.thread);
			if (t == null) {
				continue;
			}
			LivesBook.Beat b = t.beat(n.beat);
			if (b == null) {
				n.beat = t.start();
				n.since = day;
				continue;
			}
			if (b.end()) {
				continue;
			}
			for (LivesBook.Next next : b.next()) {
				if (holds(s, town, n, next, day, cal) && random.nextDouble() < next.chance()) {
					move(s, town, n, t, next.to(), day, cfg, random, event, null, "", null);
					break;
				}
			}
		}
	}

	static boolean holds(Settlement s, TownLives town, TownLives.Notable n, LivesBook.Next next, long day, SettlementCalendar cal) {
		if (day - n.since < next.after()) {
			return false;
		}
		if (next.event() != null && !event(s, next.event(), day, cal)) {
			return false;
		}
		if (next.partner() != null && !partner(s, town, n, next.partner())) {
			return false;
		}
		return next.flag() == null || n.flags.contains(next.flag()) || next.flag().startsWith("chose:") && next.flag().equals("chose:" + n.chose);
	}

	private static boolean event(Settlement s, String event, long day, SettlementCalendar cal) {
		return switch (event) {
			case "rest_day" -> cal.restDay(s, day);
			case "market_day" -> cal.marketDay(s, day);
			case "wash_day" -> cal.washDay(s, day);
			case "feast" -> cal.feast(s, day) != null;
			default -> cal.events(s, day).contains(event) || event.equals(cal.feast(s, day));
		};
	}

	private static boolean partner(Settlement s, TownLives town, TownLives.Notable n, String what) {
		Resident me = s.resident(n.resident);
		Resident p = n.partner == null ? null : s.resident(n.partner);
		return switch (what) {
			case "present" -> p != null;
			case "gone" -> p == null;
			case "grown" -> p != null && p.age != AgeStage.CHILD;
			case "sweetheart" -> me != null && p != null && p.id.equals(Life.sweetheart(me));
			case "married" -> me != null && p != null && p.id.equals(Kin.spouse(me));
			case "feud" -> me != null && p != null && Feuds.feuding(s, town, me, p);
			case "peace" -> me != null && p != null && !Feuds.feuding(s, town, me, p);
			default -> false;
		};
	}

	/**
	 * The thread goes to beat {@code to}: its effects apply. Returns the effects for the player, if one moved it (those of
	 * {@code extra}, then the beat's).
	 */
	static List<PlayerEffect> move(Settlement s, TownLives town, TownLives.Notable n, LivesBook.Thread t, String to, long day, LivesConfig cfg,
		SplittableRandom random, BiConsumer<Resident, String> event, @Nullable UUID player, String playerName, @Nullable List<LivesBook.Effect> extra) {
		LivesBook.Beat b = t.beat(to);
		if (b == null) {
			return List.of();
		}
		n.advance(to, day, player, playerName);
		town.stats.add("beats");
		if (b.end()) {
			town.stats.add("threads_ended");
		}
		Resident me = s.resident(n.resident);
		if (me != null) {
			town.news("thread", day, me.id, List.of(me.givenName, n.thread, to));
			event.accept(me, "thread");
		}
		List<PlayerEffect> out = new ArrayList<>();
		if (extra != null) {
			out.addAll(apply(s, town, n, extra, day, cfg, random, event, player));
		}
		out.addAll(apply(s, town, n, b.effects(), day, cfg, random, event, player));
		if (player == null && n.by != null) {
			// moved on by itself after a player's turn: what was due to that player waits for their next visit
			for (PlayerEffect e : out) {
				n.flags.add(PENDING + e.type() + "|" + e.item() + "|" + e.amount() + "|" + e.kind());
			}
			return List.of();
		}
		return out;
	}

	/** Applies effects; returns those meant for a player. */
	static List<PlayerEffect> apply(Settlement s, TownLives town, TownLives.Notable n, List<LivesBook.Effect> effects, long day, LivesConfig cfg,
		SplittableRandom random, BiConsumer<Resident, String> event, @Nullable UUID player) {
		List<PlayerEffect> out = new ArrayList<>();
		Resident me = s.resident(n.resident);
		Resident partner = n.partner == null ? null : s.resident(n.partner);
		for (LivesBook.Effect e : effects) {
			switch (e.type()) {
				case "news" -> {
					if (me != null) {
						town.news(e.kind().isEmpty() ? "thread" : e.kind(), day, me.id, List.of(me.givenName, partner == null ? "" : partner.givenName));
					}
				}
				case "relation" -> {
					if (me != null && partner != null) {
						Bond bond = e.bond() == null ? null : Bond.valueOf(e.bond().toUpperCase(java.util.Locale.ROOT));
						Life.touch(me, partner.id, e.amount(), bond, day, cfg.relations());
						Life.touch(partner, me.id, e.amount(), bond, day, cfg.relations());
					}
				}
				case "sweethearts" -> {
					if (me != null && partner != null && !Kin.married(me) && !Kin.married(partner)) {
						Life.touch(me, partner.id, 30, Bond.SWEETHEART, day, cfg.relations());
						Life.touch(partner, me.id, 30, Bond.SWEETHEART, day, cfg.relations());
					}
				}
				case "feud" -> {
					if (me != null && partner != null) {
						Feuds.start(s, town, me, partner, e.kind().isEmpty() ? "thread" : e.kind(), day, cfg, event);
					}
				}
				case "peace" -> {
					if (me != null && partner != null) {
						TownLives.Feud f = Feuds.between(s, town, me, partner);
						if (f != null) {
							Feuds.peace(s, town, f, day, cfg, "thread", event);
						}
					}
				}
				case "master_to" -> {
					JobType trade = me == null ? null : Life.masterOf(me) != null ? Life.masterOf(me) : me.job;
					if (me != null && partner != null && trade != null && trade != JobType.NONE) {
						Life.master(me, null, day);
						Life.keepIdle(me, day + 60);
						if (partner.age == AgeStage.CHILD) {
							// still a lad: made master the day they are grown
							Life.endApprenticeship(partner);
							Life.journeyman(partner, trade, day - cfg.crafts().masterDays());
						} else {
							Life.master(partner, trade, day);
							Life.journeyman(partner, null, day);
							town.stats.add("masters");
						}
						town.invalidate();
						event.accept(partner, "master");
					}
				}
				case "master" -> {
					Resident who = e.kind().equals("partner") ? partner : me;
					if (who != null && who.age == AgeStage.ADULT) {
						JobType trade = Life.journeyman(who) != null ? Life.journeyman(who) : Life.apprenticeTrade(who) != null ? Life.apprenticeTrade(who) : who.job;
						if (trade != null && trade != JobType.NONE && Life.masterOf(who) == null) {
							Life.master(who, trade, day);
							Life.journeyman(who, null, day);
							town.stats.add("masters");
							town.invalidate();
							event.accept(who, "master");
						}
					}
				}
				case "idle" -> {
					if (me != null) {
						Life.keepIdle(me, day + Math.max(1, e.amount()));
						town.invalidate();
					}
				}
				case "leave", "die" -> {
					Resident who = e.kind().equals("partner") ? partner : e.kind().equals("both") ? null : me;
					List<Resident> going = new ArrayList<>();
					if (e.kind().equals("both")) {
						if (me != null) {
							going.add(me);
						}
						if (partner != null) {
							going.add(partner);
						}
					} else if (who != null) {
						going.add(who);
					}
					for (Resident g : going) {
						town.stats.add(e.type().equals("die") ? "thread_deaths" : "thread_departures");
						Life.flag(g, e.type().equals("die") ? "died_thread" : "left_thread", true);
						s.removeResident(g.id);
					}
				}
				case "away" -> {
					if (me != null) {
						s.setResidentAway(me.id, (day + Math.max(1, e.amount())) * SimRules.DAY, n.thread);
					}
				}
				case "mourn" -> {
					if (me != null) {
						Life.mourn(me, day + Math.max(1, e.amount()));
					}
				}
				case "treasury" -> {
					if (me != null && e.amount() > 0) {
						long bits = e.amount();
						var eco = HearthServices.economy();
						if (eco != null) {
							bits = Math.max(e.amount() / 4, eco.takeFromPurse(s, me, e.amount()));
						}
						s.deposit(bits);
					}
				}
				case "memory", "deed", "gift", "coin" -> out.add(new PlayerEffect(e.type(), e.kind(), e.amount(), e.item()));
				default -> {
				}
			}
		}
		if (me != null || partner != null) {
			s.markDirty();
		}
		return out;
	}

	/**
	 * A player took one of the beat's choices ({@code success}: the check passed, or there was none). The thread moves to
	 * the choice's beat (or its {@code fail} beat; with neither it stays); returns the effects for the player.
	 */
	public static List<PlayerEffect> choose(Settlement s, TownLives town, TownLives.Notable n, LivesBook.Choice c, @Nullable String to, boolean success,
		UUID player, String playerName, long day) {
		LivesBook.Thread t = LivesBook.get().thread(n.thread);
		String target = to != null ? to : success ? c.to() : c.fail();
		n.chose = c.id();
		town.stats.add("choices");
		if (t == null || target == null || target.equals(n.beat)) {
			return List.of(); // a failed check that leaves the story where it was: its time keeps running
		}
		SplittableRandom random = new SplittableRandom(LivesDay.seed(s.id(), day) ^ player.getLeastSignificantBits());
		return move(s, town, n, t, target, day, Lives.config(), random, Lives.events(s), player, playerName, success ? c.effects() : null);
	}

	/** The pending effects kept on a character for this player (and forgets them). */
	public static List<PlayerEffect> takePending(TownLives.Notable n, UUID player) {
		if (!player.equals(n.by)) {
			return List.of();
		}
		List<PlayerEffect> out = new ArrayList<>();
		for (String f : List.copyOf(n.flags)) {
			if (f.startsWith(PENDING)) {
				String[] p = f.substring(PENDING.length()).split("[|]", 4);
				if (p.length >= 3) {
					int amount = 0;
					try {
						amount = Integer.parseInt(p[2]);
					} catch (NumberFormatException ignored) {
						// a hand-edited save: nothing
					}
					out.add(new PlayerEffect(p[0], p.length > 3 ? p[3] : "", amount, p[1]));
				}
				n.flags.remove(f);
			}
		}
		return out;
	}
}
