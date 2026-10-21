package dev.voidmc.hearth.economy.household;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.companion.Companions;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.economy.property.PropertyNames;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The household in conversation (docs/LIVING_WORLD_2.md §8.1, order band 150–179), until the Household page (F2) is
 * designed: a poor adult asked to keep the player's house, and one's own servants asked for the news, their wage, or let
 * go.
 */
public final class HouseholdDialogue implements DialogueProvider {
	static final String P = "household:";
	static final String KEEP = P + "keep/";
	static final String NEWS = P + "news";
	static final String WAGE = P + "wage";
	static final String GO = P + "go";

	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		PlaceConfig cfg = Companions.config();
		ServantState st = ServantState.of(r);
		if (st != null) {
			if (st.player().equals(player.getUUID())) {
				out.add(new Option(NEWS, Component.translatable("hearth.household.talk.news"), 170));
				out.add(new Option(WAGE, Component.translatable("hearth.household.talk.wage"), 171));
				out.add(new Option(GO, Component.translatable("hearth.household.talk.go"), 178));
			}
			return;
		}
		if (cfg.household().servantsPerHouse() <= 0 || !Servants.willing(s, r)) {
			return;
		}
		HouseBook book = s.data(Household.HOUSES);
		int i = 0;
		for (PlotView house : Servants.houses(s, player.getUUID())) {
			HouseBook.House h = book.house(house.id());
			int have = h == null ? 0 : h.servants.size();
			int slots = Servants.slots(s, house, cfg);
			boolean full = have >= slots;
			String id = KEEP + house.id();
			long wage = Servants.wage(cfg);
			out.add(new Option(id, Component.translatable("hearth.household.talk.keep", PropertyNames.lower(house.spec().archetype().getPath()), coins(wage)),
				172 + i, full, null, full ? Component.translatable("hearth.household.talk.full", slots) : null));
			DialogueProvider.describe(id, new DialogueProvider.Info("service", -1, wage, null));
			if (++i >= 3) {
				break;
			}
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String id) {
		if (!id.startsWith(P) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ANSWER.remove(player.getUUID());
		PlaceConfig cfg = Companions.config();
		HouseBook book = s.data(Household.HOUSES);
		HearthWorld w = HearthWorld.of(level);
		long today = w == null ? 0 : w.day();
		if (id.startsWith(KEEP)) {
			int plotId;
			try {
				plotId = Integer.parseInt(id.substring(KEEP.length()));
			} catch (NumberFormatException e) {
				return false;
			}
			PlotView house = s.plot(plotId);
			if (house == null || !player.getUUID().equals(house.owner()) || !Servants.house(house) || !Servants.willing(s, r)) {
				return false;
			}
			HouseBook.House h = book.house(plotId);
			if ((h == null ? 0 : h.servants.size()) >= Servants.slots(s, house, cfg)) {
				answer(player, Component.translatable("hearth.household.talk.full", Servants.slots(s, house, cfg)));
				return true;
			}
			EconomyService eco = HearthServices.economy();
			if (eco == null || !eco.chargeDeposit(player.getUUID(), 0, "check")) {
				answer(player, Component.translatable("hearth.household.talk.no_bank"));
				return true;
			}
			Servants.hire(s, book, r, house, today, cfg);
			HouseholdIndex.set(player, HouseholdIndex.of(player).with(new HouseholdIndex.Entry(s.id(), s.dimension(), plotId)));
			s.markDirty();
			answer(player, Component.translatable("hearth.household.talk.kept", PropertyNames.lower(house.spec().archetype().getPath()),
				coins(Servants.wage(cfg))));
			dev.voidmc.hearth.npc.companion.PlaceSummary.send(player);
			return true;
		}
		ServantState st = ServantState.of(r);
		if (st == null || !st.player().equals(player.getUUID())) {
			return false;
		}
		PlotView house = s.plot(st.plot());
		HouseBook.House h = book.house(st.plot());
		switch (id) {
			case NEWS -> {
				if (house == null || h == null) {
					return false;
				}
				List<Component> lines = Household.report(s, house, h, player, today);
				for (int i = 1; i < lines.size(); i++) {
					player.sendSystemMessage(lines.get(i));
				}
				answer(player, lines.getFirst());
			}
			case WAGE -> answer(player, Component.translatable(st.unpaid() > 0 ? "hearth.household.talk.wage_owed" : "hearth.household.talk.wage_fair",
				coins(st.wage()), st.unpaid()));
			case GO -> {
				Servants.release(s, book, r);
				if (h != null && h.servants.isEmpty() && house == null) {
					book.houses.remove(st.plot());
				}
				answer(player, Component.translatable("hearth.household.talk.gone"));
			}
			default -> {
				return false;
			}
		}
		s.markDirty();
		dev.voidmc.hearth.npc.companion.PlaceSummary.send(player);
		return true;
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement s, Resident r, String id) {
		return id.startsWith(P) ? ANSWER.remove(player.getUUID()) : null;
	}

	private static void answer(ServerPlayer player, Component line) {
		ANSWER.put(player.getUUID(), line);
	}

	static Component coins(long bits) {
		EconomyService e = HearthServices.economy();
		return e != null ? e.format(bits) : Component.literal(bits + " bits");
	}
}
