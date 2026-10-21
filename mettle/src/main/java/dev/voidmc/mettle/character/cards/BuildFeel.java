package dev.voidmc.mettle.character.cards;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.PerkText;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.character.moment.MomentKind;
import dev.voidmc.mettle.character.moment.Moments;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.BuildState.Byname;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Build feel on the server (docs/BUILD_FEEL.md): the Focus, boons (Temper, Inscribe, Reroll), Signatures, bynames,
 * milestones, Mythic shards and the forge, and the guide for new characters. Everything is decided here on the server
 * thread; the client only asks ({@link MettlePayloads.BuildAction}) and reads its synced card cache. Changes to cards go
 * through the world ledger and are committed with the player's file at once ({@link LedgerFile#commit}), as every other
 * card change that matters.
 */
public final class BuildFeel {
	/**
	 * What everyone may see of a player's build (name cards, chat hover, Hearth): the byname worn, the legend title's stars
	 * and the world it names, and the crest (the perk or Mythic of skill1's Signature). Synced to all; rebuilt, not saved.
	 */
	public record Renown(Optional<Byname> byname, int legend, String legendOf, Optional<Identifier> crest) {
		public static final Codec<Renown> CODEC = RecordCodecBuilder.create(i -> i.group(
			Byname.CODEC.optionalFieldOf("byname").forGetter(Renown::byname),
			Codec.INT.optionalFieldOf("legend", 0).forGetter(Renown::legend),
			Codec.STRING.optionalFieldOf("legend_of", "").forGetter(Renown::legendOf),
			Identifier.CODEC.optionalFieldOf("crest").forGetter(Renown::crest)
		).apply(i, Renown::new));
	}

	public static final AttachmentType<Renown> RENOWN = AttachmentRegistry.create(Mettle.id("character/renown"), b -> b
		.copyOnDeath()
		.syncWith(ByteBufCodecs.fromCodecTrusted(Renown.CODEC), AttachmentSyncPredicate.all()));

	/** When each player was last hurt (game time), for the guide's calm and the milestones' quiet. */
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	private BuildFeel() {
	}

	static void init() {
		RENOWN.toString();
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.BuildAction.TYPE, (payload, context) -> act(context.player(), payload));
		ServerPlayerEvents.JOIN.register(BuildFeel::join);
		ServerPlayerEvents.LEAVE.register(p -> LAST_HURT.remove(p.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register(dev.voidmc.mettle.core.CoreModule.AFTER_ATTACHMENTS, (oldPlayer, newPlayer, alive) -> publish(newPlayer));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0) {
				LAST_HURT.put(p.getUUID(), p.level().getGameTime());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				server.getPlayerList().getPlayers().forEach(BuildFeel::guideTick);
			}
		});
		MettleEvents.SKILL_LEVEL_UP.register((player, skill, from, to) -> {
			if (from < 20 && to >= 20) {
				addShards(player, CardConfig.get().shard_skill_20, "shard/skill20/" + skill.identifier());
			}
		});
		MettleEvents.PERK_LEARNED.register((player, perk, rank) -> Perks.kind(player, perk).ifPresent(kind -> {
			if (kind == Perk.Kind.FEAT) {
				addShards(player, CardConfig.get().shard_feat, "shard/perk/" + perk.identifier());
			} else if (kind == Perk.Kind.SYNERGY) {
				addShards(player, CardConfig.get().shard_synergy, "shard/perk/" + perk.identifier());
			}
		}));
	}

	/** Perk kinds by key (a tiny shim so the event above reads plainly). */
	private static final class Perks {
		static Optional<Perk.Kind> kind(ServerPlayer player, ResourceKey<Perk> perk) {
			return player.level().registryAccess().lookupOrThrow(MettleRegistries.PERK).getOptional(perk).map(Perk::kind);
		}
	}

	// ---------------------------------------------------------------- reading and writing

	public static BuildState get(Player player) {
		return CardStore.get(player).build();
	}

	public static void set(ServerPlayer player, BuildState next) {
		CardsState cards = CardStore.get(player);
		if (!next.equals(cards.build())) {
			CardStore.set(player, cards.withBuild(next));
		}
	}

	/** What the deal leans toward for this player now (§1.1): slotted, non-dormant cards and the Focus. */
	public static CardRules.Commitment commitment(CardsState cards) {
		return CardRules.Commitment.of(cards.cards(), cards.dormant(), cards.build().focus());
	}

	/** The character as previews and bynames read it (either side). */
	public static BuildPreview.Context context(RegistryAccess registries, CharacterState state, CardsState cards, long gameTime) {
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		return new BuildPreview.Context(cards.cards(), state::level, Cards.wits(registries, state), Cards.attributes(registries, state), perks::getOptional,
			Cards.data(registries), cards.pins(), cards.setOrder(), cards.build().focus(), cards.build().byname().tier(), state.characterLevel(),
			cards.freeSwaps(), cards.freeRebuilds(), gameTime);
	}

	/** The build as bynames read it, from the derived cache. */
	public static Bynames.Build build(RegistryAccess registries, CharacterState state, CardsState cards) {
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		List<CardData> active = cards.cards().stream().filter(c -> c.slotted() && !cards.isDormant(c.id())).toList();
		return new Bynames.Build(active, cards.tiers(), cards.sets(), cards.setOrder(), cards.build().focus(), perks::getOptional, Cards.data(registries),
			Cards.attributes(registries, state));
	}

	/** The byname a player wears (empty before their first set), as words: Hearth's greeting and the api's {@code byname}. */
	public static Optional<Component> byname(ServerPlayer player) {
		return get(player).byname().worn().map(b -> BynameText.render(player.level().registryAccess(), b));
	}

	private static void join(ServerPlayer player) {
		Cards.ensure(player);
		decideGuide(player);
		// A save from before build feel: its tier is worked out from the build as it stands, with no moment.
		CardsState cards = CardStore.get(player);
		if (!cards.build().done("byname/migrated")) {
			RegistryAccess registries = player.level().registryAccess();
			Bynames.Build build = build(registries, CharacterStore.get(player), cards);
			int tier = Bynames.earned(build, cards.build().byname().tier());
			BuildState.Bynames bn = cards.build().byname().withTier(tier);
			Optional<Byname> reads = Bynames.of(build, tier);
			if (reads.isPresent() && bn.worn().isEmpty()) {
				bn = bn.wearing(reads.get());
			}
			BuildState next = cards.build().withByname(bn).withOnce("byname/migrated");
			// Milestones already behind an old save are not replayed.
			if (!cards.slotted().isEmpty()) {
				next = next.withOnce("ms/first_card");
			}
			for (Map.Entry<Identifier, Integer> e : cards.tiers().entrySet()) {
				next = next.withOnce("ms/first_set");
				if (e.getValue() >= 4) {
					next = next.withOnce("ms/first_four");
				}
				if (e.getValue() >= 6) {
					next = next.withOnce("ms/six/" + e.getKey()).withOnce("shard/six/" + e.getKey());
				}
			}
			set(player, next);
		}
		publish(player);
	}

	/**
	 * The first time a character is seen since build feel: only one made after it shipped (no skill XP, no cards) gets the
	 * guide; a migrated one never does. Once per character.
	 */
	public static void decideGuide(ServerPlayer player) {
		BuildState b = get(player);
		if (b.done("guide/decided")) {
			return;
		}
		CharacterState state = CharacterStore.get(player);
		CardsState cards = CardStore.get(player);
		BuildState next = b.withGuide(GuideRules.decide(state.totalXp(), cards.cards().size(), cards.slotted().size())).withOnce("guide/decided");
		set(player, next);
		Mettle.LOGGER.info("{}: build feel's guide is {}", player.getName().getString(), next.guide().on() ? "on (a new character)" : "off");
	}

	// ---------------------------------------------------------------- after the deck changed (Cards.derive)

	/**
	 * The deck's derived state changed: Signatures that left their deck stop being ones, the byname tier rises when earned
	 * (a moment) and the milestones of §2.4 fire once each; a keyword's first 6-set pays its shards.
	 */
	static void derived(ServerPlayer player, CardsState before, CardsState after) {
		RegistryAccess registries = player.level().registryAccess();
		BuildState b = after.build();
		BuildState start = b;
		long today = Cards.day(player.level().getServer());
		// Signatures stay only while their card is slotted in that deck.
		for (Map.Entry<ResourceKey<Skill>, Long> e : b.signatures().entrySet()) {
			Optional<CardData> c = after.card(e.getValue());
			if (c.isEmpty() || !c.get().slotted() || !c.get().place().deck().map(e.getKey()::equals).orElse(false)) {
				b = b.withSignature(e.getKey(), Optional.empty());
				if (c.isPresent()) {
					Cards.ledger(player.level().getServer()).update(c.get().id(), x -> x.withMarks(x.marks().signedUntil(today)));
				}
			}
		}
		if (!b.done("byname/migrated")) {
			// Joining: BuildFeel.join settles an old save's tier and milestones first, without moments.
			if (!b.equals(start)) {
				CardStore.set(player, after.withBuild(b));
			}
			return;
		}
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		// The first card ever slotted.
		if (!b.done("ms/first_card")) {
			Optional<CardData> first = after.slotted().stream().filter(c -> !c.mythic()).findFirst();
			if (first.isPresent()) {
				b = b.withOnce("ms/first_card");
				Optional<Perk> p = perks.getOptional(first.get().perk());
				if (p.isPresent()) {
					Moments.milestone(player, MomentKind.CARD, Optional.of(first.get().perk().identifier()), 0, CardText.name(p.get(), first.get().rank()),
						PerkText.description(p.get(), first.get().rank()));
				}
			}
		}
		// Sets: the first, the first 4-set, each keyword's first 6-set (big, and shards).
		for (Map.Entry<Identifier, Integer> e : after.tiers().entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Identifier::toString))).toList()) {
			Identifier k = e.getKey();
			int t = e.getValue();
			if (t >= 2 && !b.done("ms/first_set")) {
				b = b.withOnce("ms/first_set");
				setMoment(player, k, 2);
			}
			if (t >= 4 && !b.done("ms/first_four")) {
				b = b.withOnce("ms/first_four");
				setMoment(player, k, 4);
			}
			if (t >= 6 && !b.done("ms/six/" + k)) {
				b = b.withOnce("ms/six/" + k);
				setMoment(player, k, 6);
				if (!b.done("shard/six/" + k)) {
					b = b.withOnce("shard/six/" + k).withShards(b.shards() + CardConfig.get().shard_first_six);
				}
			}
		}
		// The byname: a tier rise takes the new byname at once; otherwise the build's reading is only offered (§2.1 no flicker).
		Bynames.Build build = build(registries, CharacterStore.get(player), after);
		int earned = Bynames.earned(build, b.byname().tier());
		Optional<Byname> reads = Bynames.of(build, earned);
		if (earned > b.byname().tier() && reads.isPresent()) {
			b = b.withByname(b.byname().wearing(reads.get()));
			if (!b.done("ms/byname/" + earned)) {
				b = b.withOnce("ms/byname/" + earned);
				Moments.milestone(player, MomentKind.BYNAME, Optional.empty(), earned, Component.translatable("mettle.build.moment.byname", BynameText.render(registries, reads.get())),
					Component.translatable("mettle.byname.tier." + earned));
			}
		} else if (earned > b.byname().tier()) {
			b = b.withByname(b.byname().withTier(earned));
		} else if (b.byname().worn().isEmpty() && reads.isPresent()) {
			b = b.withByname(b.byname().wearing(reads.get()));
		}
		if (!b.equals(start)) {
			CardStore.set(player, after.withBuild(b));
		}
		forgeReady(player);
		publish(player);
	}

	private static void setMoment(ServerPlayer player, Identifier keyword, int tier) {
		RegistryAccess registries = player.level().registryAccess();
		Optional<Keyword> k = registries.lookupOrThrow(MettleRegistries.KEYWORD).getOptional(keyword);
		Component sub = k.map(x -> x.sets().get(tier)).map(Keyword.SetBonus::description).orElse(Component.empty());
		Moments.milestone(player, MomentKind.SET, Optional.of(keyword), tier, Component.translatable("mettle.build.set_tier", CardText.keyword(registries, keyword), tier), sub);
	}

	/** The renown everyone sees, set again when it changed. */
	static void publish(ServerPlayer player) {
		BuildState b = get(player);
		Optional<Identifier> crest = b.byname().worn().flatMap(bn -> Optional.ofNullable(b.signatures().get(bn.skill1())))
			.flatMap(id -> CardStore.get(player).card(id)).map(c -> c.mythicId().orElse(c.perk().identifier()));
		Renown next = new Renown(b.byname().worn(), b.legend(), b.legendOf(), crest);
		Renown now = player.getAttached(RENOWN);
		if (!next.equals(now)) {
			player.setAttached(RENOWN, next);
		}
	}

	// ---------------------------------------------------------------- the Focus (§1.3)

	/** Focuses a keyword (empty: none). Free; it changes once an in-game day (the pin's rule). */
	public static Optional<CardRules.Refusal> focus(ServerPlayer player, Optional<Identifier> keyword) {
		Cards.ensure(player);
		BuildState b = get(player);
		if (keyword.isPresent() && player.level().registryAccess().lookupOrThrow(MettleRegistries.KEYWORD).getOptional(keyword.get()).isEmpty()) {
			return Optional.of(CardRules.Refusal.BAD_KEYWORD);
		}
		if (keyword.equals(b.focus())) {
			return Optional.empty();
		}
		long today = Cards.day(player.level().getServer());
		if (b.focusDay() >= 0 && b.focusDay() <= today && today - b.focusDay() < CardConfig.get().focus_change_days) {
			return Optional.of(CardRules.Refusal.FOCUS_TODAY);
		}
		set(player, b.withFocus(keyword, today));
		SignaturePerks.feedback(player, Mettle.id("build/focus"), keyword.map(k -> Component.translatable("mettle.build.focused",
			CardText.keyword(player.level().registryAccess(), k))).orElse(Component.translatable("mettle.build.unfocused")));
		Mettle.LOGGER.info("{} focused {}", player.getName().getString(), keyword.map(Identifier::toString).orElse("nothing"));
		return Optional.empty();
	}

	// ---------------------------------------------------------------- boons (§3)

	/** Odd levels from {@code boons_from} give a boon instead of a reroll (DraftState migration keeps old rerolls). */
	public static DraftState levelled(DraftState d, ResourceKey<Skill> skill, int before, int after) {
		return CardRules.levelled(d, skill, before, after);
	}

	private static Optional<CardData> mine(ServerPlayer player, long id) {
		return CardStore.get(player).card(id).filter(c -> c.owner().equals(player.getUUID()));
	}

	/** Spends a boon of the card's skill on a Temper of one of its traits (§3.2). */
	public static Optional<CardRules.Refusal> temper(ServerPlayer player, long id, Identifier trait) {
		CharacterState state = Cards.ensure(player);
		Optional<CardData> card = mine(player, id);
		if (card.isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		CardData c = card.get();
		DraftState d = state.drafts();
		if (d.boons(c.skill()) <= 0) {
			return Optional.of(CardRules.Refusal.NO_BOON);
		}
		RegistryAccess registries = player.level().registryAccess();
		MinecraftServer server = player.level().getServer();
		CardRules.Data data = Cards.data(registries);
		Optional<CardRules.Refusal> no = CardRules.canTemper(c, trait, CardStore.get(player).deck(c.skill()), data, server.overworld().getGameTime());
		if (no.isPresent()) {
			return no;
		}
		List<CardData.TraitRoll> traits = CardRules.temper(c, trait, data);
		double before = c.traits().stream().filter(t -> t.trait().equals(trait)).findFirst().orElseThrow().value();
		double after = traits.stream().filter(t -> t.trait().equals(trait)).findFirst().orElseThrow().value();
		CardData.Tempered mark = new CardData.Tempered(trait, before, after, player.getGameProfile().name(), state.level(c.skill()), Cards.day(server));
		Cards.ledger(server).update(id, x -> x.withBuild(x.keywords(), traits, x.marks().withTempered(mark)));
		CharacterStore.set(player, state.withDraft(d.addBoons(c.skill(), -1)));
		Cards.sync(player);
		Component line = data.trait(trait).map(def -> Component.translatable("mettle.build.tempered", def.name(), def.format(before), def.format(after)))
			.orElse(Component.literal(trait.toString()));
		boonDone(player, "temper", c, line);
		LedgerFile.commit(server, player);
		Mettle.LOGGER.info("{} tempered card {} ({} {} -> {})", player.getName().getString(), id, trait, before, after);
		return Optional.empty();
	}

	/** Spends a boon of the card's skill on an Inscription of a keyword from its skill's pool (§3.3). */
	public static Optional<CardRules.Refusal> inscribe(ServerPlayer player, long id, Identifier keyword) {
		CharacterState state = Cards.ensure(player);
		Optional<CardData> card = mine(player, id);
		if (card.isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		CardData c = card.get();
		DraftState d = state.drafts();
		if (d.boons(c.skill()) <= 0) {
			return Optional.of(CardRules.Refusal.NO_BOON);
		}
		RegistryAccess registries = player.level().registryAccess();
		MinecraftServer server = player.level().getServer();
		Optional<CardRules.Refusal> no = CardRules.canInscribe(c, keyword, CardStore.get(player).deck(c.skill()), Cards.data(registries),
			server.overworld().getGameTime());
		if (no.isPresent()) {
			return no;
		}
		List<Identifier> keywords = CardRules.inscribe(c, keyword);
		Optional<Identifier> was = c.keywords().size() > 1 ? Optional.of(c.keywords().get(1)) : Optional.empty();
		CardData.Inscribed mark = new CardData.Inscribed(keyword, was, player.getGameProfile().name(), state.level(c.skill()), Cards.day(server));
		Cards.ledger(server).update(id, x -> x.withBuild(keywords, x.traits(), x.marks().withInscribed(mark)));
		CharacterStore.set(player, state.withDraft(d.addBoons(c.skill(), -1)));
		Cards.sync(player);
		Component name = registries.lookupOrThrow(MettleRegistries.PERK).getOptional(c.perk()).map(p -> CardText.name(p, c.rank())).orElse(Component.literal("?"));
		boonDone(player, "inscribe", c, Component.translatable("mettle.build.inscribed", CardText.keyword(registries, keyword), name));
		LedgerFile.commit(server, player);
		Mettle.LOGGER.info("{} inscribed {} on card {}", player.getName().getString(), keyword, id);
		return Optional.empty();
	}

	/** Spends a boon on a reroll, as odd levels gave before build feel; refused while five are held. */
	public static Optional<CardRules.Refusal> boonReroll(ServerPlayer player, ResourceKey<Skill> skill) {
		CharacterState state = Cards.ensure(player);
		DraftState d = state.drafts();
		if (d.boons(skill) <= 0) {
			return Optional.of(CardRules.Refusal.NO_BOON);
		}
		if (d.rerolls() >= DraftRules.MAX_REROLLS) {
			return Optional.of(CardRules.Refusal.REROLLS_FULL);
		}
		CharacterStore.set(player, state.withDraft(d.addBoons(skill, -1).withRerolls(d.rerolls() + 1)));
		SignaturePerks.feedback(player, Mettle.id("build/boon"), Component.translatable("mettle.build.boon_reroll", d.rerolls() + 1));
		return Optional.empty();
	}

	private static void boonDone(ServerPlayer player, String what, CardData card, Component line) {
		SignaturePerks.feedback(player, Mettle.id("build/" + what), line);
		BuildState b = get(player);
		if (!b.done("ms/first_" + what)) {
			set(player, b.withOnce("ms/first_" + what));
			Moments.milestone(player, MomentKind.BOON, Optional.of(card.perk().identifier()), what.equals("temper") ? 1 : 2,
				Component.translatable("mettle.build.moment." + what), line);
		}
	}

	// ---------------------------------------------------------------- Signatures (§2.2)

	/** Makes a slotted card its deck's Signature (pure identity, free, any time); {@code id} -1 clears {@code skill}'s. */
	public static Optional<CardRules.Refusal> signature(ServerPlayer player, long id, Optional<ResourceKey<Skill>> skill) {
		Cards.ensure(player);
		MinecraftServer server = player.level().getServer();
		long today = Cards.day(server);
		BuildState b = get(player);
		if (id < 0) {
			if (skill.isEmpty()) {
				return Optional.of(CardRules.Refusal.NO_CARD);
			}
			Long old = b.signatures().get(skill.get());
			if (old != null) {
				Cards.ledger(server).update(old, x -> x.withMarks(x.marks().signedUntil(today)));
			}
			set(player, b.withSignature(skill.get(), Optional.empty()));
			Cards.sync(player);
			publish(player);
			return Optional.empty();
		}
		Optional<CardData> card = mine(player, id);
		if (card.isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		if (!card.get().slotted()) {
			return Optional.of(CardRules.Refusal.NOT_SLOTTED);
		}
		ResourceKey<Skill> deck = card.get().place().deck().orElse(card.get().skill());
		Long old = b.signatures().get(deck);
		if (old != null && old == id) {
			return Optional.empty();
		}
		if (old != null) {
			Cards.ledger(server).update(old, x -> x.withMarks(x.marks().signedUntil(today)));
		}
		String name = player.getGameProfile().name();
		Cards.ledger(server).update(id, x -> x.withMarks(x.marks().signedFrom(name, today)));
		b = b.withSignature(deck, Optional.of(id));
		set(player, b);
		Cards.sync(player);
		RegistryAccess registries = player.level().registryAccess();
		Component cardName = card.get().mythicId().flatMap(m -> Mythics.def(registries, m)).map(m -> m.name())
			.or(() -> registries.lookupOrThrow(MettleRegistries.PERK).getOptional(card.get().perk()).map(p -> CardText.name(p, card.get().rank())))
			.orElse(Component.literal("?"));
		SignaturePerks.feedback(player, Mettle.id("build/signature"), Component.translatable("mettle.build.signature", cardName, Cards.skillName(player, deck)));
		if (!b.done("ms/first_signature")) {
			set(player, get(player).withOnce("ms/first_signature"));
			Moments.milestone(player, MomentKind.SIGNATURE, Optional.of(card.get().perk().identifier()), 0,
				Component.translatable("mettle.build.moment.signature", cardName), Component.translatable("mettle.build.moment.signature.line"));
		}
		LedgerFile.commit(server, player);
		publish(player);
		return Optional.empty();
	}

	// ---------------------------------------------------------------- bynames (§2.1)

	/**
	 * Wears a byname: the one the build reads as now ({@code held} -1, "Take it"), or one held before (its number in the
	 * held list). Never changes the tier earned.
	 */
	public static Optional<CardRules.Refusal> wearByname(ServerPlayer player, int held) {
		Cards.ensure(player);
		BuildState b = get(player);
		Byname pick;
		if (held < 0) {
			RegistryAccess registries = player.level().registryAccess();
			Optional<Byname> reads = Bynames.of(build(registries, CharacterStore.get(player), CardStore.get(player)), b.byname().tier());
			if (reads.isEmpty()) {
				return Optional.of(CardRules.Refusal.NO_BYNAME);
			}
			pick = reads.get();
		} else {
			if (held >= b.byname().held().size()) {
				return Optional.of(CardRules.Refusal.NO_BYNAME);
			}
			pick = b.byname().held().get(held);
		}
		set(player, b.withByname(b.byname().wearing(pick)));
		SignaturePerks.feedback(player, Mettle.id("build/byname"), Component.translatable("mettle.build.wearing", BynameText.render(player.level().registryAccess(), pick)));
		publish(player);
		return Optional.empty();
	}

	// ---------------------------------------------------------------- Mythic shards and the forge (§5)

	/** Adds shards; with {@code once} set, only the first time that source happens for this character. */
	public static void addShards(ServerPlayer player, int n, @org.jspecify.annotations.Nullable String once) {
		if (n <= 0) {
			return;
		}
		Cards.ensure(player);
		BuildState b = get(player);
		if (once != null) {
			if (b.done(once)) {
				return;
			}
			b = b.withOnce(once);
		}
		set(player, b.withShards(b.shards() + n));
		SignaturePerks.feedback(player, Mettle.id("build/shards"), Component.translatable(n == 1 ? "mettle.build.shard" : "mettle.build.shards", n,
			b.shards() + n, ForgeRules.cost(b.forges())));
		forgeReady(player);
	}

	/** A draft kept from a skill at 15 or more pays a shard (§5.2). */
	public static void draftKept(ServerPlayer player, ResourceKey<Skill> skill) {
		if (CharacterStore.get(player).level(skill) >= 15) {
			addShards(player, CardConfig.get().shard_draft_from_15, null);
		}
	}

	/** A boss killed after its world first was claimed: each player with a real part (25 % of the damage) earns shards, once per kind. */
	static void bossKilled(MinecraftServer server, String mythic, Map<UUID, Float> fight) {
		double total = fight.values().stream().mapToDouble(Float::doubleValue).sum();
		if (total <= 0) {
			return;
		}
		fight.forEach((uuid, dealt) -> {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p != null && !p.isCreative() && !p.isSpectator() && dealt / total >= CardConfig.get().shard_boss_share) {
				addShards(p, CardConfig.get().shard_boss_after_first, "shard/boss/" + mythic);
			}
		});
	}

	/** The forge-ready moment, once per forge (§2.4). */
	public static void forgeReady(ServerPlayer player) {
		BuildState b = get(player);
		String key = "ms/forge_ready/" + b.forges();
		if (b.done(key) || b.shards() < ForgeRules.cost(b.forges())) {
			return;
		}
		CharacterState state = CharacterStore.get(player);
		if (!ForgeRules.ready(Mythics.pool(player.level().getServer()), b.shards(), b.forges(), b.legend(), state::level)) {
			return;
		}
		set(player, b.withOnce(key));
		Moments.milestone(player, MomentKind.FORGE_READY, Optional.empty(), 0, Component.translatable("mettle.build.moment.forge_ready"),
			Component.translatable("mettle.build.moment.forge_ready.line", ForgeRules.cost(b.forges())));
	}

	/**
	 * At a Card Shrine: forges an unclaimed drawn Mythic of a skill at 18 (§5.3), or with none left anywhere, the legend
	 * title ({@code "legend"}). The shards are spent before the claim is written, so the claim's commit carries both; on
	 * one server thread nobody else can claim it between the check and the claim (two players forging the last one: the
	 * second is refused and keeps their shards).
	 */
	public static Optional<CardRules.Refusal> forge(ServerPlayer player, String what) {
		CharacterState state = Cards.ensure(player);
		if (Rebuilds.sitting(player).map(s -> s.kind() != Rebuilds.Kind.SHRINE).orElse(true)) {
			return Optional.of(CardRules.Refusal.NOT_AT_SHRINE);
		}
		MinecraftServer server = player.level().getServer();
		BuildState b = get(player);
		List<ForgeRules.Drawn> pool = Mythics.pool(server);
		int cost = ForgeRules.cost(b.forges());
		if ("legend".equals(what)) {
			Optional<CardRules.Refusal> no = ForgeRules.canLegend(pool, b.shards(), b.forges(), b.legend());
			if (no.isPresent()) {
				return no;
			}
			String world = server.getWorldData().getLevelName();
			set(player, b.withForge(b.shards() - cost, b.forges() + 1, b.legend() + 1, world));
			LedgerFile.commit(server, player);
			publish(player);
			SignaturePerks.feedback(player, Mettle.id("build/legend"), Component.translatable("mettle.build.legend", world, b.legend() + 1));
			Mettle.LOGGER.info("{} forged the legend title (star {}) for {} shards", player.getName().getString(), b.legend() + 1, cost);
			return Optional.empty();
		}
		Identifier id = Identifier.tryParse(what);
		Optional<ForgeRules.Drawn> mythic = id == null ? Optional.empty() : pool.stream().filter(d -> d.id().equals(id)).findFirst();
		Optional<CardRules.Refusal> no = ForgeRules.canForge(mythic, b.shards(), b.forges(), state::level);
		if (no.isPresent()) {
			return no;
		}
		set(player, b.withForge(b.shards() - cost, b.forges() + 1, b.legend(), b.legendOf()));
		if (Mythics.grant(player, id, false, CardData.How.FORGED).isEmpty()) {
			// Not reachable on one thread after the check above; if it ever is, the shards come back.
			set(player, b);
			return Optional.of(CardRules.Refusal.MYTHIC_HELD);
		}
		Mettle.LOGGER.info("{} forged the Mythic {} for {} shards", player.getName().getString(), id, cost);
		return Optional.empty();
	}

	// ---------------------------------------------------------------- the guide (§4)

	/** Once a second: the hint up steps aside when its job is done, and the next due one comes up when calm. */
	static void guideTick(ServerPlayer player) {
		BuildState b = get(player);
		if (!b.guide().on() || b.guide().skipped()) {
			return;
		}
		long now = player.level().getGameTime();
		BuildState.Guide next = GuideRules.step(b.guide(), facts(player, b), now);
		if (!next.equals(b.guide())) {
			set(player, b.withGuide(next));
		}
	}

	/** What the guide reads of a character. */
	static GuideRules.Facts facts(ServerPlayer player, BuildState b) {
		CharacterState state = CharacterStore.get(player);
		CardsState cards = CardStore.get(player);
		DraftState d = state.drafts();
		RegistryAccess registries = player.level().registryAccess();
		boolean handOpen = d.hands().values().stream().anyMatch(h -> !h.isEmpty());
		boolean anySet = !cards.tiers().isEmpty();
		boolean makesSet = false;
		if (!anySet) {
			for (List<DraftState.Card> hand : d.hands().values()) {
				for (DraftState.Card c : hand) {
					for (Identifier k : c.keywords()) {
						makesSet |= cards.sets().getOrDefault(k, 0) + 1 >= 2;
					}
				}
			}
		}
		long gameTime = player.level().getServer().overworld().getGameTime();
		boolean cantSlot = cards.cards().stream().anyMatch(c -> !c.slotted() && !c.mythic() && state.level(c.skill()) < c.needs());
		boolean fullBetter = false;
		int wits = Cards.wits(registries, state);
		for (CardData c : cards.cards()) {
			if (c.slotted() || c.mythic() || !c.attuned(gameTime) || state.level(c.skill()) < c.needs()) {
				continue;
			}
			int slots = CardRules.slots(state.level(c.skill()), wits);
			if (slots > 0 && cards.deck(c.skill()).size() >= slots) {
				fullBetter = true;
			}
		}
		long hurt = LAST_HURT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
		boolean calm = player.level().getGameTime() - hurt >= GuideRules.CALM;
		return new GuideRules.Facts(state.totalXp() > 0, handOpen, cards.cards().size(), cards.slotted().size(), b.done("guide/keyword"), makesSet,
			anySet, cantSlot, fullBetter, d.totalBoons(), calm);
	}

	/** "Skip the guide". */
	public static Optional<CardRules.Refusal> guideSkip(ServerPlayer player) {
		BuildState b = get(player);
		set(player, b.withGuide(GuideRules.skip(b.guide())));
		return Optional.empty();
	}

	/**
	 * Something the overlay saw: {@code dismiss} (the ×), {@code deck} (the Deck opened), {@code keyword} (a keyword badge
	 * hovered: hint 5 may come), {@code unhover}, {@code not_now} (the Focus offer declined). A "Focus" answer is
	 * {@link #focus} followed by {@code dismiss}.
	 */
	public static Optional<CardRules.Refusal> guideAdvance(ServerPlayer player, String event) {
		BuildState b = get(player);
		long now = player.level().getGameTime();
		BuildState next = switch (event == null ? "" : event.trim().toLowerCase(Locale.ROOT)) {
			case "keyword" -> b.withOnce("guide/keyword");
			case "dismiss", "not_now", "focus" -> b.withGuide(GuideRules.dismiss(b.guide(), now));
			case "deck" -> b.guide().current() == GuideRules.Hint.SLOTTED.number() ? b.withGuide(GuideRules.dismiss(b.guide(), now)) : b;
			case "unhover" -> b.guide().current() == GuideRules.Hint.KEYWORD.number() ? b.withGuide(GuideRules.dismiss(b.guide(), now)) : b;
			default -> b;
		};
		set(player, next);
		return Optional.empty();
	}

	/**
	 * The guided first hand (§4): a new character's first hand holds at least one Rare (or better) card, so a trait and a
	 * rank show; the first card that is Common is rolled again at Rare from the player's secret seed. Nothing else is forced.
	 */
	public static List<DraftState.Card> guidedFirstHand(List<DraftState.Card> hand, RegistryAccess registries, long seed, int dealt, CardRules.Commitment commitment) {
		if (hand.stream().anyMatch(c -> c.rarity() == DraftState.Rarity.RARE || c.rarity() == DraftState.Rarity.EPIC)) {
			return hand;
		}
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		List<DraftState.Card> out = new ArrayList<>(hand);
		for (int i = 0; i < out.size(); i++) {
			DraftState.Card c = out.get(i);
			Optional<Perk> p = perks.getOptional(c.perk());
			if (c.rarity() == DraftState.Rarity.COMMON && p.isPresent()) {
				java.util.Random random = new java.util.Random(dev.voidmc.mettle.core.MettleSecret.hash(seed, "first_rare", dealt));
				out.set(i, CardRules.roll(c.perk(), p.get(), DraftState.Rarity.RARE, Cards.data(registries), random, c.wildcard(), commitment));
				return out;
			}
		}
		return hand;
	}

	// ---------------------------------------------------------------- requests from the client

	private static void act(ServerPlayer player, MettlePayloads.BuildAction a) {
		Optional<CardRules.Refusal> no = switch (a.action()) {
			case FOCUS -> focus(player, a.text().isBlank() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(a.text().trim())));
			case TEMPER -> temper(player, a.card(), parse(a.text()));
			case INSCRIBE -> inscribe(player, a.card(), parse(a.text()));
			case BOON_REROLL -> boonReroll(player, ResourceKey.create(MettleRegistries.SKILL, parse(a.text())));
			case SIGNATURE -> signature(player, a.card(), a.text().isBlank() ? Optional.empty() : Optional.of(ResourceKey.create(MettleRegistries.SKILL, parse(a.text()))));
			case BYNAME -> wearByname(player, a.text().isBlank() && a.card() < 0 ? -1 : (int) Math.max(-1, Math.min(Integer.MAX_VALUE, a.card())));
			case FORGE -> forge(player, a.text().trim());
			case GUIDE_SKIP -> guideSkip(player);
			case GUIDE_ADVANCE -> guideAdvance(player, a.text());
		};
		no.ifPresent(r -> Cards.refused(player, r));
	}

	private static Identifier parse(String text) {
		Identifier id = Identifier.tryParse(text == null ? "" : text.trim());
		return id == null ? Mettle.id("none") : id;
	}

	/** Re-applies effects after a change that moves what the deck makes active (a Temper, an Inscription). */
	static void apply(ServerPlayer player) {
		EffectsApplier.apply(player);
	}
}
