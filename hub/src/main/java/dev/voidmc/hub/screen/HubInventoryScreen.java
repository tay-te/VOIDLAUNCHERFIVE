package dev.voidmc.hub.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.map.HubMap;
import dev.voidmc.hub.mettle.MettleLink;
import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.model.TooltipModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;
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
 *
 * <p>With VOID Mettle installed the Inventory page has two more segments over the same menu:
 * <b>Gear</b> (the worn backpack's pockets, belt and quiver, and the load) and <b>Classes</b>
 * (background, attributes, skills and their cards, drawn by the page from Mettle's state
 * message). See {@link MettleLink}.
 */
public class HubInventoryScreen extends HubScreen<InventoryMenu> {

	private static final int[] STORAGE = range(9, 27);
	private static final int[] HOTBAR = range(36, 9);

	/** Rebuild Mettle's state at most this often (ticks) while the window is open. */
	private static final int METTLE_EVERY = 10;

	public static final String MAP = "map";
	public static final String INVENTORY = "inventory";

	/** The page the hub was last left on (R-Shift opens there); null until the hub first opens. */
	private static String lastPage;

	/** The hub page: {@link #MAP} or {@link #INVENTORY}. */
	private String page;
	/** The Inventory page's segment: "inventory", or with Mettle "gear" / "classes". */
	private String segment;
	private String sentMettle;
	/** The state last sent, without the ages of chips and moments that change every build. */
	private String sentMettleKey;
	/** The Classes page has a draft, reveal or moment up (Esc and 1-3 are its), or a search field has the keyboard. */
	private boolean pageModal;
	private boolean pageText;
	/** The Classes page's moving parts, drawn natively over the still page. */
	private final CardFx fx = new CardFx();
	private boolean sentIcons;
	private int mettleAge = Integer.MAX_VALUE;
	/** Stacks the Classes page shows in its own wells (the trade's items), drawn natively at its rects. */
	private List<Bridge.ItemIcon> pageItems = List.of();
	/** The players within trade reach last told to the page (the trade request list), and when to look again. */
	private String sentNearby;

	/** Mettle's trade reach (TradeRules: 8 blocks). */
	public static final double TRADE_REACH = 8.0;
	/** Tests: stands in for the players in reach (name, distance), since a server-side stand-in is not in the client's level. */
	public static volatile List<java.util.Map.Entry<String, Double>> testNearby;

	/** The inventory page (E). */
	public HubInventoryScreen(Player player) {
		this(player, INVENTORY);
	}

	/** Opens on a page: "map", "inventory", or with Mettle the Inventory page's "gear" / "classes" segment. */
	public HubInventoryScreen(Player player, String page) {
		// Vanilla's InventoryScreen panel size: the pointer mapping uses vanilla's own geometry.
		super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"), 176, 166);
		this.page = MAP.equals(page) ? MAP : INVENTORY;
		boolean seg = "gear".equals(page) || "classes".equals(page);
		this.segment = seg && MettleLink.hasGear(player.inventoryMenu) ? page : INVENTORY;
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

	/** The hub page: "map" or "inventory". */
	@Override
	public String page() {
		return this.page;
	}

	/** The Inventory page's segment: "inventory", "gear" or "classes". */
	public String segment() {
		return this.segment;
	}

	private boolean onClasses() {
		return INVENTORY.equals(this.page) && "classes".equals(this.segment);
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
		this.leaveSegment();
		this.pageChanged();
		this.resendLayout();
	}

	/** The Classes page's moving parts and keyboard claims end with it. */
	private void leaveSegment() {
		this.fx.clear();
		this.pageItems = List.of();
		this.sentNearby = null;
		this.pageModal = false;
		this.pageText = false;
		this.mettleAge = Integer.MAX_VALUE;
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
		if (MAP.equals(this.page)) {
			return HubLayout.map(fbW, fbH, guiScale, HubMap.scale());
		}
		HubLayout.GearSlots gear = MettleLink.hasGear(this.menu) ? HubLayout.GearSlots.METTLE : null;
		return switch (this.segment) {
			case "gear" -> gear == null ? HubLayout.inventory(fbW, fbH, guiScale) : HubLayout.gear(fbW, fbH, guiScale, gear);
			case "classes" -> HubLayout.classes(fbW, fbH, guiScale);
			default -> HubLayout.inventory(fbW, fbH, guiScale, gear);
		};
	}

	/** Switches the Inventory page's segment in place: same menu, same carried stack, a new layout. */
	public void setSegment(String next) {
		if (MAP.equals(this.page) || next.equals(this.segment) || !("inventory".equals(next) || MettleLink.hasGear(this.menu))) {
			return;
		}
		this.segment = next;
		this.leaveSegment();
		this.relayout();
		this.resendLayout();
	}

	@Override
	protected void onMessage(Bridge.Message m) {
		switch (m) {
			case Bridge.Segment seg -> this.setSegment(seg.value());
			case Bridge.MettleChoose choose -> {
				MettleLink.chooseBackground(choose.json());
				this.mettleAge = METTLE_EVERY - 2;
			}
			case Bridge.MettleCall call -> {
				MettleLink.act(call.op(), call.arg());
				if (!"cardSound".equals(call.op())) {
					this.mettleAge = METTLE_EVERY - 2;  // the server's answer lands in a tick or two
				}
			}
			case Bridge.Fx f -> {
				if (this.onClasses()) {
					this.fx.set(f.items());
				}
			}
			case Bridge.Items it -> {
				if (this.onClasses()) {
					this.pageItems = it.items();
				}
			}
			case Bridge.MettleUi ui -> {
				this.pageModal = ui.modal();
				this.pageText = ui.text();
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
		if (!MettleLink.hasGear(this.menu) || MAP.equals(this.page)) {
			return;
		}
		if (this.sentMettle != null && this.mettleAge < METTLE_EVERY) {
			return;
		}
		this.mettleAge = 0;
		this.pushNearby();
		String json = MettleLink.state();
		if (json == null || json.equals(this.sentMettle)) {
			return;
		}
		// Chips and moments carry their age in ms, so the state differs on every build while one shows: send it when
		// something else changed (the page diffs what it draws; a message it would ignore still costs a parse).
		String key = AGES.matcher(json).replaceAll("");
		if (key.equals(this.sentMettleKey)) {
			return;
		}
		this.sentMettleKey = key;
		if (!this.sentIcons) {
			this.sentIcons = true;
			this.hub.send(icons(json));
		}
		this.hub.sendLatest("mettle", "{\"type\":\"mettle\",\"state\":" + json + "}");
		this.sentMettle = json;
	}

	/**
	 * The players a trade can be asked of (within {@link #TRADE_REACH}, nearest first), for the Classes page's
	 * "Request trade" list: sent with Mettle's state, only when it changed.
	 */
	private void pushNearby() {
		if (!this.onClasses()) {
			return;
		}
		List<java.util.Map.Entry<String, Double>> near = nearby(this.minecraft);
		List<String> rows = new ArrayList<>();
		for (java.util.Map.Entry<String, Double> e : near) {
			rows.add(Json.object().put("name", e.getKey()).put("d", Math.round(e.getValue() * 10) / 10.0).end());
		}
		String json = Json.object().put("type", "nearby").raw("players", Json.array(rows)).end();
		if (!json.equals(this.sentNearby)) {
			this.sentNearby = json;
			this.hub.send(json);
		}
	}

	static List<java.util.Map.Entry<String, Double>> nearby(Minecraft mc) {
		List<java.util.Map.Entry<String, Double>> test = testNearby;
		if (test != null) {
			return test;
		}
		List<java.util.Map.Entry<String, Double>> out = new ArrayList<>();
		if (mc.level == null || mc.player == null) {
			return out;
		}
		for (Player p : mc.level.players()) {
			if (p == mc.player || p.isSpectator() || !p.isAlive()) {
				continue;
			}
			double d = p.distanceTo(mc.player);
			if (d <= TRADE_REACH) {
				out.add(java.util.Map.entry(p.getName().getString(), d));
			}
		}
		out.sort(java.util.Map.Entry.comparingByValue());
		return out.size() > 8 ? out.subList(0, 8) : out;
	}

	private static final java.util.regex.Pattern AGES = java.util.regex.Pattern.compile("\"ageMs\":-?\\d+");

	// ---- the Classes page's keys and wheel ---------------------------------------------------------------------

	/** Keys the Classes page uses, by GLFW key code. */
	private static String pageKey(int key) {
		if (key == InputConstants.KEY_ESCAPE) {
			return "Escape";
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			return "Enter";
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			return "Backspace";
		}
		if (key == InputConstants.KEY_LEFT) {
			return "Left";
		}
		if (key == InputConstants.KEY_RIGHT) {
			return "Right";
		}
		if (key == InputConstants.KEY_R) {
			return "r";
		}
		return key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9 ? String.valueOf((char) ('1' + key - InputConstants.KEY_1)) : null;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (this.onClasses() && MettleLink.present()) {
			String name = pageKey(event.key());
			if (this.pageText) {
				if (name != null && !name.equals("r") && !Character.isDigit(name.charAt(0))) {
					this.hub.send(Json.object().put("type", "key").put("key", name).end());
				}
				return true;  // typing in the search field: no hotbar keys, no E closing the window
			}
			if (name != null && (this.pageModal || !name.equals("Escape"))) {
				this.hub.send(Json.object().put("type", "key").put("key", name).end());
				if (this.pageModal) {
					return true;  // Esc closes the draft or the moment, not the window; 1-3 pick
				}
			}
			if (this.pageModal && event.isCycleFocus()) {
				return true;  // a draft or moment keeps the page: Tab does not switch to the Map under it
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (this.onClasses() && this.pageText && event.isAllowedChatCharacter()) {
			this.hub.send(Json.object().put("type", "key").put("ch", event.codepointAsString()).end());
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.onClasses() && MettleLink.present()) {
			int gs = this.minecraft.getWindow().getGuiScale();
			this.hub.send(Json.object().put("type", "wheel").put("x", this.layout.physToCss(x * gs)).put("y", this.layout.physToCss(y * gs))
				.put("dy", scrollY).end());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void removed() {
		super.removed();
		this.fx.clear();
		if (MettleLink.present()) {
			MettleLink.act("cardSound", "stop#");  // a draft or reveal left half-played goes quiet with the window
		}
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
			case "container" -> "gear".equals(this.segment) && INVENTORY.equals(this.page) && MettleLink.hasGear(this.menu) ? this.activePack() : new int[0];
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
		w.put("segment", this.segment);
		w.put("mettle", MettleLink.hasGear(this.menu));
		if (MettleLink.hasGear(this.menu)) {
			// Pockets the worn gear does not have are switched off: the page draws them as absent.
			int[] off = java.util.stream.IntStream.range(MettleLink.PACK_FIRST, MettleLink.MENU_SIZE)
				.filter(i -> !this.menu.slots.get(i).isActive()).toArray();
			w.putInts("inactive", off);
		}
		this.writeEffects(w, p);
		w.put("armor", p.getArmorValue());
		w.put("level", p.experienceLevel);
		w.put("xp", Math.round(p.experienceProgress * 100) / 100.0);
	}

	/** The effects the Effects card lists, in vanilla's order; their icons are drawn natively in its wells. */
	private List<MobEffectInstance> effects = List.of();

	private void writeEffects(Json.Writer w, Player p) {
		List<MobEffectInstance> list = new ArrayList<>();
		for (MobEffectInstance e : p.getActiveEffects()) {
			if (e.showIcon()) {
				list.add(e);
			}
		}
		list.sort(null);
		this.effects = list;
		List<String> out = new ArrayList<>();
		for (MobEffectInstance e : list) {
			String name = e.getEffect().value().getDisplayName().getString();
			out.add(Json.object()
				.put("id", e.getEffect().getRegisteredName())
				.put("name", name)
				.put("amp", e.getAmplifier() > 0 ? TooltipModel.roman(e.getAmplifier() + 1) : "")
				.put("secs", e.isInfiniteDuration() ? -1 : e.getDuration() / 20)
				.put("colour", String.format(java.util.Locale.ROOT, "#%06X", e.getEffect().value().getColor() & 0xFFFFFF))
				.end());
		}
		w.raw("effects", Json.array(out));
	}

	private void extractEffects(GuiGraphicsExtractor graphics) {
		List<Rect> wells = this.layout.effectIcons;
		if (wells.isEmpty() || this.effects.isEmpty()) {
			return;
		}
		int gs = this.minecraft.getWindow().getGuiScale();
		graphics.pose().pushMatrix();
		graphics.pose().scale(1.0F / gs, 1.0F / gs);
		for (int i = 0; i < wells.size() && i < this.effects.size(); i++) {
			Rect r = wells.get(i);
			if (this.tip != null && this.tip.intersects(r)) {
				continue;
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(this.effects.get(i).getEffect()), r.x(), r.y(), r.w(), r.h());
		}
		graphics.pose().popMatrix();
	}

	/** The page's item wells (the trade): the stacks at the icon size of the inventory's wells, centred in each. */
	private void extractPageItems(GuiGraphicsExtractor graphics) {
		if (this.pageItems.isEmpty()) {
			return;
		}
		int gs = this.minecraft.getWindow().getGuiScale();
		double u = this.layout.u;
		int icon = this.layout.iconPx;
		for (Bridge.ItemIcon it : this.pageItems) {
			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(it.item());
			if (id == null || it.count() <= 0) {
				continue;
			}
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);
			if (item.isEmpty()) {
				continue;
			}
			net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item.get(), it.count());
			// Centred in the well on whole physical px; whole multiples of 16 keep the item's pixel art crisp.
			int px = (int) Math.round((it.x() + it.w() / 2.0) * u - icon / 2.0);
			int py = (int) Math.round((it.y() + it.h() / 2.0) * u - icon / 2.0);
			float sc = icon / (16.0F * gs);
			graphics.pose().pushMatrix();
			graphics.pose().translate(px / (float) gs, py / (float) gs);
			graphics.pose().scale(sc, sc);
			graphics.item(stack, 0, 0);
			graphics.itemDecorations(this.font, stack, 0, 0);
			graphics.pose().popMatrix();
		}
	}

	/** Tests: the stacks the page last asked to be drawn. */
	public List<Bridge.ItemIcon> pageItems() {
		return this.pageItems;
	}

	@Override
	protected void extractExtras(GuiGraphicsExtractor graphics, double guiMouseX, double guiMouseY) {
		this.extractEffects(graphics);
		if (this.onClasses()) {
			this.extractPageItems(graphics);
		}
		if (this.onClasses() && this.fx.active() && this.hub.texture() != null) {
			this.fx.extract(graphics, this.layout.u, this.minecraft.getWindow().getGuiScale(), this.hub.texture().width(), this.hub.texture().height());
		}
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
