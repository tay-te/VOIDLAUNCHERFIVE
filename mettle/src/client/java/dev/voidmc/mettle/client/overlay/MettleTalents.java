package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Talent;
import dev.voidmc.mettle.character.PerkText;
import dev.voidmc.mettle.character.cards.CardConfig;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.cards.TalentRules;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * The talent layer in the overlay state (docs/BUILD_FEEL.md §13, OVERLAY.md "Talents"): {@code talents} — the row and its
 * slots, the talent binder, the talent draft's hand, when the row may be rearranged, and the earned talents (the five
 * general feats). Everything from the synced character and card cache, so the Hub only reads.
 */
public final class MettleTalents {
	/** The talent card's neutral edge (§13.1: ash). */
	public static final String EDGE = "#A39E96";
	/** The most slots the row ever has (shown greyed until reached). */
	public static final int MAX_SLOTS = 5;

	private MettleTalents() {
	}

	/** {@code talents}. */
	public static JsonObject build(Minecraft mc, RegistryAccess registries) {
		Player player = mc.player;
		CharacterState state = CharacterStore.get(player);
		CardsState cards = CardStore.get(player);
		CardsState.TalentState t = cards.talent();
		int level = state.characterLevel();
		int slots = TalentRules.slots(level);
		long gameTime = player.level().getGameTime();
		JsonObject root = new JsonObject();
		root.addProperty("edge", EDGE);
		root.addProperty("characterLevel", level);
		root.addProperty("unlocked", slots > 0);
		root.addProperty("slots", slots);
		root.addProperty("slotsMax", Math.max(MAX_SLOTS, slots));
		root.add("nextSlotAt", nextSlot(level).map(n -> (com.google.gson.JsonElement) new com.google.gson.JsonPrimitive(n)).orElse(JsonNull.INSTANCE));
		int nextDraft = nextDraft(level);
		root.addProperty("nextDraftAt", nextDraft);
		root.addProperty("slotsText", s(Component.translatable("mettle.talent.slots", t.slotted().stream().filter(c -> c.place().index() < slots).count(), slots)));
		root.addProperty("nextDraftText", s(Component.translatable("mettle.talent.next_draft", nextDraft)));

		// The row: one entry per slot up to the most it will have, a card or null; greyed beyond the slots open now.
		JsonArray row = new JsonArray();
		List<CardData> slotted = t.slotted();
		for (int i = 0; i < Math.max(MAX_SLOTS, slots); i++) {
			int at = i;
			JsonObject o = new JsonObject();
			o.addProperty("slot", i);
			o.addProperty("open", i < slots);
			Optional<CardData> c = slotted.stream().filter(x -> x.place().index() == at).findFirst();
			o.add("card", c.map(x -> (com.google.gson.JsonElement) card(registries, x, gameTime)).orElse(JsonNull.INSTANCE));
			row.add(o);
		}
		root.add("row", row);
		JsonArray binder = new JsonArray();
		t.cards().stream().filter(c -> !c.slotted()).sorted(Comparator.comparing((CardData c) -> c.talentId().map(Identifier::toString).orElse(""))
			.thenComparing(c -> -TalentRules.value(c))).forEach(c -> binder.add(card(registries, c, gameTime)));
		root.add("binder", binder);

		// The talent draft.
		DraftState d = state.drafts();
		DraftState.TalentDraft draft = d.talent();
		JsonObject dj = new JsonObject();
		dj.addProperty("queued", draft.queued());
		dj.addProperty("rerolls", d.rerolls());
		JsonArray hand = new JsonArray();
		for (int i = 0; i < draft.hand().size(); i++) {
			DraftState.Card offer = draft.hand().get(i);
			JsonObject o = offer(registries, offer, slotted, slots);
			o.addProperty("index", i);
			hand.add(o);
		}
		dj.add("hand", hand);
		root.add("draft", dj);

		// Rearranging (§13.1).
		JsonObject re = new JsonObject();
		boolean awake = t.awake(gameTime);
		Optional<TalentRules.Way> way = TalentRules.rearrange(level, awake, cards.freeSwaps());
		re.add("way", way.map(w -> (com.google.gson.JsonElement) new com.google.gson.JsonPrimitive(w.name().toLowerCase(java.util.Locale.ROOT))).orElse(JsonNull.INSTANCE));
		re.addProperty("awake", awake);
		re.addProperty("secondsLeft", awake ? (int) Math.ceil((t.wakeUntil() - gameTime) / 20.0) : 0);
		re.addProperty("freeSwaps", cards.freeSwaps());
		re.addProperty("text", s(way.map(w -> switch (w) {
			case WAKE -> Component.translatable("mettle.talent.rearrange.wake", (int) Math.ceil((t.wakeUntil() - gameTime) / 20.0));
			case LOW_LEVEL -> Component.translatable("mettle.talent.rearrange.low_level");
			case FREE_SWAP -> Component.translatable("mettle.talent.rearrange.free_swap", cards.freeSwaps());
		}).orElse(Component.translatable("mettle.talent.rearrange.locked"))));
		re.addProperty("windowSeconds", CardConfig.get().talent_swap_on_wake_s);
		root.add("rearrange", re);

		// Earned talents: the general feats, always on, never slotted (the gold cards beside the row).
		JsonArray earned = new JsonArray();
		registries.lookupOrThrow(MettleRegistries.PERK).listElements().filter(r -> r.key().identifier().getPath().startsWith("general/"))
			.sorted(Comparator.comparingInt(r -> r.value().order())).forEach(r -> {
				Perk p = r.value();
				int rank = state.rank(r.key());
				JsonObject o = new JsonObject();
				o.addProperty("id", r.key().identifier().toString());
				o.addProperty("held", rank > 0);
				o.addProperty("name", rank > 0 ? s(p.name()) : "");
				o.addProperty("description", rank > 0 ? s(PerkText.description(p, 1)) : "");
				p.hint().ifPresent(h -> o.addProperty("hint", s(h)));
				o.addProperty("icon", Perk.icon(r.key()).toString());
				earned.add(o);
			});
		root.add("earned", earned);
		return root;
	}

	/** One talent card as the Hub draws it: the card look with the ash edge, no keywords, its family where a skill would be. */
	static JsonObject card(RegistryAccess registries, CardData c, long gameTime) {
		JsonObject o = new JsonObject();
		o.addProperty("id", String.valueOf(c.id()));
		o.addProperty("printId", Cards.printId(registries, c));
		Identifier id = c.talentId().orElse(Identifier.fromNamespaceAndPath("mettle", "unknown"));
		Optional<Talent> def = registries.lookupOrThrow(MettleRegistries.TALENT).getOptional(id);
		double value = TalentRules.value(c);
		describe(o, id, def, c.rarity(), value);
		o.addProperty("slotted", c.slotted());
		o.addProperty("slot", c.slotted() ? c.place().index() : -1);
		o.addProperty("how", c.drawn().how().getSerializedName());
		o.addProperty("day", c.drawn().day());
		return o;
	}

	static JsonObject offer(RegistryAccess registries, DraftState.Card offer, List<CardData> row, int slots) {
		JsonObject o = new JsonObject();
		Identifier id = TalentRules.id(offer.perk());
		Optional<Talent> def = registries.lookupOrThrow(MettleRegistries.TALENT).getOptional(id);
		double value = offer.traits().isEmpty() ? 0 : offer.traits().getFirst().value();
		describe(o, id, def, offer.rarity(), value);
		// What keeping it would do: a free slot, an upgrade of the copy in the row, or the binder.
		Optional<CardData> same = row.stream().filter(c -> c.perk().equals(offer.perk())).findFirst();
		String goes = same.isPresent() ? (value > TalentRules.value(same.get()) + 1e-9 ? "upgrade" : "binder")
			: row.stream().filter(c -> c.place().index() < slots).count() < slots ? "slot" : "binder";
		o.addProperty("goes", goes);
		same.ifPresent(c -> o.addProperty("replacesValue", def.map(t -> t.format(TalentRules.value(c))).orElse("")));
		return o;
	}

	private static void describe(JsonObject o, Identifier id, Optional<Talent> def, DraftState.Rarity rarity, double value) {
		o.addProperty("talent", id.toString());
		o.addProperty("name", def.map(t -> s(t.name())).orElse(id.getPath()));
		o.addProperty("description", def.map(t -> s(t.line(value))).orElse(""));
		o.addProperty("rarity", rarity.getSerializedName());
		o.addProperty("rarityColour", rarity.hex());
		o.addProperty("value", value);
		o.addProperty("valueText", def.map(t -> t.isFixed() ? "" : t.format(value)).orElse(""));
		o.addProperty("range", def.map(t -> t.isFixed() ? "" : t.format(t.min()) + " – " + t.format(t.max())).orElse(""));
		o.addProperty("family", def.map(t -> t.family().getSerializedName()).orElse(""));
		o.addProperty("familyName", def.map(t -> s(Component.translatable("mettle.talent.family." + t.family().getSerializedName()))).orElse(""));
		o.addProperty("fixed", def.map(Talent::isFixed).orElse(false));
		o.add("ceiling", def.flatMap(Talent::ceiling).map(c -> (com.google.gson.JsonElement) new com.google.gson.JsonPrimitive(c)).orElse(JsonNull.INSTANCE));
		o.addProperty("edge", EDGE);
		o.add("keywords", new JsonArray());
		o.addProperty("icon", "mettle:talent/" + id.getPath());
	}

	/** The next character level that opens a talent slot, if any. */
	static Optional<Integer> nextSlot(int level) {
		int now = TalentRules.slots(level);
		for (int[] step : CardConfig.get().talent_slots) {
			if (step[0] > level && step[1] > now) {
				return Optional.of(step[0]);
			}
		}
		return Optional.empty();
	}

	/** The next character level that deals a talent draft. */
	static int nextDraft(int level) {
		int l = level + 1;
		while (!TalentRules.draftAt(l) && l < level + 1000) {
			l++;
		}
		return l;
	}

	private static String s(Component c) {
		return c.getString();
	}
}
