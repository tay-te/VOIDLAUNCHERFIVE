package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.QuestConfig;
import dev.voidmc.hearth.quest.QuestEvents;
import dev.voidmc.hearth.quest.QuestProgress;
import dev.voidmc.hearth.quest.gen.GenerationContext;
import dev.voidmc.hearth.quest.gen.GoodsItems;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.gen.RewardText;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.QuestTemplates;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import dev.voidmc.hearth.quest.world.StructureLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The quest module's server logic: boards, accepting, handing in, completing, failing and abandoning. Everything a
 * player does with quests comes through here, from the notice board, the conversation, the journal or a command, and
 * every check is made here on the server.
 */
public final class QuestManager {
	/** Board entries a player should not see at all (prerequisites unmet, done and not repeatable). */
	public static final String HIDDEN = "?hidden";
	private static QuestConfig config = QuestConfig.DEFAULTS;

	private QuestManager() {
	}

	public static void setConfig(QuestConfig c) {
		config = c;
	}

	public static QuestConfig config() {
		return config;
	}

	// ------------------------------------------------------------------ boards

	/** The settlement's board, refreshed if a day has turned (or {@code force}). */
	public static SettlementQuests board(ServerLevel level, Settlement s, boolean force) {
		SettlementQuests data = QuestStore.board(s);
		long refreshedBefore = data.refreshedDay;
		int added = QuestGenerator.refresh(generationContext(level, s, data), QuestTemplates.all(), force);
		if (added > 0 || refreshedBefore != data.refreshedDay) {
			s.markDirty();
		}
		return data;
	}

	/** Posts supply quests for a construction that just stalled (called from the sim's event). */
	public static void onStalled(ServerLevel level, Settlement s, SettlementQuests.StalledNeed need) {
		SettlementQuests data = QuestStore.board(s);
		data.addNeed(need);
		QuestGenerator.postNeeds(generationContext(level, s, data), QuestTemplates.all());
		s.markDirty();
	}

	/** The generator's view of a settlement now (for the board and the dev commands). */
	public static GenerationContext generationContext(ServerLevel level, Settlement s, SettlementQuests data) {
		long day = QuestWorlds.day(level);
		// Seeded per settlement, day and issue count: the same board for everyone who looks today.
		RandomSource random = RandomSource.create(s.id() * 0x9E3779B97F4A7C15L ^ day * 0xC2B2AE3D27D4EB4FL ^ data.issued * 31L);
		List<Settlement> others = new ArrayList<>(QuestWorlds.all(level));
		others.removeIf(o -> o.id() == s.id());
		MinecraftServer server = level.getServer();
		return new GenerationContext(s, data, day, random, others, GameFactsImpl.INSTANCE, config,
			ids -> StructureLocator.request(level, s, ids, config.exploreSearchRadius(), day, id -> topUp(server, id)));
	}

	/** Fills empty board slots after a landmark search found something. */
	static void topUp(MinecraftServer server, long settlementId) {
		Settlement s = QuestWorlds.byId(server, settlementId);
		ServerLevel level = s == null ? null : QuestWorlds.level(server, s);
		if (level != null) {
			board(level, s, true);
		}
	}

	/** Why this player cannot take an offer: empty if they can, {@link #HIDDEN} if they should not see it. */
	public static String lock(ServerPlayer player, Settlement s, QuestInstance offer, PlayerQuests pq) {
		if (pq.get(offer.id) != null || offer.takenBy.contains(player.getUUID())) {
			return HIDDEN;
		}
		QuestTemplate t = QuestTemplates.get(offer.template);
		if (t != null) {
			for (Identifier pre : t.when().prerequisites()) {
				if (pq.completed(pre) == 0) {
					return HIDDEN;
				}
			}
			if (!t.repeatable() && pq.completed(t.id()) > 0) {
				return HIDDEN;
			}
		}
		int rep = s.reputation(player.getUUID());
		if (rep < offer.rules.minReputation()) {
			return Component.translatable("hearth.quest.lock.reputation", offer.rules.minReputation()).getString();
		}
		if (pq.active.size() >= config.maxActive()) {
			return Component.translatable("hearth.quest.lock.full", config.maxActive()).getString();
		}
		return "";
	}

	/** The board as this player sees it, ready to send. */
	public static QuestPayloads.Board view(ServerPlayer player, ServerLevel level, Settlement s, BlockPos boardPos) {
		SettlementQuests data = board(level, s, false);
		PlayerQuests pq = QuestStore.get(player);
		List<QuestPayloads.Board.Entry> entries = new ArrayList<>();
		for (QuestInstance offer : data.offers) {
			String lock = lock(player, s, offer, pq);
			if (!lock.equals(HIDDEN)) {
				entries.add(new QuestPayloads.Board.Entry(offer.copy(), lock));
			}
		}
		List<UUID> handIns = new ArrayList<>();
		for (QuestInstance q : pq.active) {
			if (canHandInHere(player, q, s, null)) {
				handIns.add(q.id);
			}
		}
		return new QuestPayloads.Board(s.id(), boardPos, s.name(), s.tier().getSerializedName(), s.reputation(player.getUUID()), entries, handIns);
	}

	// ------------------------------------------------------------------ accepting

	/** Takes an offer from the settlement's board. Returns whether it was taken. */
	public static boolean accept(ServerPlayer player, Settlement s, UUID offerId) {
		ServerLevel level = QuestWorlds.level(player.level().getServer(), s);
		if (level == null) {
			return false;
		}
		SettlementQuests data = QuestStore.board(s);
		QuestInstance offer = data.offer(offerId);
		if (offer == null) {
			player.sendSystemMessage(Component.translatable("hearth.quest.gone").withStyle(ChatFormatting.GRAY));
			return false;
		}
		PlayerQuests current = QuestStore.get(player);
		String lock = lock(player, s, offer, current);
		if (!lock.isEmpty()) {
			player.sendSystemMessage(Component.literal(lock.equals(HIDDEN) ? Component.translatable("hearth.quest.gone").getString() : lock)
				.withStyle(ChatFormatting.RED));
			return false;
		}
		QuestInstance q = offer.copy();
		q.status = QuestStatus.ACTIVE;
		q.takenBy.clear();
		q.timing = offer.timing.accepted(gameTime(player), QuestWorlds.TICKS_PER_DAY);
		q.escrow = Rewards.escrow(s, offer.reward.coin());
		if (q.escrow < offer.reward.coin()) {
			q.reward = RewardText.withText(q.reward.withCoin(q.escrow), GameFactsImpl.INSTANCE);
		}
		offer.takenBy.add(player.getUUID());
		if (offer.rules.exclusive()) {
			data.offers.remove(offer);
		}
		s.markDirty();

		for (Objective o : q.objectives) {
			if (o.kind == ObjectiveKind.COURIER) {
				String to = o.data.getStringOr(Objective.Keys.RECIPIENT, o.data.getStringOr(Objective.Keys.TARGET_NAME, "?"));
				QuestItems.give(player, QuestItems.make(Identifier.withDefaultNamespace("paper"),
					Component.translatable("hearth.quest.parcel.name", to).getString(),
					Component.translatable("hearth.quest.parcel.lore", s.name(), o.data.getStringOr(Objective.Keys.TARGET_NAME, "?")).getString(),
					q.id, QuestItems.ROLE_PARCEL));
			} else if (o.kind == ObjectiveKind.ESCORT) {
				Entity companion = QuestSpawns.spawnCompanion(player, q, o);
				if (companion == null) {
					Rewards.refund(s, q);
					offer.takenBy.remove(player.getUUID());
					if (offer.rules.exclusive()) {
						data.offers.add(offer);
					}
					player.sendSystemMessage(Component.translatable("hearth.quest.escort.nospace").withStyle(ChatFormatting.RED));
					return false;
				}
			}
		}
		boolean giveJournal = QuestStore.editAndGet(player, pq -> {
			pq.active.add(q);
			if (pq.tracked == null) {
				pq.tracked = q.id;
			}
			boolean first = !pq.journalGiven;
			pq.journalGiven = true;
			return first;
		});
		if (giveJournal && config.giveJournal()) {
			QuestItems.give(player, new net.minecraft.world.item.ItemStack(dev.voidmc.hearth.quest.QuestRegistry.JOURNAL));
			player.sendSystemMessage(Component.translatable("hearth.quest.journal.given").withStyle(ChatFormatting.GRAY));
		}
		say(player, q, q.text.offer());
		player.sendSystemMessage(Component.translatable("hearth.quest.accepted", Component.literal(q.text.title()).withStyle(ChatFormatting.YELLOW))
			.withStyle(ChatFormatting.GOLD));
		sound(player, SoundEvents.BOOK_PAGE_TURN, 1.0F);
		toast(player, QuestPayloads.Toast.Kind.ACCEPTED, q.text.title(), q.settlementName);
		return true;
	}

	// ------------------------------------------------------------------ handing in

	/** Whether the player could hand in or deliver something of this quest at this settlement (to this resident). */
	public static boolean canHandInHere(ServerPlayer player, QuestInstance q, Settlement s, @Nullable Resident resident) {
		if (!q.status.inProgress()) {
			return false;
		}
		for (Objective o : q.currentObjectives()) {
			if (o.settlement == s.id() && (o.kind == ObjectiveKind.COURIER || o.kind == ObjectiveKind.TALK)) {
				boolean right = o.entity == null || (resident != null && o.entity.equals(resident.id)) || (resident == null && o.kind == ObjectiveKind.COURIER);
				if (right && (o.kind == ObjectiveKind.TALK || QuestItems.count(player, q.id, QuestItems.ROLE_PARCEL) > 0)) {
					return true;
				}
			}
		}
		if (q.settlementId != s.id() || (resident != null && q.giver.resident().isPresent() && !q.giver.is(resident.id))) {
			return false;
		}
		if (q.status == QuestStatus.READY) {
			return true;
		}
		QuestProgress.ItemSource inv = QuestItems.inventory(player);
		for (Objective o : q.currentObjectives()) {
			if (QuestProgress.carried(o, inv) > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Hands over everything the player can at this settlement — to the board ({@code resident} null) or to a resident:
	 * parcels and messages for here, goods for quests from here, finished quests to turn in. Returns how many quests
	 * moved on.
	 */
	public static int handIn(ServerPlayer player, Settlement s, @Nullable Resident resident) {
		PlayerQuests pq = QuestStore.get(player).copy();
		List<QuestInstance> toComplete = new ArrayList<>();
		int moved = 0;
		for (QuestInstance q : List.copyOf(pq.active)) {
			if (!q.status.inProgress()) {
				continue;
			}
			boolean changed = false;
			for (Objective o : q.currentObjectives()) {
				if (o.settlement != s.id() || o.done()) {
					continue;
				}
				if (o.kind == ObjectiveKind.COURIER && (o.entity == null || resident == null || o.entity.equals(resident.id))
					&& QuestItems.takeAll(player, q.id, QuestItems.ROLE_PARCEL) > 0) {
					o.complete();
					changed = true;
					player.sendSystemMessage(Component.translatable("hearth.quest.courier.delivered",
						o.data.getStringOr(Objective.Keys.RECIPIENT, s.name())).withStyle(ChatFormatting.GREEN));
				} else if (o.kind == ObjectiveKind.TALK && resident != null && resident.id.equals(o.entity)) {
					o.complete();
					changed = true;
				}
			}
			boolean fromHere = q.settlementId == s.id() && (resident == null || q.giver.resident().isEmpty() || q.giver.is(resident.id));
			if (fromHere && q.status == QuestStatus.ACTIVE) {
				List<QuestProgress.Delivered> delivered = QuestProgress.handIn(q, QuestItems.inventory(player));
				for (QuestProgress.Delivered d : delivered) {
					changed = true;
					stock(s, d);
					player.sendSystemMessage(Component.translatable("hearth.quest.delivered", d.amount(),
						d.objective().data.getStringOr(Objective.Keys.TARGET_NAME, d.objective().target), d.objective().progress, d.objective().count)
						.withStyle(ChatFormatting.GREEN));
				}
			}
			if (changed) {
				moved++;
				QuestStatus settled = QuestProgress.settle(q);
				if (settled == QuestStatus.COMPLETED) {
					toComplete.add(q);
				} else if (settled == QuestStatus.READY && !fromHere) {
					readyNotice(player, q);
				}
			}
			if (fromHere && q.status == QuestStatus.READY && !toComplete.contains(q)) {
				if (takeRequiredGrants(player, q)) {
					toComplete.add(q);
					if (!changed) {
						moved++;
					}
				}
			}
		}
		for (QuestInstance q : toComplete) {
			complete(player, pq, q);
		}
		player.setAttached(QuestStore.PLAYER, pq);
		return moved;
	}

	/** Goods handed in for a build go into the settlement's stores, so the build can go on. */
	private static void stock(Settlement s, QuestProgress.Delivered d) {
		Objective o = d.objective();
		int units = o.data.getIntOr(Objective.Keys.UNITS, 0);
		if (units <= 0) {
			return;
		}
		Goods goods = null;
		String name = o.data.getStringOr(Objective.Keys.GOODS, "");
		for (Goods g : Goods.VALUES) {
			if (g.getSerializedName().equals(name)) {
				goods = g;
			}
		}
		if (goods == null) {
			GoodsItems.Choice ch = GoodsItems.find(Identifier.parse(o.target));
			goods = ch == null ? null : GoodsItems.goodsOf(ch.item());
		}
		if (goods == null) {
			return;
		}
		int total = units * d.amount();
		s.stockpile().add(goods, total);
		int plot = o.data.getIntOr(Objective.Keys.PLOT, -1);
		if (plot >= 0) {
			QuestStore.board(s).satisfy(plot, goods, total);
		}
		s.markDirty();
	}

	/** Takes the items a quest needs brought back (a found keepsake); false with a message if one is missing. */
	private static boolean takeRequiredGrants(ServerPlayer player, QuestInstance q) {
		boolean required = false;
		String name = "";
		for (Objective o : q.objectives) {
			if (o.data.getBooleanOr(Objective.Keys.GRANT_REQUIRED, false)) {
				required = true;
				name = o.data.getStringOr(Objective.Keys.GRANT_NAME, name);
			}
		}
		if (!required) {
			return true;
		}
		if (QuestItems.count(player, q.id, QuestItems.ROLE_GRANT) == 0) {
			player.sendSystemMessage(Component.translatable("hearth.quest.bring", name).withStyle(ChatFormatting.RED));
			return false;
		}
		QuestItems.takeAll(player, q.id, QuestItems.ROLE_GRANT);
		return true;
	}

	/** Completes a TALK objective for this resident (the conversation's "I have a message" option). */
	public static boolean talk(ServerPlayer player, Settlement s, Resident resident, UUID questId) {
		return handIn(player, s, resident) > 0;
	}

	// ------------------------------------------------------------------ finishing

	/** Pays and closes a quest. {@code pq} is the caller's working copy (attached by the caller). */
	static void complete(ServerPlayer player, PlayerQuests pq, QuestInstance q) {
		MinecraftServer server = player.level().getServer();
		Settlement s = QuestWorlds.byId(server, q.settlementId);
		Component paid = Rewards.pay(player, s, q, config);
		QuestItems.takeAll(player, q.id, QuestItems.ROLE_PARCEL);
		pq.finish(q, QuestStatus.COMPLETED, QuestWorlds.day(player.level()));
		say(player, q, q.text.complete());
		MutableComponent msg = Component.translatable("hearth.quest.completed", Component.literal(q.text.title()).withStyle(ChatFormatting.YELLOW))
			.withStyle(ChatFormatting.GOLD);
		player.sendSystemMessage(msg);
		if (!paid.getString().isEmpty()) {
			player.sendSystemMessage(Component.translatable("hearth.quest.reward", paid).withStyle(ChatFormatting.GRAY));
		}
		if (q.reward.reputation() != 0 && s != null) {
			player.sendSystemMessage(Component.translatable("hearth.quest.reputation.up", q.reward.reputation(), s.name(), s.reputation(player.getUUID()))
				.withStyle(ChatFormatting.DARK_GREEN));
		}
		sound(player, SoundEvents.PLAYER_LEVELUP, 1.2F);
		toast(player, QuestPayloads.Toast.Kind.COMPLETED, q.text.title(), paid.getString());
		ServerLevel level = s == null ? null : QuestWorlds.level(server, s);
		if (level != null) {
			for (Objective o : q.objectives) {
				if (o.kind == ObjectiveKind.SCOUT && o.data.contains(Objective.Keys.FOUND)) {
					QuestEvents.SITE_SCOUTED.invoker().onSiteScouted(level, s, BlockPos.of(o.data.getLongOr(Objective.Keys.FOUND, 0L)), player);
				}
			}
		}
		QuestEvents.COMPLETED.invoker().onCompleted(player, s, q);
	}

	/** Completes an active quest from outside a hand-in (auto turn-in, commands). */
	public static void completeNow(ServerPlayer player, UUID questId) {
		PlayerQuests pq = QuestStore.get(player).copy();
		QuestInstance q = pq.get(questId);
		if (q == null) {
			return;
		}
		for (Objective o : q.objectives) {
			o.complete();
		}
		complete(player, pq, q);
		player.setAttached(QuestStore.PLAYER, pq);
	}

	/** Fails or abandons a quest: escrow back to the treasury, standing down, quest items and companions taken back. */
	static void end(ServerPlayer player, PlayerQuests pq, QuestInstance q, QuestStatus outcome, Component reason) {
		MinecraftServer server = player.level().getServer();
		Settlement s = QuestWorlds.byId(server, q.settlementId);
		Rewards.refund(s, q);
		int delta = outcome == QuestStatus.ABANDONED ? config.reputationAbandon() : config.reputationFail();
		if (s != null && delta != 0) {
			s.addReputation(player.getUUID(), delta);
			s.markDirty();
		}
		QuestItems.takeAll(player, q.id, QuestItems.ROLE_PARCEL);
		QuestItems.takeAll(player, q.id, QuestItems.ROLE_GRANT);
		QuestSpawns.release(server, q);
		pq.finish(q, outcome, QuestWorlds.day(player.level()));
		player.sendSystemMessage(Component.translatable(outcome == QuestStatus.ABANDONED ? "hearth.quest.abandoned" : "hearth.quest.failed",
			Component.literal(q.text.title()).withStyle(ChatFormatting.YELLOW), reason).withStyle(ChatFormatting.RED));
		if (s != null && delta != 0) {
			player.sendSystemMessage(Component.translatable("hearth.quest.reputation.down", -delta, s.name()).withStyle(ChatFormatting.DARK_RED));
		}
		sound(player, SoundEvents.VILLAGER_NO, 1.0F);
		toast(player, QuestPayloads.Toast.Kind.FAILED, q.text.title(), reason.getString());
	}

	public static boolean abandon(ServerPlayer player, UUID questId) {
		PlayerQuests pq = QuestStore.get(player).copy();
		QuestInstance q = pq.get(questId);
		if (q == null) {
			return false;
		}
		end(player, pq, q, QuestStatus.ABANDONED, Component.translatable("hearth.quest.reason.abandoned"));
		player.setAttached(QuestStore.PLAYER, pq);
		return true;
	}

	public static void fail(ServerPlayer player, UUID questId, Component reason) {
		PlayerQuests pq = QuestStore.get(player).copy();
		QuestInstance q = pq.get(questId);
		if (q != null) {
			end(player, pq, q, QuestStatus.FAILED, reason);
			player.setAttached(QuestStore.PLAYER, pq);
		}
	}

	public static void track(ServerPlayer player, UUID questId) {
		QuestStore.edit(player, pq -> {
			if (pq.get(questId) != null) {
				pq.tracked = questId.equals(pq.tracked) ? null : questId;
			}
		});
	}

	// ------------------------------------------------------------------ messages

	/** Tells the player a quest is done and where to hand it in. */
	static void readyNotice(ServerPlayer player, QuestInstance q) {
		player.sendSystemMessage(Component.translatable("hearth.quest.ready", Component.literal(q.text.title()).withStyle(ChatFormatting.YELLOW),
			q.giver.name(), q.settlementName).withStyle(ChatFormatting.GOLD));
		sound(player, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F);
		toast(player, QuestPayloads.Toast.Kind.READY, q.text.title(), Component.translatable("hearth.quest.toast.return", q.giver.name()).getString());
	}

	/** A line spoken by the giver, in chat: "Mara Fenwick: ..." */
	static void say(ServerPlayer player, QuestInstance q, String line) {
		if (line.isEmpty()) {
			return;
		}
		player.sendSystemMessage(Component.literal(capitalized(q.giver.name()) + ": ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
	}

	static String capitalized(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
		player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.7F, pitch);
	}

	static void toast(ServerPlayer player, QuestPayloads.Toast.Kind kind, String title, String detail) {
		if (ServerPlayNetworking.canSend(player, QuestPayloads.Toast.TYPE)) {
			ServerPlayNetworking.send(player, new QuestPayloads.Toast(kind, truncate(title, 256), truncate(detail, 512)));
		}
	}

	private static String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}

	static long gameTime(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}
}
