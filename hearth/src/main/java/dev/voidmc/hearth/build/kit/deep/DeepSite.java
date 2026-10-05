package dev.voidmc.hearth.build.kit.deep;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.draft.Draft;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * How a building of the deep town reads its realm (LIVING_WORLD.md §8.2, §8.5). A deep plot stands on the realm's floor:
 * {@code origin.y} is the floor, the plot's {@code realm} param names the realm, and the ground under and round the plot
 * is the realm's floor as the planner sees it ({@code plan.deep.RealmTerrain}: where the realm's wall stands, the rock
 * rises as ground to the vault, so a building against the wall finds its back cut into the rock). What the terrain probe
 * cannot say comes in params, agreed with the planner (docs/requests/b-deep.md):
 *
 * <ul>
 *   <li>{@code ceiling_y} (int, world y): the underside of the vault over the plot, the lowest over its footprint. Nothing
 *   is built into it: {@link #top()} is the highest local y a building may take, under the vault and the plot's
 *   height. Absent: only the plot's height limits.</li>
 *   <li>{@code character} (string): the realm's character, as the underground probe names it — {@code wilds},
 *   {@code lush}, {@code dripstone}, {@code crystal}, {@code ember}, {@code mere}, {@code frozen}, {@code roots},
 *   {@code fossil}, {@code tidal}, {@code amber}, {@code brimstone}. It decides the realm's glow the town lights and
 *   decorates with ({@link Glow}). Absent: read from the {@code local} materials' {@code light} the planner gives deep
 *   plots, else the glowcap wilds.</li>
 * </ul>
 *
 * <p>The probe a deep plot is drawn on should be the realm's floor. If a caller hands it the surface's probe instead
 * (the ground there stands tens of blocks over the realm floor all over the plot), {@link #probeFor} replaces it with a
 * flat realm floor at the plot's level, so the building stands on its floor instead of digging for the sky; deep
 * archetypes draw through that ({@code Archetype.terrain}).
 */
public final class DeepSite {
	/** Characters of the realms, as Expanse's underground probe names them. */
	public static final String[] CHARACTERS = {"wilds", "lush", "dripstone", "crystal", "ember", "mere", "frozen", "roots", "fossil", "tidal", "amber",
		"brimstone"};

	private final int ceiling;
	private final int top;
	private final String character;
	private final boolean realm;

	private DeepSite(int ceiling, int top, String character, boolean realm) {
		this.ceiling = ceiling;
		this.top = top;
		this.character = character;
		this.realm = realm;
	}

	public static DeepSite of(Draft d) {
		int floorY = d.spec().floorY();
		int ceiling = d.hasParam("ceiling_y") ? d.param("ceiling_y", Integer.MAX_VALUE) - floorY : Integer.MAX_VALUE;
		int top = Math.min(d.maxHeight() - 1, ceiling == Integer.MAX_VALUE ? Integer.MAX_VALUE : ceiling - 1);
		return new DeepSite(ceiling, top, character(d.params()), d.hasParam("realm"));
	}

	/** The realm's character a plot's params name (or imply through its local light). */
	public static String character(CompoundTag params) {
		String c = params.getStringOr("character", "").toLowerCase(Locale.ROOT);
		for (String k : CHARACTERS) {
			if (c.contains(k)) {
				return k;
			}
		}
		String light = params.getCompoundOrEmpty("local").getStringOr("light", "");
		if (light.contains("amber")) {
			return "amber";
		}
		if (light.contains("prismite")) {
			return "crystal";
		}
		if (light.contains("sea_lantern")) {
			return "mere";
		}
		if (light.contains("glowcap")) {
			return "wilds";
		}
		if (light.contains("shroomlight")) {
			return "ember";
		}
		return "wilds";
	}

	/** The vault's underside relative to the floor, or {@code Integer.MAX_VALUE} when the plot was not told. */
	public int ceiling() {
		return this.ceiling;
	}

	/** Whether the plot knows where its vault is. */
	public boolean knowsCeiling() {
		return this.ceiling != Integer.MAX_VALUE;
	}

	/** The highest local y anything may be built at: under the vault (one clear of it) and the plot's height. */
	public int top() {
		return this.top;
	}

	public String character() {
		return this.character;
	}

	/** The plot was planned in a realm (it carries {@code realm}); otherwise it was built by hand or by a test. */
	public boolean inRealm() {
		return this.realm;
	}

	// -------------------------------------------------------------------------------------- the probe

	/**
	 * The probe a deep plot should be drawn on: the one given, unless the plot is in a realm and the probe plainly reads
	 * the surface over it (the ground along the plot's front stands six or more above its floor, where the planner set
	 * the floor to meet the street), in which case a flat floor at the plot's level, open above.
	 */
	public static TerrainProbe probeFor(PlotSpec spec, TerrainProbe probe) {
		if (!spec.params().contains("realm")) {
			return probe;
		}
		dev.voidmc.hearth.build.blueprint.LocalFrame f = new dev.voidmc.hearth.build.blueprint.LocalFrame(spec);
		int floor = spec.floorY();
		int above = 0;
		int n = 0;
		for (int x = 0; x < f.frontage(); x++) {
			for (int z = 0; z < Math.min(2, f.depth()); z++) {
				if (probe.groundY(f.worldX(x, z), f.worldZ(x, z)) >= floor + 6) {
					above++;
				}
				n++;
			}
		}
		return above * 4 >= n * 3 ? new FlatFloor(probe, floor) : probe;
	}

	/** A realm floor flat at one level, open above (what a deep plot stands on when only the surface's probe was given). */
	public static final class FlatFloor implements TerrainProbe {
		private final TerrainProbe base;
		private final int floor;

		public FlatFloor(TerrainProbe base, int floor) {
			this.base = base;
			this.floor = floor;
		}

		@Override
		public long seed() {
			return this.base.seed();
		}

		@Override
		public int minY() {
			return this.base.minY();
		}

		@Override
		public int maxY() {
			return this.base.maxY();
		}

		@Override
		public int seaLevel() {
			return this.base.minY();
		}

		@Override
		public int groundY(int x, int z) {
			return this.floor;
		}

		@Override
		public int waterY(int x, int z) {
			return NO_WATER;
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return null;
		}

		@Override
		public float temperature(int x, int z) {
			return 0.8F;
		}
	}
}
