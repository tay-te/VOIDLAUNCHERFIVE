package dev.voidmc.expanse.client.life;

import dev.voidmc.expanse.Expanse;
import dev.voidmc.expanse.client.model.JsonEntityModels;
import dev.voidmc.expanse.entity.life.Butterfly;
import dev.voidmc.expanse.entity.life.Dragonfly;
import dev.voidmc.expanse.entity.life.Heron;
import dev.voidmc.expanse.entity.life.LifeEntities;
import dev.voidmc.expanse.entity.life.Owl;
import dev.voidmc.expanse.entity.life.Songbird;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

/** Models and renderers for the wild creatures (entity/life). Geometry and skins come from tools/gen_living.py. */
public final class LivingClient {
	private static final ModelLayerLocation BUTTERFLY = layer("butterfly");
	private static final ModelLayerLocation DRAGONFLY = layer("dragonfly");
	private static final ModelLayerLocation SONGBIRD = layer("songbird");
	private static final ModelLayerLocation HERON = layer("heron");
	private static final ModelLayerLocation OWL = layer("owl");
	private static final ModelLayerLocation DEER = layer("deer");

	private LivingClient() {
	}

	public static void init() {
		for (ModelLayerLocation l : new ModelLayerLocation[]{BUTTERFLY, DRAGONFLY, SONGBIRD, HERON, OWL, DEER}) {
			ModelLayerRegistry.registerModelLayer(l, () -> JsonEntityModels.load(l.model().getPath()));
		}
		EntityRendererRegistry.register(LifeEntities.BUTTERFLY,
			c -> new CritterRenderer<>(c, new ButterflyModel(c.bakeLayer(BUTTERFLY)), "butterfly", Butterfly.VARIANTS, 0.1F, 0.75F));
		EntityRendererRegistry.register(LifeEntities.DRAGONFLY,
			c -> new CritterRenderer<>(c, new DragonflyModel(c.bakeLayer(DRAGONFLY)), "dragonfly", Dragonfly.VARIANTS, 0.1F, 0.8F));
		EntityRendererRegistry.register(LifeEntities.SONGBIRD,
			c -> new CritterRenderer<>(c, new SongbirdModel(c.bakeLayer(SONGBIRD)), "songbird", Songbird.VARIANTS, 0.15F, 0.85F));
		EntityRendererRegistry.register(LifeEntities.HERON,
			c -> new CritterRenderer<>(c, new HeronModel(c.bakeLayer(HERON)), "heron", Heron.VARIANTS, 0.35F, 1.0F));
		EntityRendererRegistry.register(LifeEntities.OWL,
			c -> new CritterRenderer<>(c, new OwlModel(c.bakeLayer(OWL)), "owl", Owl.VARIANTS, 0.3F, 1.0F));
		EntityRendererRegistry.register(LifeEntities.DEER, c -> new DeerRenderer(c, new DeerModel(c.bakeLayer(DEER))));
	}

	private static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(Expanse.id(name), "main");
	}
}
