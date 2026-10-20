package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.life.Venues;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The rhythm in conversation (docs/LIVING_WORLD_2.md §2.1, order bands §0.4): the innkeeper sells a round for the house
 * of an evening ({@code social:bought_a_round}, folk raise their mugs to the buyer), and the priest says when the next
 * service is. No new screen: plain options in the conversation.
 */
public final class RhythmDialogue implements DialogueProvider {
	static final String ROUND = "rhythm:round";
	static final String WHEN = "rhythm:service_when";
	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();
	/** Rounds bought: settlement/day/player, once a night. */
	private static final Set<String> BOUGHT = ConcurrentHashMap.newKeySet();

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		if (!Rhythm.config().rhythm().enabled()) {
			return;
		}
		if (resident.job == JobType.INNKEEPER && Rhythm.config().tavern().enabled() && Hooks.economy() != null) {
			RhythmData.Tavern t = RhythmData.of(settlement.culture()).tavern();
			if (TavernNight.open(t, RhythmFolk.minute(settlement), false) && !BOUGHT.contains(key(settlement, player))) {
				long cost = cost(player, settlement, resident);
				out.add(new Option(ROUND, Component.translatable("hearth.rhythm.round", Hooks.coins(cost)), 125));
				DialogueProvider.describe(ROUND, new Info("service", -1, cost, null));
			}
		}
		if (resident.job == JobType.PRIEST || resident.job == JobType.MONK) {
			out.add(new Option(WHEN, Component.translatable("hearth.rhythm.when", ServiceBehaviour.serviceName(RhythmData.of(settlement.culture()))), 85));
			DialogueProvider.describe(WHEN, Info.of("talk"));
		}
	}

	static String key(Settlement s, ServerPlayer player) {
		return s.id() + "/" + RhythmFolk.today(s) + "/" + player.getUUID();
	}

	/** A few bits a head of those drinking near the innkeeper (two heads at least). */
	static long cost(ServerPlayer player, Settlement s, Resident innkeeper) {
		int heads = 0;
		NpcService npc = HearthServices.npc();
		if (npc != null && player.level() instanceof ServerLevel level) {
			NpcService.FolkHandle keeper = npc.folk(innkeeper.id);
			BlockPos at = keeper != null ? keeper.entity().blockPosition() : player.blockPosition();
			heads = npc.folkNear(level, net.minecraft.world.phys.Vec3.atCenterOf(at), 16).size();
		}
		return (long) Math.max(2, heads) * Rhythm.config().tavern().round();
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		switch (optionId) {
			case ROUND -> {
				if (!BOUGHT.add(key(settlement, player))) {
					return false;
				}
				long cost = cost(player, settlement, resident);
				EconomyService eco = Hooks.economy();
				if (eco == null || !eco.charge(player, settlement, resident, cost)) {
					BOUGHT.remove(key(settlement, player));
					ANSWER.put(player.getUUID(), Component.translatable("hearth.rhythm.round.short"));
					return true;
				}
				int weight = Rhythm.config().tavern().standing();
				if (weight > 0) {
					Hooks.deed(player, settlement, "social:bought_a_round", weight, resident.id, player.blockPosition(), true);
				}
				PlotView tavern = tavernAt(settlement, player.blockPosition(), resident);
				if (tavern != null) {
					TavernNight.raiseMugs(settlement, tavern, player.getName().getString(), player.level().getGameTime());
				}
				ANSWER.put(player.getUUID(), Component.translatable("hearth.rhythm.round.done", player.getName()));
				if (BOUGHT.size() > 4096) {
					BOUGHT.clear();
				}
				return true;
			}
			case WHEN -> {
				ANSWER.put(player.getUUID(), when(settlement));
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	static @Nullable PlotView tavernAt(Settlement s, BlockPos at, Resident innkeeper) {
		PlotView best = null;
		double bd = Double.MAX_VALUE;
		for (PlotView p : RhythmSchedule.taverns(s)) {
			if (p.id() == innkeeper.work) {
				return p;
			}
			double d = Venues.middle(p.spec()).distSqr(at);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** When the next service is: today (and whether it is on now), tomorrow, or in so many days. */
	static Component when(Settlement s) {
		long today = RhythmFolk.today(s);
		RhythmData data = RhythmData.of(s.culture());
		String name = ServiceBehaviour.serviceName(data);
		String time = String.format(java.util.Locale.ROOT, "%02d:%02d", data.service().start() / 60, data.service().start() % 60);
		for (long d = today; d < today + 14; d++) {
			if (HearthServices.calendar().restDay(s, d)) {
				long in = d - today;
				if (in == 0) {
					int minute = RhythmFolk.minute(s);
					if (minute < data.service().end()) {
						return Component.translatable("hearth.rhythm.when.today", name, time);
					}
					continue;
				}
				return in == 1 ? Component.translatable("hearth.rhythm.when.tomorrow", name, time)
					: Component.translatable("hearth.rhythm.when.days", name, in, time);
			}
		}
		return Component.translatable("hearth.rhythm.when.none", name);
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return ANSWER.remove(player.getUUID());
	}
}
