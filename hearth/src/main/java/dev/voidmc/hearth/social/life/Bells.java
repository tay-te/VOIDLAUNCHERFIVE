package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialConfig;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * The bells of an embodied settlement: at dawn (work begins), noon and dusk (work ends) the bell tower, chapel or
 * temple rings, a stroke every second and a half, heard across the town. Only the sound is made (a real bell block is
 * not struck: that would send vanilla villagers nearby into hiding as if raiders came).
 */
public final class Bells {
	/** Where bells hang, best first; a big tower rings deeper. */
	static final List<String> TOWERS = List.of("landmark/bell_tower", "landmark/clock_tower", "landmark/cathedral", "landmark/temple", "landmark/pagoda",
		"civic/monastery", "civic/convent", "civic/chapel", "landscape/bell");
	static final int STROKE_TICKS = 30;

	private Bells() {
	}

	/** A stroke waiting to sound. */
	public record Stroke(long gameTime, ServerLevel level, BlockPos pos, float pitch, float volume) {
	}

	/** Where this settlement's bell is and how deep it sounds, or null if it has none. */
	public record Tower(BlockPos pos, float pitch) {
	}

	public static @Nullable Tower tower(Settlement s) {
		for (int i = 0; i < TOWERS.size(); i++) {
			String path = TOWERS.get(i);
			List<PlotView> plots = Venues.built(s, path);
			if (plots.isEmpty()) {
				continue;
			}
			PlotView p = plots.get(0);
			BlockPos at;
			if (path.equals("landscape/bell")) {
				List<Poi> meet = Venues.inPlot(s.pois(PoiType.MEETING_POINT), p);
				at = meet.isEmpty() ? Venues.middle(p.spec()).above(2) : meet.get(0).pos().above(2);
			} else {
				at = Venues.middle(p.spec()).above(Math.max(3, p.spec().maxHeight() * 2 / 3));
			}
			float pitch = i < 5 ? 0.7F : i < 7 ? 0.85F : 1.0F;
			return new Tower(at, pitch);
		}
		return null;
	}

	/**
	 * Whether a bell time fell in (previousMinute, minute], allowing for midnight; returns its index in the config's
	 * times, or -1.
	 */
	public static int due(SocialConfig.Bells cfg, int previousMinute, int minute) {
		if (previousMinute < 0 || previousMinute == minute) {
			return -1;
		}
		for (int i = 0; i < cfg.times().size(); i++) {
			int t = cfg.times().get(i);
			boolean crossed = previousMinute < minute ? t > previousMinute && t <= minute : t > previousMinute || t <= minute;
			// a jump of hours (sleeping through the night, /time set) rings nothing
			int span = Math.floorMod(minute - previousMinute, 24 * 60);
			if (crossed && span <= 30) {
				return i;
			}
		}
		return -1;
	}

	/** Queues the strokes for one ringing. */
	public static void ring(ArrayDeque<Stroke> queue, ServerLevel level, Tower tower, int strokes, float volume) {
		long t = level.getGameTime();
		for (int i = 0; i < strokes; i++) {
			queue.add(new Stroke(t + (long) i * STROKE_TICKS, level, tower.pos(), tower.pitch(), volume));
		}
	}

	/** Plays the strokes whose time has come. */
	public static void play(ArrayDeque<Stroke> queue, long gameTime) {
		while (!queue.isEmpty() && queue.peek().gameTime() <= gameTime) {
			Stroke s = queue.poll();
			s.level().playSound(null, s.pos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, s.volume(), s.pitch());
		}
	}
}
