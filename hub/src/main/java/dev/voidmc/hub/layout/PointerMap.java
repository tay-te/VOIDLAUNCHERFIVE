package dev.voidmc.hub.layout;

/**
 * How a pointer on the VOID screen reaches vanilla's container logic.
 *
 * <p>The VOID screen does not reimplement clicking. It hands every mouse event to
 * {@code AbstractContainerScreen} — pickup, place, split, shift-click, drag-distribute,
 * double-click collect, outside-drop, bundles, cloning — after moving the event's position into
 * vanilla's own geometry: onto the centre of the slot the VOID layout says is under the pointer,
 * onto a point inside vanilla's panel that is on no slot (VOID chrome), or far outside the panel
 * (off every VOID surface, which drops a carried stack exactly as clicking outside vanilla's
 * panel does). Vanilla then decides the click type from that position and the modifiers, so the
 * VOID screen inherits vanilla's behaviour instead of approximating it.
 *
 * <p>Pure: {@link Geometry} carries the vanilla slot positions as plain ints, so the mapping is
 * tested without the game against a copy of vanilla's hover rule ({@link #vanillaSlotAt}).
 */
public final class PointerMap {

	private PointerMap() {
	}

	public sealed interface Target permits OnSlot, OnChrome, Outside {
	}

	public record OnSlot(HubLayout.Cell cell) implements Target {
	}

	public record OnChrome() implements Target {
	}

	public record Outside() implements Target {
	}

	/** What the pointer is over, in the VOID layout. */
	public static Target resolve(HubLayout layout, double physX, double physY) {
		HubLayout.Cell cell = layout.cellAt(physX, physY);
		if (cell != null) {
			return new OnSlot(cell);
		}
		return layout.onSurface(physX, physY) ? new OnChrome() : new Outside();
	}

	/**
	 * Vanilla's view of the menu: the panel position and size (GUI px) and each slot's top-left,
	 * indexed by menu slot index.
	 */
	public record Geometry(int leftPos, int topPos, int imageW, int imageH, int[] slotX, int[] slotY) {
	}

	/** The GUI-px point to give vanilla for a target. */
	public static double[] vanillaPoint(Target target, Geometry g) {
		if (target instanceof OnSlot on) {
			int s = on.cell().slot();
			return new double[] {g.leftPos() + g.slotX()[s] + 8.0, g.topPos() + g.slotY()[s] + 8.0};
		}
		if (target instanceof OnChrome) {
			int[] dead = deadPoint(g);
			return new double[] {g.leftPos() + dead[0] + 0.5, g.topPos() + dead[1] + 0.5};
		}
		return new double[] {g.leftPos() - 4096.0, g.topPos() - 4096.0};
	}

	/**
	 * A point inside vanilla's panel that no slot's hover box covers. Scans from the top-left;
	 * every vanilla container panel has a margin around its slots, so this is found at once.
	 */
	public static int[] deadPoint(Geometry g) {
		for (int y = 0; y < g.imageH(); y++) {
			for (int x = 0; x < g.imageW(); x++) {
				if (vanillaSlotAt(g, g.leftPos() + x + 0.5, g.topPos() + y + 0.5) < 0) {
					return new int[] {x, y};
				}
			}
		}
		throw new IllegalStateException("vanilla panel has no point off every slot");
	}

	/**
	 * Vanilla's hover rule, copied from {@code AbstractContainerScreen.isHovering}: a slot owns
	 * {@code [x - 1, x + 17)} on both axes, relative to the panel. First slot in menu order wins.
	 */
	public static int vanillaSlotAt(Geometry g, double gx, double gy) {
		double xm = gx - g.leftPos();
		double ym = gy - g.topPos();
		for (int i = 0; i < g.slotX().length; i++) {
			int left = g.slotX()[i];
			int top = g.slotY()[i];
			if (xm >= left - 1 && xm < left + 16 + 1 && ym >= top - 1 && ym < top + 16 + 1) {
				return i;
			}
		}
		return -1;
	}

	/** Vanilla's {@code hasClickedOutside} for the plain panel. */
	public static boolean vanillaOutside(Geometry g, double gx, double gy) {
		return gx < g.leftPos() || gy < g.topPos() || gx >= g.leftPos() + g.imageW() || gy >= g.topPos() + g.imageH();
	}
}
