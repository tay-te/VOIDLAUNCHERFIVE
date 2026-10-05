package dev.voidmc.atlas.client.render;

import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.client.ui.MinimapHud;
import dev.voidmc.atlas.client.ui.PinShapes;
import dev.voidmc.atlas.client.ui.Theme;
import dev.voidmc.atlas.client.ui.Ui;
import dev.voidmc.atlas.core.WaypointStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The waypoints in the world, as the design draws them (355:14015): a beam of light rising from each, in its colour,
 * and a label at its top that stays the same size on screen.
 *
 * <ul>
 *   <li>Far (over 64 m): a 2 px core at 45% in an 8 px halo at 8%, and a one-line label (a dot or the colour's shape,
 *   the name, the distance, or how long a temporary one has left, or how long ago a death was).</li>
 *   <li>Near (64 m or less): a 3 px core in a 12 px halo, a ring on the ground, and a two-line label with the
 *   waypoint's icon ("18 m · 6 above").</li>
 * </ul>
 *
 * Labels never overlap: the nearer wins and the farther goes (its beam stays). Everything fades out inside 4 m and
 * hides with the HUD (F1) and the debug screen. Drawn in the HUD from the camera's own view and projection, so it sits
 * where the world is drawn; nothing is allocated per frame but the labels' strings when their numbers change.
 */
public final class WaypointBeams {
	public static final int MAX_BEAMS = 32;
	public static final double NEAR = 64;

	private final AtlasClient atlas;
	private List<Waypoint> shown = List.of();
	private long shownVersion = -1;
	private String shownDimension = "";
	private MapSession shownSession;
	private final Matrix4f viewProj = new Matrix4f();
	private final Vector4f v = new Vector4f();
	private final double[] dist = new double[256];
	private final int[] order = new int[256];
	private final float[] boxes = new float[MAX_BEAMS * 4];
	private final String[] metaText = new String[256];
	private final String[] widthFor = new String[256];
	private final boolean[] widthNear = new boolean[256];
	private final float[] nameW = new float[256];
	private final float[] metaW = new float[256];
	private final long[] metaKey = new long[256];

	public WaypointBeams(AtlasClient atlas) {
		this.atlas = atlas;
	}

	public void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		AtlasSettings settings = this.atlas.settings();
		MapSession session = this.atlas.session();
		if (!settings.waypointBeams || session == null || mc.player == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
			|| mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		long c0 = dev.voidmc.atlas.client.Perf.cpuNow();
		long t0 = System.nanoTime();
		try {
			this.draw(graphics, mc, settings, session);
		} finally {
			Perf.beams(System.nanoTime() - t0);
			dev.voidmc.atlas.client.Perf.cpu(dev.voidmc.atlas.client.Perf.cpuNow() - c0);
		}
	}

	private void draw(GuiGraphicsExtractor graphics, Minecraft mc, AtlasSettings settings, MapSession session) {
		String dim = mc.level.dimension().identifier().toString();
		WaypointStore store = session.waypoints;
		long version = store.version();
		if (version != this.shownVersion || !dim.equals(this.shownDimension) || session != this.shownSession) {
			List<Waypoint> list = new ArrayList<>();
			for (Waypoint w : store.in(dim)) {
				if (w.visible()) {
					list.add(w);
				}
			}
			this.shown = list.size() > this.dist.length ? list.subList(0, this.dist.length) : list;
			this.shownVersion = version;
			this.shownDimension = dim;
			this.shownSession = session;
			java.util.Arrays.fill(this.metaKey, Long.MIN_VALUE);
			java.util.Arrays.fill(this.widthFor, null);
		}
		if (this.shown.isEmpty()) {
			return;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 cam = camera.position();
		camera.getViewRotationProjectionMatrix(this.viewProj);
		double maxD = settings.beamDistance <= 0 ? Double.MAX_VALUE : settings.beamDistance;
		int n = 0;
		for (int i = 0; i < this.shown.size(); i++) {
			Waypoint w = this.shown.get(i);
			double dx = w.x() + 0.5 - cam.x;
			double dy = w.y() - cam.y;
			double dz = w.z() + 0.5 - cam.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > maxD || w.within() > 0 && d > w.within()) {
				continue;
			}
			this.dist[i] = Math.sqrt(d * d + dy * dy);
			this.order[n++] = i;
		}
		if (n == 0) {
			return;
		}
		for (int a = 1; a < n; a++) {
			int val = this.order[a];
			int b = a - 1;
			while (b >= 0 && this.dist[this.order[b]] > this.dist[val]) {
				this.order[b + 1] = this.order[b];
				b--;
			}
			this.order[b + 1] = val;
		}
		n = Math.min(n, MAX_BEAMS);
		Ui ui = new Ui(graphics, MinimapHud.hudUnit());
		ui.begin();
		float sw = ui.width();
		float sh = ui.height();
		int placed = 0;
		long now = System.currentTimeMillis();
		// beams first (far ones under near ones), then the labels nearest first so the nearer wins a clash
		for (int k = n - 1; k >= 0; k--) {
			Waypoint w = this.shown.get(this.order[k]);
			double d = this.dist[this.order[k]];
			float fade = (float) Math.max(0, Math.min(1, (d - 2) / 2));
			if (fade <= 0.02F) {
				continue;
			}
			boolean near = d <= NEAR;
			double height = near ? 2.6 : Math.min(140, Math.max(24, d * 0.18));
			float[] base = this.project(w.x() + 0.5 - cam.x, w.y() - cam.y, w.z() + 0.5 - cam.z, sw, sh);
			float[] top = this.project(w.x() + 0.5 - cam.x, w.y() + height - cam.y, w.z() + 0.5 - cam.z, sw, sh);
			if (base == null || top == null) {
				continue;
			}
			int rgb = w.rgb();
			ui.line(base[0], base[1], top[0], top[1], near ? 12 : 8, Theme.alpha(0xFF000000 | rgb, 0.08F * fade));
			ui.line(base[0], base[1], top[0], top[1], near ? 3 : 2, Theme.alpha(0xFF000000 | rgb, 0.45F * fade));
			if (near) {
				this.ring(ui, w, cam, sw, sh, Theme.alpha(0xFF000000 | rgb, 0.4F * fade));
			}
		}
		for (int k = 0; k < n; k++) {
			Waypoint w = this.shown.get(this.order[k]);
			double d = this.dist[this.order[k]];
			float fade = (float) Math.max(0, Math.min(1, (d - 2) / 2));
			if (fade <= 0.02F) {
				continue;
			}
			boolean near = d <= NEAR;
			double height = near ? 2.6 : Math.min(140, Math.max(24, d * 0.18));
			float[] top = this.project(w.x() + 0.5 - cam.x, w.y() + height - cam.y, w.z() + 0.5 - cam.z, sw, sh);
			if (top == null) {
				continue;
			}
			String meta = this.meta(this.order[k], w, d, cam.y, near, now);
			float x0;
			float y0;
			float bw;
			float bh;
			int index = this.order[k];
			if (this.widthFor[index] != meta || this.widthNear[index] != near) {
				// widths measured again only when the words change
				this.nameW[index] = ui.width(w.name(), near ? Ui.Font.F13 : Ui.Font.F11);
				this.metaW[index] = ui.width(meta, Ui.Font.F9, 0.6F);
				this.widthFor[index] = meta;
				this.widthNear[index] = near;
			}
			if (near) {
				bw = 16 + 28 + 10 + Math.max(this.nameW[index], this.metaW[index]) + 16;
				bh = 50;
			} else {
				bw = 10 + 6 + 7 + this.nameW[index] + 7 + this.metaW[index] + 10;
				bh = 22;
			}
			// kept on screen: a label at the edge still says where and how far
			x0 = Math.max(8, Math.min(sw - bw - 8, top[0] - bw / 2));
			y0 = Math.max(8, Math.min(sh - bh - 8, top[1] - bh));
			if (this.clashes(placed, x0, y0, bw, bh)) {
				continue;
			}
			this.boxes[placed * 4] = x0;
			this.boxes[placed * 4 + 1] = y0;
			this.boxes[placed * 4 + 2] = x0 + bw;
			this.boxes[placed * 4 + 3] = y0 + bh;
			placed++;
			int ink = Theme.alpha(Theme.INK, fade);
			int dim4 = Theme.alpha(Theme.INK_3, fade);
			if (near) {
				ui.panel(x0, y0, bw, bh, 14, Theme.alpha(0xEB0B0B0C, fade), Theme.alpha(Theme.HAIR_10, fade));
				ui.round(x0 + 11, y0 + 11, 28, 28, 8, Theme.alpha(Theme.ISLAND, fade));
				ui.glyph(w.glyph(), x0 + 11 + 6, y0 + 11 + 6, 16, ink, ink);
				ui.text(w.name(), x0 + 11 + 28 + 10, y0 + 9, Ui.Font.F13, ink);
				ui.text(meta, x0 + 11 + 28 + 10, y0 + 29, Ui.Font.F9, 0.6F, dim4);
			} else {
				ui.panel(x0, y0, bw, bh, 11, Theme.alpha(0xE60B0B0C, fade), Theme.alpha(Theme.HAIR_10, fade));
				PinShapes.head(ui, x0 + 10 + 3, y0 + bh / 2, 6, w.colour(), this.atlas.settings().shapeMarkers, Theme.alpha(0xFF000000 | w.rgb(), fade));
				float tx = x0 + 10 + 6 + 7;
				ui.text(w.name(), tx, y0 + 4.5F, Ui.Font.F11, ink);
				ui.text(meta, tx + this.nameW[this.order[k]] + 7, y0 + 7, Ui.Font.F9, 0.6F, dim4);
			}
		}
		ui.end();
	}

	private boolean clashes(int placed, float x, float y, float w, float h) {
		for (int i = 0; i < placed; i++) {
			if (x < this.boxes[i * 4 + 2] + 4 && x + w > this.boxes[i * 4] - 4 && y < this.boxes[i * 4 + 3] + 4 && y + h > this.boxes[i * 4 + 1] - 4) {
				return true;
			}
		}
		return false;
	}

	/** The line under a name: "18 m · 6 above", "412 m", "1.2 km", "4 min left", "2 min ago" (made again on change). */
	private String meta(int index, Waypoint w, double d, double camY, boolean near, long now) {
		int metres = (int) Math.round(d);
		int above = (int) Math.round(w.y() - camY);
		long minutes = w.kind() == Waypoint.Kind.DEATH ? (now - w.created()) / 60_000 : w.expires() > 0 ? Math.max(0, (w.expires() - now + 59_999) / 60_000) : -1;
		long key = (long) metres << 32 ^ (long) (near ? above : 0) << 16 ^ minutes ^ (near ? 1L << 62 : 0);
		if (this.metaKey[index] != key || this.metaText[index] == null) {
			String dist = metres >= 1000 ? String.format(Locale.ROOT, "%.1f km", metres / 1000.0) : metres + " m";
			String s;
			if (w.kind() == Waypoint.Kind.DEATH) {
				s = minutes < 1 ? "just now" : minutes + " min ago";
				s = near ? dist + "  ·  " + s : s;
			} else if (w.expires() > 0) {
				s = near ? dist + "  ·  " + minutes + " min left" : minutes + " min left";
			} else if (near) {
				s = dist + "  ·  " + (Math.abs(above) < 2 ? "level" : Math.abs(above) + (above > 0 ? " above" : " below"));
			} else {
				s = dist;
			}
			this.metaKey[index] = key;
			this.metaText[index] = s;
		}
		return this.metaText[index];
	}

	/** A ring on the ground round a near waypoint (16 points, projected). */
	private void ring(Ui ui, Waypoint w, Vec3 cam, float sw, float sh, int argb) {
		float px = Float.NaN;
		float py = 0;
		for (int i = 0; i <= 16; i++) {
			double a = i * Math.PI * 2 / 16;
			float[] p = this.project(w.x() + 0.5 + Math.cos(a) * 1.6 - cam.x, w.y() + 0.05 - cam.y, w.z() + 0.5 + Math.sin(a) * 1.6 - cam.z, sw, sh);
			if (p == null) {
				px = Float.NaN;
				continue;
			}
			if (!Float.isNaN(px)) {
				ui.line(px, py, p[0], p[1], 1.5F, argb);
			}
			px = p[0];
			py = p[1];
		}
	}

	private final float[] point = new float[2];
	private final float[] point2 = new float[2];
	private boolean flip;

	/** A point relative to the camera, on screen (design pixels), or null behind it. Alternates two arrays. */
	private float[] project(double x, double y, double z, float sw, float sh) {
		this.v.set((float) x, (float) y, (float) z, 1);
		this.viewProj.transform(this.v);
		if (this.v.w <= 0.05F) {
			return null;
		}
		float[] out = (this.flip = !this.flip) ? this.point : this.point2;
		out[0] = (this.v.x / this.v.w * 0.5F + 0.5F) * sw;
		out[1] = (1 - (this.v.y / this.v.w * 0.5F + 0.5F)) * sh;
		return out;
	}
}
