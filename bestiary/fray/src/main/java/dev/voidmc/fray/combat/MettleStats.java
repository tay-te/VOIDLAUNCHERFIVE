package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.BalanceKey;
import dev.voidmc.fray.api.FrayApi;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jspecify.annotations.Nullable;

/**
 * Mettle's stat contract (MYTHIC_POWER.md §8) read through attributes only: {@code mettle:<stat>} where Mettle
 * registers it, the neutral value where it doesn't (Mettle absent, or an older Mettle). Mettle clamps its own modifiers
 * to the player's band, so Fray just reads; then the {@link dev.voidmc.fray.api.BalanceProvider} gets the last word.
 */
public final class MettleStats {
	private static final Map<String, Optional<Holder<Attribute>>> CACHE = new HashMap<>();

	private MettleStats() {
	}

	private static Optional<Holder<Attribute>> holder(String path) {
		return CACHE.computeIfAbsent(path, p -> {
			Identifier id = Identifier.tryParse("mettle:" + p);
			return id == null ? Optional.empty() : BuiltInRegistries.ATTRIBUTE.get(id).map(h -> (Holder<Attribute>) h);
		});
	}

	/** True when Mettle registered this stat. */
	public static boolean present(String path) {
		return holder(path).isPresent();
	}

	/** The stat's value on this entity, or {@code neutral} when Mettle doesn't define it or the entity lacks it. */
	public static double get(LivingEntity entity, String path, double neutral) {
		Optional<Holder<Attribute>> h = holder(path);
		if (h.isEmpty()) {
			return neutral;
		}
		AttributeInstance inst = entity.getAttributes().getInstance(h.get());
		return inst == null ? neutral : inst.getValue();
	}

	/** An additive stat (+ points / + %); 0 when absent. */
	public static double add(LivingEntity entity, String path) {
		return get(entity, path, 0);
	}

	/** A multiplier stat (× regen, × cost); 1 when absent. */
	public static double mul(LivingEntity entity, String path) {
		return get(entity, path, 1);
	}

	/** Mettle's BalanceProvider over a base, for players; the base for everyone else. */
	public static double balance(LivingEntity entity, BalanceKey key, double base, @Nullable Identifier context) {
		return entity instanceof ServerPlayer p ? FrayApi.balance().value(p, key, base, context) : base;
	}

	public static void clearCache() {
		CACHE.clear();
	}
}
