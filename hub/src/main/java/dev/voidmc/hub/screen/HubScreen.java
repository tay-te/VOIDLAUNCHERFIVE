package dev.voidmc.hub.screen;

import com.mojang.blaze3d.platform.Window;
import dev.voidmc.hub.HubConfig;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.PointerMap;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.map.HubMap;
import dev.voidmc.hub.map.MapHost;
import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.model.CardPacer;
import dev.voidmc.hub.model.Click;
import dev.voidmc.hub.model.ClickPlanner;
import dev.voidmc.hub.model.ItemFacts;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.model.StackView;
import dev.voidmc.hub.model.TooltipModel;
import dev.voidmc.hub.ui.HubStats;
import dev.voidmc.hub.ui.HubView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A VOID-drawn screen over a real vanilla menu.
 *
 * <p>The menu, its slots, the carried stack, every click and every packet are vanilla's: this is an
 * {@link AbstractContainerScreen}, and mouse events reach its click logic unchanged except for
 * where they are (see {@link PointerMap}). What is VOID's is the drawing: Ultralight paints the
 * chrome (window, wells, rings, cards, chips) into one texture, Minecraft draws the item stacks,
 * counts, durability bars, the carried stack and the player model natively on top at the rects
 * the shared {@link HubLayout} gives both.
 */
public abstract class HubScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

	/** Filtered-out stacks are drawn under a veil of the shell colour. */
	private static final int VEIL = 0xC00B0B0C;

	protected final HubView hub;
	protected HubLayout layout;
	protected PointerMap.Geometry geometry;
	protected String filter = "all";
	protected int scrollRow;
	protected float yaw;
	private boolean turning;
	private double turnLastX;
	private String sentLayout;
	private String sentState;
	private final CardPacer cardPacer = new CardPacer();
	private boolean firstFrame;
	private final ArrayDeque<Click> pending = new ArrayDeque<>();
	private HubLayout.Cell hovered;
	private double lastCssX = Double.NaN;
	private double lastCssY = Double.NaN;
	/** Map page: a press began on the map, so its drag and release are the map's wherever they go. */
	private boolean mapGrab;
	/** Map page: the pointer is over the map, and the page was told it left (its islands unhovered). */
	private boolean pointerOnMap;
	/** Map page: the map has been told it is showing, this open (and must hear when it stops). */
	private boolean mapShown;
	/**
	 * Map page: the world behind it is Atlas's still (voidhub.frozenMap), and the hub, not the page,
	 * draws the 34 % dim: over the still through Atlas, over the live world (until the still exists)
	 * as a fill. The page's own dim stays off for the whole time, so the two are never stacked.
	 */
	private boolean frozen;
	/** The still was drawn this frame (tests). */
	private boolean stillDrawn;
	/** The hub's dim: #0B0B0C at 34 % (Figma's "dim · GL pass"). */
	private static final float DIM = 0.34F;
	private static final int DIM_ARGB = Math.round(DIM * 255) << 24 | 0x0B0B0C;
	/** "Waypoint here" feedback on the strip: what to say, and until when (nanoTime). */
	private String pillNote = "";
	private long pillNoteUntil;

	protected HubScreen(T menu, Inventory inventory, Component title, int vanillaW, int vanillaH) {
		super(menu, inventory, title, vanillaW, vanillaH);
		this.hub = HubView.get();
	}

	protected abstract HubLayout makeLayout(int fbW, int fbH, int guiScale);

	/** Screen-specific fields of the state message. */
	protected abstract void writeState(Json.Writer w);

	/** Screen-specific native drawing on top of the chrome (player model, ...). */
	protected void extractExtras(GuiGraphicsExtractor graphics, double guiMouseX, double guiMouseY) {
	}

	public HubLayout layout() {
		return this.layout;
	}

	public HubLayout.Cell hoveredCell() {
		return this.hovered;
	}

	public String filter() {
		return this.filter;
	}

	/** The hub page shown: "map" or "inventory" (a chest is its own page of the inventory). */
	public String page() {
		return "inventory";
	}

	/** Whether this screen is the hub, with its pages (the chest screen is not). */
	protected boolean hasPages() {
		return false;
	}

	/** Shows another hub page; only the hub itself has pages. */
	public void showPage(String page) {
	}

	/** Whether Atlas's still of the world was drawn behind the Map page this frame (tests). */
	public boolean stillDrawn() {
		return this.stillDrawn;
	}

	public boolean onMap() {
		return this.layout != null && this.layout.kind == HubLayout.Kind.MAP;
	}

	/** After a page switch: the new layout, and its first frame waits for the page's paint, as on open. */
	protected void pageChanged() {
		this.mapHidden();
		this.relayout();
		this.firstFrame = true;
		this.mapGrab = false;
		this.pointerOnMap = false;
		this.mapShown = false;
		this.hovered = null;
		this.hoveredSlot = null;
		this.tip = null;
		this.turning = false;
		this.lastCssX = Double.NaN;
		this.lastCssY = Double.NaN;
		HubStats.INSTANCE.lastScreenInitNs = System.nanoTime();
	}

	@Override
	protected void init() {
		super.init();
		HubStats.INSTANCE.lastScreenInitNs = System.nanoTime();
		Window w = this.minecraft.getWindow();
		this.layout = this.makeLayout(w.getWidth(), w.getHeight(), w.getGuiScale());
		this.geometry = geometryOf(this.menu, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
		this.hub.setListener(this::onBridge);
		this.hub.ensureSize(w.getWidth(), w.getHeight(), this.layout.u);
		this.sentLayout = null;
		this.sentState = null;
		this.cardPacer.reset();
		this.firstFrame = true;
		this.mapGrab = false;
		this.pointerOnMap = false;
		this.mapShown = false;
	}

	static PointerMap.Geometry geometryOf(AbstractContainerMenu menu, int left, int top, int w, int h) {
		int n = menu.slots.size();
		int[] xs = new int[n];
		int[] ys = new int[n];
		for (int i = 0; i < n; i++) {
			Slot s = menu.slots.get(i);
			xs[i] = s.x;
			ys[i] = s.y;
		}
		return new PointerMap.Geometry(left, top, w, h, xs, ys);
	}

	protected void relayout() {
		Window w = this.minecraft.getWindow();
		this.layout = this.makeLayout(w.getWidth(), w.getHeight(), w.getGuiScale());
	}

	// ---- frame -----------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// No vanilla dim or blur: the page draws the shell's flat dim itself.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Window window = this.minecraft.getWindow();
		int gs = window.getGuiScale();
		double gx = this.minecraft.mouseHandler.getScaledXPos(window);
		double gy = this.minecraft.mouseHandler.getScaledYPos(window);
		boolean map = this.onMap();
		HubLayout.Cell cell = map ? null : this.layout.cellAt(gx * gs, gy * gs);
		this.hovered = cell;
		Slot hoverSlot = null;
		if (cell != null) {
			Slot s = this.menu.slots.get(cell.slot());
			if (s.isActive()) {
				hoverSlot = s;
			}
		}
		this.hoveredSlot = hoverSlot;

		// 0. The Map page's backdrop. With the still on (the default), the hub draws the 34 % dim
		//    itself: over Atlas's still of the world (once Atlas has one the world is not drawn at
		//    all), or over the live world until then; the page's dim is off meanwhile.
		this.stillDrawn = false;
		if (map) {
			this.startMap();
			if (this.frozen) {
				MapHost host = HubMap.host();
				this.stillDrawn = host != null && host.drawFrozenWorld(graphics, DIM);
				if (!this.stillDrawn) {
					graphics.fill(0, 0, this.width, this.height, DIM_ARGB);
				}
			}
		}

		// 1. Tell the page what changed, then let Ultralight paint and upload the damage.
		this.hub.beginFrame();
		this.pushLayout();
		this.pushState();
		if (!map) {
			if (!this.layout.cells().isEmpty()) {
				this.pushDetail(cell);
			}
			if (this.layout.detailIcon != null) {
				this.pushHeld();
			}
			this.pushExtra();
		}
		if (this.firstFrame) {
			// The view still holds whatever the last VOID screen showed: let the UI thread paint
			// this one before its first frame goes out (bounded; it is usually a few ms).
			this.hub.awaitPainted(200_000_000L);
		}

		// 2. The chrome: one premultiplied quad, 1:1 with the framebuffer. (Submitted before this
		//    frame's upload below; the GUI is drawn after both.)
		graphics.pose().pushMatrix();
		graphics.pose().scale(1.0F / gs, 1.0F / gs);
		graphics.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, HubView.TEXTURE_ID, 0, 0, 0.0F, 0.0F,
			this.hub.width(), this.hub.height(), this.hub.texture().width(), this.hub.texture().height());
		graphics.pose().popMatrix();

		if (map) {
			// 3. The map, drawn natively by Atlas into the window the page drew.
			this.extractMap(graphics, mouseX, mouseY, gs);
		}
		this.hub.frame();
		// frame() delivers the page's messages: a page switch may just have changed the layout.
		if (!map && !this.onMap()) {
			// 3. Native stacks, at the same rects the page drew the wells at.
			this.extractStacks(graphics, mouseX, mouseY, gs);
			this.extractDetailIcon(graphics, gs);
			this.extractExtras(graphics, gx, gy);
		}

		// 4. The carried stack follows the pointer, at the wells' icon size.
		float s = this.layout.iconPx / (16.0F * gs);
		graphics.pose().pushMatrix();
		graphics.pose().translate((float) gx, (float) gy);
		graphics.pose().scale(s, s);
		graphics.pose().translate((float) -gx, (float) -gy);
		this.extractCarriedItem(graphics, mouseX, mouseY);
		graphics.pose().popMatrix();

		if (this.firstFrame) {
			this.firstFrame = false;
			HubStats.INSTANCE.lastScreenFirstFrameNs = System.nanoTime();
		}
	}

	/** Where the page's item tooltip is (physical px), or null: stacks under it are not drawn. */
	protected Rect tip;

	/**
	 * The Map page's window contents: Atlas's map page, through Minecraft's GUI pipeline, inside the
	 * window rectangle (on the GUI grid by construction). Without Atlas the page draws the stand-in
	 * and there is nothing to do here. The widget is made on the first call and kept.
	 */
	private void extractMap(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int gs) {
		MapHost host = HubMap.host();
		if (host == null) {
			return;
		}
		Rect w = this.layout.window;
		long t0 = System.nanoTime();
		host.render(graphics, w.x() / gs, w.y() / gs, w.w() / gs, w.h() / gs, mouseX, mouseY);
		HubStats.INSTANCE.noteMap(System.nanoTime() - t0);
	}

	private void extractStacks(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int gs) {
		Rect tip = this.menu.getCarried().isEmpty() ? this.tip : null;
		for (HubLayout.Cell cell : this.layout.cells()) {
			Slot slot = this.menu.slots.get(cell.slot());
			if (!slot.isActive() || tip != null && tip.intersects(cell.cell())) {
				continue;
			}
			boolean previewing = this.isQuickCrafting && this.quickCraftSlots.contains(slot);
			if (!slot.hasItem() && !previewing) {
				continue;  // empty wells and their placeholder art are the page's
			}
			Rect icon = cell.icon();
			float s = icon.w() / (16.0F * gs);
			graphics.pose().pushMatrix();
			graphics.pose().translate(icon.x() / (float) gs, icon.y() / (float) gs);
			graphics.pose().scale(s, s);
			graphics.pose().translate(-slot.x, -slot.y);
			this.extractSlot(graphics, slot, mouseX, mouseY);
			graphics.pose().popMatrix();
		}
		if (!"all".equals(this.filter)) {
			graphics.pose().pushMatrix();
			graphics.pose().scale(1.0F / gs, 1.0F / gs);
			for (HubLayout.Cell cell : this.layout.cells()) {
				Slot slot = this.menu.slots.get(cell.slot());
				if (slot.hasItem() && !ItemFactsReader.read(slot.getItem()).matchesFilter(this.filter)) {
					Rect c = cell.cell();
					graphics.fill(c.x() + 1, c.y() + 1, c.right() - 1, c.bottom() - 1, VEIL);
				}
			}
			graphics.pose().popMatrix();
		}
	}

	private void extractDetailIcon(GuiGraphicsExtractor graphics, int gs) {
		if (this.layout.detailIcon == null) {
			return;  // a page without the item card (Classes)
		}
		ItemStack stack = this.detailStack();
		if (stack.isEmpty()) {
			return;
		}
		Rect icon = this.layout.detailIcon;
		float s = icon.w() / (16.0F * gs);
		graphics.pose().pushMatrix();
		graphics.pose().translate(icon.x() / (float) gs, icon.y() / (float) gs);
		graphics.pose().scale(s, s);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();
	}

	// ---- page sync -------------------------------------------------------------------------------

	/** Screen-specific messages beyond layout, state and detail (Mettle's page); called every frame. */
	protected void pushExtra() {
	}

	/** Forces the layout message out again (a page switch inside one screen). */
	protected void resendLayout() {
		this.sentLayout = null;
	}

	private void pushLayout() {
		String json = this.layout.toJson();
		if (json != this.sentLayout) {
			this.hub.sendLatest("layout", json);
			this.sentLayout = json;
		}
	}

	private void pushState() {
		Json.Writer w = Json.object();
		w.put("type", "state");
		w.put("hover", this.hovered == null ? -1 : this.hovered.slot());
		w.put("held", this.minecraft.player.getInventory().getSelectedSlot());
		w.put("filter", this.filter);
		w.put("carrying", !this.menu.getCarried().isEmpty());
		int[] drag = new int[this.isQuickCrafting ? this.quickCraftSlots.size() : 0];
		if (this.isQuickCrafting) {
			int i = 0;
			for (Slot s : this.quickCraftSlots) {
				drag[i++] = s.index;
			}
			java.util.Arrays.sort(drag);
		}
		w.putInts("drag", drag);
		List<Integer> filled = new ArrayList<>();
		for (HubLayout.Cell c : this.layout.cells()) {
			if (this.menu.slots.get(c.slot()).hasItem()) {
				filled.add(c.slot());
			}
		}
		w.putInts("filled", filled.stream().mapToInt(Integer::intValue).toArray());
		w.put("world", worldName());
		w.put("day", this.minecraft.level == null ? 0 : this.minecraft.level.getOverworldClockTime() / 24000L + 1);
		w.put("page", this.page());
		w.put("atlas", HubMap.available());
		w.put("frozen", this.frozen);
		w.put("pill", System.nanoTime() < this.pillNoteUntil ? this.pillNote : "");
		this.writePurse(w);
		this.writeState(w);
		String json = w.end();
		if (!json.equals(this.sentState)) {
			this.hub.sendLatest("state", json);
			this.sentState = json;
		}
	}

	private String worldName() {
		if (this.minecraft.getSingleplayerServer() != null) {
			return this.minecraft.getSingleplayerServer().getWorldData().getLevelName();
		}
		return this.minecraft.getCurrentServer() != null ? this.minecraft.getCurrentServer().name : "World";
	}

	/** Hearth coins (copper bit / iron mark / gold crown), counted when Hearth is installed. */
	private void writePurse(Json.Writer w) {
		int copper = 0;
		int iron = 0;
		int gold = 0;
		Inventory inv = this.minecraft.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) {
				continue;
			}
			String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
			switch (path) {
				case "copper_bit" -> copper += st.getCount();
				case "iron_mark" -> iron += st.getCount();
				case "gold_crown" -> gold += st.getCount();
				default -> {
				}
			}
		}
		w.raw("purse", Json.object().put("copper", copper).put("iron", iron).put("gold", gold)
			.put("hearth", net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("hearth")).end());
	}

	/** The stack the Held card shows: the hotbar's selected slot. */
	protected ItemStack detailStack() {
		return this.minecraft.player.getInventory().getSelectedItem();
	}

	/** The hovered stack, as the floating tooltip beside its well (nothing hovered: no tooltip). */
	private void pushDetail(HubLayout.Cell cell) {
		ItemStack stack = cell == null ? ItemStack.EMPTY : this.menu.slots.get(cell.slot()).getItem();
		String key = stack.isEmpty() ? "empty" : cell.slot() + ":" + ItemStack.hashItemAndComponents(stack) + ":" + stack.getCount();
		if (!this.cardPacer.offer(key, System.nanoTime())) {
			return;
		}
		if (stack.isEmpty()) {
			this.hub.sendLatest("detail", TooltipModel.empty());
			return;
		}
		String label = TooltipModel.slotLabel(cell.role().css(), cell.ordinal());
		this.hub.sendLatest("detail", TooltipModel.of(ItemFactsReader.read(stack), label, cell.slot(), notes(stack), "detail"));
	}

	private String sentHeld;

	/** The Held card: what the hotbar's selected slot holds. Sent only when it changes. */
	private void pushHeld() {
		int held = this.minecraft.player.getInventory().getSelectedSlot();
		ItemStack stack = this.minecraft.player.getInventory().getSelectedItem();
		String key = held + ":" + (stack.isEmpty() ? "empty" : ItemStack.hashItemAndComponents(stack) + ":" + stack.getCount());
		if (key.equals(this.sentHeld)) {
			return;
		}
		this.sentHeld = key;
		String label = "HELD  ·  HOTBAR " + (held + 1);
		this.hub.sendLatest("held", stack.isEmpty() ? TooltipModel.empty("held")
			: TooltipModel.of(ItemFactsReader.read(stack), label, -1, List.of(), "held"));
	}

	/**
	 * Lines other mods add to the item's tooltip (weight, worth, maker...): the vanilla tooltip minus what the
	 * model already shows (name, enchantments, attribute lines, durability), at most four.
	 */
	private List<String> notes(ItemStack stack) {
		List<String> out = new ArrayList<>();
		try {
			List<Component> lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(this.minecraft.level), this.minecraft.player,
				net.minecraft.world.item.TooltipFlag.NORMAL);
			java.util.Set<String> enchants = new java.util.HashSet<>();
			for (ItemFacts.Enchant e : ItemFactsReader.read(stack).enchantments()) {
				enchants.add(e.name());
			}
			for (int i = 1; i < lines.size() && out.size() < 4; i++) {
				String t = lines.get(i).getString().strip();
				if (t.isEmpty() || t.startsWith("When ") || t.startsWith("Durability") || t.matches("^[+\\-]?\\d+(\\.\\d+)?%? (?!kg\\b).*")) {
					continue;
				}
				boolean ench = false;
				for (String e : enchants) {
					if (t.startsWith(e)) {
						ench = true;
						break;
					}
				}
				if (!ench) {
					out.add(t.length() > 64 ? t.substring(0, 63) + "…" : t);
				}
			}
		} catch (RuntimeException e) {
			// A mod's tooltip callback threw: the tooltip goes without notes.
		}
		return out;
	}

	private HubLayout.Cell heldCell(int held) {
		for (HubLayout.Cell c : this.layout.cells()) {
			if (c.role() == HubLayout.Role.HOTBAR && c.ordinal() == held) {
				return c;
			}
		}
		return null;
	}

	// ---- bridge ----------------------------------------------------------------------------------

	private void onBridge(Bridge.Message m) {
		switch (m) {
			case Bridge.Close c -> this.onClose();
			case Bridge.Filter f -> this.filter = f.value();
			case Bridge.Sort s -> this.queue(this.sortPlan(s.region()));
			case Bridge.MoveAll mv -> this.queue(this.moveAllPlan(mv.to()));
			case Bridge.Turn t -> this.yaw += (float) t.degrees();
			case Bridge.Tip t -> this.tip = t.w() <= 0 ? null : new Rect((int) Math.floor(t.x() * this.layout.u), (int) Math.floor(t.y() * this.layout.u),
				(int) Math.ceil(t.w() * this.layout.u), (int) Math.ceil(t.h() * this.layout.u));
			case Bridge.Page p -> this.showPage(p.value());
			case Bridge.Waypoint wp -> this.waypointHere();
			default -> this.onMessage(m);
		}
	}

	/** Messages this screen handles itself (segments, Mettle calls). */
	protected void onMessage(Bridge.Message m) {
	}

	/**
	 * The world strip's "Waypoint here": an Atlas waypoint where the player stands (selected on the
	 * map when the Map page is showing). Without Atlas the pill is dimmed and does nothing.
	 */
	public String waypointHere() {
		MapHost host = HubMap.available() ? HubMap.host() : null;
		if (host == null) {
			return null;
		}
		String name = host.addWaypointHere(this.onMap());
		if (name != null) {
			// The pill keeps its width (the strip is fixed): the name is on the map's card.
			this.pillNote = "Waypoint added";
			this.pillNoteUntil = System.nanoTime() + 1_600_000_000L;
		}
		return name;
	}

	/** Slots of a named region of this menu (storage, hotbar, container). */
	protected abstract int[] region(String name);

	public List<Click> sortPlan(String region) {
		int[] slots = this.region(region);
		return ClickPlanner.sort(slots, this.views(slots));
	}

	public List<Click> moveAllPlan(String to) {
		int[] from = this.region("inventory".equals(to) ? "container" : "storage");
		return ClickPlanner.moveAll(from, this.views(from));
	}

	protected StackView[] views(int[] slots) {
		StackView[] out = new StackView[slots.length];
		for (int i = 0; i < slots.length; i++) {
			out[i] = ItemFactsReader.view(this.menu.slots.get(slots[i]).getItem());
		}
		return out;
	}

	private void queue(List<Click> clicks) {
		if (!this.menu.getCarried().isEmpty()) {
			return;  // a plan assumes an empty cursor; never mix it with a stack in hand
		}
		this.pending.addAll(clicks);
	}

	/** Executes one planned or synthesised click through vanilla's own path. */
	public void click(Click c) {
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, c.slot(), c.button(),
			ContainerInput.valueOf(c.action().name()), this.minecraft.player);
	}

	public void runPlan(List<Click> clicks) {
		for (Click c : clicks) {
			this.click(c);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		while (!this.pending.isEmpty()) {
			this.click(this.pending.poll());
		}
	}

	// ---- input -----------------------------------------------------------------------------------

	private double physX(double guiX) {
		return guiX * this.minecraft.getWindow().getGuiScale();
	}

	private double physY(double guiY) {
		return guiY * this.minecraft.getWindow().getGuiScale();
	}

	private MouseButtonEvent toVanilla(MouseButtonEvent event, PointerMap.Target target) {
		double[] p = PointerMap.vanillaPoint(target, this.geometry);
		return new MouseButtonEvent(p[0], p[1], event.buttonInfo());
	}

	/** The Map page starts showing (first frame this open or switch): the map reads the player; the still. */
	private void startMap() {
		if (this.mapShown) {
			return;
		}
		MapHost host = HubMap.host();
		if (host == null) {
			return;
		}
		this.mapShown = true;
		host.shown();
		this.frozen = HubConfig.frozenMap() && host.canDrawStill();
		if (this.frozen) {
			host.freezeWorld(true);
		}
	}

	/** The Map page stops showing (another page, or the hub closed): the map saves, the world moves again. */
	private void mapHidden() {
		if (this.mapShown) {
			MapHost host = HubMap.existing();
			if (host != null) {
				if (this.frozen) {
					host.freezeWorld(false);
				}
				host.closed();
			}
			this.mapShown = false;
			this.frozen = false;
		}
	}

	/** The map, if the Map page is showing and Atlas is there. */
	private MapHost mapHost() {
		return this.onMap() ? HubMap.host() : null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double px = this.physX(event.x());
		double py = this.physY(event.y());
		if (this.onMap()) {
			// The map page: the window is the map's, the islands are the page's, and nothing is a
			// vanilla click (a carried stack is never dropped from here).
			if (this.layout.window.contains(px, py)) {
				MapHost host = this.mapHost();
				if (host != null) {
					this.mapGrab = true;
					host.mouseClicked(event.x(), event.y(), event.button());
				}
			} else {
				this.hub.mouseButton(true, this.layout.physToCss(px), this.layout.physToCss(py), event.button());
			}
			return true;
		}
		PointerMap.Target target = PointerMap.resolve(this.layout, px, py);
		if (!(target instanceof PointerMap.OnSlot)) {
			this.hub.mouseButton(true, this.layout.physToCss(px), this.layout.physToCss(py), event.button());
			if (this.layout.player != null && this.layout.player.contains(px, py)) {
				this.turning = true;
				this.turnLastX = px;
			}
		}
		return super.mouseClicked(this.toVanilla(event, target), doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		double px = this.physX(event.x());
		double py = this.physY(event.y());
		if (this.onMap()) {
			MapHost host = this.mapHost();
			if (this.mapGrab) {
				this.mapGrab = false;
				if (host != null) {
					host.mouseReleased(event.x(), event.y(), event.button());
				}
			} else {
				this.hub.mouseButton(false, this.layout.physToCss(px), this.layout.physToCss(py), event.button());
			}
			return true;
		}
		PointerMap.Target target = PointerMap.resolve(this.layout, px, py);
		this.hub.mouseButton(false, this.layout.physToCss(px), this.layout.physToCss(py), event.button());
		this.turning = false;
		return super.mouseReleased(this.toVanilla(event, target));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		double px = this.physX(event.x());
		double py = this.physY(event.y());
		this.forwardMove(px, py);
		if (this.onMap()) {
			MapHost host = this.mapHost();
			if (this.mapGrab && host != null) {
				host.mouseDragged(event.x(), event.y());
			}
			return true;
		}
		if (this.turning) {
			this.yaw += (float) ((px - this.turnLastX) / this.layout.u * 0.9);
			this.turnLastX = px;
			return true;
		}
		PointerMap.Target target = PointerMap.resolve(this.layout, px, py);
		return super.mouseDragged(this.toVanilla(event, target), dx, dy);
	}

	@Override
	public void mouseMoved(double x, double y) {
		this.forwardMove(this.physX(x), this.physY(y));
	}

	private void forwardMove(double px, double py) {
		if (this.onMap() && HubMap.available() && (this.mapGrab || this.layout.window.contains(px, py))) {
			// Over the map the page has nothing to hover: tell it once that the pointer left, then
			// leave Ultralight alone while the map pans (no messages, no hit-testing, no repaints).
			if (!this.pointerOnMap) {
				this.pointerOnMap = true;
				this.lastCssX = -1;
				this.lastCssY = -1;
				this.hub.mouseMove(-1, -1);
			}
			return;
		}
		this.pointerOnMap = false;
		double cx = this.layout.physToCss(px);
		double cy = this.layout.physToCss(py);
		if (cx != this.lastCssX || cy != this.lastCssY) {
			this.lastCssX = cx;
			this.lastCssY = cy;
			this.hub.mouseMove(cx, cy);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double px = this.physX(x);
		double py = this.physY(y);
		if (this.onMap()) {
			MapHost host = this.mapHost();
			if (host != null && this.layout.window.contains(px, py)) {
				host.mouseScrolled(x, y, scrollY);
			}
			return true;
		}
		if (this.layout.containerGrid != null && this.layout.maxScroll > 0 && this.layout.containerGrid.contains(px, py)) {
			int next = Math.max(0, Math.min(this.layout.maxScroll, this.scrollRow - (int) Math.signum(scrollY)));
			if (next != this.scrollRow) {
				this.scrollRow = next;
				this.relayout();
			}
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	/**
	 * Keys. The hub keeps R-Shift (close), Tab / Shift-Tab (pages) and Esc (close); on the Map page
	 * every other key is the map's, and Esc goes to the map first so it can close its card, menu or
	 * search before the hub closes. While the map has a text field, keys are text: they all go to
	 * the map (Tab moves between its search groups, R-Shift types capitals) and only an Esc it does
	 * not use closes the hub.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.onMap()) {
			this.mapKey(event);
			return true;
		}
		if (VoidHub.openHub != null && VoidHub.openHub.matches(event)) {
			this.onClose();
			return true;
		}
		if (this.hasPages() && event.isCycleFocus()) {
			this.cyclePage(event.hasShiftDown());
			return true;
		}
		return super.keyPressed(event);
	}

	private void mapKey(KeyEvent event) {
		MapHost host = this.mapHost();
		if (host != null && host.typing()) {
			if (!host.keyPressed(event) && event.isEscape()) {
				this.onClose();
			}
			return;
		}
		if (VoidHub.openHub != null && VoidHub.openHub.matches(event)) {
			this.onClose();
		} else if (event.isCycleFocus()) {
			this.cyclePage(event.hasShiftDown());
		} else if (host != null && host.keyPressed(event)) {
			return;  // the map's
		} else if (host != null && host.isMapKey(event)) {
			this.onClose();  // M closes the hub on the Map page, as it closes Atlas's own map
		} else if (event.isEscape()) {
			this.onClose();
		} else if (this.minecraft.options.keyInventory.matches(event)) {
			this.showPage("inventory");
		}
		// Anything else is nobody's: no hotbar swaps or drops from the map page.
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		MapHost host = this.mapHost();
		if (host != null) {
			host.charTyped(event);
			return true;
		}
		return super.charTyped(event);
	}

	/** The pages Tab steps through: the ones that are built. */
	private static final List<String> PAGES = List.of("map", "inventory");

	protected void cyclePage(boolean back) {
		int i = Math.max(0, PAGES.indexOf(this.page()));
		int n = PAGES.size();
		this.showPage(PAGES.get((i + (back ? n - 1 : 1)) % n));
	}

	@Override
	public void removed() {
		this.mapHidden();
		super.removed();
		this.hub.setListener(null);
		this.pending.clear();
	}

	protected static String upper(String s) {
		return s.toUpperCase(Locale.ROOT);
	}

	protected ItemFacts facts(ItemStack s) {
		return ItemFactsReader.read(s);
	}
}
