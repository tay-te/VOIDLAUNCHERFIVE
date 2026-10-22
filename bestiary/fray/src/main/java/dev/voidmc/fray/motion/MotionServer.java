package dev.voidmc.fray.motion;

import dev.voidmc.fray.Fray;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The server half of Fray Motion (ANIMATION.md §9): timelines loaded from the mods' jars, one {@link Running} per entity
 * in an action, ticked at the end of each level tick. On each ACTIVE tick every hit capsule is placed in the world
 * (entity position, facing at the start, scale) and swept from the previous tick's capsule with three sub-samples;
 * living entities in the swept bounds are tested capsule against box, hit once per strike, then line of sight, then
 * handed to {@link MotionHits}. Hits on players go through defender-favoured timing ({@link DefenderTiming}): what the
 * player's own dodge or parry covered on their screen decides, within a bounded hold. Nothing here touches a client
 * class: the dedicated server runs it as is.
 */
public final class MotionServer {
	public static final byte LAYER_BASE = 0;
	public static final byte LAYER_ACTION = 1;

	private static Map<Identifier, Timeline> timelines = Map.of();
	private static List<Identifier> order = List.of();
	private static String setHash = "";

	private static final Map<Entity, Running> RUNNING = new IdentityHashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	/** Hits found by this tick's sweeps, delivered after the sweep loop (a handler may start or stop actions). */
	private static final List<MotionHits.MotionHit> DUE = new ArrayList<>();

	/** Every hit the server decided, for tests and the studio's log. */
	public static final List<HitRecord> HITS = new CopyOnWriteArrayList<>();
	/** Timeline events as they fire ("tick name arg"), for tests. */
	public static final List<String> EVENTS = new CopyOnWriteArrayList<>();
	/** Server cost probes: time in {@link #tick} outside sweeps, and in the sweeps of ACTIVE ticks. */
	public static long tickNanos;
	public static long runningTicks;
	public static long sweepNanos;
	public static long activeTicks;

	/** Timeline events as they fire on the server (the combat side reacts to tells, windows and hits here). */
	public static final Event<EventListener> TIMELINE_EVENT = EventFactory.createArrayBacked(EventListener.class, ls -> (e, t, k, ev) -> {
		for (EventListener l : ls) {
			l.onEvent(e, t, k, ev);
		}
	});

	@FunctionalInterface
	public interface EventListener {
		void onEvent(LivingEntity entity, Timeline timeline, int tick, Timeline.Event event);
	}

	/** Whether a player's action request may start (the combat side checks stamina, state, item); default: yes. */
	@FunctionalInterface
	public interface ActionGate {
		boolean allow(ServerPlayer player, Identifier clip, @Nullable Running current);
	}

	private static volatile ActionGate gate = (p, c, r) -> true;

	public record HitRecord(int attacker, int target, Identifier clip, int tick, String phase, long gameTime, String volume, float damage, String window) {
	}

	public static final class Running {
		final LivingEntity entity;
		public final Timeline timeline;
		final int clip;
		public final long startTick;
		final float yaw;
		final float speed;
		final boolean byPlayer;
		/** The start on the defender's own clock (players' actions; clamped by {@link DefenderTiming}); else startTick. */
		public final long defenderStart;
		final Set<Integer> hit = new HashSet<>();
		int strike = -1;
		/** Per-action window overrides (Mettle's longer i-frames, the parry window): see {@link DefenderTiming#windowAt}. */
		public volatile @Nullable Map<String, int[]> windows;

		Running(LivingEntity entity, Timeline timeline, int clip, long startTick, float yaw, float speed, boolean byPlayer, long defenderStart) {
			this.entity = entity;
			this.timeline = timeline;
			this.clip = clip;
			this.startTick = startTick;
			this.yaw = yaw;
			this.speed = speed;
			this.byPlayer = byPlayer;
			this.defenderStart = defenderStart;
		}

		public int tick(long now) {
			return (int) (now - this.startTick);
		}

		public Identifier clip() {
			return this.timeline.clip;
		}
	}

	/** A hit on a player, held for defender-favoured timing until {@code deadline} (server tick). */
	private record Pending(MotionHits.MotionHit hit, ServerPlayer player, long deadline) {
	}

	private MotionServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(MotionPayloads.Clock.TYPE, MotionPayloads.Clock.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MotionPayloads.ClockAck.TYPE, MotionPayloads.ClockAck.STREAM_CODEC);
		load();
		ServerTickEvents.END_LEVEL_TICK.register(MotionServer::tick);
		// Running actions belong to one server: never carry them into the next (single player worlds, test servers).
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUNNING.clear();
			PENDING.clear();
			DUE.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DefenderTiming.forget(handler.player.getUUID()));
		EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
			Running r = RUNNING.get(entity);
			if (r != null && r.entity != player) {
				// A late joiner or someone coming into range: the running action with its real start tick.
				ServerPlayNetworking.send(player, new MotionPayloads.Play(entity.getId(), r.clip, r.startTick, r.speed, LAYER_ACTION));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(MotionPayloads.ActionRequest.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			Identifier id = idAt(payload.clip());
			Running r = id == null ? null : startPlayerAction(player, id, payload.clientTick());
			ServerPlayNetworking.send(player, new MotionPayloads.ActionAck(payload.seq(), r != null, r == null ? player.level().getGameTime() : r.startTick));
		});
		ServerPlayNetworking.registerGlobalReceiver(MotionPayloads.ClockAck.TYPE, (payload, context) -> {
			int ms = (int) ((System.nanoTime() - payload.serverNanos()) / 1_000_000L);
			DefenderTiming.recordRtt(context.player().getUUID(), ms);
		});
	}

	public static void setActionGate(ActionGate g) {
		gate = g;
	}

	/** Loads the baked timelines from every mod's {@code data/<ns>/fray/timeline}. */
	public static void load() {
		Map<Identifier, byte[]> files = ClipFiles.scan("data", "fray/timeline");
		Map<Identifier, Timeline> map = new LinkedHashMap<>();
		for (Map.Entry<Identifier, byte[]> e : files.entrySet()) {
			Timeline t = Timeline.parse(new String(e.getValue(), StandardCharsets.UTF_8));
			if (!t.clip.equals(e.getKey())) {
				throw new IllegalStateException("timeline file " + e.getKey() + " names clip " + t.clip);
			}
			map.put(e.getKey(), t);
		}
		replace(map);
	}

	/** Swaps the whole timeline set (load, and the Motion Studio's reload; the client swaps its clips the same moment). */
	public static synchronized void replace(Map<Identifier, Timeline> map) {
		Map<Identifier, String> hashes = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Timeline> e : map.entrySet()) {
			hashes.put(e.getKey(), ClipFiles.entryHash(e.getValue().clipHash, e.getValue().rigHash));
		}
		timelines = Map.copyOf(map);
		order = ClipFiles.order(map);
		setHash = ClipFiles.setHash(hashes);
		Fray.LOGGER.info("Fray Motion: {} server timelines, clip set {}", map.size(), FrayHandshake.shortHash(setHash));
	}

	public static String clipSetHash() {
		return setHash;
	}

	public static int clipCount() {
		return order.size();
	}

	public static int indexOf(Identifier id) {
		return order.indexOf(id);
	}

	public static @Nullable Identifier idAt(int index) {
		return index >= 0 && index < order.size() ? order.get(index) : null;
	}

	public static @Nullable Timeline timeline(Identifier id) {
		return timelines.get(id);
	}

	public static Map<Identifier, Timeline> timelines() {
		return timelines;
	}

	public static @Nullable Running running(Entity e) {
		return RUNNING.get(e);
	}

	/**
	 * Starts a clip on an entity, server side: the action's timeline runs here and every tracking client is told the
	 * start tick. Returns false for an unknown clip.
	 */
	public static boolean play(LivingEntity entity, Identifier clip, float speed) {
		return start(entity, clip, speed, false, Long.MIN_VALUE) != null;
	}

	/**
	 * A player's action (ANIM_EVENTS.md §4): runs from receipt like any action, and records the client's stamp, clamped by
	 * {@link DefenderTiming}, as the start of its defensive windows. Held hits the new window covers resolve at once.
	 */
	public static @Nullable Running startPlayerAction(ServerPlayer player, Identifier clip, long clientTick) {
		if (timelines.get(clip) == null || !gate.allow(player, clip, RUNNING.get(player))) {
			return null;
		}
		long receipt = player.level().getGameTime();
		long stamp = DefenderTiming.accept(player.getUUID(), clientTick, receipt);
		return startAccepted(player, clip, stamp, 1, null);
	}

	/**
	 * A player action the combat side already validated (stamina, state, item) and stamped with
	 * {@link DefenderTiming#accept}: runs it like {@link #startPlayerAction} without asking the gate again.
	 * {@code windows} overrides the timeline's defensive windows for this run; {@code speed} retimes the clip (a stand-in
	 * clip lined up with another action's ACTIVE phase).
	 */
	public static @Nullable Running startAccepted(ServerPlayer player, Identifier clip, long defenderStart, float speed, @Nullable Map<String, int[]> windows) {
		Running r = start(player, clip, speed, true, defenderStart);
		if (r != null) {
			r.windows = windows;
			recheck(player);
		}
		return r;
	}

	/**
	 * A clip that is only a picture (additive reactions: hit flinches, a blocked hit): sent to the entity's trackers and
	 * to the entity itself if it is a player and {@code toSelf} (false when that client predicted it), never replaces the
	 * running action and has no timeline effect.
	 */
	public static boolean overlay(LivingEntity entity, Identifier clip, boolean toSelf) {
		if (timelines.get(clip) == null || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		MotionPayloads.Play play = new MotionPayloads.Play(entity.getId(), indexOf(clip), level.getGameTime(), 1, LAYER_ACTION);
		for (ServerPlayer p : PlayerLookup.tracking(entity)) {
			if (p != entity) {
				ServerPlayNetworking.send(p, play);
			}
		}
		if (toSelf && entity instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, play);
		}
		return true;
	}

	private static @Nullable Running start(LivingEntity entity, Identifier id, float speed, boolean byPlayer, long defenderStart) {
		Timeline t = timelines.get(id);
		if (t == null || !(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		long now = level.getGameTime();
		// Players aim with their look; creatures attack along their body (they keep that facing through the swing).
		float yaw = entity instanceof ServerPlayer ? entity.getYRot() : entity.yBodyRot;
		Running r = new Running(entity, t, indexOf(id), now, yaw, speed, byPlayer, defenderStart == Long.MIN_VALUE ? now : defenderStart);
		RUNNING.put(entity, r);
		MotionPayloads.Play play = new MotionPayloads.Play(entity.getId(), r.clip, now, speed, LAYER_ACTION);
		for (ServerPlayer p : PlayerLookup.tracking(entity)) {
			if (p != entity) {
				ServerPlayNetworking.send(p, play);
			}
		}
		if (entity instanceof ServerPlayer self && !byPlayer) {
			ServerPlayNetworking.send(self, play);
		}
		EVENTS.add(now + " start " + id + " on " + entity.getId());
		return r;
	}

	public static void stop(LivingEntity entity, int blendTicks) {
		if (RUNNING.remove(entity) != null) {
			MotionPayloads.Stop stop = new MotionPayloads.Stop(entity.getId(), LAYER_ACTION, (byte) blendTicks);
			for (ServerPlayer p : PlayerLookup.tracking(entity)) {
				ServerPlayNetworking.send(p, stop);
			}
			if (entity instanceof ServerPlayer self) {
				ServerPlayNetworking.send(self, stop);
			}
		}
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 20 == 0) {
			// The RTT probe for defender-favoured timing (one tiny packet a second per player).
			MotionPayloads.Clock probe = new MotionPayloads.Clock(System.nanoTime());
			for (ServerPlayer p : level.players()) {
				if (ServerPlayNetworking.canSend(p, MotionPayloads.Clock.TYPE)) {
					ServerPlayNetworking.send(p, probe);
				}
			}
		}
		if (RUNNING.isEmpty() && PENDING.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		long sweep = 0;
		for (Iterator<Running> it = RUNNING.values().iterator(); it.hasNext(); ) {
			Running r = it.next();
			if (r.entity.level() != level) {
				continue;
			}
			if (r.entity.isRemoved() || !r.entity.isAlive()) {
				it.remove();
				continue;
			}
			int k = (int) (now - r.startTick);
			Timeline t = r.timeline;
			for (Timeline.Event e : t.events) {
				if (e.tick() == k) {
					EVENTS.add(now + " " + t.clip + " tick " + k + " " + e.name() + " " + e.arg());
					TIMELINE_EVENT.invoker().onEvent(r.entity, t, k, e);
				}
			}
			runningTicks++;
			int strike = t.strikeAt(k);
			if (strike >= 0) {
				if (strike != r.strike) {
					// Hit-once is per strike: the second claw of a double claw may hit the same target again.
					r.strike = strike;
					r.hit.clear();
				}
				long s0 = System.nanoTime();
				sweep(level, r, k, now);
				sweep += System.nanoTime() - s0;
				activeTicks++;
			}
			if (k >= t.ticks - 1) {
				it.remove();
			}
		}
		if (!DUE.isEmpty()) {
			List<MotionHits.MotionHit> due = new ArrayList<>(DUE);
			DUE.clear();
			for (MotionHits.MotionHit hit : due) {
				if (hit.target() instanceof ServerPlayer player) {
					defend(hit, player, hit.attacker() instanceof ServerPlayer);
				} else {
					deliver(hit, null, DefenderTiming.Window.NONE, false);
				}
			}
		}
		// Held hits whose grace ran out resolve with whatever covers them now (usually nothing).
		List<Pending> expired = new ArrayList<>();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.player.level() == level && now >= p.deadline) {
				it.remove();
				expired.add(p);
			}
		}
		for (Pending p : expired) {
			deliver(p.hit, p.player, windowFor(p.player, p.hit.serverTick()), false);
		}
		sweepNanos += sweep;
		tickNanos += System.nanoTime() - t0;
	}

	public static void resetCost() {
		tickNanos = runningTicks = sweepNanos = activeTicks = 0;
	}

	public static String costLine() {
		return String.format(Locale.ROOT, "%d running-action ticks at %.2f us each outside sweeps, %d ACTIVE-tick sweeps at %.2f us each", runningTicks,
			runningTicks == 0 ? 0 : (tickNanos - sweepNanos) / 1000.0 / runningTicks, activeTicks, activeTicks == 0 ? 0 : sweepNanos / 1000.0 / activeTicks);
	}

	private static void sweep(ServerLevel level, Running r, int k, long now) {
		Timeline t = r.timeline;
		LivingEntity self = r.entity;
		float scale = self.getScale();
		Vec3 origin = self.position();
		double rad = Math.toRadians(r.yaw);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		for (Map.Entry<String, Timeline.Track> v : t.volumes.entrySet()) {
			Timeline.Capsule cur = v.getValue().at(k);
			if (cur == null) {
				continue;
			}
			Timeline.Capsule prev = v.getValue().at(k - 1);
			if (prev == null) {
				prev = cur;
			}
			// Three sub-samples from the previous tick's capsule to this one: 20 ticks a second is coarse for a claw.
			Vec3[] a = new Vec3[3];
			Vec3[] b = new Vec3[3];
			AABB bounds = null;
			for (int s = 0; s < 3; s++) {
				float f = (s + 1) / 3F;
				a[s] = world(origin, cos, sin, scale, lerp(prev.ax(), cur.ax(), f), lerp(prev.ay(), cur.ay(), f), lerp(prev.az(), cur.az(), f));
				b[s] = world(origin, cos, sin, scale, lerp(prev.bx(), cur.bx(), f), lerp(prev.by(), cur.by(), f), lerp(prev.bz(), cur.bz(), f));
				AABB box = new AABB(a[s], b[s]);
				bounds = bounds == null ? box : bounds.minmax(box);
			}
			float radius = cur.r() * scale;
			// The prototype's allies rule: a creature never hits its own kind or its team (Bestiary's AI decides more later).
			List<Entity> near = level.getEntities(self, bounds.inflate(radius), e -> e instanceof LivingEntity le && le.isAlive() && !r.hit.contains(e.getId())
				&& e.getType() != self.getType() && !self.isAlliedTo(e));
			for (Entity e : near) {
				AABB box = e.getBoundingBox();
				boolean touched = false;
				for (int s = 0; s < 3 && !touched; s++) {
					touched = segmentBoxDistance(a[s], b[s], box) <= radius;
				}
				if (!touched || !self.hasLineOfSight(e)) {
					continue;
				}
				r.hit.add(e.getId());
				DUE.add(new MotionHits.MotionHit(self, (LivingEntity) e, t.clip, v.getKey(), k, now, t.tellColour, t.damage));
			}
		}
	}

	/** A hit on a player: resolved now if a window covers it or there's no grace, else held for a late request. */
	private static void defend(MotionHits.MotionHit hit, ServerPlayer player, boolean pvp) {
		DefenderTiming.Window w = windowFor(player, hit.serverTick());
		int grace = DefenderTiming.graceTicks(player.getUUID(), pvp);
		if (w != DefenderTiming.Window.NONE || grace <= 0) {
			deliver(hit, player, w, false);
			return;
		}
		PENDING.add(new Pending(hit, player, hit.serverTick() + grace));
	}

	/** A player's new action may cover hits held for them. */
	private static void recheck(ServerPlayer player) {
		List<Pending> covered = new ArrayList<>();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.player == player && windowFor(player, p.hit.serverTick()) != DefenderTiming.Window.NONE) {
				it.remove();
				covered.add(p);
			}
		}
		for (Pending p : covered) {
			DefenderTiming.clock(player.getUUID()).saves++;
			deliver(p.hit, player, windowFor(player, p.hit.serverTick()), true);
		}
	}

	/** Hits held for defender-favoured timing right now (tests). */
	public static int pendingHits() {
		return PENDING.size();
	}

	/** What the player's running action covered at {@code serverTick} on the player's own clock. */
	public static DefenderTiming.Window windowFor(ServerPlayer player, long serverTick) {
		Running r = RUNNING.get(player);
		if (r == null) {
			return DefenderTiming.Window.NONE;
		}
		return DefenderTiming.windowAt(r.timeline, r.defenderStart, serverTick, r.windows);
	}

	private static void deliver(MotionHits.MotionHit hit, @Nullable ServerPlayer player, DefenderTiming.Window w, boolean late) {
		if (hit.attacker().isRemoved() || !hit.target().isAlive()) {
			return;
		}
		Timeline t = timelines.get(hit.clip());
		HITS.add(new HitRecord(hit.attacker().getId(), hit.target().getId(), hit.clip(), hit.clipTick(), t == null ? "?" : t.phase(hit.clipTick()),
			hit.serverTick(), hit.volume(), hit.damage(), w.name().toLowerCase(Locale.ROOT) + (late ? " (late request)" : "")));
		MotionHits.resolve(hit, w);
	}

	private static float lerp(float a, float b, float f) {
		return a + (b - a) * f;
	}

	/** Entity-local blocks (facing +Z at yaw 0, +X the entity's left) to world coordinates. */
	public static Vec3 world(Vec3 origin, double cos, double sin, float scale, float x, float y, float z) {
		double wx = x * cos - z * sin;
		double wz = x * sin + z * cos;
		return new Vec3(origin.x + wx * scale, origin.y + y * scale, origin.z + wz * scale);
	}

	/** The shortest distance from a segment to a box (sampled; capsules are short). */
	public static double segmentBoxDistance(Vec3 a, Vec3 b, AABB box) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i <= 8; i++) {
			double f = i / 8.0;
			double x = a.x + (b.x - a.x) * f, y = a.y + (b.y - a.y) * f, z = a.z + (b.z - a.z) * f;
			double dx = Math.max(Math.max(box.minX - x, 0), x - box.maxX);
			double dy = Math.max(Math.max(box.minY - y, 0), y - box.maxY);
			double dz = Math.max(Math.max(box.minZ - z, 0), z - box.maxZ);
			best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		return best;
	}

	/** A capsule placed in the world: name, ends and radius (blocks). */
	public record Placed(String name, Vec3 a, Vec3 b, double radius, boolean hurt) {
	}

	/** Where the hit and hurt volumes of a running action are at tick {@code k} of it (studio overlay, tests). */
	public static List<Placed> volumesAt(Entity e, int k) {
		Running r = RUNNING.get(e);
		List<Placed> out = new ArrayList<>();
		if (r == null) {
			return out;
		}
		double rad = Math.toRadians(r.yaw);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		float scale = r.entity.getScale();
		for (boolean hurt : new boolean[] {false, true}) {
			for (Map.Entry<String, Timeline.Track> v : (hurt ? r.timeline.hurtVolumes : r.timeline.volumes).entrySet()) {
				Timeline.Capsule c = v.getValue().at(k);
				if (c == null || (!hurt && !r.timeline.isActive(k))) {
					continue;
				}
				out.add(new Placed(v.getKey(), world(e.position(), cos, sin, scale, c.ax(), c.ay(), c.az()), world(e.position(), cos, sin, scale, c.bx(), c.by(),
					c.bz()), c.r() * scale, hurt));
			}
		}
		return out;
	}

	/** Where the hit volumes of a running action are right now, in the world (for debug drawing and tests). */
	public static List<Vec3[]> volumesNow(Entity e) {
		Running r = RUNNING.get(e);
		List<Vec3[]> out = new ArrayList<>();
		if (r == null) {
			return out;
		}
		for (Placed p : volumesAt(e, (int) (e.level().getGameTime() - r.startTick))) {
			if (!p.hurt()) {
				out.add(new Vec3[] {p.a(), p.b()});
			}
		}
		return out;
	}

	/** Hurt volumes (weak points) of an entity's running action now, for the combat side's weak-point test. */
	public static List<Placed> hurtVolumesNow(Entity e) {
		Running r = RUNNING.get(e);
		if (r == null) {
			return List.of();
		}
		List<Placed> out = new ArrayList<>();
		for (Placed p : volumesAt(e, (int) (e.level().getGameTime() - r.startTick))) {
			if (p.hurt()) {
				out.add(p);
			}
		}
		return out;
	}
}
