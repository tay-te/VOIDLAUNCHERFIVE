package dev.voidmc.hearth.economy.bank;

import dev.voidmc.hearth.economy.EconomyMenus;
import dev.voidmc.hearth.economy.net.BankViewPayload;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.economy.net.EconomyMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The bank counter's menu: the player's inventory as slots, everything else server-described ({@link BankView}). */
public class BankMenu extends AbstractContainerMenu implements EconomyMenu {
	public static final int INVENTORY_X = 58;
	public static final int INVENTORY_Y = 152;
	private static final double MAX_DISTANCE_SQR = 10 * 10;

	private final @Nullable Bank bank;
	private final @Nullable Vec3 anchor;
	private BankView view;

	public BankMenu(int containerId, Inventory inventory, BankView view) {
		this(containerId, inventory, view, null, null);
	}

	public BankMenu(int containerId, Inventory inventory, BankView view, @Nullable Bank bank, @Nullable Vec3 anchor) {
		super(EconomyMenus.BANK, containerId);
		this.view = view;
		this.bank = bank;
		this.anchor = anchor;
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public BankView view() {
		return this.view;
	}

	public void setView(BankView view) {
		this.view = view;
	}

	@Override
	public void handle(ServerPlayer player, EconomyActionPayload action) {
		if (this.bank == null) {
			return;
		}
		this.bank.handle(player, action);
		this.view = this.bank.view(player);
		ServerPlayNetworking.send(player, new BankViewPayload(this.containerId, this.view));
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
