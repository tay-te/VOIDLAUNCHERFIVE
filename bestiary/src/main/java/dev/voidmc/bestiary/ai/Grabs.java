package dev.voidmc.bestiary.ai;

import dev.voidmc.fray.motion.DefenderTiming;
import dev.voidmc.fray.motion.MotionHits;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Who holds whom (the zombie's lunge-grab), and the last undefended timeline hit of each attacker (from
 * {@link MotionHits#RESOLVED}): a grab holds only a target its capsule really reached and that didn't dodge or parry.
 */
public final class Grabs {
	private record LastHit(int target, long tick) {
	}

	private static final Int2ObjectOpenHashMap<LastHit> LAST = new Int2ObjectOpenHashMap<>();
	private static final Int2LongOpenHashMap HELD_SINCE = new Int2LongOpenHashMap();
	private static final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap HELD_BY = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

	private Grabs() {
	}

	public static void init() {
		HELD_BY.defaultReturnValue(-1);
		MotionHits.RESOLVED.register((hit, window) -> {
			if (window == DefenderTiming.Window.NONE) {
				LAST.put(hit.attacker().getId(), new LastHit(hit.target().getId(), hit.serverTick()));
			}
		});
	}

	/** Whether {@code mob}'s timeline hit reached {@code t} undefended at or after {@code since}. */
	public static boolean hitBy(Mob mob, LivingEntity t, long since) {
		LastHit h = LAST.get(mob.getId());
		return h != null && h.target == t.getId() && h.tick >= since;
	}

	public static void hold(LivingEntity t, Mob by, long now) {
		HELD_BY.put(t.getId(), by.getId());
		HELD_SINCE.put(t.getId(), now);
	}

	public static int by(LivingEntity t) {
		return HELD_BY.get(t.getId());
	}

	public static long since(LivingEntity t) {
		return HELD_SINCE.getOrDefault(t.getId(), Long.MAX_VALUE / 2);
	}

	public static void release(LivingEntity t) {
		HELD_BY.remove(t.getId());
		HELD_SINCE.remove(t.getId());
	}

	/** A grabber died or unloaded: whoever it held is free (the Root wears off on its own). */
	public static void releaseBy(Mob by) {
		HELD_BY.int2IntEntrySet().removeIf(e -> {
			if (e.getIntValue() == by.getId()) {
				HELD_SINCE.remove(e.getIntKey());
				return true;
			}
			return false;
		});
		LAST.remove(by.getId());
	}

	public static void clear() {
		LAST.clear();
		HELD_BY.clear();
		HELD_SINCE.clear();
	}
}
