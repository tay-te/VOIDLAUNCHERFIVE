package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Companions in conversation (docs/LIVING_WORLD_2.md §8.1, order band 150–179): at an inn or tavern, "Any hands for hire?"
 * and the hands the keeper names; to one's own companion, the orders — follow, wait here, keep your distance, fight what I
 * fight, hold back, go home — their wage, and their gear ("Let me see your gear", "Take this", "Give that back") until the
 * Companion gear screen (F3) is designed.
 */
public final class CompanionDialogue implements DialogueProvider {
	static final String P = "companion:";
	static final String ASK = P + "ask";
	static final String HIRE = P + "hire/";
	static final String FOLLOW = P + "follow";
	static final String WAIT = P + "wait";
	static final String DISTANCE = P + "distance";
	static final String FIGHT = P + "fight";
	static final String HOLD = P + "hold";
	static final String GEAR = P + "gear";
	static final String TAKE = P + "take";
	static final String RETURN = P + "return";
	static final String WAGE = P + "wage";
	static final String HOME = P + "home";
	/** Archetypes whose keeper knows who would hire on. */
	private static final Set<String> INNS = Set.of("commerce/inn", "commerce/tavern", "commerce/hostel", "commerce/caravanserai");
	/** How long the hands a keeper named stay on offer to the player (game ticks). */
	private static final long OFFER_TICKS = 20 * 60 * 10;

	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();
	/** The hands each player was told of, and when. */
	private static final Map<UUID, Offered> OFFERED = new ConcurrentHashMap<>();

	private record Offered(long settlementId, List<UUID> residents, long at) {
	}

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		if (r.age == AgeStage.CHILD) {
			return;
		}
		PlaceConfig cfg = Companions.config();
		CompanionState mine = CompanionState.of(r);
		if (mine != null) {
			if (mine.player().equals(player.getUUID())) {
				orders(player, r, mine, out);
			}
			return;
		}
		if (keeper(s, r) && cfg.companions().perPlayer() > 0) {
			out.add(new Option(ASK, Component.translatable("hearth.companion.talk.ask"), 160));
			Offered o = OFFERED.get(player.getUUID());
			if (o != null && o.settlementId == s.id() && player.level().getGameTime() - o.at < OFFER_TICKS) {
				int i = 0;
				for (UUID id : o.residents) {
					Resident hand = s.resident(id);
					if (hand == null || !CompanionRules.willing(s, hand)) {
						continue;
					}
					hireOption(player, hand, cfg, 161 + i++, out);
				}
			}
		} else {
			// a hand the keeper named, asked to their face
			Offered o = OFFERED.get(player.getUUID());
			if (o != null && o.settlementId == s.id() && o.residents.contains(r.id) && CompanionRules.willing(s, r)) {
				hireOption(player, r, cfg, 165, out);
			}
		}
	}

	private static void hireOption(ServerPlayer player, Resident hand, PlaceConfig cfg, int order, List<Option> out) {
		long fee = CompanionRules.fee(hand, cfg);
		long wage = CompanionRules.wage(hand, cfg);
		boolean full = CompanionRoster.of(player).size() >= cfg.companions().perPlayer();
		String id = HIRE + hand.id;
		out.add(new Option(id, Component.translatable("hearth.companion.talk.hire", hand.fullName(), job(hand), coins(fee), coins(wage)), order, full,
			null, full ? Component.translatable("hearth.companion.talk.full", cfg.companions().perPlayer()) : null));
		DialogueProvider.describe(id, new DialogueProvider.Info("service", -1, fee, null));
	}

	private static void orders(ServerPlayer player, Resident r, CompanionState st, List<Option> out) {
		if (st.follow() != CompanionState.Follow.FOLLOW) {
			out.add(opt(FOLLOW, "follow", 150));
		}
		if (st.follow() != CompanionState.Follow.WAIT) {
			out.add(opt(WAIT, "wait", 151));
		}
		if (st.follow() != CompanionState.Follow.DISTANCE) {
			out.add(opt(DISTANCE, "distance", 152));
		}
		out.add(st.fight() ? opt(HOLD, "hold", 153) : opt(FIGHT, "fight", 153));
		out.add(opt(GEAR, "gear", 154));
		ItemStack held = player.getMainHandItem();
		if (CompanionGear.slotFor(held) != null) {
			out.add(new Option(TAKE, Component.translatable("hearth.companion.talk.take", held.getHoverName()), 155));
		}
		if (!st.gear().isEmpty()) {
			out.add(opt(RETURN, "return", 156));
		}
		out.add(opt(WAGE, "wage", 157));
		out.add(opt(HOME, "home", 179));
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String id) {
		if (!id.startsWith(P) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ANSWER.remove(player.getUUID());
		PlaceConfig cfg = Companions.config();
		if (id.equals(ASK)) {
			if (!keeper(s, r)) {
				return false;
			}
			List<Resident> hands = CompanionRules.candidates(s, cfg.companions().offered());
			List<UUID> ids = new ArrayList<>();
			for (Resident h : hands) {
				ids.add(h.id);
			}
			OFFERED.put(player.getUUID(), new Offered(s.id(), ids, level.getGameTime()));
			answer(player, hands.isEmpty() ? Component.translatable("hearth.companion.talk.none")
				: Component.translatable("hearth.companion.talk.named", names(hands)));
			return true;
		}
		if (id.startsWith(HIRE)) {
			UUID who;
			try {
				who = UUID.fromString(id.substring(HIRE.length()));
			} catch (IllegalArgumentException e) {
				return false;
			}
			Offered o = OFFERED.get(player.getUUID());
			Resident hand = s.resident(who);
			if (hand == null || o == null || o.settlementId != s.id() || !o.residents.contains(who)) {
				return false;
			}
			CompanionService.HireResult res = CompanionService.hire(player, s, hand);
			answer(player, switch (res) {
				case OK -> {
					NpcService.FolkHandle h = folk(hand);
					yield h != null ? CompanionBarks.line(h, "companion_hired", "hearth.companion.talk.hired")
						: Component.translatable("hearth.companion.talk.hired");
				}
				case FULL -> Component.translatable("hearth.companion.talk.full", cfg.companions().perPlayer());
				case UNWILLING -> Component.translatable("hearth.companion.talk.unwilling", hand.givenName);
				case CANNOT_PAY -> Component.translatable("hearth.companion.talk.cannot_pay", coins(CompanionRules.fee(hand, cfg)));
				case NO_ROOM -> Component.translatable("hearth.companion.talk.no_room");
				case NO_ECONOMY -> Component.translatable("hearth.companion.talk.no_economy");
			});
			if (res == CompanionService.HireResult.OK) {
				OFFERED.remove(player.getUUID());
			}
			return true;
		}
		CompanionState st = CompanionState.of(r);
		if (st == null || !st.player().equals(player.getUUID())) {
			return false;
		}
		NpcService.FolkHandle h = folk(r);
		switch (id) {
			case FOLLOW -> {
				st.setFollow(CompanionState.Follow.FOLLOW);
				st.setWaitAt(null);
				answer(player, h == null ? Component.translatable("hearth.companion.talk.follow_ok") : CompanionBarks.line(h, "companion_follow",
					"hearth.companion.talk.follow_ok"));
			}
			case WAIT -> {
				st.setFollow(CompanionState.Follow.WAIT);
				st.setWaitAt(h != null ? h.entity().blockPosition() : player.blockPosition());
				answer(player, h == null ? Component.translatable("hearth.companion.talk.wait_ok") : CompanionBarks.line(h, "companion_wait",
					"hearth.companion.talk.wait_ok"));
			}
			case DISTANCE -> {
				st.setFollow(CompanionState.Follow.DISTANCE);
				st.setWaitAt(null);
				answer(player, Component.translatable("hearth.companion.talk.distance_ok"));
			}
			case FIGHT -> {
				st.setFight(true);
				answer(player, Component.translatable("hearth.companion.talk.fight_ok"));
			}
			case HOLD -> {
				st.setFight(false);
				answer(player, Component.translatable("hearth.companion.talk.hold_ok"));
			}
			case GEAR -> answer(player, gearText(level, st));
			case TAKE -> {
				ItemStack held = player.getMainHandItem();
				EquipmentSlot slot = CompanionGear.slotFor(held);
				if (slot == null) {
					return false;
				}
				Map<EquipmentSlot, ItemStack> gear = new HashMap<>(CompanionGear.read(st, level.registryAccess()));
				ItemStack old = gear.put(slot, held.copyWithCount(1));
				held.shrink(1);
				if (old != null) {
					CompanionGear.give(player, old);
				}
				CompanionGear.write(st, gear, level.registryAccess());
				if (h != null) {
					h.entity().setItemSlot(slot, gear.get(slot).copy());
					h.entity().setDropChance(slot, 0.0F);
				}
				answer(player, Component.translatable("hearth.companion.talk.taken", gear.get(slot).getHoverName()));
			}
			case RETURN -> {
				if (h != null) {
					CompanionGear.undress(h.entity(), st, level.registryAccess());
				}
				for (ItemStack g : CompanionGear.takeAll(st, level.registryAccess())) {
					CompanionGear.give(player, g);
				}
				answer(player, Component.translatable("hearth.companion.talk.returned"));
			}
			case WAGE -> {
				EconomyService eco = HearthServices.economy();
				answer(player, Component.translatable("hearth.companion.talk.wage_" + mood(st), coins(st.wage()), st.unpaid()));
			}
			case HOME -> {
				Component bye = h == null ? Component.translatable("hearth.companion.talk.home_ok") : CompanionBarks.line(h, "companion_dismissed",
					"hearth.companion.talk.home_ok");
				CompanionService.release(level.getServer(), s, r, SettlementEvents.Companion.Phase.DISMISSED, null);
				answer(player, bye);
			}
			default -> {
				return false;
			}
		}
		s.markDirty();
		return true;
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement s, Resident r, String id) {
		return id.startsWith(P) ? ANSWER.remove(player.getUUID()) : null;
	}

	/** Forgets a player's offers (logout). */
	static void forget(UUID player) {
		ANSWER.remove(player);
		OFFERED.remove(player);
	}

	// ---------------------------------------------------------------- the pieces

	/** An innkeeper or tavern keeper: who knows the hands for hire. */
	static boolean keeper(Settlement s, Resident r) {
		if (r.job == JobType.INNKEEPER) {
			return true;
		}
		PlotView p = r.work == Resident.NO_PLOT ? null : s.plot(r.work);
		return p != null && INNS.contains(p.spec().archetype().getPath());
	}

	private static String mood(CompanionState st) {
		return st.unpaid() > 0 ? "owed" : st.loyalty() >= 20 ? "glad" : st.loyalty() <= -20 ? "sour" : "fair";
	}

	private static Component gearText(ServerLevel level, CompanionState st) {
		Map<EquipmentSlot, ItemStack> gear = CompanionGear.read(st, level.registryAccess());
		if (gear.isEmpty()) {
			return Component.translatable("hearth.companion.talk.gear_none");
		}
		MutableComponent list = Component.empty();
		boolean first = true;
		for (EquipmentSlot slot : CompanionGear.SLOTS) {
			ItemStack s = gear.get(slot);
			if (s == null || s.isEmpty()) {
				continue;
			}
			if (!first) {
				list.append(", ");
			}
			list.append(s.getHoverName());
			first = false;
		}
		return Component.translatable("hearth.companion.talk.gear_list", list);
	}

	private static Component names(List<Resident> hands) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < hands.size(); i++) {
			if (i > 0) {
				out.append(i == hands.size() - 1 ? Component.translatable("hearth.companion.talk.and") : Component.literal(", "));
			}
			Resident h = hands.get(i);
			out.append(Component.translatable("hearth.companion.talk.hand", h.fullName(), job(h)));
		}
		return out;
	}

	static Component job(Resident r) {
		return Component.translatable("hearth.npc.job." + r.job.getSerializedName());
	}

	static Component coins(long bits) {
		EconomyService e = HearthServices.economy();
		return e != null ? e.format(bits) : Component.literal(bits + " bits");
	}

	private static Option opt(String id, String key, int order) {
		return new Option(id, Component.translatable("hearth.companion.talk." + key), order);
	}

	private static void answer(ServerPlayer player, Component line) {
		ANSWER.put(player.getUUID(), line);
	}

	private static NpcService.@Nullable FolkHandle folk(Resident r) {
		NpcService npc = HearthServices.npc();
		return npc == null ? null : npc.folk(r.id);
	}
}
