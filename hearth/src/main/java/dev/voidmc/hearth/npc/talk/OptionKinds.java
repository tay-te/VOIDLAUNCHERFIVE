package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.PlayerSkills;

/**
 * What an option is, when its provider did not say ({@link DialogueProvider#describe}): read from its namespaced id and
 * its order band, so the conversation screens can still pick a glyph (docs/OVERLAY.md, {@code talk.options[].kind}).
 * Pure.
 */
public final class OptionKinds {
	private OptionKinds() {
	}

	/** Mettle's trainer ids ({@code mettle:train/<skill path>} and friends): the skill they teach. */
	private static final String[] METTLE_LESSONS = {"mettle:train/", "mettle:teach/", "mettle:rebuild/"};

	public static DialogueProvider.Info infer(String id, int order) {
		if (order >= 10_000 || id.equals(NpcDialogue.BYE)) {
			return DialogueProvider.Info.of("leave");
		}
		for (String prefix : METTLE_LESSONS) {
			if (id.startsWith(prefix) && id.length() > prefix.length()) {
				return new DialogueProvider.Info("train", -1, -1, "mettle:" + id.substring(prefix.length()));
			}
		}
		if (order >= DialogueProvider.TRAINING_FIRST && order <= DialogueProvider.TRAINING_LAST) {
			return DialogueProvider.Info.of("train");
		}
		if (id.equals("economy:trade")) {
			return DialogueProvider.Info.of("trade");
		}
		if (id.equals("economy:fence") || id.equals("law:fence")) {
			return DialogueProvider.Info.of("fence");
		}
		if (id.startsWith("economy:")) {
			return DialogueProvider.Info.of("bank");
		}
		if (id.startsWith("quest:") || order >= 200 && order < 300) {
			return DialogueProvider.Info.of("quest");
		}
		if (id.startsWith("law:")) {
			return DialogueProvider.Info.of("law");
		}
		if (id.equals("social:dice")) {
			return DialogueProvider.Info.of("dice");
		}
		if (id.startsWith("repair:")) {
			return DialogueProvider.Info.of("repair");
		}
		if (id.startsWith("social:") && order >= 100 && order < 200) {
			return DialogueProvider.Info.of("service");
		}
		if (id.startsWith("standing:")) {
			return new DialogueProvider.Info("talk", -1, -1, null);
		}
		return DialogueProvider.Info.of("talk");
	}

	/** Whether a kind is a speech check (the skill it reads is Speech unless the provider said otherwise). */
	public static boolean speechCheck(String kind) {
		return switch (kind) {
			case "persuade", "intimidate", "bribe", "flatter" -> true;
			default -> false;
		};
	}

	/** The provider's info, filled in from the id where it left gaps (a speech check with no skill reads Speech). */
	public static DialogueProvider.Info complete(DialogueProvider.Info given) {
		if (given.skill() == null && speechCheck(given.kind())) {
			return new DialogueProvider.Info(given.kind(), given.odds(), given.cost(), PlayerSkills.SPEECH);
		}
		return given;
	}

	/**
	 * A training option's fee where its provider did not describe one (Mettle's trainers put it in the label, written with
	 * {@code EconomyService.format}): read back from the label, else the hint, so the chip can show the coins
	 * (docs/OVERLAY.md, {@code talk.options[].cost}). Other kinds and described costs are left as they are.
	 */
	public static DialogueProvider.Info withCost(DialogueProvider.Info info, DialogueProvider.Option option) {
		if (info.cost() >= 0 || !"train".equals(info.kind())) {
			return info;
		}
		long fee = dev.voidmc.hearth.economy.coin.CoinFormat.read(option.label());
		if (fee < 0) {
			fee = dev.voidmc.hearth.economy.coin.CoinFormat.read(option.hint());
		}
		return fee < 0 ? info : new DialogueProvider.Info(info.kind(), info.odds(), fee, info.skill());
	}
}
