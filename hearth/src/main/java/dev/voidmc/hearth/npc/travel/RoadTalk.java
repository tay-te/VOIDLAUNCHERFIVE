package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.roads.RoadBook;
import dev.voidmc.hearth.sim.roads.Roads;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * "I'd like to pay toward the road to Coldbrook" (docs/LIVING_WORLD_2.md §1.1, "Roads being built"): the reeve or the
 * steward of a town takes coin toward each of its unfinished roads — a crown at a time — which hires labour for it from
 * the next day, so the road reaches the next town sooner. Giving is a deed the town remembers. Order 150–179 (travel).
 */
final class RoadTalk implements DialogueProvider {
	/** Bits a gift is: a crown. */
	static final long GIFT = 100;
	private @Nullable String lastRoad;

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		if (resident.job != JobType.REEVE && resident.job != JobType.STEWARD) {
			return;
		}
		Roads.State st = Roads.state(SimServer.world((ServerLevel) player.level()));
		if (st == null) {
			return;
		}
		EconomyService eco = HearthServices.economy();
		int order = 160;
		for (RoadBook.LiveRoad r : st.live().book().live()) {
			if (r.spur() || r.finished() || r.way() == null || r.a() != settlement.id() && r.b() != settlement.id()) {
				continue;
			}
			String other = r.a() == settlement.id() ? r.names().get(1) : r.names().get(0);
			String id = "roads:fund/" + Long.toHexString(r.id());
			boolean can = eco != null && eco.balance(player) >= GIFT;
			Component cost = eco == null ? Component.literal(GIFT + " bits") : eco.format(GIFT);
			out.add(new Option(id, Component.translatable("hearth.roads.fund", other, cost), order++, !can,
				Component.translatable("hearth.roads.fund.hint", Math.round(r.built() * 100)), can ? null : Component.translatable("hearth.roads.fund.poor")));
			DialogueProvider.describe(id, new Info("service", -1, GIFT, null));
			if (order > 175) {
				break;
			}
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		if (!optionId.startsWith("roads:fund/")) {
			return false;
		}
		Roads.State st = Roads.state(SimServer.world((ServerLevel) player.level()));
		EconomyService eco = HearthServices.economy();
		long road;
		try {
			road = Long.parseUnsignedLong(optionId.substring("roads:fund/".length()), 16);
		} catch (NumberFormatException e) {
			return false;
		}
		RoadBook.LiveRoad r = st == null ? null : st.live().book().road(road);
		if (r == null || eco == null || !eco.charge(player, GIFT)) {
			this.lastRoad = null;
			return false;
		}
		st.live().fund(road, GIFT);
		this.lastRoad = r.a() == settlement.id() ? r.names().get(1) : r.names().get(0);
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			standing.deed(new StandingService.Deed(player.getUUID(), settlement.id(), "economy:donation", 4, resident.id, player.blockPosition(), true));
		}
		return true;
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return this.lastRoad == null ? null : Component.translatable("hearth.roads.fund.thanks." + (int) Math.floorMod(player.level().getGameTime() / 20, 3),
			this.lastRoad);
	}

	static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
