package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.folk.FolkTraits;
import dev.voidmc.hearth.law.stealth.Detection;
import dev.voidmc.hearth.law.stealth.Visibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Who saw it (docs/LIVING_WORLD.md §4): the awake, embodied folk whose eyes reach the player, weighed by light,
 * distance, sneaking, armour and their own sharpness; for loud deeds, also those close enough to hear who it was.
 * Asks the npc module for the folk near the player, so crimes nobody is near to see cost nothing and towns nobody visits
 * have no witnesses at all.
 */
public final class Witnesses {
	private Witnesses() {
	}

	/** One witness and how plainly they saw (or heard) it. */
	public record Witness(FolkHandle folk, FolkTraits traits, double sight, double hearing) {
		public double certainty() {
			return Math.max(this.sight, this.hearing * 0.8);
		}
	}

	/** How well the folk round the player can see them right now, 0–1 (for the sneaking eye), ignoring hearing of deeds. */
	public static double visibility(ServerLevel level, ServerPlayer player) {
		double best = 0;
		for (Witness w : find(level, player, 0.0)) {
			best = Math.max(best, w.certainty());
		}
		return best;
	}

	/**
	 * The folk who witness the player doing something now, most certain first.
	 *
	 * @param deedNoise how loud the deed is, 0 (silent: lifting a purse) to 1 (smashing a door, a fight)
	 */
	public static List<Witness> find(ServerLevel level, ServerPlayer player, double deedNoise) {
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return List.of();
		}
		LawConfig.Witnesses cfg = Law.config().witnesses();
		Detection.Stance stance = Visibility.stance(player);
		double radius = Math.max(cfg.sightRange() * 1.45, cfg.hearingRange());
		List<Witness> out = new ArrayList<>();
		for (FolkHandle folk : npc.folkNear(level, player.position(), radius)) {
			if (folk.entity() == null || !folk.entity().isAlive()) {
				continue;
			}
			FolkTraits traits = FolkTraits.of(folk.resident());
			Detection.Observer observer = traits.observer();
			boolean asleep = !folk.awake();
			double d = folk.entity().distanceTo(player);
			double sight = 0;
			if (!asleep && d < Detection.sightRange(cfg.sightRange(), cfg.lightWeight(), stance, observer)) {
				// the raycast only for folk the light and distance leave a chance
				sight = Detection.sight(d, folk.canSee(player), cfg.sightRange(), cfg.lightWeight(), stance, observer);
			}
			double hearing = 0;
			if (deedNoise > 0 && d < cfg.hearingRange()) {
				hearing = Detection.hearing(d, cfg.hearingRange(), stance, deedNoise, clearPath(level, folk, player), observer, asleep);
			}
			if (Detection.witnessed(sight, hearing, deedNoise)) {
				out.add(new Witness(folk, traits, sight, hearing));
			}
		}
		out.sort(Comparator.comparingDouble(Witness::certainty).reversed());
		return out;
	}

	private static boolean clearPath(ServerLevel level, FolkHandle folk, ServerPlayer player) {
		Vec3 from = folk.entity().getEyePosition();
		Vec3 to = player.getEyePosition();
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, folk.entity())).getType() == HitResult.Type.MISS;
	}
}
