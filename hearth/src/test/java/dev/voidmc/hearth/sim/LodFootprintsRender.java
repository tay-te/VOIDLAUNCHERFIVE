package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.atlas.Atlas;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A top-down picture of what VOID LOD gets from {@link LodFootprints#sample} for the most settled region near the
 * origin (synthetic hills, real catalogue): left, the roof colour shaded by the height LOD would give the column (the
 * roof climbing one block per block in from the footprint's edge up to the ridge, capped at 8); right, the wall colour,
 * with planned columns hatched. Run with {@code HEARTH_RENDER=<dir> ./gradlew test --tests '*LodFootprintsRender*'}.
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_RENDER", matches = ".+")
class LodFootprintsRender {
	@Test
	void render() throws Exception {
		TestSupport.useRealCatalogue();
		long seed = Long.parseLong(System.getenv().getOrDefault("HEARTH_RENDER_SEED", "8675309"));
		SyntheticTerrain t = new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, seed);
		Atlas atlas = new Atlas(Level.OVERWORLD, t, null, TestSupport.rules(), null, b -> Culture.TEMPERATE);
		int bestX = 0;
		int bestZ = 0;
		long best = -1;
		for (int rx = -3; rx < 3; rx++) {
			for (int rz = -3; rz < 3; rz++) {
				long n = atlas.plan(rx, rz).plots().stream().filter(hp -> hp.completed() >= hp.stageCount() && !hp.road()).count();
				if (n > best) {
					best = n;
					bestX = rx;
					bestZ = rz;
				}
			}
		}
		LodFootprints f = new LodFootprints(atlas, t);
		f.sample(bestX << 10, bestZ << 10, new int[3]);
		while (!f.ready(bestX, bestZ)) {
			f.version(bestX, bestZ);
			Thread.sleep(10);
		}
		int x0 = bestX << 10;
		int z0 = bestZ << 10;
		int[][] cols = new int[1024 * 1024][];
		int minX = 1024, minZ = 1024, maxX = -1, maxZ = -1;
		for (int x = 0; x < 1024; x++) {
			for (int z = 0; z < 1024; z++) {
				int[] out = new int[3];
				if (f.sample(x0 + x, z0 + z, out)) {
					cols[z * 1024 + x] = out;
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
		}
		if (maxX < 0) {
			throw new AssertionError("nothing drawn");
		}
		minX = Math.max(0, minX - 8);
		minZ = Math.max(0, minZ - 8);
		maxX = Math.min(1023, maxX + 8);
		maxZ = Math.min(1023, maxZ + 8);
		// distance in from the footprint's edge (4-neighbour), as LOD's roof climbs
		int[] dist = new int[1024 * 1024];
		ArrayDeque<Integer> q = new ArrayDeque<>();
		for (int i = 0; i < dist.length; i++) {
			dist[i] = cols[i] == null ? 0 : -1;
		}
		for (int i = 0; i < dist.length; i++) {
			if (cols[i] != null) {
				int x = i & 1023, z = i >> 10;
				if (x == 0 || z == 0 || x == 1023 || z == 1023 || cols[i - 1] == null || cols[i + 1] == null || cols[i - 1024] == null
					|| cols[i + 1024] == null) {
					dist[i] = 1;
					q.add(i);
				}
			}
		}
		while (!q.isEmpty()) {
			int i = q.poll();
			for (int n : new int[]{i - 1, i + 1, i - 1024, i + 1024}) {
				if (n >= 0 && n < dist.length && dist[n] == -1) {
					dist[n] = dist[i] + 1;
					q.add(n);
				}
			}
		}
		int scale = Math.max(1, Math.min(6, 900 / Math.max(maxX - minX + 1, maxZ - minZ + 1)));
		int w = (maxX - minX + 1) * scale;
		int h = (maxZ - minZ + 1) * scale;
		BufferedImage img = new BufferedImage(w * 2 + 8, h, BufferedImage.TYPE_INT_RGB);
		for (int px = 0; px < img.getWidth(); px++) {
			for (int py = 0; py < h; py++) {
				img.setRGB(px, py, 0x2E3A26);
			}
		}
		for (int z = minZ; z <= maxZ; z++) {
			for (int x = minX; x <= maxX; x++) {
				int i = z * 1024 + x;
				int[] c = cols[i];
				if (c == null) {
					continue;
				}
				int eave = c[2] >> 16;
				int ridge = (short) c[2];
				int roofUp = Math.min(Math.min(dist[i] - 1, 8), ridge - eave);
				// shade: higher is lighter, the roof's slope readable
				double shade = 0.62 + 0.38 * (ridge > eave ? roofUp / (double) Math.max(1, Math.min(8, ridge - eave)) : 0.5);
				int roof = shadeRgb(c[1] & 0xFFFFFF, shade);
				int wall = c[0] & 0xFFFFFF;
				boolean planned = (c[0] & LodFootprints.STATE) == LodFootprints.PLANNED;
				for (int sx = 0; sx < scale; sx++) {
					for (int sz = 0; sz < scale; sz++) {
						int px = (x - minX) * scale + sx;
						int py = (z - minZ) * scale + sz;
						img.setRGB(px, py, roof);
						img.setRGB(w + 8 + px, py, planned && (px + py) % 4 == 0 ? 0xFF00FF : wall);
					}
				}
			}
		}
		Path dir = Path.of(System.getenv("HEARTH_RENDER"));
		Files.createDirectories(dir);
		Path file = dir.resolve("lod_footprints_region_" + bestX + "_" + bestZ + ".png");
		ImageIO.write(img, "png", file.toFile());
		System.out.printf("RENDER %s (%d finished plots, region %d,%d, %dx scale, services %s)%n", file, best, bestX, bestZ, scale,
			HearthServices.blueprints().getClass().getSimpleName());
	}

	private static int shadeRgb(int c, double s) {
		int r = (int) Math.min(255, (c >> 16 & 255) * s);
		int g = (int) Math.min(255, (c >> 8 & 255) * s);
		int b = (int) Math.min(255, (c & 255) * s);
		return r << 16 | g << 8 | b;
	}
}
