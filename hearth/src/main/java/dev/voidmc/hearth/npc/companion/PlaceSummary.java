package dev.voidmc.hearth.npc.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.household.HouseBook;
import dev.voidmc.hearth.economy.household.Household;
import dev.voidmc.hearth.economy.household.HouseholdIndex;
import dev.voidmc.hearth.economy.household.ServantState;
import dev.voidmc.hearth.economy.property.PropertyNames;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.quest.commission.Rank;
import dev.voidmc.hearth.quest.commission.Retinue;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import dev.voidmc.hearth.social.burgher.Burghers;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The player's place at a glance, for the Household page (docs/LIVING_WORLD_2.md §10 F2, and F5's retinue ranks and burgher
 * status): their companions and their orders, gear and wages; their houses, servants, wages and larder; their rank with
 * each lord; the towns where they are burghers. Until the page is designed in Figma this goes to the client as the hub
 * state's {@code household} section (an addition hosts that do not know it ignore); the conversations stay the way to act.
 * Sent at login, when something changes, and every minute while the player has any of it. Server builds; text in the
 * server's language.
 */
public final class PlaceSummary {
	public record Payload(String json) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = new Type<>(Hearth.id("npc/place_summary"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.json, 1 << 18),
			buf -> new Payload(buf.readUtf(1 << 18)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private PlaceSummary() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
	}

	/** Sends the player their summary (if their client can take it). */
	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Payload.TYPE)) {
			ServerPlayNetworking.send(player, new Payload(build(player).toString()));
		}
	}

	/** Whether the player has anything to show (the minute's refresh skips those who have none). */
	static boolean any(ServerPlayer player) {
		return CompanionRoster.of(player).size() > 0 || !HouseholdIndex.of(player).houses().isEmpty() || !Retinue.of(player).services().isEmpty();
	}

	public static JsonObject build(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		EconomyService eco = HearthServices.economy();
		JsonObject root = new JsonObject();
		JsonArray companions = new JsonArray();
		for (CompanionRoster.Entry e : CompanionRoster.of(player).entries()) {
			Settlement s = CompanionService.settlement(server, e);
			Resident r = s == null ? null : s.resident(e.resident());
			CompanionState st = r == null ? null : CompanionState.of(r);
			if (st == null) {
				continue;
			}
			JsonObject c = new JsonObject();
			c.addProperty("id", r.id.toString());
			c.addProperty("name", r.fullName());
			c.addProperty("job", Component.translatable("hearth.npc.job." + r.job.getSerializedName()).getString());
			c.addProperty("home", s.name());
			c.addProperty("wage", st.wage());
			c.addProperty("wageText", eco == null ? st.wage() + "" : eco.format(st.wage()).getString());
			c.addProperty("unpaidDays", st.unpaid());
			c.addProperty("orders", st.follow().id());
			c.addProperty("fight", st.fight());
			c.addProperty("loyalty", st.loyalty());
			c.addProperty("embodied", dev.voidmc.hearth.npc.NpcModule.embodiment().entityOf(r.id) != null);
			JsonArray gear = new JsonArray();
			for (Map.Entry<EquipmentSlot, ItemStack> g : CompanionGear.read(st, server.registryAccess()).entrySet()) {
				JsonObject o = new JsonObject();
				o.addProperty("slot", g.getKey().getSerializedName());
				o.addProperty("item", g.getValue().getHoverName().getString());
				gear.add(o);
			}
			c.add("gear", gear);
			companions.add(c);
		}
		root.add("companions", companions);
		root.addProperty("companionsMax", Companions.config().companions().perPlayer());

		JsonArray houses = new JsonArray();
		for (HouseholdIndex.Entry e : HouseholdIndex.of(player).houses()) {
			ServerLevel level = server.getLevel(e.dimension());
			Settlement s = level == null ? null : SettlementSources.find(level, e.settlementId());
			PlotView plot = s == null ? null : s.plot(e.plot());
			HouseBook.House h = s == null ? null : s.data(Household.HOUSES).house(e.plot());
			if (plot == null || h == null || !player.getUUID().equals(plot.owner())) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("town", s.name());
			o.addProperty("building", PropertyNames.lower(plot.spec().archetype().getPath()).getString());
			o.addProperty("larder", h.stocked + h.pending);
			o.addProperty("larderSize", Companions.config().household().larder());
			o.addProperty("larderShort", h.larderShort);
			JsonArray servants = new JsonArray();
			for (java.util.UUID id : h.servants) {
				Resident r = s.resident(id);
				ServantState st = r == null ? null : ServantState.of(r);
				if (st == null) {
					continue;
				}
				JsonObject sv = new JsonObject();
				sv.addProperty("name", r.fullName());
				sv.addProperty("wage", st.wage());
				sv.addProperty("wageText", eco == null ? st.wage() + "" : eco.format(st.wage()).getString());
				sv.addProperty("unpaidDays", st.unpaid());
				servants.add(sv);
			}
			o.add("servants", servants);
			houses.add(o);
		}
		root.add("houses", houses);

		JsonArray retinue = new JsonArray();
		for (Map.Entry<Long, Retinue.Service> e : Retinue.of(player).services().entrySet()) {
			Settlement seat = QuestWorlds.byId(server, e.getKey());
			if (seat == null) {
				continue;
			}
			Retinue.Service sv = e.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("seat", seat.name());
			o.addProperty("rank", sv.rank().getSerializedName());
			o.addProperty("rankName", Component.translatable(sv.rank().translationKey()).getString());
			o.addProperty("commissions", sv.done());
			o.addProperty("feat", sv.feat());
			Rank next = sv.rank().next();
			o.addProperty("next", next == null ? "" : Component.translatable(next.translationKey()).getString());
			retinue.add(o);
		}
		root.add("retinue", retinue);

		JsonArray burgher = new JsonArray();
		for (ServerLevel level : server.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			if (w == null) {
				continue;
			}
			for (Settlement s : w.settlements()) {
				if (owns(s, player.getUUID()) && Burghers.sworn(s, player.getUUID())) {
					JsonObject o = new JsonObject();
					o.addProperty("town", s.name());
					o.addProperty("since", s.data(Burghers.KEY).sworn.get(player.getUUID()));
					burgher.add(o);
				}
			}
		}
		root.add("burgherOf", burgher);
		return root;
	}

	private static boolean owns(Settlement s, java.util.UUID player) {
		for (PlotView p : s.plots()) {
			if (player.equals(p.owner())) {
				return true;
			}
		}
		return false;
	}
}
