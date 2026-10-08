package dev.voidmc.atlas.client.compat;

import dev.voidmc.atlas.core.CavernWater;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Expanse's caverns (its {@code ExpanseUnderground} API, found by reflection: Atlas does not need Expanse) as
 * {@link CavernWater}, in singleplayer only. It answers from Expanse's seed-based cavern model, so it only tints cave
 * water the player has already revealed; it never reveals anything. Answers are kept by 2 x 2 columns.
 */
public final class ExpanseCaverns implements CavernWater {
	private static final Logger LOGGER = LoggerFactory.getLogger("VOID Atlas");
	/** How close (blocks, up or down) revealed water's surface must be to the caverns' to take its kind. */
	private static final int SURFACE_SLACK = 3;
	private static final int MAX_KEPT = 1 << 18;

	private final Object sampler;
	private final Method water;
	private final Method kind;
	private final Method surface;
	private final Method distance;
	/** (x >> 1, z >> 1) to kind (low 8 bits), distance (next 8), surface (high 32). */
	private final ConcurrentHashMap<Long, Long> kept = new ConcurrentHashMap<>();
	private volatile boolean failed;

	private ExpanseCaverns(Object sampler, Method water, Method kind, Method surface, Method distance) {
		this.sampler = sampler;
		this.water = water;
		this.kind = kind;
		this.surface = surface;
		this.distance = distance;
	}

	/** The caverns of a singleplayer level on VOID Expanse's Earth, or null (no Expanse, another generator, an older one). */
	public static CavernWater of(ServerLevel level) {
		if (level == null || !FabricLoader.getInstance().isModLoaded("expanse")) {
			return null;
		}
		try {
			Class<?> api = Class.forName("dev.voidmc.expanse.api.ExpanseUnderground");
			Object sampler = api.getMethod("sampler", ServerLevel.class).invoke(null, level);
			if (sampler == null) {
				return null;
			}
			Class<?> samplerType = Class.forName("dev.voidmc.expanse.api.ExpanseUnderground$Sampler");
			Class<?> waterType = Class.forName("dev.voidmc.expanse.api.ExpanseUnderground$Water");
			return new ExpanseCaverns(sampler, samplerType.getMethod("water", int.class, int.class), waterType.getMethod("kind"),
				waterType.getMethod("surface"), waterType.getMethod("distance"));
		} catch (ReflectiveOperationException | LinkageError e) {
			// an Expanse without the underground API: cave water keeps its own colours
			return null;
		}
	}

	@Override
	public int at(int x, int z, int y) {
		if (this.failed) {
			return NONE;
		}
		long key = (long) (x >> 1) << 32 | (z >> 1) & 0xFFFFFFFFL;
		Long got = this.kept.get(key);
		long v;
		if (got != null) {
			v = got;
		} else {
			v = this.ask((x >> 1 << 1) + 1, (z >> 1 << 1) + 1);
			if (this.kept.size() > MAX_KEPT) {
				this.kept.clear();
			}
			this.kept.put(key, v);
		}
		int kind = (int) (v & 0xFF);
		int dist = (int) (v >> 8 & 0xFF);
		int surface = (int) (v >> 32);
		if (kind == NONE || Math.abs(y - surface) > SURFACE_SLACK) {
			return NONE;
		}
		// a river within a block of its bed's edge; a sea, lake or lava lake over it
		return dist <= (kind == RIVER ? 1 : 0) ? kind : NONE;
	}

	private long ask(int x, int z) {
		try {
			Object w = this.water.invoke(this.sampler, x, z);
			String k = (String) this.kind.invoke(w);
			int kind = switch (k) {
				case "river" -> RIVER;
				case "sea" -> SEA;
				case "lake" -> LAKE;
				case "lava" -> LAVA;
				default -> NONE;
			};
			if (kind == NONE) {
				return NONE;
			}
			int surface = (Integer) this.surface.invoke(w);
			int dist = (int) Math.min(255, Math.max(0, Math.ceil((Float) this.distance.invoke(w))));
			return (long) surface << 32 | (long) dist << 8 | kind;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			this.failed = true;
			LOGGER.warn("Atlas: VOID Expanse's caverns could not be asked ({}); cave water keeps its own colours", e.toString());
			return NONE;
		}
	}
}
