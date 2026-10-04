package dev.voidmc.lod.compat.iris;

import com.google.common.primitives.Ints;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.shader.GlShader;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;

/**
 * One of a shader pack's Distant Horizons programs ({@code dh_terrain}, {@code dh_water}), compiled the way Iris compiles
 * them for Distant Horizons: patched by Iris's own transformer for the Distant Horizons vertex layout, with the pack's
 * uniforms, samplers, images and blend overrides. The LOD's tiles, built in that layout ({@code TileBuilder.packFormat}),
 * go through it, so the pack lights, shadows and fogs the far terrain exactly as it does Distant Horizons'.
 *
 * <p>Mirrors Iris's {@code IrisLodRenderProgram}, which reaches into Distant Horizons' API for its uniforms and so can
 * only exist with Distant Horizons installed.
 */
final class PackLodProgram {
	private final int id;
	private final int modelOffset;
	private final int projection;
	private final int projectionInverse;
	private final int modelView;
	private final int modelViewInverse;
	private final int normalMatrix;
	private final int mircoOffset;
	private final int clipDistance;
	private final int worldYOffset;
	private final ProgramUniforms uniforms;
	private final CustomUniforms customUniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;
	private final BlendModeOverride blend;
	private final BufferBlendOverride[] bufferBlends;

	private PackLodProgram(String name, boolean shadow, boolean translucent, BlendModeOverride blend, BufferBlendOverride[] bufferBlends,
		Map<PatchShaderType, String> sources, CustomUniforms customUniforms, IrisRenderingPipeline pipeline) {
		this.id = GL32C.glCreateProgram();
		// the Distant Horizons layout, at the locations Iris binds them to
		GL32C.glBindAttribLocation(this.id, 0, "vPosition");
		GL32C.glBindAttribLocation(this.id, 1, "iris_color");
		GL32C.glBindAttribLocation(this.id, 2, "irisExtra");
		List<GlShader> shaders = new ArrayList<>();
		shaders.add(new GlShader(ShaderType.VERTEX, name + ".vsh", sources.get(PatchShaderType.VERTEX)));
		if (sources.get(PatchShaderType.TESS_CONTROL) != null) {
			shaders.add(new GlShader(ShaderType.TESSELATION_CONTROL, name + ".tcs", sources.get(PatchShaderType.TESS_CONTROL)));
		}
		if (sources.get(PatchShaderType.TESS_EVAL) != null) {
			shaders.add(new GlShader(ShaderType.TESSELATION_EVAL, name + ".tes", sources.get(PatchShaderType.TESS_EVAL)));
		}
		if (sources.get(PatchShaderType.GEOMETRY) != null) {
			shaders.add(new GlShader(ShaderType.GEOMETRY, name + ".gsh", sources.get(PatchShaderType.GEOMETRY)));
		}
		shaders.add(new GlShader(ShaderType.FRAGMENT, name + ".fsh", sources.get(PatchShaderType.FRAGMENT)));
		for (GlShader shader : shaders) {
			GL32C.glAttachShader(this.id, shader.getHandle());
		}
		GL32C.glLinkProgram(this.id);
		if (GL32C.glGetProgrami(this.id, GL32C.GL_LINK_STATUS) != GL32C.GL_TRUE) {
			String log = GL32C.glGetProgramInfoLog(this.id);
			GL32C.glDeleteProgram(this.id);
			for (GlShader shader : shaders) {
				shader.destroy();
			}
			throw new RuntimeException("VOID LOD: could not link the pack's " + name + " program: " + log);
		}
		GL32C.glUseProgram(this.id);
		for (GlShader shader : shaders) {
			shader.destroy();
		}

		this.blend = blend;
		this.bufferBlends = bufferBlends;
		ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(name, this.id);
		ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(this.id, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
		CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_VERTEX);
		customUniforms.assignTo(uniformBuilder);
		BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniformBuilder);
		ProgramImages.Builder imageBuilder = ProgramImages.builder(this.id);
		pipeline.addGbufferOrShadowSamplers(samplerBuilder, imageBuilder,
			shadow ? pipeline::getFlippedBeforeShadow : () -> translucent ? pipeline.getFlippedAfterTranslucent() : pipeline.getFlippedAfterPrepare(),
			shadow, false, true, false);
		customUniforms.mapholderToPass(uniformBuilder, this);
		this.uniforms = uniformBuilder.buildUniforms();
		this.customUniforms = customUniforms;
		this.samplers = samplerBuilder.build();
		this.images = imageBuilder.build();
		this.modelOffset = GL32C.glGetUniformLocation(this.id, "modelOffset");
		this.projection = GL32C.glGetUniformLocation(this.id, "iris_ProjectionMatrix");
		this.projectionInverse = GL32C.glGetUniformLocation(this.id, "iris_ProjectionMatrixInverse");
		this.modelView = GL32C.glGetUniformLocation(this.id, "iris_ModelViewMatrix");
		this.modelViewInverse = GL32C.glGetUniformLocation(this.id, "iris_ModelViewMatrixInverse");
		this.normalMatrix = GL32C.glGetUniformLocation(this.id, "iris_NormalMatrix");
		this.mircoOffset = GL32C.glGetUniformLocation(this.id, "mircoOffset");
		this.clipDistance = GL32C.glGetUniformLocation(this.id, "clipDistance");
		this.worldYOffset = GL32C.glGetUniformLocation(this.id, "worldYOffset");
		GL32C.glUseProgram(0);
	}

	/** Compiles {@code source} (a pack's dh_terrain or dh_water) for the LOD. */
	static PackLodProgram create(String name, boolean shadow, boolean translucent, ProgramSource source, IrisRenderingPipeline pipeline) {
		Map<PatchShaderType, String> patched = TransformPatcher.patchDHTerrain(name,
			source.getVertexSource().orElseThrow(RuntimeException::new),
			source.getTessControlSource().orElse(null),
			source.getTessEvalSource().orElse(null),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElseThrow(RuntimeException::new),
			pipeline.getTextureMap(),
			pipeline.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW));
		List<BufferBlendOverride> bufferBlends = new ArrayList<>();
		source.getDirectives().getBufferBlendOverrides().forEach(information -> {
			int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
			if (index > -1) {
				bufferBlends.add(new BufferBlendOverride(index, information.blendMode()));
			}
		});
		return new PackLodProgram(name, shadow, translucent, source.getDirectives().getBlendModeOverride().orElse(null),
			bufferBlends.toArray(BufferBlendOverride[]::new), patched, pipeline.getCustomUniforms(), pipeline);
	}

	void bind() {
		GL32C.glUseProgram(this.id);
		if (this.blend != null) {
			this.blend.apply();
		}
		for (BufferBlendOverride override : this.bufferBlends) {
			override.apply();
		}
	}

	void unbind() {
		GL32C.glUseProgram(0);
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		BlendModeOverride.restore();
	}

	/** This frame's matrices and the pack's uniforms, samplers and images. The program must be bound. */
	void fill(Matrix4fc projection, Matrix4fc modelView, float clip, int yOffset) {
		matrix(this.modelView, modelView);
		matrix(this.modelViewInverse, modelView.invert(new Matrix4f()));
		matrix(this.projection, projection);
		matrix(this.projectionInverse, projection.invert(new Matrix4f()));
		if (this.normalMatrix != -1) {
			Matrix3f normal = new Matrix4f(modelView).invert().transpose3x3(new Matrix3f());
			try (MemoryStack stack = MemoryStack.stackPush()) {
				FloatBuffer buffer = stack.mallocFloat(9);
				normal.get(buffer);
				GL32C.glUniformMatrix3fv(this.normalMatrix, false, buffer);
			}
		}
		if (this.mircoOffset != -1) {
			GL32C.glUniform1f(this.mircoOffset, 0.01F);
		}
		if (this.clipDistance != -1) {
			GL32C.glUniform1f(this.clipDistance, clip);
		}
		if (this.worldYOffset != -1) {
			GL32C.glUniform1i(this.worldYOffset, yOffset);
		}
		this.samplers.update();
		this.uniforms.update();
		this.customUniforms.push(this);
		this.images.update();
	}

	/** Where the next tile's vertices are, relative to the camera, in blocks. */
	void modelOffset(float x, float y, float z) {
		if (this.modelOffset != -1) {
			GL32C.glUniform3f(this.modelOffset, x, y, z);
		}
	}

	void free() {
		GL32C.glDeleteProgram(this.id);
	}

	private static void matrix(int location, Matrix4fc m) {
		if (location == -1) {
			return;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.mallocFloat(16);
			m.get(buffer);
			GL32C.glUniformMatrix4fv(location, false, buffer);
		}
	}
}
