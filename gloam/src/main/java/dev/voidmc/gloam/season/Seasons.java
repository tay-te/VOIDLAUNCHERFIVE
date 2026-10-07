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
 * and stand bare through winter ({@link BareCrowns}); the snow falls lower in winter ({@link Snowline}); and VOID LOD's
 * far land turns with the near ({@link SeasonLod}).
 *
 * <p>The colours move in steps, {@value #STEPS} a year (half a day each at the default eight-day seasons), not every
 * tick: at each step the tint caches are cleared and the chunk meshes around the camera rebuilt over a few ticks
 * ({@link Remesh}), and VOID LOD rebuilds its tiles in the background. Switching Gloam (or the {@code seasons} setting)
 * off is a step back to vanilla's colours. The far land follows a step once it has held for {@value #LOD_HOLD} ticks, so
 * flicking Gloam off and on again does not rebuild the whole horizon twice.
 */
public final class Seasons {
	/** Colour steps a year. */
	public static final int STEPS = 64;
	/** How long a step must hold before the far land is rebuilt for it, in ticks. */
	private static final int LOD_HOLD = 100;

	private static volatile SeasonColours colours = SeasonColours.NONE;
	private static volatile SeasonColours lodColours = SeasonColours.NONE;

	private static final Remesh REMESH = new Remesh();
	private static BareCrowns crowns;
	private static ClientLevel level;
	private static int step = Integer.MIN_VALUE;
	private static int held;
	private static int ticks;
	private static int steps;
	private static long applyNanos;

	private Seasons() {
	}

	public static void init() {
		SeasonLeaves.register();
		ClientTickEvents.END_CLIENT_TICK.register(Seasons::tick);
	}

	/** The colours the chunks are tinted with now ({@link SeasonColours#NONE} with the seasons off). Any thread. */
	public static SeasonColours colours() {
		return colours;
	}

	/** The colours VOID LOD's far land is turned to now. Any thread. */
	static SeasonColours lodColours() {
		return lodColours;
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
			held = 0;
		}
		if (now == null) {
			colours = lodColours = SeasonColours.NONE;
			return;
		}
		GloamSettings settings = GloamSettings.get();
		Air air = Atmosphere.get().air();
		boolean on = settings.enabled && settings.seasons && air != null && now.dimensionType().hasSkyLight();
		int want = on ? (int) Math.floor(air.year(now.getOverworldClockTime()) * STEPS) : -1;
		if (want != step) {
			boolean first = step == Integer.MIN_VALUE;
			step = want;
			held = 0;
			apply(mc, want < 0 ? SeasonColours.NONE : SeasonColours.at((want + 0.5F) / STEPS), first);
		} else if (held >= 0 && ++held >= LOD_HOLD) {
			held = -1;
			if (lodColours != colours) {
				lodColours = colours;
				SeasonLod.recolour();
			}
		}
		REMESH.tick(mc);
		REMESH.watch(mc);
		if (crowns == null) {
			crowns = new BareCrowns();
		}
		if (++ticks % 40 == 0 || crowns.pending) {
			// a resource reload makes the atlas again: its sprites are drawn again as they are now
			crowns.update(mc, colours, colours.on() && settings.bareTrees);
		}
	}

	private static void apply(Minecraft mc, SeasonColours next, boolean first) {
		long t0 = System.nanoTime();
		boolean changed = next.on() || colours.on();
		colours = next;
		if (first) {
			// a new level: the far land is built in these colours from the start
			lodColours = next;
			held = -1;
			SeasonLod.recolour();
		}
		if (changed && mc.level != null) {
			mc.level.clearTintCaches();
			REMESH.start(mc);
		}
		if (crowns != null) {
			crowns.update(mc, next, next.on() && GloamSettings.get().bareTrees);
		}
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
		return c.on() ? drawn & 0xFF000000 | BareCrowns.drawnAutumn(drawn & 0xFFFFFF, BareCrowns.summer(state), c) : drawn;
	}

	/** Whether the chunks have finished taking the last step's colours (marked, and the meshers' queues empty). */
	public static boolean settled() {
		Minecraft mc = Minecraft.getInstance();
		return !REMESH.busy() && (mc.levelRenderer == null || mc.levelRenderer.hasRenderedAllSections());
	}

	/** One line for the logs: the step shown and what the last ones cost. */
	public static String stats() {
		SeasonColours c = colours;
		return String.format(Locale.ROOT, "season step %d (year %.3f, %s), bare %.2f, turned %.2f; %d steps, %.2f ms each on the client thread; %s; %s",
			step, c.year(), c.on() ? "on" : "off", c.bare(), c.turned(), steps, steps == 0 ? 0 : applyNanos / 1e6 / steps, REMESH.stats(),
			crowns == null ? "no crowns" : crowns.stats());
	}
}
