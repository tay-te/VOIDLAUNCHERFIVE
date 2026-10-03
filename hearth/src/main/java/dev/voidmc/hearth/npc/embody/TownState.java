package dev.voidmc.hearth.npc.embody;

import dev.voidmc.hearth.npc.place.SettlementPlaces;
import dev.voidmc.hearth.npc.talk.TownNews;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The NPC module's in-memory state for one settlement: its places, its news, whether it is under attack, and which
 * builder is on which plot. Nothing here needs saving (places are recomputed, news is gossip); what must survive a
 * restart (scaffolding) is in settlement data.
 */
public final class TownState {
	public final long settlementId;
	public final SettlementPlaces places = new SettlementPlaces();
	public final TownNews news = new TownNews();
	/** Plot id per builder resident, so builders spread over the plots under construction. */
	public final Map<UUID, Integer> builderClaims = new HashMap<>();
	private long alarmUntil = Long.MIN_VALUE;
	private @Nullable BlockPos alarmAt;
	private long bellRungAt = -1_000_000_000L;

	public TownState(long settlementId) {
		this.settlementId = settlementId;
	}

	/** Raises the alarm (a raid, monsters in the streets) for {@code ticks} game ticks. */
	public void raiseAlarm(long gameTime, BlockPos where, int ticks) {
		this.alarmUntil = Math.max(this.alarmUntil, gameTime + ticks);
		this.alarmAt = where;
	}

	public boolean alarmed(long gameTime) {
		return gameTime < this.alarmUntil;
	}

	public @Nullable BlockPos alarmAt() {
		return this.alarmAt;
	}

	/** True for the one folk who gets to ring the bell for this alarm. */
	public boolean claimBell(long gameTime) {
		if (gameTime - this.bellRungAt < 600) {
			return false;
		}
		this.bellRungAt = gameTime;
		return true;
	}

	public int claimsOn(int plotId) {
		int n = 0;
		for (int p : this.builderClaims.values()) {
			if (p == plotId) {
				n++;
			}
		}
		return n;
	}
}
