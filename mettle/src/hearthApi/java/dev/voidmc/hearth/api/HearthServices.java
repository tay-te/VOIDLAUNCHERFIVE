package dev.voidmc.hearth.api;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Stub of Hearth's service registry: only the members Mettle calls (see package-info). */
public final class HearthServices {
	private HearthServices() {
	}

	public static @Nullable HearthWorld world(ServerLevel level) {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	public static @Nullable EconomyService economy() {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	public static PlayerSkills playerSkills() {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	public static void setPlayerSkills(@Nullable PlayerSkills service) {
		throw new UnsupportedOperationException("Hearth api stub");
	}
}
