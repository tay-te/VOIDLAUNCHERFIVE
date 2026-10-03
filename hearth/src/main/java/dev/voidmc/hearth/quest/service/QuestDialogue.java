package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The quest module's part of a conversation with a resident (order 200–299): hand-ins first ("I've brought what you
 * asked for"), messages to pass on, then the resident's own requests — "Need a hand?" — which they explain before the
 * player agrees, then how a quest of theirs is going. The NPC module owns the screen and calls this; the offer a player
 * is considering is remembered per player until they accept, decline or talk to someone else.
 */
public final class QuestDialogue implements DialogueProvider {
	private static final String PREFIX = "quest:";
	private static final Map<UUID, UUID> CONSIDERING = new ConcurrentHashMap<>();

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		ServerLevel level = QuestWorlds.level(player.level().getServer(), settlement);
		if (level == null) {
			return;
		}
		SettlementQuests board = QuestManager.board(level, settlement, false);
		PlayerQuests pq = QuestStore.get(player);

		boolean handIn = false;
		for (QuestInstance q : pq.active) {
			if (QuestManager.canHandInHere(player, q, settlement, resident)) {
				handIn = true;
			}
			for (Objective o : q.currentObjectives()) {
				if (o.kind == ObjectiveKind.TALK && resident.id.equals(o.entity)) {
					out.add(new Option(PREFIX + "talk/" + q.id, Component.translatable("hearth.quest.dialogue.message", q.giver.name()), 205));
				}
			}
		}
		if (handIn) {
			out.add(new Option(PREFIX + "handin", Component.translatable("hearth.quest.dialogue.handin"), 200));
		}

		UUID considering = CONSIDERING.get(player.getUUID());
		QuestInstance pending = considering == null ? null : board.offer(considering);
		if (pending != null && pending.giver.is(resident.id)) {
			out.add(new Option(PREFIX + "accept/" + pending.id, Component.translatable("hearth.quest.dialogue.accept", rewardText(pending)), 210));
			out.add(new Option(PREFIX + "decline/" + pending.id, Component.translatable("hearth.quest.dialogue.decline"), 211));
		} else {
			int i = 0;
			for (QuestInstance offer : board.offers) {
				if (!offer.giver.is(resident.id) || !QuestManager.lock(player, settlement, offer, pq).isEmpty()) {
					continue;
				}
				out.add(new Option(PREFIX + "offer/" + offer.id,
					Component.translatable(i == 0 ? "hearth.quest.dialogue.offer" : "hearth.quest.dialogue.offer_more", offer.text.title()), 220 + i));
				if (++i >= 3) {
					break;
				}
			}
		}
		int j = 0;
		for (QuestInstance q : pq.active) {
			if (q.giver.is(resident.id) && q.status == QuestStatus.ACTIVE) {
				out.add(new Option(PREFIX + "progress/" + q.id, Component.translatable("hearth.quest.dialogue.progress", q.text.title()), 250 + j++));
			}
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		if (!optionId.startsWith(PREFIX)) {
			return false;
		}
		String rest = optionId.substring(PREFIX.length());
		if (rest.equals("handin")) {
			return QuestManager.handIn(player, settlement, resident) > 0;
		}
		int slash = rest.indexOf('/');
		if (slash < 0) {
			return false;
		}
		UUID id;
		try {
			id = UUID.fromString(rest.substring(slash + 1));
		} catch (IllegalArgumentException e) {
			return false;
		}
		ServerLevel level = QuestWorlds.level(player.level().getServer(), settlement);
		SettlementQuests board = level == null ? null : QuestStore.board(settlement);
		switch (rest.substring(0, slash)) {
			case "offer" -> {
				QuestInstance offer = board == null ? null : board.offer(id);
				if (offer == null || !offer.giver.is(resident.id)) {
					return false;
				}
				CONSIDERING.put(player.getUUID(), id);
				QuestManager.say(player, offer, offer.text.offer().isEmpty() ? offer.text.summary() : offer.text.offer());
				for (Objective o : offer.objectives) {
					player.sendSystemMessage(Component.literal("  • " + o.label).withStyle(ChatFormatting.GRAY));
				}
				return true;
			}
			case "accept" -> {
				CONSIDERING.remove(player.getUUID());
				return QuestManager.accept(player, settlement, id);
			}
			case "decline" -> {
				CONSIDERING.remove(player.getUUID());
				return true;
			}
			case "talk" -> {
				return QuestManager.talk(player, settlement, resident, id);
			}
			case "progress" -> {
				QuestInstance q = QuestStore.get(player).get(id);
				if (q == null) {
					return false;
				}
				QuestManager.say(player, q, q.text.progress().isEmpty() ? q.text.summary() : q.text.progress());
				for (Objective o : q.objectives) {
					player.sendSystemMessage(Component.literal((o.done() ? "  ✔ " : "  • ") + o.label + (o.count > 1 ? " (" + o.progress + "/" + o.count + ")" : ""))
						.withStyle(o.done() ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private static Component rewardText(QuestInstance q) {
		return Component.literal(q.reward.text());
	}

	/** Forgets a player's half-considered offer (logout). */
	public static void forget(@Nullable UUID player) {
		if (player != null) {
			CONSIDERING.remove(player);
		}
	}
}
