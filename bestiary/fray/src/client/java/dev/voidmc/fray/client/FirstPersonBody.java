package dev.voidmc.fray.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.voidmc.fray.client.compat.IrisCompat;
import dev.voidmc.fray.motion.Channel;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/**
 * First person (ANIMATION.md §7.4): while the local player runs an action whose clip has an {@code fp} block, the
 * player's own posed arms and held item are drawn from the camera <em>inside</em>
 * {@code FirstPersonHandsAndItemsRenderer.submitHandsWithItems} (verified in 26.3: {@code GameRenderer.renderItemInHand}
 * calls it with its own submit storage and a hand render pass; Iris's {@code HandRenderer} calls the same method for its
 * solid and translucent hand passes). The same clip and the same solver as third person: the arms are the third-person
 * arms seen from the eye, moved by the clip's framing offset so the arc reads on screen.
 */
public final class FirstPersonBody {
	private static final HumanoidSolver SOLVER = new HumanoidSolver();
	private static final PoseBuffer FP_BUF = new PoseBuffer();
	private static final float[] SCRATCH = new float[3];
	/** Standing eye height (blocks) over the player's model scale: where the camera sits in model space. */
	private static final float EYE_HEIGHT = 1.62F;

	private FirstPersonBody() {
	}

	/** A model whose root is one arm, posed at draw time from the state it is submitted with. */
	private static final class ArmModel extends Model<PartPose> {
		ArmModel(ModelPart arm, RenderType type) {
			super(arm, t -> type);
		}

		@Override
		public void setupAnim(PartPose pose) {
			PoseApply.write(pose, this.root());
		}
	}

	/** Returns true when Fray drew (or deliberately skipped, in Iris's other hand pass) the hands: vanilla's are cancelled. */
	public static boolean draw(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state) {
		if (!ClientMotion.enabled) {
			return false;
		}
		AvatarRenderState avatar = playerState.avatarRenderState;
		Minecraft mc = Minecraft.getInstance();
		if (avatar == null || mc.player == null) {
			return false;
		}
		PoseBuffer buf = PoseApply.pose(avatar);
		Animator animator = ClientMotion.get(mc.player.getId());
		Clip clip = animator == null ? null : animator.clip(Animator.ACTION);
		if (buf == null || clip == null || clip.fp == null || !buf.rig.humanoid) {
			return false;
		}
		Clip.FirstPerson fp = clip.fp;
		if (fp.hideArms()) {
			return true;
		}
		boolean anyBone = false;
		for (int b = 0; b < HumanoidRig.BONES; b++) {
			anyBone |= fp.rotation() != null && fp.rotation()[b] != null || fp.position() != null && fp.position()[b] != null;
		}
		if (!anyBone && "fray:default".equals(fp.framing())) {
			// A clip that only moves the camera in first person (a dodge): vanilla's hands.
			return false;
		}
		if (IrisCompat.skipHand(state.mainHandItem)) {
			return true;
		}
		FrayStats.fpDraws++;
		AvatarRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
		PlayerModel model = renderer.getModel();
		PartPose[] rest = HumanoidRig.restPose();
		PartPose[] parts = HumanoidRig.restPose();
		// The clip's first-person curves replace its third-person ones for the bones they name.
		FP_BUF.copyFrom(buf);
		float t = clip.localTime((float) (animator.actionElapsed(ClientMotion.now(partialTicks)) / 20.0));
		for (int b = 0; b < HumanoidRig.BONES; b++) {
			int o = b * PoseBuffer.STRIDE;
			Channel r = fp.rotation() == null ? null : fp.rotation()[b];
			if (r != null) {
				r.sample(t, SCRATCH, 0);
				FP_BUF.v[o] = (float) Math.toRadians(SCRATCH[0]);
				FP_BUF.v[o + 1] = (float) Math.toRadians(SCRATCH[1]);
				FP_BUF.v[o + 2] = (float) Math.toRadians(SCRATCH[2]);
			}
			Channel p = fp.position() == null ? null : fp.position()[b];
			if (p != null) {
				p.sample(t, SCRATCH, 0);
				FP_BUF.v[o + 3] = SCRATCH[0];
				FP_BUF.v[o + 4] = -SCRATCH[1];
				FP_BUF.v[o + 5] = SCRATCH[2];
				FP_BUF.v[o + PoseBuffer.W_POS] = Math.max(FP_BUF.v[o + PoseBuffer.W_POS], FP_BUF.v[o + PoseBuffer.W_ROT]);
			}
		}
		// First person has no vanilla pose underneath: the clip over the rest pose.
		SOLVER.solve(parts, rest, FP_BUF);

		poseStack.pushPose();
		float[] off = fp.offset();
		poseStack.translate(off[0], off[1], off[2]);
		poseStack.rotateDegrees(Axis.XP, fp.pitch());
		float s = HumanoidRig.PLAYER_SCALE;
		poseStack.scale(-s, -s, s);
		// Model space with the eye at the origin: the eye is EYE_HEIGHT / s above the feet, the model origin 1.501 above.
		float eyeModelY = HumanoidRig.MODEL_Y_OFFSET - EYE_HEIGHT / s;
		poseStack.translate(0, -eyeModelY, 0);

		RenderType skin = RenderTypes.entityTranslucent(avatar.skin.body().texturePath());
		model.rightSleeve.visible = avatar.showRightSleeve;
		model.leftSleeve.visible = avatar.showLeftSleeve;
		int light = avatar.lightCoords;
		submitArm(poseStack, collector, model.rightArm, parts[HumanoidRig.RIGHT_ARM], skin, light);
		submitArm(poseStack, collector, model.leftArm, parts[HumanoidRig.LEFT_ARM], skin, light);
		submitItem(poseStack, collector, parts[HumanoidRig.RIGHT_ARM], avatar.rightHandItemState, false, light);
		submitItem(poseStack, collector, parts[HumanoidRig.LEFT_ARM], avatar.leftHandItemState, true, light);
		poseStack.popPose();
		return true;
	}

	/**
	 * The first-person camera channels of the local player's action (ANIMATION.md §7.4: kicks of a few degrees, a dodge's
	 * dip), applied to the level's view after vanilla's hurt bob. Position in blocks (Y up), rotation in degrees
	 * (x pitch, y yaw, z roll).
	 */
	public static void cameraKick(PoseStack view, float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (!ClientMotion.enabled || mc.player == null || mc.getCameraEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		Animator animator = ClientMotion.get(mc.player.getId());
		Clip clip = animator == null ? null : animator.clip(Animator.ACTION);
		if (clip == null || clip.fp == null || (clip.fp.camera() == null && clip.fp.cameraPos() == null)) {
			return;
		}
		double e = animator.actionElapsed(ClientMotion.now(partialTicks));
		if (Double.isNaN(e) || e < 0 || e > clip.length * 20) {
			return;
		}
		float t = clip.localTime((float) (e / 20.0));
		if (clip.fp.cameraPos() != null) {
			clip.fp.cameraPos().sample(t, SCRATCH, 0);
			view.translate(-SCRATCH[0], -SCRATCH[1], -SCRATCH[2]);
		}
		if (clip.fp.camera() != null) {
			clip.fp.camera().sample(t, SCRATCH, 0);
			view.rotateDegrees(Axis.ZP, SCRATCH[2]);
			view.rotateDegrees(Axis.XP, SCRATCH[0]);
			view.rotateDegrees(Axis.YP, SCRATCH[1]);
		}
	}

	private static void submitArm(PoseStack poseStack, SubmitNodeCollector collector, ModelPart arm, PartPose pose, RenderType type, int light) {
		PartPose copy = new PartPose().set(pose);
		collector.submitModel(new ArmModel(arm, type), copy, poseStack, type, light, OverlayTexture.NO_OVERLAY, -1, null, 0);
	}

	/** {@code ItemInHandLayer.submitArmWithItem}'s transform on our posed arm. */
	private static void submitItem(PoseStack poseStack, SubmitNodeCollector collector, PartPose arm, ItemStackRenderState item, boolean left, int light) {
		if (item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(arm.x / 16F, arm.y / 16F, arm.z / 16F);
		poseStack.rotate(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
		poseStack.rotateDegrees(Axis.XP, -90.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.translate((left ? -1 : 1) / 16.0F, 2 / 16.0F, -10 / 16.0F);
		item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/** Which arm is the main one (the prototype draws both arms either way). */
	static HumanoidArm mainArm(AvatarRenderState s) {
		return s.mainArm;
	}
}
