package dev.voidmc.gloam.motes;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.voidmc.gloam.Gloam;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * The particle layer the soft motes (mist wisps, breath, spray) draw in. Vanilla's translucent particles cut every
 * pixel fainter than alpha 0.1 and write depth, so a veil of mist would end in a hard line and hide the veils behind
 * it wherever they overlap out of order. This layer draws the same particle atlas with the same vertex shader, but its
 * fragment shader keeps the faint edges ({@code assets/gloam/shaders/core/mist_particle.fsh}) and it leaves the depth
 * buffer alone, so overlapping veils simply add up.
 *
 * <p>With Iris and a shader pack, the pipeline is handed to the pack's translucent particle program like vanilla's
 * own; with improved transparency (order-independent), vanilla's OIT particle pipelines draw it.
 */
final class MoteLayers {
	static final RenderPipeline MIST_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
		.withLocation(Gloam.id("pipeline/mist_particle"))
		.withFragmentShader(Gloam.id("core/mist_particle"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.build();

	static final SingleQuadParticle.Layer MIST = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_PIPELINE,
		RenderPipelines.OIT_PARTICLE);

	/**
	 * The ground mist's sheets ({@link MistMote}): like {@link #MIST_PIPELINE}, but with shaders of their own
	 * ({@code ground_mist.vsh/fsh}) that make a sheet as opaque as the eye's path through its slab is long, as the far
	 * mist's shader does, and drawn from below as well as above.
	 */
	static final RenderPipeline GROUND_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
		.withLocation(Gloam.id("pipeline/ground_mist"))
		.withVertexShader(Gloam.id("core/ground_mist"))
		.withFragmentShader(Gloam.id("core/ground_mist"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.withCull(false)
		.build();

	/** The scene's depth behind the ground mist, copied out of the main pass ({@link SoftMist}). */
	static final BindGroupLayout SCENE_DEPTH = BindGroupLayout.builder().withUniform("SceneDepth", UniformType.COMBINED_IMAGE_SAMPLER).build();

	/**
	 * {@link #GROUND_PIPELINE} made soft: the sheets fade where they near the scene behind them, read from a copy of the
	 * depth ({@code GLOAM_SOFT} in {@code ground_mist.fsh}). Drawn by {@link SoftMist} after vanilla's main pass, never
	 * through a particle layer.
	 */
	static final RenderPipeline GROUND_SOFT_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
		.withLocation(Gloam.id("pipeline/ground_mist_soft"))
		.withVertexShader(Gloam.id("core/ground_mist"))
		.withFragmentShader(Gloam.id("core/ground_mist"))
		.withShaderDefine("GLOAM_SOFT")
		.withBindGroupLayout(SCENE_DEPTH)
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.withCull(false)
		.build();

	/**
	 * Gloam's own lights (fireflies): drawn at their own colour, never through the lightmap (so a dark night, however dark
	 * the sky part makes it, cannot dim them), added onto what lies behind them and fading into the fog with distance.
	 * Without a pack only; a pack draws fireflies as the particles they are.
	 */
	static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
		.withLocation(Gloam.id("pipeline/glow"))
		.withVertexShader(Gloam.id("core/glow"))
		.withFragmentShader(Gloam.id("core/glow"))
		.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.build();

	static final SingleQuadParticle.Layer GLOW = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, GLOW_PIPELINE,
		RenderPipelines.OIT_PARTICLE);

	static final SingleQuadParticle.Layer GROUND = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, GROUND_PIPELINE,
		RenderPipelines.OIT_PARTICLE);

	private MoteLayers() {
	}

	static void init() {
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			Iris.assign();
		}
	}

	/** Iris's side in a class of its own, so the JVM only resolves it once Iris is known to be there. */
	private static final class Iris {
		static void assign() {
			var iris = net.irisshaders.iris.api.v0.IrisApi.getInstance();
			iris.assignPipeline(MIST_PIPELINE, net.irisshaders.iris.api.v0.IrisProgram.PARTICLES_TRANSLUCENT);
			iris.assignPipeline(GROUND_PIPELINE, net.irisshaders.iris.api.v0.IrisProgram.PARTICLES_TRANSLUCENT);
		}
	}
}
