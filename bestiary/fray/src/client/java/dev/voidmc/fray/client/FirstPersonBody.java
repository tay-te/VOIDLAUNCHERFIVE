package dev.voidmc.fray.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.voidmc.fray.client.compat.IrisCompat;
import dev.voidmc.fray.client.mixin.ModelPartAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.FirstPersonRig;
import dev.voidmc.fray.motion.PartPose;
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
import org.joml.Matrix4f;

/**
 * First person (ANIMATION.md §7.4): while the local player runs an action whose clip has first-person rig curves, the
 * hand, forearm and held item are drawn by the {@link FirstPersonRig} <em>inside</em>
 * {@code FirstPersonHandsAndItemsRenderer.submitHandsWithItems} (verified in 26.3: {@code GameRenderer.renderItemInHand}
 * calls it with its own submit storage and a hand render pass; Iris's {@code HandRenderer} calls the same method for its
 * solid and translucent hand passes). The clip's own first-person curves move the hand from vanilla's held-item pose
 * (the item is vanilla's first-person item, drawn where vanilla draws it at rest), so a swing starts and ends on
 * vanilla's picture; the forearm enters from the bottom corner, never the upper arm or the shoulder.
 */
public final class FirstPersonBody {
	private static final float[] SCRATCH = new float[3];

	private FirstPersonBody() {
	}

	/** A model whose root is a forearm-only copy of one arm ({@link #forearmOnly}), at the arm's origin. */
	private static final class ArmModel extends Model<PartPose> {
		ArmModel(ModelPart root, RenderType type) {
			super(root, t -> type);
		}

		@Override
		public void setupAnim(PartPose pose) {
			PoseApply.write(pose, this.root());
		}
	}

	private static final Map<ModelPart, ModelPart> FOREARM_ONLY = new IdentityHashMap<>();

	/**
	 * The arm's forearm and its sleeve's (the elbow cut's lower halves, {@link Elbows}), in parts of their own (the
	 * player model the third-person renderer poses is never touched), each closed at the elbow by a cuff: the arm's top
	 * face moved down to the cut. The whole arm when elbows are off.
	 */
	private static ModelPart forearmOnly(ModelPart arm) {
		return FOREARM_ONLY.computeIfAbsent(arm, a -> {
			List<ModelPart> fs = Elbows.forearms(a);
			if (fs.isEmpty()) {
				return a;
			}
			List<ModelPart> wholes = new ArrayList<>();
			wholes.add(a);
			wholes.addAll(((ModelPartAccess) (Object) a).fray$children().values());
			Map<String, ModelPart> kids = new HashMap<>();
			for (int i = 0; i < fs.size(); i++) {
				ModelPart f = fs.get(i);
				List<ModelPart.Cube> cubes = new ArrayList<>(((ModelPartAccess) (Object) f).fray$cubes());
				ModelPart.Cube cap = cap(wholes, f);
				if (cap != null) {
					cubes.add(cap);
				}
				ModelPart c = new ModelPart(cubes, new HashMap<>());
				c.setInitialPose(f.getInitialPose());
				c.loadPose(f.getInitialPose());
				kids.put(i == 0 ? "forearm" : "sleeve", c);
			}
			return new ModelPart(List.of(), kids);
		});
	}

	/** The cuff closing a forearm at the elbow: the top face of the part it was cut from, at the cut, facing the elbow. */
	private static ModelPart.@org.jspecify.annotations.Nullable Cube cap(List<ModelPart> wholes, ModelPart forearm) {
		for (ModelPart w : wholes) {
			if (!w.hasChild(Elbows.FOREARM) || w.getChild(Elbows.FOREARM) != forearm) {
				continue;
			}
			net.minecraft.client.model.geom.PartPose pivot = forearm.getInitialPose();
			for (ModelPart.Cube cube : ((ModelPartAccess) (Object) w).fray$cubes()) {
				for (ModelPart.Polygon poly : cube.polygons) {
					if (poly.normal().y() < -0.9F) {
						ModelPart.Vertex[] v = poly.vertices();
						ModelPart.Vertex[] out = new ModelPart.Vertex[v.length];
						for (int k = 0; k < v.length; k++) {
							out[k] = new ModelPart.Vertex(v[k].x() - pivot.x(), 0, v[k].z() - pivot.z(), v[k].u(), v[k].v());
						}
						ModelPart.Cube c = new ModelPart.Cube(0, 0, cube.minX - pivot.x(), 0, cube.minZ - pivot.z(), cube.maxX - cube.minX, 0, cube.maxZ - cube.minZ, 0, 0, 0,
							false, 64, 64, java.util.EnumSet.of(net.minecraft.core.Direction.UP));
						c.polygons[0] = new ModelPart.Polygon(out, poly.normal());
						return c;
					}
				}
			}
		}
		return null;
	}

	private static final FirstPersonRig.Pose POSE = new FirstPersonRig.Pose(), PREV = new FirstPersonRig.Pose(), MIX = new FirstPersonRig.Pose();
	private static final Matrix4f BASE = new Matrix4f(), M = new Matrix4f();

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
		Animator animator = ClientMotion.get(mc.player.getId());
		Clip clip = animator == null ? null : animator.clip(Animator.ACTION);
		if (clip == null || clip.fp == null) {
			return false;
		}
		if (clip.fp.hideArms()) {
			return true;
		}
		if (!FirstPersonRig.animates(clip)) {
			return false;
		}
		if (IrisCompat.skipHand(state.mainHandItem)) {
			return true;
		}
		double now = ClientMotion.now(partialTicks);
		// The crossfade (no jump at the start or the end of a swing): from vanilla's held item by the clip's own weight,
		// or from the previous first-person clip when one string cut blends into the next. Every blend is on the rig's
		// hands (slerp), never on drawn matrices.
		Clip prev = animator.previousAction(now);
		boolean fromClip = prev != null && FirstPersonRig.animates(prev);
		float weight = fromClip ? 1 : animator.actionWeight(now);
		if (weight <= 0.002F) {
			return false;
		}
		FirstPersonRig.sample(clip, clip.localTime((float) (animator.actionElapsed(now) / 20.0)), POSE);
		if (fromClip) {
			float a = animator.actionBlendIn(now);
			if (a < 0.998F) {
				FirstPersonRig.sample(prev, prev.localTime((float) (animator.previousElapsed(now) / 20.0)), PREV);
				POSE.set(MIX.blend(PREV, POSE, a));
			}
		} else if (weight < 0.998F) {
			POSE.set(MIX.blend(null, POSE, weight));
		}
		FrayStats.fpDraws++;

		// Vanilla's frame for the held item: its sway, as FirstPersonHandsAndItemsRenderer applies it before
		// applyItemArmTransform (whose equip height is added per hand).
		BASE.set(poseStack.last().pose());
		BASE.rotate(Axis.XP.rotationDegrees((state.viewXRot - state.xBob) * 0.1F));
		BASE.rotate(Axis.YP.rotationDegrees((state.viewYRot - state.yBob) * 0.1F));
		boolean rightMain = avatar.mainArm == HumanoidArm.RIGHT;
		float mainHeight = state.mainHandSwapScale * (1.0F - net.minecraft.util.Mth.lerp(partialTicks, state.oldMainHandHeight, state.mainHandHeight));
		float offHeight = state.offHandSwapScale * (1.0F - net.minecraft.util.Mth.lerp(partialTicks, state.oldOffHandHeight, state.offHandHeight));

		AvatarRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
		PlayerModel model = renderer.getModel();
		RenderType skin = RenderTypes.entityTranslucent(avatar.skin.body().texturePath());
		model.rightSleeve.visible = avatar.showRightSleeve;
		model.leftSleeve.visible = avatar.showLeftSleeve;
		int light = avatar.lightCoords;
		// The forearm fades in with the clip (vanilla shows no arm with an item in hand); the item stays solid.
		float armAlpha = fromClip ? 1 : weight;
		// The main hand is the rig's right hand (a left-handed player's main arm is the rig's left, mirrored).
		hand(poseStack, collector, model, skin, light, POSE, !rightMain, state.mainHandRenderState, mainHeight, armAlpha);
		float off = POSE.off * armAlpha;
		if (off > 0.002F) {
			hand(poseStack, collector, model, skin, light, POSE, rightMain, state.offHandRenderState, offHeight, off);
		} else if (!state.offHandRenderState.isEmpty()) {
			// The off hand's item where vanilla draws it (a shield, a torch), no arm.
			poseStack.pushPose();
			poseStack.last().pose().set(BASE).translate(0, offHeight * -0.6F, 0).mul(FirstPersonRig.itemAnchor(new FirstPersonRig.Hand(), rightMain, M));
			state.offHandRenderState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		return true;
	}

	/** One hand of the rig: its forearm (faded by {@code alpha}) and its first-person item. */
	private static void hand(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, RenderType skin, int light, FirstPersonRig.Pose pose,
		boolean left, ItemStackRenderState item, float height, float alpha) {
		FirstPersonRig.Hand h = pose.hand(left);
		poseStack.pushPose();
		poseStack.last().pose().set(BASE).translate(0, height * -0.6F, 0).mul(FirstPersonRig.arm(h, left, M));
		int color = alpha >= 0.998F ? -1 : ((int) (alpha * 255) << 24) | 0xFFFFFF;
		ModelPart arm = forearmOnly(left ? model.leftArm : model.rightArm);
		if (arm.hasChild("sleeve")) {
			arm.getChild("sleeve").visible = left ? model.leftSleeve.visible : model.rightSleeve.visible;
		}
		collector.submitModel(new ArmModel(arm, skin), new PartPose(), poseStack, skin, light, OverlayTexture.NO_OVERLAY, color, null, 0);
		poseStack.popPose();
		if (!item.isEmpty()) {
			poseStack.pushPose();
			poseStack.last().pose().set(BASE).translate(0, height * -0.6F, 0).mul(FirstPersonRig.itemAnchor(h, left, M));
			item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
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

	/** Which arm is the main one (the prototype draws both arms either way). */
	static HumanoidArm mainArm(AvatarRenderState s) {
		return s.mainArm;
	}
}
