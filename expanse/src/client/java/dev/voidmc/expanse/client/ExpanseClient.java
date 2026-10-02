package dev.voidmc.expanse.client;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.client.model.CapybaraModel;
import dev.voidmc.expanse.client.model.CrabModel;
import dev.voidmc.expanse.client.model.ElkModel;
import dev.voidmc.expanse.client.model.JsonEntityModels;
import dev.voidmc.expanse.client.model.MammothModel;
import dev.voidmc.expanse.client.render.AnimalRenderer;
import dev.voidmc.expanse.client.render.MammothRenderer;
import dev.voidmc.expanse.entity.ExpanseEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class ExpanseClient implements ClientModInitializer {
	private static final ModelLayerLocation ELK = layer("elk");
	private static final ModelLayerLocation MAMMOTH = layer("mammoth");
	private static final ModelLayerLocation CAPYBARA = layer("capybara");
	private static final ModelLayerLocation CRAB = layer("crab");

	@Override
	public void onInitializeClient() {
		for (ModelLayerLocation l : new ModelLayerLocation[]{ELK, MAMMOTH, CAPYBARA, CRAB}) {
			ModelLayerRegistry.registerModelLayer(l, () -> JsonEntityModels.load(l.model().getPath()));
		}
		EntityRendererRegistry.register(ExpanseEntities.ELK,
			c -> new AnimalRenderer<>(c, new ElkModel(c.bakeLayer(ELK)), "elk", 0.9F, LivingEntityRenderState::new, 0.0F));
		EntityRendererRegistry.register(ExpanseEntities.MAMMOTH, c -> new MammothRenderer(c, new MammothModel(c.bakeLayer(MAMMOTH))));
		EntityRendererRegistry.register(ExpanseEntities.CAPYBARA,
			c -> new AnimalRenderer<>(c, new CapybaraModel(c.bakeLayer(CAPYBARA)), "capybara", 0.5F, LivingEntityRenderState::new, 0.0F));
		// Crabs are drawn turned a quarter so that walking forward looks like scuttling sideways.
		EntityRendererRegistry.register(ExpanseEntities.CRAB,
			c -> new AnimalRenderer<>(c, new CrabModel(c.bakeLayer(CRAB)), "crab", 0.4F, LivingEntityRenderState::new, 90.0F));
		dev.voidmc.expanse.client.life.LivingClient.init();   // the wild creatures (entity/life)
	}

	private static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(Expanse.id(name), "main");
	}
}
