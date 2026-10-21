package dev.voidmc.hearth.plan.wild;

import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.wild.PostSite;
import dev.voidmc.hearth.sim.wild.PostType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/** Synthetic sites of every post type round the origin, for the posts' tests. */
public final class PostPlannerTestSites {
	private PostPlannerTestSites() {
	}

	/** A road along x through z = 0 at the ground ({@code x, y, z} every other column, -40..40). */
	public static int[] roadAlongX(TerrainProbe t, int z) {
		List<Integer> pts = new ArrayList<>();
		for (int x = -40; x <= 40; x += 2) {
			pts.add(x);
			pts.add(t.groundY(x, z));
			pts.add(z);
		}
		return pts.stream().mapToInt(Integer::intValue).toArray();
	}

	public static PostSite site(PostType type, TerrainProbe t) {
		return switch (type) {
			// on the road itself, facing along it
			case TOLL_GATE -> new PostSite(1, type, 0, t.groundY(0, 0), 0, Direction.EAST, 100, 42, roadAlongX(t, 0), 0, 0, 1);
			// beside the road, facing it
			case INN, CARAVAN_STOP -> {
				int off = (int) Math.round(type.radius * 0.6) + 6;
				yield new PostSite(2, type, 0, t.groundY(0, -off), -off, Direction.SOUTH, 100, 43, roadAlongX(t, 0), 0, 0, 1);
			}
			case NOMAD_CAMP -> new PostSite(3, type, 0, t.groundY(0, 0), 0, Direction.SOUTH, 100, 44, new int[0], 80, 0, 1);
			case HARBOUR -> new PostSite(4, type, -22, t.groundY(-22, 0), 0, Direction.EAST, 100, 45, new int[0], 0, 0, 1);
			default -> new PostSite(5, type, 0, t.groundY(0, 0), 0, Direction.NORTH, 100, 46, new int[0], 0, 0, 1);
		};
	}

	public static TerrainProbe terrain(PostType type) {
		return type == PostType.HARBOUR ? SyntheticTerrain.named("coast", 3) : SyntheticTerrain.named("flat", 3);
	}

}
