package dev.voidmc.gloam.sky;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;

/**
 * The sky and the light: Gloam's environment-attribute layers (dark nights by the moon and the clouds, the colours of
 * dawn and dusk, overcast skies under the fronts, mist in the fog distances) and the regional rain.
 */
public final class GloamSky {
	private GloamSky() {
	}

	/** Client start. */
	public static void init() {
	}

	/** Adds Gloam's layers to a client level's environment attributes, as the level is made. */
	public static void addLayers(EnvironmentAttributeSystem.Builder builder, ClientLevel level) {
	}
}
