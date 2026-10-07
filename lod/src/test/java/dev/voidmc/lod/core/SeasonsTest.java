package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.lod.api.LodApi;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SeasonsTest {
	private static final int GRASS_ID = 7;
	private static final TerrainSource FLAT = (x, z, cell, out) -> {
		out.ground = 64;
		out.top = 0x60A040;
		out.side = 0x806040;
		out.topMaterial = GRASS_ID;
	};

	@AfterEach
	void noSeasons() {
		LodApi.setSeasons(null);
	}

	@Test
	void grassTopsCarryTheirClassNearAndFar() {
		LodApi.setSeasons((kind, biome, material, conifer) -> kind == LodApi.Seasons.GROUND_TOP && material == GRASS_ID ? 5 : 0);
		// near: the code in the second colour's blue, on the top's vertices
		TileBuilder.Mesh near = new TileBuilder(false).build(FLAT, TileKey.of(0, 0, 0));
		ByteBuffer v = near.vertices();
		Set<Integer> tops = new TreeSet<>(), walls = new TreeSet<>();
		int[] d = new int[4];
		for (int i = 0; i < near.vertexCount(); i++) {
			TileBuilder.decode(near, i, d);
			int code = v.get(i * TileBuilder.VERTEX_BYTES + 14) & 0xFF;
			((d[3] & 7) == TileBuilder.UP ? tops : walls).add(code);
		}
		assertEquals(Set.of(5), tops);
		assertEquals(Set.of(0), walls, "the skirts are soil and rock, which never change");
		// far: bits 13.. of a record's last word
		TileBuilder.Mesh far = new TileBuilder(false).build(FLAT, TileKey.of(TileBuilder.COMPACT_FROM, 0, 0));
		assertTrue(far.compact());
		Set<Integer> farTops = new TreeSet<>();
		for (int q = 0; q < far.vertexCount() / 4; q++) {
			int o = q * TileBuilder.RECORD_BYTES;
			if ((far.vertices().getInt(o) >> 14 & 7) == TileBuilder.UP) {
				farTops.add(far.vertices().getInt(o + 12) >>> 13 & 127);
			}
		}
		assertEquals(Set.of(5), farTops);
	}

	@Test
	void theTableMovesTowardItsTargetKeepingBrightness() {
		float[] table = new float[LodApi.Seasons.CLASSES * LodApi.Seasons.VARIANTS * 5];
		for (int e = 0; e < table.length / 5; e++) {
			table[e * 5 + 4] = 1;
		}
		// class 3, variant 2: all the way to orange, at its own brightness; then 80% as bright
		int e = (3 * 4 + 2) * 5;
		table[e] = 1.0F;
		table[e + 1] = 0.5F;
		table[e + 2] = 0.0F;
		table[e + 3] = 1.0F;
		int green = 0x40A030;
		int autumn = LodApi.seasonColour(green, 3 | 2 << 5, table);
		assertTrue((autumn >> 16 & 255) > (autumn >> 8 & 255), "red over green");
		float lum0 = lum(green), lum1 = lum(autumn);
		assertEquals(lum0, lum1, 0.02F);
		table[e + 4] = 0.8F;
		assertEquals(lum0 * 0.8F, lum(LodApi.seasonColour(green, 3 | 2 << 5, table)), 0.02F);
		assertEquals(green, LodApi.seasonColour(green, 0, table), "class 0 never changes");
		assertEquals(green, LodApi.seasonColour(green, 3 | 1 << 5, table), "another variant, untouched in this table");
	}

	@Test
	void variantsAreSteadyWithinTwelveBlocksAndVaryBeyond() {
		assertEquals(LodApi.seasonVariant(0, 0), LodApi.seasonVariant(11, 11));
		assertEquals(LodApi.seasonVariant(-1, -1), LodApi.seasonVariant(-12, -12));
		Set<Integer> seen = new TreeSet<>();
		for (int x = 0; x < 12 * 16; x += 12) {
			seen.add(LodApi.seasonVariant(x, 0));
		}
		assertEquals(Set.of(0, 1, 2, 3), seen);
	}

	private static float lum(int rgb) {
		return (0.2126F * (rgb >> 16 & 255) + 0.7152F * (rgb >> 8 & 255) + 0.0722F * (rgb & 255)) / 255;
	}
}
