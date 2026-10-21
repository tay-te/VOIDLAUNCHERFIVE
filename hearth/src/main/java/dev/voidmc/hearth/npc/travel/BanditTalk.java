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

	@Override
	public com.google.gson.JsonObject card(ServerPlayer player) {
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("kind", "bandit");
		o.addProperty("hooded", true);
		o.addProperty("state", this.band.state.name().toLowerCase(java.util.Locale.ROOT));
		var cfg = dev.voidmc.hearth.sim.roads.Roads.config().bandits();
		long toll = this.band.toll(player);
		EconomyService eco = HearthServices.economy();
		o.addProperty("demand", Math.max(0, toll));
		o.addProperty("share", cfg.toll());
		o.addProperty("carried", eco == null ? 0 : eco.balance(player));
		int size = this.band.size();
		int inSight = 0;
		int bows = 0;
		String armed = "";
		for (net.minecraft.world.entity.Mob m : this.band.men) {
			if (!m.isAlive() || m.isRemoved()) {
				continue;
			}
			if (m.distanceToSqr(player) < 32 * 32 && player.hasLineOfSight(m)) {
				inSight++;
			}
			net.minecraft.world.item.ItemStack held = m.getMainHandItem();
			if (held.getItem() instanceof net.minecraft.world.item.BowItem || held.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
				bows++;
			}
			if (m == this.band.leader && !held.isEmpty()) {
				armed = held.getHoverName().getString();
			}
		}
		o.addProperty("band", size);
		o.addProperty("inSight", inSight);
		o.addProperty("bows", bows);
		o.addProperty("armed", armed);
		o.addProperty("armour", player.getArmorValue());
		var main = player.getMainHandItem();
		o.addProperty("bladeDrawn", main.is(net.minecraft.tags.ItemTags.SWORDS) || main.is(net.minecraft.tags.ItemTags.AXES));
		o.addProperty("menace", Math.round(BanditParty.armour(player) * 100));
		com.google.gson.JsonObject outcomes = new com.google.gson.JsonObject();
		outcomes.addProperty("paid", Component.translatable("hearth.roads.bandit.paid").getString());
		outcomes.addProperty("talked", Component.translatable("hearth.roads.bandit.talked").getString());
		outcomes.addProperty("talkFailed", Component.translatable("hearth.roads.bandit.talk_failed").getString());
		outcomes.addProperty("scared", Component.translatable("hearth.roads.bandit.scared").getString());
		outcomes.addProperty("threatFailed", Component.translatable("hearth.roads.bandit.threat_failed").getString());
		o.add("outcomes", outcomes);
		return o;
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
