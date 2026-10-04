package dev.voidmc.hub.screen;

import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.model.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The VOID chest over any {@code ChestMenu} (chest, barrel, ender chest, minecart chest; 1-6
 * rows). Three rows show at a time; longer containers scroll.
 */
public class HubChestScreen extends HubScreen<ChestMenu> {

	private final int rows;
	private final int[] container;
	private final int[] storage;
	private final int[] hotbar;
	private final BlockPos pos;

	public HubChestScreen(ChestMenu menu, Inventory inventory, Component title) {
		// Vanilla's ContainerScreen panel size for this many rows.
		super(menu, inventory, title, 176, 114 + menu.getRowCount() * 18);
		this.rows = menu.getRowCount();
		this.container = HubInventoryScreen.range(0, this.rows * 9);
		this.storage = HubInventoryScreen.range(this.rows * 9, 27);
		this.hotbar = HubInventoryScreen.range(this.rows * 9 + 27, 9);
		HitResult hit = net.minecraft.client.Minecraft.getInstance().hitResult;
		this.pos = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK ? bhr.getBlockPos() : null;
	}

	@Override
	protected HubLayout makeLayout(int fbW, int fbH, int guiScale) {
		return HubLayout.chest(fbW, fbH, guiScale, this.rows, this.scrollRow);
	}

	@Override
	protected int[] region(String name) {
		return switch (name) {
			case "container" -> this.container;
			case "storage" -> this.storage;
			case "hotbar" -> this.hotbar;
			default -> new int[0];
		};
	}

	@Override
	protected void writeState(Json.Writer w) {
		int used = 0;
		for (int i : this.container) {
			if (this.menu.slots.get(i).hasItem()) {
				used++;
			}
		}
		w.put("title", this.title.getString());
		w.put("coords", this.pos == null ? "" : "X " + this.pos.getX() + "  ·  Y " + this.pos.getY() + "  ·  Z " + this.pos.getZ());
		w.put("used", used);
		w.put("total", this.container.length);
	}
}
