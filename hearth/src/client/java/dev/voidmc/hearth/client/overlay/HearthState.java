package dev.voidmc.hearth.client.overlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.social.dice.DiceMenu;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The view model a host renders Hearth's screens from (docs/OVERLAY.md): everything the designed screens show (Figma
 * section 651:37945), as one JSON tree with text already translated and coin as crowns, marks and bits. Built from what
 * the server last sent ({@link HearthViews}, the open Hearth menu, the synced quests) and never decides anything; the
 * calls go back through {@link HearthActions}. Hosts reach it by reflection ({@code build()}, {@code revision()}), so
 * neither mod needs the other. Client thread.
 */
public final class HearthState {
	/** Bumped when the schema changes in a way a host must notice (fields are only ever added within a version). */
	public static final int VERSION = 1;

	private HearthState() {
	}

	// ---------------------------------------------------------------- the change counter

	private static final RevisionCounter COUNTER = new RevisionCounter();

	/**
	 * A cheap change counter (docs/OVERLAY.md §revision): it goes up whenever anything {@link #build} outputs may have
	 * changed, so a host checks it once a tick and rebuilds only then. It looks at the inputs, not the output: every Hearth
	 * payload ({@link HearthViews#version}), the open menu and its view, the synced quests, the inventory's change count
	 * (the purse), the client options, the hosted screen, and, while a quest is tracked, the player's block and facing (the
	 * compass) and the in-game hour (time left); once a second while a sentence counts down. Never goes down within a
	 * session.
	 */
	public static long revision() {
		return COUNTER.next(fingerprint(Minecraft.getInstance()));
	}

	private static long mix(long h, long v) {
		return RevisionCounter.mix(h, v);
	}

	private static long fingerprint(Minecraft mc) {
		long h = mix(17, HearthViews.version());
		h = mix(h, HearthClientOptions.revision());
		h = mix(h, System.identityHashCode(HearthScreens.hosted()));
		LocalPlayer player = mc.player;
		h = mix(h, System.identityHashCode(player));
		if (player == null || mc.level == null) {
			return h;
		}
		h = mix(h, System.identityHashCode(mc.level));
		AbstractContainerMenu menu = player.containerMenu;
		h = mix(h, System.identityHashCode(menu));
		if (menu instanceof MarketMenu m) {
			h = mix(h, System.identityHashCode(m.view()));
		} else if (menu instanceof BankMenu b) {
			h = mix(h, System.identityHashCode(b.view()));
		} else if (menu instanceof DiceMenu d) {
			h = mix(h, System.identityHashCode(d.view()));
		}
		PlayerQuests pq = QuestStore.get(player);
		h = mix(h, System.identityHashCode(pq));
		h = mix(h, player.getInventory().getTimesChanged());
		if (pq.tracked != null) {
			BlockPos p = player.blockPosition();
			h = mix(h, p.asLong());
			h = mix(h, Math.round(player.getYRot() / 5));
			h = mix(h, mc.level.getGameTime() / 1000);
		}
		if (HearthViews.status().jailTicks() > 0) {
			h = mix(h, Util.getMillis() / 1000);
		}
		return h;
	}

	// ---------------------------------------------------------------- the tree

	/** The whole state (docs/OVERLAY.md §state). Cheap enough to call when {@link #revision} changed. */
	public static JsonObject build() {
		Minecraft mc = Minecraft.getInstance();
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		LocalPlayer player = mc.player;
		boolean online = player != null && mc.level != null;
		root.addProperty("phase", online ? "online" : "offline");
		root.addProperty("hosted", HearthScreens.hosted());
		JsonObject settings = new JsonObject();
		settings.addProperty("conversationStyle", HearthClientOptions.conversationStyle());
		root.add("settings", settings);
		if (!online) {
			for (String k : new String[] {"player", "talk", "journal", "questHud", "board", "market", "bank", "confront", "lockpick", "pickpocket", "dice",
				"reputation", "law"}) {
				root.add(k, JsonNull.INSTANCE);
			}
			return root;
		}
		HearthStateJson.Lookups look = new HearthStateJson.Lookups(Portraits::ofEntity, Portraits::keyOf, target -> carried(player, target));
		long gameTime = mc.level.getGameTime();
		BlockPos from = player.blockPosition();

		JsonObject me = new JsonObject();
		me.add("purse", HearthStateJson.coins(PlayerPurse.balance(player)));
		StandingPayloads.Here here = HearthViews.here();
		if (here != null) {
			JsonObject town = new JsonObject();
			town.addProperty("id", here.id());
			town.addProperty("name", here.town());
			town.addProperty("standing", here.standing());
			town.addProperty("title", here.title());
			town.addProperty("titleName", HearthStateJson.titleName(here.title()));
			town.addProperty("fame", here.fame());
			town.addProperty("renown", here.renown());
			me.add("town", town);
		} else {
			me.add("town", JsonNull.INSTANCE);
		}
		root.add("player", me);

		var talk = HearthViews.talk();
		root.add("talk", talk == null ? JsonNull.INSTANCE : HearthStateJson.talk(talk, HearthViews.talkDetail(), HearthViews.talkStanding(), look));

		PlayerQuests pq = QuestStore.get(player);
		root.add("journal", HearthStateJson.journal(pq, gameTime, from, look));
		root.add("questHud", HearthStateJson.questHud(pq, gameTime, from, player.getYRot(), look));
		var board = HearthViews.board();
		root.add("board", board == null ? JsonNull.INSTANCE : HearthStateJson.board(board, pq, gameTime, from, look));

		AbstractContainerMenu menu = player.containerMenu;
		root.add("market", menu instanceof MarketMenu m ? HearthStateJson.market(m.containerId, m.view(), look) : JsonNull.INSTANCE);
		root.add("bank", menu instanceof BankMenu b ? HearthStateJson.bank(b.containerId, b.view()) : JsonNull.INSTANCE);
		root.add("dice", menu instanceof DiceMenu d ? HearthStateJson.dice(d.containerId, d.view()) : JsonNull.INSTANCE);

		var confront = HearthViews.confront();
		root.add("confront", confront == null ? JsonNull.INSTANCE : HearthStateJson.confront(confront, HearthViews.confrontDetail(), look));
		var lockpick = HearthViews.lockpick();
		root.add("lockpick", lockpick == null ? JsonNull.INSTANCE : HearthStateJson.lockpick(lockpick));
		var pocket = HearthViews.pocket();
		root.add("pickpocket", pocket == null ? JsonNull.INSTANCE : HearthStateJson.pickpocket(pocket));
		var page = HearthViews.reputation();
		root.add("reputation", page == null ? JsonNull.INSTANCE : HearthStateJson.reputation(page));
		root.add("law", HearthStateJson.law(HearthViews.hint(), HearthViews.status(), Util.getMillis() - HearthViews.statusAt(), HearthViews.stealth()));
		return root;
	}

	/** {@link #build} as a JSON string, for hosts that pass text. */
	public static String buildJson() {
		JsonElement e = build();
		return e.toString();
	}

	/** How many of an item ({@code minecraft:spruce_log}) or tag ({@code #minecraft:logs}) the player carries. */
	static int carried(LocalPlayer player, @Nullable String target) {
		if (target == null || target.isEmpty()) {
			return 0;
		}
		boolean tag = target.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? target.substring(1) : target);
		if (id == null) {
			return 0;
		}
		TagKey<Item> key = tag ? TagKey.create(net.minecraft.core.registries.Registries.ITEM, id) : null;
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && (tag ? stack.is(key) : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id))) {
				n += stack.getCount();
			}
		}
		return n;
	}
}
