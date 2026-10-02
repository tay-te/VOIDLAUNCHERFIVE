package dev.voidmc.expanse.entity.life;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The voices of the wild creatures: birdsong, alarm calls, owl hoots, heron croaks and wingbeats. The
 * clips are synthesised by tools/gen_living.py (assets/expanse/sounds.json).
 */
public final class LifeSounds {
	public static final SoundEvent SONGBIRD_SONG = register("entity.songbird.song");
	public static final SoundEvent SONGBIRD_CALL = register("entity.songbird.call");
	public static final SoundEvent OWL_HOOT = register("entity.owl.hoot");
	public static final SoundEvent HERON_CROAK = register("entity.heron.croak");
	public static final SoundEvent WINGS = register("entity.bird.wings");

	private LifeSounds() {
	}

	static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Expanse.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
