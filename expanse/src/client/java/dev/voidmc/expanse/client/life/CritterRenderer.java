package dev.voidmc.expanse.client.life;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.entity.life.Critter;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * One renderer for the small creatures: a skin per variant (textures/entity/&lt;name&gt;/&lt;variant&gt;.png, painted
 * by tools/gen_living.py onto one shared model), and a scale.
 */
public class CritterRenderer<T extends Critter> extends MobRenderer<T, CritterRenderState, EntityModel<CritterRenderState>> {
	private final Identifier[] textures;
	private final float scale;

	public CritterRenderer(EntityRendererProvider.Context context, EntityModel<CritterRenderState> model, String name, String[] variants,
		float shadow, float scale) {
		super(context, model, shadow);
		this.textures = new Identifier[variants.length];
		for (int i = 0; i < variants.length; i++) {
			this.textures[i] = Expanse.id("textures/entity/" + name + "/" + variants[i] + ".png");
		}
		this.scale = scale;
	}

	@Override
	public CritterRenderState createRenderState() {
		return new CritterRenderState();
	}

	@Override
	public void extractRenderState(T entity, CritterRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.variant = entity.getVariant();
		state.flying = entity.isFlying();
		state.resting = entity.isResting();
		state.sleeping = entity.isSleeping();
		state.onGround = entity.onGround();
		state.seed = entity.getId();
	}

	@Override
	public Identifier getTextureLocation(CritterRenderState state) {
		return this.textures[Mth.clamp(state.variant, 0, this.textures.length - 1)];
	}

	@Override
	protected void scale(CritterRenderState state, PoseStack poseStack) {
		poseStack.scale(this.scale, this.scale, this.scale);
	}
}
