package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FadesTest {
	private static final long F = Fades.FADE_NANOS;

	private static Map<Long, Integer> run(Fades fades, long now, long... keys) {
		LongList draw = new LongList();
		for (long k : keys) {
			draw.add(k);
		}
		fades.update(draw, now, k -> true);
		Map<Long, Integer> out = new HashMap<>();
		for (int i = 0; i < fades.size(); i++) {
			out.put(fades.key(i), fades.fade(i));
		}
		return out;
	}

	@Test
	void aNewTileFadesInThenStaysSolid() {
		Fades fades = new Fades();
		assertTrue(run(fades, 0, 1L).get(1L) > 0);
		assertEquals(0, run(fades, F, 1L).get(1L));
	}

	@Test
	void aSwapCrossFadesWithComplementaryDither() {
		Fades fades = new Fades();
		run(fades, 0, 1L);
		run(fades, F, 1L);
		// children 2..5 replace tile 1
		Map<Long, Integer> mid = run(fades, 2 * F, 2L, 3L, 4L, 5L);
		assertEquals(5, mid.size(), "the parent keeps drawing while its children fade in");
		Map<Long, Integer> half = run(fades, 2 * F + F / 2, 2L, 3L, 4L, 5L);
		assertEquals(half.get(2L), -half.get(1L), "incoming and outgoing split every pixel between them");
		Map<Long, Integer> done = run(fades, 3 * F, 2L, 3L, 4L, 5L);
		assertEquals(4, done.size(), "the parent is gone once the fade completes");
		assertEquals(0, done.get(2L));
	}

	@Test
	void aTileThatComesBackPicksUpWhereItWas() {
		Fades fades = new Fades();
		run(fades, 0, 1L);
		run(fades, F, 1L);
		run(fades, 2 * F, 2L);
		// a quarter of the way out, tile 1 comes back: three quarters of it still show
		Map<Long, Integer> back = run(fades, 2 * F + F / 4, 1L);
		int fade = back.get(1L);
		assertTrue(fade > 180 && fade < 200, "resumes at about 3/4: " + fade);
	}
}
