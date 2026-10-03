package dev.voidmc.hearth.quest;

import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.TurnIn;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The rules of objective progress, without the world: what a kill, a step, a biome or a hand-in does to a quest. The
 * service feeds it events and positions; the unit tests feed it numbers.
 */
public final class QuestProgress {
	private QuestProgress() {
	}

	/** What the server knows about a creature that died at a player's hand. */
	public record Kill(String entityId, Predicate<String> inTag, boolean monster, BlockPos pos, boolean night) {
	}

	/** Items a player can hand over: counted and taken by an item id or {@code #tag}. */
	public interface ItemSource {
		int count(String target);

		/** Takes up to {@code max}; returns how many were taken. */
		int take(String target, int max);
	}

	/** One objective's share of a hand-in. */
	public record Delivered(Objective objective, int amount) {
	}

	/** Whether a kill counts for an objective's target: {@code monster}, a {@code #tag} or an entity id. */
	public static boolean matches(String target, Kill kill) {
		if (target.equals("monster")) {
			return kill.monster();
		}
		if (target.startsWith("#")) {
			return kill.inTag().test(target.substring(1));
		}
		return target.equals(kill.entityId());
	}

	/** Counts a kill toward the quest's open kill objectives; returns whether anything changed. */
	public static boolean onKill(QuestInstance q, Kill kill) {
		if (q.status != QuestStatus.ACTIVE) {
			return false;
		}
		boolean changed = false;
		for (Objective o : q.currentObjectives()) {
			if (o.kind != ObjectiveKind.KILL || !matches(o.target, kill)) {
				continue;
			}
			if (o.data.getBooleanOr(Objective.Keys.NIGHT, false) && !kill.night()) {
				continue;
			}
			if (o.pos != null && o.radius > 0 && horizontalDistSq(o.pos, kill.pos()) > (double) o.radius * o.radius) {
				continue;
			}
			changed |= o.advance(1) > 0;
		}
		return changed;
	}

	/**
	 * Arrival objectives (reach, explore, biome) done at a position; returns those completed now so the caller can grant
	 * what they give. {@code inBiome} answers whether the position's biome is the given id.
	 */
	public static List<Objective> onPosition(QuestInstance q, BlockPos pos, Predicate<String> inBiome) {
		List<Objective> done = new ArrayList<>();
		if (q.status != QuestStatus.ACTIVE) {
			return done;
		}
		for (Objective o : q.currentObjectives()) {
			boolean arrived = switch (o.kind) {
				case REACH -> o.pos != null && near(o.pos, pos, o.radius);
				case EXPLORE -> insideBox(o, pos);
				case BIOME -> {
					for (String biome : o.target.split(",")) {
						if (!biome.isBlank() && inBiome.test(biome.trim())) {
							yield true;
						}
					}
					yield false;
				}
				default -> false;
			};
			if (arrived) {
				o.complete();
				done.add(o);
			}
		}
		return done;
	}

	/** Within {@code radius} horizontally; when the target's y is known, also within reach vertically. */
	public static boolean near(BlockPos target, BlockPos pos, int radius) {
		int r = Math.max(1, radius);
		if (horizontalDistSq(target, pos) > (double) r * r) {
			return false;
		}
		return target.getY() == Objective.SURFACE_Y || Math.abs(target.getY() - pos.getY()) <= r + 6;
	}

	/** Inside an explore objective's structure bounds, grown by its radius. */
	public static boolean insideBox(Objective o, BlockPos pos) {
		if (!o.data.contains(Objective.Keys.BOX_MIN)) {
			return o.pos != null && near(o.pos, pos, Math.max(o.radius, 24));
		}
		BlockPos min = BlockPos.of(o.data.getLongOr(Objective.Keys.BOX_MIN, 0L));
		BlockPos max = BlockPos.of(o.data.getLongOr(Objective.Keys.BOX_MAX, 0L));
		int r = Math.max(0, o.radius);
		return pos.getX() >= min.getX() - r && pos.getX() <= max.getX() + r
			&& pos.getZ() >= min.getZ() - r && pos.getZ() <= max.getZ() + r
			&& pos.getY() >= min.getY() - r - 8 && pos.getY() <= max.getY() + r + 8;
	}

	/**
	 * Hands over what the player carries toward the quest's open deliver objectives (partial deliveries count). Returns
	 * what was delivered, per objective.
	 */
	public static List<Delivered> handIn(QuestInstance q, ItemSource items) {
		List<Delivered> out = new ArrayList<>();
		if (q.status != QuestStatus.ACTIVE) {
			return out;
		}
		for (Objective o : q.currentObjectives()) {
			if (o.kind != ObjectiveKind.DELIVER || o.done()) {
				continue;
			}
			int taken = items.take(o.target, o.remaining());
			if (taken > 0) {
				o.advance(taken);
				out.add(new Delivered(o, taken));
			}
		}
		return out;
	}

	/** How many of an objective's items the player could hand in now. */
	public static int carried(Objective o, ItemSource items) {
		return o.kind == ObjectiveKind.DELIVER ? Math.min(o.remaining(), items.count(o.target)) : 0;
	}

	/**
	 * Moves an active quest whose objectives are all done to READY (or reports it complete for auto turn-in). Returns
	 * the new status, or null if nothing changed.
	 */
	public static @Nullable QuestStatus settle(QuestInstance q) {
		if (q.status == QuestStatus.ACTIVE && q.allDone()) {
			if (q.rules.turnIn() == TurnIn.AUTO) {
				return QuestStatus.COMPLETED;
			}
			q.status = QuestStatus.READY;
			return QuestStatus.READY;
		}
		return null;
	}

	/** Whether the quest's deadline has passed at this game time. */
	public static boolean expired(QuestInstance q, long gameTime) {
		return q.status.inProgress() && q.hasDeadline() && gameTime > q.timing.deadlineTick();
	}

	/** Whether a scouting position is within the objective's band from the origin. */
	public static boolean inScoutBand(Objective o, BlockPos pos) {
		if (o.pos == null) {
			return false;
		}
		double d = Math.sqrt(horizontalDistSq(o.pos, pos));
		return d >= o.data.getIntOr(Objective.Keys.MIN_DISTANCE, 0) && d <= o.data.getIntOr(Objective.Keys.MAX_DISTANCE, Integer.MAX_VALUE);
	}

	public static double horizontalDistSq(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
