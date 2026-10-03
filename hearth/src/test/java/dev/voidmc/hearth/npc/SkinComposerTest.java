package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.look.FolkLook;
import dev.voidmc.hearth.npc.look.SkinComposer;
import dev.voidmc.hearth.npc.person.Genome;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class SkinComposerTest {
	@Test
	void sameLookSamePixels() {
		FolkLook look = new FolkLook(0x5A3C91F2, Culture.KARST, JobType.SCHOLAR, AgeStage.ADULT, true);
		assertArrayEquals(SkinComposer.compose(look), SkinComposer.compose(look));
	}

	@Test
	void everyCombinationComposesAFullSkin() {
		Random random = new Random(7);
		for (Culture culture : Culture.values()) {
			for (JobType job : JobType.values()) {
				for (AgeStage age : AgeStage.values()) {
					FolkLook look = new FolkLook(random.nextInt(), culture, job, age, random.nextBoolean());
					int[] px = SkinComposer.compose(look);
					assertEquals(64 * 64, px.length);
					// Every pixel of the base layer's head front and body front is opaque: no holes in a person.
					for (int y = 8; y < 16; y++) {
						for (int x = 8; x < 16; x++) {
							assertEquals(0xFF, px[y * 64 + x] >>> 24, "head front hole at " + x + "," + y + " for " + look);
						}
					}
					for (int y = 20; y < 32; y++) {
						for (int x = 20; x < 28; x++) {
							assertEquals(0xFF, px[y * 64 + x] >>> 24, "body front hole at " + x + "," + y + " for " + look);
						}
					}
				}
			}
		}
	}

	@Test
	void appearanceChangesTheFace() {
		Set<Integer> faces = new HashSet<>();
		RandomSource random = RandomSource.create(3);
		for (int i = 0; i < 40; i++) {
			int[] px = SkinComposer.compose(new FolkLook(Genome.random(Culture.TEMPERATE, random), Culture.TEMPERATE, JobType.NONE,
				AgeStage.ADULT, i % 2 == 0));
			int h = 0;
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 32; x++) {
					h = h * 31 + px[y * 64 + x];
				}
			}
			faces.add(h);
		}
		assertTrue(faces.size() >= 38, "forty folk should have (nearly) forty different heads, got " + faces.size());
	}

	@Test
	void jobsDressDifferently() {
		int g = 0x1234ABCD;
		int[] smith = SkinComposer.compose(new FolkLook(g, Culture.ALPINE, JobType.SMITH, AgeStage.ADULT, false));
		int[] baker = SkinComposer.compose(new FolkLook(g, Culture.ALPINE, JobType.BAKER, AgeStage.ADULT, false));
		assertFalse(java.util.Arrays.equals(smith, baker));
		// The baker's apron (jacket front) is near-white; the smith's is dark leather.
		int bakerApron = baker[(32 + 4 + 6) * 64 + 20 + 3];
		int smithApron = smith[(32 + 4 + 6) * 64 + 20 + 3];
		assertTrue(luma(bakerApron) > 0.8F, "baker's apron should be white");
		assertTrue(luma(smithApron) < 0.35F, "smith's apron should be dark");
	}

	@Test
	void eldersAreGreyer() {
		int g = Genome.pack(3, 2, 1, 0, 0, false, 2);
		int[] adult = SkinComposer.compose(new FolkLook(g, Culture.TEMPERATE, JobType.NONE, AgeStage.ADULT, false));
		int[] elder = SkinComposer.compose(new FolkLook(g, Culture.TEMPERATE, JobType.NONE, AgeStage.ELDER, false));
		// The top of the head is hair.
		assertTrue(luma(elder[3 * 64 + 12]) > luma(adult[3 * 64 + 12]) + 0.2F);
		assertNotEquals(adult[3 * 64 + 12], elder[3 * 64 + 12]);
	}

	/** {@code HEARTH_FOLK_PREVIEW=1 ./gradlew test --tests '*SkinComposerTest*'}: paper-doll sheets in build/previews/folk. */
	@Test
	void preview() throws IOException {
		if (System.getenv("HEARTH_FOLK_PREVIEW") == null) {
			return;
		}
		File dir = new File("build/previews/folk");
		dir.mkdirs();
		JobType[] jobs = JobType.values();
		int scale = 5;
		int cellW = 16 * 3 + 6;
		int cellH = 34;
		// One sheet per culture: rows of men, women, children and elders; columns of jobs.
		for (Culture culture : Culture.values()) {
			RandomSource random = RandomSource.create(culture.ordinal() * 31L + 5);
			BufferedImage sheet = new BufferedImage(jobs.length * cellW * scale, 4 * cellH * scale, BufferedImage.TYPE_INT_ARGB);
			fillBackground(sheet);
			for (int row = 0; row < 4; row++) {
				for (int col = 0; col < jobs.length; col++) {
					AgeStage age = row == 2 ? AgeStage.CHILD : row == 3 ? AgeStage.ELDER : AgeStage.ADULT;
					boolean female = row == 1 || (row >= 2 && col % 2 == 1);
					JobType job = age == AgeStage.CHILD ? JobType.NONE : jobs[col];
					FolkLook look = new FolkLook(Genome.random(culture, random), culture, job, age, female);
					drawDoll(sheet, SkinComposer.compose(look), look.slim(), col * cellW, row * cellH, scale);
				}
			}
			ImageIO.write(sheet, "png", new File(dir, culture.getSerializedName() + ".png"));
		}
		// And a crowd: one of everyone, front views only, for an overall impression.
		RandomSource random = RandomSource.create(99);
		int n = Culture.values().length;
		BufferedImage crowd = new BufferedImage(n * 18 * scale, 6 * cellH * scale, BufferedImage.TYPE_INT_ARGB);
		fillBackground(crowd);
		for (int i = 0; i < n; i++) {
			Culture culture = Culture.values()[i];
			for (int j = 0; j < 6; j++) {
				JobType job = jobs[random.nextInt(jobs.length)];
				AgeStage age = j == 5 ? AgeStage.ELDER : j == 4 ? AgeStage.CHILD : AgeStage.ADULT;
				FolkLook look = new FolkLook(Genome.random(culture, random), culture, age == AgeStage.CHILD ? JobType.NONE : job, age, j % 2 == 0);
				drawFront(crowd, SkinComposer.compose(look), look.slim(), i * 18 + 1, j * cellH + 1, scale);
			}
		}
		ImageIO.write(crowd, "png", new File(dir, "crowd.png"));
	}

	private static void fillBackground(BufferedImage img) {
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				img.setRGB(x, y, 0xFF9AA6A0);
			}
		}
	}

	private static void drawDoll(BufferedImage img, int[] px, boolean slim, int ox, int oy, int scale) {
		drawFront(img, px, slim, ox + 1, oy + 1, scale);
		drawBack(img, px, slim, ox + 18, oy + 1, scale);
		drawSide(img, px, ox + 36, oy + 1, scale);
	}

	private static void drawFront(BufferedImage img, int[] px, boolean slim, int ox, int oy, int scale) {
		int aw = slim ? 3 : 4;
		// Base, then overlay: head, body, arms (wearer's right on the viewer's left), legs.
		blit(img, px, 8, 8, 8, 8, ox + 4, oy, scale);
		blit(img, px, 40, 8, 8, 8, ox + 4, oy, scale);
		blit(img, px, 20, 20, 8, 12, ox + 4, oy + 8, scale);
		blit(img, px, 20, 36, 8, 12, ox + 4, oy + 8, scale);
		blit(img, px, 44, 20, aw, 12, ox + 4 - aw, oy + 8, scale);
		blit(img, px, 44, 36, aw, 12, ox + 4 - aw, oy + 8, scale);
		blit(img, px, 36, 52, aw, 12, ox + 12, oy + 8, scale);
		blit(img, px, 52, 52, aw, 12, ox + 12, oy + 8, scale);
		blit(img, px, 4, 20, 4, 12, ox + 4, oy + 20, scale);
		blit(img, px, 4, 36, 4, 12, ox + 4, oy + 20, scale);
		blit(img, px, 20, 52, 4, 12, ox + 8, oy + 20, scale);
		blit(img, px, 4, 52, 4, 12, ox + 8, oy + 20, scale);
	}

	private static void drawBack(BufferedImage img, int[] px, boolean slim, int ox, int oy, int scale) {
		int aw = slim ? 3 : 4;
		blit(img, px, 24, 8, 8, 8, ox + 4, oy, scale);
		blit(img, px, 56, 8, 8, 8, ox + 4, oy, scale);
		blit(img, px, 32, 20, 8, 12, ox + 4, oy + 8, scale);
		blit(img, px, 32, 36, 8, 12, ox + 4, oy + 8, scale);
		// From behind the left arm is on the viewer's left.
		blit(img, px, 32 + 4 + aw + 4, 52, aw, 12, ox + 4 - aw, oy + 8, scale);
		blit(img, px, 48 + 4 + aw + 4, 52, aw, 12, ox + 4 - aw, oy + 8, scale);
		blit(img, px, 40 + 4 + aw + 4, 20, aw, 12, ox + 12, oy + 8, scale);
		blit(img, px, 40 + 4 + aw + 4, 36, aw, 12, ox + 12, oy + 8, scale);
		blit(img, px, 28, 52, 4, 12, ox + 4, oy + 20, scale);
		blit(img, px, 12, 52, 4, 12, ox + 4, oy + 20, scale);
		blit(img, px, 12, 20, 4, 12, ox + 8, oy + 20, scale);
		blit(img, px, 12, 36, 4, 12, ox + 8, oy + 20, scale);
	}

	private static void drawSide(BufferedImage img, int[] px, int ox, int oy, int scale) {
		// The wearer's right side.
		blit(img, px, 0, 8, 8, 8, ox + 4, oy, scale);
		blit(img, px, 32, 8, 8, 8, ox + 4, oy, scale);
		blit(img, px, 16, 20, 4, 12, ox + 6, oy + 8, scale);
		blit(img, px, 16, 36, 4, 12, ox + 6, oy + 8, scale);
		blit(img, px, 40, 20, 4, 12, ox + 6, oy + 8, scale);
		blit(img, px, 40, 36, 4, 12, ox + 6, oy + 8, scale);
		blit(img, px, 0, 20, 4, 12, ox + 6, oy + 20, scale);
		blit(img, px, 0, 36, 4, 12, ox + 6, oy + 20, scale);
	}

	private static void blit(BufferedImage img, int[] px, int u, int v, int w, int h, int ox, int oy, int scale) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = px[(v + y) * 64 + u + x];
				int a = c >>> 24;
				if (a == 0) {
					continue;
				}
				for (int sy = 0; sy < scale; sy++) {
					for (int sx = 0; sx < scale; sx++) {
						int ix = (ox + x) * scale + sx;
						int iy = (oy + y) * scale + sy;
						if (ix < img.getWidth() && iy < img.getHeight()) {
							int under = img.getRGB(ix, iy);
							img.setRGB(ix, iy, a == 255 ? c : blend(under, c, a / 255.0F));
						}
					}
				}
			}
		}
	}

	private static int blend(int under, int over, float a) {
		int r = Math.round((under >> 16 & 0xFF) * (1 - a) + (over >> 16 & 0xFF) * a);
		int g = Math.round((under >> 8 & 0xFF) * (1 - a) + (over >> 8 & 0xFF) * a);
		int b = Math.round((under & 0xFF) * (1 - a) + (over & 0xFF) * a);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static float luma(int argb) {
		return ((argb >> 16 & 0xFF) * 0.299F + (argb >> 8 & 0xFF) * 0.587F + (argb & 0xFF) * 0.114F) / 255.0F;
	}
}
