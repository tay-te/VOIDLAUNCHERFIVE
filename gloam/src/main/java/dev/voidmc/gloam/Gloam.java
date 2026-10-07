package dev.voidmc.gloam;

import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.far.FarAir;
import dev.voidmc.gloam.motes.Motes;
import dev.voidmc.gloam.season.Seasons;
import dev.voidmc.gloam.sky.GloamSky;
import dev.voidmc.gloam.sound.Soundscape;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Gloam: living air for the VOID world.
 *
 * <p>One model of the air ({@link dev.voidmc.gloam.air.Air}: the year, the wind, the fronts, the mist), worked out
 * where the camera is each tick ({@link Atmosphere}), feeds four parts that each stand on their own:
 * <ul>
 *   <li>{@link GloamSky}: the sky and the light, as environment-attribute layers, so vanilla, VOID LOD and shader
 *       packs all follow it;</li>
 *   <li>{@link Motes}: the small things in the air, as particles, which shader packs draw too;</li>
 *   <li>{@link Soundscape}: the ambient beds of the land, the weather and the hour;</li>
 *   <li>{@link FarAir}: the air over VOID LOD's far terrain, out to the horizon;</li>
 *   <li>{@link Seasons}: the year on the land: grass and leaves through the seasons, bare winter crowns, the snowline.</li>
 * </ul>
 * Settings live in {@code config/gloam.json} ({@link GloamSettings}); {@code gloam.*} system properties override it.
 */
public final class Gloam implements ClientModInitializer {
	public static final String ID = "gloam";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Gloam");

	private static Gloam instance;

	private final Atmosphere atmosphere = new Atmosphere();

	@Override
	public void onInitializeClient() {
		instance = this;
		GloamSettings.load();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (GloamSettings.get().enabled) {
				this.atmosphere.tick(mc);
			}
		});
		GloamSky.init();
		Motes.init();
		Soundscape.init();
		FarAir.init();
		Seasons.init();
		LOGGER.info("VOID Gloam: the air is up");
	}

	public static Gloam get() {
		return instance;
	}

	public Atmosphere atmosphere() {
		return this.atmosphere;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}
}
