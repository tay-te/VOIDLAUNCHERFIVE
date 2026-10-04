package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Vandalism is a crime (docs/LIVING_WORLD.md §2, §4): a player's damage to a settlement's buildings is gathered into
 * sprees (one crime per player per town per {@code window_ticks}, its worth summed), checked for witnesses (embodied,
 * awake folk who can see the player, through the NPC module), and reported to the law module, which decides what it
 * means. Damage nobody saw is reported later, unwitnessed, when builders find it ({@link #discovered}). Without a law
 * module, witnessed vandalism costs standing directly.
 *
 * <p>The other side: players who put damaged blocks back earn standing ({@code repair:rebuilt}), gathered the same way.
 */
public final class Vandalism {
	private record Key(UUID player, long settlement) {
	}

	private static final class Spree {
		final ServerLevel level;
		final BlockPos first;
		final long started;
		final LongArrayList positions = new LongArrayList();
		long worth;
		boolean witnessed;
		long lastWitnessCheck = Long.MIN_VALUE;
		boolean creative;

		Spree(ServerLevel level, BlockPos first, long started) {
			this.level = level;
			this.first = first;
			this.started = started;
		}
	}

	private static final class Help {
		final ServerLevel level;
		final BlockPos pos;
		final long started;
		int blocks;

		Help(ServerLevel level, BlockPos pos, long started) {
			this.level = level;
			this.pos = pos;
			this.started = started;
		}
	}

	private final RepairRuntime runtime;
	private final Map<Key, Spree> sprees = new LinkedHashMap<>();
	private final Map<Key, Help> helps = new LinkedHashMap<>();
	long reported;
	long reportedUnwitnessed;

	Vandalism(RepairRuntime runtime) {
		this.runtime = runtime;
	}

	/** A player's damage to a building (a break, or TNT they lit). */
	void note(ServerLevel level, Settlement settlement, ServerPlayer player, BlockPos pos, long worth) {
		RepairConfig.Law cfg = RepairModule.config().law();
		long now = this.runtime.now();
		Spree s = this.sprees.computeIfAbsent(new Key(player.getUUID(), settlement.id()), k -> new Spree(level, pos.immutable(), now));
		s.positions.add(pos.asLong());
		s.worth += worth;
		s.creative |= player.isCreative() || player.isSpectator();
		// witnesses are looked for once a second while the spree goes on: one who saw any of it saw the spree
		if (!s.witnessed && now - s.lastWitnessCheck >= 20) {
			s.lastWitnessCheck = now;
			s.witnessed = witnessed(level, player, cfg.witnessRadius());
		}
	}

	/** Whether an awake, embodied folk can see the player now. */
	static boolean witnessed(ServerLevel level, ServerPlayer player, double radius) {
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return false;
		}
		Vec3 at = player.position();
		for (NpcService.FolkHandle f : npc.folkNear(level, at, radius)) {
			if (f.awake() && f.canSee(player)) {
				return true;
			}
		}
		return false;
	}

	void tick(long now) {
		int window = RepairModule.config().law().windowTicks();
		Iterator<Map.Entry<Key, Spree>> it = this.sprees.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Key, Spree> e = it.next();
			if (now - e.getValue().started >= window) {
				it.remove();
				report(e.getKey(), e.getValue(), now);
			}
		}
		int helpWindow = 2400;
		Iterator<Map.Entry<Key, Help>> hi = this.helps.entrySet().iterator();
		while (hi.hasNext()) {
			Map.Entry<Key, Help> e = hi.next();
			if (now - e.getValue().started >= helpWindow) {
				hi.remove();
				deedHelp(e.getKey(), e.getValue());
			}
		}
	}

	private void report(Key key, Spree spree, long now) {
		RepairConfig.Law cfg = RepairModule.config().law();
		RepairRuntime.Located at = this.runtime.find(key.settlement);
		if (at == null || spree.creative && cfg.exemptCreative() || !spree.witnessed) {
			// nobody saw it: the builders will find it, and then the player is a suspect (see discovered)
			return;
		}
		RepairQueue q = at.settlement().data(RepairModule.QUEUE);
		for (int i = 0; i < spree.positions.size(); i++) {
			Damage d = q.get(spree.positions.getLong(i));
			if (d != null) {
				d.set(Damage.REPORTED, true);
			}
		}
		at.settlement().markDirty();
		tell(spree.level, key.player, at.settlement(), spree.first, spree.worth, spree.positions.size(), true);
	}

	/**
	 * Builders found damage a player did that nobody saw (on claiming it, or abstract labour mending it): the player is a
	 * suspect. One report per offender per call, with the worth of everything found.
	 */
	void discovered(ServerLevel level, Settlement settlement, List<Damage> found) {
		RepairConfig.Law cfg = RepairModule.config().law();
		if (!cfg.reportVandalism() || !cfg.reportOnDiscovery()) {
			return;
		}
		Map<UUID, List<Damage>> byOffender = new LinkedHashMap<>();
		for (Damage d : found) {
			if (d.offender != null && !d.has(Damage.REPORTED) && !d.has(Damage.LITTER)
				&& (d.cause == dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause.PLAYER || d.cause == dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause.EXPLOSION)) {
				byOffender.computeIfAbsent(d.offender, k -> new ArrayList<>()).add(d);
			}
		}
		byOffender.forEach((player, list) -> {
			long worth = 0;
			for (Damage d : list) {
				d.set(Damage.REPORTED, true);
				worth += d.expected() == null ? 1 : Math.max(1, RepairBill.worth(settlement, d.expected()));
			}
			settlement.markDirty();
			ServerPlayer online = this.runtime.server.getPlayerList().getPlayer(player);
			if (online != null && cfg.exemptCreative() && (online.isCreative() || online.isSpectator())) {
				return;
			}
			tell(level, player, settlement, list.getFirst().blockPos(), worth, list.size(), false);
			this.reportedUnwitnessed++;
		});
	}

	private void tell(ServerLevel level, UUID player, Settlement settlement, BlockPos pos, long worth, int blocks, boolean witnessed) {
		RepairConfig.Law cfg = RepairModule.config().law();
		if (!cfg.reportVandalism()) {
			return;
		}
		LawService law = HearthServices.law();
		if (law != null) {
			UUID victim = null;
			try {
				LawService.Owner owner = law.owner(level, pos);
				victim = owner == null ? null : owner.resident();
			} catch (RuntimeException ignored) {
				// ownership is the law module's to know; without it the settlement is the victim
			}
			law.report(new LawService.Crime(LawService.Offence.VANDALISM, player, settlement.id(), pos, victim, Math.max(1, worth), witnessed,
				level.getGameTime()));
			this.reported++;
			return;
		}
		StandingService standing = HearthServices.standing();
		if (standing != null && witnessed) {
			int weight = Math.max(-30, cfg.deedPerBlock() * blocks);
			standing.deed(new StandingService.Deed(player, settlement.id(), "repair:vandalism", weight, null, pos, true));
			this.reported++;
		}
	}

	/** A player put back a damaged block of a settlement's building. */
	void helped(ServerLevel level, Settlement settlement, ServerPlayer player, BlockPos pos) {
		long now = this.runtime.now();
		this.helps.computeIfAbsent(new Key(player.getUUID(), settlement.id()), k -> new Help(level, pos.immutable(), now)).blocks++;
		this.runtime.fires.playerRebuilt(settlement, player);
	}

	private void deedHelp(Key key, Help help) {
		StandingService standing = HearthServices.standing();
		if (standing == null) {
			return;
		}
		int max = RepairModule.config().fire().rebuildDeedMax();
		int weight = Math.min(max, 1 + help.blocks / 8);
		standing.deed(new StandingService.Deed(key.player, key.settlement, "repair:rebuilt", weight, null, help.pos, witnessedHelp(help, key.player)));
	}

	private boolean witnessedHelp(Help help, UUID player) {
		ServerPlayer p = this.runtime.server.getPlayerList().getPlayer(player);
		return p != null && witnessed(help.level, p, RepairModule.config().law().witnessRadius());
	}

	/** Flushes every spree now (server stopping, tests). */
	void flushAll() {
		tick(Long.MAX_VALUE / 2);
	}

	public String stats() {
		return this.reported + " reported (" + this.reportedUnwitnessed + " on discovery), " + this.sprees.size() + " sprees open";
	}
}
