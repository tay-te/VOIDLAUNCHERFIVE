package dev.voidmc.hearth.npc.talk;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The number key each conversation option answers to, over the whole list every {@link dev.voidmc.hearth.api.DialogueProvider}
 * filled (docs/OVERLAY.md, {@code talk.options[].key}): one goodbye takes 0, every other option counts 1 to 9 in the
 * order shown, and options past the ninth get no key (-1) but stay clickable. Keys never repeat, and goodbye is found by
 * its id first, so the keys do not shift when the option facts ({@code TalkPayloads.Detail}) arrive after the list. Pure.
 */
public final class OptionKeys {
	/** No number key (the option is still clickable). */
	public static final int NONE = -1;
	/** Goodbye's key. */
	public static final int LEAVE = 0;
	/** The last number key an ordinary option gets. */
	public static final int LAST = 9;

	private OptionKeys() {
	}

	/**
	 * The keys for these option ids, in order. {@code kind} names an option's kind (or null when not known yet); without
	 * {@link NpcDialogue#BYE} in the list, the first {@code leave} option is the goodbye. Any further {@code leave} options
	 * are counted like the rest, so none goes missing.
	 */
	public static int[] assign(List<String> ids, Function<String, @Nullable String> kind) {
		int leave = ids.indexOf(NpcDialogue.BYE);
		if (leave < 0) {
			for (int i = 0; i < ids.size(); i++) {
				if ("leave".equals(kind.apply(ids.get(i)))) {
					leave = i;
					break;
				}
			}
		}
		int[] keys = new int[ids.size()];
		int next = 1;
		for (int i = 0; i < ids.size(); i++) {
			keys[i] = i == leave ? LEAVE : next <= LAST ? next++ : NONE;
		}
		return keys;
	}
}
