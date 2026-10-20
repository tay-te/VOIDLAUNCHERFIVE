package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Remote sites embodied on their own (docs/LIVING_WORLD_2.md §6.3): a meadow 300 blocks out lies beyond its town's
 * embodiment, so a player who rides up to a flock there would find nobody. Every {@code sites.pass_ticks} the embodiment
 * manager asks here which towns that are not embodied have a remote site within {@code embody_distance} of a player
 * (or of the track out to it), and considers just those sites' workers, wherever their routine has them.
 *
 * <p>Bounded per player: only towns whose territory comes within reach of the player are looked at, each with at most
 * {@code sites.max_per_town} sites (a few distance checks each), so the work does not grow with the world.
 */
public final class RemoteEmbodiment {
	/** A town not embodied with a remote site near a player, and the residents who work its near sites. */
	public record Near(Settlement settlement, List<UUID> workers) {
	}

	private RemoteEmbodiment() {
	}

	/** The towns (not embodied) with a remote site near a player now. Server thread; cheap. */
	public static List<Near> near(ServerLevel level, List<Settlement> towns) {
		FieldWorkConfig.Sites cfg = FieldWork.config().sites();
		List<ServerPlayer> players = level.players();
		List<Near> out = new ArrayList<>();
		if (!cfg.enabled() || players.isEmpty()) {
			return out;
		}
		double reach = cfg.embodyDistance() + 16;
		for (Settlement s : towns) {
			if (s.embodied()) {
				continue;
			}
			double townReach = s.territoryRadius() + cfg.pastureDistance() + reach;
			boolean anyNear = false;
			for (ServerPlayer p : players) {
				if (Commute.distance(p.blockPosition(), s.center()) <= townReach) {
					anyNear = true;
					break;
				}
			}
			if (!anyNear) {
				continue;
			}
			RemoteSites.Plan plan = FieldSites.plan(s);
			if (!plan.anyRemote()) {
				continue;
			}
			Set<UUID> workers = new LinkedHashSet<>();
			for (int i = 0; i < plan.sites().size(); i++) {
				RemoteSites.Site site = plan.sites().get(i);
				if (!site.remote()) {
					continue;
				}
				for (ServerPlayer p : players) {
					if (nearSite(p.blockPosition(), site, reach)) {
						workers.addAll(plan.workersAt(i));
						break;
					}
				}
			}
			if (!workers.isEmpty()) {
				out.add(new Near(s, new ArrayList<>(workers)));
			}
		}
		return out;
	}

	/** Whether a player stands within {@code reach} of a site, or of the track from its town end out to it. */
	static boolean nearSite(BlockPos player, RemoteSites.Site site, double reach) {
		if (Commute.distance(player, site.work()) <= reach + site.radius()) {
			return true;
		}
		BlockPos from = site.from();
		return from != null && segmentDistance(player, from, site.work()) <= reach;
	}

	/** Horizontal distance from a point to a segment. Pure. */
	static double segmentDistance(BlockPos p, BlockPos a, BlockPos b) {
		double ax = a.getX();
		double az = a.getZ();
		double bx = b.getX() - ax;
		double bz = b.getZ() - az;
		double px = p.getX() - ax;
		double pz = p.getZ() - az;
		double len = bx * bx + bz * bz;
		double t = len <= 0 ? 0 : Math.max(0, Math.min(1, (px * bx + pz * bz) / len));
		double dx = px - bx * t;
		double dz = pz - bz * t;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
