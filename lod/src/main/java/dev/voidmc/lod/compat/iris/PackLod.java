package dev.voidmc.lod.compat.iris;

import dev.voidmc.lod.VoidLod;
import dev.voidmc.lod.render.PackFrame;
import java.io.IOException;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;

/**
 * What Iris and the shader pack are told about the LOD, standing in for Distant Horizons: that it renders, how far it
 * reaches, the perspective its depth is drawn in. Only loaded with Iris installed ({@code compat.Shaders} guards it).
 */
public final class PackLod {
	/**
	 * The vertical offset in the Distant Horizons layout: its positions are unsigned, so a vertex carries its height plus
	 * this, and the tile's offset takes it back off.
	 */
	public static final int Y_OFFSET = 4096;
	/** The LOD's near plane in its own perspective: the chunks cover everything nearer, so it only spends depth precision. */
	private static final float NEAR = 16;

	/** What {@link #wanted} said when Iris last built a pipeline: the pack was compiled with or without the LOD by it. */
	private static volatile boolean builtWith;

	private PackLod() {
	}

	/**
	 * Whether the LOD renders: on for the session, in a level with a sky. Iris asks this as "does Distant Horizons render"
	 * when it compiles a pack, to define DISTANT_HORIZONS, and it builds the overworld's pipeline before any world is
	 * loaded, so no level counts as one with a sky.
	 */
	public static boolean wanted() {
		VoidLod lod = VoidLod.get();
		Minecraft mc = Minecraft.getInstance();
		return lod != null && lod.isEnabled() && (mc.level == null || !mc.level.dimensionType().hasCeiling());
	}

	/** Iris is building a pipeline: whatever {@link #wanted} says now is what its pack gets compiled with. */
	public static boolean building() {
		builtWith = wanted();
		return builtWith;
	}

	/**
	 * Iris's per-frame check. When the answer is no longer what the pack was compiled with (F8, a level without a sky),
	 * it is compiled again, with or without DISTANT_HORIZONS, as Iris does when Distant Horizons is switched on or off.
	 */
	public static boolean checkFrame() {
		boolean wanted = wanted();
		if (wanted != builtWith && Iris.isPackInUseQuick()) {
			builtWith = wanted;
			try {
				Iris.reload();
			} catch (IOException e) {
				LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: could not reload the shader pack", e);
			}
		}
		return wanted;
	}

	/** How far the LOD reaches, in blocks: the pack's dhRenderDistance, where its border fog closes in. */
	public static int renderDistance() {
		VoidLod lod = VoidLod.get();
		return lod == null ? 0 : (int) lod.radius;
	}

	public static float nearPlane() {
		return NEAR;
	}

	/** The LOD's far plane: past its farthest corner, as Distant Horizons puts its own. */
	public static float farPlane() {
		return (renderDistance() + 512) * (float) Math.sqrt(2.0);
	}

	/** The perspective the LOD's depth is drawn in: the game's, with the LOD's own near and far planes (dhProjection). */
	public static Matrix4f projection() {
		Matrix4f game = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
		return new Matrix4f().setPerspective(game.perspectiveFov(), game.m11() / game.m00(), nearPlane(), farPlane());
	}

	/** The renderer of the pack in use, if it draws the LOD through its Distant Horizons programs. */
	public static PackLodRenderer renderer() {
		if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline
			&& pipeline.getDHCompat() != null && pipeline.getDHCompat().getInstance() instanceof PackLodRenderer renderer) {
			return renderer;
		}
		return null;
	}

	/** The GPU time of the LOD's draws through the pack, in ms, or -1. */
	public static double gpuMillis() {
		PackLodRenderer renderer = renderer();
		return renderer == null ? -1 : renderer.gpuMillis();
	}

	private static boolean shadowHooked;
	/**
	 * {@code -Dvoid.lod.packShadows=true} puts the LOD into the shadow map of packs with a dh_shadow program (Bliss). Off
	 * by default: a pack's shadow map reaches a few hundred blocks at most, about where the chunks end, so in the views
	 * tested the far land changed no visible shadow, and drawing it there is GPU work every frame for nothing seen.
	 */
	private static final boolean SHADOWS = "true".equals(System.getProperty("void.lod.packShadows"));

	/**
	 * Has Iris's shadow pass call the LOD after the chunks' shadows, once: the callback list outlives every pipeline, so
	 * the hook asks for the current renderer each time.
	 */
	static void hookShadows() {
		if (shadowHooked) {
			return;
		}
		shadowHooked = true;
		net.irisshaders.iris.shadows.ShadowRenderCallbacks.register((modelView, projection, x, y, z, tickDelta) -> {
			PackLodRenderer renderer = renderer();
			VoidLod lod = VoidLod.get();
			if (SHADOWS && renderer != null && lod != null) {
				renderer.drawShadow(lod.packFrame(), modelView, projection);
			}
		});
	}

	/** Draws the frame's water (Iris is about to run the pack's composite passes). */
	public static void drawWater() {
		PackLodRenderer renderer = renderer();
		VoidLod lod = VoidLod.get();
		if (renderer != null) {
			renderer.drawWater(lod == null ? null : lod.packFrame());
		}
	}

	/** Draws the frame's land, or only clears the LOD's depth if there is nothing to draw. */
	public static void drawOpaque(PackFrame frame) {
		PackLodRenderer renderer = renderer();
		if (renderer != null) {
			renderer.drawOpaque(frame);
		}
	}
}
