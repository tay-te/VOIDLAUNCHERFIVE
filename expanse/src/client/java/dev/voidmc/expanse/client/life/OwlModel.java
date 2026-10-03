package dev.voidmc.expanse.client.life;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * An owl: upright and round-headed, with a head that turns most of the way round to follow you, a slow
 * blink, and eyes shut while it dozes by day. In flight it leans level, spreads broad wings and tucks its
 * feet.
 */
public class OwlModel extends EntityModel<CritterRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart eyelids;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart leftSpread;
	private final ModelPart rightSpread;
	private final ModelPart tail;
	private final ModelPart leftFoot;
	private final ModelPart rightFoot;

	public OwlModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = root.getChild("head");
		this.eyelids = this.head.getChild("eyelids");
		this.leftWing = root.getChild("left_wing");
		this.rightWing = root.getChild("right_wing");
		this.leftSpread = root.getChild("left_wing_spread");
		this.rightSpread = root.getChild("right_wing_spread");
		this.tail = root.getChild("tail");
		this.leftFoot = root.getChild("left_foot");
		this.rightFoot = root.getChild("right_foot");
	}

	@Override
	public void setupAnim(CritterRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks + state.seed * 7.0F;
		boolean flying = state.flying;
		this.leftWing.visible = !flying;
		this.rightWing.visible = !flying;
		this.leftSpread.visible = flying;
		this.rightSpread.visible = flying;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		boolean blink = (t % 120.0F) < 3.0F;
		this.eyelids.visible = state.sleeping || blink;
		if (flying) {
			// Lean level: the body pivots at its base, the head goes out in front of it.
			this.body.xRot = 1.25F;
			this.head.setPos(0.0F, 18.0F, -6.5F);
			this.head.yRot *= 0.3F;
			float beat = Mth.sin(t * 0.6F);
			this.leftSpread.setPos(2.5F, 20.0F, -2.5F);
			this.rightSpread.setPos(-2.5F, 20.0F, -2.5F);
			this.leftSpread.zRot = -beat * 0.55F;
			this.rightSpread.zRot = beat * 0.55F;
			this.tail.setPos(0.0F, 21.5F, 1.0F);
			this.tail.xRot = 0.1F;
			this.leftFoot.visible = false;
			this.rightFoot.visible = false;
			return;
		}
		this.leftFoot.visible = true;
		this.rightFoot.visible = true;
		if (state.sleeping) {
			this.head.y += 0.6F;
			this.head.xRot += 0.15F;
		}
	}
}
