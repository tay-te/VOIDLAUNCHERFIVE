package dev.voidmc.fray.api;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * What the HUD and the rules know of a creature beyond vanilla: its rank, a subtitle for the target frame ("Elite ·
 * champion pack · danger 3") and its affix icons. Bestiary sets these; Fray syncs them to trackers.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface ProfileService {
	void set(LivingEntity entity, Rank rank, @Nullable Component subtitle, List<Identifier> affixes);

	Rank rank(LivingEntity entity);
}
