package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.burden.LoadState;
import dev.voidmc.mettle.burden.LoadTracker;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.character.PerkText;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.MettleAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * The view model the Ultralight overlay renders (docs/OVERLAY.md): everything the class screens show, as plain JSON with
 * text already translated, pushed as the {@code mettle} event whenever it changes. The native screens are a dev
 * fallback and read the same sources.
 */
public final class MettleState {
	/** Per-point effect lines, in plain words (BALANCE.md §4). */
	private static final Map<CharacterAttribute, String> PER_POINT = Map.of(
		CharacterAttribute.STRENGTH, "mettle.attribute.strength.per_point",
		CharacterAttribute.AGILITY, "mettle.attribute.agility.per_point",
		CharacterAttribute.VITALITY, "mettle.attribute.vitality.per_point",
		CharacterAttribute.WITS, "mettle.attribute.wits.per_point",
		CharacterAttribute.PRESENCE, "mettle.attribute.presence.per_point");

	private MettleState() {
	}

	// ---------------------------------------------------------------- the change counter (docs/OVERLAY.md §revision)

	private static long revision;
	private static long events;
	private static long lastPrint = Long.MIN_VALUE;

	/**
	 * Something {@link #build} shows arrived or changed that the inputs below can't see (a payload: feedback, a moment, a
	 * trade view, the Hall of Firsts, a sitting, a reveal, a picker result; a client option). Client thread.
	 */
	public static void bump() {
		events++;
	}

	/**
	 * A cheap change counter for hosts (the VOID Hub calls it by reflection): it goes up whenever anything {@link #build}
	 * outputs may have changed, so a host can skip rebuilding the state while it stays the same. It looks at the inputs,
	 * not the output: the synced character, cards and load (by identity: every sync is a new value), the inventory's
	 * change count, worn gear, carry capacity, each visible player's public Mythics, the server's card config, the
	 * client options, every Mettle payload; and time where the output shows it: once a second while a card attunes, four
	 * times a second while feedback, moments or a Mythic reveal are showing (their {@code ageMs}). Never goes down within a
	 * session. Client thread.
	 */
	public static synchronized long revision() {
		long print = fingerprint(net.minecraft.client.Minecraft.getInstance());
		if (print != lastPrint) {
			lastPrint = print;
			revision++;
		}
		return revision;
	}

	private static long mix(long h, long v) {
		h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
		return h;
	}

	private static long fingerprint(net.minecraft.client.Minecraft mc) {
		long h = mix(17, events);
		h = mix(h, dev.voidmc.mettle.character.cards.CardConfig.clientRevision());
		h = mix(h, dev.voidmc.mettle.client.MettleClientOptions.revision());
		Player player = mc.player;
		h = mix(h, System.identityHashCode(player));
		h = mix(h, System.identityHashCode(mc.getConnection()));
		if (player == null || mc.level == null) {
			return h;
		}
		h = mix(h, System.identityHashCode(mc.level));
		h = mix(h, System.identityHashCode(player.getAttached(dev.voidmc.mettle.core.CharacterStore.CHARACTER)));
		dev.voidmc.mettle.core.CardsState cards = dev.voidmc.mettle.core.CardStore.get(player);
		h = mix(h, System.identityHashCode(cards));
		h = mix(h, System.identityHashCode(player.getAttached(LoadState.ATTACHMENT)));
		h = mix(h, System.identityHashCode(player.getAttached(dev.voidmc.mettle.gear.WornGear.TYPE)));
		h = mix(h, player.getInventory().getTimesChanged());
		h = mix(h, Double.doubleToLongBits(player.getAttributeValue(MettleAttributes.CARRY_CAPACITY)));
		h = mix(h, mc.level.players().size());
		for (Player p : mc.level.players()) {
			h = mix(h, System.identityHashCode(p.getAttached(dev.voidmc.mettle.character.cards.Mythics.PUBLIC)));
			h = mix(h, System.identityHashCode(p.getAttached(dev.voidmc.mettle.character.cards.BuildFeel.RENOWN)));
		}
		long now = net.minecraft.util.Util.getMillis();
		long gameTime = player.level().getGameTime();
		boolean attuning = cards.cards().stream().anyMatch(c -> !c.attuned(gameTime));
		if (attuning) {
			h = mix(h, gameTime / 20);
		}
		boolean showing = RECENT.stream().anyMatch(f -> now - f.at() < FEEDBACK_MS) || MOMENTS.stream().anyMatch(m -> now - m.at() < FEEDBACK_MS);
		if (showing || MettleCards.revealing()) {
			h = mix(h, now / 250);
		}
		return h;
	}

	private static final long FEEDBACK_MS = 4000;
	private static final java.util.Deque<Feedback> RECENT = new java.util.ArrayDeque<>();

	private record Feedback(String perk, String text, long at) {
	}

	private static JsonObject lastResult;
	private static boolean pickerByOperator;

	/** The server's answer to a background choice, for a hosted picker ({@code result}). */
	public static void rememberResult(boolean ok, String reason) {
		events++;
		JsonObject r = new JsonObject();
		r.addProperty("ok", ok);
		if (reason != null) {
			r.addProperty("reason", reason);
			r.addProperty("text", Component.translatable("mettle.picker.rejected." + reason).getString());
		}
		r.addProperty("at", net.minecraft.util.Util.getMillis());
		lastResult = r;
	}

	/** Whether the open picker was asked for by an operator's reset ({@code pickerByOperator}). */
	public static void rememberPicker(boolean byOperator) {
		events++;
		pickerByOperator = byOperator;
	}

	/** A feedback chip arrived (MettleClient); kept a few seconds for {@code feedback}. */
	public static void remember(String perk, String text) {
		events++;
		RECENT.addLast(new Feedback(perk, text, net.minecraft.util.Util.getMillis()));
		while (RECENT.size() > 4) {
			RECENT.removeFirst();
		}
	}

	private static final java.util.Deque<Moment> MOMENTS = new java.util.ArrayDeque<>();

	private record Moment(JsonObject json, long at) {
	}

	/** A progress moment arrived (MomentClient); kept a few seconds for {@code moments}, like {@link #remember}. */
	public static void rememberMoment(JsonObject json) {
		events++;
		MOMENTS.addLast(new Moment(json, net.minecraft.util.Util.getMillis()));
		while (MOMENTS.size() > 6) {
			MOMENTS.removeFirst();
		}
	}

	/** Rank title bands (TREES.md): untrained 0, novice 1–4, apprentice 5–9, journeyman 10–14, expert 15–19, master 20. */
	public static String titleBand(int level) {
		return level <= 0 ? "untrained" : level < 5 ? "novice" : level < 10 ? "apprentice" : level < 15 ? "journeyman" : level < 20 ? "expert" : "master";
	}

	public static JsonObject build(Minecraft mc) {
		JsonObject root = new JsonObject();
		ClientPacketListener connection = mc.getConnection();
		if (mc.player == null || connection == null) {
			root.addProperty("phase", "offline");
			return root;
		}
		RegistryAccess registries = connection.registryAccess();
		CharacterState state = CharacterStore.get(mc.player);
		Background background = CharacterStore.background(registries, state);
		root.addProperty("phase", state.hasBackground() ? "chosen" : "undecided");

		JsonObject character = new JsonObject();
		character.add("background", state.background().map(k -> (com.google.gson.JsonElement) new com.google.gson.JsonPrimitive(k.identifier().toString())).orElse(JsonNull.INSTANCE));
		JsonObject attributes = new JsonObject();
		for (CharacterAttribute a : CharacterAttribute.values()) {
			attributes.addProperty(a.getSerializedName(), state.attribute(a, background));
		}
		character.add("attributes", attributes);
		JsonObject spent = new JsonObject();
		state.spent().forEach((a, v) -> spent.addProperty(a.getSerializedName(), v));
		character.add("spent", spent);
		List<ResourceKey<Skill>> favoured = state.favoured().isEmpty() && background != null ? background.favoured() : state.favoured();
		character.add("favoured", ids(favoured));
		character.addProperty("capacityKg", mc.player.getAttributeValue(MettleAttributes.CARRY_CAPACITY));
		int charLevel = state.characterLevel();
		character.addProperty("level", charLevel);
		character.addProperty("xp", state.totalXp());
		character.addProperty("levelXp", Curves.characterXpFor(charLevel));
		character.addProperty("nextXp", Curves.characterXpFor(Math.min(charLevel + 1, Curves.CHARACTER_CAP)));
		character.addProperty("points", Perks.attributePoints(mc.player));
		character.addProperty("maxAttribute", CharacterAttribute.MAX);
		root.add("character", character);

		JsonArray backgrounds = new JsonArray();
		registries.lookupOrThrow(MettleRegistries.BACKGROUND).listElements()
			.sorted(Comparator.comparingInt(r -> r.value().order()))
			.forEach(ref -> backgrounds.add(background(ref)));
		root.add("backgrounds", backgrounds);

		// The perk draft (docs/DRAFT.md): one deck and one reading of the character serve every skill and perk below.
		DraftState drafts = state.drafts();
		DraftRules.Deck deck = Drafts.deck(registries);
		dev.voidmc.mettle.character.cards.CardRules.Sheet cardSheet = dev.voidmc.mettle.character.cards.Cards.sheet(registries, state,
			dev.voidmc.mettle.core.CardStore.get(mc.player));
		root.add("draft", draft(drafts));
		// Cards v2 (docs/CARDS.md): the binder, decks, sets, trait totals and costs.
		root.add("cards", MettleCards.build(mc, registries));

		JsonArray skills = new JsonArray();
		registries.lookupOrThrow(MettleRegistries.SKILL).listElements()
			.sorted(Comparator.comparingInt(r -> r.value().order()))
			.forEach(ref -> {
				JsonObject s = new JsonObject();
				s.addProperty("id", ref.key().identifier().toString());
				s.addProperty("name", ref.value().name().getString());
				s.addProperty("attribute", ref.value().attribute().getSerializedName());
				s.addProperty("released", ref.value().released());
				s.addProperty("icon", Skill.icon(ref.key()).toString());
				s.addProperty("colour", String.format(java.util.Locale.ROOT, "#%06X", ref.value().colour()));
				int level = state.level(ref.key());
				s.addProperty("level", level);
				s.addProperty("xp", state.xp(ref.key()));
				s.addProperty("levelXp", Curves.skillXpFor(level));
				s.addProperty("nextXp", Curves.skillXpFor(Math.min(level + 1, Curves.SKILL_CAP)));
				// Drafts waiting; "points" is the same number, kept for hosts written for the old trees.
				int waiting = drafts.queued(ref.key());
				s.addProperty("drafts", waiting);
				s.addProperty("points", waiting);
				s.addProperty("rerolls", drafts.rerolls());
				s.add("hand", hand(registries, drafts.hand(ref.key()), state));
				// The Mythic offer's index in the hand, or -1: claimMythic takes it without using the keep (CARDS.md §3.5).
				s.addProperty("mythicOffer", dev.voidmc.mettle.character.cards.MythicRules.offerIndex(drafts.hand(ref.key())));
				// Cards v2: each frame's chance per card of a hand dealt now (cursed by depth, the rest by rolled rarity).
				JsonObject odds = new JsonObject();
				dev.voidmc.mettle.character.cards.CardRules.frameOdds(ref.key(), cardSheet, deck).forEach((r, v) -> odds.addProperty(r.getSerializedName(), Math.round(v * 10) / 10.0));
				s.add("odds", odds);
				// Rank title by level band ("Journeyman Miner") and the skill's line of lore.
				String path = ref.key().identifier().getPath();
				s.addProperty("title", Component.translatable("mettle.title." + titleBand(level), Component.translatable("mettle.skill." + path + ".noun")).getString());
				s.addProperty("lore", Component.translatable("mettle.skill." + path + ".lore").getString());
				s.addProperty("levelsBy", Component.translatable("mettle.skill." + path + ".levels").getString());
				JsonObject paths = new JsonObject();
				ref.value().paths().forEach((k, v) -> paths.addProperty(k, v.getString()));
				s.add("paths", paths);
				skills.add(s);
			});
		root.add("skills", skills);

		JsonArray perks = new JsonArray();
		registries.lookupOrThrow(MettleRegistries.PERK).listElements().forEach(ref -> {
			Perk p = ref.value();
			// Active rank: earned, or from a slotted card (cards v2); "owned" counts the cards of it in the binder and decks.
			int rank = Perks.rank(mc.player, ref.key());
			JsonObject o = new JsonObject();
			o.addProperty("owned", (int) dev.voidmc.mettle.core.CardStore.get(mc.player).cards().stream().filter(c -> c.perk().equals(ref.key())).count());
			o.addProperty("id", ref.key().identifier().toString());
			o.addProperty("skill", p.skill().identifier().toString());
			o.addProperty("kind", p.kind().getSerializedName());
			o.addProperty("tier", p.tier());
			o.addProperty("path", p.path());
			o.addProperty("rank", rank);
			o.addProperty("maxRank", p.ranks());
			o.addProperty("cost", p.cost());
			o.addProperty("order", p.order());
			o.addProperty("side", p.side());
			JsonArray values = new JsonArray();
			p.rankValues().forEach(values::add);
			o.add("rankValues", values);
			if (!p.synergy().isEmpty()) {
				JsonObject needs = new JsonObject();
				p.synergy().forEach((k, v) -> needs.addProperty(k.identifier().toString(), v));
				o.add("synergy", needs);
			}
			boolean hidden = p.kind() == Perk.Kind.FEAT && rank == 0;
			o.addProperty("name", hidden ? "" : p.name().getString());
			o.addProperty("description", hidden ? "" : PerkText.description(p, Math.max(1, Math.min(rank + 1, p.ranks()))).getString());
			p.hint().ifPresent(h -> o.addProperty("hint", h.getString()));
			p.downside().ifPresent(d -> o.addProperty("downside", d.getString()));
			if (p.kind().drafted()) {
				DraftState.Rarity rarity = DraftRules.rarity(p);
				o.addProperty("rarity", rarity.getSerializedName());
				o.addProperty("rarityColour", rarity.hex());
			}
			if (p.kind().drafted()) {
				// The same rules the server deals by (cards v2: owning a perk never stops it being dealt): null when it
				// can be dealt now, else why not.
				var why = dev.voidmc.mettle.character.cards.CardRules.dealable(ref.key(), p, cardSheet, deck);
				why.ifPresentOrElse(r -> o.addProperty("locked", Component.translatable(r.translationKey()).getString()), () -> o.add("locked", JsonNull.INSTANCE));
				o.addProperty("inDeck", why.isEmpty());
			} else {
				o.addProperty("inDeck", false);
			}
			o.addProperty("icon", Perk.icon(ref.key()).toString());
			perks.add(o);
		});
		root.add("perks", perks);

		// Tier gates (TREES.md §1): the skill level and attribute value each tier needs.
		JsonArray tiers = new JsonArray();
		for (int tier = 1; tier <= 4; tier++) {
			JsonObject t = new JsonObject();
			t.addProperty("tier", tier);
			t.addProperty("skillLevel", Curves.tierSkillGate(tier));
			t.addProperty("attribute", Curves.tierAttributeGate(tier));
			tiers.add(t);
		}
		root.add("tiers", tiers);

		// The last few seconds of feedback chips, so a screen that hides the HUD can still say what happened.
		JsonArray feedback = new JsonArray();
		long now = net.minecraft.util.Util.getMillis();
		for (Feedback f : RECENT) {
			if (now - f.at() < FEEDBACK_MS) {
				JsonObject o = new JsonObject();
				o.addProperty("perk", f.perk());
				o.addProperty("text", f.text());
				o.addProperty("ageMs", now - f.at());
				feedback.add(o);
			}
		}
		root.add("feedback", feedback);
		if (lastResult != null) {
			root.add("result", lastResult);
		}
		root.addProperty("pickerByOperator", pickerByOperator);

		// The same for progress moments (the mettleMoment event), so a host can show the toast too.
		JsonArray moments = new JsonArray();
		for (Moment m : MOMENTS) {
			if (now - m.at() < FEEDBACK_MS) {
				JsonObject o = m.json().deepCopy();
				o.addProperty("ageMs", now - m.at());
				moments.add(o);
			}
		}
		root.add("moments", moments);

		JsonArray attributeInfo = new JsonArray();
		for (CharacterAttribute a : CharacterAttribute.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", a.getSerializedName());
			o.addProperty("name", a.displayName().getString());
			o.addProperty("perPoint", Component.translatable(PER_POINT.get(a)).getString());
			attributeInfo.add(o);
		}
		root.add("attributes", attributeInfo);
		root.add("burden", burden(mc.player));
		root.add("gear", gear(mc.player));
		return root;
	}

	/** The draft's character-wide numbers and the rarity legend (docs/DRAFT.md, OVERLAY.md). */
	private static JsonObject draft(DraftState drafts) {
		JsonObject o = new JsonObject();
		o.addProperty("rerolls", drafts.rerolls());
		o.addProperty("maxRerolls", DraftRules.MAX_REROLLS);
		o.addProperty("waiting", drafts.totalQueued());
		o.addProperty("handSize", DraftRules.HAND);
		o.addProperty("wildcardChance", DraftRules.WILDCARD_CHANCE);
		JsonArray rarities = new JsonArray();
		for (DraftState.Rarity r : DraftState.Rarity.values()) {
			JsonObject ro = new JsonObject();
			ro.addProperty("id", r.getSerializedName());
			ro.addProperty("name", Component.translatable("mettle.draft.rarity." + r.getSerializedName()).getString());
			ro.addProperty("colour", r.hex());
			rarities.add(ro);
		}
		o.add("rarities", rarities);
		// When the card sounds land, so the overlay can time its animations to them (docs/SOUNDS.md §5).
		o.add("sound", dev.voidmc.mettle.client.draft.CardAudio.describe());
		return o;
	}

	/** A skill's dealt hand as cards the overlay draws: what keeping each one gives. */
	private static JsonArray hand(RegistryAccess registries, List<DraftState.Card> cards, CharacterState character) {
		JsonArray out = new JsonArray();
		var perks = registries.lookupOrThrow(MettleRegistries.PERK);
		var skills = registries.lookupOrThrow(MettleRegistries.SKILL);
		for (int i = 0; i < cards.size(); i++) {
			DraftState.Card card = cards.get(i);
			JsonObject o = new JsonObject();
			o.addProperty("index", i);
			o.addProperty("perk", card.perk().identifier().toString());
			o.addProperty("rank", card.rank());
			o.addProperty("rarity", card.rarity().getSerializedName());
			o.addProperty("frame", card.rarity().hex());
			o.addProperty("cursed", card.rarity() == DraftState.Rarity.CURSED);
			o.addProperty("wildcard", card.wildcard());
			// A Mythic offer is claimed on top of the keep: the hand stays open after it (MettleActions.claimMythic).
			o.addProperty("claim", card.rarity() == DraftState.Rarity.MYTHIC);
			o.addProperty("icon", Perk.icon(card.perk()).toString());
			Perk p = perks.getValue(card.perk());
			if (p != null) {
				o.addProperty("name", p.name().getString());
				o.addProperty("description", PerkText.description(p, card.rank()).getString());
				o.addProperty("maxRank", p.ranks());
				o.addProperty("kind", p.kind().getSerializedName());
				o.addProperty("tier", p.tier());
				o.addProperty("path", p.path());
				p.downside().ifPresentOrElse(d -> o.addProperty("downside", d.getString()), () -> o.add("downside", JsonNull.INSTANCE));
				o.addProperty("skill", p.skill().identifier().toString());
				Skill from = skills.getValue(p.skill());
				o.addProperty("skillName", from == null ? p.skill().identifier().getPath() : from.name().getString());
				o.addProperty("colour", String.format(Locale.ROOT, "#%06X", from == null ? Skill.NO_COLOUR : from.colour()));
				// The rolled card (CARDS.md §2): keywords, traits, requirement, the rank name ("Quarryman III").
				MettleCards.offer(o, registries, character, card);
			}
			out.add(o);
		}
		return out;
	}

	/** The synced load (BURDEN.md §3), for the HUD chip and the gear panel's meter. Empty until the first sync. */
	private static JsonObject burden(Player player) {
		JsonObject burden = new JsonObject();
		LoadState load = LoadState.of(player);
		if (load == null) {
			return burden;
		}
		burden.addProperty("load", load.load());
		burden.addProperty("capacity", load.capacity());
		burden.addProperty("band", load.band().name().toLowerCase(Locale.ROOT));
		burden.addProperty("exempt", load.exempt());
		BandRules rules = MettleServices.burden().rules(player);
		burden.addProperty("burdenedAt", rules.burdened());
		burden.addProperty("overburdenedAt", rules.overburdened());
		burden.addProperty("pinnedAt", rules.canBePinned() ? rules.pinned() : -1f);
		if (load.hasHeaviest()) {
			JsonObject h = new JsonObject();
			h.addProperty("item", load.heaviest().toString());
			h.addProperty("kg", load.heaviestKg());
			h.addProperty("stacks", load.heaviestStacks());
			h.addProperty("text", LoadTracker.heaviest(load).getString());
			burden.add("heaviest", h);
		}
		return burden;
	}

	/** What is worn in the three gear slots (BURDEN.md §5), with how full each piece's pockets are. */
	private static JsonObject gear(Player player) {
		JsonObject gear = new JsonObject();
		for (GearSlot slot : GearSlot.values()) {
			ItemStack worn = MettleServices.gear().worn(player, slot);
			if (worn.isEmpty()) {
				gear.add(slot.name().toLowerCase(Locale.ROOT), JsonNull.INSTANCE);
				continue;
			}
			List<ItemStack> pockets = MettleServices.gear().pockets(player, slot);
			JsonObject o = new JsonObject();
			o.addProperty("item", BuiltInRegistries.ITEM.getKey(worn.getItem()).toString());
			o.addProperty("name", worn.getHoverName().getString());
			o.addProperty("pockets", pockets.size());
			o.addProperty("used", (int) pockets.stream().filter(s -> !s.isEmpty()).count());
			gear.add(slot.name().toLowerCase(Locale.ROOT), o);
		}
		return gear;
	}

	private static JsonObject background(Holder.Reference<Background> ref) {
		Background b = ref.value();
		JsonObject o = new JsonObject();
		o.addProperty("id", ref.key().identifier().toString());
		o.addProperty("name", b.name().getString());
		o.addProperty("tagline", b.tagline().getString());
		o.addProperty("art", Background.art(ref.key()).toString());
		JsonObject bonus = new JsonObject();
		b.attributes().forEach((a, v) -> bonus.addProperty(a.getSerializedName(), v));
		o.add("bonus", bonus);
		o.add("favoured", ids(b.favoured()));
		o.addProperty("freePoints", b.freePoints());
		o.addProperty("freeFavoured", b.freeFavoured());
		if (b.signature().isPresent()) {
			JsonObject sig = new JsonObject();
			sig.addProperty("id", b.signature().get().perk().toString());
			sig.addProperty("name", b.signature().get().name().getString());
			sig.addProperty("description", b.signature().get().description().getString());
			o.add("signature", sig);
		} else {
			o.add("signature", JsonNull.INSTANCE);
		}
		JsonArray kit = new JsonArray();
		for (ItemStackTemplate t : b.kit()) {
			ItemStack stack = t.create();
			JsonObject k = new JsonObject();
			k.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			k.addProperty("count", stack.getCount());
			k.addProperty("name", stack.getHoverName().getString());
			kit.add(k);
		}
		o.add("kit", kit);
		o.addProperty("kitSummary", b.kitSummary().getString());
		return o;
	}

	private static JsonArray ids(List<? extends ResourceKey<?>> keys) {
		JsonArray a = new JsonArray();
		keys.forEach(k -> a.add(k.identifier().toString()));
		return a;
	}
}
