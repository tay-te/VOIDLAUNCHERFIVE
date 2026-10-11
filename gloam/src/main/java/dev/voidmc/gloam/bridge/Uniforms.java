package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.bridge.Contract.Slot;
import org.joml.Vector2i;
import org.joml.Vector4f;

/**
 * The {@code void_*} uniforms' values, worked out once a client tick ({@link Bridge}) and read by Iris every frame on
 * the render thread (the same thread), each a plain field read. Vectors are new objects whenever their value changes,
 * because Iris keeps the object it was handed to compare the next one with.
 *
 * <p>Iris's side is in {@link IrisSide}, a class of its own, so nothing of Iris is resolved unless it is installed.
 */
public final class Uniforms {
	/** Whether the uniforms go to the pack at all: {@code -Dgloam.bridge.uniforms=false} turns the injection off. */
	public static final boolean ENABLED = !"false".equals(System.getProperty("gloam.bridge.uniforms"));

	static final float[] FLOATS = new float[Slot.values().length * 4];
	static final int[] INTS = new int[Slot.values().length * 3];
	private static final Vector4f[] VEC4 = new Vector4f[Slot.values().length];
	private static final Vector2i[] IVEC2 = new Vector2i[Slot.values().length];
	/** The slots Gloam fills in this version: the uniforms it declares to Iris and the header's live mask. */
	static final int LIVE = bits(Slot.BRIDGE, Slot.VERSION, Slot.SEED, Slot.YEAR, Slot.WARMTH, Slot.SEASON, Slot.WIND, Slot.FRONT,
		Slot.CLOUD_COVER, Slot.RAIN, Slot.SHOWERS, Slot.STORM, Slot.MIST, Slot.MIST_TOP, Slot.MIST_TIME, Slot.WETNESS, Slot.LIGHT,
		Slot.NEAR_CENTRE);
	/** Whether Iris asked for the uniforms (the injection applied), and how many times. */
	static volatile int injected;

	static {
		for (int i = 0; i < VEC4.length; i++) {
			VEC4[i] = new Vector4f();
			IVEC2[i] = new Vector2i();
		}
		INTS[Slot.BRIDGE.ordinal() * 3] = 1;
		INTS[Slot.VERSION.ordinal() * 3] = Contract.VERSION;
	}

	private Uniforms() {
	}

	private static int bits(Slot... slots) {
		int m = 0;
		for (Slot s : slots) {
			m |= s.bit();
		}
		return m;
	}

	static void set(Slot slot, float v) {
		FLOATS[slot.ordinal() * 4] = v;
	}

	static void set(Slot slot, float x, float y, float z, float w) {
		int o = slot.ordinal() * 4;
		FLOATS[o] = x;
		FLOATS[o + 1] = y;
		FLOATS[o + 2] = z;
		FLOATS[o + 3] = w;
		Vector4f v = VEC4[slot.ordinal()];
		if (v.x != x || v.y != y || v.z != z || v.w != w) {
			VEC4[slot.ordinal()] = new Vector4f(x, y, z, w);
		}
	}

	static void set(Slot slot, int v) {
		INTS[slot.ordinal() * 3] = v;
	}

	static void set(Slot slot, int x, int y) {
		int o = slot.ordinal() * 3;
		INTS[o] = x;
		INTS[o + 1] = y;
		Vector2i v = IVEC2[slot.ordinal()];
		if (v.x != x || v.y != y) {
			IVEC2[slot.ordinal()] = new Vector2i(x, y);
		}
	}

	/** Writes every live slot's mirror into the texture's header rows. */
	static void mirror(byte[] bytes) {
		float[] f = new float[4];
		int[] n = new int[3];
		for (Slot slot : Slot.values()) {
			if ((LIVE & slot.bit()) == 0) {
				continue;
			}
			System.arraycopy(FLOATS, slot.ordinal() * 4, f, 0, 4);
			System.arraycopy(INTS, slot.ordinal() * 3, n, 0, 3);
			Contract.mirror(bytes, slot, f, n);
		}
	}

	/**
	 * Called from the Iris mixin ({@code CommonUniformsMixin}) with the uniform holder Iris builds a pack's custom
	 * uniforms from, once per pipeline. {@code holder} is Iris's {@code UniformHolder}.
	 */
	public static void addTo(Object holder) {
		if (!ENABLED) {
			return;
		}
		try {
			IrisSide.add(holder);
			if (injected++ == 0) {
				Gloam.LOGGER.info("VOID Gloam: the void_* uniforms are in the shader pack's pipeline");
			}
		} catch (LinkageError | RuntimeException e) {
			Gloam.LOGGER.warn("VOID Gloam: could not add the void_* uniforms; the pack reads the data texture's header instead", e);
		}
	}

	/** Iris's side, in a class of its own, so the JVM only resolves it once Iris is known to be there. */
	private static final class IrisSide {
		static void add(Object h) {
			net.irisshaders.iris.gl.uniform.UniformHolder u = (net.irisshaders.iris.gl.uniform.UniformHolder) h;
			net.irisshaders.iris.gl.uniform.UniformUpdateFrequency once = net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.ONCE;
			net.irisshaders.iris.gl.uniform.UniformUpdateFrequency frame = net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
			u.uniform1i(once, Slot.BRIDGE.glsl, () -> 1);
			u.uniform1i(once, Slot.VERSION.glsl, () -> Contract.VERSION);
			for (Slot slot : Slot.values()) {
				if (slot == Slot.BRIDGE || slot == Slot.VERSION || (LIVE & slot.bit()) == 0) {
					continue;
				}
				int i = slot.ordinal();
				switch (slot.type) {
					case INT -> u.uniform1i(frame, slot.glsl, () -> INTS[i * 3]);
					case FLOAT -> u.uniform1f(frame, slot.glsl, (net.irisshaders.iris.gl.uniform.FloatSupplier) () -> FLOATS[i * 4]);
					case VEC4 -> u.uniform4f(frame, slot.glsl, () -> VEC4[i]);
					case IVEC2 -> u.uniform2i(frame, slot.glsl, () -> IVEC2[i]);
					case IVEC3 -> {
						// none live in this version
					}
				}
			}
		}
	}
}
