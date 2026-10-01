package dev.voidmc.expanse.client.render;

import dev.voidmc.expanse.client.model.MammothModel;
import dev.voidmc.expanse.entity.Mammoth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class MammothRenderer extends AnimalRenderer<Mammoth, MammothRenderState, MammothModel> {
	public MammothRenderer(EntityRendererProvider.Context context, MammothModel model) {
		super(context, model, "mammoth", 1.6F, MammothRenderState::new, 0.0F);
	}

	@Override
	public void extractRenderState(Mammoth entity, MammothRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.sheared = entity.isSheared();
	}
}
