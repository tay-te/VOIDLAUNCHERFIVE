package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonArray;
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
		binder.addProperty("count", cards.cards().size());
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

		root.add("sets", sets(registries, cards));
		root.add("traits", traits(registries, cards));

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
		long today = player.level().getOverworldClockTime() / 24000L;
		pins.addProperty("canChange", cards.pinDay() < 0 || today - cards.pinDay() >= config.set_pin_cooldown_days);
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
		return root;
	}

	/** One card as the binder, the deck and the hand draw it. */
	public static JsonObject card(RegistryAccess registries, CharacterState character, CardsState cards, CardData c, CardRules.Attributes attributes, long now) {
		JsonObject o = new JsonObject();
		o.addProperty("id", String.valueOf(c.id()));
		o.addProperty("code", Cards.printId(registries, c));
		Optional<Perk> perk = registries.lookupOrThrow(MettleRegistries.PERK).getOptional(c.perk());
		common(o, registries, c.perk(), perk, c.skill(), c.rank(), c.rarity(), c.keywords(), c.traits(), c.needs(), attributes);
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
			: CardRules.canSlot(c, -1, cards.deck(c.skill()), level, Cards.wits(registries, character), now, registries.lookupOrThrow(MettleRegistries.PERK)::getOptional);
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
		return d;
	}

	private static JsonArray sets(RegistryAccess registries, CardsState cards) {
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
			int reached = CardRules.tier(count, decks);
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
			out.add(o);
		});
		return out;
	}

	private static JsonArray traits(RegistryAccess registries, CardsState cards) {
		JsonArray out = new JsonArray();
		Registry<Trait> registry = registries.lookupOrThrow(MettleRegistries.TRAIT);
		cards.traits().forEach((id, total) -> registry.getOptional(id).ifPresent(def -> {
			JsonObject o = new JsonObject();
			o.addProperty("id", id.toString());
			o.addProperty("name", def.name().getString());
			o.addProperty("skill", def.skill().identifier().toString());
			o.addProperty("value", total);
			o.addProperty("cap", CardRules.totalCap(def));
			o.addProperty("text", Component.translatable("mettle.cards.trait_total", def.name(), def.format(total), def.format(CardRules.totalCap(def))).getString());
			o.addProperty("description", CardText.sentence(def, total).getString());
			out.add(o);
		}));
		return out;
	}

	private static String skillName(RegistryAccess registries, ResourceKey<Skill> skill) {
		return registries.lookupOrThrow(MettleRegistries.SKILL).getOptional(skill).map(s -> s.name().getString()).orElse(skill.identifier().getPath());
	}
}
