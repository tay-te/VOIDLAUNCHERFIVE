package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.PerkText;
import dev.voidmc.mettle.character.cards.CardConfig;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Ceilings;
import dev.voidmc.mettle.character.cards.CardText;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * The cards part of the overlay state (docs/CARDS.md, OVERLAY.md "Cards v2"): the binder, each skill's deck with its
 * slots, the set tracker, trait totals against their caps, free swaps and rebuild costs, and the rolled parts of a draft
 * hand. Everything is read from the synced card cache and the data registries.
 */
public final class MettleCards {
	private static dev.voidmc.mettle.core.net.MettlePayloads.@org.jspecify.annotations.Nullable CardSitting sitting;

	private MettleCards() {
	}

	private static dev.voidmc.mettle.core.net.MettlePayloads.@org.jspecify.annotations.Nullable TradeView trade;

	private static java.util.List<dev.voidmc.mettle.core.net.MettlePayloads.HallEntry> hall = java.util.List.of();
	private static dev.voidmc.mettle.core.net.MettlePayloads.@org.jspecify.annotations.Nullable MythicReveal reveal;
	private static long revealAt;

	public static void hall(dev.voidmc.mettle.core.net.MettlePayloads.HallOfFirsts h) {
		hall = h.entries();
	}

	/** The Hall of Firsts as the server last sent it (who holds which Mythic). */
	public static java.util.List<dev.voidmc.mettle.core.net.MettlePayloads.HallEntry> hallEntries() {
		return hall;
	}

	/** Whether the player is at a Card Shrine (a shrine sitting is open): where forging happens. */
	public static boolean atShrine() {
		var s = sitting;
		return s != null && s.kind() == 0;
	}

	/** Whether a Mythic reveal is showing ({@code reveal}, 30 s), for MettleState.revision. */
	public static boolean revealing() {
		return reveal != null && net.minecraft.util.Util.getMillis() - revealAt < 30_000;
	}

	public static void reveal(dev.voidmc.mettle.core.net.MettlePayloads.MythicReveal r) {
		reveal = r;
		revealAt = net.minecraft.util.Util.getMillis();
	}

	/** The trade as the server last showed it to this player, or null. */
	public static void trade(dev.voidmc.mettle.core.net.MettlePayloads.@org.jspecify.annotations.Nullable TradeView view) {
		trade = view;
	}

	/** The rebuild sitting the server says is open (a Card Shrine or a trainer), or null. */
	public static void sitting(dev.voidmc.mettle.core.net.MettlePayloads.@org.jspecify.annotations.Nullable CardSitting s) {
		sitting = s;
	}

	private static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	/** {@code state.cards}. */
	public static JsonObject build(Minecraft mc, RegistryAccess registries) {
		Player player = mc.player;
		CharacterState character = CharacterStore.get(player);
		CardsState cards = CardStore.get(player);
		CardConfig config = CardConfig.get();
		CardRules.Attributes attributes = Cards.attributes(registries, character);
		int wits = Cards.wits(registries, character);
		long now = player.level().getGameTime();
		JsonObject root = new JsonObject();
		root.addProperty("version", cards.version());

		JsonObject binder = new JsonObject();
		// Mythics always fit: the count against max leaves them out (CardRules.binderCount); "cards" lists them all.
		binder.addProperty("count", CardRules.binderCount(cards.cards()));
		binder.addProperty("max", config.binder_max);
		JsonArray all = new JsonArray();
		cards.cards().stream().sorted(Comparator.comparingLong(CardData::id).reversed())
			.forEach(c -> all.add(card(registries, character, cards, c, attributes, now)));
		binder.add("cards", all);
		root.add("binder", binder);

		Registry<Skill> skills = registries.lookupOrThrow(MettleRegistries.SKILL);
		JsonArray decks = new JsonArray();
		skills.listElements().filter(r -> r.value().released()).sorted(Comparator.comparingInt(r -> r.value().order())).forEach(ref ->
			decks.add(deck(registries, character, cards, ref.key(), ref.value(), wits)));
		root.add("decks", decks);

		root.add("sets", sets(registries, cards, character));
		root.add("traits", traits(registries, cards));
		root.add("ceilings", ceilings(registries, cards));

		JsonObject costs = new JsonObject();
		JsonArray fees = new JsonArray();
		int[][] bands = {{2, 5}, {6, 11}, {12, 17}, {18, 20}};
		for (int i = 0; i < bands.length; i++) {
			JsonObject f = new JsonObject();
			f.addProperty("needsFrom", bands[i][0]);
			f.addProperty("needsTo", bands[i][1]);
			f.addProperty("bits", config.rebuild_fees[i]);
			f.addProperty("emeralds", config.rebuild_emeralds[i]);
			fees.add(f);
		}
		costs.add("fees", fees);
		costs.addProperty("mythicBits", config.mythic_fee);
		costs.addProperty("mythicEmeralds", config.rebuild_emeralds[4]);
		costs.addProperty("freeSwaps", cards.freeSwaps());
		costs.addProperty("maxFreeSwaps", config.free_swaps_max);
		JsonArray freeRebuilds = new JsonArray();
		cards.freeRebuilds().forEach(k -> freeRebuilds.add(k.identifier().toString()));
		costs.add("freeRebuilds", freeRebuilds);
		costs.addProperty("freeBelowLevel", config.free_rebuild_below_level);
		costs.addProperty("lowLevel", character.characterLevel() < config.free_rebuild_below_level);
		costs.addProperty("cityMasterScarChance", config.city_master_scar_chance);
		root.add("costs", costs);

		JsonObject pins = new JsonObject();
		JsonArray pinned = new JsonArray();
		cards.pins().forEach(k -> pinned.add(k.toString()));
		pins.add("keywords", pinned);
		pins.addProperty("max", config.active_sets);
		// The server counts days by game time (Cards.day); a pin day ahead of today was counted on the old clock.
		long today = player.level().getGameTime() / 24000L;
		pins.addProperty("canChange", cards.pinDay() < 0 || cards.pinDay() > today || today - cards.pinDay() >= config.set_pin_cooldown_days);
		root.add("pins", pins);

		JsonArray keywords = new JsonArray();
		registries.lookupOrThrow(MettleRegistries.KEYWORD).listElements().sorted(Comparator.comparing(r -> r.key().identifier().toString())).forEach(r -> {
			JsonObject k = new JsonObject();
			k.addProperty("id", r.key().identifier().toString());
			k.addProperty("name", r.value().name().getString());
			k.addProperty("icon", r.value().icon().toString());
			k.addProperty("colour", hex(r.value().colour()));
			keywords.add(k);
		});
		root.add("keywords", keywords);
		// The open rebuild sitting (CARDS.md §7.3): where the player is rebuilding, and what it can take out.
		var s = sitting;
		root.addProperty("atShrine", s != null && s.kind() == 0);
		if (s != null) {
			JsonObject so = new JsonObject();
			so.addProperty("kind", s.kind() == 0 ? "shrine" : "trainer");
			JsonArray allowed = new JsonArray();
			s.skills().forEach(k -> allowed.add(k.identifier().toString()));
			so.add("skills", allowed);
			so.addProperty("cap", s.cap());
			so.addProperty("master", s.master());
			so.addProperty("coin", s.coin());
			root.add("sitting", so);
		} else {
			root.add("sitting", JsonNull.INSTANCE);
		}
		root.addProperty("attuneTicks", config.attune_ticks);
		root.add("trade", trade(registries, character, attributes));
		root.add("mythics", mythics(registries));
		var r = reveal;
		if (r != null && net.minecraft.util.Util.getMillis() - revealAt < 30_000) {
			JsonObject ro = mythic(registries, r.mythic());
			ro.addProperty("how", r.how());
			ro.addProperty("ageMs", net.minecraft.util.Util.getMillis() - revealAt);
			root.add("reveal", ro);
		} else {
			root.add("reveal", JsonNull.INSTANCE);
		}
		// Name cards (§13.7): the Mythics every player in sight holds, from the public sync.
		JsonArray people = new JsonArray();
		for (var p : mc.level.players()) {
			java.util.List<Identifier> held = p.getAttached(dev.voidmc.mettle.character.cards.Mythics.PUBLIC);
			JsonObject po = new JsonObject();
			po.addProperty("name", p.getGameProfile().name());
			JsonArray ms = new JsonArray();
			if (held != null) {
				held.forEach(id -> ms.add(mythic(registries, id)));
			}
			po.add("mythics", ms);
			// Build feel (B2): the byname under the name, its tier pips, the crest and the legend frame.
			var renown = p.getAttached(dev.voidmc.mettle.character.cards.BuildFeel.RENOWN);
			if (renown != null && renown.byname().isPresent()) {
				po.add("byname", MettleBuild.byname(renown.byname().get(), dev.voidmc.mettle.character.cards.BynameText.of(registries)));
			} else {
				po.add("byname", JsonNull.INSTANCE);
			}
			po.add("crest", renown == null || renown.crest().isEmpty() ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(renown.crest().get().toString()));
			po.add("legend", renown == null || renown.legend() == 0 ? JsonNull.INSTANCE : MettleBuild.legend(renown.legend(), renown.legendOf()));
			people.add(po);
		}
		root.add("players", people);
		// Build feel (docs/BUILD_FEEL.md, OVERLAY.md "Build feel").
		root.add("build", MettleBuild.build(mc, registries));
		return root;
	}

	private static JsonObject mythic(RegistryAccess registries, Identifier id) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id.toString());
		registries.lookupOrThrow(MettleRegistries.MYTHIC).getOptional(id).ifPresent(m -> {
			o.addProperty("name", m.name().getString());
			o.addProperty("lore", m.lore().getString());
			o.addProperty("effect", m.effect().getString());
			o.addProperty("deed", m.deed().getString());
			o.addProperty("source", m.source().getSerializedName());
			o.addProperty("icon", m.icon().toString());
			m.skill().ifPresent(s -> o.addProperty("skill", s.identifier().toString()));
		});
		return o;
	}

	/** {@code cards.mythics}: the Hall of Firsts, every Mythic with its holder (or none yet) and copies. */
	private static JsonObject mythics(RegistryAccess registries) {
		JsonObject out = new JsonObject();
		JsonArray all = new JsonArray();
		int drawnWaiting = 0;
		Map<Identifier, dev.voidmc.mettle.core.net.MettlePayloads.HallEntry> byId = new HashMap<>();
		hall.forEach(e -> byId.put(e.mythic(), e));
		for (var ref : registries.lookupOrThrow(MettleRegistries.MYTHIC).listElements().sorted(Comparator.comparing(r -> r.key().identifier().toString())).toList()) {
			JsonObject o = mythic(registries, ref.key().identifier());
			var e = byId.get(ref.key().identifier());
			boolean claimed = e != null && !e.holder().isEmpty();
			o.addProperty("claimed", claimed);
			o.addProperty("holder", claimed ? e.holder() : "");
			o.addProperty("day", claimed ? e.day() : -1);
			o.addProperty("copies", e == null ? 0 : e.copies());
			if (ref.value().source() == dev.voidmc.mettle.api.Mythic.Source.DRAWN && !claimed) {
				drawnWaiting++;
			}
			all.add(o);
		}
		out.add("all", all);
		out.addProperty("drawnWaiting", drawnWaiting);
		out.addProperty("slots", CardConfig.get().mythic_slots);
		return out;
	}

	/** {@code cards.trade}: null when no trade is open or asked for (OVERLAY.md "Trading"). */
	private static JsonElement trade(RegistryAccess registries, CharacterState character, CardRules.Attributes attributes) {
		var t = trade;
		if (t == null || !t.open() && t.partner().isEmpty()) {
			return JsonNull.INSTANCE;
		}
		JsonObject o = new JsonObject();
		o.addProperty("open", t.open());
		o.addProperty("partner", t.partner());
		o.addProperty("asking", !t.open());
		o.add("me", tradeSide(registries, character, attributes, t.me(), false));
		o.add("them", tradeSide(registries, character, attributes, t.them(), true));
		o.addProperty("countdownSeconds", t.countdown() < 0 ? -1 : (t.countdown() + 19) / 20);
		o.addProperty("coin", t.coin());
		o.addProperty("maxCards", dev.voidmc.mettle.character.cards.TradeRules.MAX_CARDS);
		o.addProperty("maxItems", dev.voidmc.mettle.character.cards.TradeRules.MAX_ITEMS);
		return o;
	}

	private static JsonObject tradeSide(RegistryAccess registries, CharacterState character, CardRules.Attributes attributes,
		dev.voidmc.mettle.core.net.MettlePayloads.TradeSide side, boolean incoming) {
		JsonObject o = new JsonObject();
		JsonArray cards = new JsonArray();
		for (CardData c : side.cards()) {
			JsonObject card = new JsonObject();
			card.addProperty("id", String.valueOf(c.id()));
			card.addProperty("code", Cards.printId(registries, c));
			Optional<Perk> perk = registries.lookupOrThrow(MettleRegistries.PERK).getOptional(c.perk());
			common(card, registries, c.perk(), perk, c.skill(), c.rank(), c.rarity(), c.keywords(), c.traits(), c.needs(), attributes);
			card.addProperty("scars", c.scars());
			card.addProperty("owners", c.history().owners());
			card.addProperty("drawnBy", c.drawn().name());
			if (incoming) {
				// What receiving it means for this player: its requirement against their skill, and a day of attuning.
				int level = character.level(c.skill());
				card.addProperty("warning", Component.translatable("mettle.trade.warning", skillName(registries, c.skill()), c.needs(), level,
					CardConfig.get().attune_ticks / 1200).getString());
				card.addProperty("meetsRequirement", level >= c.needs());
			}
			cards.add(card);
		}
		o.add("cards", cards);
		JsonArray items = new JsonArray();
		for (net.minecraft.world.item.ItemStack st : side.items()) {
			JsonObject it = new JsonObject();
			it.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
			it.addProperty("name", st.getHoverName().getString());
			it.addProperty("count", st.getCount());
			items.add(it);
		}
		o.add("items", items);
		o.addProperty("coin", side.coin());
		o.addProperty("locked", side.locked());
		return o;
	}

	/** One card as the binder, the deck and the hand draw it. */
	public static JsonObject card(RegistryAccess registries, CharacterState character, CardsState cards, CardData c, CardRules.Attributes attributes, long now) {
		JsonObject o = new JsonObject();
		o.addProperty("id", String.valueOf(c.id()));
		o.addProperty("code", Cards.printId(registries, c));
		Optional<Perk> perk = registries.lookupOrThrow(MettleRegistries.PERK).getOptional(c.perk());
		common(o, registries, c.perk(), perk, c.skill(), c.rank(), c.rarity(), c.keywords(), c.traits(), c.needs(), attributes);
		o.addProperty("mythic", c.mythic());
		c.mythicId().ifPresent(id -> {
			JsonObject m = mythic(registries, id);
			o.addProperty("name", m.has("name") ? m.get("name").getAsString() : id.getPath());
			o.addProperty("lore", m.has("lore") ? m.get("lore").getAsString() : "");
			o.addProperty("description", m.has("effect") ? m.get("effect").getAsString() : "");
			o.addProperty("icon", m.has("icon") ? m.get("icon").getAsString() : "");
			o.addProperty("kind", "mythic");
		});
		int level = character.level(c.skill());
		boolean attuned = c.attuned(now);
		o.addProperty("attuned", attuned);
		o.addProperty("attuningMinutes", attuned ? 0 : (int) Math.ceil((c.attunedAt() - now) / 1200.0));
		o.addProperty("place", c.place().kind().getSerializedName());
		o.addProperty("slot", c.slotted() ? c.place().index() : -1);
		o.addProperty("deck", c.place().deck().orElse(c.skill()).identifier().toString());
		boolean dormant = cards.isDormant(c.id());
		o.addProperty("dormant", dormant);
		if (dormant) {
			o.addProperty("dormantReason", dormantReason(registries, character, cards, c, perk));
		}
		// Whether the card could go in its deck now (in the binder, attuned, level, a free legal slot) and, if not, why.
		Optional<CardRules.Refusal> no = c.slotted() ? Optional.of(CardRules.Refusal.NOT_IN_BINDER)
			: c.mythic() ? (cards.slotted().stream().filter(CardData::mythic).count() >= CardConfig.get().mythic_slots
				? Optional.of(CardRules.Refusal.TWO_MYTHICS) : Optional.empty())
			: CardRules.canSlot(c, -1, cards.deck(c.skill()), level, Cards.wits(registries, character), now,
				registries.lookupOrThrow(MettleRegistries.PERK)::getOptional, cards.slotted(), CardRules.extraSlots(cards.cards()));
		o.addProperty("canSlot", no.isEmpty());
		if (no.isPresent() && !c.slotted()) {
			o.addProperty("lockedReason", no.get() == CardRules.Refusal.NEEDS_LEVEL
				? Component.translatable("mettle.cards.slots_at", skillName(registries, c.skill()), c.needs()).getString()
				: no.get() == CardRules.Refusal.ATTUNING ? Component.translatable("mettle.cards.attuning", o.get("attuningMinutes").getAsInt()).getString()
				: Component.translatable(no.get().translationKey()).getString());
		} else {
			o.add("lockedReason", JsonNull.INSTANCE);
		}
		o.addProperty("meetsRequirement", level >= c.needs());
		o.addProperty("scars", c.scars());
		if (c.slotted()) {
			// What taking it out costs at a rebuild (a city master's hand at an open master sitting): fee and the scar.
			var s = sitting;
			CardRules.Scar scar = CardRules.scar(c, s != null && s.master());
			JsonObject rb = new JsonObject();
			rb.addProperty("bits", dormant ? 0 : CardRules.feeBits(c.needs()));
			rb.addProperty("emeralds", dormant ? 0 : CardRules.feeEmeralds(c.needs()));
			rb.addProperty("spared", dormant || scar.spared());
			if (!dormant && scar.loses().isPresent()) {
				CardData.TraitRoll t = scar.loses().get();
				JsonObject lose = new JsonObject();
				lose.addProperty("id", t.trait().toString());
				lose.addProperty("text", CardText.trait(registries, t, Optional.empty()).getString());
				rb.add("loses", lose);
			} else {
				rb.add("loses", JsonNull.INSTANCE);
			}
			rb.addProperty("overCap", s != null && c.needs() > s.cap());
			o.add("rebuild", rb);
		}
		JsonObject drawn = new JsonObject();
		drawn.addProperty("by", c.drawn().name());
		drawn.addProperty("level", c.drawn().level());
		drawn.addProperty("day", c.drawn().day());
		drawn.addProperty("millis", c.drawn().millis());
		drawn.addProperty("how", c.drawn().how().getSerializedName());
		drawn.addProperty("howText", Component.translatable("mettle.cards.how." + c.drawn().how().getSerializedName()).getString());
		drawn.addProperty("text", Component.translatable("mettle.cards.history", c.drawn().name(), skillName(registries, c.skill()), c.drawn().level(),
			c.drawn().day(), c.history().owners(), c.scars()).getString());
		o.add("drawn", drawn);
		o.addProperty("owners", c.history().owners());
		MettleBuild.card(o, registries, cards, c);
		JsonArray last = new JsonArray();
		c.history().last().forEach(h -> {
			JsonObject x = new JsonObject();
			x.addProperty("name", h.name());
			x.addProperty("day", h.day());
			last.add(x);
		});
		o.add("lastOwners", last);
		return o;
	}

	/** The rolled parts of a hand's offer, added to the v1 card fields: keywords, traits, requirement, rank name. */
	public static void offer(JsonObject o, RegistryAccess registries, CharacterState character, DraftState.Card card) {
		Optional<Perk> perk = registries.lookupOrThrow(MettleRegistries.PERK).getOptional(card.perk());
		ResourceKey<Skill> skill = perk.map(Perk::skill).orElse(null);
		if (skill == null) {
			return;
		}
		common(o, registries, card.perk(), perk, skill, card.rank(), card.rarity(), card.keywords(), card.traits(), card.needs(),
			Cards.attributes(registries, character));
		int level = character.level(skill);
		o.addProperty("meetsRequirement", level >= card.needs());
		o.addProperty("slotsAt", level >= card.needs() ? "" : Component.translatable("mettle.cards.slots_at", skillName(registries, skill), card.needs()).getString());
		// What keeping it would do to the build (BUILD_FEEL.md §6.1).
		Player me = Minecraft.getInstance().player;
		if (me != null) {
			o.add("preview", MettleBuild.offer(registries, character, CardStore.get(me), card, me.level().getGameTime()));
		}
	}

	private static void common(JsonObject o, RegistryAccess registries, ResourceKey<Perk> key, Optional<Perk> perk, ResourceKey<Skill> skill, int rank,
		DraftState.Rarity rarity, List<Identifier> keywords, List<CardData.TraitRoll> traits, int needs, CardRules.Attributes attributes) {
		o.addProperty("perk", key.identifier().toString());
		o.addProperty("skill", skill.identifier().toString());
		Optional<Skill> s = registries.lookupOrThrow(MettleRegistries.SKILL).getOptional(skill);
		o.addProperty("skillName", s.map(x -> x.name().getString()).orElse(skill.identifier().getPath()));
		o.addProperty("colour", hex(s.map(Skill::colour).orElse(Skill.NO_COLOUR)));
		o.addProperty("rarity", rarity.getSerializedName());
		o.addProperty("rarityName", Component.translatable("mettle.draft.rarity." + rarity.getSerializedName()).getString());
		o.addProperty("frame", rarity.hex());
		o.addProperty("rank", rank);
		o.addProperty("icon", Perk.icon(key).toString());
		perk.ifPresent(p -> {
			o.addProperty("name", CardText.name(p, rank).getString());
			o.addProperty("baseName", p.name().getString());
			o.addProperty("description", PerkText.description(p, rank).getString());
			o.addProperty("kind", p.kind().getSerializedName());
			o.addProperty("tier", p.tier());
			o.addProperty("tierName", p.kind() == Perk.Kind.MASTERY ? "M" : CardText.roman(Math.max(1, p.tier())));
			o.addProperty("path", p.path());
			o.addProperty("cursed", p.kind() == Perk.Kind.CURSED);
			p.downside().ifPresentOrElse(d -> o.addProperty("downside", d.getString()), () -> o.add("downside", JsonNull.INSTANCE));
		});
		o.addProperty("needs", needs);
		o.addProperty("needsText", Component.translatable("mettle.cards.needs_line", s.map(x -> x.name().getString()).orElse(""), needs).getString());
		JsonArray kws = new JsonArray();
		for (Identifier k : keywords) {
			JsonObject kw = new JsonObject();
			kw.addProperty("id", k.toString());
			kw.addProperty("name", CardText.keyword(registries, k).getString());
			kw.addProperty("icon", registries.lookupOrThrow(MettleRegistries.KEYWORD).getOptional(k).map(x -> x.icon().toString()).orElse(""));
			kws.add(kw);
		}
		o.add("keywords", kws);
		JsonArray ts = new JsonArray();
		Registry<Trait> traitRegistry = registries.lookupOrThrow(MettleRegistries.TRAIT);
		for (CardData.TraitRoll t : traits) {
			JsonObject to = new JsonObject();
			to.addProperty("id", t.trait().toString());
			to.addProperty("value", t.value());
			traitRegistry.getOptional(t.trait()).ifPresent(def -> {
				double boosted = CardRules.effective(def, t.value(), attributes);
				to.addProperty("name", def.name().getString());
				to.addProperty("kind", def.kind().getSerializedName());
				to.addProperty("attribute", def.kind().attribute.getSerializedName());
				to.addProperty("valueText", def.format(t.value()));
				to.addProperty("boosted", boosted);
				to.addProperty("boostedText", def.format(boosted));
				to.addProperty("cap", CardRules.cardCap(def));
				to.addProperty("description", CardText.sentence(def, t.value()).getString());
				def.arsenal().ifPresent(a -> to.addProperty("arsenal", a.getString().replace("%s", def.format(t.value()))));
			});
			ts.add(to);
		}
		o.add("traits", ts);
	}

	private static String dormantReason(RegistryAccess registries, CharacterState character, CardsState cards, CardData c, Optional<Perk> perk) {
		ResourceKey<Skill> skill = c.place().deck().orElse(c.skill());
		int level = character.level(skill);
		if (c.place().index() >= CardRules.slots(level, Cards.wits(registries, character))) {
			return Component.translatable("mettle.cards.dormant.slots").getString();
		}
		if (level < c.needs()) {
			return Component.translatable("mettle.cards.dormant.level").getString();
		}
		if (perk.isPresent() && perk.get().kind() == Perk.Kind.MASTERY) {
			boolean otherActive = cards.deck(skill).stream().anyMatch(x -> x.id() != c.id() && !cards.isDormant(x.id())
				&& registries.lookupOrThrow(MettleRegistries.PERK).getOptional(x.perk()).map(p -> p.kind() == Perk.Kind.MASTERY).orElse(false));
			return Component.translatable(otherActive ? "mettle.cards.dormant.mastery" : "mettle.cards.dormant.side").getString();
		}
		return "";
	}

	private static JsonObject deck(RegistryAccess registries, CharacterState character, CardsState cards, ResourceKey<Skill> key, Skill skill, int wits) {
		int level = character.level(key);
		int base = CardRules.baseSlots(level);
		int slots = CardRules.slots(level, wits);
		if (slots > 0) {
			// The Polymath Mythic adds a slot to every deck.
			slots += CardRules.extraSlots(cards.cards());
		}
		JsonObject d = new JsonObject();
		d.addProperty("skill", key.identifier().toString());
		d.addProperty("name", skill.name().getString());
		d.addProperty("colour", hex(skill.colour()));
		d.addProperty("level", level);
		d.addProperty("baseSlots", base);
		d.addProperty("witsSlots", slots - base);
		d.addProperty("slots", slots);
		List<CardData> deck = cards.deck(key);
		int shown = Math.max(CardRules.slots(Math.max(level, 20), 20), deck.stream().mapToInt(c -> c.place().index() + 1).max().orElse(0));
		JsonArray row = new JsonArray();
		Map<Integer, CardData> at = new HashMap<>();
		deck.forEach(c -> at.put(c.place().index(), c));
		int[] gates = CardConfig.get().wits_slots;
		for (int i = 0; i < shown; i++) {
			JsonObject s = new JsonObject();
			s.addProperty("index", i);
			CardData c = at.get(i);
			s.add("card", c == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(String.valueOf(c.id())));
			s.addProperty("dormant", c != null && cards.isDormant(c.id()));
			boolean open = i < slots;
			s.addProperty("open", open);
			if (!open) {
				// Which comes first for this slot: a skill level, or Wits.
				String reason;
				int witsNeeded = i - base < gates.length && i >= base ? gates[Math.max(0, i - base)] : 0;
				int levelNeeded = 0;
				for (int l = level + 1; l <= 20; l++) {
					if (CardRules.slots(l, wits) > i) {
						levelNeeded = l;
						break;
					}
				}
				if (levelNeeded > 0) {
					reason = Component.translatable("mettle.cards.opens_at", skill.name(), levelNeeded).getString();
				} else if (witsNeeded > 0 && base > 0) {
					reason = Component.translatable("mettle.cards.opens_wits", witsNeeded).getString();
				} else {
					reason = Component.translatable("mettle.cards.opens_wits", gates[gates.length - 1]).getString();
				}
				s.addProperty("locked", reason);
			}
			row.add(s);
		}
		d.add("row", row);
		d.addProperty("nextSlotAt", CardRules.nextSlotAt(level));
		d.addProperty("freeRebuild", cards.freeRebuilds().contains(key));
		d.addProperty("rebuildHint", Component.translatable("mettle.cards.rebuild_hint", skill.name()).getString());
		MettleBuild.deck(d, cards, key, character);
		return d;
	}

	private static JsonArray sets(RegistryAccess registries, CardsState cards, CharacterState character) {
		JsonArray out = new JsonArray();
		Map<Identifier, Set<ResourceKey<Skill>>> skillsOf = new HashMap<>();
		for (CardData c : cards.slotted()) {
			if (!cards.isDormant(c.id())) {
				c.keywords().forEach(k -> skillsOf.computeIfAbsent(k, x -> new HashSet<>()).add(c.place().deck().orElse(c.skill())));
			}
		}
		registries.lookupOrThrow(MettleRegistries.KEYWORD).listElements().sorted(Comparator.comparing(r -> r.key().identifier().toString())).forEach(r -> {
			Identifier id = r.key().identifier();
			Keyword k = r.value();
			int count = cards.sets().getOrDefault(id, 0);
			int decks = skillsOf.getOrDefault(id, Set.of()).size();
			// Build feel's gate: a 6 counts only once two of its decks reach six_tier_skill (BUILD_FEEL.md §1.2b).
			int reached = CardRules.tier(count, skillsOf.getOrDefault(id, Set.of()), character::level);
			int active = cards.tier(id);
			JsonObject o = new JsonObject();
			o.addProperty("id", id.toString());
			o.addProperty("name", k.name().getString());
			o.addProperty("theme", k.theme().getString());
			o.addProperty("icon", k.icon().toString());
			o.addProperty("colour", hex(k.colour()));
			o.addProperty("count", count);
			o.addProperty("decks", decks);
			o.addProperty("tier", reached);
			o.addProperty("active", active > 0);
			o.addProperty("pinned", cards.pins().contains(id));
			JsonArray bonuses = new JsonArray();
			for (int tier : new int[] {2, 4, 6}) {
				Keyword.SetBonus b = k.sets().get(tier);
				if (b == null) {
					continue;
				}
				JsonObject bo = new JsonObject();
				bo.addProperty("tier", tier);
				bo.addProperty("description", b.description().getString());
				bo.addProperty("reached", reached >= tier);
				bo.addProperty("active", active >= tier);
				bonuses.add(bo);
			}
			o.add("bonuses", bonuses);
			MettleBuild.set(o, cards, id, skillsOf.getOrDefault(id, Set.of()), character, registries);
			out.add(o);
		});
		return out;
	}

	/**
	 * The whole-character ceilings (docs/CARDS.md §14) for this deck: each capped stat with what the cards would add
	 * without it, the ceiling, whether it bites, and a line to show ("Movement from cards: up to +47 %, capped at +35 %").
	 */
	private static JsonArray ceilings(RegistryAccess registries, CardsState cards) {
		JsonArray out = new JsonArray();
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		Ceilings.potential(Ceilings.Build.of(cards, perks::getOptional)).forEach((stat, potential) -> {
			JsonObject o = new JsonObject();
			o.addProperty("stat", stat.key);
			o.addProperty("potential", potential);
			o.addProperty("ceiling", stat.ceiling);
			boolean capped = potential > stat.ceiling + 1e-9;
			o.addProperty("capped", capped);
			o.addProperty("text", capped ? Ceilings.line(stat, potential).getString() : "");
			out.add(o);
		});
		return out;
	}

	private static JsonArray traits(RegistryAccess registries, CardsState cards) {
		JsonArray out = new JsonArray();
		Registry<Trait> registry = registries.lookupOrThrow(MettleRegistries.TRAIT);
		Map<Ceilings.Stat, Double> capped = Ceilings.capped(Ceilings.Build.of(cards, registries.lookupOrThrow(MettleRegistries.PERK)::getOptional));
		cards.traits().forEach((id, total) -> registry.getOptional(id).ifPresent(def -> {
			JsonObject o = new JsonObject();
			o.addProperty("id", id.toString());
			o.addProperty("name", def.name().getString());
			o.addProperty("skill", def.skill().identifier().toString());
			o.addProperty("value", total);
			o.addProperty("cap", CardRules.totalCap(def));
			Component text = Component.translatable("mettle.cards.trait_total", def.name(), def.format(total), def.format(CardRules.totalCap(def)));
			Optional<Ceilings.Stat> stat = Ceilings.statOf(id.getPath()).filter(capped::containsKey);
			o.addProperty("capped", stat.isPresent());
			o.addProperty("text", stat.map(st -> Ceilings.traitNote(text, st)).orElse(text).getString());
			o.addProperty("description", CardText.sentence(def, total).getString());
			out.add(o);
		}));
		return out;
	}

	private static String skillName(RegistryAccess registries, ResourceKey<Skill> skill) {
		return registries.lookupOrThrow(MettleRegistries.SKILL).getOptional(skill).map(s -> s.name().getString()).orElse(skill.identifier().getPath());
	}
}
