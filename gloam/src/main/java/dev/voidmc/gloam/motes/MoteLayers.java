package dev.voidmc.gloam.motes;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
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
			net.irisshaders.iris.api.v0.IrisApi.getInstance().assignPipeline(MIST_PIPELINE, net.irisshaders.iris.api.v0.IrisProgram.PARTICLES_TRANSLUCENT);
		}
	}
}
