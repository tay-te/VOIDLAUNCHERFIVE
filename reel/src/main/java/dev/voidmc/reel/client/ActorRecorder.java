package dev.voidmc.reel.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.reel.Reel;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The people (and any other living thing) in a filmed movie, for Blender (tools/blender/reel_actors.py): where every
 * part of every model was on every frame, each model as its textured cubes, each skin as a picture. Blender rebuilds
 * them and plays back exactly what the game drew, its own animation baked frame by frame: walking, looking, Hearth's
 * work poses, a mob's attack.
 *
 * <p>On for one movie with the render request's actors=RADIUS (blocks from the camera). It listens on the first render
 * of each filmed frame (later samples only blur it): EntityRenderer says which entity each render state is for, and
 * LivingEntityRenderer hands over the entity's placed root and its model just before the model is queued. The model is
 * posed here from that same render state (setupAnim is a function of it, as when it is drawn) and walked part by part.
 * Writes actors.json beside camera.json, skins in actors/.
 *
 * <p>actors.json: {"fps", "frames", "models": {key: {part path: [[x, y, z, u, v] x4 per face, ...]}}, "actors": [{"id",
 * "type", "name", "model", "skin", "frames": {frame: {part path: [3x3 column-major, then translation]}}}]}. Part
 * geometry is in its part's space in blocks; a part's matrix takes that to the world (Minecraft's axes and blocks).
 */
public final class ActorRecorder {
	private static double radius;
	private static final Map<EntityRenderState, Entity> OWNERS = new IdentityHashMap<>();
	private static final Map<Integer, Actor> ACTORS = new LinkedHashMap<>();
	private static final Map<String, Map<String, float[][]>> MODELS = new LinkedHashMap<>();
	private static final Map<EntityModel<?>, String> MODEL_KEYS = new IdentityHashMap<>();
	private static final Map<Identifier, String> SKINS = new LinkedHashMap<>();
	private static Path skinDir;
	private static int lastFrame = -1;

	private ActorRecorder() {
	}

	private static final class Actor {
		final int id;
		final String type;
		final String name;
		String model;
		String skin;
		final TreeMap<Integer, Map<String, float[]>> frames = new TreeMap<>();

		Actor(Entity entity) {
			this.id = entity.getId();
			this.type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			this.name = entity.getName().getString();
		}
	}

	/** Records the next movie's actors within this many blocks of the camera (0: none). */
	public static void arm(double blocks) {
		radius = blocks;
		OWNERS.clear();
		ACTORS.clear();
		MODELS.clear();
		MODEL_KEYS.clear();
		SKINS.clear();
		lastFrame = -1;
		skinDir = null;
	}

	public static boolean armed() {
		return radius > 0;
	}

	private static int frame() {
		if (radius <= 0) {
			return -1;
		}
		int f = Director.get().recordingFrame();
		if (f != lastFrame) {
			// a new pass: last pass's render states are gone
			OWNERS.clear();
			lastFrame = f;
		}
		return f;
	}

	/** From EntityRenderer: the render state just made for this entity. */
	public static void bind(EntityRenderState state, Entity entity) {
		if (frame() >= 0) {
			OWNERS.put(state, entity);
		}
	}

	/**
	 * From LivingEntityRenderer.submit with the pose stack at the model's root (relative to the camera, as entities
	 * are drawn): the model posed for this state and every part's matrix taken.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void pose(LivingEntityRenderer renderer, EntityModel model, LivingEntityRenderState state, PoseStack poseStack, Vec3 camera) {
		int frame = frame();
		Entity entity = frame >= 0 ? OWNERS.get(state) : null;
		if (entity == null || entity.distanceToSqr(camera) > radius * radius) {
			return;
		}
		Actor actor = ACTORS.computeIfAbsent(entity.getId(), id -> new Actor(entity));
		model.setupAnim(state);
		actor.model = MODEL_KEYS.computeIfAbsent(model, m -> {
			String key = m.getClass().getSimpleName() + "-" + MODEL_KEYS.size();
			MODELS.put(key, geometry(m.root()));
			return key;
		});
		Identifier texture = renderer.getTextureLocation(state);
		if (texture != null) {
			actor.skin = SKINS.computeIfAbsent(texture, ActorRecorder::saveSkin);
		}
		Map<String, float[]> parts = new LinkedHashMap<>();
		PoseStack walk = new PoseStack();
		walk.last().pose().set(poseStack.last().pose());
		model.root().visit(walk, (pose, path, index, cube) -> {
			if (index == 0) {
				Matrix4f m = pose.pose();
				parts.put(path, new float[] {m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22(),
					(float) (m.m30() + camera.x), (float) (m.m31() + camera.y), (float) (m.m32() + camera.z)});
			}
		});
		actor.frames.put(frame, parts);
	}

	/** Every part's faces in its own space, in blocks: four corners each with the skin's u and v. */
	private static Map<String, float[][]> geometry(ModelPart root) {
		Map<String, float[][]> parts = new LinkedHashMap<>();
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			float[][] old = parts.getOrDefault(path, new float[0][]);
			float[][] faces = new float[old.length + cube.polygons.length][];
			System.arraycopy(old, 0, faces, 0, old.length);
			for (int i = 0; i < cube.polygons.length; i++) {
				ModelPart.Vertex[] v = cube.polygons[i].vertices();
				float[] face = new float[v.length * 5];
				for (int k = 0; k < v.length; k++) {
					face[k * 5] = v[k].worldX();
					face[k * 5 + 1] = v[k].worldY();
					face[k * 5 + 2] = v[k].worldZ();
					face[k * 5 + 3] = v[k].u();
					face[k * 5 + 4] = v[k].v();
				}
				faces[old.length + i] = face;
			}
			parts.put(path, faces);
		});
		return parts;
	}

	/** The skin as a PNG in actors/: a made texture (Hearth's composed skins) from its pixels, else the game's file. */
	private static String saveSkin(Identifier id) {
		String file = (id.getNamespace() + "_" + id.getPath()).replaceAll("[^A-Za-z0-9_.-]", "_");
		if (!file.endsWith(".png")) {
			file += ".png";
		}
		try {
			if (skinDir == null) {
				skinDir = Files.createTempDirectory("reel-actors");
			}
			Path to = skinDir.resolve(file);
			AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
			if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
				dynamic.getPixels().writeToFile(to);
			} else {
				var resource = Minecraft.getInstance().getResourceManager().getResource(id);
				if (resource.isEmpty()) {
					return null;
				}
				try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
					image.writeToFile(to);
				}
			}
		} catch (IOException e) {
			Reel.LOG.warn("[reel] could not save the skin {}: {}", id, e.toString());
			return null;
		}
		return "actors/" + file;
	}

	/** Writes what was recorded into the movie's folder and switches off. */
	public static void write(Path out, int fps, int frames) {
		if (radius <= 0) {
			return;
		}
		radius = 0;
		try {
			Path dir = out.resolve("actors");
			Files.createDirectories(dir);
			if (skinDir != null) {
				try (var files = Files.list(skinDir)) {
					for (Path f : files.toList()) {
						Files.move(f, dir.resolve(f.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			try (Writer w = Files.newBufferedWriter(out.resolve("actors.json"), StandardCharsets.UTF_8)) {
				w.write("{\"fps\":" + fps + ",\"frames\":" + frames + ",\"models\":{");
				boolean first = true;
				for (var m : MODELS.entrySet()) {
					w.write((first ? "" : ",") + "\"" + m.getKey() + "\":{");
					first = false;
					boolean firstPart = true;
					for (var p : m.getValue().entrySet()) {
						w.write((firstPart ? "" : ",") + "\"" + Report.escape(p.getKey()) + "\":[");
						firstPart = false;
						for (int i = 0; i < p.getValue().length; i++) {
							w.write((i == 0 ? "" : ",") + numbers(p.getValue()[i]));
						}
						w.write("]");
					}
					w.write("}");
				}
				w.write("},\"actors\":[");
				first = true;
				for (Actor a : ACTORS.values()) {
					w.write((first ? "" : ",") + "{\"id\":" + a.id + ",\"type\":\"" + a.type + "\",\"name\":\"" + Report.escape(a.name)
						+ "\",\"model\":\"" + a.model + "\",\"skin\":" + (a.skin == null ? "null" : "\"" + a.skin + "\"") + ",\"frames\":{");
					first = false;
					boolean firstFrame = true;
					for (var f : a.frames.entrySet()) {
						w.write((firstFrame ? "" : ",") + "\"" + f.getKey() + "\":{");
						firstFrame = false;
						boolean firstPart = true;
						for (var p : f.getValue().entrySet()) {
							w.write((firstPart ? "" : ",") + "\"" + Report.escape(p.getKey()) + "\":" + numbers(p.getValue()));
							firstPart = false;
						}
						w.write("}");
					}
					w.write("}}");
				}
				w.write("]}");
			}
			Reel.LOG.info("[reel] recorded {} actors in {} models", ACTORS.size(), MODELS.size());
		} catch (IOException e) {
			Reel.LOG.error("[reel] could not write the actors", e);
		}
		OWNERS.clear();
		ACTORS.clear();
	}

	private static String numbers(float[] values) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < values.length; i++) {
			b.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.5f", values[i]));
		}
		return b.append(']').toString();
	}
}
