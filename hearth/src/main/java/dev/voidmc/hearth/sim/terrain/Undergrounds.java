package dev.voidmc.hearth.sim.terrain;

import dev.voidmc.hearth.api.UndergroundProbe;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Picks the underground probe for a level: Expanse's cavern model on Earth worlds, none anywhere else. */
public final class Undergrounds {
	private Undergrounds() {
	}

	public static @Nullable UndergroundProbe create(ServerLevel level) {
		if (FabricLoader.getInstance().isModLoaded("expanse")) {
			return ExpanseUnderground.tryCreate(level);
		}
		return null;
	}
}
