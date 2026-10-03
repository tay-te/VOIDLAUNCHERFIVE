package dev.voidmc.expanse.client.life;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.client.render.AnimalRenderer;
import dev.voidmc.expanse.entity.life.Deer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Deer: spotted fawns, and antlers on the bucks. */
public class DeerRenderer extends AnimalRenderer<Deer, DeerRenderer.DeerRenderState, DeerModel> {
	private static final Identifier ADULT = Expanse.id("textures/entity/deer/deer.png");
	private static final Identifier FAWN = Expanse.id("textures/entity/deer/fawn.png");

	public DeerRenderer(EntityRendererProvider.Context context, DeerModel model) {
		super(context, model, "deer/deer", 0.6F, DeerRenderState::new, 0.0F);
	}

	@Override
	public void extractRenderState(Deer entity, DeerRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.antlers = !entity.isBaby() && entity.isBuck();
	}

	@Override
	public Identifier getTextureLocation(DeerRenderState state) {
		return state.isBaby ? FAWN : ADULT;
	}

	public static class DeerRenderState extends LivingEntityRenderState {
		public boolean antlers;
	}
}
