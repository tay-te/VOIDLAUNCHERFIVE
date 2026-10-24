package dev.voidmc.reel.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.voidmc.reel.Reel;
import dev.voidmc.reel.mixin.ClockInstanceAccessor;
import dev.voidmc.reel.shot.CameraState;
import dev.voidmc.reel.shot.Shot;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.clock.WorldClocks;

/**
 * Runs the camera. Everything happens on the client thread, once per pass of the game loop:
 *
 * <ul>
 * <li>{@link #stepClock} (from the frame timer) decides how many game ticks this pass runs and at what partial tick
 * it is drawn: in place of the wall clock, every rendered frame is a fixed step of shot time, however long it takes
 * to draw. Under the studio's game test harness the integrated server ticks in lockstep with the client, so mobs,
 * water and leaves move exactly as they would in real time.</li>
 * <li>{@link #camera} is where the camera is for this pass (the camera mixin puts it there).</li>
 * <li>{@link #onWorldRendered} captures the frame, once the world is drawn and before the GUI is.</li>
 * </ul>
 *
 * <p>A job runs PREPARE (stage set, world frozen, wait until the first frame's view is fully loaded), PREROLL (the
 * world runs a moment before frame one), RENDER (frame by frame, holding where the view is still loading and the
 * world allows it) and DRAIN (the last frames reach the disk), then encodes and reports. PLAY is a real-time
 * preview with no capture.
 */
public final class Director {
	public enum Phase { IDLE, PLAY, PREPARE, PREROLL, RENDER, DRAIN }

	private static final Director INSTANCE = new Director();
	/** Frames of a fully loaded view in a row before filming starts, and the least frames of setting up. */
	private static final int READY_STREAK = 20;
	private static final int PREPARE_MIN_FRAMES = 40;
	private static final long PREPARE_TIMEOUT_NANOS = 180_000_000_000L;
	private static final long FAR_PREPARE_NANOS = 60_000_000_000L;
	private static final long WARM_POINT_NANOS = 20_000_000_000L;
	/** How long one frame may wait for its view to finish loading before it is filmed anyway. */
	private static final int HOLD_LIMIT = 400;
	private static final int FAR_HOLD_LIMIT = 600;
	private static final int BACKLOG_LIMIT = 24;
	private static final double PACK_BASE = 100;

	/** Chunks the server sends beyond the render distance while filming (read on the server thread too). */
	public static final int SEND_AHEAD = 3;

	private volatile Phase phase = Phase.IDLE;
	private RenderJob job;
	private Shot playing;
	private long playStart;
	private final Stage stage = new Stage();
	private FrameSink sink;
	private Prefetch prefetch;
	private boolean prefetched;
	private List<CameraState> warm = List.of();
	private int warmIndex;
	private int warmStreak;
	private long warmStart;
	private long warmPointStart;
	private CameraState camera;
	private double shotTime;
	private float partial;
	private volatile long lodNanos;
	private long lastRealNanos;
	private volatile boolean midFrame;
	/** The clock shader packs animate by: continuous from setting up through the last frame, seconds. */
	private double packClock;
	private int prerollDone;

	private long ticksDone;
	private int cursor;
	private int sample;
	private boolean captureNow;
	private int captureIndex;
	private int readyStreak;
	private int prepareFrames;
	private int prerollLeft;
	private int holds;
	private int farHolds;
	/** Whether this frame's unfilmed first look has been taken. */
	private boolean probed;
	private long phaseStart;
	private long jobStart;
	private final Report report = new Report();
	private String readiness = "";
	private final List<Consumer<String>> listeners = new ArrayList<>();
	private String lastMessage = "";

	public static Director get() {
		return INSTANCE;
	}

	public Phase phase() {
		return this.phase;
	}

	public boolean busy() {
		return this.phase != Phase.IDLE;
	}

	public RenderJob job() {
		return this.job;
	}

	public void listen(Consumer<String> listener) {
		this.listeners.add(listener);
	}

	/** Where the camera is for this pass of the game loop, or null to leave it to the game. */
	public CameraState camera() {
		return this.camera;
	}

	/** Whether the frame timer is the director's: true from setting up a job until it is done. */
	public boolean clockActive() {
		return this.phase.ordinal() >= Phase.PREPARE.ordinal();
	}

	public float partialTick() {
		return this.partial;
	}

	/** Iris's frameTimeCounter while filming: the shot's own time (offset so packs never see zero or a jump). */
	public double packTime() {
		return this.packClock;
	}

	public double packFrameTime() {
		return this.phase == Phase.RENDER && this.job != null ? 1.0 / (this.job.shot().fps() * Math.max(1, this.job.samples())) : 0.05;
	}

	/** The size frames are drawn at, in place of the window's, while a job runs. */
	public int frameWidth(int window) {
		return this.clockActive() ? this.job.renderWidth() : window;
	}

	public int frameHeight(int window) {
		return this.clockActive() ? this.job.renderHeight() : window;
	}

	public void play(Minecraft mc, Shot shot) {
		if (this.busy()) {
			this.stop(mc);
		}
		this.playing = shot;
		this.playStart = System.nanoTime();
		this.phase = Phase.PLAY;
		this.camera = shot.camera(0);
		this.say("playing " + shot.name() + " (" + shot.rig().describe() + ")");
	}

	public void start(Minecraft mc, RenderJob job) {
		if (this.busy()) {
			this.stop(mc);
		}
		if (mc.level == null || mc.player == null) {
			this.say("no world loaded: open a world before filming");
			return;
		}
		this.job = job;
		this.playing = null;
		this.sink = new FrameSink(job.samples(), job.supersample());
		this.report.reset(job);
		this.camera = job.shot().camera(job.time(0, 0));
		this.shotTime = job.time(0, 0);
		this.partial = 0;
		this.ticksDone = 0;
		this.cursor = 0;
		this.sample = 0;
		this.readyStreak = 0;
		this.prepareFrames = 0;
		this.holds = 0;
		this.farHolds = 0;
		this.probed = false;
		this.captureNow = false;
		this.prerollDone = 0;
		this.packClock = PACK_BASE;
		this.lodNanos = this.lastRealNanos = System.nanoTime();
		this.midFrame = false;
		this.jobStart = this.phaseStart = System.nanoTime();
		LodHooks.begin(this, job);
		LodTrace.begin(job);
		this.prefetch = Prefetch.start(mc, job);
		this.prefetched = false;
		this.warm = job.movie() ? warmPoints(job) : List.of();
		this.warmIndex = 0;
		this.warmStreak = 0;
		this.warmStart = 0;
		// the world stands still while the first view loads; a movie whose world runs is unfrozen at preroll
		this.stage.enter(mc, job.shot(), this.camera, true);
		this.phase = Phase.PREPARE;
		this.say("filming " + job.describe() + " into " + job.out());
	}

	public void stop(Minecraft mc) {
		if (this.phase == Phase.IDLE) {
			return;
		}
		boolean filming = this.clockActive();
		this.phase = Phase.IDLE;
		this.camera = null;
		this.playing = null;
		if (filming) {
			LodHooks.end();
			this.stage.leave(mc);
			this.sink.close();
			this.say("stopped " + this.job.shot().name() + " at frame " + this.cursor + " of " + this.job.frames().length);
		}
	}

	/** Called at the start of every frame, before the camera is placed: real-time preview and the time of day. */
	public void beforeFrame(Minecraft mc) {
		if (this.phase == Phase.PLAY) {
			double t = (System.nanoTime() - this.playStart) / 1e9;
			if (t > this.playing.duration() + 0.5) {
				this.say("played " + this.playing.name());
				this.stop(mc);
				return;
			}
			this.shotTime = t;
			this.camera = this.playing.camera(t);
		}
		if (this.camera != null && Tuning.syncBody && mc.player != null && mc.player.isSpectator()) {
			// the body stands at the camera on this very frame (old position too, so nothing interpolates it elsewhere):
			// anything drawn relative to the player rather than the camera then lines up with it between ticks
			mc.player.snapTo(this.camera.pos().x(), this.camera.pos().y() - mc.player.getEyeHeight(), this.camera.pos().z(),
				(float) this.camera.yaw(), (float) this.camera.pitch());
		}
		Shot shot = this.phase == Phase.PLAY ? this.playing : this.job != null && this.clockActive() ? this.job.shot() : null;
		if (shot != null && mc.level != null) {
			Double day = shot.world().dayTimeAt(this.shotTime);
			if (day != null) {
				var instance = mc.level.clockManager().getInstance(mc.level.registryAccess().getOrThrow(WorldClocks.OVERWORLD));
				long whole = (long) Math.floor(day);
				((ClockInstanceAccessor) instance).reel$setTotalTicks(whole);
				((ClockInstanceAccessor) instance).reel$setPartialTick((float) (day - whole));
			}
		}
	}

	/**
	 * The frame timer: how many game ticks to run before this pass's frame. Sets the partial tick it is drawn at,
	 * where the camera is, and whether it is captured.
	 */
	public int stepClock(Minecraft mc) {
		this.captureNow = false;
		int ticks = switch (this.phase) {
			case PREPARE -> this.prepare(mc);
			case PREROLL -> this.preroll(mc);
			case RENDER -> this.render(mc);
			case DRAIN -> this.drain(mc);
			default -> 0;
		};
		// far terrain's clock: exactly one sample's worth of shot time on a filmed pass, the wall's time on any other
		// (setting up, waiting for land), so fades and rebuild gates advance with the film, and waits still finish
		long now = System.nanoTime();
		this.lodNanos += this.captureNow ? Math.round(this.packFrameTime() * 1e9) : now - this.lastRealNanos;
		this.lastRealNanos = now;
		this.midFrame = this.captureNow && this.sample > 0;
		return ticks;
	}

	/** The clock VOID LOD's frame timers read while filming (see LodClockMixin). */
	public long lodNanos() {
		return this.lodNanos;
	}

	/** Whether this pass renders a later sample of a frame already begun: far tiles must not change now. */
	public boolean midFrame() {
		return this.midFrame && this.phase == Phase.RENDER;
	}

	private int prepare(Minecraft mc) {
		this.prepareFrames++;
		if (!this.prefetched && this.prefetch.remaining() == 0) {
			this.prefetched = true;
			this.report.prefetched(this.prefetch.total(), System.nanoTime() - this.phaseStart);
		}
		if (this.prefetched && this.warmIndex < this.warm.size()) {
			this.warmUp();
			this.partial = 0;
			return 1;
		}
		// the chunks in view must be loaded; far terrain is waited for a minute, then its last details may come in on camera
		long waited = System.nanoTime() - this.phaseStart;
		boolean far = Readiness.farComplete() || waited > FAR_PREPARE_NANOS;
		this.readyStreak = far && Readiness.nearReady(mc) ? this.readyStreak + 1 : 0;
		this.readiness = Readiness.state(mc);
		boolean timedOut = waited > PREPARE_TIMEOUT_NANOS + this.prefetch.total() * 50_000_000L;
		if ((this.prefetched && this.readyStreak >= READY_STREAK && this.prepareFrames >= PREPARE_MIN_FRAMES) || timedOut) {
			if (timedOut) {
				this.say("the view never finished loading; filming anyway (" + this.readiness + ")");
				this.report.note("prepare timed out after " + this.prepareFrames + " frames: " + this.readiness);
			} else if (!Readiness.farComplete()) {
				this.report.note("far terrain still building when filming began: " + this.readiness);
			}
			this.report.prepared(System.nanoTime() - this.phaseStart, this.prepareFrames);
			Shot shot = this.job.shot();
			if (this.job.movie() && !shot.world().freeze()) {
				this.stage.unfreeze(mc);
				// two ticks at least, for the unfreeze to reach the client
				this.prerollLeft = Math.max(2, (int) Math.round(shot.render().preroll() * 20));
				this.enter(Phase.PREROLL);
			} else {
				this.enter(Phase.RENDER);
			}
		}
		this.partial = 0;
		return 1;   // the world is frozen: a tick only moves chunks along
	}

	/**
	 * VOID LOD builds a far tile first bare, then again once the world's trees are placed on it, in the background:
	 * a camera that moves on before that sees the trees arrive. So before filming, the camera visits points along
	 * its path and waits at each (a frozen world: only chunks and far tiles move) until the far terrain is complete,
	 * and is then put back at the first frame. The trees it placed stay placed.
	 */
	private void warmUp() {
		long now = System.nanoTime();
		if (this.warmIndex == 0 && this.warmStart == 0) {
			this.warmStart = now;
			this.warmPointStart = now;
		}
		this.camera = this.warm.get(this.warmIndex);
		this.warmStreak = Readiness.farComplete() ? this.warmStreak + 1 : 0;
		if (this.warmStreak >= 5 || now - this.warmPointStart > WARM_POINT_NANOS) {
			this.warmIndex++;
			this.warmStreak = 0;
			this.warmPointStart = now;
			if (this.warmIndex >= this.warm.size()) {
				this.report.note(String.format(Locale.ROOT, "far terrain warmed at %d points along the path in %.1f s", this.warm.size(), (now - this.warmStart) / 1e9));
				this.camera = this.job.shot().camera(this.job.time(0, 0));
				this.phaseStart = now;
				this.readyStreak = 0;
			}
		}
	}

	/** Points along the job's camera path, about every 24 blocks or 2 seconds, for the far terrain warm-up. */
	private static List<CameraState> warmPoints(RenderJob job) {
		List<CameraState> points = new ArrayList<>();
		if (!Readiness.lodPresent()) {
			return points;
		}
		CameraState last = null;
		double lastT = -10;
		for (int i = 0; i < job.frames().length; i++) {
			double t = job.time(i, 0);
			CameraState state = job.shot().camera(t);
			if (last == null || state.pos().distance(last.pos()) > 24 || t - lastT > 2 || i == job.frames().length - 1) {
				points.add(state);
				last = state;
				lastT = t;
			}
		}
		return points;
	}

	private int preroll(Minecraft mc) {
		this.prerollDone++;
		this.packClock = PACK_BASE + this.prerollDone / 20.0;
		if (--this.prerollLeft <= 0) {
			this.enter(Phase.RENDER);
		}
		this.partial = 0;
		return 1;
	}

	private int render(Minecraft mc) {
		if (this.sink.backlog() > BACKLOG_LIMIT) {
			this.report.backlogWait();
			return 0;
		}
		double t = this.job.time(this.cursor, this.sample);
		this.camera = this.jittered(this.job.shot().camera(t), this.sample);
		this.shotTime = t;
		this.packClock = PACK_BASE + this.prerollDone / 20.0 + t;
		boolean exact = this.job.movie() && !this.job.shot().world().freeze();

		// A frame's first look from its new position goes unfilmed: far terrain (VOID LOD) asks for the tiles the move
		// brought into reach there and draws coarser ones in their place until they are built, and the wait below must
		// see that before a filmed render does
		if (this.sample == 0 && !this.probed && Tuning.probe && Readiness.lodPresent()) {
			this.probed = true;
			return 0;
		}

		// Wait for the view to finish loading where waiting changes nothing on screen: sections still meshing can
		// always be waited for; chunks still to come from the server only when the world is frozen, since the
		// server needs ticks to send them.
		// far terrain (VOID LOD) is rebuilt around a moving camera all the time: wait for it briefly, never long
		boolean far = Readiness.farComplete();
		if (!far && this.farHolds < FAR_HOLD_LIMIT) {
			this.farHolds++;
			this.report.hold();
			return 0;
		}
		if (!far) {
			this.report.farBuilding(this.job.frames()[this.cursor]);
		}
		boolean ready = Readiness.nearReady(mc);
		if (!ready && this.holds < HOLD_LIMIT) {
			boolean server = Readiness.waitingOnServer(mc);
			if (!server || !exact) {
				this.holds++;
				this.report.hold();
				return server ? 1 : 0;
			}
		}
		if (!ready) {
			this.report.unready(this.job.frames()[this.cursor], Readiness.state(mc));
		}

		if (!this.job.movie()) {
			// stills: each its own moment, the world held where it is
			this.partial = 0;
			this.arm();
			return 0;
		}
		double worldTicks = t * 20;
		long want = (long) Math.floor(worldTicks + 1e-9);
		long need = want - this.ticksDone;
		if (need > 1) {
			// further behind than one tick (a low frame rate, or a long hold): catch up a tick a pass, unfilmed
			this.ticksDone++;
			this.partial = 0;
			this.report.catchUp();
			return 1;
		}
		need = Math.max(0, need);
		this.ticksDone += need;
		this.partial = (float) (worldTicks - want);
		this.arm();
		return (int) need;
	}

	/**
	 * Sample s of a frame seen through a sub-pixel offset of the view (the Halton 2,3 sequence, so any number of samples
	 * covers the pixel evenly): blending the samples then anti-aliases as well as blurring motion. The offset turns the
	 * camera rather than shifting the projection, so everything that draws with its own projection (shader packs,
	 * far terrain) is offset alike.
	 */
	private CameraState jittered(CameraState state, int sample) {
		int n = this.job.samples();
		if (!this.job.jitter() || n <= 1) {
			return state;
		}
		double dx = halton(sample + 1, 2) - 0.5;
		double dy = halton(sample + 1, 3) - 0.5;
		double degreesPerPixel = state.fov() / this.job.renderHeight();
		double yaw = dx * degreesPerPixel / Math.max(0.2, Math.cos(Math.toRadians(state.pitch())));
		return state.withAngles(state.yaw() + yaw, state.pitch() + dy * degreesPerPixel, state.roll());
	}

	private static double halton(int index, int base) {
		double f = 1;
		double r = 0;
		for (int i = index; i > 0; i /= base) {
			f /= base;
			r += f * (i % base);
		}
		return r;
	}

	private void arm() {
		this.captureNow = true;
		this.captureIndex = this.cursor;
	}

	private int drain(Minecraft mc) {
		if (this.sink.idle()) {
			this.finish(mc);
		}
		return 0;
	}

	private void enter(Phase next) {
		this.phase = next;
		this.phaseStart = System.nanoTime();
		this.readyStreak = 0;
	}

	/** After the world is drawn and before the GUI: capture this frame if it is one of the job's. */
	public void onWorldRendered(RenderTarget target) {
		if (!this.captureNow || this.phase != Phase.RENDER) {
			return;
		}
		this.captureNow = false;
		// The far view checked once more, after this render's own update: a frame's first sample is the render where
		// VOID LOD chooses (later samples hold its choice), and if it fell back to coarser tiles while finer ones are
		// rebuilt, the same instant is rendered again once they are (the world has not moved on: no ticks are owed)
		if (this.sample == 0 && Tuning.verify && Readiness.lodPresent() && !Readiness.farComplete() && this.farHolds < FAR_HOLD_LIMIT) {
			this.farHolds++;
			this.report.redo();
			return;
		}
		this.sink.capture(target, this.captureIndex, this.job.fileFor(this.captureIndex));
		LodTrace.sample(this.job.frames()[this.captureIndex], this.sample, this.midFrame, this.lodNanos);
		if (++this.sample >= this.job.samples()) {
			this.sample = 0;
			this.cursor++;
			this.holds = 0;
			this.farHolds = 0;
			this.probed = false;
			this.report.frame();
			if (this.cursor >= this.job.frames().length) {
				this.enter(Phase.DRAIN);
			}
		}
	}

	private void finish(Minecraft mc) {
		RenderJob done = this.job;
		this.phase = Phase.IDLE;
		this.camera = null;
		this.sink.close();
		LodHooks.end();
		LodTrace.end();
		this.stage.leave(mc);
		this.report.finished(System.nanoTime() - this.jobStart, this.sink.written(), this.sink.failure());
		try {
			Files.createDirectories(done.out());
			Files.writeString(done.out().resolve(done.movie() ? "render.json" : "stills.json"), this.report.json());
			if (done.movie()) {
				// the camera of every frame, for Blender overlays (tools/blender/reel_camera.py)
				Files.writeString(done.out().resolve("camera.json"), dev.voidmc.reel.tool.CameraExport.json(done.shot(), done.width(), done.height()));
			}
		} catch (IOException e) {
			Reel.LOG.error("[reel] could not write the report", e);
		}
		this.say(String.format(Locale.ROOT, "filmed %s: %d frames written in %.1f s%s", done.describe(), this.sink.written(),
			(System.nanoTime() - this.jobStart) / 1e9, this.sink.failure() != null ? " (" + this.sink.failure() + ")" : ""));
		if (done.movie() && done.encode()) {
			Encoder.encodeAsync(done, this::say);
		}
		if (!done.movie()) {
			Thread sheet = new Thread(() -> {
				try {
					var file = dev.voidmc.reel.tool.ContactSheet.make(done.out().resolve("stills"), done.shot().name(), done.out().resolve("stills.png"));
					this.say("contact sheet: " + file);
				} catch (IOException e) {
					this.say("contact sheet failed: " + e.getMessage());
				}
			}, "Reel contact sheet");
			sheet.setDaemon(true);
			sheet.start();
		}
	}

	public String statusJson() {
		StringBuilder b = new StringBuilder("{");
		b.append("\"phase\":\"").append(this.phase.name().toLowerCase(Locale.ROOT)).append('"');
		if (this.job != null) {
			b.append(",\"job\":\"").append(Report.escape(this.job.describe())).append('"');
			b.append(",\"frame\":").append(this.cursor).append(",\"frames\":").append(this.job.frames().length);
			b.append(",\"written\":").append(this.sink == null ? 0 : this.sink.written());
			if (this.phase == Phase.PREPARE && this.prefetch != null) {
				b.append(",\"prefetchLeft\":").append(this.prefetch.remaining()).append(",\"prefetch\":").append(this.prefetch.total());
				b.append(",\"warm\":\"").append(Math.min(this.warmIndex, this.warm.size())).append('/').append(this.warm.size()).append('"');
				b.append(",\"readyStreak\":").append(this.readyStreak).append(",\"loading\":\"").append(Report.escape(this.readiness)).append('"');
			}
			b.append(",\"out\":\"").append(Report.escape(this.job.out().toString())).append('"');
		}
		b.append(",\"t\":").append(String.format(Locale.ROOT, "%.3f", this.shotTime));
		b.append(",\"message\":\"").append(Report.escape(this.lastMessage)).append("\"}");
		return b.toString();
	}

	void say(String message) {
		this.lastMessage = message;
		Reel.LOG.info("[reel] {}", message);
		for (Consumer<String> listener : this.listeners) {
			listener.accept(message);
		}
	}
}
