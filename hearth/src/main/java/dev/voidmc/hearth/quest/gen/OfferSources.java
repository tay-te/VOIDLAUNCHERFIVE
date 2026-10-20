package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.quest.template.QuestTemplate;
import java.util.Arrays;
import java.util.Collection;

/**
 * Extra offer sources for a settlement's board (docs/LIVING_WORLD_2.md §11.2): road happenings (lane A1), a lord's
 * commissions (B4). {@link QuestGenerator#refresh} asks each after its own needs, events and bounty and before it fills
 * the rest of the board, so these come first when the board is short of room. Register during mod init.
 */
public final class OfferSources {
	@FunctionalInterface
	public interface Source {
		/** Posts this source's offers into {@code ctx.data().offers}; returns how many it added. */
		int post(GenerationContext ctx, Collection<QuestTemplate> templates);
	}

	private static volatile Source[] sources = new Source[0];

	private OfferSources() {
	}

	public static synchronized void register(Source source) {
		Source[] next = Arrays.copyOf(sources, sources.length + 1);
		next[next.length - 1] = source;
		sources = next;
	}

	/** Every source's offers; the number added. */
	static int post(GenerationContext ctx, Collection<QuestTemplate> templates) {
		int added = 0;
		for (Source s : sources) {
			added += s.post(ctx, templates);
		}
		return added;
	}
}
