package dev.voidmc.fray.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.voidmc.fray.client.compat.IrisCompat;
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
import dev.voidmc.fray.motion.bake.FirstPersonFrame;
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
	private static final float[] SCRATCH = new float[3];

	private FirstPersonBody() {
	}

	/** A model whose root is one arm, posed at draw time from the state it is submitted with. */
	private static final class ArmModel extends Model<PartPose> {
		private final float[] fore;

		ArmModel(ModelPart arm, RenderType type, float[] fore) {
			super(arm, t -> type);
			this.fore = fore;
		}

		@Override
		public void setupAnim(PartPose pose) {
			PoseApply.write(pose, this.root());
			// The elbow: the forearm (and the sleeve's) turned relative to the arm.
			Elbows.bend(this.root(), true);
			for (ModelPart f : Elbows.forearms(this.root())) {
				f.setRotation(this.fore[0], this.fore[1], this.fore[2]);
			}
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
		// First person is the third-person pose seen from the real eye, calmed toward a ready framing and eased
		// (FirstPersonFrame). One clip alone: sampled from the clip with the easing window; a string cut blending into the
		// next: the animator's blended pose as it is.
		float swing = FirstPersonFrame.swing(fp, dev.voidmc.fray.client.combat.FrayClientOptions.get().fpSwingScale);
		if (fromClip && a < 1) {
			float[] po = prev.fp.offset(), o = fp.offset();
			Clip.FirstPerson framing = new Clip.FirstPerson(fp.framing(), new float[] {po[0] + (o[0] - po[0]) * a, po[1] + (o[1] - po[1]) * a,
				po[2] + (o[2] - po[2]) * a}, prev.fp.pitch() + (fp.pitch() - prev.fp.pitch()) * a, null, null, null, null, false, fp.bothArms(), fp.swing());
			FirstPersonFrame.derive(buf, framing, swing, SOLVER, FRAME);
		} else {
			FirstPersonFrame.clip(clip, animator.actionElapsed(now), fp, swing, SOLVER, FRAME);
		}

		// Vanilla's held item, as FirstPersonHandsAndItemsRenderer would place it this frame (sway and equip included).
		boolean right = avatar.mainArm == HumanoidArm.RIGHT;
		boolean blendItem = weight < 0.998F && right && !state.mainHandItem.isEmpty();
		ENTRY.set(poseStack.last().pose());
		if (blendItem) {
			VANILLA.set(ENTRY);
			VANILLA.rotate(Axis.XP.rotationDegrees((state.viewXRot - state.xBob) * 0.1F));
			VANILLA.rotate(Axis.YP.rotationDegrees((state.viewYRot - state.yBob) * 0.1F));
			float inverseArmHeight = state.mainHandSwapScale * (1.0F - net.minecraft.util.Mth.lerp(partialTicks, state.oldMainHandHeight, state.mainHandHeight));
			VANILLA.translate(0.56F, -0.52F + inverseArmHeight * -0.6F, -0.72F);
		}

		RenderType skin = RenderTypes.entityTranslucent(avatar.skin.body().texturePath());
		model.rightSleeve.visible = avatar.showRightSleeve;
		model.leftSleeve.visible = avatar.showLeftSleeve;
		int light = avatar.lightCoords;
		// The arm fades in with the clip (vanilla shows no arm with an item in hand).
		int color = weight >= 0.998F ? -1 : ((int) (weight * 255) << 24) | 0xFFFFFF;
		submitArm(poseStack, collector, model.rightArm, FRAME.rightArm, FRAME.rightForeLocal, skin, light, color);
		if (blendItem) {
			submitBlendedItem(poseStack, collector, FRAME.rightItem, avatar.rightHandItemState, state.mainHandRenderState, light, weight);
		} else {
			submitItem(poseStack, collector, FRAME.rightItem, avatar.rightHandItemState, light);
		}
		float left = FRAME.left * weight;
		if (left > 0.002F) {
			int lc = left >= 0.998F ? -1 : ((int) (left * 255) << 24) | 0xFFFFFF;
			submitArm(poseStack, collector, model.leftArm, FRAME.leftArm, FRAME.leftForeLocal, skin, light, lc);
			submitItem(poseStack, collector, FRAME.leftItem, avatar.leftHandItemState, light);
		}
		return true;
	}

	/** A clip whose first person draws Fray's arms (not a camera-only clip such as a dodge, nor one hiding the arms). */
	private static boolean drawsArms(Clip clip) {
		return clip.fp != null && dev.voidmc.fray.motion.bake.Checks.drawsArms(clip.fp);
	}

	private static final Matrix4f VANILLA = new Matrix4f();
	private static final Matrix4f ENTRY = new Matrix4f();
	private static final FirstPersonFrame.Frame FRAME = new FirstPersonFrame.Frame();

	/**
	 * The held item between vanilla's first-person placement and the clip's hand, by {@code w} (0 vanilla, 1 the clip):
	 * the two final item matrices (each with its own display transform) are blended (translation, rotation, scale) and
	 * the first-person item drawn there.
	 */
	private static void submitBlendedItem(PoseStack poseStack, SubmitNodeCollector collector, Matrix4f hand, ItemStackRenderState thirdPerson,
		ItemStackRenderState firstPerson, int light, float w) {
		Matrix4f tf = layerTransform(thirdPerson);
		Matrix4f tv = layerTransform(firstPerson);
		if (tf == null || tv == null) {
			submitItem(poseStack, collector, hand, thirdPerson, light);
			return;
		}
		Matrix4f ff = new Matrix4f(ENTRY).mul(hand).mul(tf);
		Matrix4f fv = new Matrix4f(VANILLA).mul(tv);
		Matrix4f b = FirstPersonFrame.blend(fv, ff, w);
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
		// Gated and scaled like the camera shake: the cameraMotion option (default 40 %) × Damage Tilt, off with the shake.
		float k = dev.voidmc.fray.client.combat.feel.CameraShake.motionScale();
		if (k <= 0) {
			return;
		}
		float t = clip.localTime((float) (e / 20.0));
		if (clip.fp.cameraPos() != null) {
			clip.fp.cameraPos().sample(t, SCRATCH, 0);
			view.translate(-SCRATCH[0] * k, -SCRATCH[1] * k, -SCRATCH[2] * k);
		}
		if (clip.fp.camera() != null) {
			clip.fp.camera().sample(t, SCRATCH, 0);
			view.rotateDegrees(Axis.ZP, SCRATCH[2] * k);
			view.rotateDegrees(Axis.XP, SCRATCH[0] * k);
			view.rotateDegrees(Axis.YP, SCRATCH[1] * k);
		}
	}

	private static final PartPose AT_ORIGIN = new PartPose();

	/** An arm drawn at {@code at} (camera space, the part's own frame: the part itself at its origin, unrotated). */
	private static void submitArm(PoseStack poseStack, SubmitNodeCollector collector, ModelPart arm, Matrix4f at, float[] fore, RenderType type, int light,
		int color) {
		poseStack.pushPose();
		poseStack.mulPose(at);
		collector.submitModel(new ArmModel(arm, type, fore.clone()), new PartPose().set(AT_ORIGIN), poseStack, type, light, OverlayTexture.NO_OVERLAY, color, null, 0);
		poseStack.popPose();
	}

	/** The held item at {@code at} (camera space, {@code ItemInHandLayer}'s transform already applied). */
	private static void submitItem(PoseStack poseStack, SubmitNodeCollector collector, Matrix4f at, ItemStackRenderState item, int light) {
		if (item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.mulPose(at);
		item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/** Which arm is the main one (the prototype draws both arms either way). */
	static HumanoidArm mainArm(AvatarRenderState s) {
		return s.mainArm;
	}
}
