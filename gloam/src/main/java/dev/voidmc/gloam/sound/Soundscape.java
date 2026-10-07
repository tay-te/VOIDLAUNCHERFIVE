package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Noise;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The soundscape: layered ambient beds for the land, the weather and the hour, muffled indoors.
 *
 * <p>Each tick it asks {@link Mix} how loud each {@link Bed} should be, from the air at the camera
 * ({@link Atmosphere#here()}) and the land around it ({@link Land}, read every {@link #SCAN_TICKS} ticks), times the
 * {@code sound} setting, and hands that to the bed's {@link BedSound}, which eases toward it. A bed that should be
 * heard and is not playing is started (silent, then fading in); one that has faded out stops itself. Under a storm,
 * distant thunder rolls now and then ({@link ThunderRoll}). Everything is on the ambient category, so Minecraft's
 * ambient slider scales it, and all of it fades out when Gloam is switched off, under water, and where there is no
 * sky (the Nether, the End).
 *
 * <p>Vanilla's own ambience (cave moods, the underwater loop, the rain's patter near the camera) carries on beneath;
 * Expanse's creatures keep their own calls. Nothing here is registered: the sounds are client-side clips named in
 * {@code assets/gloam/sounds.json}, so a server needs nothing of this.
 */
public final class Soundscape {
	/** How often the land around the camera is read, in ticks. */
	static final int SCAN_TICKS = 30;

	private static final Bed[] BEDS = Bed.values();
	private static final BedSound[] PLAYING = new BedSound[BEDS.length];
	private static final float[] VOLUMES = new float[BEDS.length];
	private static final Land LAND = new Land();
	private static final RandomSource RANDOM = RandomSource.create();

	private static ClientLevel level;
	private static int ticks, rolls, nextRoll;

	private Soundscape() {
	}

	/** Common start: nothing to register (the beds are client-side clips, played by name). */
	public static void register() {
	}

	/** Client start. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Soundscape::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.isPaused()) {
			return;
		}
		SoundManager sounds = mc.getSoundManager();
		if (mc.level == null || mc.player == null) {
			if (level != null) {
				stopAll(sounds);
				level = null;
			}
			return;
		}
		if (mc.level != level) {
			stopAll(sounds);
			level = mc.level;
			LAND.reset();
			ticks = 0;
			rolls = 0;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.isInitialized() ? camera.position() : mc.player.getEyePosition();
		GloamSettings settings = GloamSettings.get();
		boolean on = settings.enabled && settings.sound > 0 && level.dimensionType().hasSkyLight()
			&& mc.options.getFinalSoundSourceVolume(SoundSource.AMBIENT) > 0
			&& !level.getFluidState(BlockPos.containing(eye)).is(FluidTags.WATER);
		if (on && ticks++ % SCAN_TICKS == 0) {
			LAND.read(level, eye);
		}
		AirSample air = Atmosphere.get().here();
		Surroundings land = LAND.now();
		if (on) {
			Mix.volumes(air, land, VOLUMES);
		} else {
			Arrays.fill(VOLUMES, 0);
		}
		float scale = (float) settings.sound;
		float windPitch = Mix.windPitch(air);
		for (Bed bed : BEDS) {
			int i = bed.ordinal();
			float volume = VOLUMES[i] * scale;
			BedSound sound = PLAYING[i];
			if (sound != null && (sound.isStopped() || !sounds.isActive(sound))) {
				// it faded out and stopped, or the sound engine let it go (a reload, /stopsound)
				if (!sound.isStopped()) {
					sound.stop();
				}
				sound = PLAYING[i] = null;
			}
			if (sound == null && volume > 0.002F && ticks % 10 == 0) {
				sound = new BedSound(bed, eye);
				sounds.play(sound);
				PLAYING[i] = sound;
			}
			if (sound != null) {
				float pitch = bed == Bed.WIND_OPEN || bed == Bed.WIND_LEAVES || bed == Bed.WIND_HIGH ? windPitch : 1;
				float ox = 0, oz = 0;
				switch (bed) {
					case STREAM -> {
						ox = land.runningX();
						oz = land.runningZ();
					}
					case WAVES -> {
						ox = land.shoreX();
						oz = land.shoreZ();
					}
					case FROGS -> {
						ox = land.stillX();
						oz = land.stillZ();
					}
					default -> {
					}
				}
				sound.aim(volume, pitch, eye, ox, oz);
			}
		}
		if (on && settings.thunder) {
			thunder(sounds, air, land, eye, scale);
		}
	}

	/** Under a storm, a distant roll of thunder every half a minute or so, more often the fiercer the storm. */
	private static void thunder(SoundManager sounds, AirSample air, Surroundings land, Vec3 eye, float scale) {
		float storm = Noise.smoothstep(0.3F, 0.9F, air.storm());
		if (storm <= 0) {
			return;
		}
		if (nextRoll > 0) {
			nextRoll--;
			return;
		}
		if (RANDOM.nextFloat() < storm * 0.01F) {
			float muffled = (1 - 0.5F * air.indoors()) * (1 - 0.8F * land.buried());
			float volume = (0.45F + 0.55F * RANDOM.nextFloat()) * (0.4F + 0.6F * storm) * muffled * scale;
			sounds.play(new ThunderRoll(eye, Math.min(1, volume), RANDOM));
			rolls++;
			nextRoll = 200; // never two rolls within ten seconds
		}
	}

	private static void stopAll(SoundManager sounds) {
		for (int i = 0; i < PLAYING.length; i++) {
			if (PLAYING[i] != null) {
				PLAYING[i].stop();
				sounds.stop(PLAYING[i]);
				PLAYING[i] = null;
			}
		}
	}

	/**
	 * The soundscape's clips the sound engine has decoded and loaded so far ({@code path size format}), for the logs:
	 * the engine drops a clip it cannot decode without a word, so this is how to see they all load.
	 */
	public static String loadedClips() {
		StringBuilder out = new StringBuilder();
		int[] count = {0};
		Minecraft.getInstance().getSoundManager().getSoundCacheDebugStats((id, size, format) -> {
			if (id.getNamespace().equals("gloam")) {
				count[0]++;
				out.append(String.format(Locale.ROOT, "%s %d KB %.0f Hz %d ch, ", id.getPath(), size / 1024, format.getSampleRate(), format.getChannels()));
			}
		});
		return count[0] + " loaded: " + out;
	}

	/**
	 * What is playing, for the logs and the showcase: each bed that is audible or wanted, as {@code name now>target},
	 * then the land as last read and the thunder rolls so far.
	 */
	public static String describe() {
		StringBuilder out = new StringBuilder();
		for (Bed bed : BEDS) {
			BedSound sound = PLAYING[bed.ordinal()];
			float now = sound == null ? 0 : sound.volumeNow(), target = sound == null ? VOLUMES[bed.ordinal()] : sound.target();
			if (now > 0.002F || target > 0.002F) {
				out.append(String.format(Locale.ROOT, "%s %.2f>%.2f, ", bed.key(), now, target));
			}
		}
		if (out.isEmpty()) {
			out.append("silent, ");
		}
		Surroundings l = LAND.now();
		out.append(String.format(Locale.ROOT, "land: canopy %.2f trees %.2f grass %.2f sand %.2f snow %.2f still %.2f running %.2f shore %.2f "
				+ "height %.0f exposure %.2f roof %.2f buried %.2f; thunder rolls %d", l.canopy(), l.trees(), l.grass(), l.sand(), l.snow(),
			l.still(), l.running(), l.shore(), l.height(), l.exposure(), l.roof(), l.buried(), rolls));
		return out.toString();
	}
}
