package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.compat.Shaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The small living things in the air: drifting leaves, pollen and dust in the light, fireflies, mist wisps, breath in
 * the cold, wind-driven snow and the spray at the foot of waterfalls.
 *
 * <p>They are particles, so shader packs draw them too. {@link MoteSpawner} places them around the camera once a tick
 * from what is really there (the undersides of leaf canopies, meadows and flowers, water, the mist layer, falling
 * water) and the air at the camera ({@link Atmosphere#here()}); each {@link Mote} then moves itself with the wind.
 * How many there are follows the {@code motes} setting, Minecraft's own particle setting, a per-tick spawn budget and
 * a cap on how many may live at once ({@code moteLimit}). Switching Gloam or the motes off fades the ones in the air
 * out within half a second.
 */
public final class Motes {
	/** The kinds of mote: each its own particle type, sprites ({@code assets/gloam/particles}) and count. */
	public enum Kind {
		LEAF("leaf"),
		POLLEN("pollen"),
		FIREFLY("firefly"),
		WISP("wisp"),
		BREATH("breath"),
		SNOW("snow"),
		SPRAY("spray");

		final String name;
		final SimpleParticleType type = FabricParticleTypes.simple();
		final List<Mote> live = new ArrayList<>();
		SpriteSet sprites;
		int peak;

		Kind(String name) {
			this.name = name;
		}

		TextureAtlasSprite sprite(RandomSource random) {
			return this.sprites.get(random);
		}

		/** How many of this kind are in the air now. */
		public int count() {
			return this.live.size();
		}
	}

	private static final MoteSpawner SPAWNER = new MoteSpawner();
	private static ClientLevel level;
	private static Vec3 camera = Vec3.ZERO;
	private static final Vector3f MIST = new Vector3f(0.8F, 0.82F, 0.85F);
	private static boolean active, pack;
	private static int total;
	// what the motes cost, summed since the showcase last asked (stats())
	private static long spawnNanos, particleNanos, ticks;

	private Motes() {
	}

	/** Common start (registries): the particle types. */
	public static void register() {
		for (Kind kind : Kind.values()) {
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, Gloam.id(kind.name), kind.type);
		}
	}

	/** Client start: the particle providers and the spawner. */
	public static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		for (Kind kind : Kind.values()) {
			// the providers serve /particle and anything else that asks for a mote by its type; Gloam's own motes
			// are made directly, with what they need to know (a leaf's tint, how thick the mist is)
			registry.register(kind.type, sprites -> {
				kind.sprites = sprites;
				return (options, level, x, y, z, xa, ya, za, random) -> Mote.standard(kind, level, x, y, z, random);
			});
		}
		MoteLayers.init();
		ClientTickEvents.END_CLIENT_TICK.register(Motes::tick);
	}

	private static void tick(Minecraft mc) {
		long start = System.nanoTime();
		if (mc.level != level) {
			level = mc.level;
			for (Kind kind : Kind.values()) {
				kind.live.clear();
			}
		}
		total = 0;
		for (Kind kind : Kind.values()) {
			kind.live.removeIf(mote -> !mote.isAlive());
			total += kind.live.size();
			kind.peak = Math.max(kind.peak, kind.live.size());
		}
		Camera cam = mc.gameRenderer.mainCamera();
		if (cam.isInitialized()) {
			camera = cam.position();
			if (level != null) {
				mistColour(cam);
			}
		}
		pack = Shaders.packInUse();
		GloamSettings settings = GloamSettings.get();
		active = level != null && settings.enabled && settings.motes > 0 && Atmosphere.get().air() != null;
		if (!active || mc.isPaused() || !level.dimensionType().hasSkyLight()) {
			return;
		}
		float density = (float) settings.motes * switch (mc.options.particles().get()) {
			case ALL -> 1.0F;
			case DECREASED -> 0.5F;
			case MINIMAL -> 0.2F;
		};
		SPAWNER.tick(mc, level, Atmosphere.get().here(), camera, density, settings.moteLimit);
		spawnNanos += System.nanoTime() - start;
		ticks++;
	}

	/** Whether motes may be in the air: Gloam on, the motes setting above zero, a world loaded. */
	static boolean active() {
		return active;
	}

	/** The camera's position as of this tick. */
	static Vec3 camera() {
		return camera;
	}

	/**
	 * The colour of mist, breath and spray this tick: the colour of the air itself (the fog's colour, leaning a little
	 * toward the sky's, as vanilla mixes them), lifted a touch toward white in daylight, since mist lit by the sky looks
	 * brighter than the haze behind it. The soft motes are drawn in it at full brightness, so they always match the fog
	 * around them, dark at night and pale at dawn, whatever the sky layers do.
	 */
	private static void mistColour(Camera cam) {
		Vector3fc fog = cam.attributeProbe().getValue(EnvironmentAttributes.FOG_COLOR, 1.0F);
		Vector3fc sky = cam.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1.0F);
		float rain = level.getRainLevel(1.0F);
		float shade = 1 - rain * 0.4F;
		MIST.set(fog).lerp(new Vector3f(sky).mul(shade), 0.25F);
		float light = Math.max(MIST.x, Math.max(MIST.y, MIST.z));
		MIST.lerp(new Vector3f(light), 0.25F).lerp(new Vector3f(1), 0.12F * light);
	}

	/** The colour of the air for the soft motes, this tick. */
	static Vector3fc mist() {
		return MIST;
	}

	/** Whether a shader pack is drawing (it then lights the motes itself). */
	static boolean pack() {
		return pack;
	}

	/** How many motes of every kind are in the air. */
	static int total() {
		return total;
	}

	/** Adds a mote to the particle engine and counts it. */
	static void add(Mote mote) {
		Minecraft.getInstance().particleEngine.add(mote);
		mote.kind.live.add(mote);
		total++;
	}

	static void timeParticle(long nanos) {
		particleNanos += nanos;
	}

	/**
	 * What the motes have done since this was last called: how many of each kind are in the air now (and the most
	 * there were), and what they cost per client tick on average: the spawner and the motes' own movement.
	 */
	public static String stats() {
		StringBuilder sb = new StringBuilder();
		int now = 0;
		for (Kind kind : Kind.values()) {
			now += kind.count();
			sb.append(kind.name).append(' ').append(kind.count()).append(" (max ").append(kind.peak).append("), ");
			kind.peak = kind.count();
		}
		double perTick = Math.max(1, ticks);
		sb.append(String.format(Locale.ROOT, "total %d; spawner %.1f us/tick, motes %.1f us/tick over %d ticks", now, spawnNanos / perTick / 1000.0,
			particleNanos / perTick / 1000.0, ticks));
		spawnNanos = 0;
		particleNanos = 0;
		ticks = 0;
		return sb.toString();
	}
}
