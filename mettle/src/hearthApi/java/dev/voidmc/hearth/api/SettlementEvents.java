package dev.voidmc.hearth.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Stub of Hearth's settlement events: only the members Mettle uses (see package-info), with Hearth's signatures. */
public final class SettlementEvents {
	public static final Event<StandingChanged> STANDING_CHANGED = EventFactory.createArrayBacked(StandingChanged.class,
		ls -> (w, s, p, from, to) -> {
			for (StandingChanged l : ls) {
				l.onStandingChanged(w, s, p, from, to);
			}
		});

	private SettlementEvents() {
	}

	@FunctionalInterface
	public interface StandingChanged {
		void onStandingChanged(HearthWorld world, Settlement settlement, java.util.UUID player, StandingService.Title from,
			StandingService.Title to);
	}
}
