package dev.voidmc.fray.client;

import dev.voidmc.fray.client.mixin.ModelPartAccess;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla-bound rigs ({@link Rig#vanilla}, 2026-10-22): a clip for a spider or a creeper drives vanilla's own model
 * parts, found by name, <em>on top of</em> vanilla's pose (its walk swing stays underneath). Each bone adds
 * {@code clip × weight + additive} to its part, the same sums {@link dev.voidmc.fray.motion.RigSolver} gives the bake
 * over the rig's rest pose, so hit volumes sit where the parts are drawn. A bone named {@code root} with no vanilla part
 * turns the whole model about its rest offset (the rig file's pivot), applied to the pose stack like the humanoid root.
 */
public final class VanillaBind {
	private record Binding(Rig rig, @Nullable ModelPart[] parts) {
	}

	private static final Map<Model<?>, Binding> BINDINGS = new WeakHashMap<>();
	private static final Quaternionf QUAT = new Quaternionf();

	private VanillaBind() {
	}

	private static Binding bind(Model<?> model, Rig rig) {
		Binding b = BINDINGS.get(model);
		if (b != null && b.rig == rig) {
			return b;
		}
		ModelPart[] parts = new ModelPart[rig.bones()];
		for (int i = 0; i < parts.length; i++) {
			parts[i] = find(model.root(), rig.name(i));
		}
		b = new Binding(rig, parts);
		BINDINGS.put(model, b);
		return b;
	}

	private static @Nullable ModelPart find(ModelPart at, String name) {
		Map<String, ModelPart> children = ((ModelPartAccess) (Object) at).fray$children();
		ModelPart direct = children.get(name);
		if (direct != null) {
			return direct;
		}
		for (ModelPart c : children.values()) {
			ModelPart f = find(c, name);
			if (f != null) {
				return f;
			}
		}
		return null;
	}

	/** Adds the pose to the model's parts (called right after each {@code setupAnim}, so it is added once). */
	public static void apply(Model<?> model, PoseBuffer buf) {
		Binding b = bind(model, buf.rig);
		float[] v = buf.v;
		for (int i = 0; i < b.parts.length && i < buf.bones; i++) {
			ModelPart p = b.parts[i];
			if (p == null) {
				continue;
			}
			int o = i * PoseBuffer.STRIDE;
			float w = v[o + PoseBuffer.W_ROT];
			p.xRot += v[o] * w + v[o + PoseBuffer.ADD_ROT];
			p.yRot += v[o + 1] * w + v[o + PoseBuffer.ADD_ROT + 1];
			p.zRot += v[o + 2] * w + v[o + PoseBuffer.ADD_ROT + 2];
			w = v[o + PoseBuffer.W_POS];
			p.x += v[o + 3] * w + v[o + PoseBuffer.ADD_POS];
			p.y += v[o + 4] * w + v[o + PoseBuffer.ADD_POS + 1];
			p.z += v[o + 5] * w + v[o + PoseBuffer.ADD_POS + 2];
			w = v[o + PoseBuffer.W_SCALE];
			if (w > 0) {
				p.xScale += v[o + 6] * w;
				p.yScale += v[o + 7] * w;
				p.zScale += v[o + 8] * w;
			}
		}
	}

	/**
	 * The virtual root's transform (model space, blocks): {@code translate(pivot + pos) · rotate · translate(-pivot)},
	 * which is where the rig file's children of {@code root} (offsets relative to the pivot) end up. False when the rig has
	 * no virtual root or it is at rest.
	 */
	public static boolean root(PoseBuffer buf, Matrix4f out) {
		Rig rig = buf.rig;
		if (rig.bones() == 0 || !rig.name(0).equals("root") || buf.bones == 0) {
			return false;
		}
		float[] v = buf.v;
		float wr = v[PoseBuffer.W_ROT], wp = v[PoseBuffer.W_POS];
		float rx = v[0] * wr + v[PoseBuffer.ADD_ROT], ry = v[1] * wr + v[PoseBuffer.ADD_ROT + 1], rz = v[2] * wr + v[PoseBuffer.ADD_ROT + 2];
		float px = v[3] * wp + v[PoseBuffer.ADD_POS], py = v[4] * wp + v[PoseBuffer.ADD_POS + 1], pz = v[5] * wp + v[PoseBuffer.ADD_POS + 2];
		if (rx == 0 && ry == 0 && rz == 0 && px == 0 && py == 0 && pz == 0) {
			return false;
		}
		PartPose rest = rig.rest[0];
		out.identity().translate((rest.x + px) / 16F, (rest.y + py) / 16F, (rest.z + pz) / 16F);
		out.rotate(QUAT.rotationZYX(rz, ry, rx));
		out.translate(-rest.x / 16F, -rest.y / 16F, -rest.z / 16F);
		return true;
	}
}
