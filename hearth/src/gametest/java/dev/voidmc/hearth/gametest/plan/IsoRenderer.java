package dev.voidmc.hearth.gametest.plan;

import dev.voidmc.hearth.api.TerrainProbe;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The isometric overview: the scene's land as column prisms and the settlement's blocks as cubes, drawn back to front
 * (painter's order on the column grid, bottom to top within a column), lit from the viewer's left. The land is cut away
 * at the scene's edge like a diorama so slopes, terraces and cliffs read.
 */
public final class IsoRenderer {
	private final Scene scene;
	private final double a;
	private final double hs;
	private final int base;
	private final int ox;
	private final int oy;
	private final int[] px = new int[4];
	private final int[] py = new int[4];

	public IsoRenderer(Scene scene, int maxWidth) {
		this.scene = scene;
		this.a = Math.max(1.0, Math.min(4.0, maxWidth / (double) (scene.w + scene.d)));
		this.hs = this.a * 1.15;
		int lo = Integer.MAX_VALUE;
		for (int g : scene.ground) {
			lo = Math.min(lo, g);
		}
		this.base = lo - 6;
		this.ox = (int) Math.round(scene.d * this.a) + 20;
		this.oy = 60;
	}

	public BufferedImage render(String title) {
		Scene s = this.scene;
		int top = this.base;
		for (int i = 0; i < s.ground.length; i++) {
			top = Math.max(top, s.ground[i]);
			LongArrayList b = s.blocks[i];
			if (b != null) {
				for (int k = 0; k < b.size(); k++) {
					top = Math.max(top, (int) (b.getLong(k) >>> 24) - 1024 + 1);
				}
			}
		}
		int width = (int) Math.round((s.w + s.d) * this.a) + 40;
		int height = (int) Math.round((s.w + s.d) * this.a / 2 + (top - this.base) * this.hs) + this.oy + 40;
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0xE9E4D8));
		g.fillRect(0, 0, width, height);
		int lift = (int) Math.round((top - this.base) * this.hs);
		for (int sum = 0; sum <= s.w + s.d - 2; sum++) {
			for (int x = Math.max(0, sum - s.d + 1); x <= Math.min(s.w - 1, sum); x++) {
				int z = sum - x;
				column(g, x, z, lift);
			}
		}
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.setColor(new Color(0x2A2018));
		g.drawString(title, 14, 24);
		g.dispose();
		return img;
	}

	private void column(Graphics2D g, int x, int z, int lift) {
		Scene s = this.scene;
		int i = z * s.w + x;
		int ground = s.level[i] != Integer.MIN_VALUE ? s.level[i] : s.ground[i];
		int water = s.water[i];
		boolean wet = water != TerrainProbe.NO_WATER && water > ground && s.level[i] == Integer.MIN_VALUE;
		int topColor = s.surface[i] != 0 ? s.surface[i] : wet ? 0x7A6A50
			: s.underground ? Palette.cave(ground, s.caveFloor) : Palette.land(ground, s.sea, s.temperature[i]);
		double shade = 1.0;
		if (s.surface[i] == 0 && !wet) {
			int gx = heightAt(x + 1, z) - heightAt(x - 1, z);
			int gz = heightAt(x, z + 1) - heightAt(x, z - 1);
			shade = Math.max(0.7, Math.min(1.15, 1.0 - (gx + gz) * 0.06 + 0.04));
		}
		int right = x + 1 < s.w ? heightAt(x + 1, z) : this.base;
		int left = z + 1 < s.d ? heightAt(x, z + 1) : this.base;
		int sideColor = ground - Math.min(right, left) > 3 ? 0x8C8478 : 0x8A6E4E;
		if (s.surface[i] != 0 && s.level[i] != Integer.MIN_VALUE && s.level[i] > s.ground[i]) {
			sideColor = 0x9A9284;
		}
		// the land
		if (ground > right) {
			face(g, x, z, right, ground, 'r', Palette.shade(sideColor, 0.8), lift);
		}
		if (ground > left) {
			face(g, x, z, left, ground, 'l', Palette.shade(sideColor, 0.62), lift);
		}
		face(g, x, z, ground, ground, 't', Palette.shade(topColor, shade), lift);
		if (wet) {
			int depth = water - ground;
			int wc = Palette.lerp(Palette.WATER_SHALLOW, Palette.WATER_DEEP, Math.min(1, depth / 8.0));
			if (x + 1 >= s.w) {
				face(g, x, z, ground, water, 'r', Palette.shade(wc, 0.8), lift);
			}
			if (z + 1 >= s.d) {
				face(g, x, z, ground, water, 'l', Palette.shade(wc, 0.65), lift);
			}
			face(g, x, z, water, water, 't', wc, lift);
		}
		LongArrayList b = s.blocks[i];
		if (b == null) {
			return;
		}
		long[] sorted = b.toLongArray();
		java.util.Arrays.sort(sorted);
		for (long v : sorted) {
			int y = (int) (v >>> 24) - 1024;
			int rgb = (int) (v & 0xFFFFFF);
			face(g, x, z, y, y + 1, 'r', Palette.shade(rgb, 0.8), lift);
			face(g, x, z, y, y + 1, 'l', Palette.shade(rgb, 0.64), lift);
			face(g, x, z, y + 1, y + 1, 't', rgb, lift);
		}
	}

	private int heightAt(int x, int z) {
		Scene s = this.scene;
		x = Math.max(0, Math.min(s.w - 1, x));
		z = Math.max(0, Math.min(s.d - 1, z));
		int i = z * s.w + x;
		return s.level[i] != Integer.MIN_VALUE ? s.level[i] : s.ground[i];
	}

	/** A face of the column at (x, z): its top at height y1 ('t'), or its +x ('r') or +z ('l') side from y0 to y1. */
	private void face(Graphics2D g, int x, int z, int y0, int y1, char kind, int rgb, int lift) {
		switch (kind) {
			case 't' -> {
				corner(0, x, y1, z, lift);
				corner(1, x + 1, y1, z, lift);
				corner(2, x + 1, y1, z + 1, lift);
				corner(3, x, y1, z + 1, lift);
			}
			case 'r' -> {
				corner(0, x + 1, y1, z, lift);
				corner(1, x + 1, y1, z + 1, lift);
				corner(2, x + 1, y0, z + 1, lift);
				corner(3, x + 1, y0, z, lift);
			}
			default -> {
				corner(0, x, y1, z + 1, lift);
				corner(1, x + 1, y1, z + 1, lift);
				corner(2, x + 1, y0, z + 1, lift);
				corner(3, x, y0, z + 1, lift);
			}
		}
		g.setColor(new Color(rgb));
		g.fillPolygon(this.px, this.py, 4);
	}

	private void corner(int k, int x, int y, int z, int lift) {
		this.px[k] = (int) Math.round((x - z) * this.a) + this.ox;
		this.py[k] = (int) Math.round((x + z) * this.a * 0.5 - (y - this.base) * this.hs) + this.oy + lift;
	}
}
