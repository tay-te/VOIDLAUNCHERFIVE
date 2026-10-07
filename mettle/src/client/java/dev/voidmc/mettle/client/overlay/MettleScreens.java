package dev.voidmc.mettle.client.overlay;

import java.util.function.BooleanSupplier;

/**
 * Where Mettle's screens open. A host that draws Mettle in its own UI (the VOID Hub's Classes page) registers an opener
 * here; K then opens that instead of the native fallback screen. Hosts find this class reflectively, so neither mod
 * needs the other to build.
 */
public final class MettleScreens {
	private static BooleanSupplier character;

	private MettleScreens() {
	}

	/** The host's "open the character page"; it returns false when it cannot open now (the native screen opens). */
	public static void setCharacterOpener(BooleanSupplier opener) {
		character = opener;
	}

	/** Opens the host's character page, if a host is registered and able. */
	public static boolean openCharacter() {
		return character != null && character.getAsBoolean();
	}
}
