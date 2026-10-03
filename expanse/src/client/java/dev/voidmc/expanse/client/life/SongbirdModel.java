package dev.voidmc.expanse.client.life;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A small bird. In flight the wings swing out and beat fast and the legs tuck back; mid-hop the wings
 * flick; on the ground it pecks now and then; on a perch its tail bobs and its head turns; asleep, the
 * head sinks into the shoulders.
 */
public class SongbirdModel extends EntityModel<CritterRenderState> {
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public SongbirdModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.tail = root.getChild("tail");
		this.leftWing = root.getChild("left_wing");
		this.rightWing = root.getChild("right_wing");
		this.leftLeg = root.getChild("left_leg");
		this.rightLeg = root.getChild("right_leg");
	}

	@Override
	public void setupAnim(CritterRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks + state.seed * 5.3F;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD;
		if (state.flying) {
			float beat = Mth.sin(t * 1.9F);
			this.leftWing.zRot = -1.45F - beat * 0.8F;
			this.rightWing.zRot = -this.leftWing.zRot;
			this.leftLeg.xRot = 1.3F;
			this.rightLeg.xRot = 1.3F;
			this.tail.xRot -= 0.15F;
			return;
		}
		if (state.sleeping) {
			this.head.y += 1.0F;
			this.head.xRot += 0.35F;
			return;
		}
		if (!state.onGround && !state.resting) {
			// Mid-hop: a flick of the wings.
			this.leftWing.zRot = -0.5F;
			this.rightWing.zRot = 0.5F;
		} else if (!state.resting) {
			// Pecking, in short bursts at odd moments.
			float cycle = (t % 70.0F) / 70.0F;
			if (cycle < 0.12F) {
				float dip = Mth.sin(cycle / 0.12F * Mth.PI * 2.0F);
				this.head.xRot += Math.max(0.0F, dip) * 0.9F;
				this.head.y += Math.max(0.0F, dip) * 0.8F;
			}
		}
		this.tail.xRot += Mth.sin(t * 0.35F) * (state.resting ? 0.12F : 0.06F);
	}
}
