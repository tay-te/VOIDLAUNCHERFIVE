package dev.voidmc.hub.screen;

import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.mettle.MettleLink;
import dev.voidmc.hub.model.Bridge;
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
 * The VOID inventory over {@code player.inventoryMenu}: armour, offhand, 2x2 crafting with its
 * result, 27 storage and 9 hotbar slots, the live player model (drag to turn), and the
 * hovered-item card.
 *
 * <p>With VOID Mettle installed the window has two more pages over the same menu, switched by the
 * segments: <b>Gear</b> (the worn backpack's pockets, belt and quiver, and the load) and
 * <b>Classes</b> (background, attributes, skills and their perk trees, drawn by the page from
 * Mettle's state message). See {@link MettleLink}.
 */
public class HubInventoryScreen extends HubScreen<InventoryMenu> {

	private static final int[] STORAGE = range(9, 27);
	private static final int[] HOTBAR = range(36, 9);

	/** Rebuild Mettle's state at most this often (ticks) while the window is open. */
	private static final int METTLE_EVERY = 10;

	private String page;
	private String sentMettle;
	private boolean sentIcons;
	private int mettleAge = Integer.MAX_VALUE;

	public HubInventoryScreen(Player player) {
		this(player, "inventory");
	}

	/** Opens on a page: "inventory", or with Mettle "gear" / "classes". */
	public HubInventoryScreen(Player player, String page) {
		// Vanilla's InventoryScreen panel size: the pointer mapping uses vanilla's own geometry.
		super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"), 176, 166);
		this.page = MettleLink.hasGear(player.inventoryMenu) ? page : "inventory";
	}

	public String page() {
		return this.page;
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
		HubLayout.GearSlots gear = MettleLink.hasGear(this.menu) ? HubLayout.GearSlots.METTLE : null;
		return switch (this.page) {
			case "gear" -> gear == null ? HubLayout.inventory(fbW, fbH, guiScale) : HubLayout.gear(fbW, fbH, guiScale, gear);
			case "classes" -> HubLayout.classes(fbW, fbH, guiScale);
			default -> HubLayout.inventory(fbW, fbH, guiScale, gear);
		};
	}

	/** Switches page in place: same menu, same carried stack, a new layout. */
	public void setPage(String next) {
		if (next.equals(this.page) || !("inventory".equals(next) || MettleLink.hasGear(this.menu))) {
			return;
		}
		this.page = next;
		this.mettleAge = Integer.MAX_VALUE;
		this.relayout();
		this.resendLayout();
	}

	@Override
	protected void onMessage(Bridge.Message m) {
		switch (m) {
			case Bridge.Segment seg -> this.setPage(seg.value());
			case Bridge.MettleCall call -> {
				switch (call.op()) {
					case "learnPerk" -> MettleLink.learnPerk(call.arg());
					case "spendAttribute" -> MettleLink.spendAttribute(call.arg());
					default -> {
					}
				}
				this.mettleAge = METTLE_EVERY - 2;  // the server's answer lands in a tick or two
			}
			default -> {
			}
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (this.mettleAge < Integer.MAX_VALUE) {
			this.mettleAge++;
		}
	}

	/**
	 * Mettle's state, rebuilt every {@link #METTLE_EVERY} ticks and sent only when it changed; the
	 * icons it names go once, as data URLs (the page cannot read another mod's assets).
	 */
	@Override
	protected void pushExtra() {
		if (!MettleLink.hasGear(this.menu)) {
			return;
		}
		if (this.sentMettle != null && this.mettleAge < METTLE_EVERY) {
			return;
		}
		this.mettleAge = 0;
		String json = MettleLink.state();
		if (json == null || json.equals(this.sentMettle)) {
			return;
		}
		if (!this.sentIcons) {
			this.sentIcons = true;
			this.hub.send(icons(json));
		}
		this.hub.sendLatest("mettle", "{\"type\":\"mettle\",\"state\":" + json + "}");
		this.sentMettle = json;
	}

	/** Every {@code "icon"} / {@code "art"} texture the state names, as one icons message. */
	static String icons(String stateJson) {
		java.util.Set<String> ids = new java.util.TreeSet<>();
		collect(Json.parseObject(stateJson), ids);
		Json.Writer map = Json.object();
		for (String id : ids) {
			String url = MettleLink.icon(id);
			if (url != null) {
				map.put(id, url);
			}
		}
		return Json.object().put("type", "icons").raw("map", map.end()).end();
	}

	private static void collect(Object node, java.util.Set<String> out) {
		if (node instanceof java.util.Map<?, ?> m) {
			for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
				if (("icon".equals(e.getKey()) || "art".equals(e.getKey())) && e.getValue() instanceof String s && s.endsWith(".png")) {
					out.add(s);
				} else {
					collect(e.getValue(), out);
				}
			}
		} else if (node instanceof java.util.List<?> l) {
			for (Object o : l) {
				collect(o, out);
			}
		}
	}

	@Override
	protected int[] region(String name) {
		return switch (name) {
			case "storage" -> STORAGE;
			case "hotbar" -> HOTBAR;
			// The Gear page's "container" is the worn pack: its pockets that are switched on.
			case "container" -> "gear".equals(this.page) && MettleLink.hasGear(this.menu) ? this.activePack() : new int[0];
			default -> new int[0];
		};
	}

	private int[] activePack() {
		return java.util.stream.IntStream.range(MettleLink.PACK_FIRST, MettleLink.PACK_FIRST + MettleLink.PACK_SLOTS)
			.filter(i -> this.menu.slots.get(i).isActive()).toArray();
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
		w.put("page", this.page);
		w.put("mettle", MettleLink.hasGear(this.menu));
		if (MettleLink.hasGear(this.menu)) {
			// Pockets the worn gear does not have are switched off: the page draws them as absent.
			int[] off = java.util.stream.IntStream.range(MettleLink.PACK_FIRST, MettleLink.MENU_SIZE)
				.filter(i -> !this.menu.slots.get(i).isActive()).toArray();
			w.putInts("inactive", off);
		}
		w.put("armor", p.getArmorValue());
		w.put("level", p.experienceLevel);
		w.put("xp", Math.round(p.experienceProgress * 100) / 100.0);
	}

	@Override
	protected void extractExtras(GuiGraphicsExtractor graphics, double guiMouseX, double guiMouseY) {
		Rect box = this.layout.player;
		if (box == null) {
			return;  // the Gear and Classes pages have no figure
		}
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
