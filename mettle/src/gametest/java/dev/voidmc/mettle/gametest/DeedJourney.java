package dev.voidmc.mettle.gametest;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.voidmc.mettle.character.cards.BuildFeel;
import dev.voidmc.mettle.character.cards.CardConfig;
import dev.voidmc.mettle.character.cards.CardLedger;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.Deeds;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Deed Mythics, one of a kind (docs/BUILD_FEEL.md §12): Bree (a stand-in player) walks Long Walker first and holds its
 * card; the player does it after her and gets 10 shards and the title, never a card, and nothing twice; the title in the
 * overlay and worn. Then a world with two Unclad copies from the old numbered rules migrates to one: the earliest stays,
 * the other is released and logged, its holder gets 30 shards and the title on joining, and a second run changes
 * nothing. Put back after.
 */
final class DeedJourney {
	private static final Identifier WALKER = Identifier.fromNamespaceAndPath("mettle", "long_walker");
	private static final Identifier UNCLAD = Identifier.fromNamespaceAndPath("mettle", "unclad");
	private static FakePlayer bree;

	private DeedJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		BuildState[] saved = new BuildState[1];
		server.runCommand("gamemode survival @a");

		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerPlayer b = bree(s, p);
			Cards.ensure(p);
			saved[0] = BuildFeel.get(p);
			Cards.clear(p);
			Cards.clear(b);
			Cards.ledger(s).unclaim("long_walker");
			Cards.ledger(s).unclaim("unclad");
			BuildFeel.set(p, saved[0].withTitles(BuildState.Titles.NONE).withShards(0).withoutOnce("shard/deed"));
			// Bree is first: the card is hers, claimed in the Hall like a world first.
			if (!Mythics.deed(b, "long_walker")) {
				return "Bree's Long Walker was not granted";
			}
			Optional<CardData.Holder> holder = Cards.ledger(s).first("long_walker");
			if (holder.isEmpty() || !holder.get().player().equals(b.getUUID()) || CardStore.get(b).cards().stream().noneMatch(c -> c.mythicId().map(WALKER::equals).orElse(false))) {
				return "the Hall's Long Walker holder: " + holder;
			}
			// The player after her: 10 shards and the title, never a card.
			int cards = CardStore.get(p).cards().size();
			if (!Mythics.deed(p, "long_walker")) {
				return "the later achiever got nothing";
			}
			BuildState after = BuildFeel.get(p);
			if (CardStore.get(p).cards().size() != cards || after.shards() != CardConfig.get().deed_later_shards || !after.titles().has(WALKER)
				|| !after.titles().worn().equals(Optional.of(WALKER))) {
				return "the later achiever: cards " + CardStore.get(p).cards().size() + " (was " + cards + "), shards " + after.shards() + ", titles " + after.titles();
			}
			if (!Cards.ledger(s).deeds("long_walker").equals(List.of(b.getUUID(), p.getUUID()))) {
				return "the deed's list: " + Cards.ledger(s).deeds("long_walker");
			}
			// Nothing twice.
			if (Mythics.deed(p, "long_walker") || BuildFeel.get(p).shards() != CardConfig.get().deed_later_shards) {
				return "the deed paid twice";
			}
			return CardStore.get(b).cards().stream().filter(c -> c.mythicId().map(WALKER::equals).orElse(false)).count() == 1
				&& Cards.ledger(s).all().stream().filter(c -> c.mythicId().map(WALKER::equals).orElse(false) && c.place().kind() != CardData.PlaceKind.RELEASED).count() == 1
				? null : "Long Walker exists more than once";
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			JsonObject titles = MettleState.build(mc).getAsJsonObject("cards").getAsJsonObject("build").getAsJsonObject("titles");
			return titles.get("worn").getAsString().equals(WALKER.toString()) && "Long Walker".equals(titles.get("wornText").getAsString())
				&& titles.getAsJsonArray("held").size() == 1 ? null : "the overlay's titles: " + titles;
		}));
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			if (Deeds.wear(p, Optional.of(WALKER), true).isPresent() || !BuildFeel.get(p).titles().instead()) {
				return "wearing the title instead of the byname";
			}
			var renown = p.getAttached(BuildFeel.RENOWN);
			return renown != null && renown.title().equals(Optional.of(WALKER)) && renown.titleInstead() ? null : "the public renown: " + renown;
		}));

		// A world from before: two numbered Unclad copies, the player's the earlier (copy 1), Bree's the second.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerPlayer b = bree(s, p);
			CardLedger ledger = Cards.ledger(s);
			CardData mine = Mythics.grant(p, UNCLAD, true).orElseThrow();
			CardData hers = Mythics.grant(b, UNCLAD, true).orElseThrow();
			ledger.unclaim("unclad");
			ledger.deed("unclad", p.getUUID());
			ledger.deed("unclad", b.getUUID());
			int shards = BuildFeel.get(b).shards();
			int logged = ledger.returns().size();
			callMigrate(s);
			if (ledger.first("unclad").map(h -> !h.player().equals(p.getUUID())).orElse(true)) {
				return "the Hall after the migration: " + ledger.first("unclad");
			}
			if (!ledger.released(hers.id()) || ledger.released(mine.id())) {
				return "the migration kept the wrong copy: mine released " + ledger.released(mine.id()) + ", hers " + ledger.released(hers.id());
			}
			if (ledger.returns().size() != logged + 1 || ledger.returns().getLast().card() != hers.id() || ledger.returns().getLast().settled()) {
				return "the return log: " + ledger.returns();
			}
			// Idempotent: a second start changes nothing.
			callMigrate(s);
			if (ledger.returns().size() != logged + 1) {
				return "a second migration logged again";
			}
			// Her next join settles it: 30 shards and the title, once.
			callSettle(b);
			callSettle(b);
			BuildState bs = BuildFeel.get(b);
			if (bs.shards() != shards + CardConfig.get().deed_migration_shards || !bs.titles().has(UNCLAD) || !ledger.returns().getLast().settled()) {
				return "the returned copy's holder: shards " + bs.shards() + " (was " + shards + "), titles " + bs.titles();
			}
			return CardStore.get(b).cards().stream().noneMatch(c -> c.id() == hers.id()) ? null : "the returned copy is still in her binder";
		}));

		// Put back.
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerPlayer b = bree(s, p);
			Cards.clear(p);
			Cards.clear(b);
			Cards.ledger(s).unclaim("long_walker");
			Cards.ledger(s).unclaim("unclad");
			BuildFeel.set(p, saved[0]);
			BuildFeel.set(b, BuildState.EMPTY);
			return null;
		}));
		context.waitTicks(3);
	}

	/** The start-up migration (package-private in Deeds; the journey runs it again as a restart would). */
	private static void callMigrate(MinecraftServer s) {
		try {
			var m = Deeds.class.getDeclaredMethod("migrate", MinecraftServer.class);
			m.setAccessible(true);
			m.invoke(null, s);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void callSettle(ServerPlayer p) {
		try {
			var m = Deeds.class.getDeclaredMethod("settle", ServerPlayer.class);
			m.setAccessible(true);
			m.invoke(null, p);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static ServerPlayer bree(MinecraftServer s, ServerPlayer near) {
		if (bree == null || bree.isRemoved() || !bree.isAlive()) {
			bree = FakePlayer.get(s.overworld(), new GameProfile(UUID.fromString("6d657474-6c65-4465-8564-62726565aaaa"), "Bree"));
		}
		bree.snapTo(near.getX() + 2, near.getY(), near.getZ(), 0, 0);
		return bree;
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
