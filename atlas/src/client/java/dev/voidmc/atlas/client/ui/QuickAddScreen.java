package dev.voidmc.atlas.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Quick-add (B), as the design draws it (Figma frame 355:14018): a small card over the world, which stays in view
 * behind a light dim, to name a waypoint where the player stands and pick its colour, icon, when it shows (always,
 * within some distance, or for a while) and whether friends see it. Enter saves, Esc cancels. Starts with the last
 * colour used, the pin, Always, not shared. The same card edits an existing waypoint.
 */
public final class QuickAddScreen extends Screen {
	private static final float W = 420;
	/** The card's height with the second line under "Show it" (Within, Temporary); 48 less without it. */
	private static final float H = 518;
	private static final MarkerGlyph[] ICONS = {MarkerGlyph.PIN, MarkerGlyph.HOUSE, MarkerGlyph.TOWER, MarkerGlyph.PEAK, MarkerGlyph.CAVE,
		MarkerGlyph.WRECK, MarkerGlyph.GEM, MarkerGlyph.DOT};
	private static final Integer[] WITHIN = {64, 128, 256, 512};
	private static final Integer[] MINUTES = {5, 10, 30, 60};

	private enum Show {
		ALWAYS, WITHIN, TEMPORARY
	}

	private final AtlasClient atlas;
	private final @Nullable Screen parent;
	/** The waypoint being edited; null for a new one. */
	private final @Nullable Waypoint existing;
	private final int x, y, z;
	private final String dimension;
	private final String placeholder;
	private final TextField name = new TextField(48);
	private int colour;
	private MarkerGlyph icon = MarkerGlyph.PIN;
	private Show show = Show.ALWAYS;
	private int within;
	private int minutes;
	/** Editing a temporary waypoint keeps its expiry unless the length is changed. */
	private boolean minutesTouched;
	private boolean shared;
	private final List<FormKit.Hit> hits = new ArrayList<>();
	private float scale = 1;

	/** Opens quick-add at the player's feet (the B key). Does nothing outside a world. */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		AtlasClient atlas = AtlasClient.get();
		if (p == null || atlas.session() == null) {
			return;
		}
		mc.gui.setScreen(new QuickAddScreen(atlas, null, null, p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().identifier().toString()));
	}

	/** Edits an existing waypoint; closing returns to whatever screen was open. */
	public QuickAddScreen(Waypoint existing) {
		this(AtlasClient.get(), Minecraft.getInstance().gui.screen(), existing, existing.x(), existing.y(), existing.z(), existing.dimension());
	}

	private QuickAddScreen(AtlasClient atlas, @Nullable Screen parent, @Nullable Waypoint existing, int x, int y, int z, String dimension) {
		super(Component.literal(existing == null ? "New waypoint" : "Edit waypoint"));
		this.atlas = atlas;
		this.parent = parent;
		this.existing = existing;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		AtlasSettings s = atlas.settings();
		this.within = nearest(WITHIN, s.quickAddWithin);
		this.minutes = nearest(MINUTES, s.temporaryMinutes);
		var session = atlas.session();
		this.placeholder = "Waypoint " + (session == null ? 1 : session.waypoints.all().size() + 1);
		this.name.placeholder = this.placeholder;
		this.name.focused = true;
		if (existing == null) {
			this.colour = s.lastColour;
		} else {
			this.name.set(existing.name());
			this.colour = existing.colour();
			this.icon = existing.glyph();
			this.shared = existing.shared();
			if (existing.kind() == Waypoint.Kind.TEMPORARY) {
				this.show = Show.TEMPORARY;
				long left = existing.expires() - System.currentTimeMillis();
				if (left > 0) {
					this.minutes = nearest(MINUTES, (int) Math.ceil(left / 60_000.0));
				}
			} else if (existing.within() > 0) {
				this.show = Show.WITHIN;
				this.within = existing.within();
			}
		}
	}

	private static int nearest(Integer[] options, int value) {
		int best = options[0];
		for (int o : options) {
			if (Math.abs(o - value) < Math.abs(best - value)) {
				best = o;
			}
		}
		return best;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(this.parent);
	}

	private float unit() {
		var window = Minecraft.getInstance().getWindow();
		return Math.min(MinimapHud.hudUnit(), Ui.fit(window.getWidth(), window.getHeight(), W + 48, H + 48));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// the world stays in view: only the design's light dim, no blur
		Ui ui = new Ui(graphics, this.unit());
		ui.begin();
		ui.rect(0, 0, ui.width(), ui.height(), 0x610B0B0C);
		ui.end();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Ui ui = new Ui(graphics, this.unit());
		this.scale = ui.scale;
		this.hits.clear();
		ui.begin();
		ui.prewarm();
		FormKit kit = new FormKit(ui, this.hits, ui.design(mouseX), ui.design(mouseY));
		boolean second = this.show != Show.ALWAYS;
		float h = second ? H : H - 48;
		// left of the middle, as the frame has it, so the spot ahead stays in view; the top stays put as the card grows
		float px = Math.max(16, Math.min(ui.width() - W - 16, ui.width() / 2 - 330));
		float py = Math.max(16, (ui.height() - H) / 2);
		ui.panel(px, py, W, h, 24, Theme.ISLAND, Theme.HAIR_10);
		float cx = px + 19;
		float cw = W - 38;
		this.header(ui, kit, cx, py + 19, cw);
		this.field(ui, cx, py + 75, cw);
		this.swatches(ui, kit, cx, py + 133);
		this.icons(ui, kit, cx, py + 196);
		kit.caps("SHOW IT", cx, py + 267);
		Show[] shows = Show.values();
		kit.segmented(cx, py + 286, cw, true, shows, this::label, this.show, v -> this.show = v);
		float ry = py + 334;
		if (this.show == Show.TEMPORARY) {
			this.subRow(ui, kit, cx, ry, cw, "Gone after", MINUTES, m -> m == 60 ? "1 h" : m + " min", this.minutes, m -> {
				this.minutes = m;
				this.minutesTouched = true;
			});
		} else if (this.show == Show.WITHIN) {
			this.subRow(ui, kit, cx, ry, cw, "Shown within", WITHIN, d -> d + " m", this.within, d -> this.within = d);
		}
		ry = py + (second ? 390 : 342);
		kit.toggleRow(cx, ry, cw, Theme.WELL, "Share with friends", "Your friends see it on their maps, through VOID", this.shared, true,
			() -> this.shared = !this.shared);
		ry += FormKit.ROW_H + 16;
		float bw = (cw - 8) / 2;
		kit.button(cx, ry, bw, true, "Save", "Enter", this::save);
		kit.button(cx + bw + 8, ry, bw, false, "Cancel", "Esc", this::onClose);
		ui.end();
	}

	private String label(Show s) {
		return switch (s) {
			case ALWAYS -> "Always";
			case WITHIN -> "Within " + this.within + " m";
			case TEMPORARY -> "Temporary";
		};
	}

	private void header(Ui ui, FormKit kit, float x, float y, float w) {
		ui.panel(x, y, 40, 40, 12, Theme.WELL, Theme.HAIR_6);
		FormKit.pin(ui, x + 10, y + 5, this.colour, this.atlas.settings().shapeMarkers);
		ui.text(this.existing == null ? "New waypoint" : "Edit waypoint", x + 52, y + 2.5F, Ui.Font.F17, Theme.INK);
		String where = this.existing == null ? "WHERE YOU STAND" : MapPage.dimensionName(this.dimension).toUpperCase(java.util.Locale.ROOT);
		String sub = "X " + MapPage.minus(this.x) + "  ·  Y " + MapPage.minus(this.y) + "  ·  Z " + MapPage.minus(this.z) + "  ·  " + where;
		ui.text(ui.fit(sub, Ui.Font.F85, 1, w - 52 - 40), x + 52, y + 26.5F, Ui.Font.F85, 1, Theme.INK_4);
		if (this.existing == null) {
			kit.keycap("B", x + w - kit.keycapWidth("B"), y + 7.5F);
		}
	}

	/** The name field: a well with a 14% hairline, the name in the first ink and an ink caret. */
	private void field(Ui ui, float x, float y, float w) {
		ui.panel(x, y, w, 42, 12, Theme.WELL, 0x24FFFFFF);
		TextField f = this.name;
		float tx = x + 15;
		float room = w - 30;
		Ui.Font font = Ui.Font.F13;
		if (f.text.isEmpty()) {
			ui.text(ui.fit(f.placeholder, font, room), tx, y + 13, font, Theme.INK_4);
		}
		float caretX = ui.width(f.text.substring(0, f.caret), font);
		float scroll = Math.max(0, caretX - room + 4);
		ui.flush();
		// the scissor goes through the pose, so it takes design pixels
		ui.g.enableScissor((int) Math.floor(tx - 1), (int) Math.floor(y), (int) Math.ceil(x + w - 14), (int) Math.ceil(y + 42));
		ui.text(f.text, tx - scroll, y + 13, font, Theme.INK);
		if ((System.currentTimeMillis() / 530) % 2 == 0) {
			ui.round(ui.snap(tx + caretX - scroll + 1), y + 13, 1.5F, 16, 0.75F, Theme.ink(0.8F));
		}
		ui.flush();
		ui.g.disableScissor();
	}

	private void swatches(Ui ui, FormKit kit, float x, float y) {
		kit.caps("COLOUR", x, y);
		float sy = y + 19;
		for (int i = 0; i < Waypoint.COLOURS.length; i++) {
			float sx = x + i * 40;
			if (i == this.colour) {
				ui.ring(sx + 14, sy + 14, 14, 1.5F, Theme.ink(0.9F));
			} else if (kit.over(sx, sy, 28, 28)) {
				ui.ring(sx + 14, sy + 14, 14, 1.5F, Theme.ink(0.3F));
			}
			ui.circle(sx + 14, sy + 14, 10, 0xFF000000 | Waypoint.COLOURS[i]);
			int index = i;
			kit.hit(sx - 4, sy - 4, 36, 36, () -> this.colour = index);
		}
	}

	private void icons(Ui ui, FormKit kit, float x, float y) {
		kit.caps("ICON", x, y);
		float ty = y + 19;
		for (int i = 0; i < ICONS.length; i++) {
			MarkerGlyph g = ICONS[i];
			float tx = x + i * 44;
			boolean on = g == this.icon;
			boolean hover = !on && kit.over(tx, ty, 36, 36);
			ui.panel(tx, ty, 36, 36, 11, on ? Theme.RAISED : Theme.WELL, on ? 0x4DFFFFFF : hover ? Theme.HAIR_16 : Theme.HAIR_6);
			int ink = Theme.ink(on ? 1 : hover ? 0.8F : 0.6F);
			ui.glyph(g, tx + 10, ty + 10, 16, ink, ink);
			kit.hit(tx - 2, ty - 2, 40, 40, () -> this.icon = g);
		}
	}

	/** The line under "Show it": a label in the second ink, then a segmented control filling the rest. */
	private <T> void subRow(Ui ui, FormKit kit, float x, float y, float w, String label, T[] values, java.util.function.Function<T, String> names,
		T current, java.util.function.Consumer<T> set) {
		ui.text(label, x, y + 12.5F, Ui.Font.F12, Theme.INK_2);
		float lw = ui.width(label, Ui.Font.F12) + 10;
		kit.segmented(x + lw, y, w - lw, true, values, names, current, set);
	}

	private void save() {
		var session = this.atlas.session();
		if (session == null) {
			this.onClose();
			return;
		}
		String n = this.name.text.isBlank() ? (this.existing == null ? this.placeholder : this.existing.name()) : this.name.text;
		Waypoint w = this.existing == null
			? Waypoint.create(n, this.x, this.y, this.z, this.dimension, this.colour)
			: this.existing.withName(n).withColour(this.colour);
		w = w.withIcon(this.icon.name()).withShared(this.shared).withWithin(this.show == Show.WITHIN ? this.within : 0);
		if (this.show == Show.TEMPORARY) {
			boolean keep = this.existing != null && this.existing.kind() == Waypoint.Kind.TEMPORARY && !this.minutesTouched && this.existing.expires() > 0;
			w = w.withKind(Waypoint.Kind.TEMPORARY).withExpires(keep ? this.existing.expires() : System.currentTimeMillis() + this.minutes * 60_000L);
		} else if (w.kind() == Waypoint.Kind.TEMPORARY) {
			w = w.withKind(Waypoint.Kind.USER).withExpires(0);
		}
		session.waypoints.put(w);
		session.saveSoon(false);
		AtlasSettings s = this.atlas.settings();
		s.lastColour = this.colour;
		if (this.show == Show.WITHIN) {
			s.quickAddWithin = this.within;
		}
		s.save();
		this.onClose();
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
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			this.save();
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			this.onClose();
			return true;
		}
		return this.name.keyPressed(event) || super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.name.charTyped(event) || super.charTyped(event);
	}
}
