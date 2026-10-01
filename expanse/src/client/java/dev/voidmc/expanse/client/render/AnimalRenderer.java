package dev.voidmc.expanse.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.expanse.Expanse;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * One renderer for all of the mod's animals: a model, a texture, and babies drawn at half size (the
 * entity's scale attribute does not include age, so that is done here).
 */
public class AnimalRenderer<T extends Mob, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends MobRenderer<T, S, M> {
	private final Identifier texture;
	private final Supplier<S> state;
	private final float yawOffset;

	public AnimalRenderer(EntityRendererProvider.Context context, M model, String texture, float shadow, Supplier<S> state, float yawOffset) {
		super(context, model, shadow);
		this.texture = Expanse.id("textures/entity/" + texture + ".png");
		this.state = state;
		this.yawOffset = yawOffset;
	}

	@Override
	public Identifier getTextureLocation(S state) {
		return this.texture;
	}

	@Override
	public S createRenderState() {
		return this.state.get();
	}

	@Override
	protected void scale(S state, PoseStack poseStack) {
		if (state.isBaby) {
			poseStack.scale(0.5F, 0.5F, 0.5F);
		}
	}

	@Override
	protected void setupRotations(S state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot + this.yawOffset, entityScale);
	}
}
