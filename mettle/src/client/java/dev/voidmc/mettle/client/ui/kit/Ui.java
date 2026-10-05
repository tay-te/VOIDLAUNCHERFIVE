package dev.voidmc.mettle.client.ui.kit;

/**
 * VOID panel tokens (pvp/design/quiet-cell-system.md and the Figma page "Mettle · Character & Gear"). Flat: state is
 * carried by fill steps, never by blur, gradients or shadows. Colour encodes a value or a state, never decoration.
 */
public final class Ui {
	public static final int SHELL = 0xFF0B0B0C;
	public static final int BASE = 0xFF131315;
	public static final int CARD = 0xFF191A1C;
	public static final int CARD_HOVER = 0xFF1D1E20;
	public static final int RAISED = 0xFF212225;
	public static final int WELL = 0xFF0E0E10;
	public static final int TILE = 0xFF161618;

	public static final int TEXT = 0xFFEDEEEF;
	public static final int TEXT_2 = 0xFF9A9DA1;
	public static final int TEXT_3 = 0xFF626569;
	public static final int TEXT_CHIP = 0xFFC4C6C9;

	/** "Temper", a muted tempered-steel blue: live values only (XP, weight fill, available perks, the selection). */
	public static final int ACCENT = 0xFFA7B9D8;

	/** States only: a heavy load, and things that went wrong. */
	public static final int AMBER = 0xFFE2B866;
	public static final int DANGER = 0xFFC05B54;

	/** Behind every open panel. No blur, by the house rules. */
	public static final int DIM = 0x800A0B0C;

	private Ui() {
	}

	/** White at an alpha, for hairlines and quiet fills. */
	public static int white(float alpha) {
		return alpha(0xFFFFFF, alpha);
	}

	public static int alpha(int rgb, float alpha) {
		return (Math.round(Math.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
	}
}
