package dev.voidmc.lod;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/**
 * The chunks dissolve into the LOD behind them instead of ending in a wall. Vanilla fades a newly drawn section in from
 * the fog colour, which in front of the LOD flashes pale at the seam each time a chunk arrives, and its chunks end in
 * the ragged square of the last loaded sections. With the LOD on, vanilla's chunk shaders (patched as they load:
 * {@code mixin.ChunkShaderMixin}) dither the chunks away instead, over the last {@value #EDGE} blocks before
 * {@link #edge} and while a section fades in, so the LOD shows through; the LOD is drawn whole under that ring
 * ({@link VoidLod#clipRadius}).
 *
 * <p>No uniform is added for it: under vanilla's renderer the edge's radius rides in the visibility vanilla already
 * hands each section ({@link #encode}), which is 0..1 alone and {@code 2 * edge + 0.25 + 0.5 * visibility} with the
 * LOD behind; under Sodium's, in its render-distance fog start, negative ({@link #sodiumFogStart}). Not with a shader
 * pack, which draws the chunks with its own shaders.
 */
public final class ChunkFade {
	public static final Identifier TERRAIN = Identifier.withDefaultNamespace("core/terrain");
	/** Sodium's chunk shader, which goes through vanilla's shader loading too. */
	public static final Identifier SODIUM_TERRAIN = Identifier.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque");
	/** Blocks over which the chunks dissolve before their edge. */
	public static final int EDGE = 24;
	/** The fewest chunks drawn for an edge to fade over (below it, sections still dissolve in, but nothing at the edge). */
	private static final int MIN_DISTANCE = 5;
	/** An edge beyond any chunk: new sections dissolve in, the chunks' edge does not. */
	private static final int NO_EDGE = 4000;
	private static final boolean ENABLED = !"false".equals(System.getProperty("void.lod.chunkFade"));
	private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");

	private static volatile boolean vertexPatched, fragmentPatched, sodiumPatched;
	/** The edge this frame, in blocks from the camera; 0 while the chunks draw as vanilla's. */
	private static volatile int seam;

	private ChunkFade() {
	}

	/** Whether the chunks dissolve into the LOD (the LOD drawing behind them, under vanilla's renderer). */
	public static boolean on() {
		return seam > 0;
	}

	/** Each frame, on the render thread: whether the LOD is drawn behind the chunks, and how many chunks are drawn. */
	static void update(boolean lodBehind, int renderDistance) {
		boolean patched = SODIUM ? sodiumPatched : vertexPatched && fragmentPatched;
		if (!ENABLED || !lodBehind || !patched) {
			seam = 0;
		} else {
			seam = renderDistance >= MIN_DISTANCE ? edge(renderDistance) : NO_EDGE;
		}
	}

	/** Where the chunks are gone, in blocks: inside the sections the game always draws, so their ragged end is hidden. */
	static int edge(int renderDistance) {
		return renderDistance * 16 - 20;
	}

	/** Whether the chunks' edge dissolves at this render distance (when {@link #on}). */
	static boolean fadesEdge(int renderDistance) {
		return renderDistance >= MIN_DISTANCE;
	}

	/** A section's visibility as handed to the chunk shaders: with the edge's radius in it while {@link #on}. */
	public static float encode(float visibility) {
		int s = seam;
		return s <= 0 ? visibility : 2.0F * s + 0.25F + 0.5F * visibility;
	}

	/** Sodium's render-distance fog start as its chunk shader is handed it: the edge, negative, while {@link #on}. */
	public static float sodiumFogStart(float start) {
		int s = seam;
		return s <= 0 ? start : -s;
	}

	private static final String DITHER = "fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))))";

	private static final String VERTEX_FIND =
		"    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));";
	private static final String VERTEX_REPLACE = """
		    // VOID LOD: past 1, the section's visibility carries the radius the chunks dissolve by (dev.voidmc.lod.ChunkFade)
		    float voidLodSeam = floor(ChunkVisibility * 0.5);
		    float voidLodFade = voidLodSeam > 0.0 ? clamp((ChunkVisibility - 2.0 * voidLodSeam - 0.25) * 2.0, 0.0, 1.0) : ChunkVisibility;
		    voidLodFade = mix(1.0, voidLodFade, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
		    chunkVisibility = voidLodSeam > 0.0 ? -(2.0 * voidLodSeam + 0.25 + 0.5 * voidLodFade) : voidLodFade;""";
	// (not a text block: that would strip the lines' own indentation, which the source has)
	private static final String FRAGMENT_FIND = "    #ifndef OIT_ALPHA_ONLY\n"
		+ "    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);\n"
		+ "    #endif";
	private static final String FRAGMENT_REPLACE = """
		    if (chunkVisibility < 0.0) {
		        // VOID LOD: dissolve into the far view behind, as a section fades in and over the chunks' last blocks
		        float voidLodU = -chunkVisibility;
		        float voidLodSeam = floor(voidLodU * 0.5);
		        float voidLodFade = clamp((voidLodU - 2.0 * voidLodSeam - 0.25) * 2.0, 0.0, 1.0);
		        float voidLodEdge = clamp((voidLodSeam - cylindricalVertexDistance) / %d.0, 0.0, 1.0);
		        if (%s >= min(voidLodFade, voidLodEdge)) {
		            discard;
		        }
		    }
		    #ifndef OIT_ALPHA_ONLY
		    else {
		        color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
		    }
		    #endif""".formatted(EDGE, DITHER);
	private static final String[][] SODIUM_FRAGMENT = {
		{"    color *= v_Color; // Apply per-vertex color modulator\n", """
			    color *= v_Color; // Apply per-vertex color modulator
			#ifdef USE_FOG
			    if (u_RenderFog.x < 0.0) {
			        // VOID LOD: dissolve into the far view behind, as a section fades in and over the chunks' last blocks
			        float voidLodEdge = clamp((-u_RenderFog.x - v_FragDistance.x) / %d.0, 0.0, 1.0);
			        if (%s >= min(fadeFactor, voidLodEdge)) {
			            discard;
			        }
			    }
			#endif
			""".formatted(EDGE, DITHER)},
		// no fading from the fog colour then, and no render-distance fog (it starts at the LOD's edge, past the chunks)
		{"    float factor = fadeFactor;", "    float factor = u_RenderFog.x < 0.0 ? 1.0 : fadeFactor;"},
		{"u_EnvironmentFog, u_RenderFog, factor)", "u_EnvironmentFog, u_RenderFog.x < 0.0 ? vec2(1e30) : u_RenderFog, factor)"},
	};

	/** Sodium's chunk fragment shader {@code source}, patched; as it was if it is not the one expected. */
	public static String patchSodium(String source, ShaderType type) {
		if (type != ShaderType.FRAGMENT) {
			return source;
		}
		String s = source.replace("\r\n", "\n");
		for (String[] change : SODIUM_FRAGMENT) {
			if (!s.contains(change[0])) {
				LoggerFactory.getLogger("VOID LOD").info("VOID LOD: Sodium's chunk shader is not the one expected; the chunks will end at their edge as they do");
				sodiumPatched = false;
				return source;
			}
			s = s.replace(change[0], change[1]);
		}
		sodiumPatched = true;
		return s;
	}

	/** Vanilla's chunk shader {@code source}, patched; as it was if it is not the one expected (a pack's own, say). */
	public static String patch(String source, ShaderType type) {
		String s = source.replace("\r\n", "\n");
		boolean vertex = type == ShaderType.VERTEX;
		String find = vertex ? VERTEX_FIND : FRAGMENT_FIND;
		if (!s.contains(find)) {
			LoggerFactory.getLogger("VOID LOD").info("VOID LOD: the chunks' {} shader is not vanilla's; they will end at their edge as they do",
				vertex ? "vertex" : "fragment");
			if (vertex) {
				vertexPatched = false;
			} else {
				fragmentPatched = false;
			}
			return source;
		}
		if (vertex) {
			vertexPatched = true;
		} else {
			fragmentPatched = true;
		}
		return s.replace(find, vertex ? VERTEX_REPLACE : FRAGMENT_REPLACE);
	}
}
