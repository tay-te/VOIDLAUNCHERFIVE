package dev.voidmc.hearth.standing.model;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;

/** Where the standing module's state is saved: per settlement and per Hearth world (docs/STANDING.md, "Saved state"). */
public final class StandingKeys {
	public static final HearthDataKey<TownLedger> TOWN = HearthDataKey.register(Hearth.id("standing/town"), HearthDataKey.Scope.SETTLEMENT,
		TownLedger.CODEC, TownLedger::new);
	public static final HearthDataKey<RegionData> REGION = HearthDataKey.register(Hearth.id("standing/region"), HearthDataKey.Scope.WORLD,
		RegionData.CODEC, RegionData::new);

	private StandingKeys() {
	}

	/** Forces the keys to register during init, before any world loads. */
	public static void init() {
	}
}
