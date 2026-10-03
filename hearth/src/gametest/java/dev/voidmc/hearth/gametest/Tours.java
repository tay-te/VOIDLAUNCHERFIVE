package dev.voidmc.hearth.gametest;

/**
 * Which client tours run: all of them, or only those named in {@code -Dhearth.tour=integration,folk} (the gametest
 * runner itself can only filter by mod id).
 */
public final class Tours {
	private Tours() {
	}

	public static boolean wanted(String name) {
		String only = System.getProperty("hearth.tour");
		if (only == null || only.isBlank()) {
			return true;
		}
		for (String s : only.split(",")) {
			if (s.trim().equals(name)) {
				return true;
			}
		}
		return false;
	}
}
