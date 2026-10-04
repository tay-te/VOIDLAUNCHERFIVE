package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Births, weddings and deaths as the town marks them (docs/SOCIAL.md §1). All of it is data and runs the same whether
 * anyone is near or not: a match is made and the banns are posted, on the day the couple are wed (and the feast eats
 * from the stockpile), the dead are given a funeral the next day. The gatherings themselves are folk behaviours, seen
 * only when the town is embodied.
 */
public final class FamilyLife {
	private FamilyLife() {
	}

	/** A new notice for the board (the caller posts it and announces it). */
	public record Posting(String kind, long eventDay, String key, List<String> args) {
	}

	/** The day's family business: weddings held today, a new match perhaps. */
	public static void day(Settlement s, SocialLedger ledger, long day, SocialConfig.Family cfg, Consumer<Posting> post) {
		ledger.family.removeIf(e -> e.day() < day - 1);
		// today's weddings: the couple are wed if both are still here and free
		for (int i = ledger.family.size() - 1; i >= 0; i--) {
			SocialLedger.FamilyEvent e = ledger.family.get(i);
			if (e.kind() != SocialLedger.FamilyEvent.Kind.WEDDING || e.day() != day || e.people().size() < 2) {
				continue;
			}
			Resident a = s.resident(e.people().get(0));
			Resident b = s.resident(e.people().get(1));
			if (a == null || b == null || !FolkFacts.marry(a, b, day)) {
				ledger.family.remove(i);
				post.accept(new Posting("wedding", day, "hearth.social.notice.wedding_off", e.names()));
				continue;
			}
			// one household: the second moves in with the first (the sim sorts out a full house at its next count)
			if (a.home != Resident.NO_PLOT && b.home != a.home) {
				b.home = a.home;
			} else if (a.home == Resident.NO_PLOT && b.home != Resident.NO_PLOT) {
				a.home = b.home;
			}
			s.stockpile().take(Goods.FOOD, cfg.weddingFood());
		}
		if (!cfg.weddings()) {
			return;
		}
		SplittableRandom random = new SplittableRandom(TownCalendar.mix(s.id() * 0x51ED27L + day));
		if (random.nextDouble() >= cfg.weddingChance()) {
			return;
		}
		Set<UUID> promised = new HashSet<>();
		for (SocialLedger.FamilyEvent e : ledger.family) {
			if (e.kind() == SocialLedger.FamilyEvent.Kind.WEDDING) {
				promised.addAll(e.people());
			}
		}
		Matchmaker.Match m = Matchmaker.find(s.residents(), promised, cfg.sameSex(), cfg.sameSexShare(), random);
		if (m == null) {
			return;
		}
		long when = day + Math.max(1, cfg.bannsDays());
		List<UUID> kin = new ArrayList<>(FolkFacts.family(s, m.a()));
		for (UUID k : FolkFacts.family(s, m.b())) {
			if (!kin.contains(k)) {
				kin.add(k);
			}
		}
		List<String> names = List.of(m.a().fullName(), m.b().fullName());
		ledger.family.add(new SocialLedger.FamilyEvent(SocialLedger.FamilyEvent.Kind.WEDDING, when, List.of(m.a().id, m.b().id), names, kin, ""));
		post.accept(new Posting("wedding", when, "hearth.social.notice.banns", names));
	}

	/** A child was born in town. */
	public static void born(Settlement s, Resident child, long day, SocialConfig.Family cfg, Consumer<Posting> post) {
		if (!cfg.births() || child.age != AgeStage.CHILD) {
			return;
		}
		List<String> parents = new ArrayList<>();
		for (UUID p : FolkFacts.parents(child)) {
			Resident r = s.resident(p);
			if (r != null) {
				parents.add(r.givenName);
			}
		}
		if (parents.isEmpty()) {
			return;
		}
		String key = parents.size() >= 2 ? "hearth.social.notice.birth" : "hearth.social.notice.birth_one";
		List<String> args = new ArrayList<>();
		args.add(child.givenName);
		args.addAll(parents.subList(0, Math.min(2, parents.size())));
		post.accept(new Posting("birth", day, key, args));
	}

	/**
	 * Someone left the settlement for good.
	 *
	 * @param cause {@code age}, {@code killed}, {@code slain}, or {@code left} (emigrated: no funeral)
	 */
	public static void gone(Settlement s, SocialLedger ledger, Resident r, String cause, long day, SocialConfig.Family cfg, Consumer<Posting> post) {
		if (cause.equals("left")) {
			post.accept(new Posting("left", day, "hearth.social.notice.left", List.of(r.fullName())));
			return;
		}
		// a death: kin are found from the living, whose links still name the dead
		List<UUID> kin = new ArrayList<>();
		for (Resident o : s.residents()) {
			if (o.id.equals(r.id)) {
				continue;
			}
			if (r.id.equals(FolkFacts.spouse(o)) || FolkFacts.parents(o).contains(r.id) || FolkFacts.children(o).contains(r.id)
				|| FolkFacts.parents(r).contains(o.id)) {
				kin.add(o.id);
			}
		}
		// any wedding of theirs is off
		for (int i = ledger.family.size() - 1; i >= 0; i--) {
			SocialLedger.FamilyEvent e = ledger.family.get(i);
			if (e.kind() == SocialLedger.FamilyEvent.Kind.WEDDING && e.people().contains(r.id) && e.day() >= day) {
				ledger.family.remove(i);
				post.accept(new Posting("wedding", e.day(), "hearth.social.notice.wedding_off", e.names()));
			}
		}
		String deathKey = switch (cause) {
			case "slain" -> "hearth.social.notice.slain";
			case "killed" -> "hearth.social.notice.killed";
			default -> "hearth.social.notice.died";
		};
		post.accept(new Posting("death", day, deathKey, List.of(r.fullName())));
		if (!cfg.funerals()) {
			return;
		}
		long when = day + Math.max(0, cfg.funeralDelayDays());
		ledger.family.add(new SocialLedger.FamilyEvent(SocialLedger.FamilyEvent.Kind.FUNERAL, when, List.of(r.id), List.of(r.fullName()), kin, cause));
		post.accept(new Posting("funeral", when, "hearth.social.notice.funeral", List.of(r.fullName())));
	}
}
