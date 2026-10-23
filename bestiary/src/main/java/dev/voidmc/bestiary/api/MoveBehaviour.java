package dev.voidmc.bestiary.api;

import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.fray.motion.Timeline;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Code for a move beyond its clip (a move names it as {@code behaviour}): shooting on the release tick, leaping,
 * spitting, a fuse, a grab. The clip still owns the timing and the tell; melee hits are the clip's capsules. Register
 * with {@link BestiaryApi#registerBehaviour}; the boss lane adds its specials the same way.
 */
public interface MoveBehaviour {
	/** Everything a behaviour needs about the move that is running. {@code tick} is the clip tick (0 at the start). */
	record Context(ServerLevel level, Mob mob, Brain brain, MoveDef move, LivingEntity target, Timeline timeline, long start) {
		public int strikeTick() {
			return Math.max(0, this.timeline.activeStart);
		}
	}

	/** Extra conditions (a bow in hand, on the ground, a clear line). */
	default boolean canStart(ServerLevel level, Mob mob, Brain brain, MoveDef move, LivingEntity target) {
		return true;
	}

	default void start(Context ctx) {
	}

	default void tick(Context ctx, int tick) {
	}

	/** {@code interrupted}: the clip stopped early (a stagger, a parry, death). */
	default void end(Context ctx, boolean interrupted) {
	}
}
