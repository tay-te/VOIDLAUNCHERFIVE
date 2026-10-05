package dev.voidmc.mettle.burden;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.LoadBand;
import org.junit.jupiter.api.Test;

/** Load bands and how band modifiers move them (BURDEN.md §3, BALANCE.md §5.1). */
class BandsTest {
	private static final BandRules DEFAULT = BandRules.DEFAULT;

	@Test
	void defaultThresholds() {
		assertEquals(LoadBand.LIGHT, Bands.band(0f, 49f, DEFAULT));
		assertEquals(LoadBand.LIGHT, Bands.band(36.7f, 49f, DEFAULT));
		assertEquals(LoadBand.BURDENED, Bands.band(36.75f, 49f, DEFAULT));
		assertEquals(LoadBand.BURDENED, Bands.band(48.9f, 49f, DEFAULT));
		assertEquals(LoadBand.OVERBURDENED, Bands.band(49f, 49f, DEFAULT));
		assertEquals(LoadBand.OVERBURDENED, Bands.band(61.25f, 49f, DEFAULT));
		assertEquals(LoadBand.PINNED, Bands.band(61.3f, 49f, DEFAULT));
		// No capacity at all: anything carried pins you, nothing carried is light.
		assertEquals(LoadBand.PINNED, Bands.band(0.1f, 0f, DEFAULT));
		assertEquals(LoadBand.LIGHT, Bands.band(0f, 0f, DEFAULT));
	}

	@Test
	void modifiersMoveThresholds() {
		// Strong Back: Burdened from 85 %.
		BandRules strongBack = new BandRules(0.85f, 1.0f, 1.25f, 1.0f, true);
		assertEquals(LoadBand.LIGHT, Bands.band(40f, 49f, strongBack));
		assertEquals(LoadBand.BURDENED, Bands.band(42f, 49f, strongBack));
		// Beast of Burden: never Pinned, Overburdened at worst.
		BandRules beast = new BandRules(0.75f, 1.0f, 1.25f, 1.0f, false);
		assertEquals(LoadBand.OVERBURDENED, Bands.band(500f, 49f, beast));
		// Ox: penalties at half strength.
		BandRules ox = new BandRules(0.75f, 1.0f, 1.25f, 0.5f, true);
		assertEquals(-0.35f, Bands.scaled(Bands.SPEED, LoadBand.PINNED, ox), 1e-6);
		assertEquals(-0.2f, Bands.scaled(Bands.JUMP, LoadBand.OVERBURDENED, ox), 1e-6);
		assertEquals(0.25f, Bands.scaled(Bands.HUNGER, LoadBand.BURDENED, ox), 1e-6);
		assertEquals(0.125f, Bands.scaled(Bands.FALL, LoadBand.BURDENED, ox), 1e-6);
		// Athletics' Steady Pace: no band penalty at all while Burdened (scale 0).
		BandRules steady = new BandRules(0.75f, 1.0f, 1.25f, 0f, true);
		for (float[] table : new float[][] {Bands.SPEED, Bands.JUMP, Bands.FALL, Bands.HUNGER, Bands.MOUNT, Bands.SINK}) {
			assertEquals(0f, Bands.scaled(table, LoadBand.BURDENED, steady), 1e-9);
		}
	}

	@Test
	void penaltiesMatchTheBalanceSheet() {
		assertEquals(0f, Bands.scaled(Bands.SPEED, LoadBand.LIGHT, DEFAULT));
		assertEquals(-0.10f, Bands.scaled(Bands.SPEED, LoadBand.BURDENED, DEFAULT), 1e-6);
		assertEquals(-0.30f, Bands.scaled(Bands.SPEED, LoadBand.OVERBURDENED, DEFAULT), 1e-6);
		assertEquals(-0.70f, Bands.scaled(Bands.SPEED, LoadBand.PINNED, DEFAULT), 1e-6);
		assertEquals(0f, Bands.scaled(Bands.JUMP, LoadBand.BURDENED, DEFAULT));
		assertEquals(-0.40f, Bands.scaled(Bands.JUMP, LoadBand.OVERBURDENED, DEFAULT), 1e-6);
		assertEquals(-1.0f, Bands.scaled(Bands.JUMP, LoadBand.PINNED, DEFAULT), 1e-6);
		assertEquals(0.25f, Bands.scaled(Bands.FALL, LoadBand.BURDENED, DEFAULT), 1e-6);
		assertEquals(0.5f, Bands.scaled(Bands.HUNGER, LoadBand.BURDENED, DEFAULT), 1e-6);
		assertEquals(0f, Bands.scaled(Bands.MOUNT, LoadBand.BURDENED, DEFAULT));
		assertEquals(-0.2f, Bands.scaled(Bands.MOUNT, LoadBand.OVERBURDENED, DEFAULT), 1e-6);
	}
}
