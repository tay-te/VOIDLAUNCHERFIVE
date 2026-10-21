package dev.voidmc.hearth.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Goods that live outside inventories (VOID Mettle's cards): the owning mod keeps them, Hearth only lists them at a
 * broker's table (docs/requests/mettle-cards.md §7). Registered with {@link HearthServices#addVirtualGoods}. Ids are
 * the owner's, unique within its {@link #kind}. Server thread only. Added 2026-10-21.
 *
 * <p>Hearth's side: a broker (a merchant in a town or city's market) keeps a board of listings; the seller names the
 * price and pays a listing fee to the town; a sale pays the town's tax and the rest to the seller's bank deposits
 * (offline or not); a listing not sold within its term is {@linkplain #release released}. The owner decides what may be
 * listed and moves the goods: {@link #hold} into escrow, {@link #release} back, {@link #transfer} to the buyer.
 */
public interface VirtualGoods {
	/** A namespaced kind ({@code mettle:card}). */
	String kind();

	/** What the seller may list now (ids). */
	List<Long> listable(ServerPlayer seller);

	/** "Tunneler · Rare". */
	Component name(long id);

	/** Tooltip lines (traits, requirement, history). */
	List<Component> describe(long id);

	/** A display-only stack Hearth can draw; never given out. */
	ItemStack display(long id);

	/** Takes it into escrow for a listing; false if it cannot be listed. */
	boolean hold(long id, ServerPlayer seller, long listing);

	/** The listing was withdrawn or expired: back to the seller. */
	void release(long id);

	/** Sold: give it to the buyer (who may be offline). False if it cannot (the sale is then called off and nothing paid). */
	boolean transfer(long id, UUID buyer);
}
