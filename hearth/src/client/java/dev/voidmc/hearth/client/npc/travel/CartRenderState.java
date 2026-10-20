package dev.voidmc.hearth.client.npc.travel;

import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** What a cart is drawn with: its kind and load, its heading and tilt, how far its wheels have turned. */
public class CartRenderState extends EntityRenderState {
	public CartEntity.Kind kind = CartEntity.Kind.CART;
	public CartEntity.Load load = CartEntity.Load.NONE;
	public float yRot;
	public float tilt;
	/** Blocks rolled. */
	public float rolled;
}
