package dev.voidmc.expanse.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Six scuttling legs in two alternating tripods, and claws that snap now and then. */
public class CrabModel extends EntityModel<LivingEntityRenderState> {
	private final ModelPart[] leftLegs = new ModelPart[3];
	private final ModelPart[] rightLegs = new ModelPart[3];
	private final ModelPart leftClaw;
	private final ModelPart rightClaw;
	private final ModelPart body;

	public CrabModel(ModelPart root) {
		super(root);
		for (int i = 0; i < 3; i++) {
			this.leftLegs[i] = root.getChild("left_leg_" + i);
			this.rightLegs[i] = root.getChild("right_leg_" + i);
		}
		this.leftClaw = root.getChild("left_claw");
		this.rightClaw = root.getChild("right_claw");
		this.body = root.getChild("body");
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos * 1.6F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		for (int i = 0; i < 3; i++) {
			float phase = (i % 2 == 0 ? 0 : Mth.PI);
			this.leftLegs[i].zRot += Mth.cos(pos + phase) * 0.5F * speed;
			this.leftLegs[i].yRot += Mth.sin(pos + phase) * 0.3F * speed;
			this.rightLegs[i].zRot += Mth.cos(pos + phase + Mth.PI) * 0.5F * speed;
			this.rightLegs[i].yRot += Mth.sin(pos + phase + Mth.PI) * 0.3F * speed;
		}
		float t = state.ageInTicks;
		float snap = Math.max(0.0F, Mth.sin(t * 0.25F)) * (Mth.sin(t * 0.031F) > 0.6F ? 0.35F : 0.05F);
		this.leftClaw.xRot -= snap;
		this.rightClaw.xRot -= Math.max(0.0F, Mth.sin(t * 0.25F + 1.0F)) * (Mth.sin(t * 0.031F) > 0.6F ? 0.35F : 0.05F);
		this.body.y += Mth.sin(pos * 2) * 0.3F * speed;
	}
}
