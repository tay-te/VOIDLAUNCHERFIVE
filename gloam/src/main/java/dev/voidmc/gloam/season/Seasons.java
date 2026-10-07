package dev.voidmc.gloam.season;

import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Atmosphere;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Seasons you can see: the year Gloam already keeps ({@link Air#year}) shown on the land. The grass and leaves take the
 * season's colours through vanilla's biome colour resolvers ({@link SeasonResolvers}, tree by tree in
 * {@link SeasonLeaves}), so the chunks bake them in and shader packs see them; the broadleaf crowns thin in late autumn
 * and stand bare through winter, their leaf blocks drawn as twigs ({@link SeasonalLeaves}); the snow falls lower in winter ({@link Snowline}); and VOID LOD's
 * far land turns with the near ({@link SeasonLod}).
 *
 * <p>The colours move in steps, {@value #STEPS} a year (half a day each at the default eight-day seasons), not every
 * tick: at each step the tint caches are cleared and the chunk meshes around the camera rebuilt over a few ticks
 * ({@link Remesh}). Switching Gloam (or the {@code seasons} setting) off is a step back to vanilla's colours. The far
 * land needs no rebuilding: VOID LOD turns it at draw time by a table set from the year every {@value #LOD_TICKS} ticks.
 */
public final class Seasons {
	/** Colour steps a year. */
	public static final int STEPS = 64;
	/** How often the far land's season table is set again, in ticks. */
	private static final int LOD_TICKS = 10;

	private static volatile SeasonColours colours = SeasonColours.NONE;

	private static final Remesh REMESH = new Remesh();
	/** Whether the crowns were thinned at the last step (the setting can change between steps). */
	private static boolean bareTrees;
	private static ClientLevel level;
	private static int step = Integer.MIN_VALUE;
	private static int ticks;
	private static boolean lodOn;
	private static int steps;
	private static long applyNanos;

	private Seasons() {
	}

	public static void init() {
		SeasonLeaves.register();
		SeasonalLeaves.register();
		ClientTickEvents.END_CLIENT_TICK.register(Seasons::tick);
	}

	/** The colours the chunks are tinted with now ({@link SeasonColours#NONE} with the seasons off). Any thread. */
	public static SeasonColours colours() {
		return colours;
	}

	/** Whether VOID LOD is working out vanilla's block colours on this thread, so the tints must give vanilla's. */
	static boolean lodBaking() {
		return SeasonLod.baking();
	}

	private static void tick(Minecraft mc) {
		ClientLevel now = mc.level;
		if (now != level) {
			level = now;
			step = Integer.MIN_VALUE;
			if (now != null) {
				SeasonLod.install();
			}
		}
		if (now == null) {
			colours = SeasonColours.NONE;
			return;
		}
		GloamSettings settings = GloamSettings.get();
		Air air = Atmosphere.get().air();
		boolean on = settings.enabled && settings.seasons && air != null && now.dimensionType().hasSkyLight();
		float year = on ? air.year(now.getOverworldClockTime()) : 0;
		int want = on ? (int) Math.floor(year * STEPS) : -1;
		if (want != step) {
			step = want;
			apply(mc, want < 0 ? SeasonColours.NONE : SeasonColours.at((want + 0.5F) / STEPS));
		}
		if (++ticks % LOD_TICKS == 0 || want < 0 != !lodOn) {
			// the far land's table follows the year smoothly (it costs nothing to change), and goes at once with the seasons
			lodOn = want >= 0;
			SeasonLod.tints(lodOn ? SeasonColours.at(year) : SeasonColours.NONE);
		}
		REMESH.tick(mc);
		REMESH.watch(mc);
		if (settings.bareTrees != bareTrees && step != Integer.MIN_VALUE) {
			// the crowns' models follow the setting: a step in the same colours
			apply(mc, colours);
		}
	}

	private static void apply(Minecraft mc, SeasonColours next) {
		long t0 = System.nanoTime();
		boolean changed = next.on() || colours.on() || bareTrees != GloamSettings.get().bareTrees;
		colours = next;
		if (changed && mc.level != null) {
			mc.level.clearTintCaches();
			REMESH.start(mc);
		}
		bareTrees = GloamSettings.get().bareTrees;
		steps++;
		applyNanos += System.nanoTime() - t0;
	}

	/** How many leaves the crowns drop now, as a share of what they would with the seasons not shown: few from a bare crown. */
	public static float leafFall() {
		SeasonColours c = colours;
		return c.on() && GloamSettings.get().bareTrees ? Math.min(1, 1.15F - c.bare()) : 1;
	}

	/**
	 * The colour of an untinted leaf block ({@code drawn}: its texture's) as the season shows it: the crowns Expanse draws
	 * in their autumn colours (maple, aspen, larch) are green until autumn.
	 */
	public static int untintedLeaf(net.minecraft.world.level.block.state.BlockState state, int drawn) {
		SeasonColours c = colours;
		return c.on() ? drawn & 0xFF000000 | SeasonalLeaves.drawnAutumn(drawn & 0xFFFFFF, SeasonalLeaves.summer(state), c) : drawn;
	}

	/** Whether the chunks have finished taking the last step's colours (marked, and the meshers' queues empty). */
	public static boolean settled() {
		Minecraft mc = Minecraft.getInstance();
		return !REMESH.busy() && (mc.levelRenderer == null || mc.levelRenderer.hasRenderedAllSections());
	}

	/** One line for the logs: the step shown and what the last ones cost. */
	public static String stats() {
		SeasonColours c = colours;
		return String.format(Locale.ROOT, "season step %d (year %.3f, %s), bare %.2f, turned %.2f; %d steps, %.2f ms each on the client thread; %s",
			step, c.year(), c.on() ? "on" : "off", c.bare(), c.turned(), steps, steps == 0 ? 0 : applyNanos / 1e6 / steps, REMESH.stats());
	}
}
