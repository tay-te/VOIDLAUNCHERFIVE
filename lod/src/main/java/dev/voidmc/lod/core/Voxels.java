package dev.voidmc.lod.core;

import java.util.Arrays;

/**
 * Blocks a {@link Decorator} placed, one entry each: where, what kind (leaves, a log), the colour of its top and its
 * sides as the chunks draw them, and its materials for the block textures. A log whose rings face along x or z says
 * so ({@link #axis}). Reused build after build.
 */
public final class Voxels {
	public static final byte LEAVES = 1;
	public static final byte LOG = 2;

	public int size;
	public int[] x = new int[256];
	public int[] y = new int[256];
	public int[] z = new int[256];
	public byte[] kind = new byte[256];
	/** 0: the rings face up and down; 1: along x; 2: along z. */
	public byte[] axis = new byte[256];
	public int[] topColor = new int[256];
	public int[] sideColor = new int[256];
	public int[] topMaterial = new int[256];
	public int[] sideMaterial = new int[256];
	/** The season code of a leaf block ({@code LodApi.Seasons}: class, variant), 0 for none; set as the tile is built. */
	public byte[] season = new byte[256];

	public void clear() {
		this.size = 0;
	}

	public void add(int x, int y, int z, byte kind, byte axis, int topColor, int sideColor, int topMaterial, int sideMaterial) {
		if (this.size == this.x.length) {
			int n = this.size * 2;
			this.x = Arrays.copyOf(this.x, n);
			this.y = Arrays.copyOf(this.y, n);
			this.z = Arrays.copyOf(this.z, n);
			this.kind = Arrays.copyOf(this.kind, n);
			this.axis = Arrays.copyOf(this.axis, n);
			this.topColor = Arrays.copyOf(this.topColor, n);
			this.sideColor = Arrays.copyOf(this.sideColor, n);
			this.topMaterial = Arrays.copyOf(this.topMaterial, n);
			this.sideMaterial = Arrays.copyOf(this.sideMaterial, n);
			this.season = Arrays.copyOf(this.season, n);
		}
		int i = this.size++;
		this.x[i] = x;
		this.y[i] = y;
		this.z[i] = z;
		this.kind[i] = kind;
		this.axis[i] = axis;
		this.topColor[i] = topColor;
		this.sideColor[i] = sideColor;
		this.season[i] = 0;
		this.topMaterial[i] = topMaterial;
		this.sideMaterial[i] = sideMaterial;
	}
}
