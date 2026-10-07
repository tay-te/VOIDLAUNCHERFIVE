package dev.voidmc.atlas.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Atlas's settings, as the design draws them (Figma frame 355:14287): a fixed panel beside the world, opposite the
 * minimap's corner, with the Minimap / Map / Waypoints / Data tabs and rows of toggles, segmented choices and steps.
 * The minimap itself shows as a live preview in its corner, above the screen's light dim. Every change applies at
 * once; the file is written when the window closes.
 */
public final class SettingsScreen extends Screen {
	private static final float W = 520;
	/** The panel's height: the Minimap tab's, the longest; the other tabs sit at its top. */
	private static final float H = 806;
	/** How far the panel stands in from the side of the screen. */
	private static final float SIDE = 120;
	private static final String[] TABS = {"Minimap", "Map", "Waypoints", "Data"};
	private static final int[] ZOOM_STEPS = {4, 5, 7};
	private static final int[] DISK_CAPS = {1024, 2048, 4096, 8192, 16384, 0};
	private static final Integer[] MINUTES = {5, 10, 30, 60};
	private static final Integer[] DEATH_MINUTES = {5, 10, 30, 0};
	private static final Integer[] WITHIN = {64, 128, 256, 512};

	/** True only while this screen asks the minimap to draw itself as the preview (see {@link #previewing()}). */
	private static boolean previewing;

	private final AtlasClient atlas;
	private final @Nullable Screen parent;
	private final List<FormKit.Hit> hits = new ArrayList<>();
	/** The preview's own minimap (its own zoom easing), drawn in the HUD's corner above the dim. */
	private final MinimapHud preview;
	private int tab;
	/** Each tab's height as last drawn (the panel fits the tab shown). */
	private final float[] tabHeights = {H, H, H, H};
	private float scale = 1;
	// the layout cursor while a tab is drawn
	private FormKit kit;
	private float rx;
	private float ry;

	public SettingsScreen(AtlasClient atlas, @Nullable Screen parent) {
		super(Component.translatable("atlas.settings.title"));
		this.atlas = atlas;
		this.parent = parent;
		this.preview = new MinimapHud(atlas);
	}

	/**
	 * Whether the settings are drawing the minimap preview right now. The HUD minimap hides behind any screen; it
	 * should draw anyway while this is true, so the preview shows above the settings' dim.
	 */
	public static boolean previewing() {
		return previewing;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.atlas.settings().save();
	}

	private float unit() {
		var window = Minecraft.getInstance().getWindow();
		float h = Math.max(H, this.tabHeights[this.tab]);
		return Math.min(MinimapHud.hudUnit(), Ui.fit(window.getWidth(), window.getHeight(), W + 48, h + 48));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// the world stays in view behind the design's light dim; no blur
		Ui ui = new Ui(graphics, this.unit());
		ui.begin();
		ui.rect(0, 0, ui.width(), ui.height(), 0x730B0B0C);
		ui.end();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.livePreview(graphics);
		Ui ui = new Ui(graphics, this.unit());
		this.scale = ui.scale;
		this.hits.clear();
		ui.begin();
		ui.prewarm();
		FormKit kit = new FormKit(ui, this.hits, ui.design(mouseX), ui.design(mouseY));
		this.kit = kit;
		AtlasSettings s = this.atlas.settings();
		boolean left = s.minimapCorner == AtlasSettings.Corner.TOP_LEFT || s.minimapCorner == AtlasSettings.Corner.BOTTOM_LEFT;
		float sw = ui.width();
		float side = sw >= W + SIDE * 2 ? SIDE : Math.max(16, (sw - W) / 2);
		float px = left ? sw - side - W : side;
		float ph = this.tabHeights[this.tab];
		float py = Math.max(16, (ui.height() - ph) / 2);
		ui.panel(px, py, W, ph, 24, Theme.ISLAND, Theme.HAIR_10);
		float cx = px + 19;
		float cw = W - 38;
		// the header: the caps line, the tab's name, the Esc keycap
		kit.caps("ATLAS  ·  SETTINGS", cx, py + 19);
		ui.text(TABS[this.tab], cx, py + 33, Ui.Font.F18, Theme.INK);
		float kw = kit.keycapWidth("Esc");
		kit.keycap("Esc", cx + cw - kw, py + 25);
		kit.hit(cx + cw - kw, py + 25, kw, 25, this::onClose);
		Integer[] tabs = {0, 1, 2, 3};
		kit.segmented(cx, py + 66, cw, true, tabs, i -> TABS[i], this.tab, i -> this.tab = i);
		this.rx = cx;
		this.ry = py + 116;
		switch (this.tab) {
			case 0 -> this.minimapTab(s, cw);
			case 1 -> this.mapTab(s, cw);
			case 2 -> this.waypointsTab(s, cw);
			default -> this.dataTab(cw);
		}
		this.tabHeights[this.tab] = Math.max(H, this.ry - py + 10);
		ui.end();
		this.kit = null;
	}

	/** The real minimap in its corner, drawn above the dim, and "LIVE PREVIEW" under it (above it at the bottom). */
	private void livePreview(GuiGraphicsExtractor graphics) {
		AtlasSettings s = this.atlas.settings();
		Minecraft mc = Minecraft.getInstance();
		if (!s.minimap || mc.player == null || !this.atlas.minimapAllowed()) {
			return;
		}
		previewing = true;
		try {
			this.preview.extract(graphics, mc.getDeltaTracker());
		} finally {
			previewing = false;
		}
		// the caption, laid out as MinimapHud lays out the minimap
		Ui ui = new Ui(graphics, MinimapHud.hudUnit());
		ui.begin();
		float side = s.minimapSize.side;
		float info = s.minimapCoordinates ? 46 : 0;
		boolean left = s.minimapCorner == AtlasSettings.Corner.TOP_LEFT || s.minimapCorner == AtlasSettings.Corner.BOTTOM_LEFT;
		boolean bottom = s.minimapCorner == AtlasSettings.Corner.BOTTOM_LEFT || s.minimapCorner == AtlasSettings.Corner.BOTTOM_RIGHT;
		float centre = left ? 16 + side / 2 : ui.width() - 16 - side / 2;
		float top = bottom ? ui.height() - 16 - side - info - 12 - 11 : 16 + side + info + 12;
		ui.textCentred("LIVE PREVIEW", centre, top, Ui.Font.F85, 1, Theme.INK_4);
		ui.end();
	}

	// --- the tabs --------------------------------------------------------------------------------------------------

	private void minimapTab(AtlasSettings s, float w) {
		this.group("LOOK");
		this.choice(w, "Shape", "How the minimap is cut", AtlasSettings.Shape.values(), v -> v.label, s.minimapShape, v -> s.minimapShape = v);
		this.choice(w, "Size", "144, 200 or 256 px", AtlasSettings.Size.values(), v -> v.label, s.minimapSize, v -> s.minimapSize = v);
		String[] zooms = new String[ZOOM_STEPS.length];
		int zoomAt = 0;
		for (int i = 0; i < ZOOM_STEPS.length; i++) {
			zooms[i] = MapPage.scaleLabel(AtlasSettings.ZOOMS[ZOOM_STEPS[i]]);
			if (Math.abs(ZOOM_STEPS[i] - s.minimapZoom) < Math.abs(ZOOM_STEPS[zoomAt] - s.minimapZoom)) {
				zoomAt = i;
			}
		}
		this.steps(w, "Zoom", "How much of the world it shows", zooms, zoomAt, i -> s.minimapZoom = ZOOM_STEPS[i]);
		this.toggle(w, "Turn with me", "Off: north stays up", s.minimapRotates, true, () -> s.minimapRotates = !s.minimapRotates);
		this.group("SHOW");
		boolean caves = this.atlas.caveViewAllowed(false);
		this.toggle(w, "Cave view underground", caves ? "Switches to the depth slice when there is no sky" : "Off on this server", caves && s.minimapAutoCave,
			caves, () -> s.minimapAutoCave = !s.minimapAutoCave);
		boolean radar = !this.atlas.multiplayer() || this.atlas.rules().radarAllowed(false);
		this.toggle(w, "Radar", radar ? "Players and mobs within 64 blocks" : "Off on this server", radar && (s.radarPlayers || s.radarMobs), radar, () -> {
			boolean on = !(s.radarPlayers || s.radarMobs);
			s.radarPlayers = on;
			s.radarMobs = on;
		});
		this.toggle(w, "Waypoint beams", "In the world, beyond 8 blocks", s.waypointBeams, true, () -> s.waypointBeams = !s.waypointBeams);
		this.toggle(w, "Colour-blind shapes", "Each waypoint colour gets its own shape", s.shapeMarkers, true, () -> {
			s.shapeMarkers = !s.shapeMarkers;
			s.legendVersion++;
		});
		this.group("PLACE");
		AtlasSettings.Corner[] corners = {AtlasSettings.Corner.TOP_LEFT, AtlasSettings.Corner.TOP_RIGHT};
		this.choice(w, "Corner", "Where the minimap sits", corners, v -> v.label, s.minimapCorner, v -> s.minimapCorner = v);
	}

	private void mapTab(AtlasSettings s, float w) {
		this.section8(s, w);
		this.group("MAP LOOK");
		this.kit.segmented(this.rx, this.ry, w, true, AtlasSettings.MapLook.values(), v -> v.label, s.mapLook, v -> s.mapLook = v);
		this.ry += FormKit.SEG_H + 8;
		this.kit.ui.text("Night begins at dusk (12 500 ticks), eases in over 20 s", this.rx + 4, this.ry, Ui.Font.F105, Theme.INK_3);
		this.ry += 13 + 10;
		this.toggle(w, "Live backdrop", "The world moves behind the open map; costs a render a frame", s.liveBackdrop, true,
			() -> s.liveBackdrop = !s.liveBackdrop);
		this.group("ON SCREEN");
		this.toggle(w, "Minimap", "N shows and hides it in game", s.minimap, true, () -> s.minimap = !s.minimap);
		this.toggle(w, "Coordinates under the minimap", "The biome and where you stand", s.minimapCoordinates, true,
			() -> s.minimapCoordinates = !s.minimapCoordinates);
		this.toggle(w, "Compass", "A strip at the top with the places you have sighted", s.compass, true, () -> s.compass = !s.compass);
	}

	/**
	 * The section 8 rows of the Map tab (Figma 375:37216): far sight, friends and party (only with a VOID transport),
	 * performance (with the quiet note while Light is on) and the journal.
	 */
	private void section8(AtlasSettings s, float w) {
		boolean lod = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(dev.voidmc.atlas.client.FarSight.LOD_MOD);
		this.group("FAR SIGHT");
		this.toggle(w, "Map what you can see (VOID LOD)", lod ? "Chunks VOID LOD draws fill the map; walking adds detail" : "Needs VOID LOD installed",
			lod && s.farSight, lod, () -> s.farSight = !s.farSight);
		if (this.atlas.social.available()) {
			this.group("FRIENDS AND PARTY");
			this.choice(w, "Share my position", "Who sees you live on their map", dev.voidmc.atlas.api.Privacy.values(), SettingsScreen::privacyLabel,
				this.atlas.social.privacy(), v -> s.sharePrivacy = v.id());
			this.toggle(w, "Share explored map with party", "Your walked land fills in their maps, theirs in yours", s.shareMap, true,
				() -> s.shareMap = !s.shareMap);
		}
		this.group("PERFORMANCE");
		if (dev.voidmc.atlas.client.PerfGovernor.light()) {
			// the row grows to carry the quiet note while Light is on
			Ui ui = this.kit.ui;
			float h = 82;
			ui.panel(this.rx, this.ry, w, h, 14, Theme.ISLAND, Theme.HAIR_7);
			ui.text("Performance", this.rx + 17, this.ry + 10.5F, Ui.Font.F13, Theme.INK);
			ui.text("Auto drops to Light while the game is struggling", this.rx + 17, this.ry + 29.5F, Ui.Font.F105, Theme.INK_3);
			long min = dev.voidmc.atlas.client.PerfGovernor.lightMillis() / 60_000L;
			MinimapHud.quietNote(ui, MinimapHud.LIGHT_NOTE + (min >= 1 ? "  ·  " + min + " min" : ""), this.rx + 9, this.ry + 49, w - 220);
			AtlasSettings.Performance[] values = AtlasSettings.Performance.values();
			float sw = this.kit.segmentedWidth(values, v -> v.label);
			this.kit.segmented(this.rx + w - 13 - sw, this.ry + (h - 40) / 2, sw, false, values, v -> v.label, s.performance, v -> s.performance = v);
			this.ry += h + 10;
		} else {
			this.choice(w, "Performance", "Auto drops to Light while the game is struggling", AtlasSettings.Performance.values(), v -> v.label, s.performance,
				v -> s.performance = v);
		}
		this.group("JOURNAL");
		this.toggle(w, "Journal", "First discoveries by day, explored share per biome area", s.journal, true, () -> s.journal = !s.journal);
	}

	static String privacyLabel(dev.voidmc.atlas.api.Privacy v) {
		return switch (v) {
			case FRIENDS -> "Friends";
			case PARTY -> "Party";
			case NOBODY -> "Nobody";
		};
	}

	private static AtlasSettings.Preset parsePreset(String name) {
		try {
			return AtlasSettings.Preset.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private void waypointsTab(AtlasSettings s, float w) {
		this.group("WHERE YOU DIE");
		this.choice(w, "Death points clear after", "Or never, until you delete them", DEATH_MINUTES, m -> m == 0 ? "Never" : m + " min", s.deathMinutes,
			m -> s.deathMinutes = m);
		this.toggle(w, "Clear on reaching", "Once you are back within " + AtlasSettings.DEATH_REACH + " blocks", s.deathClearsOnReach, true,
			() -> s.deathClearsOnReach = !s.deathClearsOnReach);
		this.group("TEMPORARY");
		this.choice(w, "Temporary waypoints last", "From chat, shares and quick-add", MINUTES, m -> m == 60 ? "1 h" : m + " min", s.temporaryMinutes,
			m -> s.temporaryMinutes = m);
		this.toggle(w, "Chat coordinates", "Coordinates in chat become a waypoint", s.chatCoordinates, true, () -> s.chatCoordinates = !s.chatCoordinates);
		this.group("QUICK-ADD  ·  B");
		this.choice(w, "Within", "The distance quick-add offers", WITHIN, d -> d + " m", s.quickAddWithin, d -> s.quickAddWithin = d);
	}

	private void dataTab(float w) {
		AtlasSettings s = this.atlas.settings();
		this.group("STORAGE");
		String[] caps = new String[DISK_CAPS.length];
		int capAt = DISK_CAPS.length - 1;
		for (int i = 0; i < DISK_CAPS.length; i++) {
			caps[i] = DISK_CAPS[i] == 0 ? "No cap" : DISK_CAPS[i] / 1024 + " GB";
			// a cap set by hand in the file shows as the nearest step
			if (s.diskCapMb > 0 && DISK_CAPS[i] > 0 && Math.abs(DISK_CAPS[i] - s.diskCapMb) < Math.abs(DISK_CAPS[capAt] - s.diskCapMb)) {
				capAt = i;
			}
		}
		this.steps(w, "Disk cap", "Far regions seen longest ago go first", caps, capAt, i -> s.diskCapMb = DISK_CAPS[i]);
		String[] workers = {"1", "2", "3", "4"};
		this.steps(w, "Map workers", "Threads that read chunks and draw tiles (next world)", workers, Math.max(0, Math.min(3, s.workerThreads - 1)),
			i -> s.workerThreads = i + 1);
		this.group("EXPORT AND IMPORT");
		this.button(w, "Export map as image", "A PNG of the map as it is shown", "Export", () -> this.atlas.openExport());
		this.button(w, "Import waypoints", "From other map mods in this instance", "Import", () -> this.atlas.offerImport());
		this.group("PRESET");
		AtlasSettings.Preset current = s.preset == null ? null : parsePreset(s.preset);
		this.choice(w, "Start from", current == null ? "Sets several defaults at once" : current.detail, AtlasSettings.Preset.values(), v -> v.label, current,
			s::apply);
		this.group("BACKUPS");
		this.kit.ui.text("Waypoints, places and the region index are backed up as you play", this.rx + 4, this.ry - 4, Ui.Font.F105, Theme.INK_3);
	}

	// --- rows: each advances the cursor by its height and the 10 px gap ---------------------------------------------

	private void group(String label) {
		this.kit.caps(label, this.rx + 4, this.ry + 8);
		this.ry += 19 + 10;
	}

	private void toggle(float w, String label, String hint, boolean on, boolean enabled, Runnable flip) {
		this.kit.toggleRow(this.rx, this.ry, w, Theme.ISLAND, label, hint, on, enabled, flip);
		this.ry += FormKit.ROW_H + 10;
	}

	private <T> void choice(float w, String label, String hint, T[] values, Function<T, String> names, T current, Consumer<T> set) {
		this.kit.choiceRow(this.rx, this.ry, w, Theme.ISLAND, label, hint, values, names, current, set);
		this.ry += FormKit.ROW_H + 10;
	}

	private void steps(float w, String label, String hint, String[] values, int selected, IntConsumer set) {
		this.kit.stepsRow(this.rx, this.ry, w, Theme.ISLAND, label, hint, values, selected, set);
		this.ry += FormKit.ROW_H + 10;
	}

	private void button(float w, String label, String hint, String button, Runnable action) {
		this.kit.buttonRow(this.rx, this.ry, w, Theme.ISLAND, label, hint, button, action);
		this.ry += FormKit.ROW_H + 10;
	}

	// --- input -----------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && FormKit.click(this.hits, (float) (event.x() / this.scale), (float) (event.y() / this.scale))) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		// Tab and the arrows move between the tabs; Esc closes (the screen's own handling)
		if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_LEFT) {
			int step = key == InputConstants.KEY_LEFT || key == InputConstants.KEY_TAB && event.hasShiftDown() ? -1 : 1;
			this.tab = Math.floorMod(this.tab + step, TABS.length);
			return true;
		}
		return super.keyPressed(event);
	}
}
