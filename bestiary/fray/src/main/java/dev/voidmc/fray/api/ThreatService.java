package dev.voidmc.fray.api;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Threat tables (CONTRACTS.md §8): at most 8 entries per hostile creature. Damage adds 1 per point, healing or warding
 * someone it fights 0.5, a taunt sets the taunter to top + 20 % for its ticks. Bestiary's tactics read {@link #top};
 * vanilla creatures keep their own targeting.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface ThreatService {
	void add(Mob mob, LivingEntity source, float amount, Identifier reason);

	void taunt(Mob mob, LivingEntity taunter, int ticks);

	@Nullable LivingEntity top(Mob mob);

	List<ThreatEntry> table(Mob mob);

	void clear(Mob mob);
}
