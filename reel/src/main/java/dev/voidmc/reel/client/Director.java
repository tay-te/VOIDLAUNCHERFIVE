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
	/** How long one frame may wait for its view to finish loading before it is filmed anyway. */
	private static final int HOLD_LIMIT = 400;
	private static final int BACKLOG_LIMIT = 24;

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
	private CameraState camera;
	private double shotTime;
	private float partial;

	private long ticksDone;
	private int cursor;
	private int sample;
	private boolean captureNow;
	private int captureIndex;
	private int readyStreak;
	private int prepareFrames;
	private int prerollLeft;
	private int holds;
	private long phaseStart;
	private long jobStart;
	private final Report report = new Report();
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

	/** The size frames are drawn at, in place of the window's, while a job runs. */
	public int frameWidth(int window) {
		return this.clockActive() ? this.job.width() : window;
	}

	public int frameHeight(int window) {
		return this.clockActive() ? this.job.height() : window;
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
		this.sink = new FrameSink(job.samples());
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
		this.captureNow = false;
		this.jobStart = this.phaseStart = System.nanoTime();
		this.prefetch = Prefetch.start(mc, job);
		this.prefetched = false;
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
		return switch (this.phase) {
			case PREPARE -> this.prepare(mc);
			case PREROLL -> this.preroll(mc);
			case RENDER -> this.render(mc);
			case DRAIN -> this.drain(mc);
			default -> 0;
		};
	}

	private int prepare(Minecraft mc) {
		this.prepareFrames++;
		if (!this.prefetched && this.prefetch.remaining() == 0) {
			this.prefetched = true;
			this.report.prefetched(this.prefetch.total(), System.nanoTime() - this.phaseStart);
		}
		this.readyStreak = Readiness.ready(mc) ? this.readyStreak + 1 : 0;
		boolean timedOut = System.nanoTime() - this.phaseStart > PREPARE_TIMEOUT_NANOS + this.prefetch.total() * 50_000_000L;
		if ((this.prefetched && this.readyStreak >= READY_STREAK && this.prepareFrames >= PREPARE_MIN_FRAMES) || timedOut) {
			if (timedOut) {
				this.say("the view never finished loading; filming anyway");
				this.report.note("prepare timed out after " + this.prepareFrames + " frames");
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

	private int preroll(Minecraft mc) {
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
		this.camera = this.job.shot().camera(t);
		this.shotTime = t;
		boolean exact = this.job.movie() && !this.job.shot().world().freeze();

		// Wait for the view to finish loading where waiting changes nothing on screen: sections still meshing can
		// always be waited for; chunks still to come from the server only when the world is frozen, since the
		// server needs ticks to send them.
		boolean ready = Readiness.ready(mc);
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
		this.sink.capture(target, this.captureIndex, this.job.fileFor(this.captureIndex));
		if (++this.sample >= this.job.samples()) {
			this.sample = 0;
			this.cursor++;
			this.holds = 0;
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
		this.stage.leave(mc);
		this.report.finished(System.nanoTime() - this.jobStart, this.sink.written(), this.sink.failure());
		try {
			Files.createDirectories(done.out());
			Files.writeString(done.out().resolve(done.movie() ? "render.json" : "stills.json"), this.report.json());
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
