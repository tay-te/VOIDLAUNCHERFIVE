package dev.voidmc.fray.api;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Noise for hearing (BESTIARY.md §3): anything that makes a sound a creature could hear reports it here with a radius;
 * Bestiary's perception asks what was heard near a creature. Fray reports hits (16 blocks) and parries (20) itself;
 * Bestiary adds footsteps, broken blocks and explosions, Hearth its bells (64). A sound is kept for about five seconds.
 *
 * @since 0.2.0 (2026-10-22)
 */
public interface PerceptionService {
	/** A sound: where, how far it carries, what made it and when (game tick). */
	record Noise(Vec3 pos, float radius, Identifier kind, int sourceId, long tick) {
	}

	Identifier HIT = Identifier.fromNamespaceAndPath("fray", "hit");
	Identifier PARRY = Identifier.fromNamespaceAndPath("fray", "parry");
	Identifier BLOCK_BROKEN = Identifier.fromNamespaceAndPath("fray", "block_broken");
	Identifier EXPLOSION = Identifier.fromNamespaceAndPath("fray", "explosion");
	Identifier STEP = Identifier.fromNamespaceAndPath("fray", "step");
	Identifier BELL = Identifier.fromNamespaceAndPath("fray", "bell");

	void noise(ServerLevel level, Vec3 pos, float radius, Identifier kind, @Nullable Entity source);

	/** The noises heard at {@code at} (within each noise's radius) made at or after {@code since}, newest first. */
	List<Noise> heard(ServerLevel level, Vec3 at, long since);
}
