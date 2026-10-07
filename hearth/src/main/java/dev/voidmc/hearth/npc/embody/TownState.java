package dev.voidmc.hearth.npc.embody;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.person.Office;
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
 * restart (scaffolding, listed in the settlement's scaffold ledger) is in settlement data.
 */
public final class TownState {
	public final long settlementId;
	public final SettlementPlaces places = new SettlementPlaces();
	public final TownNews news = new TownNews();
	/** Plot id per builder resident, so builders spread over the plots under construction. */
	public final Map<UUID, Integer> builderClaims = new HashMap<>();
	/** Plot id per gatherer (miners, quarriers, diggers, foresters on a plantation), so they spread over excavations. */
	public final Map<UUID, Integer> gatherClaims = new HashMap<>();
	/** Building sites by plot id: the crew's shared view of each (docs/FOLK.md §5). */
	public final Map<Integer, dev.voidmc.hearth.npc.work.BuildSite> sites = new HashMap<>();
	/** Trees a forester has marked for felling (log base, packed), so two foresters never take the same tree. */
	public final java.util.Set<Long> treesClaimed = new java.util.HashSet<>();
	private long alarmUntil = Long.MIN_VALUE;
	private @Nullable BlockPos alarmAt;
	private long bellRungAt = -1_000_000_000L;
	/** Who holds each office (captain, master builder); recomputed when residents come or go. */
	private final Map<Office, UUID> officeHolders = new java.util.EnumMap<>(Office.class);
	private boolean officesDirty = true;
	private boolean officesChanged;

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

	/** Residents came or went: offices may have changed hands. */
	public void invalidateOffices() {
		this.officesDirty = true;
	}

	/** The office a resident holds here (the guard captain, the master builder), or {@link Office#NONE}. */
	public Office office(Settlement settlement, Resident resident) {
		refreshOffices(settlement);
		for (Map.Entry<Office, UUID> e : this.officeHolders.entrySet()) {
			if (e.getValue().equals(resident.id)) {
				return e.getKey();
			}
		}
		return Office.NONE;
	}

	/** The holder of an office, or null if there is none (a trade needs two adults before one of them leads). */
	public @Nullable UUID holder(Settlement settlement, Office office) {
		refreshOffices(settlement);
		return this.officeHolders.get(office);
	}

	/** True once after the offices changed hands, so embodied folk can be re-dressed. */
	public boolean officesChanged() {
		boolean c = this.officesChanged;
		this.officesChanged = false;
		return c;
	}

	private void refreshOffices(Settlement settlement) {
		if (!this.officesDirty) {
			return;
		}
		this.officesDirty = false;
		Map<Office, UUID> before = new java.util.EnumMap<>(this.officeHolders);
		this.officeHolders.clear();
		for (Office o : Office.values()) {
			JobType trade = o.trade();
			UUID h = trade == null ? null : Office.holder(settlement, trade);
			if (h != null) {
				this.officeHolders.put(o, h);
			}
		}
		if (!before.equals(this.officeHolders)) {
			this.officesChanged = true;
		}
	}

	public int gatherClaimsOn(int plotId) {
		int n = 0;
		for (int p : this.gatherClaims.values()) {
			if (p == plotId) {
				n++;
			}
		}
		return n;
	}

	/** The building site of a plot, made on first asking. */
	public dev.voidmc.hearth.npc.work.BuildSite site(int plotId) {
		return this.sites.computeIfAbsent(plotId, dev.voidmc.hearth.npc.work.BuildSite::new);
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
