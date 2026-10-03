package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Composes what folk say from the dialogue templates and the moment: time of day, weather, the player's standing in
 * town, the speaker's traits, mood, trade, age and culture, and the town's news.
 */
public final class Greetings {
	/** Ticks between greetings in passing for one player, across all folk (a busy square should not spam chat). */
	private static final int PASSING_COOLDOWN = 20 * 40;
	private static final Map<UUID, Long> LAST_PASSING = new HashMap<>();

	private Greetings() {
	}

	public static DialogueTemplates.Context context(ServerLevel level, Settlement settlement, Resident resident, @Nullable ServerPlayer player,
		BlockPos at) {
		NpcData data = NpcData.of(resident);
		String weather = level.isThundering() ? "storm" : level.isRainingAt(at.above()) ? "rain" : "clear";
		int rep = player == null ? 0 : settlement.reputation(player.getUUID());
		int met = player == null ? 0 : data.timesMet(player.getUUID());
		return new DialogueTemplates.Context(NpcTime.minuteOfDay(level), weather, rep, data.traits(), resident.job, settlement.culture(), resident.age,
			data.mood(), met);
	}

	/** The common placeholders. */
	public static Map<String, String> values(Settlement settlement, Resident resident, @Nullable ServerPlayer player) {
		Map<String, String> v = new HashMap<>();
		v.put("player", player == null ? "friend" : player.getName().getString());
		v.put("name", resident.fullName());
		v.put("given", resident.givenName);
		v.put("settlement", settlement.name());
		v.put("job", jobWord(resident));
		v.put("culture", settlement.culture().getSerializedName());
		return v;
	}

	public static String jobWord(Resident resident) {
		if (resident.age == AgeStage.CHILD) {
			return "child";
		}
		return switch (resident.job) {
			case NONE -> "neighbour";
			default -> resident.job.getSerializedName().replace('_', ' ');
		};
	}

	/** The opening line of a conversation, with a piece of town news now and then. */
	public static String greeting(ServerLevel level, Settlement settlement, Resident resident, ServerPlayer player, BlockPos at, RandomSource random) {
		DialogueTemplates.Context ctx = context(level, settlement, resident, player, at);
		Map<String, String> v = values(settlement, resident, player);
		String line = DialogueTemplates.pick("greeting", ctx, random);
		String text = DialogueTemplates.fill(line != null ? line : "Hello.", v);
		if (random.nextInt(3) == 0) {
			String news = news(level, settlement, resident, player, at, random, false);
			if (news != null) {
				text = text + " " + news;
			}
		}
		return text;
	}

	/**
	 * Something from the town's news (a building finished, a shortage, the town grown), or null when there is none
	 * (or, with {@code orNothing}, a line saying nothing much is happening).
	 */
	public static @Nullable String news(ServerLevel level, Settlement settlement, Resident resident, @Nullable ServerPlayer player, BlockPos at,
		RandomSource random, boolean orNothing) {
		DialogueTemplates.Context ctx = context(level, settlement, resident, player, at);
		Map<String, String> v = values(settlement, resident, player);
		TownNews news = NpcModule.embodiment().town(settlement).news;
		List<TownNews.Item> recent = news.recent(NpcTime.today(), 3);
		if (!recent.isEmpty()) {
			TownNews.Item item = recent.get(random.nextInt(Math.min(recent.size(), 3)));
			String category = switch (item.kind()) {
				case BUILT -> "news.built";
				case STALLED -> "news.stalled";
				case TIER -> "news.tier";
				case ARRIVED -> "news.arrived";
				case DIED -> "news.died";
				case FOUNDED -> "news.founded";
			};
			v.put("building", item.subject());
			v.put("other", item.subject());
			if (item.goods() != null) {
				v.put("goods", goodsWord(item.goods()));
				v.put("amount", Integer.toString(item.amount()));
			}
			if (item.tier() != null) {
				v.put("tier", item.tier().getSerializedName());
			}
			String line = DialogueTemplates.pick(category, ctx, random);
			if (line != null) {
				return DialogueTemplates.fill(line, v);
			}
		}
		if (!orNothing) {
			return null;
		}
		String line = DialogueTemplates.pick("news.none", ctx, random);
		return line == null ? null : DialogueTemplates.fill(line, v);
	}

	public static String goodsWord(dev.voidmc.hearth.api.Goods goods) {
		return switch (goods) {
			case FOOD -> "food";
			case WOOD -> "timber";
			case STONE -> "stone";
			case CLAY -> "clay and brick";
			case GLASS -> "glass";
			case METAL -> "metal";
			case CLOTH -> "cloth";
			case FUEL -> "coal";
			case TOOLS -> "tools";
			case LUXURY -> "fine goods";
		};
	}

	/** A building's name from its archetype id: {@code hearth:house/tower_house} is "tower house". */
	public static String buildingName(net.minecraft.resources.Identifier archetype) {
		String path = archetype.getPath();
		int slash = path.lastIndexOf('/');
		return (slash >= 0 ? path.substring(slash + 1) : path).replace('_', ' ').toLowerCase(Locale.ROOT);
	}

	/**
	 * A greeting in passing, as a chat line from the folk, for players they know (reputation 25+ or met before).
	 * Rate-limited per player.
	 */
	public static void greetInPassing(ServerLevel level, FolkBrain brain, ServerPlayer player) {
		int rep = brain.settlement.reputation(player.getUUID());
		if (rep < 25 && brain.data.timesMet(player.getUUID()) == 0) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST_PASSING.get(player.getUUID());
		if (last != null && now - last < PASSING_COOLDOWN) {
			return;
		}
		LAST_PASSING.put(player.getUUID(), now);
		if (LAST_PASSING.size() > 512) {
			LAST_PASSING.clear();
		}
		DialogueTemplates.Context ctx = context(level, brain.settlement, brain.resident, player, brain.folk.blockPosition());
		String line = DialogueTemplates.pick("passing", ctx, brain.random);
		if (line == null) {
			return;
		}
		String text = DialogueTemplates.fill(line, values(brain.settlement, brain.resident, player));
		player.sendSystemMessage(Component.literal(brain.resident.givenName).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(": " + text).withStyle(ChatFormatting.GRAY)));
	}
}
