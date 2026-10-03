package dev.voidmc.hearth.api;

import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A module's slot in a settlement's (or a world's) saved data: {@code settlement.data(KEY)}. The sim module persists
 * every registered key with its codec, so a module can keep its own state without the sim knowing what it is. Data
 * under a key no longer registered is kept as-is, so removing a module never loses a world's data.
 *
 * <p>Register keys during mod initialisation, before any world loads.
 *
 * @param <T> a mutable holder the module changes in place (then calls {@code markDirty()})
 */
public record HearthDataKey<T>(Identifier id, Scope scope, Codec<T> codec, Supplier<T> factory) {
	private static final Map<Identifier, HearthDataKey<?>> KEYS = new LinkedHashMap<>();

	public static synchronized <T> HearthDataKey<T> register(Identifier id, Scope scope, Codec<T> codec, Supplier<T> factory) {
		HearthDataKey<T> key = new HearthDataKey<>(id, scope, codec, factory);
		if (KEYS.putIfAbsent(id, key) != null) {
			throw new IllegalStateException("hearth data key registered twice: " + id);
		}
		return key;
	}

	public static synchronized @Nullable HearthDataKey<?> get(Identifier id) {
		return KEYS.get(id);
	}

	public static synchronized Collection<HearthDataKey<?>> all() {
		return Collections.unmodifiableCollection(KEYS.values());
	}

	public enum Scope {
		/** One per settlement: {@link Settlement#data}. */
		SETTLEMENT,
		/** One per world (dimension): {@link HearthWorld#data}. */
		WORLD
	}
}
