package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.quest.QuestEvents;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.model.SettlementQuests.Happening;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.QuestTemplates;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.Nullable;

/**
 * Quests for the living world (docs/LIVING_WORLD.md §4–§7): turns what happens to towns into happenings on their boards —
 * a fire through a timber street, a mine collapse, a caravan robbed on the road, a quarry spent, a town in decline — and
 * what players do about them back into the world: a rescued miner's gratitude, recovered cargo in the stores, a new site
 * for the sim to work, a name cleared with the law.
 *
 * <p>Everything here is event-driven: the sim's and the repair module's events (cheap, bounded per settlement), the
 * quest lifecycle, and the culprit's damage event. Urgent happenings (fire, collapse) are called out to players in the
 * town with a link to answer them.
 */
public final class LivingQuests {
	/** Fire damage a plot has taken today, below the brigade threshold: settlement id → plot id → [day, blocks]. */
	private static final Map<Long, Map<Integer, long[]>> SMOULDERING = new HashMap<>();

	private LivingQuests() {
	}

	/** Hooks the phase 3 events. Called from the module's init. */
	public static void register() {
		SettlementEvents.DAMAGED.register(LivingQuests::onDamaged);
		SettlementEvents.SITE_DEPLETED.register(LivingQuests::onSiteDepleted);
		SettlementEvents.SITE_FOUND.register(LivingQuests::onSiteFound);
		SettlementEvents.PROSPERITY_CHANGED.register(LivingQuests::onProsperity);
		SettlementEvents.CARAVAN.register(LivingQuests::onCaravan);
		SettlementEvents.CRIME_COMMITTED.register(LivingQuests::onCrime);
		SettlementEvents.DAY_PASSED.register(LivingQuests::onDay);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(QuestTracker::onDamage);
		// a culprit or a robbery's drover is no merchant: no trading with them
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
			entity.entityTags().contains(QuestSpawns.ROLE_CULPRIT) || (entity.entityTags().contains(QuestSpawns.ROLE_AMBUSH)
				&& entity instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Forgets transient state (server stop). */
	public static void clear() {
		SMOULDERING.clear();
	}

	// ------------------------------------------------------------------ happenings

	/**
	 * Records a happening on a settlement's board and answers it at once (an offer, if a template fits); urgent ones are
	 * called out to the players in the town. Returns the offer made, or null.
	 */
	public static @Nullable QuestInstance happen(ServerLevel level, Settlement s, String kind, int plot, BlockPos pos, long other, int amount,
		String goods, String detail, boolean urgent) {
		SettlementQuests data = QuestStore.board(s);
		Happening h = data.addHappening(kind, QuestWorlds.day(level), plot, pos, other, amount, goods, detail);
		QuestGenerator.postEvents(QuestManager.generationContext(level, s, data), QuestTemplates.all());
		s.markDirty();
		QuestInstance offer = null;
		for (QuestInstance q : data.offers) {
			if (q.takenBy.isEmpty() && SettlementQuests.happeningOf(q) == h.id()) {
				offer = q;
			}
		}
		if (offer != null && urgent && QuestManager.config().living().urgentBroadcast()) {
			broadcast(level, s, offer);
		}
		return offer;
	}

	static void onDamaged(HearthWorld world, Settlement s, PlotView plot, SettlementEvents.Damaged.Cause cause, int blocks, @Nullable UUID player) {
		ServerLevel level = world.level();
		switch (cause) {
			case FIRE -> {
				if (!s.embodied()) {
					return;
				}
				long day = world.day();
				long[] seen = SMOULDERING.computeIfAbsent(s.id(), k -> new HashMap<>()).computeIfAbsent(plot.id(), k -> new long[]{day, 0});
				if (seen[0] != day) {
					seen[0] = day;
					seen[1] = 0;
				}
				seen[1] += blocks;
				if (seen[1] < QuestManager.config().living().fireMinBlocks()) {
					return;
				}
				SMOULDERING.get(s.id()).remove(plot.id());
				happen(level, s, Happening.FIRE, plot.id(), middle(plot), -1, (int) seen[1], "", "", true);
			}
			case EXPLOSION -> {
				String path = plot.spec().archetype().getPath();
				if (isMine(path)) {
					collapse(level, s, plot);
				} else {
					happen(level, s, Happening.BLAST, plot.id(), middle(plot), -1, blocks, "", "", false);
				}
			}
			default -> {
			}
		}
	}

	static void onSiteDepleted(HearthWorld world, Settlement s, ResourceSite site) {
		BlockPos c = site.center();
		happen(world.level(), s, Happening.DEPLETED, -1, new BlockPos(c.getX(), Objective.SURFACE_Y, c.getZ()), -1, site.capacity(),
			mainGoods(site.yields()), site.kind().getSerializedName(), false);
	}

	/** A new site of a kind the town had lost: its spent-site happenings are answered. */
	static void onSiteFound(HearthWorld world, Settlement s, ResourceSite site) {
		SettlementQuests data = QuestStore.board(s);
		boolean changed = false;
		for (Happening h : List.copyOf(data.happenings)) {
			if (!h.resolved() && h.kind().equals(Happening.DEPLETED) && h.detail().equals(site.kind().getSerializedName())) {
				changed |= data.resolve(h.id());
			}
		}
		if (changed) {
			s.markDirty();
		}
	}

	static void onProsperity(HearthWorld world, Settlement s, boolean rising, String why) {
		SettlementQuests data = QuestStore.board(s);
		if (rising) {
			data.decliningSince = Long.MIN_VALUE;
			for (Happening h : List.copyOf(data.happenings)) {
				if (!h.resolved() && h.kind().equals(Happening.DECLINING)) {
					data.resolve(h.id());
				}
			}
			s.markDirty();
			return;
		}
		if (!data.declining()) {
			data.decliningSince = world.day();
		}
		happen(world.level(), s, Happening.DECLINING, -1, s.center(), -1, 1, "", why, false);
	}

	static void onCaravan(HearthWorld world, Settlement from, Settlement to, SettlementEvents.CaravanEvent.Kind kind, long caravanId, int[] cargo) {
		ServerLevel level = world.level();
		String main = mainGoods(cargo);
		String text = cargoText(cargo);
		int total = 0;
		for (int n : cargo) {
			total += Math.max(0, n);
		}
		switch (kind) {
			case DEPARTED -> {
				// an escort is worth offering only where someone could take it: the board of a town a player is in
				if (from.embodied()) {
					// the caravan's id rides in the happening's plot field: an escort taken protects that very caravan
					happen(level, from, Happening.CARAVAN_DEPARTED, (int) caravanId, from.center(), to.id(), total, main, text, false);
				}
			}
			case ARRIVED -> {
				SettlementQuests data = QuestStore.board(from);
				for (Happening h : List.copyOf(data.happenings)) {
					if (!h.resolved() && h.kind().equals(Happening.CARAVAN_DEPARTED) && h.other() == to.id() && (h.plot() <= 0 || h.plot() == (int) caravanId)) {
						data.resolve(h.id());
						from.markDirty();
					}
				}
			}
			case ROBBED, LOST -> {
				String k = kind == SettlementEvents.CaravanEvent.Kind.ROBBED ? Happening.CARAVAN_ROBBED : Happening.CARAVAN_LOST;
				BlockPos mid = new BlockPos((from.center().getX() + to.center().getX()) / 2, Objective.SURFACE_Y,
					(from.center().getZ() + to.center().getZ()) / 2);
				happen(level, from, k, -1, mid, to.id(), total, main, text, false);
				happen(level, to, k, -1, mid, from.id(), total, main, text, false);
			}
		}
	}

	static void onCrime(HearthWorld world, Settlement s, LawService.Crime crime) {
		SettlementQuests data = QuestStore.board(s);
		data.crime = Math.min(100, data.crime + switch (crime.offence()) {
			case MURDER -> 4;
			case ASSAULT, ROBBERY, JAILBREAK, RESISTING_ARREST -> 2;
			default -> 1;
		});
		s.markDirty();
	}

	/** While a player is near a town with a worked mine, the mine may collapse (a small chance a day, from the config). */
	static void onDay(HearthWorld world, Settlement s, long day) {
		double chance = QuestManager.config().living().collapseChance();
		if (!s.embodied() || chance <= 0) {
			return;
		}
		RandomSource random = RandomSource.create(s.id() * 0x9E3779B97F4A7C15L ^ day * 0x2545F4914F6CDD1DL);
		if (random.nextDouble() >= chance) {
			return;
		}
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.PLANNED && isMine(p.spec().archetype().getPath())) {
				collapse(world.level(), s, p);
				return;
			}
		}
	}

	/** A mine fell in: the miners at work are trapped, and the town calls for help. */
	static void collapse(ServerLevel level, Settlement s, PlotView plot) {
		List<String> names = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.CHILD && (r.job == JobType.MINER || r.job == JobType.QUARRIER) && names.size() < 3) {
				names.add(r.fullName());
			}
		}
		if (names.isEmpty()) {
			// nobody was down there: the props are replaced, and the repair module mends the rest
			return;
		}
		happen(level, s, Happening.COLLAPSE, plot.id(), QuestGenerator.plotPlace(s, plot), -1, names.size(), Goods.ORE.getSerializedName(),
			joinNames(names), true);
	}

	private static boolean isMine(String archetypePath) {
		return archetypePath.startsWith("extract/mine") || archetypePath.startsWith("extract/shaft") || archetypePath.startsWith("deep/lift");
	}

	// ------------------------------------------------------------------ outcomes

	/** What a completed quest does to the world beyond its reward. */
	static void completed(ServerLevel level, @Nullable Settlement s, ServerPlayer player, QuestInstance q) {
		if (s == null) {
			return;
		}
		SettlementQuests data = QuestStore.board(s);
		int hid = q.extra.getIntOr(QuestInstance.Extra.HAPPENING, 0);
		Happening h = hid == 0 ? null : data.happening(hid);
		if (h != null && !h.resolved()) {
			data.resolve(hid);
			s.markDirty();
			if ((h.kind().equals(Happening.CARAVAN_ROBBED) || h.kind().equals(Happening.CARAVAN_LOST)) && broughtBack(q)) {
				returnCargo(s, h, player);
			}
		}
		for (Objective o : q.objectives) {
			if (o.kind == ObjectiveKind.PROSPECT && o.data.contains(Objective.Keys.FOUND)) {
				BlockPos found = BlockPos.of(o.data.getLongOr(Objective.Keys.FOUND, 0L));
				ResourceSite.Kind kind = kind(o.data.getStringOr(Objective.Keys.RESOURCE, o.target));
				if (kind != null) {
					QuestEvents.SITE_PROSPECTED.invoker().onSiteProspected(level, s, kind, found, player);
					// the sim surveys round it and adds the site (SITE_FOUND resolves the spent-site happening); the
					// planner then opens a working there
					s.reportSite(kind, found, "prospected by " + player.getName().getString());
					player.sendSystemMessage(Component.translatable("hearth.quest.prospect.reported", s.name(),
						Component.literal(found.getX() + ", " + found.getZ())).withStyle(ChatFormatting.GREEN));
				}
			}
			if (o.kind == ObjectiveKind.RESCUE) {
				for (UUID victim : victims(o)) {
					Standings.deed(s, player.getUUID(), "hearth:quest/rescued", 3, victim, player.blockPosition(),
						QuestManager.config().living().deeds());
				}
			}
		}
		QuestTemplate t = QuestTemplates.get(q.template);
		if (t != null && t.when().wanted() == QuestTemplate.Wanted.ONLY) {
			QuestEvents.NAME_CLEARED.invoker().onNameCleared(player, s, q);
			dev.voidmc.hearth.api.LawService law = dev.voidmc.hearth.api.HearthServices.law();
			if (law != null) {
				law.pardon(player.getUUID(), s.id(), "worked off: " + q.text.title());
			}
			player.sendSystemMessage(Component.translatable("hearth.quest.name_cleared", q.giver.name(), s.name()).withStyle(ChatFormatting.GREEN));
		}
	}

	/** What a failed quest costs beyond standing: trapped miners not dug out in time die, where the server allows it. */
	/** The sim's caravan an escort quest answers (its happening's plot field), or 0. */
	static long caravanOf(Settlement s, QuestInstance q) {
		int hid = q.extra.getIntOr(QuestInstance.Extra.HAPPENING, 0);
		if (hid == 0) {
			hid = SettlementQuests.happeningOf(q);
		}
		Happening h = hid == 0 ? null : QuestStore.board(s).happening(hid);
		return h != null && h.kind().equals(Happening.CARAVAN_DEPARTED) && h.plot() > 0 ? h.plot() : 0;
	}

	/** A quest was taken: an escort of a real caravan rides with it, so whatever the road held, it arrives. */
	static void accepted(ServerLevel level, Settlement s, ServerPlayer player, QuestInstance q) {
		long caravan = caravanOf(s, q);
		HearthWorld world = HearthWorld.of(level);
		if (caravan > 0 && world != null && world.escortCaravan(caravan)) {
			dev.voidmc.hearth.Hearth.LOG.debug("[hearth quest] {} escorts caravan {} from {}", player.getName().getString(), caravan, s.name());
		}
	}

	static void ended(ServerLevel level, Settlement s, ServerPlayer player, QuestInstance q, QuestStatus outcome) {
		if (outcome == QuestStatus.FAILED) {
			// the escort was lost on the road: so was the caravan it rode with
			long caravan = caravanOf(s, q);
			HearthWorld world = HearthWorld.of(level);
			if (caravan > 0 && world != null) {
				world.caravanLost(caravan, null);
			}
		}
		if (outcome != QuestStatus.FAILED || !QuestManager.config().living().rescueFailureKills()) {
			return;
		}
		for (Objective o : q.objectives) {
			if (o.kind != ObjectiveKind.RESCUE || o.done()) {
				continue;
			}
			List<String> dead = new ArrayList<>();
			for (UUID id : victims(o)) {
				Resident r = s.resident(id);
				if (r != null && s.removeResident(id)) {
					dead.add(r.fullName());
				}
			}
			if (!dead.isEmpty()) {
				s.markDirty();
				player.sendSystemMessage(Component.translatable("hearth.quest.rescue.lost", joinNames(dead)).withStyle(ChatFormatting.DARK_RED));
			}
			int hid = q.extra.getIntOr(QuestInstance.Extra.HAPPENING, 0);
			if (hid != 0 && QuestStore.board(s).resolve(hid)) {
				s.markDirty();
			}
		}
	}

	private static boolean broughtBack(QuestInstance q) {
		for (Objective o : q.objectives) {
			if (o.data.getBooleanOr(Objective.Keys.GRANT_REQUIRED, false)) {
				return true;
			}
		}
		return false;
	}

	/** Recovered cargo goes back into the stores (as much as the stores hold). */
	private static void returnCargo(Settlement s, Happening h, ServerPlayer player) {
		for (Goods g : Goods.VALUES) {
			if (g.getSerializedName().equals(h.goods())) {
				int added = s.stockpile().add(g, Math.max(1, Math.min(h.amount(), 128)));
				s.markDirty();
				if (added > 0) {
					player.sendSystemMessage(Component.translatable("hearth.quest.cargo.returned", added, QuestGenerator.readable(g), s.name())
						.withStyle(ChatFormatting.GREEN));
				}
				return;
			}
		}
	}

	private static List<UUID> victims(Objective o) {
		List<UUID> out = new ArrayList<>();
		ListTag list = o.data.getListOrEmpty(QuestGenerator.VICTIMS);
		for (int i = 0; i < list.size(); i++) {
			try {
				list.getString(i).map(UUID::fromString).ifPresent(out::add);
			} catch (IllegalArgumentException ignored) {
				// a malformed id from an edited save
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ calling for help

	/**
	 * Calls out an urgent offer to the players in or near the town: the title, the summary, and a link that answers it
	 * ({@code /hearth heed}), with a bell.
	 */
	public static void broadcast(ServerLevel level, Settlement s, QuestInstance offer) {
		int reach = 96 + QuestGenerator.settlementRadius(s);
		for (ServerPlayer p : level.players()) {
			boolean in = QuestWorlds.at(level, p.blockPosition()) instanceof Settlement at && at.id() == s.id();
			if (!in && p.blockPosition().distSqr(s.center()) > (double) reach * reach) {
				continue;
			}
			String command = "/hearth heed " + offer.id;
			MutableComponent answer = Component.translatable("hearth.quest.heed.answer").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(offer.text.summary()))));
			p.sendSystemMessage(Component.translatable("hearth.quest.heed.call", Component.literal(offer.text.title()).withStyle(ChatFormatting.YELLOW))
				.withStyle(ChatFormatting.GOLD).append(" ").append(answer));
			level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F, 0.8F);
		}
	}

	/**
	 * A player answered a call ({@code /hearth heed <offer>}): the offer is taken if it is still up on the board of the town
	 * they are in or near.
	 */
	public static boolean heed(ServerPlayer player, UUID offerId) {
		ServerLevel level = player.level();
		Settlement s = QuestWorlds.at(level, player.blockPosition());
		if (s == null) {
			s = QuestWorlds.nearest(level, player.blockPosition(), 256);
		}
		if (s == null || QuestStore.board(s).offer(offerId) == null) {
			player.sendSystemMessage(Component.translatable("hearth.quest.gone").withStyle(ChatFormatting.GRAY));
			return false;
		}
		return QuestManager.accept(player, s, offerId);
	}

	// ------------------------------------------------------------------ helpers

	private static BlockPos middle(PlotView plot) {
		var spec = plot.spec();
		return new BlockPos(spec.origin().getX() + spec.sizeX() / 2, spec.origin().getY(), spec.origin().getZ() + spec.sizeZ() / 2);
	}

	/** The goods with the most units (by ordinal index), or empty. */
	static String mainGoods(int[] units) {
		int best = -1;
		int most = 0;
		for (int i = 0; i < units.length && i < Goods.VALUES.length; i++) {
			if (units[i] > most) {
				most = units[i];
				best = i;
			}
		}
		return best < 0 ? "" : Goods.VALUES[best].getSerializedName();
	}

	static String mainGoods(float[] yields) {
		int best = -1;
		float most = 0;
		for (int i = 0; i < yields.length && i < Goods.VALUES.length; i++) {
			if (yields[i] > most) {
				most = yields[i];
				best = i;
			}
		}
		return best < 0 ? "" : Goods.VALUES[best].getSerializedName();
	}

	/** "ore and timber": the two goods a caravan carried most of. */
	static String cargoText(int[] units) {
		int first = -1;
		int second = -1;
		for (int i = 0; i < units.length && i < Goods.VALUES.length; i++) {
			if (units[i] <= 0) {
				continue;
			}
			if (first < 0 || units[i] > units[first]) {
				second = first;
				first = i;
			} else if (second < 0 || units[i] > units[second]) {
				second = i;
			}
		}
		if (first < 0) {
			return "goods";
		}
		String a = QuestGenerator.readable(Goods.VALUES[first]);
		return second < 0 ? a : a + " and " + QuestGenerator.readable(Goods.VALUES[second]);
	}

	private static String joinNames(List<String> names) {
		if (names.size() <= 1) {
			return names.isEmpty() ? "" : names.getFirst();
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
	}

	private static ResourceSite.@Nullable Kind kind(String name) {
		for (ResourceSite.Kind k : ResourceSite.Kind.values()) {
			if (k.getSerializedName().equals(name)) {
				return k;
			}
		}
		return null;
	}
}
