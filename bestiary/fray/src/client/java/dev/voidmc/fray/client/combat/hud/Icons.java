package dev.voidmc.fray.client.combat.hud;

import dev.voidmc.fray.api.StatusType;

/**
 * Fray's status icons (Figma H3: "7×7 icon at 2 px") and the HUD's small glyphs, baked by {@code tools/hud_sprites.py}
 * into the GUI atlas ({@code fray:hud/icon_<status>}): one sprite each. Vanilla effects use their own sprites.
 */
public final class Icons {
	private Icons() {
	}

	/** Draws a status icon {@code size} design px across with its top-left at (x, y). */
	public static void status(Paint p, StatusType s, float x, float y, float size, float alpha) {
		p.blit(Paint.sprite("icon_" + s.id()), x, y, size, size, Paint.alpha(0xFFFFFFFF, alpha));
	}

	/** The Art's spiral (H10). */
	public static void spiral(Paint p, float cx, float cy, float size, int col) {
		p.blit(Paint.sprite("spiral"), cx - size / 2, cy - size / 2, size, size, col);
	}

	/** A small shield (guard broken, affix chips), tinted. */
	public static void shield(Paint p, float x, float y, float size, int col) {
		p.blit(Paint.sprite("icon_shield"), x, y, size, size, col);
	}
}
