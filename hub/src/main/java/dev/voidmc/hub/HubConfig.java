package dev.voidmc.hub;

/**
 * Prototype switches, read from system properties so a launch argument (or
 * {@code JAVA_TOOL_OPTIONS}) flips them without a config screen.
 *
 * <ul>
 *   <li>{@code -Dvoidhub.replaceInventory=false} — keep vanilla's inventory and chest screens (the
 *       hub still opens on Right Shift). Default on for this test.</li>
 *   <li>{@code -Dvoidhub.prewarm=false} — create Ultralight on the first open instead of when a
 *       world is joined.</li>
 *   <li>{@code -Dvoidhub.hideHud=false} — keep the vanilla HUD under the hub (the design hides it).</li>
 *   <li>{@code -Dvoidhub.upload=full} — upload the whole surface on every change (the baseline the
 *       dirty-rectangle path is measured against).</li>
 * </ul>
 */
public final class HubConfig {

	private static volatile Boolean replaceInventory;
	private static volatile Boolean hideHud;

	private HubConfig() {
	}

	public static boolean replaceInventory() {
		Boolean v = replaceInventory;
		return v != null ? v : Boolean.parseBoolean(System.getProperty("voidhub.replaceInventory", "true"));
	}

	public static void setReplaceInventory(boolean on) {
		replaceInventory = on;
	}

	public static boolean prewarm() {
		return Boolean.parseBoolean(System.getProperty("voidhub.prewarm", "true"));
	}

	private static volatile Boolean frozenMap;

	/**
	 * The Map page over Atlas's still of the world (drawn once, then the world is not drawn at all),
	 * at the hub's own 34 % dim. On by default; {@code -Dvoidhub.frozenMap=false} keeps the live world.
	 */
	public static boolean frozenMap() {
		Boolean v = frozenMap;
		return v != null ? v : Boolean.parseBoolean(System.getProperty("voidhub.frozenMap", "true"));
	}

	/** Overrides the switch; null goes back to the system property (tests). */
	public static void setFrozenMap(Boolean on) {
		frozenMap = on;
	}

	public static boolean hideHud() {
		Boolean v = hideHud;
		return v != null ? v : Boolean.parseBoolean(System.getProperty("voidhub.hideHud", "true"));
	}

	public static void setHideHud(boolean on) {
		hideHud = on;
	}
}
