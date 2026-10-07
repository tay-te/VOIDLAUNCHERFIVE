package dev.voidmc.expanse.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Land another mod has claimed to build on (VOID Hearth's planned settlements: their plots and roads). Expanse owns
 * the land between settlements and keeps its own things off claimed land: the little things and wayside sights, the
 * fishponds, anything else that covers more than the block a feature starts on. (Single-block decoration needs
 * nothing from here: Hearth guards each feature placement itself.)
 *
 * <p>A mod registers one {@link Claim} at startup. Expanse asks from world generation workers, many times per chunk,
 * so a claim must be thread-safe and cheap (a lookup, not a computation).
 */
public final class LandClaims {
	/** Whether the column (x, z) of {@code level} is claimed. */
	@FunctionalInterface
	public interface Claim {
		boolean claimed(ServerLevel level, int x, int z);
	}

	private static final List<Claim> CLAIMS = new CopyOnWriteArrayList<>();

	private LandClaims() {
	}

	public static void register(Claim claim) {
		CLAIMS.add(claim);
	}

	/** Whether any mod has registered a claim (when none has, nothing is ever claimed). */
	public static boolean any() {
		return !CLAIMS.isEmpty();
	}

	/** Whether any registered claim covers the column (x, z). */
	public static boolean claimed(WorldGenLevel level, int x, int z) {
		if (CLAIMS.isEmpty()) {
			return false;
		}
		ServerLevel server = level.getLevel();
		for (Claim c : CLAIMS) {
			if (c.claimed(server, x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Whether any column of the box (x0, z0)-(x1, z1), sampled every {@code step} blocks and at its corners, is claimed. */
	public static boolean anyClaimed(WorldGenLevel level, int x0, int z0, int x1, int z1, int step) {
		if (CLAIMS.isEmpty()) {
			return false;
		}
		for (int x = x0; ; x = Math.min(x + step, x1)) {
			for (int z = z0; ; z = Math.min(z + step, z1)) {
				if (claimed(level, x, z)) {
					return true;
				}
				if (z == z1) {
					break;
				}
			}
			if (x == x1) {
				return false;
			}
		}
	}
}
