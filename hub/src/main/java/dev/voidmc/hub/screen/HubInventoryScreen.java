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

	/** Look for players in trade reach this often (ticks) while the window is open. */
	private static final int NEARBY_EVERY = 10;

	public static final String MAP = "map";
	public static final String INVENTORY = "inventory";
	/** VOID Hearth's Quests page: journal, notice board, reputation and finished quests (with Hearth installed). */
	public static final String QUESTS = "quests";

	/** The Quests page's segment: "journal", "board", "reputation" or "finished" (the page keeps it; Java sets it on open). */
	private String questSeg = "journal";
	/** The Quests page was told its screen and segment for this open (or this page switch). */
	private boolean questsSent;
	/** A tick passed: Hearth's revision is read again (HearthSync). */
	private boolean hearthCheck = true;

	/** The page the hub was last left on (R-Shift opens there); null until the hub first opens. */
	private static String lastPage;

	/** The hub page: {@link #MAP} or {@link #INVENTORY}. */
	private String page;
	/** The Inventory page's segment: "inventory", or with Mettle "gear" / "classes". */
	private String segment;
	/** A tick passed: Mettle's state signature is read again (MettleSync). */
	private boolean mettleCheck = true;
	/** The Classes page has a draft, reveal, moment, menu, popover or keyboard focus up (Esc, arrows and 1-3 are its), or a search field has the keyboard. */
	private boolean pageModal;
	private boolean pageText;
	/** The Classes page's moving parts, drawn natively over the still page. */
	private final CardFx fx = new CardFx();
	private int nearbyAge = Integer.MAX_VALUE;
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
		this.page = MAP.equals(page) ? MAP : QUESTS.equals(page) && dev.voidmc.hub.hearth.HearthLink.present() ? QUESTS : INVENTORY;
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

	/**
	 * Mettle's first-join background picker, hosted on the Classes page. It is not a page the player chose, so it is not
	 * remembered: the first R-Shift after it still opens on the first page (the Map with VOID Atlas). A page the player
	 * picks from the pages island while it is up is remembered as usual.
	 */
	public static HubInventoryScreen picker(Player player) {
		String before = lastPage;
		HubInventoryScreen screen = new HubInventoryScreen(player, "classes");
		lastPage = before;
		return screen;
	}

	/** Whether a page is remembered for R-Shift (tests). */
	public static boolean remembersPage() {
		return lastPage != null;
	}

	/** Forgets the last page (a new world, or tests). */
	public static void resetLastPage() {
		lastPage = null;
	}

	/** Sets the page the next R-Shift opens on (tests). */
	public static void rememberPage(String page) {
		lastPage = MAP.equals(page) ? MAP : QUESTS.equals(page) ? QUESTS : INVENTORY;
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
		boolean quests = QUESTS.equals(page) && dev.voidmc.hub.hearth.HearthLink.present();
		if (!MAP.equals(page) && !INVENTORY.equals(page) && !quests || page.equals(this.page)) {
			return;  // Mods and Friends are not built yet; Quests needs Hearth
		}
		if (QUESTS.equals(this.page)) {
			this.leaveQuests();
		}
		this.page = page;
		lastPage = page;
		this.questsSent = false;
		this.leaveSegment();
		this.pageChanged();
		this.resendLayout();
	}

	private static final List<String> PAGES_HEARTH = List.of(MAP, INVENTORY, QUESTS);

	@Override
	protected List<String> pages() {
		return dev.voidmc.hub.hearth.HearthLink.present() ? PAGES_HEARTH : super.pages();
	}

	/** Sets the Quests page's segment before this screen opens (Hearth's journal, board or reputation opener). */
	public void questSegment(String seg) {
		this.questSeg = seg;
	}

	/** Shows the Quests page on a segment (an opener while the hub is open). */
	public void showQuests(String seg) {
		this.questSeg = seg;
		this.questsSent = false;
		this.showPage(QUESTS);
	}

	public String questSeg() {
		return this.questSeg;
	}

	/**
	 * Leaving the Quests page: a board or reputation page Hearth handed the hub is done with (Hearth forgets the board's
	 * view), and the page's keyboard claims go.
	 */
	private void leaveQuests() {
		String hosted = dev.voidmc.hub.hearth.HearthLink.hosted();
		if ("board".equals(hosted) || "journal".equals(hosted) || "reputation".equals(hosted)) {
			dev.voidmc.hub.hearth.HearthLink.close(hosted);
		}
		this.pageModal = false;
		this.pageText = false;
	}

	/** The Classes page's moving parts and keyboard claims end with it. */
	private void leaveSegment() {
		this.fx.clear();
		this.pageItems = List.of();
		this.sentNearby = null;
		this.pageModal = false;
		this.pageText = false;
		this.nearbyAge = Integer.MAX_VALUE;
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
		if (QUESTS.equals(this.page)) {
			return HubLayout.quests(fbW, fbH, guiScale);
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
		if (!INVENTORY.equals(this.page) || next.equals(this.segment) || !("inventory".equals(next) || MettleLink.hasGear(this.menu))) {
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
			// The server's answer changes Mettle's client state: MettleSync sees it within a tick.
			case Bridge.MettleChoose choose -> MettleLink.chooseBackground(choose.json());
			case Bridge.MettleCall call -> MettleLink.act(call.op(), call.arg());
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
				if (this.onClasses()) {
					this.pageModal = ui.modal();
					this.pageText = ui.text();
				}
			}
			// The Quests page: Hearth's server answers in the state, which HearthSync sends within a tick.
			case Bridge.HearthCall call -> dev.voidmc.hub.hearth.HearthLink.act(call.op(), call.arg());
			case Bridge.HearthUi ui -> {
				if (QUESTS.equals(this.page)) {
					this.pageModal = ui.modal();
					this.pageText = ui.text();
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.mettleCheck = true;
		this.hearthCheck = true;
		if (this.nearbyAge < Integer.MAX_VALUE) {
			this.nearbyAge++;
		}
	}

	/**
	 * Mettle's state, rebuilt only when what it is built from changed ({@link dev.voidmc.hub.mettle.MettleSync}: a
	 * signature once a tick, the text written off the render thread, sent only when it differs from the page's copy);
	 * the icons it names go once each, as data URLs (the page cannot read another mod's assets).
	 */
	@Override
	protected void pushExtra() {
		if (QUESTS.equals(this.page)) {
			if (!this.questsSent) {
				// Every open and page switch tells the page its screen and segment; it answers with its keyboard claims.
				this.questsSent = true;
				this.hub.sendLatest("hearthScreen", Json.object().put("type", "hearthScreen").put("screen", QUESTS).put("seg", this.questSeg).end());
			}
			dev.voidmc.hub.hearth.HearthSync.frame(this.hub, this.hearthCheck);
			this.hearthCheck = false;
			return;
		}
		if (!MettleLink.hasGear(this.menu) || MAP.equals(this.page)) {
			return;
		}
		dev.voidmc.hub.mettle.MettleSync.frame(this.hub, this.mettleCheck);
		this.mettleCheck = false;
		if (this.nearbyAge >= NEARBY_EVERY) {
			this.nearbyAge = 0;
			this.pushNearby();
		}
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
		if (key == InputConstants.KEY_UP) {
			return "Up";
		}
		if (key == InputConstants.KEY_DOWN) {
			return "Down";
		}
		if (key == InputConstants.KEY_R) {
			return "r";
		}
		if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
			return String.valueOf((char) ('1' + key - InputConstants.KEY_NUMPAD1));
		}
		return key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9 ? String.valueOf((char) ('1' + key - InputConstants.KEY_1)) : null;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (QUESTS.equals(this.page)) {
			String name = HubEventScreen.pageKey(event.key());
			if (event.isEscape() && this.pageModal) {
				this.hub.send(Json.object().put("type", "hkey").put("key", "Escape").end());
				return true;
			}
			if (name != null && !event.isEscape()) {
				this.hub.send(Json.object().put("type", "hkey").put("key", name).end());
				if (!Character.isDigit(name.charAt(0))) {
					return true;  // arrows and Enter move the page's selection; digits still swap the hotbar
				}
			}
		}
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
				if (this.pageModal || name.equals("Up") || name.equals("Down") || name.equals("Left") || name.equals("Right")) {
					return true;  // Esc closes the draft, moment, menu or focus, not the window; 1-3 pick; arrows move the page's focus
				}
			}
			if (this.pageModal && event.isCycleFocus()) {
				// A draft, moment or keyboard focus keeps the page: Tab moves its focus, it does not switch to the Map under it.
				this.hub.send(Json.object().put("type", "key").put("key", event.hasShiftDown() ? "ShiftTab" : "Tab").end());
				return true;
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
		if (QUESTS.equals(this.page)) {
			this.leaveQuests();
		}
		super.removed();
		// What the page sent in this screen's last frame: a pick or a click still counts; native layers, keyboard
		// claims and animation sounds were this screen's and are dropped (the next screen gets the page's own resend).
		this.hub.drainInbox(m -> {
			if (m instanceof Bridge.MettleCall call) {
				MettleLink.act(call.op(), call.arg());
			} else if (m instanceof Bridge.MettleChoose choose) {
				MettleLink.chooseBackground(choose.json());
			} else if (m instanceof Bridge.HearthCall call) {
				dev.voidmc.hub.hearth.HearthLink.act(call.op(), call.arg());
			}
		});
		this.fx.clear();
		if (MettleLink.present()) {
			MettleLink.act("cardSound", "stop#");  // a draft or reveal left half-played goes quiet with the window
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
