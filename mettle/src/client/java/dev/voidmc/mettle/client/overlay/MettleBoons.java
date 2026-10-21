package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.cards.BuildFeel;
import dev.voidmc.mettle.character.cards.BuildPreview;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.CardText;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Boon and slot previews asked for by the page (MettleActions.previewTemper, previewInscribe, boonOptions, previewSlot):
 * worked out on the client from the synced cards with the same pure rules the server checks, so what the confirm screen
 * shows is what the server does. Nothing is sent.
 */
final class MettleBoons {
	private MettleBoons() {
	}

	private record Me(RegistryAccess registries, CharacterState state, CardsState cards, long gameTime) {
	}

	private static @Nullable Me me() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) {
			return null;
		}
		return new Me(mc.getConnection().registryAccess(), CharacterStore.get(mc.player), CardStore.get(mc.player), mc.player.level().getGameTime());
	}

	private static JsonObject refused(String why) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("reason", why);
		return o;
	}

	private static JsonObject refusal(CardRules.Refusal r) {
		JsonObject o = refused(Component.translatable(r.translationKey()).getString());
		o.addProperty("refusal", r.name().toLowerCase(java.util.Locale.ROOT));
		return o;
	}

	static JsonObject temper(long id, @Nullable Identifier trait) {
		Me me = me();
		if (me == null || trait == null) {
			return refused("");
		}
		Optional<CardData> card = me.cards().card(id);
		if (card.isEmpty()) {
			return refusal(CardRules.Refusal.NO_CARD);
		}
		CardRules.Data data = Cards.data(me.registries());
		Optional<CardRules.Refusal> no = me.state().drafts().boons(card.get().skill()) <= 0 ? Optional.of(CardRules.Refusal.NO_BOON)
			: CardRules.canTemper(card.get(), trait, me.cards().deck(card.get().skill()), data, me.gameTime());
		if (no.isPresent()) {
			return refusal(no.get());
		}
		CardData after = card.get().withBuild(card.get().keywords(), CardRules.temper(card.get(), trait, data), card.get().marks());
		BuildPreview.Preview p = BuildPreview.change(BuildFeel.context(me.registries(), me.state(), me.cards(), me.gameTime()), card.get(), after);
		JsonObject o = MettleBuild.preview(me.registries(), p, after);
		o.addProperty("ok", true);
		Optional<Trait> def = data.trait(trait);
		double from = card.get().traits().stream().filter(t -> t.trait().equals(trait)).findFirst().orElseThrow().value();
		double to = after.traits().stream().filter(t -> t.trait().equals(trait)).findFirst().orElseThrow().value();
		o.addProperty("from", from);
		o.addProperty("to", to);
		def.ifPresent(d -> {
			o.addProperty("text", Component.translatable("mettle.build.tempered", d.name(), d.format(from), d.format(to)).getString());
			o.addProperty("bandTop", CardRules.bandTop(d, card.get().rarity()));
			o.addProperty("atBandTop", to >= CardRules.bandTop(d, card.get().rarity()));
		});
		return o;
	}

	static JsonObject inscribe(long id, @Nullable Identifier keyword) {
		Me me = me();
		if (me == null || keyword == null) {
			return refused("");
		}
		Optional<CardData> card = me.cards().card(id);
		if (card.isEmpty()) {
			return refusal(CardRules.Refusal.NO_CARD);
		}
		CardRules.Data data = Cards.data(me.registries());
		Optional<CardRules.Refusal> no = me.state().drafts().boons(card.get().skill()) <= 0 ? Optional.of(CardRules.Refusal.NO_BOON)
			: CardRules.canInscribe(card.get(), keyword, me.cards().deck(card.get().skill()), data, me.gameTime());
		if (no.isPresent()) {
			return refusal(no.get());
		}
		CardData after = card.get().withBuild(CardRules.inscribe(card.get(), keyword), card.get().traits(), card.get().marks());
		BuildPreview.Preview p = BuildPreview.change(BuildFeel.context(me.registries(), me.state(), me.cards(), me.gameTime()), card.get(), after);
		JsonObject o = MettleBuild.preview(me.registries(), p, after);
		o.addProperty("ok", true);
		o.addProperty("text", Component.translatable("mettle.build.inscribed", CardText.keyword(me.registries(), keyword),
			CardText.perk(me.registries(), card.get().perk()).map(x -> (Component) CardText.name(x, card.get().rank())).orElse(Component.literal("?"))).getString());
		return o;
	}

	static JsonObject options(long id) {
		Me me = me();
		JsonObject o = new JsonObject();
		if (me == null) {
			return o;
		}
		Optional<CardData> card = me.cards().card(id);
		if (card.isEmpty()) {
			return refusal(CardRules.Refusal.NO_CARD);
		}
		CardData c = card.get();
		CardRules.Data data = Cards.data(me.registries());
		boolean boon = me.state().drafts().boons(c.skill()) > 0;
		o.addProperty("card", String.valueOf(id));
		o.addProperty("skill", c.skill().identifier().toString());
		o.addProperty("boons", me.state().drafts().boons(c.skill()));
		JsonArray tempers = new JsonArray();
		for (CardData.TraitRoll t : c.traits()) {
			JsonObject to = new JsonObject();
			to.addProperty("trait", t.trait().toString());
			Optional<CardRules.Refusal> no = !boon ? Optional.of(CardRules.Refusal.NO_BOON) : CardRules.canTemper(c, t.trait(), me.cards().deck(c.skill()), data, me.gameTime());
			to.addProperty("ok", no.isEmpty());
			to.addProperty("reason", no.map(r -> Component.translatable(r.translationKey()).getString()).orElse(""));
			to.addProperty("from", t.value());
			data.trait(t.trait()).ifPresent(d -> {
				double after = CardRules.tempered(d, c.rarity(), t.value());
				to.addProperty("to", after);
				to.addProperty("name", d.name().getString());
				to.addProperty("text", Component.translatable("mettle.build.tempered", d.name(), d.format(t.value()), d.format(after)).getString());
			});
			tempers.add(to);
		}
		o.add("temper", tempers);
		JsonArray inscribes = new JsonArray();
		for (Identifier k : data.pool(c.skill())) {
			JsonObject ko = new JsonObject();
			ko.addProperty("keyword", k.toString());
			ko.addProperty("name", CardText.keyword(me.registries(), k).getString());
			Optional<CardRules.Refusal> no = !boon ? Optional.of(CardRules.Refusal.NO_BOON) : CardRules.canInscribe(c, k, me.cards().deck(c.skill()), data, me.gameTime());
			ko.addProperty("ok", no.isEmpty());
			ko.addProperty("reason", no.map(r -> Component.translatable(r.translationKey()).getString()).orElse(""));
			inscribes.add(ko);
		}
		o.add("inscribe", inscribes);
		return o;
	}

	static JsonObject slot(long id) {
		Me me = me();
		if (me == null) {
			return new JsonObject();
		}
		Optional<CardData> card = me.cards().card(id);
		if (card.isEmpty() || card.get().slotted()) {
			return refusal(card.isEmpty() ? CardRules.Refusal.NO_CARD : CardRules.Refusal.NOT_IN_BINDER);
		}
		return BuildPreview.of(BuildFeel.context(me.registries(), me.state(), me.cards(), me.gameTime()), card.get())
			.map(p -> {
				JsonObject o = MettleBuild.preview(me.registries(), p, card.get());
				o.addProperty("ok", true);
				o.addProperty("title", Component.translatable("mettle.preview.if_slotted").getString());
				return o;
			}).orElseGet(() -> refused(""));
	}
}
