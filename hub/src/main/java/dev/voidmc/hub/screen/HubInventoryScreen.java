package dev.voidmc.hub.screen;

import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.map.HubMap;
import dev.voidmc.hub.model.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The hub (R-Shift) and the VOID inventory (E), over {@code player.inventoryMenu}. Its pages are
 * the hub's: <b>Inventory</b> — armour, offhand, 2x2 crafting with its result, 27 storage and 9
 * hotbar slots, the live player model (drag to turn) and the hovered-item card — and <b>Map</b>,
 * VOID Atlas's map in the hub's window (a stand-in without Atlas). Tab, Shift-Tab or the pages
 * island switch between them; the menu stays open underneath either way.
 */
public class HubInventoryScreen extends HubScreen<InventoryMenu> {

	public static final String MAP = "map";
	public static final String INVENTORY = "inventory";

	private static final int[] STORAGE = range(9, 27);
	private static final int[] HOTBAR = range(36, 9);

	/** The page the hub was last left on (R-Shift opens there); null until the hub first opens. */
	private static String lastPage;

	private String page;

	/** The inventory page (E). */
	public HubInventoryScreen(Player player) {
		this(player, INVENTORY);
	}

	public HubInventoryScreen(Player player, String page) {
		// Vanilla's InventoryScreen panel size: the pointer mapping uses vanilla's own geometry.
		super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"), 176, 166);
		this.page = MAP.equals(page) ? MAP : INVENTORY;
		lastPage = this.page;
	}

	/**
	 * The page R-Shift opens the hub on: where it was left, and at first the Map when VOID Atlas is
	 * installed (the Inventory otherwise, rather than a stand-in).
	 */
	public static String rShiftPage() {
		if (lastPage != null) {
			return lastPage;
		}
		return HubMap.available() ? MAP : INVENTORY;
	}

	/** Forgets the last page (a new world, or tests). */
	public static void resetLastPage() {
		lastPage = null;
	}

	/** Sets the page the next R-Shift opens on (tests). */
	public static void rememberPage(String page) {
		lastPage = MAP.equals(page) ? MAP : INVENTORY;
	}

	@Override
	public String page() {
		return this.page;
	}

	@Override
	protected boolean hasPages() {
		return true;
	}

	@Override
	public void showPage(String page) {
		if (!MAP.equals(page) && !INVENTORY.equals(page) || page.equals(this.page)) {
			return;  // Quests, Mods and Friends are not built yet
		}
		this.page = page;
		lastPage = page;
		this.pageChanged();
	}

	static int[] range(int start, int n) {
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = start + i;
		}
		return out;
	}

	@Override
	protected HubLayout makeLayout(int fbW, int fbH, int guiScale) {
		return MAP.equals(this.page) ? HubLayout.map(fbW, fbH, guiScale) : HubLayout.inventory(fbW, fbH, guiScale);
	}

	@Override
	protected int[] region(String name) {
		return switch (name) {
			case "storage" -> STORAGE;
			case "hotbar" -> HOTBAR;
			default -> new int[0];
		};
	}

	@Override
	protected void writeState(Json.Writer w) {
		Player p = this.minecraft.player;
		int used = 0;
		for (int i = 9; i < 45; i++) {
			if (this.menu.slots.get(i).hasItem()) {
				used++;
			}
		}
		w.put("used", used);
		w.put("total", 36);
		w.put("armor", p.getArmorValue());
		w.put("level", p.experienceLevel);
		w.put("xp", Math.round(p.experienceProgress * 100) / 100.0);
	}

	@Override
	protected void extractExtras(GuiGraphicsExtractor graphics, double guiMouseX, double guiMouseY) {
		Rect box = this.layout.player;
		int gs = this.minecraft.getWindow().getGuiScale();
		// The model stands in the box above the "DRAG TO TURN" label.
		int x0 = (int) Math.ceil(box.x() / (double) gs);
		int y0 = (int) Math.ceil(box.y() / (double) gs);
		int x1 = (int) Math.floor(box.right() / (double) gs);
		int y1 = (int) Math.floor((box.y() + box.h() * 0.84) / (double) gs);
		int size = Math.max(8, (int) Math.round((y1 - y0) * 0.44));
		extractPlayer(graphics, x0, y0, x1, y1, size, this.yaw, (float) guiMouseY, this.minecraft.player);
	}

	/**
	 * {@code InventoryScreen.extractEntityInInventoryFollowsMouse}, with the body turned by
	 * {@code yaw} (dragged) instead of only following the pointer.
	 */
	static void extractPlayer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float yaw, float mouseY, LivingEntity entity) {
		float centerY = (y0 + y1) / 2.0F;
		float yAngle = (float) Math.atan((centerY - mouseY) / 40.0F);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf xRotation = new Quaternionf().rotateX(yAngle * 20.0F * (float) (Math.PI / 180.0));
		rotation.mul(xRotation);
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + yaw;
			living.yRot = 0.0F;
			living.xRot = living.pose != Pose.FALL_FLYING ? -yAngle * 20.0F : 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		graphics.entity(state, size, translation, rotation, xRotation, x0, y0, x1, y1);
	}
}
