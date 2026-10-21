package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SpeechSkill;
import dev.voidmc.mettle.character.skill.speech.SpeechRules;
import dev.voidmc.mettle.compat.HearthChannels;
import dev.voidmc.mettle.compat.HearthLink;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The Speech page's numbers (docs/BUILD_FEEL.md §14.2; OVERLAY.md "Speech"): what VOID Hearth reads of the character
 * (the effective Speech and Presence, the bonus keys and Speech's share of the market edge), which effects are on their
 * fallbacks, and the Mettle-only perks. Read from the synced character and cards, like the rest of the state.
 */
final class MettleSpeech {
	private MettleSpeech() {
	}

	static JsonObject build(Minecraft mc) {
		Player player = mc.player;
		SpeechRules.Holdings h = SpeechSkill.holdings(player);
		boolean bonus = HearthLink.bonus();
		JsonObject o = new JsonObject();
		o.addProperty("colour", "#E8C46A");
		o.addProperty("hearth", HearthLink.hearth());
		o.addProperty("readsKeys", bonus);
		o.addProperty("checkEvents", HearthLink.checks());
		o.addProperty("level", h.level());
		o.addProperty("effectiveLevel", SpeechRules.effectiveSpeech(h, bonus));
		o.addProperty("presence", h.presence());
		o.addProperty("effectivePresence", SpeechRules.effectivePresence(h, bonus));
		o.addProperty("edge", Math.round(SpeechRules.edge(h, bonus) * 1000) / 10.0);
		o.addProperty("edgeText", String.format(Locale.ROOT, "%+.1f %%", SpeechRules.edge(h, bonus) * 100));
		JsonArray keys = new JsonArray();
		if (bonus) {
			for (Map.Entry<String, Double> e : SpeechRules.bonuses(h).entrySet()) {
				if (e.getValue() > 0) {
					JsonObject k = new JsonObject();
					k.addProperty("key", e.getKey());
					k.addProperty("value", e.getValue());
					keys.add(k);
				}
			}
		}
		o.add("keys", keys);
		JsonArray fallbacks = new JsonArray();
		for (HearthChannels.Route r : HearthChannels.ROUTES) {
			if (r.source().startsWith("speech/") && HearthLink.hearth() && !bonus && r.channel() != r.fallback() && held(player, r.source())) {
				fallbacks.add(r.source());
			}
		}
		o.add("fallbacks", fallbacks);
		o.addProperty("retriesPerDay", bonus && h.secondWord() ? 1 : 0);
		o.addProperty("appraiser", Perks.has(player, SpeechSkill.APPRAISER));
		o.addProperty("coinWeight", Math.round(SpeechRules.coinWeight(Perks.has(player, SpeechSkill.LEDGER_KEEPER),
			dev.voidmc.mettle.character.cards.CardHooks.pct(player, "speech/coinwise")) * 100));
		return o;
	}

	private static boolean held(Player player, String source) {
		return Perks.has(player, dev.voidmc.mettle.Mettle.id(source)) || dev.voidmc.mettle.character.cards.CardHooks.pct(player, source) > 0;
	}
}
