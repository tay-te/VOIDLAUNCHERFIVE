package dev.voidmc.hub.screen;

import com.mojang.blaze3d.platform.Window;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.PointerMap;
import dev.voidmc.hub.layout.Rect;
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
		HubLayout.Cell cell = this.layout.cellAt(gx * gs, gy * gs);
		this.hovered = cell;
		Slot hoverSlot = null;
		if (cell != null) {
			Slot s = this.menu.slots.get(cell.slot());
			if (s.isActive()) {
				hoverSlot = s;
			}
		}
		this.hoveredSlot = hoverSlot;

		// 1. Tell the page what changed, then let Ultralight paint and upload the damage.
		this.hub.beginFrame();
		this.pushLayout();
		this.pushState();
		this.pushDetail(cell);
		if (this.firstFrame) {
			// The view still holds whatever the last VOID screen showed: let the UI thread paint
			// this one before its first frame goes out (bounded; it is usually a few ms).
			this.hub.awaitPainted(200_000_000L);
		}
		this.hub.frame();

		// 2. The chrome: one premultiplied quad, 1:1 with the framebuffer.
		graphics.pose().pushMatrix();
		graphics.pose().scale(1.0F / gs, 1.0F / gs);
		graphics.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, HubView.TEXTURE_ID, 0, 0, 0.0F, 0.0F,
			this.hub.width(), this.hub.height(), this.hub.texture().width(), this.hub.texture().height());
		graphics.pose().popMatrix();

		// 3. Native stacks, at the same rects the page drew the wells at.
		this.extractStacks(graphics, mouseX, mouseY, gs);
		this.extractDetailIcon(graphics, gs);
		this.extractExtras(graphics, gx, gy);

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

	private void extractStacks(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int gs) {
		for (HubLayout.Cell cell : this.layout.cells()) {
			Slot slot = this.menu.slots.get(cell.slot());
			if (!slot.isActive()) {
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

	/** The stack the card shows: the hovered one, else the held one. */
	protected ItemStack detailStack() {
		if (this.hovered != null) {
			ItemStack s = this.menu.slots.get(this.hovered.slot()).getItem();
			if (!s.isEmpty()) {
				return s;
			}
		}
		return this.minecraft.player.getInventory().getSelectedItem();
	}

	private void pushDetail(HubLayout.Cell cell) {
		ItemStack hoveredStack = cell == null ? ItemStack.EMPTY : this.menu.slots.get(cell.slot()).getItem();
		ItemStack stack;
		String label;
		int slot;
		if (!hoveredStack.isEmpty()) {
			stack = hoveredStack;
			label = "HOVERED  ·  " + TooltipModel.slotLabel(cell.role().css(), cell.ordinal());
			slot = cell.slot();
		} else {
			int held = this.minecraft.player.getInventory().getSelectedSlot();
			stack = this.minecraft.player.getInventory().getSelectedItem();
			label = "HELD  ·  HOTBAR " + (held + 1);
			HubLayout.Cell heldCell = this.heldCell(held);
			slot = heldCell == null ? -1 : heldCell.slot();
		}
		String key = stack.isEmpty() ? "empty" : slot + ":" + label + ":" + ItemStack.hashItemAndComponents(stack) + ":" + stack.getCount();
		if (!this.cardPacer.offer(key, System.nanoTime())) {
			return;
		}
		this.hub.sendLatest("detail", stack.isEmpty() ? TooltipModel.empty() : TooltipModel.of(ItemFactsReader.read(stack), label, slot));
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
			default -> {
			}
		}
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

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double px = this.physX(event.x());
		double py = this.physY(event.y());
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

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (VoidHub.openHub != null && VoidHub.openHub.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
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
