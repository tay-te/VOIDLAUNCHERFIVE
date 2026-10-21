package dev.voidmc.fray.motion;

import dev.voidmc.fray.Fray;
import java.util.Locale;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Where timeline hits go (ANIM_EVENTS.md §3). Fray Motion decides <em>that</em> a capsule touched a target and what the
 * defender's own action covered at that moment (with defender-favoured timing); the combat pipeline decides what it
 * does. Without a handler, Fray applies plain damage for an undefended hit (the prototype's behaviour, for tests).
 */
public final class MotionHits {
	/**
	 * A hit from a timeline: the attacker's clip and volume, the attacker's clip tick, the server tick it was decided on
	 * (on the defender's clock this is the tick they saw it at), the tell colour and the timeline's damage.
	 */
	public record MotionHit(LivingEntity attacker, LivingEntity target, Identifier clip, String volume, int clipTick, long serverTick, String tell,
		float damage) {
	}

	@FunctionalInterface
	public interface Handler {
		void apply(MotionHit hit, DefenderTiming.Window window);
	}

	/** Fires for every resolved hit, after the handler (tests, the studio's log, AI hearing). */
	public static final Event<Handler> RESOLVED = EventFactory.createArrayBacked(Handler.class, ls -> (hit, window) -> {
		for (Handler l : ls) {
			l.apply(hit, window);
		}
	});

	private static volatile Handler handler = MotionHits::fallback;

	private MotionHits() {
	}

	/** The combat pipeline takes over every timeline hit. */
	public static void setHandler(Handler h) {
		handler = h;
	}

	static void resolve(MotionHit hit, DefenderTiming.Window window) {
		handler.apply(hit, window);
		RESOLVED.invoker().apply(hit, window);
	}

	/** No combat pipeline: undefended hits deal the timeline's damage (or the attacker's attack damage). */
	static void fallback(MotionHit hit, DefenderTiming.Window window) {
		LivingEntity self = hit.attacker();
		if (window != DefenderTiming.Window.NONE || !(self.level() instanceof ServerLevel level)) {
			Fray.LOGGER.info(String.format(Locale.ROOT, "hit: %s -> %s by %s at tick %d: %s", self.getName().getString(), hit.target().getName().getString(),
				hit.clip(), hit.clipTick(), window));
			return;
		}
		float damage = hit.damage() > 0 ? hit.damage() : (float) (self.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? self.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1);
		DamageSource source = self instanceof ServerPlayer p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(self);
		boolean hurt = hit.target().hurtServer(level, source, damage);
		Fray.LOGGER.info(String.format(Locale.ROOT, "hit: %s -> %s by %s (%s) at tick %d, %.1f damage%s", self.getName().getString(),
			hit.target().getName().getString(), hit.clip(), hit.volume(), hit.clipTick(), damage, hurt ? "" : " (not hurt: immune)"));
	}
}
