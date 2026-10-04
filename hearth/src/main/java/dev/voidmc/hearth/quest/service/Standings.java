package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.StandingService.Title;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The quest module's view of reputation (docs/LIVING_WORLD.md §3): through the standing service when it is present, else
 * the settlement's own numbers. Every completion, failure and abandonment is reported as a {@link StandingService.Deed},
 * so the standing module moves the town's standing, the giver's opinion and the gossip; without it the settlement's
 * standing is moved directly, as in phase 2.
 */
public final class Standings {
	private Standings() {
	}

	/** The player's standing in a settlement, −100 to 100. */
	public static int standing(Settlement s, UUID player) {
		StandingService st = HearthServices.standing();
		return st != null ? st.standing(s.id(), player) : s.reputation(player);
	}

	/** The player's title in a settlement: the standing service's, or one read from the standing and property. */
	public static Title title(Settlement s, UUID player) {
		StandingService st = HearthServices.standing();
		if (st != null) {
			return st.title(s.id(), player);
		}
		return titleOf(s.reputation(player), owns(s, player));
	}

	/**
	 * The title a standing earns without the standing module: Outlaw ≤ −60 &lt; Distrusted ≤ −15 &lt; Stranger &lt; 10 ≤ Known
	 * &lt; 35 ≤ Friend &lt; 75 ≤ Hero; a property owner who is at least known is a Burgher.
	 */
	public static Title titleOf(int standing, boolean ownsProperty) {
		if (standing <= -60) {
			return Title.OUTLAW;
		}
		if (standing <= -15) {
			return Title.DISTRUSTED;
		}
		if (standing < 10) {
			return Title.STRANGER;
		}
		if (standing >= 75) {
			return Title.HERO;
		}
		if (ownsProperty) {
			return Title.BURGHER;
		}
		return standing >= 35 ? Title.FRIEND : Title.KNOWN;
	}

	/**
	 * Whether a title meets a template's {@code min_title}. Titles rank in their declared order, except that a Burgher
	 * (a property owner) counts as a Friend: owning a house earns trust, not heroism.
	 */
	public static boolean meets(Title have, @Nullable Title need) {
		if (need == null) {
			return true;
		}
		return rank(have) >= rank(need);
	}

	private static int rank(Title t) {
		return switch (t) {
			case OUTLAW -> 0;
			case DISTRUSTED -> 1;
			case STRANGER -> 2;
			case KNOWN -> 3;
			case FRIEND, BURGHER -> 4;
			case HERO -> 5;
		};
	}

	private static boolean owns(Settlement s, UUID player) {
		for (PlotView p : s.plots()) {
			if (player.equals(p.owner())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reports a deed. With the standing service (and deeds on in the config) the service moves the numbers; without it the
	 * settlement's standing moves by {@code weight} directly.
	 *
	 * @param resident the resident most touched (the giver, the rescued), or null
	 */
	public static void deed(Settlement s, UUID player, String kind, int weight, @Nullable UUID resident, BlockPos pos, boolean useService) {
		StandingService st = HearthServices.standing();
		if (useService && st != null) {
			st.deed(new StandingService.Deed(player, s.id(), kind, weight, resident, pos, true));
		} else if (weight != 0) {
			s.addReputation(player, weight);
		}
		s.markDirty();
	}
}
