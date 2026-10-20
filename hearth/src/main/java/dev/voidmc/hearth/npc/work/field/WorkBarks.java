package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Work said aloud (docs/LIVING_WORLD_2.md §0.2, §6.7): a herder hupping at the flock, the burner fretting over the clamp,
 * miners on the track, a fisher at the oars, a stallholder calling the real wares. Lines come from
 * {@code hearth_dialogue/barks/work.json} (categories {@code bark.work.<kind>}, the dialogue templates' conditions), said
 * to players within 16 blocks, with the shared cooldowns: one line a folk a minute, one a player a town every 4 s, the
 * same line not again for 10 minutes. Server thread.
 */
public final class WorkBarks {
	static final int RANGE = 16;
	static final long PER_FOLK = 20 * 60;
	static final long PER_PLAYER_TOWN = 20 * 4;
	static final long PER_LINE = 20 * 60 * 10;

	private static final Map<UUID, Long> FOLK = new HashMap<>();
	private static final Map<String, Long> PLAYER_TOWN = new HashMap<>();
	private static final Map<String, Long> LINE = new HashMap<>();

	private WorkBarks() {
	}

	/**
	 * Perhaps says a line of {@code bark.work.<kind>} (a chance of one in {@code oneIn} each call, then the cooldowns).
	 * {@code values} fills the line's placeholders ({goods}, {river}, {settlement}...). Returns whether a line was said.
	 */
	public static boolean maybe(FolkBrain brain, ServerLevel level, String kind, int oneIn, @Nullable Map<String, String> values) {
		if (oneIn > 1 && brain.random.nextInt(oneIn) != 0) {
			return false;
		}
		long now = level.getGameTime();
		Long last = FOLK.get(brain.resident.id);
		if (last != null && now - last < PER_FOLK && now >= last) {
			return false;
		}
		ServerPlayer hearer = null;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(brain.folk) <= RANGE * RANGE) {
				Long heard = PLAYER_TOWN.get(p.getUUID() + "|" + brain.settlement.id());
				if (heard == null || now - heard >= PER_PLAYER_TOWN || now < heard) {
					hearer = p;
					break;
				}
			}
		}
		if (hearer == null) {
			return false;
		}
		String category = "bark.work." + kind;
		DialogueTemplates.Context ctx = new DialogueTemplates.Context(NpcTime.minuteOfDay(level), weather(level, brain), 0, brain.traits, brain.resident.job,
			brain.settlement.culture(), brain.resident.age, 0, 0);
		String line = null;
		for (int tries = 0; tries < 3 && line == null; tries++) {
			String pick = DialogueTemplates.pick(category, ctx, brain.random);
			if (pick == null) {
				return false;
			}
			Long said = LINE.get(category + "|" + brain.settlement.id() + "|" + pick);
			if (said == null || now - said >= PER_LINE || now < said) {
				line = pick;
			}
		}
		if (line == null) {
			return false;
		}
		Map<String, String> fill = new HashMap<>();
		fill.put("name", brain.resident.fullName());
		fill.put("given", brain.resident.givenName);
		fill.put("settlement", brain.settlement.name());
		if (values != null) {
			fill.putAll(values);
		}
		String text = DialogueTemplates.fill(line, fill);
		if (!text.isEmpty() && Character.isLowerCase(text.charAt(0))) {
			text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
		}
		if (text.contains("{")) {
			// a line whose placeholders this place cannot fill (no river, no wares named): not said
			return false;
		}
		Component msg = Component.translatable("hearth.npc.say", Component.literal(brain.resident.fullName()).withStyle(ChatFormatting.GOLD),
			Component.literal(text));
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(brain.folk) <= RANGE * RANGE) {
				String key = p.getUUID() + "|" + brain.settlement.id();
				Long heard = PLAYER_TOWN.get(key);
				if (heard == null || now - heard >= PER_PLAYER_TOWN || now < heard) {
					p.sendSystemMessage(msg);
					PLAYER_TOWN.put(key, now);
				}
			}
		}
		FOLK.put(brain.resident.id, now);
		LINE.put(category + "|" + brain.settlement.id() + "|" + line, now);
		if (LINE.size() > 4096 || FOLK.size() > 4096 || PLAYER_TOWN.size() > 4096) {
			clear();
		}
		return true;
	}

	private static String weather(ServerLevel level, FolkBrain brain) {
		if (level.isThundering()) {
			return "storm";
		}
		return level.isRainingAt(brain.folk.blockPosition().above()) ? "rain" : "clear";
	}

	public static void clear() {
		FOLK.clear();
		PLAYER_TOWN.clear();
		LINE.clear();
	}
}
