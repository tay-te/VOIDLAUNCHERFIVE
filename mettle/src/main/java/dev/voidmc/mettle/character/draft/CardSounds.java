package dev.voidmc.mettle.character.draft;

import dev.voidmc.mettle.Mettle;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The sound events of the card draft (docs/SOUNDS.md): the layers the cues in {@code assets/mettle/card_cues.json} are
 * built from. Each is defined in {@code assets/mettle/sounds.json} (written by {@code tools/sounds/events.py}): the
 * synthesised ones point at Mettle's own files, the rest at Minecraft's own sound files by name. They are only ever
 * played on the client ({@code client.draft.CardAudio}); registering them lets resource packs and {@code /playsound}
 * find them. {@code CardCuesTest} checks this list against sounds.json.
 */
public final class CardSounds {
	public static final List<String> NAMES = List.of(
		// synthesised (tools/sounds/synth.py)
		"card.ash", "card.burn", "card.crackle", "card.flare", "card.glow", "card.gold", "card.riffle", "card.sizzle",
		"card.slide", "card.whoosh", "card.wrong",
		"mythic.burst", "mythic.crack", "mythic.idle", "mythic.rumble", "mythic.swell", "mythic.tail",
		// vanilla files, referenced by name
		"card.deny", "card.ember", "card.enchant", "card.fizz", "card.flip", "card.ignite", "card.ominous", "card.put",
		"card.slot", "card.slot_enchanted", "card.tap", "card.turn", "card.weight",
		"mythic.bloom", "mythic.dim", "mythic.eye", "mythic.glimmer", "mythic.heartbeat", "mythic.rise", "mythic.shard"
	);

	private CardSounds() {
	}

	public static void init() {
		for (String name : NAMES) {
			Identifier id = Mettle.id(name);
			Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		}
	}
}
