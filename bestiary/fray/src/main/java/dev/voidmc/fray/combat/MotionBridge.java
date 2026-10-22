package dev.voidmc.fray.combat;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.motion.MotionServer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Fray combat ↔ Fray Motion, by the animation contract (docs/ANIM_EVENTS.md §3, §4) without a compile-time tie to the
 * motion package (the animation lane builds it on its own branch):
 * <ul>
 * <li>{@code MotionHits.setHandler}: every creature timeline hit comes here with the window the defender's own action
 * covered (defender-favoured, judged by Fray Motion's DefenderTiming), and runs through the damage pipeline with that
 * outcome; a player's own clip volumes are ignored (the player's melee is the combat side's arc sweep, COMBAT.md
 * §10.1);</li>
 * <li>{@code MotionServer.startPlayerAction(player, clip, stamp)}: a player action's clip runs on the server with the
 * defender stamp, so the motion side knows the player's i-frame, parry and block windows.</li>
 * </ul>
 * Found by reflection at init; absent (the stage 0 MotionServer), creature hits reach Fray through {@code hurtServer}
 * and {@link Defense} judges them on the combat side's own windows.
 */
public final class MotionBridge {
	private static boolean hits;
	private static @Nullable Method startPlayerAction;
	private static MethodHandle attacker, target, clip, tell, damage;

	private MotionBridge() {
	}

	public static boolean timelineHits() {
		return hits;
	}

	public static boolean playerActions() {
		return startPlayerAction != null;
	}

	public static void init() {
		try {
			Class<?> hitsClass = Class.forName("dev.voidmc.fray.motion.MotionHits");
			Class<?> handler = Class.forName("dev.voidmc.fray.motion.MotionHits$Handler");
			Class<?> hit = Class.forName("dev.voidmc.fray.motion.MotionHits$MotionHit");
			MethodHandles.Lookup l = MethodHandles.publicLookup();
			attacker = l.findVirtual(hit, "attacker", MethodType.methodType(LivingEntity.class));
			target = l.findVirtual(hit, "target", MethodType.methodType(LivingEntity.class));
			clip = l.findVirtual(hit, "clip", MethodType.methodType(Identifier.class));
			tell = l.findVirtual(hit, "tell", MethodType.methodType(String.class));
			damage = l.findVirtual(hit, "damage", MethodType.methodType(float.class));
			Object proxy = Proxy.newProxyInstance(MotionBridge.class.getClassLoader(), new Class<?>[] {handler}, (p, m, args) -> switch (m.getName()) {
				case "apply" -> {
					onHit(args[0], ((Enum<?>) args[1]).name());
					yield null;
				}
				case "hashCode" -> System.identityHashCode(p);
				case "equals" -> p == args[0];
				case "toString" -> "Fray combat's timeline hit handler";
				default -> null;
			});
			hitsClass.getMethod("setHandler", handler).invoke(null, proxy);
			hits = true;
		} catch (ClassNotFoundException e) {
			hits = false;
		} catch (ReflectiveOperationException e) {
			Fray.LOGGER.warn("Fray Motion's MotionHits doesn't match the animation contract: {}", e.toString());
		}
		try {
			startPlayerAction = MotionServer.class.getMethod("startPlayerAction", ServerPlayer.class, Identifier.class, long.class);
		} catch (NoSuchMethodException e) {
			startPlayerAction = null;
		}
		Fray.LOGGER.info("Fray combat ↔ motion: timeline hits {}, player actions on Fray Motion {}", hits ? "handled by the pipeline" : "through hurtServer",
			startPlayerAction != null ? "yes" : "no (clips are played by the combat side's own packets)");
	}

	private static void onHit(Object hit, String window) {
		try {
			LivingEntity a = (LivingEntity) attacker.invoke(hit);
			LivingEntity t = (LivingEntity) target.invoke(hit);
			Defense.INSTANCE.timelineHit(a, t, (Identifier) clip.invoke(hit), (String) tell.invoke(hit), (float) damage.invoke(hit), window);
		} catch (Throwable e) {
			Fray.LOGGER.warn("timeline hit failed: {}", e.toString());
		}
	}

	/** Starts the player's clip on Fray Motion with the defender stamp (when the motion side offers it). */
	public static boolean startPlayerAction(ServerPlayer player, Identifier clipId, long stamp) {
		if (startPlayerAction == null || MotionServer.timeline(clipId) == null) {
			return false;
		}
		try {
			return startPlayerAction.invoke(null, player, clipId, stamp) != null;
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}
}
