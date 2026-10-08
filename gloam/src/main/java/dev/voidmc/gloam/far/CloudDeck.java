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

	private static final float[] STORM = {0.5F, 0.53F, 0.6F}, SHOWER = {0.72F, 0.74F, 0.79F};
	/** The dusk palette (docs/STYLE.md): cream, gold, orange, rose, violet, dusk blue, and a pale lilac for near tops. */
	private static final float[] CREAM = {1.0F, 0.94F, 0.82F}, GOLD = {1.0F, 0.84F, 0.55F}, ORANGE = {1.0F, 0.66F, 0.4F},
		ROSE = {0.94F, 0.6F, 0.64F}, LILAC = {0.86F, 0.84F, 0.93F}, VIOLET = {0.64F, 0.55F, 0.76F}, DUSK_BLUE = {0.42F, 0.48F, 0.68F};
	/** How far toward the horizon's colour each ring of cells is taken: none near, then in steps. */
	private static final float[] RING_FOG = {0.0F, 0.0F, 0.12F, 0.26F, 0.42F, 0.58F};

	/**
	 * The dawn or dusk band a group of faces takes, by which way they face, which half of the sky they are in and how
	 * far off they are: in the sun's half the tops go cream near, gold further, orange toward the horizon, the sides
	 * facing it orange and the undersides rose; in the other half the tops lilac near and violet further, the undersides
	 * dusk blue.
	 */
	private static float[] band(CloudGrid.Group gr, float sunX) {
		int f = gr.face();
		boolean east = f == CloudGrid.FACE_EAST || f == CloudGrid.FACE_BOTTOM_EAST || f == CloudGrid.FACE_TOP_EAST;
		boolean west = f == CloudGrid.FACE_WEST || f == CloudGrid.FACE_BOTTOM || f == CloudGrid.FACE_TOP;
		boolean sunHalf = east ? sunX > 0 : west && sunX < 0;
		boolean top = f == CloudGrid.FACE_TOP || f == CloudGrid.FACE_TOP_EAST;
		boolean bottom = f == CloudGrid.FACE_BOTTOM || f == CloudGrid.FACE_BOTTOM_EAST;
		int ring = gr.ring();
		if (f == CloudGrid.FACE_NS) {
			return ring < 3 ? LILAC : VIOLET;
		}
		if (sunHalf) {
			if (top) {
				return ring < 2 ? CREAM : ring < 4 ? GOLD : ORANGE;
			}
			return bottom ? ROSE : ORANGE;
		}
		if (top) {
			return ring < 3 ? LILAC : VIOLET;
		}
		return bottom ? DUSK_BLUE : VIOLET;
	}

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
	void prepare(double frameX, double frameZ, double below, int color, float[] light) {
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
		float sunX = light[0], glow = light[5];
		float bright = Math.max(r, Math.max(g, b));
		for (int i = 0; i < this.groups.size(); i++) {
			CloudGrid.Group gr = this.groups.get(i);
			int at = i * this.stride;
			// The palette (docs/STYLE.md: chosen, never blended freely): vanilla's cloud colour, a shower grey, a storm grey;
			// vanilla's shading gives each its lit top, its sides and its shadowed underside in flat steps.
			float[] c = gr.tone() == 2 ? STORM : gr.tone() == 1 ? SHOWER : null;
			float cr = c == null ? r : c[0] * bright, cg = c == null ? g : c[1] * bright, cb = c == null ? b : c[2] * bright;
			if (glow > 0.15F) {
				// Dawn and dusk in bands, without a shader pack (which lights the clouds itself): gold on the tops and the
				// high deck in the sun's half of the sky, orange on the sides toward it and the undersides of the main deck,
				// rose under the low stratus and on the sides along it, violet on the far side, dusk blue underneath away
				// from it. Two steps in: half, then whole.
				float[] band = band(gr, sunX);
				float k = Math.clamp(bright * 1.15F, 0.35F, 1.0F) * (gr.tone() == 2 ? 0.7F : 1.0F);
				float w = glow > 0.5F ? 1.0F : 0.5F;
				cr += (band[0] * k - cr) * w;
				cg += (band[1] * k - cg) * w;
				cb += (band[2] * k - cb) * w;
			}
			// The air between, in steps, one per ring of cells (each twice as far as the last): the far clouds paler and
			// crisper (whole, not vanilla's translucent near clouds), sinking toward the horizon's colour band by band.
			int ring = Math.min(gr.ring(), RING_FOG.length - 1);
			float fogged = RING_FOG[ring];
			cr += (light[6] - cr) * fogged;
			cg += (light[7] - cg) * fogged;
			cb += (light[8] - cb) * fogged;
			float alpha = ring >= 2 ? Math.min(1.0F, a * 1.25F) * (1.0F - fogged * 0.5F) : a;
			this.infos.putFloat(at, cr).putFloat(at + 4, cg).putFloat(at + 8, cb).putFloat(at + 12, alpha);
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
