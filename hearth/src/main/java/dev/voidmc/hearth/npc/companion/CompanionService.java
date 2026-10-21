package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.embody.EmbodimentManager;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.service.Escorts;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Companions in the world (docs/LIVING_WORLD_2.md §8): hiring and letting go, the wage at the home town's dawn, the
 * player's logging off and on (folded into the record to wait, embodied beside them again), being left behind in another
 * dimension, loyalty, and death. Abstract by default: a companion is a resident flagged away with
 * {@code extra("companion")}; their folk exists only while their player is online and in reach, pinned so the embodiment
 * manager never folds them, within a server cap that shrinks as the server slows. Server thread.
 */
public final class CompanionService {
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("companions");
	/** Ticks between the presence passes (embody, re-pin, the waiting clock, the server cap). */
	static final int PASS_TICKS = 20;
	private static final String WHY = "companion";

	/** Online players' companions by home town: who to pay when a town's day passes (offline days are not charged). */
	private static final Map<Long, List<Hire>> BY_TOWN = new HashMap<>();
	private static int ticks;

	record Hire(UUID player, UUID resident) {
	}

	private CompanionService() {
	}

	static PlaceConfig cfg() {
		return Companions.config();
	}

	// ---------------------------------------------------------------- hiring and letting go

	public enum HireResult {
		OK, FULL, UNWILLING, CANNOT_PAY, NO_ROOM, NO_ECONOMY
	}

	/** Hires a resident as the player's companion: the fee goes into their purse, they come along from today. */
	public static HireResult hire(ServerPlayer player, Settlement s, Resident r) {
		PlaceConfig cfg = cfg();
		CompanionRoster roster = CompanionRoster.of(player);
		if (roster.size() >= cfg.companions().perPlayer()) {
			return HireResult.FULL;
		}
		if (!CompanionRules.willing(s, r)) {
			return HireResult.UNWILLING;
		}
		if (!(player.level() instanceof ServerLevel level) || level.dimension() != s.dimension() || embodiedCount() >= serverCap(level.getServer())) {
			return HireResult.NO_ROOM;
		}
		EconomyService eco = HearthServices.economy();
		long fee = CompanionRules.fee(r, cfg);
		if (eco == null) {
			return HireResult.NO_ECONOMY;
		}
		if (!eco.charge(player, s, r, fee)) {
			return HireResult.CANNOT_PAY;
		}
		long day = day(level, s);
		CompanionState.hire(r, player.getUUID(), day, CompanionRules.wage(r, cfg));
		s.setResidentAway(r.id, Long.MAX_VALUE, WHY);
		s.markDirty();
		CompanionRoster.set(player, roster.with(new CompanionRoster.Entry(s.id(), s.dimension(), r.id)));
		index(player.getUUID(), s.id(), r.id);
		Escorts.pin(r.id, player.getUUID());
		embody(level, s, r, player);
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			standing.remember(r.id, player.getUUID(), "npc:hired", 5);
		}
		fire(level, s, r.id, player.getUUID(), SettlementEvents.Companion.Phase.HIRED);
		return HireResult.OK;
	}

	/**
	 * They stop serving: told to go home ({@code DISMISSED}), or gone of their own accord ({@code LEFT}: unpaid, disloyal,
	 * left behind too long). Gear goes back to the player (into their pack, or at their feet). Their folk walks off and is
	 * folded with the rest of the town's when no player is near.
	 */
	public static void release(MinecraftServer server, Settlement s, Resident r, SettlementEvents.Companion.Phase phase, @Nullable String why) {
		CompanionState st = CompanionState.of(r);
		if (st == null) {
			return;
		}
		UUID playerId = st.player();
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		Folk f = NpcModule.embodiment().entityOf(r.id);
		List<ItemStack> gear = CompanionGear.takeAll(st, server.registryAccess());
		if (f != null) {
			CompanionGear.undress(f, st, server.registryAccess());
			if (f.brain() != null && why != null) {
				f.brain().handle().say(Component.translatable(why), null);
			}
		}
		if (!gear.isEmpty()) {
			if (player != null) {
				for (ItemStack g : gear) {
					CompanionGear.give(player, g);
				}
				player.sendSystemMessage(Component.translatable("hearth.companion.gear_back", r.givenName).withStyle(ChatFormatting.GRAY));
			} else if (f != null && f.level() instanceof ServerLevel fl) {
				CompanionGear.dropAt(fl, f.position(), gear);
			}
		}
		CompanionState.release(r);
		Escorts.unpin(r.id);
		s.setResidentAway(r.id, Long.MIN_VALUE, "home");
		s.markDirty();
		unindex(s.id(), r.id);
		if (player != null) {
			CompanionRoster.set(player, CompanionRoster.of(player).without(r.id));
			if (phase == SettlementEvents.Companion.Phase.LEFT) {
				player.sendSystemMessage(Component.translatable("hearth.companion.left", r.fullName(), s.name()).withStyle(ChatFormatting.GRAY));
			}
		}
		ServerLevel level = server.getLevel(s.dimension());
		if (level != null) {
			fire(level, s, r.id, playerId, phase);
		}
	}

	/**
	 * A companion died serving (their folk was killed): what they carried falls where they fell, their family mourns and
	 * holds it against the player who led them there. The resident is removed by the embodiment manager (permadeath),
	 * and the funeral follows as for any death. Called before that removal, while the record still says who they served.
	 */
	public static void died(ServerLevel level, Folk folk, Settlement s, Resident r) {
		CompanionState st = CompanionState.of(r);
		if (st == null) {
			return;
		}
		UUID playerId = st.player();
		CompanionGear.dropAt(level, folk.position(), CompanionGear.takeAll(st, level.registryAccess()));
		StandingService standing = HearthServices.standing();
		for (Resident kin : CompanionRules.kin(s, r)) {
			if (standing != null) {
				standing.remember(kin.id, playerId, "npc:companion_died", -30);
			}
		}
		if (standing != null) {
			standing.deed(new StandingService.Deed(playerId, s.id(), "npc:companion_lost", -3, r.id, folk.blockPosition(), false));
		}
		Escorts.unpin(r.id);
		unindex(s.id(), r.id);
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
		if (player != null) {
			CompanionRoster.set(player, CompanionRoster.of(player).without(r.id));
			player.sendSystemMessage(Component.translatable("hearth.companion.died", r.fullName(), s.name()).withStyle(ChatFormatting.DARK_RED));
		}
		CompanionState.release(r);
		s.setResidentAway(r.id, Long.MIN_VALUE, "dead");
		fire(level, s, r.id, playerId, SettlementEvents.Companion.Phase.DIED);
	}

	/** A companion's record went (old age while waiting folded, a removal): the hire ends with them. */
	static void removed(HearthWorld w, Settlement s, Resident r) {
		CompanionState st = CompanionState.of(r);
		if (st == null) {
			return;
		}
		Escorts.unpin(r.id);
		unindex(s.id(), r.id);
		ServerPlayer player = w.level().getServer().getPlayerList().getPlayer(st.player());
		if (player != null) {
			for (ItemStack g : CompanionGear.takeAll(st, w.level().registryAccess())) {
				CompanionGear.give(player, g);
			}
			CompanionRoster.set(player, CompanionRoster.of(player).without(r.id));
		}
		fire(w.level(), s, r.id, st.player(), SettlementEvents.Companion.Phase.DIED);
	}

	// ---------------------------------------------------------------- the day's wage

	/** A town's day passed: its companions whose players are online are paid (or not) at dawn. */
	static void dayPassed(HearthWorld w, Settlement s, long day) {
		List<Hire> hires = BY_TOWN.get(s.id());
		if (hires == null || hires.isEmpty()) {
			return;
		}
		MinecraftServer server = w.level().getServer();
		EconomyService eco = HearthServices.economy();
		for (Hire h : List.copyOf(hires)) {
			Resident r = s.resident(h.resident());
			CompanionState st = r == null ? null : CompanionState.of(r);
			if (st == null || !st.player().equals(h.player())) {
				unindex(s.id(), h.resident());
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(h.player());
			Resident payee = r;
			CompanionRules.Payday pd = CompanionRules.payday(st, day, player != null, bits -> eco != null && player != null && eco.charge(player, s, payee, bits),
				cfg());
			s.markDirty();
			switch (pd) {
				case PAID -> {
					if (player != null) {
						player.sendSystemMessage(Component.translatable("hearth.companion.paid", r.givenName, eco.format(st.wage())).withStyle(ChatFormatting.GRAY));
					}
				}
				case UNPAID -> {
					if (player != null) {
						player.sendSystemMessage(Component.translatable("hearth.companion.unpaid", r.givenName,
							eco == null ? Component.literal(st.wage() + "") : eco.format(st.wage())).withStyle(ChatFormatting.GOLD));
					}
				}
				case LEFT -> release(server, s, r, SettlementEvents.Companion.Phase.LEFT, "hearth.companion.say.unpaid_leave");
				default -> {
				}
			}
		}
	}

	// ---------------------------------------------------------------- what the player does

	/** A crime: their companions take it ill (theft from their own town most of all; the greedy do not mind elsewhere). */
	static void crime(HearthWorld w, Settlement where, LawService.Crime crime) {
		MinecraftServer server = w.level().getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(crime.offender());
		if (player == null) {
			return;
		}
		for (CompanionRoster.Entry e : CompanionRoster.of(player).entries()) {
			Settlement home = settlement(server, e);
			Resident r = home == null ? null : home.resident(e.resident());
			CompanionState st = r == null ? null : CompanionState.of(r);
			if (st == null) {
				continue;
			}
			boolean greedy = NpcData.of(r).traits().contains(Trait.GREEDY);
			int delta = CompanionRules.crimeLoyalty(home.id() == crime.settlementId(), greedy, severity(crime.offence()));
			if (delta == 0) {
				continue;
			}
			st.addLoyalty(delta);
			home.markDirty();
			if (CompanionRules.leaves(st, cfg())) {
				release(server, home, r, SettlementEvents.Companion.Phase.LEFT, "hearth.companion.say.disloyal_leave");
			} else {
				Folk f = NpcModule.embodiment().entityOf(r.id);
				if (f != null && f.brain() != null) {
					CompanionBarks.say(f.brain().handle(), "companion_crime", player);
				}
			}
		}
	}

	/** A quest done: their companions are proud to have been there. */
	static void questDone(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (CompanionRoster.Entry e : CompanionRoster.of(player).entries()) {
			Settlement home = settlement(server, e);
			Resident r = home == null ? null : home.resident(e.resident());
			CompanionState st = r == null ? null : CompanionState.of(r);
			if (st == null) {
				continue;
			}
			st.addLoyalty(3);
			home.markDirty();
			Folk f = NpcModule.embodiment().entityOf(r.id);
			if (f != null && f.brain() != null && f.distanceToSqr(player) < 32 * 32) {
				CompanionBarks.say(f.brain().handle(), "companion_deed", player);
			}
		}
	}

	static int severity(LawService.Offence o) {
		return switch (o) {
			case TRESPASS -> 1;
			case LOCKPICKING, POACHING, VANDALISM -> 3;
			case THEFT, PICKPOCKETING -> 4;
			case ASSAULT, RESISTING_ARREST, JAILBREAK -> 6;
			case ROBBERY -> 8;
			case MURDER -> 10;
		};
	}

	// ---------------------------------------------------------------- presence: login, logout, reach

	/** Joined: their companions are found and, once the player stands in a loaded world, brought to their side. */
	static void joined(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompanionRoster roster = CompanionRoster.of(player);
		CompanionRoster kept = roster;
		for (CompanionRoster.Entry e : roster.entries()) {
			Settlement s = settlement(server, e);
			Resident r = s == null ? null : s.resident(e.resident());
			CompanionState st = r == null ? null : CompanionState.of(r);
			if (st == null || !st.player().equals(player.getUUID())) {
				// gone while the player was away (old age, the town abandoned): the roster forgets them
				kept = kept.without(e.resident());
				continue;
			}
			index(player.getUUID(), s.id(), r.id);
			Escorts.pin(r.id, player.getUUID());
		}
		if (kept != roster) {
			CompanionRoster.set(player, kept);
		}
	}

	/** Logged off: their companions fold into the record and wait (abstract, away from their town, not paid). */
	static void left(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		EmbodimentManager em = NpcModule.embodiment();
		for (CompanionRoster.Entry e : CompanionRoster.of(player).entries()) {
			unindex(e.settlementId(), e.resident());
			Escorts.unpin(e.resident());
			Folk f = em.entityOf(e.resident());
			if (f != null && f.level() instanceof ServerLevel level) {
				em.fold(level, f, true);
			}
			Settlement s = settlement(server, e);
			Resident r = s == null ? null : s.resident(e.resident());
			CompanionState st = r == null ? null : CompanionState.of(r);
			if (st != null) {
				st.setApartSince(-1);
				s.markDirty();
			}
		}
	}

	/** Every second: companions of online players are kept beside them (or where told to wait), within the server cap. */
	static void tick(MinecraftServer server) {
		if (++ticks % PASS_TICKS != 0 || BY_TOWN.isEmpty()) {
			return;
		}
		TIMER.time(() -> pass(server));
	}

	private static void pass(MinecraftServer server) {
		EmbodimentManager em = NpcModule.embodiment();
		int cap = serverCap(server);
		boolean shed = LoadCaps.shedding(LoadCaps.mspt(server), LoadCaps.highMspt());
		int embodied = embodiedCount();
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CompanionRoster roster = CompanionRoster.of(player);
			if (roster.size() == 0) {
				continue;
			}
			for (CompanionRoster.Entry e : roster.entries()) {
				Settlement s = settlement(server, e);
				Resident r = s == null ? null : s.resident(e.resident());
				CompanionState st = r == null ? null : CompanionState.of(r);
				if (st == null || !st.player().equals(player.getUUID())) {
					continue;
				}
				Escorts.pin(r.id, player.getUUID());
				Folk f = em.entityOf(r.id);
				boolean here = player.level().dimension() == s.dimension() && !player.isSpectator();
				if (here && st.apartSince() >= 0 || !here && st.apartSince() < 0) {
					st.setApartSince(here ? -1 : now);
					s.markDirty();
				}
				boolean embodiedNow = f != null && !f.isFolded();
				net.minecraft.core.BlockPos waitAt = st.follow() == CompanionState.Follow.WAIT ? st.waitAt() : null;
				boolean nearWait = waitAt != null && player.blockPosition().distSqr(waitAt) < 96 * 96;
				boolean room = embodied < cap && (LoadCaps.admitting(LoadCaps.mspt(server), LoadCaps.highMspt()) || embodied < Math.min(cap, 8));
				boolean far = embodiedNow && f.distanceToSqr(player) > 48 * 48;
				CompanionRules.Presence what = CompanionRules.presence(here, embodiedNow, waitAt != null, nearWait, here ? 0 : now - st.apartSince(),
					shed && embodied > cap, far, room, cfg());
				ServerLevel level = (ServerLevel) player.level();
				switch (what) {
					case GO_HOME -> release(server, s, r, SettlementEvents.Companion.Phase.LEFT, null);
					case SHED -> {
						// the server is struggling: one left behind waits as a record until it eases
						em.fold(level, f, true);
						embodied--;
					}
					case EMBODY_AT_WAIT -> {
						if (level.isPositionEntityTicking(waitAt) && embodyAt(level, s, r, waitAt) != null) {
							embodied++;
						}
					}
					case EMBODY_NEAR -> {
						if (level.isPositionEntityTicking(player.blockPosition()) && embody(level, s, r, player) != null) {
							embodied++;
						}
					}
					default -> {
					}
				}
			}
		}
	}

	private static @Nullable Folk embody(ServerLevel level, Settlement s, Resident r, ServerPlayer player) {
		return embodyAt(level, s, r, player.blockPosition());
	}

	private static @Nullable Folk embodyAt(ServerLevel level, Settlement s, Resident r, net.minecraft.core.BlockPos at) {
		Folk f = NpcModule.embodiment().spawnNear(level, s, r, at);
		CompanionState st = CompanionState.of(r);
		if (f != null && st != null) {
			CompanionGear.dress(f, st, level.registryAccess());
		}
		return f;
	}

	/** The server's companion cap at its current tick time (§9.2: 64, shrinking with MSPT; never below 8). */
	static int serverCap(MinecraftServer server) {
		return LoadCaps.cap(cfg().companions().maxPerServer(), 8, LoadCaps.mspt(server), LoadCaps.highMspt());
	}

	/** Companions embodied now (online players' companions with folk). */
	static int embodiedCount() {
		EmbodimentManager em = NpcModule.embodiment();
		int n = 0;
		for (List<Hire> hires : BY_TOWN.values()) {
			for (Hire h : hires) {
				Folk f = em.entityOf(h.resident());
				if (f != null && !f.isFolded()) {
					n++;
				}
			}
		}
		return n;
	}

	/** The number of online players' companions (for the sim stats). */
	public static int online() {
		int n = 0;
		for (List<Hire> hires : BY_TOWN.values()) {
			n += hires.size();
		}
		return n;
	}

	// ---------------------------------------------------------------- bookkeeping

	static void index(UUID player, long town, UUID resident) {
		List<Hire> l = BY_TOWN.computeIfAbsent(town, k -> new ArrayList<>(2));
		l.removeIf(h -> h.resident().equals(resident));
		l.add(new Hire(player, resident));
	}

	static void unindex(long town, UUID resident) {
		List<Hire> l = BY_TOWN.get(town);
		if (l != null) {
			l.removeIf(h -> h.resident().equals(resident));
			if (l.isEmpty()) {
				BY_TOWN.remove(town);
			}
		}
	}

	static void clear() {
		BY_TOWN.clear();
		ticks = 0;
	}

	static @Nullable Settlement settlement(MinecraftServer server, CompanionRoster.Entry e) {
		ServerLevel level = server.getLevel(e.dimension());
		return level == null ? null : SettlementSources.find(level, e.settlementId());
	}

	/** The home town's day (the sim's), for the wage book. */
	static long day(ServerLevel level, Settlement s) {
		HearthWorld w = HearthWorld.of(level);
		return w == null ? 0 : w.day();
	}

	private static void fire(ServerLevel level, Settlement s, UUID resident, UUID player, SettlementEvents.Companion.Phase phase) {
		HearthWorld w = HearthWorld.of(level);
		if (w != null) {
			try {
				SettlementEvents.COMPANION.invoker().onCompanion(w, s, resident, player, phase);
			} catch (RuntimeException ex) {
				Hearth.LOG.warn("a companion listener failed", ex);
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
			if (p != null) {
				PlaceSummary.send(p);
			}
		}
	}
}
