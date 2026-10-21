package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Mythic;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.cards.BuildFeel;
import dev.voidmc.mettle.character.cards.BuildPreview;
import dev.voidmc.mettle.character.cards.BuildSummary;
import dev.voidmc.mettle.character.cards.BynameText;
import dev.voidmc.mettle.character.cards.Bynames;
import dev.voidmc.mettle.character.cards.CardConfig;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.CardText;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.ForgeRules;
import dev.voidmc.mettle.character.cards.GuideRules;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.BuildState.Byname;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;

/**
 * Build feel in the overlay state (docs/BUILD_FEEL.md, OVERLAY.md "Build feel"): {@code cards.build} (Focus, momentum,
 * byname, "Your build", boons, Signatures, shards and the forge, the guide), the set tracker's cues, each deck's tempered
 * and inscribed counts, card marks, and the preview of what a card in a hand would do. Every number comes from the synced
 * card cache and the pure rules (BuildPreview, BuildSummary, Bynames), so the Hub only reads.
 */
public final class MettleBuild {
	private MettleBuild() {
	}

	private static String s(Component c) {
		return c.getString();
	}

	private static Component skillName(RegistryAccess registries, ResourceKey<Skill> skill) {
		return registries.lookupOrThrow(MettleRegistries.SKILL).getOptional(skill).map(Skill::name).orElse(Component.literal(skill.identifier().getPath()));
	}

	private static Component cardName(RegistryAccess registries, CardData c) {
		if (c.mythic()) {
			return c.mythicId().flatMap(m -> registries.lookupOrThrow(MettleRegistries.MYTHIC).getOptional(m)).map(Mythic::name).orElse(Component.literal("?"));
		}
		return registries.lookupOrThrow(MettleRegistries.PERK).getOptional(c.perk()).map(p -> (Component) CardText.name(p, c.rank())).orElse(Component.literal("?"));
	}

	/** {@code cards.build}. */
	public static JsonObject build(Minecraft mc, RegistryAccess registries) {
		Player player = mc.player;
		CharacterState state = CharacterStore.get(player);
		CardsState cards = CardStore.get(player);
		BuildState b = cards.build();
		CardConfig config = CardConfig.get();
		long gameTime = player.level().getGameTime();
		long today = gameTime / 24000L;
		JsonObject root = new JsonObject();

		// The Focus (§1.3) and how far each keyword leans the deal now (§1.2).
		JsonObject focus = new JsonObject();
		focus.add("keyword", b.focus().map(k -> (JsonElement) new com.google.gson.JsonPrimitive(k.toString())).orElse(JsonNull.INSTANCE));
		focus.addProperty("name", b.focus().map(k -> s(CardText.keyword(registries, k))).orElse(""));
		focus.addProperty("canChange", b.focusDay() < 0 || b.focusDay() > today || today - b.focusDay() >= config.focus_change_days);
		focus.addProperty("weight", config.focus_weight);
		focus.addProperty("note", s(Component.translatable("mettle.build.focus_once")));
		root.add("focus", focus);
		CardRules.Commitment commitment = BuildFeel.commitment(cards);
		CardRules.Data data = Cards.data(registries);
		JsonArray momentum = new JsonArray();
		registries.lookupOrThrow(MettleRegistries.KEYWORD).listElements().sorted(Comparator.comparing(r -> r.key().identifier().toString())).forEach(r -> {
			JsonObject o = new JsonObject();
			Identifier k = r.key().identifier();
			o.addProperty("keyword", k.toString());
			o.addProperty("momentum", r.value().momentum());
			o.addProperty("commitment", commitment.of(k));
			o.addProperty("perkLean", CardRules.round(CardRules.perkLean(k, commitment, data)));
			o.addProperty("keywordLean", CardRules.round(CardRules.keywordLean(k, commitment, data)));
			momentum.add(o);
		});
		root.add("momentum", momentum);
		root.addProperty("sixTierSkill", config.six_tier_skill);

		// The byname (§2.1): worn, earned tier, what the build reads as ("Take it"), and every one held.
		BynameText words = BynameText.of(registries);
		Bynames.Build build = BuildFeel.build(registries, state, cards);
		Optional<Byname> reads = Bynames.of(build, b.byname().tier());
		JsonObject byname = new JsonObject();
		byname.add("worn", b.byname().worn().map(x -> (JsonElement) byname(x, words)).orElse(JsonNull.INSTANCE));
		byname.addProperty("tier", b.byname().tier());
		byname.addProperty("tierName", b.byname().tier() == 0 ? "" : s(Component.translatable("mettle.byname.tier." + b.byname().tier())));
		byname.add("reads", reads.map(x -> (JsonElement) byname(x, words)).orElse(JsonNull.INSTANCE));
		boolean offer = reads.isPresent() && !reads.equals(b.byname().worn());
		byname.addProperty("offer", offer);
		byname.addProperty("offerText", offer ? s(Component.translatable("mettle.byname.offer", Bynames.render(reads.get(), words))) : "");
		JsonArray held = new JsonArray();
		for (int i = 0; i < b.byname().held().size(); i++) {
			Byname h = b.byname().held().get(i);
			JsonObject o = byname(h, words);
			o.addProperty("index", i);
			o.addProperty("wearing", b.byname().worn().map(h::equals).orElse(false));
			held.add(o);
		}
		byname.add("held", held);
		Optional<Long> crestCard = b.byname().worn().flatMap(w -> Optional.ofNullable(b.signatures().get(w.skill1())));
		byname.add("crest", crestCard.flatMap(cards::card).map(c -> (JsonElement) new com.google.gson.JsonPrimitive(c.mythic()
			? c.mythicId().map(Identifier::toString).orElse("") : Perk.icon(c.perk()).toString())).orElse(JsonNull.INSTANCE));
		byname.add("legend", b.legend() == 0 ? JsonNull.INSTANCE : legend(b.legend(), b.legendOf()));
		root.add("byname", byname);

		// Deed titles (§12): held, the one worn, and whether it stands instead of the byname or beside it.
		JsonObject titles = new JsonObject();
		titles.add("worn", b.titles().worn().map(w -> (JsonElement) new com.google.gson.JsonPrimitive(w.toString())).orElse(JsonNull.INSTANCE));
		titles.addProperty("wornText", b.titles().worn().map(w -> s(dev.voidmc.mettle.character.cards.Deeds.title(w))).orElse(""));
		titles.addProperty("instead", b.titles().instead());
		JsonArray heldTitles = new JsonArray();
		for (Identifier t : b.titles().held()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", t.toString());
			o.addProperty("text", s(dev.voidmc.mettle.character.cards.Deeds.title(t)));
			o.addProperty("wearing", b.titles().worn().map(t::equals).orElse(false));
			heldTitles.add(o);
		}
		titles.add("held", heldTitles);
		root.add("titles", titles);

		// "Your build" (§2.3).
		root.add("summary", summary(registries, state, cards, build, words));

		// Boons (§3).
		JsonObject boons = new JsonObject();
		DraftState drafts = state.drafts();
		boons.addProperty("total", drafts.totalBoons());
		boons.addProperty("text", drafts.totalBoons() == 0 ? "" : s(drafts.totalBoons() == 1 ? Component.translatable("mettle.build.boon")
			: Component.translatable("mettle.build.boons", drafts.totalBoons())));
		JsonArray bySkill = new JsonArray();
		drafts.boons().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(k -> k.identifier().toString()))).forEach(e -> {
			JsonObject o = new JsonObject();
			o.addProperty("skill", e.getKey().identifier().toString());
			o.addProperty("name", s(skillName(registries, e.getKey())));
			o.addProperty("count", e.getValue());
			bySkill.add(o);
		});
		boons.add("bySkill", bySkill);
		JsonArray choices = new JsonArray();
		for (String c : new String[] {"temper", "inscribe", "reroll"}) {
			JsonObject o = new JsonObject();
			o.addProperty("id", c);
			o.addProperty("name", s(Component.translatable("mettle.build.boon." + c)));
			o.addProperty("what", s(Component.translatable("mettle.build.boon." + c + ".what")));
			o.addProperty("enabled", !c.equals("reroll") || drafts.rerolls() < DraftRules.MAX_REROLLS);
			choices.add(o);
		}
		boons.add("choices", choices);
		boons.addProperty("temperStep", config.temper_step);
		boons.addProperty("temperPerDeck", config.temper_per_deck);
		boons.addProperty("inscribePerDeck", config.inscribe_per_deck);
		boons.addProperty("rerollsFull", drafts.rerolls() >= DraftRules.MAX_REROLLS);
		root.add("boons", boons);

		JsonArray signatures = new JsonArray();
		b.signatures().forEach((skill, id) -> {
			JsonObject o = new JsonObject();
			o.addProperty("skill", skill.identifier().toString());
			o.addProperty("card", String.valueOf(id));
			signatures.add(o);
		});
		root.add("signatures", signatures);

		// Mythic shards and the forge (§5).
		root.add("shards", shards(b));
		root.add("forge", forge(registries, state, b, MettleCards.atShrine()));
		JsonArray milestones = new JsonArray();
		b.once().stream().filter(k -> k.startsWith("ms/")).forEach(k -> milestones.add(k.substring(3)));
		root.add("milestones", milestones);
		root.add("guide", guide(registries, state, cards, b, gameTime));
		return root;
	}

	static JsonObject legend(int stars, String of) {
		JsonObject o = new JsonObject();
		o.addProperty("stars", stars);
		o.addProperty("of", of);
		o.addProperty("title", s(Component.translatable("mettle.byname.legend", of)));
		return o;
	}

	static JsonObject byname(Byname b, BynameText words) {
		JsonObject o = new JsonObject();
		o.addProperty("text", s(Bynames.render(b, words)));
		o.addProperty("tier", b.tier());
		o.addProperty("tierName", s(Component.translatable("mettle.byname.tier." + b.tier())));
		o.addProperty("tierLine", s(Component.translatable("mettle.byname.tier_line", CardText.roman(b.tier()), Component.translatable("mettle.byname.tier." + b.tier()))));
		o.addProperty("line", s(Bynames.line(b, words)));
		o.addProperty("kw1", b.kw1().toString());
		b.kw2().ifPresent(k -> o.addProperty("kw2", k.toString()));
		o.addProperty("skill1", b.skill1().identifier().toString());
		b.skill2().ifPresent(k -> o.addProperty("skill2", k.identifier().toString()));
		b.mythic().ifPresent(k -> o.addProperty("mythic", k.toString()));
		return o;
	}

	private static JsonObject summary(RegistryAccess registries, CharacterState state, CardsState cards, Bynames.Build build, BynameText words) {
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		CardRules.Derived derived = new CardRules.Derived(cards.setOrder(), cards.active(), cards.dormant(), cards.traits(), cards.sets(), cards.tiers());
		BuildState b = cards.build();
		BuildSummary.Summary sum = BuildSummary.of(build, derived, Cards.data(registries), b.byname().worn(), b.byname().tier(), b.shards(),
			ForgeRules.cost(b.forges()));
		JsonObject o = new JsonObject();
		o.addProperty("empty", cards.slotted().isEmpty());
		o.addProperty("emptyText", s(Component.translatable("mettle.build.empty")));
		o.add("headline", sum.byname().map(x -> (JsonElement) byname(x, words)).orElse(JsonNull.INSTANCE));
		JsonArray lines = new JsonArray();
		Registry<Keyword> keywords = registries.lookupOrThrow(MettleRegistries.KEYWORD);
		Registry<Trait> traits = registries.lookupOrThrow(MettleRegistries.TRAIT);
		for (BuildSummary.Line l : sum.lines()) {
			JsonObject lo = new JsonObject();
			lo.addProperty("kind", l.kind().name().toLowerCase(java.util.Locale.ROOT));
			lo.addProperty("id", l.id().toString());
			switch (l.kind()) {
				case SET -> {
					Optional<Keyword.SetBonus> bonus = keywords.getOptional(l.id()).map(k -> k.sets().get(l.tier()));
					lo.addProperty("text", bonus.map(x -> s(x.summary().orElse(x.description()))).orElse(""));
					Component name = CardText.keyword(registries, l.id());
					String six = l.tier() >= 6 ? bonus.map(x -> x.description().getString()).map(d -> d.contains(":") ? d.substring(0, d.indexOf(':')) : "").orElse("") : "";
					lo.addProperty("source", six.isEmpty() ? s(Component.translatable("mettle.build.set_tier", name, l.tier()))
						: s(Component.translatable("mettle.build.set_line", name, l.tier(), six)));
					lo.addProperty("tier", l.tier());
				}
				case TRAIT -> traits.getOptional(l.id()).ifPresent(t -> {
					lo.addProperty("text", s(CardText.sentence(t, l.value())));
					lo.addProperty("source", s(Component.translatable("mettle.build.trait_line", t.name(), t.format(l.value()), t.format(l.cap()))));
					lo.addProperty("value", l.value());
					lo.addProperty("cap", l.cap());
				});
				case MASTERY -> perks.getOptional(dev.voidmc.mettle.character.Perks.key(l.id())).ifPresent(p -> {
					lo.addProperty("text", s(Component.translatable("mettle.build.mastery_line", p.name())));
					lo.addProperty("source", s(skillName(registries, p.skill())));
				});
				case MYTHIC -> registries.lookupOrThrow(MettleRegistries.MYTHIC).getOptional(l.id()).ifPresent(m -> {
					lo.addProperty("text", s(Component.translatable("mettle.build.mythic_line", m.name())));
					lo.addProperty("source", s(m.effect()));
				});
			}
			lines.add(lo);
		}
		o.add("lines", lines);
		o.add("next", sum.next().map(n -> {
			JsonObject no = new JsonObject();
			no.addProperty("kind", n.kind().name().toLowerCase(java.util.Locale.ROOT));
			Component kw = CardText.keyword(registries, n.keyword());
			Component cardsWord = Component.translatable(n.cards() == 1 ? "mettle.build.card" : "mettle.build.cards");
			no.addProperty("text", s(switch (n.kind()) {
				case SET -> Component.translatable("mettle.build.next.set", n.cards(), kw, cardsWord, kw, n.tier());
				case BYNAME -> Component.translatable("mettle.build.next.byname", n.cards(), kw, cardsWord, kw, n.tier());
				case FORGE -> Component.translatable("mettle.build.next.forge", n.shards());
			}));
			if (n.kind() != BuildSummary.NextKind.FORGE) {
				no.addProperty("keyword", n.keyword().toString());
				no.addProperty("cards", n.cards());
				no.addProperty("tier", n.tier());
			}
			return (JsonElement) no;
		}).orElse(JsonNull.INSTANCE));
		return o;
	}

	private static JsonObject shards(BuildState b) {
		JsonObject o = new JsonObject();
		int cost = ForgeRules.cost(b.forges());
		o.addProperty("count", b.shards());
		o.addProperty("cost", cost);
		o.addProperty("forges", b.forges());
		o.addProperty("text", s(Component.translatable("mettle.build.shards_meter", b.shards(), cost)));
		JsonArray ladder = new JsonArray();
		for (int c : CardConfig.get().shard_forge_costs) {
			ladder.add(c);
		}
		o.add("ladder", ladder);
		o.addProperty("enough", b.shards() >= cost);
		return o;
	}

	/** The Forge tab (§5.3, B14): every drawn Mythic, forgeable, locked (and why) or held (by whom), from the Hall of Firsts. */
	private static JsonObject forge(RegistryAccess registries, CharacterState state, BuildState b, boolean atShrine) {
		JsonObject o = new JsonObject();
		o.addProperty("atShrine", atShrine);
		Map<Identifier, String> holders = new HashMap<>();
		MettleCards.hallEntries().forEach(e -> {
			if (!e.holder().isEmpty()) {
				holders.put(e.mythic(), e.holder());
			}
		});
		List<ForgeRules.Drawn> pool = new ArrayList<>();
		JsonArray all = new JsonArray();
		int cost = ForgeRules.cost(b.forges());
		registries.lookupOrThrow(MettleRegistries.MYTHIC).listElements()
			.filter(r -> r.value().source() == Mythic.Source.DRAWN && r.value().skill().isPresent())
			.sorted(Comparator.comparing((net.minecraft.core.Holder.Reference<Mythic> r) -> r.value().skill().get().identifier().toString())
				.thenComparing(r -> r.key().identifier().toString()))
			.forEach(r -> {
				Identifier id = r.key().identifier();
				ResourceKey<Skill> skill = r.value().skill().get();
				boolean claimed = holders.containsKey(id);
				pool.add(new ForgeRules.Drawn(id, skill, claimed));
				JsonObject m = new JsonObject();
				m.addProperty("id", id.toString());
				m.addProperty("name", s(r.value().name()));
				m.addProperty("effect", s(r.value().effect()));
				m.addProperty("icon", r.value().icon().toString());
				m.addProperty("skill", skill.identifier().toString());
				m.addProperty("skillName", s(skillName(registries, skill)));
				Optional<CardRules.Refusal> no = ForgeRules.canForge(Optional.of(new ForgeRules.Drawn(id, skill, claimed)), Integer.MAX_VALUE, b.forges(), state::level);
				String st = claimed ? "held" : no.isEmpty() ? "forgeable" : "locked";
				m.addProperty("state", st);
				m.addProperty("holder", claimed ? holders.get(id) : "");
				m.addProperty("text", s(claimed ? Component.translatable("mettle.build.forge.held", holders.get(id))
					: no.isEmpty() ? Component.translatable("mettle.build.forge.forgeable")
					: Component.translatable("mettle.build.forge.locked", skillName(registries, skill), CardConfig.get().forge_skill)));
				m.addProperty("canForgeNow", !claimed && no.isEmpty() && b.shards() >= cost && atShrine);
				all.add(m);
			});
		o.add("pool", all);
		long left = pool.stream().filter(d -> !d.claimed()).count();
		o.addProperty("waiting", left);
		o.addProperty("poolText", s(Component.translatable("mettle.build.forge.pool", left, pool.size())));
		List<ResourceKey<Skill>> skills = ForgeRules.skillsWithUnclaimed(pool);
		boolean nothingMine = pool.stream().noneMatch(d -> !d.claimed() && state.level(d.skill()) >= CardConfig.get().forge_skill);
		o.addProperty("waitingText", left > 0 && nothingMine ? s(Component.translatable("mettle.build.forge.waiting",
			Component.literal(String.join(", ", skills.stream().map(k -> s(skillName(registries, k))).toList())))) : "");
		boolean legend = ForgeRules.canLegend(pool, Integer.MAX_VALUE, b.forges(), b.legend()).isEmpty();
		o.addProperty("legendOffered", legend);
		o.addProperty("legendText", legend ? s(Component.translatable("mettle.build.forge.legend")) : "");
		o.addProperty("cost", cost);
		return o;
	}

	private static JsonElement guide(RegistryAccess registries, CharacterState state, CardsState cards, BuildState b, long gameTime) {
		JsonObject o = new JsonObject();
		BuildState.Guide g = b.guide();
		o.addProperty("on", g.on() && !g.skipped());
		o.addProperty("skipped", g.skipped());
		JsonArray seen = new JsonArray();
		g.seen().forEach(seen::add);
		o.add("seen", seen);
		GuideRules.Hint hint = g.on() && !g.skipped() ? GuideRules.Hint.of(g.current()) : null;
		if (hint == null) {
			o.add("hint", JsonNull.INSTANCE);
			return o;
		}
		JsonObject h = new JsonObject();
		h.addProperty("id", hint.number());
		h.addProperty("key", hint.key());
		h.addProperty("anchor", hint.anchor);
		h.addProperty("ageTicks", Math.max(0, gameTime - g.since()));
		// The first hint carries "Skip the guide".
		h.addProperty("skip", hint == GuideRules.Hint.XP || g.seen().size() <= 1);
		h.addProperty("skipText", s(Component.translatable("mettle.guide.skip")));
		JsonArray buttons = new JsonArray();
		Component text;
		switch (hint) {
			case XP -> {
				ResourceKey<Skill> skill = state.drafts().recent().isEmpty() ? null : state.drafts().recent().getFirst();
				text = Component.translatable("mettle.guide.1", skill == null ? Component.literal("?") : skillName(registries, skill));
			}
			case FIRST_SET -> {
				Identifier k = cards.tiers().keySet().stream().sorted(Comparator.comparing(Identifier::toString)).findFirst().orElse(CardRules.HEX);
				Component name = CardText.keyword(registries, k);
				text = Component.translatable("mettle.guide.7", name);
				JsonObject focus = new JsonObject();
				focus.addProperty("id", "focus");
				focus.addProperty("keyword", k.toString());
				focus.addProperty("text", s(Component.translatable("mettle.guide.focus", name)));
				buttons.add(focus);
				JsonObject no = new JsonObject();
				no.addProperty("id", "not_now");
				no.addProperty("text", s(Component.translatable("mettle.guide.not_now")));
				buttons.add(no);
			}
			case CANT_SLOT -> {
				Optional<CardData> c = cards.cards().stream().filter(x -> !x.slotted() && !x.mythic() && state.level(x.skill()) < x.needs()).findFirst();
				text = c.map(x -> Component.translatable("mettle.guide.8", skillName(registries, x.skill()), x.needs()))
					.orElse(Component.translatable("mettle.guide.8", "?", "?"));
				c.ifPresent(x -> h.addProperty("card", String.valueOf(x.id())));
			}
			case SWAPS -> text = Component.translatable("mettle.guide.9", cards.freeSwaps());
			default -> text = Component.translatable("mettle.guide." + hint.number());
		}
		h.addProperty("text", s(text));
		h.add("buttons", buttons);
		o.add("hint", h);
		return o;
	}

	// ---------------------------------------------------------------- per deck, per card, per set

	/** A deck's build feel counts: tempered and inscribed against the slot rules, the Signature, boons waiting. */
	public static void deck(JsonObject d, CardsState cards, ResourceKey<Skill> skill, CharacterState state) {
		List<CardData> deck = cards.deck(skill);
		CardConfig c = CardConfig.get();
		long tempered = deck.stream().filter(CardData::tempered).count();
		long inscribed = deck.stream().filter(CardData::inscribed).count();
		d.addProperty("tempered", tempered);
		d.addProperty("temperMax", c.temper_per_deck);
		d.addProperty("temperedText", s(Component.translatable("mettle.build.deck_tempered", tempered, c.temper_per_deck)));
		d.addProperty("inscribed", inscribed);
		d.addProperty("inscribeMax", c.inscribe_per_deck);
		d.addProperty("inscribedText", s(Component.translatable("mettle.build.deck_inscribed", inscribed, c.inscribe_per_deck)));
		Long sig = cards.build().signatures().get(skill);
		d.add("signature", sig == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(String.valueOf(sig)));
		d.addProperty("boons", state.drafts().boons(skill));
	}

	/** The set tracker's build feel cues (§6.2, B4): the asleep 6, one card from the next tier, the Focus marker. */
	public static void set(JsonObject o, CardsState cards, Identifier keyword, Set<ResourceKey<Skill>> decks, CharacterState state, RegistryAccess registries) {
		int count = cards.sets().getOrDefault(keyword, 0);
		boolean asleep = CardRules.sixAsleep(count, decks, state::level);
		o.addProperty("asleep", asleep);
		o.addProperty("asleepText", asleep ? s(Component.translatable("mettle.build.asleep", CardText.keyword(registries, keyword), CardConfig.get().six_tier_skill)) : "");
		int next = count < 2 ? 2 : count < 4 ? 4 : count < 6 ? 6 : 0;
		o.addProperty("next", next);
		o.addProperty("oneAway", next > 0 && next - count == 1);
		o.addProperty("nextText", next == 0 ? "" : s(Component.translatable("mettle.build.one_away", count, next)));
		o.addProperty("focus", cards.build().focus().map(keyword::equals).orElse(false));
		o.addProperty("momentum", Cards.data(registries).momentum(keyword));
	}

	/** A card's marks (B9) and its Signature flag; what boons it can take is asked with MettleActions.previewTemper / Inscribe. */
	public static void card(JsonObject o, RegistryAccess registries, CardsState cards, CardData c) {
		JsonObject marks = new JsonObject();
		c.marks().tempered().ifPresentOrElse(t -> {
			JsonObject to = new JsonObject();
			to.addProperty("trait", t.trait().toString());
			to.addProperty("before", t.before());
			to.addProperty("after", t.after());
			Optional<Trait> def = registries.lookupOrThrow(MettleRegistries.TRAIT).getOptional(t.trait());
			to.addProperty("text", s(Component.translatable("mettle.build.mark.tempered", t.by(), skillName(registries, c.skill()), t.level(),
				def.map(Trait::name).orElse(Component.literal(t.trait().getPath())), def.map(x -> x.format(t.before())).orElse(""),
				def.map(x -> x.format(t.after())).orElse(""))));
			to.addProperty("day", t.day());
			marks.add("tempered", to);
		}, () -> marks.add("tempered", JsonNull.INSTANCE));
		c.marks().inscribed().ifPresentOrElse(in -> {
			JsonObject io = new JsonObject();
			io.addProperty("keyword", in.keyword().toString());
			in.was().ifPresent(w -> io.addProperty("was", w.toString()));
			io.addProperty("text", s(Component.translatable("mettle.build.mark.inscribed", CardText.keyword(registries, in.keyword()), in.by(),
				skillName(registries, c.skill()), in.level())));
			io.addProperty("day", in.day());
			marks.add("inscribed", io);
		}, () -> marks.add("inscribed", JsonNull.INSTANCE));
		JsonArray signed = new JsonArray();
		c.marks().signed().forEach(x -> signed.add(s(x.to() < 0 ? Component.translatable("mettle.build.mark.signing", x.by(), x.from())
			: Component.translatable("mettle.build.mark.signed", x.by(), x.from(), x.to()))));
		marks.add("signed", signed);
		o.add("marks", marks);
		o.addProperty("signature", cards.build().signatures().containsValue(c.id()));
	}

	// ---------------------------------------------------------------- previews (§6.1)

	/** The preview of a hand's offer: where it goes, set deltas, you-lines (at most three short lines in {@code lines}). */
	public static JsonElement offer(RegistryAccess registries, CharacterState state, CardsState cards, DraftState.Card offer, long gameTime) {
		if (offer.rarity() == DraftState.Rarity.MYTHIC) {
			return JsonNull.INSTANCE;
		}
		Optional<Perk> perk = registries.lookupOrThrow(MettleRegistries.PERK).getOptional(offer.perk());
		if (perk.isEmpty()) {
			return JsonNull.INSTANCE;
		}
		BuildPreview.Context ctx = BuildFeel.context(registries, state, cards, gameTime);
		return BuildPreview.of(ctx, BuildPreview.asCard(offer, perk.get())).map(p -> (JsonElement) preview(registries, p, BuildPreview.asCard(offer, perk.get())))
			.orElse(JsonNull.INSTANCE);
	}

	/** A preview as the overlay draws it. */
	public static JsonObject preview(RegistryAccess registries, BuildPreview.Preview p, CardData card) {
		JsonObject o = new JsonObject();
		BuildPreview.Placement pl = p.placement();
		JsonObject place = new JsonObject();
		place.addProperty("kind", pl.kind().name().toLowerCase(java.util.Locale.ROOT));
		place.addProperty("slot", pl.slot());
		place.addProperty("pointsDelta", pl.pointsDelta());
		pl.replaces().ifPresent(r -> {
			place.addProperty("replaces", String.valueOf(r.id()));
			place.addProperty("replacesName", s(cardName(registries, r)));
		});
		Component cost = pl.kind() != BuildPreview.Place.REPLACE ? null
			: pl.free().map(f -> (Component) Component.translatable("mettle.preview.cost." + f.name().toLowerCase(java.util.Locale.ROOT)))
				.orElse(Component.translatable("mettle.preview.cost.rebuild", pl.fee()));
		place.addProperty("cost", cost == null ? "" : s(cost));
		place.addProperty("fee", pl.fee());
		Component where = switch (pl.kind()) {
			case FILL -> Component.translatable("mettle.preview.fills", pl.slot() + 1);
			case REPLACE -> Component.translatable("mettle.preview.replaces", pl.replaces().map(r -> cardName(registries, r)).orElse(Component.literal("?")),
				(pl.pointsDelta() >= 0 ? "+" : "") + pl.pointsDelta()).append(" · ").append(cost);
			case WAITS -> Component.translatable("mettle.preview.waits", skillName(registries, card.skill()), pl.needs());
			case BLOCKED -> Component.translatable("mettle.preview.blocked", pl.refusal().map(r -> (Component) Component.translatable(r.translationKey())).orElse(Component.empty()));
			case CHANGE -> Component.translatable("mettle.preview.change", cardName(registries, card));
		};
		place.addProperty("text", s(where));
		o.add("placement", place);
		Registry<Keyword> keywords = registries.lookupOrThrow(MettleRegistries.KEYWORD);
		JsonArray sets = new JsonArray();
		String setLine = null;
		for (BuildPreview.SetDelta d : p.sets()) {
			JsonObject so = new JsonObject();
			Component name = CardText.keyword(registries, d.keyword());
			so.addProperty("keyword", d.keyword().toString());
			so.addProperty("name", s(name));
			so.addProperty("from", d.from());
			so.addProperty("to", d.to());
			so.addProperty("tierFrom", d.tierFrom());
			so.addProperty("tierTo", d.tierTo());
			so.addProperty("opens", d.opens());
			so.addProperty("breaks", d.breaks());
			so.addProperty("asleep", d.asleep());
			Component text;
			if (d.opens()) {
				Component desc = keywords.getOptional(d.keyword()).map(k -> k.sets().get(d.tierTo())).map(x -> x.summary().orElse(x.description())).orElse(Component.empty());
				text = Component.translatable("mettle.preview.opens", name, d.from(), d.to(), name, d.tierTo(), desc);
			} else if (d.breaks()) {
				text = Component.translatable("mettle.preview.breaks", name, d.from(), d.to(), name, d.tierFrom());
			} else if (d.asleep()) {
				text = Component.translatable("mettle.preview.asleep", name, d.from(), d.to(), CardConfig.get().six_tier_skill);
			} else {
				text = Component.translatable("mettle.preview.set", name, d.from(), d.to());
			}
			so.addProperty("text", s(text));
			if (setLine == null && card.keywords().contains(d.keyword()) || setLine == null && (d.opens() || d.breaks())) {
				setLine = s(text);
			}
			sets.add(so);
		}
		o.add("sets", sets);
		JsonArray traits = new JsonArray();
		String youLine = null;
		Registry<Trait> traitReg = registries.lookupOrThrow(MettleRegistries.TRAIT);
		for (BuildPreview.TraitDelta t : p.traits()) {
			JsonObject to = new JsonObject();
			to.addProperty("trait", t.trait().toString());
			to.addProperty("from", t.from());
			to.addProperty("to", t.to());
			to.addProperty("cap", t.cap());
			Optional<Trait> def = traitReg.getOptional(t.trait());
			String text = def.map(x -> s(Component.translatable("mettle.preview.trait", x.name(), x.format(t.from()), x.format(t.to()), x.format(t.cap())))).orElse("");
			to.addProperty("text", text);
			if (youLine == null) {
				youLine = text;
			}
			traits.add(to);
		}
		o.add("traits", traits);
		BynameText words = BynameText.of(registries);
		if (p.renames()) {
			String text = s(Component.translatable("mettle.preview.byname", Bynames.render(p.after().get(), words)));
			o.addProperty("byname", text);
			youLine = text;
		} else {
			o.add("byname", JsonNull.INSTANCE);
		}
		o.addProperty("focus", p.focus());
		JsonArray lines = new JsonArray();
		lines.add(s(where));
		if (setLine != null) {
			lines.add(setLine);
		}
		if (youLine != null) {
			lines.add(youLine);
		} else if (p.focus()) {
			lines.add(s(Component.translatable("mettle.preview.focus")));
		}
		o.add("lines", lines);
		return o;
	}

	/** Which decks hold each keyword's slotted, non-dormant cards (for the tracker's asleep cue). */
	public static Map<Identifier, Set<ResourceKey<Skill>>> decks(CardsState cards) {
		Map<Identifier, Set<ResourceKey<Skill>>> out = new HashMap<>();
		for (CardData c : cards.slotted()) {
			if (!cards.isDormant(c.id())) {
				c.keywords().forEach(k -> out.computeIfAbsent(k, x -> new HashSet<>()).add(c.place().deck().orElse(c.skill())));
			}
		}
		return out;
	}
}
