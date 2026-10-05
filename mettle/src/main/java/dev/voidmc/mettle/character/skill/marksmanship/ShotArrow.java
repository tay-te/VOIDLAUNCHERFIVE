package dev.voidmc.mettle.character.skill.marksmanship;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What an arrow remembers about how it was loosed, added to every arrow by {@code AbstractArrowMixin}. Server only and
 * not saved: an arrow still in flight across a restart just forgets its perks.
 */
public interface ShotArrow {
	/** Where it was loosed from (the shooter's eyes), or null for arrows not loosed from a weapon. */
	@Nullable Vec3 mettle$origin();

	/** A fully drawn bow or a crossbow (Pinning Shot). */
	boolean mettle$fullDraw();

	/** How much faster than vanilla perks launched it; hits divide by this so speed never doubles as damage. */
	float mettle$speed();

	void mettle$loosed(Vec3 origin, boolean fullDraw, float speed);
}
