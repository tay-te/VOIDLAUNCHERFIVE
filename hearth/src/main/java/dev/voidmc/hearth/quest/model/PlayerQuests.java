package dev.voidmc.hearth.quest.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.QuestType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One player's quests: what they are doing, what they have done, which quest the HUD tracks. Stored on the player as a
 * persistent Fabric attachment and synced to that player's client only (the journal and HUD read it there).
 *
 * <p>Fabric syncs an attachment when a different value is set, so the server never mutates the attached value in place:
 * it mutates a {@link #copy()} and sets that (see {@code QuestStore}).
 */
public final class PlayerQuests {
	/** History kept for the journal; completion counts are kept for every template regardless. */
	public static final int HISTORY_LIMIT = 40;

	public static final Codec<PlayerQuests> CODEC = RecordCodecBuilder.create(i -> i.group(
		QuestInstance.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(p -> p.active),
		QuestRecord.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(p -> p.history),
		UUIDUtil.CODEC.optionalFieldOf("tracked").forGetter(p -> Optional.ofNullable(p.tracked)),
		Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("completions", Map.of()).forGetter(p -> p.completions),
		Codec.BOOL.optionalFieldOf("journal_given", false).forGetter(p -> p.journalGiven)
	).apply(i, (active, history, tracked, completions, journal) -> new PlayerQuests(active, history, tracked.orElse(null), completions, journal)));

	public final List<QuestInstance> active;
	public final List<QuestRecord> history;
	public @Nullable UUID tracked;
	public final Map<Identifier, Integer> completions;
	public boolean journalGiven;

	public PlayerQuests() {
		this(List.of(), List.of(), null, Map.of(), false);
	}

	public PlayerQuests(List<QuestInstance> active, List<QuestRecord> history, @Nullable UUID tracked, Map<Identifier, Integer> completions,
		boolean journalGiven) {
		this.active = new ArrayList<>(active.size());
		for (QuestInstance q : active) {
			this.active.add(q.copy());
		}
		this.history = new ArrayList<>(history);
		this.tracked = tracked;
		this.completions = new HashMap<>(completions);
		this.journalGiven = journalGiven;
	}

	public PlayerQuests copy() {
		return new PlayerQuests(this.active, this.history, this.tracked, this.completions, this.journalGiven);
	}

	public @Nullable QuestInstance get(UUID id) {
		for (QuestInstance q : this.active) {
			if (q.id.equals(id)) {
				return q;
			}
		}
		return null;
	}

	public int completed(Identifier template) {
		return this.completions.getOrDefault(template, 0);
	}

	/** The tracked quest if it is still active, else the first active quest, else null. */
	public @Nullable QuestInstance trackedOrFirst() {
		if (this.tracked != null) {
			QuestInstance q = get(this.tracked);
			if (q != null) {
				return q;
			}
		}
		return this.active.isEmpty() ? null : this.active.getFirst();
	}

	/** Moves a quest out of the active list into history with its outcome. */
	public void finish(QuestInstance quest, QuestStatus outcome, long day) {
		this.active.removeIf(q -> q.id.equals(quest.id));
		quest.status = outcome;
		if (outcome == QuestStatus.COMPLETED) {
			this.completions.merge(quest.template, 1, Integer::sum);
		}
		this.history.addFirst(new QuestRecord(quest.template, quest.type, quest.text.title(), quest.settlementName, quest.giver.name(), outcome,
			day, outcome == QuestStatus.COMPLETED ? quest.reward.coin() : 0));
		while (this.history.size() > HISTORY_LIMIT) {
			this.history.removeLast();
		}
		if (quest.id.equals(this.tracked)) {
			this.tracked = this.active.isEmpty() ? null : this.active.getFirst().id;
		}
	}

	/** A finished quest, as the journal's history shows it. */
	public record QuestRecord(Identifier template, QuestType type, String title, String settlement, String giver, QuestStatus outcome, long day,
		long coin) {
		public static final Codec<QuestRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("template").forGetter(QuestRecord::template),
			QuestType.CODEC.fieldOf("type").forGetter(QuestRecord::type),
			Codec.STRING.fieldOf("title").forGetter(QuestRecord::title),
			Codec.STRING.optionalFieldOf("settlement", "").forGetter(QuestRecord::settlement),
			Codec.STRING.optionalFieldOf("giver", "").forGetter(QuestRecord::giver),
			QuestStatus.CODEC.fieldOf("outcome").forGetter(QuestRecord::outcome),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(QuestRecord::day),
			Codec.LONG.optionalFieldOf("coin", 0L).forGetter(QuestRecord::coin)
		).apply(i, QuestRecord::new));
	}
}
