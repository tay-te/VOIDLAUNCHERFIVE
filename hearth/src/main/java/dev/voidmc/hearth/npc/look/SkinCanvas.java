package dev.voidmc.hearth.npc.look;

/**
 * A 64×64 player-layout skin being painted, with the layout's boxes named so drawing code says "the front of the
 * body, row 9" instead of texture coordinates. Pixels are ARGB; 0 is transparent.
 *
 * <p>Box faces follow the cube UV layout vanilla uses for {@code texOffs(u, v)} and size {@code (w, h, d)}: top and
 * bottom in the first {@code d} rows, then right, front, left and back side by side. "Right" is the wearer's right,
 * so on the front face x = 0 is the wearer's right-hand edge. Going round a box ({@link #around}) runs right, front,
 * left, back and is continuous at every seam.
 */
final class SkinCanvas {
	static final int SIZE = 64;

	/** A box of the layout. */
	record Part(int u, int v, int w, int h, int d) {
		int around() {
			return 2 * (this.w + this.d);
		}
	}

	static final Part HEAD = new Part(0, 0, 8, 8, 8);
	static final Part HAT = new Part(32, 0, 8, 8, 8);
	static final Part BODY = new Part(16, 16, 8, 12, 4);
	static final Part JACKET = new Part(16, 32, 8, 12, 4);
	static final Part RIGHT_LEG = new Part(0, 16, 4, 12, 4);
	static final Part RIGHT_PANTS = new Part(0, 32, 4, 12, 4);
	static final Part LEFT_LEG = new Part(16, 48, 4, 12, 4);
	static final Part LEFT_PANTS = new Part(0, 48, 4, 12, 4);

	static Part rightArm(boolean slim) {
		return new Part(40, 16, slim ? 3 : 4, 12, 4);
	}

	static Part rightSleeve(boolean slim) {
		return new Part(40, 32, slim ? 3 : 4, 12, 4);
	}

	static Part leftArm(boolean slim) {
		return new Part(32, 48, slim ? 3 : 4, 12, 4);
	}

	static Part leftSleeve(boolean slim) {
		return new Part(48, 48, slim ? 3 : 4, 12, 4);
	}

	final int[] px = new int[SIZE * SIZE];

	void set(int x, int y, int argb) {
		if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
			this.px[y * SIZE + x] = argb;
		}
	}

	int get(int x, int y) {
		return x >= 0 && y >= 0 && x < SIZE && y < SIZE ? this.px[y * SIZE + x] : 0;
	}

	/** Texture x of the column {@code s} going round the box's sides. */
	static int aroundX(Part p, int s) {
		int n = p.around();
		s = Math.floorMod(s, n);
		return p.u + s;
	}

	/** Sets the pixel at column {@code s} round the box (0 = the right face's back edge), side row {@code y}. */
	void around(Part p, int s, int y, int argb) {
		if (y >= 0 && y < p.h) {
			set(aroundX(p, s), p.v + p.d + y, argb);
		}
	}

	int getAround(Part p, int s, int y) {
		return get(aroundX(p, s), p.v + p.d + y);
	}

	/** Front face: {@code x} from the wearer's right. */
	void front(Part p, int x, int y, int argb) {
		if (x >= 0 && x < p.w && y >= 0 && y < p.h) {
			set(p.u + p.d + x, p.v + p.d + y, argb);
		}
	}

	int getFront(Part p, int x, int y) {
		return get(p.u + p.d + x, p.v + p.d + y);
	}

	/** Back face: {@code x} from the wearer's left (as seen from behind, left to right). */
	void back(Part p, int x, int y, int argb) {
		if (x >= 0 && x < p.w && y >= 0 && y < p.h) {
			set(p.u + 2 * p.d + p.w + x, p.v + p.d + y, argb);
		}
	}

	/** Wearer's right face: {@code z} from the back to the front. */
	void right(Part p, int z, int y, int argb) {
		if (z >= 0 && z < p.d && y >= 0 && y < p.h) {
			set(p.u + z, p.v + p.d + y, argb);
		}
	}

	/** Wearer's left face: {@code z} from the front to the back. */
	void left(Part p, int z, int y, int argb) {
		if (z >= 0 && z < p.d && y >= 0 && y < p.h) {
			set(p.u + p.d + p.w + z, p.v + p.d + y, argb);
		}
	}

	/** Top face: {@code x} as the front, {@code z} from the back (0) to the front (d - 1). */
	void top(Part p, int x, int z, int argb) {
		if (x >= 0 && x < p.w && z >= 0 && z < p.d) {
			set(p.u + p.d + x, p.v + z, argb);
		}
	}

	void bottom(Part p, int x, int z, int argb) {
		if (x >= 0 && x < p.w && z >= 0 && z < p.d) {
			set(p.u + p.d + p.w + x, p.v + z, argb);
		}
	}

	/** Paints every face of a box with {@code colour(face x, face y, which face)}. */
	void fill(Part p, Shader shader) {
		for (int s = 0; s < p.around(); s++) {
			for (int y = 0; y < p.h; y++) {
				around(p, s, y, shader.colour(s, y, Face.SIDE));
			}
		}
		for (int x = 0; x < p.w; x++) {
			for (int z = 0; z < p.d; z++) {
				top(p, x, z, shader.colour(x, z, Face.TOP));
				bottom(p, x, z, shader.colour(x, z, Face.BOTTOM));
			}
		}
	}

	/** Which side of the box a column round it lies on. */
	static Face sideOf(Part p, int s) {
		s = Math.floorMod(s, p.around());
		if (s < p.d) {
			return Face.RIGHT;
		}
		if (s < p.d + p.w) {
			return Face.FRONT;
		}
		if (s < 2 * p.d + p.w) {
			return Face.LEFT;
		}
		return Face.BACK;
	}

	/** The column round the box of front-face x. */
	static int frontS(Part p, int x) {
		return p.d + x;
	}

	/** The column round the box of back-face x (from the wearer's left). */
	static int backS(Part p, int x) {
		return 2 * p.d + p.w + x;
	}

	enum Face {
		SIDE, TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK
	}

	@FunctionalInterface
	interface Shader {
		int colour(int a, int b, Face face);
	}
}
