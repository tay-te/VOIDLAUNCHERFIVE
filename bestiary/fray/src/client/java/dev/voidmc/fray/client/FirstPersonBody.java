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
import dev.voidmc.fray.client.mixin.ItemLayerAccess;
import dev.voidmc.fray.client.mixin.ItemStackRenderStateAccess;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

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
		if (buf == null || clip == null || !drawsArms(clip) || !buf.rig.humanoid) {
			return clip != null && clip.fp != null && clip.fp.hideArms() && buf != null;
		}
		Clip.FirstPerson fp = clip.fp;
		if (IrisCompat.skipHand(state.mainHandItem)) {
			return true;
		}
		double now = ClientMotion.now(partialTicks);
		// The crossfade with vanilla's held item (no jump at the start or the end of a swing): the clip's own weight, or
		// from the previous first-person clip when one string cut blends into the next.
		Clip prev = animator.previousAction(now);
		boolean fromClip = prev != null && drawsArms(prev);
		float weight = fromClip ? 1 : animator.actionWeight(now);
		if (weight <= 0.002F) {
			return false;
		}
		float a = fromClip ? animator.actionBlendIn(now) : 1;
		FrayStats.fpDraws++;
		AvatarRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
		PlayerModel model = renderer.getModel();
		PartPose[] rest = HumanoidRig.restPose();
		PartPose[] parts = HumanoidRig.restPose();
		// The clip's first-person curves replace its third-person ones for the bones they name.
		FP_BUF.copyFrom(buf);
		float t = clip.localTime((float) (animator.actionElapsed(now) / 20.0));
		float tp = fromClip ? prev.localTime((float) (animator.previousElapsed(now) / 20.0)) : 0;
		for (int b = 0; b < HumanoidRig.BONES; b++) {
			int o = b * PoseBuffer.STRIDE;
			Channel r = fp.rotation() == null ? null : fp.rotation()[b];
			Channel rp = fromClip && prev.fp.rotation() != null ? prev.fp.rotation()[b] : null;
			if (r != null || rp != null) {
				for (int i = 0; i < 3; i++) {
					SCRATCH2[i] = (float) Math.toDegrees(FP_BUF.v[o + i]);
					SCRATCH[i] = SCRATCH2[i];
				}
				if (r != null) {
					r.sample(t, SCRATCH, 0);
				}
				if (rp != null) {
					rp.sample(tp, SCRATCH2, 0);
				}
				for (int i = 0; i < 3; i++) {
					FP_BUF.v[o + i] = (float) Math.toRadians(fromClip ? SCRATCH2[i] + (SCRATCH[i] - SCRATCH2[i]) * a : SCRATCH[i]);
				}
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
		// First person has no vanilla pose underneath: the clip over the rest pose; the torso only by the fp block's own curve.
		dev.voidmc.fray.motion.bake.FirstPersonFrame.torso(FP_BUF, fp);
		SOLVER.solve(parts, rest, FP_BUF);

		// Vanilla's held item, as FirstPersonHandsAndItemsRenderer would place it this frame (sway and equip included).
		boolean right = avatar.mainArm == HumanoidArm.RIGHT;
		boolean blendItem = weight < 0.998F && right && !state.mainHandItem.isEmpty();
		if (blendItem) {
			VANILLA.set(poseStack.last().pose());
			VANILLA.rotate(Axis.XP.rotationDegrees((state.viewXRot - state.xBob) * 0.1F));
			VANILLA.rotate(Axis.YP.rotationDegrees((state.viewYRot - state.yBob) * 0.1F));
			float inverseArmHeight = state.mainHandSwapScale * (1.0F - net.minecraft.util.Mth.lerp(partialTicks, state.oldMainHandHeight, state.mainHandHeight));
			VANILLA.translate(0.56F, -0.52F + inverseArmHeight * -0.6F, -0.72F);
		}

		poseStack.pushPose();
		float[] off = fp.offset();
		float pitch = fp.pitch();
		if (fromClip) {
			float[] po = prev.fp.offset();
			off = new float[] {po[0] + (off[0] - po[0]) * a, po[1] + (off[1] - po[1]) * a, po[2] + (off[2] - po[2]) * a};
			pitch = prev.fp.pitch() + (pitch - prev.fp.pitch()) * a;
		}
		poseStack.translate(off[0], off[1], off[2]);
		poseStack.rotateDegrees(Axis.XP, pitch);
		float s = HumanoidRig.PLAYER_SCALE;
		poseStack.scale(-s, -s, s);
		// Model space with the eye at the origin: the eye is EYE_HEIGHT / s above the feet, the model origin 1.501 above.
		float eyeModelY = HumanoidRig.MODEL_Y_OFFSET - EYE_HEIGHT / s;
		poseStack.translate(0, -eyeModelY, 0);

		RenderType skin = RenderTypes.entityTranslucent(avatar.skin.body().texturePath());
		model.rightSleeve.visible = avatar.showRightSleeve;
		model.leftSleeve.visible = avatar.showLeftSleeve;
		int light = avatar.lightCoords;
		// The arm fades in with the clip (vanilla shows no arm with an item in hand).
		int color = weight >= 0.998F ? -1 : ((int) (weight * 255) << 24) | 0xFFFFFF;
		submitArm(poseStack, collector, model.rightArm, parts[HumanoidRig.RIGHT_ARM], skin, light, color);
		if (blendItem) {
			submitBlendedItem(poseStack, collector, parts[HumanoidRig.RIGHT_ARM], avatar.rightHandItemState, state.mainHandRenderState, light, weight);
		} else {
			submitItem(poseStack, collector, parts[HumanoidRig.RIGHT_ARM], avatar.rightHandItemState, false, light, FP_BUF);
		}
		if (fp.bothArms()) {
			submitArm(poseStack, collector, model.leftArm, parts[HumanoidRig.LEFT_ARM], skin, light, color);
			submitItem(poseStack, collector, parts[HumanoidRig.LEFT_ARM], avatar.leftHandItemState, true, light, FP_BUF);
		}
		poseStack.popPose();
		return true;
	}

	/** A clip whose first person draws Fray's arms (not a camera-only clip such as a dodge, nor one hiding the arms). */
	private static boolean drawsArms(Clip clip) {
		Clip.FirstPerson fp = clip.fp;
		if (fp == null || fp.hideArms()) {
			return false;
		}
		for (int b = 0; b < HumanoidRig.BONES; b++) {
			if (fp.rotation() != null && fp.rotation()[b] != null || fp.position() != null && fp.position()[b] != null) {
				return true;
			}
		}
		return !"fray:default".equals(fp.framing());
	}

	private static final Matrix4f VANILLA = new Matrix4f();
	private static final float[] SCRATCH2 = new float[3];

	/**
	 * The held item between vanilla's first-person placement and the clip's hand, by {@code w} (0 vanilla, 1 the clip):
	 * the two final item matrices (each with its own display transform) are blended (translation, rotation, scale) and
	 * the first-person item drawn there.
	 */
	private static void submitBlendedItem(PoseStack poseStack, SubmitNodeCollector collector, PartPose arm, ItemStackRenderState thirdPerson,
		ItemStackRenderState firstPerson, int light, float w) {
		Matrix4f tf = layerTransform(thirdPerson);
		Matrix4f tv = layerTransform(firstPerson);
		if (tf == null || tv == null) {
			submitItem(poseStack, collector, arm, thirdPerson, false, light, FP_BUF);
			return;
		}
		poseStack.pushPose();
		itemChain(poseStack, arm, false, FP_BUF);
		Matrix4f ff = new Matrix4f(poseStack.last().pose()).mul(tf);
		poseStack.popPose();
		Matrix4f fv = new Matrix4f(VANILLA).mul(tv);
		Matrix4f b = blend(fv, ff, w);
		poseStack.pushPose();
		poseStack.last().setIdentity();
		poseStack.last().mulPose(b.mul(tv.invert(new Matrix4f())));
		firstPerson.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/** Layer 0's display transform of an item state, as a matrix (null without layers). */
	private static @Nullable Matrix4f layerTransform(ItemStackRenderState item) {
		ItemStackRenderStateAccess acc = (ItemStackRenderStateAccess) (Object) item;
		if (acc.fray$layerCount() <= 0) {
			return null;
		}
		ItemLayerAccess layer = (ItemLayerAccess) (Object) acc.fray$layers()[0];
		PoseStack tmp = new PoseStack();
		tmp.last().setIdentity();
		layer.fray$itemTransform().apply(acc.fray$displayContext().leftHand(), tmp.last());
		tmp.last().mulPose(layer.fray$localTransform());
		return new Matrix4f(tmp.last().pose());
	}

	/** Translation, rotation and scale of two transforms blended (rotation by slerp). */
	static Matrix4f blend(Matrix4f a, Matrix4f b, float w) {
		Vector3f ta = a.getTranslation(new Vector3f()), tb = b.getTranslation(new Vector3f());
		Vector3f sa = a.getScale(new Vector3f()), sb = b.getScale(new Vector3f());
		Quaternionf qa = a.getUnnormalizedRotation(new Quaternionf()).normalize(), qb = b.getUnnormalizedRotation(new Quaternionf()).normalize();
		return new Matrix4f().translationRotateScale(ta.lerp(tb, w), qa.slerp(qb, w), sa.lerp(sb, w));
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

	private static void submitArm(PoseStack poseStack, SubmitNodeCollector collector, ModelPart arm, PartPose pose, RenderType type, int light, int color) {
		PartPose copy = new PartPose().set(pose);
		collector.submitModel(new ArmModel(arm, type), copy, poseStack, type, light, OverlayTexture.NO_OVERLAY, color, null, 0);
	}

	/** {@code ItemInHandLayer.submitArmWithItem}'s transform on our posed arm. */
	private static void submitItem(PoseStack poseStack, SubmitNodeCollector collector, PartPose arm, ItemStackRenderState item, boolean left, int light,
		PoseBuffer buf) {
		if (item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		itemChain(poseStack, arm, left, buf);
		item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	private static void itemChain(PoseStack poseStack, PartPose arm, boolean left, PoseBuffer buf) {
		poseStack.translate(arm.x / 16F, arm.y / 16F, arm.z / 16F);
		poseStack.rotate(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
		PoseApply.itemBone(buf, left, poseStack);
		poseStack.rotateDegrees(Axis.XP, -90.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.translate((left ? -1 : 1) / 16.0F, 2 / 16.0F, -10 / 16.0F);
	}

	/** Which arm is the main one (the prototype draws both arms either way). */
	static HumanoidArm mainArm(AvatarRenderState s) {
		return s.mainArm;
	}
}
