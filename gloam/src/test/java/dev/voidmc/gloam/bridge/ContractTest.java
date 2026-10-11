package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.bridge.Contract.Slot;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContractTest {
	@Test
	void h16CoversTheWorldToAThirtySecondOfABlock() {
		for (float y : new float[]{-1024, -64, -0.5F, 0, 62.96875F, 63, 100.25F, 383, 639.03125F, 1023.96875F}) {
			int h = Contract.h16(y);
			assertEquals(y, Contract.fromH16(h >>> 8, h & 255), 1e-6, "h16 of " + y);
		}
		// anything between steps rounds to the nearest 32nd
		int h = Contract.h16(70.01F);
		assertEquals(70.0F, Contract.fromH16(h >>> 8, h & 255), 1e-6);
		// and the ends clamp
		assertEquals(0, Contract.h16(-5000));
		assertEquals(65535, Contract.h16(5000));
	}

	@Test
	void theN0TexelCarriesH16InRedAndGreen() {
		int t = Contract.n0(87.5F, 0.5F, 1000);
		int hi = t & 255, lo = t >>> 8 & 255;
		assertEquals(87.5F, Contract.fromH16(hi, lo), 1e-6);
		assertEquals(128, t >>> 16 & 255);
		assertEquals(250, t >>> 24 & 255, "1 km of water distance is 250 four-block steps");
		assertEquals(255, Contract.n0(0, 0, 5000) >>> 24 & 255);
	}

	@Test
	void theN1TexelPacksHazeKindAndFlagsIntoAlpha() {
		int t = Contract.n1(1, -1, 0.5F, Contract.hazeKind("misty_lakes"), Contract.CELL_PRESENT | Contract.CELL_WATER);
		assertEquals(255, t & 255);
		assertEquals(0, t >>> 8 & 255);
		assertEquals(128, t >>> 16 & 255);
		int a = t >>> 24 & 255;
		assertEquals(3, a >> 4);
		assertEquals(Contract.CELL_PRESENT | Contract.CELL_WATER, a & 15);
	}

	@Test
	void cellsWrapIntoTheirTileWhateverTheCentre() {
		// tile t starts at ((t % 4) * 256, 4 + (t / 4) * 256)
		assertEquals(0, Contract.texelX(Contract.N0, 0));
		assertEquals(256, Contract.texelX(Contract.N1, 0));
		assertEquals(4, Contract.texelY(Contract.N0, 0));
		assertEquals(4 + 256, Contract.texelY(Contract.W0, 0));
		assertEquals(512 + 255, Contract.texelX(Contract.F0, -1), "cell -1 sits in the tile's last column");
		assertEquals(4 + 255, Contract.texelY(Contract.N0, -1));
		assertEquals(Contract.texelX(Contract.N0, 5), Contract.texelX(Contract.N0, 5 + 256 * 1000));
		assertEquals(Contract.texelX(Contract.N0, -7), Contract.texelX(Contract.N0, -7 - 256 * 3));

		// every cell of a window round any centre has a texel of its own
		for (int centre : new int[]{0, 1, -1, 127, 128, -129, 123456, -987654}) {
			Set<Integer> seen = new HashSet<>();
			for (int c = centre - 127; c <= centre + 128; c++) {
				int x = Contract.texelX(Contract.N1, c);
				assertTrue(x >= 256 && x < 512);
				assertTrue(seen.add(x), "two cells share a texel round " + centre);
			}
		}
		assertTrue(Contract.valid(10, -117, 10, 10));
		assertFalse(Contract.valid(10, -118, 10, 10), "128 cells off is out of reach");
	}

	@Test
	void cellsFloorNegativeCoordinates() {
		assertEquals(0, Contract.cell(15.9, 16));
		assertEquals(-1, Contract.cell(-0.1, 16));
		assertEquals(-2, Contract.cell(-17, 16));
	}

	@Test
	void centresComeBackFromTheirLowSixteenBits() {
		for (int centre : new int[]{0, 5, -5, 32767, 70000, -1_875_000, 1_875_000}) {
			for (int camera : new int[]{centre, centre - 200, centre + 3000}) {
				assertEquals(centre, Contract.unwrap16(centre & 0xFFFF, camera));
			}
		}
		byte[] b = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		Contract.centre(b, Contract.N1, -1_875_000, 70000);
		int o = Contract.offset(Contract.CENTRES_TEXEL + Contract.N1, 0);
		int x16 = b[o] & 255 | (b[o + 1] & 255) << 8, z16 = b[o + 2] & 255 | (b[o + 3] & 255) << 8;
		assertEquals(-1_875_000, Contract.unwrap16(x16, -1_874_990));
		assertEquals(70000, Contract.unwrap16(z16, 70100));
	}

	@Test
	void theHeaderStartsWithTheMagicAndVersion() {
		byte[] b = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		Contract.header(b, Contract.FLAG_EXPANSE | Contract.FLAG_SINGLEPLAYER, 0x1234);
		assertArrayEquals(new byte[]{'V', 'O', 'I', 'D'}, new byte[]{b[0], b[1], b[2], b[3]});
		assertEquals(Contract.VERSION, b[4]);
		assertEquals(Contract.FLAG_EXPANSE | Contract.FLAG_SINGLEPLAYER, b[5]);
		assertEquals(0x34, b[6] & 255);
		assertEquals(0x12, b[7] & 255);
		Contract.masks(b, 3, 0x8000_0001);
		assertEquals(3, b[Contract.offset(Contract.TILE_MASK_TEXEL, 0)]);
		assertEquals(0x8000_0001, Contract.getInt(b, Contract.offset(Contract.LIVE_MASK_TEXEL, 0)));
	}

	@Test
	void theMirrorHoldsEveryUniformInDesignOrder() {
		assertEquals(31, Slot.values().length, "the design's table 2.2 has 31 uniforms");
		assertEquals(0, Slot.BRIDGE.ordinal());
		assertEquals(16, Slot.MIST_TIME.ordinal());
		assertEquals(27, Slot.NEAR_CENTRE.ordinal());
		// the mirror's columns stay clear of row 0's own fields only by row: every slot fits in the header's width
		assertTrue(Slot.values().length <= 64);
		for (Slot s : Slot.values()) {
			assertTrue(s.glsl.startsWith("void_"));
		}
	}

	@Test
	void theMirrorRoundTripsFloatsVectorsAndInts() {
		byte[] b = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
		Contract.mirror(b, Slot.MIST_TIME, new float[]{0.73F, 0, 0, 0}, new int[3]);
		assertEquals(0.73F, Contract.mirroredFloat(b, Slot.MIST_TIME, 0), 1.0F / 65535);
		// row 1 alone is the 8-bit value
		assertEquals(Math.round(0.73F * 65535) >>> 8, b[Contract.offset(Slot.MIST_TIME.ordinal(), 1)] & 255);

		Contract.mirror(b, Slot.MIST_TOP, new float[]{87.25F, 0, 0, 0}, new int[3]);
		assertEquals(87.25F, Contract.mirroredFloat(b, Slot.MIST_TOP, 0), 1.0F / 32);

		Contract.mirror(b, Slot.WIND, new float[]{-0.6F, 0.8F, 7.5F, 0.25F}, new int[3]);
		assertEquals(-0.6F, Contract.mirroredFloat(b, Slot.WIND, 0), 1e-4);
		assertEquals(0.8F, Contract.mirroredFloat(b, Slot.WIND, 1), 1e-4);
		assertEquals(7.5F, Contract.mirroredFloat(b, Slot.WIND, 2), 1e-3);
		assertEquals(0.25F, Contract.mirroredFloat(b, Slot.WIND, 3), 1e-4);

		Contract.mirror(b, Slot.SEED, new float[4], new int[]{0x7ABC_DEF1, 0, 0});
		assertEquals(0x7ABC_DEF1, Contract.mirroredInt(b, Slot.SEED, 0));
		Contract.mirror(b, Slot.NEAR_CENTRE, new float[4], new int[]{-117_188, 250_000, 0});
		assertEquals(-117_188, Contract.mirroredInt(b, Slot.NEAR_CENTRE, 0));
		assertEquals(250_000, Contract.mirroredInt(b, Slot.NEAR_CENTRE, 1));

		// out of range clamps to the ends
		Contract.mirror(b, Slot.RAIN, new float[]{1.7F, 0, 0, 0}, new int[3]);
		assertEquals(1.0F, Contract.mirroredFloat(b, Slot.RAIN, 0), 1e-6);
		// a slot's mirror never reaches into its neighbour's column
		assertEquals(0, b[Contract.offset(Slot.RAIN.ordinal() + 1, 1)]);
	}
}
