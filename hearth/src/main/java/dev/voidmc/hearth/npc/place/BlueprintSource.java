package dev.voidmc.hearth.npc.place;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * A settlement that knows its own blueprints (the dev sandbox, which runs without the build module). Everyone else's
 * come from {@link HearthServices#blueprints()}.
 */
public interface BlueprintSource {
	@Nullable Blueprint blueprint(PlotView plot);

	/** The blueprint of a plot, from the settlement itself or the build module; null if neither can make one. */
	static @Nullable Blueprint of(ServerLevel level, Settlement settlement, PlotView plot) {
		if (settlement instanceof BlueprintSource source) {
			return source.blueprint(plot);
		}
		if (!HearthServices.hasBlueprints()) {
			return null;
		}
		HearthWorld world = HearthWorld.of(level);
		if (world == null) {
			return null;
		}
		try {
			return HearthServices.blueprints().blueprint(plot.spec(), world.terrain());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
