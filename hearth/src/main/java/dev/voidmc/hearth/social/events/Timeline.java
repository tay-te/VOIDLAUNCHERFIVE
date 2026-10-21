package dev.voidmc.hearth.social.events;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A scene's timeline (docs/LIVING_WORLD_2.md §0.2 "Scenes", §3.3): the beats of a rule in time, in game minutes from
 * the scene's start. Pure: which beats fall due between two moments, and whether a rule's timeline is sound — it ends,
 * every role it names exists and has a place to be, every action and venue is known.
 */
public final class Timeline {
	private Timeline() {
	}

	/** The beats due after minute {@code from} up to and including {@code to}, in order (from -1 takes those at 0). */
	public static List<EventRule.Beat> due(List<EventRule.Beat> beats, int from, int to) {
		List<EventRule.Beat> out = new ArrayList<>();
		for (EventRule.Beat b : beats) {
			if (b.at() > from && b.at() <= to) {
				out.add(b);
			}
		}
		return out;
	}

	/** Game minutes since a scene began, from town ticks (a minute is 1000/60 ticks). */
	public static int minutes(long start, long now) {
		return (int) Math.max(0, (now - start) * 60 / 1000);
	}

	/** Where a role stands at a minute: the venue of its last placing beat at or before it, else its own place, else "". */
	public static String placeAt(EventRule rule, String role, int minute) {
		String at = "";
		for (EventRule.RoleSpec r : rule.roles()) {
			if (r.name().equals(role)) {
				at = r.place();
			}
		}
		for (EventRule.Beat b : rule.timeline()) {
			if (b.at() > minute) {
				break;
			}
			if (b.places() && (b.role().equals(role) || b.role().equals("all")) && !b.to().isEmpty()) {
				at = b.to();
			}
		}
		return at;
	}

	/** What is wrong with a rule's scene (empty when it is sound). */
	public static List<String> problems(EventRule rule) {
		List<String> out = new ArrayList<>();
		String k = rule.kind().id() + (rule.scene().isEmpty() ? "" : ":" + rule.scene());
		if (rule.duration() <= 0) {
			out.add(k + ": a scene must last (duration " + rule.duration() + ")");
		}
		if (!rule.venue().isEmpty() && !SceneVenues.NAMES.contains(rule.venue())) {
			out.add(k + ": unknown venue " + rule.venue());
		}
		Set<String> roles = new HashSet<>();
		for (EventRule.RoleSpec r : rule.roles()) {
			if (!roles.add(r.name())) {
				out.add(k + ": role " + r.name() + " twice");
			}
			if (!SceneCast.PICKS.contains(r.pick())) {
				out.add(k + ": role " + r.name() + " picks unknown " + r.pick());
			}
			if (!r.place().isEmpty() && !SceneVenues.NAMES.contains(r.place())) {
				out.add(k + ": role " + r.name() + " stands at unknown " + r.place());
			}
		}
		int last = -1;
		for (EventRule.Beat b : rule.timeline()) {
			if (b.at() < last) {
				out.add(k + ": beat at " + b.at() + " out of order");
			}
			last = Math.max(last, b.at());
			if (b.at() < 0 || b.at() > rule.duration()) {
				out.add(k + ": beat at " + b.at() + " outside the scene's " + rule.duration() + " minutes");
			}
			if (!EventRule.ACTIONS.contains(b.action())) {
				out.add(k + ": unknown action " + b.action());
			}
			if (!b.role().equals("all") && !b.role().equals("scene") && !roles.contains(b.role())) {
				out.add(k + ": beat for unknown role " + b.role());
			}
			if (!b.to().isEmpty() && !SceneVenues.NAMES.contains(b.to())) {
				out.add(k + ": beat goes to unknown venue " + b.to());
			}
			if (b.places() && b.to().isEmpty()) {
				out.add(k + ": a " + b.action() + " beat at " + b.at() + " needs a place");
			}
		}
		// every role has somewhere to be from the first minute it is on
		for (EventRule.RoleSpec r : rule.roles()) {
			if (placeAt(rule, r.name(), rule.duration()).isEmpty()) {
				out.add(k + ": role " + r.name() + " has no place");
			}
		}
		return out;
	}
}
