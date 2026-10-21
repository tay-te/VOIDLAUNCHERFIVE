package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Mythic;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.CardData;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Deed Mythics, one of a kind, on the server (docs/BUILD_FEEL.md §12): the first player to do a deed holds its card;
 * everyone after them gets {@code deed_later_shards} shards and the deed's title, once. The one-time migration of the
 * numbered copies the old rules made runs at every start (it finds nothing to do once done): the earliest copy of each
 * deed stays, every other copy is released and logged in the ledger, and its holder gets {@code deed_migration_shards}
 * shards and the title on their next join. Ledger first, then the player's file, as every card change that matters.
 */
public final class Deeds {
	private Deeds() {
	}

	static void init() {
		ServerPlayerEvents.JOIN.register(Deeds::settle);
	}

	/** Every deed Mythic's id path ({@code long_walker}). */
	static Set<String> ids(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(MettleRegistries.MYTHIC).listElements().filter(r -> r.value().source() == Mythic.Source.DEED)
			.map(r -> r.key().identifier().getPath()).collect(Collectors.toSet());
	}

	/** A player did a deed: the card for the first, shards and the title for the rest, nothing twice. */
	static boolean done(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		CardLedger ledger = Cards.ledger(server);
		DeedRules.Outcome outcome = DeedRules.outcome(ledger.first(id).isPresent(), ledger.deeds(id).contains(player.getUUID()));
		return switch (outcome) {
			case DONE -> false;
			case CARD -> Mythics.grant(player, Mettle.id(id), false).isPresent();
			case LATER -> later(player, Mettle.id(id));
		};
	}

	/** Someone holds the deed's card already: this player is on its list now, with shards and the title. */
	private static boolean later(ServerPlayer player, Identifier deed) {
		MinecraftServer server = player.level().getServer();
		CardLedger ledger = Cards.ledger(server);
		Cards.ensure(player);
		ledger.deed(deed.getPath(), player.getUUID());
		BuildFeel.addShards(player, CardConfig.get().deed_later_shards, "shard/deed/" + deed.getPath());
		giveTitle(player, deed);
		Component name = Mythics.def(server.registryAccess(), deed).map(Mythic::name).orElse(Component.literal(deed.getPath()));
		String holder = ledger.first(deed.getPath()).map(CardData.Holder::name).orElse("?");
		player.sendSystemMessage(Component.translatable("mettle.deed.later", name.copy().withStyle(ChatFormatting.GOLD), holder, title(deed),
			CardConfig.get().deed_later_shards));
		LedgerFile.commit(server, player);
		Mettle.LOGGER.info("{} did the deed {} after {} holds it: {} shards and the title", player.getName().getString(), deed, holder,
			CardConfig.get().deed_later_shards);
		return true;
	}

	/** The deed's title, kept on the character (the first one earned is worn at once). */
	static void giveTitle(ServerPlayer player, Identifier deed) {
		BuildState b = BuildFeel.get(player);
		BuildState.Titles next = b.titles().with(deed);
		if (!next.equals(b.titles())) {
			BuildFeel.set(player, b.withTitles(next));
			BuildFeel.publish(player);
		}
	}

	/** A deed title's words: "Long Walker". */
	public static Component title(Identifier deed) {
		return Component.translatable("mettle.title." + deed.getPath());
	}

	/** Wears a held title (empty: none), beside the byname or instead of it. */
	public static Optional<CardRules.Refusal> wear(ServerPlayer player, Optional<Identifier> deed, boolean instead) {
		BuildState b = BuildFeel.get(player);
		if (deed.isPresent() && !b.titles().has(deed.get())) {
			return Optional.of(CardRules.Refusal.NO_TITLE);
		}
		BuildFeel.set(player, b.withTitles(b.titles().wearing(deed, instead)));
		BuildFeel.publish(player);
		SignaturePerks.feedback(player, Mettle.id("build/title"), deed.map(d -> Component.translatable("mettle.deed.wearing", title(d)))
			.orElse(Component.translatable("mettle.deed.wearing_none")));
		return Optional.empty();
	}

	// ---------------------------------------------------------------- the migration (§12, once)

	/** At start: each deed with several copies keeps its earliest; the rest are returned and logged. Idempotent. */
	static void migrate(MinecraftServer server) {
		CardLedger ledger = Cards.ledger(server);
		DeedRules.Plan plan = DeedRules.migrate(ledger.all(), ids(server), ledger::deeds);
		boolean changed = false;
		for (var e : plan.keep().entrySet()) {
			if (ledger.first(e.getKey()).isEmpty()) {
				ledger.claim(e.getKey(), DeedRules.holder(e.getValue()));
				changed = true;
			}
		}
		long day = Cards.day(server);
		for (CardData c : plan.returned()) {
			String deed = c.mythicId().orElseThrow().getPath();
			String name = c.history().last().isEmpty() ? c.drawn().name() : c.history().last().getLast().name();
			ledger.update(c.id(), x -> x.withPlace(CardData.Place.RELEASED));
			ledger.addReturn(new CardLedger.Returned(c.id(), deed, c.owner(), name, day, false));
			Mettle.LOGGER.warn("Mettle: deed Mythics are one of a kind now: {}'s copy of {} (card {}) was returned; {} keeps card {} (the first to do it). "
				+ "{} gets {} shards and the title on their next join", name, deed, c.id(), plan.keep().get(deed).drawn().name(), plan.keep().get(deed).id(), name,
				CardConfig.get().deed_migration_shards);
			changed = true;
		}
		if (changed) {
			LedgerFile.save(server);
			Mettle.LOGGER.info("Mettle: deed Mythics are one of a kind: {} deeds kept their first copy, {} copies returned", plan.keep().size(),
				plan.returned().size());
		}
	}

	/** On join: a returned copy's holder gets the shards and the title, once, told why. */
	static void settle(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CardLedger ledger = Cards.ledger(server);
		List<CardLedger.Returned> mine = ledger.returns().stream().filter(r -> !r.settled() && r.player().equals(player.getUUID())).toList();
		if (mine.isEmpty()) {
			return;
		}
		Cards.ensure(player);
		for (CardLedger.Returned r : mine) {
			Identifier deed = Mettle.id(r.deed());
			BuildFeel.addShards(player, CardConfig.get().deed_migration_shards, "shard/deed_returned/" + r.card());
			giveTitle(player, deed);
			ledger.settleReturn(r.card());
			Component name = Mythics.def(server.registryAccess(), deed).map(Mythic::name).orElse(Component.literal(r.deed()));
			String holder = ledger.first(r.deed()).map(CardData.Holder::name).orElse("?");
			player.sendSystemMessage(Component.translatable("mettle.deed.returned", name.copy().withStyle(ChatFormatting.GOLD), holder,
				CardConfig.get().deed_migration_shards, title(deed)));
			Mettle.LOGGER.info("{}: returned deed copy {} ({}) settled: {} shards and the title", player.getName().getString(), r.card(), r.deed(),
				CardConfig.get().deed_migration_shards);
		}
		Cards.sync(player);
		LedgerFile.commit(server, player);
	}
}
