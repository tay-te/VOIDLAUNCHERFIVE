package dev.voidmc.hearth.client.npc.travel;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import dev.voidmc.hearth.npc.travel.cart.Carts;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

/**
 * Client side of the travellers: the cart model and renderer (§1).
 * Lane A1 of docs/LIVING_WORLD_2.md (§11.3) owns this package; lane 0 left this entry point empty.
 */
public final class TravelClient {
	private static final ModelLayerLocation WAGON = new ModelLayerLocation(Hearth.id("cart"), "wagon");
	private static final ModelLayerLocation CART = new ModelLayerLocation(Hearth.id("cart"), "cart");
	private static final ModelLayerLocation HANDCART = new ModelLayerLocation(Hearth.id("cart"), "handcart");

	private TravelClient() {
	}

	static ModelLayerLocation layer(CartEntity.Kind kind) {
		return switch (kind) {
			case WAGON -> WAGON;
			case CART -> CART;
			case HANDCART -> HANDCART;
		};
	}

	/** Called once from {@code NpcClient.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		ModelLayerRegistry.registerModelLayer(WAGON, CartModel::wagon);
		ModelLayerRegistry.registerModelLayer(CART, CartModel::cart);
		ModelLayerRegistry.registerModelLayer(HANDCART, CartModel::handcart);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(Carts.CART, CartRenderer::new);
	}
}
