package dev.voidmc.fray.client;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.MotionPayloads;
import dev.voidmc.fray.motion.PoseBuffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The client half of Fray Motion: one {@link Animator} per animated entity, fed by the server's {@code motion/play}
 * packets (and by the local player's predicted actions), sampled once per frame per entity (the render state carries
 * the pose to every later {@code setupAnim}, Iris's shadow pass included).
 */
public final class ClientMotion {
	/** The sampled pose rides in the entity's render state (Fabric's render state data), so every later setupAnim sees it. */
	public static final RenderStateDataKey<PoseBuffer> POSE = RenderStateDataKey.create(() -> "fray:pose");

	/** Off: nothing is sampled or applied (vanilla visuals); the perf test's baseline. */
	public static boolean enabled = true;

	private static final Int2ObjectMap<Animator> ANIMATORS = new Int2ObjectOpenHashMap<>();
	/** Per entity type: idle variants (BASE) and walk variants (LOCO), picked per entity. */
	private static final Map<EntityType<?>, Identifier[]> BASE_CLIPS = new HashMap<>();
	private static final Map<EntityType<?>, Identifier[]> LOCO_CLIPS = new HashMap<>();
	private static final Map<Integer, Double> PENDING = new HashMap<>();
	private static int seq;

	/** What arrived and when (client view), for tests: "entity clip start now". */
	public static final List<String> LOG = new CopyOnWriteArrayList<>();
	/** Last ack: server start tick minus the predicted local start (ticks). */
	public static double lastAckOffset = Double.NaN;
	/** Frames sampled and frames served from the per-frame cache (Iris's shadow pass), for the perf report. */
	public static long cacheHits;

	private ClientMotion() {
	}

	/** Fray's client networking for motion (play, stop, ack, the RTT probe's echo). */
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MotionPayloads.Clock.TYPE, (payload, context) -> context.responseSender()
			.sendPacket(new MotionPayloads.ClockAck(payload.serverNanos())));
	}

	/** Every entity of this type loops one of {@code clips} on its BASE layer (client-side, never sent), picked per entity. */
	public static void registerBase(EntityType<?> type, Identifier... clips) {
		BASE_CLIPS.put(type, clips);
	}

	/** Every entity of this type plays one of these walk loops on its LOCO layer, synced to its walk cycle. */
	public static void registerLocomotion(EntityType<?> type, Identifier... clips) {
		LOCO_CLIPS.put(type, clips);
	}

	public static double now(float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : mc.level.getGameTime() + partialTick;
	}

	public static @Nullable Animator get(int entityId) {
		return ANIMATORS.get(entityId);
	}

	public static Animator animator(int entityId) {
		Animator a = ANIMATORS.computeIfAbsent(entityId, id -> new Animator());
		Minecraft mc = Minecraft.getInstance();
		a.lastUsed = mc.level == null ? 0 : mc.level.getGameTime();
		return a;
	}

	public static void clear() {
		ANIMATORS.clear();
		PENDING.clear();
	}

	public static void remove(int entityId) {
		ANIMATORS.remove(entityId);
	}

	/** Every live animator drops its clips and picks them again (the studio's reload). */
	public static void restartAll() {
		ANIMATORS.clear();
	}

	public static void onPlay(MotionPayloads.Play p) {
		Clip clip = ClientClips.byIndex(p.clip());
		if (clip == null) {
			Fray.LOGGER.warn("motion/play for unknown clip index {}", p.clip());
			return;
		}
		double now = now(0);
		animator(p.entity()).play(clip.blend == Clip.Blend.ADDITIVE ? Animator.ADD : p.layer(), clip, p.startTick(), p.speed());
		LOG.add(String.format(Locale.ROOT, "play %d %s start %d now %.0f", p.entity(), clip.id, p.startTick(), now));
	}

	public static void onStop(MotionPayloads.Stop s) {
		Animator a = ANIMATORS.get(s.entity());
		if (a != null) {
			a.stop(s.layer(), now(0), s.blendTicks());
		}
	}

	public static void onAck(MotionPayloads.ActionAck ack) {
		Double local = PENDING.remove(ack.seq());
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || local == null) {
			return;
		}
		Animator a = animator(mc.player.getId());
		if (!ack.accepted()) {
			a.stop(Animator.ACTION, now(0), 3);
			LOG.add("refused " + ack.seq());
			return;
		}
		// Prediction: the clip started at the press, on this client's clock (which runs behind the server's by the
		// downlink latency). The server's start on its own clock is later by about the round trip; on this client's clock
		// the two agree within a tick or two, so the predicted start stays (no visible correction).
		lastAckOffset = ack.startTick() - local;
		LOG.add(String.format(Locale.ROOT, "ack %d server start %d local start %.0f offset %.1f ticks", ack.seq(), ack.startTick(), local, lastAckOffset));
	}

	/** The local player starts an action: played at once (prediction) and requested from the server. */
	public static boolean startLocal(Identifier id) {
		Minecraft mc = Minecraft.getInstance();
		Clip clip = ClientClips.get(id);
		if (mc.player == null || clip == null) {
			return false;
		}
		double start = now(0);
		animator(mc.player.getId()).play(clip.blend == Clip.Blend.ADDITIVE ? Animator.ADD : Animator.ACTION, clip, start, 1);
		int s = ++seq;
		PENDING.put(s, start);
		ClientPlayNetworking.send(new MotionPayloads.ActionRequest(ClientClips.indexOf(id), s, (long) start));
		return true;
	}

	/** Called at the end of {@code LivingEntityRenderer.extractRenderState}. */
	public static void sample(LivingEntity entity, LivingEntityRenderState state, float partialTick) {
		if (!enabled) {
			return;
		}
		long t0 = System.nanoTime();
		Animator a = ANIMATORS.get(entity.getId());
		if (a == null) {
			Identifier[] base = BASE_CLIPS.get(entity.getType());
			Identifier[] loco = LOCO_CLIPS.get(entity.getType());
			if (base == null && loco == null) {
				return;
			}
			a = animator(entity.getId());
			startLoops(a, entity, base, loco);
		}
		double now = now(partialTick);
		long frame = FrayStats.frameId;
		PoseBuffer buf;
		if (a.cachedFrame == frame && a.cachedNow == now) {
			// Iris's shadow pass (or any second extraction this frame): the pose is already sampled.
			buf = a.cached;
			cacheHits++;
		} else {
			buf = a.sample(now, state.walkAnimationPos, state.walkAnimationSpeed);
			a.cached = buf;
			a.cachedFrame = frame;
			a.cachedNow = now;
			FrayStats.samples++;
		}
		if (buf != null) {
			state.setData(POSE, buf);
		}
		a.lastUsed = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
		FrayStats.sampleNanos += System.nanoTime() - t0;
		FrayStats.extracts++;
	}

	private static void startLoops(Animator a, LivingEntity entity, Identifier @Nullable [] base, Identifier @Nullable [] loco) {
		// Each creature picks its own variant and starts at its own phase, so a crowd doesn't sway in step.
		int h = Math.floorMod(entity.getUUID().hashCode() * 31 + entity.getId(), 9973);
		if (base != null && base.length > 0) {
			Clip clip = ClientClips.get(base[h % base.length]);
			if (clip != null) {
				a.play(Animator.BASE, clip, -(h % 40), 1);
			}
		}
		if (loco != null && loco.length > 0) {
			Clip clip = ClientClips.get(loco[(h / 7) % loco.length]);
			if (clip != null) {
				a.play(Animator.LOCO, clip, 0, 1);
			}
		}
	}

	/** Client tick: the local player's root motion, and pruning animators of entities no longer drawn. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			if (!ANIMATORS.isEmpty()) {
				clear();
			}
			return;
		}
		LocalPlayer player = mc.player;
		if (player != null) {
			Animator a = ANIMATORS.get(player.getId());
			Clip c = a == null ? null : a.clip(Animator.ACTION);
			if (c != null && c.motion != null && Double.isNaN(a.frozenElapsed)) {
				double e = (now(0) - a.start(Animator.ACTION)) / 20.0;
				float from = c.motion[0], to = c.motion[1];
				if (e >= from && e < to && player.onGround()) {
					// Eased: fast at the start of the move, slowing into the recovery (a dash, not a slide).
					double span = to - from, x = (e - from) / span;
					double speed = c.motion[2] / (span * 20.0) * 2 * (1 - x) / 1.0;
					Vec3 dir = Vec3.directionFromRotation(0, player.getYRot() - c.motion[3]);
					Vec3 v = player.getDeltaMovement();
					player.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
				}
			}
		}
		long now = mc.level.getGameTime();
		ANIMATORS.int2ObjectEntrySet().removeIf(e -> {
			Animator an = e.getValue();
			boolean loopsOnly = an.clip(Animator.ACTION) == null && an.layers[Animator.ADD].cur.clip == null;
			return an.idle() || now - an.lastUsed > (loopsOnly ? 100 : 600);
		});
	}
}
