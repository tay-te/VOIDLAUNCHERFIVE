package dev.voidmc.hearth.standing.speech;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.StandingEngine;
import dev.voidmc.hearth.standing.StandingModule;
import dev.voidmc.hearth.standing.model.Memories;
import dev.voidmc.hearth.standing.model.TownLedger;
import dev.voidmc.hearth.standing.net.StandingNetwork;
import dev.voidmc.hearth.standing.rules.StandingRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The standing module's part of a conversation (order 40–59, small talk): flattery, asking what folk say about you,
 * news from other towns, and making amends with someone who thinks ill of you — each speech check showing its odds.
 * Someone who dislikes you will not gossip with you until you persuade, frighten or pay them. Replies are spoken by the
 * folk (or written in chat without the npc module); the conversation screen's standing line shows the last outcome.
 */
public final class StandingDialogue implements DialogueProvider {
	private static final String PREFIX = "standing:";
	private static final String FLATTER = PREFIX + "flatter";
	private static final String REPUTATION = PREFIX + "reputation";
	private static final String NEWS = PREFIX + "news";
	private static final String AMENDS = PREFIX + "amends";
	private static final String AMENDS_BRIBE = PREFIX + "amends_bribe";
	private static final String ASK = PREFIX + "ask/";
	/** Below this opinion a resident will not tell you what folk say until talked round. */
	private static final int TALKATIVE = -10;
	/** The deed kind for amends made (a small, personal and town-wide kindness). */
	private static final String AMENDS_KIND = "standing:amends";

	/** Per player: the resident who just refused to gossip with them. */
	private static final Map<UUID, UUID> REFUSED = new ConcurrentHashMap<>();
	/** Per player: who they last talked to and how the last check went, for the screen's line. */
	private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();
	/** What the resident just answered this player, for the conversation's {@link #reply} right after {@link #select}. */
	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();

	private record Last(UUID resident, Component result) {
	}

	public static void forget(UUID player) {
		REFUSED.remove(player);
		LAST.remove(player);
		ANSWER.remove(player);
	}

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		StandingEngine engine = StandingModule.engine();
		if (engine == null) {
			return;
		}
		UUID id = player.getUUID();
		int opinion = engine.opinion(settlement, resident, id);
		Last last = LAST.get(id);
		if (last != null && !last.resident.equals(resident.id)) {
			LAST.remove(id);
			REFUSED.remove(id);
			last = null;
		}
		StandingNetwork.sendTalk(player, entityId(resident), resident.fullName(), opinion, last == null ? Component.empty() : last.result);
		if (!StandingModule.config().speech().dialogue() || resident.age == AgeStage.CHILD) {
			return;
		}
		long today = engine.host().day();
		Memories mem = Memories.of(resident);
		if (!mem.has(id, SpeechKind.FLATTER.memoryKind(true), today) && !mem.has(id, SpeechKind.FLATTER.memoryKind(false), today)) {
			SpeechOdds.Odds flatter = SpeechCheck.odds(player, settlement, resident, SpeechKind.FLATTER, 0, 0);
			out.add(new Option(FLATTER, SpeechCheck.label(flatter, Component.translatable("hearth.standing.dialogue.flatter")), 40));
			describe(FLATTER, "flatter", flatter, -1);
		}
		if (resident.id.equals(REFUSED.get(id))) {
			SpeechOdds.Odds persuade = SpeechCheck.odds(player, settlement, resident, SpeechKind.PERSUADE, 0.1, 0);
			out.add(new Option(ASK + "persuade", SpeechCheck.label(persuade, Component.translatable("hearth.standing.dialogue.ask.persuade")), 42));
			describe(ASK + "persuade", "persuade", persuade, -1);
			SpeechOdds.Odds intimidate = SpeechCheck.odds(player, settlement, resident, SpeechKind.INTIMIDATE, 0.1, 0);
			out.add(new Option(ASK + "intimidate", SpeechCheck.label(intimidate, Component.translatable("hearth.standing.dialogue.ask.intimidate")), 43));
			describe(ASK + "intimidate", "intimidate", intimidate, -1);
			bribeOption(player, settlement, resident, ASK + "bribe", "hearth.standing.dialogue.ask.bribe", 0.1, 44, out);
		} else {
			out.add(new Option(REPUTATION, Component.translatable("hearth.standing.dialogue.reputation"), 42));
		}
		if (StandingEngine.ledger(settlement).news().stream().anyMatch(n -> !n.local())) {
			out.add(new Option(NEWS, Component.translatable("hearth.standing.dialogue.news"), 46));
		}
		if (opinion < 0 && !mem.has(id, AMENDS_KIND, today) && !mem.has(id, SpeechKind.PERSUADE.memoryKind(false), today)) {
			SpeechOdds.Odds amends = SpeechCheck.odds(player, settlement, resident, SpeechKind.PERSUADE, 0.15, 0);
			out.add(new Option(AMENDS, SpeechCheck.label(amends, Component.translatable("hearth.standing.dialogue.amends")), 48));
			describe(AMENDS, "persuade", amends, -1);
			bribeOption(player, settlement, resident, AMENDS_BRIBE, "hearth.standing.dialogue.amends_bribe", 0.15, 49, out);
		}
	}

	private static void bribeOption(ServerPlayer player, Settlement s, Resident r, String id, String key, double difficulty, int order, List<Option> out) {
		EconomyService economy = HearthServices.economy();
		if (economy == null) {
			return;
		}
		long bits = SpeechCheck.expectedBribe(r, difficulty);
		if (economy.balance(player) < bits) {
			return;
		}
		SpeechOdds.Odds odds = SpeechCheck.odds(player, s, r, SpeechKind.BRIBE, difficulty, bits);
		out.add(new Option(id, SpeechCheck.label(odds, Component.translatable(key, economy.format(bits))), order));
		describe(id, "bribe", odds, bits);
	}

	/** The option's facts for the richer conversation screens (docs/OVERLAY.md): a speech check reading Speech. */
	private static void describe(String id, String kind, SpeechOdds.Odds odds, long cost) {
		DialogueProvider.describe(id, new DialogueProvider.Info(kind, odds.percent(), cost, dev.voidmc.hearth.api.PlayerSkills.SPEECH));
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		StandingEngine engine = StandingModule.engine();
		if (engine == null || !optionId.startsWith(PREFIX)) {
			return false;
		}
		UUID id = player.getUUID();
		ANSWER.remove(id);
		RandomGenerator random = new java.util.Random(player.getRandom().nextLong());
		switch (optionId) {
			case FLATTER -> {
				SpeechCheck.Outcome o = SpeechCheck.attempt(player, settlement, resident, SpeechKind.FLATTER, 0, 0);
				reply(player, resident, line("flatter." + (o.success() ? "success" : "failure"), random), o);
				return true;
			}
			case REPUTATION -> {
				if (engine.opinion(settlement, resident, id) < TALKATIVE) {
					REFUSED.put(id, resident.id);
					reply(player, resident, line("refuse", random), null);
					return true;
				}
				reply(player, resident, reputationLine(engine, player, settlement, resident, random), null);
				return true;
			}
			case NEWS -> {
				reply(player, resident, newsLine(settlement, id, random), null);
				return true;
			}
			case AMENDS -> {
				SpeechCheck.Outcome o = SpeechCheck.attempt(player, settlement, resident, SpeechKind.PERSUADE, 0.15, 0);
				if (o.success()) {
					amends(engine, player, settlement, resident, 10);
				}
				reply(player, resident, line("amends." + (o.success() ? "success" : "failure"), random), o);
				return true;
			}
			case AMENDS_BRIBE -> {
				return bribe(engine, player, settlement, resident, 0.15, random, true);
			}
			default -> {
				if (!optionId.startsWith(ASK) || !resident.id.equals(REFUSED.get(id))) {
					return false;
				}
				String how = optionId.substring(ASK.length());
				if (how.equals("bribe")) {
					return bribe(engine, player, settlement, resident, 0.1, random, false);
				}
				SpeechKind kind = how.equals("intimidate") ? SpeechKind.INTIMIDATE : SpeechKind.PERSUADE;
				SpeechCheck.Outcome o = SpeechCheck.attempt(player, settlement, resident, kind, 0.1, 0);
				if (!o.retry()) {
					REFUSED.remove(id);
				}
				if (o.success()) {
					reply(player, resident, reputationLine(engine, player, settlement, resident, random), o);
				} else {
					reply(player, resident, line("ask." + how + ".failure", random), o);
				}
				return true;
			}
		}
	}

	/** Offers the expected bribe; on success the coin changes hands and the resident talks (or forgives). */
	private boolean bribe(StandingEngine engine, ServerPlayer player, Settlement settlement, Resident resident, double difficulty, RandomGenerator random,
		boolean amends) {
		EconomyService economy = HearthServices.economy();
		if (economy == null) {
			return false;
		}
		long bits = SpeechCheck.expectedBribe(resident, difficulty);
		if (economy.balance(player) < bits) {
			reply(player, resident, line("cannot_afford", random), null);
			return true;
		}
		SpeechCheck.Outcome o = SpeechCheck.attempt(player, settlement, resident, SpeechKind.BRIBE, difficulty, bits);
		if (!o.retry()) {
			REFUSED.remove(player.getUUID());
		}
		if (!o.success()) {
			reply(player, resident, line("bribe.refused", random), o);
			return true;
		}
		if (!economy.charge(player, bits)) {
			reply(player, resident, line("cannot_afford", random), null);
			return true;
		}
		if (amends) {
			amends(engine, player, settlement, resident, 12);
			reply(player, resident, line("amends.bribed", random), o);
		} else {
			reply(player, resident, reputationLine(engine, player, settlement, resident, random), o);
		}
		return true;
	}

	/** Amends made: the resident remembers it, and the town hears a little of it. */
	private static void amends(StandingEngine engine, ServerPlayer player, Settlement settlement, Resident resident, int memory) {
		engine.remember(resident, player.getUUID(), AMENDS_KIND, memory);
		engine.deed(new dev.voidmc.hearth.api.StandingService.Deed(player.getUUID(), settlement.id(), AMENDS_KIND, 1, null, player.blockPosition(), true));
		settlement.markDirty();
	}

	// ---------------------------------------------------------------- what they say

	private static Component reputationLine(StandingEngine engine, ServerPlayer player, Settlement settlement, Resident resident, RandomGenerator random) {
		UUID id = player.getUUID();
		String name = player.nameAndId().name();
		Title title = engine.title(settlement, id);
		StandingRules rules = StandingRules.get();
		String verdict = rules.keyed("title_" + title.getSerializedName(), name, settlement.name(), random);
		MutableComponent out = verdict != null ? Component.literal(verdict)
			: Component.translatable("hearth.standing.say.verdict", Component.translatable("hearth.standing.title_phrase." + title.getSerializedName(), settlement.name()));
		List<TownLedger.News> news = StandingEngine.ledger(settlement).newsOf(id);
		if (!news.isEmpty()) {
			TownLedger.News n = news.get(random.nextInt(Math.min(3, news.size())));
			String told = rules.line(n.kind(), n.weight(), name, n.town(), random);
			if (told != null) {
				out.append(" ").append(told);
			}
		}
		Memories.Memory own = Memories.of(resident).strongest(id, engine.host().day(), engine.config().opinion());
		if (own != null && Math.abs(own.weight()) >= 10 && !own.kind().startsWith(PREFIX) && !own.kind().startsWith(StandingEngine.HEARSAY)) {
			String mine = rules.keyed(own.weight() > 0 ? "memory_good" : "memory_bad", name, settlement.name(), random);
			if (mine != null) {
				out.append(" ").append(mine);
			}
		}
		return out;
	}

	private static Component newsLine(Settlement settlement, UUID self, RandomGenerator random) {
		StandingRules rules = StandingRules.get();
		for (TownLedger.News n : StandingEngine.ledger(settlement).news()) {
			if (n.local()) {
				continue;
			}
			String who = n.player().equals(self) ? "you" : n.playerName().isEmpty() ? "a stranger" : n.playerName();
			String told = rules.line(n.kind(), n.weight(), who, n.town(), random);
			if (told != null) {
				return Component.translatable("hearth.standing.say.news", n.town()).append(" ").append(told);
			}
		}
		return Component.translatable("hearth.standing.say.no_news");
	}

	private static Component line(String key, RandomGenerator random) {
		return Component.translatable("hearth.standing.say." + key + "." + random.nextInt(3));
	}

	private static int entityId(Resident resident) {
		NpcService npc = HearthServices.npc();
		NpcService.FolkHandle f = npc == null ? null : npc.folk(resident.id);
		return f == null ? -1 : f.entity().getId();
	}

	/** The resident says it (their folk speaks if embodied; otherwise it is written in chat), and the screen's line shows the outcome. */
	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return optionId.startsWith(PREFIX) ? ANSWER.remove(player.getUUID()) : null;
	}

	private static void reply(ServerPlayer player, Resident resident, Component line, SpeechCheck.@Nullable Outcome outcome) {
		ANSWER.put(player.getUUID(), line);
		NpcService npc = HearthServices.npc();
		NpcService.FolkHandle f = npc == null ? null : npc.folk(resident.id);
		if (f != null) {
			f.say(line, player);
		} else {
			player.sendSystemMessage(Component.literal(resident.fullName() + ": ").withStyle(ChatFormatting.GOLD).append(line.copy().withStyle(ChatFormatting.WHITE)));
		}
		if (outcome == null) {
			LAST.put(player.getUUID(), new Last(resident.id, line));
			return;
		}
		MutableComponent header = Component.translatable(outcome.success() ? "hearth.standing.result.success" : "hearth.standing.result.failure",
			Component.translatable(outcome.kind().translationKey()), outcome.percent()).withStyle(outcome.success() ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED);
		LAST.put(player.getUUID(), new Last(resident.id, header.copy().append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
			.append(line.copy().withStyle(ChatFormatting.RESET))));
		player.sendSystemMessage(header);
		if (outcome.retry()) {
			player.sendSystemMessage(Component.translatable("hearth.standing.result.retry").withStyle(ChatFormatting.GOLD));
		}
	}
}
