package dev.voidmc.hearth.quest.commission;

import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.quest.service.Standings;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The retinue's rules (docs/LIVING_WORLD_2.md §8.1–§8.2), pure: who may ask to serve, when a servant of the lord may rise
 * and why not yet, what a new lord does to a service, and which commissions a rank is given.
 */
public final class RetinueRules {
	private RetinueRules() {
	}

	/** Whether a player may ask to serve: Known in the seat at least, and not wanted there. */
	public static boolean mayPetition(Title title) {
		return Standings.meets(title, Title.KNOWN);
	}

	public enum Why {
		OK, TOP, PLEDGE, COMMISSIONS, TITLE, STANDING, FEAT, NO_KNIGHTHOOD
	}

	/**
	 * Whether the servant may rise to the next rank now, and if not, what they lack.
	 *
	 * @param pledged  sworn to the lord with the pledge of service (the social module's pledge)
	 * @param standing their standing in the seat
	 */
	public static Why rise(Retinue.Service s, boolean pledged, Title title, int standing, PlaceConfig.Commissions cfg) {
		Rank next = s.rank().next();
		if (next == null || next == Rank.KNIGHT && !cfg.knighthood()) {
			return next == null ? Why.TOP : Why.NO_KNIGHTHOOD;
		}
		switch (next) {
			case RETAINER -> {
				if (!pledged) {
					return Why.PLEDGE;
				}
				return s.done() >= cfg.retainer() ? Why.OK : Why.COMMISSIONS;
			}
			case SWORN -> {
				if (s.done() < cfg.sworn()) {
					return Why.COMMISSIONS;
				}
				return Standings.meets(title, Title.FRIEND) ? Why.OK : Why.TITLE;
			}
			case KNIGHT -> {
				if (s.done() < cfg.knight()) {
					return Why.COMMISSIONS;
				}
				if (standing < cfg.knightStanding()) {
					return Why.STANDING;
				}
				return s.feat() ? Why.OK : Why.FEAT;
			}
			default -> {
				return Why.TOP;
			}
		}
	}

	/** How many commissions the next rank asks in all (for "two more"), or 0 at the top. */
	public static int needed(Rank rank, PlaceConfig.Commissions cfg) {
		Rank next = rank.next();
		if (next == null) {
			return 0;
		}
		return switch (next) {
			case RETAINER -> cfg.retainer();
			case SWORN -> cfg.sworn();
			case KNIGHT -> cfg.knight();
			default -> 0;
		};
	}

	/**
	 * The service as the seat now stands: a new lord (the old one dead, an heir in the seat) keeps it one rank lower until
	 * the player pledges again; pledged again, the old rank comes back. Unchanged when the lord is the same or the seat
	 * is empty.
	 */
	public static Retinue.Service confirm(Retinue.Service s, @Nullable UUID currentLord, boolean pledgedToCurrent) {
		if (currentLord == null) {
			return s;
		}
		Retinue.Service out = s;
		if (s.lord().isPresent() && !s.lord().get().equals(currentLord)) {
			out = s.underHeir(currentLord);
		} else if (s.lord().isEmpty()) {
			out = new Retinue.Service(s.rank(), s.done(), java.util.Optional.of(currentLord), s.since(), s.feat(), s.demotedFrom(), s.horse(), s.house(),
				s.open());
		}
		if (out.demotedFrom().isPresent() && pledgedToCurrent) {
			out = out.repledged();
		}
		return out;
	}

	/** Whether a commission of this least rank is given to a servant of this rank. */
	public static boolean given(Rank least, Rank have) {
		return have.atLeast(least);
	}
}
