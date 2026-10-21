package dev.voidmc.fray.client;

import dev.voidmc.fray.client.compat.IrisCompat;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;

/**
 * Copies a sampled pose onto vanilla model parts, right after a {@code setupAnim} (ANIMATION.md §7.3 step 3). Called
 * after the one in {@code LivingEntityRenderer.submit} (for layers that read part positions, such as held items) and
 * after the one in {@code ModelFeatureRenderer.prepareModel} (each drawn body and armour model, in every pass: the
 * main pass, Iris's shadow pass). Because the pose comes from the render state only, every call gives the same pose.
 *
 * <p>The prototype binds the humanoid rig to any {@code HumanoidModel} (zombies and their kin, players, armour).
 */
public final class PoseApply {
	private static final HumanoidSolver SOLVER = new HumanoidSolver();
	private static final PartPose[] UNDER = HumanoidRig.restPose();
	private static final ModelPart[] PARTS = new ModelPart[HumanoidRig.LAST_PART - HumanoidRig.FIRST_PART + 1];

	/** Per model class: its rest pose (looked up once; every instance of a class shares its part layout). */
	private static final Map<Class<?>, PartPose[]> REST = new java.util.concurrent.ConcurrentHashMap<>();

	private PoseApply() {
	}

	public static @Nullable PoseBuffer pose(Object state) {
		return state instanceof FabricRenderState frs ? frs.getData(ClientMotion.POSE) : null;
	}

	/** Applies the state's pose (if any) to the model (if it is one the rig knows). */
	public static void apply(Model<?> model, Object state) {
		if (!(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		PoseBuffer buf = pose(state);
		if (buf == null || !ClientMotion.enabled || !buf.rig.humanoid) {
			return;
		}
		long t0 = System.nanoTime();
		applyHumanoid(humanoid, buf);
		FrayStats.applyNanos += System.nanoTime() - t0;
		FrayStats.applies++;
		if (IrisCompat.inShadowPass()) {
			FrayStats.shadowApplies++;
		}
	}

	public static void applyHumanoid(HumanoidModel<?> m, PoseBuffer buf) {
		PartPose[] rest = REST.computeIfAbsent(m.getClass(), k -> rest(m));
		ModelPart[] parts = PARTS;
		parts[0] = m.head;
		parts[1] = m.body;
		parts[2] = m.rightArm;
		parts[3] = m.leftArm;
		parts[4] = m.rightLeg;
		parts[5] = m.leftLeg;
		for (int b = HumanoidRig.FIRST_PART; b <= HumanoidRig.LAST_PART; b++) {
			read(parts[b - HumanoidRig.FIRST_PART], UNDER[b]);
		}
		SOLVER.solve(UNDER, rest, buf);
		for (int b = HumanoidRig.FIRST_PART; b <= HumanoidRig.LAST_PART; b++) {
			write(UNDER[b], parts[b - HumanoidRig.FIRST_PART]);
		}
	}

	/** The model's parts in bone order HEAD..LEFT_LEG. */
	public static ModelPart[] parts(HumanoidModel<?> m) {
		return new ModelPart[] {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
	}

	private static PartPose[] rest(HumanoidModel<?> m) {
		PartPose[] rest = HumanoidRig.restPose();
		ModelPart[] parts = parts(m);
		for (int b = HumanoidRig.FIRST_PART; b <= HumanoidRig.LAST_PART; b++) {
			net.minecraft.client.model.geom.PartPose p = parts[b - HumanoidRig.FIRST_PART].getInitialPose();
			rest[b].x = p.x();
			rest[b].y = p.y();
			rest[b].z = p.z();
			rest[b].xRot = p.xRot();
			rest[b].yRot = p.yRot();
			rest[b].zRot = p.zRot();
			rest[b].xScale = p.xScale();
			rest[b].yScale = p.yScale();
			rest[b].zScale = p.zScale();
		}
		return rest;
	}

	public static void read(ModelPart p, PartPose out) {
		out.x = p.x;
		out.y = p.y;
		out.z = p.z;
		out.xRot = p.xRot;
		out.yRot = p.yRot;
		out.zRot = p.zRot;
		out.xScale = p.xScale;
		out.yScale = p.yScale;
		out.zScale = p.zScale;
	}

	public static void write(PartPose in, ModelPart p) {
		p.x = in.x;
		p.y = in.y;
		p.z = in.z;
		p.xRot = in.xRot;
		p.yRot = in.yRot;
		p.zRot = in.zRot;
		p.xScale = in.xScale;
		p.yScale = in.yScale;
		p.zScale = in.zScale;
	}

	/** The root transform for this state's pose, or null (applied to the PoseStack after setupRotations). */
	public static boolean root(Object state, org.joml.Matrix4f out) {
		PoseBuffer buf = pose(state);
		return buf != null && ClientMotion.enabled && buf.rig.humanoid && SOLVER.rootTransform(buf, out);
	}
}
