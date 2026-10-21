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

	/** Adds goods that live outside inventories (a character mod's cards), for the broker's table. One per kind; a second of a kind replaces the first. */
	public static void addVirtualGoods(VirtualGoods goods) {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	/** Every registered kind of virtual goods (empty without a mod that adds some). */
	public static java.util.List<VirtualGoods> virtualGoods() {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	/** The virtual goods of a kind, or null. */
	public static @Nullable VirtualGoods virtualGoods(String kind) {
		throw new UnsupportedOperationException("Hearth api stub");
	}
}
