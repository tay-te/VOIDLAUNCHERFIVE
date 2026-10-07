package dev.voidmc.atlas.api;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Room on the screen that another mod's HUD keeps (a quest panel, a party list): Atlas's own HUD pieces keep out of
 * every reserved rectangle, moved the least way that clears it (the compass strip, the discovery toasts, the beam labels
 * held at the screen's edge, and the minimap when it sits in that corner). Nothing is reserved unless a mod asks.
 * Reach it with {@link AtlasApi#hud()}; everything here is for the client thread.
 */
public interface AtlasHud {
	/**
	 * A rectangle in GUI-scaled pixels (the units {@code GuiGraphics} draws the HUD in), from its top-left
	 * {@code (x0, y0)} to its bottom-right {@code (x1, y1)}.
	 */
	record Rect(float x0, float y0, float x1, float y1) {
		public Rect {
			if (x1 < x0) {
				float t = x0;
				x0 = x1;
				x1 = t;
			}
			if (y1 < y0) {
				float t = y0;
				y0 = y1;
				y1 = t;
			}
		}

		/** The rectangle at {@code (x, y)}, {@code width x height}. */
		public static Rect of(float x, float y, float width, float height) {
			return new Rect(x, y, x + width, y + height);
		}
	}

	/**
	 * Keeps the room {@code rect} gives (asked again each frame, so it can follow your panel's size and whether it is
	 * showing; null: nothing is drawn now, so nothing is kept). The same {@code id} replaces the earlier one.
	 */
	default void reserve(String id, Supplier<@Nullable Rect> rect) {
	}

	/** Lets the room reserved under {@code id} go. */
	default void unreserve(String id) {
	}
}
