package dev.voidmc.fray.client.combat.feel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hit-stop (COMBAT.md §5): a landed hit freezes the attacker's and the victim's pose for a few ticks, on every client
 * that sees the hit. Client-side pictures only: the entity keeps moving (its frozen pose travels with it) and the
 * server's tick, damage and windows are never touched. Fray Motion's clip carries on underneath; when the freeze ends
 * the pose catches up with it (no drift from the server's timing). The victim shudders along the push.
 */
public final class HitStop {
	private static final class Entry {
		long start, end;
		@Nullable EntityRenderState state;
		boolean victim;
		float dx, dz, amp;
	}

	private static final Int2ObjectMap<Entry> ACTIVE = new Int2ObjectOpenHashMap<>();
	/** Shudder: peak offset in blocks for the heaviest freeze, and its frequency (Hz). */
	private static final float SHUDDER = 0.06F, SHUDDER_HZ = 28F;

	/** Cost of the frozen-entity path (ns), and frames served from a frozen state (perf, tests). */
	public static long nanos;
	public static long frozenFrames;
	/** Freezes started (tests). */
	public static int started;

	private HitStop() {
	}

	/** Freezes {@code entity} for {@code ticks} (from now, wall time; a longer freeze already running wins). */
	public static void start(int entity, int ticks, double pushX, double pushZ, boolean victim) {
		if (ticks <= 0 || entity < 0) {
			return;
		}
		long now = System.nanoTime();
		long end = now + ticks * 50_000_000L;
		Entry e = ACTIVE.get(entity);
		if (e == null || now >= e.end) {
			e = new Entry();
			e.start = now;
			ACTIVE.put(entity, e);
		}
		e.end = Math.max(e.end, end);
		e.victim |= victim;
		// Fray Motion's per-entity hold: every view of the animated pose (third person, the shadow pass, the local player's
		// first-person arms and camera channels) stops at the same moment and rejoins the clip's timeline afterwards.
		dev.voidmc.fray.client.Animator a = dev.voidmc.fray.client.ClientMotion.get(entity);
		if (a != null) {
			double t = dev.voidmc.fray.client.ClientMotion.now(0);
			a.hold(t, t + (e.end - now) / 50_000_000.0);
		}
		double len = Math.sqrt(pushX * pushX + pushZ * pushZ);
		if (victim && len > 1e-4) {
			e.dx = (float) (pushX / len);
			e.dz = (float) (pushZ / len);
			e.amp = SHUDDER * Math.min(1, ticks / 8F);
		}
		started++;
	}

	/** The frozen state to draw instead of a fresh one, or null (not frozen, or its first frame: extract as usual). */
	public static @Nullable EntityRenderState frozen(Entity entity, float partialTick) {
		if (ACTIVE.isEmpty()) {
			return null;
		}
		long t0 = System.nanoTime();
		Entry e = ACTIVE.get(entity.getId());
		EntityRenderState s = null;
		if (e != null && entity == Minecraft.getInstance().player) {
			// The local player: the Animator's hold freezes the clip; vanilla's parts stay live (the head follows the mouse),
			// and first person (never extracted here) freezes the same way.
			if (t0 >= e.end) {
				ACTIVE.remove(entity.getId());
			}
		} else if (e != null) {
			if (t0 >= e.end) {
				ACTIVE.remove(entity.getId());
			} else if (e.state != null) {
				s = e.state;
				double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
				double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
				double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
				if (e.victim && e.amp > 0) {
					float k = (t0 - e.start) / (float) Math.max(1, e.end - e.start);
					double sh = e.amp * (1 - k) * Math.sin((t0 - e.start) / 1e9 * 2 * Math.PI * SHUDDER_HZ);
					x += e.dx * sh;
					z += e.dz * sh;
				}
				s.x = x;
				s.y = y;
				s.z = z;
				Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
				s.distanceToCameraSq = cam.distanceToSqr(x, y, z);
				frozenFrames++;
			}
		}
		nanos += System.nanoTime() - t0;
		return s;
	}

	/** The state extracted on a freeze's first frame becomes the frozen one. */
	public static void capture(Entity entity, @Nullable EntityRenderState state) {
		if (ACTIVE.isEmpty() || state == null) {
			return;
		}
		Entry e = ACTIVE.get(entity.getId());
		if (e != null && e.state == null) {
			e.state = state;
		}
	}

	public static boolean isFrozen(int entity) {
		Entry e = ACTIVE.get(entity);
		return e != null && System.nanoTime() < e.end;
	}

	public static int active() {
		return ACTIVE.size();
	}

	/** Client tick: forgets finished freezes of entities no longer drawn. */
	public static void prune() {
		if (!ACTIVE.isEmpty()) {
			long now = System.nanoTime();
			ACTIVE.int2ObjectEntrySet().removeIf(x -> now >= x.getValue().end);
		}
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
