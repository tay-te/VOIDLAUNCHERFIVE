package dev.voidmc.mettle.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.draft.CardSounds;
import dev.voidmc.mettle.client.draft.CardAudio;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The card draft's sounds (docs/SOUNDS.md) in the real client: every card sound event resolves, every file it names
 * (Mettle's own and the vanilla ones it borrows by name) is in the game's resources, and the overlay's calls play their
 * cues through to the end without a throw: a deal, a hover of every rarity, a pick with its burn, a reroll, a slot, a set,
 * the Mythic reveal, and the Mythic shimmer starting on hover and fading when the pointer leaves.
 */
final class CardSoundJourney {
	private static final List<String> RARITIES = List.of("common", "rare", "epic", "mastery", "cursed", "wildcard", "earned", "mythic");

	private CardSoundJourney() {
	}

	static void run(ClientGameTestContext context) {
		// A screen is up, as the draft's would be (the Mythic shimmer lets go when no screen is open).
		context.setScreen(dev.voidmc.mettle.client.character.CharacterScreen::new);
		context.waitTicks(5);
		check(context.computeOnClient(CardSoundJourney::resources));

		context.runOnClient(mc -> {
			CardAudio.resetForTest();
			MettleActions.cardSound("deal", "mettle:mining");
		});
		for (String r : RARITIES) {
			context.waitTicks(3);
			context.runOnClient(mc -> MettleActions.cardSound("hover#" + r));
		}
		context.waitTicks(2);
		check(context.computeOnClient(mc -> CardAudio.shimmering() || CardAudio.silent() ? null : "the Mythic shimmer did not start on hover"));
		context.runOnClient(mc -> MettleActions.cardSound("unhover", ""));
		context.waitTicks(20);
		check(context.computeOnClient(mc -> CardAudio.shimmering() ? "the Mythic shimmer is still playing after the pointer left" : null));
		context.runOnClient(mc -> {
			MettleActions.cardSound("pick", "epic");
			MettleActions.cardSound("reroll", "");
			MettleActions.cardSound("slot", "");
			MettleActions.cardSound("set_complete", "");
		});
		context.waitTicks(30);
		context.runOnClient(mc -> MettleActions.cardSound("mythic_reveal", ""));
		context.waitTicks(130);
		check(context.computeOnClient(mc -> {
			List<String> played = CardAudio.played();
			List<String> want = new ArrayList<>(List.of("deal@mettle:mining", "pick@epic", "pick.epic@epic", "burn@epic", "reroll", "slot",
				"set_complete", "mythic_reveal"));
			for (String r : RARITIES) {
				want.add("hover." + r + "@" + r);
			}
			want.removeAll(played);
			if (!want.isEmpty()) {
				return "cues that never played: " + want + " (played " + played + ")";
			}
			if (CardAudio.pending() != 0) {
				return CardAudio.pending() + " card sound layers still waiting after every cue should have ended";
			}
			JsonObject sound = MettleState.build(mc).getAsJsonObject("draft").getAsJsonObject("sound");
			if (sound == null || !sound.getAsJsonObject("markers").has("mythic_reveal")) {
				return "draft.sound.markers.mythic_reveal is missing from the overlay state";
			}
			return null;
		}));
		context.runOnClient(mc -> CardAudio.stop());
		context.setScreen(() -> null);
	}

	/** Every card event resolves, and every sound file sounds.json names is in the game's resources. */
	private static String resources(Minecraft mc) {
		List<String> problems = new ArrayList<>();
		for (String name : CardSounds.NAMES) {
			if (mc.getSoundManager().getSoundEvent(Mettle.id(name)) == null) {
				problems.add("event " + name + " does not resolve");
			}
		}
		var res = mc.getResourceManager().getResource(Mettle.id("sounds.json"));
		if (res.isEmpty()) {
			return "assets/mettle/sounds.json is not in the resources";
		}
		try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
			JsonObject events = JsonParser.parseReader(r).getAsJsonObject();
			for (var e : events.entrySet()) {
				for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
					Identifier file = Identifier.parse(s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString());
					Identifier path = file.withPath(p -> "sounds/" + p + ".ogg");
					if (mc.getResourceManager().getResource(path).isEmpty()) {
						problems.add(e.getKey() + ": no " + path);
					}
				}
			}
		} catch (Exception e) {
			return "reading sounds.json: " + e;
		}
		return problems.isEmpty() ? null : String.join("; ", problems);
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
