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

	/**
	 * "Let me see your gear" was picked (said aloud or on the Household page): the client opens the Companion gear screen
	 * (F3) where a host draws one, for the companion with this resident id. Added 2026-10-21 (docs/OVERLAY.md).
	 */
	public record OpenGear(String resident) implements CustomPacketPayload {
		public static final Type<OpenGear> TYPE = new Type<>(Hearth.id("npc/open_gear"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenGear> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.resident, 64),
			buf -> new OpenGear(buf.readUtf(64)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * A call from the Household and Companion gear pages (C2S, docs/OVERLAY.md): {@code verb} on the companion
	 * {@code resident}: an order ({@code follow}, {@code wait}, {@code distance}, {@code fight}, {@code hold}, {@code home},
	 * {@code return}), {@code gear} (open the gear screen), {@code lend} ({@code arg}: the inventory slot), {@code take}
	 * ({@code arg}: the equipment slot's name), or {@code refresh} (send the summary again). The server checks it all.
	 */
	public record Action(String verb, String resident, String arg) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Hearth.id("npc/place_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeUtf(p.verb, 32);
			buf.writeUtf(p.resident, 64);
			buf.writeUtf(p.arg, 64);
		}, buf -> new Action(buf.readUtf(32), buf.readUtf(64), buf.readUtf(64)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private PlaceSummary() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenGear.TYPE, OpenGear.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, ctx) -> act(ctx.player(), payload));
	}

	/** Tells the client to open the gear screen for a companion. */
	static void openGear(ServerPlayer player, java.util.UUID resident) {
		if (ServerPlayNetworking.canSend(player, OpenGear.TYPE)) {
			ServerPlayNetworking.send(player, new OpenGear(resident.toString()));
		}
	}

	/** Carries out a page's call (server thread): only for the player's own companions; everything re-checked. */
	static void act(ServerPlayer player, Action a) {
		if (a.verb().equals("refresh")) {
			send(player);
			return;
		}
		java.util.UUID id;
		try {
			id = java.util.UUID.fromString(a.resident());
		} catch (IllegalArgumentException e) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		Settlement s = null;
		for (CompanionRoster.Entry e : CompanionRoster.of(player).entries()) {
			if (e.resident().equals(id)) {
				s = CompanionService.settlement(server, e);
			}
		}
		Resident r = s == null ? null : s.resident(id);
		CompanionState st = r == null ? null : CompanionState.of(r);
		if (st == null || !st.player().equals(player.getUUID())) {
			return;
		}
		switch (a.verb()) {
			case "gear" -> openGear(player, id);
			case "lend" -> {
				int slot;
				try {
					slot = Integer.parseInt(a.arg());
				} catch (NumberFormatException e) {
					return;
				}
				CompanionDialogue.lend(player, s, r, slot);
			}
			case "take" -> CompanionDialogue.takeBack(player, s, r, a.arg());
			default -> {
				if (CompanionDialogue.order(player, s, r, a.verb()) != null) {
					s.markDirty();
				}
			}
		}
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
			Map<EquipmentSlot, ItemStack> worn = CompanionGear.read(st, server.registryAccess());
			JsonArray gear = new JsonArray();
			for (Map.Entry<EquipmentSlot, ItemStack> g : worn.entrySet()) {
				JsonObject o = new JsonObject();
				o.addProperty("slot", g.getKey().getSerializedName());
				o.addProperty("item", g.getValue().getHoverName().getString());
				gear.add(o);
			}
			c.add("gear", gear);
			companion(c, player, s, r, st, worn);
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
			o.addProperty("settlementId", s.id());
			o.addProperty("plot", e.plot());
			o.addProperty("servantsMax", Companions.config().household().servantsPerHouse());
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
				sv.addProperty("id", r.id.toString());
				sv.addProperty("look", lookHex(s, r));
				sv.addProperty("job", Component.translatable("hearth.npc.job." + r.job.getSerializedName()).getString());
				sv.addProperty("duty", dev.voidmc.hearth.economy.household.ServantWork.duty(r.id));
				sv.addProperty("since", st.since());
				sv.addProperty("paidDay", st.paidDay());
				sv.addProperty("today", CompanionService.day(level, s));
				sv.addProperty("unpaidLimit", Companions.config().household().unpaidDays());
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
			retinue(o, player, seat, sv);
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
					burgher(o, player, level, s);
					burgher.add(o);
				}
			}
		}
		root.add("burgherOf", burgher);
		return root;
	}

	// ---------------------------------------------------------------- 2026-10-21: the living-world screens (F2, F3, F5)

	/** A resident's look key as 16 hex digits (the client composes the portrait from it), or "" for none. */
	static String lookHex(Settlement s, Resident r) {
		long key = dev.voidmc.hearth.npc.look.ResidentLooks.key(s, r);
		return key == dev.voidmc.hearth.npc.look.ResidentLooks.NONE ? "" : String.format(java.util.Locale.ROOT, "%016x", key);
	}

	/** The Household roster's and the gear screen's facts for a companion (docs/OVERLAY.md {@code household.companions}). */
	static void companion(JsonObject c, ServerPlayer player, Settlement s, Resident r, CompanionState st, Map<EquipmentSlot, ItemStack> worn) {
		ServerLevel home = player.level().getServer().getLevel(s.dimension());
		c.addProperty("settlementId", s.id());
		c.addProperty("look", lookHex(s, r));
		JsonArray traits = new JsonArray();
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (dev.voidmc.hearth.npc.person.Trait t : dev.voidmc.hearth.npc.person.NpcData.of(r).traits()) {
			ids.add(t.getSerializedName());
		}
		java.util.Collections.sort(ids);
		ids.forEach(traits::add);
		c.add("traits", traits);
		c.addProperty("since", st.since());
		c.addProperty("paidDay", st.paidDay());
		c.addProperty("today", home == null ? 0 : CompanionService.day(home, s));
		c.addProperty("unpaidLimit", Companions.config().companions().unpaidDays());
		c.addProperty("leaveBelow", Companions.config().companions().leaveBelow());
		c.addProperty("opinion", dev.voidmc.hearth.social.Hooks.opinion(r.id, player));
		c.add("waitAt", pos(st.waitAt()));
		dev.voidmc.hearth.api.NpcService npc = HearthServices.npc();
		dev.voidmc.hearth.api.NpcService.FolkHandle h = npc == null ? null : npc.folk(r.id);
		net.minecraft.core.BlockPos at = h == null ? null : h.entity().blockPosition();
		c.add("pos", h != null && h.entity().level() == player.level() ? pos(at) : com.google.gson.JsonNull.INSTANCE);
		String where = "";
		if (h != null && h.entity().level() instanceof ServerLevel lvl) {
			HearthWorld w = HearthWorld.of(lvl);
			Settlement here = w == null ? null : w.settlementAt(at);
			where = here == null ? "" : here.name();
		}
		c.addProperty("where", where);
		c.addProperty("near", CompanionDialogue.near(player, r));
		c.addProperty("armor", h == null ? -1 : h.entity().getArmorValue());
		c.addProperty("lastLine", st.lastLine());
		c.addProperty("lastWhere", st.lastWhere());
		JsonArray slots = new JsonArray();
		for (EquipmentSlot slot : CompanionGear.SLOTS) {
			ItemStack stack = worn.getOrDefault(slot, ItemStack.EMPTY);
			JsonObject o = new JsonObject();
			o.addProperty("slot", slot.getSerializedName());
			o.addProperty("item", stack.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			o.addProperty("name", stack.isEmpty() ? "" : stack.getHoverName().getString());
			o.addProperty("count", stack.getCount());
			o.addProperty("empty", stack.isEmpty());
			slots.add(o);
		}
		c.add("slots", slots);
	}

	static com.google.gson.JsonElement pos(net.minecraft.core.BlockPos p) {
		if (p == null) {
			return com.google.gson.JsonNull.INSTANCE;
		}
		JsonObject o = new JsonObject();
		o.addProperty("x", p.getX());
		o.addProperty("y", p.getY());
		o.addProperty("z", p.getZ());
		return o;
	}

	/** The Reputation page's retinue card (F5): the lord, the ranks, what the next one asks, the rights held. */
	static void retinue(JsonObject o, ServerPlayer player, Settlement seat, Retinue.Service sv) {
		var cfg = dev.voidmc.hearth.quest.commission.Commissions.cfg();
		o.addProperty("seatId", seat.id());
		o.addProperty("lord", dev.voidmc.hearth.quest.commission.Commissions.lordName(seat));
		o.addProperty("rankId", sv.rank().getSerializedName());
		o.addProperty("standing", dev.voidmc.hearth.quest.service.Standings.standing(seat, player.getUUID()));
		o.addProperty("knightStanding", cfg.knightStanding());
		o.addProperty("needed", dev.voidmc.hearth.quest.commission.RetinueRules.needed(sv.rank(), cfg));
		o.addProperty("knighthood", cfg.knighthood());
		o.addProperty("demoted", sv.demotedFrom().isPresent());
		o.addProperty("since", sv.since());
		dev.voidmc.hearth.quest.commission.RetinueRules.Why why = dev.voidmc.hearth.quest.commission.Commissions.why(player, seat, sv);
		o.addProperty("why", why.name().toLowerCase(java.util.Locale.ROOT));
		JsonArray ranks = new JsonArray();
		for (Rank r : Rank.values()) {
			JsonObject j = new JsonObject();
			j.addProperty("id", r.getSerializedName());
			j.addProperty("name", Component.translatable(r.translationKey()).getString());
			j.addProperty("commissions", switch (r) {
				case PETITIONER -> 0;
				case RETAINER -> cfg.retainer();
				case SWORN -> cfg.sworn();
				case KNIGHT -> cfg.knight();
			});
			j.addProperty("reached", sv.rank().atLeast(r));
			j.addProperty("current", sv.rank() == r);
			ranks.add(j);
		}
		o.add("ranks", ranks);
		JsonArray rights = new JsonArray();
		if (sv.rank().atLeast(Rank.RETAINER)) {
			rights.add("lodging");
			rights.add("toll_free");
		}
		if (sv.horse()) {
			rights.add("horse");
		}
		if (sv.rank().atLeast(Rank.SWORN)) {
			rights.add("colours");
		}
		if (sv.house()) {
			rights.add("house");
		}
		o.add("rights", rights);
	}

	/** A sworn burgher's town (F5): the rights, and the council's sitting with the works on its table. */
	static void burgher(JsonObject o, ServerPlayer player, ServerLevel level, Settlement s) {
		var cfg = Companions.config().burghers();
		var book = s.data(Burghers.KEY);
		HearthWorld w = HearthWorld.of(level);
		long today = w == null ? 0 : w.day();
		o.addProperty("settlementId", s.id());
		o.addProperty("today", today);
		o.addProperty("dues", cfg.dues());
		JsonArray rights = new JsonArray();
		// as the reeve tells it (hearth.burgher.talk.rights_text)
		for (String r : new String[] {"dues", "arms", "feast", "mass", "council"}) {
			rights.add(r);
		}
		o.add("rights", rights);
		JsonObject council = new JsonObject();
		council.addProperty("sitting", book.sitting != Long.MIN_VALUE);
		council.addProperty("lastSitting", book.lastSitting == Long.MIN_VALUE ? -1 : book.lastSitting);
		// the earliest day it may sit again (it sits on the first rest day from then)
		council.addProperty("dueFrom", book.lastSitting == Long.MIN_VALUE ? today : book.lastSitting + cfg.councilDays());
		Integer mine = book.votes.get(player.getUUID());
		JsonArray table = new JsonArray();
		for (int plot : book.table) {
			PlotView p = s.plot(plot);
			JsonObject t = new JsonObject();
			t.addProperty("plot", plot);
			t.addProperty("name", p == null ? "" : PropertyNames.lower(p.spec().archetype().getPath()).getString());
			int votes = 0;
			for (int v : book.votes.values()) {
				if (v == plot) {
					votes++;
				}
			}
			t.addProperty("votes", votes);
			t.addProperty("yours", mine != null && mine == plot);
			table.add(t);
		}
		council.add("table", table);
		PlotView chosen = book.chosen < 0 ? null : s.plot(book.chosen);
		council.addProperty("lastChoice", chosen == null ? "" : PropertyNames.lower(chosen.spec().archetype().getPath()).getString());
		o.add("council", council);
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
