package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import org.jspecify.annotations.Nullable;

/**
 * The phase 3 additions to the economy service (purses, household wealth, service prices, fines and bribes, funded
 * projects, business owners) for whoever needs them before the api's {@link EconomyService} carries them — the lead
 * integrating the modules, dev tools and tests. Other modules should code against the api and the request in
 * docs/requests/p3-economy.md, not against this class.
 */
public final class EconomyAccess {
	private EconomyAccess() {
	}

	/** The economy's service with its phase 3 methods, or null if the economy module is not running. */
	public static @Nullable EconomyServiceImpl get() {
		return HearthServices.economy() instanceof EconomyServiceImpl impl ? impl : null;
	}
}
