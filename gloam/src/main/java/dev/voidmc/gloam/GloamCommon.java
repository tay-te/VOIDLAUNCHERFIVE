package dev.voidmc.gloam;

import dev.voidmc.gloam.motes.Motes;
import dev.voidmc.gloam.sound.Soundscape;
import net.fabricmc.api.ModInitializer;

/** Gloam's registries, on both sides: the particle types and the sound events. */
public final class GloamCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		Motes.register();
		Soundscape.register();
	}
}
