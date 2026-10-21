package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Crowds that react (docs/LIVING_WORLD_2.md §5.3): every {@code pass_ticks} (10) it looks at each player in an embodied
 * town — what they hold and wear, whether they are fighting, their title and the law's word on them — takes the folk
 * within 16 blocks (at most 24), and with {@link CrowdSelector} gives each at most one reaction, the most salient player
 * first. The reactions themselves are folk behaviours ({@link CrowdBehaviour}) that pick up the {@link Order}s left here.
 * Spectacles (a brawl, a trial, a body) draw onlookers the same way. One spatial query and some arithmetic per player per
 * pass; nothing is saved.
 */
public final class CrowdDirector {
	/** Folk looked at round one player. */
	static final double RADIUS = 16;
	static final int MAX_NEAR = 24;
	/** A folk killed lies this long as a body the crowd gathers round. */
	static final int BODY_TICKS = 20 * 60;

	private final Map<UUID, Order> orders = new HashMap<>();
	private final CrowdSelector.Cooldowns cooldowns = new CrowdSelector.Cooldowns();
	private final Map<UUID, Long> drawnSince = new HashMap<>();
	private final Map<UUID, Seen> seen = new HashMap<>();
	private final Map<Long, Long> outlawSeen = new HashMap<>();
	private final Map<Long, Households> households = new HashMap<>();
	private final List<Spectacle> spectacles = new ArrayList<>();
	private final List<Body> bodies = new ArrayList<>();
	/** Per player: what the last full look saw, so a player standing still among folk who have all reacted is looked at less. */
	private final Map<UUID, Quiet> quiet = new HashMap<>();
	private int tick;

	/** The last full look at a player: their stimuli, where they stood, when, and whether anyone new reacted. */
	private record Quiet(long stimuli, BlockPos at, long when, boolean fresh) {
	}

	/** At most this long between full looks at a quiet player (a refreshed reaction holds 60 ticks). */
	static final int QUIET_TICKS = 40;
	/** How long a player's title, outlawry and menace are kept between reads (the law's guards act on an outlaw at once). */
	static final int SEEN_TICKS = 100;

	/** A thing worth watching. {@code settlement} 0 for any. */
	record Spectacle(long settlement, ResourceKey<Level> dimension, BlockPos at, String kind, long until, int max) {
	}

	record Body(ResourceKey<Level> dimension, BlockPos at, long until) {
	}

	/** A player's title, outlawry and menace in a town, refreshed every {@link #SEEN_TICKS} (standing caches them too). */
	private record Seen(long settlement, Title title, boolean outlaw, float menace, long until) {
	}

	/** The home plots with a child in them, for which women are mothers in a crowd. */
	private record Households(Set<Integer> withChildren, long until) {
	}

	// ---------------------------------------------------------------- orders, for the behaviours

	/** The live order for a folk with this reaction, or null. */
	public @Nullable Order order(UUID folk, Reaction reaction, long now) {
		Order o = this.orders.get(folk);
		return o != null && o.reaction == reaction && o.live(now) ? o : null;
	}

	/** The live order for a folk, whatever its reaction. */
	public @Nullable Order order(UUID folk, long now) {
		Order o = this.orders.get(folk);
		return o != null && o.live(now) ? o : null;
	}

	public int liveOrders(long now) {
		int n = 0;
		for (Order o : this.orders.values()) {
			if (o.live(now)) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- spectacles and bodies

	public void spectacle(Settlement s, ServerLevel level, BlockPos at, String kind, int seconds, int maxOnlookers) {
		long now = level.getGameTime();
		this.spectacles.removeIf(x -> x.until < now || x.at.equals(at) && x.kind.equals(kind));
		if (this.spectacles.size() < 64) {
			this.spectacles.add(new Spectacle(s.id(), level.dimension(), at.immutable(), kind, now + seconds * 20L, Math.clamp(maxOnlookers, 0, 24)));
		}
	}

	/** A folk (or anyone) died here: a body for the crowd and the watch. */
	public void body(ServerLevel level, BlockPos at, @Nullable Settlement s) {
		long now = level.getGameTime();
		this.bodies.removeIf(b -> b.until < now);
		if (this.bodies.size() < 32) {
			this.bodies.add(new Body(level.dimension(), at.immutable(), now + BODY_TICKS));
		}
		if (s != null) {
			spectacle(s, level, at, "body", 45, 8);
		}
	}

	/** Whether an outlaw was in the town lately (its doors and shutters stay shut). */
	public boolean outlawAbout(long settlementId, long now) {
		Long t = this.outlawSeen.get(settlementId);
		return t != null && now - t < 20 * 30;
	}

	// ---------------------------------------------------------------- the pass

	public void tick(MinecraftServer server) {
		this.tick++;
		CrowdConfig.Crowds cfg = Crowds.config().crowds();
		if (!cfg.enabled() || this.tick % cfg.passTicks() != 0) {
			return;
		}
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return;
		}
		long t0 = System.nanoTime();
		try {
			pass(server, npc, cfg);
		} finally {
			long nanos = System.nanoTime() - t0;
			CrowdStats.DIRECTOR.add(nanos);
			CrowdStats.DIRECTOR_NANOS.add(nanos);
		}
	}

	private void pass(MinecraftServer server, NpcService npc, CrowdConfig.Crowds cfg) {
		long now = server.overworld().getGameTime();
		double mspt = LoadCaps.mspt(server);
		double high = LoadCaps.highMspt();
		boolean admitting = LoadCaps.admitting(mspt, high);
		int cap = LoadCaps.cap(cfg.maxReactingPerPlayer(), Math.min(2, cfg.maxReactingPerPlayer()), mspt, high);
		long cooldown = cfg.reactionCooldownSeconds() * 20L;
		if (this.tick % 200 == 0) {
			prune(now);
		}
		Set<UUID> online = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			HearthWorld world = HearthWorld.of(level);
			if (world == null) {
				continue;
			}
			Map<Settlement, List<ServerPlayer>> towns = new LinkedHashMap<>();
			for (ServerPlayer p : level.players()) {
				online.add(p.getUUID());
				if (p.isSpectator() || !p.isAlive()) {
					continue;
				}
				Settlement s = world.settlementAt(p.blockPosition());
				if (s != null && s.embodied()) {
					towns.computeIfAbsent(s, k -> new ArrayList<>(2)).add(p);
				}
			}
			int minute = NpcTime.minuteOfDay(level);
			for (Map.Entry<Settlement, List<ServerPlayer>> e : towns.entrySet()) {
				Settlement s = e.getKey();
				List<CrowdSelector.PlayerInput> inputs = new ArrayList<>(e.getValue().size());
				Map<UUID, PlayerFacts> facts = new HashMap<>();
				Map<UUID, FolkHandle> handles = new HashMap<>();
				Map<UUID, Role> roles = new HashMap<>();
				for (ServerPlayer p : e.getValue()) {
					CrowdStats.PLAYER_PASSES.increment();
					long f0 = System.nanoTime();
					PlayerFacts f = facts(p, s, level, now, minute);
					CrowdStats.FACTS_NANOS.add(System.nanoTime() - f0);
					facts.put(p.getUUID(), f);
					List<Stimuli.Sensed> sensed = Stimuli.of(f);
					if (Stimuli.has(sensed, Stimulus.OUTLAW)) {
						this.outlawSeen.put(s.id(), now);
					}
					if (sensed.isEmpty()) {
						this.quiet.remove(p.getUUID());
						continue;
					}
					// the same stimuli, the player where they were, nobody new reacting last time: the folk near have had their say
					// (cooldowns), so the next full look can wait a few passes (§5.4's budget on a busy server)
					long sig = 0;
					for (Stimuli.Sensed x : sensed) {
						sig |= 1L << x.stimulus().ordinal();
					}
					Quiet q = this.quiet.get(p.getUUID());
					if (q != null && q.stimuli == sig && !q.fresh && now - q.when < QUIET_TICKS && q.at.closerThan(p.blockPosition(), 2)) {
						CrowdStats.QUIET_SKIPS.increment();
						continue;
					}
					this.quiet.put(p.getUUID(), new Quiet(sig, p.blockPosition(), now, false));
					long n0 = System.nanoTime();
					List<CrowdSelector.Candidate> near = new ArrayList<>();
					for (FolkHandle h : npc.folkNear(level, p.position(), RADIUS)) {
						if (near.size() >= MAX_NEAR) {
							break;
						}
						if (h.settlement().id() != s.id() || !available(h)) {
							continue;
						}
						Role role = roles.computeIfAbsent(h.resident().id, k -> role(s, h.resident(), now));
						handles.put(h.resident().id, h);
						near.add(new CrowdSelector.Candidate(h.resident().id, role, h.entity().distanceTo(p)));
					}
					CrowdStats.NEAR_NANOS.add(System.nanoTime() - n0);
					if (!near.isEmpty()) {
						inputs.add(new CrowdSelector.PlayerInput(p.getUUID(), sensed, near));
					}
				}
				Map<UUID, CrowdSelector.Ongoing> ongoing = new HashMap<>();
				for (Order o : this.orders.values()) {
					if (o.live(now) || o.done && now <= o.until) {
						ongoing.put(o.folk, new CrowdSelector.Ongoing(o.player == null ? NOBODY : o.player, o.stimulus, o.reaction));
					}
				}
				List<CrowdSelector.Assignment> picks = inputs.isEmpty() ? List.of()
					: CrowdSelector.select(inputs, ongoing, this.cooldowns, now, cooldown, cap, admitting);
				Set<UUID> taken = new HashSet<>();
				for (CrowdSelector.Assignment a : picks) {
					taken.add(a.folk());
					if (!a.refresh()) {
						Quiet q = this.quiet.get(a.player());
						if (q != null && !q.fresh) {
							this.quiet.put(a.player(), new Quiet(q.stimuli, q.at, q.when, true));
						}
					}
					ServerPlayer p = server.getPlayerList().getPlayer(a.player());
					if (p == null) {
						continue;
					}
					issue(a, p, facts.get(a.player()), roles.getOrDefault(a.folk(), Role.FOLK), level, now);
				}
				if (admitting && !this.spectacles.isEmpty()) {
					long x0 = System.nanoTime();
					spectacles(s, level, npc, now, taken, roles);
					CrowdStats.SPECTACLE_NANOS.add(System.nanoTime() - x0);
				}
			}
		}
		this.drawnSince.keySet().retainAll(online);
		this.quiet.keySet().retainAll(online);
		this.seen.keySet().retainAll(online);
	}

	/** Stands in for "no player" in the selector's ongoing map (a spectacle's onlookers). */
	private static final UUID NOBODY = new UUID(0, 0);

	private void issue(CrowdSelector.Assignment a, ServerPlayer p, @Nullable PlayerFacts f, Role role, ServerLevel level, long now) {
		Order o = this.orders.get(a.folk());
		BlockPos origin = switch (a.stimulus()) {
			case FIGHT -> fightCentre(p);
			case BODY -> nearestBody(level, p.blockPosition());
			default -> null;
		};
		if (a.refresh() && o != null) {
			if (o.reaction == Reaction.GAWK || o.reaction == Reaction.SHUN) {
				// still going on: keep watching, keep the door shut
				o.until = Math.max(o.until, now + hold(o.reaction, true));
			}
			if (origin != null) {
				o.origin = origin;
			}
			o.approach |= approach(a, f, role);
			return;
		}
		Title title = f == null ? null : f.title();
		// a finished remark stays on the books its whole hold, so the next comes a while later (small talk the longest)
		long hold = a.stimulus() == Stimulus.FRIEND ? 20 * 45 : hold(a.reaction(), false);
		Order fresh = new Order(a.folk(), a.player(), a.stimulus(), a.reaction(), role, a.rank(), now, now + hold, title,
			a.stimulus().category());
		fresh.origin = origin;
		fresh.approach = approach(a, f, role);
		this.orders.put(a.folk(), fresh);
		CrowdStats.reacted(a.reaction());
	}

	private static boolean approach(CrowdSelector.Assignment a, @Nullable PlayerFacts f, Role role) {
		if (role != Role.GUARD || a.reaction() != Reaction.REMARK) {
			return false;
		}
		return a.stimulus() == Stimulus.BODY || a.stimulus() == Stimulus.BLADE && f != null && f.drawnTicks() >= Stimuli.DRAWN_TOO_LONG;
	}

	/** How long an order lasts unless refreshed (ticks). */
	static long hold(Reaction r, boolean refresh) {
		if (refresh) {
			return 60;
		}
		return switch (r) {
			case SCATTER -> 260;
			case GAWK -> 200;
			case HAIL -> 20 * 35;
			case SHUN -> 20 * 60;
			case MUTTER -> 120;
			case REMARK -> 20 * 15;
		};
	}

	private void spectacles(Settlement s, ServerLevel level, NpcService npc, long now, Set<UUID> taken, Map<UUID, Role> roles) {
		for (Iterator<Spectacle> it = this.spectacles.iterator(); it.hasNext(); ) {
			Spectacle x = it.next();
			if (x.until < now) {
				it.remove();
				continue;
			}
			if (x.dimension != level.dimension() || x.settlement != 0 && x.settlement != s.id()) {
				continue;
			}
			int count = 0;
			for (Order o : this.orders.values()) {
				if (o.player == null && o.live(now) && x.at.equals(o.origin)) {
					count++;
					if (o.reaction == Reaction.GAWK) {
						o.until = Math.max(o.until, Math.min(x.until, now + 60));
					}
				}
			}
			if (count >= x.max) {
				continue;
			}
			for (FolkHandle h : npc.folkNear(level, Vec3.atCenterOf(x.at), RADIUS + 4)) {
				if (count >= x.max) {
					break;
				}
				UUID id = h.resident().id;
				Order o = this.orders.get(id);
				if (taken.contains(id) || h.settlement().id() != s.id() || o != null && (o.live(now) || o.done && now <= o.until) || !available(h)
					|| !this.cooldowns.ready(id, Stimulus.FIGHT, now)) {
					continue;
				}
				Role role = roles.computeIfAbsent(id, k -> role(s, h.resident(), now));
				if (role == Role.GUARD && !x.kind.equals("body")) {
					// guards see to a brawl themselves, and watch a trial from their posts
					continue;
				}
				Reaction r = role == Role.GUARD ? Reaction.REMARK : Reaction.GAWK;
				Order fresh = new Order(id, null, x.kind.equals("body") ? Stimulus.BODY : Stimulus.FIGHT, r, role, count, now, Math.min(x.until, now + 200), null,
					"crowd_spectacle_" + x.kind);
				fresh.origin = x.at;
				fresh.approach = role == Role.GUARD;
				this.orders.put(id, fresh);
				this.cooldowns.mark(id, Stimulus.FIGHT, now + 20 * 10);
				CrowdStats.reacted(r);
				count++;
			}
		}
	}

	// ---------------------------------------------------------------- what the crowd sees

	private PlayerFacts facts(ServerPlayer p, Settlement s, ServerLevel level, long now, int minute) {
		boolean drawn = weaponDrawn(p);
		int drawnTicks = 0;
		if (drawn) {
			long since = this.drawnSince.computeIfAbsent(p.getUUID(), k -> now);
			drawnTicks = (int) Math.min(Integer.MAX_VALUE, now - since);
		} else {
			this.drawnSince.remove(p.getUUID());
		}
		Seen seen = this.seen.get(p.getUUID());
		if (seen == null || seen.settlement != s.id() || seen.until < now) {
			StandingService standing = HearthServices.standing();
			LawService law = HearthServices.law();
			Title title = standing == null ? Title.STRANGER : standing.title(s.id(), p.getUUID());
			boolean outlaw = law != null && law.outlaw(p.getUUID(), s.id());
			float menace = standing == null ? 0 : standing.menace(p);
			seen = new Seen(s.id(), title, outlaw, menace, now + SEEN_TICKS);
			this.seen.put(p.getUUID(), seen);
		}
		int combat = Integer.MAX_VALUE;
		LivingEntity by = p.getLastHurtByMob();
		if (by != null && by.isAlive() && by.distanceToSqr(p) < 24 * 24) {
			combat = Math.min(combat, p.tickCount - p.getLastHurtByMobTimestamp());
		}
		LivingEntity hit = p.getLastHurtMob();
		if (hit != null && hit.isAlive() && hit.distanceToSqr(p) < 24 * 24) {
			combat = Math.min(combat, p.tickCount - p.getLastHurtMobTimestamp());
		}
		boolean night = minute >= 22 * 60 || minute < 5 * 60;
		float health = p.getMaxHealth() <= 0 ? 1 : p.getHealth() / p.getMaxHealth();
		return new PlayerFacts(drawn, drawnTicks, p.getArmorValue(), Math.max(0, combat), seen.title, seen.outlaw, seen.menace, p.isShiftKeyDown(), night,
			health, nearestBody(level, p.blockPosition()) != null);
	}

	/** The middle of a fight: between the player and whom they fight. */
	private static BlockPos fightCentre(ServerPlayer p) {
		LivingEntity other = p.getLastHurtMob() != null && p.getLastHurtMob().isAlive() ? p.getLastHurtMob() : p.getLastHurtByMob();
		if (other == null || other.distanceToSqr(p) > 24 * 24) {
			return p.blockPosition();
		}
		return BlockPos.containing((p.getX() + other.getX()) / 2, Math.min(p.getY(), other.getY()), (p.getZ() + other.getZ()) / 2);
	}

	private @Nullable BlockPos nearestBody(ServerLevel level, BlockPos at) {
		long now = level.getGameTime();
		for (Body b : this.bodies) {
			if (b.until >= now && b.dimension == level.dimension() && b.at.closerThan(at, 6)) {
				return b.at;
			}
		}
		return null;
	}

	/** A weapon in the main hand, as the standing module reads it (Appearances.weaponDrawn). */
	public static boolean weaponDrawn(ServerPlayer player) {
		ItemStack hand = player.getMainHandItem();
		return !hand.isEmpty() && (hand.is(ItemTags.SWORDS) || hand.is(ItemTags.AXES) || hand.is(ItemTags.SPEARS) || hand.is(Items.MACE)
			|| hand.is(Items.TRIDENT) || hand.is(Items.BOW) || hand.is(Items.CROSSBOW));
	}

	/**
	 * Whether a folk is free to react: awake, not talking with a player, not fleeing or raising the alarm, and not taken by
	 * another module's behaviour that matters more (a witness, a guard on duty, an escort).
	 */
	static boolean available(FolkHandle h) {
		if (!h.awake() || !(h.entity() instanceof Folk f)) {
			return false;
		}
		FolkBrain brain = f.brain();
		if (brain == null || brain.routinePriority() >= 199) {
			return false;
		}
		NpcService.FolkBehaviour running = brain.behaviour();
		return running == null || running instanceof CrowdBehaviour || running.priority() < Reaction.HAIL.priority();
	}

	private Role role(Settlement s, Resident r, long now) {
		boolean mother = false;
		if (r.female && r.age == AgeStage.ADULT && r.home != Resident.NO_PLOT) {
			Households h = this.households.get(s.id());
			if (h == null || h.until < now) {
				Set<Integer> homes = new HashSet<>();
				for (Resident x : s.residents()) {
					if (x.age == AgeStage.CHILD && x.home != Resident.NO_PLOT) {
						homes.add(x.home);
					}
				}
				h = new Households(homes, now + 1200);
				this.households.put(s.id(), h);
			}
			mother = h.withChildren.contains(r.home);
		}
		return Role.of(r, mother);
	}

	private void prune(long now) {
		this.orders.values().removeIf(o -> now > o.until + 20);
		this.cooldowns.prune(now);
		this.outlawSeen.values().removeIf(t -> now - t > 20 * 60);
		this.households.values().removeIf(h -> h.until < now);
		this.bodies.removeIf(b -> b.until < now);
		this.spectacles.removeIf(x -> x.until < now);
	}

	public void clear() {
		this.orders.clear();
		this.cooldowns.clear();
		this.drawnSince.clear();
		this.seen.clear();
		this.outlawSeen.clear();
		this.households.clear();
		this.spectacles.clear();
		this.bodies.clear();
		this.quiet.clear();
	}

	/** For {@code /hearth crowds}: what the crowd sees about a player right now. */
	public List<Stimuli.Sensed> sense(ServerPlayer p) {
		HearthWorld world = HearthWorld.of(p.level());
		Settlement s = world == null ? null : world.settlementAt(p.blockPosition());
		if (s == null || !(p.level() instanceof ServerLevel level)) {
			return List.of();
		}
		return Stimuli.of(facts(p, s, level, level.getGameTime(), NpcTime.minuteOfDay(level)));
	}
}
