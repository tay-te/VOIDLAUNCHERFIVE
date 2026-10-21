package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.law.justice.Trials;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.TownLife;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The town events in conversation (docs/LIVING_WORLD_2.md §3.1; order band 80–99, §0.4) — no new screen, plain options
 * with their odds and costs described for the Hub's conversation page: what is on in town and who is being married; a
 * gift for the couple; a pauper's burial paid; testimony at a trial (a speech check that weighs what the player saw);
 * a poor accused's fine paid, or a convict let out of the pillory (or freed, a crime); mercy pleaded with the lord at the
 * gallows; the archery contest and the melee entered; a poor family's tax paid; a coin for the crew at a topping-out; a
 * gift to rebuilding a burnt house.
 */
public final class EventDialogue implements DialogueProvider {
	static final String WHAT = "events:what";
	static final String WHO_WED = "events:who_wed";
	static final String GIFT = "events:gift";
	static final String BURIAL = "events:burial";
	static final String FOR = "events:testify_for";
	static final String AGAINST = "events:testify_against";
	static final String FINE = "events:pay_fine";
	static final String LET_OUT = "events:let_out";
	static final String FREE = "events:free";
	static final String MERCY = "events:mercy";
	static final String ARCHERY = "events:archery";
	static final String MELEE = "events:melee";
	static final String TAX = "events:pay_tax";
	static final String CREW = "events:crew_coin";
	static final String REBUILD = "events:rebuild";

	static final long GIFT_BITS = 10;
	static final long BURIAL_BITS = 15;
	static final long ENTRY_BITS = 5;
	static final long CREW_BITS = 2;
	static final long REBUILD_BITS = 20;

	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();
	/** What each player has done once already (event/option), so a gift or a testimony is given once. */
	private static final Set<String> DONE = ConcurrentHashMap.newKeySet();

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		if (!TownEvents.config().events().enabled()) {
			return;
		}
		EventBook book = s.data(EventBook.KEY);
		long today = TownLife.today(s);
		Scheduled wedding = null;
		for (Scheduled e : book.scheduled) {
			if (e.kind() == EventKind.WEDDING && e.phase() != Scheduled.Phase.ENDED && e.day() >= today && e.day() <= today + 3) {
				wedding = e;
			}
		}
		add(out, WHAT, Component.translatable("hearth.events.ask.what"), 82, "talk", -1);
		if (wedding != null) {
			add(out, WHO_WED, Component.translatable("hearth.events.ask.who_wed"), 83, "talk", -1);
			if (wedding.day() == today && wedding.principals().contains(r.id) && !done(player, wedding, GIFT)) {
				add(out, GIFT, Component.translatable("hearth.events.gift", Hooks.coins(GIFT_BITS)), 86, "service", GIFT_BITS);
			}
		}
		if (r.job == JobType.PRIEST || r.job == JobType.MONK) {
			Scheduled f = funeralOfPoor(s, book, today);
			if (f != null && !done(player, f, BURIAL)) {
				add(out, BURIAL, Component.translatable("hearth.events.burial", f.args().getStringOr("names", ""), Hooks.coins(BURIAL_BITS)), 87, "service",
					BURIAL_BITS);
			}
		}
		for (SceneRun run : Scenes.runs(s.id())) {
			Scheduled e = run.event;
			switch (e.kind()) {
				case TRIAL -> {
					if (run.role("judge").contains(r.id) && !done(player, e, FOR)) {
						String accused = e.args().getStringOr("accused", "");
						boolean saw = witnessed(e, player);
						int odds = (int) Math.round(100 * testimonyOdds(player, s, r, e));
						add(out, FOR, Component.translatable(saw ? "hearth.events.testify.for_saw" : "hearth.events.testify.for", accused), 90, "persuade", -1,
							odds);
						add(out, AGAINST, Component.translatable(saw ? "hearth.events.testify.against_saw" : "hearth.events.testify.against", accused), 91,
							"persuade", -1, odds);
						if (e.args().getBooleanOr("poor", false) && !run.iv.flag("fine_paid")) {
							long fine = e.args().getLongOr("fine", 10);
							add(out, FINE, Component.translatable("hearth.events.pay_fine", accused, Hooks.coins(fine)), 92, "law", fine);
						}
					}
				}
				case PILLORY -> {
					List<UUID> convict = run.role("convict");
					if (convict.isEmpty()) {
						continue;
					}
					String name = EventEffects.name(s, convict);
					if ((r.job == JobType.JAILER || r.job == JobType.GUARD || r.job == JobType.JUDGE) && !convict.contains(r.id)) {
						long fine = e.args().getLongOr("fine", 10);
						add(out, LET_OUT, Component.translatable("hearth.events.let_out", name, Hooks.coins(fine)), 93, "law", fine);
					}
					if (convict.contains(r.id)) {
						add(out, FREE, Component.translatable("hearth.events.free", name), 94, "law", -1);
					}
				}
				case EXECUTION -> {
					if (r.job == JobType.LORD && !done(player, e, MERCY)) {
						int odds = (int) Math.round(100 * mercyOdds(player, s, r));
						add(out, MERCY, Component.translatable("hearth.events.mercy", EventEffects.name(s, e.principals())), 95, "persuade", -1, odds);
					}
				}
				case FAIR -> {
					if (FairScene.open(run) && !FairScene.entrants(run).containsKey(player.getUUID())
						&& player.blockPosition().distSqr(run.venue("range")) <= 64 * 64) {
						add(out, ARCHERY, Component.translatable("hearth.events.archery.enter", Hooks.coins(ENTRY_BITS)), 96, "service", ENTRY_BITS);
					}
				}
				case TOURNAMENT -> {
					if (run.minute >= run.rule.param("melee_from", 60) - 10 && TourneyScene.rounds(run, player.getUUID()) == 0) {
						add(out, MELEE, Component.translatable("hearth.events.melee.enter", Hooks.coins(ENTRY_BITS)), 97, "service", ENTRY_BITS);
					}
				}
				case TAX -> {
					if (run.role("reeve").contains(r.id) && e.args().contains("poor_family") && !run.iv.flag("paid_for")) {
						long owed = taxShare(e);
						add(out, TAX, Component.translatable("hearth.events.pay_tax", e.args().getStringOr("poor_name", ""), Hooks.coins(owed)), 98, "service",
							owed);
					}
				}
				case TOPPING_OUT -> {
					if ((run.role("master").contains(r.id) || run.role("crew").contains(r.id)) && !done(player, e, CREW)) {
						add(out, CREW, Component.translatable("hearth.events.crew_coin", Hooks.coins(CREW_BITS)), 99, "service", CREW_BITS);
					}
				}
				default -> {
				}
			}
		}
		if ((r.job == JobType.BUILDER || r.job == JobType.CARPENTER || r.job == JobType.MASON) && !book.burnt.isEmpty()) {
			EventBook.Burnt b = book.burnt.get(0);
			if (!DONE.contains(player.getUUID() + "/" + s.id() + "/rebuild/" + b.plot() + "/" + b.day())) {
				add(out, REBUILD, Component.translatable("hearth.events.rebuild", EventEffects.household(s, b.plot()), Hooks.coins(REBUILD_BITS)), 99,
					"service", REBUILD_BITS);
			}
		}
	}

	private static void add(List<Option> out, String id, Component label, int order, String kind, long cost) {
		add(out, id, label, order, kind, cost, -1);
	}

	private static void add(List<Option> out, String id, Component label, int order, String kind, long cost, int odds) {
		Component hint = odds >= 0 ? Component.literal(odds + " %") : cost > 0 ? Hooks.coins(cost) : null;
		out.add(new Option(id, label, order, false, hint, null));
		DialogueProvider.describe(id, new Info(kind, odds, cost, odds >= 0 ? PlayerSkills.SPEECH : null));
	}

	private static boolean done(ServerPlayer p, Scheduled e, String what) {
		return DONE.contains(p.getUUID() + "/" + e.key() + "/" + what);
	}

	private static boolean markDone(ServerPlayer p, Scheduled e, String what) {
		if (DONE.size() > 8192) {
			DONE.clear();
		}
		return DONE.add(p.getUUID() + "/" + e.key() + "/" + what);
	}

	static boolean witnessed(Scheduled e, ServerPlayer p) {
		return Outcomes.uuids(e.args(), "witnesses").contains(p.getUUID());
	}

	static double testimonyOdds(ServerPlayer p, Settlement s, Resident judge, Scheduled e) {
		StandingService st = HearthServices.standing();
		int standing = st == null ? 0 : st.standing(s.id(), p.getUUID());
		int opinion = st == null ? 0 : st.opinion(judge.id, p.getUUID());
		float charisma = st == null ? 0 : st.charisma(p);
		return Trials.testimonyOdds(standing, opinion, charisma, witnessed(e, p), e.args().getStringOr("offence", "theft"));
	}

	static double mercyOdds(ServerPlayer p, Settlement s, Resident lord) {
		StandingService st = HearthServices.standing();
		return st == null ? 0.15 : Math.min(0.6, st.odds(p, s, lord, StandingService.Speech.PERSUADE, 0.7, 0).chance());
	}

	/** A poor family's share of the tax: what the town owes split over its households, at least 5 bits. */
	static long taxShare(Scheduled e) {
		long owed = Outcomes.taxOwed(e.args().getLongOr("treasury", 0), e.args().getIntOr("population", 0), 0.10, 60);
		return Math.max(5, owed / Math.max(4, e.args().getIntOr("population", 8) / 4));
	}

	/** A funeral today or tomorrow whose family is poor, or null. */
	static @Nullable Scheduled funeralOfPoor(Settlement s, EventBook book, long today) {
		for (Scheduled e : book.scheduled) {
			if (e.kind() != EventKind.FUNERAL || e.phase() == Scheduled.Phase.ENDED || e.day() < today || e.day() > today + 1) {
				continue;
			}
			for (UUID k : e.family()) {
				Resident kin = s.resident(k);
				if (kin != null && (kin.station == Station.POOR || kin.station == Station.PEASANT)) {
					return e;
				}
			}
			if (e.family().isEmpty()) {
				return e; // nobody to bury them: a pauper's grave
			}
		}
		return null;
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String optionId) {
		EventBook book = s.data(EventBook.KEY);
		long today = TownLife.today(s);
		switch (optionId) {
			case WHAT -> {
				ANSWER.put(player.getUUID(), whatsOn(s, book, today));
				return true;
			}
			case WHO_WED -> {
				for (Scheduled e : book.scheduled) {
					if (e.kind() == EventKind.WEDDING && e.phase() != Scheduled.Phase.ENDED && e.day() >= today) {
						long in = e.day() - today;
						ANSWER.put(player.getUUID(), Component.translatable(in == 0 ? "hearth.events.answer.wed_today" : "hearth.events.answer.wed_days",
							e.args().getStringOr("names", ""), clock(e.start()), in));
						return true;
					}
				}
				return false;
			}
			case GIFT -> {
				Scheduled e = find(book, EventKind.WEDDING, today);
				if (e == null || !e.principals().contains(r.id) || !markDone(player, e, GIFT)) {
					return false;
				}
				if (!Hooks.charge(player, GIFT_BITS, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				Hooks.deed(player, s, "social:wedding_gift", 3, r.id, player.blockPosition(), true);
				StandingService st = HearthServices.standing();
				if (st != null) {
					for (UUID other : e.principals()) {
						if (!other.equals(r.id)) {
							st.remember(other, player.getUUID(), "wedding_gift", 6);
						}
					}
				}
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.gift"));
				return true;
			}
			case BURIAL -> {
				Scheduled e = funeralOfPoor(s, book, today);
				if (e == null || !markDone(player, e, BURIAL)) {
					return false;
				}
				if (!Hooks.charge(player, BURIAL_BITS, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				s.deposit(BURIAL_BITS);
				Hooks.deed(player, s, "social:paid_burial", 3, e.family().isEmpty() ? null : e.family().get(0), player.blockPosition(), true);
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.burial", e.args().getStringOr("names", "")));
				return true;
			}
			case FOR, AGAINST -> {
				SceneRun run = Scenes.running(s.id(), EventKind.TRIAL);
				if (run == null || !markDone(player, run.event, FOR)) {
					return false;
				}
				double odds = testimonyOdds(player, s, r, run.event);
				boolean believed = player.getRandom().nextDouble() < odds;
				HearthServices.playerSkills().award(player, PlayerSkills.SPEECH, believed ? 30 : 10, "hearth:events/testimony");
				if (believed) {
					run.iv.add(player.getUUID(), "testimony", optionId.equals(FOR) ? -1 : 1);
					UUID accused = run.event.principals().isEmpty() ? null : run.event.principals().get(0);
					if (optionId.equals(FOR) && accused != null) {
						StandingService st = HearthServices.standing();
						if (st != null) {
							st.remember(accused, player.getUUID(), "spoke_for_me", 10);
						}
					}
				}
				ANSWER.put(player.getUUID(), Component.translatable(believed ? "hearth.events.answer.believed" : "hearth.events.answer.not_believed"));
				return true;
			}
			case FINE -> {
				SceneRun run = Scenes.running(s.id(), EventKind.TRIAL);
				if (run == null || run.iv.flag("fine_paid")) {
					return false;
				}
				long fine = run.event.args().getLongOr("fine", 10);
				if (!Hooks.charge(player, fine, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				s.deposit(fine);
				run.iv.set(player.getUUID(), "fine_paid");
				Hooks.deed(player, s, "social:paid_fine", 2, run.event.principals().isEmpty() ? null : run.event.principals().get(0), player.blockPosition(),
					true);
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.fine_paid"));
				return true;
			}
			case LET_OUT -> {
				SceneRun run = Scenes.running(s.id(), EventKind.PILLORY);
				if (run == null) {
					return false;
				}
				long fine = run.event.args().getLongOr("fine", 10);
				if (!Hooks.charge(player, fine, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				s.deposit(fine);
				run.iv.set(player.getUUID(), "fine_paid");
				run.put("end_now", Boolean.TRUE);
				Hooks.deed(player, s, "social:paid_fine", 2, run.role("convict").isEmpty() ? null : run.role("convict").get(0), player.blockPosition(), true);
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.let_out"));
				return true;
			}
			case FREE -> {
				SceneRun run = Scenes.running(s.id(), EventKind.PILLORY);
				if (run == null) {
					return false;
				}
				run.iv.set(player.getUUID(), "freed");
				run.put("end_now", Boolean.TRUE);
				// freeing a convict is breaking them out: a crime if anyone sees it (the whole square does)
				Hooks.report(new LawService.Crime(LawService.Offence.JAILBREAK, player.getUUID(), s.id(), player.blockPosition(), r.id, 0, true,
					player.level().getGameTime()));
				StandingService st = HearthServices.standing();
				if (st != null) {
					st.remember(r.id, player.getUUID(), "freed_me", 25);
				}
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.freed"));
				return true;
			}
			case MERCY -> {
				SceneRun run = Scenes.running(s.id(), EventKind.EXECUTION);
				if (run == null || !markDone(player, run.event, MERCY)) {
					return false;
				}
				double odds = mercyOdds(player, s, r);
				boolean granted = player.getRandom().nextDouble() < odds;
				HearthServices.playerSkills().award(player, PlayerSkills.SPEECH, granted ? 45 : 15, "hearth:events/mercy");
				if (granted) {
					run.iv.set(player.getUUID(), "mercy");
					run.put("end_now", Boolean.TRUE);
				}
				ANSWER.put(player.getUUID(), Component.translatable(granted ? "hearth.events.answer.mercy" : "hearth.events.answer.no_mercy"));
				return true;
			}
			case ARCHERY -> {
				SceneRun run = Scenes.running(s.id(), EventKind.FAIR);
				if (run == null || !FairScene.open(run)) {
					return false;
				}
				if (!Hooks.charge(player, ENTRY_BITS, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				s.deposit(ENTRY_BITS);
				ANSWER.put(player.getUUID(), Component.translatable(FairScene.enter(run, player) ? "hearth.events.answer.archery" : "hearth.events.answer.full"));
				return true;
			}
			case MELEE -> {
				SceneRun run = Scenes.running(s.id(), EventKind.TOURNAMENT);
				if (run == null) {
					return false;
				}
				if (!Hooks.charge(player, ENTRY_BITS, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				s.deposit(ENTRY_BITS);
				ANSWER.put(player.getUUID(), Component.translatable(TourneyScene.enter(run, player) ? "hearth.events.answer.melee" : "hearth.events.answer.full"));
				return true;
			}
			case TAX -> {
				SceneRun run = Scenes.running(s.id(), EventKind.TAX);
				if (run == null || run.iv.flag("paid_for")) {
					return false;
				}
				long owed = taxShare(run.event);
				if (!Hooks.charge(player, owed, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				run.iv.set(player.getUUID(), "paid_for");
				UUID poor = null;
				try {
					poor = UUID.fromString(run.event.args().getStringOr("poor_family", ""));
				} catch (IllegalArgumentException ignored) {
					poor = null;
				}
				Hooks.deed(player, s, "social:paid_tax", 5, poor, player.blockPosition(), true);
				StandingService st = HearthServices.standing();
				if (st != null && poor != null) {
					st.remember(poor, player.getUUID(), "paid_our_tax", 30); // a strong memory
				}
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.tax", run.event.args().getStringOr("poor_name", "")));
				return true;
			}
			case CREW -> {
				SceneRun run = Scenes.running(s.id(), EventKind.TOPPING_OUT);
				if (run == null || !markDone(player, run.event, CREW)) {
					return false;
				}
				if (!Hooks.charge(player, CREW_BITS, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				Hooks.deed(player, s, "social:crew_coin", 1, r.id, player.blockPosition(), true);
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.crew"));
				return true;
			}
			case REBUILD -> {
				if (book.burnt.isEmpty()) {
					return false;
				}
				EventBook.Burnt b = book.burnt.get(0);
				if (!DONE.add(player.getUUID() + "/" + s.id() + "/rebuild/" + b.plot() + "/" + b.day())) {
					return false;
				}
				if (!Hooks.charge(player, REBUILD_BITS, r, s)) {
					ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.short"));
					return true;
				}
				s.deposit(REBUILD_BITS);
				// coin for timber: the house is up a day sooner
				book.burnt.set(0, new EventBook.Burnt(b.plot(), b.day(), Math.max(b.day() + 1, b.until() - 1), b.applied(), b.blocks()));
				s.markDirty();
				Hooks.deed(player, s, "social:gave_to_rebuilding", 2, null, player.blockPosition(), true);
				ANSWER.put(player.getUUID(), Component.translatable("hearth.events.answer.rebuild"));
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private static @Nullable Scheduled find(EventBook book, EventKind kind, long day) {
		for (Scheduled e : book.scheduled) {
			if (e.kind() == kind && e.day() == day && e.phase() != Scheduled.Phase.ENDED) {
				return e;
			}
		}
		return null;
	}

	/** What is on in town today (and what is coming in the next three days), as a resident would say it. */
	static Component whatsOn(Settlement s, EventBook book, long today) {
		List<Component> parts = new ArrayList<>();
		for (Scheduled e : book.scheduled) {
			if (e.phase() == Scheduled.Phase.ENDED || e.day() < today || e.day() > today + 3) {
				continue;
			}
			Component when = e.day() == today ? Component.translatable("hearth.events.answer.at", clock(e.start()))
				: e.day() == today + 1 ? Component.translatable("hearth.social.when.tomorrow") : Component.translatable("hearth.social.when.days", e.day() - today);
			parts.add(Component.translatable("hearth.events.answer.item", Component.translatable(e.kind().translationKey()), when));
		}
		HearthWorld w = TownLife.world(s);
		if (w != null && dev.voidmc.hearth.social.calendar.TownCalendar.nextFairDay(s.id(), today, s.culture(), s.foundedDay(), s.tier(),
			dev.voidmc.hearth.social.SocialModule.config().festivals()) == today + 7) {
			parts.add(Component.translatable("hearth.events.answer.fair_week"));
		}
		if (parts.isEmpty()) {
			return Component.translatable("hearth.events.answer.quiet");
		}
		Component out = Component.translatable("hearth.events.answer.on");
		for (int i = 0; i < parts.size() && i < 4; i++) {
			out = out.copy().append(i == 0 ? " " : "; ").append(parts.get(i));
		}
		return out;
	}

	static String clock(long townTick) {
		int m = SocialTime.minute(townTick);
		return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return ANSWER.remove(player.getUUID());
	}
}
