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
			PoseBuffer vb = pose(state);
			if (vb != null && ClientMotion.enabled && vb.rig.vanilla) {
				// A vanilla-bound rig (spider, creeper): the clip on top of vanilla's own pose.
				long t0 = System.nanoTime();
				VanillaBind.apply(model, vb);
				FrayStats.applyNanos += System.nanoTime() - t0;
				FrayStats.applies++;
			}
			return;
		}
		PoseBuffer buf = pose(state);
		if (buf == null || !ClientMotion.enabled || !buf.rig.humanoid) {
			// No clip: straight elbows, vanilla's boxes (models are shared between entities).
			Elbows.bend(humanoid.rightArm, false);
			Elbows.bend(humanoid.leftArm, false);
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
		// The elbows: the forearm's own rotation, relative to its arm (and the sleeve's forearm with it).
		bend(m.rightArm, buf, rest[HumanoidRig.RIGHT_FOREARM], HumanoidRig.RIGHT_FOREARM);
		bend(m.leftArm, buf, rest[HumanoidRig.LEFT_FOREARM], HumanoidRig.LEFT_FOREARM);
	}

	private static final float[] LOCAL = new float[3];

	private static void bend(ModelPart arm, PoseBuffer buf, PartPose rest, int bone) {
		java.util.List<ModelPart> fs = Elbows.forearms(arm);
		if (fs.isEmpty()) {
			return;
		}
		HumanoidSolver.forearmLocal(buf, rest, bone, LOCAL);
		Elbows.bend(arm, Math.abs(LOCAL[0]) + Math.abs(LOCAL[1]) + Math.abs(LOCAL[2]) > 1e-4F);
		for (ModelPart f : fs) {
			f.xRot = LOCAL[0];
			f.yRot = LOCAL[1];
			f.zRot = LOCAL[2];
		}
	}

	/**
	 * The held item rides the forearm: right after vanilla's {@code translateToHand} (the arm), the forearm's turn about
	 * the elbow (in the arm's frame), so vanilla's hand offsets that follow still land in the hand.
	 */
	public static void forearm(Object parentModel, boolean left, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		if (!(parentModel instanceof HumanoidModel<?> h)) {
			return;
		}
		java.util.List<ModelPart> fs = Elbows.forearms(left ? h.leftArm : h.rightArm);
		if (fs.isEmpty()) {
			return;
		}
		ModelPart f = fs.getFirst();
		if (f.xRot == 0 && f.yRot == 0 && f.zRot == 0) {
			return;
		}
		poseStack.translate(f.x / 16F, f.y / 16F, f.z / 16F);
		poseStack.rotate(new org.joml.Quaternionf().rotationZYX(f.zRot, f.yRot, f.xRot));
		poseStack.translate(-f.x / 16F, -f.y / 16F, -f.z / 16F);
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

	/**
	 * The {@code item_r} / {@code item_l} bone (ANIMATION.md §4.1): rotation about the grip (about 10 px down the arm, in the
	 * arm's frame) and an offset in pixels, applied right after vanilla's {@code translateToHand}.
	 */
	public static void itemBone(Object state, boolean left, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		PoseBuffer buf = pose(state);
		if (buf == null || !ClientMotion.enabled || !buf.rig.humanoid) {
			return;
		}
		itemBone(buf, left, poseStack);
	}

	public static void itemBone(PoseBuffer buf, boolean left, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		int o = (left ? HumanoidRig.ITEM_L : HumanoidRig.ITEM_R) * PoseBuffer.STRIDE;
		float[] v = buf.v;
		float wr = v[o + PoseBuffer.W_ROT], wp = v[o + PoseBuffer.W_POS];
		float rx = v[o] * wr + v[o + PoseBuffer.ADD_ROT], ry = v[o + 1] * wr + v[o + PoseBuffer.ADD_ROT + 1], rz = v[o + 2] * wr + v[o + PoseBuffer.ADD_ROT + 2];
		float px = v[o + 3] * wp + v[o + PoseBuffer.ADD_POS], py = v[o + 4] * wp + v[o + PoseBuffer.ADD_POS + 1], pz = v[o + 5] * wp + v[o + PoseBuffer.ADD_POS + 2];
		if (rx == 0 && ry == 0 && rz == 0 && px == 0 && py == 0 && pz == 0) {
			return;
		}
		float gx = (left ? 1 : -1) / 16F, gy = 10 / 16F;
		poseStack.translate(gx + px / 16F, gy + py / 16F, pz / 16F);
		poseStack.rotate(new org.joml.Quaternionf().rotationZYX(rz, ry, rx));
		poseStack.translate(-gx, -gy, 0);
	}

	/** The root transform for this state's pose, or null (applied to the PoseStack after setupRotations). */
	public static boolean root(Object state, org.joml.Matrix4f out) {
		PoseBuffer buf = pose(state);
		if (buf != null && ClientMotion.enabled && buf.rig.vanilla) {
			return VanillaBind.root(buf, out);
		}
		return buf != null && ClientMotion.enabled && buf.rig.humanoid && SOLVER.rootTransform(buf, out);
	}
}
