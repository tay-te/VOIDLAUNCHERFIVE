package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.look.FolkLook;
import dev.voidmc.hearth.npc.look.Portrait;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Residents' 16×16 portraits for the Hub: cut from their own skin, and a PNG any browser reads. */
class PortraitTest {
	private static final int SKIN_ARGB = 0xFFC08060;

	@Test
	void theBustTakesTheFaceTheBodyAndTheArmsFromTheSkin() {
		int[] skin = new int[64 * 64];
		// the face's front (8..15, 8..15), the body's (20..27, 20..31), the arms' fronts
		fill(skin, 8, 8, 8, 8, SKIN_ARGB);
		fill(skin, 20, 20, 8, 12, 0xFF304050);
		fill(skin, 44, 20, 4, 12, 0xFF102030);
		fill(skin, 36, 52, 4, 12, 0xFF203040);
		// a hat brim over the face's top row
		fill(skin, 40, 8, 8, 1, 0xFF6A4A2A);
		int[] p = Portrait.bust(skin, false);
		assertEquals(256, p.length);
		assertEquals(0, p[0] >>> 24, "the corner is clear");
		assertEquals(0xFF6A4A2A, p[1 * 16 + 4], "the hat over the head");
		assertEquals(SKIN_ARGB, p[4 * 16 + 7], "the face");
		assertEquals(0xFF304050, p[12 * 16 + 7], "the body");
		assertEquals(0xFF102030, p[12 * 16 + 1], "the right arm on the viewer's left");
		assertEquals(0xFF203040, p[12 * 16 + 14], "the left arm");
	}

	@Test
	void eachLookHasItsOwnPortraitAsAPng() throws Exception {
		FolkLook a = new FolkLook(1234, Culture.values()[0], JobType.INNKEEPER, AgeStage.ADULT, true);
		FolkLook b = new FolkLook(98765, Culture.values()[0], JobType.GUARD, AgeStage.ADULT, false);
		int[] pa = Portrait.of(a);
		int[] pb = Portrait.of(b);
		assertArrayEquals(pa, Portrait.of(a), "the same look, the same face");
		assertNotEquals(java.util.Arrays.hashCode(pa), java.util.Arrays.hashCode(pb));
		String url = Portrait.dataUrl(pa);
		assertTrue(url.startsWith("data:image/png;base64,"));
		byte[] png = Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1));
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
		assertEquals(16, img.getWidth());
		assertEquals(16, img.getHeight());
		assertEquals(pa[4 * 16 + 7], img.getRGB(7, 4), "the PNG holds the same pixels");
		assertTrue(png.length < 2048, "small enough to send with every state");
	}

	private static void fill(int[] skin, int x, int y, int w, int h, int c) {
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				skin[(y + j) * 64 + x + i] = c;
			}
		}
	}
}
