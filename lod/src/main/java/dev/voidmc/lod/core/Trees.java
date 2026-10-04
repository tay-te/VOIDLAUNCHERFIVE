package dev.voidmc.lod.core;

/**
 * Individual trees for the close, blocky tiles, where a forest drawn as a flat blanket would read as a
 * slab. Trees stand on a jittered grid one crown across, each grid cell holding a tree with the
 * probability that gives the biome's cover; each tree gets its own height, crown, shade and kind (a conifer
 * with the probability of the biome's conifer share). It is all a hash of world position, so neighbouring
 * tiles agree on every tree that straddles their border.
 *
 * <p>These are not the trees the chunk generator places (that would mean running its features), but at the
 * distance the LOD starts, a tree is a few pixels of crown, and the count, size and colour are the biome's.
 */
final class Trees {
	private static final long SALT = 0x7EE5_0F_F0E57L;

	private Trees() {
	}

	/**
	 * How far the crown over ({@code x}, {@code z}) stands above the ground, or 0 where no crown is. The tree's
	 * own shade, a factor near 1, goes in {@code shade[0]}, and the height of the canopy's underside above the
	 * ground in {@code shade[1]}. {@code conifer} is the share of the trees that are conifers.
	 */
	static float crown(double x, double z, float canopy, float cover, float radius, float conifer, float[] shade) {
		if (canopy < 2 || cover <= 0.004F) {
			return 0;
		}
		float r = Math.max(1.5F, radius);
		int spacing = Math.max(3, Math.round(2 * r));
		double area = Math.PI * r * r;
		float p = (float) Math.min(1, cover * spacing * spacing / area);
		int gx = (int) Math.floor(x / spacing);
		int gz = (int) Math.floor(z / spacing);
		float best = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				long h = Colors.hash(SALT, gx + dx, gz + dz);
				if (Colors.unit(h, 0) >= p) {
					continue;
				}
				long h2 = Colors.hash(SALT + 1, gx + dx, gz + dz);
				double tx = (gx + dx + 0.5 + 0.8 * (Colors.unit(h, 24) - 0.5)) * spacing;
				double tz = (gz + dz + 0.5 + 0.8 * (Colors.unit(h2, 0) - 0.5)) * spacing;
				float tr = r * (0.8F + 0.4F * Colors.unit(h2, 24));
				float th = canopy * (0.75F + 0.5F * Colors.unit(h, 40));
				double ex = x - tx, ez = z - tz;
				double d2 = ex * ex + ez * ez;
				double reach = tr + 0.5;
				if (d2 > reach * reach) {
					continue;
				}
				// The crown's profile: conifers (spruce, pine) are cones, broadleaf trees (oak, birch) domes, both
				// stepping down toward the rim the way their leaf blocks do. A squat dome is boxy, like an oak's
				// leaf blocks; a tall, narrow one (aspen, poplar) rounds off like an egg instead of standing as a
				// pillar.
				float d = (float) Math.sqrt(d2 / (reach * reach));
				float cone = Colors.unit(Colors.hash(SALT + 2, gx + dx, gz + dz), 8) < conifer ? 1 : 0;
				float rim = 0.7F - 0.35F * Colors.smooth(1.8F, 3.2F, th / tr);
				float dome = rim + (1 - rim) * (float) Math.sqrt(Math.max(0, 1 - d * d));
				float spire = 1 - 0.72F * d;
				float height = th * (dome + (spire - dome) * cone);
				if (height > best) {
					best = height;
					shade[0] = 0.86F + 0.24F * Colors.unit(h2, 40);
					// how high the canopy's underside is: a little under half the tree is bare trunk
					shade[1] = th * (0.35F + 0.15F * cone);
				}
			}
		}
		return best;
	}
}
