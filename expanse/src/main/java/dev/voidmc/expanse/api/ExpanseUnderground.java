package dev.voidmc.expanse.api;

import dev.voidmc.expanse.world.terrain.CavernModel;
import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The spaces under the land, for other mods: what kind of place a point underground is (VOID Gloam's mist, drip and
 * echo), and where the water lies in the caverns (VOID Hearth's river landings).
 *
 * <p>All of it comes from the seed (the cavern model), not from the blocks: the same before and after the chunks are
 * generated, whatever players have dug since. Cheap enough to ask a few times a second; safe from any thread.
 */
public final class ExpanseUnderground {
	private static final String[] REALMS = {"wilds", "lush", "dripstone", "crystal", "ember", "mere", "frozen", "roots", "fossil", "tidal",
		"amber", "brimstone"};
	/** How far up and down a sample looks for the floor and the roof. */
	private static final int LOOK = 160;

	private ExpanseUnderground() {
	}

	/**
	 * One point underground.
	 *
	 * @param underground    whether the point lies under the land's surface (more than three blocks down)
	 * @param space          how far (blocks) the point is inside open space: 0 in the rock, 2 to 4 in a passage, tens
	 *                       in a realm or over a great river
	 * @param floor          the floor under the point (y; the point's own y in the rock)
	 * @param ceiling        the roof over it (y; the point's own y in the rock)
	 * @param water          the cavern water that is nearest: {@code river}, {@code sea} (a buried sea), {@code lake}
	 *                       (a realm's lake), {@code lava}, or {@code none}
	 * @param waterSurface   its surface (y), {@link Integer#MIN_VALUE} for none
	 * @param waterDistance  blocks across to it (0 over it; 1000 for none)
	 * @param realm          the realm the point is in ({@code wilds}, {@code lush}, {@code dripstone}, {@code crystal},
	 *                       {@code ember}, {@code mere}, {@code frozen}, {@code roots}, {@code fossil}, {@code tidal},
	 *                       {@code amber}, {@code brimstone}), {@code sea} under a buried sea, or {@code none}
	 * @param greatRiver     0 to 1: how far the river here is one of the great rivers (wide water, a high vault)
	 * @param glowworms      whether glowworm silk hangs from the roof here (the great rivers' vaults, the tidal grottos)
	 * @param bats           whether bats roost here (the great rivers, the buried seas)
	 * @param drip           whether water drips from the roof here (wet realms, rivers, seas)
	 * @param mine           whether the point is in or by one of the great mines
	 */
	public record Here(boolean underground, float space, int floor, int ceiling, String water, int waterSurface, float waterDistance,
		String realm, float greatRiver, boolean glowworms, boolean bats, boolean drip, boolean mine) {
	}

	/** The water in the caverns under one column. */
	public record Water(String kind, int surface, float distance) {
		static final Water NONE = new Water("none", Integer.MIN_VALUE, 1000);
	}

	/** Asks about one level's underground. */
	public interface Sampler {
		Here at(int x, int y, int z);

		/**
		 * The cavern water under (x, z): a river tunnel's, a buried sea's or a realm's lake, with its surface; kind
		 * {@code none} where there is none within reach.
		 */
		Water water(int x, int z);
	}

	/** A sampler for the level, or null if it is not on the Expanse's Earth terrain. */
	public static @Nullable Sampler sampler(ServerLevel level) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return null;
		}
		CavernModel caverns = earth.model(level.getChunkSource().randomState()).caverns();
		return new Sampler() {
			@Override
			public Here at(int x, int y, int z) {
				return ExpanseUnderground.at(caverns, x, y, z);
			}

			@Override
			public Water water(int x, int z) {
				return ExpanseUnderground.water(caverns, caverns.info(x, z));
			}
		};
	}

	private static Here at(CavernModel caverns, int x, int y, int z) {
		Column land = caverns.terrain().sample(x, z);
		float space = Math.max(0, caverns.inside(x, y, z));
		int floor = y;
		int ceiling = y;
		if (space > 0) {
			while (floor > y - LOOK && caverns.inside(x, floor - 1, z) > 0) {
				floor -= Math.max(1, (int) (caverns.inside(x, floor - 1, z) * 0.7F));
			}
			while (ceiling < y + LOOK && caverns.inside(x, ceiling + 1, z) > 0) {
				ceiling += Math.max(1, (int) (caverns.inside(x, ceiling + 1, z) * 0.7F));
			}
		}
		CavernModel.Info c = caverns.info(x, z);
		Water water = water(caverns, c);
		boolean inRealm = c.hall && c.hallEdge > 0;
		String realm = !inRealm ? "none" : c.sea ? "sea" : c.theme >= 0 && c.theme < REALMS.length ? REALMS[c.theme] : "none";
		boolean great = c.riverOn && c.riverGreat >= 0.25F && c.riverDist < c.riverSpan;
		boolean glowworms = great || inRealm && !c.sea && c.theme == CavernModel.TIDAL;
		boolean bats = great || inRealm && c.sea;
		boolean drip = !water.kind().equals("none") && !water.kind().equals("lava") && water.distance() < 24
			|| inRealm && (c.theme == CavernModel.DRIPSTONE || c.theme == CavernModel.LUSH || c.theme == CavernModel.TIDAL);
		return new Here(y < land.height - 3, space, floor, ceiling, water.kind(), water.surface(), water.distance(), realm,
			c.riverOn ? c.riverGreat : 0, glowworms, bats, drip, c.mine != null);
	}

	private static Water water(CavernModel caverns, CavernModel.Info c) {
		if (c.hall && c.hallEdge > 0 && c.lake > c.floor) {
			String kind = c.sea ? "sea" : c.lava ? "lava" : "lake";
			return new Water(kind, (int) Math.floor(c.lake), 0);
		}
		if (c.riverOn && !c.riverFalls && c.riverWater > -900) {
			return new Water("river", c.waterTop(), Math.max(0, c.riverDist - c.riverHalfWidth));
		}
		return Water.NONE;
	}
}
