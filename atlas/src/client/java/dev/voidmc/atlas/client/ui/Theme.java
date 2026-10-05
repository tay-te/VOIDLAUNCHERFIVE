package dev.voidmc.atlas.client.ui;

/** The VOID UI colours (ARGB), from the Figma file: flat fills, hairlines, three inks, violet for what is live. */
public final class Theme {
	/** The window. */
	public static final int SHELL = 0xFF0B0B0C;
	/** Wells: sunken controls, tiles, inputs. */
	public static final int WELL = 0xFF131315;
	/** Islands: cards, rows, readouts. */
	public static final int ISLAND = 0xFF191A1C;
	/** A selected segment, an active chip. */
	public static final int RAISED = 0xFF26272B;
	/** A button face. */
	public static final int BUTTON = 0xFF212225;
	/** The map's well: what unexplored land shows. */
	public static final int MAP_WELL = 0xFF0E0F11;

	public static final int INK = 0xFFEDEEEF;
	public static final int INK_2 = 0xFF9A9DA1;
	public static final int INK_3 = 0xFF808387;
	public static final int INK_4 = 0xFF626569;

	/** The player and the selection: live. */
	public static final int VIOLET = 0xFF9F8BFF;
	/** Another player, online: live. */
	public static final int GREEN = 0xE63DD68C;

	public static final int HAIR_5 = 0x0DFFFFFF;
	public static final int HAIR_6 = 0x0FFFFFFF;
	public static final int HAIR_7 = 0x12FFFFFF;
	public static final int HAIR_8 = 0x14FFFFFF;
	public static final int HAIR_10 = 0x1AFFFFFF;
	public static final int HAIR_12 = 0x1FFFFFFF;
	public static final int HAIR_16 = 0x29FFFFFF;

	private Theme() {
	}

	/** {@code argb} with its alpha multiplied by {@code a}. */
	public static int alpha(int argb, float a) {
		int alpha = Math.round((argb >>> 24) * Math.max(0, Math.min(1, a)));
		return alpha << 24 | (argb & 0xFFFFFF);
	}

	/** An opaque colour from 0xRRGGBB. */
	public static int opaque(int rgb) {
		return 0xFF000000 | rgb;
	}

	/** {@code #EDEEEF} at an opacity, the design's glyph and hairline convention. */
	public static int ink(float a) {
		return alpha(INK, a);
	}
}
