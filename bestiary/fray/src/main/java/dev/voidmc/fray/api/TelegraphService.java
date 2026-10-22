package dev.voidmc.fray.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Tells aimed at players (H9): the attacker's tell colour, its windup and a subtitle line. Bestiary's AI (and Fray
 * Motion's timelines with a {@code tell} block) call it when a tell starts; the target's client draws the shape-coded
 * cue and edge flash. The strike that follows should carry {@link TellColour#hitFlags()}.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface TelegraphService {
	void tell(LivingEntity attacker, LivingEntity target, TellColour colour, int windupTicks, @Nullable Component subtitle);
}
