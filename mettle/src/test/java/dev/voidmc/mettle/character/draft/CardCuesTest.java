package dev.voidmc.mettle.character.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.core.DraftState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The card draft's sound cues (docs/SOUNDS.md): the cue file, the events it names, and how rarities pick cues. */
class CardCuesTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/mettle");
	/** Every rarity the draft can show, today's and the card system's (CARDS.md). */
	private static final List<String> RARITIES = List.of("common", "rare", "epic", "mastery", "cursed", "wildcard", "earned", "mythic");
	/** Vanilla events a cue may use directly: the note blocks Mettle's moments already chime with. */
	private static final Set<String> VANILLA = Set.of("minecraft:block.note_block.chime", "minecraft:block.note_block.bell",
		"minecraft:block.note_block.bass", "minecraft:block.note_block.pling", "minecraft:block.note_block.harp");

	private static JsonObject soundsJson() throws IOException {
		return JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void registeredEventsMatchSoundsJson() throws IOException {
		assertEquals(new TreeSet<>(soundsJson().keySet()), new TreeSet<>(CardSounds.NAMES), "CardSounds.NAMES and assets/mettle/sounds.json");
	}

	@Test
	void everyMettleSoundFileShips() throws IOException {
		List<String> missing = new ArrayList<>();
		for (var e : soundsJson().entrySet()) {
			for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
				String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
				if (name.startsWith("mettle:") && !Files.isRegularFile(ASSETS.resolve("sounds/" + name.substring(7) + ".ogg"))) {
					missing.add(name);
				}
			}
		}
		assertEquals(List.of(), missing);
	}

	@Test
	void everyLayerNamesAKnownEventAtAPlayablePitchAndVolume() {
		CardCues cues = CardCues.get();
		List<String> problems = new ArrayList<>();
		for (String cue : cues.names()) {
			for (CardCues.Layer l : cues.cue(cue)) {
				boolean known = l.sound().contains(":") ? VANILLA.contains(l.sound()) : CardSounds.NAMES.contains(l.sound());
				if (!known) {
					problems.add(cue + ": unknown event " + l.sound());
				}
				float lowest = l.pitch() * (1 - l.jitter());
				float highest = l.pitch() * (1 + l.jitter());
				if (lowest < 0.5f || highest > 2.0f + 1e-4f) {
					problems.add(cue + ": " + l.sound() + " pitch " + l.pitch() + " ± " + l.jitter() + " leaves 0.5–2 (Minecraft clamps it)");
				}
				if (l.volume() <= 0f || l.volume() > 1f) {
					problems.add(cue + ": " + l.sound() + " volume " + l.volume());
				}
				if (l.atMs() < 0) {
					problems.add(cue + ": " + l.sound() + " starts before its cue");
				}
			}
		}
		for (CardCues.Loop loop : cues.loops()) {
			if (!CardSounds.NAMES.contains(loop.sound())) {
				problems.add("loop " + loop.sound() + " is not a card sound");
			}
		}
		assertEquals(List.of(), problems);
	}

	@Test
	void everyRarityHasAHoverAndAPick() {
		CardCues cues = CardCues.get();
		for (String r : RARITIES) {
			assertTrue(cues.has("hover." + r), "hover." + r);
			if (!r.equals("common")) {
				assertTrue(cues.has("pick." + r), "pick." + r);
			}
		}
		for (DraftState.Rarity r : DraftState.Rarity.values()) {
			assertTrue(cues.has("hover." + r.getSerializedName()), "hover for the draft's own rarity " + r);
		}
		for (String cue : List.of("deal", "pick", "burn", "reroll", "reroll_denied", "nudge", "slot", "set_complete", "mythic_reveal")) {
			assertTrue(cues.has(cue), cue);
		}
		assertTrue(cues.loop("mythic_idle").isPresent());
		assertEquals("hover.common", cues.forRarity("hover", "legendary"), "an unknown rarity sounds Common");
	}

	@Test
	void aHandGetsTheStingerOfItsRarestCard() {
		CardCues cues = CardCues.get();
		assertEquals(Optional.empty(), cues.stinger(Set.of("common", "rare")));
		assertEquals(Optional.of("stinger.epic"), cues.stinger(Set.of("common", "epic")));
		assertEquals(Optional.of("stinger.cursed"), cues.stinger(Set.of("epic", "cursed", "wildcard")));
		assertEquals(Optional.of("stinger.wildcard"), cues.stinger(Set.of("wildcard", "epic")));
		assertEquals(Optional.of("stinger.earned"), cues.stinger(Set.of("earned", "mastery")));
	}

	@Test
	void wildcardsSoundLikeWildcardsUnlessTheirRarityHasItsOwnVoice() {
		assertEquals("wildcard", CardCues.rarityKey("rare", true));
		assertEquals("wildcard", CardCues.rarityKey("EPIC", true));
		assertEquals("cursed", CardCues.rarityKey("cursed", true));
		assertEquals("mythic", CardCues.rarityKey("mythic", true));
		assertEquals("rare", CardCues.rarityKey("rare", false));
		assertEquals("common", CardCues.rarityKey(null, false));
	}

	@Test
	void theRevealMarkersMatchItsLayers() {
		CardCues cues = CardCues.get();
		JsonObject m = cues.markers().getAsJsonObject("mythic_reveal");
		int flip = m.get("flip").getAsInt();
		assertTrue(cues.cue("mythic_reveal").stream().anyMatch(l -> l.sound().equals("mythic.burst") && l.atMs() == flip), "the burst lands on the flip");
		List<Integer> cracks = new ArrayList<>();
		m.getAsJsonArray("cracks").forEach(e -> cracks.add(e.getAsInt()));
		List<Integer> layers = cues.cue("mythic_reveal").stream().filter(l -> l.sound().equals("mythic.crack")).map(CardCues.Layer::atMs).toList();
		assertEquals(cracks, layers);
		assertFalse(cues.cue("mythic_reveal").stream().anyMatch(l -> l.atMs() > m.get("settled").getAsInt()), "nothing starts after the reveal settles");
	}
}
