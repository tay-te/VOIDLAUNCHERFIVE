package dev.voidmc.hearth.economy.property;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Display names for plots, from their archetype path ({@code house/cottage} → "Cottage"). */
public final class PropertyNames {
	private PropertyNames() {
	}

	/**
	 * The building's name: the shared lang key {@code hearth.archetype.<category>.<name>} if a module defines it, otherwise
	 * the archetype's own name made readable.
	 */
	public static MutableComponent building(String archetypePath) {
		String path = archetypePath.contains(":") ? archetypePath.substring(archetypePath.indexOf(':') + 1) : archetypePath;
		return Component.translatableWithFallback("hearth.archetype." + path.replace('/', '.'), pretty(path));
	}

	/** The building's name inside a sentence: "for the chapel" ({@code hearth.archetype.<...>.lower}, or lower case). */
	public static MutableComponent lower(String archetypePath) {
		String path = archetypePath.contains(":") ? archetypePath.substring(archetypePath.indexOf(':') + 1) : archetypePath;
		return Component.translatableWithFallback("hearth.archetype." + path.replace('/', '.') + ".lower", pretty(path).toLowerCase(Locale.ROOT));
	}

	/** {@code commerce/market_hall} → "Market hall". */
	public static String pretty(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').trim();
		return name.isEmpty() ? path : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
	}
}
