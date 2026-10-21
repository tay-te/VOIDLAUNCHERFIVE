package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.sim.roads.Traffic;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The bandits' demand (docs/LIVING_WORLD_2.md §1.3): "Your purse, or your life." Pay a share of the coin carried and be
 * let by (they remember you paid); talk your way past or frighten them off (speech checks, menace and armour weighing
 * heavily on the second); or draw your blade. Walking off is an answer too.
 */
final class BanditTalk implements StrangerTalk {
	private final BanditParty band;
	private final Traffic traffic;

	BanditTalk(BanditParty band, Traffic traffic) {
		this.band = band;
		this.traffic = traffic;
	}

	@Override
	public String name() {
		return Component.translatable("hearth.roads.who.bandit").getString();
	}

	@Override
	public String title() {
		return "hearth.npc.job.bandit";
	}

	@Override
	public String from() {
		return Component.translatable("hearth.roads.bandit.from").getString();
	}

	@Override
	public Component greeting(ServerPlayer player) {
		return Component.translatable("hearth.roads.bandit.demand." + (int) Math.floorMod(this.band.bandId + player.getId(), 4));
	}

	@Override
	public List<Option> options(ServerPlayer player) {
		List<Option> out = new ArrayList<>();
		if (this.band.state != BanditParty.State.DEMAND) {
			out.add(new Option("bye", Component.translatable("hearth.roads.bandit.go"), false, null, "leave", -1, -1));
			return out;
		}
		long toll = this.band.toll(player);
		EconomyService eco = HearthServices.economy();
		Component coins = eco == null ? Component.literal(toll + " bits") : eco.format(toll);
		boolean can = eco != null && toll > 0 && eco.balance(player) >= toll;
		out.add(new Option("pay", Component.translatable("hearth.roads.bandit.pay", coins), !can,
			can ? null : Component.translatable(toll <= 0 ? "hearth.roads.bandit.pay.none" : "hearth.roads.bandit.pay.short"), "bribe", -1, toll));
		out.add(new Option("talk", Component.translatable("hearth.roads.bandit.talk"), false, null, "persuade", (int) Math.round(this.band.persuadeOdds(player) * 100), -1));
		out.add(new Option("threaten", Component.translatable("hearth.roads.bandit.threaten"), false, null, "intimidate",
			(int) Math.round(this.band.intimidateOdds(player) * 100), -1));
		out.add(new Option("fight", Component.translatable("hearth.roads.bandit.fight"), false, null, "fight", -1, -1));
		out.add(new Option("run", Component.translatable("hearth.roads.bandit.run"), false, null, "leave", -1, -1));
		return out;
	}

	/** They set on the player: the last word goes to the chat and the conversation closes. */
	private @Nullable Component attacked(ServerPlayer player, String line) {
		if (this.band.leader != null) {
			BanditParty.say((net.minecraft.server.level.ServerLevel) player.level(), this.band.leader, Component.translatable(line));
		}
		return null;
	}

	@Override
	public @Nullable Component choose(ServerPlayer player, String option) {
		long now = player.level().getGameTime();
		if (this.band.state != BanditParty.State.DEMAND) {
			return null;
		}
		switch (option) {
			case "pay" -> {
				EconomyService eco = HearthServices.economy();
				long toll = this.band.toll(player);
				if (eco != null && eco.charge(player, toll)) {
					this.band.letBy(player, now);
					return Component.translatable("hearth.roads.bandit.paid");
				}
				this.band.fight(player);
				return attacked(player, "hearth.roads.bandit.cant_pay");
			}
			case "talk" -> {
				if (player.getRandom().nextDouble() < this.band.persuadeOdds(player)) {
					this.band.letBy(player, now);
					return Component.translatable("hearth.roads.bandit.talked");
				}
				this.band.fight(player);
				return attacked(player, "hearth.roads.bandit.talk_failed");
			}
			case "threaten" -> {
				if (player.getRandom().nextDouble() < this.band.intimidateOdds(player)) {
					this.band.flee((net.minecraft.server.level.ServerLevel) player.level(), this.traffic, now);
					return Component.translatable("hearth.roads.bandit.scared");
				}
				this.band.fight(player);
				return attacked(player, "hearth.roads.bandit.threat_failed");
			}
			default -> {
				// fight, run, or anything else: they take it as an answer
				this.band.fight(player);
				return null;
			}
		}
	}
}
