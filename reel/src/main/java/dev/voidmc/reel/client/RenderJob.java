package dev.voidmc.reel.client;

import dev.voidmc.reel.shot.Shot;
import java.nio.file.Path;
import java.util.Locale;

/**
 * What the director films: a whole shot as a movie (every frame, the world stepped in time with the camera), or a
 * handful of stills at chosen moments (the world held still) for a quick look before committing to a render.
 *
 * @param frames  the frame numbers to film, in order
 * @param samples renders blended into each frame (the shot's own unless overridden)
 * @param width   the size frames are rendered at, whatever the window's
 * @param out     the folder this job writes into: renders/&lt;shot&gt;
 */
public record RenderJob(Kind kind, Shot shot, int[] frames, int samples, int width, int height, Path out, boolean encode, boolean jitter, int supersample) {
	public enum Kind { MOVIE, STILLS }

	public static RenderJob movie(Shot shot, Path renders, Integer samples, Integer width, Integer height, boolean encode) {
		int[] frames = new int[shot.frames()];
		for (int i = 0; i < frames.length; i++) {
			frames[i] = i;
		}
		return new RenderJob(Kind.MOVIE, shot, frames, samples != null ? samples : shot.render().samples(), width != null ? width : shot.width(),
			height != null ? height : shot.height(), renders.resolve(shot.name()), encode, Tuning.jitter(shot), Tuning.supersample(shot));
	}

	/** count stills spread evenly from the first frame to the last, at half the shot's size unless given. */
	public static RenderJob stills(Shot shot, Path renders, int count, Integer width, Integer height) {
		int last = shot.frames() - 1;
		count = Math.max(1, Math.min(count, last + 1));
		int[] frames = new int[count];
		for (int i = 0; i < count; i++) {
			frames[i] = count == 1 ? 0 : (int) Math.round((double) i * last / (count - 1));
		}
		return new RenderJob(Kind.STILLS, shot, frames, 1, width != null ? width : shot.width() / 2, height != null ? height : shot.height() / 2,
			renders.resolve(shot.name()), false, false, Tuning.supersample(shot));
	}

	/** Exactly the given moments (seconds), each as one still. */
	public static RenderJob at(Shot shot, Path renders, double[] seconds, Integer width, Integer height) {
		int[] frames = new int[seconds.length];
		for (int i = 0; i < seconds.length; i++) {
			frames[i] = (int) Math.round(Math.clamp(seconds[i], 0, shot.duration()) * shot.fps());
		}
		java.util.Arrays.sort(frames);
		return new RenderJob(Kind.STILLS, shot, frames, 1, width != null ? width : shot.width() / 2, height != null ? height : shot.height() / 2,
			renders.resolve(shot.name()), false, false, Tuning.supersample(shot));
	}

	public boolean movie() {
		return this.kind == Kind.MOVIE;
	}

	/** The instant sample s of the i-th filmed frame shows, in seconds into the shot. */
	public double time(int index, int sample) {
		int frame = this.frames[index];
		if (this.samples <= 1) {
			return this.shot.timeOf(frame);
		}
		Shot blended = new Shot(this.shot.name(), this.shot.fps(), this.shot.duration(), this.width, this.height, this.shot.world(), this.shot.rig(),
			this.shot.shake(), this.shot.render().withSamples(this.samples));
		return blended.exposure(frame)[sample];
	}

	public Path fileFor(int index) {
		int frame = this.frames[index];
		if (this.movie()) {
			return this.out.resolve("frames").resolve(String.format(Locale.ROOT, "%06d.png", frame));
		}
		return this.out.resolve("stills").resolve(String.format(Locale.ROOT, "%s_f%05d_t%06.2f.png", this.shot.name(), frame, this.shot.timeOf(frame)));
	}

	public String describe() {
		return String.format(Locale.ROOT, "%s %s: %d frame%s at %dx%d, %d sample%s%s%s", this.kind.name().toLowerCase(Locale.ROOT), this.shot.name(),
			this.frames.length, this.frames.length == 1 ? "" : "s", this.width, this.height, this.samples, this.samples == 1 ? "" : "s",
			this.jitter && this.samples > 1 ? ", jittered" : "", this.supersample > 1 ? ", " + this.supersample + "x supersampled" : "");
	}

	/** The size each sample is rendered at: the frame's, times the supersampling. */
	public int renderWidth() {
		return this.width * this.supersample;
	}

	public int renderHeight() {
		return this.height * this.supersample;
	}
}
