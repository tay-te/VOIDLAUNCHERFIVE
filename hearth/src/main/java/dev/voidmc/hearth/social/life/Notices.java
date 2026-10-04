package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.social.SocialLedger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The town's notices: births, weddings to come, funerals, deaths, appointments, feast days. Kept in the ledger (newest
 * last), read out by a crier in the square when the town is embodied, listed in conversation and by command, and meant
 * for the quest module's notice board (docs/requests/p3-social.md).
 */
public final class Notices {
	private Notices() {
	}

	/** Adds a notice, dropping the oldest past ones beyond {@code max}. Returns it. */
	public static SocialLedger.Notice post(SocialLedger ledger, String kind, long day, long eventDay, String key, List<String> args, int max) {
		SocialLedger.Notice n = new SocialLedger.Notice(kind, day, eventDay, key, List.copyOf(args), false);
		ledger.notices.add(n);
		while (ledger.notices.size() > max) {
			// drop the oldest notice whose event is past; failing that the oldest of all
			int drop = 0;
			for (int i = 0; i < ledger.notices.size(); i++) {
				if (ledger.notices.get(i).eventDay() < day) {
					drop = i;
					break;
				}
			}
			ledger.notices.remove(drop);
		}
		return n;
	}

	/** Whether a notice of this kind and text is already up (so a day caught up twice does not post twice). */
	public static boolean has(SocialLedger ledger, String key, List<String> args, long eventDay) {
		for (SocialLedger.Notice n : ledger.notices) {
			if (n.key().equals(key) && n.args().equals(args) && n.eventDay() == eventDay) {
				return true;
			}
		}
		return false;
	}

	/** The notice's words. An argument written {@code @key} is itself translated (a festival's name, an office). */
	public static Component text(SocialLedger.Notice n) {
		Object[] args = new Object[n.args().size()];
		for (int i = 0; i < args.length; i++) {
			String a = n.args().get(i);
			args[i] = a.startsWith("@") ? Component.translatable(a.substring(1)) : a;
		}
		return Component.translatable(n.key(), args);
	}

	/** Notices still current on {@code day} (put up within a week, or announcing something to come), newest first. */
	public static List<SocialLedger.Notice> current(SocialLedger ledger, long day) {
		List<SocialLedger.Notice> out = new ArrayList<>();
		for (int i = ledger.notices.size() - 1; i >= 0; i--) {
			SocialLedger.Notice n = ledger.notices.get(i);
			if (n.eventDay() >= day || day - n.day() <= 7) {
				out.add(n);
			}
		}
		return out;
	}

	/** The notice's words with when it happens, told from {@code today}: "(tomorrow)", "(in 3 days)", "(today)". */
	public static Component text(SocialLedger.Notice n, long today) {
		Component base = text(n);
		long d = n.eventDay() - today;
		if (n.eventDay() == n.day()) {
			return base; // news
		}
		Component when = d < 0 ? null : d == 0 ? Component.translatable("hearth.social.when.today")
			: d == 1 ? Component.translatable("hearth.social.when.tomorrow") : Component.translatable("hearth.social.when.days", d);
		return when == null ? base : base.copy().append(" ").append(when);
	}
}
