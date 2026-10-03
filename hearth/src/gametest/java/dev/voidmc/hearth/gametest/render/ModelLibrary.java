package dev.voidmc.hearth.gametest.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.gametest.render.Assets.Texture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Turns block states into textured quads the way the game's model bakery does, from the vanilla JSON: blockstate
 * variants and multipart, model parents and texture variables, elements with per-face UVs, face rotation, element
 * rotation and the variant's x/y rotation. Blocks drawn by block-entity renderers in game (chests, banners, decorated
 * pots, bell bodies, skulls, shulker boxes) and fluids get simple stand-in geometry with their real textures.
 */
public final class ModelLibrary {
	private final Assets assets;
	private final Map<BlockState, List<Quad>> cache = new IdentityHashMap<>();

	public ModelLibrary(Assets assets) {
		this.assets = assets;
	}

	/**
	 * One textured quad in block space (0..1). Vertices go top-left, top-right, bottom-right, bottom-left of the texture
	 * as seen from outside.
	 *
	 * @param tint     0xRRGGBB multiplier, or -1
	 * @param cull     the neighbour direction that hides this face when solid, or null
	 * @param aligned  an axis-aligned face on the block's boundary (gets ambient occlusion)
	 */
	public record Quad(float[] pos, float[] uv, Texture tex, int tint, float nx, float ny, float nz, @Nullable Direction cull, boolean shade,
		boolean translucent, boolean aligned) {
	}

	public synchronized List<Quad> quads(BlockState state) {
		List<Quad> q = this.cache.get(state);
		if (q == null) {
			q = build(state);
			this.cache.put(state, q);
		}
		return q;
	}

	private List<Quad> build(BlockState state) {
		if (state.isAir()) {
			return List.of();
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String path = id.getPath();
		if (path.equals("structure_void") || path.equals("barrier") || path.equals("light")) {
			return List.of();
		}
		List<Quad> special = special(state, id, path);
		if (special != null) {
			return special;
		}
		JsonObject bs = this.assets.json("assets/" + id.getNamespace() + "/blockstates/" + path + ".json");
		List<Quad> out = new ArrayList<>();
		if (bs == null) {
			return cube(this.assets.texture("minecraft:block/stone"), -1);
		}
		Map<String, String> props = new HashMap<>();
		state.getValues().forEach(v -> props.put(v.property().getName(), v.valueName()));
		if (bs.has("variants")) {
			JsonObject variants = bs.getAsJsonObject("variants");
			JsonElement chosen = null;
			for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
				if (matchesVariant(e.getKey(), props)) {
					chosen = e.getValue();
					break;
				}
			}
			if (chosen != null) {
				addModel(out, first(chosen), state, path);
			}
		} else if (bs.has("multipart")) {
			for (JsonElement part : bs.getAsJsonArray("multipart")) {
				JsonObject p = part.getAsJsonObject();
				if (!p.has("when") || matchesWhen(p.getAsJsonObject("when"), props)) {
					addModel(out, first(p.get("apply")), state, path);
				}
			}
		}
		if (out.isEmpty()) {
			String particle = particle(bs, props);
			if (particle != null) {
				return cube(this.assets.texture(particle), -1);
			}
		}
		return out;
	}

	private static JsonObject first(JsonElement e) {
		return e.isJsonArray() ? e.getAsJsonArray().get(0).getAsJsonObject() : e.getAsJsonObject();
	}

	private static boolean matchesVariant(String key, Map<String, String> props) {
		if (key.isEmpty()) {
			return true;
		}
		for (String kv : key.split(",")) {
			int eq = kv.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String v = props.get(kv.substring(0, eq));
			if (v == null || !v.equals(kv.substring(eq + 1))) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesWhen(JsonObject when, Map<String, String> props) {
		if (when.has("OR")) {
			for (JsonElement e : when.getAsJsonArray("OR")) {
				if (matchesWhen(e.getAsJsonObject(), props)) {
					return true;
				}
			}
			return false;
		}
		if (when.has("AND")) {
			for (JsonElement e : when.getAsJsonArray("AND")) {
				if (!matchesWhen(e.getAsJsonObject(), props)) {
					return false;
				}
			}
			return true;
		}
		for (Map.Entry<String, JsonElement> e : when.entrySet()) {
			String v = props.get(e.getKey());
			String want = e.getValue().getAsString();
			boolean neg = want.startsWith("!");
			if (neg) {
				want = want.substring(1);
			}
			boolean any = false;
			for (String alt : want.split("\\|")) {
				if (alt.equals(v)) {
					any = true;
					break;
				}
			}
			if (any == neg) {
				return false;
			}
		}
		return true;
	}

	private @Nullable String particle(JsonObject bs, Map<String, String> props) {
		JsonObject m = null;
		if (bs.has("variants")) {
			for (Map.Entry<String, JsonElement> e : bs.getAsJsonObject("variants").entrySet()) {
				m = first(e.getValue());
				break;
			}
		}
		if (m == null) {
			return null;
		}
		Map<String, String> tex = new HashMap<>();
		resolveModel(m.get("model").getAsString(), tex, new ArrayList<>());
		return resolveTex("#particle", tex);
	}

	// ------------------------------------------------------------------------------------------- models

	private void addModel(List<Quad> out, JsonObject variant, BlockState state, String path) {
		String model = variant.get("model").getAsString();
		int rx = variant.has("x") ? variant.get("x").getAsInt() : 0;
		int ry = variant.has("y") ? variant.get("y").getAsInt() : 0;
		Map<String, String> tex = new HashMap<>();
		List<JsonArray> elements = new ArrayList<>();
		resolveModel(model, tex, elements);
		if (elements.isEmpty()) {
			return;
		}
		boolean translucent = isTranslucent(path);
		for (JsonElement el : elements.get(0)) {
			JsonObject e = el.getAsJsonObject();
			float[] from = vec(e.getAsJsonArray("from"));
			float[] to = vec(e.getAsJsonArray("to"));
			boolean shade = !e.has("shade") || e.get("shade").getAsBoolean();
			JsonObject rot = e.has("rotation") ? e.getAsJsonObject("rotation") : null;
			JsonObject faces = e.getAsJsonObject("faces");
			for (Map.Entry<String, JsonElement> fe : faces.entrySet()) {
				Direction dir = Direction.byName(fe.getKey());
				if (dir == null) {
					continue;
				}
				JsonObject f = fe.getValue().getAsJsonObject();
				String texRef = resolveTex(f.get("texture").getAsString(), tex);
				Texture t = texRef == null ? Texture.MISSING : this.assets.texture(texRef);
				float[] uv = f.has("uv") ? vec4(f.getAsJsonArray("uv")) : defaultUv(dir, from, to);
				int frot = f.has("rotation") ? f.get("rotation").getAsInt() : 0;
				Direction cull = f.has("cullface") ? Direction.byName(f.get("cullface").getAsString()) : null;
				int tint = f.has("tintindex") ? tint(path, state) : -1;
				float[] pos = corners(dir, from, to);
				float[] n = {dir.getStepX(), dir.getStepY(), dir.getStepZ()};
				boolean aligned = rot == null && boundary(dir, from, to);
				if (rot != null) {
					rotateElement(pos, n, rot);
				}
				if (rx != 0 || ry != 0) {
					rotateModel(pos, n, rx, ry);
					if (cull != null) {
						cull = rotateDir(cull, rx, ry);
					}
				}
				out.add(new Quad(pos, uvCorners(uv, frot), t, tint, n[0], n[1], n[2], cull, shade, translucent, aligned));
			}
		}
	}

	private void resolveModel(String model, Map<String, String> tex, List<JsonArray> elements) {
		String ref = model;
		for (int i = 0; i < 16 && ref != null; i++) {
			String ns = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "minecraft";
			String p = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
			JsonObject m = this.assets.json("assets/" + ns + "/models/" + p + ".json");
			if (m == null) {
				break;
			}
			if (m.has("textures")) {
				for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("textures").entrySet()) {
					JsonElement v = e.getValue();
					String s = v.isJsonObject() ? (v.getAsJsonObject().has("sprite") ? v.getAsJsonObject().get("sprite").getAsString() : null) : v.getAsString();
					if (s != null) {
						tex.putIfAbsent(e.getKey(), s);
					}
				}
			}
			if (elements.isEmpty() && m.has("elements")) {
				elements.add(m.getAsJsonArray("elements"));
			}
			ref = m.has("parent") ? m.get("parent").getAsString() : null;
		}
	}

	private static @Nullable String resolveTex(String ref, Map<String, String> tex) {
		String r = ref;
		for (int i = 0; i < 10 && r != null && r.startsWith("#"); i++) {
			r = tex.get(r.substring(1));
		}
		if (r == null || r.startsWith("#")) {
			return null;
		}
		return r.contains(":") ? r : "minecraft:" + r;
	}

	private static float[] vec(JsonArray a) {
		return new float[]{a.get(0).getAsFloat() / 16f, a.get(1).getAsFloat() / 16f, a.get(2).getAsFloat() / 16f};
	}

	private static float[] vec4(JsonArray a) {
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()};
	}

	private static boolean boundary(Direction d, float[] f, float[] t) {
		boolean full = switch (d.getAxis()) {
			case X -> f[1] == 0 && t[1] == 1 && f[2] == 0 && t[2] == 1;
			case Y -> f[0] == 0 && t[0] == 1 && f[2] == 0 && t[2] == 1;
			case Z -> f[0] == 0 && t[0] == 1 && f[1] == 0 && t[1] == 1;
		};
		float plane = switch (d) {
			case DOWN -> f[1];
			case UP -> t[1];
			case NORTH -> f[2];
			case SOUTH -> t[2];
			case WEST -> f[0];
			case EAST -> t[0];
		};
		return full && (plane == 0 || plane == 1);
	}

	/** Default UVs (0..16) from the element's extent, as the game derives them. */
	private static float[] defaultUv(Direction d, float[] f, float[] t) {
		float x0 = f[0] * 16;
		float y0 = f[1] * 16;
		float z0 = f[2] * 16;
		float x1 = t[0] * 16;
		float y1 = t[1] * 16;
		float z1 = t[2] * 16;
		return switch (d) {
			case DOWN -> new float[]{x0, 16 - z1, x1, 16 - z0};
			case UP -> new float[]{x0, z0, x1, z1};
			case NORTH -> new float[]{16 - x1, 16 - y1, 16 - x0, 16 - y0};
			case SOUTH -> new float[]{x0, 16 - y1, x1, 16 - y0};
			case WEST -> new float[]{z0, 16 - y1, z1, 16 - y0};
			case EAST -> new float[]{16 - z1, 16 - y1, 16 - z0, 16 - y0};
		};
	}

	/** Vertex positions TL, TR, BR, BL as seen from outside the face. */
	private static float[] corners(Direction d, float[] f, float[] t) {
		float x0 = f[0];
		float y0 = f[1];
		float z0 = f[2];
		float x1 = t[0];
		float y1 = t[1];
		float z1 = t[2];
		return switch (d) {
			case NORTH -> new float[]{x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0};
			case SOUTH -> new float[]{x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1};
			case WEST -> new float[]{x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0};
			case EAST -> new float[]{x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1};
			case UP -> new float[]{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1};
			case DOWN -> new float[]{x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0};
		};
	}

	/** UVs per vertex (TL, TR, BR, BL), the texture turned clockwise by the face rotation. */
	private static float[] uvCorners(float[] uv, int rotation) {
		float[][] c = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
		int k = Math.floorMod(rotation / 90, 4);
		float[] out = new float[8];
		for (int i = 0; i < 4; i++) {
			float[] src = c[Math.floorMod(i - k, 4)];
			out[i * 2] = src[0];
			out[i * 2 + 1] = src[1];
		}
		return out;
	}

	private static void rotateElement(float[] pos, float[] n, JsonObject rot) {
		float[] o = vec(rot.getAsJsonArray("origin"));
		String axis = rot.get("axis").getAsString();
		double a = Math.toRadians(rot.get("angle").getAsFloat());
		boolean rescale = rot.has("rescale") && rot.get("rescale").getAsBoolean();
		double scale = rescale ? 1.0 / Math.cos(Math.abs(a)) : 1.0;
		double c = Math.cos(a);
		double s = Math.sin(a);
		for (int i = 0; i < 4; i++) {
			double x = pos[i * 3] - o[0];
			double y = pos[i * 3 + 1] - o[1];
			double z = pos[i * 3 + 2] - o[2];
			double[] r = rot(axis, x, y, z, c, s);
			if (rescale) {
				switch (axis) {
					case "x" -> {
						r[1] *= scale;
						r[2] *= scale;
					}
					case "y" -> {
						r[0] *= scale;
						r[2] *= scale;
					}
					default -> {
						r[0] *= scale;
						r[1] *= scale;
					}
				}
			}
			pos[i * 3] = (float) (r[0] + o[0]);
			pos[i * 3 + 1] = (float) (r[1] + o[1]);
			pos[i * 3 + 2] = (float) (r[2] + o[2]);
		}
		double[] rn = rot(axis, n[0], n[1], n[2], c, s);
		n[0] = (float) rn[0];
		n[1] = (float) rn[1];
		n[2] = (float) rn[2];
	}

	private static double[] rot(String axis, double x, double y, double z, double c, double s) {
		return switch (axis) {
			case "x" -> new double[]{x, y * c - z * s, y * s + z * c};
			case "y" -> new double[]{x * c + z * s, y, -x * s + z * c};
			default -> new double[]{x * c - y * s, x * s + y * c, z};
		};
	}

	/** The variant's rotation: x (around the X axis, 90 turns north to down) then y (90 turns north to east). */
	private static void rotateModel(float[] pos, float[] n, int rx, int ry) {
		for (int i = 0; i < 4; i++) {
			float[] p = {pos[i * 3] - 0.5f, pos[i * 3 + 1] - 0.5f, pos[i * 3 + 2] - 0.5f};
			turn(p, rx, ry);
			pos[i * 3] = p[0] + 0.5f;
			pos[i * 3 + 1] = p[1] + 0.5f;
			pos[i * 3 + 2] = p[2] + 0.5f;
		}
		turn(n, rx, ry);
	}

	private static void turn(float[] p, int rx, int ry) {
		for (int k = 0; k < Math.floorMod(rx / 90, 4); k++) {
			float y = p[1];
			float z = p[2];
			p[1] = z;
			p[2] = -y;
		}
		for (int k = 0; k < Math.floorMod(ry / 90, 4); k++) {
			float x = p[0];
			float z = p[2];
			p[0] = -z;
			p[2] = x;
		}
	}

	private static Direction rotateDir(Direction d, int rx, int ry) {
		float[] v = {d.getStepX(), d.getStepY(), d.getStepZ()};
		turn(v, rx, ry);
		return Direction.getApproximateNearest(v[0], v[1], v[2]);
	}

	// ------------------------------------------------------------------------------------------- colours

	private static boolean isTranslucent(String path) {
		return path.contains("stained_glass") || path.equals("ice") || path.equals("slime_block") || path.equals("honey_block") || path.equals("tinted_glass")
			|| path.equals("water");
	}

	static int tint(String path, BlockState state) {
		if (path.contains("birch_leaves")) {
			return 0x80A755;
		}
		if (path.contains("spruce_leaves")) {
			return 0x619961;
		}
		// vanilla tints only these leaves with the foliage colour; cherry, azalea and pale oak keep their own
		if (path.equals("oak_leaves") || path.equals("jungle_leaves") || path.equals("acacia_leaves") || path.equals("dark_oak_leaves")
			|| path.equals("mangrove_leaves") || path.equals("vine")) {
			return 0x6AA62E;
		}
		if (path.endsWith("leaves")) {
			return -1;
		}
		if (path.equals("water") || path.contains("water_cauldron")) {
			return 0x3F76E4;
		}
		if (path.equals("lily_pad")) {
			return 0x208030;
		}
		if (path.contains("stem")) {
			return 0xC7B21C;
		}
		if (path.equals("redstone_wire")) {
			return 0xC00000;
		}
		return 0x86B85A;
	}

	// ------------------------------------------------------------------------------------------- stand-ins

	private @Nullable List<Quad> special(BlockState state, Identifier id, String path) {
		switch (path) {
			case "water" -> {
				Texture t = this.assets.texture("minecraft:block/water_still");
				int level = state.hasProperty(BlockStateProperties.LEVEL) ? state.getValue(BlockStateProperties.LEVEL) : 0;
				float top = level == 0 ? 0.875f : Math.max(0.15f, 0.875f - level / 9f);
				return box(0, 0, 0, 1, top, 1, t, t, 0x3F76E4, true);
			}
			case "lava" -> {
				Texture t = this.assets.texture("minecraft:block/lava_still");
				return box(0, 0, 0, 1, 0.875f, 1, t, t, -1, false);
			}
			case "chest", "trapped_chest", "ender_chest" -> {
				String tex = "minecraft:entity/chest/" + (path.equals("chest") ? "normal" : path.equals("trapped_chest") ? "trapped" : "ender");
				Texture top = this.assets.crop(tex, 14, 0, 14, 14);
				Texture side = this.assets.stack(tex + "#side", this.assets.crop(tex, 14, 14, 14, 5), this.assets.crop(tex, 14, 33, 14, 10));
				return box(1 / 16f, 0, 1 / 16f, 15 / 16f, 14 / 16f, 15 / 16f, top, side, -1, false);
			}
			case "decorated_pot" -> {
				Texture side = this.assets.texture("minecraft:entity/decorated_pot/decorated_pot_side");
				Texture base = this.assets.texture("minecraft:entity/decorated_pot/decorated_pot_base");
				List<Quad> q = new ArrayList<>(box(1 / 16f, 0, 1 / 16f, 15 / 16f, 1, 15 / 16f, base, side, -1, false));
				return q;
			}
			case "bell" -> {
				return null;
			}
			default -> {
			}
		}
		if (path.endsWith("_banner")) {
			String colour = path.replace("_wall_banner", "").replace("_banner", "");
			Texture wool = this.assets.texture("minecraft:block/" + colour + "_wool");
			Texture pole = this.assets.texture("minecraft:block/oak_planks");
			List<Quad> q = new ArrayList<>();
			if (path.contains("_wall_")) {
				Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
				float[] b = switch (f) {
					case NORTH -> new float[]{0.05f, -0.9f, 0.86f, 0.95f, 0.85f, 0.94f};
					case SOUTH -> new float[]{0.05f, -0.9f, 0.06f, 0.95f, 0.85f, 0.14f};
					case WEST -> new float[]{0.86f, -0.9f, 0.05f, 0.94f, 0.85f, 0.95f};
					default -> new float[]{0.06f, -0.9f, 0.05f, 0.14f, 0.85f, 0.95f};
				};
				q.addAll(box(b[0], b[1], b[2], b[3], b[4], b[5], wool, wool, -1, false));
			} else {
				q.addAll(box(7 / 16f, 0, 7 / 16f, 9 / 16f, 1.75f, 9 / 16f, pole, pole, -1, false));
				q.addAll(box(0.05f, 0.9f, 0.4f, 0.95f, 1.85f, 0.48f, wool, wool, -1, false));
			}
			return q;
		}
		if (path.endsWith("shulker_box")) {
			Texture t = this.assets.texture("minecraft:block/" + (path.equals("shulker_box") ? "purple" : path.replace("_shulker_box", "")) + "_concrete");
			return box(0, 0, 0, 1, 1, 1, t, t, -1, false);
		}
		if (path.endsWith("_skull") || path.endsWith("_head")) {
			Texture t = this.assets.texture("minecraft:block/bone_block_side");
			return box(0.25f, 0, 0.25f, 0.75f, 0.5f, 0.75f, t, t, -1, false);
		}
		return null;
	}

	private List<Quad> cube(Texture t, int tint) {
		return box(0, 0, 0, 1, 1, 1, t, t, tint, false);
	}

	/** An axis-aligned box with one texture on top and bottom and one on the sides, mapped over the whole face. */
	List<Quad> box(float x0, float y0, float z0, float x1, float y1, float z1, Texture top, Texture side, int tint, boolean translucent) {
		List<Quad> q = new ArrayList<>();
		float[] f = {x0, y0, z0};
		float[] t = {x1, y1, z1};
		for (Direction d : Direction.values()) {
			Texture tex = d.getAxis() == Direction.Axis.Y ? top : side;
			float[] uv = {0, 0, 16, 16};
			Direction cull = boundary(d, f, t) ? d : null;
			q.add(new Quad(corners(d, f, t), uvCorners(uv, 0), tex, tint, d.getStepX(), d.getStepY(), d.getStepZ(), cull, true, translucent,
				boundary(d, f, t)));
		}
		return q;
	}
}
