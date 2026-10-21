package dev.voidmc.fray.motion;

import java.util.Locale;

/** Keyframe interpolations: vanilla's two (LINEAR, CATMULLROM) and STEP (vanilla's pre/post targets). */
public enum Interp {
	LINEAR, CATMULLROM, STEP;

	public static Interp parse(String s) {
		return switch (s.toLowerCase(Locale.ROOT)) {
			case "linear" -> LINEAR;
			case "catmullrom", "catmull_rom", "smooth" -> CATMULLROM;
			case "step" -> STEP;
			default -> throw new IllegalArgumentException("unknown interpolation " + s);
		};
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
