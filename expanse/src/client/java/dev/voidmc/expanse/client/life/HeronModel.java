package dev.voidmc.expanse.client.life;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A heron. Standing, the wings are folded at its sides and the neck sways; wading, now and then it
 * strikes, plunging its head down into the water and back. In flight the broad wings beat slowly, the
 * neck folds back into the shoulders and the legs trail behind, as a heron's do.
 */
public class HeronModel extends EntityModel<CritterRenderState> {
	private final ModelPart body;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart leftSpread;
	private final ModelPart rightSpread;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public HeronModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.neck = root.getChild("neck");
		this.head = this.neck.getChild("head");
		this.leftWing = this.body.getChild("left_wing");
		this.rightWing = this.body.getChild("right_wing");
		this.leftSpread = this.body.getChild("left_wing_spread");
		this.rightSpread = this.body.getChild("right_wing_spread");
		this.leftLeg = root.getChild("left_leg");
		this.rightLeg = root.getChild("right_leg");
	}

	@Override
	public void setupAnim(CritterRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks + state.seed * 11.0F;
		boolean flying = state.flying;
		this.leftWing.visible = !flying;
		this.rightWing.visible = !flying;
		this.leftSpread.visible = flying;
		this.rightSpread.visible = flying;
		if (flying) {
			float beat = Mth.sin(t * 0.5F);
			this.leftSpread.zRot = -beat * 0.5F;
			this.rightSpread.zRot = beat * 0.5F;
			this.body.xRot = 0.0F;
			// Neck folded back, head held level in front of the shoulders.
			this.neck.xRot = -0.55F;
			this.neck.y += 4.0F;
			this.neck.z -= 1.0F;
			this.head.xRot = 0.2F;
			this.leftLeg.xRot = 1.45F;
			this.rightLeg.xRot = 1.45F;
			this.leftLeg.y -= 2.0F;
			this.rightLeg.y -= 2.0F;
			return;
		}
		float walk = Math.min(1.0F, state.walkAnimationSpeed);
		this.leftLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.7F * walk;
		this.rightLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + Mth.PI) * 0.7F * walk;
		this.neck.xRot += Mth.sin(t * 0.04F) * 0.06F;
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.6F;
		// The strike: every so often, standing in the water, a lunge of the neck down and back.
		float cycle = ((t + state.seed * 37.0F) % 180.0F) / 180.0F;
		if (state.isInWater && walk < 0.1F && cycle < 0.05F) {
			float lunge = Mth.sin(cycle / 0.05F * Mth.PI);
			this.neck.xRot += lunge * 1.5F;
			this.head.xRot += lunge * 0.4F;
		}
	}
}
