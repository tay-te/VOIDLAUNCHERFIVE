package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tracker (mastery): sneak still for two seconds to read the ground. The nearest wild animal within 96 blocks leaves a
 * line of faint, earth-brown prints from you to it, and keeps leaving them where it walks, for 30 seconds. The prints
 * are particles sent to that one player only: nobody else sees them, and they don't glow.
 */
public final class Tracker {
	/** Dull earth brown, small: reads as scuffed ground, not as magic. */
	private static final DustParticleOptions PRINT = new DustParticleOptions(0x4A3A2A, 0.7f);
	private static final double SEND_RANGE = 30;

	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Integer> STILL = new HashMap<>();
	private static final Map<UUID, Trail> TRAILS = new HashMap<>();

	/** One reading of the ground: whose trail, the prints so far, and when it fades. */
	public static final class Trail {
		final UUID animal;
		final List<Vec3> prints = new ArrayList<>();
		final long endsAt;

		Trail(UUID animal, long endsAt) {
			this.animal = animal;
			this.endsAt = endsAt;
		}

		public UUID animal() {
			return this.animal;
		}

		public int prints() {
			return this.prints.size();
		}
	}

	private Tracker() {
	}

	public static void tick(ServerPlayer player, int tick) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Trail trail = TRAILS.get(id);
		if (trail != null) {
			Entity animal = level.getEntity(trail.animal);
			if (now >= trail.endsAt || !Perks.has(player, SurvivalSkill.TRACKER)) {
				TRAILS.remove(id);
			} else {
				if (animal != null && animal.isAlive()) {
					Vec3 last = trail.prints.isEmpty() ? null : trail.prints.getLast();
					Vec3 at = animal.position();
					if (last == null || last.distanceToSqr(at) >= SurvivalRules.TRACKER_PRINT_SPACING * SurvivalRules.TRACKER_PRINT_SPACING) {
						trail.prints.add(at);
					}
				}
				if (tick % 20 == 0) {
					show(player, trail);
				}
			}
		}

		Vec3 last = LAST_POS.put(id, player.position());
		boolean still = last != null && last.distanceToSqr(player.position()) < 0.0025 && player.isShiftKeyDown() && player.onGround();
		if (!still || !Perks.has(player, SurvivalSkill.TRACKER)) {
			STILL.remove(id);
			return;
		}
		int ticks = STILL.merge(id, 10, Integer::sum);
		if (ticks == SurvivalRules.TRACKER_STILL) {
			read(player);
		}
	}

	/** Reads the ground: picks the nearest wild animal and lays its trail. */
	public static @Nullable Trail read(ServerPlayer player) {
		ServerLevel level = player.level();
		Animal animal = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(SurvivalRules.TRACKER_RANGE),
				a -> a.isAlive() && Hunting.wild(a) && a.distanceToSqr(player) > 9)
			.stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(player))).orElse(null);
		if (animal == null) {
			SurvivalSkill.chipThrottled(player, SurvivalSkill.TRACKER, 100, "tracker.none");
			return null;
		}
		Trail trail = new Trail(animal.getUUID(), level.getGameTime() + SurvivalRules.TRACKER_DURATION);
		// The ground between you and it, every couple of blocks, starting a little way ahead of your feet.
		Vec3 from = player.position();
		Vec3 to = animal.position();
		double length = from.distanceTo(to);
		for (double d = 2.5; d < length; d += SurvivalRules.TRACKER_PRINT_SPACING) {
			Vec3 p = from.lerp(to, d / length);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
			// Under trees or in a cave the heightmap is wrong; then follow the straight line instead.
			double y = Math.abs(ground - p.y) <= 6 ? ground : p.y;
			trail.prints.add(new Vec3(p.x, y, p.z));
		}
		trail.prints.add(to);
		TRAILS.put(player.getUUID(), trail);
		show(player, trail);
		SurvivalSkill.chip(player, SurvivalSkill.TRACKER, "tracker", animal.getType().getDescription(), (int) length, direction(to.subtract(from)));
		return trail;
	}

	private static Component direction(Vec3 d) {
		// 0 = south in Minecraft's yaw; compass points clockwise from north.
		double angle = Math.toDegrees(Math.atan2(d.x, -d.z));
		String[] points = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		int i = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
		return Component.translatable("mettle.feedback.survival.direction." + points[i]);
	}

	private static void show(ServerPlayer player, Trail trail) {
		ServerLevel level = player.level();
		for (Vec3 p : trail.prints) {
			if (p.distanceToSqr(player.position()) <= SEND_RANGE * SEND_RANGE) {
				level.sendParticles(player, PRINT, false, true, p.x, p.y + 0.06, p.z, 2, 0.18, 0.0, 0.18, 0.0);
			}
		}
	}

	/** Test hook: the player's live trail, if any. */
	public static @Nullable Trail trail(ServerPlayer player) {
		return TRAILS.get(player.getUUID());
	}

	public static void forget(UUID player) {
		LAST_POS.remove(player);
		STILL.remove(player);
		TRAILS.remove(player);
	}

	public static void clear() {
		LAST_POS.clear();
		STILL.clear();
		TRAILS.clear();
	}
}
