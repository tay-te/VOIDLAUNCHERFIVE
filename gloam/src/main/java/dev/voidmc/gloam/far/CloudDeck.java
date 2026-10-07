package dev.voidmc.gloam.far;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.GpuFormat;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

/**
 * Gloam's clouds on the GPU, drawn by vanilla's cloud renderer in place of its own cells (CloudRendererMixin): the mesh
 * of {@link CloudGrid}, in vanilla's face format, drawn group by group through whatever pipeline vanilla draws its
 * clouds with this frame (fancy or fast, plain or improved transparency), each group with its own {@code CloudInfo}
 * (its cells' size and place, its height, its tone). A shader pack's clouds program, which Iris swaps in for vanilla's
 * pipelines, draws the very same faces, so under a pack Gloam's clouds are the pack's clouds.
 */
final class CloudDeck implements AutoCloseable {
	/** std140 CloudInfo: vec4 colour, vec3 offset, vec3 cell size. */
	private static final int INFO_BYTES = 48;

	private final GpuDevice device = RenderSystem.getDevice();
	private GpuBuffer faces;
	private List<CloudGrid.Group> groups = List.of();
	private int maxGroup;
	private final GpuTexture shade;
	private final GpuTextureView shadeView;
	private double shadeX, shadeZ, shadeSize;
	private boolean hasShade;
	private ByteBuffer infos = MemoryUtil.memAlloc(INFO_BYTES * 64);
	private GpuBufferSlice infoSlice;
	private int stride;
	private int drawn;

	CloudDeck() {
		this.shade = this.device.createTexture("VOID Gloam cloud shadows", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, CloudGrid.SHADE, CloudGrid.SHADE, 1, 1);
		this.shadeView = this.device.createTextureView(this.shade);
	}

	/** Takes in a finished mesh (render thread, outside any pass). */
	void accept(CloudGrid.Mesh mesh) {
		if (this.faces != null) {
			this.faces.close();
			this.faces = null;
		}
		this.groups = mesh.groups();
		this.maxGroup = mesh.maxGroup();
		if (!this.groups.isEmpty()) {
			this.faces = this.device.createBuffer(() -> "VOID Gloam clouds", GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, mesh.faces());
		}
		if (mesh.shade() != null) {
			this.device.createCommandEncoder().writeToTexture(this.shade, mesh.shade(), 0, 0, 0, 0, CloudGrid.SHADE, CloudGrid.SHADE);
			this.shadeX = mesh.shadeX();
			this.shadeZ = mesh.shadeZ();
			this.shadeSize = mesh.shadeSize();
			this.hasShade = true;
		}
	}

	/**
	 * This frame's places for the groups: the camera at frame ({@code frameX}, {@code frameZ}), {@code below} blocks
	 * under the deck's base; vanilla's cloud colour, darkened for showery and stormy tones. Outside any pass.
	 */
	void prepare(double frameX, double frameZ, double below, int color) {
		if (this.groups.isEmpty()) {
			return;
		}
		int align = this.device.getDeviceInfo().limits().minUniformOffsetAlignment();
		this.stride = (INFO_BYTES + align - 1) / align * align;
		int need = this.stride * this.groups.size();
		if (this.infos.capacity() < need) {
			this.infos = MemoryUtil.memRealloc(this.infos, need);
		}
		this.infos.clear();
		float r = ARGB.red(color) / 255.0F, g = ARGB.green(color) / 255.0F, b = ARGB.blue(color) / 255.0F, a = ARGB.alpha(color) / 255.0F;
		for (int i = 0; i < this.groups.size(); i++) {
			CloudGrid.Group gr = this.groups.get(i);
			int at = i * this.stride;
			// showers greyer, storms dark, a little bluish, as the deck is thick and the light comes through little
			float dim = gr.tone() == 2 ? 0.52F : gr.tone() == 1 ? 0.76F : 1.0F;
			float blue = gr.tone() == 2 ? 1.06F : 1.0F;
			this.infos.putFloat(at, r * dim).putFloat(at + 4, g * dim).putFloat(at + 8, b * dim * blue).putFloat(at + 12, a);
			this.infos.putFloat(at + 16, (float) (gr.originX() * (double) gr.size() - frameX));
			this.infos.putFloat(at + 20, (float) (gr.y() - below));
			this.infos.putFloat(at + 24, (float) (gr.originZ() * (double) gr.size() - frameZ));
			this.infos.putFloat(at + 32, gr.size()).putFloat(at + 36, gr.height()).putFloat(at + 40, gr.size());
		}
		this.infos.position(0).limit(need);
		CommandEncoder encoder = this.device.createCommandEncoder();
		this.infoSlice = encoder.transientMemory().uploadGpu(this.infos, align, GpuBuffer.USAGE_UNIFORM);
		RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(this.maxGroup * 6);
		RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(this.maxGroup * 6);
	}

	/** Whether there is anything to draw. */
	boolean anything() {
		return this.faces != null && this.infoSlice != null && !this.groups.isEmpty();
	}

	/** Draws every group with {@code pipeline}, as vanilla's CloudRenderer would draw its one mesh. */
	void draw(RenderPass pass, RenderPipeline pipeline) {
		if (!this.anything()) {
			return;
		}
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
		pass.setUniform("DynamicTransforms", transforms);
		pass.setIndexBuffer(indices.getBuffer(), indices.type());
		pass.setVertexBuffer(0, null);
		pass.setUniform("CloudFaces", this.faces);
		for (int i = 0; i < this.groups.size(); i++) {
			CloudGrid.Group gr = this.groups.get(i);
			pass.setUniform("CloudInfo", this.infoSlice.slice((long) i * this.stride, INFO_BYTES));
			// the group's faces from the shared buffer: vanilla's shader finds a face from gl_VertexID, which the base
			// vertex offsets
			pass.drawIndexed(gr.count() * 6, 1, 0, gr.first() * 4, 0);
		}
		this.drawn++;
	}

	int faceCount() {
		int n = 0;
		for (CloudGrid.Group g : this.groups) {
			n += g.count();
		}
		return n;
	}

	int groupCount() {
		return this.groups.size();
	}

	boolean hasShade() {
		return this.hasShade;
	}

	GpuTextureView shadeView() {
		return this.shadeView;
	}

	double shadeX() {
		return this.shadeX;
	}

	double shadeZ() {
		return this.shadeZ;
	}

	double shadeSize() {
		return this.shadeSize;
	}

	@Override
	public void close() {
		if (this.faces != null) {
			this.faces.close();
		}
		this.shadeView.close();
		this.shade.close();
		MemoryUtil.memFree(this.infos);
	}
}
