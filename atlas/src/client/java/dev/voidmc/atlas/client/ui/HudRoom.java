package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.AtlasHud;
import dev.voidmc.atlas.core.KeepOut;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The room other mods' HUDs keep ({@link AtlasHud#reserve}), asked of them once a frame, and where Atlas's HUD pieces
 * go to keep out of it ({@link KeepOut}). With nothing reserved, every piece stays where it is, at no cost.
 */
public final class HudRoom implements AtlasHud {
	public static final HudRoom INSTANCE = new HudRoom();

	private final Map<String, Supplier<@Nullable Rect>> reservations = new ConcurrentHashMap<>();
	/** This frame's rectangles, in GUI pixels (x0, y0, x1, y1 each). */
	private float[] gui = new float[0];
	private int count;

	private HudRoom() {
	}

	@Override
	public void reserve(String id, Supplier<@Nullable Rect> rect) {
		if (id != null && rect != null) {
			this.reservations.put(id, rect);
		}
	}

	@Override
	public void unreserve(String id) {
		if (id != null) {
			this.reservations.remove(id);
		}
	}

	/** Asks each reservation for its rectangle (once a frame, before the HUD is drawn). */
	public void poll() {
		if (this.reservations.isEmpty()) {
			this.count = 0;
			return;
		}
		float[] out = this.gui.length >= this.reservations.size() * 4 ? this.gui : new float[this.reservations.size() * 4];
		int n = 0;
		for (var e : this.reservations.entrySet()) {
			Rect r;
			try {
				r = e.getValue().get();
			} catch (RuntimeException ex) {
				r = null; // a mod's mistake costs only its own room
			}
			if (r == null || r.x1() <= r.x0() || r.y1() <= r.y0() || n * 4 + 4 > out.length) {
				continue;
			}
			out[n * 4] = r.x0();
			out[n * 4 + 1] = r.y0();
			out[n * 4 + 2] = r.x1();
			out[n * 4 + 3] = r.y1();
			n++;
		}
		this.gui = out;
		this.count = n;
	}

	/** Whether anything is reserved this frame. */
	public boolean any() {
		return this.count > 0;
	}

	/** This frame's rectangles in a {@link Ui}'s units (its unit to GUI pixels is {@link Ui#scale}). */
	private float[] in(Ui ui) {
		float[] r = new float[this.count * 4];
		float k = 1 / ui.scale;
		for (int i = 0; i < r.length; i++) {
			r[i] = this.gui[i] * k;
		}
		return r;
	}

	/**
	 * Where a HUD piece of {@code w x h} at {@code (x, y)} (in the Ui's units) goes to keep out of the reserved room,
	 * inside the screen and {@code margin} from its edges: {@code {x, y}}, unmoved when nothing is in the way.
	 */
	public float[] place(Ui ui, float x, float y, float w, float h, float margin) {
		if (this.count == 0) {
			return new float[]{x, y};
		}
		return KeepOut.place(x, y, w, h, ui.width(), ui.height(), margin, this.in(ui), this.count);
	}

	/** For the tour and tests: whether a box (in the Ui's units) lies over any reserved room. */
	public boolean hits(Ui ui, float x, float y, float w, float h) {
		return this.count > 0 && KeepOut.hits(x, y, w, h, this.in(ui), this.count);
	}

	/** For the tour: what is reserved now, in GUI pixels. */
	public static String describe() {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < INSTANCE.count; i++) {
			b.append(String.format(java.util.Locale.ROOT, "(%.0f,%.0f)-(%.0f,%.0f) ", INSTANCE.gui[i * 4], INSTANCE.gui[i * 4 + 1], INSTANCE.gui[i * 4 + 2],
				INSTANCE.gui[i * 4 + 3]));
		}
		return b.toString().trim();
	}
}
