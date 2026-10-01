package dev.voidmc.expanse.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The four-legged animals: legs that swing in a walk, a head that follows the look direction, and
 * whatever small motions each animal adds on top (ears, tail, trunk).
 *
 * <p>Animations add to each part's rest pose rather than replacing it, because the rest poses carry
 * real angles (an elk's neck leans forward, a mammoth's trunk curls) that {@code resetPose} restores
 * every frame.
 */
public class ExpanseAnimalModel<S extends LivingEntityRenderState> extends EntityModel<S> {
	protected final ModelPart head;
	protected final ModelPart rightFrontLeg;
	protected final ModelPart leftFrontLeg;
	protected final ModelPart rightHindLeg;
	protected final ModelPart leftHindLeg;
	private final float stride;

	public ExpanseAnimalModel(ModelPart root, float stride) {
		super(root);
		this.head = root.getChild("head");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
		this.stride = stride;
	}

	@Override
	public void setupAnim(S state) {
		super.setupAnim(state);
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD;
		float pos = state.walkAnimationPos;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float swing = this.stride * speed;
		this.rightHindLeg.xRot += Mth.cos(pos * 0.6662F) * swing;
		this.leftHindLeg.xRot += Mth.cos(pos * 0.6662F + Mth.PI) * swing;
		this.rightFrontLeg.xRot += Mth.cos(pos * 0.6662F + Mth.PI) * swing;
		this.leftFrontLeg.xRot += Mth.cos(pos * 0.6662F) * swing;
	}

	protected static void sway(ModelPart part, float ageInTicks, float speed, float amount, float phase) {
		part.zRot += Mth.sin(ageInTicks * speed + phase) * amount;
	}
}
