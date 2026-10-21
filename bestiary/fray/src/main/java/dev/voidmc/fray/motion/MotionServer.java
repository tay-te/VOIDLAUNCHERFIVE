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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The server half of Fray Motion (ANIMATION.md §9): timelines loaded from the mods' jars, one {@link Running} per entity
 * in an action, ticked at the end of each level tick. On each ACTIVE tick the hit capsule is placed in the world (entity
 * position, facing at the start, scale) and swept from the previous tick's capsule with three sub-samples; living
 * entities in the swept bounds are tested capsule against box, hit once, then line of sight, then damage. Nothing
 * here touches a client class: the dedicated server runs it as is.
 */
public final class MotionServer {
	public static final byte LAYER_BASE = 0;
	public static final byte LAYER_ACTION = 1;

	private static Map<Identifier, Timeline> timelines = Map.of();
	private static List<Identifier> order = List.of();
	private static String setHash = "";

	private static final Map<Entity, Running> RUNNING = new IdentityHashMap<>();

	/** Every hit the server decided, for tests and the studio's log. */
	public static final List<HitRecord> HITS = new CopyOnWriteArrayList<>();
	/** Timeline events as they fire ("tick name arg"), for tests. */
	public static final List<String> EVENTS = new CopyOnWriteArrayList<>();
	/** Server cost probes: time in {@link #tick} outside sweeps, and in the sweeps of ACTIVE ticks. */
	public static long tickNanos;
	public static long runningTicks;
	public static long sweepNanos;
	public static long activeTicks;

	public record HitRecord(int attacker, int target, Identifier clip, int tick, String phase, long gameTime, String volume, float damage) {
	}

	public static final class Running {
		final LivingEntity entity;
		final Timeline timeline;
		final int clip;
		final long startTick;
		final float yaw;
		final float speed;
		final boolean byPlayer;
		final Set<Integer> hit = new HashSet<>();

		Running(LivingEntity entity, Timeline timeline, int clip, long startTick, float yaw, float speed, boolean byPlayer) {
			this.entity = entity;
			this.timeline = timeline;
			this.clip = clip;
			this.startTick = startTick;
			this.yaw = yaw;
			this.speed = speed;
			this.byPlayer = byPlayer;
		}
	}

	private MotionServer() {
	}

	public static void init() {
		load();
		ServerTickEvents.END_LEVEL_TICK.register(MotionServer::tick);
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
			boolean ok = id != null && !RUNNING.containsKey(player);
			long start = player.level().getGameTime();
			if (ok) {
				start(player, id, 1, true);
			}
			Fray.LOGGER.debug("action request {} seq {} from {}: {}", id, payload.seq(), player.getName().getString(), ok);
			ServerPlayNetworking.send(player, new MotionPayloads.ActionAck(payload.seq(), ok, start));
		});
	}

	/** Loads the baked timelines from every mod's {@code data/<ns>/fray/timeline}. */
	public static void load() {
		Map<Identifier, byte[]> files = ClipFiles.scan("data", "fray/timeline");
		Map<Identifier, Timeline> map = new LinkedHashMap<>();
		Map<Identifier, String> hashes = new LinkedHashMap<>();
		for (Map.Entry<Identifier, byte[]> e : files.entrySet()) {
			Timeline t = Timeline.parse(new String(e.getValue(), StandardCharsets.UTF_8));
			if (!t.clip.equals(e.getKey())) {
				throw new IllegalStateException("timeline file " + e.getKey() + " names clip " + t.clip);
			}
			map.put(e.getKey(), t);
			hashes.put(e.getKey(), t.clipHash);
		}
		timelines = map;
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

	public static @Nullable Running running(Entity e) {
		return RUNNING.get(e);
	}

	/**
	 * Starts a clip on an entity, server side: the action's timeline runs here and every tracking client is told the
	 * start tick. Returns false for an unknown clip.
	 */
	public static boolean play(LivingEntity entity, Identifier clip, float speed) {
		return start(entity, clip, speed, false) != null;
	}

	private static @Nullable Running start(LivingEntity entity, Identifier id, float speed, boolean byPlayer) {
		Timeline t = timelines.get(id);
		if (t == null || !(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		long now = level.getGameTime();
		// Players aim with their look; creatures attack along their body (they keep that facing through the swing).
		float yaw = entity instanceof ServerPlayer ? entity.getYRot() : entity.yBodyRot;
		Running r = new Running(entity, t, indexOf(id), now, yaw, speed, byPlayer);
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
		if (RUNNING.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		long now = level.getGameTime();
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
				}
			}
			runningTicks++;
			if (t.isActive(k)) {
				long s0 = System.nanoTime();
				sweep(level, r, k, now);
				sweepNanos += System.nanoTime() - s0;
				activeTicks++;
			}
			if (k >= t.ticks - 1) {
				it.remove();
			}
		}
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
		for (Map.Entry<String, Timeline.Capsule[]> v : t.volumes.entrySet()) {
			Timeline.Capsule cur = v.getValue()[k - t.activeStart];
			Timeline.Capsule prev = k == t.activeStart ? t.sweepFrom.get(v.getKey()) : v.getValue()[k - 1 - t.activeStart];
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
				float damage = t.damage > 0 ? t.damage : (float) (self.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? self.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1);
				DamageSource source = self instanceof ServerPlayer p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(self);
				boolean hurt = ((LivingEntity) e).hurtServer(level, source, damage);
				HitRecord rec = new HitRecord(self.getId(), e.getId(), t.clip, k, t.phase(k), now, v.getKey(), hurt ? damage : 0);
				HITS.add(rec);
				Fray.LOGGER.info(String.format(Locale.ROOT, "hit: %s -> %s by %s (%s) at tick %d (%s), %.1f damage%s", self.getName().getString(),
					e.getName().getString(), t.clip, v.getKey(), k, t.phase(k), damage, hurt ? "" : " (not hurt: immune)"));
			}
		}
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

	/** Where a hit volume of a running action is right now, in the world (for debug drawing and tests). */
	public static List<Vec3[]> volumesNow(Entity e) {
		Running r = RUNNING.get(e);
		List<Vec3[]> out = new ArrayList<>();
		if (r == null) {
			return out;
		}
		int k = (int) (e.level().getGameTime() - r.startTick);
		if (!r.timeline.isActive(k)) {
			return out;
		}
		double rad = Math.toRadians(r.yaw);
		for (Timeline.Capsule[] caps : r.timeline.volumes.values()) {
			Timeline.Capsule c = caps[k - r.timeline.activeStart];
			out.add(new Vec3[] {world(e.position(), Math.cos(rad), Math.sin(rad), r.entity.getScale(), c.ax(), c.ay(), c.az()),
				world(e.position(), Math.cos(rad), Math.sin(rad), r.entity.getScale(), c.bx(), c.by(), c.bz())});
		}
		return out;
	}
}
