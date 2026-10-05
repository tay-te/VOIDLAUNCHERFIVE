package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.data.MapSession;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Atlas's islands in the HUD besides the minimap: the discovery toast (the design's {@code atlas/toast · discovered},
 * top centre, sliding 8 px, held 4 s, once per place) and the optional compass strip ({@code atlas/compass strip}: ±90°
 * round the heading, with the tracked and sighted places on it at their bearings).
 */
public final class HudOverlays {
	private static final long HOLD_NANOS = 4_000_000_000L;
	private static final long SLIDE_NANOS = 220_000_000L;
	/** How long an info island stays, a prompt waits, and the first-run hint shows. */
	public static final long INFO_NANOS = 6_000_000_000L;
	public static final long PROMPT_NANOS = 30_000_000_000L;
	public static final long FIRST_RUN_NANOS = 8_000_000_000L;

	/**
	 * One island (the design's quiet island, top centre, one at a time): a tile, a small caps line over a title, and
	 * either a key cap with a hint ("M on the map") or a prompt's two buttons (answered on the map; unanswered it goes
	 * after its time, and the default stands).
	 */
	public record Toast(String kind, String name, MarkerGlyph glyph, boolean region, String key, String keyText, String[] buttons, long hold) {
		Toast(String kind, String name, MarkerGlyph glyph, boolean region) {
			this(kind, name, glyph, region, region ? null : "M", region ? null : "on the map", null, HOLD_NANOS);
		}
	}

	private final AtlasClient atlas;
	private final ArrayDeque<Toast> queue = new ArrayDeque<>();
	private Toast showing;
	private long shownAt;

	/** Queues an island (client thread): an info line, a prompt, a notice. */
	public void island(Toast t) {
		if (this.queue.size() >= 6) {
			this.queue.pollFirst();
		}
		this.queue.add(t);
	}

	/** The info island: "ATLAS · NEW WORLD" over its title, 6 s. */
	public void info(String kind, String title) {
		this.island(new Toast(kind, title, MarkerGlyph.DOT, true, null, null, null, INFO_NANOS));
	}

	/** A prompt with two answers, shown up to 30 s (answered on the map). */
	public void prompt(String kind, String title, String first, String second) {
		this.island(new Toast(kind, title, MarkerGlyph.DOT, true, null, null, new String[]{first, second}, PROMPT_NANOS));
	}

	private long deathAt;
	private int deathMinutes;

	/** The player is back from death: "Death waypoint clears itself in N min", bottom left, 6 s. */
	public void death(int minutes) {
		this.deathAt = System.nanoTime();
		this.deathMinutes = minutes;
	}

	/** The death island, bottom left (the design's 355:14018). */
	private void deathIsland(Ui ui, long now) {
		long age = now - this.deathAt;
		if (this.deathAt == 0 || age > INFO_NANOS) {
			return;
		}
		float a = Math.min(1, Math.min(age / (float) SLIDE_NANOS, (INFO_NANOS - age) / (float) SLIDE_NANOS));
		String kind = "DEATH  ·  JUST NOW";
		String title = this.deathMinutes > 0 ? "Death waypoint clears itself in " + this.deathMinutes + " min" : "Death waypoint kept until you delete it";
		float w = 8 + 38 + 14 + Math.max(ui.width(kind, Ui.Font.F85, 1), ui.width(title, Ui.Font.F13)) + 18;
		float h = 54;
		float x = 24;
		float y = ui.height() - 24 - h;
		ui.round(x, y, w, h, 26, Theme.alpha(Theme.ISLAND, a));
		ui.border(x, y, w, h, 26, 0, Theme.alpha(Theme.HAIR_10, a));
		ui.round(x + 8, y + 8, 38, 38, 13, Theme.alpha(Theme.WELL, a));
		ui.glyph(MarkerGlyph.CROSS, x + 8 + 11, y + 8 + 11, 16, Theme.ink(a), Theme.ink(a));
		ui.text(kind, x + 60, y + 11, Ui.Font.F85, 1, Theme.alpha(Theme.INK_4, a));
		ui.text(title, x + 60, y + 26, Ui.Font.F13, Theme.ink(a));
	}

	/** The island showing now, if any (the tour photographs it). */
	public Toast showingIsland() {
		return this.showing;
	}

	/** Drops what is queued and showing (the tour, between shots). */
	public void clearIslands() {
		this.queue.clear();
		this.showing = null;
	}

	public HudOverlays(AtlasClient atlas) {
		this.atlas = atlas;
	}

	/** A place was discovered: its toast is shown once (a region's names it, with no keycap). Client thread. */
	public void discovered(Place p) {
		boolean region = p.isLabel();
		String kind = region ? "ENTERED  ·  " + Kinds.of(p).word : "DISCOVERED  ·  " + Kinds.of(p).word;
		if (this.queue.size() >= 3) {
			this.queue.pollFirst();
		}
		this.queue.add(new Toast(kind, p.name(), Kinds.of(p).glyph, region));
	}

	public void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		long c0 = dev.voidmc.atlas.client.Perf.cpuNow();
		long t0 = System.nanoTime();
		try {
			this.extractInner(graphics, delta);
		} finally {
			dev.voidmc.atlas.client.Perf.hud(System.nanoTime() - t0);
			dev.voidmc.atlas.client.Perf.cpu(dev.voidmc.atlas.client.Perf.cpuNow() - c0);
		}
	}

	private void extractInner(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen() || this.atlas.session() == null) {
			return;
		}
		boolean compass = this.atlas.settings().compass && mc.gui.screen() == null;
		long now = System.nanoTime();
		if (this.showing != null && (now - this.shownAt > this.showing.hold
			|| this.showing.buttons != null && this.atlas.worlds.question().isEmpty())) {
			this.showing = null;
		}
		if (this.showing == null && !this.queue.isEmpty()) {
			this.showing = this.queue.pollFirst();
			this.shownAt = now;
		}
		boolean death = this.deathAt != 0 && now - this.deathAt < INFO_NANOS;
		if (!compass && this.showing == null && !death) {
			return;
		}
		Ui ui = new Ui(graphics, MinimapHud.hudUnit());
		ui.begin();
		float top = 16;
		if (compass) {
			this.compass(ui, p, ui.width() / 2 - 260, top);
			top += 46 + 24;
		}
		if (this.showing != null && mc.gui.screen() == null) {
			long age = now - this.shownAt;
			float slide = Math.min(1, age / (float) SLIDE_NANOS);
			float out = Math.max(0, (age - (this.showing.hold - SLIDE_NANOS)) / (float) SLIDE_NANOS);
			float t = slide - out;
			this.toast(ui, this.showing, ui.width() / 2, top - 8 * (1 - t), t);
		}
		if (death && mc.gui.screen() == null) {
			this.deathIsland(ui, now);
		}
		ui.end();
	}

	/** The toast island: tile, "DISCOVERED · SHIPWRECK" over the name, and "M on the map". */
	private void toast(Ui ui, Toast toast, float centreX, float y, float alpha) {
		float nameW = Math.max(ui.width(toast.kind, Ui.Font.F85, 1), ui.width(toast.name, Ui.Font.F15));
		boolean hint = toast.key != null;
		float hintW = !hint ? 0 : 14 + 1 + 14 + ui.width(toast.key, Ui.Font.F12) + 16 + 8 + ui.width(toast.keyText, Ui.Font.F11) + 10;
		float buttonsW = 0;
		if (toast.buttons != null) {
			buttonsW = 14 + 1 + 6;
			for (String b : toast.buttons) {
				buttonsW += ui.width(b, Ui.Font.F12) + 30 + 6;
			}
		}
		float w = 8 + 38 + 14 + nameW + hintW + buttonsW + (hint || toast.buttons != null ? 0 : 14);
		float x = centreX - w / 2;
		float h = 54;
		float a = Math.max(0, Math.min(1, alpha));
		ui.round(x, y, w, h, 26, Theme.alpha(Theme.ISLAND, a));
		ui.border(x, y, w, h, 26, 0, Theme.alpha(Theme.HAIR_10, a));
		ui.round(x + 8, y + 8, 38, 38, 13, Theme.alpha(Theme.WELL, a));
		ui.border(x + 8, y + 8, 38, 38, 13, 0, Theme.alpha(Theme.HAIR_16, a));
		if (toast.glyph == MarkerGlyph.DOT) {
			// Atlas's own mark: a small grid
			for (int gy = 0; gy < 3; gy++) {
				for (int gx = 0; gx < 3; gx++) {
					ui.round(x + 8 + 14 + gx * 4, y + 8 + 14 + gy * 4, 2.6F, 2.6F, 0.6F, Theme.ink(a * 0.9F));
				}
			}
		} else {
			ui.glyph(toast.glyph, x + 8 + 11, y + 8 + 11, 16, Theme.ink(a), Theme.ink(a));
		}
		float tx = x + 8 + 38 + 14;
		ui.text(toast.kind, tx, y + 11, Ui.Font.F85, 1, Theme.alpha(Theme.INK_4, a));
		ui.text(toast.name, tx, y + 25, Ui.Font.F15, Theme.ink(a));
		if (toast.buttons != null) {
			float bx = tx + nameW + 14;
			ui.rect(bx, y + 13, 1, 28, Theme.alpha(Theme.HAIR_8, a));
			bx += 1 + 6;
			for (int i = 0; i < toast.buttons.length; i++) {
				String b = toast.buttons[i];
				float bw = ui.width(b, Ui.Font.F12) + 30;
				if (i == 0) {
					ui.round(bx, y + 9, bw, 36, 18, Theme.alpha(Theme.RAISED, a));
					ui.border(bx, y + 9, bw, 36, 18, 0, Theme.alpha(Theme.HAIR_10, a));
				}
				ui.text(b, bx + 15, y + 18, Ui.Font.F12, Theme.alpha(i == 0 ? Theme.INK : Theme.INK_2, a));
				bx += bw + 6;
			}
		}
		if (hint) {
			float sx = tx + nameW + 14;
			ui.rect(sx, y + 13, 1, 28, Theme.alpha(Theme.HAIR_8, a));
			float kx = sx + 1 + 14;
			float kw = ui.width(toast.key, Ui.Font.F12) + 16;
			ui.round(kx, y + 15, kw, 24, 7, Theme.alpha(0x0FFFFFFF, a));
			ui.border(kx, y + 15, kw, 24, 7, 0, Theme.alpha(Theme.HAIR_10, a));
			ui.text(toast.key, kx + 8, y + 19, Ui.Font.F12, Theme.alpha(Theme.INK_2, a));
			ui.text(toast.keyText, kx + kw + 8, y + 20, Ui.Font.F11, Theme.alpha(Theme.INK_2, a));
		}
	}

	/**
	 * The compass strip: 520 x 46, the heading a tick at the top middle, ticks every 15°, the cardinal and
	 * intercardinal names, and the tracked and sighted places within reach at their bearings, with their distances.
	 */
	private void compass(Ui ui, LocalPlayer p, float x, float y) {
		float w = 520;
		ui.round(x, y, w, 46, 23, 0xC70B0B0C);
		double heading = Math.floorMod((long) Math.round(p.getYRot() + 180), 360);
		float perDeg = w / 180F;
		ui.flush();
		ui.g.enableScissor((int) Math.floor(x + 14), (int) Math.floor(y), (int) Math.ceil(x + w - 14), (int) Math.ceil(y + 46));
		String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		for (int deg = 0; deg < 360; deg += 15) {
			double off = ((deg - heading + 540) % 360) - 180;
			if (Math.abs(off) > 92) {
				continue;
			}
			float tx = (float) (x + w / 2 + off * perDeg);
			if (deg % 45 == 0) {
				String n = names[deg / 45];
				Ui.Font font = n.length() == 1 ? Ui.Font.F11 : Ui.Font.F95;
				ui.textCentred(n, tx, y + 7, font, 0.6F, n.equals("N") ? Theme.INK : Theme.INK_2);
			} else {
				ui.round(tx - 1, y + 11, 2, 5, 1, Theme.ink(0.22F));
			}
		}
		ui.round(x + w / 2 - 1, y, 2, 8, 1, Theme.ink(0.9F));
		// the places: tracked first, then sighted, nearest first
		MapSession session = this.atlas.session();
		String dim = p.level().dimension().identifier().toString();
		List<Place> shown = new ArrayList<>();
		String tracked = this.atlas.settings().tracked;
		Place trackedPlace = tracked == null ? null : this.atlas.placeFor(tracked);
		if (trackedPlace != null && trackedPlace.dimension().equals(dim)) {
			shown.add(trackedPlace);
		}
		if (session != null) {
			List<Place> sighted = new ArrayList<>();
			for (Place pl : session.places.visible(dim)) {
				if (Kinds.sighted(pl) && !pl.isLabel() && pl != trackedPlace && pl.distanceTo(p.getX(), p.getZ()) < 1500) {
					sighted.add(pl);
				}
			}
			sighted.sort((a, b) -> Double.compare(a.distanceTo(p.getX(), p.getZ()), b.distanceTo(p.getX(), p.getZ())));
			shown.addAll(sighted.subList(0, Math.min(6, sighted.size())));
		}
		List<float[]> taken = new ArrayList<>();
		for (Place pl : shown) {
			double bearing = Math.toDegrees(Math.atan2(pl.x() + 0.5 - p.getX(), -(pl.z() + 0.5 - p.getZ())));
			double off = ((bearing - heading + 540) % 360) - 180;
			if (Math.abs(off) > 88) {
				continue;
			}
			float mx = (float) (x + w / 2 + off * perDeg) - 7;
			String dist = MarkerPainter.distance(pl.distanceTo(p.getX(), p.getZ()));
			float end = mx + 17 + ui.width(dist, Ui.Font.F85, 0.6F);
			boolean clear = true;
			for (float[] r : taken) {
				clear &= end < r[0] || mx > r[1];
			}
			if (!clear) {
				continue;
			}
			taken.add(new float[]{mx - 4, end + 4});
			boolean hollow = Kinds.sighted(pl);
			ui.round(mx, y + 26, 14, 14, 4.5F, hollow ? 0x990B0B0C : Theme.ISLAND);
			ui.border(mx, y + 26, 14, 14, 4.5F, 0, hollow ? 0x4DFFFFFF : Theme.HAIR_16);
			MarkerGlyph g = pl == trackedPlace && !hollow ? MarkerGlyph.TRACK : Kinds.glyph(pl);
			ui.glyph(g, mx + 2.5F, y + 28.5F, 9, Theme.ink(hollow ? 0.6F : 0.9F), Theme.ink(hollow ? 0.6F : 0.9F));
			ui.text(dist, mx + 17, y + 28, Ui.Font.F85, 0.6F, Theme.INK_4);
		}
		ui.flush();
		ui.g.disableScissor();
	}

	/** For the tests: whether a toast is up, and what it says. */
	public String showing() {
		return this.showing == null ? "" : this.showing.kind.toLowerCase(Locale.ROOT) + " " + this.showing.name;
	}
}
