package dev.voidmc.expanse.client.life;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A butterfly: a body and two wings. In flight the wings beat between nearly flat and nearly closed and
 * the body bobs with each beat; settled, the wings stand closed over the back and open slowly now and
 * then, as a butterfly basking on a flower does.
 */
public class ButterflyModel extends EntityModel<CritterRenderState> {
	private final ModelPart body;
	private final ModelPart antennae;
	private final ModelPart leftWing;
	private final ModelPart rightWing;

	public ButterflyModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.antennae = this.body.getChild("antennae");
		this.leftWing = this.body.getChild("left_wing");
		this.rightWing = this.body.getChild("right_wing");
	}

	@Override
	public void setupAnim(CritterRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks + state.seed * 3.7F;
		float open;
		if (state.resting) {
			float bask = Math.max(0.0F, Mth.sin(t * 0.06F));
			open = 1.45F - bask * bask * 1.25F;
		} else {
			float beat = Mth.sin(t * 1.25F) * 0.5F + 0.5F;
			open = 0.2F + beat * 1.25F;
			this.body.y += Mth.sin(t * 1.25F + 1.2F) * 0.7F;
			this.body.xRot = -0.18F;
		}
		// Wings up: the left one turns about -z, the right about +z.
		this.leftWing.zRot = -open;
		this.rightWing.zRot = open;
		this.antennae.xRot += Mth.sin(t * 0.21F) * 0.1F;
	}
}
