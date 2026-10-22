package dev.voidmc.fray.combat;

import dev.voidmc.fray.Fray;

import dev.voidmc.fray.motion.MotionHits;
import dev.voidmc.fray.motion.MotionServer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Fray combat ↔ Fray Motion, by the animation contract (docs/ANIM_EVENTS.md):
 * <ul>
 * <li>{@link MotionHits#setHandler}: every creature timeline hit runs through the damage pipeline, judged on the
 * defender's clock (§3, §4); a player's own clip volumes are ignored (the player's melee is the combat side's arc sweep
 * over the clip's ACTIVE ticks);</li>
 * <li>{@link MotionServer#setActionGate}: a player's raw clip request (Fray Motion's own {@code ActionRequest}, not a
 * combat input) passes only if the combat side would allow it (not busy, able to act, the stamina paid);</li>
 * <li>player actions start on Fray Motion with their defender stamp ({@link #start}), and reactions (stagger, flinch,
 * hit and block flinches) play the creature's or player's clips.</li>
 * </ul>
 */
public final class MotionBridge {
	private static final Map<EntityType<?>, Map<String, Identifier>> REACTIONS = new ConcurrentHashMap<>();

	private MotionBridge() {
	}

	public static void init() {
		MotionHits.setHandler((hit, window) -> Defense.INSTANCE.timelineHit(hit.attacker(), hit.target(), hit.clip(), hit.tell(), hit.damage(),
			hit.serverTick()));
		MotionServer.setActionGate((player, clip, running) -> {
			CombatState s = CombatState.of(player);
			return s.player == null || s.player.allowRaw(clip);
		});
		Fray.LOGGER.info("Fray combat ↔ motion: timeline hits through the pipeline, player actions on Fray Motion, raw clip requests gated");
	}

	/** Starts a player action's clip on Fray Motion (already validated and stamped by the combat side). */
	public static boolean start(ServerPlayer player, Identifier clip, long defenderStart, float speed, @Nullable Map<String, int[]> windows) {
		return MotionServer.timeline(clip) != null && MotionServer.startAccepted(player, clip, defenderStart, speed, windows) != null;
	}

	public static void stop(LivingEntity entity, int blendTicks) {
		MotionServer.stop(entity, blendTicks);
	}

	/**
	 * The entity's reaction clip {@code <type>/<name>} (zombie/stagger, gallows_knight/stagger, player/flinch), from any
	 * namespace, or null.
	 */
	public static @Nullable Identifier reaction(LivingEntity e, String name) {
		Map<Identifier, ?> set = MotionServer.timelines();
		if (set != lookedUpIn) {
			// The Motion Studio's reload swapped the timelines.
			REACTIONS.clear();
			lookedUpIn = set;
		}
		Map<String, Identifier> m = REACTIONS.computeIfAbsent(e.getType(), t -> new ConcurrentHashMap<>());
		Identifier found = m.get(name);
		if (found == null && !m.containsKey(name)) {
			String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath() + "/" + name;
			for (Identifier id : MotionServer.timelines().keySet()) {
				if (id.getPath().equals(path)) {
					found = id;
					break;
				}
			}
			m.put(name, found == null ? NONE : found);
		}
		return found == NONE ? null : found;
	}

	private static final Identifier NONE = Fray.id("none");
	private static volatile @Nullable Map<Identifier, ?> lookedUpIn;

	/** Plays a reaction that takes over the action layer (stagger, flinch): its timeline runs, the action stops. */
	public static void react(LivingEntity e, String name) {
		Identifier clip = reaction(e, name);
		if (clip != null) {
			MotionServer.play(e, clip, 1);
		}
	}

	/** Plays an additive reaction (hit_light, block_hit): a picture on top of whatever runs. */
	public static void flinch(LivingEntity e, String name) {
		Identifier clip = reaction(e, name);
		if (clip != null) {
			MotionServer.overlay(e, clip, true);
		}
	}

}
