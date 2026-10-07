package dev.voidmc.gloam.sky;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.compat.Shaders;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The sky drawn pixel-true (docs/STYLE.md) when no shader pack draws it: vanilla's sky disc, which fades from the
 * sky's colour into the fog's toward the horizon, in five flat bands; and the glow round a low sun in four flat steps
 * of strength. The same geometry and colours as vanilla's, through Gloam's own pipelines ({@code SkyRendererMixin}
 * swaps them in); a shader pack keeps its own sky.
 */
public final class SkyBands {
	/** Vanilla's sky pipeline with the banded fragment shader. */
	public static final RenderPipeline SKY = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
		.withLocation(Gloam.id("pipeline/sky_banded"))
		.withVertexShader(Gloam.id("core/sky_banded"))
		.withFragmentShader(Gloam.id("core/sky_banded"))
		.withVertexBinding(0, DefaultVertexFormat.POSITION)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();

	/** Vanilla's sunrise and sunset fan with the stepped fragment shader. */
	public static final RenderPipeline SUNRISE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withLocation(Gloam.id("pipeline/sunrise_banded"))
		.withVertexShader(Gloam.id("core/sunrise_banded"))
		.withFragmentShader(Gloam.id("core/sunrise_banded"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
		.build();

	private SkyBands() {
	}

	/** Whether the sky is drawn in bands this frame: Gloam and its sky on, the setting on, and no shader pack drawing. */
	public static boolean on() {
		GloamSettings s = GloamSettings.get();
		return s.enabled && s.sky && s.pixelSky && !Shaders.packInUse();
	}
}
