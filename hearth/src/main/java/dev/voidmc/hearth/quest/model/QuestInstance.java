package dev.voidmc.hearth.quest.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A quest made from a template for one settlement: its text already filled in, its objectives bound to real items,
 * places and people. The same class is an offer on a board ({@link QuestStatus#OFFERED}) and, copied, a player's quest.
 *
 * <p>Mutable, server thread only. Players' copies are synced to their client as part of {@link PlayerQuests}.
 */
public final class QuestInstance {
	public static final Codec<QuestInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(q -> q.id),
		Identifier.CODEC.fieldOf("template").forGetter(q -> q.template),
		QuestType.CODEC.fieldOf("type").forGetter(q -> q.type),
		QuestStatus.CODEC.fieldOf("status").forGetter(q -> q.status),
		Text.CODEC.fieldOf("text").forGetter(q -> q.text),
		Giver.CODEC.fieldOf("giver").forGetter(q -> q.giver),
		Codec.LONG.fieldOf("settlement").forGetter(q -> q.settlementId),
		Codec.STRING.optionalFieldOf("settlement_name", "").forGetter(q -> q.settlementName),
		BlockPos.CODEC.fieldOf("turn_in_pos").forGetter(q -> q.turnInPos),
		Objective.CODEC.listOf().fieldOf("objectives").forGetter(q -> q.objectives),
		QuestReward.CODEC.optionalFieldOf("reward", QuestReward.NONE).forGetter(q -> q.reward),
		Codec.LONG.optionalFieldOf("escrow", 0L).forGetter(q -> q.escrow),
		Timing.CODEC.fieldOf("timing").forGetter(q -> q.timing),
		Rules.CODEC.fieldOf("rules").forGetter(q -> q.rules),
		UUIDUtil.CODEC.listOf().optionalFieldOf("taken_by", List.of()).forGetter(q -> List.copyOf(q.takenBy))
	).apply(i, QuestInstance::new));

	public final UUID id;
	public final Identifier template;
	public final QuestType type;
	public QuestStatus status;
	public Text text;
	public Giver giver;
	public final long settlementId;
	public String settlementName;
	/** Where it is handed in: the giver's settlement (its notice board or centre). The journal's waypoint for READY. */
	public BlockPos turnInPos;
	public final List<Objective> objectives;
	public QuestReward reward;
	/** Coin taken from the treasury when the quest was accepted, so the reward is there when it is done. */
	public long escrow;
	public Timing timing;
	public final Rules rules;
	/** On a board: players who have taken a copy (each player takes an offer once). */
	public final Set<UUID> takenBy;

	public QuestInstance(UUID id, Identifier template, QuestType type, QuestStatus status, Text text, Giver giver, long settlementId,
		String settlementName, BlockPos turnInPos, List<Objective> objectives, QuestReward reward, long escrow, Timing timing, Rules rules,
		List<UUID> takenBy) {
		this.id = id;
		this.template = template;
		this.type = type;
		this.status = status;
		this.text = text;
		this.giver = giver;
		this.settlementId = settlementId;
		this.settlementName = settlementName;
		this.turnInPos = turnInPos;
		this.objectives = new ArrayList<>(objectives.size());
		for (Objective o : objectives) {
			this.objectives.add(o.copy());
		}
		this.reward = reward;
		this.escrow = escrow;
		this.timing = timing;
		this.rules = rules;
		this.takenBy = new HashSet<>(takenBy);
	}

	/** A deep copy (a player's own copy of a board offer). */
	public QuestInstance copy() {
		return new QuestInstance(this.id, this.template, this.type, this.status, this.text, this.giver, this.settlementId, this.settlementName,
			this.turnInPos, this.objectives, this.reward, this.escrow, this.timing, this.rules, List.copyOf(this.takenBy));
	}

	/** The objectives the player can make progress on now: all of them, or for a sequential quest the first not done. */
	public List<Objective> currentObjectives() {
		if (!this.rules.sequential) {
			List<Objective> open = new ArrayList<>();
			for (Objective o : this.objectives) {
				if (!o.done()) {
					open.add(o);
				}
			}
			return open;
		}
		for (Objective o : this.objectives) {
			if (!o.done()) {
				return List.of(o);
			}
		}
		return List.of();
	}

	public boolean allDone() {
		for (Objective o : this.objectives) {
			if (!o.done()) {
				return false;
			}
		}
		return true;
	}

	/** The first objective not yet done, or null. */
	public @Nullable Objective firstOpen() {
		for (Objective o : this.objectives) {
			if (!o.done()) {
				return o;
			}
		}
		return null;
	}

	public boolean hasDeadline() {
		return this.timing.deadlineTick > 0;
	}

	@Override
	public String toString() {
		return "Quest[" + this.template + " '" + this.text.title() + "' " + this.status.getSerializedName() + "]";
	}

	/**
	 * The quest's words, placeholders already filled in.
	 *
	 * @param offer    what the giver says when offering it
	 * @param progress what they say while it is under way
	 * @param complete what they say when it is handed in
	 */
	public record Text(String title, String summary, String offer, String progress, String complete) {
		public static final Codec<Text> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("title").forGetter(Text::title),
			Codec.STRING.optionalFieldOf("summary", "").forGetter(Text::summary),
			Codec.STRING.optionalFieldOf("offer", "").forGetter(Text::offer),
			Codec.STRING.optionalFieldOf("progress", "").forGetter(Text::progress),
			Codec.STRING.optionalFieldOf("complete", "").forGetter(Text::complete)
		).apply(i, Text::new));
	}

	/**
	 * Who asked. A quest from the board as a whole (no particular resident) has no resident id and the settlement's
	 * council as its name.
	 */
	public record Giver(Optional<UUID> resident, String name, String job) {
		public static final Codec<Giver> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.optionalFieldOf("resident").forGetter(Giver::resident),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Giver::name),
			Codec.STRING.optionalFieldOf("job", "").forGetter(Giver::job)
		).apply(i, Giver::new));

		public boolean is(UUID resident) {
			return this.resident.isPresent() && this.resident.get().equals(resident);
		}
	}

	/**
	 * @param offeredDay   the sim day it went up on the board
	 * @param expiresDay   the day the offer comes down if nobody takes it
	 * @param deadlineDays days allowed once taken (0: no deadline)
	 * @param acceptedTick game time it was taken
	 * @param deadlineTick game time by which it must be done (0: none)
	 */
	public record Timing(long offeredDay, long expiresDay, int deadlineDays, long acceptedTick, long deadlineTick) {
		public static final Codec<Timing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("offered_day", 0L).forGetter(Timing::offeredDay),
			Codec.LONG.optionalFieldOf("expires_day", 0L).forGetter(Timing::expiresDay),
			Codec.INT.optionalFieldOf("deadline_days", 0).forGetter(Timing::deadlineDays),
			Codec.LONG.optionalFieldOf("accepted_tick", 0L).forGetter(Timing::acceptedTick),
			Codec.LONG.optionalFieldOf("deadline_tick", 0L).forGetter(Timing::deadlineTick)
		).apply(i, Timing::new));

		public Timing accepted(long gameTime, long ticksPerDay) {
			return new Timing(this.offeredDay, this.expiresDay, this.deadlineDays, gameTime,
				this.deadlineDays > 0 ? gameTime + this.deadlineDays * ticksPerDay : 0L);
		}
	}

	/**
	 * @param sequential    objectives are done in order (find the keepsake, then bring it back)
	 * @param minReputation standing needed to take it
	 * @param exclusive     the first taker removes it from the board
	 */
	public record Rules(boolean sequential, TurnIn turnIn, int minReputation, boolean exclusive) {
		public static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("sequential", false).forGetter(Rules::sequential),
			TurnIn.CODEC.optionalFieldOf("turn_in", TurnIn.GIVER).forGetter(Rules::turnIn),
			Codec.INT.optionalFieldOf("min_reputation", -1000).forGetter(Rules::minReputation),
			Codec.BOOL.optionalFieldOf("exclusive", false).forGetter(Rules::exclusive)
		).apply(i, Rules::new));
	}
}
