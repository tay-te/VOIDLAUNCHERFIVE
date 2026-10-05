package dev.voidmc.hearth.build.kit.age;

/** Why an addition did not fit, printed for previews when the env var HEARTH_AGE_DBG is set; always false. */
final class Why {
	static final boolean ON = System.getenv("HEARTH_AGE_DBG") != null;

	private Why() {
	}

	static boolean no(String what, String why) {
		if (ON) {
			System.out.println("WHY " + what + ": " + why);
		}
		return false;
	}
}
