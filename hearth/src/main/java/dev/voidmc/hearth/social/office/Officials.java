package dev.voidmc.hearth.social.office;

import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Appoints and keeps a settlement's officials (docs/SOCIAL.md §3). Run once a sim day after the sim has handed out the
 * day's jobs (on {@code DAY_PASSED}), and when an office holder dies or leaves: posts get their job and seat back (the
 * sim's job assignment knows nothing of offices), vacancies are filled, and holders who can no longer serve step down.
 * Other modules find officials by job ({@link dev.voidmc.hearth.api.JobType#REEVE}, {@code LORD}, {@code STEWARD},
 * {@code JUDGE}, {@code JAILER}) or by {@code extra("social").office}.
 */
public final class Officials {
	private Officials() {
	}

	/** An appointment that changed, for the notice board. */
	public record Change(Office office, @Nullable Resident before, @Nullable Resident after) {
	}

	/** Whether the settlement has (or should have) this office now. */
	public static boolean wanted(Office office, Settlement s, SocialConfig.Offices cfg) {
		SettlementTier tier = s.tier();
		return switch (office) {
			case REEVE -> tier.ordinal() >= cfg.reeveTier().ordinal();
			case LORD, STEWARD -> tier.ordinal() >= cfg.lordTier().ordinal();
			case JUDGE -> cfg.judges() && hasBuilt(s, "law/courthouse");
			case JAILER -> cfg.judges() && hasBuilt(s, "law/jail");
			case GUARD_CAPTAIN, PRIEST, MASTER_BUILDER -> tier.ordinal() >= SettlementTier.HAMLET.ordinal();
		};
	}

	/** Brings the settlement's offices up to date; returns what changed. */
	public static List<Change> update(Settlement s, SocialLedger ledger, SocialConfig.Offices cfg, long today) {
		List<Change> changes = new ArrayList<>();
		if (!cfg.enabled()) {
			return changes;
		}
		for (Office office : Office.values()) {
			UUID before = ledger.offices.get(office.id());
			Resident prev = before == null ? null : s.resident(before);
			Resident next = null;
			if (wanted(office, s, cfg)) {
				Set<UUID> others = new HashSet<>();
				ledger.offices.forEach((k, v) -> {
					if (!k.equals(office.id())) {
						others.add(v);
					}
				});
				next = Succession.choose(office, s.residents(), before, ledger.lastLord, cfg.dynasties(), others, today);
			}
			if (prev != null && (next == null || !next.id.equals(prev.id))) {
				stepDown(office, prev);
			}
			if (next == null) {
				ledger.offices.remove(office.id());
			} else {
				ledger.offices.put(office.id(), next.id);
				install(office, s, next);
				if (office == Office.LORD) {
					ledger.lastLord = next.id;
				}
			}
			boolean changed = before == null ? next != null : next == null || !before.equals(next.id);
			if (changed) {
				changes.add(new Change(office, prev, next));
			}
		}
		if (!changes.isEmpty()) {
			s.markDirty();
		}
		return changes;
	}

	/** The holder of an office, or null. */
	public static @Nullable Resident holder(Settlement s, SocialLedger ledger, Office office) {
		UUID id = ledger.offices.get(office.id());
		return id == null ? null : s.resident(id);
	}

	/** The office a resident holds here, or null. */
	public static @Nullable Office officeOf(SocialLedger ledger, UUID resident) {
		for (var e : ledger.offices.entrySet()) {
			if (e.getValue().equals(resident)) {
				return Office.byId(e.getKey());
			}
		}
		return null;
	}

	/** The plot an office is held from, or {@link Resident#NO_PLOT}. */
	public static int seat(Settlement s, Office office) {
		for (String path : office.seats) {
			for (PlotView p : s.plots()) {
				if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals(path)) {
					return p.id();
				}
			}
		}
		return Resident.NO_PLOT;
	}

	private static void install(Office office, Settlement s, Resident r) {
		r.extra("social").putString("office", office.id());
		if (office.post != null) {
			r.job = office.post;
			r.work = seat(s, office);
		}
		if (r.station.ordinal() < office.station.ordinal() || office == Office.PRIEST) {
			r.station = office.station;
		}
	}

	private static void stepDown(Office office, Resident r) {
		r.extra("social").remove("office");
		if (office.post != null && r.job == office.post) {
			// the sim gives them a trade again at its next daily assignment
			r.job = dev.voidmc.hearth.api.JobType.NONE;
			r.work = Resident.NO_PLOT;
			r.station = dev.voidmc.hearth.api.Station.of(r.job);
		}
	}

	static boolean hasBuilt(Settlement s, String path) {
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals(path)) {
				return true;
			}
		}
		return false;
	}
}
