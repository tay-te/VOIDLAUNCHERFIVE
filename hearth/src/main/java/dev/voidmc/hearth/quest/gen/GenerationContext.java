package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.QuestConfig;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Everything the generator reads to make a settlement's offers. Built by the game from live state, or directly by tests
 * from a {@code SimpleSettlement}. Nothing in it touches the world: the generator never loads a chunk or scans blocks.
 *
 * @param data             the settlement's quest state (offers are added to it)
 * @param day              the sim day
 * @param others           other live settlements of the same world (destinations for couriers, escorts, letters)
 * @param locateRequests   called with structure ids an exploration template wanted but found no located target for;
 *                         the game queues a search off the hot path and a later refresh can use the result
 */
public record GenerationContext(Settlement settlement, SettlementQuests data, long day, RandomSource random, List<? extends Settlement> others,
	GameFacts facts, QuestConfig config, Consumer<List<Identifier>> locateRequests) {
}
