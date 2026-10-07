package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.gametest.render.ModelLibrary.Quad;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A small software renderer for block worlds: real block models and textures from the client jar, an orthographic camera
 * at any yaw and pitch (isometric from a corner, or straight down for plans), a z-buffer with alpha-tested cutouts and
 * blended translucency, Minecraft-like face shading and smooth ambient occlusion, and supersampling for clean edges.
 */
public final class IsoRenderer {
	/** Camera: yaw 45 looks from the south-east corner, 225 from the north-west; pitch 30 is the 2:1 game-art angle. */
	public record View(double yaw, double pitch) {
		public static final View SOUTH_EAST = new View(45, 30);
		public static final View NORTH_WEST = new View(225, 30);
		public static final View SOUTH_WEST = new View(-45, 30);
		public static final View NORTH_EAST = new View(135, 30);
		public static final View TOP = new View(0, 90);
	}

	private static final float[] AO = {0.50f, 0.68f, 0.84f, 1.0f};

	private final ModelLibrary models;

	public IsoRenderer(ModelLibrary models) {
		this.models = models;
	}

	private static final class Tri {
		final float[] x = new float[3];
		final float[] y = new float[3];
		final float[] z = new float[3];
		final float[] u = new float[3];
		final float[] v = new float[3];
		final float[] b = new float[3];
		/** Night light colour per vertex (r, g, b), or null for daylight. */
		float[] lr;
		float[] lg;
		float[] lb;
		Assets.Texture tex;
		int tint;
		float depth;
	}

	/**
	 * Renders a scene.
	 *
	 * @param scale      pixels per block edge in the output
	 * @param supersample render this many times larger and average down
	 * @param background ARGB background (alpha 0 for transparent)
	 */
	public BufferedImage render(Voxels world, View view, int scale, int supersample, int background) {
		return render(world, view, scale, supersample, background, null);
	}

	/**
	 * Renders a scene by night: each vertex takes the colour of the block and sky light round it (smoothly averaged as
	 * the game does), lanterns and candles glow, unlit corners go black. {@code light} is computed on the whole scene
	 * (so a cutaway shows the light the full building gives).
	 */
	public BufferedImage render(Voxels world, View view, int scale, int supersample, int background, @org.jspecify.annotations.Nullable NightLight light) {
		double yaw = Math.toRadians(view.yaw);
		double pitch = Math.toRadians(view.pitch);
		double cy = Math.cos(yaw);
		double sy = Math.sin(yaw);
		double cp = Math.cos(pitch);
		double sp = Math.sin(pitch);
		double cx0 = (world.minX() + world.maxX() + 1) / 2.0;
		double cy0 = (world.minY() + world.maxY() + 1) / 2.0;
		double cz0 = (world.minZ() + world.maxZ() + 1) / 2.0;
		// a camera-left vector in world space for side shading
		List<Tri> opaque = new ArrayList<>();
		List<Tri> translucent = new ArrayList<>();
		float minSx = Float.MAX_VALUE;
		float maxSx = -Float.MAX_VALUE;
		float minSy = Float.MAX_VALUE;
		float maxSy = -Float.MAX_VALUE;
		float[] sx = new float[4];
		float[] syy = new float[4];
		float[] sz = new float[4];
		float[] br = new float[4];
		for (Long2ObjectMap.Entry<BlockState> e : world.all().long2ObjectEntrySet()) {
			long k = e.getLongKey();
			int bx = Voxels.kx(k);
			int by = Voxels.ky(k);
			int bz = Voxels.kz(k);
			BlockState state = e.getValue();
			List<Quad> quads = quadsFor(world, state, bx, by, bz);
			for (Quad q : quads) {
				if (q.cull() != null) {
					Direction d = q.cull();
					BlockState n = world.get(bx + d.getStepX(), by + d.getStepY(), bz + d.getStepZ());
					if (n != null && (n.isSolidRender() || state.skipRendering(n, d))) {
						continue;
					}
				}
				// normal in camera space
				double nx1 = q.nx() * cy - q.nz() * sy;
				double nz1 = q.nx() * sy + q.nz() * cy;
				double nToward = nz1 * cp + q.ny() * sp;
				if (nToward <= 1e-4) {
					continue;
				}
				float shade = 1f;
				if (q.shade()) {
					double h = Math.sqrt(q.nx() * q.nx() + q.nz() * q.nz());
					double side = h > 1e-6 ? 0.71 - 0.12 * (nx1 / h) : 0.71;
					shade = (float) (q.ny() >= 0 ? side + (1.0 - side) * q.ny() : side + (0.5 - side) * -q.ny());
				}
				float[] ao = q.aligned() ? ambient(world, q, bx, by, bz) : null;
				float[] lc = light == null ? null : nightLight(light, q, bx, by, bz, state);
				for (int i = 0; i < 4; i++) {
					double wx = bx + q.pos()[i * 3] - cx0;
					double wy = by + q.pos()[i * 3 + 1] - cy0;
					double wz = bz + q.pos()[i * 3 + 2] - cz0;
					double x1 = wx * cy - wz * sy;
					double z1 = wx * sy + wz * cy;
					double up = wy * cp - z1 * sp;
					double toward = z1 * cp + wy * sp;
					sx[i] = (float) x1;
					syy[i] = (float) -up;
					sz[i] = (float) -toward;
					br[i] = shade * (ao == null ? 1f : ao[i]);
					minSx = Math.min(minSx, sx[i]);
					maxSx = Math.max(maxSx, sx[i]);
					minSy = Math.min(minSy, syy[i]);
					maxSy = Math.max(maxSy, syy[i]);
				}
				for (int t = 0; t < 2; t++) {
					int[] idx = t == 0 ? new int[]{0, 1, 2} : new int[]{0, 2, 3};
					Tri tri = new Tri();
					float dsum = 0;
					for (int j = 0; j < 3; j++) {
						int i = idx[j];
						tri.x[j] = sx[i];
						tri.y[j] = syy[i];
						tri.z[j] = sz[i];
						tri.u[j] = q.uv()[i * 2];
						tri.v[j] = q.uv()[i * 2 + 1];
						tri.b[j] = br[i];
						dsum += sz[i];
					}
					if (lc != null) {
						tri.lr = new float[3];
						tri.lg = new float[3];
						tri.lb = new float[3];
						for (int j = 0; j < 3; j++) {
							tri.lr[j] = lc[idx[j] * 3];
							tri.lg[j] = lc[idx[j] * 3 + 1];
							tri.lb[j] = lc[idx[j] * 3 + 2];
						}
					}
					tri.tex = q.tex();
					tri.tint = q.tint();
					tri.depth = dsum / 3;
					(q.translucent() ? translucent : opaque).add(tri);
				}
			}
		}
		if (opaque.isEmpty() && translucent.isEmpty()) {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			return img;
		}
		int ss = Math.max(1, supersample);
		double px = scale * ss;
		int pad = 2 * ss;
		int w = (int) Math.ceil((maxSx - minSx) * px) + 2 * pad;
		int h = (int) Math.ceil((maxSy - minSy) * px) + 2 * pad;
		int[] color = new int[w * h];
		float[] zbuf = new float[w * h];
		java.util.Arrays.fill(zbuf, Float.MAX_VALUE);
		float ox = (float) (-minSx * px + pad);
		float oy = (float) (-minSy * px + pad);
		for (Tri t : opaque) {
			raster(t, color, zbuf, w, h, (float) px, ox, oy, false);
		}
		translucent.sort((a, b) -> Float.compare(b.depth, a.depth));
		for (Tri t : translucent) {
			raster(t, color, zbuf, w, h, (float) px, ox, oy, true);
		}
		BufferedImage big = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		big.setRGB(0, 0, w, h, color, 0, w);
		BufferedImage out = ss == 1 ? big : downsample(big, ss);
		if ((background >>> 24) != 0) {
			BufferedImage bg = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = bg.createGraphics();
			g.setColor(new java.awt.Color(background, true));
			g.fillRect(0, 0, bg.getWidth(), bg.getHeight());
			g.drawImage(out, 0, 0, null);
			g.dispose();
			return bg;
		}
		return out;
	}

	private List<Quad> quadsFor(Voxels world, BlockState state, int x, int y, int z) {
		if (state.getBlock() instanceof LiquidBlock) {
			BlockState above = world.get(x, y + 1, z);
			if (above != null && above.getFluidState().getType() == state.getFluidState().getType()) {
				Assets.Texture t = this.models.quads(state).get(0).tex();
				return this.models.box(0, 0, 0, 1, 1, 1, t, t, ModelLibrary.tint("water", state), true);
			}
		}
		return this.models.quads(state);
	}

	private static boolean occludes(Voxels w, int x, int y, int z) {
		BlockState s = w.get(x, y, z);
		return s != null && (s.isSolidRender() || s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	}

	/**
	 * Night colour per vertex (12 floats: r, g, b for each of the 4 vertices): the light of the cell the face looks into
	 * averaged with the three round each corner that are not opaque, as smooth lighting does; glowing blocks glow.
	 */
	private static float[] nightLight(NightLight light, Quad q, int bx, int by, int bz, BlockState state) {
		float[] out = new float[12];
		if (state.getLightEmission() >= 10 || NightLight.marker(state)) {
			java.util.Arrays.fill(out, 1.05f);
			return out;
		}
		int dx = Math.round(q.nx());
		int dy = Math.round(q.ny());
		int dz = Math.round(q.nz());
		boolean onFace = q.aligned() && (dx != 0 || dy != 0 || dz != 0);
		if (onFace && light.opaque(bx + dx, by + dy, bz + dz)) {
			// a face a cutaway opened (the top of a wall cut at head height, solid above in the whole building): it takes
			// the light of the room beside it, not the dark inside the wall
			float bl = 0;
			float sk = 0;
			for (int ox = -1; ox <= 1; ox++) {
				for (int oy = -1; oy <= 1; oy++) {
					for (int oz = -1; oz <= 1; oz++) {
						int cx = bx + dx + ox;
						int cy = by + dy + oy;
						int cz = bz + dz + oz;
						if (!light.opaque(cx, cy, cz)) {
							bl = Math.max(bl, light.block(cx, cy, cz) - 1);
							sk = Math.max(sk, light.sky(cx, cy, cz) - 1);
						}
					}
				}
			}
			float[] col = NightLight.colour(Math.max(0, bl), Math.max(0, sk));
			for (int i = 0; i < 4; i++) {
				System.arraycopy(col, 0, out, i * 3, 3);
			}
			return out;
		}
		for (int i = 0; i < 4; i++) {
			float bl;
			float sk;
			if (!onFace) {
				int sx = bx;
				int sy = by;
				int sz = bz;
				if (light.opaque(bx, by, bz)) {
					sx += dx;
					sy += dy;
					sz += dz;
				}
				bl = light.block(sx, sy, sz);
				sk = light.sky(sx, sy, sz);
			} else {
				float px = q.pos()[i * 3] - 0.5f;
				float py = q.pos()[i * 3 + 1] - 0.5f;
				float pz = q.pos()[i * 3 + 2] - 0.5f;
				int tx = dx != 0 ? 0 : (px > 0 ? 1 : -1);
				int ty = dy != 0 ? 0 : (py > 0 ? 1 : -1);
				int tz = dz != 0 ? 0 : (pz > 0 ? 1 : -1);
				int fx = bx + dx;
				int fy = by + dy;
				int fz = bz + dz;
				int[][] cells;
				if (dx != 0) {
					cells = new int[][]{{fx, fy, fz}, {fx, fy + ty, fz}, {fx, fy, fz + tz}, {fx, fy + ty, fz + tz}};
				} else if (dy != 0) {
					cells = new int[][]{{fx, fy, fz}, {fx + tx, fy, fz}, {fx, fy, fz + tz}, {fx + tx, fy, fz + tz}};
				} else {
					cells = new int[][]{{fx, fy, fz}, {fx + tx, fy, fz}, {fx, fy + ty, fz}, {fx + tx, fy + ty, fz}};
				}
				float sb = 0;
				float ss = 0;
				int n = 0;
				for (int k = 0; k < 4; k++) {
					int[] c = cells[k];
					if (k > 0 && light.opaque(c[0], c[1], c[2])) {
						continue;
					}
					sb += light.block(c[0], c[1], c[2]);
					ss += light.sky(c[0], c[1], c[2]);
					n++;
				}
				bl = sb / n;
				sk = ss / n;
			}
			float[] col = NightLight.colour(bl, sk);
			out[i * 3] = col[0];
			out[i * 3 + 1] = col[1];
			out[i * 3 + 2] = col[2];
		}
		return out;
	}

	/** Per-vertex ambient occlusion for a face on a block boundary, as the game's smooth lighting does it. */
	private static float[] ambient(Voxels w, Quad q, int bx, int by, int bz) {
		int dx = Math.round(q.nx());
		int dy = Math.round(q.ny());
		int dz = Math.round(q.nz());
		float[] out = new float[4];
		for (int i = 0; i < 4; i++) {
			float px = q.pos()[i * 3] - 0.5f;
			float py = q.pos()[i * 3 + 1] - 0.5f;
			float pz = q.pos()[i * 3 + 2] - 0.5f;
			int tx = dx != 0 ? 0 : (px > 0 ? 1 : -1);
			int ty = dy != 0 ? 0 : (py > 0 ? 1 : -1);
			int tz = dz != 0 ? 0 : (pz > 0 ? 1 : -1);
			int fx = bx + dx;
			int fy = by + dy;
			int fz = bz + dz;
			boolean s1;
			boolean s2;
			if (dx != 0) {
				s1 = occludes(w, fx, fy + ty, fz);
				s2 = occludes(w, fx, fy, fz + tz);
			} else if (dy != 0) {
				s1 = occludes(w, fx + tx, fy, fz);
				s2 = occludes(w, fx, fy, fz + tz);
			} else {
				s1 = occludes(w, fx + tx, fy, fz);
				s2 = occludes(w, fx, fy + ty, fz);
			}
			boolean c = occludes(w, fx + tx, fy + ty, fz + tz);
			int level = s1 && s2 ? 0 : 3 - ((s1 ? 1 : 0) + (s2 ? 1 : 0) + (c ? 1 : 0));
			out[i] = AO[level];
		}
		return out;
	}

	private static void raster(Tri t, int[] color, float[] zbuf, int w, int h, float px, float ox, float oy, boolean blend) {
		float x0 = t.x[0] * px + ox;
		float y0 = t.y[0] * px + oy;
		float x1 = t.x[1] * px + ox;
		float y1 = t.y[1] * px + oy;
		float x2 = t.x[2] * px + ox;
		float y2 = t.y[2] * px + oy;
		float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
		if (Math.abs(area) < 1e-6f) {
			return;
		}
		int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
		int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
		int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
		int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
		float inv = 1f / area;
		int tr = t.tint < 0 ? 255 : (t.tint >> 16) & 0xFF;
		int tg = t.tint < 0 ? 255 : (t.tint >> 8) & 0xFF;
		int tb = t.tint < 0 ? 255 : t.tint & 0xFF;
		for (int py = minY; py <= maxY; py++) {
			float cyp = py + 0.5f;
			for (int pxx = minX; pxx <= maxX; pxx++) {
				float cxp = pxx + 0.5f;
				float w0 = ((x1 - cxp) * (y2 - cyp) - (x2 - cxp) * (y1 - cyp)) * inv;
				float w1 = ((x2 - cxp) * (y0 - cyp) - (x0 - cxp) * (y2 - cyp)) * inv;
				float w2 = 1 - w0 - w1;
				if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) {
					continue;
				}
				float z = w0 * t.z[0] + w1 * t.z[1] + w2 * t.z[2];
				int i = py * w + pxx;
				if (z > zbuf[i] + 1e-4f) {
					continue;
				}
				float u = w0 * t.u[0] + w1 * t.u[1] + w2 * t.u[2];
				float v = w0 * t.v[0] + w1 * t.v[1] + w2 * t.v[2];
				int c = t.tex.sample(u, v);
				int a = c >>> 24;
				if (a < (blend ? 8 : 128)) {
					continue;
				}
				float b = w0 * t.b[0] + w1 * t.b[1] + w2 * t.b[2];
				float mr = b;
				float mg = b;
				float mb = b;
				if (t.lr != null) {
					mr *= w0 * t.lr[0] + w1 * t.lr[1] + w2 * t.lr[2];
					mg *= w0 * t.lg[0] + w1 * t.lg[1] + w2 * t.lg[2];
					mb *= w0 * t.lb[0] + w1 * t.lb[1] + w2 * t.lb[2];
				}
				int r = Math.min(255, (int) (((c >> 16) & 0xFF) * tr / 255f * mr));
				int g = Math.min(255, (int) (((c >> 8) & 0xFF) * tg / 255f * mg));
				int bl = Math.min(255, (int) ((c & 0xFF) * tb / 255f * mb));
				if (blend) {
					float al = Math.max(a / 255f, 0.55f);
					int d = color[i];
					int da = d >>> 24;
					int dr = (d >> 16) & 0xFF;
					int dg = (d >> 8) & 0xFF;
					int db = d & 0xFF;
					r = (int) (r * al + dr * (1 - al));
					g = (int) (g * al + dg * (1 - al));
					bl = (int) (bl * al + db * (1 - al));
					int na = Math.max(da, (int) (al * 255));
					color[i] = na << 24 | r << 16 | g << 8 | bl;
				} else {
					color[i] = 0xFF000000 | r << 16 | g << 8 | bl;
					zbuf[i] = z;
				}
			}
		}
	}

	private static BufferedImage downsample(BufferedImage big, int ss) {
		int w = big.getWidth() / ss;
		int h = big.getHeight() / ss;
		int[] src = big.getRGB(0, 0, big.getWidth(), big.getHeight(), null, 0, big.getWidth());
		int[] dst = new int[w * h];
		int bw = big.getWidth();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = 0;
				int r = 0;
				int g = 0;
				int b = 0;
				for (int j = 0; j < ss; j++) {
					for (int i = 0; i < ss; i++) {
						int c = src[(y * ss + j) * bw + x * ss + i];
						int ca = c >>> 24;
						a += ca;
						r += ((c >> 16) & 0xFF) * ca;
						g += ((c >> 8) & 0xFF) * ca;
						b += (c & 0xFF) * ca;
					}
				}
				int n = ss * ss;
				if (a == 0) {
					dst[y * w + x] = 0;
				} else {
					dst[y * w + x] = (a / n) << 24 | (r / a) << 16 | (g / a) << 8 | (b / a);
				}
			}
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0, 0, w, h, dst, 0, w);
		return out;
	}
}
