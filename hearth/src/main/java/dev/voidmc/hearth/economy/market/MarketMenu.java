package dev.voidmc.hearth.economy.market;

import dev.voidmc.hearth.economy.EconomyMenus;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.economy.net.EconomyMenu;
import dev.voidmc.hearth.economy.net.MarketViewPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The trade menu. It has only the player's inventory as slots (so they can see and arrange what they carry); the market
 * itself is rows the server describes in a {@link MarketView} and the client clicks by id. Server-authoritative: every
 * trade is decided by {@link Market} and the new view sent back.
 */
public class MarketMenu extends AbstractContainerMenu implements EconomyMenu {
	public static final int INVENTORY_X = 58;
	public static final int INVENTORY_Y = 152;
	/** Walk further than this from where the market was opened and it closes. */
	private static final double MAX_DISTANCE_SQR = 10 * 10;

	private final @Nullable Market market;
	private final @Nullable Vec3 anchor;
	private MarketView view;

	/** Client side: built from the opening data. */
	public MarketMenu(int containerId, Inventory inventory, MarketView view) {
		this(containerId, inventory, view, null, null);
	}

	/** Server side. */
	public MarketMenu(int containerId, Inventory inventory, MarketView view, @Nullable Market market, @Nullable Vec3 anchor) {
		super(EconomyMenus.MARKET, containerId);
		this.view = view;
		this.market = market;
		this.anchor = anchor;
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public MarketView view() {
		return this.view;
	}

	/** Client: a new view arrived. */
	public void setView(MarketView view) {
		this.view = view;
	}

	@Override
	public void handle(ServerPlayer player, EconomyActionPayload action) {
		if (this.market == null || action.action() != EconomyActionPayload.TRADE) {
			return;
		}
		this.market.trade(player, action.index(), action.amount() != 0);
		this.view = this.market.view(player);
		ServerPlayNetworking.send(player, new MarketViewPayload(this.containerId, this.view));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.anchor == null || player.isAlive() && player.position().distanceToSqr(this.anchor) <= MAX_DISTANCE_SQR;
	}
}
