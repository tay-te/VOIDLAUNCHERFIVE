package dev.voidmc.reel.shot;

/**
 * One shot: a camera move, the state of the world while it runs, and how it is filmed. Written as a JSON file in
 * shots/ (see docs/SHOTS.md) and read by {@link ShotParser}; everything here is plain data and maths, so a shot
 * can be checked, plotted and timed without the game.
 */
public record Shot(String name, int fps, double duration, int width, int height, World world, Rig rig, Shake shake, Render render) {
	/**
	 * The world while the shot runs. time is the day time at the first frame in ticks (null leaves the clock
	 * alone), timeRate how fast the day moves (1 as in the game, 0 holds the light, 60 a minute of daylight per
	 * second), weather clear, rain or thunder (null leaves it), and freeze stops the world's ticking (mobs,
	 * water, leaves) so only the camera moves.
	 */
	public record World(Double time, double timeRate, String weather, boolean freeze) {
		public static final World UNCHANGED = new World(null, 1, null, false);

		/** The day time t seconds into the shot, or null when the shot leaves the clock alone. */
		public Double dayTimeAt(double t) {
			return this.time == null ? null : this.time + t * 20 * this.timeRate;
		}
	}

	/**
	 * How frames are made. shutter is the shutter angle in degrees (180 exposes each frame for half its time,
	 * the film look; 360 smears everything), samples how many renders are blended into each frame for motion
	 * blur (1 for none), preroll how many seconds the world runs before the first frame so water, leaves and
	 * mobs are already moving. jitter gives each sample its own sub-pixel offset of the view, so blending the samples
	 * also anti-aliases (distant detail stops crawling); supersample renders each sample that many times larger in
	 * each direction and scales it down.
	 */
	public record Render(double shutter, int samples, double preroll, boolean jitter, int supersample) {
		public static final Render DEFAULT = new Render(180, 1, 0, true, 1);

		public Render withSamples(int samples) {
			return new Render(this.shutter, samples, this.preroll, this.jitter, this.supersample);
		}
	}

	public int frames() {
		return Math.max(1, (int) Math.round(this.duration * this.fps));
	}

	public double timeOf(int frame) {
		return (double) frame / this.fps;
	}

	/**
	 * The instants blended into one frame: samples spread over the exposure window, centred on the frame's own
	 * time so a key lands on the frame it names, and kept inside the shot.
	 */
	public double[] exposure(int frame) {
		int n = Math.max(1, this.render.samples());
		double centre = this.timeOf(frame);
		if (n == 1) {
			return new double[] {centre};
		}
		double open = this.render.shutter() / 360.0 / this.fps;
		double[] times = new double[n];
		for (int i = 0; i < n; i++) {
			times[i] = Math.clamp(centre + open * ((i + 0.5) / n - 0.5), 0, this.duration);
		}
		return times;
	}

	/** The camera t seconds into the shot, shake and all. */
	public CameraState camera(double t) {
		CameraState state = this.rig.at(Math.clamp(t, 0, this.duration));
		return this.shake == null ? state : this.shake.apply(state, t);
	}

	public double aspect() {
		return (double) this.width / this.height;
	}
}
