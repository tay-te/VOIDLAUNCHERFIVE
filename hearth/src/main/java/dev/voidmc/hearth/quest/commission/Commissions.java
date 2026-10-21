package dev.voidmc.hearth.quest.commission;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LivesService;
import dev.voidmc.hearth.api.QuestEvents;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.companion.Companions;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;

/**
 * The player's place: a lord's commissions and retinue ranks (docs/LIVING_WORLD_2.md §8). At a town with a lord, a player
 * of Known standing asks to serve; with the pledge of service and a commission done they are a retainer, then a sworn man
 * (three, a Friend of the seat), then a household knight (six, standing 70, a tournament won or a raid repelled; dubbed at
 * the throne). Commissions are {@code hearth_quest/commission/} templates bound to the domain's real state
 * ({@link Binder}), offered in the lord's or steward's conversation by rank and paid from the seat's treasury as any
 * official's quest is. Ranks per player per seat ({@link Retinue}); an heir keeps a service one rank lower until the
 * player pledges again. Event-driven: nothing ticks.
 *
 * <p>Lane B4 of docs/LIVING_WORLD_2.md (§11.3) owns this package.
 */
public final class Commissions {
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("commissions");

	private Commissions() {
	}

	/** Called once from {@code QuestModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		Retinue.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(CommissionSpecs.ID, new CommissionSpecs());
		DialogueProvider.register(new CommissionDialogue());
		QuestEvents.COMPLETED.register((player, s, type, template, tier) -> {
			if (s != null && CommissionSpecs.get(template) != null) {
				TIMER.time(() -> done(player, s, template));
			}
		});
		QuestEvents.FAILED.register((player, s, type, template, tier) -> closed(player, s, template));
		QuestEvents.ABANDONED.register((player, s, type, template, tier) -> closed(player, s, template));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			CommissionDialogue.forget(handler.player.getUUID()));
	}

	static PlaceConfig.Commissions cfg() {
		return Companions.config().commissions();
	}

	/** A commission done: counted toward the next rank (and the feat a knighthood asks, for raids and tournaments). */
	static void done(ServerPlayer player, Settlement seat, Identifier template) {
		CommissionSpecs.Spec spec = CommissionSpecs.get(template);
		Retinue r = Retinue.of(player);
		Retinue.Service s = r.service(seat.id());
		if (spec == null || s == null) {
			return;
		}
		Retinue.Service next = s.withDone(s.done() + 1, spec.feat());
		Retinue.set(player, r.with(seat.id(), next));
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			standing.deed(new StandingService.Deed(player.getUUID(), seat.id(), "quest:commission", 4, lordId(seat), player.blockPosition(), true));
		}
		int needed = RetinueRules.needed(next.rank(), cfg());
		if (needed > 0 && next.done() >= needed) {
			player.sendSystemMessage(Component.translatable("hearth.retinue.may_rise", seat.name()).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void closed(ServerPlayer player, @Nullable Settlement seat, Identifier template) {
		if (seat == null || CommissionSpecs.get(template) == null) {
			return;
		}
		Retinue r = Retinue.of(player);
		Retinue.Service s = r.service(seat.id());
		if (s != null && s.open().isPresent()) {
			Retinue.set(player, r.with(seat.id(), s.withOpen(null)));
		}
	}

	// ---------------------------------------------------------------- what other modules may ask

	/** The player's rank at a lord's seat, or null if they do not serve there. */
	public static @Nullable Rank rank(ServerPlayer player, long seat) {
		Retinue.Service s = Retinue.of(player).service(seat);
		return s == null ? null : s.rank();
	}

	/**
	 * The player's highest rank with the lord whose domain a settlement is in (its own lord's seat, or the nearest seat
	 * whose domain takes it in): what tolls, lodging and the guards' manners read. Null if none.
	 */
	public static @Nullable Rank rankIn(ServerPlayer player, Settlement s) {
		Retinue r = Retinue.of(player);
		if (r.services().isEmpty()) {
			return null;
		}
		Retinue.Service own = r.service(s.id());
		if (own != null) {
			return own.rank();
		}
		Rank best = null;
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		int radius = cfg().domainRadius();
		for (var e : r.services().entrySet()) {
			Settlement seat = QuestWorlds.byId(level.getServer(), e.getKey());
			if (seat != null && seat.dimension() == s.dimension() && seat.center().distSqr(s.center()) <= (long) radius * radius
				&& (best == null || e.getValue().rank().atLeast(best))) {
				best = e.getValue().rank();
			}
		}
		return best;
	}

	/** Toll-free passage on the lord's roads (a retainer and up; lane A1's and B3's tolls ask this). */
	public static boolean tollFree(ServerPlayer player, Settlement s) {
		Rank r = rankIn(player, s);
		return r != null && r.atLeast(Rank.RETAINER);
	}

	/** Lodging at the keep (a retainer and up). */
	public static boolean lodging(ServerPlayer player, Settlement seat) {
		Rank r = rank(player, seat.id());
		return r != null && r.atLeast(Rank.RETAINER);
	}

	/** The lord's colours: local dress for the standing module's look (a sworn man and up). */
	public static boolean colours(ServerPlayer player, Settlement s) {
		Rank r = rankIn(player, s);
		return r != null && r.atLeast(Rank.SWORN);
	}

	// ---------------------------------------------------------------- the seat

	static @Nullable Resident lord(Settlement s) {
		return Officials.holder(s, s.data(SocialModule.LEDGER), Office.LORD);
	}

	static @Nullable UUID lordId(Settlement s) {
		Resident l = lord(s);
		return l == null ? null : l.id;
	}

	/** Whether the resident speaks for a seat: its lord or steward. */
	static boolean speaksForLord(Settlement s, Resident r) {
		Office o = Officials.officeOf(s.data(SocialModule.LEDGER), r.id);
		return (o == Office.LORD || o == Office.STEWARD) && lord(s) != null;
	}

	/** The binder's world for a seat, from the live services. */
	static Binder.World world(ServerLevel level, Settlement seat) {
		HearthWorld w = HearthWorld.of(level);
		List<Settlement> others = QuestWorlds.all(level);
		long day = QuestWorlds.day(level);
		return new Binder.World() {
			@Override
			public long day() {
				return day;
			}

			@Override
			public List<? extends Settlement> others() {
				return others;
			}

			@Override
			public @Nullable Resident lord(Settlement s) {
				return Commissions.lord(s);
			}

			@Override
			public @Nullable Resident reeve(Settlement s) {
				return Officials.holder(s, s.data(SocialModule.LEDGER), Office.REEVE);
			}

			@Override
			public RoadNetwork roads() {
				return w == null ? RoadNetwork.NONE : w.roads();
			}

			@Override
			public List<LivesService.Feud> feuds(Settlement s) {
				LivesService lives = HearthServices.lives();
				return lives == null ? List.of() : lives.feuds(s);
			}

			@Override
			public List<String> events(Settlement s, long d) {
				return HearthServices.calendar().events(s, d);
			}

			@Override
			public int crime(Settlement s) {
				return QuestStore.board(s).crime;
			}

			@Override
			public int domainRadius() {
				return cfg().domainRadius();
			}
		};
	}

	/**
	 * Applies a binding's people to a bound quest: the TALK and COURIER objectives name the reeve, the heads of the houses,
	 * the neighbouring lord, in their own town.
	 */
	static void speak(QuestInstance q, Binder.Binding b) {
		for (Binder.Speak sp : b.speak()) {
			if (sp.objective() >= q.objectives.size()) {
				continue;
			}
			Objective o = q.objectives.get(sp.objective());
			String oldName = o.data.getStringOr(Objective.Keys.RECIPIENT, "");
			String oldTown = o.data.getStringOr(Objective.Keys.TARGET_NAME, "");
			o.entity = sp.resident().id;
			o.settlement = sp.town().id();
			o.pos = sp.town().center();
			o.data.putString(Objective.Keys.RECIPIENT, sp.resident().fullName());
			o.data.putString(Objective.Keys.TARGET_NAME, sp.town().name());
			if (!oldName.isEmpty()) {
				o.label = o.label.replace(oldName, sp.resident().fullName());
			}
			if (!oldTown.isEmpty()) {
				o.label = o.label.replace(oldTown, sp.town().name());
			}
		}
	}

	/** The seat's quest board (commissions pass through it as they are taken). */
	static SettlementQuests board(Settlement seat) {
		return QuestStore.board(seat);
	}
}
