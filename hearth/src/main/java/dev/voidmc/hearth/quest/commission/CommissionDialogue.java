package dev.voidmc.hearth.quest.commission;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SceneService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.economy.item.DeedData;
import dev.voidmc.hearth.economy.item.DeedItem;
import dev.voidmc.hearth.quest.gen.GenerationContext;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.service.Standings;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.QuestTemplates;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The lord's commissions in conversation (docs/LIVING_WORLD_2.md §8.1, order band 260–279), with the lord or the steward of
 * a seat: asking to serve; "Have you a commission for me?" — bound there and then to the domain's state, told, and taken
 * or put off; asking to rise (and why not yet); what a rank gives. Rights given on rising: the lord's horse to a sworn man,
 * a house in the seat by deed to a household knight, dubbed at the throne before the court (a scene through the town
 * events' runner where it is there, else a beat of the conversation heard by all near).
 */
public final class CommissionDialogue implements DialogueProvider {
	static final String P = "commission:";
	static final String SERVE = P + "serve";
	static final String ASK = P + "ask";
	static final String TAKE = P + "take";
	static final String DECLINE = P + "decline";
	static final String RISE = P + "rise";
	static final String RIGHTS = P + "rights";
	public static final net.minecraft.resources.Identifier DUBBING = Hearth.id("dubbing");

	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private record Pending(long seat, QuestInstance quest) {
	}

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		if (!Commissions.speaksForLord(s, r)) {
			return;
		}
		Retinue retinue = Retinue.of(player);
		Retinue.Service sv = retinue.service(s.id());
		StandingService.Title title = Standings.title(s, player.getUUID());
		if (sv == null) {
			if (RetinueRules.mayPetition(title)) {
				out.add(new Option(SERVE, Component.translatable("hearth.retinue.talk.serve"), 260));
			}
			return;
		}
		sv = confirm(player, s, retinue, sv);
		Pending p = PENDING.get(player.getUUID());
		if (p != null && p.seat == s.id()) {
			out.add(new Option(TAKE, Component.translatable("hearth.retinue.talk.take", p.quest.text.title()), 262));
			out.add(new Option(DECLINE, Component.translatable("hearth.retinue.talk.decline"), 263));
		} else if (!onCommission(player, sv)) {
			out.add(new Option(ASK, Component.translatable("hearth.retinue.talk.ask"), 261));
		}
		Rank next = sv.rank().next();
		if (next != null && (next != Rank.KNIGHT || Commissions.cfg().knighthood())) {
			RetinueRules.Why why = RetinueRules.rise(sv, pledged(s, player), title, Standings.standing(s, player.getUUID()), Commissions.cfg());
			boolean ok = why == RetinueRules.Why.OK;
			out.add(new Option(RISE, Component.translatable("hearth.retinue.talk.rise", Component.translatable(next.translationKey())), 264, !ok, null,
				ok ? null : whyNot(why, sv)));
		}
		out.add(new Option(RIGHTS, Component.translatable("hearth.retinue.talk.rights"), 265));
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String id) {
		if (!id.startsWith(P) || !Commissions.speaksForLord(s, r) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ANSWER.remove(player.getUUID());
		Retinue retinue = Retinue.of(player);
		Retinue.Service sv = retinue.service(s.id());
		switch (id) {
			case SERVE -> {
				if (sv != null || !RetinueRules.mayPetition(Standings.title(s, player.getUUID()))) {
					return false;
				}
				Retinue.set(player, retinue.with(s.id(), Retinue.Service.start(Commissions.lordId(s), QuestWorlds.day(level))));
				answer(player, Component.translatable("hearth.retinue.talk.served", player.getName()));
				dev.voidmc.hearth.npc.companion.PlaceSummary.send(player);
				return true;
			}
			case ASK -> {
				if (sv == null || onCommission(player, sv)) {
					return false;
				}
				QuestInstance q = TIMED(() -> offer(level, s, r, sv));
				if (q == null) {
					answer(player, Component.translatable("hearth.retinue.talk.nothing"));
					return true;
				}
				PENDING.put(player.getUUID(), new Pending(s.id(), q));
				for (Objective o : q.objectives) {
					player.sendSystemMessage(Component.literal("  • " + o.label).withStyle(ChatFormatting.GRAY));
				}
				answer(player, Component.literal(q.text.offer().isEmpty() ? q.text.summary() : q.text.offer()));
				return true;
			}
			case TAKE -> {
				Pending p = PENDING.remove(player.getUUID());
				if (p == null || p.seat != s.id() || sv == null) {
					return false;
				}
				SettlementQuests board = QuestStore.board(s);
				board.offers.add(p.quest);
				boolean taken = QuestManager.accept(player, s, p.quest.id);
				board.offers.remove(p.quest);
				s.markDirty();
				if (taken) {
					Retinue.set(player, Retinue.of(player).with(s.id(), sv.withOpen(p.quest.id)));
				}
				return true;
			}
			case DECLINE -> {
				PENDING.remove(player.getUUID());
				answer(player, Component.translatable("hearth.retinue.talk.declined"));
				return true;
			}
			case RISE -> {
				if (sv == null) {
					return false;
				}
				return rise(level, player, s, r, retinue, sv);
			}
			case RIGHTS -> {
				Rank rank = sv == null ? null : sv.rank();
				answer(player, Component.translatable(rank == null ? "hearth.retinue.rights.none" : "hearth.retinue.rights." + rank.getSerializedName(),
					s.name()));
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement s, Resident r, String id) {
		return id.startsWith(P) ? ANSWER.remove(player.getUUID()) : null;
	}

	static void forget(UUID player) {
		ANSWER.remove(player);
		PENDING.remove(player);
	}

	// ---------------------------------------------------------------- commissions

	private static <T> T TIMED(java.util.function.Supplier<T> work) {
		long t0 = System.nanoTime();
		try {
			return work.get();
		} finally {
			Commissions.TIMER.add(System.nanoTime() - t0);
		}
	}

	/** A commission for the servant's rank, bound to the domain as it stands, or null when the domain offers none now. */
	static @Nullable QuestInstance offer(ServerLevel level, Settlement seat, Resident giver, Retinue.Service sv) {
		Binder.World world = Commissions.world(level, seat);
		SettlementQuests data = QuestStore.board(seat);
		GenerationContext ctx = QuestManager.generationContext(level, seat, data);
		List<CommissionSpecs.Spec> specs = new ArrayList<>();
		for (CommissionSpecs.Spec spec : CommissionSpecs.all()) {
			if (RetinueRules.given(spec.least(), sv.rank()) && data.cooldowns.getOrDefault(spec.template(), Long.MIN_VALUE) <= ctx.day()) {
				specs.add(spec);
			}
		}
		// the higher commissions first, a fresh order each day
		RandomSource random = RandomSource.create(seat.id() * 31 + ctx.day() * 7 + sv.done());
		java.util.Collections.shuffle(specs, new java.util.Random(random.nextLong()));
		specs.sort((a, b) -> Integer.compare(b.least().ordinal(), a.least().ordinal()));
		return bindFirst(specs, seat, world, ctx, giver);
	}

	/** Binds the first spec the world bears out (also the tests' way in). */
	static @Nullable QuestInstance bindFirst(List<CommissionSpecs.Spec> specs, Settlement seat, Binder.World world, GenerationContext ctx,
		@Nullable Resident giver) {
		for (CommissionSpecs.Spec spec : specs) {
			QuestTemplate t = QuestTemplates.get(spec.template());
			Binder.Binding b = t == null ? null : Binder.bind(spec.kind(), seat, world, ctx.day() * 131 + seat.id());
			if (b == null) {
				continue;
			}
			QuestInstance q = QuestGenerator.bind(t, ctx, null, b.happening());
			if (q == null) {
				continue;
			}
			Commissions.speak(q, b);
			if (giver != null) {
				q.giver = new QuestInstance.Giver(Optional.of(giver.id), giver.fullName(), giver.job.getSerializedName());
			}
			return q;
		}
		return null;
	}

	private static boolean onCommission(ServerPlayer player, Retinue.Service sv) {
		if (sv.open().isEmpty()) {
			return false;
		}
		PlayerQuests pq = QuestStore.get(player);
		QuestInstance q = pq.get(sv.open().get());
		return q != null && q.status.inProgress();
	}

	// ---------------------------------------------------------------- ranks

	/** The service as the seat stands now (a new lord, a pledge renewed), saved if it changed. */
	private static Retinue.Service confirm(ServerPlayer player, Settlement s, Retinue retinue, Retinue.Service sv) {
		Retinue.Service now = RetinueRules.confirm(sv, Commissions.lordId(s), pledged(s, player));
		if (!now.equals(sv)) {
			Retinue.set(player, retinue.with(s.id(), now));
			if (now.demotedFrom().isPresent() && sv.demotedFrom().isEmpty()) {
				player.sendSystemMessage(Component.translatable("hearth.retinue.heir", s.name()).withStyle(ChatFormatting.GOLD));
			}
		}
		return now;
	}

	/** Whether the player swore the pledge of service to the seat's lord (the social module's pledge, on the lord). */
	static boolean pledged(Settlement s, ServerPlayer player) {
		Resident lord = Commissions.lord(s);
		if (lord == null) {
			return false;
		}
		var list = lord.extra.getCompoundOrEmpty("social").getListOrEmpty("pledged");
		String me = player.getUUID().toString();
		for (int i = 0; i < list.size(); i++) {
			if (list.getStringOr(i, "").equals(me)) {
				return true;
			}
		}
		return false;
	}

	private static boolean rise(ServerLevel level, ServerPlayer player, Settlement s, Resident speaker, Retinue retinue, Retinue.Service sv) {
		StandingService.Title title = Standings.title(s, player.getUUID());
		if (RetinueRules.rise(sv, pledged(s, player), title, Standings.standing(s, player.getUUID()), Commissions.cfg()) != RetinueRules.Why.OK) {
			return false;
		}
		Rank next = sv.rank().next();
		if (next == null) {
			return false;
		}
		Resident lord = Commissions.lord(s);
		if (next == Rank.KNIGHT) {
			BlockPos throne = throne(s, player.blockPosition());
			if (throne != null && throne.distSqr(player.blockPosition()) > 12 * 12) {
				answer(player, Component.translatable("hearth.retinue.talk.at_throne"));
				return true;
			}
		}
		Retinue.Service up = sv.withRank(next);
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			standing.deed(new StandingService.Deed(player.getUUID(), s.id(), "quest:retinue_rank", 4 + 4 * next.ordinal(), lord == null ? null : lord.id,
				player.blockPosition(), true));
		}
		Component rankName = Component.translatable(next.translationKey());
		if (next == Rank.SWORN && !up.horse() && horse(level, player, s)) {
			up = up.withHorse();
			player.sendSystemMessage(Component.translatable("hearth.retinue.horse").withStyle(ChatFormatting.GRAY));
		}
		if (next == Rank.KNIGHT) {
			SceneService scenes = HearthServices.scenes();
			CompoundTag args = new CompoundTag();
			args.putString("player", player.getUUID().toString());
			boolean staged = scenes != null && lord != null && scenes.stage(s, DUBBING, List.of(lord.id), player, args);
			Component heard = Component.translatable("hearth.retinue.dubbed_heard", player.getName(), lord == null ? s.name() : lord.fullName(), s.name())
				.withStyle(ChatFormatting.GOLD);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(player) < 48 * 48) {
					p.sendSystemMessage(heard);
				}
			}
			if (!up.house() && house(player, s)) {
				up = up.withHouse();
			}
			answer(player, Component.translatable(staged ? "hearth.retinue.talk.dubbed_scene" : "hearth.retinue.talk.dubbed", player.getName()));
		} else {
			answer(player, Component.translatable("hearth.retinue.talk.risen", rankName));
		}
		Retinue.set(player, retinue.with(s.id(), up));
		dev.voidmc.hearth.npc.companion.PlaceSummary.send(player);
		return true;
	}

	private static Component whyNot(RetinueRules.Why why, Retinue.Service sv) {
		return switch (why) {
			case PLEDGE -> Component.translatable("hearth.retinue.why.pledge");
			case COMMISSIONS -> Component.translatable("hearth.retinue.why.commissions",
				Math.max(0, RetinueRules.needed(sv.rank(), Commissions.cfg()) - sv.done()));
			case TITLE -> Component.translatable("hearth.retinue.why.title");
			case STANDING -> Component.translatable("hearth.retinue.why.standing", Commissions.cfg().knightStanding());
			case FEAT -> Component.translatable("hearth.retinue.why.feat");
			default -> Component.translatable("hearth.retinue.why.top");
		};
	}

	private static @Nullable BlockPos throne(Settlement s, BlockPos near) {
		BlockPos best = null;
		for (Poi p : s.pois(PoiType.THRONE)) {
			if (best == null || p.pos().distSqr(near) < best.distSqr(near)) {
				best = p.pos();
			}
		}
		return best;
	}

	/** The lord's horse for a sworn man: saddled, tamed to them, beside them. */
	private static boolean horse(ServerLevel level, ServerPlayer player, Settlement s) {
		AbstractHorse horse = dev.voidmc.hearth.social.service.Stables.mount(s.culture()).create(level, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return false;
		}
		BlockPos at = dev.voidmc.hearth.npc.entity.FolkSpots.standableNear(level, player.blockPosition().offset(2, 0, 2), 4);
		if (at == null) {
			return false;
		}
		horse.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0);
		horse.setTamed(true);
		horse.setOwner(player);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		horse.setPersistenceRequired();
		return level.addFreshEntity(horse);
	}

	/**
	 * A household knight's house in the seat, by deed: a built house nobody owns or lives in, else a plot of land for one;
	 * false (asked again at the next rising talk) when the seat has none free.
	 */
	private static boolean house(ServerPlayer player, Settlement s) {
		Set<Integer> lived = new HashSet<>();
		for (Resident r : s.residents()) {
			lived.add(r.home);
		}
		PlotView pick = null;
		for (PlotView p : s.plots()) {
			if (p.owner() != null || !p.spec().archetype().getPath().startsWith("house/") || lived.contains(p.id())) {
				continue;
			}
			if (p.state() == PlotState.BUILT) {
				pick = p;
				break;
			}
			if (pick == null && p.state() == PlotState.PLANNED) {
				pick = p;
			}
		}
		if (pick == null) {
			player.sendSystemMessage(Component.translatable("hearth.retinue.no_house").withStyle(ChatFormatting.GRAY));
			return false;
		}
		s.setPlotOwner(pick.id(), player.getUUID());
		s.markDirty();
		var spec = pick.spec();
		ItemStack deed = DeedItem.create(new DeedData(s.id(), s.name(), pick.id(), spec.archetype().getPath(), pick.state() == PlotState.BUILT,
			spec.origin(), spec.sizeX(), spec.sizeZ(), false));
		if (!player.getInventory().add(deed)) {
			player.drop(deed, false, Prediction.SERVER_ONLY);
		}
		player.sendSystemMessage(Component.translatable("hearth.retinue.house", s.name()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	private static void answer(ServerPlayer player, Component line) {
		ANSWER.put(player.getUUID(), line);
	}

	static boolean steward(Settlement s, Resident r) {
		return Officials.officeOf(s.data(SocialModule.LEDGER), r.id) == Office.STEWARD;
	}
}
