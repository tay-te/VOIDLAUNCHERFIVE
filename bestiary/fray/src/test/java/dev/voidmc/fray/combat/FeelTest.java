package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.combat.math.Feel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Hit-stop and camera shake maths (COMBAT.md §5). */
class FeelTest {
	private static Feel.Stop stop(int flags) {
		return Feel.hitStop(flags, GuardOutcome.NONE, false, false);
	}

	@Test
	void hitStopScalesWithWeight() {
		assertEquals(new Feel.Stop(2, 2), stop(HitFlags.LIGHT), "a light: 2 ticks");
		assertEquals(3, stop(HitFlags.LIGHT | HitFlags.CRIT).victim(), "a crit adds one");
		assertEquals(4, stop(HitFlags.HEAVY).victim());
		assertEquals(5, stop(HitFlags.CHARGED).victim());
		assertEquals(7, stop(HitFlags.RIPOSTE | HitFlags.CRIT).victim(), "riposte 6, it counts as a crit: 7");
		assertEquals(8, Feel.hitStop(HitFlags.HEAVY, GuardOutcome.NONE, true, false).victim(), "a Break: 8");
		assertEquals(Feel.MAX_STOP, Feel.hitStop(HitFlags.RIPOSTE | HitFlags.CRIT, GuardOutcome.NONE, true, true).victim(), "capped");
		int light = stop(HitFlags.LIGHT).victim();
		for (int f : new int[] {HitFlags.HEAVY, HitFlags.CHARGED, HitFlags.RIPOSTE, HitFlags.PLUNGE, HitFlags.EXECUTE}) {
			assertTrue(stop(f).victim() > light, "heavier than a light: " + f);
		}
	}

	@Test
	void hitStopByOutcome() {
		assertEquals(Feel.Stop.NONE, Feel.hitStop(HitFlags.HEAVY, GuardOutcome.DODGED, false, false), "a dodged hit doesn't stop");
		assertEquals(Feel.Stop.NONE, Feel.hitStop(HitFlags.HEAVY, GuardOutcome.SLIP, false, false));
		assertEquals(new Feel.Stop(4, 4), Feel.hitStop(HitFlags.LIGHT, GuardOutcome.PARRIED, false, false));
		assertEquals(new Feel.Stop(5, 6), Feel.hitStop(HitFlags.LIGHT, GuardOutcome.GUARD_BREAK, false, false));
		assertEquals(2, Feel.hitStop(HitFlags.LIGHT, GuardOutcome.BLOCKED, false, false).victim());
		assertEquals(Feel.Stop.NONE, stop(HitFlags.STATUS), "status procs never stop");
		assertEquals(0, stop(HitFlags.PROJECTILE).attacker(), "an archer isn't frozen by its arrow");
		assertEquals(2, stop(HitFlags.PROJECTILE).victim());
	}

	@Test
	void shakeWeights() {
		assertEquals(1, Feel.weight(HitFlags.LIGHT, GuardOutcome.NONE, true, false), 1e-6, "Break is the heaviest");
		assertEquals(1, Feel.weight(HitFlags.HEAVY_TELL, GuardOutcome.NONE, false, true), 1e-6, "a boss slam too");
		assertEquals(0.9F, Feel.weight(HitFlags.LIGHT, GuardOutcome.GUARD_BREAK, false, false), 1e-6);
		assertEquals(0, Feel.weight(HitFlags.HEAVY, GuardOutcome.DODGED, false, false));
		assertTrue(Feel.weight(HitFlags.LIGHT, GuardOutcome.NONE, false, false) < Feel.weight(HitFlags.HEAVY, GuardOutcome.NONE, false, false));
	}

	@Test
	void shakeAmplitude() {
		float light = Feel.amplitude(Feel.Role.INCOMING, 0.25F, 0.05F, 0, false);
		float heavy = Feel.amplitude(Feel.Role.INCOMING, 0.5F, 0.05F, 0, false);
		float hurt = Feel.amplitude(Feel.Role.INCOMING, 0.5F, 0.3F, 0, false);
		assertTrue(light > 0 && heavy > light && hurt > heavy, light + " < " + heavy + " < " + hurt);
		assertEquals(0, Feel.amplitude(Feel.Role.OUTGOING, 0.25F, 0, 0, false), "my own lights don't shake");
		assertEquals(1.6F, Feel.amplitude(Feel.Role.OUTGOING, 0.5F, 0, 0, true), 1e-6, "my Break does");
		assertEquals(0, Feel.amplitude(Feel.Role.BYSTANDER, 0.5F, 0, 2, false), "others' hits don't shake me");
		assertTrue(Feel.amplitude(Feel.Role.BYSTANDER, 1, 0, 4, false) > Feel.amplitude(Feel.Role.BYSTANDER, 1, 0, 12, false), "a slam fades with distance");
		assertEquals(0, Feel.amplitude(Feel.Role.BYSTANDER, 1, 0, 20, false));
		assertTrue(Feel.amplitude(Feel.Role.INCOMING, 1, 1, 0, true) <= Feel.MAX_SHAKE);
	}

	@Test
	void shakeDecaysQuickly() {
		float tau = Feel.tau(1);
		assertEquals(1, Feel.along(0, tau), 1e-6);
		assertTrue(Math.abs(Feel.along(2 * tau, tau)) < 0.14F);
		assertEquals(0, Feel.along(4 * tau + 0.001F, tau), "gone after four time constants");
		assertTrue(Feel.life(1) <= 0.5F, "the heaviest shake is over within half a second");
		float[] big = {6, 8};
		Feel.clamp(big);
		assertEquals(Feel.MAX_SHAKE, Math.hypot(big[0], big[1]), 1e-4);
	}

	@Test
	void shakeDirection() {
		// Facing +Z (yaw 0); the right-hand side is -X. Hit from the left (+X side): pushed toward -X, the view turns right.
		float[] u = Feel.direction(-1, 0, 0);
		assertEquals(1, u[0], 1e-5);
		assertEquals(0, u[1], 1e-5);
		// Hit from the front: pushed back (-Z), the view tips up (negative pitch).
		u = Feel.direction(0, -1, 0);
		assertEquals(0, u[0], 1e-5);
		assertEquals(-1, u[1], 1e-5);
		assertArrayEquals(new float[] {0, 1}, Feel.direction(0, 0, 90), "no direction: a dip");
	}

	/** Hit-stop is a picture: the server side (main source set) never refers to the client's freeze or shake. */
	@Test
	void serverNeverReferencesHitStop() throws IOException {
		Path main = Path.of("src/main/java");
		try (Stream<Path> files = Files.walk(main)) {
			List<Path> bad = files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("Feel.java")).filter(p -> {
				try {
					String s = Files.readString(p);
					return s.contains("HitStop") || s.contains("CameraShake") || s.contains("Feel.");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}).toList();
			assertEquals(List.of(), bad, "server code that reads the hit-stop");
		}
	}
}
