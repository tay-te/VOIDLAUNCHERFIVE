package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.quest.model.QuestReward;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/** Writes a reward out for the client: "2 marks, 4 bits · 3 × Bread · +4 standing". */
public final class RewardText {
	private RewardText() {
	}

	public static String describe(QuestReward reward, GameFacts facts) {
		List<String> parts = new ArrayList<>();
		if (reward.coin() > 0) {
			parts.add(facts.coinText(reward.coin()));
		}
		for (QuestReward.ItemReward item : reward.items()) {
			parts.add(item.count() + " × " + facts.itemName(item.item()));
		}
		if (reward.reputation() != 0) {
			parts.add((reward.reputation() > 0 ? "+" : "") + reward.reputation() + " standing");
		}
		return String.join(" · ", parts);
	}

	/** The reward with its text filled in. */
	public static QuestReward withText(QuestReward reward, GameFacts facts) {
		return reward.withText(describe(reward, facts));
	}

	static String itemName(Identifier id, GameFacts facts) {
		return facts.itemName(id);
	}
}
